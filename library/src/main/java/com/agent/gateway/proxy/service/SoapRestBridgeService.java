package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.function.Function;

@ApplicationScoped
@Slf4j
public class SoapRestBridgeService {

    public record SoapInvocation<O>(
        Object soapRequest,
        String soapActionOverride,
        Class<O> responseType
    ) {
    }

    @Inject
    ProxyProperties proxyProperties;

    @Inject
    SoapBackendService soapBackendService;

    @Inject
    BackendInvocationFailureMapper failureMapper;

    public <I, O> Response invoke(
        String backendName,
        ProxyRequestContext requestContext,
        I input,
        Function<I, SoapInvocation<O>> inputMapper,
        Function<O, Response> successMapper) {

        ProxyProperties.BackendDefinition backend = findBackend(backendName);
        if (backend == null) {
            return backendNotFound(backendName);
        }
        if (!backend.enabled()) {
            return backendDisabled(backendName);
        }
        if (!"soap".equalsIgnoreCase(backend.protocol())) {
            return protocolNotSupported(backendName, backend.protocol());
        }

        try {
            SoapInvocation<O> invocation = inputMapper.apply(input);
            O soapResponse = soapBackendService.invoke(
                backend,
                requestContext,
                invocation.soapRequest(),
                invocation.soapActionOverride(),
                invocation.responseType()
            );
            return successMapper.apply(soapResponse);
        } catch (RuntimeException e) {
            return failureMapper.toResponse(backendName, e, "Failed to invoke backend '%s'");
        }
    }

    private ProxyProperties.BackendDefinition findBackend(String backendName) {
        return proxyProperties.backends().stream()
            .filter(backend -> backend.name().equals(backendName))
            .findFirst()
            .orElse(null);
    }

    private Response backendNotFound(String backendName) {
        log.warn("Backend not found: {}", backendName);
        return Response.status(Response.Status.NOT_FOUND)
            .entity(Map.of("error", "Backend not found: " + backendName))
            .build();
    }

    private Response backendDisabled(String backendName) {
        log.warn("Backend is disabled: {}", backendName);
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
            .entity(Map.of("error", "Backend is disabled: " + backendName))
            .build();
    }

    private Response protocolNotSupported(String backendName, String protocol) {
        log.warn("Backend {} uses unsupported SOAP bridge protocol {}", backendName, protocol);
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of(
                "error",
                "Backend '%s' uses protocol '%s' and must be handled by a matching concrete resource"
                    .formatted(backendName, protocol)
            ))
            .build();
    }
}
