package com.genzai.study.service;

import com.genzai.study.model.TaskType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StudySchedulerTest {

    @Test
    @DisplayName("Scheduler reserves last 2 days before exam as BUFFER")
    void generateSchedule_ReservesBufferDays() {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate examDate = LocalDate.of(2026, 6, 15);
        List<StudyScheduler.TopicWeight> topics = List.of(
                new StudyScheduler.TopicWeight("Data Structures", 1.0),
                new StudyScheduler.TopicWeight("Algorithms", 1.0)
        );

        var result = StudyScheduler.generateSchedule(topics, startDate, examDate, 2.0, Set.of(), 2);

        assertThat(result.getTasks()).isNotEmpty();
        // June 13 and June 14 should be BUFFER tasks
        List<StudyScheduler.ScheduledTask> bufferTasks = result.getTasks().stream()
                .filter(t -> t.getType() == TaskType.BUFFER)
                .toList();

        assertThat(bufferTasks).hasSize(2);
        assertThat(bufferTasks).extracting(StudyScheduler.ScheduledTask::getDate)
                .containsExactlyInAnyOrder(LocalDate.of(2026, 6, 13), LocalDate.of(2026, 6, 14));
    }

    @Test
    @DisplayName("Scheduler drops revision tasks that fall on or after the exam date")
    void generateSchedule_DropsRevisionsPastExam() {
        // Only 5 days between start and exam: D+7 revisions must be dropped
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate examDate = LocalDate.of(2026, 6, 6);
        List<StudyScheduler.TopicWeight> topics = List.of(
                new StudyScheduler.TopicWeight("Operating Systems", 1.0)
        );

        var result = StudyScheduler.generateSchedule(topics, startDate, examDate, 2.0, Set.of(), 2);

        // No task should have date >= examDate
        for (StudyScheduler.ScheduledTask t : result.getTasks()) {
            assertThat(t.getDate()).isBefore(examDate);
        }
    }

    @Test
    @DisplayName("Scheduler respects rest days and distributes tasks only on non-rest days")
    void generateSchedule_RespectsRestDays() {
        LocalDate startDate = LocalDate.of(2026, 6, 1); // Monday
        LocalDate examDate = LocalDate.of(2026, 6, 20);
        Set<DayOfWeek> restDays = Set.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        List<StudyScheduler.TopicWeight> topics = List.of(
                new StudyScheduler.TopicWeight("DBMS", 1.0)
        );

        var result = StudyScheduler.generateSchedule(topics, startDate, examDate, 2.0, restDays, 2);

        List<StudyScheduler.ScheduledTask> studyTasks = result.getTasks().stream()
                .filter(t -> t.getType() == TaskType.STUDY)
                .toList();

        for (StudyScheduler.ScheduledTask st : studyTasks) {
            assertThat(restDays).doesNotContain(st.getDate().getDayOfWeek());
        }
    }

    @Test
    @DisplayName("Scheduler distributes study time proportionally to topic weights")
    void generateSchedule_WeightProportionality() {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate examDate = LocalDate.of(2026, 6, 25);
        List<StudyScheduler.TopicWeight> topics = List.of(
                new StudyScheduler.TopicWeight("Major Topic", 3.0),
                new StudyScheduler.TopicWeight("Minor Topic", 1.0)
        );

        var result = StudyScheduler.generateSchedule(topics, startDate, examDate, 2.0, Set.of(), 2);

        int majorMinutes = result.getTasks().stream()
                .filter(t -> t.getType() == TaskType.STUDY && t.getTopic().equals("Major Topic"))
                .mapToInt(StudyScheduler.ScheduledTask::getPlannedMinutes)
                .sum();

        int minorMinutes = result.getTasks().stream()
                .filter(t -> t.getType() == TaskType.STUDY && t.getTopic().equals("Minor Topic"))
                .mapToInt(StudyScheduler.ScheduledTask::getPlannedMinutes)
                .sum();

        assertThat(majorMinutes).isGreaterThan(minorMinutes);
    }

    @Test
    @DisplayName("Scheduler returns warning when exam is too close for study window")
    void generateSchedule_ExamTooCloseReturnsWarning() {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate examDate = LocalDate.of(2026, 6, 3); // Only 2 days: buffer eats all days
        List<StudyScheduler.TopicWeight> topics = List.of(
                new StudyScheduler.TopicWeight("Networks", 1.0)
        );

        var result = StudyScheduler.generateSchedule(topics, startDate, examDate, 2.0, Set.of(), 2);

        assertThat(result.getWarnings()).isNotEmpty();
        assertThat(result.getStudyDaysCount()).isEqualTo(0);
    }

    @Test
    @DisplayName("Scheduler reports warning and shortfall when topics cannot fit within available daily capacity")
    void generateSchedule_ShortfallWhenCapacityExceeded() {
        LocalDate startDate = LocalDate.of(2026, 6, 1);
        LocalDate examDate = LocalDate.of(2026, 6, 7); // 6 days total, 2 buffer = 3 study days (June 1, 2, 3)
        // With only 0.5 hours per day (30 mins/day), 10 massive topics will easily exceed capacity
        List<StudyScheduler.TopicWeight> topics = List.of(
                new StudyScheduler.TopicWeight("Topic 1", 3.0),
                new StudyScheduler.TopicWeight("Topic 2", 3.0),
                new StudyScheduler.TopicWeight("Topic 3", 3.0),
                new StudyScheduler.TopicWeight("Topic 4", 3.0),
                new StudyScheduler.TopicWeight("Topic 5", 3.0),
                new StudyScheduler.TopicWeight("Topic 6", 3.0),
                new StudyScheduler.TopicWeight("Topic 7", 3.0),
                new StudyScheduler.TopicWeight("Topic 8", 3.0),
                new StudyScheduler.TopicWeight("Topic 9", 3.0),
                new StudyScheduler.TopicWeight("Topic 10", 3.0)
        );

        var result = StudyScheduler.generateSchedule(topics, startDate, examDate, 0.5, Set.of(), 2);

        assertThat(result.getWarnings()).isNotEmpty();
        assertThat(result.getShortfallMinutes()).isGreaterThan(0);
    }
}
