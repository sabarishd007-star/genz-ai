package com.genzai.service;

import com.genzai.dto.DashboardSummaryDto;
import com.genzai.model.UserSettings;
import com.genzai.rag.repository.RagDocumentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private RagDocumentRepository ragDocumentRepository;

    @Mock
    private UserActivityService userActivityService;

    @Mock
    private com.genzai.aptitude.repository.AptitudeAttemptRepository aptitudeAttemptRepository;

    @Mock
    private com.genzai.study.repository.StudyPlanRepository studyPlanRepository;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    @DisplayName("getDashboardSummary isolates data between User A and User B")
    void getDashboardSummary_UserIsolation() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();

        when(ragDocumentRepository.countByUserId(userA.toString())).thenReturn(7L);
        when(ragDocumentRepository.countByUserId(userB.toString())).thenReturn(2L);

        when(userActivityService.calculateActiveStreak(userA)).thenReturn(5);
        when(userActivityService.calculateActiveStreak(userB)).thenReturn(1);

        when(userActivityService.getOrCreateSettings(userA))
                .thenReturn(UserSettings.builder().userId(userA).timezone("Asia/Kolkata").build());
        when(userActivityService.getOrCreateSettings(userB))
                .thenReturn(UserSettings.builder().userId(userB).timezone("America/New_York").build());

        DashboardSummaryDto summaryA = dashboardService.getDashboardSummary(userA);
        DashboardSummaryDto summaryB = dashboardService.getDashboardSummary(userB);

        assertThat(summaryA.getDocumentsCount()).isEqualTo(7L);
        assertThat(summaryA.getActiveStreakDays()).isEqualTo(5);
        assertThat(summaryA.getUserTimezone()).isEqualTo("Asia/Kolkata");

        assertThat(summaryB.getDocumentsCount()).isEqualTo(2L);
        assertThat(summaryB.getActiveStreakDays()).isEqualTo(1);
        assertThat(summaryB.getUserTimezone()).isEqualTo("America/New_York");

        verify(userActivityService).logActivity(eq(userA), eq("DASHBOARD_VIEW"), any(), isNull());
        verify(userActivityService).logActivity(eq(userB), eq("DASHBOARD_VIEW"), any(), isNull());
    }

    @Test
    @DisplayName("getDashboardSummary returns safe default summary when userId is null")
    void getDashboardSummary_NullUser_ReturnsSafeDefaults() {
        DashboardSummaryDto summary = dashboardService.getDashboardSummary(null);

        assertThat(summary.getDocumentsCount()).isEqualTo(0L);
        assertThat(summary.getAptitudeScore()).isEqualTo("--");
        assertThat(summary.getActiveStreakDays()).isEqualTo(1);
        assertThat(summary.getUserTimezone()).isEqualTo("Asia/Kolkata");
    }

    @Test
    @DisplayName("getDashboardSummary counts only ACTIVE plans whose examDate is today or later in user timezone")
    void getDashboardSummary_ActivePlansCountFilter() {
        UUID user = UUID.randomUUID();
        when(ragDocumentRepository.countByUserId(user.toString())).thenReturn(3L);
        when(userActivityService.calculateActiveStreak(user)).thenReturn(2);
        when(userActivityService.getOrCreateSettings(user))
                .thenReturn(UserSettings.builder().userId(user).timezone("Asia/Kolkata").build());

        java.time.LocalDate todayInKolkata = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata"));
        when(studyPlanRepository.countActivePlansForDashboard(
                eq(user), eq(com.genzai.study.model.PlanStatus.ACTIVE), eq(todayInKolkata)
        )).thenReturn(4L);

        DashboardSummaryDto summary = dashboardService.getDashboardSummary(user);

        assertThat(summary.getStudyPlansCount()).isEqualTo(4);
        verify(studyPlanRepository).countActivePlansForDashboard(
                eq(user), eq(com.genzai.study.model.PlanStatus.ACTIVE), eq(todayInKolkata)
        );
    }
}
