package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Schema Loader Service
 * 
 * Loads OpenAPI schemas from filesystem or classpath.
 * Schemas are loaded once at startup and cached in memory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SchemaLoader {
    
    private final ProxyProperties proxyProperties;
    private final ResourceLoader resourceLoader;
    private final Map<String, OpenAPI> schemas = new ConcurrentHashMap<>();
    
    @PostConstruct
    public void loadSchemas() {
        String schemaDirectory = proxyProperties.getSchemas().getDirectory();
        log.info("Loading schemas from: {}", schemaDirectory);
        
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            String pattern = schemaDirectory + "*.{yaml,yml}";
            
            Resource[] resources = resolver.getResources(pattern);
            
            if (resources.length == 0) {
                log.warn("No schema files found in: {}", schemaDirectory);
                return;
            }
            
            for (Resource resource : resources) {
                loadSchema(resource);
            }
            
            log.info("Loaded {} schemas successfully", schemas.size());
            schemas.keySet().forEach(name -> log.info("  - {}", name));
            
        } catch (Exception e) {
            log.error("Failed to load schemas from: {}", schemaDirectory, e);
        }
    }
    
    private void loadSchema(Resource resource) {
        try {
            String filename = resource.getFilename();
            if (filename == null) {
                return;
            }
            
            ParseOptions options = new ParseOptions();
            options.setResolve(true);
            options.setResolveFully(true);
            
            OpenAPIV3Parser parser = new OpenAPIV3Parser();
            SwaggerParseResult result = parser.readLocation(
                resource.getURL().toString(), 
                null, 
                options
            );
            
            if (result.getOpenAPI() != null) {
                schemas.put(filename, result.getOpenAPI());
                log.info("Loaded schema: {}", filename);
            } else {
                log.error("Failed to parse schema: {} - {}", 
                    filename, result.getMessages());
            }
            
        } catch (Exception e) {
            log.error("Error loading schema: {}", resource.getFilename(), e);
        }
    }
    
    /**
     * Get schema by filename
     */
    public Optional<OpenAPI> getSchema(String schemaName) {
        return Optional.ofNullable(schemas.get(schemaName));
    }
    
    /**
     * Check if a schema exists
     */
    public boolean hasSchema(String schemaName) {
        return schemas.containsKey(schemaName);
    }
    
    /**
     * Get all loaded schema names
     */
    public java.util.Set<String> getLoadedSchemas() {
        return schemas.keySet();
    }
    
    /**
     * Reload all schemas (for development/testing)
     */
    public void reload() {
        schemas.clear();
        loadSchemas();
    }
}
