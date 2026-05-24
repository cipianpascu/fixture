package com.agent.gateway.proxy.model;

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
}
