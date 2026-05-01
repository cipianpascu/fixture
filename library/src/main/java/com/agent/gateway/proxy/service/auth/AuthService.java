package com.agent.gateway.proxy.service.auth;

import com.agent.gateway.proxy.model.ProxyRequestContext;

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
    void enrichHeaders(ProxyRequestContext request, Map<String, String> headers);
}
