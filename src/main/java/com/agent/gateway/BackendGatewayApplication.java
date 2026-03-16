package com.agent.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties
public class BackendGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendGatewayApplication.class, args);
    }
}
