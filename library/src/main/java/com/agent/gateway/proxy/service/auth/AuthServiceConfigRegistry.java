package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AuthServiceConfigRegistry {

    private final Map<String, ResolvedAuthServiceConfig> configs = new ConcurrentHashMap<>();
    private volatile boolean initialized;

    @Inject
    ProxyProperties proxyProperties;

    @Inject
    Config config;

    void onStart(@Observes StartupEvent event) {
        ensureLoaded();
    }

    public ResolvedAuthServiceConfig get(String serviceName) {
        ensureLoaded();
        ResolvedAuthServiceConfig config = configs.get(serviceName);
        if (config == null) {
            throw new ProxyConfigurationException(
                "Referenced auth service '%s' is not configured under gateway.%s".formatted(serviceName, serviceName)
            );
        }
        return config;
    }

    void ensureLoaded() {
        if (initialized) {
            return;
        }
        synchronized (this) {
            if (initialized) {
                return;
            }
            loadReferencedServices();
            initialized = true;
        }
    }

    private void loadReferencedServices() {
        Set<String> referencedServices = new LinkedHashSet<>();
        for (ProxyProperties.BackendDefinition backend : proxyProperties.backends()) {
            backend.authRequest().map(ProxyProperties.AuthRequestConfig::service).ifPresent(referencedServices::add);
            backend.authzRequest().map(ProxyProperties.AuthzRequestConfig::service).ifPresent(referencedServices::add);
        }
        for (String serviceName : referencedServices) {
            configs.put(serviceName, resolveServiceConfig(serviceName));
        }
    }

    private ResolvedAuthServiceConfig resolveServiceConfig(String serviceName) {
        String prefix = "gateway." + serviceName + ".";
        boolean configured = false;
        for (String propertyName : config.getPropertyNames()) {
            if (propertyName.startsWith(prefix)) {
                configured = true;
                break;
            }
        }
        if (!configured) {
            throw new ProxyConfigurationException(
                "Referenced auth service '%s' is not configured under gateway.%s".formatted(serviceName, serviceName)
            );
        }
        String serviceUrl = config.getOptionalValue(prefix + "service-url", String.class)
            .orElse("http://localhost:8081");
        Duration timeout = config.getOptionalValue(prefix + "timeout", Duration.class)
            .orElse(Duration.ofSeconds(5));

        Map<String, String> securityConfig = new LinkedHashMap<>();
        String securityPrefix = prefix + "security-config.";
        for (String propertyName : config.getPropertyNames()) {
            if (propertyName.startsWith(securityPrefix)) {
                String suffix = propertyName.substring(securityPrefix.length());
                config.getOptionalValue(propertyName, String.class)
                    .ifPresent(value -> securityConfig.put(suffix, value));
            }
        }

        return new ResolvedAuthServiceConfig(
            serviceName,
            config.getOptionalValue(prefix + "enabled", Boolean.class).orElse(true),
            serviceUrl,
            config.getOptionalValue(prefix + "session-id-header", String.class).orElse("X-Session-Id"),
            config.getOptionalValue(prefix + "session-id-cookie", String.class).orElse("sessionId"),
            timeout,
            config.getOptionalValue(prefix + "tls-profile", String.class),
            config.getOptionalValue(prefix + "security-type", String.class),
            Map.copyOf(securityConfig)
        );
    }
}
