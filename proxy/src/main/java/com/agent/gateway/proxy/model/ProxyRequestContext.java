package com.agent.gateway.proxy.model;

import java.util.Map;

public record ProxyRequestContext(
    String method,
    String requestUri,
    String queryString,
    Map<String, String> headers,
    Map<String, String> cookies
) {

    public String header(String name) {
        return headers.get(name);
    }

    public String cookie(String name) {
        return cookies.get(name);
    }
}
