package com.db.olorin.rest.service.auth;

import com.db.olorin.rest.model.ProxyRequestContext;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Basic Auth Service - Provides Basic Authentication header from configured credentials (Quarkus)
 */
@Slf4j
public class BasicAuthService implements AuthService {
    
    private final String username;
    private final String password;
    private final String basicAuthHeader;
    
    public BasicAuthService(String username, String password) {
        this.username = username;
        this.password = password;
        this.basicAuthHeader = createBasicAuthHeader(username, password);
    }
    
    @Override
    public void enrichHeaders(ProxyRequestContext request, Map<String, String> headers, String requestBody) {
        headers.put("Authorization", basicAuthHeader);
        log.debug("Attached Basic auth header for user: {}", username);
    }
    
    /**
     * Create Basic Authentication header value
     */
    private String createBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }
}
