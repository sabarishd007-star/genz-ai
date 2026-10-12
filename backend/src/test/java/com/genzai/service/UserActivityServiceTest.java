package com.genzai.service;

import com.genzai.model.UserSettings;
import com.genzai.repository.ActivityLogRepository;
import com.genzai.repository.UserSettingsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserActivityServiceTest {

    @Mock
    private ActivityLogRepository activityLogRepository;

    @Mock
    private UserSettingsRepository userSettingsRepository;

    @InjectMocks
    private UserActivityService userActivityService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("calculateActiveStreak should return at least 1 for empty activity")
    void calculateActiveStreak_EmptyActivity_ReturnsOne() {
        when(userSettingsRepository.findByUserId(userId))
                .thenReturn(Optional.of(UserSettings.builder().userId(userId).timezone("Asia/Kolkata").build()));
        when(activityLogRepository.findActivityTimestampsByUserId(userId))
                .thenReturn(List.of());

        int streak = userActivityService.calculateActiveStreak(userId);
        assertThat(streak).isEqualTo(1);
    }

    @Test
    @DisplayName("calculateActiveStreak respects Asia/Kolkata timezone over consecutive days")
    void calculateActiveStreak_ConsecutiveDaysInKolkata_CountsConsecutiveStreak() {
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        when(userSettingsRepository.findByUserId(userId))
                .thenReturn(Optional.of(UserSettings.builder().userId(userId).timezone("Asia/Kolkata").build()));

        ZonedDateTime nowKolkata = ZonedDateTime.now(kolkata);
        Instant today = nowKolkata.toInstant();
        Instant yesterday = nowKolkata.minusDays(1).toInstant();
        Instant twoDaysAgo = nowKolkata.minusDays(2).toInstant();

        when(activityLogRepository.findActivityTimestampsByUserId(userId))
                .thenReturn(List.of(today, yesterday, twoDaysAgo));

        int streak = userActivityService.calculateActiveStreak(userId);
        assertThat(streak).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("calculateActiveStreak accounts for day boundary difference between UTC and Asia/Kolkata")
    void calculateActiveStreak_DayBoundaryDifference_UTCVsKolkata() {
        // Activity logged at 19:30 UTC yesterday is already 01:00 AM IST today (+5:30)
        ZoneId kolkata = ZoneId.of("Asia/Kolkata");
        when(userSettingsRepository.findByUserId(userId))
                .thenReturn(Optional.of(UserSettings.builder().userId(userId).timezone("Asia/Kolkata").build()));

        LocalDate todayKolkata = LocalDate.now(kolkata);
        ZonedDateTime todayEarlyMorning = todayKolkata.atTime(1, 0).atZone(kolkata);

        when(activityLogRepository.findActivityTimestampsByUserId(userId))
                .thenReturn(List.of(todayEarlyMorning.toInstant()));

        int streak = userActivityService.calculateActiveStreak(userId);
        assertThat(streak).isGreaterThanOrEqualTo(1);
    }
}
