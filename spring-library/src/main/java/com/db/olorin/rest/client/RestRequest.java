package com.db.olorin.rest.client;

import org.springframework.http.HttpMethod;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A strongly typed outbound REST operation. The consuming application's
 * generated request and response classes become {@code I} and {@code O}.
 */
public record RestRequest<I, O>(
    HttpMethod method,
    String path,
    I body,
    Map<String, String> headers,
    Map<String, String> queryParameters,
    Class<O> responseType,
    Map<String, String> cookies,
    Map<String, List<String>> additionalHeaders,
    Map<String, List<String>> additionalQueryParameters
) {
    public RestRequest {
        Objects.requireNonNull(method, "method must not be null");
        Objects.requireNonNull(path, "path must not be null");
        Objects.requireNonNull(responseType, "responseType must not be null");
        headers = headers == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(headers));
        queryParameters = queryParameters == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(queryParameters));
        cookies = cookies == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(cookies));
        additionalHeaders = copyMulti(additionalHeaders);
        additionalQueryParameters = copyMulti(additionalQueryParameters);
    }

    public RestRequest(HttpMethod method, String path, I body, Map<String, String> headers,
                       Map<String, String> queryParameters, Class<O> responseType) {
        this(method, path, body, headers, queryParameters, responseType, Map.of(), Map.of(), Map.of());
    }

    public static <I, O> RestRequest<I, O> of(
        HttpMethod method, String path, I body, Class<O> responseType) {
        return new RestRequest<>(method, path, body, Map.of(), Map.of(), responseType, Map.of(), Map.of(), Map.of());
    }

    public RestRequest<I, O> withCookies(Map<String, String> values) {
        return new RestRequest<>(method, path, body, headers, queryParameters, responseType, values, additionalHeaders, additionalQueryParameters);
    }

    public RestRequest<I, O> withAdditionalHeaders(Map<String, List<String>> values) {
        return new RestRequest<>(method, path, body, headers, queryParameters, responseType, cookies, values, additionalQueryParameters);
    }

    public RestRequest<I, O> withAdditionalQueryParameters(Map<String, List<String>> values) {
        return new RestRequest<>(method, path, body, headers, queryParameters, responseType, cookies, additionalHeaders, values);
    }

    private static Map<String, List<String>> copyMulti(Map<String, List<String>> values) {
        if (values == null || values.isEmpty()) return Map.of();
        Map<String, List<String>> result = new LinkedHashMap<>();
        values.forEach((name, entries) -> result.put(name, entries == null ? List.of() : List.copyOf(entries)));
        return Map.copyOf(result);
    }
}
