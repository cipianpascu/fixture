package com.agent.gateway.proxy.resource;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.service.BackendInvocationFailureMapper;
import com.agent.gateway.proxy.service.ProxyService;
import com.agent.gateway.proxy.service.SchemaValidationService;
import com.agent.gateway.proxy.service.SoapBackendService;
import com.agent.gateway.proxy.validation.ValidationResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
public abstract class BaseResource {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    protected SchemaValidationService validationService;

    @Inject
    protected ProxyProperties proxyProperties;

    @Inject
    protected ProxyService proxyService;

    @Inject
    protected SoapBackendService soapBackendService;

    @Inject
    protected BackendInvocationFailureMapper backendInvocationFailureMapper;

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

    protected Response defaultProxy(
        String backendName,
        String schemaName,
        String contractPath,
        ProxyRequestContext requestContext,
        String requestBody) {
        ProxyProperties.BackendDefinition backend = findBackend(backendName);
        if (backend == null) {
            return backendNotFound(backendName);
        }

        if (!isBackendEnabled(backend)) {
            return backendDisabled(backendName);
        }

        if (!supportsDefaultProxy(backend)) {
            return protocolNotSupported(backendName, backend.protocol());
        }

        if (proxyProperties.schemas().validateRequests()) {
            ValidationResult validation = validateContract(
                schemaName,
                contractPath,
                requestContext,
                requestBody
            );

            if (!validation.isValid()) {
                return requestValidationFailed(requestContext, contractPath, validation);
            }
        }

        Response response = forward(backend, requestContext, requestBody);
        return applyResponseContract(schemaName, contractPath, requestContext, response);
    }

    protected Optional<JsonNode> parseSuccessfulJsonResponse(Response response) {
        if (response == null || response.getStatusInfo().getFamily() != Response.Status.Family.SUCCESSFUL) {
            return Optional.empty();
        }

        MediaType mediaType = response.getMediaType();
        if (mediaType != null && !mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            return Optional.empty();
        }

        try {
            return Optional.of(parseJsonEntity(response));
        } catch (Exception e) {
            log.warn("Failed to parse successful JSON response", e);
            return Optional.empty();
        }
    }

    protected JsonNode parseJsonEntity(Response response) throws Exception {
        Object entity = response.getEntity();
        if (entity == null) {
            return objectMapper.nullNode();
        }
        if (entity instanceof JsonNode jsonNode) {
            return jsonNode;
        }
        if (entity instanceof byte[] bytes) {
            return objectMapper.readTree(bytes);
        }
        if (entity instanceof String text) {
            return objectMapper.readTree(text);
        }
        return objectMapper.valueToTree(entity);
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

    protected boolean supportsDefaultProxy(ProxyProperties.BackendDefinition backend) {
        return backend != null && "rest".equalsIgnoreCase(backend.protocol());
    }

    protected Response protocolNotSupported(String backendName, String protocol) {
        log.warn("Backend {} uses unsupported default-proxy protocol {}", backendName, protocol);
        return Response.status(Response.Status.BAD_REQUEST)
            .entity(Map.of(
                "error",
                "Backend '%s' uses protocol '%s' and must be handled by a concrete resource"
                    .formatted(backendName, protocol)
            ))
            .build();
    }

    protected Response backendInvocationFailed(String backendName, Throwable failure) {
        return backendInvocationFailureMapper.toResponse(
            backendName,
            failure,
            "Failed to invoke backend '%s'"
        );
    }

    protected ProxyRequestContext toRequestContext(
        UriInfo uriInfo,
        HttpHeaders httpHeaders,
        ContainerRequestContext requestContext) {
        Map<String, List<String>> headers = new HashMap<>();
        httpHeaders.getRequestHeaders().forEach((key, values) -> {
            if (!values.isEmpty()) {
                headers.put(key.toLowerCase(Locale.ROOT), List.copyOf(values));
            }
        });

        Map<String, String> cookies = new HashMap<>();
        httpHeaders.getCookies().forEach((key, cookie) -> cookies.put(key, cookie.getValue()));

        return buildRequestContextWithHeaderLists(
            requestContext.getMethod(),
            uriInfo.getRequestUri().getPath(),
            uriInfo.getRequestUri().getRawQuery(),
            headers,
            cookies
        );
    }

    protected ProxyRequestContext buildRequestContextWithHeaderLists(
        String method,
        String requestUri,
        String queryString,
        Map<String, List<String>> headers,
        Map<String, String> cookies) {
        Map<String, List<String>> normalizedHeaders = new HashMap<>();
        headers.forEach((key, values) -> normalizedHeaders.put(key.toLowerCase(Locale.ROOT), List.copyOf(values)));
        return new ProxyRequestContext(
            method,
            requestUri,
            queryString,
            normalizedHeaders,
            new HashMap<>(cookies)
        );
    }

    protected ProxyRequestContext buildRequestContext(
        String method,
        String requestUri,
        String queryString,
        Map<String, String> headers,
        Map<String, String> cookies) {
        Map<String, List<String>> normalizedHeaders = new HashMap<>();
        headers.forEach((key, value) -> normalizedHeaders.put(key.toLowerCase(Locale.ROOT), List.of(value)));
        return buildRequestContextWithHeaderLists(method, requestUri, queryString, normalizedHeaders, cookies);
    }

    protected ProxyRequestContext deriveRequestContext(
        ProxyRequestContext source,
        String method,
        String requestUri,
        String queryString) {
        return buildRequestContextWithHeaderLists(
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
