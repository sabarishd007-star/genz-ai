package com.genzai.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class BlueprintValidationService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JsonSchema jsonSchema;

    @Getter
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        public ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }
    }

    @PostConstruct
    public void init() {
        try {
            JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
            InputStream schemaStream = new ClassPathResource("schemas/project-blueprint-schema.json").getInputStream();
            this.jsonSchema = factory.getSchema(schemaStream);
            log.info("Initialized Draft 2020-12 Project Blueprint JSON Schema");
        } catch (Exception e) {
            log.error("Failed to load project-blueprint-schema.json", e);
            throw new IllegalStateException("Could not load blueprint schema", e);
        }
    }

    public ValidationResult validate(Map<String, Object> content) {
        try {
            JsonNode jsonNode = objectMapper.valueToTree(content);
            return validateNode(jsonNode);
        } catch (Exception e) {
            log.warn("Failed to convert map to JsonNode during validation: {}", e.getMessage());
            return new ValidationResult(false, List.of("Malformed JSON structure: " + e.getMessage()));
        }
    }

    public ValidationResult validate(String jsonString) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            return validateNode(jsonNode);
        } catch (Exception e) {
            log.warn("Failed to parse string to JsonNode: {}", e.getMessage());
            return new ValidationResult(false, List.of("Invalid JSON format: " + e.getMessage()));
        }
    }

    private ValidationResult validateNode(JsonNode node) {
        Set<ValidationMessage> messages = jsonSchema.validate(node);
        if (messages.isEmpty()) {
            return new ValidationResult(true, List.of());
        }

        List<String> errors = new ArrayList<>();
        for (ValidationMessage msg : messages) {
            errors.add(msg.getMessage());
        }
        return new ValidationResult(false, errors);
    }
}
