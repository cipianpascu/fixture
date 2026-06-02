package com.agent.gateway.proxy.app.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;

import java.util.Map;

@ConfigMapping(prefix = "gateway.resources")
public interface GatewayResourceProperties {
    @WithParentName
    Map<String, Map<String, String>> resources();
}
