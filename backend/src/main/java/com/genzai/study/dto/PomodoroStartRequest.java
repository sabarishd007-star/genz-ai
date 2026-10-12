package com.genzai.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PomodoroStartRequest {
    private UUID taskId;
    @Builder.Default
    private Integer plannedSeconds = 1500;
}
