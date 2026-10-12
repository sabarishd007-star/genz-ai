package com.genzai.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class LlmProviderService {

    private final Optional<ChatClient> chatClientOptional;
    private final String llmModel;
    private final String llmBaseUrl;

    public LlmProviderService(
            Optional<ChatClient> chatClientOptional,
            @Value("${spring.ai.openai.chat.options.model:minigpt}") String llmModel,
            @Value("${spring.ai.openai.base-url:http://localhost:8000}") String llmBaseUrl
    ) {
        this.chatClientOptional = chatClientOptional;
        this.llmModel = llmModel;
        this.llmBaseUrl = llmBaseUrl;
    }

    /**
     * Checks whether a general-purpose LLM (Ollama / OpenAI / external provider)
     * is configured, specifically excluding the local MiniGPT microservice.
     */
    public boolean isGeneralLlmAvailable() {
        if (chatClientOptional.isEmpty()) return false;
        // MiniGPT microservice is strictly reserved for MiniGPT Studio and must NOT be used for aptitude, study, or projects.
        boolean isMiniGpt = (llmModel != null && llmModel.toLowerCase().contains("minigpt"))
                || (llmBaseUrl != null && llmBaseUrl.contains(":8000"));
        return !isMiniGpt;
    }

    /**
     * Generates completion using a real general LLM (if configured and not MiniGPT).
     * Returns Optional.empty() if no real general LLM is available.
     */
    public Optional<String> generateGeneralCompletion(String prompt, String systemPrompt) {
        if (!isGeneralLlmAvailable()) {
            log.info("General LLM unavailable or configured as MiniGPT (which is reserved for MiniGPT Studio). Bypassing.");
            return Optional.empty();
        }

        try {
            ChatClient client = chatClientOptional.get();
            var spec = client.prompt();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                spec.system(systemPrompt);
            }
            String response = spec.user(prompt).call().content();
            if (response != null && !response.isBlank()) {
                return Optional.of(response);
            }
        } catch (Exception ex) {
            log.warn("General LLM call failed ({}).", ex.getMessage());
        }

        return Optional.empty();
    }

    public String generateCompletion(String prompt, String systemPrompt) {
        return generateGeneralCompletion(prompt, systemPrompt)
                .orElseGet(() -> generateOfflineFallback(prompt, systemPrompt));
    }

    private String generateOfflineFallback(String prompt, String systemPrompt) {
        return "Deterministic step-by-step breakdown: Analyze problem conditions, apply standard definitions, eliminate contradictory options.";
    }
}
