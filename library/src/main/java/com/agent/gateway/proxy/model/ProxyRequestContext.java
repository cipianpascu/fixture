package com.agent.gateway.proxy.model;

import java.util.Locale;
import java.util.Map;

public record ProxyRequestContext(
    String method,
    String requestUri,
    String queryString,
    Map<String, String> headers,
    Map<String, String> cookies
) {

    public String header(String name) {
        if (name == null) {
            return null;
        }
        return headers.get(name.toLowerCase(Locale.ROOT));
    }

    public String cookie(String name) {
        return cookies.get(name);
    }
}
