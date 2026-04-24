package com.agent.gateway.proxy.resource;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.service.ProxyService;
import com.agent.gateway.proxy.service.SchemaValidationService;
import com.agent.gateway.proxy.validation.ValidationResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
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
    
    @Context
    HttpServletRequest servletRequest;
    
    @Path("/{backendName}/{path:.*}")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyGet(
            @PathParam("backendName") String backendName,
            @PathParam("path") String path) {
        return proxy(backendName, null);
    }
    
    @Path("/{backendName}/{path:.*}")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyPost(
            @PathParam("backendName") String backendName,
            @PathParam("path") String path,
            String requestBody) {
        return proxy(backendName, requestBody);
    }
    
    @Path("/{backendName}/{path:.*}")
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyPut(
            @PathParam("backendName") String backendName,
            @PathParam("path") String path,
            String requestBody) {
        return proxy(backendName, requestBody);
    }
    
    @Path("/{backendName}/{path:.*}")
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyDelete(
            @PathParam("backendName") String backendName,
            @PathParam("path") String path) {
        return proxy(backendName, null);
    }
    
    @Path("/{backendName}/{path:.*}")
    @PATCH
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response proxyPatch(
            @PathParam("backendName") String backendName,
            @PathParam("path") String path,
            String requestBody) {
        return proxy(backendName, requestBody);
    }
    
    private Response proxy(String backendName, String requestBody) {
        log.info("Proxying request: {} {}", servletRequest.getMethod(), servletRequest.getRequestURI());
        
        // 1. Find backend configuration
        ProxyProperties.BackendDefinition backend = findBackend(backendName);
        if (backend == null) {
            log.warn("Backend not found: {}", backendName);
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Backend not found: " + backendName))
                .build();
        }
        
        // 2. Check if backend is enabled
        if (!backend.enabled()) {
            log.warn("Backend is disabled: {}", backendName);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .entity(Map.of("error", "Backend is disabled: " + backendName))
                .build();
        }
        
        // 3. Extract path
        String path = extractPath(servletRequest, backendName);
        
        // 4. Validate request against schema (if validation enabled)
        if (proxyProperties.schemas().validateRequests()) {
            ValidationResult validation = validationService.validateRequest(
                backend.schema().orElse(null),
                servletRequest.getMethod(),
                path,
                requestBody,
                getHeaders(servletRequest)
            );
            
            if (!validation.isValid()) {
                log.warn("Request validation failed for {} {}: {}", 
                    servletRequest.getMethod(), path, validation.getErrors());
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of(
                        "error", "Request validation failed",
                        "details", validation.getErrors()
                    ))
                    .build();
            }
        }
        
        // 5. Forward to backend
        return proxyService.forward(backend, servletRequest, requestBody);
    }
    
    // Helper methods
    
    private ProxyProperties.BackendDefinition findBackend(String name) {
        return proxyProperties.backends().stream()
            .filter(b -> b.name().equals(name))
            .findFirst()
            .orElse(null);
    }
    
    private String extractPath(HttpServletRequest request, String backendName) {
        String requestURI = request.getRequestURI();
        String prefix = "/api/v1/" + backendName;
        if (requestURI.startsWith(prefix)) {
            return requestURI.substring(prefix.length());
        }
        return requestURI;
    }
    
    private Map<String, String> getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        var headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers;
    }
}
