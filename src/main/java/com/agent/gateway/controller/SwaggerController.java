package com.agent.gateway.controller;

import com.agent.gateway.dto.BackendOpenApiCatalogDTO;
import com.agent.gateway.dto.BackendOpenApiDescriptorDTO;
import com.agent.gateway.entity.BackendConfig;
import com.agent.gateway.service.BackendConfigService;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.List;
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
    public ResponseEntity<List<BackendOpenApiDescriptorDTO>> listBackendsWithSchemas(HttpServletRequest request) {
        List<BackendOpenApiDescriptorDTO> backends = backendConfigService.getAllBackends().stream()
                .filter(b -> b.getOpenApiSchema() != null && !b.getOpenApiSchema().isEmpty())
                .map(b -> toDescriptor(b, request))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(backends);
    }

    @GetMapping("/catalog")
    @Operation(summary = "List backend OpenAPI documents in an agent-friendly catalog")
    public ResponseEntity<BackendOpenApiCatalogDTO> getBackendOpenApiCatalog(HttpServletRequest request) {
        List<BackendOpenApiDescriptorDTO> backends = backendConfigService.getAllBackends().stream()
                .filter(b -> b.getOpenApiSchema() != null && !b.getOpenApiSchema().isEmpty())
                .map(b -> toDescriptor(b, request))
                .collect(Collectors.toList());

        BackendOpenApiCatalogDTO catalog = BackendOpenApiCatalogDTO.builder()
                .serverUrl(baseUrl(request))
                .backendCount(backends.size())
                .generatedAt(LocalDateTime.now())
                .backends(backends)
                .build();

        return ResponseEntity.ok(catalog);
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

        OpenAPI openAPI = new OpenAPIV3Parser().readContents(backend.getOpenApiSchema(), null, null).getOpenAPI();
        if (openAPI == null) {
            log.warn("Backend has invalid OpenAPI schema: {}", backendName);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"Backend has an invalid OpenAPI schema\"}");
        }
        
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Json.pretty(openAPI));
    }

    private BackendOpenApiDescriptorDTO toDescriptor(BackendConfig backend, HttpServletRequest request) {
        String openapiPath = "/api/backends/" + backend.getName() + "/openapi.json";
        String swaggerPath = "/swagger-ui.html?url=" + openapiPath;
        OpenAPI openAPI = new OpenAPIV3Parser().readContents(backend.getOpenApiSchema(), null, null).getOpenAPI();

        return BackendOpenApiDescriptorDTO.builder()
                .name(backend.getName())
                .title(openAPI != null && openAPI.getInfo() != null ? openAPI.getInfo().getTitle() : backend.getName())
                .version(openAPI != null && openAPI.getInfo() != null ? openAPI.getInfo().getVersion() : null)
                .description(openAPI != null && openAPI.getInfo() != null ? openAPI.getInfo().getDescription() : null)
                .backendBaseUrl(backend.getBaseUrl())
                .backendPath(backend.getPath())
                .gatewayPathPrefix("/api/v1/" + backend.getName())
                .securityType(backend.getSecurityType() != null ? backend.getSecurityType().name() : null)
                .enabled(backend.getEnabled())
                .openapiPath(openapiPath)
                .swaggerPath(swaggerPath)
                .openapiUrl(baseUrl(request) + openapiPath)
                .swaggerUrl(baseUrl(request) + swaggerPath)
                .build();
    }

    private String baseUrl(HttpServletRequest request) {
        return ServletUriComponentsBuilder.fromRequestUri(request)
                .replacePath(null)
                .replaceQuery(null)
                .build()
                .toUriString();
    }
}
