package com.agent.gateway.config;

import com.agent.gateway.model.GatewayMode;
import com.agent.gateway.model.SecurityType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "gateway")
@Data
public class GatewayProperties {

    private GatewayMode mode = GatewayMode.ROUTING;
    private List<BackendDefinition> backends = new ArrayList<>();

    @Data
    public static class BackendDefinition {
        private String name;
        private String baseUrl;
        private String path;
        private SecurityType securityType;
        private Map<String, String> securityConfig = new HashMap<>();
        private Boolean enabled = true;
    }
}
