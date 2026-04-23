package com.agent.gateway.service;

import com.agent.gateway.entity.BackendConfig;
import com.agent.gateway.model.SecurityType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProxyService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @CircuitBreaker(name = "default", fallbackMethod = "proxyFallback")
    @Retry(name = "default")
    @TimeLimiter(name = "default")
    public ResponseEntity<String> proxyRequest(BackendConfig backend, String remainingPath, 
                                               HttpServletRequest request, String requestBody) {
        
        String targetUrl = buildTargetUrl(backend, remainingPath, request.getQueryString());
        log.info("Proxying {} request to: {}", request.getMethod(), targetUrl);
        
        HttpHeaders headers = copyHeaders(request);
        applySecurity(headers, backend);
        
        HttpEntity<String> httpEntity = new HttpEntity<>(requestBody, headers);
        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(targetUrl, method, httpEntity, String.class);
            log.info("Proxy response: {} from {}", response.getStatusCode(), targetUrl);
            return response;
        } catch (Exception e) {
            log.error("Error proxying request to {}: {}", targetUrl, e.getMessage());
            throw e;
        }
    }

    private String buildTargetUrl(BackendConfig backend, String remainingPath, String queryString) {
        StringBuilder url = new StringBuilder(backend.getBaseUrl());
        
        if (!remainingPath.startsWith("/")) {
            url.append("/");
        }
        url.append(remainingPath);
        
        if (queryString != null && !queryString.isEmpty()) {
            url.append("?").append(queryString);
        }
        
        return url.toString();
    }

    private HttpHeaders copyHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = request.getHeaderNames();
        
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                // Skip host header to avoid conflicts
                if (!"host".equalsIgnoreCase(headerName)) {
                    headers.put(headerName, Collections.list(request.getHeaders(headerName)));
                }
            }
        }
        
        return headers;
    }

    private void applySecurity(HttpHeaders headers, BackendConfig backend) {
        try {
            if (backend.getSecurityConfig() != null && !backend.getSecurityConfig().isEmpty()) {
                Map<String, String> securityConfig = objectMapper.readValue(backend.getSecurityConfig(), 
                        new TypeReference<Map<String, String>>() {});
                
                // Security is passed through from incoming request
                // Additional processing can be added here if needed
                log.debug("Security type: {} for backend: {}", backend.getSecurityType(), backend.getName());
            }
        } catch (Exception e) {
            log.error("Error processing security config", e);
        }
    }

    // Fallback method for circuit breaker
    public ResponseEntity<String> proxyFallback(BackendConfig backend, String remainingPath, 
                                                HttpServletRequest request, String requestBody, Throwable t) {
        log.error("Circuit breaker fallback triggered for backend: {}. Error: {}", 
                backend.getName(), t.getMessage());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("{\"error\": \"Service temporarily unavailable. Please try again later.\", \"backend\": \"" 
                        + backend.getName() + "\"}");
    }
}
