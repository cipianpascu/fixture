package com.agent.gateway.dto;

import com.agent.gateway.model.SecurityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackendConfigDTO {
    private Long id;
    private String name;
    private String baseUrl;
    private String path;
    private SecurityType securityType;
    private String securityConfig;
    private Boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
