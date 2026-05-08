package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.AuthServiceException;
import com.agent.gateway.proxy.exception.AuthenticationRequiredException;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.service.TlsContextFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
public class TransactionIdAuthService implements AuthService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ProxyProperties proxyProperties;
    private final CloudRunIdTokenProvider cloudRunIdTokenProvider;
    private final Map<String, String> securityConfig;
    private final HttpClient authHttpClient;
    private final String authUrl;
    private final String cloudRunAudience;

    public TransactionIdAuthService(
        ProxyProperties proxyProperties,
        TlsContextFactory tlsContextFactory,
        CloudRunIdTokenProvider cloudRunIdTokenProvider,
        Map<String, String> securityConfig) {
        this.proxyProperties = proxyProperties;
        this.cloudRunIdTokenProvider = cloudRunIdTokenProvider;
        this.securityConfig = securityConfig == null ? Map.of() : Map.copyOf(securityConfig);
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(proxyProperties.auth().timeout());
        tlsContextFactory.createAuthSslContext().ifPresent(builder::sslContext);
        this.authHttpClient = builder.build();
        this.authUrl = buildAuthUrl(proxyProperties.auth().serviceUrl(), this.securityConfig);
        this.cloudRunAudience = resolveCloudRunAudience();
    }

    @Override
    public void enrichHeaders(ProxyRequestContext request, Map<String, String> headers, String requestBody) {
        if (!proxyProperties.auth().enabled()) {
            log.debug("Auth is disabled, skipping transaction-id lookup");
            return;
        }

        Map<String, Object> authRequestBody = buildAuthRequestBody(request);
        JsonNode authResponse = callAuthService(authRequestBody);
        applyConfiguredHeaders(authResponse, headers);
    }

    private Map<String, Object> buildAuthRequestBody(ProxyRequestContext request) {
        Map<String, String> bodyMappings = prefixedEntries("request-body.");
        if (bodyMappings.isEmpty()) {
            throw new ProxyConfigurationException("transactionid auth requires at least one request-body.* mapping");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        bodyMappings.forEach((fieldName, mapping) -> payload.put(fieldName, resolveRequestValue(request, fieldName, mapping)));
        return payload;
    }

    private Object resolveRequestValue(ProxyRequestContext request, String fieldName, String mapping) {
        if (mapping == null || mapping.isBlank()) {
            throw new ProxyConfigurationException(
                "Empty request mapping configured for transactionid auth field '%s'".formatted(fieldName));
        }

        if (mapping.startsWith("header:")) {
            String headerName = mapping.substring("header:".length());
            String value = request.header(headerName);
            if (value == null || value.isBlank()) {
                throw new AuthenticationRequiredException(
                    "Missing required header '%s' for transactionid-authenticated backend".formatted(headerName));
            }
            return value;
        }

        if (mapping.startsWith("cookie:")) {
            String cookieName = mapping.substring("cookie:".length());
            String value = request.cookie(cookieName);
            if (value == null || value.isBlank()) {
                throw new AuthenticationRequiredException(
                    "Missing required cookie '%s' for transactionid-authenticated backend".formatted(cookieName));
            }
            return value;
        }

        if (mapping.startsWith("literal:")) {
            return mapping.substring("literal:".length());
        }

        throw new ProxyConfigurationException(
            "Unsupported transactionid request mapping '%s' for field '%s'".formatted(mapping, fieldName));
    }

    private JsonNode callAuthService(Map<String, Object> authRequestBody) {
        try {
            String requestJson = OBJECT_MAPPER.writeValueAsString(authRequestBody);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(authUrl))
                .timeout(proxyProperties.auth().timeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson));

            if (cloudRunAudience != null) {
                builder.header("X-Serverless-Authorization", "Bearer " + cloudRunIdTokenProvider.getIdToken(cloudRunAudience));
            }

            HttpResponse<String> response = authHttpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String suffix = response.body() == null || response.body().isBlank() ? "" : ": " + response.body();
                throw new AuthServiceException("Auth service returned HTTP %d%s".formatted(response.statusCode(), suffix));
            }

            return OBJECT_MAPPER.readTree(response.body());
        } catch (AuthServiceException e) {
            throw e;
        } catch (IOException e) {
            throw new AuthServiceException("Failed to call auth service for transaction ID", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthServiceException("Auth service transaction-id request was interrupted", e);
        }
    }

    private void applyConfiguredHeaders(JsonNode authResponse, Map<String, String> headers) {
        Map<String, String> responseHeaders = prefixedEntries("response-headers.");
        if (responseHeaders.isEmpty()) {
            throw new ProxyConfigurationException("transactionid auth requires at least one response-headers.* mapping");
        }

        responseHeaders.forEach((headerName, responseField) -> {
            JsonNode value = authResponse.path(responseField);
            if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
                throw new AuthServiceException(
                    "Auth service response field '%s' was missing or empty for header '%s'"
                        .formatted(responseField, headerName));
            }
            headers.put(headerName, value.asText());
        });
    }

    private Map<String, String> prefixedEntries(String prefix) {
        Map<String, String> values = new LinkedHashMap<>();
        securityConfig.forEach((key, value) -> {
            if (key != null && key.startsWith(prefix)) {
                values.put(key.substring(prefix.length()), value);
            }
        });
        return values;
    }

    private String buildAuthUrl(String serviceUrl, Map<String, String> config) {
        String authPath = config.getOrDefault("auth-path", "/auth/transactions");
        if (authPath.isBlank()) {
            throw new ProxyConfigurationException("transactionid auth-path must not be blank");
        }
        if (authPath.startsWith("http://") || authPath.startsWith("https://")) {
            return authPath;
        }
        boolean serviceEndsWithSlash = serviceUrl.endsWith("/");
        boolean pathStartsWithSlash = authPath.startsWith("/");
        if (serviceEndsWithSlash && pathStartsWithSlash) {
            return serviceUrl.substring(0, serviceUrl.length() - 1) + authPath;
        }
        if (!serviceEndsWithSlash && !pathStartsWithSlash) {
            return serviceUrl + "/" + authPath;
        }
        return serviceUrl + authPath;
    }

    private String resolveCloudRunAudience() {
        String authSecurityType = proxyProperties.auth().securityType().orElse("none");
        if ("cloudrun".equalsIgnoreCase(authSecurityType) || "cloud_run".equalsIgnoreCase(authSecurityType)) {
            return CloudRunAudienceResolver.resolveAudience(
                proxyProperties.auth().serviceUrl(),
                proxyProperties.auth().securityConfig(),
                "auth service"
            );
        }
        return null;
    }
}
