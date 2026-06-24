package com.agent.gateway.proxy.history;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
@Slf4j
public class HistoryService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Inject
    ProxyProperties proxyProperties;

    @Inject
    Instance<HistoryPayloadMapper> payloadMappers;

    @Inject
    Instance<HistoryPublisher> publishers;

    Clock clock = Clock.systemUTC();
    Map<String, String> manifestAttributes;
    ExecutorService executor;

    private final ThreadFactory threadFactory = runnable -> {
        Thread thread = new Thread(runnable, "bfa-history-publisher");
        thread.setDaemon(true);
        return thread;
    };

    public void emit(
        ProxyProperties.BackendDefinition backend,
        ProxyRequestContext request,
        String incomingBody,
        Map<String, List<String>> outboundHeaders,
        String outboundBody) {
        if (!shouldEmit(backend, request)) {
            return;
        }

        EffectiveHistoryConfig config = effectiveConfig(backend);
        Map<String, String> additionalProperties = resolveAdditionalProperties(
            backend.history()
                .map(ProxyProperties.BackendHistoryConfig::additionalProperties)
                .orElse(Map.of()),
            request,
            outboundHeaders,
            backend.history()
                .flatMap(ProxyProperties.BackendHistoryConfig::tokenHeader)
                .orElse("authorization")
        );
        HistoryRequestContext context = new HistoryRequestContext(
            backend,
            request,
            incomingBody,
            outboundHeaders == null ? Map.of() : Map.copyOf(outboundHeaders),
            outboundBody,
            additionalProperties
        );

        Object payload = buildPayload(context, config);
        if (payload == null) {
            log.debug("No history payload mapper produced a payload for backend '{}'", backend.name());
            return;
        }

        HistoryPublishRequest publishRequest = new HistoryPublishRequest(
            backend,
            config.provider(),
            config.serviceUrl(),
            config.projectId(),
            config.topic(),
            config.timeout(),
            config.tlsProfile(),
            additionalProperties,
            payload
        );

        publish(publishRequest, config);
    }

    public Map<String, String> resolveAdditionalProperties(
        Map<String, String> configuredProperties,
        ProxyRequestContext request,
        Map<String, List<String>> outboundHeaders) {
        return resolveAdditionalProperties(configuredProperties, request, outboundHeaders, "authorization");
    }

    public Map<String, String> resolveAdditionalProperties(
        Map<String, String> configuredProperties,
        ProxyRequestContext request,
        Map<String, List<String>> outboundHeaders,
        String tokenHeader) {
        if (configuredProperties == null || configuredProperties.isEmpty()) {
            return Map.of();
        }

        Map<String, String> resolved = new LinkedHashMap<>();
        Optional<JsonNode> tokenPayload = requiresTokenPayload(configuredProperties)
            ? tokenFromHeader(tokenHeader, request, outboundHeaders).flatMap(this::decodeJwtPayload)
            : Optional.empty();
        configuredProperties.forEach((name, source) -> resolveAdditionalProperty(
                source,
                request,
                outboundHeaders,
                tokenPayload
            )
            .ifPresent(value -> resolved.put(name, value)));
        return Map.copyOf(resolved);
    }

    private boolean requiresTokenPayload(Map<String, String> configuredProperties) {
        return configuredProperties.values().stream()
            .anyMatch(source -> source != null && source.startsWith("token:"));
    }

    private boolean shouldEmit(ProxyProperties.BackendDefinition backend, ProxyRequestContext request) {
        if (!proxyProperties.history().enabled()) {
            return false;
        }
        boolean backendEnabled = backend.history()
            .flatMap(ProxyProperties.BackendHistoryConfig::enabled)
            .orElse(true);
        if (!backendEnabled) {
            return false;
        }
        String method = request.method();
        if (method == null || method.isBlank()) {
            return false;
        }
        return proxyProperties.history().methods().stream()
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.toUpperCase(Locale.ROOT))
            .anyMatch(value -> value.equals(method.toUpperCase(Locale.ROOT)));
    }

    private Object buildPayload(HistoryRequestContext context, EffectiveHistoryConfig config) {
        try {
            for (HistoryPayloadMapper mapper : payloadMappers) {
                if (!mapper.supports(context.backend())) {
                    continue;
                }
                Object payload = mapper.map(context);
                if (payload != null) {
                    return payload;
                }
            }
            return null;
        } catch (RuntimeException e) {
            if (config.failOpen()) {
                log.warn(
                    "History payload mapping failed for backend '{}'; continuing because fail-open=true",
                    context.backend().name(),
                    e
                );
                return null;
            }
            throw e;
        }
    }

    private void publish(HistoryPublishRequest request, EffectiveHistoryConfig config) {
        HistoryPublisher publisher = resolvePublisher(request.provider());
        Runnable publishTask = () -> {
            try {
                publisher.publish(request);
            } catch (RuntimeException e) {
                if (config.confirmed() && !config.failOpen()) {
                    throw e;
                }
                log.warn(
                    "History publish failed for backend '{}'; continuing because delivery-mode={} fail-open={}",
                    request.backend().name(),
                    config.deliveryMode(),
                    config.failOpen(),
                    e
                );
            }
        };

        if (config.confirmed()) {
            publishTask.run();
            return;
        }

        try {
            executor().execute(publishTask);
        } catch (RejectedExecutionException e) {
            if (config.failOpen()) {
                log.warn(
                    "History publish task submission failed for backend '{}'; continuing because fail-open=true",
                    request.backend().name(),
                    e
                );
                return;
            }
            throw e;
        }
    }

    private ExecutorService executor() {
        if (executor != null) {
            return executor;
        }
        synchronized (this) {
            if (executor != null) {
                return executor;
            }
            executor = buildExecutor(proxyProperties.history().executor());
            return executor;
        }
    }

    private ExecutorService buildExecutor(Optional<ProxyProperties.HistoryExecutorConfig> configuredExecutor) {
        ProxyProperties.HistoryExecutorConfig config = configuredExecutor.orElse(defaultExecutorConfig());
        int coreThreads = config.coreThreads();
        int maxThreads = config.maxThreads();
        int queueCapacity = config.queueCapacity();
        if (coreThreads < 0) {
            throw new ProxyConfigurationException("gateway.history.executor.core-threads must be >= 0");
        }
        if (maxThreads < 1) {
            throw new ProxyConfigurationException("gateway.history.executor.max-threads must be >= 1");
        }
        if (coreThreads > maxThreads) {
            throw new ProxyConfigurationException(
                "gateway.history.executor.core-threads must be <= max-threads");
        }
        if (queueCapacity < 0) {
            throw new ProxyConfigurationException("gateway.history.executor.queue-capacity must be >= 0");
        }
        return new ThreadPoolExecutor(
            coreThreads,
            maxThreads,
            30,
            TimeUnit.SECONDS,
            queueCapacity == 0 ? new SynchronousQueue<>() : new ArrayBlockingQueue<>(queueCapacity),
            threadFactory,
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    private ProxyProperties.HistoryExecutorConfig defaultExecutorConfig() {
        return new ProxyProperties.HistoryExecutorConfig() {
            @Override
            public int coreThreads() {
                return 2;
            }

            @Override
            public int maxThreads() {
                return 8;
            }

            @Override
            public int queueCapacity() {
                return 1000;
            }
        };
    }

    private HistoryPublisher resolvePublisher(String provider) {
        for (HistoryPublisher publisher : publishers) {
            if (publisher.supports(provider)) {
                return publisher;
            }
        }
        throw new ProxyConfigurationException("No history publisher configured for provider '%s'".formatted(provider));
    }

    private Optional<String> resolveAdditionalProperty(
        String source,
        ProxyRequestContext request,
        Map<String, List<String>> outboundHeaders,
        Optional<JsonNode> tokenPayload) {
        if (source == null || source.isBlank()) {
            return Optional.empty();
        }
        if (source.startsWith("literal:")) {
            return Optional.of(source.substring("literal:".length()));
        }
        if (source.startsWith("header:")) {
            String headerName = source.substring("header:".length());
            return firstHeaderValue(request.header(headerName), outboundHeaders, headerName);
        }
        if (source.startsWith("cookie:")) {
            return Optional.ofNullable(request.cookie(source.substring("cookie:".length())));
        }
        if (source.startsWith("token:")) {
            return resolveTokenClaim(source.substring("token:".length()), tokenPayload);
        }
        if (source.startsWith("date:")) {
            return Optional.of(resolveDate(source.substring("date:".length())));
        }
        if (source.startsWith("manifest:")) {
            return resolveManifestAttribute(source.substring("manifest:".length()));
        }
        throw new ProxyConfigurationException("Unsupported history additional property source '%s'".formatted(source));
    }

    private Optional<String> resolveManifestAttribute(String attributeName) {
        String normalizedAttributeName = attributeName == null ? "" : attributeName.trim();
        if (normalizedAttributeName.isBlank()) {
            throw new ProxyConfigurationException("History manifest additional property attribute must not be blank");
        }
        return Optional.ofNullable(loadManifestAttributes().get(normalizedAttributeName))
            .filter(value -> !value.isBlank());
    }

    private Map<String, String> loadManifestAttributes() {
        if (manifestAttributes != null) {
            return manifestAttributes;
        }
        synchronized (this) {
            if (manifestAttributes != null) {
                return manifestAttributes;
            }
            manifestAttributes = readManifestAttributes();
            return manifestAttributes;
        }
    }

    private Map<String, String> readManifestAttributes() {
        Map<String, String> attributes = new LinkedHashMap<>();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = HistoryService.class.getClassLoader();
        }
        try {
            java.util.Enumeration<URL> manifests = classLoader.getResources("META-INF/MANIFEST.MF");
            while (manifests.hasMoreElements()) {
                URL manifestUrl = manifests.nextElement();
                try (java.io.InputStream inputStream = manifestUrl.openStream()) {
                    Attributes mainAttributes = new Manifest(inputStream).getMainAttributes();
                    for (Map.Entry<Object, Object> entry : mainAttributes.entrySet()) {
                        attributes.putIfAbsent(entry.getKey().toString(), entry.getValue().toString());
                    }
                }
            }
        } catch (IOException e) {
            log.debug("Unable to read classpath manifests for history additional property extraction", e);
        }
        return Map.copyOf(attributes);
    }

    private String resolveDate(String format) {
        String normalizedFormat = format == null ? "" : format.trim();
        if (normalizedFormat.isBlank()) {
            throw new ProxyConfigurationException("History date additional property format must not be blank");
        }
        if ("timestamp".equalsIgnoreCase(normalizedFormat)) {
            return String.valueOf(clock.instant().toEpochMilli());
        }
        if ("epoch-second".equalsIgnoreCase(normalizedFormat) || "epoch_seconds".equalsIgnoreCase(normalizedFormat)) {
            return String.valueOf(clock.instant().getEpochSecond());
        }
        if ("iso-instant".equalsIgnoreCase(normalizedFormat) || "iso_instant".equalsIgnoreCase(normalizedFormat)) {
            return DateTimeFormatter.ISO_INSTANT.format(clock.instant());
        }
        try {
            return DateTimeFormatter.ofPattern(normalizedFormat)
                .withZone(ZoneOffset.UTC)
                .format(clock.instant());
        } catch (IllegalArgumentException | DateTimeException e) {
            throw new ProxyConfigurationException(
                "Unsupported history date additional property format '%s'".formatted(normalizedFormat), e);
        }
    }

    private Optional<String> firstHeaderValue(
        String incomingValue,
        Map<String, List<String>> outboundHeaders,
        String headerName) {
        if (incomingValue != null && !incomingValue.isBlank()) {
            return Optional.of(incomingValue);
        }
        if (outboundHeaders == null || headerName == null) {
            return Optional.empty();
        }
        List<String> values = outboundHeaders.get(headerName.toLowerCase(Locale.ROOT));
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(values.getFirst()).filter(value -> !value.isBlank());
    }

    private Optional<String> resolveTokenClaim(
        String claimExpression,
        Optional<JsonNode> tokenPayload) {
        if (tokenPayload.isEmpty()) {
            return Optional.empty();
        }
        for (String claimName : claimExpression.split("\\|")) {
            String normalizedClaim = claimName.trim();
            if (normalizedClaim.isEmpty()) {
                continue;
            }
            JsonNode claim = tokenPayload.get().get(normalizedClaim);
            Optional<String> value = jsonValue(claim);
            if (value.isPresent()) {
                return value;
            }
        }
        return Optional.empty();
    }

    private Optional<String> tokenFromHeader(
        String headerName,
        ProxyRequestContext request,
        Map<String, List<String>> outboundHeaders) {
        String resolvedHeaderName = headerName == null || headerName.isBlank()
            ? "authorization"
            : headerName;
        return firstHeaderValue(request.header(resolvedHeaderName), outboundHeaders, resolvedHeaderName)
            .map(value -> {
                String trimmed = value.trim();
                if (trimmed.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) {
                    return trimmed.substring("Bearer ".length()).trim();
                }
                return trimmed;
            })
            .filter(value -> !value.isBlank());
    }

    private Optional<JsonNode> decodeJwtPayload(String token) {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(parts[1]);
            return Optional.of(OBJECT_MAPPER.readTree(new String(decoded, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            log.debug("Unable to decode JWT payload for history additional property extraction", e);
            return Optional.empty();
        }
    }

    private Optional<String> jsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        if (node.isTextual()) {
            return Optional.of(node.asText()).filter(value -> !value.isBlank());
        }
        if (node.isNumber() || node.isBoolean()) {
            return Optional.of(node.asText());
        }
        if (node.isArray()) {
            List<String> values = java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .flatMap(value -> jsonValue(value).stream())
                .toList();
            return values.isEmpty() ? Optional.empty() : Optional.of(String.join(",", values));
        }
        return Optional.of(node.toString());
    }

    private EffectiveHistoryConfig effectiveConfig(ProxyProperties.BackendDefinition backend) {
        ProxyProperties.HistoryConfig global = proxyProperties.history();
        Optional<ProxyProperties.BackendHistoryConfig> backendConfig = backend.history();
        return new EffectiveHistoryConfig(
            backendConfig.flatMap(ProxyProperties.BackendHistoryConfig::provider).orElse(global.provider()),
            backendConfig.flatMap(ProxyProperties.BackendHistoryConfig::deliveryMode).orElse(global.deliveryMode()),
            backendConfig.flatMap(ProxyProperties.BackendHistoryConfig::failOpen).orElse(global.failOpen()),
            backendConfig.flatMap(ProxyProperties.BackendHistoryConfig::serviceUrl).orElse(global.serviceUrl()),
            backendConfig.flatMap(ProxyProperties.BackendHistoryConfig::projectId).or(global::projectId),
            backendConfig.flatMap(ProxyProperties.BackendHistoryConfig::topic).or(global::topic),
            global.timeout(),
            global.tlsProfile()
        );
    }

    void onShutdown(@Observes ShutdownEvent event) {
        if (executor != null) {
            executor.shutdown();
        }
    }

    private record EffectiveHistoryConfig(
        String provider,
        String deliveryMode,
        boolean failOpen,
        String serviceUrl,
        Optional<String> projectId,
        Optional<String> topic,
        java.time.Duration timeout,
        Optional<String> tlsProfile
    ) {
        boolean confirmed() {
            return "confirmed".equalsIgnoreCase(deliveryMode);
        }
    }
}
