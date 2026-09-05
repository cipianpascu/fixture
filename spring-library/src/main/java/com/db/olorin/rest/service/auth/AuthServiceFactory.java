package com.db.olorin.rest.service.auth;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.exception.ProxyConfigurationException;
import com.db.olorin.rest.model.ProxyRequestContext;

import java.util.Map;

/** Creates the outbound authentication strategy configured for a backend. */
public class AuthServiceFactory {
    private final CloudRunIdTokenProvider cloudRunIdTokenProvider;
    private final AuthServiceConfigRegistry registry;
    private final AuthServiceCallerFactory callers;
    private final AuthzTokenCache cache;

    public AuthServiceFactory(CloudRunIdTokenProvider cloudRunIdTokenProvider, AuthServiceConfigRegistry registry,
        AuthServiceCallerFactory callers, AuthzTokenCache cache) {
        this.cloudRunIdTokenProvider = cloudRunIdTokenProvider;
        this.registry = registry;
        this.callers = callers;
        this.cache = cache;
    }

    public AuthService createAuthService(ProxyProperties.BackendDefinition backend) {
        String type = backend.securityType().orElse("none").toLowerCase();
        return switch (type) {
            case "", "none" -> (request, headers, body) -> { };
            case "basic", "basic_auth" -> basic(backend);
            case "cloudrun", "cloud_run" -> new CloudRunAuthService(
                cloudRunIdTokenProvider, backend.baseUrl(), backend.securityConfig());
            case "jwt" -> new JwtAuthService(backend, callers, cache);
            case "form" -> new FormAuthService(backend, callers, registry);
            default -> throw new ProxyConfigurationException(
                "Security type '%s' is not available in the Spring REST client for backend '%s'"
                    .formatted(type, backend.name()));
        };
    }

    private AuthService basic(ProxyProperties.BackendDefinition backend) {
        Map<String, String> config = backend.securityConfig();
        String username = config.get("username");
        String password = config.get("password");
        if (username == null || password == null) {
            throw new ProxyConfigurationException(
                "Basic authentication requires securityConfig.username and securityConfig.password for backend '%s'"
                    .formatted(backend.name()));
        }
        return new BasicAuthService(username, password);
    }
}
