package com.agent.gateway.proxy.history;

public interface HistoryPublisher {

    boolean supports(String provider);

    void publish(HistoryPublishRequest request);
}
