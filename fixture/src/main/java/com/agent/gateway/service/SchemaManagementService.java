package com.agent.gateway.service;

import com.agent.gateway.dto.SchemaUploadRequest;
import com.agent.gateway.entity.BackendConfig;
import com.agent.gateway.entity.MockEndpoint;
import com.agent.gateway.entity.MockResponse;
import com.agent.gateway.repository.MockEndpointRepository;
import io.swagger.v3.oas.models.OpenAPI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaManagementService {

    private final OpenApiSchemaService schemaService;
    private final MockEndpointRepository mockEndpointRepository;
    private final BackendConfigService backendConfigService;

    /**
     * Upload and apply OpenAPI schema to a backend
     */
    @Transactional
    public BackendConfig uploadSchema(Long backendId, SchemaUploadRequest request) {
        BackendConfig backend = backendConfigService.getAllBackends().stream()
                .filter(b -> b.getId().equals(backendId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Backend not found: " + backendId));

        // Validate the schema
        OpenAPI openAPI = schemaService.parseSchema(request.getOpenApiSchema());
        if (openAPI == null) {
            throw new IllegalArgumentException("Invalid OpenAPI schema");
        }

        // Update backend with new schema
        backend.setOpenApiSchema(request.getOpenApiSchema());
        BackendConfig updated = backendConfigService.updateBackend(backendId, backend);

        // Handle existing mocks based on migration option
        if (request.getMigrationOption() != null) {
            handleSchemaMigration(backend, openAPI, request.getMigrationOption());
        }

        log.info("Schema uploaded successfully for backend: {}", backend.getName());
        return updated;
    }

    /**
     * Handle migration of existing mocks when schema changes
     */
    @Transactional
    public void handleSchemaMigration(BackendConfig backend, OpenAPI openAPI,
                                      SchemaUploadRequest.SchemaMigrationOption option) {
        List<MockEndpoint> endpoints = mockEndpointRepository.findByBackendName(backend.getName());

        switch (option) {
            case DELETE_MOCKS:
                log.info("Deleting {} existing mock endpoints for backend: {}", 
                        endpoints.size(), backend.getName());
                mockEndpointRepository.deleteAll(endpoints);
                break;

            case REVALIDATE_AND_DISABLE:
                log.info("Revalidating {} existing mock endpoints for backend: {}", 
                        endpoints.size(), backend.getName());
                revalidateAndDisableMocks(endpoints, openAPI);
                break;
        }
    }

    /**
     * Revalidate existing mocks and disable incompatible ones
     */
    @Transactional
    public void revalidateAndDisableMocks(List<MockEndpoint> endpoints, OpenAPI openAPI) {
        for (MockEndpoint endpoint : endpoints) {
            boolean isValid = schemaService.validateEndpoint(openAPI, endpoint.getPath(), endpoint.getMethod());
            
            if (!isValid) {
                log.warn("Disabling incompatible mock endpoint: {} {}", 
                        endpoint.getMethod(), endpoint.getPath());
                endpoint.setEnabled(false);
                mockEndpointRepository.save(endpoint);
            } else {
                // Validate responses for this endpoint
                validateAndDisableIncompatibleResponses(endpoint, openAPI);
            }
        }
    }

    /**
     * Validate responses for an endpoint and disable incompatible ones
     */
    private void validateAndDisableIncompatibleResponses(MockEndpoint endpoint, OpenAPI openAPI) {
        if (endpoint.getResponses() == null || endpoint.getResponses().isEmpty()) {
            return;
        }

        for (MockResponse response : endpoint.getResponses()) {
            OpenApiSchemaService.ValidationResult result = schemaService.validateResponse(
                    openAPI,
                    endpoint.getPath(),
                    endpoint.getMethod(),
                    response.getHttpStatus(),
                    response.getResponseBody()
            );

            if (!result.isValid()) {
                log.warn("Disabling incompatible mock response '{}' for {} {}: {}", 
                        response.getName(),
                        endpoint.getMethod(),
                        endpoint.getPath(),
                        result.getMessage());
                response.setEnabled(false);
            }
        }
    }

    /**
     * Get validation status for all mocks of a backend
     */
    public List<MockValidationResult> validateAllMocks(Long backendId) {
        BackendConfig backend = backendConfigService.getAllBackends().stream()
                .filter(b -> b.getId().equals(backendId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Backend not found: " + backendId));

        if (backend.getOpenApiSchema() == null) {
            throw new IllegalArgumentException("Backend has no OpenAPI schema");
        }

        OpenAPI openAPI = schemaService.parseSchema(backend.getOpenApiSchema());
        List<MockEndpoint> endpoints = mockEndpointRepository.findByBackendName(backend.getName());
        List<MockValidationResult> results = new ArrayList<>();

        for (MockEndpoint endpoint : endpoints) {
            boolean endpointValid = schemaService.validateEndpoint(openAPI, endpoint.getPath(), endpoint.getMethod());
            
            MockValidationResult endpointResult = new MockValidationResult(
                    endpoint.getId(),
                    endpoint.getMethod() + " " + endpoint.getPath(),
                    "endpoint",
                    endpointValid,
                    endpointValid ? null : "Endpoint not found in schema"
            );
            results.add(endpointResult);

            // Validate responses
            if (endpoint.getResponses() != null) {
                for (MockResponse response : endpoint.getResponses()) {
                    OpenApiSchemaService.ValidationResult validationResult = schemaService.validateResponse(
                            openAPI,
                            endpoint.getPath(),
                            endpoint.getMethod(),
                            response.getHttpStatus(),
                            response.getResponseBody()
                    );

                    MockValidationResult responseResult = new MockValidationResult(
                            response.getId(),
                            response.getName(),
                            "response",
                            validationResult.isValid(),
                            validationResult.getMessage()
                    );
                    results.add(responseResult);
                }
            }
        }

        return results;
    }

    /**
     * Result of mock validation
     */
    public record MockValidationResult(
            Long id,
            String name,
            String type,
            boolean valid,
            String message
    ) {}
}
