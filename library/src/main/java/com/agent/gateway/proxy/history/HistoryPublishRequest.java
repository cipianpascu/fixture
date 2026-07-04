package com.agent.gateway.proxy.history;

import com.agent.gateway.proxy.config.ProxyProperties;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

public record HistoryPublishRequest(
    ProxyProperties.BackendDefinition backend,
    String provider,
    String serviceUrl,
    Optional<String> projectId,
    Optional<String> topic,
    Duration timeout,
    Optional<String> tlsProfile,
    Map<String, String> attributes,
    Object payload,
    HistoryStatus status,
    Optional<Integer> backendStatusCode
) {
    public HistoryPublishRequest(
        ProxyProperties.BackendDefinition backend,
        String provider,
        String serviceUrl,
        Optional<String> projectId,
        Optional<String> topic,
        Duration timeout,
        Optional<String> tlsProfile,
        Map<String, String> attributes,
        Object payload) {
        this(
            backend,
            provider,
            serviceUrl,
            projectId,
            topic,
            timeout,
            tlsProfile,
            attributes,
            payload,
            HistoryStatus.SUBMITTED,
            Optional.empty()
        );
    }
}
