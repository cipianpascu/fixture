package com.agent.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for schema generation from mocks
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaGenerationResult {
    private BackendConfigDTO backend;
    private Integer endpointCount;
    private String schema;
    private String message;
}
