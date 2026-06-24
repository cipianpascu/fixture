package com.agent.gateway.proxy.service;

import com.agent.gateway.proxy.config.ProxyProperties;
import com.agent.gateway.proxy.TestHistoryConfig;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaLoaderTest {

    @Test
    void detectsSelfContainedOpenApi31Schema() throws Exception {
        SchemaLoader schemaLoader = new SchemaLoader();
        URL schemaUrl = getClass().getResource("/schemas/self-contained-openapi31.json");
        assertNotNull(schemaUrl);

        String schemaContent = Files.readString(Path.of(schemaUrl.toURI()));
        assertTrue(schemaLoader.hasOnlyInternalReferences(schemaContent, schemaUrl.getPath()));
    }

    @Test
    void detectsExternalReferences() {
        SchemaLoader schemaLoader = new SchemaLoader();
        String schema = """
            {
              "openapi": "3.1.0",
              "info": {"title": "test", "version": "1"},
              "paths": {},
              "components": {
                "schemas": {
                  "Wrapper": {
                    "properties": {
                      "value": {
                        "$ref": "./common.json#/components/schemas/Value"
                      }
                    }
                  }
                }
              }
            }
            """;

        assertFalse(schemaLoader.hasOnlyInternalReferences(schema, "wrapper.json"));
    }

    @Test
    void parsesSelfContainedOpenApi31SchemaFromContents() throws Exception {
        SchemaLoader schemaLoader = new SchemaLoader();
        URL schemaUrl = getClass().getResource("/schemas/self-contained-openapi31.json");
        assertNotNull(schemaUrl);

        SwaggerParseResult result = schemaLoader.parseSchema(schemaUrl);

        assertNotNull(result.getOpenAPI());
        assertTrue(result.getMessages() == null || result.getMessages().isEmpty(), () -> String.valueOf(result.getMessages()));
        assertNotNull(result.getOpenAPI().getComponents().getSchemas().get("EmailAddressWithConfirmation"));
    }

    @Test
    void getDocumentationSchemasLoadsSchemasLazily() throws Exception {
        SchemaLoader schemaLoader = new SchemaLoader();
        injectProxyProperties(schemaLoader, proxyPropertiesFor("/schemas/", true));

        Map<String, org.eclipse.microprofile.openapi.models.OpenAPI> documentationSchemas = schemaLoader.getDocumentationSchemas();

        assertFalse(documentationSchemas.isEmpty());
        assertNotNull(documentationSchemas.get("self-contained-openapi31.json"));
    }

    @Test
    void sanitizesCrLfForLogOutput() throws Exception {
        SchemaLoader schemaLoader = new SchemaLoader();
        Method sanitizer = SchemaLoader.class.getDeclaredMethod("sanitizeForLog", String.class);
        sanitizer.setAccessible(true);

        String sanitized = (String) sanitizer.invoke(schemaLoader, "schema.yaml\r\nforged=true");

        assertEquals("schema.yaml__forged=true", sanitized);
    }

    private void injectProxyProperties(SchemaLoader schemaLoader, ProxyProperties proxyProperties) throws Exception {
        Field field = SchemaLoader.class.getDeclaredField("proxyProperties");
        field.setAccessible(true);
        field.set(schemaLoader, proxyProperties);
    }

    private ProxyProperties proxyPropertiesFor(String schemaDirectory, boolean validateBodies) {
        ProxyProperties.SchemaConfig schemaConfig = (ProxyProperties.SchemaConfig) Proxy.newProxyInstance(
            ProxyProperties.SchemaConfig.class.getClassLoader(),
            new Class[]{ProxyProperties.SchemaConfig.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "directory" -> "classpath:" + schemaDirectory;
                case "validateRequests" -> true;
                case "validateBodies" -> validateBodies;
                case "validateResponses" -> false;
                case "strictMode" -> true;
                default -> method.getDefaultValue();
            }
        );

        ProxyProperties.AuthConfig authConfig = (ProxyProperties.AuthConfig) Proxy.newProxyInstance(
            ProxyProperties.AuthConfig.class.getClassLoader(),
            new Class[]{ProxyProperties.AuthConfig.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "enabled" -> true;
                case "serviceUrl" -> "http://localhost:8081";
                case "sessionIdHeader" -> "X-Session-Id";
                case "sessionIdCookie" -> "sessionId";
                case "timeout" -> java.time.Duration.ofSeconds(5);
                case "tlsProfile", "securityType" -> Optional.empty();
                case "securityConfig" -> Map.of();
                default -> method.getDefaultValue();
            }
        );

        return (ProxyProperties) Proxy.newProxyInstance(
            ProxyProperties.class.getClassLoader(),
            new Class[]{ProxyProperties.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "schemas" -> schemaConfig;
                case "auth" -> authConfig;
                case "history" -> TestHistoryConfig.disabled();
                case "tls" -> Optional.empty();
                case "backends" -> List.of();
                default -> method.getDefaultValue();
            }
        );
    }
}
