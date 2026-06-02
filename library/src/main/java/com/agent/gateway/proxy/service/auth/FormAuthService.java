package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.AuthServiceException;
import com.agent.gateway.proxy.exception.AuthenticationRequiredException;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class FormAuthService implements AuthService {

    private final ProxyProperties proxyProperties;
    private final AuthServiceCaller authServiceCaller;
    private final Map<String, String> securityConfig;

    public FormAuthService(
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
            log.debug("Auth is disabled, skipping form auth lookup");
            return;
        }

        if (inlineMode()) {
            log.debug("Using inline form auth for backend request");
            return;
        }

        Map<String, String> formParameters = buildFormParameters(request);
        JsonNode authResponse = authServiceCaller.postForm(authPath(), formParameters, JsonNode.class);
        applyConfiguredHeaders(authResponse, headers);
    }

    @Override
    public String transformRequestBody(ProxyRequestContext request, Map<String, String> headers, String requestBody) {
        if (!inlineMode()) {
            return requestBody;
        }

        String contentType = headers.get("content-type");
        if (requestBody != null && !requestBody.isBlank()
            && contentType != null
            && !contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
            throw new ProxyConfigurationException(
                "form auth inline mode requires application/x-www-form-urlencoded requests");
        }

        Map<String, String> merged = new LinkedHashMap<>(parseFormBody(requestBody));
        merged.putAll(buildFormParameters(request));
        headers.put("content-type", "application/x-www-form-urlencoded");
        return encodeFormBody(merged);
    }

    private Map<String, String> buildFormParameters(ProxyRequestContext request) {
        Map<String, String> mappings = prefixedEntries("form-params.");
        if (mappings.isEmpty()) {
            throw new ProxyConfigurationException("form auth requires at least one form-params.* mapping");
        }

        Map<String, String> parameters = new LinkedHashMap<>();
        mappings.forEach((fieldName, mapping) -> parameters.put(fieldName, resolveRequestValue(request, fieldName, mapping)));
        return parameters;
    }

    private String resolveRequestValue(ProxyRequestContext request, String fieldName, String mapping) {
        if (mapping == null || mapping.isBlank()) {
            throw new ProxyConfigurationException(
                "Empty request mapping configured for form auth field '%s'".formatted(fieldName));
        }

        if (mapping.startsWith("header:")) {
            String headerName = mapping.substring("header:".length());
            String value = request.header(headerName);
            if (value == null || value.isBlank()) {
                throw new AuthenticationRequiredException(
                    "Missing required header '%s' for form-authenticated backend".formatted(headerName));
            }
            return value;
        }

        if (mapping.startsWith("cookie:")) {
            String cookieName = mapping.substring("cookie:".length());
            String value = request.cookie(cookieName);
            if (value == null || value.isBlank()) {
                throw new AuthenticationRequiredException(
                    "Missing required cookie '%s' for form-authenticated backend".formatted(cookieName));
            }
            return value;
        }

        if (mapping.startsWith("literal:")) {
            return mapping.substring("literal:".length());
        }

        throw new ProxyConfigurationException(
            "Unsupported form auth request mapping '%s' for field '%s'".formatted(mapping, fieldName));
    }

    private void applyConfiguredHeaders(JsonNode authResponse, Map<String, String> headers) {
        Map<String, String> responseHeaders = prefixedEntries("response-headers.");
        if (responseHeaders.isEmpty()) {
            throw new ProxyConfigurationException("form auth requires at least one response-headers.* mapping");
        }

        responseHeaders.forEach((headerName, responseField) -> {
            JsonNode value = authResponse.path(responseField);
            if (value.isMissingNode() || value.isNull() || value.asText().isBlank()) {
                throw new AuthServiceException(
                    "Auth service response field '%s' was missing or empty for header '%s'"
                        .formatted(responseField, headerName));
            }
            String prefix = securityConfig.get("response-header-prefixes." + headerName);
            String headerValue = value.asText();
            headers.put(
                headerName,
                prefix == null || prefix.isBlank() ? headerValue : prefix + " " + headerValue
            );
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
        String authPath = securityConfig.getOrDefault("auth-path", "/auth/form");
        if (authPath.isBlank()) {
            throw new ProxyConfigurationException("form auth-path must not be blank");
        }
        return authPath;
    }

    private boolean inlineMode() {
        return "inline".equalsIgnoreCase(securityConfig.getOrDefault("service", "inline"));
    }

    private Map<String, String> parseFormBody(String requestBody) {
        Map<String, String> values = new LinkedHashMap<>();
        if (requestBody == null || requestBody.isBlank()) {
            return values;
        }

        for (String pair : requestBody.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = decode(parts[0]);
            String value = parts.length > 1 ? decode(parts[1]) : "";
            values.put(key, value);
        }
        return values;
    }

    private String encodeFormBody(Map<String, String> parameters) {
        return parameters.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
