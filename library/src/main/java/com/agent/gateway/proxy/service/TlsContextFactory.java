package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class TlsContextFactory {

    private final ConcurrentHashMap<String, SSLContext> sslContexts = new ConcurrentHashMap<>();

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
            KeyManager[] keyManagers = profile.keystore()
                .map(this::createKeyManagers)
                .orElse(null);
            TrustManager[] trustManagers = profile.truststore()
                .map(this::createTrustManagers)
                .orElse(null);

            SSLContext sslContext = strongestTlsContext();
            sslContext.init(keyManagers, trustManagers, null);
            return sslContext;
        } catch (Exception e) {
            throw new ProxyConfigurationException(
                "Failed to initialize TLS profile '%s'".formatted(profileName), e);
        }
    }

    private SSLContext strongestTlsContext() throws NoSuchAlgorithmException {
        try {
            return SSLContext.getInstance("TLSv1.3");
        } catch (NoSuchAlgorithmException e) {
            return SSLContext.getInstance("TLSv1.2");
        }
    }

    private KeyManager[] createKeyManagers(ProxyProperties.StoreConfig storeConfig) {
        try {
            KeyStore keyStore = loadKeyStore(storeConfig);
            String keyPassword = storeConfig.keyPassword()
                .orElseGet(() -> storeConfig.password().orElse(""));
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keyPassword.toCharArray());
            return kmf.getKeyManagers();
        } catch (Exception e) {
            throw new ProxyConfigurationException(
                "Failed to initialize key managers for '%s'".formatted(storeConfig.path()), e);
        }
    }

    private TrustManager[] createTrustManagers(ProxyProperties.StoreConfig storeConfig) {
        try {
            KeyStore trustStore = loadTrustStore(storeConfig);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
            return tmf.getTrustManagers();
        } catch (Exception e) {
            throw new ProxyConfigurationException(
                "Failed to initialize trust managers for '%s'".formatted(storeConfig.path()), e);
        }
    }

    private ProxyProperties.TlsProfile resolveProfile(String profileName) {
        return proxyProperties.tls()
            .map(ProxyProperties.TlsConfig::profiles)
            .map(profiles -> profiles.get(profileName))
            .orElseThrow(() -> new ProxyConfigurationException(
                "TLS profile '%s' is not configured".formatted(profileName)));
    }

    private KeyStore loadTrustStore(ProxyProperties.StoreConfig storeConfig) {
        String path = storeConfig.path();
        String type = storeConfig.type();

        try {
            if (isPemType(type)) {
                return loadPemTrustStore(path);
            } else if (isPkcs12Type(type)) {
                String password = storeConfig.password().orElse("");
                return loadPkcs12Store(path, password);
            } else {
                String password = storeConfig.password().orElse("");
                return loadJksStore(path, password, type);
            }
        } catch (IOException e) {
            throw new ProxyConfigurationException(
                "Failed to read truststore '%s'".formatted(path), e);
        } catch (Exception e) {
            throw new ProxyConfigurationException(
                "Failed to load truststore '%s'".formatted(path), e);
        }
    }

    private KeyStore loadPemTrustStore(String path) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);

        try (InputStream is = Files.newInputStream(Path.of(path))) {
            int count = 0;
            while (is.available() > 0) {
                Certificate cert = cf.generateCertificate(is);
                keyStore.setCertificateEntry("cert-" + count++, cert);
            }
        }
        return keyStore;
    }

    private KeyStore loadPkcs12Store(String path, String password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (InputStream is = Files.newInputStream(Path.of(path))) {
            keyStore.load(is, password.toCharArray());
        }
        return keyStore;
    }

    private KeyStore loadJksStore(String path, String password, String type) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(type);
        try (InputStream is = Files.newInputStream(Path.of(path))) {
            keyStore.load(is, password.toCharArray());
        }
        return keyStore;
    }

    private KeyStore loadKeyStore(ProxyProperties.StoreConfig storeConfig) {
        String path = storeConfig.path();
        String type = storeConfig.type();

        try {
            if (isPemType(type)) {
                return loadPemKeyStore(storeConfig);
            } else if (isPkcs12Type(type)) {
                String password = storeConfig.password().orElse("");
                return loadPkcs12Store(path, password);
            } else {
                String password = storeConfig.password().orElse("");
                return loadJksStore(path, password, type);
            }
        } catch (IOException e) {
            throw new ProxyConfigurationException(
                "Failed to read keystore '%s'".formatted(path), e);
        } catch (Exception e) {
            throw new ProxyConfigurationException(
                "Failed to load keystore '%s'".formatted(path), e);
        }
    }

    private KeyStore loadPemKeyStore(ProxyProperties.StoreConfig storeConfig) throws Exception {
        String certPath = storeConfig.path();
        String keyPath = certPath.replaceAll("\\.(crt|pem)$", ".key");
        if (!Files.exists(Path.of(keyPath))) {
            keyPath = certPath + ".key";
        }
        String keyPassword = storeConfig.keyPassword().orElse("");

        // Load certificate
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        Certificate cert;
        try (InputStream is = Files.newInputStream(Path.of(certPath))) {
            cert = cf.generateCertificate(is);
        }

        // Load private key
        PrivateKey privateKey = loadPemPrivateKey(keyPath, keyPassword);

        // Create keystore
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setKeyEntry("default", privateKey, keyPassword.toCharArray(), new Certificate[]{cert});

        return keyStore;
    }

    private PrivateKey loadPemPrivateKey(String keyPath, String password) throws Exception {
        String pemContent = Files.readString(Path.of(keyPath), StandardCharsets.UTF_8);

        // Extract base64 content between PEM markers
        String base64Content = extractPemContent(pemContent, "PRIVATE KEY");
        if (base64Content == null) {
            throw new ProxyConfigurationException(
                "No private key found in PEM file: " + keyPath);
        }

        byte[] keyBytes = Base64.getDecoder().decode(base64Content);

        // Try PKCS#8 format first
        try {
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        } catch (Exception e) {
            // If PKCS#8 fails, try other algorithms or formats
            try {
                KeyFactory kf = KeyFactory.getInstance("EC");
                PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
                return kf.generatePrivate(spec);
            } catch (Exception e2) {
                throw new ProxyConfigurationException(
                    "Failed to load private key from: " + keyPath +
                    " (only unencrypted PKCS#8 keys are supported)", e);
            }
        }
    }

    private String extractPemContent(String pemContent, String markerType) {
        String beginMarker = "-----BEGIN " + markerType + "-----";
        String endMarker = "-----END " + markerType + "-----";

        int beginIndex = pemContent.indexOf(beginMarker);
        int endIndex = pemContent.indexOf(endMarker);

        if (beginIndex == -1 || endIndex == -1) {
            return null;
        }

        // Extract content between markers, removing whitespace
        String content = pemContent.substring(beginIndex + beginMarker.length(), endIndex);
        return content.replaceAll("\\s+", "");
    }


    private boolean isPemType(String type) {
        return "PEM".equalsIgnoreCase(type);
    }

    private boolean isPkcs12Type(String type) {
        return "PKCS12".equalsIgnoreCase(type) || "P12".equalsIgnoreCase(type) || "PFX".equalsIgnoreCase(type);
    }
}
