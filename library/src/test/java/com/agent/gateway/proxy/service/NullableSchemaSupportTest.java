package com.agent.gateway.proxy.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NullableSchemaSupportTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);

    @Test
    void schemaLoaderAllowsNullForNullableStringProperty() throws Exception {
        JsonNode jsonSchemaNode = invokeSchemaLoaderConversion(buildFrontendAnswerSchema());
        JsonSchema jsonSchema = schemaFactory.getSchema(jsonSchemaNode);

        Set<com.networknt.schema.ValidationMessage> errors = jsonSchema.validate(
            objectMapper.readTree("""
                {"elementId":"abc","value":null}
                """)
        );

        assertTrue(errors.isEmpty(), () -> "Expected nullable value to pass validation but got: " + errors);
    }

    @Test
    void schemaValidationServiceAllowsNullForNullableStringProperty() throws Exception {
        JsonNode jsonSchemaNode = invokeSchemaValidationServiceConversion(buildFrontendAnswerSchema());
        JsonSchema jsonSchema = schemaFactory.getSchema(jsonSchemaNode);

        Set<com.networknt.schema.ValidationMessage> errors = jsonSchema.validate(
            objectMapper.readTree("""
                {"elementId":"abc","value":null}
                """)
        );

        assertTrue(errors.isEmpty(), () -> "Expected nullable value to pass validation but got: " + errors);
    }

    private OpenAPI buildFrontendAnswerSchema() {
        ObjectSchema frontendAnswer = new ObjectSchema();
        frontendAnswer.addProperties("elementId", new StringSchema());
        frontendAnswer.addProperties("value", new StringSchema().nullable(true));

        OpenAPI openAPI = new OpenAPI();
        openAPI.components(new io.swagger.v3.oas.models.Components().addSchemas("FrontendAnswer", frontendAnswer));
        return openAPI;
    }

    private JsonNode invokeSchemaLoaderConversion(OpenAPI openAPI) throws Exception {
        SchemaLoader schemaLoader = new SchemaLoader();
        Method method = SchemaLoader.class.getDeclaredMethod(
            "convertToJsonSchema",
            Schema.class,
            OpenAPI.class
        );
        method.setAccessible(true);
        return (JsonNode) method.invoke(schemaLoader, openAPI.getComponents().getSchemas().get("FrontendAnswer"), openAPI);
    }

    private JsonNode invokeSchemaValidationServiceConversion(OpenAPI openAPI) throws Exception {
        SchemaValidationService service = new SchemaValidationService();
        Method method = SchemaValidationService.class.getDeclaredMethod(
            "convertToJsonSchema",
            Schema.class,
            OpenAPI.class
        );
        method.setAccessible(true);
        return (JsonNode) method.invoke(service, openAPI.getComponents().getSchemas().get("FrontendAnswer"), openAPI);
    }
}
