package com.db.olorin.rest.history;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GcpPubSubHistoryPublisherTest {
    @Test
    void removesNullPubSubAttributeKeysAndValuesBeforeSerialization() {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("traceId", "trace-123");
        attributes.put("empty", "");
        attributes.put("missingValue", null);
        attributes.put(null, "missingKey");

        assertThat(GcpPubSubHistoryPublisher.cleanAttributes(attributes))
            .containsExactlyInAnyOrderEntriesOf(Map.of("traceId", "trace-123", "empty", ""));
    }
}
