package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.auth.AuthRequest;
import com.agent.gateway.proxy.auth.AuthTokens;
import com.agent.gateway.proxy.config.ProxyProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * JWT Auth Service - Retrieves JWT tokens from external auth service
 */
@Component
@Slf4j
public class JwtAuthService implements AuthService {
    
    private final ProxyProperties proxyProperties;
    private final RestTemplate restTemplate;
    private final List<String> scopes;
    
    public JwtAuthService(ProxyProperties proxyProperties, RestTemplateBuilder restTemplateBuilder) {
        this.proxyProperties = proxyProperties;
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(proxyProperties.getAuth().getTimeout())
            .setReadTimeout(proxyProperties.getAuth().getTimeout())
            .build();
        this.scopes = List.of(); // Default empty, will be set per backend
    }
    
    /**
     * Create JWT auth service with specific scopes
     */
    public JwtAuthService(ProxyProperties proxyProperties, RestTemplateBuilder restTemplateBuilder, List<String> scopes) {
        this.proxyProperties = proxyProperties;
        this.restTemplate = restTemplateBuilder
            .setConnectTimeout(proxyProperties.getAuth().getTimeout())
            .setReadTimeout(proxyProperties.getAuth().getTimeout())
            .build();
        this.scopes = scopes;
    }
    
    @Override
    public void enrichHeaders(HttpServletRequest request, HttpHeaders headers) {
        if (!proxyProperties.getAuth().isEnabled()) {
            log.debug("Auth is disabled, skipping token retrieval");
            return;
        }
        
        // Extract sessionId from request
        Optional<String> sessionId = extractSessionId(request);
        if (sessionId.isEmpty()) {
            log.warn("No sessionId found in request");
            return;
        }
        
        log.debug("Found sessionId: {}, requesting scopes: {}", sessionId.get(), scopes);
        
        // Call auth service to get tokens
        Optional<AuthTokens> authTokens = retrieveTokens(sessionId.get(), scopes);
        
        if (authTokens.isPresent()) {
            AuthTokens tokens = authTokens.get();
            if (tokens.getServiceToken() != null) {
                headers.set("X-Service-Token", tokens.getServiceToken());
            }
            if (tokens.getUserGrantsToken() != null) {
                headers.set("X-User-Grants-Token", tokens.getUserGrantsToken());
            }
            log.debug("Attached JWT auth tokens to request");
        }
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
