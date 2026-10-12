package com.genzai.study.service;

import com.genzai.study.model.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

public class StudyScheduler {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicWeight {
        private String name;
        @Builder.Default
        private double weight = 1.0;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduledTask {
        private LocalDate date;
        private String topic;
        private TaskType type;
        private int plannedMinutes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleResult {
        private List<ScheduledTask> tasks;
        private List<String> warnings;
        private int totalPlannedMinutes;
        private int capacityMinutes;
        private int shortfallMinutes;
        private int studyDaysCount;
        private int restDaysCount;
        private int bufferDaysCount;
    }

    /**
     * Pure, deterministic schedule calculation without internal clock.
     */
    public static ScheduleResult generateSchedule(
            List<TopicWeight> topics,
            LocalDate startDate,
            LocalDate examDate,
            double hoursPerDay,
            Set<DayOfWeek> restWeekdays,
            int bufferDays
    ) {
        List<String> warnings = new ArrayList<>();
        List<ScheduledTask> tasks = new ArrayList<>();

        if (topics == null || topics.isEmpty()) {
            warnings.add("No topics provided to schedule.");
            return ScheduleResult.builder()
                    .tasks(Collections.emptyList())
                    .warnings(warnings)
                    .totalPlannedMinutes(0)
                    .capacityMinutes(0)
                    .shortfallMinutes(0)
                    .build();
        }

        if (examDate == null || startDate == null || !examDate.isAfter(startDate)) {
            warnings.add("Exam date must be strictly after the start date.");
            return ScheduleResult.builder()
                    .tasks(Collections.emptyList())
                    .warnings(warnings)
                    .totalPlannedMinutes(0)
                    .capacityMinutes(0)
                    .shortfallMinutes(0)
                    .build();
        }

        if (restWeekdays == null) {
            restWeekdays = Collections.emptySet();
        }

        int dailyCapMinutes = (int) Math.round(hoursPerDay * 60.0);
        if (dailyCapMinutes <= 0) {
            warnings.add("Hours per day must be greater than zero.");
            dailyCapMinutes = 60;
        }

        // 1. Identify Buffer Days (last bufferDays immediately preceding examDate)
        // e.g. if examDate is June 10 and bufferDays=2: June 8 and June 9 are BUFFER days.
        int effectiveBufferDays = Math.max(0, bufferDays);
        LocalDate lastStudyDate = examDate.minusDays(effectiveBufferDays + 1);

        List<LocalDate> bufferDates = new ArrayList<>();
        for (int i = effectiveBufferDays; i >= 1; i--) {
            LocalDate bDate = examDate.minusDays(i);
            if (!bDate.isBefore(startDate)) {
                bufferDates.add(bDate);
            }
        }

        // 2. Identify Non-rest Study Days between startDate and lastStudyDate
        List<LocalDate> availableStudyDays = new ArrayList<>();
        int totalRestDays = 0;

        LocalDate cur = startDate;
        while (!cur.isAfter(lastStudyDate)) {
            if (restWeekdays.contains(cur.getDayOfWeek())) {
                totalRestDays++;
            } else {
                availableStudyDays.add(cur);
            }
            cur = cur.plusDays(1);
        }

        int totalDaysAvailable = availableStudyDays.size();
        int capacityMinutes = totalDaysAvailable * dailyCapMinutes;

        if (totalDaysAvailable == 0) {
            warnings.add("No non-rest study days available before exam buffer window. Exam is too close or all days are marked rest.");
            // Reserve buffer days tasks nonetheless
            for (LocalDate bDate : bufferDates) {
                tasks.add(ScheduledTask.builder()
                        .date(bDate)
                        .topic("Final Revision & Mock Test Drill")
                        .type(TaskType.BUFFER)
                        .plannedMinutes(dailyCapMinutes)
                        .build());
            }
            return ScheduleResult.builder()
                    .tasks(tasks)
                    .warnings(warnings)
                    .totalPlannedMinutes(tasks.stream().mapToInt(ScheduledTask::getPlannedMinutes).sum())
                    .capacityMinutes(0)
                    .shortfallMinutes(1)
                    .studyDaysCount(0)
                    .restDaysCount(totalRestDays)
                    .bufferDaysCount(bufferDates.size())
                    .build();
        }

        // 3. Normalize Topic Weights and calculate target minutes
        double totalWeight = topics.stream().mapToDouble(t -> Math.max(0.1, t.getWeight())).sum();

        // Base study allocation: target roughly 65% of daily capacity for initial study,
        // reserving ~35% for spaced repetition tasks (+1, +3, +7).
        int totalStudyMinutesTarget = (int) Math.round(capacityMinutes * 0.65);

        // Map topic to target study minutes
        Map<String, Integer> topicTargetMinutes = new LinkedHashMap<>();
        for (TopicWeight tw : topics) {
            double ratio = Math.max(0.1, tw.getWeight()) / totalWeight;
            int minutes = (int) Math.round(totalStudyMinutesTarget * ratio);
            topicTargetMinutes.put(tw.getName(), Math.max(30, minutes));
        }

        // 4. Distribute STUDY tasks across available study days
        Map<LocalDate, Integer> dayUsedMinutes = new HashMap<>();
        for (LocalDate d : availableStudyDays) {
            dayUsedMinutes.put(d, 0);
        }

        List<ScheduledTask> studyTasks = new ArrayList<>();
        int currentDayIdx = 0;

        for (Map.Entry<String, Integer> entry : topicTargetMinutes.entrySet()) {
            String topicName = entry.getKey();
            int remainingTopicMinutes = entry.getValue();

            while (remainingTopicMinutes > 0 && currentDayIdx < availableStudyDays.size()) {
                LocalDate studyDate = availableStudyDays.get(currentDayIdx);
                int used = dayUsedMinutes.get(studyDate);
                int availableOnDay = dailyCapMinutes - used;

                if (availableOnDay <= 20) {
                    currentDayIdx++;
                    continue;
                }

                int allocateMinutes = Math.min(remainingTopicMinutes, Math.min(availableOnDay, 90));
                ScheduledTask sTask = ScheduledTask.builder()
                        .date(studyDate)
                        .topic(topicName)
                        .type(TaskType.STUDY)
                        .plannedMinutes(allocateMinutes)
                        .build();

                studyTasks.add(sTask);
                tasks.add(sTask);
                dayUsedMinutes.put(studyDate, used + allocateMinutes);
                remainingTopicMinutes -= allocateMinutes;

                if (dayUsedMinutes.get(studyDate) >= dailyCapMinutes - 15) {
                    currentDayIdx++;
                }
            }
        }

        // 5. Generate Spaced Repetition Tasks: D+1, D+3, D+7
        int[] revisionOffsets = new int[]{1, 3, 7};
        int[] revisionMinutes = new int[]{30, 20, 20};

        for (ScheduledTask sTask : studyTasks) {
            for (int rIdx = 0; rIdx < revisionOffsets.length; rIdx++) {
                int offset = revisionOffsets[rIdx];
                int revMin = revisionMinutes[rIdx];
                LocalDate revDate = sTask.getDate().plusDays(offset);

                // RULE: Drop any revision task that falls on or after examDate
                if (!revDate.isBefore(examDate)) {
                    continue;
                }

                // If falling on a rest day, shift to next non-rest day before exam
                LocalDate adjustedRevDate = revDate;
                while (restWeekdays.contains(adjustedRevDate.getDayOfWeek()) && adjustedRevDate.isBefore(examDate)) {
                    adjustedRevDate = adjustedRevDate.plusDays(1);
                }

                if (!adjustedRevDate.isBefore(examDate)) {
                    continue;
                }

                tasks.add(ScheduledTask.builder()
                        .date(adjustedRevDate)
                        .topic(sTask.getTopic())
                        .type(TaskType.REVISION)
                        .plannedMinutes(revMin)
                        .build());
            }
        }

        // 6. Add Buffer Days tasks
        for (LocalDate bDate : bufferDates) {
            tasks.add(ScheduledTask.builder()
                    .date(bDate)
                    .topic("Final Mock Test Drill & Buffer Review")
                    .type(TaskType.BUFFER)
                    .plannedMinutes(dailyCapMinutes)
                    .build());
        }

        // Sort tasks chronologically by date
        tasks.sort(Comparator.comparing(ScheduledTask::getDate).thenComparing(ScheduledTask::getType));

        // 7. Check for capacity shortfalls & warnings
        int totalPlanned = tasks.stream().mapToInt(ScheduledTask::getPlannedMinutes).sum();
        int totalExamCapacity = (int) (ChronoUnit.DAYS.between(startDate, examDate) * dailyCapMinutes);
        int shortfall = Math.max(0, totalPlanned - totalExamCapacity);

        if (shortfall > 0) {
            warnings.add(String.format(
                    "Capacity shortfall: schedule requires %d mins but only %d mins are available. Plan compressed.",
                    totalPlanned, totalExamCapacity
            ));
        }

        return ScheduleResult.builder()
                .tasks(tasks)
                .warnings(warnings)
                .totalPlannedMinutes(totalPlanned)
                .capacityMinutes(capacityMinutes)
                .shortfallMinutes(shortfall)
                .studyDaysCount(totalDaysAvailable)
                .restDaysCount(totalRestDays)
                .bufferDaysCount(bufferDates.size())
                .build();
    }
}
