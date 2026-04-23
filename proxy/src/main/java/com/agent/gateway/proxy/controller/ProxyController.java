package com.agent.gateway.proxy.controller;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.service.ProxyService;
import com.agent.gateway.proxy.service.SchemaValidationService;
import com.agent.gateway.proxy.validation.ValidationResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Proxy Controller - Production Routing
 * 
 * Simple routing controller with schema validation.
 * NO admin endpoints, NO mocking - pure proxying only.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ProxyController {
    
    private final SchemaValidationService validationService;
    private final ProxyProperties proxyProperties;
    private final ProxyService proxyService;
    
    @RequestMapping(
        value = "/{backendName}/**",
        method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, 
                  RequestMethod.DELETE, RequestMethod.PATCH, RequestMethod.HEAD, 
                  RequestMethod.OPTIONS}
    )
    public ResponseEntity<?> proxy(
            @PathVariable String backendName,
            HttpServletRequest request,
            @RequestBody(required = false) String requestBody) {
        
        log.info("Proxying request: {} {}", request.getMethod(), request.getRequestURI());
        
        // 1. Find backend configuration
        ProxyProperties.BackendDefinition backend = findBackend(backendName);
        if (backend == null) {
            log.warn("Backend not found: {}", backendName);
            return ResponseEntity.status(404)
                .body(Map.of("error", "Backend not found: " + backendName));
        }
        
        // 2. Check if backend is enabled
        if (!backend.isEnabled()) {
            log.warn("Backend is disabled: {}", backendName);
            return ResponseEntity.status(503)
                .body(Map.of("error", "Backend is disabled: " + backendName));
        }
        
        // 3. Extract path
        String path = extractPath(request, backendName);
        
        // 4. Validate request against schema (if validation enabled)
        if (proxyProperties.getSchemas().isValidateRequests()) {
            ValidationResult validation = validationService.validateRequest(
                backend.getSchema(),
                request.getMethod(),
                path,
                requestBody,
                getHeaders(request)
            );
            
            if (!validation.isValid()) {
                log.warn("Request validation failed for {} {}: {}", 
                    request.getMethod(), path, validation.getErrors());
                return ResponseEntity.badRequest()
                    .body(Map.of(
                        "error", "Request validation failed",
                        "details", validation.getErrors()
                    ));
            }
        }
        
        // 5. Forward to backend
        return proxyService.forward(backend, request, requestBody);
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "module", "proxy",
            "backends", proxyProperties.getBackends().stream()
                .filter(ProxyProperties.BackendDefinition::isEnabled)
                .map(ProxyProperties.BackendDefinition::getName)
                .toList()
        ));
    }
    
    // Helper methods
    
    private ProxyProperties.BackendDefinition findBackend(String name) {
        return proxyProperties.getBackends().stream()
            .filter(b -> b.getName().equals(name))
            .findFirst()
            .orElse(null);
    }
    
    private String extractPath(HttpServletRequest request, String backendName) {
        String requestURI = request.getRequestURI();
        String prefix = "/api/v1/" + backendName;
        if (requestURI.startsWith(prefix)) {
            return requestURI.substring(prefix.length());
        }
        return requestURI;
    }
    
    private Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        var headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }
}
