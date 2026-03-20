package com.agent.gateway.controller;

import com.agent.gateway.dto.*;
import jakarta.validation.Valid;
import com.agent.gateway.entity.BackendConfig;
import com.agent.gateway.entity.MockEndpoint;
import com.agent.gateway.entity.MockResponse;
import com.agent.gateway.repository.MockEndpointRepository;
import com.agent.gateway.service.BackendConfigService;
import com.agent.gateway.service.MockGeneratorService;
import com.agent.gateway.service.MockService;
import com.agent.gateway.service.SchemaGeneratorService;
import com.agent.gateway.service.SchemaManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin API", description = "Administrative endpoints for managing backends and mocks")
public class AdminController {

    private final BackendConfigService backendConfigService;
    private final MockService mockService;
    private final SchemaManagementService schemaManagementService;
    private final MockGeneratorService mockGeneratorService;
    private final SchemaGeneratorService schemaGeneratorService;
    private final MockEndpointRepository mockEndpointRepository;

    // ============== Backend Management ==============
    
    @GetMapping("/backends")
    @Operation(summary = "Get all backends")
    public ResponseEntity<List<BackendConfigDTO>> getAllBackends() {
        return ResponseEntity.ok(backendConfigService.getAllBackends().stream()
                .map(this::toBackendConfigDto)
                .collect(Collectors.toList()));
    }

    @GetMapping("/backends/{id}")
    @Operation(summary = "Get backend by ID")
    public ResponseEntity<BackendConfigDTO> getBackendById(@PathVariable Long id) {
        return backendConfigService.getAllBackends().stream()
                .filter(b -> b.getId().equals(id))
                .findFirst()
                .map(this::toBackendConfigDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/backends")
    @Operation(summary = "Create new backend")
    public ResponseEntity<BackendConfigDTO> createBackend(@Valid @RequestBody BackendConfig backendConfig) {
        try {
            BackendConfig created = backendConfigService.createBackend(backendConfig);
            return ResponseEntity.status(HttpStatus.CREATED).body(toBackendConfigDto(created));
        } catch (IllegalArgumentException e) {
            log.error("Error creating backend", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/backends/{id}")
    @Operation(summary = "Update backend")
    public ResponseEntity<BackendConfigDTO> updateBackend(@PathVariable Long id, 
                                                       @Valid @RequestBody BackendConfig backendConfig) {
        try {
            BackendConfig updated = backendConfigService.updateBackend(id, backendConfig);
            return ResponseEntity.ok(toBackendConfigDto(updated));
        } catch (IllegalArgumentException e) {
            log.error("Error updating backend", e);
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/backends/{id}")
    @Operation(summary = "Delete backend")
    public ResponseEntity<Void> deleteBackend(@PathVariable Long id) {
        backendConfigService.deleteBackend(id);
        return ResponseEntity.noContent().build();
    }

    // ============== Mock Endpoint Management ==============
    
    @GetMapping("/mock-endpoints")
    @Operation(summary = "Get all mock endpoints")
    public ResponseEntity<List<MockEndpointDTO>> getAllMockEndpoints() {
        return ResponseEntity.ok(mockService.getAllMockEndpoints().stream()
                .map(this::toMockEndpointDto)
                .collect(Collectors.toList()));
    }

    @GetMapping("/mock-endpoints/{id}")
    @Operation(summary = "Get mock endpoint by ID")
    public ResponseEntity<MockEndpointDTO> getMockEndpointById(@PathVariable Long id) {
        return mockService.getMockEndpointById(id)
                .map(this::toMockEndpointDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/mock-endpoints/backend/{backendName}")
    @Operation(summary = "Get mock endpoints by backend name")
    public ResponseEntity<List<MockEndpointDTO>> getMockEndpointsByBackend(@PathVariable String backendName) {
        return ResponseEntity.ok(mockService.getMockEndpointsByBackend(backendName).stream()
                .map(this::toMockEndpointDto)
                .collect(Collectors.toList()));
    }

    @PostMapping("/mock-endpoints")
    @Operation(summary = "Create new mock endpoint")
    public ResponseEntity<MockEndpointDTO> createMockEndpoint(@Valid @RequestBody CreateMockEndpointRequest request) {
        MockEndpoint mockEndpoint = MockEndpoint.builder()
                .backendName(request.getBackendName())
                .method(request.getMethod())
                .path(request.getPath())
                .description(request.getDescription())
                .openApiSchema(request.getOpenApiSchema())
                .enabled(request.getEnabled())
                .build();
        MockEndpoint created = mockService.createMockEndpoint(mockEndpoint);
        return ResponseEntity.status(HttpStatus.CREATED).body(toMockEndpointDto(created));
    }

    @PutMapping("/mock-endpoints/{id}")
    @Operation(summary = "Update mock endpoint")
    public ResponseEntity<MockEndpointDTO> updateMockEndpoint(@PathVariable Long id,
                                                              @Valid @RequestBody UpdateMockEndpointRequest request) {
        MockEndpoint existing = mockService.getMockEndpointById(id)
                .orElseThrow(() -> new IllegalArgumentException("MockEndpoint not found with id: " + id));
        
        if (request.getBackendName() != null) existing.setBackendName(request.getBackendName());
        if (request.getMethod() != null) existing.setMethod(request.getMethod());
        if (request.getPath() != null) existing.setPath(request.getPath());
        if (request.getDescription() != null) existing.setDescription(request.getDescription());
        if (request.getOpenApiSchema() != null) existing.setOpenApiSchema(request.getOpenApiSchema());
        if (request.getEnabled() != null) existing.setEnabled(request.getEnabled());
        
        MockEndpoint updated = mockService.updateMockEndpoint(id, existing);
        return ResponseEntity.ok(toMockEndpointDto(updated));
    }

    @DeleteMapping("/mock-endpoints/{id}")
    @Operation(summary = "Delete mock endpoint")
    public ResponseEntity<Void> deleteMockEndpoint(@PathVariable Long id) {
        mockService.deleteMockEndpoint(id);
        return ResponseEntity.noContent().build();
    }

    // ============== Mock Response Management ==============
    
    @GetMapping("/mock-endpoints/{endpointId}/responses")
    @Operation(summary = "Get all responses for an endpoint")
    public ResponseEntity<List<MockResponseDTO>> getResponsesByEndpoint(@PathVariable Long endpointId) {
        return ResponseEntity.ok(mockService.getResponsesByEndpoint(endpointId).stream()
                .map(this::toMockResponseDto)
                .collect(Collectors.toList()));
    }

    @GetMapping("/mock-responses/{id}")
    @Operation(summary = "Get mock response by ID")
    public ResponseEntity<MockResponseDTO> getMockResponseById(@PathVariable Long id) {
        return mockService.getMockResponseById(id)
                .map(this::toMockResponseDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/mock-endpoints/{endpointId}/responses")
    @Operation(summary = "Create new mock response for an endpoint")
    public ResponseEntity<MockResponseDTO> createMockResponse(@PathVariable Long endpointId,
                                                              @Valid @RequestBody CreateMockResponseRequest request) {
        MockResponse mockResponse = MockResponse.builder()
                .name(request.getName())
                .matchConditions(request.getMatchConditions())
                .httpStatus(request.getHttpStatus())
                .responseBody(request.getResponseBody())
                .responseHeaders(request.getResponseHeaders())
                .priority(request.getPriority())
                .enabled(request.getEnabled())
                .delayMs(request.getDelayMs())
                .build();
        MockResponse created = mockService.createMockResponse(endpointId, mockResponse);
        return ResponseEntity.status(HttpStatus.CREATED).body(toMockResponseDto(created));
    }

    @PutMapping("/mock-responses/{id}")
    @Operation(summary = "Update mock response")
    public ResponseEntity<MockResponseDTO> updateMockResponse(@PathVariable Long id,
                                                              @Valid @RequestBody UpdateMockResponseRequest request) {
        MockResponse existing = mockService.getMockResponseById(id)
                .orElseThrow(() -> new IllegalArgumentException("MockResponse not found with id: " + id));
        
        if (request.getName() != null) existing.setName(request.getName());
        if (request.getMatchConditions() != null) existing.setMatchConditions(request.getMatchConditions());
        if (request.getHttpStatus() != null) existing.setHttpStatus(request.getHttpStatus());
        if (request.getResponseBody() != null) existing.setResponseBody(request.getResponseBody());
        if (request.getResponseHeaders() != null) existing.setResponseHeaders(request.getResponseHeaders());
        if (request.getPriority() != null) existing.setPriority(request.getPriority());
        if (request.getEnabled() != null) existing.setEnabled(request.getEnabled());
        if (request.getDelayMs() != null) existing.setDelayMs(request.getDelayMs());
        
        MockResponse updated = mockService.updateMockResponse(id, existing);
        return ResponseEntity.ok(toMockResponseDto(updated));
    }

    @DeleteMapping("/mock-responses/{id}")
    @Operation(summary = "Delete mock response")
    public ResponseEntity<Void> deleteMockResponse(@PathVariable Long id) {
        mockService.deleteMockResponse(id);
        return ResponseEntity.noContent().build();
    }

    // ============== OpenAPI Schema Management ==============

    @PostMapping(value = "/backends/{id}/schema", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload OpenAPI schema for a backend")
    public ResponseEntity<SchemaUploadResult> uploadSchema(
            @PathVariable Long id,
            @RequestPart("schemaFile") MultipartFile schemaFile,
            @RequestPart(value = "migrationOption", required = false) String migrationOption) {
        try {
            SchemaUploadRequest request = SchemaUploadRequest.builder()
                    .openApiSchema(new String(schemaFile.getBytes(), StandardCharsets.UTF_8))
                    .migrationOption(parseMigrationOption(migrationOption))
                    .build();

            BackendConfig updated = schemaManagementService.uploadSchema(id, request);
            
            // Get validation stats (simplified - enhance later with actual counts)
            SchemaUploadResult.ValidationSummary validation = SchemaUploadResult.ValidationSummary.builder()
                    .mocksRevalidated(0)
                    .mocksDisabled(0)
                    .mocksDeleted(0)
                    .build();
            
            SchemaUploadResult result = SchemaUploadResult.builder()
                    .backend(toBackendConfigDto(updated))
                    .message("Schema uploaded successfully")
                    .validation(validation)
                    .build();
            
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            log.error("Error reading uploaded schema file", e);
            throw new IllegalArgumentException("Failed to read schema file: " + e.getMessage());
        }
    }

    @GetMapping("/backends/{id}/schema")
    @Operation(summary = "Get OpenAPI schema for a backend")
    public ResponseEntity<String> getSchema(@PathVariable Long id) {
        return backendConfigService.getAllBackends().stream()
                .filter(b -> b.getId().equals(id))
                .findFirst()
                .map(BackendConfig::getOpenApiSchema)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/backends/{id}/schema")
    @Operation(summary = "Delete OpenAPI schema from a backend")
    public ResponseEntity<Void> deleteSchema(@PathVariable Long id) {
        try {
            BackendConfig backend = backendConfigService.getAllBackends().stream()
                    .filter(b -> b.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Backend not found"));
            
            backend.setOpenApiSchema(null);
            backendConfigService.updateBackend(id, backend);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.error("Error deleting schema", e);
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/backends/{id}/generate-schema")
    @Operation(summary = "Generate OpenAPI schema from existing mock endpoints")
    public ResponseEntity<SchemaGenerationResult> generateSchemaFromMocks(@PathVariable Long id) {
        BackendConfig backend = backendConfigService.getAllBackends().stream()
                .filter(b -> b.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Backend not found with id: " + id));
        
        String generatedSchema = schemaGeneratorService.generateSchemaFromMocks(backend);
        
        if (generatedSchema == null) {
            throw new IllegalArgumentException("Could not generate schema. No mock endpoints found or generation failed.");
        }
        
        int endpointCount = mockEndpointRepository.findByBackendName(backend.getName()).size();
        
        // Save the generated schema to the backend
        backend.setOpenApiSchema(generatedSchema);
        BackendConfig updated = backendConfigService.updateBackend(id, backend);
        
        SchemaGenerationResult result = SchemaGenerationResult.builder()
                .backend(toBackendConfigDto(updated))
                .endpointCount(endpointCount)
                .schema(generatedSchema)
                .message("Schema generated successfully from " + endpointCount + " mock endpoints")
                .build();
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/backends/{id}/validate-mocks")
    @Operation(summary = "Validate all mocks against the schema")
    public ResponseEntity<List<SchemaManagementService.MockValidationResult>> validateMocks(@PathVariable Long id) {
        try {
            List<SchemaManagementService.MockValidationResult> results = 
                    schemaManagementService.validateAllMocks(id);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException e) {
            log.error("Error validating mocks", e);
            return ResponseEntity.badRequest().build();
        }
    }

    // ============== Mock Generation from Schema ==============

    @PostMapping("/backends/{id}/generate-mocks")
    @Operation(summary = "Generate mocks from OpenAPI schema with guided values")
    public ResponseEntity<MockGenerationResult> generateMocks(@PathVariable Long id,
                                                             @Valid @RequestBody GuidedMockGenerationRequest request) {
        BackendConfig backend = backendConfigService.getAllBackends().stream()
                .filter(b -> b.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Backend not found with id: " + id));

        if (backend.getOpenApiSchema() == null) {
            throw new IllegalArgumentException("Backend has no OpenAPI schema");
        }

        io.swagger.v3.oas.models.OpenAPI openAPI = 
                new io.swagger.v3.parser.OpenAPIV3Parser().readContents(
                        backend.getOpenApiSchema(), null, null).getOpenAPI();

        List<MockEndpoint> generatedEndpoints = new ArrayList<>();
        List<MockResponse> generatedResponses = new ArrayList<>();

        if (request.isGenerateEndpoints()) {
            generatedEndpoints = mockGeneratorService.generateMockEndpoints(backend, openAPI);
            for (MockEndpoint endpoint : generatedEndpoints) {
                MockEndpoint saved = mockService.createMockEndpoint(endpoint);
                
                if (request.isGenerateResponses()) {
                    List<MockResponse> responses = mockGeneratorService.generateMockResponses(
                            saved, openAPI, request.getGuidedValues());
                    for (MockResponse response : responses) {
                        MockResponse savedResponse = mockService.createMockResponse(saved.getId(), response);
                        generatedResponses.add(savedResponse);
                    }
                }
            }
        }

        List<MockEndpointDTO> endpointDtos = generatedEndpoints.stream()
                .map(this::toMockEndpointDto)
                .collect(Collectors.toList());

        log.info("Generated {} endpoints and {} responses for backend: {} (id: {})", 
                generatedEndpoints.size(), generatedResponses.size(), backend.getName(), id);

        MockGenerationResult result = MockGenerationResult.builder()
                .endpointsGenerated(generatedEndpoints.size())
                .responsesGenerated(generatedResponses.size())
                .endpoints(endpointDtos)
                .message("Successfully generated mocks from schema")
                .build();

        return ResponseEntity.ok(result);
    }

    @PostMapping("/mock-endpoints/{endpointId}/generate-responses")
    @Operation(summary = "Generate mock responses from schema for one existing mock endpoint")
    public ResponseEntity<Map<String, Object>> generateResponsesForEndpoint(
            @PathVariable Long endpointId,
            @RequestBody(required = false) Map<String, Object> guidedValues) {
        try {
            MockEndpoint endpoint = mockService.getMockEndpointById(endpointId)
                    .orElseThrow(() -> new IllegalArgumentException("MockEndpoint not found with id: " + endpointId));

            BackendConfig backend = backendConfigService.getBackendByName(endpoint.getBackendName())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Backend not found for mock endpoint: " + endpoint.getBackendName()));

            if (backend.getOpenApiSchema() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Backend has no OpenAPI schema"));
            }

            io.swagger.v3.oas.models.OpenAPI openAPI =
                    new io.swagger.v3.parser.OpenAPIV3Parser().readContents(
                            backend.getOpenApiSchema(), null, null).getOpenAPI();

            List<MockResponse> generatedResponses = new ArrayList<>();
            List<MockResponse> responses = mockGeneratorService.generateMockResponses(
                    endpoint, openAPI, guidedValues == null ? Map.of() : guidedValues);

            for (MockResponse response : responses) {
                MockResponse savedResponse = mockService.createMockResponse(endpointId, response);
                generatedResponses.add(savedResponse);
            }

            List<MockResponseDTO> generatedResponseDtos = generatedResponses.stream()
                    .map(this::toMockResponseDto)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "endpointId", endpointId,
                    "responsesGenerated", generatedResponses.size(),
                    "responses", generatedResponseDtos,
                    "message", "Successfully generated mock responses from schema"
            ));
        } catch (Exception e) {
            log.error("Error generating mock responses for endpoint {}", endpointId, e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/backends/{id}/generate-response")
    @Operation(summary = "Generate a single mock response with guided values")
    public ResponseEntity<Map<String, Object>> generateSingleResponse(
            @PathVariable Long id,
            @RequestParam String path,
            @RequestParam String method,
            @RequestParam(defaultValue = "200") int statusCode,
            @RequestBody Map<String, Object> guidedValues) {
        try {
            BackendConfig backend = backendConfigService.getAllBackends().stream()
                    .filter(b -> b.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Backend not found with id: " + id));

            if (backend.getOpenApiSchema() == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Backend has no OpenAPI schema"));
            }

            io.swagger.v3.oas.models.OpenAPI openAPI = 
                    new io.swagger.v3.parser.OpenAPIV3Parser().readContents(
                            backend.getOpenApiSchema(), null, null).getOpenAPI();

            String responseBody = mockGeneratorService.generateGuidedMockResponse(
                    openAPI, path, method, statusCode, guidedValues);

            Map<String, Object> result = new HashMap<>();
            result.put("path", path);
            result.put("method", method);
            result.put("statusCode", statusCode);
            result.put("responseBody", responseBody);
            result.put("message", "Successfully generated mock response");

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error generating response", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    private MockEndpointDTO toMockEndpointDto(MockEndpoint endpoint) {
        List<MockResponseDTO> responses = endpoint.getResponses() == null ? List.of() : endpoint.getResponses().stream()
                .map(this::toMockResponseDto)
                .collect(Collectors.toList());

        return MockEndpointDTO.builder()
                .id(endpoint.getId())
                .backendName(endpoint.getBackendName())
                .method(endpoint.getMethod())
                .path(endpoint.getPath())
                .description(endpoint.getDescription())
                .openApiSchema(endpoint.getOpenApiSchema())
                .enabled(endpoint.getEnabled())
                .responses(responses)
                .createdAt(endpoint.getCreatedAt())
                .updatedAt(endpoint.getUpdatedAt())
                .build();
    }

    private MockResponseDTO toMockResponseDto(MockResponse response) {
        return MockResponseDTO.builder()
                .id(response.getId())
                .name(response.getName())
                .matchConditions(response.getMatchConditions())
                .httpStatus(response.getHttpStatus())
                .responseBody(response.getResponseBody())
                .responseHeaders(response.getResponseHeaders())
                .priority(response.getPriority())
                .enabled(response.getEnabled())
                .delayMs(response.getDelayMs())
                .createdAt(response.getCreatedAt())
                .updatedAt(response.getUpdatedAt())
                .build();
    }

    private BackendConfigDTO toBackendConfigDto(BackendConfig backend) {
        return BackendConfigDTO.builder()
                .id(backend.getId())
                .name(backend.getName())
                .baseUrl(backend.getBaseUrl())
                .path(backend.getPath())
                .securityType(backend.getSecurityType())
                .securityConfig(backend.getSecurityConfig())
                .enabled(backend.getEnabled())
                .createdAt(backend.getCreatedAt())
                .updatedAt(backend.getUpdatedAt())
                .build();
    }

    private SchemaUploadRequest.SchemaMigrationOption parseMigrationOption(String migrationOption) {
        if (migrationOption == null || migrationOption.isBlank()) {
            return null;
        }

        return SchemaUploadRequest.SchemaMigrationOption.valueOf(migrationOption.trim().toUpperCase());
    }
}
