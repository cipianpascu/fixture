package com.agent.gateway.proxy.history;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.model.ProxyRequestContext;

import java.util.Map;

public interface HistoryPayloadMapper {

    default boolean supports(
        ProxyProperties.BackendDefinition backend,
        ProxyRequestContext incomingRequest,
        HistoryStatus status) {
        return true;
    }

    Object map(HistoryRequestContext context);

    default Map<String, String> attributes(HistoryRequestContext context) {
        return context.additionalProperties();
    }
}
