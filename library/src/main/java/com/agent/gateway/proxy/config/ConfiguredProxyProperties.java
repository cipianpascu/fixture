package com.agent.gateway.proxy.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithParentName;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ConfiguredProxyProperties implements ProxyProperties {

    @Inject
    SchemaConfigMapping schemaConfig;

    @Inject
    TlsConfigMapping tlsConfig;

    @Inject
    BackendDefinitionsConfig backendDefinitionsConfig;

    @Override
    public SchemaConfig schemas() {
        return schemaConfig;
    }

    @Override
    public Optional<TlsConfig> tls() {
        return Optional.ofNullable(tlsConfig);
    }

    @Override
    public List<BackendDefinition> backends() {
        return backendDefinitionsConfig.backends();
    }

    @ConfigMapping(prefix = "gateway.schemas")
    public interface SchemaConfigMapping extends ProxyProperties.SchemaConfig {
    }

    @ConfigMapping(prefix = "gateway.tls")
    public interface TlsConfigMapping extends ProxyProperties.TlsConfig {
    }

    @ConfigMapping(prefix = "gateway.backends")
    public interface BackendDefinitionsConfig {
        @WithParentName
        List<ProxyProperties.BackendDefinition> backends();
    }
}
