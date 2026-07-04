package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.AuthServiceException;
import com.agent.gateway.proxy.exception.AuthenticationDeniedException;
import com.agent.gateway.proxy.exception.AuthenticationRequiredException;
import com.agent.gateway.proxy.exception.AuthorizationDeniedException;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import com.agent.gateway.proxy.exception.UpstreamProxyException;
import com.agent.gateway.proxy.history.HistoryService;
import com.agent.gateway.proxy.history.HistoryStatus;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.service.auth.AuthService;
import com.agent.gateway.proxy.service.auth.AuthServiceFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Proxy Service - Lightweight Request Forwarding (Quarkus)
 * 
 * NO database dependencies - pure HTTP proxying.
 */
@ApplicationScoped
@Slf4j
public class ProxyService {

    static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
        "connection",
        "content-length",
        "expect",
        "host",
        "transfer-encoding",
        "upgrade"
    );

    static final Set<String> NON_FORWARDED_REQUEST_HEADERS = Set.of(
        "accept-encoding",
        "content-encoding"
    );

    private static final Set<String> SENSITIVE_RESPONSE_HEADERS = Set.of(
        "authorization",
        "proxy-authorization",
        "proxy-authenticate",
        "set-cookie",
        "cookie",
        "x-glue-token",
        "x-auth-z-token",
        "x-customer-access-token",
        "x-serverless-authorization"
    );

    private static final String MASKED_VALUE = "***";
    
    @Inject
    AuthServiceFactory authServiceFactory;

    @Inject
    TlsContextFactory tlsContextFactory;

    @Inject
    BackendInvocationFailureMapper failureMapper;

    @Inject
    HistoryService historyService;

    private final Map<String, HttpClient> httpClients = new ConcurrentHashMap<>();
    
    /**
     * Forward request to backend
     */
    @Retry(
        maxRetries = 2,
        delay = 200,
        retryOn = {UpstreamProxyException.class, AuthServiceException.class},
        abortOn = {
            ProxyConfigurationException.class,
            AuthenticationRequiredException.class,
            AuthenticationDeniedException.class,
            AuthorizationDeniedException.class
        }
    )
    @CircuitBreaker(
        requestVolumeThreshold = 4,
        failureRatio = 0.5,
        delay = 5000,
        failOn = {UpstreamProxyException.class, AuthServiceException.class},
        skipOn = {
            ProxyConfigurationException.class,
            AuthenticationRequiredException.class,
            AuthenticationDeniedException.class,
            AuthorizationDeniedException.class
        }
    )
    @Fallback(fallbackMethod = "forwardFallback")
    public Response forward(
            ProxyProperties.BackendDefinition backend,
            ProxyRequestContext request,
            String requestBody) {
        Map<String, List<String>> headers = null;
        String outboundRequestBody = null;
        boolean submittedHistoryEmitted = false;
        
        try {
            // Build target URL
            String targetUrl = buildTargetUrl(backend, request);
            log.info("Forwarding {} request to: {}", request.method(), targetUrl);
            
            // Build headers
            headers = buildHeaders(request);
            
            // Get appropriate auth service for this backend and enrich headers
            AuthService authService = authServiceFactory.createAuthService(backend);
            Map<String, String> authHeaders = flattenHeaders(headers);
            authService.enrichHeaders(backend, request, authHeaders, requestBody);
            outboundRequestBody = authService.transformRequestBody(request, authHeaders, requestBody);
            headers = mergeAuthHeaders(headers, authHeaders);
            logBackendHeaders(backend, targetUrl, headers);
            emitHistory(
                backend,
                request,
                requestBody,
                headers,
                outboundRequestBody,
                HistoryStatus.SUBMITTED,
                null,
                null,
                false
            );
            submittedHistoryEmitted = true;
            
            // Build HTTP request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(backend.timeout());
            
            // Add headers
            headers.forEach((name, values) -> values.forEach(value -> requestBuilder.header(name, value)));
            
            // Set method and body
            HttpRequest.BodyPublisher bodyPublisher = outboundRequestBody != null && !outboundRequestBody.isEmpty()
                ? HttpRequest.BodyPublishers.ofString(outboundRequestBody)
                : HttpRequest.BodyPublishers.noBody();
            
            requestBuilder.method(request.method(), bodyPublisher);
            
            // Forward request
            HttpResponse<byte[]> response = getHttpClient(backend).send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofByteArray()
            );
            
            log.info("Received response: {} from {}", response.statusCode(), targetUrl);

            DecodedResponse decodedResponse = decodeResponse(response);
            emitHistory(
                backend,
                request,
                requestBody,
                headers,
                outboundRequestBody,
                statusFrom(response.statusCode()),
                response.statusCode(),
                new String(decodedResponse.body(), StandardCharsets.UTF_8),
                true
            );
            
            // Build JAX-RS response
            Response.ResponseBuilder responseBuilder = Response.status(response.statusCode());
            
            // Copy response headers
            Set<String> blockedResponseHeaders = blockedResponseHeaders(backend);
            response.headers().map().forEach((name, values) -> {
                if (blockedResponseHeaders.contains(name.toLowerCase(Locale.ROOT))) {
                    return;
                }
                if (decodedResponse.decompressed() && "content-encoding".equalsIgnoreCase(name)) {
                    return;
                }
                values.forEach(value -> responseBuilder.header(name, value));
            });
            
            // Set body
            responseBuilder.entity(decodedResponse.body());
            
            return responseBuilder.build();
            
        } catch (IOException e) {
            emitFailedHistoryAfterSubmission(backend, request, requestBody, headers, outboundRequestBody, submittedHistoryEmitted);
            throw new UpstreamProxyException("Failed to reach backend '%s'".formatted(backend.name()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            emitFailedHistoryAfterSubmission(backend, request, requestBody, headers, outboundRequestBody, submittedHistoryEmitted);
            throw new UpstreamProxyException("Request to backend '%s' was interrupted".formatted(backend.name()), e);
        }
    }

    public Response forwardFallback(
            ProxyProperties.BackendDefinition backend,
            ProxyRequestContext request,
            String requestBody,
            Throwable failure) {
        return failureMapper.toResponse(backend.name(), failure, "Failed to forward request to backend '%s'");
    }

    private void emitHistory(
        ProxyProperties.BackendDefinition backend,
        ProxyRequestContext request,
        String requestBody,
        Map<String, List<String>> headers,
        String outboundRequestBody,
        HistoryStatus status,
        Integer backendStatusCode,
        String backendResponseBody,
        boolean postBackendCall) {
        if (historyService == null) {
            return;
        }
        try {
            historyService.emit(
                backend,
                request,
                requestBody,
                headers,
                outboundRequestBody,
                status,
                backendStatusCode,
                backendResponseBody
            );
        } catch (RuntimeException e) {
            if (postBackendCall) {
                throw new ProxyConfigurationException(
                    "Post-backend history emission failed for backend '%s'".formatted(backend.name()), e);
            }
            throw e;
        }
    }

    private void emitFailedHistoryAfterSubmission(
        ProxyProperties.BackendDefinition backend,
        ProxyRequestContext request,
        String requestBody,
        Map<String, List<String>> headers,
        String outboundRequestBody,
        boolean submittedHistoryEmitted) {
        if (!submittedHistoryEmitted) {
            return;
        }
        emitHistory(
            backend,
            request,
            requestBody,
            headers,
            outboundRequestBody,
            HistoryStatus.FAILED,
            null,
            null,
            true
        );
    }

    private HistoryStatus statusFrom(int backendStatusCode) {
        return backendStatusCode >= 200 && backendStatusCode < 400
            ? HistoryStatus.FULFILLED
            : HistoryStatus.FAILED;
    }
    
    /**
     * Build target URL for backend
     */
    private String buildTargetUrl(ProxyProperties.BackendDefinition backend, ProxyRequestContext request) {
        String requestURI = request.requestUri();
        
        // Remove the /api/v1/{backendName} prefix
        String prefix = "/api/v1/" + backend.name();
        String path = requestURI.startsWith(prefix) 
            ? requestURI.substring(prefix.length()) 
            : requestURI;
        
        // Build full URL
        String targetUrl = backend.baseUrl() + backend.path() + path;
        
        // Add query string if present
        String queryString = request.queryString();
        if (queryString != null && !queryString.isEmpty()) {
            targetUrl += "?" + queryString;
        }
        
        return targetUrl;
    }
    
    /**
     * Build HTTP headers from request
     */
    private Map<String, List<String>> buildHeaders(ProxyRequestContext request) {
        Map<String, List<String>> headers = new HashMap<>(request.headers());
        headers.entrySet().removeIf(entry ->
            HOP_BY_HOP_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT))
                || NON_FORWARDED_REQUEST_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT)));
        return headers;
    }

    static Map<String, String> flattenHeaders(Map<String, List<String>> headers) {
        Map<String, String> flattened = new LinkedHashMap<>();
        headers.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            if (value == null || value.isEmpty()) {
                return;
            }
            flattened.put(key.toLowerCase(Locale.ROOT), value.getLast());
        });
        return flattened;
    }

    static Map<String, List<String>> mergeAuthHeaders(
        Map<String, List<String>> originalHeaders,
        Map<String, String> authHeaders) {
        Map<String, List<String>> merged = new LinkedHashMap<>();
        originalHeaders.forEach((key, values) -> merged.put(key.toLowerCase(Locale.ROOT), new ArrayList<>(values)));
        authHeaders.forEach((key, value) -> {
            if (key == null) {
                return;
            }
            merged.put(key.toLowerCase(Locale.ROOT), new ArrayList<>(List.of(value)));
        });
        return merged;
    }

    private HttpClient getHttpClient(ProxyProperties.BackendDefinition backend) {
        String clientKey = clientKey(backend);
        return httpClients.computeIfAbsent(clientKey, ignored -> buildHttpClient(backend));
    }

    private HttpClient buildHttpClient(ProxyProperties.BackendDefinition backend) {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .version(resolveHttpVersion(backend));
        tlsContextFactory.createBackendSslContext(backend).ifPresent(builder::sslContext);
        createProxySelector(backend).ifPresent(builder::proxy);
        return builder.build();
    }

    static String clientKey(ProxyProperties.BackendDefinition backend) {
        String tlsProfile = backend.tlsProfile().orElse("__default__");
        String httpVersion = backend.httpVersion();
        String proxyKey = backend.proxy()
            .map(proxy -> "%s:%d:%s".formatted(
                proxy.host(),
                proxy.port(),
                String.join(",", proxy.nonProxyHosts())))
            .orElse("__no_proxy__");
        return tlsProfile + "|" + httpVersion + "|" + proxyKey;
    }

    static Optional<ProxySelector> createProxySelector(ProxyProperties.BackendDefinition backend) {
        return backend.proxy().map(BackendProxySelector::new);
    }

    static DecodedResponse decodeResponse(HttpResponse<byte[]> response) throws IOException {
        byte[] body = response.body() == null ? new byte[0] : response.body();
        String contentEncoding = response.headers().firstValue("Content-Encoding").orElse("");
        if (!"gzip".equalsIgnoreCase(contentEncoding)) {
            return new DecodedResponse(body, false);
        }
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(body))) {
            return new DecodedResponse(gzipInputStream.readAllBytes(), true);
        }
    }

    static HttpClient.Version resolveHttpVersion(ProxyProperties.BackendDefinition backend) {
        String configured = backend.httpVersion();
        if (configured == null || configured.isBlank()) {
            return HttpClient.Version.HTTP_1_1;
        }

        String normalized = configured.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "http1_1", "http1.1", "http/1.1" -> HttpClient.Version.HTTP_1_1;
            case "http2", "http_2", "http/2" -> HttpClient.Version.HTTP_2;
            default -> throw new ProxyConfigurationException(
                "Unsupported http-version '%s' for backend '%s'".formatted(configured, backend.name()));
        };
    }

    private Set<String> blockedResponseHeaders(ProxyProperties.BackendDefinition backend) {
        Set<String> blocked = new LinkedHashSet<>(HOP_BY_HOP_HEADERS);
        blocked.addAll(SENSITIVE_RESPONSE_HEADERS);

        Map<String, String> securityConfig = backend.securityConfig();
        if (securityConfig == null || securityConfig.isEmpty()) {
            return blocked;
        }

        String bearerHeader = securityConfig.get("bearer-header");
        if (bearerHeader != null && !bearerHeader.isBlank()) {
            blocked.add(bearerHeader.toLowerCase(Locale.ROOT));
        }

        securityConfig.keySet().forEach(key -> {
            if (key == null) {
                return;
            }
            if (key.startsWith("token-headers.")) {
                blocked.add(key.substring("token-headers.".length()).toLowerCase(Locale.ROOT));
            }
            if (key.startsWith("response-headers.")) {
                blocked.add(key.substring("response-headers.".length()).toLowerCase(Locale.ROOT));
            }
            if (key.startsWith("static-headers.")) {
                blocked.add(key.substring("static-headers.".length()).toLowerCase(Locale.ROOT));
            }
        });

        return blocked;
    }

    private void logBackendHeaders(
        ProxyProperties.BackendDefinition backend,
        String targetUrl,
        Map<String, List<String>> headers) {
        if (!log.isDebugEnabled()) {
            return;
        }
        log.debug(
            "Outbound headers for backend {} to {}: {}",
            backend.name(),
            targetUrl,
            sanitizeHeadersForLogging(backend, headers)
        );
    }

    static Map<String, String> sanitizeHeadersForLogging(
        ProxyProperties.BackendDefinition backend,
        Map<String, List<String>> headers) {
        Set<String> sensitiveHeaders = sensitiveRequestHeaders(backend);
        Map<String, String> sanitized = new HashMap<>();
        headers.forEach((name, values) -> {
            if (name == null) {
                return;
            }
            String value = values == null || values.isEmpty() ? null : values.getLast();
            if (sensitiveHeaders.contains(name.toLowerCase(Locale.ROOT))) {
                sanitized.put(name, MASKED_VALUE);
            } else {
                sanitized.put(name, value);
            }
        });
        return sanitized;
    }

    static Set<String> sensitiveRequestHeaders(ProxyProperties.BackendDefinition backend) {
        Set<String> sensitive = new LinkedHashSet<>();
        sensitive.add("authorization");
        sensitive.add("proxy-authorization");
        sensitive.add("cookie");
        sensitive.add("set-cookie");
        sensitive.add("x-glue-token");
        sensitive.add("x-auth-z-token");
        sensitive.add("x-customer-access-token");
        sensitive.add("x-serverless-authorization");

        Map<String, String> securityConfig = backend.securityConfig();
        if (securityConfig == null || securityConfig.isEmpty()) {
            return sensitive;
        }

        String bearerHeader = securityConfig.get("bearer-header");
        if (bearerHeader != null && !bearerHeader.isBlank()) {
            sensitive.add(bearerHeader.toLowerCase(Locale.ROOT));
        }

        securityConfig.keySet().forEach(key -> {
            if (key == null) {
                return;
            }
            if (key.startsWith("token-headers.")) {
                sensitive.add(key.substring("token-headers.".length()).toLowerCase(Locale.ROOT));
            }
            if (key.startsWith("static-headers.")) {
                sensitive.add(key.substring("static-headers.".length()).toLowerCase(Locale.ROOT));
            }
        });

        return sensitive;
    }

    static final class BackendProxySelector extends ProxySelector {

        private final ProxyProperties.ProxyConfig proxyConfig;
        private final java.net.Proxy proxy;

        BackendProxySelector(ProxyProperties.ProxyConfig proxyConfig) {
            this.proxyConfig = Objects.requireNonNull(proxyConfig, "proxyConfig");
            this.proxy = new java.net.Proxy(
                java.net.Proxy.Type.HTTP,
                InetSocketAddress.createUnresolved(proxyConfig.host(), proxyConfig.port())
            );
        }

        @Override
        public List<java.net.Proxy> select(URI uri) {
            if (uri == null) {
                throw new IllegalArgumentException("URI must not be null");
            }
            String host = uri.getHost();
            if (host != null && isNonProxyHost(host)) {
                return List.of(java.net.Proxy.NO_PROXY);
            }
            return List.of(proxy);
        }

        @Override
        public void connectFailed(URI uri, java.net.SocketAddress sa, IOException ioe) {
            log.warn("Proxy connection failed for backend target {} via {}", uri, sa, ioe);
        }

        private boolean isNonProxyHost(String host) {
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            for (String configuredHost : proxyConfig.nonProxyHosts()) {
                if (configuredHost == null || configuredHost.isBlank()) {
                    continue;
                }
                String normalizedPattern = configuredHost.toLowerCase(Locale.ROOT).trim();
                if (normalizedPattern.startsWith("*.")) {
                    String suffix = normalizedPattern.substring(1);
                    if (normalizedHost.endsWith(suffix)) {
                        return true;
                    }
                    continue;
                }
                if (normalizedHost.equals(normalizedPattern)) {
                    return true;
                }
            }
            return false;
        }
    }

    record DecodedResponse(byte[] body, boolean decompressed) {
    }
}
