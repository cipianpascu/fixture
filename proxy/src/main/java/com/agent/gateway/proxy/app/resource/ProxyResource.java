package com.agent.gateway.proxy.app.resource;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.resource.BaseResource;
import com.agent.gateway.proxy.validation.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.core.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Proxy Resource - Production Routing (Quarkus)
 *
 * Simple routing resource with schema validation.
 * NO admin endpoints, NO mocking - pure proxying only.
 */
@Path("/api/v1")
@ApplicationScoped
@Slf4j
public class ProxyResource extends BaseResource {

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
            return backendNotFound(backendName);
        }

        if (!isBackendEnabled(backend)) {
            return backendDisabled(backendName);
        }

        String path = extractContractPath(requestContext.requestUri(), "/api/v1/" + backendName);

        if (proxyProperties.schemas().validateRequests()) {
            ValidationResult validation = validateContract(
                backend.schema().orElse(null),
                path,
                requestContext,
                requestBody
            );

            if (!validation.isValid()) {
                return requestValidationFailed(requestContext, path, validation);
            }
        }

        Response response = forward(backend, requestContext, requestBody);
        return applyResponseContract(backend.schema().orElse(null), path, requestContext, response);
    }
}
