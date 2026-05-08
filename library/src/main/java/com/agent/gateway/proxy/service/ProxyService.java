package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.AuthServiceException;
import com.agent.gateway.proxy.exception.AuthenticationRequiredException;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import com.agent.gateway.proxy.exception.UpstreamProxyException;
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
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.Map;

/**
 * Proxy Service - Lightweight Request Forwarding (Quarkus)
 * 
 * NO database dependencies - pure HTTP proxying.
 */
@ApplicationScoped
@Slf4j
public class ProxyService {

    private static final Set<String> HOP_BY_HOP_HEADERS = Set.of(
        "connection",
        "content-length",
        "expect",
        "host",
        "transfer-encoding",
        "upgrade"
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
    
    @Inject
    AuthServiceFactory authServiceFactory;

    @Inject
    TlsContextFactory tlsContextFactory;

    private final Map<String, HttpClient> httpClients = new ConcurrentHashMap<>();
    
    /**
     * Forward request to backend
     */
    @Retry(
        maxRetries = 2,
        delay = 200,
        retryOn = {UpstreamProxyException.class, AuthServiceException.class},
        abortOn = {ProxyConfigurationException.class, AuthenticationRequiredException.class}
    )
    @CircuitBreaker(
        requestVolumeThreshold = 4,
        failureRatio = 0.5,
        delay = 5000,
        failOn = {UpstreamProxyException.class, AuthServiceException.class},
        skipOn = {ProxyConfigurationException.class, AuthenticationRequiredException.class}
    )
    @Fallback(fallbackMethod = "forwardFallback")
    public Response forward(
            ProxyProperties.BackendDefinition backend,
            ProxyRequestContext request,
            String requestBody) {
        
        try {
            // Build target URL
            String targetUrl = buildTargetUrl(backend, request);
            log.info("Forwarding {} request to: {}", request.method(), targetUrl);
            
            // Build headers
            Map<String, String> headers = buildHeaders(request);
            
            // Get appropriate auth service for this backend and enrich headers
            AuthService authService = authServiceFactory.createAuthService(backend);
            authService.enrichHeaders(request, headers, requestBody);
            
            // Build HTTP request
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(backend.timeout());
            
            // Add headers
            headers.forEach(requestBuilder::header);
            
            // Set method and body
            HttpRequest.BodyPublisher bodyPublisher = requestBody != null && !requestBody.isEmpty()
                ? HttpRequest.BodyPublishers.ofString(requestBody)
                : HttpRequest.BodyPublishers.noBody();
            
            requestBuilder.method(request.method(), bodyPublisher);
            
            // Forward request
            HttpResponse<String> response = getHttpClient(backend).send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
            );
            
            log.info("Received response: {} from {}", response.statusCode(), targetUrl);
            
            // Build JAX-RS response
            Response.ResponseBuilder responseBuilder = Response.status(response.statusCode());
            
            // Copy response headers
            Set<String> blockedResponseHeaders = blockedResponseHeaders(backend);
            response.headers().map().forEach((name, values) -> {
                if (blockedResponseHeaders.contains(name.toLowerCase(Locale.ROOT))) {
                    return;
                }
                values.forEach(value -> responseBuilder.header(name, value));
            });
            
            // Set body
            responseBuilder.entity(response.body());
            
            return responseBuilder.build();
            
        } catch (IOException e) {
            throw new UpstreamProxyException("Failed to reach backend '%s'".formatted(backend.name()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamProxyException("Request to backend '%s' was interrupted".formatted(backend.name()), e);
        }
    }

    public Response forwardFallback(
            ProxyProperties.BackendDefinition backend,
            ProxyRequestContext request,
            String requestBody,
            Throwable failure) {
        log.error("Proxy forwarding failed for backend {} after fault-tolerance handling", backend.name(), failure);

        if (failure instanceof ProxyConfigurationException configurationException) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", configurationException.getMessage()))
                .build();
        }

        if (failure instanceof AuthenticationRequiredException authenticationRequiredException) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", authenticationRequiredException.getMessage()))
                .build();
        }

        if (failure instanceof CircuitBreakerOpenException) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("error", "Backend '%s' is temporarily unavailable".formatted(backend.name())))
                .build();
        }

        return Response.status(Response.Status.BAD_GATEWAY)
            .entity(Map.of("error", "Failed to forward request to backend '%s'".formatted(backend.name())))
            .build();
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
    private Map<String, String> buildHeaders(ProxyRequestContext request) {
        Map<String, String> headers = new HashMap<>(request.headers());
        headers.entrySet().removeIf(entry ->
            HOP_BY_HOP_HEADERS.contains(entry.getKey().toLowerCase(Locale.ROOT)));
        return headers;
    }

    private HttpClient getHttpClient(ProxyProperties.BackendDefinition backend) {
        String clientKey = backend.tlsProfile().orElse("__default__");
        return httpClients.computeIfAbsent(clientKey, ignored -> buildHttpClient(backend));
    }

    private HttpClient buildHttpClient(ProxyProperties.BackendDefinition backend) {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30));
        tlsContextFactory.createBackendSslContext(backend).ifPresent(builder::sslContext);
        return builder.build();
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
}
