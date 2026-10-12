package com.genzai.study.dto;

import com.genzai.study.service.StudyScheduler;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePlanRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String syllabusText;
    private List<StudyScheduler.TopicWeight> topics;

    private LocalDate startDate;

    @NotNull(message = "Exam date is required")
    @Future(message = "Exam date must be in the future")
    private LocalDate examDate;

    @Builder.Default
    private Double hoursPerDay = 2.0;

    private Set<DayOfWeek> restWeekdays;

    @Builder.Default
    private Integer bufferDays = 2;
}
