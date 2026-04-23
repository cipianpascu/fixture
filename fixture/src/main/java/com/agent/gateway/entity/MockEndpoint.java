package com.agent.gateway.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "mock_endpoint",
       uniqueConstraints = @UniqueConstraint(
           columnNames = {"backend_name", "method", "path"},
           name = "uk_endpoint_backend_method_path"
       ))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MockEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String backendName; // References BackendConfig.name

    @Column(nullable = false)
    private String method; // GET, POST, PUT, DELETE, etc.

    @Column(nullable = false)
    private String path; // Endpoint path pattern (e.g., /users/{id})

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(columnDefinition = "TEXT")
    private String openApiSchema; // OpenAPI schema for this endpoint

    @Column(nullable = false)
    private Boolean enabled = true;

    @OneToMany(mappedBy = "mockEndpoint", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MockResponse> responses = new ArrayList<>();

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
