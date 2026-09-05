package com.db.olorin.rest.history;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.model.ProxyRequestContext;

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
