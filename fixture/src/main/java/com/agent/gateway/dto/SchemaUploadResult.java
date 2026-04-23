package com.agent.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for schema upload operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaUploadResult {
    
    private BackendConfigDTO backend;
    private String message;
    private ValidationSummary validation;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationSummary {
        private Integer mocksRevalidated;
        private Integer mocksDisabled;
        private Integer mocksDeleted;
    }
}
