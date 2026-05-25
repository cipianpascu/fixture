package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.exception.AuthenticationDeniedException;
import com.agent.gateway.proxy.exception.AuthenticationRequiredException;
import com.agent.gateway.proxy.exception.AuthorizationDeniedException;
import com.agent.gateway.proxy.exception.ProxyConfigurationException;
import com.agent.gateway.proxy.exception.SoapFaultException;
import com.agent.gateway.proxy.exception.UpstreamProxyException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException;

import java.util.Map;

@ApplicationScoped
@Slf4j
public class BackendInvocationFailureMapper {

    public Response toResponse(String backendName, Throwable failure, String genericMessage) {
        log.error("Backend invocation failed for {}", backendName, failure);

        if (failure instanceof ProxyConfigurationException configurationException) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(Map.of("error", configurationException.getMessage()))
                .build();
        }
        if (failure instanceof AuthenticationRequiredException authenticationRequiredException) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", authenticationRequiredException.getMessage()))
                .build();
        }
        if (failure instanceof AuthenticationDeniedException authenticationDeniedException) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(Map.of("error", authenticationDeniedException.getMessage()))
                .build();
        }
        if (failure instanceof AuthorizationDeniedException authorizationDeniedException) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(Map.of("error", authorizationDeniedException.getMessage()))
                .build();
        }
        if (failure instanceof CircuitBreakerOpenException) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("error", "Backend '%s' is temporarily unavailable".formatted(backendName)))
                .build();
        }
        if (failure instanceof SoapFaultException soapFaultException) {
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(Map.of("error", soapFaultException.getMessage()))
                .build();
        }
        if (failure instanceof UpstreamProxyException upstreamProxyException) {
            return Response.status(Response.Status.BAD_GATEWAY)
                .entity(Map.of("error", upstreamProxyException.getMessage()))
                .build();
        }

        return Response.status(Response.Status.BAD_GATEWAY)
            .entity(Map.of("error", genericMessage.formatted(backendName)))
            .build();
    }
}
