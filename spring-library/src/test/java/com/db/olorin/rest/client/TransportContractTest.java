package com.db.olorin.rest.client;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.history.HistoryService;
import com.db.olorin.rest.service.TlsContextFactory;
import com.db.olorin.rest.service.auth.AuthServiceFactory;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransportContractTest {
    private HttpServer server;

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
    void selectsConfiguredProxyAndBypassesExactAndWildcardHosts() {
        ProxyProperties.BackendDefinition backend = backend();
        ProxyProperties.ProxyConfig proxy = mock(ProxyProperties.ProxyConfig.class);
        when(proxy.host()).thenReturn("proxy.internal");
        when(proxy.port()).thenReturn(8080);
        when(proxy.nonProxyHosts()).thenReturn(List.of("localhost", "*.svc.cluster.local"));
        when(backend.proxy()).thenReturn(Optional.of(proxy));

        ProxySelector selector = SpringTypedRestClient.createProxySelector(backend).orElseThrow();

        Proxy selected = selector.select(URI.create("https://partner.example.com/api")).getFirst();
        assertThat(selected.type()).isEqualTo(Proxy.Type.HTTP);
        assertThat(selector.select(URI.create("https://localhost/health"))).isEqualTo(List.of(Proxy.NO_PROXY));
        assertThat(selector.select(URI.create("https://orders.svc.cluster.local/api"))).isEqualTo(List.of(Proxy.NO_PROXY));
    }

    @Test
    void resolvesSupportedHttpVersionsAndRejectsUnknownVersions() {
        ProxyProperties.BackendDefinition backend = backend();
        when(backend.httpVersion()).thenReturn("http2");
        assertThat(SpringTypedRestClient.resolveHttpVersion(backend)).isEqualTo(HttpClient.Version.HTTP_2);
        when(backend.httpVersion()).thenReturn("http/1.1");
        assertThat(SpringTypedRestClient.resolveHttpVersion(backend)).isEqualTo(HttpClient.Version.HTTP_1_1);
    }

    @Test
    void removesHopByHopAndConnectionNominatedHeaders() {
        Map<String, String> headers = SpringTypedRestClient.filterOutboundHeaders(Map.of(
            "Connection", "X-Internal-Route, X-Temporary",
            "X-Internal-Route", "admin",
            "x-temporary", "one",
            "Transfer-Encoding", "chunked",
            "X-Request-Id", "trace-123"
        ));

        assertThat(headers).containsExactly(Map.entry("X-Request-Id", "trace-123"));
    }

    @Test
    void decodesGzippedJsonResponses() throws Exception {
        server.createContext("/compressed", exchange -> {
            byte[] compressed = gzip("{\"value\":\"ok\"}".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            exchange.sendResponseHeaders(200, compressed.length);
            exchange.getResponseBody().write(compressed);
            exchange.close();
        });

        assertThat(client().exchange("orders", RestRequest.of(HttpMethod.GET, "/compressed", null, Response.class)).value())
            .isEqualTo("ok");
    }

    @Test
    void doesNotForwardUnsafeHeadersToBackend() {
        AtomicReference<String> nominated = new AtomicReference<>();
        AtomicReference<String> connection = new AtomicReference<>();
        server.createContext("/headers", exchange -> {
            nominated.set(exchange.getRequestHeaders().getFirst("X-Internal-Route"));
            connection.set(exchange.getRequestHeaders().getFirst("Connection"));
            respond(exchange, "{\"value\":\"ok\"}");
        });

        client().exchange("orders", new RestRequest<>(HttpMethod.GET, "/headers", null,
            Map.of("Connection", "X-Internal-Route", "X-Internal-Route", "private"), Map.of(), Response.class));

        assertThat(nominated.get()).isNull();
        assertThat(connection.get()).isNull();
    }

    private SpringTypedRestClient client() {
        ProxyProperties properties = mock(ProxyProperties.class);
        ProxyProperties.BackendDefinition backend = backend();
        when(properties.backends()).thenReturn(List.of(backend));
        AuthServiceFactory authFactory = mock(AuthServiceFactory.class);
        when(authFactory.createAuthService(backend)).thenReturn((request, headers, body) -> { });
        return new SpringTypedRestClient(properties, RestClient.builder(), authFactory,
            new TlsContextFactory(properties), mock(HistoryService.class));
    }

    private ProxyProperties.BackendDefinition backend() {
        ProxyProperties.BackendDefinition backend = mock(ProxyProperties.BackendDefinition.class);
        when(backend.name()).thenReturn("orders");
        when(backend.enabled()).thenReturn(true);
        when(backend.baseUrl()).thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(backend.path()).thenReturn("");
        when(backend.timeout()).thenReturn(Duration.ofSeconds(5));
        when(backend.httpVersion()).thenReturn("http1_1");
        when(backend.proxy()).thenReturn(Optional.empty());
        when(backend.tlsProfile()).thenReturn(Optional.empty());
        return backend;
    }

    private static byte[] gzip(byte[] value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(value);
        }
        return output.toByteArray();
    }

    private static void respond(HttpExchange exchange, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    record Response(String value) { }
}
