package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.model.ProxyRequestContext;
import com.agent.gateway.proxy.config.ProxyProperties;

import java.util.Map;

/**
 * Auth Service Interface (Quarkus)
 * 
 * Implementations provide different authentication strategies (JWT, Basic, etc.)
 */
public interface AuthService {
    
    /**
     * Enrich request headers with authentication tokens/credentials
     * 
     * @param request The incoming HTTP request context
     * @param headers The headers to enrich (will be modified in place)
     */
    void enrichHeaders(ProxyRequestContext request, Map<String, String> headers, String requestBody);

    /**
     * Enrich request headers with backend context when auth behavior needs backend identity.
     */
    default void enrichHeaders(
        ProxyProperties.BackendDefinition backend,
        ProxyRequestContext request,
        Map<String, String> headers,
        String requestBody) {
        enrichHeaders(request, headers, requestBody);
    }

    /**
     * Optionally transform the outbound request body after auth headers are prepared.
     * Implementations that do not need to modify the body should return it unchanged.
     */
    default String transformRequestBody(
        ProxyRequestContext request,
        Map<String, String> headers,
        String requestBody) {
        return requestBody;
    }
}
