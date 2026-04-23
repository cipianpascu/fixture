package com.agent.gateway.service;

import com.agent.gateway.entity.BackendConfig;
import com.agent.gateway.entity.MockEndpoint;
import com.agent.gateway.entity.MockResponse;
import com.agent.gateway.repository.MockEndpointRepository;
import com.agent.gateway.repository.MockResponseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to generate OpenAPI 3.0 schemas from existing mock endpoints and responses
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaGeneratorService {

    private final MockEndpointRepository mockEndpointRepository;
    private final MockResponseRepository mockResponseRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generate OpenAPI 3.0 schema from all mock endpoints for a backend
     */
    public String generateSchemaFromMocks(BackendConfig backend) {
        List<MockEndpoint> endpoints = mockEndpointRepository.findByBackendName(backend.getName());
        
        if (endpoints.isEmpty()) {
            log.warn("No mock endpoints found for backend: {}", backend.getName());
            return null;
        }

        try {
            ObjectNode schema = objectMapper.createObjectNode();
            
            // OpenAPI version
            schema.put("openapi", "3.0.0");
            
            // Info section
            ObjectNode info = schema.putObject("info");
            info.put("title", backend.getName() + " API");
            info.put("description", "Generated from mock endpoints for " + backend.getName());
            info.put("version", "1.0.0");
            
            // Servers section
            schema.putArray("servers")
                .addObject()
                .put("url", backend.getBaseUrl() + backend.getPath())
                .put("description", backend.getName() + " server");
            
            // Paths section
            ObjectNode paths = schema.putObject("paths");
            
            // Group endpoints by path
            Map<String, List<MockEndpoint>> endpointsByPath = endpoints.stream()
                .collect(Collectors.groupingBy(MockEndpoint::getPath));
            
            // Process each path
            for (Map.Entry<String, List<MockEndpoint>> entry : endpointsByPath.entrySet()) {
                String path = entry.getKey();
                List<MockEndpoint> pathEndpoints = entry.getValue();
                
                ObjectNode pathItem = paths.putObject(path);
                
                // Process each method for this path
                for (MockEndpoint endpoint : pathEndpoints) {
                    generateOperationFromEndpoint(pathItem, endpoint);
                }
            }
            
            // Components/schemas section
            ObjectNode components = schema.putObject("components");
            ObjectNode schemas = components.putObject("schemas");
            
            // Add Error schema
            ObjectNode errorSchema = schemas.putObject("Error");
            errorSchema.put("type", "object");
            ObjectNode errorProps = errorSchema.putObject("properties");
            errorProps.putObject("error").put("type", "string");
            errorProps.putObject("message").put("type", "string");
            
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
            
        } catch (Exception e) {
            log.error("Error generating schema from mocks", e);
            return null;
        }
    }

    private void generateOperationFromEndpoint(ObjectNode pathItem, MockEndpoint endpoint) {
        String method = endpoint.getMethod().toLowerCase();
        ObjectNode operation = pathItem.putObject(method);
        
        // Summary and description
        operation.put("summary", endpoint.getDescription() != null ? 
            endpoint.getDescription() : method.toUpperCase() + " " + endpoint.getPath());
        operation.put("operationId", endpoint.getMethod() + "_" + 
            endpoint.getPath().replaceAll("[^a-zA-Z0-9]", "_"));
        
        // Tags
        operation.putArray("tags").add(endpoint.getBackendName());
        
        // Get responses for this endpoint
        List<MockResponse> responses = mockResponseRepository
            .findByMockEndpointIdAndEnabled(endpoint.getId(), true);
        
        // Responses section
        ObjectNode responsesNode = operation.putObject("responses");
        
        if (responses.isEmpty()) {
            // Default response if no mocks
            addDefaultResponse(responsesNode);
        } else {
            // Group responses by status code
            Map<Integer, List<MockResponse>> responsesByStatus = responses.stream()
                .collect(Collectors.groupingBy(MockResponse::getHttpStatus));
            
            for (Map.Entry<Integer, List<MockResponse>> entry : responsesByStatus.entrySet()) {
                Integer statusCode = entry.getKey();
                List<MockResponse> statusResponses = entry.getValue();
                
                // Use the first response as example (highest priority)
                MockResponse exampleResponse = statusResponses.stream()
                    .max(Comparator.comparing(MockResponse::getPriority))
                    .orElse(statusResponses.get(0));
                
                addResponseToOperation(responsesNode, statusCode, exampleResponse);
            }
        }
        
        // Add parameters if path contains variables
        if (endpoint.getPath().contains("{")) {
            addPathParameters(operation, endpoint.getPath());
        }
    }

    private void addResponseToOperation(ObjectNode responsesNode, Integer statusCode, MockResponse mockResponse) {
        ObjectNode response = responsesNode.putObject(String.valueOf(statusCode));
        
        String description = getHttpStatusDescription(statusCode);
        if (mockResponse.getName() != null && !mockResponse.getName().isEmpty()) {
            description = mockResponse.getName() + " - " + description;
        }
        response.put("description", description);
        
        // Add content if response body exists
        if (mockResponse.getResponseBody() != null && !mockResponse.getResponseBody().isEmpty()) {
            ObjectNode content = response.putObject("content");
            ObjectNode mediaType = content.putObject("application/json");
            
            // Try to infer schema from response body
            try {
                JsonNode responseBody = objectMapper.readTree(mockResponse.getResponseBody());
                ObjectNode schema = inferSchemaFromJson(responseBody);
                mediaType.set("schema", schema);
                
                // Add example
                mediaType.set("example", responseBody);
            } catch (Exception e) {
                log.debug("Could not parse response body as JSON for inference: {}", e.getMessage());
                // If not JSON, just add as string schema
                mediaType.putObject("schema").put("type", "string");
            }
        }
        
        // Add headers if they exist
        if (mockResponse.getResponseHeaders() != null && !mockResponse.getResponseHeaders().isEmpty()) {
            try {
                Map<String, String> headers = objectMapper.readValue(
                    mockResponse.getResponseHeaders(), 
                    new TypeReference<Map<String, String>>() {}
                );
                
                if (!headers.isEmpty()) {
                    ObjectNode headersNode = response.putObject("headers");
                    for (String headerName : headers.keySet()) {
                        if (!headerName.equalsIgnoreCase("content-type")) {
                            ObjectNode headerSchema = headersNode.putObject(headerName);
                            headerSchema.putObject("schema").put("type", "string");
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Could not parse response headers: {}", e.getMessage());
            }
        }
    }

    private ObjectNode inferSchemaFromJson(JsonNode node) {
        ObjectNode schema = objectMapper.createObjectNode();
        
        if (node.isObject()) {
            schema.put("type", "object");
            ObjectNode properties = schema.putObject("properties");
            
            node.fields().forEachRemaining(field -> {
                String fieldName = field.getKey();
                JsonNode fieldValue = field.getValue();
                properties.set(fieldName, inferSchemaFromJson(fieldValue));
            });
            
        } else if (node.isArray()) {
            schema.put("type", "array");
            if (node.size() > 0) {
                schema.set("items", inferSchemaFromJson(node.get(0)));
            } else {
                schema.putObject("items").put("type", "object");
            }
            
        } else if (node.isBoolean()) {
            schema.put("type", "boolean");
        } else if (node.isInt() || node.isLong()) {
            schema.put("type", "integer");
        } else if (node.isFloat() || node.isDouble()) {
            schema.put("type", "number");
        } else if (node.isNull()) {
            schema.put("type", "string");
            schema.put("nullable", true);
        } else {
            schema.put("type", "string");
            
            // Try to detect format
            String value = node.asText();
            if (value.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
                schema.put("format", "date-time");
            } else if (value.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
                schema.put("format", "email");
            } else if (value.matches("^https?://.*")) {
                schema.put("format", "uri");
            }
        }
        
        return schema;
    }

    private void addDefaultResponse(ObjectNode responsesNode) {
        ObjectNode response = responsesNode.putObject("200");
        response.put("description", "Successful response");
        response.putObject("content")
            .putObject("application/json")
            .putObject("schema")
            .put("type", "object");
    }

    private void addPathParameters(ObjectNode operation, String path) {
        // Extract parameter names from path (e.g., /users/{id} -> id)
        String[] parts = path.split("/");
        for (String part : parts) {
            if (part.startsWith("{") && part.endsWith("}")) {
                String paramName = part.substring(1, part.length() - 1);
                
                ObjectNode param = operation.putArray("parameters").addObject();
                param.put("name", paramName);
                param.put("in", "path");
                param.put("required", true);
                param.putObject("schema").put("type", "string");
                param.put("description", paramName + " parameter");
            }
        }
    }

    private String getHttpStatusDescription(Integer statusCode) {
        return switch (statusCode) {
            case 200 -> "Successful response";
            case 201 -> "Created";
            case 204 -> "No content";
            case 400 -> "Bad request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not found";
            case 500 -> "Internal server error";
            default -> "Response";
        };
    }
}
