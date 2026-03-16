package com.agent.gateway.service;

import com.agent.gateway.entity.MockEndpoint;
import com.agent.gateway.entity.MockResponse;
import com.agent.gateway.repository.MockEndpointRepository;
import com.agent.gateway.repository.MockResponseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MockServiceTest {

    @Mock
    private MockEndpointRepository mockEndpointRepository;

    @Mock
    private MockResponseRepository mockResponseRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private MockService mockService;

    private MockEndpoint mockEndpoint;
    private MockResponse mockResponse;

    @BeforeEach
    void setUp() {
        mockEndpoint = MockEndpoint.builder()
                .id(1L)
                .backendName("test-service")
                .method("GET")
                .path("/users")
                .enabled(true)
                .build();

        mockResponse = MockResponse.builder()
                .id(1L)
                .mockEndpoint(mockEndpoint)
                .name("Success Response")
                .httpStatus(200)
                .responseBody("{\"users\":[]}")
                .priority(10)
                .enabled(true)
                .build();
    }

    @Test
    void testFindMatchingResponse_Success() {
        // Given
        List<MockEndpoint> endpoints = Arrays.asList(mockEndpoint);
        List<MockResponse> responses = Arrays.asList(mockResponse);

        when(mockEndpointRepository.findByBackendNameAndEnabled("test-service", true))
                .thenReturn(endpoints);
        when(mockResponseRepository.findByMockEndpointIdOrderByPriorityDesc(1L))
                .thenReturn(responses);

        // When
        Optional<MockResponse> result = mockService.findMatchingResponse(
                "test-service", "GET", "/users", request, null);

        // Then
        assertTrue(result.isPresent());
        assertEquals(mockResponse.getId(), result.get().getId());
        verify(mockEndpointRepository).findByBackendNameAndEnabled("test-service", true);
        verify(mockResponseRepository).findByMockEndpointIdOrderByPriorityDesc(1L);
    }

    @Test
    void testFindMatchingResponse_NoEndpointFound() {
        // Given
        when(mockEndpointRepository.findByBackendNameAndEnabled("test-service", true))
                .thenReturn(Arrays.asList());

        // When
        Optional<MockResponse> result = mockService.findMatchingResponse(
                "test-service", "GET", "/users", request, null);

        // Then
        assertFalse(result.isPresent());
    }

    @Test
    void testCreateMockEndpoint() {
        // Given
        when(mockEndpointRepository.save(any(MockEndpoint.class)))
                .thenReturn(mockEndpoint);

        // When
        MockEndpoint result = mockService.createMockEndpoint(mockEndpoint);

        // Then
        assertNotNull(result);
        assertEquals(mockEndpoint.getId(), result.getId());
        verify(mockEndpointRepository).save(mockEndpoint);
    }

    @Test
    void testGetMockEndpointsByBackend() {
        // Given
        List<MockEndpoint> endpoints = Arrays.asList(mockEndpoint);
        when(mockEndpointRepository.findByBackendName("test-service"))
                .thenReturn(endpoints);

        // When
        List<MockEndpoint> result = mockService.getMockEndpointsByBackend("test-service");

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(mockEndpoint.getId(), result.get(0).getId());
    }

    @Test
    void testUpdateMockEndpoint() {
        // Given
        MockEndpoint updatedEndpoint = MockEndpoint.builder()
                .backendName("test-service")
                .method("POST")
                .path("/users")
                .enabled(true)
                .build();

        when(mockEndpointRepository.findById(1L))
                .thenReturn(Optional.of(mockEndpoint));
        when(mockEndpointRepository.save(any(MockEndpoint.class)))
                .thenReturn(mockEndpoint);

        // When
        MockEndpoint result = mockService.updateMockEndpoint(1L, updatedEndpoint);

        // Then
        assertNotNull(result);
        verify(mockEndpointRepository).findById(1L);
        verify(mockEndpointRepository).save(any(MockEndpoint.class));
    }

    @Test
    void testDeleteMockEndpoint() {
        // When
        mockService.deleteMockEndpoint(1L);

        // Then
        verify(mockEndpointRepository).deleteById(1L);
    }

    @Test
    void testCreateMockResponse() {
        // Given
        when(mockEndpointRepository.findById(1L))
                .thenReturn(Optional.of(mockEndpoint));
        when(mockResponseRepository.save(any(MockResponse.class)))
                .thenReturn(mockResponse);

        // When
        MockResponse result = mockService.createMockResponse(1L, mockResponse);

        // Then
        assertNotNull(result);
        assertEquals(mockResponse.getId(), result.getId());
        verify(mockEndpointRepository).findById(1L);
        verify(mockResponseRepository).save(mockResponse);
    }

    @Test
    void testCreateMockResponse_EndpointNotFound() {
        // Given
        when(mockEndpointRepository.findById(1L))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            mockService.createMockResponse(1L, mockResponse);
        });
    }
}
