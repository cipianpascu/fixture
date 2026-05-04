package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.auth.AuthTokens;
import com.agent.gateway.proxy.client.AuthClient;
import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.AuthServiceException;
import com.agent.gateway.proxy.exception.AuthenticationRequiredException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Locale;
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
    private final Map<String, String> securityConfig;
    
    public JwtAuthService(
        ProxyProperties proxyProperties,
        AuthClient authClient,
        ProxyProperties.AuthRequestConfig authRequestConfig,
        Map<String, String> securityConfig) {
        this.proxyProperties = proxyProperties;
        this.authClient = authClient;
        this.authRequestConfig = authRequestConfig;
        this.securityConfig = securityConfig == null ? Map.of() : Map.copyOf(securityConfig);
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
            if (tokens.getDisallowedPss() != null && !tokens.getDisallowedPss().isEmpty()) {
                throw new AuthServiceException(
                    "Auth service disallowed requested pss: " + tokens.getDisallowedPss());
            }
            applyConfiguredHeaders(tokens, headers);
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

    private void applyConfiguredHeaders(AuthTokens tokens, Map<String, String> headers) {
        String bearerSource = securityConfig.get("bearer-source");
        if (bearerSource != null && !bearerSource.isBlank()) {
            String bearerToken = tokenValue(tokens, bearerSource)
                .orElseThrow(() -> new AuthServiceException(
                    "Configured bearer-source '%s' did not resolve to a token".formatted(bearerSource)));
            String bearerHeader = securityConfig.getOrDefault("bearer-header", "Authorization");
            String prefix = securityConfig.getOrDefault("bearer-prefix", "Bearer");
            headers.put(bearerHeader, prefix.isBlank() ? bearerToken : prefix + " " + bearerToken);
        }

        Map<String, String> tokenHeaderMappings = prefixedEntries("token-headers.");
        for (Map.Entry<String, String> entry : tokenHeaderMappings.entrySet()) {
            String headerName = entry.getKey();
            String tokenSource = entry.getValue();
            String tokenValue = tokenValue(tokens, tokenSource)
                .orElseThrow(() -> new AuthServiceException(
                    "Configured token source '%s' for header '%s' did not resolve to a token"
                        .formatted(tokenSource, headerName)));
            headers.put(headerName, tokenValue);
        }

        for (Map.Entry<String, String> entry : prefixedEntries("static-headers.").entrySet()) {
            headers.put(entry.getKey(), entry.getValue());
        }

        if (bearerSource == null && tokenHeaderMappings.isEmpty()) {
            applyLegacyDefaultHeaders(tokens, headers);
        }
    }

    private Map<String, String> prefixedEntries(String prefix) {
        Map<String, String> values = new LinkedHashMap<>();
        securityConfig.forEach((key, value) -> {
            if (key != null && key.startsWith(prefix)) {
                values.put(key.substring(prefix.length()), value);
            }
        });
        return values;
    }

    private void applyLegacyDefaultHeaders(AuthTokens tokens, Map<String, String> headers) {
        if (tokens.getGlueToken() != null) {
            headers.put("X-Glue-Token", tokens.getGlueToken());
        }
        if (tokens.getAuthZToken() != null) {
            headers.put("X-Auth-Z-Token", tokens.getAuthZToken());
        }
        if (tokens.getCustomerAccessToken() != null) {
            headers.put("X-Customer-Access-Token", tokens.getCustomerAccessToken());
        }
    }

    private Optional<String> tokenValue(AuthTokens tokens, String tokenSource) {
        return switch (tokenSource.toLowerCase(Locale.ROOT)) {
            case "gluetoken", "glue_token" -> Optional.ofNullable(tokens.getGlueToken());
            case "authztoken", "auth_z_token" -> Optional.ofNullable(tokens.getAuthZToken());
            case "customeraccesstoken", "customer_access_token" ->
                Optional.ofNullable(tokens.getCustomerAccessToken());
            default -> Optional.empty();
        };
    }
}
