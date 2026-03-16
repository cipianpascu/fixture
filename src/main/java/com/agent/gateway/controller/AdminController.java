package com.agent.gateway.controller;

import com.agent.gateway.entity.BackendConfig;
import com.agent.gateway.entity.MockEndpoint;
import com.agent.gateway.entity.MockResponse;
import com.agent.gateway.service.BackendConfigService;
import com.agent.gateway.service.MockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin API", description = "Administrative endpoints for managing backends and mocks")
public class AdminController {

    private final BackendConfigService backendConfigService;
    private final MockService mockService;

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
    public ResponseEntity<List<MockEndpoint>> getAllMockEndpoints() {
        return ResponseEntity.ok(mockService.getAllMockEndpoints());
    }

    @GetMapping("/mock-endpoints/{id}")
    @Operation(summary = "Get mock endpoint by ID")
    public ResponseEntity<MockEndpoint> getMockEndpointById(@PathVariable Long id) {
        return mockService.getMockEndpointById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/mock-endpoints/backend/{backendName}")
    @Operation(summary = "Get mock endpoints by backend name")
    public ResponseEntity<List<MockEndpoint>> getMockEndpointsByBackend(@PathVariable String backendName) {
        return ResponseEntity.ok(mockService.getMockEndpointsByBackend(backendName));
    }

    @PostMapping("/mock-endpoints")
    @Operation(summary = "Create new mock endpoint")
    public ResponseEntity<MockEndpoint> createMockEndpoint(@RequestBody MockEndpoint mockEndpoint) {
        MockEndpoint created = mockService.createMockEndpoint(mockEndpoint);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/mock-endpoints/{id}")
    @Operation(summary = "Update mock endpoint")
    public ResponseEntity<MockEndpoint> updateMockEndpoint(@PathVariable Long id, 
                                                           @RequestBody MockEndpoint mockEndpoint) {
        try {
            MockEndpoint updated = mockService.updateMockEndpoint(id, mockEndpoint);
            return ResponseEntity.ok(updated);
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
    public ResponseEntity<List<MockResponse>> getResponsesByEndpoint(@PathVariable Long endpointId) {
        return ResponseEntity.ok(mockService.getResponsesByEndpoint(endpointId));
    }

    @GetMapping("/mock-responses/{id}")
    @Operation(summary = "Get mock response by ID")
    public ResponseEntity<MockResponse> getMockResponseById(@PathVariable Long id) {
        return mockService.getMockResponseById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/mock-endpoints/{endpointId}/responses")
    @Operation(summary = "Create new mock response for an endpoint")
    public ResponseEntity<MockResponse> createMockResponse(@PathVariable Long endpointId,
                                                           @RequestBody MockResponse mockResponse) {
        try {
            MockResponse created = mockService.createMockResponse(endpointId, mockResponse);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            log.error("Error creating mock response", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @PutMapping("/mock-responses/{id}")
    @Operation(summary = "Update mock response")
    public ResponseEntity<MockResponse> updateMockResponse(@PathVariable Long id,
                                                           @RequestBody MockResponse mockResponse) {
        try {
            MockResponse updated = mockService.updateMockResponse(id, mockResponse);
            return ResponseEntity.ok(updated);
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
}
