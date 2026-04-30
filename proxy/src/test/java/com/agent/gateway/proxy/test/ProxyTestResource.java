package com.agent.gateway.proxy.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ProxyTestResource implements QuarkusTestResourceLifecycleManager {

    private HttpServer backendServer;
    private HttpServer authServer;

    private static final AtomicInteger RETRY_SESSION_CALLS = new AtomicInteger();
    private static final AtomicInteger CLOUD_RUN_AUTH_SESSION_CALLS = new AtomicInteger();

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
        config.put("gateway.backends[0].authScopes", "");

        config.put("gateway.backends[1].name", "templated-service");
        config.put("gateway.backends[1].baseUrl", backendBaseUrl);
        config.put("gateway.backends[1].path", "/templated");
        config.put("gateway.backends[1].schema", "templated-service.yaml");
        config.put("gateway.backends[1].enabled", "true");
        config.put("gateway.backends[1].securityType", "none");
        config.put("gateway.backends[1].authScopes", "");

        config.put("gateway.backends[2].name", "unsupported-auth-service");
        config.put("gateway.backends[2].baseUrl", backendBaseUrl);
        config.put("gateway.backends[2].path", "/unsupported");
        config.put("gateway.backends[2].schema", "secondary-service.yaml");
        config.put("gateway.backends[2].enabled", "true");
        config.put("gateway.backends[2].securityType", "oauth2");
        config.put("gateway.backends[2].authScopes", "");

        config.put("gateway.backends[3].name", "misconfigured-basic-service");
        config.put("gateway.backends[3].baseUrl", backendBaseUrl);
        config.put("gateway.backends[3].path", "/basic");
        config.put("gateway.backends[3].schema", "secondary-service.yaml");
        config.put("gateway.backends[3].enabled", "true");
        config.put("gateway.backends[3].securityType", "basic");
        config.put("gateway.backends[3].authScopes", "");

        config.put("gateway.backends[4].name", "jwt-service");
        config.put("gateway.backends[4].baseUrl", backendBaseUrl);
        config.put("gateway.backends[4].path", "/jwt");
        config.put("gateway.backends[4].schema", "secondary-service.yaml");
        config.put("gateway.backends[4].enabled", "true");
        config.put("gateway.backends[4].securityType", "jwt");
        config.put("gateway.backends[4].authScopes[0]", "read:users");

        config.put("gateway.backends[5].name", "cloudrun-service");
        config.put("gateway.backends[5].baseUrl", backendBaseUrl);
        config.put("gateway.backends[5].path", "/cloudrun");
        config.put("gateway.backends[5].schema", "secondary-service.yaml");
        config.put("gateway.backends[5].enabled", "true");
        config.put("gateway.backends[5].securityType", "cloudrun");
        config.put("gateway.backends[5].securityConfig.audience", "https://orders-service-ew.a.run.app/");

        config.put("gateway.backends[6].name", "orders-service");
        config.put("gateway.backends[6].baseUrl", backendBaseUrl);
        config.put("gateway.backends[6].path", "/orders");
        config.put("gateway.backends[6].schema", "secondary-service.yaml");
        config.put("gateway.backends[6].enabled", "true");
        config.put("gateway.backends[6].securityType", "none");

        config.put("gateway.backends[7].name", "payments-service");
        config.put("gateway.backends[7].baseUrl", backendBaseUrl);
        config.put("gateway.backends[7].path", "/payments");
        config.put("gateway.backends[7].schema", "secondary-service.yaml");
        config.put("gateway.backends[7].enabled", "true");
        config.put("gateway.backends[7].securityType", "none");

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

    private void registerBackendHandlers() {
        backendServer.createContext("/internal/secondary/ping", exchange ->
            respond(exchange, 200, "{\"status\":\"secondary-ok\"}"));
        backendServer.createContext("/templated/items/123", exchange ->
            respond(exchange, 200, "{\"ok\":true}"));
        backendServer.createContext("/jwt/ping", exchange ->
            respond(exchange, 200, "{\"status\":\"jwt-ok\"}"));
        backendServer.createContext("/cloudrun/ping", exchange ->
            respond(exchange, 200,
                "{\"serverlessAuthorization\":\"%s\"}".formatted(
                    exchange.getRequestHeaders().getFirst("X-Serverless-Authorization"))));
        backendServer.createContext("/orders/details/123", exchange ->
            respond(exchange, 200, "{\"id\":\"123\",\"status\":\"READY\"}"));
        backendServer.createContext("/payments/orders/123", exchange ->
            respond(exchange, 200, "{\"orderId\":\"123\",\"paymentStatus\":\"PAID\"}"));
    }

    private void registerAuthHandlers() {
        authServer.createContext("/auth/tokens", exchange -> {
            String sessionId = exchange.getRequestHeaders().getFirst("X-Session-Id");
            String serverlessAuthorization = exchange.getRequestHeaders().getFirst("X-Serverless-Authorization");
            if ("retry-session".equals(sessionId)) {
                int callNumber = RETRY_SESSION_CALLS.incrementAndGet();
                if (callNumber == 1) {
                    respond(exchange, 503, "{\"error\":\"temporary auth outage\"}");
                    return;
                }
                respond(exchange, 200, "{\"serviceToken\":\"svc-token\",\"userGrantsToken\":\"usr-token\"}");
                return;
            }

            if ("cloudrun-auth-session".equals(sessionId)) {
                CLOUD_RUN_AUTH_SESSION_CALLS.incrementAndGet();
                if (!"Bearer test-id-token-for:https://oauth-service-ew.a.run.app/".equals(serverlessAuthorization)) {
                    respond(exchange, 401, "{\"error\":\"missing cloud run auth\"}");
                    return;
                }
                respond(exchange, 200, "{\"serviceToken\":\"svc-token\",\"userGrantsToken\":\"usr-token\"}");
                return;
            }

            respond(exchange, 200, "{\"serviceToken\":\"svc-token\",\"userGrantsToken\":\"usr-token\"}");
        });
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
