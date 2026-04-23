package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.validation.ValidationResult;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Schema Validation Service
 * 
 * Validates incoming requests against OpenAPI schemas.
 * Ensures only valid requests are forwarded to backends.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaValidationService {
    
    private final SchemaLoader schemaLoader;
    private final ProxyProperties proxyProperties;
    
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
            if (proxyProperties.getSchemas().isStrictMode()) {
                return ValidationResult.rejected("No schema configured for this backend");
            }
            log.warn("No schema configured, allowing request in non-strict mode");
            return ValidationResult.allowed();
        }
        
        // Load schema
        Optional<OpenAPI> schemaOpt = schemaLoader.getSchema(schemaName);
        
        if (schemaOpt.isEmpty()) {
            if (proxyProperties.getSchemas().isStrictMode()) {
                return ValidationResult.rejected("Schema not found: " + schemaName);
            }
            log.warn("Schema not found, allowing request in non-strict mode: {}", schemaName);
            return ValidationResult.allowed();
        }
        
        OpenAPI schema = schemaOpt.get();
        
        // Find matching path in schema
        PathItem pathItem = findMatchingPath(schema, path);
        if (pathItem == null) {
            return ValidationResult.rejected(
                String.format("Path '%s' not found in schema '%s'", path, schemaName));
        }
        
        // Get operation for the HTTP method
        Operation operation = getOperation(pathItem, method);
        if (operation == null) {
            return ValidationResult.rejected(
                String.format("Method %s not allowed for path %s in schema %s", 
                    method, path, schemaName));
        }
        
        // TODO: Add request body validation if needed
        // For now, we just validate path and method existence
        
        log.debug("Request validated successfully: {} {} against {}", method, path, schemaName);
        return ValidationResult.allowed();
    }
    
    /**
     * Find matching path in OpenAPI schema
     * Handles path parameters like /users/{id}
     */
    private PathItem findMatchingPath(OpenAPI schema, String requestPath) {
        Paths paths = schema.getPaths();
        if (paths == null) {
            return null;
        }
        
        // First try exact match
        PathItem exactMatch = paths.get(requestPath);
        if (exactMatch != null) {
            return exactMatch;
        }
        
        // Try pattern matching for paths with parameters
        String[] requestParts = requestPath.split("/");
        
        for (Map.Entry<String, PathItem> entry : paths.entrySet()) {
            String schemaPath = entry.getKey();
            if (pathMatches(schemaPath, requestPath)) {
                return entry.getValue();
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
}
