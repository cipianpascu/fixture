package com.agent.gateway.proxy.history;

import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import com.agent.gateway.proxy.exception.UpstreamProxyException;
import com.agent.gateway.proxy.service.TlsContextFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class GcpPubSubHistoryPublisher implements HistoryPublisher {

    private static final String PROVIDER = "gcp-pubsub";
    private static final String PUBSUB_SCOPE = "https://www.googleapis.com/auth/pubsub";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    TlsContextFactory tlsContextFactory;

    @Override
    public boolean supports(String provider) {
        return PROVIDER.equalsIgnoreCase(provider) || "pubsub".equalsIgnoreCase(provider);
    }

    @Override
    public void publish(HistoryPublishRequest request) {
        String projectId = request.projectId()
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new ProxyConfigurationException(
                "History Pub/Sub project-id is required for backend '%s'".formatted(request.backend().name())));
        String topic = request.topic()
            .filter(value -> !value.isBlank())
            .orElseThrow(() -> new ProxyConfigurationException(
                "History Pub/Sub topic is required for backend '%s'".formatted(request.backend().name())));

        try {
            String payloadJson = OBJECT_MAPPER.writeValueAsString(request.payload());
            String body = OBJECT_MAPPER.writeValueAsString(Map.of(
                "messages",
                List.of(Map.of(
                    "data", Base64.getEncoder().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8)),
                    "attributes", request.attributes()
                ))
            ));

            HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(publishUrl(request.serviceUrl(), projectId, topic)))
                .timeout(request.timeout())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + accessToken())
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));

            HttpResponse<String> response = httpClient(request.timeout(), request.tlsProfile())
                .send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new UpstreamProxyException(
                    "History Pub/Sub publish for backend '%s' returned HTTP %d: %s"
                        .formatted(request.backend().name(), response.statusCode(), response.body()));
            }
        } catch (ProxyConfigurationException | UpstreamProxyException e) {
            throw e;
        } catch (IOException e) {
            throw new UpstreamProxyException(
                "Failed to publish history message for backend '%s'".formatted(request.backend().name()), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamProxyException(
                "History publish for backend '%s' was interrupted".formatted(request.backend().name()), e);
        }
    }

    private HttpClient httpClient(Duration timeout, java.util.Optional<String> tlsProfile) {
        HttpClient.Builder builder = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .version(HttpClient.Version.HTTP_1_1);
        tlsContextFactory.createServiceSslContext(tlsProfile).ifPresent(builder::sslContext);
        return builder.build();
    }

    private String accessToken() throws IOException {
        GoogleCredentials credentials = GoogleCredentials.getApplicationDefault()
            .createScoped(List.of(PUBSUB_SCOPE));
        credentials.refreshIfExpired();
        AccessToken accessToken = credentials.getAccessToken();
        if (accessToken == null || accessToken.getTokenValue() == null || accessToken.getTokenValue().isBlank()) {
            throw new ProxyConfigurationException("Google credentials did not provide a Pub/Sub access token");
        }
        return accessToken.getTokenValue();
    }

    private String publishUrl(String serviceUrl, String projectId, String topic) {
        String baseUrl = serviceUrl.endsWith("/") ? serviceUrl.substring(0, serviceUrl.length() - 1) : serviceUrl;
        return "%s/v1/projects/%s/topics/%s:publish".formatted(
            baseUrl,
            encodePath(projectId),
            encodePath(topic)
        );
    }

    private String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
