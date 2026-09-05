package com.db.olorin.rest.history;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.exception.UpstreamProxyException;
import com.db.olorin.rest.model.ProxyRequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoryServiceContractTest {

    @Test
    void confirmedFailClosedPropagatesPublisherFailure() {
        HistoryService service = historyService("confirmed", false, mapper(), failingPublisher());

        assertThatThrownBy(() -> emit(service, backend(Optional.empty())))
            .isInstanceOf(UpstreamProxyException.class)
            .hasMessage("publish failed");
    }

    @Test
    void confirmedFailOpenContinuesAfterPublisherFailure() {
        HistoryService service = historyService("confirmed", true, mapper(), failingPublisher());

        emit(service, backend(Optional.empty()));
    }

    @Test
    void appliesBackendProviderAndDeliveryOverridesToPublisherRequest() {
        AtomicReference<HistoryPublishRequest> captured = new AtomicReference<>();
        HistoryPublisher publisher = new HistoryPublisher() {
            @Override
            public boolean supports(String provider) {
                return "backend-provider".equals(provider);
            }

            @Override
            public void publish(HistoryPublishRequest request) {
                captured.set(request);
            }
        };
        HistoryService service = historyService("confirmed", false, mapper(), publisher);
        ProxyProperties.BackendHistoryConfig history = mock(ProxyProperties.BackendHistoryConfig.class);
        when(history.enabled()).thenReturn(Optional.empty());
        when(history.additionalProperties()).thenReturn(Map.of());
        when(history.generatedHeaders()).thenReturn(Map.of());
        when(history.tokenHeader()).thenReturn(Optional.empty());
        when(history.provider()).thenReturn(Optional.of("backend-provider"));
        when(history.deliveryMode()).thenReturn(Optional.of("confirmed"));
        when(history.failOpen()).thenReturn(Optional.of(false));
        when(history.serviceUrl()).thenReturn(Optional.of("http://backend-history.test"));
        when(history.projectId()).thenReturn(Optional.of("backend-project"));
        when(history.topic()).thenReturn(Optional.of("backend-topic"));

        emit(service, backend(Optional.of(history)));

        assertThat(captured.get()).isNotNull();
        assertThat(captured.get().provider()).isEqualTo("backend-provider");
        assertThat(captured.get().serviceUrl()).isEqualTo("http://backend-history.test");
        assertThat(captured.get().projectId()).contains("backend-project");
        assertThat(captured.get().topic()).contains("backend-topic");
    }

    @Test
    void doesNotMapOrPublishWhenHistoryIsDisabledForBackend() {
        AtomicInteger mapped = new AtomicInteger();
        AtomicInteger published = new AtomicInteger();
        HistoryPayloadMapper mapper = context -> {
            mapped.incrementAndGet();
            return Map.of("event", "history");
        };
        HistoryService service = historyService("confirmed", false, mapper, publisher(request -> published.incrementAndGet()));

        emit(service, backend(Optional.of(disabledHistory())));

        assertThat(mapped).hasValue(0);
        assertThat(published).hasValue(0);
    }

    @Test
    void asyncFailClosedPropagatesQueueRejectionAndFailOpenSwallowsIt() {
        HistoryService failClosed = historyService("async", false, mapper(), publisher(request -> { }));
        failClosed.executor = new RejectingExecutorService();
        assertThatThrownBy(() -> emit(failClosed, backend(Optional.empty())))
            .isInstanceOf(RejectedExecutionException.class);

        HistoryService failOpen = historyService("async", true, mapper(), publisher(request -> { }));
        failOpen.executor = new RejectingExecutorService();
        emit(failOpen, backend(Optional.empty()));
    }

    private void emit(HistoryService service, ProxyProperties.BackendDefinition backend) {
        service.emit(
            backend,
            new ProxyRequestContext("POST", "/orders", null, Map.of(), Map.of()),
            "{\"id\":1}", Map.of(), "{\"id\":1}", HistoryStatus.SUBMITTED, null, null);
    }

    private HistoryService historyService(
        String deliveryMode, boolean failOpen, HistoryPayloadMapper mapper, HistoryPublisher publisher) {
        ProxyProperties properties = mock(ProxyProperties.class);
        ProxyProperties.HistoryConfig historyConfig = mock(ProxyProperties.HistoryConfig.class);
        when(properties.history()).thenReturn(historyConfig);
        when(historyConfig.enabled()).thenReturn(true);
        when(historyConfig.provider()).thenReturn("test");
        when(historyConfig.deliveryMode()).thenReturn(deliveryMode);
        when(historyConfig.failOpen()).thenReturn(failOpen);
        when(historyConfig.serviceUrl()).thenReturn("http://history.test");
        when(historyConfig.projectId()).thenReturn(Optional.of("project"));
        when(historyConfig.topic()).thenReturn(Optional.of("topic"));
        when(historyConfig.timeout()).thenReturn(Duration.ofSeconds(1));
        when(historyConfig.tlsProfile()).thenReturn(Optional.empty());
        return new HistoryService(properties, providerOf(mapper), providerOf(publisher));
    }

    private ProxyProperties.BackendDefinition backend(Optional<ProxyProperties.BackendHistoryConfig> history) {
        ProxyProperties.BackendDefinition backend = mock(ProxyProperties.BackendDefinition.class);
        when(backend.name()).thenReturn("orders");
        when(backend.history()).thenReturn(history);
        return backend;
    }

    private ProxyProperties.BackendHistoryConfig disabledHistory() {
        ProxyProperties.BackendHistoryConfig history = mock(ProxyProperties.BackendHistoryConfig.class);
        when(history.enabled()).thenReturn(Optional.of(false));
        return history;
    }

    private HistoryPayloadMapper mapper() {
        return context -> Map.of("status", context.status().name());
    }

    private HistoryPublisher failingPublisher() {
        return publisher(request -> { throw new UpstreamProxyException("publish failed"); });
    }

    private HistoryPublisher publisher(java.util.function.Consumer<HistoryPublishRequest> consumer) {
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
    private <T> ObjectProvider<T> providerOf(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.iterator()).thenAnswer(invocation -> List.of(value).iterator());
        return provider;
    }

    private static final class RejectingExecutorService extends AbstractExecutorService {
        @Override public void shutdown() { }
        @Override public List<Runnable> shutdownNow() { return List.of(); }
        @Override public boolean isShutdown() { return false; }
        @Override public boolean isTerminated() { return false; }
        @Override public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        @Override public void execute(Runnable command) { throw new RejectedExecutionException("queue full"); }
    }
}
