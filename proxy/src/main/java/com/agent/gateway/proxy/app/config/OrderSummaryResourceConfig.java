package com.agent.gateway.proxy.app.config;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;

@ApplicationScoped
public class OrderSummaryResourceConfig {

    private static final String RESOURCE_NAME = "order-summary";

    @Inject
    GatewayResourceProperties gatewayResourceProperties;

    public String schema() {
        return value("schema", "order-summary.yaml");
    }

    public String ordersBackend() {
        return value("orders-backend", "orders-service");
    }

    public String ordersPathTemplate() {
        return value("orders-path-template", "/details/{id}");
    }

    public String paymentsBackend() {
        return value("payments-backend", "payments-service");
    }

    public String paymentsPathTemplate() {
        return value("payments-path-template", "/orders/{id}");
    }

    private String value(String key, String defaultValue) {
        return resourceValues().getOrDefault(key, defaultValue);
    }

    private Map<String, String> resourceValues() {
        return gatewayResourceProperties.resources().getOrDefault(RESOURCE_NAME, Map.of());
    }
}
