package com.agent.gateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "mock_response")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MockResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mock_endpoint_id", nullable = false)
    private MockEndpoint mockEndpoint;

    @Column(nullable = false)
    private String name; // Descriptive name for this response scenario

    @Column(columnDefinition = "TEXT")
    private String matchConditions; // JSON string defining when to use this response (based on request params, headers, body)

    @Column(nullable = false)
    private Integer httpStatus = 200;

    @Column(columnDefinition = "TEXT")
    private String responseBody; // JSON/XML/Text response body

    @Column(columnDefinition = "TEXT")
    private String responseHeaders; // JSON string for response headers

    @Column(nullable = false)
    private Integer priority = 0; // Higher priority responses are checked first

    @Column(nullable = false)
    private Boolean enabled = true;

    private Integer delayMs = 0; // Simulate network latency

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
