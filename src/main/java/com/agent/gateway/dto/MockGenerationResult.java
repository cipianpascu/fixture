package com.agent.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for mock generation operations
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockGenerationResult {
    private Integer endpointsGenerated;
    private Integer responsesGenerated;
    private List<MockEndpointDTO> endpoints;
    private String message;
}
