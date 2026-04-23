package com.agent.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new mock endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMockEndpointRequest {
    
    @NotBlank(message = "Backend name is required")
    private String backendName;
    
    @NotBlank(message = "HTTP method is required")
    @Pattern(regexp = "GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS", message = "Invalid HTTP method")
    private String method;
    
    @NotBlank(message = "Path is required")
    @Pattern(regexp = "^/.*", message = "Path must start with /")
    private String path;
    
    private String description;
    
    private String openApiSchema;
    
    @Builder.Default
    private Boolean enabled = true;
}
