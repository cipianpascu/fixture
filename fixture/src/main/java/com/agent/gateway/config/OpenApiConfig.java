package com.agent.gateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Backend Gateway API")
                        .version("1.0.0")
                        .description("API Gateway for routing and mocking multiple backend services")
                        .contact(new Contact()
                                .name("Backend Gateway Team")
                                .email("support@example.com")));
    }
}
