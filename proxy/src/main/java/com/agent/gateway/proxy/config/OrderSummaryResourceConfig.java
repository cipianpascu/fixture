package com.agent.gateway.proxy.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "gateway.resources.order-summary")
public interface OrderSummaryResourceConfig {

    @WithDefault("order-summary.yaml")
    String schema();

    @WithName("orders-backend")
    @WithDefault("orders-service")
    String ordersBackend();

    @WithName("orders-path-template")
    @WithDefault("/details/{id}")
    String ordersPathTemplate();

    @WithName("payments-backend")
    @WithDefault("payments-service")
    String paymentsBackend();

    @WithName("payments-path-template")
    @WithDefault("/orders/{id}")
    String paymentsPathTemplate();
}
