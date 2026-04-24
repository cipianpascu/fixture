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

    @Override
    public Map<String, String> start() {
        try {
            backendServer = HttpServer.create(new InetSocketAddress(0), 0);
            authServer = HttpServer.create(new InetSocketAddress(0), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start test servers", e);
        }

        RETRY_SESSION_CALLS.set(0);
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

    private void registerBackendHandlers() {
        backendServer.createContext("/internal/secondary/ping", exchange ->
            respond(exchange, 200, "{\"status\":\"secondary-ok\"}"));
        backendServer.createContext("/templated/items/123", exchange ->
            respond(exchange, 200, "{\"ok\":true}"));
        backendServer.createContext("/jwt/ping", exchange ->
            respond(exchange, 200, "{\"status\":\"jwt-ok\"}"));
    }

    private void registerAuthHandlers() {
        authServer.createContext("/auth/tokens", exchange -> {
            String sessionId = exchange.getRequestHeaders().getFirst("X-Session-Id");
            if ("retry-session".equals(sessionId)) {
                int callNumber = RETRY_SESSION_CALLS.incrementAndGet();
                if (callNumber == 1) {
                    respond(exchange, 503, "{\"error\":\"temporary auth outage\"}");
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
