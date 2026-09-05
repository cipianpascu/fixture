package com.db.olorin.rest.config;


import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Configuration properties for the proxy module (Quarkus)
 * Loaded from application.yml - NO database
 */
public interface ProxyProperties {
    
    SchemaConfig schemas();
    
    Optional<TlsConfig> tls();

    HistoryConfig history();
    
    List<BackendDefinition> backends();
    
    interface AuthConfig {
        boolean enabled();
        
        String serviceUrl();
        
        String sessionIdHeader();
        
        String sessionIdCookie();
        
        Duration timeout();

        Optional<String> tlsProfile();

        Optional<String> securityType();

        Map<String, String> securityConfig();

        Optional<AuthCacheConfig> cache();
    }

    interface AuthCacheConfig {
        boolean enabled();

        Duration expirySkew();

        int maxSize();
    }
    
    interface SchemaConfig {
        String directory();
        
        boolean validateRequests();
        
        boolean validateBodies();
        
        boolean validateResponses();
        
        boolean strictMode();
    }

    interface AuthRequestConfig {
        Optional<String> service();

        Optional<String> path();

        Optional<List<String>> sparteGvo();

        Optional<List<String>> btx();

        Optional<List<String>> pss();
    }

    interface AuthzRequestConfig {
        Optional<String> service();

        String path();

        Optional<String> branchCustomerNumber();

        Optional<List<String>> gvoEntitlementsList();

        Optional<List<String>> businessTransactions();

        Optional<List<String>> serviceShopTransactions();
    }
    
    interface BackendDefinition {
        String name();
        
        String baseUrl();
        
        String path();
        
        Optional<String> schema();
        
        Duration timeout();
        
        boolean enabled();

        String protocol();

        String httpVersion();
        
        Optional<String> securityType();
        
        Map<String, String> securityConfig();

        Optional<AuthRequestConfig> authRequest();

        Optional<AuthzRequestConfig> authzRequest();

        Optional<BackendHistoryConfig> history();

        Optional<String> tlsProfile();

        Optional<SoapConfig> soap();

        Optional<ProxyConfig> proxy();

        List<String> forwardHeaders();

        List<String> forwardCookies();
    }

    interface SoapConfig {
        String version();

        Optional<String> soapAction();
    }

    interface ProxyConfig {
        String host();

        int port();

        List<String> nonProxyHosts();
    }

    interface HistoryConfig {
        boolean enabled();

        String provider();

        String deliveryMode();

        boolean failOpen();

        String serviceUrl();

        Optional<String> projectId();

        Optional<String> topic();

        Duration timeout();

        Optional<String> tlsProfile();

        Optional<HistoryExecutorConfig> executor();
    }

    interface HistoryExecutorConfig {
        int coreThreads();

        int maxThreads();

        int queueCapacity();
    }

    interface BackendHistoryConfig {
        Optional<Boolean> enabled();

        Optional<String> provider();

        Optional<String> deliveryMode();

        Optional<Boolean> failOpen();

        Optional<String> serviceUrl();

        Optional<String> projectId();

        Optional<String> topic();

        Optional<String> tokenHeader();

        Map<String, String> additionalProperties();

        /**
         * Values generated for the request and inserted into outbound headers before
         * history emission. Currently supports {@code generator:uuid}.
         */
        Map<String, String> generatedHeaders();
    }

    interface TlsConfig {
        Map<String, TlsProfile> profiles();
    }

    interface TlsProfile {
        Optional<StoreConfig> truststore();

        Optional<StoreConfig> keystore();
    }

    interface StoreConfig {
        String path();

        Optional<String> password();

        String type();

        Optional<String> keyPassword();
    }
}
