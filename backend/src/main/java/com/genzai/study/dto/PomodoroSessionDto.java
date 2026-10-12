package com.genzai.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PomodoroSessionDto {
    private UUID id;
    private UUID taskId;
    private Integer plannedSeconds;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private Boolean counted;
}
