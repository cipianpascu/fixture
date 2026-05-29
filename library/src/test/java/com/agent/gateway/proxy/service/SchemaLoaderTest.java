package com.agent.gateway.proxy.service;

import io.swagger.v3.parser.core.models.SwaggerParseResult;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

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
}
