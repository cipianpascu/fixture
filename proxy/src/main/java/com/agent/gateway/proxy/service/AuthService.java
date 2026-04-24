package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.auth.AuthRequest;
import com.agent.gateway.proxy.auth.AuthTokens;
import com.agent.gateway.proxy.config.ProxyProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Auth Service - Retrieves authentication tokens from auth service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {
    
    private final ProxyProperties proxyProperties;
    private final RestTemplate restTemplate;
    
    public AuthService(ProxyProperties proxyProperties, RestTemplateBuilder restTemplateBuilder) {
        this.proxyProperties = proxyProperties;
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(proxyProperties.getAuth().getTimeout())
            .setReadTimeout(proxyProperties.getAuth().getTimeout())
            .build();
    }
    
    /**
     * Extract sessionId from request and retrieve auth tokens with backend-specific scopes
     */
    public Optional<AuthTokens> getAuthTokens(HttpServletRequest request, List<String> scopes) {
        if (!proxyProperties.getAuth().isEnabled()) {
            log.debug("Auth is disabled, skipping token retrieval");
            return Optional.empty();
        }
        
        // Extract sessionId from request
        Optional<String> sessionId = extractSessionId(request);
        if (sessionId.isEmpty()) {
            log.warn("No sessionId found in request");
            return Optional.empty();
        }
        
        log.debug("Found sessionId: {}, requesting scopes: {}", sessionId.get(), scopes);
        
        // Call auth service to get tokens
        return retrieveTokens(sessionId.get(), scopes);
    }
    
    /**
     * Extract sessionId from request headers or cookies
     */
    private Optional<String> extractSessionId(HttpServletRequest request) {
        // Try header first
        String headerName = proxyProperties.getAuth().getSessionIdHeader();
        String sessionId = request.getHeader(headerName);
        if (sessionId != null && !sessionId.isEmpty()) {
            return Optional.of(sessionId);
        }
        
        // Try cookie
        String cookieName = proxyProperties.getAuth().getSessionIdCookie();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            return Arrays.stream(cookies)
                .filter(cookie -> cookieName.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst();
        }
        
        return Optional.empty();
    }
    
    /**
     * Retrieve tokens from auth service with scopes
     */
    private Optional<AuthTokens> retrieveTokens(String sessionId, List<String> scopes) {
        try {
            String authServiceUrl = proxyProperties.getAuth().getServiceUrl();
            if (authServiceUrl == null || authServiceUrl.isEmpty()) {
                log.warn("Auth service URL not configured");
                return Optional.empty();
            }
            
            // Build request to auth service
            HttpHeaders headers = new HttpHeaders();
            headers.set(proxyProperties.getAuth().getSessionIdHeader(), sessionId);
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            // Create request body with scopes
            AuthRequest authRequest = new AuthRequest(scopes);
            HttpEntity<AuthRequest> entity = new HttpEntity<>(authRequest, headers);
            
            // Call auth service
            log.debug("Calling auth service: {} with sessionId: {} and scopes: {}", 
                authServiceUrl, sessionId, scopes);
            ResponseEntity<AuthTokens> response = restTemplate.exchange(
                authServiceUrl,
                HttpMethod.POST,
                entity,
                AuthTokens.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                log.debug("Retrieved auth tokens successfully");
                return Optional.of(response.getBody());
            }
            
            log.warn("Auth service returned non-success status: {}", response.getStatusCode());
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error retrieving tokens from auth service", e);
            return Optional.empty();
        }
    }
}
