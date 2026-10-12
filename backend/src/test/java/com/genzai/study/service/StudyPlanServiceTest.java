package com.genzai.study.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genzai.aptitude.service.AptitudeService;
import com.genzai.model.UserSettings;
import com.genzai.service.UserActivityService;
import com.genzai.study.dto.*;
import com.genzai.study.model.*;
import com.genzai.study.repository.PomodoroSessionRepository;
import com.genzai.study.repository.StudyPlanRepository;
import com.genzai.study.repository.StudyTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudyPlanServiceTest {

    @Mock
    private StudyPlanRepository planRepository;

    @Mock
    private StudyTaskRepository taskRepository;

    @Mock
    private PomodoroSessionRepository pomodoroRepository;

    @Mock
    private UserActivityService userActivityService;

    @Mock
    private AptitudeService aptitudeService;

    @Mock
    private TopicExtractionService topicExtractionService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private StudyPlanService studyPlanService;

    private UUID userId;
    private UUID otherUserId;
    private StudyPlan samplePlan;
    private StudyTask sampleTask;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();

        samplePlan = StudyPlan.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .title("GATE CSE 2027")
                .status(PlanStatus.ACTIVE)
                .examDate(LocalDate.now().plusMonths(3))
                .hoursPerDay(2.0)
                .timezone("Asia/Kolkata")
                .build();

        sampleTask = StudyTask.builder()
                .id(UUID.randomUUID())
                .planId(samplePlan.getId())
                .userId(userId)
                .taskDate(LocalDate.now().plusDays(2))
                .topic("Operating Systems")
                .type(TaskType.STUDY)
                .plannedMinutes(60)
                .status(TaskStatus.PENDING)
                .build();
    }

    @Test
    @DisplayName("getTodayInUserTimezone evaluates according to user's stored timezone")
    void getTodayInUserTimezone_RespectsUserTimezone() {
        when(userActivityService.getOrCreateSettings(userId))
                .thenReturn(UserSettings.builder().userId(userId).timezone("Asia/Kolkata").build());

        LocalDate today = studyPlanService.getTodayInUserTimezone(userId);

        LocalDate expectedKolkataToday = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        assertThat(today).isEqualTo(expectedKolkataToday);
    }

    @Test
    @DisplayName("getTodayInUserTimezone handles 23:30 IST boundary vs UTC correctly")
    void getTodayInUserTimezone_BoundaryCondition() {
        when(userActivityService.getOrCreateSettings(userId))
                .thenReturn(UserSettings.builder().userId(userId).timezone("Asia/Kolkata").build());

        // At 18:00 UTC on 2026-06-01, IST is 23:30 on 2026-06-01 (same day)
        java.time.Instant instant2330 = java.time.Instant.parse("2026-06-01T18:00:00Z");
        LocalDate dateAt2330 = studyPlanService.getTodayInUserTimezone(instant2330, userId);
        assertThat(dateAt2330).isEqualTo(LocalDate.of(2026, 6, 1));

        // At 18:31 UTC on 2026-06-01, UTC is June 1st, but IST is 00:01 on June 2nd!
        java.time.Instant instant0001 = java.time.Instant.parse("2026-06-01T18:31:00Z");
        LocalDate dateAt0001 = studyPlanService.getTodayInUserTimezone(instant0001, userId);
        assertThat(dateAt0001).isEqualTo(LocalDate.of(2026, 6, 2));
    }

    @Test
    @DisplayName("getPlanById throws 404/NoSuchElementException when user B accesses user A's plan")
    void getPlanById_EnforcesUserIsolation() {
        when(planRepository.findByIdAndUserId(samplePlan.getId(), otherUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> studyPlanService.getPlanById(otherUserId, samplePlan.getId()))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Study plan not found");
    }

    @Test
    @DisplayName("updateTaskStatus throws 404 when user B accesses user A's task")
    void updateTaskStatus_EnforcesUserIsolation() {
        when(taskRepository.findByIdAndUserId(sampleTask.getId(), otherUserId))
                .thenReturn(Optional.empty());

        UpdateTaskStatusRequest req = new UpdateTaskStatusRequest(TaskStatus.DONE);

        assertThatThrownBy(() -> studyPlanService.updateTaskStatus(otherUserId, samplePlan.getId(), sampleTask.getId(), req))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Study task not found");
    }

    @Test
    @DisplayName("updateTaskStatus logs ONE activity log row idempotently on DONE")
    void updateTaskStatus_LogsActivityIdempotently() {
        when(taskRepository.findByIdAndUserId(sampleTask.getId(), userId))
                .thenReturn(Optional.of(sampleTask));
        when(taskRepository.save(any(StudyTask.class))).thenAnswer(i -> i.getArgument(0));

        UpdateTaskStatusRequest reqDone = new UpdateTaskStatusRequest(TaskStatus.DONE);
        studyPlanService.updateTaskStatus(userId, samplePlan.getId(), sampleTask.getId(), reqDone);

        verify(userActivityService, times(1)).logActivity(
                eq(userId), eq("STUDY_TASK_COMPLETED"), anyString(), anyString()
        );

        // Setting to DONE again should NOT trigger another activity log
        sampleTask.setStatus(TaskStatus.DONE);
        studyPlanService.updateTaskStatus(userId, samplePlan.getId(), sampleTask.getId(), reqDone);

        verify(userActivityService, times(1)).logActivity(
                eq(userId), eq("STUDY_TASK_COMPLETED"), anyString(), anyString()
        );
    }

    @Test
    @DisplayName("catchUp redistributes overdue pending tasks into remaining study days")
    void catchUp_RedistributesOverdueTasks() {
        when(planRepository.findByIdAndUserId(samplePlan.getId(), userId))
                .thenReturn(Optional.of(samplePlan));
        when(userActivityService.getOrCreateSettings(userId))
                .thenReturn(UserSettings.builder().userId(userId).timezone("Asia/Kolkata").build());

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        StudyTask overdueTask = StudyTask.builder()
                .id(UUID.randomUUID())
                .planId(samplePlan.getId())
                .userId(userId)
                .taskDate(today.minusDays(3))
                .topic("Computer Networks")
                .status(TaskStatus.PENDING)
                .build();

        when(taskRepository.findByPlanIdAndUserIdAndTaskDateLessThanAndStatus(
                eq(samplePlan.getId()), eq(userId), eq(today), eq(TaskStatus.PENDING)
        )).thenReturn(List.of(overdueTask));

        CatchUpResponse response = studyPlanService.catchUp(userId, samplePlan.getId());

        assertThat(response.getRedistributedTasksCount()).isEqualTo(1);
        assertThat(overdueTask.getTaskDate()).isAfterOrEqualTo(today);
        verify(taskRepository).saveAll(List.of(overdueTask));
    }

    @Test
    @DisplayName("completePomodoro counts session only when elapsed time is >= 90% of planned duration")
    void completePomodoro_ValidatesNinetyPercentDuration() {
        UUID sessionId = UUID.randomUUID();
        // Session started 1000s ago with 1500s planned: 1000/1500 = 66.7% (< 90%)
        PomodoroSession underDurationSession = PomodoroSession.builder()
                .id(sessionId)
                .userId(userId)
                .plannedSeconds(1500)
                .startedAt(OffsetDateTime.now().minusSeconds(1000))
                .counted(false)
                .build();

        when(pomodoroRepository.findByIdAndUserId(sessionId, userId))
                .thenReturn(Optional.of(underDurationSession));
        when(pomodoroRepository.save(any(PomodoroSession.class))).thenAnswer(i -> i.getArgument(0));

        PomodoroSessionDto result = studyPlanService.completePomodoro(userId, sessionId);

        assertThat(result.getCounted()).isFalse();
        verify(userActivityService, never()).logActivity(any(), eq("POMODORO_COMPLETED"), any(), any());

        // Session started 1400s ago with 1500s planned: 1400/1500 = 93.3% (>= 90%)
        PomodoroSession validSession = PomodoroSession.builder()
                .id(sessionId)
                .userId(userId)
                .plannedSeconds(1500)
                .startedAt(OffsetDateTime.now().minusSeconds(1400))
                .counted(false)
                .build();

        when(pomodoroRepository.findByIdAndUserId(sessionId, userId))
                .thenReturn(Optional.of(validSession));

        PomodoroSessionDto validResult = studyPlanService.completePomodoro(userId, sessionId);

        assertThat(validResult.getCounted()).isTrue();
        verify(userActivityService, times(1)).logActivity(
                eq(userId), eq("POMODORO_COMPLETED"), anyString(), anyString()
        );
    }
}
