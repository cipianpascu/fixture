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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class MockService {

    private final MockEndpointRepository mockEndpointRepository;
    private final MockResponseRepository mockResponseRepository;
    private final ObjectMapper objectMapper;

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
}
