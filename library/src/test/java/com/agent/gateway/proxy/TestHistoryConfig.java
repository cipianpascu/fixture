package com.agent.gateway.proxy;

import com.agent.gateway.proxy.config.ProxyProperties;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public final class TestHistoryConfig {

    private TestHistoryConfig() {
    }

    public static ProxyProperties.HistoryConfig disabled() {
        return new ProxyProperties.HistoryConfig() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public String provider() {
                return "gcp-pubsub";
            }

            @Override
            public String deliveryMode() {
                return "async";
            }

            @Override
            public boolean failOpen() {
                return true;
            }

            @Override
            public List<String> methods() {
                return List.of("POST", "PUT", "PATCH", "DELETE");
            }

            @Override
            public String serviceUrl() {
                return "https://pubsub.googleapis.com";
            }

            @Override
            public Optional<String> projectId() {
                return Optional.empty();
            }

            @Override
            public Optional<String> topic() {
                return Optional.empty();
            }

            @Override
            public Duration timeout() {
                return Duration.ofSeconds(5);
            }

            @Override
            public Optional<String> tlsProfile() {
                return Optional.empty();
            }
        };
    }
}
