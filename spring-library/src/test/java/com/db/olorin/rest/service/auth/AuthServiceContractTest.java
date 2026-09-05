package com.db.olorin.rest.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.exception.AuthenticationDeniedException;
import com.db.olorin.rest.exception.AuthorizationDeniedException;
import com.db.olorin.rest.exception.ProxyConfigurationException;
import com.db.olorin.rest.model.ProxyRequestContext;
import com.db.olorin.rest.service.TlsContextFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class AuthServiceContractTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void basicAuthUsesConfiguredCredentials() {
        ProxyProperties.BackendDefinition backend = backend(Map.of("username", "service", "password", "secret"));
        when(backend.securityType()).thenReturn(Optional.of("basic"));
        AuthServiceFactory factory = new AuthServiceFactory(audience -> "unused", mock(AuthServiceConfigRegistry.class), mock(AuthServiceCallerFactory.class), new AuthzTokenCache());
        Map<String, String> headers = new LinkedHashMap<>();

        factory.createAuthService(backend).enrichHeaders(context(), headers, null);

        assertThat(headers).containsEntry("Authorization", "Basic c2VydmljZTpzZWNyZXQ=");
    }

    @Test
    void formServiceMapsRequestSourcesAndAuthResponseHeader() throws Exception {
        AuthServiceCallerFactory callers = mock(AuthServiceCallerFactory.class);
        AuthServiceConfigRegistry registry = mock(AuthServiceConfigRegistry.class);
        AuthServiceCaller caller = mock(AuthServiceCaller.class);
        when(registry.get("forms")).thenReturn(serviceConfig());
        when(callers.get("forms")).thenReturn(caller);
        JsonNode response = JSON.readTree("{\"access_token\":\"form-token\"}");
        when(caller.postForm(eq("/oauth/token"), eq(Map.of("client_id", "client", "secret", "cookie-secret")), eq(JsonNode.class))).thenReturn(response);
        ProxyProperties.BackendDefinition backend = backend(Map.of(
            "service", "forms", "auth-path", "/oauth/token",
            "form-params.client_id", "header:X-Client", "form-params.secret", "cookie:secret",
            "response-headers.Authorization", "access_token", "response-header-prefixes.Authorization", "Bearer"));
        Map<String, String> headers = new LinkedHashMap<>();

        new FormAuthService(backend, callers, registry).enrichHeaders(context(), headers, null);

        verify(caller).postForm(eq("/oauth/token"), eq(Map.of("client_id", "client", "secret", "cookie-secret")), eq(JsonNode.class));
        assertThat(headers).containsEntry("Authorization", "Bearer form-token");
    }

    @Test
    void inlineFormRejectsNonFormBodyAndRequiresMappedFields() {
        ProxyProperties.BackendDefinition backend = backend(Map.of("service", "inline"));
        FormAuthService service = new FormAuthService(backend, null, null);

        assertThatThrownBy(() -> service.transformRequestBody(context(), new LinkedHashMap<>(Map.of("content-type", "application/json")), "{}"))
            .isInstanceOf(ProxyConfigurationException.class)
            .hasMessageContaining("application/x-www-form-urlencoded");
        assertThatThrownBy(() -> service.transformRequestBody(context(), new LinkedHashMap<>(), null))
            .isInstanceOf(ProxyConfigurationException.class)
            .hasMessageContaining("form-params");
    }

    @Test
    void formServiceRejectsServiceModeWithoutAResponseHeaderMapping() {
        ProxyProperties.BackendDefinition backend = backend(Map.of("service", "forms", "form-params.client", "header:X-Client"));
        AuthServiceConfigRegistry registry = mock(AuthServiceConfigRegistry.class);
        when(registry.get("forms")).thenReturn(serviceConfig());

        assertThatThrownBy(() -> new FormAuthService(backend, mock(AuthServiceCallerFactory.class), registry)
            .enrichHeaders(context(), new LinkedHashMap<>(), null))
            .isInstanceOf(ProxyConfigurationException.class)
            .hasMessageContaining("response-headers");
    }

    @Test
    void authCallerMapsFormPayloadCloudRunTokenAndDenialStatuses() {
        AtomicReference<String> requestBody = new AtomicReference<>();
        AtomicReference<String> cloudRunAuthorization = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            cloudRunAuthorization.set(exchange.getRequestHeaders().getFirst("X-Serverless-Authorization"));
            int status = exchange.getRequestURI().getPath().contains("denied") ? 401 : 200;
            write(exchange, status, status == 200 ? "{\"ok\":true}" : "denied");
        });
        ResolvedAuthServiceConfig config = new ResolvedAuthServiceConfig("auth", true, baseUrl(), "X-Session", "session", Duration.ofSeconds(2),
            Optional.empty(), Optional.of("cloudrun"), Map.of("audience", "https://auth.example"), new ResolvedAuthCacheConfig(false, Duration.ZERO, 1));
        AuthServiceCaller caller = new AuthServiceCaller(config, new TlsContextFactory(mock(ProxyProperties.class)), audience -> "id-token");

        JsonNode response = caller.postForm("/form", Map.of("scope", "read write"), JsonNode.class);

        assertThat(response.path("ok").asBoolean()).isTrue();
        assertThat(requestBody.get()).isEqualTo("scope=read+write");
        assertThat(cloudRunAuthorization.get()).isEqualTo("Bearer id-token");
        assertThatThrownBy(() -> caller.postJson("/denied", Map.of(), JsonNode.class))
            .isInstanceOf(AuthenticationDeniedException.class)
            .hasMessageContaining("HTTP 401");
    }

    @Test
    void cloudRunUsesConfiguredAudienceAndRejectsInvalidBaseUrl() {
        Map<String, String> headers = new LinkedHashMap<>();
        new CloudRunAuthService(audience -> {
            assertThat(audience).isEqualTo("https://custom-audience");
            return "identity-token";
        }, "https://backend.example/api", Map.of("audience", "https://custom-audience"))
            .enrichHeaders(context(), headers, null);

        assertThat(headers).containsEntry("X-Serverless-Authorization", "Bearer identity-token");
        assertThatThrownBy(() -> new CloudRunAuthService(audience -> "token", "/not-absolute", Map.of()))
            .isInstanceOf(ProxyConfigurationException.class)
            .hasMessageContaining("absolute URL");
    }

    private ProxyProperties.BackendDefinition backend(Map<String, String> config) {
        ProxyProperties.BackendDefinition backend = mock(ProxyProperties.BackendDefinition.class);
        when(backend.name()).thenReturn("products");
        when(backend.baseUrl()).thenReturn("https://backend.example");
        when(backend.securityConfig()).thenReturn(config);
        return backend;
    }

    private ResolvedAuthServiceConfig serviceConfig() {
        return new ResolvedAuthServiceConfig("forms", true, "http://forms.example", "X-Session", "session", Duration.ofSeconds(2),
            Optional.empty(), Optional.empty(), Map.of(), new ResolvedAuthCacheConfig(false, Duration.ZERO, 1));
    }

    private ProxyRequestContext context() {
        return new ProxyRequestContext("POST", "/products", null, Map.of("X-Client", List.of("client")), Map.of("secret", "cookie-secret"));
    }

    private void startServer(Handler handler) {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", handler::handle);
            server.start();
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private String baseUrl() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    private void write(HttpExchange exchange, int status, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface Handler { void handle(HttpExchange exchange) throws IOException; }
}
