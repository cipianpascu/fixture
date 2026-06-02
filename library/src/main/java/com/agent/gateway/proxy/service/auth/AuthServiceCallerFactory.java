package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.service.TlsContextFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AuthServiceCallerFactory {

    private final Map<String, AuthServiceCaller> callers = new ConcurrentHashMap<>();

    @Inject
    AuthServiceConfigRegistry authServiceConfigRegistry;

    @Inject
    TlsContextFactory tlsContextFactory;

    @Inject
    CloudRunIdTokenProvider cloudRunIdTokenProvider;

    public AuthServiceCaller get(String serviceName) {
        return callers.computeIfAbsent(serviceName, name -> new AuthServiceCaller(
            authServiceConfigRegistry.get(name),
            tlsContextFactory,
            cloudRunIdTokenProvider
        ));
    }

    public ResolvedAuthServiceConfig getConfig(String serviceName) {
        return authServiceConfigRegistry.get(serviceName);
    }
}
