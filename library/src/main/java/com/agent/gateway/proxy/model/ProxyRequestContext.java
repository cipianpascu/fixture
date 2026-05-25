package com.agent.gateway.proxy.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

public record ProxyRequestContext(
    String method,
    String requestUri,
    String queryString,
    Map<String, List<String>> headers,
    Map<String, String> cookies
) {

    public ProxyRequestContext {
        headers = normalizeHeaders(headers);
        cookies = cookies == null ? Map.of() : new LinkedHashMap<>(cookies);
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
