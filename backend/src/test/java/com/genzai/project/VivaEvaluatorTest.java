package com.genzai.project;

import com.genzai.llm.LlmProviderService;
import com.genzai.project.service.VivaEvaluatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class VivaEvaluatorTest {

    private LlmProviderService mockLlmProvider;
    private VivaEvaluatorService evaluatorService;
    private final UUID testUser = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mockLlmProvider = Mockito.mock(LlmProviderService.class);
        evaluatorService = new VivaEvaluatorService(mockLlmProvider);
    }

    @Test
    @DisplayName("Server-side scoring only: judge response parsed and bounded between 0 and 10")
    void judgeResponseParsing_AndServerSideScoringOnly() {
        when(mockLlmProvider.isGeneralLlmAvailable()).thenReturn(true);
        when(mockLlmProvider.generateGeneralCompletion(anyString(), anyString())).thenReturn(
                Optional.of("{\"correctness\": 9, \"completeness\": 8, \"clarity\": 9, \"feedback\": \"Strong explanation of RLS.\"}")
        );

        var result = evaluatorService.evaluateAnswer(
                testUser,
                "Explain RLS",
                "PostgreSQL Row-Level Security checks app.current_tenant",
                "RLS filters rows at engine level using app.current_tenant session variable"
        );

        assertThat(result.getJudgedBy()).isEqualTo("LLM");
        assertThat(result.getScores().get("correctness")).isEqualTo(9);
        assertThat(result.getScores().get("completeness")).isEqualTo(8);
        assertThat(result.getScores().get("clarity")).isEqualTo(9);
        assertThat(result.getScores().get("overall")).isEqualTo(8.7);
        assertThat(result.getFeedback()).contains("Strong explanation of RLS");
    }

    @Test
    @DisplayName("Offline fallback uses keyword-coverage heuristic labeled 'indicative only'")
    void heuristicEvaluator_ReturnsIndicativeFeedback_OnOfflineFallback() {
        when(mockLlmProvider.isGeneralLlmAvailable()).thenReturn(false);

        String modelAnswer = "Row-Level Security enforces tenant filtering in PostgreSQL using session settings.";
        String studentAnswer = "Row-Level Security applies filtering in PostgreSQL database.";

        var result = evaluatorService.evaluateAnswer(testUser, "How does RLS work?", modelAnswer, studentAnswer);

        assertThat(result.getJudgedBy()).isEqualTo("HEURISTIC");
        assertThat(result.getFeedback()).contains("indicative only");
        Number overall = (Number) result.getScores().get("overall");
        assertThat(overall.doubleValue()).isBetween(0.0, 10.0);
    }

    @Test
    @DisplayName("Rate-limiting enforces maximum evaluations per user per minute")
    void rateLimiter_EnforcesLimitPerUser() {
        when(mockLlmProvider.isGeneralLlmAvailable()).thenReturn(false);

        UUID user = UUID.randomUUID();
        // Submit 15 times
        for (int i = 0; i < 15; i++) {
            evaluatorService.evaluateAnswer(user, "Q", "Answer", "My answer");
        }

        // 16th time within the same minute should be rejected
        assertThatThrownBy(() -> evaluatorService.evaluateAnswer(user, "Q", "Answer", "My answer"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Rate limit exceeded");
    }
}
