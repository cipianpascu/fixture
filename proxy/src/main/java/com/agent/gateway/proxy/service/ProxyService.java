package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.auth.AuthTokens;
import com.agent.gateway.proxy.config.ProxyProperties;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Optional;

/**
 * Proxy Service - Lightweight Request Forwarding
 * 
 * NO database dependencies - pure HTTP proxying.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProxyService {
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final AuthService authService;
    
    /**
     * Forward request to backend
     */
    public ResponseEntity<String> forward(
            ProxyProperties.BackendDefinition backend,
            HttpServletRequest request,
            String requestBody) {
        
        try {
            // Build target URL
            String targetUrl = buildTargetUrl(backend, request);
            log.info("Forwarding {} request to: {}", request.getMethod(), targetUrl);
            
            // Build headers
            HttpHeaders headers = buildHeaders(request);
            
            // Get auth tokens with backend-specific scopes and attach to headers
            Optional<AuthTokens> authTokens = authService.getAuthTokens(request, backend.getAuthScopes());
            if (authTokens.isPresent()) {
                AuthTokens tokens = authTokens.get();
                if (tokens.getServiceToken() != null) {
                    headers.set("X-Service-Token", tokens.getServiceToken());
                }
                if (tokens.getUserGrantsToken() != null) {
                    headers.set("X-User-Grants-Token", tokens.getUserGrantsToken());
                }
                log.debug("Attached auth tokens to backend request");
            }
            
            // Create HTTP entity
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);
            
            // Forward request
            ResponseEntity<String> response = restTemplate.exchange(
                URI.create(targetUrl),
                HttpMethod.valueOf(request.getMethod()),
                entity,
                String.class
            );
            
            log.info("Received response: {} from {}", response.getStatusCode(), targetUrl);
            return response;
            
        } catch (Exception e) {
            log.error("Error forwarding request to backend: {}", backend.getName(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body("{\"error\":\"Failed to forward request: " + e.getMessage() + "\"}");
        }
    }
    
    /**
     * Build target URL for backend
     */
    private String buildTargetUrl(ProxyProperties.BackendDefinition backend, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        
        // Remove the /api/v1/{backendName} prefix
        String prefix = "/api/v1/" + backend.getName();
        String path = requestURI.startsWith(prefix) 
            ? requestURI.substring(prefix.length()) 
            : requestURI;
        
        // Build full URL
        String targetUrl = backend.getBaseUrl() + backend.getPath() + path;
        
        // Add query string if present
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }
        
        return targetUrl;
    }
    
    /**
     * Build HTTP headers from request
     */
    private HttpHeaders buildHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                
                // Skip Host header (will be set by RestTemplate)
                if (!"Host".equalsIgnoreCase(headerName)) {
                    headers.put(headerName, Collections.singletonList(headerValue));
                }
            }
        }
        
        return headers;
    }
}
