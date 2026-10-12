package com.genzai.study.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genzai.llm.LlmProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicExtractionService {

    private final LlmProviderService llmProviderService;
    private final ObjectMapper objectMapper;

    private static final Pattern HEADING_PATTERN = Pattern.compile("^(?:#{1,4}\\s*|[0-9]+[\\.\\)]\\s*|[-*•]\\s*)(.+)");

    /**
     * Extracts topics with relative weights from syllabus or document text.
     * Uses general LLM if available, otherwise deterministic heading/line heuristics.
     */
    public List<StudyScheduler.TopicWeight> extractTopics(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        // 1. Try real General LLM (if configured and not MiniGPT)
        if (llmProviderService.isGeneralLlmAvailable()) {
            for (int attempt = 1; attempt <= 2; attempt++) {
                try {
                    String prompt = "Extract the key study topics with relative weight (1.0 to 3.0 based on depth) from the following syllabus text.\n" +
                            "Return strictly a JSON array of objects with 'name' and 'weight', e.g. [{\"name\": \"Topic A\", \"weight\": 1.5}]. Do not include markdown ticks.\n\n" +
                            "Syllabus:\n" + text.substring(0, Math.min(text.length(), 4000));
                    String system = "You are a curriculum analyst. Extract structured study topics in valid JSON format only.";

                    Optional<String> responseOpt = llmProviderService.generateGeneralCompletion(prompt, system);
                    if (responseOpt.isPresent()) {
                        String cleaned = cleanJsonString(responseOpt.get());
                        List<StudyScheduler.TopicWeight> parsed = objectMapper.readValue(
                                cleaned, new TypeReference<List<StudyScheduler.TopicWeight>>() {}
                        );
                        if (parsed != null && !parsed.isEmpty()) {
                            return parsed;
                        }
                    }
                } catch (Exception ex) {
                    log.warn("Topic extraction via LLM attempt {} failed: {}", attempt, ex.getMessage());
                }
            }
        }

        // 2. Deterministic Fallback: Heading and line heuristic parsing
        return extractTopicsOffline(text);
    }

    private List<StudyScheduler.TopicWeight> extractTopicsOffline(String text) {
        Set<String> extractedNames = new LinkedHashSet<>();
        String[] lines = text.split("\\r?\\n");

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.length() < 3 || line.length() > 80) {
                continue;
            }

            var matcher = HEADING_PATTERN.matcher(line);
            if (matcher.find()) {
                String candidate = matcher.group(1).trim();
                if (candidate.length() >= 3 && !candidate.startsWith("http")) {
                    extractedNames.add(cleanTopicTitle(candidate));
                }
            } else if (line.endsWith(":") || line.equalsIgnoreCase("Unit") || line.contains("Chapter")) {
                extractedNames.add(cleanTopicTitle(line.replace(":", "")));
            }
        }

        // If no headings found, split by commas or paragraphs
        if (extractedNames.isEmpty()) {
            for (String part : text.split("[,;\\n]")) {
                String cleaned = part.trim();
                if (cleaned.length() >= 4 && cleaned.length() <= 60) {
                    extractedNames.add(cleanTopicTitle(cleaned));
                }
                if (extractedNames.size() >= 10) break;
            }
        }

        if (extractedNames.isEmpty()) {
            extractedNames.add("Core Fundamentals");
            extractedNames.add("Advanced Concepts");
            extractedNames.add("Applied Problem Solving");
        }

        List<StudyScheduler.TopicWeight> result = new ArrayList<>();
        for (String name : extractedNames) {
            result.add(StudyScheduler.TopicWeight.builder()
                    .name(name)
                    .weight(1.0)
                    .build());
        }

        return result;
    }

    private String cleanTopicTitle(String s) {
        return s.replaceAll("[#*_`]", "").trim();
    }

    private String cleanJsonString(String raw) {
        String s = raw.trim();
        if (s.startsWith("```json")) {
            s = s.substring(7);
        } else if (s.startsWith("```")) {
            s = s.substring(3);
        }
        if (s.endsWith("```")) {
            s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }
}
