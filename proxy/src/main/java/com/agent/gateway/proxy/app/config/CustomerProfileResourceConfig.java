package com.agent.gateway.proxy.app.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class CustomerProfileResourceConfig {

    private static final String RESOURCE_NAME = "customer-profile";

    @Inject
    GatewayResourceProperties gatewayResourceProperties;

    public String schema() {
        return value("schema", "customer-profile.yaml");
    }

    public String backend() {
        return value("backend", "customer-profile-soap-service");
    }

    public Optional<String> soapAction() {
        return Optional.ofNullable(resourceValues().get("soap-action"));
    }

    private String value(String key, String defaultValue) {
        return resourceValues().getOrDefault(key, defaultValue);
    }

    private Map<String, String> resourceValues() {
        return gatewayResourceProperties.resources().getOrDefault(RESOURCE_NAME, Map.of());
    }
}
