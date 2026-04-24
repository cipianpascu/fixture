package com.agent.gateway.proxy.service.auth;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Basic Auth Service - Provides Basic Authentication header from configured credentials
 */
@Component
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
    public void enrichHeaders(HttpServletRequest request, HttpHeaders headers) {
        headers.set(HttpHeaders.AUTHORIZATION, basicAuthHeader);
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
