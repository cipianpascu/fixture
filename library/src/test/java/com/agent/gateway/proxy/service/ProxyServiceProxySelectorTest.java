package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

    private ProxyProperties.BackendDefinition backend(String name, ProxyProperties.ProxyConfig proxyConfig) {
        return backend(name, proxyConfig, "http1_1");
    }

    private ProxyProperties.BackendDefinition backend(
        String name,
        ProxyProperties.ProxyConfig proxyConfig,
        String httpVersion) {
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
                return Map.of();
            }

            @Override
            public Optional<ProxyProperties.AuthRequestConfig> authRequest() {
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
}
