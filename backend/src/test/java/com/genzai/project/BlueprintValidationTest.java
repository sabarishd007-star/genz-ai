package com.genzai.project;

import com.genzai.project.service.BlueprintValidationService;
import com.genzai.project.template.DomainBlueprintTemplates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BlueprintValidationTest {

    private BlueprintValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new BlueprintValidationService();
        validationService.init();
    }

    @Test
    @DisplayName("Template fallback for Full-Stack Web is always Draft 2020-12 schema-valid")
    void fullStackWebTemplate_IsAlwaysSchemaValid() {
        Map<String, Object> template = DomainBlueprintTemplates.getFullStackWebTemplate("Ecommerce Platform", "Multi-tenant storefront");
        BlueprintValidationService.ValidationResult result = validationService.validate(template);

        assertThat(result.isValid())
                .withFailMessage("Full-Stack Web template schema validation failed: " + result.getErrors())
                .isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Template fallback for AI/ML is always Draft 2020-12 schema-valid")
    void aiMlTemplate_IsAlwaysSchemaValid() {
        Map<String, Object> template = DomainBlueprintTemplates.getAiMlTemplate("GenZ RAG", "Local causal transformer");
        BlueprintValidationService.ValidationResult result = validationService.validate(template);

        assertThat(result.isValid())
                .withFailMessage("AI/ML template schema validation failed: " + result.getErrors())
                .isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Template fallback for Mobile/Cloud is always Draft 2020-12 schema-valid")
    void mobileCloudTemplate_IsAlwaysSchemaValid() {
        Map<String, Object> template = DomainBlueprintTemplates.getMobileCloudTemplate("SyncMaster", "Offline delta synchronization");
        BlueprintValidationService.ValidationResult result = validationService.validate(template);

        assertThat(result.isValid())
                .withFailMessage("Mobile/Cloud template schema validation failed: " + result.getErrors())
                .isTrue();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    @DisplayName("Schema validation rejects malformed JSON missing required properties and returns errors")
    void schemaValidation_RejectsMalformedJson() {
        Map<String, Object> malformed = new HashMap<>();
        malformed.put("problemStatement", "Short"); // Too short (< 20)
        malformed.put("features", Map.of("mvp", List.of())); // Missing later, empty mvp

        BlueprintValidationService.ValidationResult result = validationService.validate(malformed);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors()).isNotEmpty();
    }
}
