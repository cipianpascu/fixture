package com.agent.gateway.proxy.config;

import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration properties for the proxy module (Quarkus)
 * Loaded from application.yml - NO database
 */
public interface ProxyProperties {
    
    @WithName("schemas")
    SchemaConfig schemas();
    
    @WithName("tls")
    Optional<TlsConfig> tls();

    @WithName("history")
    HistoryConfig history();
    
    @WithName("backends")
    List<BackendDefinition> backends();
    
    interface AuthConfig {
        @WithDefault("true")
        boolean enabled();
        
        @WithName("service-url")
        @WithDefault("http://localhost:8081")
        String serviceUrl();
        
        @WithName("session-id-header")
        @WithDefault("X-Session-Id")
        String sessionIdHeader();
        
        @WithName("session-id-cookie")
        @WithDefault("sessionId")
        String sessionIdCookie();
        
        @WithDefault("5s")
        Duration timeout();

        @WithName("tls-profile")
        Optional<String> tlsProfile();

        @WithName("security-type")
        Optional<String> securityType();

        @WithName("security-config")
        Map<String, String> securityConfig();
    }
    
    interface SchemaConfig {
        @WithDefault("classpath:schemas/")
        String directory();
        
        @WithName("validate-requests")
        @WithDefault("true")
        boolean validateRequests();
        
        @WithName("validate-bodies")
        @WithDefault("true")
        boolean validateBodies();
        
        @WithName("validate-responses")
        @WithDefault("false")
        boolean validateResponses();
        
        @WithName("strict-mode")
        @WithDefault("true")
        boolean strictMode();
    }

    interface AuthRequestConfig {
        Optional<String> service();

        Optional<String> path();

        @WithName("sparte-gvo")
        Optional<List<String>> sparteGvo();

        Optional<List<String>> btx();

        Optional<List<String>> pss();
    }

    interface AuthzRequestConfig {
        Optional<String> service();

        String path();

        @WithName("branch-customer-number")
        Optional<String> branchCustomerNumber();

        @WithName("gvo-entitlements-list")
        Optional<List<String>> gvoEntitlementsList();

        @WithName("business-transactions")
        Optional<List<String>> businessTransactions();

        @WithName("service-shop-transactions")
        Optional<List<String>> serviceShopTransactions();
    }
    
    interface BackendDefinition {
        String name();
        
        @WithName("baseUrl")
        String baseUrl();
        
        String path();
        
        Optional<String> schema();
        
        @WithDefault("30s")
        Duration timeout();
        
        @WithDefault("true")
        boolean enabled();

        @WithDefault("rest")
        String protocol();

        @WithName("http-version")
        @WithDefault("http1_1")
        String httpVersion();
        
        @WithName("securityType")
        Optional<String> securityType();
        
        @WithName("securityConfig")
        Map<String, String> securityConfig();

        @WithName("auth-request")
        Optional<AuthRequestConfig> authRequest();

        @WithName("authz-request")
        Optional<AuthzRequestConfig> authzRequest();

        Optional<BackendHistoryConfig> history();

        @WithName("tls-profile")
        Optional<String> tlsProfile();

        Optional<SoapConfig> soap();

        Optional<ProxyConfig> proxy();
    }

    interface SoapConfig {
        @WithDefault("1.1")
        String version();

        @WithName("soap-action")
        Optional<String> soapAction();
    }

    interface ProxyConfig {
        String host();

        int port();

        @WithName("non-proxy-hosts")
        @WithDefault("")
        List<String> nonProxyHosts();
    }

    interface HistoryConfig {
        @WithDefault("false")
        boolean enabled();

        @WithDefault("gcp-pubsub")
        String provider();

        @WithName("delivery-mode")
        @WithDefault("async")
        String deliveryMode();

        @WithName("fail-open")
        @WithDefault("true")
        boolean failOpen();

        @WithDefault("POST,PUT,PATCH,DELETE")
        List<String> methods();

        @WithName("service-url")
        @WithDefault("https://pubsub.googleapis.com")
        String serviceUrl();

        @WithName("project-id")
        Optional<String> projectId();

        Optional<String> topic();

        @WithDefault("5s")
        Duration timeout();

        @WithName("tls-profile")
        Optional<String> tlsProfile();
    }

    interface BackendHistoryConfig {
        Optional<Boolean> enabled();

        Optional<String> provider();

        @WithName("delivery-mode")
        Optional<String> deliveryMode();

        @WithName("fail-open")
        Optional<Boolean> failOpen();

        @WithName("service-url")
        Optional<String> serviceUrl();

        @WithName("project-id")
        Optional<String> projectId();

        Optional<String> topic();

        @WithName("additional-properties")
        Map<String, String> additionalProperties();
    }

    interface TlsConfig {
        @WithName("profiles")
        Map<String, TlsProfile> profiles();
    }

    interface TlsProfile {
        Optional<StoreConfig> truststore();

        Optional<StoreConfig> keystore();
    }

    interface StoreConfig {
        String path();

        Optional<String> password();

        @WithDefault("PKCS12")
        String type();

        @WithName("key-password")
        Optional<String> keyPassword();
    }
}
