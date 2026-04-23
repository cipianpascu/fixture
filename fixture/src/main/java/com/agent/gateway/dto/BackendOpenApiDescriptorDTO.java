package com.agent.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackendOpenApiDescriptorDTO {
    private String name;
    private String title;
    private String version;
    private String description;
    private String backendBaseUrl;
    private String backendPath;
    private String gatewayPathPrefix;
    private String securityType;
    private Boolean enabled;
    private String openapiUrl;
    private String swaggerUrl;
    private String openapiPath;
    private String swaggerPath;
}
