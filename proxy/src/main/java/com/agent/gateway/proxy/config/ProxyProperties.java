package com.agent.gateway.proxy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configuration properties for the proxy module
 * Loaded from application.yml - NO database
 */
@Component
@ConfigurationProperties(prefix = "gateway")
@Data
public class ProxyProperties {
    
    private SchemaConfig schemas = new SchemaConfig();
    private AuthConfig auth = new AuthConfig();
    private List<BackendDefinition> backends = new ArrayList<>();
    
    @Data
    public static class AuthConfig {
        private boolean enabled = true;
        private String serviceUrl;  // Auth service URL
        private String sessionIdHeader = "X-Session-Id";  // Header name for session ID
        private String sessionIdCookie = "sessionId";  // Cookie name for session ID
        private Duration timeout = Duration.ofSeconds(5);
    }
    
    @Data
    public static class SchemaConfig {
        private String directory = "classpath:schemas/";
        private boolean validateRequests = true;
        private boolean validateBodies = true;  // Validate request body against schema
        private boolean validateResponses = false;
        private boolean strictMode = true;  // Reject if no schema found
    }
    
    @Data
    public static class BackendDefinition {
        private String name;
        private String baseUrl;
        private String path;
        private String schema;  // Schema filename
        private Duration timeout = Duration.ofSeconds(30);
        private boolean enabled = true;
        private String securityType;
        private Map<String, String> securityConfig;
        private List<String> authScopes = new ArrayList<>();  // Scopes for auth service token request
    }
}
