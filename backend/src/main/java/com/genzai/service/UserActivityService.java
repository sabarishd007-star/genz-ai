package com.genzai.service;

import com.genzai.model.ActivityLog;
import com.genzai.model.UserSettings;
import com.genzai.repository.ActivityLogRepository;
import com.genzai.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserActivityService {

    private final ActivityLogRepository activityLogRepository;
    private final UserSettingsRepository userSettingsRepository;

    @Transactional
    public void logActivity(UUID userId, String activityType, String description, String metadataJson) {
        if (userId == null) return;
        try {
            ActivityLog logEntry = ActivityLog.builder()
                    .userId(userId)
                    .activityType(activityType)
                    .description(description)
                    .metadata(metadataJson)
                    .build();
            activityLogRepository.save(logEntry);
        } catch (Exception e) {
            log.error("Failed to persist activity log for user {}: {}", userId, e.getMessage());
        }
    }

    @Transactional
    public UserSettings getOrCreateSettings(UUID userId) {
        return userSettingsRepository.findByUserId(userId).orElseGet(() -> {
            UserSettings settings = UserSettings.builder()
                    .userId(userId)
                    .timezone("Asia/Kolkata")
                    .build();
            return userSettingsRepository.save(settings);
        });
    }

    @Transactional(readOnly = true)
    public ZoneId getUserZoneId(UUID userId) {
        String tz = userSettingsRepository.findByUserId(userId)
                .map(UserSettings::getTimezone)
                .orElse("Asia/Kolkata");
        try {
            return ZoneId.of(tz);
        } catch (Exception e) {
            return ZoneId.of("Asia/Kolkata");
        }
    }

    /**
     * Calculates the consecutive daily streak based on the user's timezone.
     */
    @Transactional(readOnly = true)
    public int calculateActiveStreak(UUID userId) {
        if (userId == null) return 0;
        ZoneId zoneId = getUserZoneId(userId);
        LocalDate today = LocalDate.now(zoneId);

        List<Instant> timestamps = activityLogRepository.findActivityTimestampsByUserId(userId);
        if (timestamps.isEmpty()) {
            return 1; // Default initial streak for active session
        }

        Set<LocalDate> activeDates = timestamps.stream()
                .map(ts -> ts.atZone(zoneId).toLocalDate())
                .collect(Collectors.toCollection(TreeSet::new));

        // Always include today if user is making an active request
        activeDates.add(today);

        int streak = 0;
        LocalDate cursor = today;

        while (activeDates.contains(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }

        return Math.max(streak, 1);
    }
}
