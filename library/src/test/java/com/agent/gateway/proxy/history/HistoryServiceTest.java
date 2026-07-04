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
import java.util.Collections;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HistoryServiceTest {

    @Test
    void resolvesAdditionalPropertiesFromLiteralHeaderCookieAndFallbackTokenClaims() {
        HistoryService service = new HistoryService();
        service.clock = Clock.fixed(Instant.parse("2026-06-12T08:15:30.123Z"), ZoneOffset.UTC);
        service.manifestAttributes = Map.of("Implementation-Version", "1.2.3");
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
                "eventInstant", "date:iso-instant",
                "appVersion", "manifest:Implementation-Version"
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
        assertEquals("1.2.3", values.get("appVersion"));
    }

    @Test
    void resolvesMixedAdditionalPropertySourcesInOrder() {
        HistoryService service = new HistoryService();
        ProxyRequestContext request = new ProxyRequestContext(
            "POST",
            "/api/v1/orders",
            null,
            Map.of("authorization", List.of("Bearer " + jwt(Map.of("c_partner_id", "token-customer")))),
            Map.of()
        );

        Map<String, String> values = service.resolveAdditionalProperties(
            Map.of("customerId", "header:X-Customer-Id||token:partner_id|c_partner_id"),
            request,
            Map.of("x-customer-id", List.of("header-customer"))
        );

        assertEquals("header-customer", values.get("customerId"));
    }

    @Test
    void resolvesMixedAdditionalPropertyFallbackWhenFirstSourceIsMissing() {
        HistoryService service = new HistoryService();
        ProxyRequestContext request = new ProxyRequestContext(
            "POST",
            "/api/v1/orders",
            null,
            Map.of("authorization", List.of("Bearer " + jwt(Map.of("c_partner_id", "token-customer")))),
            Map.of()
        );

        Map<String, String> values = service.resolveAdditionalProperties(
            Map.of("customerId", "header:X-Customer-Id||token:partner_id|c_partner_id"),
            request,
            Map.of()
        );

        assertEquals("token-customer", values.get("customerId"));
    }

    @Test
    void resolvesTokenClaimsFromConfiguredTokenHeader() {
        HistoryService service = new HistoryService();
        ProxyRequestContext request = new ProxyRequestContext(
            "POST",
            "/api/v1/orders",
            null,
            Map.of(
                "authorization", List.of("Bearer " + jwt(Map.of("customer_id", "wrong-customer"))),
                "x-history-token", List.of("Bearer " + jwt(Map.of("customer_id", "history-customer")))
            ),
            Map.of()
        );

        Map<String, String> values = service.resolveAdditionalProperties(
            Map.of("customerId", "token:customer_id"),
            request,
            Map.of(),
            "X-History-Token"
        );

        assertEquals("history-customer", values.get("customerId"));
    }

    @Test
    void tokenClaimsPreferOutboundHeaderOverIncomingCloudRunToken() {
        HistoryService service = new HistoryService();
        ProxyRequestContext request = new ProxyRequestContext(
            "POST",
            "/api/v1/orders",
            null,
            Map.of("authorization", List.of("Bearer " + jwt(Map.of("customer_id", "cloudrun-caller")))),
            Map.of()
        );

        Map<String, String> values = service.resolveAdditionalProperties(
            Map.of("customerId", "token:customer_id"),
            request,
            Map.of("authorization", List.of("Bearer " + jwt(Map.of("customer_id", "backend-token"))))
        );

        assertEquals("backend-token", values.get("customerId"));
    }

    @Test
    void confirmedFailClosedPropagatesPublishFailureBeforeBackendCall() {
        HistoryService service = historyService(
            historyConfig(true, "confirmed", false),
            mapper(context -> Map.of("backend", context.backend().name())),
            publisher(request -> {
                throw new UpstreamProxyException("publish failed");
            })
        );

        assertThrows(
            UpstreamProxyException.class,
            () -> emitSubmitted(
                service,
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
            mapper(context -> Map.of("backend", context.backend().name())),
            publisher(request -> {
                throw new UpstreamProxyException("publish failed");
            })
        );

        emitSubmitted(service, backend(Optional.empty()), request("POST"), "{}", Map.of(), "{}");
    }

    @Test
    void skipsDisabledBackendAndUnsupportedRequests() {
        AtomicInteger mapped = new AtomicInteger();
        HistoryService service = historyService(
            historyConfig(true, "confirmed", false),
            mapper(
                (backend, request) -> !"GET".equals(request.method()),
                context -> {
                mapped.incrementAndGet();
                return Map.of();
                }
            ),
            publisher(request -> {
            })
        );

        emitSubmitted(service, backend(Optional.of(false)), request("POST"), "{}", Map.of(), "{}");
        emitSubmitted(service, backend(Optional.empty()), request("GET"), "{}", Map.of(), "{}");

        assertEquals(0, mapped.get());
    }

    @Test
    void passesPayloadAndAttributesToPublisher() {
        AtomicReference<HistoryPublishRequest> published = new AtomicReference<>();
        HistoryService service = historyService(
            historyConfig(true, "confirmed", false),
            mapper(context -> Map.of("customerId", context.additionalProperties().get("customerId"))),
            publisher(published::set)
        );

        emitSubmitted(
            service,
            backend(Optional.empty(), Map.of("customerId", "literal:customer-123")),
            request("PATCH"),
            "{}",
            Map.of(),
            "{}"
        );

        assertEquals("customer-123", published.get().attributes().get("customerId"));
        assertEquals(Map.of("customerId", "customer-123"), published.get().payload());
    }

    @Test
    void mapperCanReplaceConfiguredAttributes() {
        AtomicReference<HistoryPublishRequest> published = new AtomicReference<>();
        HistoryService service = historyService(
            historyConfig(true, "confirmed", false),
            new HistoryPayloadMapper() {
                @Override
                public Object map(HistoryRequestContext context) {
                    return Map.of("backend", context.backend().name());
                }

                @Override
                public Map<String, String> attributes(HistoryRequestContext context) {
                    return Map.of("eventType", "ORDER_CHANGED");
                }
            },
            publisher(published::set)
        );

        emitSubmitted(
            service,
            backend(Optional.empty(), Map.of("customerId", "literal:customer-123")),
            request("PATCH"),
            "{}",
            Map.of(),
            "{}"
        );

        assertEquals(Map.of("eventType", "ORDER_CHANGED"), published.get().attributes());
        assertEquals(Map.of("backend", "orders"), published.get().payload());
    }

    @Test
    void publishesLifecycleStatusToMapperAndPublisher() {
        List<HistoryPublishRequest> published = new ArrayList<>();
        HistoryService service = historyService(
            historyConfig(true, "confirmed", false),
            new HistoryPayloadMapper() {
                @Override
                public Object map(HistoryRequestContext context) {
                    return Map.of(
                        "status", context.status().name(),
                        "backendStatusCode", context.backendStatusCode().map(String::valueOf).orElse("none"),
                        "backendResponseBody", context.backendResponseBody() == null
                            ? "none"
                            : context.backendResponseBody()
                    );
                }
            },
            publisher(published::add)
        );

        service.emit(
            backend(Optional.empty()),
            request("PATCH"),
            "{}",
            Map.of(),
            "{}",
            HistoryStatus.SUBMITTED,
            null,
            null
        );
        service.emit(
            backend(Optional.empty()),
            request("PATCH"),
            "{}",
            Map.of(),
            "{}",
            HistoryStatus.FULFILLED,
            200,
            "{\"ok\":true}"
        );

        assertEquals(HistoryStatus.SUBMITTED, published.getFirst().status());
        assertEquals(Optional.empty(), published.getFirst().backendStatusCode());
        assertEquals(Map.of(
            "status", "SUBMITTED",
            "backendStatusCode", "none",
            "backendResponseBody", "none"
        ), published.getFirst().payload());
        assertEquals(HistoryStatus.FULFILLED, published.get(1).status());
        assertEquals(Optional.of(200), published.get(1).backendStatusCode());
        assertEquals(Map.of(
            "status", "FULFILLED",
            "backendStatusCode", "200",
            "backendResponseBody", "{\"ok\":true}"
        ), published.get(1).payload());
    }

    @Test
    void asyncFailClosedPropagatesBoundedExecutorRejection() {
        HistoryService service = historyService(
            historyConfig(true, "async", false, executorConfig(0, 1, 0)),
            mapper(context -> Map.of("backend", context.backend().name())),
            publisher(request -> {
            })
        );
        service.executor = new RejectingExecutorService();

        assertThrows(
            RejectedExecutionException.class,
            () -> emitSubmitted(service, backend(Optional.empty()), request("POST"), "{}", Map.of(), "{}")
        );
    }

    @Test
    void asyncFailOpenSwallowsBoundedExecutorRejection() {
        HistoryService service = historyService(
            historyConfig(true, "async", true, executorConfig(0, 1, 0)),
            mapper(context -> Map.of("backend", context.backend().name())),
            publisher(request -> {
            })
        );
        service.executor = new RejectingExecutorService();

        emitSubmitted(service, backend(Optional.empty()), request("POST"), "{}", Map.of(), "{}");
    }

    private void emitSubmitted(
        HistoryService service,
        ProxyProperties.BackendDefinition backend,
        ProxyRequestContext request,
        String incomingBody,
        Map<String, List<String>> outboundHeaders,
        String outboundBody) {
        service.emit(
            backend,
            request,
            incomingBody,
            outboundHeaders,
            outboundBody,
            HistoryStatus.SUBMITTED,
            null,
            null
        );
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

    private HistoryPayloadMapper mapper(java.util.function.Function<HistoryRequestContext, Object> mapper) {
        return mapper((backend, request) -> true, mapper);
    }

    private HistoryPayloadMapper mapper(
        java.util.function.BiPredicate<ProxyProperties.BackendDefinition, ProxyRequestContext> supports,
        java.util.function.Function<HistoryRequestContext, Object> mapper) {
        return new HistoryPayloadMapper() {
            @Override
            public boolean supports(
                ProxyProperties.BackendDefinition backend,
                ProxyRequestContext incomingRequest) {
                return supports.test(backend, incomingRequest);
            }

            @Override
            public Object map(HistoryRequestContext context) {
                return mapper.apply(context);
            }
        };
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
        return historyConfig(enabled, deliveryMode, failOpen, Optional.empty());
    }

    private ProxyProperties.HistoryConfig historyConfig(
        boolean enabled,
        String deliveryMode,
        boolean failOpen,
        Optional<ProxyProperties.HistoryExecutorConfig> executorConfig) {
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

            @Override
            public Optional<ProxyProperties.HistoryExecutorConfig> executor() {
                return executorConfig;
            }
        };
    }

    private Optional<ProxyProperties.HistoryExecutorConfig> executorConfig(
        int coreThreads,
        int maxThreads,
        int queueCapacity) {
        return Optional.of(new ProxyProperties.HistoryExecutorConfig() {
            @Override
            public int coreThreads() {
                return coreThreads;
            }

            @Override
            public int maxThreads() {
                return maxThreads;
            }

            @Override
            public int queueCapacity() {
                return queueCapacity;
            }
        });
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
                    public Optional<String> tokenHeader() {
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

    private static final class RejectingExecutorService extends AbstractExecutorService {
        private boolean shutdown;

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            return shutdown;
        }

        @Override
        public void execute(Runnable command) {
            throw new RejectedExecutionException("queue full");
        }
    }
}
