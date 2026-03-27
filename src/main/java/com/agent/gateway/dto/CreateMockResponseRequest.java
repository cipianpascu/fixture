package com.agent.gateway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a new mock response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMockResponseRequest {
    
    private String name;
    
    private String matchConditions;
    
    @NotNull(message = "HTTP status is required")
    @Min(value = 100, message = "HTTP status must be between 100 and 599")
    @Max(value = 599, message = "HTTP status must be between 100 and 599")
    private Integer httpStatus;
    
    private String responseBody;
    
    private String responseHeaders;
    
    @Builder.Default
    @Min(value = 0, message = "Priority must be non-negative")
    private Integer priority = 10;
    
    @Builder.Default
    private Boolean enabled = true;
    
    @Min(value = 0, message = "Delay must be non-negative")
    private Integer delayMs;
}
