package com.agent.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackendOpenApiCatalogDTO {
    private String serverUrl;
    private Integer backendCount;
    private LocalDateTime generatedAt;
    private List<BackendOpenApiDescriptorDTO> backends;
}
