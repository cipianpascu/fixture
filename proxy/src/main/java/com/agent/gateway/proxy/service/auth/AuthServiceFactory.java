package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.client.AuthClient;
import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.service.TlsContextFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Map;

/**
 * Auth Service Factory - Creates appropriate auth service based on backend configuration (Quarkus)
 */
@ApplicationScoped
@Slf4j
public class AuthServiceFactory {
    
    @Inject
    ProxyProperties proxyProperties;

    @Inject
    TlsContextFactory tlsContextFactory;
    
    private AuthClient authClient;
    
    /**
     * Get or create auth client lazily using configured service URL
     */
    private AuthClient getAuthClient() {
        if (authClient == null) {
            String serviceUrl = proxyProperties.auth().serviceUrl();
            log.debug("Creating AuthClient with base URL: {}", serviceUrl);
            RestClientBuilder builder = RestClientBuilder.newBuilder()
                .baseUri(URI.create(serviceUrl))
                .connectTimeout(proxyProperties.auth().timeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(proxyProperties.auth().timeout().toMillis(), TimeUnit.MILLISECONDS);
            tlsContextFactory.createAuthSslContext().ifPresent(builder::sslContext);
            authClient = builder.build(AuthClient.class);
        }
        return authClient;
    }
    
    /**
     * Create auth service for a backend
     */
    public AuthService createAuthService(ProxyProperties.BackendDefinition backend) {
        String authType = backend.securityType().orElse(null);
        
        if (authType == null || authType.isEmpty()) {
            log.debug("No auth type configured for backend: {}", backend.name());
            return new NoOpAuthService();
        }
        
        return switch (authType.toLowerCase()) {
            case "jwt" -> createJwtAuthService(backend);
            case "basic", "basic_auth" -> createBasicAuthService(backend);
            case "none" -> new NoOpAuthService();
            default -> {
                throw new ProxyConfigurationException(
                    "Unsupported securityType '%s' for backend '%s'".formatted(authType, backend.name()));
            }
        };
    }
    
    /**
     * Create JWT auth service with backend-specific scopes
     */
    private AuthService createJwtAuthService(ProxyProperties.BackendDefinition backend) {
        log.debug("Creating JWT auth service for backend: {} with scopes: {}", 
            backend.name(), backend.authScopes().orElse(List.of()));
        return new JwtAuthService(
            proxyProperties, 
            getAuthClient(),  // Use lazy-initialized client
            backend.authScopes().orElse(List.of())
        );
    }
    
    /**
     * Create Basic auth service with credentials from backend config
     */
    private AuthService createBasicAuthService(ProxyProperties.BackendDefinition backend) {
        Map<String, String> config = backend.securityConfig();
        String username = config != null ? config.get("username") : null;
        String password = config != null ? config.get("password") : null;
        
        if (username == null || password == null) {
            throw new ProxyConfigurationException(
                "Basic auth configured but username/password missing for backend '%s'".formatted(backend.name()));
        }
        
        log.debug("Creating Basic auth service for backend: {} with username: {}", 
            backend.name(), username);
        return new BasicAuthService(username, password);
    }
    
    /**
     * No-op auth service (does nothing)
     */
    private static class NoOpAuthService implements AuthService {
        @Override
        public void enrichHeaders(ProxyRequestContext request, Map<String, String> headers) {
            // Do nothing
        }
    }
}
