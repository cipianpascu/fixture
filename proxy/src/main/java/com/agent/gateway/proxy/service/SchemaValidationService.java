package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.validation.ValidationResult;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.ValidationMessage;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Schema Validation Service (OPTIMIZED - Quarkus)
 * 
 * Validates incoming requests against OpenAPI schemas.
 * Uses pre-compiled JSON schemas loaded at startup for performance.
 */
@ApplicationScoped
@Slf4j
public class SchemaValidationService {

    private record PathMatch(String schemaPath, PathItem pathItem) {
    }
    
    @Inject
    SchemaLoader schemaLoader;
    
    @Inject
    ProxyProperties proxyProperties;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Validate an incoming request against its schema
     */
    public ValidationResult validateRequest(
            String schemaName, 
            String method, 
            String path,
            String requestBody,
            Map<String, String> headers) {
        
        // If no schema name provided
        if (schemaName == null || schemaName.isEmpty()) {
            if (proxyProperties.schemas().strictMode()) {
                return ValidationResult.rejected("No schema configured for this backend");
            }
            log.warn("No schema configured, allowing request in non-strict mode");
            return ValidationResult.allowed();
        }
        
        // Load schema
        Optional<OpenAPI> schemaOpt = schemaLoader.getSchema(schemaName);
        
        if (schemaOpt.isEmpty()) {
            if (proxyProperties.schemas().strictMode()) {
                return ValidationResult.rejected("Schema not found: " + schemaName);
            }
            log.warn("Schema not found, allowing request in non-strict mode: {}", schemaName);
            return ValidationResult.allowed();
        }
        
        OpenAPI schema = schemaOpt.get();
        
        // Find matching path in schema
        PathMatch pathMatch = findMatchingPath(schema, path);
        if (pathMatch == null) {
            return ValidationResult.rejected(
                String.format("Path '%s' not found in schema '%s'", path, schemaName));
        }
        
        // Get operation for the HTTP method
        Operation operation = getOperation(pathMatch.pathItem(), method);
        if (operation == null) {
            return ValidationResult.rejected(
                String.format("Method %s not allowed for path %s in schema %s", 
                    method, path, schemaName));
        }
        
        // Validate request body if enabled and present (OPTIMIZED - uses cached schema)
        if (proxyProperties.schemas().validateBodies() && 
            requestBody != null && !requestBody.isEmpty() && !requestBody.isBlank()) {
            ValidationResult bodyValidation = validateRequestBody(
                schemaName, pathMatch.schemaPath(), method, requestBody);
            if (!bodyValidation.isValid()) {
                return bodyValidation;
            }
        }
        
        log.debug("Request validated successfully: {} {} against {}", method, path, schemaName);
        return ValidationResult.allowed();
    }

    public Response applyResponseContract(
        String schemaName,
        String method,
        String path,
        Response response) {

        if (!proxyProperties.schemas().validateResponses() || response == null) {
            return response;
        }

        if (schemaName == null || schemaName.isBlank()) {
            return response;
        }

        MediaType mediaType = response.getMediaType();
        if (mediaType != null && !mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            return response;
        }

        Object entity = response.getEntity();
        if (entity == null) {
            return response;
        }

        Optional<OpenAPI> schemaOpt = schemaLoader.getSchema(schemaName);
        if (schemaOpt.isEmpty()) {
            return response;
        }

        PathMatch pathMatch = findMatchingPath(schemaOpt.get(), path);
        if (pathMatch == null) {
            return response;
        }

        Operation operation = getOperation(pathMatch.pathItem(), method);
        if (operation == null || operation.getResponses() == null) {
            return response;
        }

        io.swagger.v3.oas.models.responses.ApiResponse apiResponse = responseSchemaForStatus(
            operation,
            response.getStatus()
        );
        if (apiResponse == null || apiResponse.getContent() == null) {
            return response;
        }

        io.swagger.v3.oas.models.media.MediaType responseMediaType = apiResponse.getContent().get("application/json");
        if (responseMediaType == null) {
            responseMediaType = apiResponse.getContent().get("*/*");
        }
        if (responseMediaType == null || responseMediaType.getSchema() == null) {
            return response;
        }

        try {
            JsonNode body = toJsonNode(entity);
            JsonNode trimmedBody = trimToSchema(body, responseMediaType.getSchema(), schemaOpt.get());
            return rebuildResponse(response, trimmedBody);
        } catch (Exception e) {
            log.warn("Failed to trim response payload for {} {} against {}", method, path, schemaName, e);
            return response;
        }
    }
    
    /**
     * Validate request body against pre-compiled schema (OPTIMIZED)
     * 
     * Uses cached JSON schemas pre-compiled at startup instead of converting
     * on every request. This provides significant performance improvement.
     */
    private ValidationResult validateRequestBody(
            String schemaName, String schemaPath, String method, String requestBody) {
        try {
            // Get pre-compiled JSON schema from cache (loaded at startup)
            JsonSchema jsonSchema = schemaLoader.getCompiledJsonSchema(schemaName, schemaPath, method);
            
            if (jsonSchema == null) {
                // No body schema defined for this operation - allow request
                log.debug("No request body schema defined for {} {}", method, schemaPath);
                return ValidationResult.allowed();
            }
            
            // Parse request body
            JsonNode requestJson = objectMapper.readTree(requestBody);
            
            // Validate using pre-compiled schema (FAST!)
            Set<ValidationMessage> errors = jsonSchema.validate(requestJson);
            
            if (!errors.isEmpty()) {
                List<String> errorMessages = errors.stream()
                    .map(ValidationMessage::getMessage)
                    .collect(Collectors.toList());
                
                log.warn("Request body validation failed: {}", errorMessages);
                return ValidationResult.rejected(errorMessages);
            }
            
            return ValidationResult.allowed();
            
        } catch (Exception e) {
            log.error("Error validating request body", e);
            // Be lenient on validation errors - allow the request
            return ValidationResult.allowed();
        }
    }
    
    /**
     * Find matching path in OpenAPI schema
     * Handles path parameters like /users/{id}
     */
    private PathMatch findMatchingPath(OpenAPI schema, String requestPath) {
        Paths paths = schema.getPaths();
        if (paths == null) {
            return null;
        }
        
        // First try exact match
        PathItem exactMatch = paths.get(requestPath);
        if (exactMatch != null) {
            return new PathMatch(requestPath, exactMatch);
        }
        
        // Try pattern matching for paths with parameters
        for (Map.Entry<String, PathItem> entry : paths.entrySet()) {
            String schemaPath = entry.getKey();
            if (pathMatches(schemaPath, requestPath)) {
                return new PathMatch(schemaPath, entry.getValue());
            }
        }
        
        return null;
    }
    
    /**
     * Check if a request path matches a schema path pattern
     * e.g., /users/123 matches /users/{id}
     */
    private boolean pathMatches(String schemaPath, String requestPath) {
        String[] schemaParts = schemaPath.split("/");
        String[] requestParts = requestPath.split("/");
        
        if (schemaParts.length != requestParts.length) {
            return false;
        }
        
        for (int i = 0; i < schemaParts.length; i++) {
            String schemaPart = schemaParts[i];
            String requestPart = requestParts[i];
            
            // Check if it's a path parameter (e.g., {id})
            if (schemaPart.startsWith("{") && schemaPart.endsWith("}")) {
                // Path parameter - any value matches
                continue;
            }
            
            // Literal path segment - must match exactly
            if (!schemaPart.equals(requestPart)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get the operation for a specific HTTP method
     */
    private Operation getOperation(PathItem pathItem, String method) {
        return switch (method.toUpperCase()) {
            case "GET" -> pathItem.getGet();
            case "POST" -> pathItem.getPost();
            case "PUT" -> pathItem.getPut();
            case "DELETE" -> pathItem.getDelete();
            case "PATCH" -> pathItem.getPatch();
            case "HEAD" -> pathItem.getHead();
            case "OPTIONS" -> pathItem.getOptions();
            default -> null;
        };
    }

    private io.swagger.v3.oas.models.responses.ApiResponse responseSchemaForStatus(Operation operation, int statusCode) {
        if (operation.getResponses() == null) {
            return null;
        }

        io.swagger.v3.oas.models.responses.ApiResponse exact = operation.getResponses().get(String.valueOf(statusCode));
        if (exact != null) {
            return exact;
        }

        String family = (statusCode / 100) + "XX";
        io.swagger.v3.oas.models.responses.ApiResponse familyMatch = operation.getResponses().get(family);
        if (familyMatch != null) {
            return familyMatch;
        }

        return operation.getResponses().get("default");
    }

    private JsonNode toJsonNode(Object entity) throws Exception {
        if (entity instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        if (entity instanceof String body) {
            return objectMapper.readTree(body);
        }
        return objectMapper.valueToTree(entity);
    }

    private JsonNode trimToSchema(
        JsonNode value,
        io.swagger.v3.oas.models.media.Schema<?> schema,
        OpenAPI openAPI) {

        if (value == null || value.isNull() || schema == null) {
            return value;
        }

        io.swagger.v3.oas.models.media.Schema<?> resolvedSchema = resolveSchema(schema, openAPI);
        if (resolvedSchema == null) {
            return value;
        }

        if ("array".equals(resolvedSchema.getType()) && value.isArray() && resolvedSchema.getItems() != null) {
            ArrayNode trimmedArray = objectMapper.createArrayNode();
            for (JsonNode item : value) {
                trimmedArray.add(trimToSchema(item, resolvedSchema.getItems(), openAPI));
            }
            return trimmedArray;
        }

        boolean objectLike = "object".equals(resolvedSchema.getType()) || resolvedSchema.getProperties() != null;
        if (objectLike && value.isObject()) {
            ObjectNode trimmedObject = objectMapper.createObjectNode();
            Map<String, io.swagger.v3.oas.models.media.Schema> properties = resolvedSchema.getProperties();
            if (properties == null || properties.isEmpty()) {
                return trimmedObject;
            }

            for (Map.Entry<String, io.swagger.v3.oas.models.media.Schema> property : properties.entrySet()) {
                JsonNode propertyValue = value.get(property.getKey());
                if (propertyValue != null) {
                    trimmedObject.set(property.getKey(), trimToSchema(propertyValue, property.getValue(), openAPI));
                }
            }
            return trimmedObject;
        }

        return value;
    }

    private io.swagger.v3.oas.models.media.Schema<?> resolveSchema(
        io.swagger.v3.oas.models.media.Schema<?> schema,
        OpenAPI openAPI) {

        if (schema.get$ref() == null) {
            return schema;
        }
        return resolveSchemaReference(schema.get$ref(), openAPI);
    }

    private io.swagger.v3.oas.models.media.Schema<?> resolveSchemaReference(String ref, OpenAPI openAPI) {
        if (ref == null || !ref.startsWith("#/components/schemas/")) {
            return null;
        }

        String schemaName = ref.substring("#/components/schemas/".length());
        if (openAPI.getComponents() == null || openAPI.getComponents().getSchemas() == null) {
            return null;
        }
        return openAPI.getComponents().getSchemas().get(schemaName);
    }

    private Response rebuildResponse(Response original, JsonNode entity) {
        Response.ResponseBuilder builder = Response.status(original.getStatus());
        MultivaluedMap<String, Object> headers = original.getHeaders();
        headers.forEach((name, values) -> {
            if ("content-length".equalsIgnoreCase(name)) {
                return;
            }
            values.forEach(value -> builder.header(name, value));
        });
        return builder.entity(entity.toString()).build();
    }
}
