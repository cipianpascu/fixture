package com.db.olorin.rest.service.auth;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.config.RestConfigurationAdapter;
import com.db.olorin.rest.exception.ProxyConfigurationException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Resolves referenced gateway auth services from the same ConfigurationIF section. */
public final class AuthServiceConfigRegistry {
    public static final String DEFAULT_JWT_SERVICE = "auth";
    private final ProxyProperties properties;
    private final Map<String, String> values;
    private final Map<String, ResolvedAuthServiceConfig> cache = new ConcurrentHashMap<>();

    public AuthServiceConfigRegistry(ProxyProperties properties, RestConfigurationAdapter configuration) {
        this.properties = properties;
        this.values = configuration.values();
    }

    public ResolvedAuthServiceConfig get(String name) {
        return cache.computeIfAbsent(name, this::resolve);
    }

    private ResolvedAuthServiceConfig resolve(String name) {
        String prefix = "gateway." + name + ".";
        if (values.keySet().stream().noneMatch(key -> key.startsWith(prefix))) {
            throw new ProxyConfigurationException("Referenced auth service '%s' is not configured under gateway.%s".formatted(name, name));
        }
        Map<String, String> security = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key.startsWith(prefix + "security-config.")) security.put(key.substring((prefix + "security-config.").length()), value);
            if (key.startsWith(prefix + "securityConfig.")) security.put(key.substring((prefix + "securityConfig.").length()), value);
        });
        return new ResolvedAuthServiceConfig(
            name, bool(prefix + "enabled", true), value(prefix + "service-url", "http://localhost:8081"),
            value(prefix + "session-id-header", "X-Session-Id"), value(prefix + "session-id-cookie", "sessionId"),
            duration(prefix + "timeout", Duration.ofSeconds(5)), optional(prefix + "tls-profile"),
            optional(prefix + "security-type"), Map.copyOf(security),
            new ResolvedAuthCacheConfig(bool(prefix + "cache.enabled", false), duration(prefix + "cache.expiry-skew", Duration.ofSeconds(30)), integer(prefix + "cache.max-size", 10_000)));
    }
    private String value(String key, String fallback) { return values.getOrDefault(key, fallback); }
    private Optional<String> optional(String key) { return Optional.ofNullable(values.get(key)).filter(value -> !value.isBlank()); }
    private boolean bool(String key, boolean fallback) { return optional(key).map(Boolean::parseBoolean).orElse(fallback); }
    private int integer(String key, int fallback) { try { return optional(key).map(Integer::parseInt).orElse(fallback); } catch (NumberFormatException e) { throw new ProxyConfigurationException("Invalid integer " + key, e); } }
    private Duration duration(String key, Duration fallback) {
        String value = values.get(key); if (value == null) return fallback;
        try { return value.matches("\\d+") ? Duration.ofMillis(Long.parseLong(value)) : Duration.parse(value.startsWith("P") ? value : "PT" + value.toUpperCase()); }
        catch (RuntimeException e) { throw new ProxyConfigurationException("Invalid duration " + key, e); }
    }
}
