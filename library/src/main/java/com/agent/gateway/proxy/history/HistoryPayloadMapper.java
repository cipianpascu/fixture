package com.agent.gateway.proxy.history;

import com.agent.gateway.proxy.config.ProxyProperties;

public interface HistoryPayloadMapper {

    default boolean supports(ProxyProperties.BackendDefinition backend) {
        return true;
    }

    Object map(HistoryRequestContext context);
}
