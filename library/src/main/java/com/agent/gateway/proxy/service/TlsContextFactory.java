package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TlsContextFactory {

    private final Map<String, SSLContext> sslContexts = new ConcurrentHashMap<>();

    @Inject
    ProxyProperties proxyProperties;

    public Optional<SSLContext> createServiceSslContext(Optional<String> profileName) {
        return createSslContext(profileName);
    }

    public Optional<SSLContext> createBackendSslContext(ProxyProperties.BackendDefinition backend) {
        return createSslContext(backend.tlsProfile());
    }

    private Optional<SSLContext> createSslContext(Optional<String> profileName) {
        return profileName.map(name -> sslContexts.computeIfAbsent(name, this::buildSslContext));
    }

    private SSLContext buildSslContext(String profileName) {
        ProxyProperties.TlsProfile profile = resolveProfile(profileName);

        try {
            KeyManagerFactory keyManagerFactory = profile.keystore()
                .map(this::buildKeyManagerFactory)
                .orElse(null);
            TrustManagerFactory trustManagerFactory = profile.truststore()
                .map(this::buildTrustManagerFactory)
                .orElse(null);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(
                keyManagerFactory != null ? keyManagerFactory.getKeyManagers() : null,
                trustManagerFactory != null ? trustManagerFactory.getTrustManagers() : null,
                null
            );
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new ProxyConfigurationException(
                "Failed to initialize TLS profile '%s'".formatted(profileName), e);
        }
    }

    private ProxyProperties.TlsProfile resolveProfile(String profileName) {
        return proxyProperties.tls()
            .map(ProxyProperties.TlsConfig::profiles)
            .map(profiles -> profiles.get(profileName))
            .orElseThrow(() -> new ProxyConfigurationException(
                "TLS profile '%s' is not configured".formatted(profileName)));
    }

    private KeyManagerFactory buildKeyManagerFactory(ProxyProperties.StoreConfig storeConfig) {
        try {
            KeyStore keyStore = loadKeyStore(storeConfig);
            KeyManagerFactory keyManagerFactory =
                KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagerFactory.init(
                keyStore,
                storeConfig.keyPassword().orElseGet(() -> storeConfig.password().orElse("")).toCharArray()
            );
            return keyManagerFactory;
        } catch (GeneralSecurityException e) {
            throw new ProxyConfigurationException(
                "Failed to initialize key managers for keystore '%s'".formatted(storeConfig.path()), e);
        }
    }

    private TrustManagerFactory buildTrustManagerFactory(ProxyProperties.StoreConfig storeConfig) {
        try {
            KeyStore trustStore = loadKeyStore(storeConfig);
            TrustManagerFactory trustManagerFactory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
            return trustManagerFactory;
        } catch (GeneralSecurityException e) {
            throw new ProxyConfigurationException(
                "Failed to initialize trust managers for truststore '%s'".formatted(storeConfig.path()), e);
        }
    }

    private KeyStore loadKeyStore(ProxyProperties.StoreConfig storeConfig) {
        Path storePath = Path.of(storeConfig.path());
        try (InputStream inputStream = Files.newInputStream(storePath)) {
            KeyStore keyStore = KeyStore.getInstance(storeConfig.type());
            keyStore.load(inputStream, storeConfig.password().orElse("").toCharArray());
            return keyStore;
        } catch (IOException e) {
            throw new ProxyConfigurationException(
                "Failed to read TLS store '%s'".formatted(storeConfig.path()), e);
        } catch (GeneralSecurityException e) {
            throw new ProxyConfigurationException(
                "Failed to load TLS store '%s'".formatted(storeConfig.path()), e);
        }
    }
}
