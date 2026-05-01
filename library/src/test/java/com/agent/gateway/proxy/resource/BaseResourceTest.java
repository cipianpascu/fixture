package com.agent.gateway.proxy.resource;

import com.agent.gateway.proxy.model.ProxyRequestContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class BaseResourceTest {

    private final TestBaseResource resource = new TestBaseResource();

    @Test
    void buildsDerivedRequestContextsWithoutMutatingTheSource() {
        ProxyRequestContext source = resource.build(
            "GET",
            "/api/v1/orders/123",
            "expand=true",
            Map.of("X-Trace-Id", "trace-1"),
            Map.of("sessionId", "abc")
        );

        ProxyRequestContext derived = resource.derive(
            source,
            "POST",
            "/internal/orders/123/audit",
            null
        );

        assertEquals("GET", source.method());
        assertEquals("/api/v1/orders/123", source.requestUri());
        assertEquals("expand=true", source.queryString());

        assertEquals("POST", derived.method());
        assertEquals("/internal/orders/123/audit", derived.requestUri());
        assertEquals("expand=true", derived.queryString());
        assertEquals("trace-1", derived.header("X-Trace-Id"));
        assertEquals("abc", derived.cookie("sessionId"));
        assertNotSame(source.headers(), derived.headers());
        assertNotSame(source.cookies(), derived.cookies());
    }

    @Test
    void extractsContractPathFromConfiguredPrefix() {
        assertEquals(
            "/items/123",
            resource.extract("/api/v1/catalog/items/123", "/api/v1/catalog")
        );
    }

    private static final class TestBaseResource extends BaseResource {
        private ProxyRequestContext build(
            String method,
            String requestUri,
            String queryString,
            Map<String, String> headers,
            Map<String, String> cookies) {
            return buildRequestContext(method, requestUri, queryString, headers, cookies);
        }

        private ProxyRequestContext derive(
            ProxyRequestContext source,
            String method,
            String requestUri,
            String queryString) {
            return deriveRequestContext(source, method, requestUri, queryString);
        }

        private String extract(String requestUri, String routePrefix) {
            return extractContractPath(requestUri, routePrefix);
        }
    }
}
