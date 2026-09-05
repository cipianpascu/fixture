package com.db.olorin.rest.client;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.service.TlsContextFactory;
import com.db.olorin.rest.service.auth.AuthService;
import com.db.olorin.rest.service.auth.AuthServiceFactory;
import com.db.olorin.rest.history.HistoryService;
import com.db.olorin.rest.history.HistoryStatus;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringTypedRestClientIntegrationTest {
    private HttpServer server;
    private HistoryService history;
    private ProxyProperties.BackendDefinition backend;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void serializesAndDeserializesGeneratedStyleTypedModelsWithHeadersAndQueryParameters() {
        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> header = new AtomicReference<>();
        server.createContext("/products", exchange -> {
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            query.set(exchange.getRequestURI().getQuery());
            header.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
            respond(exchange, 200, "{\"id\":\"p-1\",\"name\":\"book\"}");
        });

        SpringTypedRestClient client = client();
        ProductResponse response = client.exchange("products", new RestRequest<>(
            HttpMethod.POST, "/products", new ProductRequest("book"),
            Map.of("X-Trace-Id", "trace-1"), Map.of("include", "price"), ProductResponse.class));

        assertThat(response.id()).isEqualTo("p-1");
        assertThat(response.name()).isEqualTo("book");
        assertThat(body.get()).contains("\"name\":\"book\"");
        assertThat(query.get()).isEqualTo("include=price");
        assertThat(header.get()).isEqualTo("trace-1");
        verify(history).emit(eq(backend), any(), any(), any(), any(), eq(HistoryStatus.SUBMITTED), eq(null), eq(null));
        verify(history).emit(eq(backend), any(), any(), any(), any(), eq(HistoryStatus.FULFILLED), eq(200), any());
    }

    @Test
    void retriesTransientUpstreamFailuresBeforeReturningTheTypedResponse() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/retry", exchange -> {
            if (calls.incrementAndGet() < 3) {
                respond(exchange, 503, "{\"error\":\"temporary\"}");
            } else {
                respond(exchange, 200, "{\"id\":\"p-2\",\"name\":\"recovered\"}");
            }
        });

        ProductResponse response = client().exchange(
            "products", RestRequest.of(HttpMethod.GET, "/retry", null, ProductResponse.class));

        assertThat(response.name()).isEqualTo("recovered");
        assertThat(calls).hasValue(3);
    }

    @Test
    void joinsConfiguredBackendPathAndEmitsFailedHistoryWithClassifiedFailure() {
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/api/orders", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            respond(exchange, 503, "temporary");
        });
        SpringTypedRestClient client = client();
        when(backend.path()).thenReturn("/api/");

        assertThatThrownBy(() -> client.exchange("products", RestRequest.of(HttpMethod.GET, "/orders", null, ProductResponse.class)))
            .isInstanceOf(com.db.olorin.rest.exception.UpstreamProxyException.class);
        assertThat(path.get()).isEqualTo("/api/orders");
        verify(history).emit(eq(backend), any(), any(), any(), any(), eq(HistoryStatus.FAILED), eq(503), any());
    }

    @Test
    void doesNotRetryAuthenticationDenials() {
        AtomicInteger calls = new AtomicInteger();
        server.createContext("/denied", exchange -> { calls.incrementAndGet(); respond(exchange, 401, "denied"); });

        assertThatThrownBy(() -> client().exchange("products", RestRequest.of(HttpMethod.GET, "/denied", null, ProductResponse.class)))
            .isInstanceOf(com.db.olorin.rest.exception.AuthenticationDeniedException.class);

        assertThat(calls).hasValue(1);
    }

    @Test
    void forwardsOnlyConfiguredCurrentRequestHeadersAndCookiesWithoutOverwritingExplicitValues() {
        AtomicReference<String> token = new AtomicReference<>(); AtomicReference<String> cookie = new AtomicReference<>();
        server.createContext("/context", exchange -> { token.set(exchange.getRequestHeaders().getFirst("x-asm-rctoken")); cookie.set(exchange.getRequestHeaders().getFirst("Cookie")); respond(exchange, 200, "{\"id\":\"p-3\",\"name\":\"context\"}"); });
        SpringTypedRestClient client = client();
        when(backend.forwardHeaders()).thenReturn(List.of("x-asm-rctoken")); when(backend.forwardCookies()).thenReturn(List.of("session"));
        MockHttpServletRequest incoming = new MockHttpServletRequest(); incoming.addHeader("x-asm-rctoken", "asm-token"); incoming.setCookies(new jakarta.servlet.http.Cookie("session", "s-1"));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));
        try {
            client.exchange("products", RestRequest.of(HttpMethod.GET, "/context", null, ProductResponse.class));
            assertThat(token.get()).isEqualTo("asm-token"); assertThat(cookie.get()).isEqualTo("session=s-1");
            client.exchange("products", new RestRequest<>(HttpMethod.GET, "/context", null, Map.of("x-asm-rctoken", "explicit"), Map.of(), ProductResponse.class).withCookies(Map.of("session", "explicit-cookie")));
            assertThat(token.get()).isEqualTo("explicit"); assertThat(cookie.get()).isEqualTo("session=explicit-cookie");
        } finally { RequestContextHolder.resetRequestAttributes(); }
    }

    private SpringTypedRestClient client() {
        ProxyProperties properties = mock(ProxyProperties.class);
        backend = mock(ProxyProperties.BackendDefinition.class);
        when(properties.backends()).thenReturn(List.of(backend));
        when(backend.name()).thenReturn("products");
        when(backend.enabled()).thenReturn(true);
        when(backend.baseUrl()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(backend.path()).thenReturn("");
        when(backend.timeout()).thenReturn(Duration.ofSeconds(5));
        when(backend.httpVersion()).thenReturn("http1_1");
        when(backend.proxy()).thenReturn(Optional.empty());
        when(backend.tlsProfile()).thenReturn(Optional.empty());
        when(backend.forwardHeaders()).thenReturn(List.of());
        when(backend.forwardCookies()).thenReturn(List.of());
        AuthServiceFactory authFactory = mock(AuthServiceFactory.class);
        when(authFactory.createAuthService(backend)).thenReturn((request, headers, requestBody) -> { });
        history = mock(HistoryService.class);
        return new SpringTypedRestClient(properties, RestClient.builder(), authFactory, new TlsContextFactory(properties), history);
    }

    private void respond(HttpExchange exchange, int status, String payload) throws IOException {
        byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    record ProductRequest(String name) { }
    record ProductResponse(String id, String name) { }
}
