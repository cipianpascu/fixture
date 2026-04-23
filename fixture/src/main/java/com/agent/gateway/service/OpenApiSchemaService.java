package com.agent.gateway.service;

import com.agent.gateway.entity.BackendConfig;
import com.agent.gateway.model.ValidationMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OpenApiSchemaService {

    private final ObjectMapper objectMapper;

    /**
     * Parse and validate OpenAPI schema
     */
    public OpenAPI parseSchema(String schemaContent) {
        if (schemaContent == null || schemaContent.trim().isEmpty()) {
            return null;
        }

        try {
            ParseOptions options = new ParseOptions();
            options.setResolve(true);
            options.setResolveFully(true);

            SwaggerParseResult result = new OpenAPIV3Parser().readContents(schemaContent, null, options);

            if (result.getMessages() != null && !result.getMessages().isEmpty()) {
                log.warn("OpenAPI parsing warnings: {}", result.getMessages());
            }

            return result.getOpenAPI();
        } catch (Exception e) {
            log.error("Failed to parse OpenAPI schema", e);
            throw new IllegalArgumentException("Invalid OpenAPI schema: " + e.getMessage());
        }
    }

    /**
     * Validate if a path and method exist in the schema
     */
    public boolean validateEndpoint(OpenAPI openAPI, String path, String method) {
        if (openAPI == null || openAPI.getPaths() == null) {
            return true; // No schema means no validation
        }

        PathItem pathItem = openAPI.getPaths().get(path);
        if (pathItem == null) {
            // Try to match path patterns (e.g., /users/{id})
            pathItem = findMatchingPath(openAPI, path);
        }

        if (pathItem == null) {
            return false;
        }

        Operation operation = getOperationByMethod(pathItem, method);
        return operation != null;
    }

    /**
     * Validate response body against schema
     */
    public ValidationResult validateResponse(OpenAPI openAPI, String path, String method,
                                             int statusCode, String responseBody) {
        if (openAPI == null) {
            return ValidationResult.success();
        }

        try {
            PathItem pathItem = openAPI.getPaths().get(path);
            if (pathItem == null) {
                pathItem = findMatchingPath(openAPI, path);
            }

            if (pathItem == null) {
                return ValidationResult.warning("Path not found in schema: " + path);
            }

            Operation operation = getOperationByMethod(pathItem, method);
            if (operation == null) {
                return ValidationResult.warning("Method " + method + " not found for path: " + path);
            }

            if (operation.getResponses() == null) {
                return ValidationResult.success();
            }

            String statusKey = String.valueOf(statusCode);
            ApiResponse apiResponse = operation.getResponses().get(statusKey);
            if (apiResponse == null) {
                apiResponse = operation.getResponses().get("default");
            }

            if (apiResponse == null) {
                return ValidationResult.warning("Status code " + statusCode + " not defined in schema");
            }

            // Validate response body structure
            if (apiResponse.getContent() != null && !apiResponse.getContent().isEmpty()) {
                MediaType mediaType = apiResponse.getContent().get("application/json");
                if (mediaType != null && mediaType.getSchema() != null) {
                    return validateJsonAgainstSchema(responseBody, mediaType.getSchema());
                }
            }

            return ValidationResult.success();
        } catch (Exception e) {
            log.error("Error validating response", e);
            return ValidationResult.error("Validation error: " + e.getMessage());
        }
    }

    /**
     * Validate request against schema
     */
    public ValidationResult validateRequest(OpenAPI openAPI, String path, String method,
                                           Map<String, String> queryParams, String requestBody) {
        if (openAPI == null) {
            return ValidationResult.success();
        }

        try {
            PathItem pathItem = openAPI.getPaths().get(path);
            if (pathItem == null) {
                pathItem = findMatchingPath(openAPI, path);
            }

            if (pathItem == null) {
                return ValidationResult.warning("Path not found in schema: " + path);
            }

            Operation operation = getOperationByMethod(pathItem, method);
            if (operation == null) {
                return ValidationResult.warning("Method " + method + " not found for path: " + path);
            }

            // Validate query parameters
            if (operation.getParameters() != null) {
                for (Parameter param : operation.getParameters()) {
                    if ("query".equals(param.getIn()) && Boolean.TRUE.equals(param.getRequired())) {
                        if (queryParams == null || !queryParams.containsKey(param.getName())) {
                            return ValidationResult.error("Required query parameter missing: " + param.getName());
                        }
                    }
                }
            }

            // Validate request body
            if (operation.getRequestBody() != null && requestBody != null) {
                Content content = operation.getRequestBody().getContent();
                if (content != null && content.get("application/json") != null) {
                    MediaType mediaType = content.get("application/json");
                    if (mediaType.getSchema() != null) {
                        return validateJsonAgainstSchema(requestBody, mediaType.getSchema());
                    }
                }
            }

            return ValidationResult.success();
        } catch (Exception e) {
            log.error("Error validating request", e);
            return ValidationResult.error("Validation error: " + e.getMessage());
        }
    }

    /**
     * Get all paths and operations from schema
     */
    public Map<String, Map<String, Operation>> getAllEndpoints(OpenAPI openAPI) {
        Map<String, Map<String, Operation>> endpoints = new LinkedHashMap<>();

        if (openAPI == null || openAPI.getPaths() == null) {
            return endpoints;
        }

        openAPI.getPaths().forEach((path, pathItem) -> {
            Map<String, Operation> operations = new LinkedHashMap<>();
            
            if (pathItem.getGet() != null) operations.put("GET", pathItem.getGet());
            if (pathItem.getPost() != null) operations.put("POST", pathItem.getPost());
            if (pathItem.getPut() != null) operations.put("PUT", pathItem.getPut());
            if (pathItem.getDelete() != null) operations.put("DELETE", pathItem.getDelete());
            if (pathItem.getPatch() != null) operations.put("PATCH", pathItem.getPatch());
            if (pathItem.getOptions() != null) operations.put("OPTIONS", pathItem.getOptions());
            if (pathItem.getHead() != null) operations.put("HEAD", pathItem.getHead());

            if (!operations.isEmpty()) {
                endpoints.put(path, operations);
            }
        });

        return endpoints;
    }

    /**
     * Should validation be enforced based on backend config and validation mode
     */
    public boolean shouldEnforceValidation(BackendConfig backend) {
        return backend != null &&
               backend.getValidationMode() == ValidationMode.STRICT &&
               backend.getOpenApiSchema() != null;
    }

    public boolean shouldWarnOnValidation(BackendConfig backend) {
        return backend != null &&
               backend.getValidationMode() == ValidationMode.WARN &&
               backend.getOpenApiSchema() != null;
    }

    // Helper methods

    private PathItem findMatchingPath(OpenAPI openAPI, String requestPath) {
        if (openAPI.getPaths() == null) {
            return null;
        }

        // Try exact match first
        PathItem exactMatch = openAPI.getPaths().get(requestPath);
        if (exactMatch != null) {
            return exactMatch;
        }

        // Try pattern matching for path parameters
        for (Map.Entry<String, PathItem> entry : openAPI.getPaths().entrySet()) {
            String schemaPath = entry.getKey();
            if (pathMatches(schemaPath, requestPath)) {
                return entry.getValue();
            }
        }

        return null;
    }

    private boolean pathMatches(String schemaPath, String requestPath) {
        // Convert schema path like /users/{id} to regex
        String regex = schemaPath.replaceAll("\\{[^}]+\\}", "[^/]+");
        regex = "^" + regex + "$";
        return requestPath.matches(regex);
    }

    private Operation getOperationByMethod(PathItem pathItem, String method) {
        return switch (method.toUpperCase()) {
            case "GET" -> pathItem.getGet();
            case "POST" -> pathItem.getPost();
            case "PUT" -> pathItem.getPut();
            case "DELETE" -> pathItem.getDelete();
            case "PATCH" -> pathItem.getPatch();
            case "OPTIONS" -> pathItem.getOptions();
            case "HEAD" -> pathItem.getHead();
            default -> null;
        };
    }

    private ValidationResult validateJsonAgainstSchema(String jsonString, Schema<?> schema) {
        try {
            if (jsonString == null || jsonString.trim().isEmpty()) {
                return ValidationResult.success();
            }

            JsonNode jsonNode = objectMapper.readTree(jsonString);
            List<String> errors = new ArrayList<>();

            validateJsonNode(jsonNode, schema, "", errors);

            if (!errors.isEmpty()) {
                return ValidationResult.error("Schema validation failed: " + String.join(", ", errors));
            }

            return ValidationResult.success();
        } catch (Exception e) {
            return ValidationResult.error("JSON parsing error: " + e.getMessage());
        }
    }

    private void validateJsonNode(JsonNode jsonNode, Schema<?> schema, String path, List<String> errors) {
        if (schema == null) {
            return;
        }

        // Basic type validation
        String type = schema.getType();
        if (type != null) {
            boolean valid = switch (type) {
                case "object" -> jsonNode.isObject();
                case "array" -> jsonNode.isArray();
                case "string" -> jsonNode.isTextual();
                case "number", "integer" -> jsonNode.isNumber();
                case "boolean" -> jsonNode.isBoolean();
                default -> true;
            };

            if (!valid) {
                errors.add("Type mismatch at " + path + ": expected " + type + " but got " + jsonNode.getNodeType());
            }
        }

        // Validate required properties for objects
        if ("object".equals(type) && schema.getRequired() != null) {
            for (String requiredProp : schema.getRequired()) {
                if (!jsonNode.has(requiredProp)) {
                    errors.add("Required property missing at " + path + ": " + requiredProp);
                }
            }
        }

        // Keep validation simple - full JSON Schema validation would require additional library
        // This provides basic type and required field checking
    }

    /**
     * Validation result class
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final ValidationLevel level;

        private ValidationResult(boolean valid, String message, ValidationLevel level) {
            this.valid = valid;
            this.message = message;
            this.level = level;
        }

        public static ValidationResult success() {
            return new ValidationResult(true, null, ValidationLevel.SUCCESS);
        }

        public static ValidationResult warning(String message) {
            return new ValidationResult(true, message, ValidationLevel.WARNING);
        }

        public static ValidationResult error(String message) {
            return new ValidationResult(false, message, ValidationLevel.ERROR);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public ValidationLevel getLevel() {
            return level;
        }

        public boolean hasWarnings() {
            return level == ValidationLevel.WARNING;
        }

        public enum ValidationLevel {
            SUCCESS, WARNING, ERROR
        }
    }
}
