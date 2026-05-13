package com.agent.gateway.proxy.app.resource;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.resource.BaseResource;
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
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED, MediaType.WILDCARD})
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
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED, MediaType.WILDCARD})
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
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED, MediaType.WILDCARD})
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

        String path = extractContractPath(requestContext.requestUri(), "/api/v1/" + backendName);
        ProxyProperties.BackendDefinition backend = findBackend(backendName);
        String schemaName = backend != null ? backend.schema().orElse(null) : null;
        return defaultProxy(backendName, schemaName, path, requestContext, requestBody);
    }
}
