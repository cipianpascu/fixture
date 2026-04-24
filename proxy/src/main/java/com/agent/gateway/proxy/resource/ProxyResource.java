package com.agent.gateway.proxy.resource;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.service.ProxyService;
import com.agent.gateway.proxy.service.SchemaValidationService;
import com.agent.gateway.proxy.validation.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Proxy Resource - Production Routing (Quarkus)
 *
 * Simple routing resource with schema validation.
 * NO admin endpoints, NO mocking - pure proxying only.
 */
@Path("/api/v1")
@ApplicationScoped
@Slf4j
public class ProxyResource {

    @Inject
    SchemaValidationService validationService;

    @Inject
    ProxyProperties proxyProperties;

    @Inject
    ProxyService proxyService;

    @Path("/{backendName}/{path:.*}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyGet(
            @PathParam("backendName") String backendName,
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders,
            @Context ContainerRequestContext requestContext) {
        return proxy(backendName, null, toRequestContext(uriInfo, httpHeaders, requestContext));
    }

    @Path("/{backendName}/{path:.*}")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyPost(
            @PathParam("backendName") String backendName,
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders,
            @Context ContainerRequestContext requestContext,
            String requestBody) {
        return proxy(backendName, requestBody, toRequestContext(uriInfo, httpHeaders, requestContext));
    }

    @Path("/{backendName}/{path:.*}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyPut(
            @PathParam("backendName") String backendName,
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders,
            @Context ContainerRequestContext requestContext,
            String requestBody) {
        return proxy(backendName, requestBody, toRequestContext(uriInfo, httpHeaders, requestContext));
    }

    @Path("/{backendName}/{path:.*}")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyDelete(
            @PathParam("backendName") String backendName,
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders,
            @Context ContainerRequestContext requestContext) {
        return proxy(backendName, null, toRequestContext(uriInfo, httpHeaders, requestContext));
    }

    @Path("/{backendName}/{path:.*}")
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyPatch(
            @PathParam("backendName") String backendName,
            @PathParam("path") String path,
            @Context UriInfo uriInfo,
            @Context HttpHeaders httpHeaders,
            @Context ContainerRequestContext requestContext,
            String requestBody) {
        return proxy(backendName, requestBody, toRequestContext(uriInfo, httpHeaders, requestContext));
    }

    private Response proxy(String backendName, String requestBody, ProxyRequestContext requestContext) {
        log.info("Proxying request: {} {}", requestContext.method(), requestContext.requestUri());

        ProxyProperties.BackendDefinition backend = findBackend(backendName);
        if (backend == null) {
            log.warn("Backend not found: {}", backendName);
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Backend not found: " + backendName))
                .build();
        }

        if (!backend.enabled()) {
            log.warn("Backend is disabled: {}", backendName);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("error", "Backend is disabled: " + backendName))
                .build();
        }

        String path = extractPath(requestContext.requestUri(), backendName);

        if (proxyProperties.schemas().validateRequests()) {
            ValidationResult validation = validationService.validateRequest(
                backend.schema().orElse(null),
                requestContext.method(),
                path,
                requestBody,
                requestContext.headers()
            );

            if (!validation.isValid()) {
                log.warn("Request validation failed for {} {}: {}",
                    requestContext.method(), path, validation.getErrors());
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                        "error", "Request validation failed",
                        "details", validation.getErrors()
                    ))
                    .build();
            }
        }

        return proxyService.forward(backend, requestContext, requestBody);
    }

    private ProxyProperties.BackendDefinition findBackend(String name) {
        return proxyProperties.backends().stream()
            .filter(b -> b.name().equals(name))
            .findFirst()
            .orElse(null);
    }

    private String extractPath(String requestUri, String backendName) {
        String prefix = "/api/v1/" + backendName;
        if (requestUri.startsWith(prefix)) {
            return requestUri.substring(prefix.length());
        }
        return requestUri;
    }

    private ProxyRequestContext toRequestContext(
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

        return new ProxyRequestContext(
            requestContext.getMethod(),
            uriInfo.getRequestUri().getPath(),
            uriInfo.getRequestUri().getRawQuery(),
            headers,
            cookies
        );
    }
}
