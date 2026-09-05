package com.db.olorin.rest.history;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.model.ProxyRequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoryUuidCorrelationTest {
    @Test
    void generatesOneUuidPerRequestAndReusesItForHeaderAndHistoryProperties() {
        ProxyProperties.BackendDefinition backend = mock(ProxyProperties.BackendDefinition.class);
        ProxyProperties.BackendHistoryConfig history = mock(ProxyProperties.BackendHistoryConfig.class);
        when(backend.history()).thenReturn(Optional.of(history));
        when(history.generatedHeaders()).thenReturn(Map.of("X-Request-Id", "generator:uuid"));
        when(history.additionalProperties()).thenReturn(Map.of("correlationId", "generator:uuid"));

        HistoryService service = new HistoryService(
            mock(ProxyProperties.class),
            mock(ObjectProvider.class),
            mock(ObjectProvider.class));
        ProxyRequestContext request = new ProxyRequestContext("POST", "/orders", null, Map.of(), Map.of());
        Map<String, List<String>> outbound = new LinkedHashMap<>();

        service.enrichOutboundHeaders(backend, request, outbound);
        Map<String, String> properties = service.resolveAdditionalProperties(history.additionalProperties(), request, outbound);

        assertThat(outbound.get("x-request-id")).singleElement().isEqualTo(properties.get("correlationId"));
        service.enrichOutboundHeaders(backend, request, outbound);
        assertThat(outbound.get("x-request-id")).singleElement().matches(value -> value.matches("[0-9a-f-]{36}"));
    }

    @Test
    void publishesCorrelatedSubmittedFulfilledAndFailedLifecycleEvents() {
        ProxyProperties.BackendDefinition backend = mock(ProxyProperties.BackendDefinition.class);
        ProxyProperties.BackendHistoryConfig history = mock(ProxyProperties.BackendHistoryConfig.class);
        when(backend.name()).thenReturn("orders");
        when(backend.history()).thenReturn(Optional.of(history));
        when(history.enabled()).thenReturn(Optional.empty());
        when(history.additionalProperties()).thenReturn(Map.of("requestId", "generator:uuid"));
        when(history.generatedHeaders()).thenReturn(Map.of("X-Request-Id", "generator:uuid"));
        when(history.tokenHeader()).thenReturn(Optional.empty());
        when(history.provider()).thenReturn(Optional.empty());
        when(history.deliveryMode()).thenReturn(Optional.empty());
        when(history.failOpen()).thenReturn(Optional.empty());
        when(history.serviceUrl()).thenReturn(Optional.empty());
        when(history.projectId()).thenReturn(Optional.empty());
        when(history.topic()).thenReturn(Optional.empty());

        List<HistoryPublishRequest> published = new ArrayList<>();
        HistoryService service = historyService(
            mapper(context -> Map.of("lifecycle", context.status().name())),
            publisher(published::add)
        );
        ProxyRequestContext request = new ProxyRequestContext("PATCH", "/orders/42", null, Map.of(), Map.of());
        Map<String, List<String>> outboundHeaders = new LinkedHashMap<>();
        service.enrichOutboundHeaders(backend, request, outboundHeaders);

        service.emit(backend, request, "{\"id\":42}", outboundHeaders, "{\"id\":42}",
            HistoryStatus.SUBMITTED, null, null);
        service.emit(backend, request, "{\"id\":42}", outboundHeaders, "{\"id\":42}",
            HistoryStatus.FULFILLED, 200, "{\"ok\":true}");
        service.emit(backend, request, "{\"id\":42}", outboundHeaders, "{\"id\":42}",
            HistoryStatus.FAILED, 503, "backend unavailable");

        assertThat(published).hasSize(3);
        assertThat(published).extracting(HistoryPublishRequest::status)
            .containsExactly(HistoryStatus.SUBMITTED, HistoryStatus.FULFILLED, HistoryStatus.FAILED);
        assertThat(published).extracting(requestToPublish -> requestToPublish.attributes().get("requestId"))
            .containsOnly(outboundHeaders.get("x-request-id").getFirst());
        assertThat(published.get(1).backendStatusCode()).contains(200);
        assertThat(published.get(2).backendStatusCode()).contains(503);
    }

    @Test
    void preservesCallerProvidedGeneratedHeader() {
        ProxyProperties.BackendDefinition backend = mock(ProxyProperties.BackendDefinition.class);
        ProxyProperties.BackendHistoryConfig history = mock(ProxyProperties.BackendHistoryConfig.class);
        when(backend.history()).thenReturn(Optional.of(history));
        when(history.generatedHeaders()).thenReturn(Map.of("X-Request-Id", "generator:uuid"));
        HistoryService service = new HistoryService(mock(ProxyProperties.class), mock(ObjectProvider.class), mock(ObjectProvider.class));
        Map<String, List<String>> outbound = new LinkedHashMap<>(Map.of("x-request-id", List.of("caller-id")));

        service.enrichOutboundHeaders(backend, new ProxyRequestContext("POST", "/orders", null, Map.of(), Map.of()), outbound);

        assertThat(outbound.get("x-request-id")).containsExactly("caller-id");
    }

    private HistoryService historyService(HistoryPayloadMapper mapper, HistoryPublisher publisher) {
        ProxyProperties properties = mock(ProxyProperties.class);
        ProxyProperties.HistoryConfig historyConfig = mock(ProxyProperties.HistoryConfig.class);
        when(properties.history()).thenReturn(historyConfig);
        when(historyConfig.enabled()).thenReturn(true);
        when(historyConfig.provider()).thenReturn("test");
        when(historyConfig.deliveryMode()).thenReturn("confirmed");
        when(historyConfig.failOpen()).thenReturn(false);
        when(historyConfig.serviceUrl()).thenReturn("http://history.test");
        when(historyConfig.projectId()).thenReturn(Optional.of("project"));
        when(historyConfig.topic()).thenReturn(Optional.of("topic"));
        when(historyConfig.timeout()).thenReturn(java.time.Duration.ofSeconds(1));
        when(historyConfig.tlsProfile()).thenReturn(Optional.empty());
        ObjectProvider<HistoryPayloadMapper> mappers = providerOf(mapper);
        ObjectProvider<HistoryPublisher> publishers = providerOf(publisher);
        return new HistoryService(properties, mappers, publishers);
    }

    @SuppressWarnings("unchecked")
    private <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.iterator()).thenAnswer(invocation -> List.of(value).iterator());
        return provider;
    }

    private HistoryPayloadMapper mapper(java.util.function.Function<HistoryRequestContext, Object> mapper) {
        return mapper::apply;
    }

    private HistoryPublisher publisher(java.util.function.Consumer<HistoryPublishRequest> publisher) {
        return new HistoryPublisher() {
            @Override
            public boolean supports(String provider) {
                return "test".equals(provider);
            }

            @Override
            public void publish(HistoryPublishRequest request) {
                publisher.accept(request);
            }
        };
    }
}
