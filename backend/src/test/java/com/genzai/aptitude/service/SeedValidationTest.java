package com.genzai.aptitude.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SeedValidationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern GARBLED_PATTERN = Pattern.compile("â\\x80|Ã[^\\s]|\\ufffd|\\\\u00[89][0-9a-f]");

    private static final String[] SEED_FILES = {
            "aptitude/quantitative_aqua_rat.json",
            "aptitude/logical_seed.json",
            "aptitude/verbal_seed.json",
            "aptitude/core_cs_seed.json"
    };

    @Test
    @DisplayName("All bundled seed banks adhere strictly to data hygiene rules")
    void validateAllSeedBanks() throws Exception {
        Set<String> seenHashes = new HashSet<>();

        for (String filePath : SEED_FILES) {
            ClassPathResource res = new ClassPathResource(filePath);
            assertThat(res.exists()).as("Seed file exists: " + filePath).isTrue();

            try (InputStream is = res.getInputStream()) {
                List<JsonNode> nodes = objectMapper.readValue(is, new TypeReference<List<JsonNode>>() {});
                assertThat(nodes).as("Seed file not empty: " + filePath).isNotEmpty();

                for (JsonNode q : nodes) {
                    String hash = q.path("contentHash").asText();
                    assertThat(hash).as("contentHash is present").isNotBlank();
                    assertThat(seenHashes.add(hash)).as("Duplicate hash detected: " + hash).isTrue();

                    String question = q.path("question").asText();
                    assertThat(question).as("Question text is non-empty").isNotBlank();

                    // Rule 1: Exactly 5 options
                    JsonNode optionsNode = q.path("options");
                    assertThat(optionsNode.isArray()).isTrue();
                    assertThat(optionsNode.size()).as("Must have exactly 5 options: " + question).isEqualTo(5);

                    // Rule 2: Valid correct answer key
                    String correct = q.path("correct").asText().toUpperCase();
                    assertThat(correct).as("Correct key must be A-E").isIn("A", "B", "C", "D", "E");

                    // Rule 3: No duplicate options & no single letter placeholders
                    Set<String> optionTexts = new HashSet<>();
                    for (JsonNode opt : optionsNode) {
                        String optStr = opt.asText().trim();
                        assertThat(optStr).as("Option must not be empty").isNotBlank();
                        // Must not be a single placeholder letter (e.g. "A", "B", etc.)
                        assertThat(optStr.matches("^[A-Ea-e]$"))
                                .as("Option must not be a single letter placeholder: " + optStr).isFalse();
                        assertThat(optionTexts.add(optStr.toLowerCase()))
                                .as("Duplicate option in question: " + optStr).isTrue();
                    }

                    // Rule 4: No garbled unicode
                    String allText = question + " " + q.path("rationale").asText();
                    assertThat(GARBLED_PATTERN.matcher(allText).find())
                            .as("Garbled text detected in: " + question).isFalse();
                }
            }
        }
    }
}
