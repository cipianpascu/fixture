package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.service.auth.AuthService;
import com.agent.gateway.proxy.service.auth.AuthServiceFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Proxy Service - Lightweight Request Forwarding (Quarkus)
 * 
 * NO database dependencies - pure HTTP proxying.
 */
@ApplicationScoped
@Slf4j
public class ProxyService {
    
    @Inject
    AuthServiceFactory authServiceFactory;
    
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();
    
    /**
     * Forward request to backend
     */
    public Response forward(
            ProxyProperties.BackendDefinition backend,
            HttpServletRequest request,
            String requestBody) {
        
        try {
            // Build target URL
            String targetUrl = buildTargetUrl(backend, request);
            log.info("Forwarding {} request to: {}", request.getMethod(), targetUrl);
            
            // Build headers
            Map<String, String> headers = buildHeaders(request);
            
            // Get appropriate auth service for this backend and enrich headers
            AuthService authService = authServiceFactory.createAuthService(backend);
            authService.enrichHeaders(request, headers);
            
            // Build HTTP request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(backend.timeout());
            
            // Add headers
            headers.forEach(requestBuilder::header);
            
            // Set method and body
            HttpRequest.BodyPublisher bodyPublisher = requestBody != null && !requestBody.isEmpty()
                ? HttpRequest.BodyPublishers.ofString(requestBody)
                : HttpRequest.BodyPublishers.noBody();
            
            requestBuilder.method(request.getMethod(), bodyPublisher);
            
            // Forward request
            HttpResponse<String> response = httpClient.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
            );
            
            log.info("Received response: {} from {}", response.statusCode(), targetUrl);
            
            // Build JAX-RS response
            Response.ResponseBuilder responseBuilder = Response.status(response.statusCode());
            
            // Copy response headers
            response.headers().map().forEach((name, values) -> 
                values.forEach(value -> responseBuilder.header(name, value))
            );
            
            // Set body
            responseBuilder.entity(response.body());
            
            return responseBuilder.build();
            
        } catch (Exception e) {
            log.error("Error forwarding request to backend: {}", backend.name(), e);
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity("{\"error\":\"Failed to forward request: " + e.getMessage() + "\"}")
                .build();
        }
    }
    
    /**
     * Build target URL for backend
     */
    private String buildTargetUrl(ProxyProperties.BackendDefinition backend, HttpServletRequest request) {
        String requestURI = request.getRequestURI();
        
        // Remove the /api/v1/{backendName} prefix
        String prefix = "/api/v1/" + backend.name();
        String path = requestURI.startsWith(prefix) 
            ? requestURI.substring(prefix.length()) 
            : requestURI;
        
        // Build full URL
        String targetUrl = backend.baseUrl() + backend.path() + path;
        
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
    private Map<String, String> buildHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                
                // Skip Host header (will be set by HttpClient)
                if (!"Host".equalsIgnoreCase(headerName)) {
                    headers.put(headerName, headerValue);
                }
            }
        }
        
        return headers;
    }
}
