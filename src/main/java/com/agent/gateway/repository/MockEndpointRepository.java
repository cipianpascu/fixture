package com.agent.gateway.repository;

import com.agent.gateway.entity.MockEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MockEndpointRepository extends JpaRepository<MockEndpoint, Long> {
    
    List<MockEndpoint> findByBackendName(String backendName);
    
    List<MockEndpoint> findByEnabled(Boolean enabled);
    
    Optional<MockEndpoint> findByBackendNameAndMethodAndPath(String backendName, String method, String path);
    
    List<MockEndpoint> findByBackendNameAndEnabled(String backendName, Boolean enabled);
}
