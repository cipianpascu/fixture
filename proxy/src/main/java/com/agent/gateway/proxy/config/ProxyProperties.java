package com.agent.gateway.proxy.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration properties for the proxy module (Quarkus)
 * Loaded from application.yml - NO database
 */
@ConfigMapping(prefix = "gateway")
public interface ProxyProperties {
    
    @WithName("schemas")
    SchemaConfig schemas();
    
    @WithName("auth")
    AuthConfig auth();

    @WithName("tls")
    Optional<TlsConfig> tls();
    
    @WithName("backends")
    List<BackendDefinition> backends();
    
    interface AuthConfig {
        @WithDefault("true")
        boolean enabled();
        
        @WithName("service-url")
        @WithDefault("http://localhost:8081")
        String serviceUrl();
        
        @WithName("session-id-header")
        @WithDefault("X-Session-Id")
        String sessionIdHeader();
        
        @WithName("session-id-cookie")
        @WithDefault("sessionId")
        String sessionIdCookie();
        
        @WithDefault("5s")
        Duration timeout();

        @WithName("tls-profile")
        Optional<String> tlsProfile();
    }
    
    interface SchemaConfig {
        @WithDefault("classpath:schemas/")
        String directory();
        
        @WithName("validate-requests")
        @WithDefault("true")
        boolean validateRequests();
        
        @WithName("validate-bodies")
        @WithDefault("true")
        boolean validateBodies();
        
        @WithName("validate-responses")
        @WithDefault("false")
        boolean validateResponses();
        
        @WithName("strict-mode")
        @WithDefault("true")
        boolean strictMode();
    }
    
    interface BackendDefinition {
        String name();
        
        @WithName("baseUrl")
        String baseUrl();
        
        String path();
        
        Optional<String> schema();
        
        @WithDefault("30s")
        Duration timeout();
        
        @WithDefault("true")
        boolean enabled();
        
        @WithName("securityType")
        Optional<String> securityType();
        
        @WithName("securityConfig")
        Map<String, String> securityConfig();
        
        @WithName("authScopes")
        Optional<List<String>> authScopes();

        @WithName("tls-profile")
        Optional<String> tlsProfile();
    }

    interface TlsConfig {
        @WithName("profiles")
        Map<String, TlsProfile> profiles();
    }

    interface TlsProfile {
        Optional<StoreConfig> truststore();

        Optional<StoreConfig> keystore();
    }

    interface StoreConfig {
        String path();

        Optional<String> password();

        @WithDefault("PKCS12")
        String type();

        @WithName("key-password")
        Optional<String> keyPassword();
    }
}
