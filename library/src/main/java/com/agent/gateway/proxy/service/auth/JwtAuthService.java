package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.auth.AuthTokens;
import com.agent.gateway.proxy.auth.AuthzRequest;
import com.agent.gateway.proxy.auth.AuthzTokens;
import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.AuthResponseMappingException;
import com.agent.gateway.proxy.exception.AuthServiceException;
import com.agent.gateway.proxy.exception.AuthenticationDeniedException;
import com.agent.gateway.proxy.exception.AuthorizationDeniedException;
import com.agent.gateway.proxy.exception.AuthenticationRequiredException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JWT Auth Service - Retrieves JWT tokens from external auth service (Quarkus)
 */
@Slf4j
public class JwtAuthService implements AuthService {
    
    private final ProxyProperties proxyProperties;
    private final AuthServiceCaller authServiceCaller;
    private final Optional<ProxyProperties.AuthRequestConfig> authRequestConfig;
    private final Optional<ProxyProperties.AuthzRequestConfig> authzRequestConfig;
    private final Map<String, String> securityConfig;
    
    public JwtAuthService(
        ProxyProperties proxyProperties,
        AuthServiceCaller authServiceCaller,
        Optional<ProxyProperties.AuthRequestConfig> authRequestConfig,
        Optional<ProxyProperties.AuthzRequestConfig> authzRequestConfig,
        Map<String, String> securityConfig) {
        this.proxyProperties = proxyProperties;
        this.authServiceCaller = authServiceCaller;
        this.authRequestConfig = authRequestConfig;
        this.authzRequestConfig = authzRequestConfig;
        this.securityConfig = securityConfig == null ? Map.of() : Map.copyOf(securityConfig);
    }
    
    @Override
    public void enrichHeaders(ProxyRequestContext request, Map<String, String> headers, String requestBody) {
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
            "Found sessionId: {}, requesting auth sparteGvo={}, btx={}, pss={} and authz branchCustomerNumber={}, gvoEntitlementsList={}, businessTransactions={}, serviceShopTransactions={}",
            sessionId.get(),
            authRequestConfig.flatMap(ProxyProperties.AuthRequestConfig::sparteGvo).orElse(null),
            authRequestConfig.flatMap(ProxyProperties.AuthRequestConfig::btx).orElse(null),
            authRequestConfig.flatMap(ProxyProperties.AuthRequestConfig::pss).orElse(null),
            resolveBranchCustomerNumber(request).orElse(null),
            authzRequestConfig.flatMap(ProxyProperties.AuthzRequestConfig::gvoEntitlementsList).orElse(null),
            authzRequestConfig.flatMap(ProxyProperties.AuthzRequestConfig::businessTransactions).orElse(null),
            authzRequestConfig.flatMap(ProxyProperties.AuthzRequestConfig::serviceShopTransactions).orElse(null)
        );

        Optional<AuthTokens> authTokens = authRequestConfig.isPresent()
            ? retrieveTokens(sessionId.get())
            : Optional.empty();
        Optional<AuthzTokens> authzTokens = authzRequestConfig.isPresent()
            ? retrieveAuthorizationTokens(sessionId.get(), request)
            : Optional.empty();

        if (authTokens.isPresent()) {
            AuthTokens tokens = authTokens.get();
            if (tokens.getDisallowedPss() != null && !tokens.getDisallowedPss().isEmpty()) {
                log.warn(
                    "Auth service denied requested pss. Requested sparteGvo={}, btx={}, pss={}, denied={}",
                    authRequestConfig.flatMap(ProxyProperties.AuthRequestConfig::sparteGvo).orElse(null),
                    authRequestConfig.flatMap(ProxyProperties.AuthRequestConfig::btx).orElse(null),
                    authRequestConfig.flatMap(ProxyProperties.AuthRequestConfig::pss).orElse(null),
                    tokens.getDisallowedPss()
                );
                throw new AuthorizationDeniedException(
                    "Auth service disallowed requested pss: " + tokens.getDisallowedPss());
            }
        }

        if (authzTokens.isPresent()) {
            AuthzTokens tokens = authzTokens.get();
            if (tokens.getDisallowedServiceShopTransactions() != null
                && !tokens.getDisallowedServiceShopTransactions().isEmpty()) {
                log.warn(
                    "Authz service denied requested serviceShopTransactions. Requested branchCustomerNumber={}, gvoEntitlementsList={}, businessTransactions={}, serviceShopTransactions={}, denied={}",
                    resolveBranchCustomerNumber(request).orElse(null),
                    authzRequestConfig.flatMap(ProxyProperties.AuthzRequestConfig::gvoEntitlementsList).orElse(null),
                    authzRequestConfig.flatMap(ProxyProperties.AuthzRequestConfig::businessTransactions).orElse(null),
                    authzRequestConfig.flatMap(ProxyProperties.AuthzRequestConfig::serviceShopTransactions).orElse(null),
                    tokens.getDisallowedServiceShopTransactions()
                );
                throw new AuthorizationDeniedException(
                    "Authz service disallowed requested serviceShopTransactions: "
                        + tokens.getDisallowedServiceShopTransactions());
            }
        }

        if (authTokens.isEmpty() && authzTokens.isEmpty()) {
            throw new AuthServiceException("Auth services returned no usable tokens");
        }

        applyConfiguredHeaders(authTokens.orElse(null), authzTokens.orElse(null), headers);
        log.debug("Attached JWT auth/authz tokens to request");
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
            ProxyProperties.AuthRequestConfig config = authRequestConfig.orElseThrow();
            log.debug(
                "Calling auth service with sessionId: {}, path={}, sparteGvo={}, btx={}, pss={}",
                sessionId,
                config.path().orElse("/auth/tokens/{sessionId}"),
                config.sparteGvo().orElse(null),
                config.btx().orElse(null),
                config.pss().orElse(null)
            );
            
            com.agent.gateway.proxy.auth.AuthRequest authRequest = 
                new com.agent.gateway.proxy.auth.AuthRequest(
                    config.sparteGvo().orElse(null),
                    config.btx().orElse(null),
                    config.pss().orElse(null)
                );

            AuthTokens tokens = authServiceCaller.postJson(
                resolveAuthPath(config.path().orElse(null), sessionId),
                authRequest,
                AuthTokens.class
            );
            
            if (tokens != null) {
                if (tokens.getGlueToken() == null &&
                    tokens.getAuthZToken() == null &&
                    tokens.getCustomerAccessToken() == null) {
                    log.warn(
                        "Auth service returned no token fields. Presence={}",
                        tokenPresence(tokens, null)
                    );
                    throw new AuthResponseMappingException(
                        "Authentication response did not contain any usable token");
                }
                log.debug("Retrieved auth tokens successfully");
                return Optional.of(tokens);
            }
            
            throw new AuthServiceException("Auth service returned null response");
        } catch (Exception e) {
            if (e instanceof AuthServiceException authServiceException) {
                throw authServiceException;
            }
            if (e instanceof AuthenticationRequiredException authenticationRequiredException) {
                throw authenticationRequiredException;
            }
            if (e instanceof AuthenticationDeniedException authenticationDeniedException) {
                throw authenticationDeniedException;
            }
            if (e instanceof AuthorizationDeniedException authorizationDeniedException) {
                throw authorizationDeniedException;
            }
            throw new AuthServiceException("Error retrieving tokens from auth service", e);
        }
    }

    private Optional<AuthzTokens> retrieveAuthorizationTokens(String sessionId, ProxyRequestContext request) {
        try {
            ProxyProperties.AuthzRequestConfig config = authzRequestConfig.orElseThrow();
            String branchCustomerNumber = resolveBranchCustomerNumber(request)
                .orElseThrow(() -> new AuthenticationRequiredException(
                    "Missing branchCustomerNumber for JWT authz flow"));

            log.debug(
                "Calling authz service with sessionId: {} and branchCustomerNumber={}, gvoEntitlementsList={}, businessTransactions={}, serviceShopTransactions={}",
                sessionId,
                branchCustomerNumber,
                config.gvoEntitlementsList().orElse(null),
                config.businessTransactions().orElse(null),
                config.serviceShopTransactions().orElse(null)
            );

            AuthzRequest authzRequest = new AuthzRequest(
                branchCustomerNumber,
                config.gvoEntitlementsList().orElse(null),
                config.businessTransactions().orElse(null),
                config.serviceShopTransactions().orElse(null)
            );

            AuthzTokens tokens = authServiceCaller.postJson(
                resolveAuthzPath(config.path(), sessionId),
                authzRequest,
                AuthzTokens.class
            );

            if (tokens != null) {
                if (tokens.getAuthorizationToken() == null
                    && tokens.getEidpAccessToken() == null
                    && tokens.getCustomerAccessToken() == null
                    && (tokens.getDisallowedServiceShopTransactions() == null
                        || tokens.getDisallowedServiceShopTransactions().isEmpty())) {
                    log.warn(
                        "Authz service returned no token fields. Presence={}",
                        tokenPresence(null, tokens)
                    );
                    throw new AuthResponseMappingException(
                        "Authorization response did not contain any usable token");
                }
                log.debug("Retrieved authz tokens successfully");
                return Optional.of(tokens);
            }

            throw new AuthServiceException("Authz service returned null response");
        } catch (Exception e) {
            if (e instanceof AuthServiceException authServiceException) {
                throw authServiceException;
            }
            if (e instanceof AuthenticationRequiredException authenticationRequiredException) {
                throw authenticationRequiredException;
            }
            if (e instanceof AuthenticationDeniedException authenticationDeniedException) {
                throw authenticationDeniedException;
            }
            if (e instanceof AuthorizationDeniedException authorizationDeniedException) {
                throw authorizationDeniedException;
            }
            throw new AuthServiceException("Error retrieving authorization tokens from auth service", e);
        }
    }

    private void applyConfiguredHeaders(AuthTokens authTokens, AuthzTokens authzTokens, Map<String, String> headers) {
        String bearerSource = securityConfig.get("bearer-source");
        if (bearerSource != null && !bearerSource.isBlank()) {
            String bearerToken = tokenValue(authTokens, authzTokens, bearerSource)
                .orElseThrow(() -> missingMappedToken(
                    "Configured bearer-source '%s' did not resolve to a token".formatted(bearerSource),
                    bearerSource,
                    null,
                    authTokens,
                    authzTokens));
            String bearerHeader = securityConfig.getOrDefault("bearer-header", "Authorization");
            String prefix = securityConfig.getOrDefault("bearer-prefix", "Bearer");
            headers.put(bearerHeader, prefix.isBlank() ? bearerToken : prefix + " " + bearerToken);
        }

        Map<String, String> tokenHeaderMappings = prefixedEntries("token-headers.");
        for (Map.Entry<String, String> entry : tokenHeaderMappings.entrySet()) {
            String headerName = entry.getKey();
            String tokenSource = entry.getValue();
            String tokenValue = tokenValue(authTokens, authzTokens, tokenSource)
                .orElseThrow(() -> missingMappedToken(
                    "Configured token source '%s' for header '%s' did not resolve to a token"
                        .formatted(tokenSource, headerName),
                    tokenSource,
                    headerName,
                    authTokens,
                    authzTokens));
            headers.put(headerName, tokenValue);
        }

        for (Map.Entry<String, String> entry : prefixedEntries("static-headers.").entrySet()) {
            headers.put(entry.getKey(), entry.getValue());
        }

        if (bearerSource == null && tokenHeaderMappings.isEmpty()) {
            applyLegacyDefaultHeaders(authTokens, headers);
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
        if (tokens == null) {
            return;
        }
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

    private Optional<String> tokenValue(AuthTokens authTokens, AuthzTokens authzTokens, String tokenSource) {
        return switch (tokenSource.toLowerCase(Locale.ROOT)) {
            case "gluetoken", "glue_token" ->
                Optional.ofNullable(authTokens != null ? authTokens.getGlueToken() : null);
            case "authztoken", "auth_z_token" ->
                Optional.ofNullable(authTokens != null ? authTokens.getAuthZToken() : null);
            case "customeraccesstoken", "customer_access_token" ->
                Optional.ofNullable(authTokens != null ? authTokens.getCustomerAccessToken() : null);
            case "authorizationtoken", "authorization_token" ->
                Optional.ofNullable(authzTokens != null ? authzTokens.getAuthorizationToken() : null);
            case "eidpaccesstoken", "eidp_access_token" ->
                Optional.ofNullable(authzTokens != null ? authzTokens.getEidpAccessToken() : null);
            case "authzcustomeraccesstoken", "authz_customer_access_token" ->
                Optional.ofNullable(authzTokens != null ? authzTokens.getCustomerAccessToken() : null);
            default -> Optional.empty();
        };
    }

    private AuthResponseMappingException missingMappedToken(
        String message,
        String tokenSource,
        String headerName,
        AuthTokens authTokens,
        AuthzTokens authzTokens) {
        if (headerName == null) {
            log.warn(
                "JWT auth mapping failed: {}. tokenSource={}, presence={}",
                message,
                tokenSource,
                tokenPresence(authTokens, authzTokens)
            );
        } else {
            log.warn(
                "JWT auth mapping failed: {}. tokenSource={}, headerName={}, presence={}",
                message,
                tokenSource,
                headerName,
                tokenPresence(authTokens, authzTokens)
            );
        }
        return new AuthResponseMappingException(message);
    }

    private Map<String, Boolean> tokenPresence(AuthTokens authTokens, AuthzTokens authzTokens) {
        Map<String, Boolean> presence = new LinkedHashMap<>();
        presence.put(
            "glue_token",
            authTokens != null && authTokens.getGlueToken() != null && !authTokens.getGlueToken().isBlank()
        );
        presence.put(
            "auth_z_token",
            authTokens != null && authTokens.getAuthZToken() != null && !authTokens.getAuthZToken().isBlank()
        );
        presence.put(
            "customer_access_token",
            authTokens != null
                && authTokens.getCustomerAccessToken() != null
                && !authTokens.getCustomerAccessToken().isBlank()
        );
        presence.put(
            "disallowed_pss",
            authTokens != null
                && authTokens.getDisallowedPss() != null
                && !authTokens.getDisallowedPss().isEmpty()
        );
        presence.put(
            "authorization_token",
            authzTokens != null
                && authzTokens.getAuthorizationToken() != null
                && !authzTokens.getAuthorizationToken().isBlank()
        );
        presence.put(
            "eidp_access_token",
            authzTokens != null
                && authzTokens.getEidpAccessToken() != null
                && !authzTokens.getEidpAccessToken().isBlank()
        );
        presence.put(
            "authz_customer_access_token",
            authzTokens != null
                && authzTokens.getCustomerAccessToken() != null
                && !authzTokens.getCustomerAccessToken().isBlank()
        );
        presence.put(
            "disallowed_service_shop_transactions",
            authzTokens != null
                && authzTokens.getDisallowedServiceShopTransactions() != null
                && !authzTokens.getDisallowedServiceShopTransactions().isEmpty()
        );
        return presence;
    }

    private Optional<String> resolveBranchCustomerNumber(ProxyRequestContext request) {
        return authzRequestConfig.flatMap(config ->
            config.branchCustomerNumberSource()
                .map(mapping -> resolveScalarMapping(mapping, request))
                .or(() -> config.branchCustomerNumber().filter(value -> !value.isBlank()))
        );
    }

    private String resolveScalarMapping(String mapping, ProxyRequestContext request) {
        if (mapping == null || mapping.isBlank()) {
            throw new AuthenticationRequiredException("branchCustomerNumber mapping must not be blank");
        }
        if (mapping.startsWith("header:")) {
            String headerName = mapping.substring("header:".length());
            String value = request.header(headerName);
            if (value == null || value.isBlank()) {
                throw new AuthenticationRequiredException(
                    "Missing required header '%s' for JWT authz flow".formatted(headerName));
            }
            return value;
        }
        if (mapping.startsWith("cookie:")) {
            String cookieName = mapping.substring("cookie:".length());
            String value = request.cookie(cookieName);
            if (value == null || value.isBlank()) {
                throw new AuthenticationRequiredException(
                    "Missing required cookie '%s' for JWT authz flow".formatted(cookieName));
            }
            return value;
        }
        if (mapping.startsWith("literal:")) {
            return mapping.substring("literal:".length());
        }
        return mapping;
    }

    private String resolveAuthzPath(String configuredPath, String sessionId) {
        String encodedSessionId = encodePathSegment(sessionId);
        if (configuredPath.contains("{sessionId}")) {
            return configuredPath.replace("{sessionId}", encodedSessionId);
        }
        return configuredPath.endsWith("/")
            ? configuredPath + encodedSessionId
            : configuredPath + "/" + encodedSessionId;
    }

    private String resolveAuthPath(String configuredPath, String sessionId) {
        String path = (configuredPath == null || configuredPath.isBlank())
            ? "/auth/tokens/{sessionId}"
            : configuredPath;
        return resolveAuthzPath(path, sessionId);
    }

    private String encodePathSegment(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
