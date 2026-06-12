package com.agent.gateway.proxy.history;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.UpstreamProxyException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HistoryServiceTest {

    @Test
    void resolvesAdditionalPropertiesFromLiteralHeaderCookieAndFallbackTokenClaims() {
        HistoryService service = new HistoryService();
        service.clock = Clock.fixed(Instant.parse("2026-06-12T08:15:30.123Z"), ZoneOffset.UTC);
        ProxyRequestContext request = new ProxyRequestContext(
            "POST",
            "/api/v1/orders",
            null,
            Map.of("authorization", List.of("Bearer " + jwt(Map.of(
                "c_partner_id", "customer-123",
                "tenant_id", "tenant-77"
            )))),
            Map.of("SESSION", "cookie-session")
        );

        Map<String, String> values = service.resolveAdditionalProperties(
            Map.of(
                "source", "literal:bfa",
                "traceId", "header:X-Trace-Id",
                "session", "cookie:SESSION",
                "customerId", "token:partner_id|c_partner_id",
                "tenantId", "token:tenant_id",
                "eventDate", "date:yyyy-MM-dd",
                "eventTimestamp", "date:timestamp",
                "eventInstant", "date:iso-instant"
            ),
            request,
            Map.of("x-trace-id", List.of("trace-123"))
        );

        assertEquals("bfa", values.get("source"));
        assertEquals("trace-123", values.get("traceId"));
        assertEquals("cookie-session", values.get("session"));
        assertEquals("customer-123", values.get("customerId"));
        assertEquals("tenant-77", values.get("tenantId"));
        assertEquals("2026-06-12", values.get("eventDate"));
        assertEquals("1781252130123", values.get("eventTimestamp"));
        assertEquals("2026-06-12T08:15:30.123Z", values.get("eventInstant"));
    }

    @Test
    void confirmedFailClosedPropagatesPublishFailureBeforeBackendCall() {
        HistoryService service = historyService(
            historyConfig(true, "confirmed", false),
            context -> Map.of("backend", context.backend().name()),
            publisher(request -> {
                throw new UpstreamProxyException("publish failed");
            })
        );

        assertThrows(
            UpstreamProxyException.class,
            () -> service.emit(
                backend(Optional.empty()),
                request("POST"),
                "{}",
                Map.of(),
                "{}"
            )
        );
    }

    @Test
    void confirmedFailOpenDoesNotPropagatePublishFailure() {
        HistoryService service = historyService(
            historyConfig(true, "confirmed", true),
            context -> Map.of("backend", context.backend().name()),
            publisher(request -> {
                throw new UpstreamProxyException("publish failed");
            })
        );

        service.emit(backend(Optional.empty()), request("POST"), "{}", Map.of(), "{}");
    }

    @Test
    void skipsDisabledBackendAndNonMutatingMethods() {
        AtomicInteger mapped = new AtomicInteger();
        HistoryService service = historyService(
            historyConfig(true, "confirmed", false),
            context -> {
                mapped.incrementAndGet();
                return Map.of();
            },
            publisher(request -> {
            })
        );

        service.emit(backend(Optional.of(false)), request("POST"), "{}", Map.of(), "{}");
        service.emit(backend(Optional.empty()), request("GET"), "{}", Map.of(), "{}");

        assertEquals(0, mapped.get());
    }

    @Test
    void passesPayloadAndAttributesToPublisher() {
        AtomicReference<HistoryPublishRequest> published = new AtomicReference<>();
        HistoryService service = historyService(
            historyConfig(true, "confirmed", false),
            context -> Map.of("customerId", context.additionalProperties().get("customerId")),
            publisher(published::set)
        );

        service.emit(
            backend(Optional.empty(), Map.of("customerId", "literal:customer-123")),
            request("PATCH"),
            "{}",
            Map.of(),
            "{}"
        );

        assertEquals("customer-123", published.get().attributes().get("customerId"));
        assertEquals(Map.of("customerId", "customer-123"), published.get().payload());
    }

    private HistoryService historyService(
        ProxyProperties.HistoryConfig historyConfig,
        HistoryPayloadMapper mapper,
        HistoryPublisher publisher) {
        HistoryService service = new HistoryService();
        service.proxyProperties = proxyProperties(historyConfig);
        service.payloadMappers = instance(List.of(mapper));
        service.publishers = instance(List.of(publisher));
        return service;
    }

    private ProxyProperties proxyProperties(ProxyProperties.HistoryConfig historyConfig) {
        return new ProxyProperties() {
            @Override
            public SchemaConfig schemas() {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<TlsConfig> tls() {
                return Optional.empty();
            }

            @Override
            public HistoryConfig history() {
                return historyConfig;
            }

            @Override
            public List<BackendDefinition> backends() {
                return List.of();
            }
        };
    }

    private ProxyProperties.HistoryConfig historyConfig(boolean enabled, String deliveryMode, boolean failOpen) {
        return new ProxyProperties.HistoryConfig() {
            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public String provider() {
                return "test";
            }

            @Override
            public String deliveryMode() {
                return deliveryMode;
            }

            @Override
            public boolean failOpen() {
                return failOpen;
            }

            @Override
            public List<String> methods() {
                return List.of("POST", "PUT", "PATCH", "DELETE");
            }

            @Override
            public String serviceUrl() {
                return "http://history.example.com";
            }

            @Override
            public Optional<String> projectId() {
                return Optional.of("project");
            }

            @Override
            public Optional<String> topic() {
                return Optional.of("topic");
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

    private ProxyProperties.BackendDefinition backend(Optional<Boolean> historyEnabled) {
        return backend(historyEnabled, Map.of());
    }

    private ProxyProperties.BackendDefinition backend(
        Optional<Boolean> historyEnabled,
        Map<String, String> additionalProperties) {
        return new ProxyProperties.BackendDefinition() {
            @Override
            public String name() {
                return "orders";
            }

            @Override
            public String baseUrl() {
                return "http://backend.example.com";
            }

            @Override
            public String path() {
                return "/orders";
            }

            @Override
            public Optional<String> schema() {
                return Optional.empty();
            }

            @Override
            public Duration timeout() {
                return Duration.ofSeconds(5);
            }

            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String protocol() {
                return "rest";
            }

            @Override
            public String httpVersion() {
                return "http1_1";
            }

            @Override
            public Optional<String> securityType() {
                return Optional.of("none");
            }

            @Override
            public Map<String, String> securityConfig() {
                return Map.of();
            }

            @Override
            public Optional<ProxyProperties.AuthRequestConfig> authRequest() {
                return Optional.empty();
            }

            @Override
            public Optional<ProxyProperties.AuthzRequestConfig> authzRequest() {
                return Optional.empty();
            }

            @Override
            public Optional<ProxyProperties.BackendHistoryConfig> history() {
                if (historyEnabled.isEmpty() && additionalProperties.isEmpty()) {
                    return Optional.empty();
                }
                return Optional.of(new ProxyProperties.BackendHistoryConfig() {
                    @Override
                    public Optional<Boolean> enabled() {
                        return historyEnabled;
                    }

                    @Override
                    public Optional<String> provider() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<String> deliveryMode() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<Boolean> failOpen() {
                        return Optional.empty();
                    }

                    @Override
                    public Optional<String> serviceUrl() {
                        return Optional.empty();
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
                    public Map<String, String> additionalProperties() {
                        return additionalProperties;
                    }
                });
            }

            @Override
            public Optional<String> tlsProfile() {
                return Optional.empty();
            }

            @Override
            public Optional<ProxyProperties.SoapConfig> soap() {
                return Optional.empty();
            }

            @Override
            public Optional<ProxyProperties.ProxyConfig> proxy() {
                return Optional.empty();
            }
        };
    }

    private ProxyRequestContext request(String method) {
        return new ProxyRequestContext(method, "/api/v1/orders", null, Map.of(), Map.of());
    }

    private HistoryPublisher publisher(Consumer<HistoryPublishRequest> consumer) {
        return new HistoryPublisher() {
            @Override
            public boolean supports(String provider) {
                return "test".equals(provider);
            }

            @Override
            public void publish(HistoryPublishRequest request) {
                consumer.accept(request);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> Instance<T> instance(List<T> values) {
        return (Instance<T>) Proxy.newProxyInstance(
            Instance.class.getClassLoader(),
            new Class[]{Instance.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "iterator" -> values.iterator();
                case "isUnsatisfied" -> values.isEmpty();
                case "isAmbiguous" -> values.size() > 1;
                case "select" -> proxy;
                case "get" -> values.getFirst();
                case "destroy" -> null;
                case "handles" -> List.of();
                default -> defaultValue(method.getReturnType());
            }
        );
    }

    private Object defaultValue(Class<?> returnType) {
        if (returnType == boolean.class) {
            return false;
        }
        if (returnType == Iterator.class) {
            return List.of().iterator();
        }
        if (returnType == Annotation[].class) {
            return new Annotation[0];
        }
        return null;
    }

    private String jwt(Map<String, String> claims) {
        String header = base64("{\"alg\":\"none\"}");
        String payload = claims.entrySet().stream()
            .map(entry -> "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\"")
            .collect(java.util.stream.Collectors.joining(",", "{", "}"));
        return header + "." + base64(payload) + ".";
    }

    private String base64(String json) {
        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
