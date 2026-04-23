package com.agent.gateway.service;

import com.agent.gateway.config.GatewayProperties;
import com.agent.gateway.entity.BackendConfig;
import com.agent.gateway.model.GatewayMode;
import com.agent.gateway.repository.BackendConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BackendConfigService {

    private final BackendConfigRepository backendConfigRepository;
    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;

    @PostConstruct
    @Transactional
    public void initializeBackends() {
        if (gatewayProperties.getMode() == GatewayMode.ROUTING) {
            log.info("Initializing backends from configuration file in ROUTING mode");
            for (GatewayProperties.BackendDefinition backendDef : gatewayProperties.getBackends()) {
                Optional<BackendConfig> existing = backendConfigRepository.findByName(backendDef.getName());
                if (existing.isEmpty()) {
                    try {
                        BackendConfig config = BackendConfig.builder()
                                .name(backendDef.getName())
                                .baseUrl(backendDef.getBaseUrl())
                                .path(backendDef.getPath())
                                .securityType(backendDef.getSecurityType())
                                .securityConfig(objectMapper.writeValueAsString(backendDef.getSecurityConfig()))
                                .enabled(backendDef.getEnabled())
                                .build();
                        backendConfigRepository.save(config);
                        log.info("Initialized backend: {}", backendDef.getName());
                    } catch (JsonProcessingException e) {
                        log.error("Failed to serialize security config for backend: {}", backendDef.getName(), e);
                    }
                }
            }
        } else {
            log.info("Running in FIXTURE mode - backends are database-driven");
        }
    }

    public List<BackendConfig> getAllBackends() {
        return backendConfigRepository.findAll();
    }

    public List<BackendConfig> getEnabledBackends() {
        return backendConfigRepository.findByEnabled(true);
    }

    public Optional<BackendConfig> getBackendByName(String name) {
        return backendConfigRepository.findByName(name);
    }

    @Transactional
    public BackendConfig createBackend(BackendConfig backendConfig) {
        if (backendConfigRepository.existsByName(backendConfig.getName())) {
            throw new IllegalArgumentException("Backend with name '" + backendConfig.getName() + "' already exists");
        }
        return backendConfigRepository.save(backendConfig);
    }

    @Transactional
    public BackendConfig updateBackend(Long id, BackendConfig backendConfig) {
        BackendConfig existing = backendConfigRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Backend not found with id: " + id));
        
        existing.setBaseUrl(backendConfig.getBaseUrl());
        existing.setPath(backendConfig.getPath());
        existing.setSecurityType(backendConfig.getSecurityType());
        existing.setSecurityConfig(backendConfig.getSecurityConfig());
        existing.setEnabled(backendConfig.getEnabled());
        
        return backendConfigRepository.save(existing);
    }

    @Transactional
    public void deleteBackend(Long id) {
        backendConfigRepository.deleteById(id);
    }
}
