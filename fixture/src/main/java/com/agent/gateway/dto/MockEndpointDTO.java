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
public class MockEndpointDTO {
    private Long id;
    private String backendName;
    private String method;
    private String path;
    private String description;
    private String openApiSchema;
    private Boolean enabled;
    private List<MockResponseDTO> responses;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
