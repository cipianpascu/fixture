package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TlsContextFactoryTest {

    @TempDir
    Path tempDir;

    @Test
    void createsSslContextFromConfiguredTruststoreProfile() throws Exception {
        Path truststorePath = writeEmptyStore(tempDir.resolve("truststore.p12"), "PKCS12", "changeit");

        TlsContextFactory factory = new TlsContextFactory();
        factory.proxyProperties = proxyProperties(
            Map.of("internal-ca", tlsProfile(storeConfig(truststorePath, "changeit", "PKCS12"), null))
        );

        SSLContext sslContext = factory.createBackendSslContext(backend("orders", "internal-ca")).orElseThrow();

        assertNotNull(sslContext);
    }

    @Test
    void rejectsUnknownTlsProfile() {
        TlsContextFactory factory = new TlsContextFactory();
        factory.proxyProperties = proxyProperties(Map.of());

        assertThrows(
            ProxyConfigurationException.class,
            () -> factory.createBackendSslContext(backend("orders", "missing-profile"))
        );
    }

    private Path writeEmptyStore(Path path, String type, String password)
        throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance(type);
        keyStore.load(null, password.toCharArray());
        try (OutputStream outputStream = Files.newOutputStream(path)) {
            keyStore.store(outputStream, password.toCharArray());
        }
        return path;
    }

    private ProxyProperties proxyProperties(Map<String, ProxyProperties.TlsProfile> profiles) {
        return new ProxyProperties() {
            @Override
            public SchemaConfig schemas() {
                return new SchemaConfig() {
                    @Override
                    public String directory() {
                        return "classpath:schemas/";
                    }

                    @Override
                    public boolean validateRequests() {
                        return true;
                    }

                    @Override
                    public boolean validateBodies() {
                        return true;
                    }

                    @Override
                    public boolean validateResponses() {
                        return false;
                    }

                    @Override
                    public boolean strictMode() {
                        return true;
                    }
                };
            }

            @Override
            public Optional<TlsConfig> tls() {
                return Optional.of(() -> profiles);
            }

            @Override
            public List<BackendDefinition> backends() {
                return List.of();
            }
        };
    }

    private ProxyProperties.BackendDefinition backend(String name, String tlsProfile) {
        return new ProxyProperties.BackendDefinition() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String baseUrl() {
                return "https://backend.example.com";
            }

            @Override
            public String path() {
                return "/api";
            }

            @Override
            public Optional<String> schema() {
                return Optional.empty();
            }

            @Override
            public Duration timeout() {
                return Duration.ofSeconds(30);
            }

            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String protocol() {
                return "rest";
            }

            @Override
            public String httpVersion() {
                return "http1_1";
            }

            @Override
            public Optional<String> securityType() {
                return Optional.of("none");
            }

            @Override
            public Map<String, String> securityConfig() {
                return Map.of();
            }

            @Override
            public Optional<ProxyProperties.AuthRequestConfig> authRequest() {
                return Optional.empty();
            }

            @Override
            public Optional<ProxyProperties.AuthzRequestConfig> authzRequest() {
                return Optional.empty();
            }

            @Override
            public Optional<String> tlsProfile() {
                return Optional.of(tlsProfile);
            }

            @Override
            public Optional<ProxyProperties.SoapConfig> soap() {
                return Optional.empty();
            }

            @Override
            public Optional<ProxyProperties.ProxyConfig> proxy() {
                return Optional.empty();
            }
        };
    }

    private ProxyProperties.TlsProfile tlsProfile(
        ProxyProperties.StoreConfig truststore,
        ProxyProperties.StoreConfig keystore) {
        return new ProxyProperties.TlsProfile() {
            @Override
            public Optional<ProxyProperties.StoreConfig> truststore() {
                return Optional.ofNullable(truststore);
            }

            @Override
            public Optional<ProxyProperties.StoreConfig> keystore() {
                return Optional.ofNullable(keystore);
            }
        };
    }

    private ProxyProperties.StoreConfig storeConfig(Path path, String password, String type) {
        return new ProxyProperties.StoreConfig() {
            @Override
            public String path() {
                return path.toString();
            }

            @Override
            public Optional<String> password() {
                return Optional.of(password);
            }

            @Override
            public String type() {
                return type;
            }

            @Override
            public Optional<String> keyPassword() {
                return Optional.empty();
            }
        };
    }
}
