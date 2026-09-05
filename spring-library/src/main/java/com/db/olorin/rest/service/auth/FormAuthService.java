package com.db.olorin.rest.service.auth;

import com.db.olorin.rest.exception.AuthServiceException;
import com.db.olorin.rest.exception.AuthenticationRequiredException;
import com.db.olorin.rest.exception.ProxyConfigurationException;
import com.db.olorin.rest.model.ProxyRequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Legacy gateway form-auth strategy, including inline form enrichment. */
public final class FormAuthService implements AuthService {
    private final ProxyPropertiesHolder backend;
    private final AuthServiceCallerFactory callers;
    private final AuthServiceConfigRegistry registry;

    public FormAuthService(com.db.olorin.rest.config.ProxyProperties.BackendDefinition backend,
        AuthServiceCallerFactory callers, AuthServiceConfigRegistry registry) {
        this.backend = new ProxyPropertiesHolder(backend);
        this.callers = callers;
        this.registry = registry;
    }

    @Override public void enrichHeaders(ProxyRequestContext request, Map<String, String> headers, String body) {
        if (inline()) return;
        String service = config().getOrDefault("service", "auth");
        ResolvedAuthServiceConfig serviceConfig = registry.get(service);
        if (!serviceConfig.enabled()) return;
        String authPath = config().getOrDefault("auth-path", "/auth/form");
        if (authPath.isBlank()) throw new ProxyConfigurationException("form auth-path must not be blank");
        Map<String, String> responseHeaders = mappings("response-headers.");
        if (responseHeaders.isEmpty()) throw new ProxyConfigurationException("form auth requires at least one response-headers.* mapping");
        JsonNode response = callers.get(service).postForm(authPath, fields(request), JsonNode.class);
        responseHeaders.forEach((header, field) -> {
            String value = response.path(field).asText(null);
            if (value == null || value.isBlank()) throw new AuthServiceException("Form auth response field '" + field + "' is missing");
            String prefix = config().get("response-header-prefixes." + header);
            headers.put(header, prefix == null || prefix.isBlank() ? value : prefix + " " + value);
        });
    }

    @Override public String transformRequestBody(ProxyRequestContext request, Map<String, String> headers, String body) {
        if (!inline()) return body;
        String contentType = header(headers, "content-type");
        if (body != null && !body.isBlank() && contentType != null && !contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
            throw new ProxyConfigurationException("form auth inline mode requires application/x-www-form-urlencoded requests");
        }
        Map<String, String> values = decode(body);
        values.putAll(fields(request));
        headers.put("content-type", "application/x-www-form-urlencoded");
        return encode(values);
    }

    private boolean inline() { return "inline".equalsIgnoreCase(config().getOrDefault("service", "inline")); }
    private Map<String, String> fields(ProxyRequestContext request) {
        Map<String, String> mapping = mappings("form-params.");
        if (mapping.isEmpty()) throw new ProxyConfigurationException("form auth requires form-params.* mappings");
        Map<String, String> result = new LinkedHashMap<>();
        mapping.forEach((field, source) -> result.put(field, resolve(source, request)));
        return result;
    }
    private String resolve(String source, ProxyRequestContext request) {
        if (source.startsWith("literal:")) return source.substring(8);
        String value;
        if (source.startsWith("header:")) value=request.header(source.substring(7));
        else if (source.startsWith("cookie:")) value=request.cookie(source.substring(7));
        else throw new ProxyConfigurationException("Unsupported form auth mapping '" + source + "'");
        if (value == null || value.isBlank()) throw new AuthenticationRequiredException("Missing required form auth value");
        return value;
    }
    private Map<String, String> mappings(String prefix) {
        Map<String, String> result = new LinkedHashMap<>();
        config().forEach((key, value) -> { if (key.startsWith(prefix)) result.put(key.substring(prefix.length()), value); });
        return result;
    }
    private Map<String, String> config() { return backend.backend.securityConfig(); }
    private String header(Map<String, String> headers, String name) {
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst().orElse(null);
    }
    private Map<String,String> decode(String body) { Map<String,String> result=new LinkedHashMap<>(); if(body==null||body.isBlank())return result; for(String pair:body.split("&")){String[] parts=pair.split("=",2);result.put(URLDecoder.decode(parts[0],StandardCharsets.UTF_8),parts.length==1?"":URLDecoder.decode(parts[1],StandardCharsets.UTF_8));}return result; }
    private String encode(Map<String,String> values) { return values.entrySet().stream().map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&")); }
    private record ProxyPropertiesHolder(com.db.olorin.rest.config.ProxyProperties.BackendDefinition backend) { }
}
