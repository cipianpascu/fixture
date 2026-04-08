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
        Map<String, String> pathParams = new HashMap<>();
        
        for (MockEndpoint endpoint : endpoints) {
            if (endpoint.getMethod().equalsIgnoreCase(method)) {
                Map<String, String> extractedParams = extractPathParameters(endpoint.getPath(), path);
                if (extractedParams != null) {
                    matchedEndpoint = endpoint;
                    pathParams = extractedParams;
                    break;
                }
            }
        }
        
        if (matchedEndpoint == null) {
            log.debug("No mock endpoint found for {} {} on backend {}", method, path, backendName);
            return Optional.empty();
        }
        
        log.debug("Matched endpoint: {} {} with path params: {}", method, matchedEndpoint.getPath(), pathParams);
        
        // Get responses ordered by priority
        List<MockResponse> responses = mockResponseRepository.findByMockEndpointIdOrderByPriorityDesc(matchedEndpoint.getId());
        
        // Find first matching response based on match conditions
        for (MockResponse response : responses) {
            if (matchesConditions(response, request, requestBody, pathParams)) {
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
    
    /**
     * Process template variables in response body
     * Supports:
     * - {{request.body}} - mirrors the request body
     * - {{request.path}} - the request path
     * - {{request.method}} - the HTTP method
     * - {{request.header.HeaderName}} - specific request header
     * - {{request.query.paramName}} - specific query parameter
     * - {{pathParam.name}} - path parameter value
     */
    public String processTemplateVariables(String responseBody, HttpServletRequest request, 
                                          String requestBody, Map<String, String> pathParams) {
        if (responseBody == null || responseBody.isEmpty()) {
            return responseBody;
        }
        
        String processed = responseBody;
        
        // {{request.body}} - mirror request body
        if (processed.contains("{{request.body}}")) {
            processed = processed.replace("{{request.body}}", 
                    requestBody != null ? requestBody : "");
        }
        
        // {{request.path}}
        if (processed.contains("{{request.path}}")) {
            processed = processed.replace("{{request.path}}", 
                    request.getRequestURI() != null ? request.getRequestURI() : "");
        }
        
        // {{request.method}}
        if (processed.contains("{{request.method}}")) {
            processed = processed.replace("{{request.method}}", 
                    request.getMethod() != null ? request.getMethod() : "");
        }
        
        // {{request.header.X}} - extract specific headers
        processed = replaceHeaderVariables(processed, request);
        
        // {{request.query.X}} - extract query parameters
        processed = replaceQueryVariables(processed, request);
        
        // {{pathParam.X}} - extract path parameters
        processed = replacePathParamVariables(processed, pathParams);
        
        return processed;
    }
    
    private String replaceHeaderVariables(String text, HttpServletRequest request) {
        String result = text;
        // Find all {{request.header.XXX}} patterns
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{request\\.header\\.([^}]+)\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        
        while (matcher.find()) {
            String headerName = matcher.group(1);
            String headerValue = request.getHeader(headerName);
            result = result.replace("{{request.header." + headerName + "}}", 
                    headerValue != null ? headerValue : "");
        }
        
        return result;
    }
    
    private String replaceQueryVariables(String text, HttpServletRequest request) {
        String result = text;
        // Find all {{request.query.XXX}} patterns
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{request\\.query\\.([^}]+)\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String paramValue = request.getParameter(paramName);
            result = result.replace("{{request.query." + paramName + "}}", 
                    paramValue != null ? paramValue : "");
        }
        
        return result;
    }
    
    private String replacePathParamVariables(String text, Map<String, String> pathParams) {
        String result = text;
        // Find all {{pathParam.XXX}} patterns
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{\\{pathParam\\.([^}]+)\\}\\}");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String paramValue = pathParams.get(paramName);
            result = result.replace("{{pathParam." + paramName + "}}", 
                    paramValue != null ? paramValue : "");
        }
        
        return result;
    }

    /**
     * Extract path parameters from actual path based on pattern
     * Supports:
     * - {param} - named path parameters: /users/{id} matches /users/123
     * - * - single wildcard: /api/* /data matches /api/anything/data
     * - ** - multi-segment wildcard: /api/** matches /api/a/b/c
     * 
     * e.g., pattern="/users/{id}/orders/{orderId}" and path="/users/123/orders/456"
     * returns {"id": "123", "orderId": "456"}
     * 
     * e.g., pattern="/some/generic/path/*" and path="/some/generic/path/first"
     * returns {} (matches but no named params)
     * 
     * Returns null if path doesn't match pattern
     */
    private Map<String, String> extractPathParameters(String pattern, String actualPath) {
        Map<String, String> params = new HashMap<>();
        
        String[] patternParts = pattern.split("/");
        String[] pathParts = actualPath.split("/");
        
        // Check for ** wildcard (matches rest of path)
        for (int i = 0; i < patternParts.length; i++) {
            if ("**".equals(patternParts[i])) {
                // ** matches everything from this point on
                // Verify prefix matches
                for (int j = 0; j < i; j++) {
                    if (j >= pathParts.length) {
                        return null; // Path too short
                    }
                    if (!matchesSegment(patternParts[j], pathParts[j], params)) {
                        return null;
                    }
                }
                // Store the remaining path if ** is used
                if (i < pathParts.length) {
                    StringBuilder remaining = new StringBuilder();
                    for (int j = i; j < pathParts.length; j++) {
                        if (remaining.length() > 0) remaining.append("/");
                        remaining.append(pathParts[j]);
                    }
                    params.put("_wildcard", remaining.toString());
                }
                return params;
            }
        }
        
        // No **, check if lengths match (allowing for * wildcards)
        if (patternParts.length != pathParts.length) {
            return null;
        }
        
        // Match each segment
        for (int i = 0; i < patternParts.length; i++) {
            if (!matchesSegment(patternParts[i], pathParts[i], params)) {
                return null;
            }
        }
        
        return params;
    }
    
    /**
     * Check if a path segment matches a pattern segment
     * Supports {param}, *, and literal matching
     */
    private boolean matchesSegment(String patternSegment, String pathSegment, Map<String, String> params) {
        if (patternSegment.startsWith("{") && patternSegment.endsWith("}")) {
            // Named parameter
            String paramName = patternSegment.substring(1, patternSegment.length() - 1);
            params.put(paramName, pathSegment);
            return true;
        } else if ("*".equals(patternSegment)) {
            // Single wildcard - matches any single segment
            return true;
        } else {
            // Literal match
            return patternSegment.equals(pathSegment);
        }
    }

    private boolean matchesConditions(MockResponse response, HttpServletRequest request, String requestBody, 
                                     Map<String, String> pathParams) {
        if (response.getMatchConditions() == null || response.getMatchConditions().isEmpty()) {
            return true; // No conditions means always match
        }
        
        try {
            Map<String, Object> conditions = objectMapper.readValue(response.getMatchConditions(), 
                    new TypeReference<Map<String, Object>>() {});
            
            // Check path parameters
            if (conditions.containsKey("pathParams")) {
                @SuppressWarnings("unchecked")
                Map<String, String> expectedPathParams = (Map<String, String>) conditions.get("pathParams");
                for (Map.Entry<String, String> entry : expectedPathParams.entrySet()) {
                    String actualValue = pathParams.get(entry.getKey());
                    if (!entry.getValue().equals(actualValue)) {
                        log.debug("Path param mismatch: expected {}={}, got {}", 
                                entry.getKey(), entry.getValue(), actualValue);
                        return false;
                    }
                }
            }
            
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
            
            // Check request body contains (literal string match)
            if (conditions.containsKey("bodyContains")) {
                String expectedContent = (String) conditions.get("bodyContains");
                if (requestBody == null || !requestBody.contains(expectedContent)) {
                    return false;
                }
            }
            
            // Check JSON body attributes (flexible matching - handles both strings and numbers)
            if (conditions.containsKey("bodyAttributes")) {
                if (requestBody == null || requestBody.isEmpty()) {
                    return false;
                }
                
                @SuppressWarnings("unchecked")
                Map<String, Object> expectedAttributes = (Map<String, Object>) conditions.get("bodyAttributes");
                
                // Parse request body as JSON
                try {
                    Map<String, Object> actualBody = objectMapper.readValue(requestBody, 
                            new TypeReference<Map<String, Object>>() {});
                    
                    // Check each expected attribute
                    for (Map.Entry<String, Object> entry : expectedAttributes.entrySet()) {
                        String key = entry.getKey();
                        Object expectedValue = entry.getValue();
                        Object actualValue = getNestedValue(actualBody, key);
                        
                        if (!valuesMatch(expectedValue, actualValue)) {
                            log.debug("Body attribute mismatch: expected {}={}, got {}", 
                                    key, expectedValue, actualValue);
                            return false;
                        }
                    }
                } catch (Exception e) {
                    log.debug("Error parsing request body as JSON for attribute matching", e);
                    return false;
                }
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error parsing match conditions", e);
            return false;
        }
    }
    
    /**
     * Get nested value from JSON object using dot notation
     * Examples: "id", "user.name", "order.items[0].price"
     */
    private Object getNestedValue(Map<String, Object> json, String key) {
        if (json == null) {
            return null;
        }
        
        // Simple key (no nesting)
        if (!key.contains(".")) {
            return json.get(key);
        }
        
        // Nested key - traverse the path
        String[] parts = key.split("\\.");
        Object current = json;
        
        for (String part : parts) {
            if (current == null) {
                return null;
            }
            
            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(part);
            } else {
                return null;
            }
        }
        
        return current;
    }
    
    /**
     * Check if two values match, handling type conversions
     * Handles: strings, numbers, booleans, null
     */
    private boolean valuesMatch(Object expected, Object actual) {
        if (expected == null && actual == null) {
            return true;
        }
        if (expected == null || actual == null) {
            return false;
        }
        
        // Direct equality
        if (expected.equals(actual)) {
            return true;
        }
        
        // String comparison (normalize)
        String expectedStr = expected.toString();
        String actualStr = actual.toString();
        
        if (expectedStr.equals(actualStr)) {
            return true;
        }
        
        // Numeric comparison - handle "123" vs 123
        try {
            if (isNumeric(expected) && isNumeric(actual)) {
                double expectedNum = toDouble(expected);
                double actualNum = toDouble(actual);
                return Math.abs(expectedNum - actualNum) < 0.0001;
            }
        } catch (Exception e) {
            // Not numeric, fall through
        }
        
        return false;
    }
    
    private boolean isNumeric(Object obj) {
        if (obj instanceof Number) {
            return true;
        }
        if (obj instanceof String) {
            try {
                Double.parseDouble((String) obj);
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return false;
    }
    
    private double toDouble(Object obj) {
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        if (obj instanceof String) {
            return Double.parseDouble((String) obj);
        }
        throw new IllegalArgumentException("Cannot convert to double: " + obj);
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
