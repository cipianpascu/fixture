package com.agent.gateway.service;

import com.agent.gateway.entity.BackendConfig;
import com.agent.gateway.entity.MockEndpoint;
import com.agent.gateway.entity.MockResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockGeneratorService {

    private final OpenApiSchemaService schemaService;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    /**
     * Generate mock endpoints from OpenAPI schema
     */
    public List<MockEndpoint> generateMockEndpoints(BackendConfig backend, OpenAPI openAPI) {
        List<MockEndpoint> endpoints = new ArrayList<>();

        Map<String, Map<String, Operation>> allEndpoints = schemaService.getAllEndpoints(openAPI);

        for (Map.Entry<String, Map<String, Operation>> pathEntry : allEndpoints.entrySet()) {
            String path = pathEntry.getKey();

            for (Map.Entry<String, Operation> methodEntry : pathEntry.getValue().entrySet()) {
                String method = methodEntry.getKey();
                Operation operation = methodEntry.getValue();

                MockEndpoint endpoint = MockEndpoint.builder()
                        .backendName(backend.getName())
                        .method(method)
                        .path(path)
                        .description(operation.getSummary() != null ? operation.getSummary() : operation.getDescription())
                        .openApiSchema(serializeOperation(operation))
                        .enabled(true)
                        .build();

                endpoints.add(endpoint);
            }
        }

        log.info("Generated {} mock endpoints from OpenAPI schema for backend: {}", 
                endpoints.size(), backend.getName());
        return endpoints;
    }

    /**
     * Generate mock responses for an endpoint with guided values
     */
    public List<MockResponse> generateMockResponses(MockEndpoint endpoint, OpenAPI openAPI,
                                                    Map<String, Object> guidedValues) {
        List<MockResponse> responses = new ArrayList<>();

        Map<String, Map<String, Operation>> allEndpoints = schemaService.getAllEndpoints(openAPI);
        Map<String, Operation> operations = allEndpoints.get(endpoint.getPath());

        if (operations == null) {
            log.warn("No operations found for path: {}", endpoint.getPath());
            return responses;
        }

        Operation operation = operations.get(endpoint.getMethod());
        if (operation == null || operation.getResponses() == null) {
            log.warn("No operation or responses found for {} {}", endpoint.getMethod(), endpoint.getPath());
            return responses;
        }

        // Generate response for each status code
        operation.getResponses().forEach((statusCode, apiResponse) -> {
            try {
                int httpStatus = parseStatusCode(statusCode);
                String responseBody = generateResponseBody(apiResponse, guidedValues);

                MockResponse mockResponse = MockResponse.builder()
                        .mockEndpoint(endpoint)
                        .name(apiResponse.getDescription() != null ? 
                              apiResponse.getDescription() : "Response " + statusCode)
                        .httpStatus(httpStatus)
                        .responseBody(responseBody)
                        .responseHeaders("{\"Content-Type\":\"application/json\"}")
                        .priority(httpStatus == 200 ? 10 : 5)
                        .enabled(httpStatus >= 200 && httpStatus < 300)
                        .delayMs(100)
                        .build();

                responses.add(mockResponse);
            } catch (Exception e) {
                log.error("Error generating response for status {}: {}", statusCode, e.getMessage());
            }
        });

        log.info("Generated {} mock responses for endpoint {} {}", 
                responses.size(), endpoint.getMethod(), endpoint.getPath());
        return responses;
    }

    /**
     * Generate a single mock response with guided values
     */
    public String generateGuidedMockResponse(OpenAPI openAPI, String path, String method,
                                            int statusCode, Map<String, Object> guidedValues) {
        Map<String, Map<String, Operation>> allEndpoints = schemaService.getAllEndpoints(openAPI);
        Map<String, Operation> operations = allEndpoints.get(path);

        if (operations == null) {
            throw new IllegalArgumentException("Path not found in schema: " + path);
        }

        Operation operation = operations.get(method);
        if (operation == null) {
            throw new IllegalArgumentException("Method " + method + " not found for path: " + path);
        }

        String statusKey = String.valueOf(statusCode);
        ApiResponse apiResponse = operation.getResponses().get(statusKey);
        if (apiResponse == null) {
            apiResponse = operation.getResponses().get("default");
        }

        if (apiResponse == null) {
            throw new IllegalArgumentException("Status code " + statusCode + " not found in schema");
        }

        return generateResponseBody(apiResponse, guidedValues);
    }

    // Private helper methods

    private String generateResponseBody(ApiResponse apiResponse, Map<String, Object> guidedValues) {
        if (apiResponse.getContent() == null || apiResponse.getContent().isEmpty()) {
            return "{}";
        }

        MediaType mediaType = apiResponse.getContent().get("application/json");
        if (mediaType == null || mediaType.getSchema() == null) {
            return "{}";
        }

        try {
            JsonNode generated = generateFromSchema(mediaType.getSchema(), guidedValues, "");
            return objectMapper.writeValueAsString(generated);
        } catch (Exception e) {
            log.error("Error generating response body", e);
            return "{}";
        }
    }

    private JsonNode generateFromSchema(Schema<?> schema, Map<String, Object> guidedValues, String path) {
        if (schema == null) {
            return objectMapper.createObjectNode();
        }

        // Handle references
        if (schema.get$ref() != null) {
            // For now, return empty object for references
            return objectMapper.createObjectNode();
        }

        String type = schema.getType();
        if (type == null && schema instanceof ArraySchema) {
            type = "array";
        }

        return switch (type != null ? type : "object") {
            case "object" -> generateObject(schema, guidedValues, path);
            case "array" -> generateArray(schema, guidedValues, path);
            case "string" -> generateString(schema, guidedValues, path);
            case "integer", "number" -> generateNumber(schema, guidedValues, path);
            case "boolean" -> generateBoolean(schema, guidedValues, path);
            default -> objectMapper.createObjectNode();
        };
    }

    private JsonNode generateObject(Schema<?> schema, Map<String, Object> guidedValues, String path) {
        ObjectNode object = objectMapper.createObjectNode();

        if (schema.getProperties() != null) {
            schema.getProperties().forEach((propName, propSchema) -> {
                String propPath = path.isEmpty() ? propName : path + "." + propName;
                
                // Check if there's a guided value for this property
                if (guidedValues.containsKey(propPath)) {
                    object.set(propName, convertToJsonNode(guidedValues.get(propPath)));
                } else {
                    object.set(propName, generateFromSchema((Schema<?>) propSchema, guidedValues, propPath));
                }
            });
        }

        // Add example values if no properties defined
        if (object.isEmpty() && schema.getExample() != null) {
            return convertToJsonNode(schema.getExample());
        }

        return object;
    }

    private JsonNode generateArray(Schema<?> schema, Map<String, Object> guidedValues, String path) {
        ArrayNode array = objectMapper.createArrayNode();

        // Check for guided array size
        int arraySize = 2; // default
        if (guidedValues.containsKey(path + "[].__size")) {
            Object sizeObj = guidedValues.get(path + "[].__size");
            if (sizeObj instanceof Number) {
                arraySize = ((Number) sizeObj).intValue();
            }
        }

        Schema<?> itemSchema = null;
        if (schema instanceof ArraySchema) {
            itemSchema = ((ArraySchema) schema).getItems();
        }

        for (int i = 0; i < arraySize; i++) {
            String itemPath = path + "[" + i + "]";
            if (itemSchema != null) {
                array.add(generateFromSchema(itemSchema, guidedValues, itemPath));
            } else {
                array.add(objectMapper.createObjectNode());
            }
        }

        return array;
    }

    private JsonNode generateString(Schema<?> schema, Map<String, Object> guidedValues, String path) {
        // Check for guided value
        if (guidedValues.containsKey(path)) {
            return convertToJsonNode(guidedValues.get(path));
        }

        // Use example if provided
        if (schema.getExample() != null) {
            return objectMapper.valueToTree(schema.getExample());
        }

        // Use enum if provided
        if (schema.getEnum() != null && !schema.getEnum().isEmpty()) {
            return objectMapper.valueToTree(schema.getEnum().get(0));
        }

        // Generate based on format
        String format = schema.getFormat();
        if (format != null) {
            return switch (format) {
                case "date" -> objectMapper.valueToTree(LocalDate.now().format(DateTimeFormatter.ISO_DATE));
                case "date-time" -> objectMapper.valueToTree(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME));
                case "email" -> objectMapper.valueToTree("user@example.com");
                case "uuid" -> objectMapper.valueToTree(UUID.randomUUID().toString());
                case "uri" -> objectMapper.valueToTree("https://example.com");
                default -> objectMapper.valueToTree("string_value");
            };
        }

        return objectMapper.valueToTree("string_value");
    }

    private JsonNode generateNumber(Schema<?> schema, Map<String, Object> guidedValues, String path) {
        // Check for guided value
        if (guidedValues.containsKey(path)) {
            return convertToJsonNode(guidedValues.get(path));
        }

        // Use example if provided
        if (schema.getExample() != null) {
            return objectMapper.valueToTree(schema.getExample());
        }

        // Generate within min/max if specified
        BigDecimal min = schema.getMinimum();
        BigDecimal max = schema.getMaximum();

        if (min != null && max != null) {
            double range = max.doubleValue() - min.doubleValue();
            double value = min.doubleValue() + (random.nextDouble() * range);
            return "integer".equals(schema.getType()) ? 
                   objectMapper.valueToTree((int) value) : 
                   objectMapper.valueToTree(value);
        }

        return "integer".equals(schema.getType()) ? 
               objectMapper.valueToTree(random.nextInt(100)) : 
               objectMapper.valueToTree(random.nextDouble() * 100);
    }

    private JsonNode generateBoolean(Schema<?> schema, Map<String, Object> guidedValues, String path) {
        // Check for guided value
        if (guidedValues.containsKey(path)) {
            return convertToJsonNode(guidedValues.get(path));
        }

        // Use example if provided
        if (schema.getExample() != null) {
            return objectMapper.valueToTree(schema.getExample());
        }

        return objectMapper.valueToTree(random.nextBoolean());
    }

    private JsonNode convertToJsonNode(Object value) {
        if (value instanceof JsonNode) {
            return (JsonNode) value;
        }
        return objectMapper.valueToTree(value);
    }

    private int parseStatusCode(String statusCode) {
        try {
            return Integer.parseInt(statusCode);
        } catch (NumberFormatException e) {
            return 200; // default for "default" or other non-numeric status codes
        }
    }

    private String serializeOperation(Operation operation) {
        try {
            return objectMapper.writeValueAsString(operation);
        } catch (Exception e) {
            log.error("Error serializing operation", e);
            return "{}";
        }
    }
}
