package com.genzai.study.dto;

import com.genzai.study.model.TaskStatus;
import com.genzai.study.model.TaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyTaskDto {
    private UUID id;
    private UUID planId;
    private LocalDate taskDate;
    private String topic;
    private TaskType type;
    private Integer plannedMinutes;
    private TaskStatus status;
    private OffsetDateTime completedAt;
    private UUID sourceTaskId;
}
