package com.agent.gateway.controller;

import com.agent.gateway.config.GatewayProperties;
import com.agent.gateway.entity.BackendConfig;
import com.agent.gateway.entity.MockResponse;
import com.agent.gateway.model.GatewayMode;
import com.agent.gateway.service.BackendConfigService;
import com.agent.gateway.service.MockService;
import com.agent.gateway.service.OpenApiSchemaService;
import com.agent.gateway.service.ProxyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class GatewayController {

    private final GatewayProperties gatewayProperties;
    private final BackendConfigService backendConfigService;
    private final ProxyService proxyService;
    private final MockService mockService;
    private final OpenApiSchemaService schemaService;
    private final ObjectMapper objectMapper;

    @Hidden
    @RequestMapping(value = "/{backendName}/**", method = {RequestMethod.GET, RequestMethod.POST, 
            RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.OPTIONS})
    public ResponseEntity<String> handleRequest(@PathVariable String backendName,
                                                HttpServletRequest request,
                                                @RequestBody(required = false) String requestBody) {
        
        log.info("Received {} request for backend: {}", request.getMethod(), backendName);
        
        // Extract the remaining path after the backend name
        String fullPath = request.getRequestURI();
        String basePath = "/api/v1/" + backendName;
        String remainingPath = fullPath.substring(basePath.length());
        
        if (remainingPath.isEmpty()) {
            remainingPath = "/";
        }
        
        // Get backend configuration
        Optional<BackendConfig> backendOpt = backendConfigService.getBackendByName(backendName);
        if (backendOpt.isEmpty()) {
            log.warn("Backend not found: {}", backendName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\": \"Backend not found: " + backendName + "\"}");
        }
        
        BackendConfig backend = backendOpt.get();
        if (!backend.getEnabled()) {
            log.warn("Backend is disabled: {}", backendName);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"error\": \"Backend is currently disabled: " + backendName + "\"}");
        }
        
        // Route based on gateway mode
        if (gatewayProperties.getMode() == GatewayMode.FIXTURE) {
            return handleFixtureRequest(backendName, request.getMethod(), remainingPath, request, requestBody);
        } else {
            return handleRoutingRequest(backend, remainingPath, request, requestBody);
        }
    }

    private ResponseEntity<String> handleRoutingRequest(BackendConfig backend, String remainingPath,
                                                        HttpServletRequest request, String requestBody) {
        try {
            return proxyService.proxyRequest(backend, remainingPath, request, requestBody);
        } catch (Exception e) {
            log.error("Error routing request to backend: {}", backend.getName(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\": \"Failed to route request: " + e.getMessage() + "\"}");
        }
    }

    private ResponseEntity<String> handleFixtureRequest(String backendName, String method, String path,
                                                        HttpServletRequest request, String requestBody) {
        // Validate request against schema if configured
        Optional<BackendConfig> backendOpt = backendConfigService.getBackendByName(backendName);
        if (backendOpt.isPresent() && backendOpt.get().getOpenApiSchema() != null) {
            BackendConfig backend = backendOpt.get();
            OpenAPI openAPI = schemaService.parseSchema(backend.getOpenApiSchema());
            
            if (openAPI != null && schemaService.shouldEnforceValidation(backend)) {
                // Extract query parameters
                Map<String, String> queryParams = new java.util.HashMap<>();
                request.getParameterMap().forEach((key, values) -> {
                    if (values.length > 0) {
                        queryParams.put(key, values[0]);
                    }
                });
                
                OpenApiSchemaService.ValidationResult validationResult = schemaService.validateRequest(
                        openAPI, path, method, queryParams, requestBody);
                
                if (!validationResult.isValid()) {
                    log.error("Request validation failed: {}", validationResult.getMessage());
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("{\"error\": \"Request validation failed\", \"details\": \"" + 
                                  validationResult.getMessage() + "\"}");
                }
                
                if (validationResult.hasWarnings()) {
                    log.warn("Request validation warning: {}", validationResult.getMessage());
                }
            }
        }
        
        Optional<MockResponse> mockResponseOpt = mockService.findMatchingResponse(
                backendName, method, path, request, requestBody);
        
        if (mockResponseOpt.isEmpty()) {
            log.warn("No mock found for {} {} on backend {}", method, path, backendName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\": \"No mock configured for this endpoint\"}");
        }
        
        MockResponse mockResponse = mockResponseOpt.get();
        
        // Simulate delay if configured
        if (mockResponse.getDelayMs() != null && mockResponse.getDelayMs() > 0) {
            try {
                Thread.sleep(mockResponse.getDelayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        // Process template variables in response body
        // Note: pathParams are empty here but can be enhanced if needed
        String processedBody = mockService.processTemplateVariables(
                mockResponse.getResponseBody(), 
                request, 
                requestBody, 
                new java.util.HashMap<>()
        );
        
        // Build response headers
        HttpHeaders headers = new HttpHeaders();
        if (mockResponse.getResponseHeaders() != null && !mockResponse.getResponseHeaders().isEmpty()) {
            try {
                Map<String, String> headerMap = objectMapper.readValue(mockResponse.getResponseHeaders(), 
                        new TypeReference<Map<String, String>>() {});
                headerMap.forEach(headers::add);
            } catch (Exception e) {
                log.error("Error parsing response headers", e);
            }
        }
        
        // Set default content type if not specified
        if (!headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
            headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        }
        
        log.info("Returning mock response: {} with status {}", mockResponse.getName(), mockResponse.getHttpStatus());
        return new ResponseEntity<>(processedBody, headers, 
                HttpStatus.valueOf(mockResponse.getHttpStatus()));
    }

    @GetMapping("/health")
    @Operation(summary = "Get gateway health and active mode")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "mode", gatewayProperties.getMode(),
                "backends", backendConfigService.getEnabledBackends().size()
        ));
    }
}
