package com.agent.gateway.proxy.app.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

@ConfigMapping(prefix = "gateway.resources.customer-profile")
public interface CustomerProfileResourceConfig {

    @WithDefault("customer-profile.yaml")
    String schema();

    @WithName("backend")
    @WithDefault("customer-profile-soap-service")
    String backend();

    @WithName("soap-action")
    Optional<String> soapAction();
}
