package com.db.olorin.rest.history;

public interface HistoryPublisher {

    boolean supports(String provider);

    void publish(HistoryPublishRequest request);
}
