package com.agent.gateway.controller;

import com.agent.gateway.entity.BackendConfig;
import com.agent.gateway.service.BackendConfigService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/backends")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Backend OpenAPI", description = "Per-backend OpenAPI specification endpoints")
public class SwaggerController {

    private final BackendConfigService backendConfigService;

    /**
     * List all backends with OpenAPI schemas
     * Returns backend information for use with Swagger UI
     */
    @GetMapping("/with-schemas")
    @Operation(summary = "List all backends with OpenAPI schemas")
    public ResponseEntity<List<Map<String, Object>>> listBackendsWithSchemas() {
        List<Map<String, Object>> backends = backendConfigService.getAllBackends().stream()
                .filter(b -> b.getOpenApiSchema() != null && !b.getOpenApiSchema().isEmpty())
                .map(b -> {
                    Map<String, Object> info = new HashMap<>();
                    info.put("name", b.getName());
                    info.put("baseUrl", b.getBaseUrl());
                    info.put("path", b.getPath());
                    info.put("securityType", b.getSecurityType());
                    info.put("enabled", b.getEnabled());
                    info.put("openapiUrl", "/api/backends/" + b.getName() + "/openapi.json");
                    info.put("swaggerUrl", "/swagger-ui.html?url=/api/backends/" + b.getName() + "/openapi.json");
                    return info;
                })
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(backends);
    }

    /**
     * Serve the OpenAPI specification for a specific backend
     * Can be used directly with Swagger UI
     */
    @GetMapping(value = "/{backendName}/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get OpenAPI specification for a backend")
    @Hidden
    public ResponseEntity<String> getOpenApiSpec(@PathVariable String backendName) {
        Optional<BackendConfig> backendOpt = backendConfigService.getBackendByName(backendName);
        
        if (backendOpt.isEmpty()) {
            log.warn("Backend not found: {}", backendName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\":\"Backend not found: " + backendName + "\"}");
        }
        
        BackendConfig backend = backendOpt.get();
        if (backend.getOpenApiSchema() == null || backend.getOpenApiSchema().isEmpty()) {
            log.warn("Backend has no OpenAPI schema: {}", backendName);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("{\"error\":\"Backend has no OpenAPI schema\"}");
        }
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(backend.getOpenApiSchema());
    }
}
