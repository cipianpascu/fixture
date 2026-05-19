package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class ProxyServiceProxySelectorTest {

    @Test
    void createsHttpProxySelectorForBackend() {
        ProxySelector selector = ProxyService.createProxySelector(
            backend("orders", proxy("proxy.internal", 8080, List.of()))
        ).orElseThrow();

        List<Proxy> proxies = selector.select(URI.create("https://partner.example.com/api/orders"));

        assertEquals(1, proxies.size());
        Proxy proxy = proxies.getFirst();
        assertEquals(Proxy.Type.HTTP, proxy.type());
        InetSocketAddress address = assertInstanceOf(InetSocketAddress.class, proxy.address());
        assertEquals("proxy.internal", address.getHostString());
        assertEquals(8080, address.getPort());
    }

    @Test
    void bypassesProxyForConfiguredNonProxyHosts() {
        ProxySelector selector = ProxyService.createProxySelector(
            backend("orders", proxy("proxy.internal", 8080, List.of("localhost", "*.svc.cluster.local")))
        ).orElseThrow();

        assertEquals(
            List.of(Proxy.NO_PROXY),
            selector.select(URI.create("https://localhost/health"))
        );
        assertEquals(
            List.of(Proxy.NO_PROXY),
            selector.select(URI.create("https://orders.svc.cluster.local/api"))
        );
    }

    @Test
    void clientKeyIncludesProxyConfiguration() {
        String firstKey = ProxyService.clientKey(backend("orders", proxy("proxy-a.internal", 8080, List.of())));
        String secondKey = ProxyService.clientKey(backend("orders", proxy("proxy-b.internal", 8080, List.of())));

        assertNotEquals(firstKey, secondKey);
    }

    @Test
    void clientKeyIncludesHttpVersion() {
        String firstKey = ProxyService.clientKey(backend("orders", proxy("proxy.internal", 8080, List.of()), "http1_1"));
        String secondKey = ProxyService.clientKey(backend("orders", proxy("proxy.internal", 8080, List.of()), "http2"));

        assertNotEquals(firstKey, secondKey);
    }

    @Test
    void resolvesConfiguredHttpVersion() {
        assertEquals(
            HttpClient.Version.HTTP_2,
            ProxyService.resolveHttpVersion(backend("orders", proxy("proxy.internal", 8080, List.of()), "http2"))
        );
        assertEquals(
            HttpClient.Version.HTTP_1_1,
            ProxyService.resolveHttpVersion(backend("orders", proxy("proxy.internal", 8080, List.of()), "http/1.1"))
        );
    }

    @Test
    void decodesGzippedResponseBodies() throws Exception {
        byte[] original = "{\"status\":\"ok\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ProxyService.DecodedResponse decoded = ProxyService.decodeResponse(
            httpResponse(gzip(original), Map.of("Content-Encoding", List.of("gzip")))
        );

        assertEquals(true, decoded.decompressed());
        assertArrayEquals(original, decoded.body());
    }

    @Test
    void masksSensitiveHeadersForDebugLogging() {
        Map<String, String> sanitized = ProxyService.sanitizeHeadersForLogging(
            backend("orders", proxy("proxy.internal", 8080, List.of()), "http1_1", Map.of(
                "bearer-header", "Authorization",
                "token-headers.X-Glue-Token", "glue_token",
                "static-headers.x-api-key", "secret-key"
            )),
            Map.of(
                "authorization", "Bearer token",
                "cookie", "session=abc",
                "X-Glue-Token", "glue",
                "x-api-key", "secret-key",
                "x-trace-id", "trace-123"
            )
        );

        assertEquals("***", sanitized.get("authorization"));
        assertEquals("***", sanitized.get("cookie"));
        assertEquals("***", sanitized.get("X-Glue-Token"));
        assertEquals("***", sanitized.get("x-api-key"));
        assertEquals("trace-123", sanitized.get("x-trace-id"));
    }

    @Test
    void normalizesHeadersCaseInsensitivelyAfterAuthEnrichment() {
        Map<String, String> normalized = ProxyService.normalizeHeaders(Map.of(
            "authorization", "Bearer incoming-user-token",
            "Authorization", "Bearer backend-token",
            "X-Request-Id", "tx-123"
        ));

        assertEquals(2, normalized.size());
        assertEquals("Bearer backend-token", normalized.get("authorization"));
        assertEquals("tx-123", normalized.get("x-request-id"));
    }

    private ProxyProperties.BackendDefinition backend(String name, ProxyProperties.ProxyConfig proxyConfig) {
        return backend(name, proxyConfig, "http1_1");
    }

    private ProxyProperties.BackendDefinition backend(
        String name,
        ProxyProperties.ProxyConfig proxyConfig,
        String httpVersion) {
        return backend(name, proxyConfig, httpVersion, Map.of());
    }

    private ProxyProperties.BackendDefinition backend(
        String name,
        ProxyProperties.ProxyConfig proxyConfig,
        String httpVersion,
        Map<String, String> securityConfig) {
        return new ProxyProperties.BackendDefinition() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String baseUrl() {
                return "https://backend.example.com";
            }

            @Override
            public String path() {
                return "/api";
            }

            @Override
            public Optional<String> schema() {
                return Optional.empty();
            }

            @Override
            public Duration timeout() {
                return Duration.ofSeconds(30);
            }

            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String httpVersion() {
                return httpVersion;
            }

            @Override
            public Optional<String> securityType() {
                return Optional.of("none");
            }

            @Override
            public Map<String, String> securityConfig() {
                return securityConfig;
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
            public Optional<String> tlsProfile() {
                return Optional.empty();
            }

            @Override
            public Optional<ProxyProperties.ProxyConfig> proxy() {
                return Optional.of(proxyConfig);
            }
        };
    }

    private ProxyProperties.ProxyConfig proxy(String host, int port, List<String> nonProxyHosts) {
        return new ProxyProperties.ProxyConfig() {
            @Override
            public String host() {
                return host;
            }

            @Override
            public int port() {
                return port;
            }

            @Override
            public List<String> nonProxyHosts() {
                return nonProxyHosts;
            }
        };
    }

    private HttpResponse<byte[]> httpResponse(byte[] body, Map<String, List<String>> headers) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return 200;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<byte[]>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return HttpHeaders.of(headers, (name, value) -> true);
            }

            @Override
            public byte[] body() {
                return body;
            }

            @Override
            public Optional<javax.net.ssl.SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("http://backend.example.com");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    private byte[] gzip(byte[] payload) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream)) {
            gzipOutputStream.write(payload);
        }
        return outputStream.toByteArray();
    }
}
