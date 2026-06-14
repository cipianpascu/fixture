package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.config.ProxyProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthServiceFactoryTest {

    @Test
    void defaultsMissingJwtAuthRequestServiceToAuth() {
        RecordingAuthServiceCallerFactory callerFactory = new RecordingAuthServiceCallerFactory();
        AuthServiceFactory factory = new AuthServiceFactory();
        factory.authServiceCallerFactory = callerFactory;

        factory.createAuthService(backend(Optional.of(authRequest(Optional.empty(), Optional.of(List.of("FirstFunction")), Optional.empty(), Optional.empty())), Optional.empty()));

        assertEquals(List.of("auth"), callerFactory.requestedServices);
    }

    @Test
    void defaultsMissingJwtAuthzRequestServiceToAuth() {
        RecordingAuthServiceCallerFactory callerFactory = new RecordingAuthServiceCallerFactory();
        AuthServiceFactory factory = new AuthServiceFactory();
        factory.authServiceCallerFactory = callerFactory;

        factory.createAuthService(backend(Optional.empty(), Optional.of(authzRequest(Optional.empty(), "/auth/authz/eidp/{sessionId}"))));

        assertEquals(List.of("auth"), callerFactory.requestedServices);
    }

    private ProxyProperties.BackendDefinition backend(
        Optional<ProxyProperties.AuthRequestConfig> authRequest,
        Optional<ProxyProperties.AuthzRequestConfig> authzRequest
    ) {
        return new ProxyProperties.BackendDefinition() {
            @Override
            public String name() {
                return "jwt-backend";
            }

            @Override
            public String baseUrl() {
                return "http://localhost";
            }

            @Override
            public String path() {
                return "/jwt";
            }

            @Override
            public Optional<String> schema() {
                return Optional.empty();
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
                return "rest";
            }

            @Override
            public String httpVersion() {
                return "http1_1";
            }

            @Override
            public Optional<String> securityType() {
                return Optional.of("jwt");
            }

            @Override
            public Map<String, String> securityConfig() {
                return Map.of();
            }

            @Override
            public Optional<ProxyProperties.AuthRequestConfig> authRequest() {
                return authRequest;
            }

            @Override
            public Optional<ProxyProperties.AuthzRequestConfig> authzRequest() {
                return authzRequest;
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
                return Optional.empty();
            }

            @Override
            public Optional<ProxyProperties.ProxyConfig> proxy() {
                return Optional.empty();
            }
        };
    }

    private ProxyProperties.AuthRequestConfig authRequest(
        Optional<String> service,
        Optional<List<String>> btx,
        Optional<List<String>> sparteGvo,
        Optional<List<String>> pss
    ) {
        return new ProxyProperties.AuthRequestConfig() {
            @Override
            public Optional<String> service() {
                return service;
            }

            @Override
            public Optional<String> path() {
                return Optional.empty();
            }

            @Override
            public Optional<List<String>> sparteGvo() {
                return sparteGvo;
            }

            @Override
            public Optional<List<String>> btx() {
                return btx;
            }

            @Override
            public Optional<List<String>> pss() {
                return pss;
            }
        };
    }

    private ProxyProperties.AuthzRequestConfig authzRequest(Optional<String> service, String path) {
        return new ProxyProperties.AuthzRequestConfig() {
            @Override
            public Optional<String> service() {
                return service;
            }

            @Override
            public String path() {
                return path;
            }

            @Override
            public Optional<String> branchCustomerNumber() {
                return Optional.empty();
            }

            @Override
            public Optional<List<String>> gvoEntitlementsList() {
                return Optional.empty();
            }

            @Override
            public Optional<List<String>> businessTransactions() {
                return Optional.empty();
            }

            @Override
            public Optional<List<String>> serviceShopTransactions() {
                return Optional.empty();
            }
        };
    }

    private static final class RecordingAuthServiceCallerFactory extends AuthServiceCallerFactory {
        private final java.util.ArrayList<String> requestedServices = new java.util.ArrayList<>();

        @Override
        public AuthServiceCaller get(String serviceName) {
            requestedServices.add(serviceName);
            return null;
        }

        @Override
        public ResolvedAuthServiceConfig getConfig(String serviceName) {
            return new ResolvedAuthServiceConfig(
                serviceName,
                true,
                "http://localhost:8081",
                "X-Session-Id",
                "sessionId",
                Duration.ofSeconds(5),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                ResolvedAuthCacheConfig.disabled()
            );
        }
    }
}
