package com.agent.gateway.dto;

import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing mock endpoint
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMockEndpointRequest {
    
    private String backendName;
    
    @Pattern(regexp = "GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS", message = "Invalid HTTP method")
    private String method;
    
    @Pattern(regexp = "^/.*", message = "Path must start with /")
    private String path;
    
    private String description;
    
    private String openApiSchema;
    
    private Boolean enabled;
}
