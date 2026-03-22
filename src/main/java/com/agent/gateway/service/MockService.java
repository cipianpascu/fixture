package com.agent.gateway.service;

import com.agent.gateway.entity.MockEndpoint;
import com.agent.gateway.entity.MockResponse;
import com.agent.gateway.repository.MockEndpointRepository;
import com.agent.gateway.repository.MockResponseRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockService {

    private final MockEndpointRepository mockEndpointRepository;
    private final MockResponseRepository mockResponseRepository;
    private final ObjectMapper objectMapper;
    private final BackendConfigService backendConfigService;
    private final OpenApiSchemaService schemaService;

    public Optional<MockResponse> findMatchingResponse(String backendName, String method, String path, 
                                                        HttpServletRequest request, String requestBody) {
        // Find matching endpoint
        List<MockEndpoint> endpoints = mockEndpointRepository.findByBackendNameAndEnabled(backendName, true);
        
        MockEndpoint matchedEndpoint = null;
        for (MockEndpoint endpoint : endpoints) {
            if (endpoint.getMethod().equalsIgnoreCase(method) && pathMatches(endpoint.getPath(), path)) {
                matchedEndpoint = endpoint;
                break;
            }
        }
        
        if (matchedEndpoint == null) {
            log.debug("No mock endpoint found for {} {} on backend {}", method, path, backendName);
            return Optional.empty();
        }
        
        // Get responses ordered by priority
        List<MockResponse> responses = mockResponseRepository.findByMockEndpointIdOrderByPriorityDesc(matchedEndpoint.getId());
        
        // Find first matching response based on match conditions
        for (MockResponse response : responses) {
            if (matchesConditions(response, request, requestBody)) {
                log.info("Found matching mock response: {} for {} {}", response.getName(), method, path);
                return Optional.of(response);
            }
        }
        
        // Return first response as default if no conditions match
        if (!responses.isEmpty()) {
            log.info("Using default mock response for {} {}", method, path);
            return Optional.of(responses.get(0));
        }
        
        return Optional.empty();
    }

    private boolean pathMatches(String pattern, String actualPath) {
        // Convert path pattern like /users/{id} to regex
        String regex = pattern.replaceAll("\\{[^}]+\\}", "[^/]+");
        regex = "^" + regex + "$";
        return actualPath.matches(regex);
    }

    private boolean matchesConditions(MockResponse response, HttpServletRequest request, String requestBody) {
        if (response.getMatchConditions() == null || response.getMatchConditions().isEmpty()) {
            return true; // No conditions means always match
        }
        
        try {
            Map<String, Object> conditions = objectMapper.readValue(response.getMatchConditions(), 
                    new TypeReference<Map<String, Object>>() {});
            
            // Check query parameters
            if (conditions.containsKey("queryParams")) {
                @SuppressWarnings("unchecked")
                Map<String, String> expectedParams = (Map<String, String>) conditions.get("queryParams");
                for (Map.Entry<String, String> entry : expectedParams.entrySet()) {
                    String actualValue = request.getParameter(entry.getKey());
                    if (!entry.getValue().equals(actualValue)) {
                        return false;
                    }
                }
            }
            
            // Check headers
            if (conditions.containsKey("headers")) {
                @SuppressWarnings("unchecked")
                Map<String, String> expectedHeaders = (Map<String, String>) conditions.get("headers");
                for (Map.Entry<String, String> entry : expectedHeaders.entrySet()) {
                    String actualValue = request.getHeader(entry.getKey());
                    if (!entry.getValue().equals(actualValue)) {
                        return false;
                    }
                }
            }
            
            // Check request body contains
            if (conditions.containsKey("bodyContains")) {
                String expectedContent = (String) conditions.get("bodyContains");
                if (requestBody == null || !requestBody.contains(expectedContent)) {
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error parsing match conditions", e);
            return false;
        }
    }

    // CRUD operations for MockEndpoint
    @Transactional
    public MockEndpoint createMockEndpoint(MockEndpoint mockEndpoint) {
        return mockEndpointRepository.save(mockEndpoint);
    }

    /**
     * Create a mock endpoint or return existing one if it already exists
     * (idempotent operation for schema-based generation)
     */
    @Transactional
    public MockEndpoint createOrGetMockEndpoint(MockEndpoint mockEndpoint) {
        Optional<MockEndpoint> existing = mockEndpointRepository.findByBackendNameAndMethodAndPath(
                mockEndpoint.getBackendName(),
                mockEndpoint.getMethod(),
                mockEndpoint.getPath()
        );
        
        if (existing.isPresent()) {
            log.debug("Endpoint already exists: {} {} on {}, reusing existing (id: {})",
                    mockEndpoint.getMethod(), mockEndpoint.getPath(), mockEndpoint.getBackendName(), existing.get().getId());
            
            // Optionally update description and schema if provided
            MockEndpoint existingEndpoint = existing.get();
            if (mockEndpoint.getDescription() != null) {
                existingEndpoint.setDescription(mockEndpoint.getDescription());
            }
            if (mockEndpoint.getOpenApiSchema() != null) {
                existingEndpoint.setOpenApiSchema(mockEndpoint.getOpenApiSchema());
            }
            
            return mockEndpointRepository.save(existingEndpoint);
        }
        
        log.info("Creating new endpoint: {} {} on {}",
                mockEndpoint.getMethod(), mockEndpoint.getPath(), mockEndpoint.getBackendName());
        return mockEndpointRepository.save(mockEndpoint);
    }

    public List<MockEndpoint> getAllMockEndpoints() {
        return mockEndpointRepository.findAll();
    }

    public List<MockEndpoint> getMockEndpointsByBackend(String backendName) {
        return mockEndpointRepository.findByBackendName(backendName);
    }

    public Optional<MockEndpoint> getMockEndpointById(Long id) {
        return mockEndpointRepository.findById(id);
    }

    @Transactional
    public MockEndpoint updateMockEndpoint(Long id, MockEndpoint mockEndpoint) {
        MockEndpoint existing = mockEndpointRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MockEndpoint not found with id: " + id));
        
        existing.setBackendName(mockEndpoint.getBackendName());
        existing.setMethod(mockEndpoint.getMethod());
        existing.setPath(mockEndpoint.getPath());
        existing.setDescription(mockEndpoint.getDescription());
        existing.setOpenApiSchema(mockEndpoint.getOpenApiSchema());
        existing.setEnabled(mockEndpoint.getEnabled());
        
        return mockEndpointRepository.save(existing);
    }

    @Transactional
    public void deleteMockEndpoint(Long id) {
        mockEndpointRepository.deleteById(id);
    }

    // CRUD operations for MockResponse
    @Transactional
    public MockResponse createMockResponse(Long endpointId, MockResponse mockResponse) {
        MockEndpoint endpoint = mockEndpointRepository.findById(endpointId)
                .orElseThrow(() -> new IllegalArgumentException("MockEndpoint not found with id: " + endpointId));
        
        // Validate against schema if configured
        validateMockResponse(endpoint, mockResponse);
        
        mockResponse.setMockEndpoint(endpoint);
        return mockResponseRepository.save(mockResponse);
    }

    public List<MockResponse> getResponsesByEndpoint(Long endpointId) {
        return mockResponseRepository.findByMockEndpointIdAndEnabled(endpointId, true);
    }

    public Optional<MockResponse> getMockResponseById(Long id) {
        return mockResponseRepository.findById(id);
    }

    @Transactional
    public MockResponse updateMockResponse(Long id, MockResponse mockResponse) {
        MockResponse existing = mockResponseRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("MockResponse not found with id: " + id));
        
        existing.setName(mockResponse.getName());
        existing.setMatchConditions(mockResponse.getMatchConditions());
        existing.setHttpStatus(mockResponse.getHttpStatus());
        existing.setResponseBody(mockResponse.getResponseBody());
        existing.setResponseHeaders(mockResponse.getResponseHeaders());
        existing.setPriority(mockResponse.getPriority());
        existing.setEnabled(mockResponse.getEnabled());
        existing.setDelayMs(mockResponse.getDelayMs());
        
        return mockResponseRepository.save(existing);
    }

    @Transactional
    public void deleteMockResponse(Long id) {
        mockResponseRepository.deleteById(id);
    }

    /**
     * Clone a single response to another endpoint
     */
    @Transactional
    public MockResponse cloneResponse(Long responseId, Long targetEndpointId) {
        MockResponse source = mockResponseRepository.findById(responseId)
                .orElseThrow(() -> new IllegalArgumentException("Source response not found with id: " + responseId));
        
        MockEndpoint targetEndpoint = mockEndpointRepository.findById(targetEndpointId)
                .orElseThrow(() -> new IllegalArgumentException("Target endpoint not found with id: " + targetEndpointId));
        
        MockResponse cloned = MockResponse.builder()
                .mockEndpoint(targetEndpoint)
                .name(source.getName() + " (copy)")
                .matchConditions(source.getMatchConditions())
                .httpStatus(source.getHttpStatus())
                .responseBody(source.getResponseBody())
                .responseHeaders(source.getResponseHeaders())
                .priority(source.getPriority())
                .enabled(source.getEnabled())
                .delayMs(source.getDelayMs())
                .build();
        
        // Validate against target endpoint's schema
        validateMockResponse(targetEndpoint, cloned);
        
        log.info("Cloned response '{}' (id: {}) to endpoint {} (id: {})", 
                source.getName(), responseId, targetEndpoint.getPath(), targetEndpointId);
        
        return mockResponseRepository.save(cloned);
    }

    /**
     * Clone all responses from one endpoint to another
     */
    @Transactional
    public List<MockResponse> cloneAllResponses(Long sourceEndpointId, Long targetEndpointId) {
        MockEndpoint sourceEndpoint = mockEndpointRepository.findById(sourceEndpointId)
                .orElseThrow(() -> new IllegalArgumentException("Source endpoint not found with id: " + sourceEndpointId));
        
        MockEndpoint targetEndpoint = mockEndpointRepository.findById(targetEndpointId)
                .orElseThrow(() -> new IllegalArgumentException("Target endpoint not found with id: " + targetEndpointId));
        
        List<MockResponse> sourceResponses = mockResponseRepository.findByMockEndpointId(sourceEndpointId);
        List<MockResponse> clonedResponses = new ArrayList<>();
        
        for (MockResponse source : sourceResponses) {
            MockResponse cloned = MockResponse.builder()
                    .mockEndpoint(targetEndpoint)
                    .name(source.getName())
                    .matchConditions(source.getMatchConditions())
                    .httpStatus(source.getHttpStatus())
                    .responseBody(source.getResponseBody())
                    .responseHeaders(source.getResponseHeaders())
                    .priority(source.getPriority())
                    .enabled(source.getEnabled())
                    .delayMs(source.getDelayMs())
                    .build();
            
            // Validate against target endpoint's schema
            validateMockResponse(targetEndpoint, cloned);
            
            clonedResponses.add(mockResponseRepository.save(cloned));
        }
        
        log.info("Cloned {} responses from endpoint {} (id: {}) to endpoint {} (id: {})", 
                sourceResponses.size(), sourceEndpoint.getPath(), sourceEndpointId, 
                targetEndpoint.getPath(), targetEndpointId);
        
        return clonedResponses;
    }

    // Validation methods
    private void validateMockResponse(MockEndpoint endpoint, MockResponse mockResponse) {
        var backendOpt = backendConfigService.getBackendByName(endpoint.getBackendName());
        if (backendOpt.isEmpty()) {
            return; // No backend found, skip validation
        }

        var backend = backendOpt.get();
        if (backend.getOpenApiSchema() == null) {
            return; // No schema configured, skip validation
        }

        io.swagger.v3.oas.models.OpenAPI openAPI = schemaService.parseSchema(backend.getOpenApiSchema());
        if (openAPI == null) {
            return; // Invalid schema, skip validation
        }

        OpenApiSchemaService.ValidationResult result = schemaService.validateResponse(
                openAPI,
                endpoint.getPath(),
                endpoint.getMethod(),
                mockResponse.getHttpStatus(),
                mockResponse.getResponseBody()
        );

        if (!result.isValid() && schemaService.shouldEnforceValidation(backend)) {
            throw new IllegalArgumentException("Schema validation failed: " + result.getMessage());
        }

        if (result.hasWarnings() && schemaService.shouldWarnOnValidation(backend)) {
            log.warn("Schema validation warning for mock response '{}': {}", 
                    mockResponse.getName(), result.getMessage());
        }
    }
}
