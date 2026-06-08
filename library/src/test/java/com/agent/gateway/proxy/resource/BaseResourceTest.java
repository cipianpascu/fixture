package com.agent.gateway.proxy.resource;

import com.agent.gateway.proxy.TestHistoryConfig;
import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.service.ProxyService;
import com.agent.gateway.proxy.service.SchemaValidationService;
import com.agent.gateway.proxy.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void preservesRepeatedHeadersInRequestContext() {
        ProxyRequestContext requestContext = resource.buildWithHeaderLists(
            "GET",
            "/api/v1/orders/123",
            null,
            Map.of("X-Trace-Id", List.of("trace-1", "trace-2")),
            Map.of()
        );

        assertEquals(List.of("trace-1", "trace-2"), requestContext.headers().get("x-trace-id"));
        assertEquals("trace-1", requestContext.header("X-Trace-Id"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void normalizesRawHeaderMapsThatContainScalarValues() {
        Map rawHeaders = Map.of("X-Trace-Id", "trace-1");

        ProxyRequestContext requestContext = new ProxyRequestContext(
            "GET",
            "/api/v1/orders/123",
            null,
            rawHeaders,
            Map.of()
        );

        assertEquals(List.of("trace-1"), requestContext.headers().get("x-trace-id"));
        assertEquals("trace-1", requestContext.header("X-Trace-Id"));
    }

    @Test
    void extractsContractPathFromConfiguredPrefix() {
        assertEquals(
            "/items/123",
            resource.extract("/api/v1/catalog/items/123", "/api/v1/catalog")
        );
    }

    @Test
    void parsesSuccessfulJsonResponses() {
        Optional<JsonNode> parsed = resource.parseSuccessfulJson(
            Response.ok("{\"status\":\"ok\"}").type(MediaType.APPLICATION_JSON).build()
        );

        assertTrue(parsed.isPresent());
        assertEquals("ok", parsed.get().path("status").asText());
    }

    @Test
    void skipsErrorResponsesWhenParsingSuccessfulJson() {
        Optional<JsonNode> parsed = resource.parseSuccessfulJson(
            Response.status(502).entity("{\"error\":\"downstream\"}").type(MediaType.APPLICATION_JSON).build()
        );

        assertFalse(parsed.isPresent());
    }

    @Test
    void usesDefaultProxyFlowForSimpleProxying() {
        resource.proxyProperties = proxyProperties(backend("orders", "orders.yaml"));
        resource.validationService = new SchemaValidationService() {
            @Override
            public ValidationResult validateRequest(
                String schemaName,
                String path,
                ProxyRequestContext requestContext,
                String requestBody,
                String method) {
                return ValidationResult.allowed();
            }

            @Override
            public Response applyResponseContract(String schemaName, String method, String path, Response response) {
                return response;
            }
        };
        resource.proxyService = new ProxyService() {
            @Override
            public Response forward(
                ProxyProperties.BackendDefinition backend,
                ProxyRequestContext request,
                String requestBody) {
                return Response.ok("{\"status\":\"proxied\"}").type(MediaType.APPLICATION_JSON).build();
            }
        };

        Response response = resource.defaultProxyCall(
            "orders",
            "orders.yaml",
            "/ping",
            resource.build("GET", "/api/v1/orders/ping", null, Map.of(), Map.of()),
            null
        );

        assertEquals(200, response.getStatus());
        assertEquals("{\"status\":\"proxied\"}", response.getEntity());
    }

    private ProxyProperties proxyProperties(ProxyProperties.BackendDefinition backend) {
        return new ProxyProperties() {
            @Override
            public SchemaConfig schemas() {
                return new SchemaConfig() {
                    @Override
                    public String directory() {
                        return "classpath:schemas/";
                    }

                    @Override
                    public boolean validateRequests() {
                        return true;
                    }

                    @Override
                    public boolean validateBodies() {
                        return true;
                    }

                    @Override
                    public boolean validateResponses() {
                        return false;
                    }

                    @Override
                    public boolean strictMode() {
                        return true;
                    }
                };
            }

            @Override
            public Optional<TlsConfig> tls() {
                return Optional.empty();
            }

            @Override
            public HistoryConfig history() {
                return TestHistoryConfig.disabled();
            }

            @Override
            public java.util.List<BackendDefinition> backends() {
                return java.util.List.of(backend);
            }
        };
    }

    private ProxyProperties.BackendDefinition backend(String name, String schema) {
        return new ProxyProperties.BackendDefinition() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String baseUrl() {
                return "http://backend.example.com";
            }

            @Override
            public String path() {
                return "/api";
            }

            @Override
            public Optional<String> schema() {
                return Optional.of(schema);
            }

            @Override
            public java.time.Duration timeout() {
                return java.time.Duration.ofSeconds(30);
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
                return Optional.empty();
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

    private static final class TestBaseResource extends BaseResource {
        private ProxyRequestContext build(
            String method,
            String requestUri,
            String queryString,
            Map<String, String> headers,
            Map<String, String> cookies) {
            return buildRequestContext(method, requestUri, queryString, headers, cookies);
        }

        private ProxyRequestContext buildWithHeaderLists(
            String method,
            String requestUri,
            String queryString,
            Map<String, List<String>> headers,
            Map<String, String> cookies) {
            return buildRequestContextWithHeaderLists(method, requestUri, queryString, headers, cookies);
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

        private Optional<JsonNode> parseSuccessfulJson(Response response) {
            return parseSuccessfulJsonResponse(response);
        }

        private Response defaultProxyCall(
            String backendName,
            String schemaName,
            String contractPath,
            ProxyRequestContext requestContext,
            String requestBody) {
            return defaultProxy(backendName, schemaName, contractPath, requestContext, requestBody);
        }
    }
}
