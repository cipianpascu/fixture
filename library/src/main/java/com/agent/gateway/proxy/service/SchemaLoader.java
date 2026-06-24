package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.smallrye.openapi.runtime.io.OpenApiParser;
import io.quarkus.runtime.StartupEvent;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URL;
import java.net.JarURLConnection;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Schema Loader Service (Quarkus)
 * 
 * Loads OpenAPI schemas from classpath.
 * Schemas are loaded once at startup and cached in memory.
 */
@ApplicationScoped
@Slf4j
public class SchemaLoader {
    
    @Inject
    ProxyProperties proxyProperties;
    
    private final Map<String, OpenAPI> schemas = new ConcurrentHashMap<>();
    private final Map<String, org.eclipse.microprofile.openapi.models.OpenAPI> documentationSchemas = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Map<String, com.networknt.schema.JsonSchema>>> cachedJsonSchemas = new ConcurrentHashMap<>();
    private volatile boolean initialized;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory());
    private final com.networknt.schema.JsonSchemaFactory schemaFactory = 
        com.networknt.schema.JsonSchemaFactory.getInstance(com.networknt.schema.SpecVersion.VersionFlag.V7);
    
    void onStart(@Observes StartupEvent event) {
        ensureLoaded();
    }
    
    public void loadSchemas() {
        ensureLoaded();
    }

    public void ensureLoaded() {
        if (initialized) {
            return;
        }

        synchronized (this) {
            if (initialized) {
                return;
            }
            loadSchemasInternal();
            initialized = true;
        }
    }

    private void loadSchemasInternal() {
        String schemaDirectory = proxyProperties.schemas().directory();
        log.info("Loading schemas from: {}", sanitizeForLog(schemaDirectory));

        try {
            Set<String> schemaFiles = new LinkedHashSet<>(discoverSchemaFiles(schemaDirectory));
            schemaFiles.addAll(proxyProperties.backends().stream()
                .map(ProxyProperties.BackendDefinition::schema)
                .flatMap(Optional::stream)
                .filter(schemaName -> !schemaName.isBlank())
                .collect(Collectors.toSet()));

            if (schemaFiles.isEmpty()) {
                log.info("No schemas discovered under {}", sanitizeForLog(schemaDirectory));
                return;
            }

            for (String filename : schemaFiles) {
                Optional<URL> schemaUrl = resolveSchemaUrl(schemaDirectory, filename);
                if (schemaUrl.isPresent()) {
                    loadSchema(filename, schemaUrl.get());
                } else {
                    log.error(
                        "Configured schema '{}' could not be resolved from {}",
                        sanitizeForLog(filename),
                        sanitizeForLog(schemaDirectory)
                    );
                }
            }
            
            log.info("Loaded {} schemas successfully", schemas.size());
            schemas.keySet().forEach(name -> log.info("  - {}", sanitizeForLog(name)));
            
        } catch (Exception e) {
            log.error("Failed to load schemas from: {}", sanitizeForLog(schemaDirectory), e);
            throw new IllegalStateException("Failed to load schemas from " + schemaDirectory, e);
        }
    }

    private Optional<URL> resolveSchemaUrl(String schemaDirectory, String filename) {
        try {
            if (schemaDirectory.startsWith("classpath:") || schemaDirectory.startsWith("classpath*:")) {
                String resourcePath = schemaDirectory
                    .replace("classpath*:", "")
                    .replace("classpath:", "");
                if (!resourcePath.endsWith("/")) {
                    resourcePath += "/";
                }
                if (resourcePath.startsWith("/")) {
                    resourcePath = resourcePath.substring(1);
                }

                ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                return Optional.ofNullable(classLoader.getResource(resourcePath + filename));
            }

            Path basePath;
            if (schemaDirectory.startsWith("file:")) {
                basePath = Paths.get(URI.create(schemaDirectory));
            } else {
                basePath = Paths.get(schemaDirectory);
            }

            Path schemaPath = basePath.resolve(filename);
            if (Files.exists(schemaPath)) {
                return Optional.of(schemaPath.toUri().toURL());
            }
        } catch (Exception e) {
            log.error(
                "Failed to resolve schema '{}' from {}",
                sanitizeForLog(filename),
                sanitizeForLog(schemaDirectory),
                e
            );
        }

        return Optional.empty();
    }

    private Set<String> discoverSchemaFiles(String schemaDirectory) {
        Set<String> filenames = new LinkedHashSet<>();

        try {
            if (schemaDirectory.startsWith("classpath:") || schemaDirectory.startsWith("classpath*:")) {
                String resourcePath = schemaDirectory
                    .replace("classpath*:", "")
                    .replace("classpath:", "");
                if (!resourcePath.endsWith("/")) {
                    resourcePath += "/";
                }
                if (resourcePath.startsWith("/")) {
                    resourcePath = resourcePath.substring(1);
                }

                Enumeration<URL> resources = Thread.currentThread()
                    .getContextClassLoader()
                    .getResources(resourcePath);

                while (resources.hasMoreElements()) {
                    URL url = resources.nextElement();
                    if ("file".equals(url.getProtocol())) {
                        try (Stream<Path> paths = Files.list(Paths.get(url.toURI()))) {
                            paths.filter(Files::isRegularFile)
                                .map(path -> path.getFileName().toString())
                                .filter(this::isSchemaFile)
                                .forEach(filenames::add);
                        }
                    } else if ("jar".equals(url.getProtocol())) {
                        JarURLConnection connection = (JarURLConnection) url.openConnection();
                        try (JarFile jarFile = connection.getJarFile()) {
                            String prefix = connection.getEntryName();
                            Enumeration<JarEntry> entries = jarFile.entries();
                            while (entries.hasMoreElements()) {
                                JarEntry entry = entries.nextElement();
                                String name = entry.getName();
                                if (entry.isDirectory() || !name.startsWith(prefix) || name.equals(prefix)) {
                                    continue;
                                }
                                String candidate = name.substring(prefix.length());
                                if (!candidate.contains("/") && isSchemaFile(candidate)) {
                                    filenames.add(candidate);
                                }
                            }
                        }
                    }
                }
                return filenames;
            }

            Path basePath = schemaDirectory.startsWith("file:")
                ? Paths.get(URI.create(schemaDirectory))
                : Paths.get(schemaDirectory);

            if (Files.isDirectory(basePath)) {
                try (Stream<Path> paths = Files.list(basePath)) {
                    paths.filter(Files::isRegularFile)
                        .map(path -> path.getFileName().toString())
                        .filter(this::isSchemaFile)
                        .forEach(filenames::add);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to enumerate schema files from {}", sanitizeForLog(schemaDirectory), e);
        }

        return filenames;
    }

    private boolean isSchemaFile(String filename) {
        String lowerCase = filename.toLowerCase(Locale.ROOT);
        return lowerCase.endsWith(".yaml") || lowerCase.endsWith(".yml") || lowerCase.endsWith(".json");
    }

    private String sanitizeForLog(String value) {
        if (value == null) {
            return null;
        }
        return value
            .replace('\r', '_')
            .replace('\n', '_');
    }
    
    private void loadSchema(String filename, URL schemaUrl) {
        try {
            SwaggerParseResult result = parseSchema(schemaUrl);
            
            if (result.getOpenAPI() != null) {
                OpenAPI openAPI = result.getOpenAPI();
                schemas.put(filename, openAPI);
                documentationSchemas.put(filename, OpenApiParser.parse(schemaUrl));
                
                // Pre-compile JSON schemas for validation (optimization) if body validation enabled
                if (proxyProperties.schemas().validateBodies()) {
                    preCompileJsonSchemas(filename, openAPI);
                }
                
                log.info("Loaded schema: {}", filename);
            } else {
                log.error("Failed to parse schema: {} - {}", 
                    filename, result.getMessages());
            }
            
        } catch (Exception e) {
            log.error("Error loading schema: {}", filename, e);
        }
    }

    SwaggerParseResult parseSchema(URL schemaUrl) throws Exception {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        options.setResolveFully(true);

        OpenAPIV3Parser parser = new OpenAPIV3Parser();
        String schemaContent;
        try (InputStream inputStream = schemaUrl.openStream()) {
            schemaContent = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (hasOnlyInternalReferences(schemaContent, schemaUrl.getPath())) {
            return parser.readContents(schemaContent, null, options);
        }
        return parser.readLocation(schemaUrl.toString(), null, options);
    }

    boolean hasOnlyInternalReferences(String schemaContent, String locationHint) {
        try {
            JsonNode root = parseSchemaTree(schemaContent, locationHint);
            return hasOnlyInternalReferences(root);
        } catch (Exception e) {
            log.debug("Falling back to location-based schema parsing for {}", locationHint, e);
            return false;
        }
    }

    private JsonNode parseSchemaTree(String schemaContent, String locationHint) throws Exception {
        String normalizedLocation = locationHint == null ? "" : locationHint.toLowerCase(Locale.ROOT);
        if (normalizedLocation.endsWith(".yaml") || normalizedLocation.endsWith(".yml")) {
            return yamlObjectMapper.readTree(schemaContent);
        }
        return objectMapper.readTree(schemaContent);
    }

    private boolean hasOnlyInternalReferences(JsonNode node) {
        if (node == null) {
            return true;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if ("$ref".equals(field.getKey()) && field.getValue().isTextual()) {
                    String ref = field.getValue().asText();
                    if (!ref.startsWith("#")) {
                        return false;
                    }
                }
                if (!hasOnlyInternalReferences(field.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                if (!hasOnlyInternalReferences(child)) {
                    return false;
                }
            }
        }
        return true;
    }
    
    /**
     * Get schema by filename
     */
    public Optional<OpenAPI> getSchema(String schemaName) {
        return Optional.ofNullable(schemas.get(schemaName));
    }

    public Optional<org.eclipse.microprofile.openapi.models.OpenAPI> getDocumentationSchema(String schemaName) {
        return Optional.ofNullable(documentationSchemas.get(schemaName));
    }

    public Map<String, org.eclipse.microprofile.openapi.models.OpenAPI> getDocumentationSchemas() {
        ensureLoaded();
        return Collections.unmodifiableMap(documentationSchemas);
    }
    
    /**
     * Check if a schema exists
     */
    public boolean hasSchema(String schemaName) {
        return getSchema(schemaName).isPresent();
    }
    
    /**
     * Get all loaded schema names
     */
    public java.util.Set<String> getLoadedSchemas() {
        return schemas.keySet();
    }

    /**
     * Get pre-compiled JSON schema for validation (cached at load time)
     * 
     * @param schemaName The schema filename
     * @param path The API path
     * @param method The HTTP method
     * @return Pre-compiled JsonSchema, or null if not found
     */
    public com.networknt.schema.JsonSchema getCompiledJsonSchema(String schemaName, String path, String method) {
        Map<String, Map<String, com.networknt.schema.JsonSchema>> pathMap = cachedJsonSchemas.get(schemaName);
        if (pathMap == null) {
            return null;
        }
        Map<String, com.networknt.schema.JsonSchema> methodMap = pathMap.getOrDefault(path, Map.of());
        return methodMap.getOrDefault(method.toUpperCase(), null);
    }
    
    /**
     * Pre-compile JSON schemas for all paths and methods in an OpenAPI spec
     * This is done once at load time for performance optimization
     */
    private void preCompileJsonSchemas(String schemaName, OpenAPI openAPI) {
        if (openAPI.getPaths() == null) {
            return;
        }
        
        Map<String, Map<String, com.networknt.schema.JsonSchema>> pathMap = new HashMap<>();
        
        for (Map.Entry<String, io.swagger.v3.oas.models.PathItem> pathEntry : openAPI.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            io.swagger.v3.oas.models.PathItem pathItem = pathEntry.getValue();
            
            Map<String, com.networknt.schema.JsonSchema> methodMap = new HashMap<>();
            
            // Pre-compile for each HTTP method
            preCompileForOperation(openAPI, "GET", pathItem.getGet(), methodMap);
            preCompileForOperation(openAPI, "POST", pathItem.getPost(), methodMap);
            preCompileForOperation(openAPI, "PUT", pathItem.getPut(), methodMap);
            preCompileForOperation(openAPI, "DELETE", pathItem.getDelete(), methodMap);
            preCompileForOperation(openAPI, "PATCH", pathItem.getPatch(), methodMap);
            preCompileForOperation(openAPI, "HEAD", pathItem.getHead(), methodMap);
            preCompileForOperation(openAPI, "OPTIONS", pathItem.getOptions(), methodMap);
            
            if (!methodMap.isEmpty()) {
                pathMap.put(path, methodMap);
            }
        }
        
        cachedJsonSchemas.put(schemaName, pathMap);
        log.debug("Pre-compiled {} JSON schemas for {}", pathMap.size(), schemaName);
    }
    
    /**
     * Pre-compile JSON schema for a specific operation
     */
    private void preCompileForOperation(OpenAPI openAPI, String method, 
                                        io.swagger.v3.oas.models.Operation operation,
                                        Map<String, com.networknt.schema.JsonSchema> methodMap) {
        if (operation == null) {
            return;
        }
        
        io.swagger.v3.oas.models.parameters.RequestBody requestBody = operation.getRequestBody();
        if (requestBody == null || requestBody.getContent() == null) {
            return;
        }
        
        io.swagger.v3.oas.models.media.Content content = requestBody.getContent();
        io.swagger.v3.oas.models.media.MediaType mediaType = content.get("application/json");
        if (mediaType == null) {
            mediaType = content.get("*/*");
        }
        
        if (mediaType == null || mediaType.getSchema() == null) {
            return;
        }
        
        try {
            // Convert OpenAPI schema to JSON Schema format
            com.fasterxml.jackson.databind.JsonNode schemaNode = convertToJsonSchema(mediaType.getSchema(), openAPI);
            
            // Pre-compile the JSON schema
            com.networknt.schema.JsonSchema jsonSchema = schemaFactory.getSchema(schemaNode);
            
            methodMap.put(method, jsonSchema);
            log.trace("Pre-compiled JSON schema for {} {}", method, operation.getOperationId());
            
        } catch (Exception e) {
            log.warn("Failed to pre-compile schema for {} operation: {}", method, e.getMessage());
        }
    }
    
    /**
     * Convert OpenAPI schema to JSON Schema format
     * (Reused from SchemaValidationService - could be extracted to utility class)
     */
    private com.fasterxml.jackson.databind.JsonNode convertToJsonSchema(
            io.swagger.v3.oas.models.media.Schema<?> schema, OpenAPI openAPI) throws Exception {
        return convertToJsonSchema(schema, openAPI, new HashSet<>(), Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private com.fasterxml.jackson.databind.JsonNode convertToJsonSchema(
            io.swagger.v3.oas.models.media.Schema<?> schema,
            OpenAPI openAPI,
            Set<String> visitingRefs,
            Set<io.swagger.v3.oas.models.media.Schema<?>> visitingSchemas) throws Exception {
        
        Map<String, Object> jsonSchema = new HashMap<>();
        
        // Handle $ref
        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            if (!visitingRefs.add(ref)) {
                return objectMapper.valueToTree(shallowSchemaMap(resolveSchemaReference(ref, openAPI)));
            }
            io.swagger.v3.oas.models.media.Schema<?> resolvedSchema = resolveSchemaReference(ref, openAPI);
            if (resolvedSchema != null) {
                try {
                    return convertToJsonSchema(resolvedSchema, openAPI, visitingRefs, visitingSchemas);
                } finally {
                    visitingRefs.remove(ref);
                }
            }
        }

        if (!visitingSchemas.add(schema)) {
            return objectMapper.valueToTree(shallowSchemaMap(schema));
        }
        
        jsonSchema.put("$schema", "http://json-schema.org/draft-07/schema#");
        
        if (schema.getType() != null) {
            jsonSchema.put("type", jsonSchemaType(schema));
        }
        
        if (schema.getProperties() != null && !schema.getProperties().isEmpty()) {
            Map<String, Object> properties = new HashMap<>();
            for (Map.Entry<String, io.swagger.v3.oas.models.media.Schema> entry : schema.getProperties().entrySet()) {
                properties.put(entry.getKey(), schemaToMap(entry.getValue(), openAPI, visitingRefs, visitingSchemas));
            }
            jsonSchema.put("properties", properties);
        }
        
        if (schema.getRequired() != null && !schema.getRequired().isEmpty()) {
            jsonSchema.put("required", schema.getRequired());
        }
        
        if (schema.getItems() != null) {
            jsonSchema.put("items", schemaToMap(schema.getItems(), openAPI, visitingRefs, visitingSchemas));
        }
        
        if (schema.getEnum() != null) {
            jsonSchema.put("enum", schema.getEnum());
        }
        
        if (schema.getFormat() != null) {
            jsonSchema.put("format", schema.getFormat());
        }
        
        if (schema.getMinimum() != null) {
            jsonSchema.put("minimum", schema.getMinimum());
        }
        
        if (schema.getMaximum() != null) {
            jsonSchema.put("maximum", schema.getMaximum());
        }
        
        if (schema.getMinLength() != null) {
            jsonSchema.put("minLength", schema.getMinLength());
        }
        
        if (schema.getMaxLength() != null) {
            jsonSchema.put("maxLength", schema.getMaxLength());
        }
        
        if (schema.getPattern() != null) {
            jsonSchema.put("pattern", schema.getPattern());
        }
        
        visitingSchemas.remove(schema);
        return objectMapper.valueToTree(jsonSchema);
    }
    
    private Map<String, Object> schemaToMap(io.swagger.v3.oas.models.media.Schema<?> schema, OpenAPI openAPI) {
        return schemaToMap(schema, openAPI, new HashSet<>(), Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private Map<String, Object> schemaToMap(
        io.swagger.v3.oas.models.media.Schema<?> schema,
        OpenAPI openAPI,
        Set<String> visitingRefs,
        Set<io.swagger.v3.oas.models.media.Schema<?>> visitingSchemas) {
        Map<String, Object> map = new HashMap<>();
        
        if (schema.get$ref() != null) {
            String ref = schema.get$ref();
            if (!visitingRefs.add(ref)) {
                return shallowSchemaMap(resolveSchemaReference(ref, openAPI));
            }
            io.swagger.v3.oas.models.media.Schema<?> resolvedSchema = resolveSchemaReference(ref, openAPI);
            if (resolvedSchema != null) {
                try {
                    return schemaToMap(resolvedSchema, openAPI, visitingRefs, visitingSchemas);
                } finally {
                    visitingRefs.remove(ref);
                }
            }
        }

        if (!visitingSchemas.add(schema)) {
            return shallowSchemaMap(schema);
        }
        
        if (schema.getType() != null) map.put("type", jsonSchemaType(schema));
        if (schema.getProperties() != null) {
            Map<String, Object> properties = new HashMap<>();
            for (Map.Entry<String, io.swagger.v3.oas.models.media.Schema> entry : schema.getProperties().entrySet()) {
                properties.put(entry.getKey(), schemaToMap(entry.getValue(), openAPI, visitingRefs, visitingSchemas));
            }
            map.put("properties", properties);
        }
        if (schema.getRequired() != null) map.put("required", schema.getRequired());
        if (schema.getItems() != null) map.put("items", schemaToMap(schema.getItems(), openAPI, visitingRefs, visitingSchemas));
        if (schema.getEnum() != null) map.put("enum", schema.getEnum());
        if (schema.getFormat() != null) map.put("format", schema.getFormat());
        if (schema.getMinimum() != null) map.put("minimum", schema.getMinimum());
        if (schema.getMaximum() != null) map.put("maximum", schema.getMaximum());
        if (schema.getMinLength() != null) map.put("minLength", schema.getMinLength());
        if (schema.getMaxLength() != null) map.put("maxLength", schema.getMaxLength());
        if (schema.getPattern() != null) map.put("pattern", schema.getPattern());
        visitingSchemas.remove(schema);
        return map;
    }

    private Map<String, Object> shallowSchemaMap(io.swagger.v3.oas.models.media.Schema<?> schema) {
        Map<String, Object> map = new HashMap<>();
        if (schema == null) {
            return map;
        }
        if (schema.getType() != null) {
            map.put("type", jsonSchemaType(schema));
        }
        if (schema.getEnum() != null) {
            map.put("enum", schema.getEnum());
        }
        if (schema.getFormat() != null) {
            map.put("format", schema.getFormat());
        }
        if (schema.getMinimum() != null) {
            map.put("minimum", schema.getMinimum());
        }
        if (schema.getMaximum() != null) {
            map.put("maximum", schema.getMaximum());
        }
        if (schema.getMinLength() != null) {
            map.put("minLength", schema.getMinLength());
        }
        if (schema.getMaxLength() != null) {
            map.put("maxLength", schema.getMaxLength());
        }
        if (schema.getPattern() != null) {
            map.put("pattern", schema.getPattern());
        }
        return map;
    }
    
    private io.swagger.v3.oas.models.media.Schema<?> resolveSchemaReference(String ref, OpenAPI openAPI) {
        if (ref == null || !ref.startsWith("#/components/schemas/")) {
            return null;
        }
        
        String schemaName = ref.substring("#/components/schemas/".length());
        
        if (openAPI.getComponents() != null && openAPI.getComponents().getSchemas() != null) {
            return openAPI.getComponents().getSchemas().get(schemaName);
        }
        
        return null;
    }

    private Object jsonSchemaType(io.swagger.v3.oas.models.media.Schema<?> schema) {
        if (schema == null || schema.getType() == null) {
            return null;
        }
        if (Boolean.TRUE.equals(schema.getNullable())) {
            return List.of(schema.getType(), "null");
        }
        return schema.getType();
    }
    
    /**
     * Reload all schemas (for development/testing)
     */
    public void reload() {
        synchronized (this) {
            initialized = false;
            schemas.clear();
            documentationSchemas.clear();
            cachedJsonSchemas.clear();
            loadSchemasInternal();
            initialized = true;
        }
    }
}
