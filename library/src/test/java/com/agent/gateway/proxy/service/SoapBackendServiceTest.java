package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.exception.SoapFaultException;
import com.agent.gateway.proxy.exception.UpstreamProxyException;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.service.auth.AuthService;
import com.agent.gateway.proxy.service.auth.AuthServiceFactory;
import com.sun.net.httpserver.HttpServer;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SoapBackendServiceTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void invokesSoapBackendWithPostAndSoapAction() throws Exception {
        AtomicReference<String> lastMethod = new AtomicReference<>();
        AtomicReference<String> lastSoapAction = new AtomicReference<>();
        AtomicReference<String> lastAcceptEncoding = new AtomicReference<>();
        AtomicReference<String> lastBody = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/soap/customer-profile", exchange -> {
            lastMethod.set(exchange.getRequestMethod());
            lastSoapAction.set(exchange.getRequestHeaders().getFirst("SOAPAction"));
            lastAcceptEncoding.set(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(
                exchange,
                200,
                """
                    <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:cp="http://agent.com/customerprofile">
                      <soapenv:Body>
                        <cp:GetCustomerProfileResponse>
                          <cp:customerId>321</cp:customerId>
                          <cp:fullName>Jane Doe</cp:fullName>
                        </cp:GetCustomerProfileResponse>
                      </soapenv:Body>
                    </soapenv:Envelope>
                    """
            );
        });
        server.start();

        SoapBackendService service = new SoapBackendService();
        service.authServiceFactory = noOpAuthServiceFactory();
        service.tlsContextFactory = new TlsContextFactory();

        TestSoapRequest requestBody = new TestSoapRequest();
        requestBody.setCustomerId("321");

        TestSoapResponse response = service.invoke(
            backend("http://127.0.0.1:" + server.getAddress().getPort(), "/soap/customer-profile"),
            requestContext(),
            requestBody,
            "urn:GetCustomerProfile",
            TestSoapResponse.class
        );

        assertEquals("POST", lastMethod.get());
        assertEquals("\"urn:GetCustomerProfile\"", lastSoapAction.get());
        assertEquals(null, lastAcceptEncoding.get());
        assertEquals("321", response.getCustomerId());
        assertEquals("Jane Doe", response.getFullName());
        org.junit.jupiter.api.Assertions.assertTrue(lastBody.get().contains("<customerId>321</customerId>"));
    }

    @Test
    void mapsSoapFaultToException() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/soap/customer-profile", exchange -> respond(
            exchange,
            500,
            """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body>
                    <soapenv:Fault>
                      <faultcode>soapenv:Server</faultcode>
                      <faultstring>customer profile unavailable</faultstring>
                    </soapenv:Fault>
                  </soapenv:Body>
                </soapenv:Envelope>
                """
        ));
        server.start();

        SoapBackendService service = new SoapBackendService();
        service.authServiceFactory = noOpAuthServiceFactory();
        service.tlsContextFactory = new TlsContextFactory();

        TestSoapRequest requestBody = new TestSoapRequest();
        requestBody.setCustomerId("fault");

        SoapFaultException exception = assertThrows(
            SoapFaultException.class,
            () -> service.invoke(
                backend("http://127.0.0.1:" + server.getAddress().getPort(), "/soap/customer-profile"),
                requestContext(),
                requestBody,
                "urn:GetCustomerProfile",
                TestSoapResponse.class
            )
        );

        assertEquals("SOAP Fault from backend: soapenv:Server customer profile unavailable", exception.getMessage());
    }

    @Test
    void rejectsSoapResponsesWithDoctypeDeclarations() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/soap/customer-profile", exchange -> respond(
            exchange,
            200,
            """
                <!DOCTYPE soapenv:Envelope [<!ENTITY external SYSTEM "file:///not-available">]>
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/">
                  <soapenv:Body><external/></soapenv:Body>
                </soapenv:Envelope>
                """
        ));
        server.start();

        SoapBackendService service = new SoapBackendService();
        service.authServiceFactory = noOpAuthServiceFactory();
        service.tlsContextFactory = new TlsContextFactory();

        TestSoapRequest requestBody = new TestSoapRequest();
        requestBody.setCustomerId("321");

        assertThrows(
            UpstreamProxyException.class,
            () -> service.invoke(
                backend("http://127.0.0.1:" + server.getAddress().getPort(), "/soap/customer-profile"),
                requestContext(),
                requestBody,
                "urn:GetCustomerProfile",
                TestSoapResponse.class
            )
        );
    }

    private AuthServiceFactory noOpAuthServiceFactory() {
        return new AuthServiceFactory() {
            @Override
            public AuthService createAuthService(ProxyProperties.BackendDefinition backend) {
                return (request, headers, requestBody) -> {
                };
            }
        };
    }

    private ProxyRequestContext requestContext() {
        return new ProxyRequestContext(
            "GET",
            "/api/v1/customer-profiles/321",
            null,
            new LinkedHashMap<>(Map.of(
                "x-session-id", List.of("soap-session"),
                "accept-encoding", List.of("gzip")
            )),
            Map.of()
        );
    }

    private ProxyProperties.BackendDefinition backend(String baseUrl, String path) {
        return new ProxyProperties.BackendDefinition() {
            @Override
            public String name() {
                return "customer-profile-soap-service";
            }

            @Override
            public String baseUrl() {
                return baseUrl;
            }

            @Override
            public String path() {
                return path;
            }

            @Override
            public Optional<String> schema() {
                return Optional.of("customer-profile.yaml");
            }

            @Override
            public Duration timeout() {
                return Duration.ofSeconds(5);
            }

            @Override
            public boolean enabled() {
                return true;
            }

            @Override
            public String protocol() {
                return "soap";
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
            public Optional<ProxyProperties.BackendHistoryConfig> history() {
                return Optional.empty();
            }

            @Override
            public Optional<String> tlsProfile() {
                return Optional.empty();
            }

            @Override
            public Optional<ProxyProperties.SoapConfig> soap() {
                return Optional.of(new ProxyProperties.SoapConfig() {
                    @Override
                    public String version() {
                        return "1.1";
                    }

                    @Override
                    public Optional<String> soapAction() {
                        return Optional.of("urn:GetCustomerProfile");
                    }
                });
            }

            @Override
            public Optional<ProxyProperties.ProxyConfig> proxy() {
                return Optional.empty();
            }
        };
    }

    private void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/xml; charset=utf-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    @XmlRootElement(name = "GetCustomerProfileRequest", namespace = "http://agent.com/customerprofile")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TestSoapRequest {
        @XmlElement(namespace = "http://agent.com/customerprofile", required = true)
        private String customerId;

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }
    }

    @XmlRootElement(name = "GetCustomerProfileResponse", namespace = "http://agent.com/customerprofile")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class TestSoapResponse {
        @XmlElement(namespace = "http://agent.com/customerprofile", required = true)
        private String customerId;

        @XmlElement(namespace = "http://agent.com/customerprofile", required = true)
        private String fullName;

        public String getCustomerId() {
            return customerId;
        }

        public String getFullName() {
            return fullName;
        }
    }
}
