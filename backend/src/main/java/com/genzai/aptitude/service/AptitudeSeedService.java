package com.genzai.aptitude.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genzai.aptitude.entity.AptitudeQuestion;
import com.genzai.aptitude.entity.AptitudeTrack;
import com.genzai.aptitude.repository.AptitudeQuestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AptitudeSeedService implements CommandLineRunner {

    private final AptitudeQuestionRepository questionRepository;
    private final ObjectMapper objectMapper;

    private static final String[] SEED_RESOURCES = {
            "aptitude/quantitative_aqua_rat.json",
            "aptitude/logical_seed.json",
            "aptitude/verbal_seed.json",
            "aptitude/core_cs_seed.json"
    };

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Starting Aptitude Engine question bank verification/seeding...");
        int totalInserted = 0;

        for (String resourcePath : SEED_RESOURCES) {
            try {
                ClassPathResource resource = new ClassPathResource(resourcePath);
                if (!resource.exists()) {
                    log.warn("Seed resource {} not found on classpath, skipping.", resourcePath);
                    continue;
                }

                try (InputStream is = resource.getInputStream()) {
                    List<JsonNode> nodes = objectMapper.readValue(is, new TypeReference<List<JsonNode>>() {});
                    List<AptitudeQuestion> batch = new ArrayList<>();

                    for (JsonNode node : nodes) {
                        String hash = node.path("contentHash").asText();
                        if (questionRepository.existsByContentHash(hash)) {
                            continue;
                        }

                        JsonNode optionsNode = node.path("options");
                        String optionsJson = objectMapper.writeValueAsString(optionsNode);

                        AptitudeQuestion q = AptitudeQuestion.builder()
                                .contentHash(hash)
                                .track(AptitudeTrack.valueOf(node.path("track").asText("QUANTITATIVE")))
                                .topic(node.path("topic").asText("OTHER"))
                                .difficulty(node.path("difficulty").asText("MEDIUM"))
                                .difficultyApproximate(node.path("difficultyApproximate").asBoolean(false))
                                .question(node.path("question").asText())
                                .optionsJson(optionsJson)
                                .correctAnswer(node.path("correct").asText("A").toUpperCase())
                                .rationale(node.path("rationale").asText(""))
                                .source(node.path("source").asText("SEED"))
                                .flaggedForReview(node.path("flaggedForReview").asBoolean(false))
                                .build();

                        batch.add(q);
                    }

                    if (!batch.isEmpty()) {
                        questionRepository.saveAll(batch);
                        totalInserted += batch.size();
                        log.info("Seeded {} questions from {}", batch.size(), resourcePath);
                    } else {
                        log.info("All questions from {} are already present.", resourcePath);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to seed aptitude questions from {}: {}", resourcePath, e.getMessage(), e);
            }
        }

        log.info("Aptitude Engine seed complete. Total new questions inserted: {}. Total in bank: {}",
                totalInserted, questionRepository.count());
    }
}
