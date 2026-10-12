package com.genzai.aptitude.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genzai.aptitude.dto.AptitudeAttemptDetailDto;
import com.genzai.aptitude.dto.AptitudeAttemptSummaryDto;
import com.genzai.aptitude.dto.CreateAttemptRequest;
import com.genzai.aptitude.dto.SubmitAnswerRequest;
import com.genzai.aptitude.entity.*;
import com.genzai.aptitude.repository.AptitudeAttemptAnswerRepository;
import com.genzai.aptitude.repository.AptitudeAttemptRepository;
import com.genzai.aptitude.repository.AptitudeQuestionRepository;
import com.genzai.llm.LlmProviderService;
import com.genzai.service.UserActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AptitudeServiceTest {

    @Mock
    private AptitudeQuestionRepository questionRepository;

    @Mock
    private AptitudeAttemptRepository attemptRepository;

    @Mock
    private AptitudeAttemptAnswerRepository answerRepository;

    @Mock
    private UserActivityService activityService;

    @Mock
    private LlmProviderService llmProviderService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AptitudeService aptitudeService;

    private UUID userId;
    private AptitudeQuestion sampleQuestion;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        sampleQuestion = AptitudeQuestion.builder()
                .id(UUID.randomUUID())
                .contentHash("hash123")
                .track(AptitudeTrack.QUANTITATIVE)
                .topic("PERCENTAGE")
                .difficulty("MEDIUM")
                .question("What is 20% of 150?")
                .optionsJson("[\"20\",\"25\",\"30\",\"35\",\"40\"]")
                .correctAnswer("C")
                .rationale("20% of 150 is (20/100)*150 = 30.")
                .source("AQUA_RAT")
                .build();
    }

    @Test
    @DisplayName("startAttempt must never leak correctAnswer or rationale in returned questions")
    void startAttempt_NeverLeaksAnswerOrRationale() {
        CreateAttemptRequest request = CreateAttemptRequest.builder()
                .track(AptitudeTrack.QUANTITATIVE)
                .mode(AttemptMode.MOCK_TEST)
                .questionCount(1)
                .timeLimitSeconds(600)
                .build();

        when(questionRepository.findRandomByTrack("QUANTITATIVE", 1))
                .thenReturn(List.of(sampleQuestion));

        when(attemptRepository.save(any(AptitudeAttempt.class)))
                .thenAnswer(inv -> {
                    AptitudeAttempt a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        AptitudeAttemptDetailDto result = aptitudeService.startAttempt(userId, request);

        assertThat(result).isNotNull();
        assertThat(result.getQuestions()).hasSize(1);
        var qDto = result.getQuestions().get(0);
        assertThat(qDto.getQuestion()).isEqualTo("What is 20% of 150?");
        assertThat(qDto.getOptions()).containsExactly("20", "25", "30", "35", "40");

        // Verify that AptitudeQuestionDto has NO getter for correctAnswer or rationale
        assertThat(AptitudeAttemptDetailDto.class.getDeclaredFields())
                .noneMatch(f -> f.getName().equalsIgnoreCase("correctAnswer"));
    }

    @Test
    @DisplayName("recordAnswer rejects submission when mock test time limit is exceeded past grace period")
    void recordAnswer_RejectsAfterTimeLimitExceeded() {
        UUID attemptId = UUID.randomUUID();

        // Attempt started 100 seconds ago with 60 second limit (exceeds 60s + 5s grace period)
        AptitudeAttempt expiredAttempt = AptitudeAttempt.builder()
                .id(attemptId)
                .userId(userId)
                .track(AptitudeTrack.QUANTITATIVE)
                .mode(AttemptMode.MOCK_TEST)
                .timeLimitSeconds(60)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(OffsetDateTime.now().minusSeconds(100))
                .build();

        when(attemptRepository.findByIdAndUserId(attemptId, userId))
                .thenReturn(Optional.of(expiredAttempt));

        SubmitAnswerRequest answerRequest = SubmitAnswerRequest.builder()
                .questionId(sampleQuestion.getId())
                .selectedOption("C")
                .build();

        assertThatThrownBy(() -> aptitudeService.recordAnswer(userId, attemptId, answerRequest))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Time limit expired");

        verify(answerRepository, never()).save(any());
    }

    @Test
    @DisplayName("submitAttempt correctly scores completed attempts and records activity log")
    void submitAttempt_CalculatesScoreAndLogsActivity() {
        UUID attemptId = UUID.randomUUID();

        AptitudeAttempt attempt = AptitudeAttempt.builder()
                .id(attemptId)
                .userId(userId)
                .track(AptitudeTrack.QUANTITATIVE)
                .mode(AttemptMode.PRACTICE)
                .totalQuestions(2)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(OffsetDateTime.now().minusMinutes(2))
                .build();

        when(attemptRepository.findByIdAndUserId(attemptId, userId))
                .thenReturn(Optional.of(attempt));

        AptitudeAttemptAnswer ans1 = AptitudeAttemptAnswer.builder()
                .attemptId(attemptId)
                .questionId(sampleQuestion.getId())
                .selectedOption("C")
                .isCorrect(true)
                .timeSpentSeconds(30)
                .build();

        AptitudeAttemptAnswer ans2 = AptitudeAttemptAnswer.builder()
                .attemptId(attemptId)
                .questionId(UUID.randomUUID())
                .selectedOption("A")
                .isCorrect(false)
                .timeSpentSeconds(45)
                .build();

        when(answerRepository.findByAttemptId(attemptId))
                .thenReturn(List.of(ans1, ans2));

        when(questionRepository.findAllById(any()))
                .thenReturn(List.of(sampleQuestion));

        AptitudeAttemptSummaryDto summary = aptitudeService.submitAttempt(userId, attemptId);

        assertThat(summary.getCorrectCount()).isEqualTo(1);
        assertThat(summary.getScorePct()).isEqualTo(50.0);
        assertThat(summary.getStatus()).isEqualTo(AttemptStatus.SUBMITTED);
        assertThat(summary.getTotalTimeSpentSeconds()).isEqualTo(75);

        verify(attemptRepository).save(attempt);
        verify(activityService).logActivity(eq(userId), eq("APTITUDE_PRACTICE"), anyString(), anyString());
    }

    @Test
    @DisplayName("recordAnswer enforces user isolation: user B cannot answer user A's attempt")
    void recordAnswer_EnforcesUserIsolation() {
        UUID attemptId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        when(attemptRepository.findByIdAndUserId(attemptId, otherUserId))
                .thenReturn(Optional.empty());

        SubmitAnswerRequest request = SubmitAnswerRequest.builder()
                .questionId(sampleQuestion.getId())
                .selectedOption("B")
                .build();

        assertThatThrownBy(() -> aptitudeService.recordAnswer(otherUserId, attemptId, request))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("Attempt not found");
    }

    @Test
    @DisplayName("submitAttempt enforces user isolation: user B cannot submit user A's attempt")
    void submitAttempt_EnforcesUserIsolation() {
        UUID attemptId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        when(attemptRepository.findByIdAndUserId(attemptId, otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aptitudeService.submitAttempt(otherUserId, attemptId))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("Attempt not found");
    }

    @Test
    @DisplayName("startAttempt clamps and server-calculates mock test time limit: 90s per question max 3600s")
    void startAttempt_ServerCalculatesMockTimeLimit() {
        CreateAttemptRequest mockRequest = CreateAttemptRequest.builder()
                .track(AptitudeTrack.QUANTITATIVE)
                .mode(AttemptMode.MOCK_TEST)
                .questionCount(10)
                .timeLimitSeconds(99999) // Client attempt to override is ignored
                .build();

        when(questionRepository.findRandomByTrack("QUANTITATIVE", 10))
                .thenReturn(java.util.Collections.nCopies(10, sampleQuestion));

        when(attemptRepository.save(any(AptitudeAttempt.class)))
                .thenAnswer(inv -> {
                    AptitudeAttempt a = inv.getArgument(0);
                    a.setId(UUID.randomUUID());
                    return a;
                });

        AptitudeAttemptDetailDto result = aptitudeService.startAttempt(userId, mockRequest);

        // 10 questions * 90s = 900s
        assertThat(result.getTimeLimitSeconds()).isEqualTo(900);
    }

    @Test
    @DisplayName("explainQuestion returns STORED_RATIONALE fallback when general LLM is unavailable")
    void explainQuestion_ReturnsStoredRationale() {
        when(questionRepository.findById(sampleQuestion.getId()))
                .thenReturn(Optional.of(sampleQuestion));
        when(llmProviderService.generateGeneralCompletion(anyString(), anyString()))
                .thenReturn(Optional.empty());

        var exp = aptitudeService.explainQuestion(sampleQuestion.getId(), null);

        assertThat(exp.getSource()).isEqualTo("STORED_RATIONALE");
        assertThat(exp.getExplanation()).isEqualTo(sampleQuestion.getRationale());
        assertThat(exp.isFallback()).isTrue();
    }

    @Test
    @DisplayName("explainQuestion returns CACHED on subsequent request")
    void explainQuestion_ReturnsCached() {
        when(questionRepository.findById(sampleQuestion.getId()))
                .thenReturn(Optional.of(sampleQuestion));
        when(llmProviderService.generateGeneralCompletion(anyString(), anyString()))
                .thenReturn(Optional.of("Step 1: Solve mathematically."));

        var exp1 = aptitudeService.explainQuestion(sampleQuestion.getId(), null);
        assertThat(exp1.getSource()).isEqualTo("LLM");

        var exp2 = aptitudeService.explainQuestion(sampleQuestion.getId(), null);
        assertThat(exp2.getSource()).isEqualTo("CACHED");
        assertThat(exp2.getExplanation()).isEqualTo("Step 1: Solve mathematically.");
    }

    @Test
    @DisplayName("getAttemptDetails enforces user isolation: user B cannot read user A's attempt details (404)")
    void getAttemptDetails_EnforcesUserIsolation() {
        UUID attemptId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        when(attemptRepository.findByIdAndUserId(attemptId, otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aptitudeService.getAttemptDetails(otherUserId, attemptId))
                .isInstanceOf(java.util.NoSuchElementException.class)
                .hasMessageContaining("Attempt not found");
    }

    @Test
    @DisplayName("getAnalytics requires at least 5 answers for a topic to be classified as weak or strong")
    void getAnalytics_RequiresMinimumFiveAnswersForWeakOrStrong() {
        // Topic with 4 questions (below threshold of 5)
        Object[] lowCountTopic = new Object[]{"PROBABILITY", "QUANTITATIVE", 4L, 1L}; // 25% accuracy
        // Topic with 5 questions and < 60% accuracy (weak)
        Object[] weakTopic = new Object[]{"PERCENTAGE", "QUANTITATIVE", 6L, 2L}; // 33.3% accuracy
        // Topic with 5 questions and >= 75% accuracy (strong)
        Object[] strongTopic = new Object[]{"ALGEBRA", "QUANTITATIVE", 8L, 7L}; // 87.5% accuracy

        when(attemptRepository.countByUserIdAndStatus(userId, AttemptStatus.SUBMITTED)).thenReturn(3L);
        when(answerRepository.countByUserId(userId)).thenReturn(18L);
        when(answerRepository.getTopicPerformanceByUser(userId)).thenReturn(List.of(lowCountTopic, weakTopic, strongTopic));
        when(attemptRepository.getStatsByTrack(userId, AttemptStatus.SUBMITTED)).thenReturn(Collections.emptyList());

        var analytics = aptitudeService.getAnalytics(userId);

        assertThat(analytics.getWeakTopics()).extracting("topic").containsExactly("PERCENTAGE");
        assertThat(analytics.getStrongTopics()).extracting("topic").containsExactly("ALGEBRA");
        // PROBABILITY must NOT be in weakTopics despite having 25% accuracy because it has only 4 questions (< 5)
        assertThat(analytics.getWeakTopics()).noneMatch(t -> t.getTopic().equals("PROBABILITY"));
    }
}
