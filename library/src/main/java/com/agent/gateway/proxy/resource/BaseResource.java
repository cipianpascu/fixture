package com.agent.gateway.proxy.resource;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.service.ProxyService;
import com.agent.gateway.proxy.service.SchemaValidationService;
import com.agent.gateway.proxy.validation.ValidationResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class BaseResource {

    @Inject
    protected SchemaValidationService validationService;

    @Inject
    protected ProxyProperties proxyProperties;

    @Inject
    protected ProxyService proxyService;

    protected ProxyProperties.BackendDefinition findBackend(String name) {
        return proxyProperties.backends().stream()
            .filter(backend -> backend.name().equals(name))
            .findFirst()
            .orElse(null);
    }

    protected boolean isBackendEnabled(ProxyProperties.BackendDefinition backend) {
        return backend != null && backend.enabled();
    }

    protected ValidationResult validateContract(
        String schemaName,
        String contractPath,
        ProxyRequestContext requestContext,
        String requestBody) {
        return validationService.validateRequest(
            schemaName,
            contractPath,
            requestContext,
            requestBody,
            requestContext.method()
        );
    }

    protected Response forward(
        ProxyProperties.BackendDefinition backend,
        ProxyRequestContext requestContext,
        String requestBody) {
        return proxyService.forward(backend, requestContext, requestBody);
    }

    protected Response applyResponseContract(
        String schemaName,
        String contractPath,
        ProxyRequestContext requestContext,
        Response response) {
        return validationService.applyResponseContract(
            schemaName,
            requestContext.method(),
            contractPath,
            response
        );
    }

    protected Response backendNotFound(String backendName) {
        log.warn("Backend not found: {}", backendName);
        return Response.status(Response.Status.NOT_FOUND)
            .entity(Map.of("error", "Backend not found: " + backendName))
            .build();
    }

    protected Response backendDisabled(String backendName) {
        log.warn("Backend is disabled: {}", backendName);
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
            .entity(Map.of("error", "Backend is disabled: " + backendName))
            .build();
    }

    protected Response requestValidationFailed(
        ProxyRequestContext requestContext,
        String contractPath,
        ValidationResult validation) {
        log.warn(
            "Request validation failed for {} {}: {}",
            requestContext.method(),
            contractPath,
            validation.getErrors()
        );
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of(
                "error", "Request validation failed",
                "details", validation.getErrors()
            ))
            .build();
    }

    protected ProxyRequestContext toRequestContext(
        UriInfo uriInfo,
        HttpHeaders httpHeaders,
        ContainerRequestContext requestContext) {
        Map<String, String> headers = new HashMap<>();
        httpHeaders.getRequestHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                headers.put(key, values.get(0));
            }
        });

        Map<String, String> cookies = new HashMap<>();
        httpHeaders.getCookies().forEach((key, cookie) -> cookies.put(key, cookie.getValue()));

        return buildRequestContext(
            requestContext.getMethod(),
            uriInfo.getRequestUri().getPath(),
            uriInfo.getRequestUri().getRawQuery(),
            headers,
            cookies
        );
    }

    protected ProxyRequestContext buildRequestContext(
        String method,
        String requestUri,
        String queryString,
        Map<String, String> headers,
        Map<String, String> cookies) {
        return new ProxyRequestContext(
            method,
            requestUri,
            queryString,
            new HashMap<>(headers),
            new HashMap<>(cookies)
        );
    }

    protected ProxyRequestContext deriveRequestContext(
        ProxyRequestContext source,
        String method,
        String requestUri,
        String queryString) {
        return buildRequestContext(
            method != null ? method : source.method(),
            requestUri != null ? requestUri : source.requestUri(),
            queryString != null ? queryString : source.queryString(),
            source.headers(),
            source.cookies()
        );
    }

    protected String extractContractPath(String requestUri, String routePrefix) {
        if (requestUri.startsWith(routePrefix)) {
            return requestUri.substring(routePrefix.length());
        }
        return requestUri;
    }
}
