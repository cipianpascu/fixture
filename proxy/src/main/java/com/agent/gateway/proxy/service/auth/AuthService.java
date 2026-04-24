package com.agent.gateway.proxy.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;

/**
 * Auth Service Interface
 * 
 * Implementations provide different authentication strategies (JWT, Basic, etc.)
 */
public interface AuthService {
    
    /**
     * Enrich request headers with authentication tokens/credentials
     * 
     * @param request The incoming HTTP request
     * @param headers The headers to enrich (will be modified in place)
     */
    void enrichHeaders(HttpServletRequest request, HttpHeaders headers);
}
