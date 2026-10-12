package com.genzai.study.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genzai.aptitude.dto.AptitudeAnalyticsDto;
import com.genzai.aptitude.dto.TopicStatDto;
import com.genzai.aptitude.service.AptitudeService;
import com.genzai.service.UserActivityService;
import com.genzai.study.dto.*;
import com.genzai.study.model.*;
import com.genzai.study.repository.PomodoroSessionRepository;
import com.genzai.study.repository.StudyPlanRepository;
import com.genzai.study.repository.StudyTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StudyPlanService {

    private final StudyPlanRepository planRepository;
    private final StudyTaskRepository taskRepository;
    private final PomodoroSessionRepository pomodoroRepository;
    private final UserActivityService userActivityService;
    private final AptitudeService aptitudeService;
    private final TopicExtractionService topicExtractionService;
    private final ObjectMapper objectMapper;

    public String getUserTimezone(UUID userId) {
        if (userId == null) return "Asia/Kolkata";
        try {
            return userActivityService.getOrCreateSettings(userId).getTimezone();
        } catch (Exception e) {
            return "Asia/Kolkata";
        }
    }

    public LocalDate getTodayInUserTimezone(UUID userId) {
        return getTodayInUserTimezone(java.time.Instant.now(), userId);
    }

    public LocalDate getTodayInUserTimezone(java.time.Instant instant, UUID userId) {
        String tz = getUserTimezone(userId);
        try {
            return instant.atZone(ZoneId.of(tz)).toLocalDate();
        } catch (Exception e) {
            return instant.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
        }
    }

    @Transactional(readOnly = true)
    public StudyScheduler.ScheduleResult previewPlan(UUID userId, PreviewPlanRequest request) {
        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : getTodayInUserTimezone(userId);

        List<StudyScheduler.TopicWeight> topics = request.getTopics();
        if (topics == null || topics.isEmpty()) {
            topics = topicExtractionService.extractTopics(request.getSyllabusText());
        }

        return StudyScheduler.generateSchedule(
                topics,
                startDate,
                request.getExamDate(),
                request.getHoursPerDay() != null ? request.getHoursPerDay() : 2.0,
                request.getRestWeekdays() != null ? request.getRestWeekdays() : Collections.emptySet(),
                request.getBufferDays() != null ? request.getBufferDays() : 2
        );
    }

    @Transactional
    public StudyPlanDto createPlan(UUID userId, CreatePlanRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required to create a study plan");
        }

        LocalDate startDate = request.getStartDate() != null ? request.getStartDate() : getTodayInUserTimezone(userId);
        String tz = getUserTimezone(userId);

        List<StudyScheduler.TopicWeight> topics = request.getTopics();
        if (topics == null || topics.isEmpty()) {
            topics = topicExtractionService.extractTopics(request.getSyllabusText());
        }

        StudyScheduler.ScheduleResult scheduleResult = StudyScheduler.generateSchedule(
                topics,
                startDate,
                request.getExamDate(),
                request.getHoursPerDay() != null ? request.getHoursPerDay() : 2.0,
                request.getRestWeekdays() != null ? request.getRestWeekdays() : Collections.emptySet(),
                request.getBufferDays() != null ? request.getBufferDays() : 2
        );

        StudyPlan plan = StudyPlan.builder()
                .userId(userId)
                .title(request.getTitle())
                .status(PlanStatus.ACTIVE)
                .examDate(request.getExamDate())
                .hoursPerDay(request.getHoursPerDay() != null ? request.getHoursPerDay() : 2.0)
                .timezone(tz)
                .build();

        StudyPlan savedPlan = planRepository.save(plan);

        List<StudyTask> tasksToSave = new ArrayList<>();
        for (StudyScheduler.ScheduledTask st : scheduleResult.getTasks()) {
            tasksToSave.add(StudyTask.builder()
                    .planId(savedPlan.getId())
                    .userId(userId)
                    .taskDate(st.getDate())
                    .topic(st.getTopic())
                    .type(st.getType())
                    .plannedMinutes(st.getPlannedMinutes())
                    .status(TaskStatus.PENDING)
                    .build());
        }

        List<StudyTask> savedTasks = taskRepository.saveAll(tasksToSave);

        // Log plan creation activity
        try {
            userActivityService.logActivity(
                    userId,
                    "STUDY_PLAN_CREATED",
                    "Created study plan: " + savedPlan.getTitle() + " (" + savedTasks.size() + " tasks)",
                    objectMapper.writeValueAsString(Map.of("planId", savedPlan.getId().toString(), "examDate", savedPlan.getExamDate().toString()))
            );
        } catch (Exception ignored) {}

        return toPlanDto(savedPlan, savedTasks, getTodayInUserTimezone(userId));
    }

    @Transactional(readOnly = true)
    public List<StudyPlanDto> getPlans(UUID userId) {
        if (userId == null) return Collections.emptyList();
        LocalDate today = getTodayInUserTimezone(userId);
        List<StudyPlan> plans = planRepository.findByUserIdOrderByCreatedAtDesc(userId);

        return plans.stream().map(p -> {
            List<StudyTask> tasks = taskRepository.findByPlanIdAndUserIdOrderByTaskDateAsc(p.getId(), userId);
            return toPlanDto(p, tasks, today);
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public StudyPlanDto getPlanById(UUID userId, UUID planId) {
        if (userId == null) throw new IllegalArgumentException("User ID required");
        StudyPlan plan = planRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new NoSuchElementException("Study plan not found: " + planId));

        List<StudyTask> tasks = taskRepository.findByPlanIdAndUserIdOrderByTaskDateAsc(planId, userId);
        return toPlanDto(plan, tasks, getTodayInUserTimezone(userId));
    }

    @Transactional
    public StudyTaskDto updateTaskStatus(UUID userId, UUID planId, UUID taskId, UpdateTaskStatusRequest request) {
        if (userId == null) throw new IllegalArgumentException("User ID required");

        StudyTask task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new NoSuchElementException("Study task not found: " + taskId));

        if (!task.getPlanId().equals(planId)) {
            throw new NoSuchElementException("Task does not belong to specified plan: " + planId);
        }

        TaskStatus previousStatus = task.getStatus();
        TaskStatus newStatus = request.getStatus();
        task.setStatus(newStatus);

        if (newStatus == TaskStatus.DONE && previousStatus != TaskStatus.DONE) {
            task.setCompletedAt(OffsetDateTime.now());
            // Log ONE activity log row idempotently
            try {
                userActivityService.logActivity(
                        userId,
                        "STUDY_TASK_COMPLETED",
                        "Completed task: " + task.getTopic() + " (" + task.getType() + ")",
                        objectMapper.writeValueAsString(Map.of("taskId", task.getId().toString(), "planId", planId.toString()))
                );
            } catch (Exception ignored) {}
        } else if (newStatus != TaskStatus.DONE) {
            task.setCompletedAt(null);
        }

        StudyTask updated = taskRepository.save(task);
        return toTaskDto(updated);
    }

    @Transactional
    public CatchUpResponse catchUp(UUID userId, UUID planId) {
        if (userId == null) throw new IllegalArgumentException("User ID required");
        StudyPlan plan = planRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new NoSuchElementException("Study plan not found: " + planId));

        LocalDate today = getTodayInUserTimezone(userId);
        List<StudyTask> overduePending = taskRepository.findByPlanIdAndUserIdAndTaskDateLessThanAndStatus(
                planId, userId, today, TaskStatus.PENDING
        );

        if (overduePending.isEmpty()) {
            return CatchUpResponse.builder()
                    .redistributedTasksCount(0)
                    .summary("All past tasks are completed or up to date! Nothing to redistribute.")
                    .warnings(Collections.emptyList())
                    .build();
        }

        // Available dates to redistribute into: from today until 2 days before examDate (before buffer window)
        LocalDate bufferStart = plan.getExamDate().minusDays(2);
        List<LocalDate> futureStudyDates = new ArrayList<>();
        LocalDate cur = today;
        while (cur.isBefore(bufferStart)) {
            futureStudyDates.add(cur);
            cur = cur.plusDays(1);
        }

        List<String> warnings = new ArrayList<>();
        if (futureStudyDates.isEmpty()) {
            warnings.add("Exam buffer window is already active. Overdue tasks rescheduled to today.");
            futureStudyDates.add(today);
        }

        int dateIdx = 0;
        for (StudyTask task : overduePending) {
            LocalDate targetDate = futureStudyDates.get(dateIdx % futureStudyDates.size());
            task.setTaskDate(targetDate);
            dateIdx++;
        }

        taskRepository.saveAll(overduePending);

        String summary = String.format("Successfully redistributed %d overdue tasks across remaining study days before the exam.", overduePending.size());
        return CatchUpResponse.builder()
                .redistributedTasksCount(overduePending.size())
                .summary(summary)
                .warnings(warnings)
                .build();
    }

    @Transactional
    public int injectWeakTopicRevisions(UUID userId, UUID planId) {
        if (userId == null) throw new IllegalArgumentException("User ID required");
        StudyPlan plan = planRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new NoSuchElementException("Study plan not found: " + planId));

        AptitudeAnalyticsDto analytics = aptitudeService.getAnalytics(userId);
        List<TopicStatDto> weakTopics = analytics.getWeakTopics(); // already filtered for <60% accuracy and >=5 answers

        if (weakTopics == null || weakTopics.isEmpty()) {
            return 0;
        }

        LocalDate today = getTodayInUserTimezone(userId);
        LocalDate weekStart = today.minusDays(7);
        LocalDate weekEnd = today.plusDays(7);
        List<StudyTask> newRevisionTasks = new ArrayList<>();

        for (TopicStatDto wt : weakTopics) {
            String topicName = "Aptitude Revision: " + wt.getTopic();
            boolean alreadyScheduled = taskRepository.existsAptitudeRevisionThisWeek(
                    planId, userId, topicName, TaskType.APTITUDE_REVISION, weekStart, weekEnd
            );

            if (!alreadyScheduled) {
                newRevisionTasks.add(StudyTask.builder()
                        .planId(planId)
                        .userId(userId)
                        .taskDate(today)
                        .topic(topicName)
                        .type(TaskType.APTITUDE_REVISION)
                        .plannedMinutes(30)
                        .status(TaskStatus.PENDING)
                        .build());
            }
        }

        if (!newRevisionTasks.isEmpty()) {
            taskRepository.saveAll(newRevisionTasks);
        }

        return newRevisionTasks.size();
    }

    @Transactional
    public PomodoroSessionDto startPomodoro(UUID userId, PomodoroStartRequest request) {
        if (userId == null) throw new IllegalArgumentException("User ID required");

        PomodoroSession session = PomodoroSession.builder()
                .userId(userId)
                .taskId(request.getTaskId())
                .plannedSeconds(request.getPlannedSeconds() != null ? request.getPlannedSeconds() : 1500)
                .startedAt(OffsetDateTime.now())
                .counted(false)
                .build();

        PomodoroSession saved = pomodoroRepository.save(session);
        return toPomodoroDto(saved);
    }

    @Transactional
    public PomodoroSessionDto completePomodoro(UUID userId, UUID sessionId) {
        if (userId == null) throw new IllegalArgumentException("User ID required");

        PomodoroSession session = pomodoroRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new NoSuchElementException("Pomodoro session not found: " + sessionId));

        OffsetDateTime now = OffsetDateTime.now();
        session.setCompletedAt(now);

        long elapsedSeconds = Duration.between(session.getStartedAt(), now).getSeconds();
        boolean counted = elapsedSeconds >= (long) (session.getPlannedSeconds() * 0.90);
        session.setCounted(counted);

        PomodoroSession updated = pomodoroRepository.save(session);

        // If counted, write ONE activity_log row idempotently
        if (counted) {
            try {
                userActivityService.logActivity(
                        userId,
                        "POMODORO_COMPLETED",
                        "Completed 25-minute Pomodoro focus session (" + elapsedSeconds + "s)",
                        objectMapper.writeValueAsString(Map.of("sessionId", session.getId().toString(), "elapsedSeconds", elapsedSeconds))
                );
            } catch (Exception ignored) {}
        }

        return toPomodoroDto(updated);
    }

    private StudyPlanDto toPlanDto(StudyPlan plan, List<StudyTask> tasks, LocalDate today) {
        int total = tasks.size();
        int completed = (int) tasks.stream().filter(t -> t.getStatus() == TaskStatus.DONE).count();
        double progress = total > 0 ? (completed * 100.0 / total) : 0.0;
        long daysToExam = ChronoUnit.DAYS.between(today, plan.getExamDate());

        Map<LocalDate, List<StudyTaskDto>> tasksByDate = tasks.stream()
                .map(this::toTaskDto)
                .collect(Collectors.groupingBy(StudyTaskDto::getTaskDate, TreeMap::new, Collectors.toList()));

        return StudyPlanDto.builder()
                .id(plan.getId())
                .title(plan.getTitle())
                .status(plan.getStatus())
                .examDate(plan.getExamDate())
                .hoursPerDay(plan.getHoursPerDay())
                .timezone(plan.getTimezone())
                .daysToExam(daysToExam)
                .progressPercentage(Math.round(progress * 10.0) / 10.0)
                .totalTasks(total)
                .completedTasks(completed)
                .tasksByDate(tasksByDate)
                .createdAt(plan.getCreatedAt())
                .build();
    }

    private StudyTaskDto toTaskDto(StudyTask t) {
        return StudyTaskDto.builder()
                .id(t.getId())
                .planId(t.getPlanId())
                .taskDate(t.getTaskDate())
                .topic(t.getTopic())
                .type(t.getType())
                .plannedMinutes(t.getPlannedMinutes())
                .status(t.getStatus())
                .completedAt(t.getCompletedAt())
                .sourceTaskId(t.getSourceTaskId())
                .build();
    }

    private PomodoroSessionDto toPomodoroDto(PomodoroSession s) {
        return PomodoroSessionDto.builder()
                .id(s.getId())
                .taskId(s.getTaskId())
                .plannedSeconds(s.getPlannedSeconds())
                .startedAt(s.getStartedAt())
                .completedAt(s.getCompletedAt())
                .counted(s.getCounted())
                .build();
    }
}
