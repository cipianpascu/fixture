package com.db.olorin.rest.config;

import com.db.olorin.rest.client.SpringTypedRestClient;
import com.db.olorin.rest.client.TypedRestClient;
import com.db.olorin.rest.client.TypedSoapClient;
import com.db.olorin.rest.client.SpringTypedSoapClient;
import com.db.olorin.rest.client.OlorinRestClientInjector;
import com.db.olorin.rest.service.auth.AuthServiceFactory;
import com.db.olorin.rest.service.auth.CloudRunIdTokenProvider;
import com.db.olorin.rest.service.auth.GoogleCloudRunIdTokenProvider;
import com.db.olorin.rest.service.auth.AuthServiceConfigRegistry;
import com.db.olorin.rest.service.auth.AuthServiceCallerFactory;
import com.db.olorin.rest.service.auth.AuthzTokenCache;
import com.db.olorin.rest.service.TlsContextFactory;
import com.db.olorin.rest.history.GcpPubSubHistoryPublisher;
import com.db.olorin.rest.history.HistoryPayloadMapper;
import com.db.olorin.rest.history.HistoryPublisher;
import com.db.olorin.rest.history.HistoryService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@ConditionalOnBean(RestConfigurationProvider.class)
public class RestClientAutoConfiguration {

    @Bean
    ProxyProperties proxyProperties(RestConfigurationProvider provider) {
        return RestConfigurationAdapter.bind(provider);
    }

    @Bean
    RestConfigurationAdapter restConfigurationAdapter(RestConfigurationProvider provider) {
        return RestConfigurationAdapter.from(provider.configuration());
    }

    @Bean
    CloudRunIdTokenProvider cloudRunIdTokenProvider() {
        return new GoogleCloudRunIdTokenProvider();
    }

    @Bean
    AuthServiceConfigRegistry authServiceConfigRegistry(
        ProxyProperties properties, RestConfigurationAdapter configuration) {
        return new AuthServiceConfigRegistry(properties, configuration);
    }

    @Bean
    TlsContextFactory tlsContextFactory(ProxyProperties properties) {
        return new TlsContextFactory(properties);
    }

    @Bean
    AuthServiceCallerFactory authServiceCallerFactory(
        AuthServiceConfigRegistry registry, TlsContextFactory tlsContextFactory,
        CloudRunIdTokenProvider cloudRunIdTokenProvider) {
        return new AuthServiceCallerFactory(registry, tlsContextFactory, cloudRunIdTokenProvider);
    }

    @Bean
    AuthzTokenCache authzTokenCache() {
        return new AuthzTokenCache();
    }

    @Bean
    GcpPubSubHistoryPublisher gcpPubSubHistoryPublisher(TlsContextFactory tlsContextFactory) {
        return new GcpPubSubHistoryPublisher(tlsContextFactory);
    }

    @Bean
    HistoryService historyService(
        ProxyProperties properties,
        ObjectProvider<HistoryPayloadMapper> mappers,
        ObjectProvider<HistoryPublisher> publishers) {
        return new HistoryService(properties, mappers, publishers);
    }

    @Bean
    AuthServiceFactory authServiceFactory(
        CloudRunIdTokenProvider cloudRunIdTokenProvider, AuthServiceConfigRegistry registry,
        AuthServiceCallerFactory callers, AuthzTokenCache cache) {
        return new AuthServiceFactory(cloudRunIdTokenProvider, registry, callers, cache);
    }

    @Bean
    TypedRestClient typedRestClient(
        ProxyProperties properties,
        AuthServiceFactory authServiceFactory,
        TlsContextFactory tlsContextFactory,
        HistoryService historyService) {
        return new SpringTypedRestClient(properties, RestClient.builder(), authServiceFactory, tlsContextFactory, historyService);
    }

    @Bean
    TypedSoapClient typedSoapClient(ProxyProperties properties, TlsContextFactory tlsContextFactory, AuthServiceFactory authServiceFactory, HistoryService historyService) {
        return new SpringTypedSoapClient(properties, tlsContextFactory, authServiceFactory, historyService);
    }

    @Bean
    OlorinRestClientInjector olorinRestClientInjector(TypedRestClient typedRestClient) {
        return new OlorinRestClientInjector(typedRestClient);
    }
}
