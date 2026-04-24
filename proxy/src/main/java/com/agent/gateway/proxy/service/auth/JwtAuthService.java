package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.auth.AuthTokens;
import com.agent.gateway.proxy.client.AuthClient;
import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.AuthenticationRequiredException;
import com.agent.gateway.proxy.exception.AuthServiceException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import lombok.extern.slf4j.Slf4j;

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
    public void enrichHeaders(ProxyRequestContext request, Map<String, String> headers) {
        if (!proxyProperties.auth().enabled()) {
            log.debug("Auth is disabled, skipping token retrieval");
            return;
        }
        
        // Extract sessionId from request
        Optional<String> sessionId = extractSessionId(request);
        if (sessionId.isEmpty()) {
            throw new AuthenticationRequiredException(
                "Missing session identifier for JWT-authenticated backend");
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
            return;
        }

        throw new AuthServiceException("Auth service returned no usable tokens");
    }
    
    /**
     * Extract sessionId from request headers or cookies
     */
    private Optional<String> extractSessionId(ProxyRequestContext request) {
        // Try header first
        String headerName = proxyProperties.auth().sessionIdHeader();
        String sessionId = request.header(headerName);
        if (sessionId != null && !sessionId.isEmpty()) {
            return Optional.of(sessionId);
        }
        
        // Try cookie
        String cookieName = proxyProperties.auth().sessionIdCookie();
        String cookieValue = request.cookie(cookieName);
        if (cookieValue != null && !cookieValue.isEmpty()) {
            return Optional.of(cookieValue);
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
                if (tokens.getServiceToken() == null && tokens.getUserGrantsToken() == null) {
                    throw new AuthServiceException("Auth service returned an empty token payload");
                }
                log.debug("Retrieved auth tokens successfully");
                return Optional.of(tokens);
            }
            
            throw new AuthServiceException("Auth service returned null response");
            
        } catch (Exception e) {
            throw new AuthServiceException("Error retrieving tokens from auth service", e);
        }
    }
}
