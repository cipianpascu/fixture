package com.agent.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard error response format for all API errors
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    private String error;           // Error type (e.g., "ValidationError", "NotFound")
    private String message;         // Human-readable error message
    private String path;            // Request path that caused the error
    private Integer status;         // HTTP status code
    private LocalDateTime timestamp; // When the error occurred
    private List<String> details;   // Additional details (e.g., validation errors)
}
