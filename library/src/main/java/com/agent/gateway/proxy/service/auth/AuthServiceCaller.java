package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.exception.AuthServiceException;
import com.agent.gateway.proxy.exception.AuthenticationDeniedException;
import com.agent.gateway.proxy.exception.AuthorizationDeniedException;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import com.agent.gateway.proxy.service.TlsContextFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

public class AuthServiceCaller {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ResolvedAuthServiceConfig serviceConfig;
    private final CloudRunIdTokenProvider cloudRunIdTokenProvider;
    private final HttpClient httpClient;
    private final String cloudRunAudience;

    public AuthServiceCaller(
        ResolvedAuthServiceConfig serviceConfig,
        TlsContextFactory tlsContextFactory,
        CloudRunIdTokenProvider cloudRunIdTokenProvider) {
        this.serviceConfig = serviceConfig;
        this.cloudRunIdTokenProvider = cloudRunIdTokenProvider;

        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(serviceConfig.timeout())
            .version(HttpClient.Version.HTTP_1_1);
        tlsContextFactory.createServiceSslContext(serviceConfig.tlsProfile()).ifPresent(builder::sslContext);
        this.httpClient = builder.build();
        this.cloudRunAudience = resolveCloudRunAudience();
    }

    public <T> T postJson(String pathOrUrl, Object requestBody, Class<T> responseType) {
        try {
            String requestJson = OBJECT_MAPPER.writeValueAsString(requestBody);
            return send(pathOrUrl, requestJson, "application/json", responseType);
        } catch (AuthServiceException e) {
            throw e;
        } catch (IOException e) {
            throw new AuthServiceException("Failed to call auth service '%s'".formatted(serviceConfig.name()), e);
        }
    }

    public <T> T postForm(String pathOrUrl, Map<String, String> parameters, Class<T> responseType) {
        String formBody = parameters.entrySet().stream()
            .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
            .collect(Collectors.joining("&"));
        return send(pathOrUrl, formBody, "application/x-www-form-urlencoded", responseType);
    }

    private <T> T send(String pathOrUrl, String body, String contentType, Class<T> responseType) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(resolveUrl(pathOrUrl)))
                .timeout(serviceConfig.timeout())
                .header("Accept", "application/json")
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body));

            if (cloudRunAudience != null) {
                builder.header(
                    "X-Serverless-Authorization",
                    "Bearer " + cloudRunIdTokenProvider.getIdToken(cloudRunAudience)
                );
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                throw new AuthenticationDeniedException(describeFailure(response));
            }
            if (response.statusCode() == 403) {
                throw new AuthorizationDeniedException(describeFailure(response));
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new AuthServiceException(describeFailure(response));
            }

            if (responseType == String.class) {
                return responseType.cast(response.body());
            }

            if (response.body() == null || response.body().isBlank()) {
                throw new AuthServiceException("Auth service returned an empty response body");
            }

            if (responseType == JsonNode.class) {
                return responseType.cast(OBJECT_MAPPER.readTree(response.body()));
            }
            return OBJECT_MAPPER.readValue(response.body(), responseType);
        } catch (AuthServiceException e) {
            throw e;
        } catch (IOException e) {
            throw new AuthServiceException("Failed to call auth service '%s'".formatted(serviceConfig.name()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthServiceException(
                "Auth service '%s' request was interrupted".formatted(serviceConfig.name()), e);
        }
    }

    private String resolveUrl(String pathOrUrl) {
        if (pathOrUrl == null || pathOrUrl.isBlank()) {
            throw new AuthServiceException(
                "Auth service '%s' path must not be blank".formatted(serviceConfig.name()));
        }

        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) {
            return pathOrUrl;
        }

        String serviceUrl = serviceConfig.serviceUrl();
        boolean serviceEndsWithSlash = serviceUrl.endsWith("/");
        boolean pathStartsWithSlash = pathOrUrl.startsWith("/");
        if (serviceEndsWithSlash && pathStartsWithSlash) {
            return serviceUrl.substring(0, serviceUrl.length() - 1) + pathOrUrl;
        }
        if (!serviceEndsWithSlash && !pathStartsWithSlash) {
            return serviceUrl + "/" + pathOrUrl;
        }
        return serviceUrl + pathOrUrl;
    }

    private String resolveCloudRunAudience() {
        String authSecurityType = serviceConfig.securityType().orElse("none");
        if ("cloudrun".equalsIgnoreCase(authSecurityType) || "cloud_run".equalsIgnoreCase(authSecurityType)) {
            return CloudRunAudienceResolver.resolveAudience(
                serviceConfig.serviceUrl(),
                serviceConfig.securityConfig(),
                "auth service '%s'".formatted(serviceConfig.name())
            );
        }
        if (authSecurityType.isBlank() || "none".equalsIgnoreCase(authSecurityType)) {
            return null;
        }
        throw new ProxyConfigurationException(
            "Unsupported security-type '%s' for auth service '%s'".formatted(
                authSecurityType, serviceConfig.name()));
    }

    private String describeFailure(HttpResponse<String> response) {
        String responseBody = response.body();
        if (responseBody == null || responseBody.isBlank()) {
            return "Auth service '%s' returned HTTP %d".formatted(serviceConfig.name(), response.statusCode());
        }
        return "Auth service '%s' returned HTTP %d: %s".formatted(
            serviceConfig.name(), response.statusCode(), responseBody);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
