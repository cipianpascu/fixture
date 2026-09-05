package com.db.olorin.rest.service;

import com.db.olorin.rest.config.ProxyProperties;
import com.db.olorin.rest.exception.ProxyConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TlsContextFactoryTest {
    @TempDir Path directory;

    @Test
    void buildsStrongTlsContextFromConfiguredPkcs12Truststore() throws Exception {
        Path store = emptyStore("trust.p12", "changeit");
        ProxyProperties properties = mock(ProxyProperties.class);
        ProxyProperties.TlsConfig tls = mock(ProxyProperties.TlsConfig.class);
        ProxyProperties.TlsProfile profile = mock(ProxyProperties.TlsProfile.class);
        ProxyProperties.StoreConfig truststore = mock(ProxyProperties.StoreConfig.class);
        ProxyProperties.BackendDefinition backend = mock(ProxyProperties.BackendDefinition.class);
        when(properties.tls()).thenReturn(Optional.of(tls)); when(tls.profiles()).thenReturn(Map.of("internal", profile));
        when(profile.truststore()).thenReturn(Optional.of(truststore)); when(profile.keystore()).thenReturn(Optional.empty());
        when(truststore.path()).thenReturn(store.toString()); when(truststore.password()).thenReturn(Optional.of("changeit")); when(truststore.type()).thenReturn("PKCS12");
        when(backend.tlsProfile()).thenReturn(Optional.of("internal"));

        var context = new TlsContextFactory(properties).createBackendSslContext(backend).orElseThrow();

        assertThat(context.getProtocol()).isIn("TLSv1.3", "TLSv1.2");
    }

    @Test
    void rejectsUnknownTlsProfile() {
        ProxyProperties properties = mock(ProxyProperties.class); ProxyProperties.BackendDefinition backend = mock(ProxyProperties.BackendDefinition.class);
        when(properties.tls()).thenReturn(Optional.of(() -> Map.of())); when(backend.tlsProfile()).thenReturn(Optional.of("missing"));
        assertThatThrownBy(() -> new TlsContextFactory(properties).createBackendSslContext(backend))
            .isInstanceOf(ProxyConfigurationException.class).hasMessageContaining("missing");
    }

    @Test
    void buildsStrongTlsContextFromPemTruststore() throws Exception {
        Path certificate = directory.resolve("ca.pem");
        Files.writeString(certificate, CERTIFICATE_PEM);
        ProxyProperties properties = mock(ProxyProperties.class);
        ProxyProperties.TlsConfig tls = mock(ProxyProperties.TlsConfig.class);
        ProxyProperties.TlsProfile profile = mock(ProxyProperties.TlsProfile.class);
        ProxyProperties.StoreConfig truststore = mock(ProxyProperties.StoreConfig.class);
        ProxyProperties.BackendDefinition backend = mock(ProxyProperties.BackendDefinition.class);
        when(properties.tls()).thenReturn(Optional.of(tls)); when(tls.profiles()).thenReturn(Map.of("pem", profile));
        when(profile.truststore()).thenReturn(Optional.of(truststore)); when(profile.keystore()).thenReturn(Optional.empty());
        when(truststore.path()).thenReturn(certificate.toString()); when(truststore.type()).thenReturn("PEM");
        when(backend.tlsProfile()).thenReturn(Optional.of("pem"));

        var context = new TlsContextFactory(properties).createBackendSslContext(backend).orElseThrow();

        assertThat(context.getProtocol()).isIn("TLSv1.3", "TLSv1.2");
    }

    @Test
    void initializesContextWhenMutualTlsKeystoreAndTruststoreAreConfigured() throws Exception {
        Path trust = emptyStore("trust.p12", "changeit");
        Path key = emptyStore("client.p12", "changeit");
        ProxyProperties properties = mock(ProxyProperties.class);
        ProxyProperties.TlsConfig tls = mock(ProxyProperties.TlsConfig.class);
        ProxyProperties.TlsProfile profile = mock(ProxyProperties.TlsProfile.class);
        ProxyProperties.StoreConfig truststore = store(trust);
        ProxyProperties.StoreConfig keystore = store(key);
        ProxyProperties.BackendDefinition backend = mock(ProxyProperties.BackendDefinition.class);
        when(properties.tls()).thenReturn(Optional.of(tls)); when(tls.profiles()).thenReturn(Map.of("mtls", profile));
        when(profile.truststore()).thenReturn(Optional.of(truststore)); when(profile.keystore()).thenReturn(Optional.of(keystore));
        when(backend.tlsProfile()).thenReturn(Optional.of("mtls"));

        assertThat(new TlsContextFactory(properties).createBackendSslContext(backend).orElseThrow().getProtocol())
            .isIn("TLSv1.3", "TLSv1.2");
    }

    private ProxyProperties.StoreConfig store(Path path) {
        ProxyProperties.StoreConfig store = mock(ProxyProperties.StoreConfig.class);
        when(store.path()).thenReturn(path.toString()); when(store.password()).thenReturn(Optional.of("changeit"));
        when(store.keyPassword()).thenReturn(Optional.of("changeit")); when(store.type()).thenReturn("PKCS12");
        return store;
    }

    private Path emptyStore(String name, String password) throws Exception {
        Path path = directory.resolve(name); KeyStore store = KeyStore.getInstance("PKCS12"); store.load(null, password.toCharArray());
        try (OutputStream output = Files.newOutputStream(path)) { store.store(output, password.toCharArray()); }
        return path;
    }

    private static final String CERTIFICATE_PEM = """
        -----BEGIN CERTIFICATE-----
        MIIDEzCCAfugAwIBAgIUV7z4uvfqfd8prP/KI8ZWw2t4YoswDQYJKoZIhvcNAQEL
        BQAwHTEbMBkGA1UEAwwSY2lwcmlhbnBhc2N1LVYxLTExMB4XDTIzMTIwMzExNTUw
        N1oXDTMzMTEzMDExNTUwN1owHTEbMBkGA1UEAwwSY2lwcmlhbnBhc2N1LVYxLTEx
        MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAlz/7PAp0eDceXIExqv97
        K+5PT1iE6ibtgAHd9vkVtAnszgzSTmtdmy55jIJbtXFWxlb1IftOqdt6sYsRB844
        i29ec+kZQ7Ob3mt0ATVNs808D80gZTVcf9r2DJA2HSEOTFENc8GQAUpwpY0DIBz/
        WNjdSL7/stcihAxsz271XYN4UzF89AfrAfn5eupt1rQXAZ57lPfpw2tfVze9GFvY
        6nkLFkYt8dfZ7OvAsgPu9PJc7SzHt30Nsy2UT+2iqvU7gfMUS4M8cZL4cOus9GCw
        WWgFiUGnbs6j+gxgr+fy8rO1nR72+Ylzun0AMPpPMPY7SGhuDy6l+0XzA1cAfVsE
        CwIDAQABo0swSTAJBgNVHRMEAjAAMB0GA1UdEQQWMBSCEmNpcHJpYW5wYXNjdS1W
        MS0xMTAdBgNVHQ4EFgQUcdoTlqSu0Vs+SKCbAc53JKELJH0wDQYJKoZIhvcNAQEL
        BQADggEBAE3Hu4ZAmHit19YMzrPfFZ2qgCeVKoahSHLbh/P6nOKkgi0qLly/KV10
        4gMLukevs+iTLRtHcPEKw8FoRuaG0HYvxOEdmMeHBPuB4iGM0U4h+jiHF2PNXx0E
        MK6in4pz9bx+HQxialg+djK39JeyOtZrimbvu/904MvUlcSQ+t+6dJOZQqGTOvEa
        v//EdwK5OVUG3X1sBvYwsBWRANcEzj1pOdOMHox98RnYpmLsH5hs9s9ZnSQaChpH
        FJBxHCGpi0b8enZlyovaNuLcARF479Jb3orkOGYuOIgiyOfUK5Hn+zIxgj91vUGQ
        LEtelwpQmpI0UQu6YAZxpxTcBDxOJ7U=
        -----END CERTIFICATE-----
        """;
}
