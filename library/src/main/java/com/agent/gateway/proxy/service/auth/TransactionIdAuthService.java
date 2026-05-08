package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.AuthServiceException;
import com.agent.gateway.proxy.exception.AuthenticationRequiredException;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
public class TransactionIdAuthService implements AuthService {

    private final ProxyProperties proxyProperties;
    private final AuthServiceCaller authServiceCaller;
    private final Map<String, String> securityConfig;

    public TransactionIdAuthService(
        ProxyProperties proxyProperties,
        AuthServiceCaller authServiceCaller,
        Map<String, String> securityConfig) {
        this.proxyProperties = proxyProperties;
        this.authServiceCaller = authServiceCaller;
        this.securityConfig = securityConfig == null ? Map.of() : Map.copyOf(securityConfig);
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
        return authServiceCaller.postJson(authPath(), authRequestBody, JsonNode.class);
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

    private String authPath() {
        String authPath = securityConfig.getOrDefault("auth-path", "/auth/transactions");
        if (authPath.isBlank()) {
            throw new ProxyConfigurationException("transactionid auth-path must not be blank");
        }
        return authPath;
    }
}
