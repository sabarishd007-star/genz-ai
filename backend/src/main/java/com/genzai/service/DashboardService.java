package com.genzai.service;

import com.genzai.dto.DashboardSummaryDto;
import com.genzai.rag.repository.RagDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RagDocumentRepository ragDocumentRepository;
    private final UserActivityService userActivityService;
    private final com.genzai.aptitude.repository.AptitudeAttemptRepository aptitudeAttemptRepository;
    private final com.genzai.study.repository.StudyPlanRepository studyPlanRepository;
    private final com.genzai.project.repository.ProjectRepository projectRepository;
    private final com.genzai.project.repository.ProjectMilestoneRepository projectMilestoneRepository;

    @Transactional
    public DashboardSummaryDto getDashboardSummary(UUID userId) {
        if (userId == null) {
            return DashboardSummaryDto.builder()
                    .documentsCount(0)
                    .studyPlansCount(0)
                    .aptitudeScore("--")
                    .activeStreakDays(1)
                    .userTimezone("Asia/Kolkata")
                    .activeProjectsCount(0)
                    .milestoneCompletionRate("0%")
                    .build();
        }

        String userIdStr = userId.toString();
        long docCount = ragDocumentRepository.countByUserId(userIdStr);
        int streak = userActivityService.calculateActiveStreak(userId);
        String tz = userActivityService.getOrCreateSettings(userId).getTimezone();

        // Calculate real average aptitude score if attempts exist
        String aptitudeScoreStr = "--";
        var attempts = aptitudeAttemptRepository.findByUserIdOrderByStartedAtDesc(userId);
        var submitted = attempts.stream()
                .filter(a -> a.getStatus() == com.genzai.aptitude.entity.AttemptStatus.SUBMITTED)
                .toList();
        if (!submitted.isEmpty()) {
            double avgScore = submitted.stream().mapToDouble(com.genzai.aptitude.entity.AptitudeAttempt::getScorePct).average().orElse(0.0);
            aptitudeScoreStr = Math.round(avgScore) + "%";
        }

        // Calculate real active study plans (ACTIVE plans with examDate >= today in user timezone)
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneId.of(tz));
        long activePlansCount = studyPlanRepository.countActivePlansForDashboard(
                userId, com.genzai.study.model.PlanStatus.ACTIVE, today
        );

        // Project metrics
        long activeProjects = projectRepository.countByUserIdAndStatus(userId, "ACTIVE");
        long totalMilestones = projectMilestoneRepository.countByUserId(userId);
        long completedMilestones = projectMilestoneRepository.countByUserIdAndStatus(userId, "DONE");
        String completionRate = totalMilestones > 0
                ? Math.round((completedMilestones * 100.0) / totalMilestones) + "%"
                : "0%";

        // Log this activity
        userActivityService.logActivity(userId, "DASHBOARD_VIEW", "Viewed dashboard summary", null);

        return DashboardSummaryDto.builder()
                .documentsCount(docCount)
                .studyPlansCount((int) activePlansCount)
                .aptitudeScore(aptitudeScoreStr)
                .activeStreakDays(streak)
                .userTimezone(tz)
                .activeProjectsCount(activeProjects)
                .milestoneCompletionRate(completionRate)
                .build();
    }
}
