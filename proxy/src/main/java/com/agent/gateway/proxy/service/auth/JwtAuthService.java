package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.auth.AuthTokens;
import com.agent.gateway.proxy.client.AuthClient;
import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.AuthServiceException;
import com.agent.gateway.proxy.exception.AuthenticationRequiredException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.Optional;

/**
 * JWT Auth Service - Retrieves JWT tokens from external auth service (Quarkus)
 */
@Slf4j
public class JwtAuthService implements AuthService {
    
    private final ProxyProperties proxyProperties;
    private final AuthClient authClient;
    private final ProxyProperties.AuthRequestConfig authRequestConfig;
    
    public JwtAuthService(
        ProxyProperties proxyProperties,
        AuthClient authClient,
        ProxyProperties.AuthRequestConfig authRequestConfig) {
        this.proxyProperties = proxyProperties;
        this.authClient = authClient;
        this.authRequestConfig = authRequestConfig;
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
        
        log.debug(
            "Found sessionId: {}, requesting sparteGvo={}, btx={}, pss={}",
            sessionId.get(),
            authRequestConfig.sparteGvo(),
            authRequestConfig.btx(),
            authRequestConfig.pss()
        );
        
        // Call auth service to get tokens
        Optional<AuthTokens> authTokens = retrieveTokens(sessionId.get());
        
        if (authTokens.isPresent()) {
            AuthTokens tokens = authTokens.get();
            if (tokens.getGlueToken() != null) {
                headers.put("X-Glue-Token", tokens.getGlueToken());
            }
            if (tokens.getAuthZToken() != null) {
                headers.put("X-Auth-Z-Token", tokens.getAuthZToken());
            }
            if (tokens.getCustomerAccessToken() != null) {
                headers.put("X-Customer-Access-Token", tokens.getCustomerAccessToken());
            }
            if (tokens.getDisallowedPss() != null && !tokens.getDisallowedPss().isEmpty()) {
                throw new AuthServiceException(
                    "Auth service disallowed requested pss: " + tokens.getDisallowedPss());
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
    private Optional<AuthTokens> retrieveTokens(String sessionId) {
        try {
            // Call auth service using REST client
            log.debug(
                "Calling auth service with sessionId: {} and sparteGvo={}, btx={}, pss={}",
                sessionId,
                authRequestConfig.sparteGvo(),
                authRequestConfig.btx(),
                authRequestConfig.pss()
            );
            
            com.agent.gateway.proxy.auth.AuthRequest authRequest = 
                new com.agent.gateway.proxy.auth.AuthRequest(
                    authRequestConfig.sparteGvo(),
                    authRequestConfig.btx(),
                    authRequestConfig.pss()
                );
            
            AuthTokens tokens = authClient.getTokens(sessionId, authRequest);
            
            if (tokens != null) {
                if (tokens.getGlueToken() == null &&
                    tokens.getAuthZToken() == null &&
                    tokens.getCustomerAccessToken() == null) {
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
