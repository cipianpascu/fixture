package com.agent.gateway.proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Backend Gateway Proxy Application
 * 
 * Lightweight production routing module with schema validation.
 * NO database, NO admin API, file-based configuration only.
 */
@SpringBootApplication
public class BackendGatewayProxyApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(BackendGatewayProxyApplication.class, args);
    }
}
