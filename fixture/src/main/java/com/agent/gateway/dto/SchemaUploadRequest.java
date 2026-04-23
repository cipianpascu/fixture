package com.agent.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaUploadRequest {
    private String openApiSchema; // OpenAPI 3.0 specification content (JSON or YAML)
    private SchemaMigrationOption migrationOption;

    public enum SchemaMigrationOption {
        DELETE_MOCKS,           // Delete all existing mocks
        REVALIDATE_AND_DISABLE  // Revalidate and disable incompatible mocks
    }
}
