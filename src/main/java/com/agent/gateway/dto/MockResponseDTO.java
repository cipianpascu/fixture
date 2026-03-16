package com.agent.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockResponseDTO {
    private Long id;
    private String name;
    private String matchConditions;
    private Integer httpStatus;
    private String responseBody;
    private String responseHeaders;
    private Integer priority;
    private Boolean enabled;
    private Integer delayMs;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
