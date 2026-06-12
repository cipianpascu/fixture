package com.agent.gateway.proxy.history;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GcpPubSubHistoryPublisherTest {

    @Test
    void cleansNullAttributesBeforeWireSerialization() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("traceId", "trace-123");
        attributes.put("missingValue", null);
        attributes.put(null, "missing-key");
        attributes.put("emptyValue", "");

        Map<String, String> cleaned = GcpPubSubHistoryPublisher.cleanAttributes(attributes);

        assertEquals(Map.of("traceId", "trace-123", "emptyValue", ""), cleaned);
    }
}
