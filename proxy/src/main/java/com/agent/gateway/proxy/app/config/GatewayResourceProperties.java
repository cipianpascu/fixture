package com.agent.gateway.proxy.app.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.Map;

@ConfigMapping(prefix = "gateway")
public interface GatewayResourceProperties {

    @WithName("resources")
    Map<String, Map<String, String>> resources();
}
