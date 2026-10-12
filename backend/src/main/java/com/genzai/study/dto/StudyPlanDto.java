package com.genzai.study.dto;

import com.genzai.study.model.PlanStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyPlanDto {
    private UUID id;
    private String title;
    private PlanStatus status;
    private LocalDate examDate;
    private Double hoursPerDay;
    private String timezone;
    private long daysToExam;
    private double progressPercentage;
    private int totalTasks;
    private int completedTasks;
    private Map<LocalDate, List<StudyTaskDto>> tasksByDate;
    private OffsetDateTime createdAt;
}
