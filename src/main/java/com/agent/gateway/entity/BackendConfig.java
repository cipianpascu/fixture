package com.agent.gateway.entity;

import com.agent.gateway.model.SecurityType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "backend_config")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BackendConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String baseUrl;

    @Column(nullable = false)
    private String path;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecurityType securityType;

    @Column(columnDefinition = "TEXT")
    private String securityConfig; // JSON string for security configuration

    @Column(nullable = false)
    private Boolean enabled = true;

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
