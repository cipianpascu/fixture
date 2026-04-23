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
    private List<BackendDefinition> backends = new ArrayList<>();
    
    @Data
    public static class SchemaConfig {
        private String directory = "classpath:schemas/";
        private boolean validateRequests = true;
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
    }
}
