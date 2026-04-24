package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.config.ProxyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;

/**
 * Auth Service Factory - Creates appropriate auth service based on backend configuration
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthServiceFactory {
    
    private final ProxyProperties proxyProperties;
    private final RestTemplateBuilder restTemplateBuilder;
    
    /**
     * Create auth service for a backend
     */
    public AuthService createAuthService(ProxyProperties.BackendDefinition backend) {
        String authType = backend.getSecurityType();
        
        if (authType == null || authType.isEmpty()) {
            log.debug("No auth type configured for backend: {}", backend.getName());
            return new NoOpAuthService();
        }
        
        return switch (authType.toLowerCase()) {
            case "jwt" -> createJwtAuthService(backend);
            case "basic" -> createBasicAuthService(backend);
            default -> {
                log.warn("Unknown auth type '{}' for backend: {}, using no-op", authType, backend.getName());
                yield new NoOpAuthService();
            }
        };
    }
    
    /**
     * Create JWT auth service with backend-specific scopes
     */
    private AuthService createJwtAuthService(ProxyProperties.BackendDefinition backend) {
        log.debug("Creating JWT auth service for backend: {} with scopes: {}", 
            backend.getName(), backend.getAuthScopes());
        return new JwtAuthService(proxyProperties, restTemplateBuilder, backend.getAuthScopes());
    }
    
    /**
     * Create Basic auth service with credentials from backend config
     */
    private AuthService createBasicAuthService(ProxyProperties.BackendDefinition backend) {
        String username = backend.getSecurityConfig() != null 
            ? backend.getSecurityConfig().get("username") 
            : null;
        String password = backend.getSecurityConfig() != null 
            ? backend.getSecurityConfig().get("password") 
            : null;
        
        if (username == null || password == null) {
            log.error("Basic auth configured but username/password missing for backend: {}", backend.getName());
            return new NoOpAuthService();
        }
        
        log.debug("Creating Basic auth service for backend: {} with username: {}", 
            backend.getName(), username);
        return new BasicAuthService(username, password);
    }
    
    /**
     * No-op auth service (does nothing)
     */
    private static class NoOpAuthService implements AuthService {
        @Override
        public void enrichHeaders(jakarta.servlet.http.HttpServletRequest request, 
                                 org.springframework.http.HttpHeaders headers) {
            // Do nothing
        }
    }
}
