package com.db.olorin.rest.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.exception.ProxyConfigurationException;
import com.db.olorin.rest.exception.UpstreamProxyException;
import com.db.olorin.rest.history.HistoryService;
import com.db.olorin.rest.history.HistoryStatus;
import com.db.olorin.rest.model.ProxyRequestContext;
import com.db.olorin.rest.service.TlsContextFactory;
import com.db.olorin.rest.service.auth.AuthService;
import com.db.olorin.rest.service.auth.AuthServiceFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;

/** Spring REST client for generated JSON request and response models. */
public class SpringTypedRestClient implements TypedRestClient {
    private static final Set<String> HOP_BY_HOP = Set.of("connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer", "transfer-encoding", "upgrade");
    private final ProxyProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final AuthServiceFactory authServiceFactory;
    private final TlsContextFactory tlsContextFactory;
    private final HistoryService historyService;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public SpringTypedRestClient(ProxyProperties properties, RestClient.Builder restClientBuilder,
                                 AuthServiceFactory authServiceFactory, TlsContextFactory tlsContextFactory,
                                 HistoryService historyService) {
        this.properties = properties; this.restClientBuilder = restClientBuilder;
        this.authServiceFactory = authServiceFactory; this.tlsContextFactory = tlsContextFactory; this.historyService = historyService;
    }

    @Override public <I, O> O exchange(String clientName, RestRequest<I, O> typedRequest) {
        ProxyProperties.BackendDefinition backend = backend(clientName);
        String relativePath = appendQuery(normalizePath(backend.path(), typedRequest.path()), typedRequest.queryParameters(), typedRequest.additionalQueryParameters());
        Map<String, String> outbound = headers(typedRequest);
        forwardFromCurrentRequest(backend, typedRequest, outbound);
        ProxyRequestContext context = new ProxyRequestContext(typedRequest.method().name(), relativePath, null, inputHeaders(typedRequest.headers()), typedRequest.cookies());
        String incomingBody = typedRequest.body() == null ? null : String.valueOf(typedRequest.body());
        String transformed = incomingBody;
        Map<String, List<String>> historyHeaders = toHistoryHeaders(outbound);
        try {
            AuthService auth = authServiceFactory.createAuthService(backend);
            auth.enrichHeaders(backend, context, outbound, incomingBody);
            transformed = auth.transformRequestBody(context, outbound, incomingBody);
            Object body = transformed != null && !transformed.equals(incomingBody) ? transformed : typedRequest.body();
            historyHeaders = toHistoryHeaders(outbound);
            historyService.enrichOutboundHeaders(backend, context, historyHeaders);
            historyHeaders.forEach((name, values) -> { if (!values.isEmpty()) outbound.put(name, values.getFirst()); });
            historyService.emit(backend, context, incomingBody, historyHeaders, transformed, HistoryStatus.SUBMITTED, null, null);
            ResponseEntity<O> response = invoke(backend, typedRequest, relativePath, outbound, body);
            historyService.emit(backend, context, incomingBody, historyHeaders, transformed, HistoryStatus.FULFILLED, response.getStatusCode().value(), response.getBody() == null ? null : String.valueOf(response.getBody()));
            return response.getBody();
        } catch (RuntimeException failure) {
            RuntimeException categorized = categorize(clientName, typedRequest.method(), relativePath, failure);
            historyService.emit(backend, context, incomingBody, historyHeaders, transformed, HistoryStatus.FAILED, status(failure), responseBody(failure));
            throw categorized;
        }
    }

    private <I, O> ResponseEntity<O> invoke(ProxyProperties.BackendDefinition backend, RestRequest<I, O> request,
                                              String path, Map<String, String> headers, Object body) {
        CircuitState circuit = circuits.computeIfAbsent(backend.name(), ignored -> new CircuitState());
        circuit.assertAvailable(backend.name());
        RuntimeException failure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                RestClient.RequestBodySpec call = restClientBuilder.clone().requestFactory(new JdkClientHttpRequestFactory(httpClient(backend))).build()
                    .method(request.method()).uri(join(backend.baseUrl(), path)).headers(values -> {
                        values.setAccept(List.of(MediaType.APPLICATION_JSON)); headers.forEach(values::set);
                    });
                if (body != null) call.body(body);
                ResponseEntity<byte[]> rawResponse = call.retrieve().toEntity(byte[].class);
                ResponseEntity<O> response = decodeResponse(rawResponse, request.responseType());
                circuit.success(); return response;
            } catch (RuntimeException e) {
                failure = categorize(backend.name(), request.method(), path, e);
                if (!transientFailure(failure) || attempt == 3) { circuit.failure(); throw failure; }
                pause();
            }
        }
        throw failure;
    }

    private <O> ResponseEntity<O> decodeResponse(ResponseEntity<byte[]> response, Class<O> responseType) {
        byte[] body = response.getBody();
        if (body == null || body.length == 0 || responseType == Void.class || responseType == void.class) {
            return new ResponseEntity<>(null, response.getHeaders(), response.getStatusCode());
        }
        try {
            byte[] decoded = "gzip".equalsIgnoreCase(response.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING))
                ? gunzip(body) : body;
            O converted;
            if (responseType == byte[].class) {
                converted = responseType.cast(decoded);
            } else if (responseType == String.class) {
                converted = responseType.cast(new String(decoded, StandardCharsets.UTF_8));
            } else {
                converted = objectMapper.readValue(decoded, responseType);
            }
            return new ResponseEntity<>(converted, response.getHeaders(), response.getStatusCode());
        } catch (IOException error) {
            throw new UpstreamProxyException("Unable to decode backend response", error);
        }
    }

    private byte[] gunzip(byte[] compressed) throws IOException {
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
            return gzip.readAllBytes();
        }
    }

    private ProxyProperties.BackendDefinition backend(String name) {
        return properties.backends().stream().filter(value -> value.enabled() && value.name().equals(name)).findFirst()
            .orElseThrow(() -> new ProxyConfigurationException("No enabled backend named '%s' is configured".formatted(name)));
    }
    private Map<String, String> headers(RestRequest<?, ?> request) {
        Map<String, String> result = new LinkedHashMap<>();
        request.headers().forEach((name, value) -> putHeader(result, name, value));
        request.additionalHeaders().forEach((name, values) -> { if (!values.isEmpty()) putHeader(result, name, String.join(",", values)); });
        if (!request.cookies().isEmpty()) result.put(HttpHeaders.COOKIE, request.cookies().entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining("; ")));
        if (request.body() != null) result.putIfAbsent(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        return filterOutboundHeaders(result);
    }
    private void forwardFromCurrentRequest(ProxyProperties.BackendDefinition backend, RestRequest<?, ?> request, Map<String, String> outbound) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return;
        jakarta.servlet.http.HttpServletRequest current = attributes.getRequest();
        backend.forwardHeaders().forEach(name -> {
            if (name == null || name.isBlank() || containsHeader(outbound, name) || HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) return;
            String value = current.getHeader(name); if (value != null && !value.isBlank()) outbound.put(name, value);
        });
        if (backend.forwardCookies().isEmpty() || containsHeader(outbound, HttpHeaders.COOKIE)) return;
        Map<String, String> cookies = new LinkedHashMap<>();
        if (current.getCookies() != null) for (jakarta.servlet.http.Cookie cookie : current.getCookies()) cookies.put(cookie.getName(), cookie.getValue());
        List<String> selected = backend.forwardCookies().stream().filter(cookies::containsKey).map(name -> name + "=" + cookies.get(name)).toList();
        if (!selected.isEmpty()) outbound.put(HttpHeaders.COOKIE, String.join("; ", selected));
    }
    private boolean containsHeader(Map<String, String> headers, String name) { return headers.keySet().stream().anyMatch(existing -> existing.equalsIgnoreCase(name)); }
    private void putHeader(Map<String, String> headers, String name, String value) {
        if (name == null || value == null) return;
        headers.put(name, value);
    }
    static Map<String, String> filterOutboundHeaders(Map<String, String> candidateHeaders) {
        Set<String> nominated = candidateHeaders.entrySet().stream()
            .filter(entry -> HttpHeaders.CONNECTION.equalsIgnoreCase(entry.getKey()))
            .flatMap(entry -> List.of(entry.getValue().split(",")).stream())
            .map(String::trim).filter(value -> !value.isEmpty())
            .map(value -> value.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());
        Map<String, String> filtered = new LinkedHashMap<>();
        candidateHeaders.forEach((name, value) -> {
            String normalized = name.toLowerCase(Locale.ROOT);
            if (!HOP_BY_HOP.contains(normalized) && !nominated.contains(normalized)) {
                filtered.put(name, value);
            }
        });
        return filtered;
    }
    private String normalizePath(String basePath, String operationPath) {
        String left = basePath == null ? "" : basePath.trim(); String right = operationPath == null ? "" : operationPath.trim();
        if (left.isBlank() || "/".equals(left)) return right.startsWith("/") ? right : "/" + right;
        return (left.startsWith("/") ? left : "/" + left).replaceAll("/+$", "") + "/" + right.replaceFirst("^/+", "");
    }
    private String appendQuery(String path, Map<String, String> scalar, Map<String, List<String>> multi) {
        List<String> entries = new ArrayList<>(); scalar.forEach((name, value) -> add(entries, name, value)); multi.forEach((name, values) -> values.forEach(value -> add(entries, name, value)));
        if (entries.isEmpty()) return path; return path + (path.contains("?") ? "&" : "?") + String.join("&", entries);
    }
    private void add(List<String> entries, String name, String value) { if (name != null && value != null) entries.add(URLEncoder.encode(name, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)); }
    private String join(String base, String path) { return base.replaceAll("/+$", "") + (path.startsWith("/") ? path : "/" + path); }
    private Map<String, List<String>> inputHeaders(Map<String, String> headers) { Map<String, List<String>> result = new LinkedHashMap<>(); headers.forEach((name, value) -> result.put(name, List.of(value))); return result; }
    private Map<String, List<String>> toHistoryHeaders(Map<String, String> headers) { Map<String, List<String>> result = new LinkedHashMap<>(); headers.forEach((name, value) -> result.put(name.toLowerCase(Locale.ROOT), List.of(value))); return result; }
    private Integer status(RuntimeException error) { return response(error).map(value -> value.getStatusCode().value()).orElse(null); }
    private String responseBody(RuntimeException error) { return response(error).map(RestClientResponseException::getResponseBodyAsString).orElse(null); }
    private Optional<RestClientResponseException> response(Throwable error) {
        Throwable current=error;
        while(current!=null) { if(current instanceof RestClientResponseException response) return Optional.of(response); current=current.getCause(); }
        return Optional.empty();
    }
    private RuntimeException categorize(String client, org.springframework.http.HttpMethod method, String path, RuntimeException error) {
        if (error instanceof ProxyConfigurationException || error instanceof UpstreamProxyException || error instanceof com.db.olorin.rest.exception.AuthenticationRequiredException || error instanceof com.db.olorin.rest.exception.AuthenticationDeniedException || error instanceof com.db.olorin.rest.exception.AuthorizationDeniedException || error instanceof com.db.olorin.rest.exception.AuthServiceException) return error;
        if (error instanceof RestClientResponseException response) {
            int code = response.getStatusCode().value();
            if (code == 401) return new com.db.olorin.rest.exception.AuthenticationDeniedException("Backend rejected authentication for '" + client + "'");
            if (code == 403) return new com.db.olorin.rest.exception.AuthorizationDeniedException("Backend denied authorization for '" + client + "'");
            return new UpstreamProxyException("Backend '" + client + "' returned HTTP " + code, error);
        }
        return new UpstreamProxyException("REST client '" + client + "' failed calling " + method + " " + path, error);
    }
    private boolean transientFailure(RuntimeException error) { return error instanceof UpstreamProxyException || error instanceof com.db.olorin.rest.exception.AuthServiceException; }
    private void pause() { try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new UpstreamProxyException("Interrupted while retrying upstream call", e); } }
    private HttpClient httpClient(ProxyProperties.BackendDefinition backend) {
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(backend.timeout()).version(resolveHttpVersion(backend));
        tlsContextFactory.createBackendSslContext(backend).ifPresent(builder::sslContext);
        createProxySelector(backend).ifPresent(builder::proxy);
        return builder.build();
    }
    static HttpClient.Version resolveHttpVersion(ProxyProperties.BackendDefinition backend) {
        return switch (backend.httpVersion().toLowerCase(Locale.ROOT)) {
            case "http2", "http_2", "http/2" -> HttpClient.Version.HTTP_2;
            case "http1_1", "http1.1", "http/1.1" -> HttpClient.Version.HTTP_1_1;
            default -> throw new ProxyConfigurationException("Unsupported HTTP version '" + backend.httpVersion() + "' for backend '" + backend.name() + "'");
        };
    }
    static Optional<ProxySelector> createProxySelector(ProxyProperties.BackendDefinition backend) {
        return backend.proxy().map(BackendProxySelector::new);
    }
    static final class BackendProxySelector extends ProxySelector {
        private final ProxyProperties.ProxyConfig config; private BackendProxySelector(ProxyProperties.ProxyConfig config) { this.config = config; }
        @Override public List<java.net.Proxy> select(URI uri) { String host = uri.getHost(); if (host != null && config.nonProxyHosts().stream().anyMatch(pattern -> host.matches(pattern.replace(".", "\\.").replace("*", ".*")))) return List.of(java.net.Proxy.NO_PROXY); return List.of(new java.net.Proxy(java.net.Proxy.Type.HTTP, new InetSocketAddress(config.host(), config.port()))); }
        @Override public void connectFailed(URI uri, java.net.SocketAddress address, java.io.IOException exception) { }
    }
    private static final class CircuitState {
        private final ArrayList<Boolean> recent = new ArrayList<>(); private long until;
        synchronized void assertAvailable(String backend) { if (until > System.currentTimeMillis()) throw new UpstreamProxyException("Circuit breaker is open for backend '" + backend + "'"); if (until != 0) { until = 0; recent.clear(); } }
        synchronized void success() { recent.add(Boolean.TRUE); trim(); }
        synchronized void failure() { recent.add(Boolean.FALSE); trim(); if (recent.size() >= 4 && recent.stream().filter(value -> !value).count() * 2 >= recent.size()) until = System.currentTimeMillis() + 5000; }
        private void trim() { while (recent.size() > 4) recent.removeFirst(); }
    }
}
