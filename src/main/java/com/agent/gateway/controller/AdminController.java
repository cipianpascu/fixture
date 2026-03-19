package com.agent.gateway.controller;

import com.agent.gateway.dto.GuidedMockGenerationRequest;
import com.agent.gateway.dto.MockEndpointDTO;
import com.agent.gateway.dto.MockResponseDTO;
import com.agent.gateway.dto.SchemaUploadRequest;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<List<BackendConfig>> getAllBackends() {
        return ResponseEntity.ok(backendConfigService.getAllBackends());
    }

    @GetMapping("/backends/{id}")
    @Operation(summary = "Get backend by ID")
    public ResponseEntity<BackendConfig> getBackendById(@PathVariable Long id) {
        return backendConfigService.getAllBackends().stream()
                .filter(b -> b.getId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/backends")
    @Operation(summary = "Create new backend")
    public ResponseEntity<BackendConfig> createBackend(@RequestBody BackendConfig backendConfig) {
        try {
            BackendConfig created = backendConfigService.createBackend(backendConfig);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.error("Error creating backend", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/backends/{id}")
    @Operation(summary = "Update backend")
    public ResponseEntity<BackendConfig> updateBackend(@PathVariable Long id, 
                                                       @RequestBody BackendConfig backendConfig) {
        try {
            BackendConfig updated = backendConfigService.updateBackend(id, backendConfig);
            return ResponseEntity.ok(updated);
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
    public ResponseEntity<MockEndpointDTO> createMockEndpoint(@RequestBody MockEndpoint mockEndpoint) {
        MockEndpoint created = mockService.createMockEndpoint(mockEndpoint);
        return ResponseEntity.status(HttpStatus.CREATED).body(toMockEndpointDto(created));
    }

    @PutMapping("/mock-endpoints/{id}")
    @Operation(summary = "Update mock endpoint")
    public ResponseEntity<MockEndpointDTO> updateMockEndpoint(@PathVariable Long id,
                                                              @RequestBody MockEndpoint mockEndpoint) {
        try {
            MockEndpoint updated = mockService.updateMockEndpoint(id, mockEndpoint);
            return ResponseEntity.ok(toMockEndpointDto(updated));
        } catch (IllegalArgumentException e) {
            log.error("Error updating mock endpoint", e);
            return ResponseEntity.notFound().build();
        }
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
                                                              @RequestBody MockResponse mockResponse) {
        try {
            MockResponse created = mockService.createMockResponse(endpointId, mockResponse);
            return ResponseEntity.status(HttpStatus.CREATED).body(toMockResponseDto(created));
        } catch (IllegalArgumentException e) {
            log.error("Error creating mock response", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/mock-responses/{id}")
    @Operation(summary = "Update mock response")
    public ResponseEntity<MockResponseDTO> updateMockResponse(@PathVariable Long id,
                                                              @RequestBody MockResponse mockResponse) {
        try {
            MockResponse updated = mockService.updateMockResponse(id, mockResponse);
            return ResponseEntity.ok(toMockResponseDto(updated));
        } catch (IllegalArgumentException e) {
            log.error("Error updating mock response", e);
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/mock-responses/{id}")
    @Operation(summary = "Delete mock response")
    public ResponseEntity<Void> deleteMockResponse(@PathVariable Long id) {
        mockService.deleteMockResponse(id);
        return ResponseEntity.noContent().build();
    }

    // ============== OpenAPI Schema Management ==============

    @PostMapping("/backends/{id}/schema")
    @Operation(summary = "Upload OpenAPI schema for a backend")
    public ResponseEntity<BackendConfig> uploadSchema(@PathVariable Long id,
                                                      @RequestBody SchemaUploadRequest request) {
        try {
            BackendConfig updated = schemaManagementService.uploadSchema(id, request);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            log.error("Error uploading schema", e);
            return ResponseEntity.badRequest().build();
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
    public ResponseEntity<Map<String, Object>> generateSchemaFromMocks(@PathVariable Long id) {
        try {
            BackendConfig backend = backendConfigService.getAllBackends().stream()
                    .filter(b -> b.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Backend not found with id: " + id));
            
            String generatedSchema = schemaGeneratorService.generateSchemaFromMocks(backend);
            
            if (generatedSchema == null) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Could not generate schema. No mock endpoints found or generation failed."));
            }
            
            // Save the generated schema to the backend
            backend.setOpenApiSchema(generatedSchema);
            backendConfigService.updateBackend(id, backend);
            
            return ResponseEntity.ok(Map.of(
                    "message", "Schema generated successfully from " + 
                               mockEndpointRepository.findByBackendName(backend.getName()).size() + " mock endpoints",
                    "schema", generatedSchema
            ));
        } catch (IllegalArgumentException e) {
            log.error("Error generating schema from mocks", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error generating schema", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to generate schema: " + e.getMessage()));
        }
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
    public ResponseEntity<Map<String, Object>> generateMocks(@PathVariable Long id,
                                                             @RequestBody GuidedMockGenerationRequest request) {
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

            Map<String, Object> result = new HashMap<>();
            result.put("endpointsGenerated", generatedEndpoints.size());
            result.put("responsesGenerated", generatedResponses.size());
            result.put("endpoints", generatedEndpoints);
            result.put("message", "Successfully generated mocks from schema");

            log.info("Generated {} endpoints and {} responses for backend: {} (id: {})", 
                    generatedEndpoints.size(), generatedResponses.size(), backend.getName(), id);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error generating mocks", e);
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
}
