package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.auth.AuthTokens;
import com.agent.gateway.proxy.client.AuthClient;
import com.agent.gateway.proxy.config.ProxyProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JWT Auth Service - Retrieves JWT tokens from external auth service (Quarkus)
 */
@Slf4j
public class JwtAuthService implements AuthService {
    
    private final ProxyProperties proxyProperties;
    private final AuthClient authClient;
    private final List<String> scopes;
    
    public JwtAuthService(ProxyProperties proxyProperties, AuthClient authClient, List<String> scopes) {
        this.proxyProperties = proxyProperties;
        this.authClient = authClient;
        this.scopes = scopes != null ? scopes : List.of();
    }
    
    @Override
    public void enrichHeaders(HttpServletRequest request, Map<String, String> headers) {
        if (!proxyProperties.auth().enabled()) {
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
                headers.put("X-Service-Token", tokens.getServiceToken());
            }
            if (tokens.getUserGrantsToken() != null) {
                headers.put("X-User-Grants-Token", tokens.getUserGrantsToken());
            }
            log.debug("Attached JWT auth tokens to request");
        }
    }
    
    /**
     * Extract sessionId from request headers or cookies
     */
    private Optional<String> extractSessionId(HttpServletRequest request) {
        // Try header first
        String headerName = proxyProperties.auth().sessionIdHeader();
        String sessionId = request.getHeader(headerName);
        if (sessionId != null && !sessionId.isEmpty()) {
            return Optional.of(sessionId);
        }
        
        // Try cookie
        String cookieName = proxyProperties.auth().sessionIdCookie();
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
            // Call auth service using REST client
            // URL is configured via quarkus.rest-client.auth-service.url
            log.debug("Calling auth service with sessionId: {} and scopes: {}", sessionId, scopes);
            
            com.agent.gateway.proxy.auth.AuthRequest authRequest = 
                new com.agent.gateway.proxy.auth.AuthRequest(scopes);
            
            AuthTokens tokens = authClient.getTokens(sessionId, authRequest);
            
            if (tokens != null) {
                log.debug("Retrieved auth tokens successfully");
                return Optional.of(tokens);
            }
            
            log.warn("Auth service returned null response");
            return Optional.empty();
            
        } catch (Exception e) {
            log.error("Error retrieving tokens from auth service", e);
            return Optional.empty();
        }
    }
}
