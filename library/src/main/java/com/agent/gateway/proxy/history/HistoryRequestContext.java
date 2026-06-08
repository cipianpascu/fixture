package com.agent.gateway.proxy.history;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.model.ProxyRequestContext;

import java.util.List;
import java.util.Map;

public record HistoryRequestContext(
    ProxyProperties.BackendDefinition backend,
    ProxyRequestContext incomingRequest,
    String incomingBody,
    Map<String, List<String>> outboundHeaders,
    String outboundBody,
    Map<String, String> additionalProperties
) {
}
