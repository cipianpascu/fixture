package com.agent.gateway.proxy.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyTestResource implements QuarkusTestResourceLifecycleManager {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer backendServer;
    private HttpServer authServer;

    private static final AtomicInteger RETRY_SESSION_CALLS = new AtomicInteger();
    private static final AtomicInteger CLOUD_RUN_AUTH_SESSION_CALLS = new AtomicInteger();
    private static final AtomicReference<String> LAST_APIGEE_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> LAST_APIGEE_API_KEY = new AtomicReference<>();
    private static final AtomicReference<String> LAST_GLUE_AUTHORIZATION = new AtomicReference<>();
    private static final AtomicReference<String> LAST_GLUE_TOKEN = new AtomicReference<>();
    private static final AtomicInteger ORDER_DETAILS_CALLS = new AtomicInteger();
    private static final AtomicInteger PAYMENT_ORDER_CALLS = new AtomicInteger();

    @Override
    public Map<String, String> start() {
        try {
            backendServer = HttpServer.create(new InetSocketAddress(0), 0);
            authServer = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start test servers", e);
        }

        RETRY_SESSION_CALLS.set(0);
        CLOUD_RUN_AUTH_SESSION_CALLS.set(0);
        LAST_APIGEE_AUTHORIZATION.set(null);
        LAST_APIGEE_API_KEY.set(null);
        LAST_GLUE_AUTHORIZATION.set(null);
        LAST_GLUE_TOKEN.set(null);
        ORDER_DETAILS_CALLS.set(0);
        PAYMENT_ORDER_CALLS.set(0);
        registerBackendHandlers();
        registerAuthHandlers();

        backendServer.start();
        authServer.start();

        String backendBaseUrl = "http://127.0.0.1:" + backendServer.getAddress().getPort();
        String authBaseUrl = "http://127.0.0.1:" + authServer.getAddress().getPort();

        Map<String, String> config = new LinkedHashMap<>();
        config.put("gateway.schemas.directory", "classpath:schemas/");
        config.put("gateway.schemas.validate-requests", "true");
        config.put("gateway.schemas.validate-bodies", "true");
        config.put("gateway.schemas.validate-responses", "true");
        config.put("gateway.schemas.strict-mode", "true");
        config.put("gateway.auth.enabled", "true");
        config.put("gateway.auth.service-url", authBaseUrl);
        config.put("gateway.auth.security-type", "cloudrun");
        config.put("gateway.auth.security-config.audience", "https://oauth-service-ew.a.run.app/");

        config.put("gateway.backends[0].name", "secondary-service");
        config.put("gateway.backends[0].baseUrl", backendBaseUrl);
        config.put("gateway.backends[0].path", "/internal/secondary");
        config.put("gateway.backends[0].schema", "secondary-service.yaml");
        config.put("gateway.backends[0].enabled", "true");
        config.put("gateway.backends[0].securityType", "none");

        config.put("gateway.backends[1].name", "templated-service");
        config.put("gateway.backends[1].baseUrl", backendBaseUrl);
        config.put("gateway.backends[1].path", "/templated");
        config.put("gateway.backends[1].schema", "templated-service.yaml");
        config.put("gateway.backends[1].enabled", "true");
        config.put("gateway.backends[1].securityType", "none");

        config.put("gateway.backends[2].name", "unsupported-auth-service");
        config.put("gateway.backends[2].baseUrl", backendBaseUrl);
        config.put("gateway.backends[2].path", "/unsupported");
        config.put("gateway.backends[2].schema", "secondary-service.yaml");
        config.put("gateway.backends[2].enabled", "true");
        config.put("gateway.backends[2].securityType", "oauth2");

        config.put("gateway.backends[3].name", "misconfigured-basic-service");
        config.put("gateway.backends[3].baseUrl", backendBaseUrl);
        config.put("gateway.backends[3].path", "/basic");
        config.put("gateway.backends[3].schema", "secondary-service.yaml");
        config.put("gateway.backends[3].enabled", "true");
        config.put("gateway.backends[3].securityType", "basic");

        config.put("gateway.backends[4].name", "jwt-service");
        config.put("gateway.backends[4].baseUrl", backendBaseUrl);
        config.put("gateway.backends[4].path", "/jwt");
        config.put("gateway.backends[4].schema", "secondary-service.yaml");
        config.put("gateway.backends[4].enabled", "true");
        config.put("gateway.backends[4].securityType", "jwt");
        config.put("gateway.backends[4].auth-request.sparte-gvo[0]", "a");
        config.put("gateway.backends[4].auth-request.sparte-gvo[1]", "b");
        config.put("gateway.backends[4].auth-request.btx[0]", "FirstFunction");
        config.put("gateway.backends[4].auth-request.pss[0]", "SecondFunction");

        config.put("gateway.backends[5].name", "jwt-apigee-service");
        config.put("gateway.backends[5].baseUrl", backendBaseUrl);
        config.put("gateway.backends[5].path", "/jwt-apigee");
        config.put("gateway.backends[5].schema", "secondary-service.yaml");
        config.put("gateway.backends[5].enabled", "true");
        config.put("gateway.backends[5].securityType", "jwt");
        config.put("gateway.backends[5].auth-request.sparte-gvo[0]", "a");
        config.put("gateway.backends[5].auth-request.sparte-gvo[1]", "b");
        config.put("gateway.backends[5].auth-request.btx[0]", "FirstFunction");
        config.put("gateway.backends[5].auth-request.pss[0]", "SecondFunction");
        config.put("gateway.backends[5].securityConfig.bearer-source", "customer_access_token");
        config.put("gateway.backends[5].securityConfig.static-headers.x-api-key", "test-apigee-key");

        config.put("gateway.backends[6].name", "jwt-glue-service");
        config.put("gateway.backends[6].baseUrl", backendBaseUrl);
        config.put("gateway.backends[6].path", "/jwt-glue");
        config.put("gateway.backends[6].schema", "secondary-service.yaml");
        config.put("gateway.backends[6].enabled", "true");
        config.put("gateway.backends[6].securityType", "jwt");
        config.put("gateway.backends[6].auth-request.sparte-gvo[0]", "a");
        config.put("gateway.backends[6].auth-request.sparte-gvo[1]", "b");
        config.put("gateway.backends[6].auth-request.btx[0]", "FirstFunction");
        config.put("gateway.backends[6].auth-request.pss[0]", "SecondFunction");
        config.put("gateway.backends[6].securityConfig.bearer-source", "auth_z_token");
        config.put("gateway.backends[6].securityConfig.token-headers.X-Glue-Token", "glue_token");

        config.put("gateway.backends[7].name", "cloudrun-service");
        config.put("gateway.backends[7].baseUrl", backendBaseUrl);
        config.put("gateway.backends[7].path", "/cloudrun");
        config.put("gateway.backends[7].schema", "cloudrun-service.yaml");
        config.put("gateway.backends[7].enabled", "true");
        config.put("gateway.backends[7].securityType", "cloudrun");
        config.put("gateway.backends[7].securityConfig.audience", "https://orders-service-ew.a.run.app/");

        config.put("gateway.backends[8].name", "orders-service");
        config.put("gateway.backends[8].baseUrl", backendBaseUrl);
        config.put("gateway.backends[8].path", "/orders");
        config.put("gateway.backends[8].schema", "secondary-service.yaml");
        config.put("gateway.backends[8].enabled", "true");
        config.put("gateway.backends[8].securityType", "none");

        config.put("gateway.backends[9].name", "payments-service");
        config.put("gateway.backends[9].baseUrl", backendBaseUrl);
        config.put("gateway.backends[9].path", "/payments");
        config.put("gateway.backends[9].schema", "secondary-service.yaml");
        config.put("gateway.backends[9].enabled", "true");
        config.put("gateway.backends[9].securityType", "none");

        config.put("gateway.backends[10].name", "parameter-service");
        config.put("gateway.backends[10].baseUrl", backendBaseUrl);
        config.put("gateway.backends[10].path", "/params");
        config.put("gateway.backends[10].schema", "parameter-service.yaml");
        config.put("gateway.backends[10].enabled", "true");
        config.put("gateway.backends[10].securityType", "none");

        config.put("gateway.backends[11].name", "recursive-service");
        config.put("gateway.backends[11].baseUrl", backendBaseUrl);
        config.put("gateway.backends[11].path", "/recursive");
        config.put("gateway.backends[11].schema", "recursive-service.yaml");
        config.put("gateway.backends[11].enabled", "true");
        config.put("gateway.backends[11].securityType", "none");

        config.put("gateway.resources.order-summary.schema", "order-summary.yaml");
        config.put("gateway.resources.order-summary.orders-backend", "orders-service");
        config.put("gateway.resources.order-summary.orders-path-template", "/details/{id}");
        config.put("gateway.resources.order-summary.payments-backend", "payments-service");
        config.put("gateway.resources.order-summary.payments-path-template", "/orders/{id}");

        return config;
    }

    @Override
    public void stop() {
        if (backendServer != null) {
            backendServer.stop(0);
        }
        if (authServer != null) {
            authServer.stop(0);
        }
    }

    public static int getRetrySessionCalls() {
        return RETRY_SESSION_CALLS.get();
    }

    public static int getCloudRunAuthSessionCalls() {
        return CLOUD_RUN_AUTH_SESSION_CALLS.get();
    }

    public static String getLastApigeeAuthorization() {
        return LAST_APIGEE_AUTHORIZATION.get();
    }

    public static String getLastApigeeApiKey() {
        return LAST_APIGEE_API_KEY.get();
    }

    public static String getLastGlueAuthorization() {
        return LAST_GLUE_AUTHORIZATION.get();
    }

    public static String getLastGlueToken() {
        return LAST_GLUE_TOKEN.get();
    }

    public static int getOrderDetailsCalls() {
        return ORDER_DETAILS_CALLS.get();
    }

    public static int getPaymentOrderCalls() {
        return PAYMENT_ORDER_CALLS.get();
    }

    private void registerBackendHandlers() {
        backendServer.createContext("/internal/secondary/ping", exchange ->
            respond(exchange, 200, "{\"status\":\"secondary-ok\",\"internal\":\"discard-me\"}"));
        backendServer.createContext("/templated/items/123", exchange ->
            respond(exchange, 200, "{\"ok\":true,\"debug\":true}"));
        backendServer.createContext("/jwt/ping", exchange ->
            respond(exchange, 200, "{\"status\":\"jwt-ok\",\"internal\":\"discard-me\"}"));
        backendServer.createContext("/jwt-apigee/ping", exchange -> {
            LAST_APIGEE_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
            LAST_APIGEE_API_KEY.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            exchange.getResponseHeaders().add("Authorization", "Bearer should-not-leak");
            exchange.getResponseHeaders().add("x-api-key", "should-not-leak");
            exchange.getResponseHeaders().add("Set-Cookie", "session=should-not-leak");
            respond(exchange, 200, "{\"status\":\"jwt-ok\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/jwt-glue/ping", exchange -> {
            LAST_GLUE_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
            LAST_GLUE_TOKEN.set(exchange.getRequestHeaders().getFirst("X-Glue-Token"));
            exchange.getResponseHeaders().add("Authorization", "Bearer should-not-leak");
            exchange.getResponseHeaders().add("X-Glue-Token", "should-not-leak");
            respond(exchange, 200, "{\"status\":\"jwt-ok\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/cloudrun/ping", exchange ->
            respond(exchange, 200,
                "{\"serverlessAuthorization\":\"%s\",\"internal\":\"discard-me\"}".formatted(
                    exchange.getRequestHeaders().getFirst("X-Serverless-Authorization"))));
        backendServer.createContext("/orders/details/123", exchange -> {
            ORDER_DETAILS_CALLS.incrementAndGet();
            respond(exchange, 200, "{\"id\":\"123\",\"status\":\"READY\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/orders/details/500", exchange -> {
            ORDER_DETAILS_CALLS.incrementAndGet();
            respond(exchange, 200, "{\"id\":\"500\",\"status\":\"READY\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/payments/orders/123", exchange -> {
            PAYMENT_ORDER_CALLS.incrementAndGet();
            respond(exchange, 200, "{\"orderId\":\"123\",\"paymentStatus\":\"PAID\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/payments/orders/500", exchange -> {
            PAYMENT_ORDER_CALLS.incrementAndGet();
            respond(exchange, 502, "{\"error\":\"payments-down\",\"internal\":\"discard-me\"}");
        });
        backendServer.createContext("/params/search/123", exchange ->
            respond(exchange, 200, "{\"ok\":true,\"debug\":\"discard-me\"}"));
        backendServer.createContext("/recursive/tree", exchange ->
            respond(exchange, 200, "{\"ok\":true}"));
    }

    private void registerAuthHandlers() {
        authServer.createContext("/auth/tokens", exchange -> {
            String sessionId = exchange.getRequestURI().getPath().substring("/auth/tokens/".length());
            String serverlessAuthorization = exchange.getRequestHeaders().getFirst("X-Serverless-Authorization");
            JsonNode requestBody = OBJECT_MAPPER.readTree(exchange.getRequestBody());
            assertAuthRequestShape(requestBody);
            if ("retry-session".equals(sessionId)) {
                int callNumber = RETRY_SESSION_CALLS.incrementAndGet();
                if (callNumber == 1) {
                    respond(exchange, 503, "{\"error\":\"temporary auth outage\"}");
                    return;
                }
                respond(
                    exchange,
                    200,
                    "{\"glue_token\":\"glue-token\",\"auth_z_token\":\"authz-token\",\"customer_access_token\":\"customer-token\",\"disallowed_pss\":[]}"
                );
                return;
            }

            if ("cloudrun-auth-session".equals(sessionId)) {
                CLOUD_RUN_AUTH_SESSION_CALLS.incrementAndGet();
                if (!"Bearer test-id-token-for:https://oauth-service-ew.a.run.app/".equals(serverlessAuthorization)) {
                    respond(exchange, 401, "{\"error\":\"missing cloud run auth\"}");
                    return;
                }
                respond(
                    exchange,
                    200,
                    "{\"glue_token\":\"glue-token\",\"auth_z_token\":\"authz-token\",\"customer_access_token\":\"customer-token\",\"disallowed_pss\":[]}"
                );
                return;
            }

            if ("fatal-session".equals(sessionId)) {
                respond(exchange, 500, "{\"error\":\"auth-service-down\"}");
                return;
            }

            respond(
                exchange,
                200,
                "{\"glue_token\":\"glue-token\",\"auth_z_token\":\"authz-token\",\"customer_access_token\":\"customer-token\",\"disallowed_pss\":[]}"
            );
        });
    }

    private static void assertAuthRequestShape(JsonNode requestBody) {
        if (!requestBody.path("sparteGvo").isArray() ||
            !requestBody.path("btx").isArray() ||
            !requestBody.path("pss").isArray()) {
            throw new IllegalStateException("Auth request does not match expected JSON shape: " + requestBody);
        }
    }

    private static void respond(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        } finally {
            exchange.close();
        }
    }
}
