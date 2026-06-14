package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.auth.AuthTokens;
import com.agent.gateway.proxy.auth.AuthzRequest;
import com.agent.gateway.proxy.auth.AuthzTokens;
import com.agent.gateway.proxy.config.ProxyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.agent.gateway.proxy.exception.AuthResponseMappingException;
import com.agent.gateway.proxy.exception.AuthServiceException;
import com.agent.gateway.proxy.exception.AuthenticationDeniedException;
import com.agent.gateway.proxy.exception.AuthorizationDeniedException;
import com.agent.gateway.proxy.exception.AuthenticationRequiredException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * JWT Auth Service - Retrieves JWT tokens from external auth service (Quarkus)
 */
@Slf4j
public class JwtAuthService implements AuthService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    
    private final Optional<AuthServiceCaller> authServiceCaller;
    private final Optional<ResolvedAuthServiceConfig> authServiceConfig;
    private final Optional<AuthServiceCaller> authzServiceCaller;
    private final Optional<ResolvedAuthServiceConfig> authzServiceConfig;
    private final Optional<ProxyProperties.AuthRequestConfig> authRequestConfig;
    private final Optional<ProxyProperties.AuthzRequestConfig> authzRequestConfig;
    private final Map<String, String> securityConfig;
    private final AuthzTokenCache authzTokenCache;
    
    public JwtAuthService(
        Optional<AuthServiceCaller> authServiceCaller,
        Optional<ResolvedAuthServiceConfig> authServiceConfig,
        Optional<AuthServiceCaller> authzServiceCaller,
        Optional<ResolvedAuthServiceConfig> authzServiceConfig,
        Optional<ProxyProperties.AuthRequestConfig> authRequestConfig,
        Optional<ProxyProperties.AuthzRequestConfig> authzRequestConfig,
        Map<String, String> securityConfig,
        AuthzTokenCache authzTokenCache) {
        this.authServiceCaller = authServiceCaller;
        this.authServiceConfig = authServiceConfig;
        this.authzServiceCaller = authzServiceCaller;
        this.authzServiceConfig = authzServiceConfig;
        this.authRequestConfig = authRequestConfig;
        this.authzRequestConfig = authzRequestConfig;
        this.securityConfig = securityConfig == null ? Map.of() : Map.copyOf(securityConfig);
        this.authzTokenCache = authzTokenCache;
    }
    
    @Override
    public void enrichHeaders(ProxyRequestContext request, Map<String, String> headers, String requestBody) {
        enrichHeaders(null, request, headers, requestBody);
    }

    @Override
    public void enrichHeaders(
        ProxyProperties.BackendDefinition backend,
        ProxyRequestContext request,
        Map<String, String> headers,
        String requestBody) {
        String backendName = backend == null ? null : backend.name();
        log.debug(
            "Preparing JWT auth for auth-service={}, authz-service={}, auth sparteGvo={}, btx={}, pss={} and authz branchCustomerNumber={}, gvoEntitlementsList={}, businessTransactions={}, serviceShopTransactions={}",
            authServiceConfig.map(ResolvedAuthServiceConfig::name).orElse(null),
            authzServiceConfig.map(ResolvedAuthServiceConfig::name).orElse(null),
            authRequestConfig.flatMap(ProxyProperties.AuthRequestConfig::sparteGvo).orElse(null),
            authRequestConfig.flatMap(ProxyProperties.AuthRequestConfig::btx).orElse(null),
            authRequestConfig.flatMap(ProxyProperties.AuthRequestConfig::pss).orElse(null),
            resolveBranchCustomerNumber(request).orElse(null),
            authzRequestConfig.flatMap(ProxyProperties.AuthzRequestConfig::gvoEntitlementsList).orElse(null),
            authzRequestConfig.flatMap(ProxyProperties.AuthzRequestConfig::businessTransactions).orElse(null),
            authzRequestConfig.flatMap(ProxyProperties.AuthzRequestConfig::serviceShopTransactions).orElse(null)
        );

        Optional<AuthTokens> authTokens = authRequestConfig.isPresent() && authServiceConfig.map(ResolvedAuthServiceConfig::enabled).orElse(false)
            ? retrieveTokens(resolveSessionId(request, authServiceConfig, "auth"))
            : Optional.empty();
        Optional<AuthzTokens> authzTokens = authzRequestConfig.isPresent() && authzServiceConfig.map(ResolvedAuthServiceConfig::enabled).orElse(false)
            ? retrieveAuthorizationTokens(resolveSessionId(request, authzServiceConfig, "authz"), request, backendName)
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
            List<String> requestedTransactions = authzRequestConfig
                .flatMap(ProxyProperties.AuthzRequestConfig::serviceShopTransactions)
                .orElse(List.of());
            List<String> deniedTransactions = requestedTransactions.stream()
                .filter(requested -> tokens.getAllowedServiceShopTransactions() == null
                    || !tokens.getAllowedServiceShopTransactions().contains(requested))
                .toList();

            if (!deniedTransactions.isEmpty()) {
                log.warn(
                    "Authz service did not allow requested serviceShopTransactions. Requested branchCustomerNumber={}, gvoEntitlementsList={}, businessTransactions={}, serviceShopTransactions={}, allowed={}, denied={}",
                    resolveBranchCustomerNumber(request).orElse(null),
                    authzRequestConfig.flatMap(ProxyProperties.AuthzRequestConfig::gvoEntitlementsList).orElse(null),
                    authzRequestConfig.flatMap(ProxyProperties.AuthzRequestConfig::businessTransactions).orElse(null),
                    authzRequestConfig.flatMap(ProxyProperties.AuthzRequestConfig::serviceShopTransactions).orElse(null),
                    tokens.getAllowedServiceShopTransactions(),
                    deniedTransactions
                );
                throw new AuthorizationDeniedException(
                    "Authz service disallowed requested serviceShopTransactions: " + deniedTransactions);
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
    private Optional<String> extractSessionId(ProxyRequestContext request, ResolvedAuthServiceConfig serviceConfig) {
        // Try header first
        String headerName = serviceConfig.sessionIdHeader();
        String sessionId = request.header(headerName);
        if (sessionId != null && !sessionId.isEmpty()) {
            return Optional.of(sessionId);
        }
        
        // Try cookie
        String cookieName = serviceConfig.sessionIdCookie();
        String cookieValue = request.cookie(cookieName);
        if (cookieValue != null && !cookieValue.isEmpty()) {
            return Optional.of(cookieValue);
        }
        
        return Optional.empty();
    }

    private String resolveSessionId(
        ProxyRequestContext request,
        Optional<ResolvedAuthServiceConfig> serviceConfig,
        String flowName) {
        ResolvedAuthServiceConfig config = serviceConfig.orElseThrow(() -> new AuthServiceException(
            "Missing %s service configuration for JWT-authenticated backend".formatted(flowName)));
        return extractSessionId(request, config)
            .orElseThrow(() -> new AuthenticationRequiredException(
                "Missing session identifier for JWT-authenticated backend"));
    }
    
    /**
     * Retrieve tokens from auth service with scopes
     */
    private Optional<AuthTokens> retrieveTokens(String sessionId) {
        try {
            ProxyProperties.AuthRequestConfig config = authRequestConfig.orElseThrow();
            AuthServiceCaller caller = authServiceCaller.orElseThrow();
            log.debug(
                "Calling auth service '{}' with sessionId: {}, path={}, sparteGvo={}, btx={}, pss={}",
                authServiceConfig.map(ResolvedAuthServiceConfig::name).orElse("unknown"),
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

            AuthTokens tokens = caller.postJson(
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

    private Optional<AuthzTokens> retrieveAuthorizationTokens(
        String sessionId,
        ProxyRequestContext request,
        String backendName) {
        try {
            ProxyProperties.AuthzRequestConfig config = authzRequestConfig.orElseThrow();
            AuthServiceCaller caller = authzServiceCaller.orElseThrow();
            ResolvedAuthServiceConfig serviceConfig = authzServiceConfig.orElseThrow();
            String branchCustomerNumber = resolveBranchCustomerNumber(request)
                .orElseThrow(() -> new AuthenticationRequiredException(
                    "Missing branchCustomerNumber for JWT authz flow"));

            if (backendName != null && serviceConfig.cache().enabled() && authzTokenCache != null) {
                Optional<AuthzTokens> cachedTokens = authzTokenCache.get(sessionId, backendName);
                if (cachedTokens.isPresent()) {
                    log.debug(
                        "Using cached authz tokens for service '{}', backend '{}', sessionId '{}'",
                        serviceConfig.name(),
                        backendName,
                        sessionId
                    );
                    return cachedTokens;
                }
            }

            log.debug(
                "Calling authz service '{}' with sessionId: {} and branchCustomerNumber={}, gvoEntitlementsList={}, businessTransactions={}, serviceShopTransactions={}",
                serviceConfig.name(),
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

            AuthzTokens tokens = caller.postJson(
                resolveAuthzPath(config.path(), sessionId),
                authzRequest,
                AuthzTokens.class
            );

            if (tokens != null) {
                if (tokens.getAuthorizationToken() == null
                    && tokens.getGlueAccessToken() == null
                    && tokens.getCustomerAccessToken() == null
                    && (tokens.getAllowedServiceShopTransactions() == null
                        || tokens.getAllowedServiceShopTransactions().isEmpty())) {
                    log.warn(
                        "Authz service returned no token fields. Presence={}",
                        tokenPresence(null, tokens)
                    );
                    throw new AuthResponseMappingException(
                        "Authorization response did not contain any usable token");
                }
                log.debug("Retrieved authz tokens successfully");
                cacheAuthorizationTokens(serviceConfig, sessionId, backendName, tokens);
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
            case "glueaccesstoken", "glue_access_token", "eidpaccesstoken", "eidp_access_token" ->
                Optional.ofNullable(authzTokens != null ? authzTokens.getGlueAccessToken() : null);
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
            "glue_access_token",
            authzTokens != null
                && authzTokens.getGlueAccessToken() != null
                && !authzTokens.getGlueAccessToken().isBlank()
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
                && authzTokens.getGlueAccessToken() != null
                && !authzTokens.getGlueAccessToken().isBlank()
        );
        presence.put(
            "authz_customer_access_token",
            authzTokens != null
                && authzTokens.getCustomerAccessToken() != null
                && !authzTokens.getCustomerAccessToken().isBlank()
        );
        presence.put(
            "allowed_service_shop_transactions",
            authzTokens != null
                && authzTokens.getAllowedServiceShopTransactions() != null
                && !authzTokens.getAllowedServiceShopTransactions().isEmpty()
        );
        return presence;
    }

    private Optional<String> resolveBranchCustomerNumber(ProxyRequestContext request) {
        return authzRequestConfig.flatMap(config ->
            config.branchCustomerNumber()
                .filter(value -> !value.isBlank())
                .map(mapping -> resolveScalarMapping(mapping, request))
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

    private void cacheAuthorizationTokens(
        ResolvedAuthServiceConfig serviceConfig,
        String sessionId,
        String backendName,
        AuthzTokens tokens) {
        if (backendName == null || !serviceConfig.cache().enabled() || authzTokenCache == null) {
            return;
        }

        tokenExpiry(tokens)
            .map(expiresAt -> expiresAt.minus(serviceConfig.cache().expirySkew()))
            .ifPresentOrElse(
                expiresAt -> authzTokenCache.put(
                    sessionId,
                    backendName,
                    tokens,
                    expiresAt,
                    serviceConfig.cache().maxSize()
                ),
                () -> log.debug(
                    "Skipping authz token cache for service '{}' and backend '{}' because no JWT exp claim was found",
                    serviceConfig.name(),
                    backendName
                )
            );
    }

    private Optional<Instant> tokenExpiry(AuthzTokens tokens) {
        return Stream.of(
                tokens.getAuthorizationToken(),
                tokens.getGlueAccessToken(),
                tokens.getCustomerAccessToken()
            )
            .flatMap(token -> jwtExpiry(token).stream())
            .min(Instant::compareTo);
    }

    private Optional<Instant> jwtExpiry(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            JsonNode payload = OBJECT_MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
            JsonNode exp = payload.get("exp");
            if (exp == null || !exp.canConvertToLong()) {
                return Optional.empty();
            }
            return Optional.of(Instant.ofEpochSecond(exp.asLong()));
        } catch (Exception e) {
            log.debug("Unable to decode JWT exp claim for authz cache", e);
            return Optional.empty();
        }
    }
}
