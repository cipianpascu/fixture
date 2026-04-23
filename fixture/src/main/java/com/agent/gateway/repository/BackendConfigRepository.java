package com.agent.gateway.repository;

import com.agent.gateway.entity.BackendConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BackendConfigRepository extends JpaRepository<BackendConfig, Long> {
    
    Optional<BackendConfig> findByName(String name);
    
    List<BackendConfig> findByEnabled(Boolean enabled);
    
    boolean existsByName(String name);
}
