package com.agent.gateway.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating an existing mock response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMockResponseRequest {
    
    private String name;
    
    private String matchConditions;
    
    @Min(value = 100, message = "HTTP status must be between 100 and 599")
    @Max(value = 599, message = "HTTP status must be between 100 and 599")
    private Integer httpStatus;
    
    private String responseBody;
    
    private String responseHeaders;
    
    @Min(value = 0, message = "Priority must be non-negative")
    private Integer priority;
    
    private Boolean enabled;
    
    @Min(value = 0, message = "Delay must be non-negative")
    private Integer delayMs;
}
