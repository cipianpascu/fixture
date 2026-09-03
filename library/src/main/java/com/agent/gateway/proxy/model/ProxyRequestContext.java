package com.agent.gateway.proxy.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public record ProxyRequestContext(
    String method,
    String requestUri,
    String queryString,
    Map<String, List<String>> headers,
    Map<String, String> cookies,
    Map<String, String> generatedValues
) {

    public ProxyRequestContext {
        headers = normalizeHeaders(headers);
        cookies = cookies == null ? Map.of() : new LinkedHashMap<>(cookies);
        generatedValues = generatedValues == null ? new ConcurrentHashMap<>() : generatedValues;
    }

    public ProxyRequestContext(
        String method,
        String requestUri,
        String queryString,
        Map<String, List<String>> headers,
        Map<String, String> cookies) {
        this(method, requestUri, queryString, headers, cookies, new ConcurrentHashMap<>());
    }

    public String header(String name) {
        if (name == null) {
            return null;
        }
        List<String> values = headers.get(name.toLowerCase(Locale.ROOT));
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.getFirst();
    }

    public String cookie(String name) {
        return cookies.get(name);
    }

    /**
     * Resolves a generated value once for this request context. Derived request contexts
     * should retain {@link #generatedValues()} when they describe the same inbound request.
     */
    public String generatedValue(String name, Supplier<String> generator) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Generated request value name must not be blank");
        }
        if (generator == null) {
            throw new IllegalArgumentException("Generated request value supplier must not be null");
        }
        synchronized (generatedValues) {
            return generatedValues.computeIfAbsent(name, ignored -> generator.get());
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> normalizeHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> normalized = new LinkedHashMap<>();
        ((Map<?, ?>) headers).forEach((rawKey, rawValue) -> {
            if (rawKey == null || rawValue == null) {
                return;
            }
            String key = rawKey.toString().toLowerCase(Locale.ROOT);
            List<String> values = normalizeHeaderValues(rawValue);
            if (!values.isEmpty()) {
                normalized.put(key, List.copyOf(values));
            }
        });
        return normalized;
    }

    private static List<String> normalizeHeaderValues(Object rawValue) {
        if (rawValue instanceof List<?> list) {
            return list.stream()
                .filter(value -> value != null)
                .map(Object::toString)
                .toList();
        }
        if (rawValue instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object value : iterable) {
                if (value != null) {
                    values.add(value.toString());
                }
            }
            return values;
        }
        return List.of(rawValue.toString());
    }
}
