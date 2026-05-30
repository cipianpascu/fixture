package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

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
    AuthServiceCaller authServiceCaller;

    @Inject
    CloudRunIdTokenProvider cloudRunIdTokenProvider;
    
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
            case "form" -> createFormAuthService(backend);
            case "transactionid", "transaction_id" -> createTransactionIdAuthService(backend);
            case "cloudrun", "cloud_run" -> createCloudRunAuthService(backend);
            case "none" -> new NoOpAuthService();
            default -> {
                throw new ProxyConfigurationException(
                    "Unsupported securityType '%s' for backend '%s'".formatted(authType, backend.name()));
            }
        };
    }
    
    /**
     * Create JWT auth service with backend-specific auth request
     */
    private AuthService createJwtAuthService(ProxyProperties.BackendDefinition backend) {
        if (backend.authRequest().isEmpty() && backend.authzRequest().isEmpty()) {
            throw new ProxyConfigurationException(
                "JWT auth configured but both auth-request and authz-request are missing for backend '%s'"
                    .formatted(backend.name()));
        }
        log.debug(
            "Creating JWT auth service for backend: {} with auth-request: sparteGvo={}, btx={}, pss={} and authz-request: path={}, branchCustomerNumber={}, gvoEntitlementsList={}, businessTransactions={}, serviceShopTransactions={}",
            backend.name(),
            backend.authRequest().flatMap(ProxyProperties.AuthRequestConfig::sparteGvo).orElse(null),
            backend.authRequest().flatMap(ProxyProperties.AuthRequestConfig::btx).orElse(null),
            backend.authRequest().flatMap(ProxyProperties.AuthRequestConfig::pss).orElse(null),
            backend.authzRequest().map(ProxyProperties.AuthzRequestConfig::path).orElse(null),
            backend.authzRequest().flatMap(ProxyProperties.AuthzRequestConfig::branchCustomerNumber).orElse(null),
            backend.authzRequest().flatMap(ProxyProperties.AuthzRequestConfig::gvoEntitlementsList).orElse(null),
            backend.authzRequest().flatMap(ProxyProperties.AuthzRequestConfig::businessTransactions).orElse(null),
            backend.authzRequest().flatMap(ProxyProperties.AuthzRequestConfig::serviceShopTransactions).orElse(null)
        );
        return new JwtAuthService(
            proxyProperties, 
            authServiceCaller,
            backend.authRequest(),
            backend.authzRequest(),
            backend.securityConfig()
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

    private AuthService createCloudRunAuthService(ProxyProperties.BackendDefinition backend) {
        log.debug("Creating Cloud Run auth service for backend: {}", backend.name());
        return new CloudRunAuthService(
            cloudRunIdTokenProvider,
            backend.baseUrl(),
            backend.securityConfig()
        );
    }

    private AuthService createTransactionIdAuthService(ProxyProperties.BackendDefinition backend) {
        log.debug("Creating transaction-id auth service for backend: {}", backend.name());
        return new TransactionIdAuthService(
            proxyProperties,
            authServiceCaller,
            backend.securityConfig()
        );
    }

    private AuthService createFormAuthService(ProxyProperties.BackendDefinition backend) {
        log.debug("Creating form auth service for backend: {}", backend.name());
        return new FormAuthService(
            proxyProperties,
            authServiceCaller,
            backend.securityConfig()
        );
    }
    
    /**
     * No-op auth service (does nothing)
     */
    private static class NoOpAuthService implements AuthService {
        @Override
        public void enrichHeaders(ProxyRequestContext request, Map<String, String> headers, String requestBody) {
            // Do nothing
        }
    }
}
