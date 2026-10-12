package com.genzai.aptitude.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genzai.aptitude.dto.*;
import com.genzai.aptitude.entity.*;
import com.genzai.aptitude.repository.AptitudeAttemptAnswerRepository;
import com.genzai.aptitude.repository.AptitudeAttemptRepository;
import com.genzai.aptitude.repository.AptitudeQuestionRepository;
import com.genzai.llm.LlmProviderService;
import com.genzai.service.UserActivityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AptitudeService {

    private final AptitudeQuestionRepository questionRepository;
    private final AptitudeAttemptRepository attemptRepository;
    private final AptitudeAttemptAnswerRepository answerRepository;
    private final UserActivityService activityService;
    private final LlmProviderService llmProviderService;
    private final ObjectMapper objectMapper;

    private static final Map<AptitudeTrack, String> TRACK_TITLES = Map.of(
            AptitudeTrack.QUANTITATIVE, "Quantitative Aptitude",
            AptitudeTrack.LOGICAL, "Logical Reasoning",
            AptitudeTrack.VERBAL, "Verbal Ability",
            AptitudeTrack.CORE_CS, "Core Computer Science"
    );

    private static final Map<AptitudeTrack, String> TRACK_DESCRIPTIONS = Map.of(
            AptitudeTrack.QUANTITATIVE, "Arithmetic, percentages, ratios, algebra, and problem-solving benchmarks.",
            AptitudeTrack.LOGICAL, "Deductive logic, syllogisms, series, patterns, and analytical reasoning.",
            AptitudeTrack.VERBAL, "Vocabulary, reading comprehension, grammar corrections, and language aptitude.",
            AptitudeTrack.CORE_CS, "Operating systems, computer networks, database systems, and data structures."
    );

    @Transactional(readOnly = true)
    public List<TrackSummaryDto> getTracks() {
        List<TrackSummaryDto> list = new ArrayList<>();
        for (AptitudeTrack track : AptitudeTrack.values()) {
            long count = questionRepository.countByTrack(track);
            List<String> topics = questionRepository.findDistinctTopicsByTrack(track);
            list.add(TrackSummaryDto.builder()
                    .track(track)
                    .title(TRACK_TITLES.getOrDefault(track, track.name()))
                    .description(TRACK_DESCRIPTIONS.getOrDefault(track, ""))
                    .totalQuestions(count)
                    .topics(topics)
                    .build());
        }
        return list;
    }

    @Transactional
    public AptitudeAttemptDetailDto startAttempt(UUID userId, CreateAttemptRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required to start an attempt");
        }

        int requestedCount = request.getQuestionCount() != null ? request.getQuestionCount() : 10;
        List<AptitudeQuestion> questions;

        if (request.getTopic() != null && !request.getTopic().isBlank()) {
            questions = questionRepository.findRandomByTrackAndTopic(
                    request.getTrack().name(), request.getTopic().trim(), requestedCount
            );
        } else {
            questions = questionRepository.findRandomByTrack(request.getTrack().name(), requestedCount);
        }

        if (questions.isEmpty()) {
            throw new IllegalStateException("No questions available for track " + request.getTrack());
        }

        AttemptMode mode = request.getMode() != null ? request.getMode() : AttemptMode.PRACTICE;
        // Server strictly decides time limit: mock test is 90 seconds per question (capped at 60 minutes / 3600s), practice is 0
        int calculatedTimeLimit = 0;
        if (mode == AttemptMode.MOCK_TEST) {
            calculatedTimeLimit = Math.min(questions.size() * 90, 3600);
        }

        AptitudeAttempt attempt = AptitudeAttempt.builder()
                .userId(userId)
                .track(request.getTrack())
                .mode(mode)
                .totalQuestions(questions.size())
                .correctCount(0)
                .scorePct(0.0)
                .timeLimitSeconds(calculatedTimeLimit)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(OffsetDateTime.now())
                .build();

        AptitudeAttempt savedAttempt = attemptRepository.save(attempt);

        // Sanitize questions: strip correctAnswer and rationale completely!
        List<AptitudeQuestionDto> sanitizedQuestions = questions.stream()
                .map(this::toSanitizedQuestionDto)
                .collect(Collectors.toList());

        return AptitudeAttemptDetailDto.builder()
                .id(savedAttempt.getId())
                .track(savedAttempt.getTrack())
                .mode(savedAttempt.getMode())
                .totalQuestions(savedAttempt.getTotalQuestions())
                .timeLimitSeconds(savedAttempt.getTimeLimitSeconds())
                .status(savedAttempt.getStatus())
                .startedAt(savedAttempt.getStartedAt())
                .questions(sanitizedQuestions)
                .build();
    }

    @Transactional
    public AnswerFeedbackDto recordAnswer(UUID userId, UUID attemptId, SubmitAnswerRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        AptitudeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new NoSuchElementException("Attempt not found: " + attemptId));

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("Attempt is already " + attempt.getStatus() + ". Cannot submit further answers.");
        }

        // Server-side timing enforcement: grace period of 5 seconds
        if (attempt.getMode() == AttemptMode.MOCK_TEST && attempt.getTimeLimitSeconds() > 0) {
            long elapsedSeconds = Duration.between(attempt.getStartedAt(), OffsetDateTime.now()).getSeconds();
            if (elapsedSeconds > (attempt.getTimeLimitSeconds() + 5)) {
                throw new IllegalStateException("Time limit expired (" + elapsedSeconds + "s > " + attempt.getTimeLimitSeconds() + "s limit). Submissions are closed.");
            }
        }

        AptitudeQuestion question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new NoSuchElementException("Question not found: " + request.getQuestionId()));

        String selectedOption = request.getSelectedOption() != null ? request.getSelectedOption().trim().toUpperCase() : null;
        boolean isCorrect = selectedOption != null && selectedOption.equalsIgnoreCase(question.getCorrectAnswer());

        AptitudeAttemptAnswer answer = answerRepository.findByAttemptIdAndQuestionId(attemptId, question.getId())
                .orElseGet(() -> AptitudeAttemptAnswer.builder()
                        .attemptId(attemptId)
                        .userId(userId)
                        .questionId(question.getId())
                        .build());

        answer.setSelectedOption(selectedOption);
        answer.setIsCorrect(isCorrect);
        answer.setTimeSpentSeconds(request.getTimeSpentSeconds() != null ? request.getTimeSpentSeconds() : 0);
        answerRepository.save(answer);

        // In practice mode, provide instant learning feedback
        if (attempt.getMode() == AttemptMode.PRACTICE) {
            return AnswerFeedbackDto.builder()
                    .questionId(question.getId())
                    .selectedOption(selectedOption)
                    .isCorrect(isCorrect)
                    .correctAnswer(question.getCorrectAnswer())
                    .rationale(question.getRationale())
                    .submitted(true)
                    .build();
        }

        // In mock test mode, acknowledge submission without leaking correct answer or rationale
        return AnswerFeedbackDto.builder()
                .questionId(question.getId())
                .selectedOption(selectedOption)
                .isCorrect(null)
                .correctAnswer(null)
                .rationale(null)
                .submitted(true)
                .build();
    }

    @Transactional
    public AptitudeAttemptSummaryDto submitAttempt(UUID userId, UUID attemptId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        AptitudeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new NoSuchElementException("Attempt not found: " + attemptId));

        List<AptitudeAttemptAnswer> answers = answerRepository.findByAttemptId(attemptId);
        int correct = 0;
        int totalTimeSpent = 0;

        for (AptitudeAttemptAnswer ans : answers) {
            if (Boolean.TRUE.equals(ans.getIsCorrect())) {
                correct++;
            }
            totalTimeSpent += (ans.getTimeSpentSeconds() != null ? ans.getTimeSpentSeconds() : 0);
        }

        int totalQ = attempt.getTotalQuestions() > 0 ? attempt.getTotalQuestions() : answers.size();
        double scorePct = totalQ > 0 ? (correct * 100.0 / totalQ) : 0.0;

        attempt.setCorrectCount(correct);
        attempt.setScorePct(Math.round(scorePct * 10.0) / 10.0);
        attempt.setStatus(AttemptStatus.SUBMITTED);
        attempt.setCompletedAt(OffsetDateTime.now());
        attemptRepository.save(attempt);

        // Record activity log for dashboard and streak tracking
        try {
            String metadataJson = objectMapper.writeValueAsString(Map.of(
                    "attemptId", attempt.getId().toString(),
                    "track", attempt.getTrack().name(),
                    "mode", attempt.getMode().name(),
                    "correctCount", correct,
                    "totalQuestions", totalQ,
                    "scorePct", attempt.getScorePct()
            ));
            activityService.logActivity(
                    userId,
                    "APTITUDE_PRACTICE",
                    "Completed " + attempt.getTrack() + " " + attempt.getMode() + " test: " + correct + "/" + totalQ + " (" + attempt.getScorePct() + "%)",
                    metadataJson
            );
        } catch (Exception e) {
            log.error("Failed to serialize aptitude attempt metadata for activity log: {}", e.getMessage());
        }

        return buildSummaryDto(attempt, answers, totalTimeSpent);
    }

    @Transactional(readOnly = true)
    public AptitudeAttemptSummaryDto getAttemptDetails(UUID userId, UUID attemptId) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }

        AptitudeAttempt attempt = attemptRepository.findByIdAndUserId(attemptId, userId)
                .orElseThrow(() -> new NoSuchElementException("Attempt not found: " + attemptId));

        List<AptitudeAttemptAnswer> answers = answerRepository.findByAttemptId(attemptId);
        int totalTimeSpent = answers.stream()
                .mapToInt(a -> a.getTimeSpentSeconds() != null ? a.getTimeSpentSeconds() : 0)
                .sum();

        return buildSummaryDto(attempt, answers, totalTimeSpent);
    }

    @Transactional(readOnly = true)
    public List<AptitudeAttempt> getHistory(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return attemptRepository.findByUserIdOrderByStartedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public AptitudeAnalyticsDto getAnalytics(UUID userId) {
        if (userId == null) {
            return AptitudeAnalyticsDto.builder()
                    .totalAttempts(0)
                    .totalPracticed(0)
                    .overallAccuracyPct(0.0)
                    .trackAccuracy(Collections.emptyMap())
                    .weakTopics(Collections.emptyList())
                    .strongTopics(Collections.emptyList())
                    .allTopics(Collections.emptyList())
                    .build();
        }

        long totalAttempts = attemptRepository.countByUserIdAndStatus(userId, AttemptStatus.SUBMITTED);
        long totalPracticed = answerRepository.countByUserId(userId);

        List<Object[]> rawTopicStats = answerRepository.getTopicPerformanceByUser(userId);
        List<TopicStatDto> allTopics = new ArrayList<>();
        long totalCorrect = 0;

        for (Object[] row : rawTopicStats) {
            String topic = (String) row[0];
            Object trackObj = row[1];
            String track = trackObj != null ? trackObj.toString() : "";
            long total = ((Number) row[2]).longValue();
            long correct = ((Number) row[3]).longValue();
            totalCorrect += correct;

            double accuracy = total > 0 ? (correct * 100.0 / total) : 0.0;
            allTopics.add(TopicStatDto.builder()
                    .topic(topic)
                    .track(track)
                    .totalQuestions(total)
                    .correctAnswers(correct)
                    .accuracyPct(Math.round(accuracy * 10.0) / 10.0)
                    .build());
        }

        double overallAccuracy = totalPracticed > 0 ? (totalCorrect * 100.0 / totalPracticed) : 0.0;

        // Group by track
        Map<String, Double> trackAccuracy = new HashMap<>();
        List<Object[]> trackStats = attemptRepository.getStatsByTrack(userId, AttemptStatus.SUBMITTED);
        for (Object[] row : trackStats) {
            String trackName = row[0].toString();
            Double avgScore = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
            trackAccuracy.put(trackName, Math.round(avgScore * 10.0) / 10.0);
        }

        List<TopicStatDto> weakTopics = allTopics.stream()
                .filter(t -> t.getTotalQuestions() >= 5 && t.getAccuracyPct() < 60.0)
                .sorted(Comparator.comparingDouble(TopicStatDto::getAccuracyPct))
                .collect(Collectors.toList());

        List<TopicStatDto> strongTopics = allTopics.stream()
                .filter(t -> t.getTotalQuestions() >= 5 && t.getAccuracyPct() >= 75.0)
                .sorted((a, b) -> Double.compare(b.getAccuracyPct(), a.getAccuracyPct()))
                .collect(Collectors.toList());

        return AptitudeAnalyticsDto.builder()
                .totalAttempts(totalAttempts)
                .totalPracticed(totalPracticed)
                .overallAccuracyPct(Math.round(overallAccuracy * 10.0) / 10.0)
                .trackAccuracy(trackAccuracy)
                .weakTopics(weakTopics)
                .strongTopics(strongTopics)
                .allTopics(allTopics)
                .build();
    }

    private final java.util.concurrent.ConcurrentHashMap<UUID, String> explanationCache = new java.util.concurrent.ConcurrentHashMap<>();

    public ExplainQuestionResponse explainQuestion(UUID questionId, String userQuery) {
        AptitudeQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new NoSuchElementException("Question not found: " + questionId));

        // 1. Cached explanation
        if ((userQuery == null || userQuery.isBlank()) && explanationCache.containsKey(questionId)) {
            return ExplainQuestionResponse.builder()
                    .questionId(question.getId())
                    .explanation(explanationCache.get(questionId))
                    .source("CACHED")
                    .modelUsed("Cache")
                    .isFallback(false)
                    .build();
        }

        List<String> options = parseOptions(question.getOptionsJson());
        String formattedOptions = String.join(", ", options);

        // 2. Real General LLM through LlmProviderService (MiniGPT is strictly bypassed)
        String prompt = String.format(
                "Question: %s\nOptions: %s\nCorrect Answer Key: %s\nStored Solution: %s%s\n" +
                "Provide a clear, step-by-step mathematical/logical explanation. Your final answer must stay strictly consistent with the correct key %s.",
                question.getQuestion(), formattedOptions, question.getCorrectAnswer(),
                question.getRationale() != null ? question.getRationale() : "N/A",
                userQuery != null && !userQuery.isBlank() ? "\nUser follow-up: " + userQuery : "",
                question.getCorrectAnswer()
        );
        String system = "You are an expert tutor in aptitude, mathematics, logic, and computer science. Give an encouraging, clear, and stepwise explanation consistent with the correct answer key.";

        Optional<String> generalLlmResponse = llmProviderService.generateGeneralCompletion(prompt, system);
        if (generalLlmResponse.isPresent() && !generalLlmResponse.get().isBlank()) {
            String exp = generalLlmResponse.get();
            if (userQuery == null || userQuery.isBlank()) {
                explanationCache.put(questionId, exp);
            }
            return ExplainQuestionResponse.builder()
                    .questionId(question.getId())
                    .explanation(exp)
                    .source("LLM")
                    .modelUsed("General LLM")
                    .isFallback(false)
                    .build();
        }

        // 3. Stored rationale fallback
        String fallbackExplanation = (question.getRationale() != null && !question.getRationale().isBlank())
                ? question.getRationale()
                : "Correct answer key: " + question.getCorrectAnswer() + ". (Offline rationale)";

        return ExplainQuestionResponse.builder()
                .questionId(question.getId())
                .explanation(fallbackExplanation)
                .source("STORED_RATIONALE")
                .modelUsed("Stored Rationale")
                .isFallback(true)
                .build();
    }

    private AptitudeQuestionDto toSanitizedQuestionDto(AptitudeQuestion q) {
        return AptitudeQuestionDto.builder()
                .id(q.getId())
                .track(q.getTrack())
                .topic(q.getTopic())
                .difficulty(q.getDifficulty())
                .question(q.getQuestion())
                .options(parseOptions(q.getOptionsJson()))
                .build();
    }

    private AptitudeAttemptSummaryDto buildSummaryDto(AptitudeAttempt attempt, List<AptitudeAttemptAnswer> answers, int totalTimeSpent) {
        Map<UUID, AptitudeAttemptAnswer> answerMap = answers.stream()
                .collect(Collectors.toMap(AptitudeAttemptAnswer::getQuestionId, a -> a, (a1, a2) -> a1));

        List<AptitudeQuestionReviewDto> reviewList = new ArrayList<>();
        List<UUID> qIds = new ArrayList<>(answerMap.keySet());
        if (!qIds.isEmpty()) {
            List<AptitudeQuestion> questions = questionRepository.findAllById(qIds);
            for (AptitudeQuestion q : questions) {
                AptitudeAttemptAnswer ans = answerMap.get(q.getId());
                reviewList.add(AptitudeQuestionReviewDto.builder()
                        .questionId(q.getId())
                        .track(q.getTrack())
                        .topic(q.getTopic())
                        .difficulty(q.getDifficulty())
                        .question(q.getQuestion())
                        .options(parseOptions(q.getOptionsJson()))
                        .selectedOption(ans != null ? ans.getSelectedOption() : null)
                        .correctAnswer(q.getCorrectAnswer())
                        .isCorrect(ans != null ? ans.getIsCorrect() : false)
                        .timeSpentSeconds(ans != null ? ans.getTimeSpentSeconds() : 0)
                        .rationale(q.getRationale())
                        .build());
            }
        }

        return AptitudeAttemptSummaryDto.builder()
                .id(attempt.getId())
                .track(attempt.getTrack())
                .mode(attempt.getMode())
                .totalQuestions(attempt.getTotalQuestions())
                .correctCount(attempt.getCorrectCount())
                .scorePct(attempt.getScorePct())
                .timeLimitSeconds(attempt.getTimeLimitSeconds())
                .totalTimeSpentSeconds(totalTimeSpent)
                .status(attempt.getStatus())
                .startedAt(attempt.getStartedAt())
                .completedAt(attempt.getCompletedAt())
                .questions(reviewList)
                .build();
    }

    private List<String> parseOptions(String optionsJson) {
        if (optionsJson == null || optionsJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(optionsJson, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.error("Failed to parse optionsJson: {}", optionsJson);
            return Collections.emptyList();
        }
    }
}
