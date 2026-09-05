package com.db.olorin.rest.history;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.model.ProxyRequestContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record HistoryRequestContext(
    ProxyProperties.BackendDefinition backend,
    ProxyRequestContext incomingRequest,
    String incomingBody,
    Map<String, List<String>> outboundHeaders,
    String outboundBody,
    Map<String, String> additionalProperties,
    HistoryStatus status,
    Optional<Integer> backendStatusCode,
    String backendResponseBody
) {
    public HistoryRequestContext(
        ProxyProperties.BackendDefinition backend,
        ProxyRequestContext incomingRequest,
        String incomingBody,
        Map<String, List<String>> outboundHeaders,
        String outboundBody,
        Map<String, String> additionalProperties) {
        this(
            backend,
            incomingRequest,
            incomingBody,
            outboundHeaders,
            outboundBody,
            additionalProperties,
            HistoryStatus.SUBMITTED,
            Optional.empty(),
            null
        );
    }
}
