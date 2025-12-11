package com.healthplan.indexapi.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * JSON Schema Validator, injected to PlanService.java to validate data
 */
@Component
public class JsonSchemaValidator {

    private final JsonSchema schema;
    private final ObjectMapper objectMapper;

    public JsonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        try {
            JsonNode schemaNode = JsonLoader.fromResource("/schemas/plan-schema.json"); // Use JsonLoader load plan-schema.json from resource
            JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
            this.schema = factory.getJsonSchema(schemaNode);

        } catch (ProcessingException | IOException e) {
            throw new RuntimeException("Failed to load JSON schema", e);
        }
    }

    public void validate(String jsonString) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString); // Json string -> node
            ProcessingReport report = schema.validate(jsonNode); // validate node by schema

            if (!report.isSuccess()) {
                throw new IllegalArgumentException("JSON validation failed: " + report.toString());
            }
        } catch (ProcessingException e) {
            throw new IllegalArgumentException("JSON validation error: " + e.getMessage());
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid JSON format: " + e.getMessage());
        }
    }
}
