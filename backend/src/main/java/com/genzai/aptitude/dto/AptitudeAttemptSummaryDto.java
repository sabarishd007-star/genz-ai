package com.genzai.aptitude.dto;

import com.genzai.aptitude.entity.AptitudeTrack;
import com.genzai.aptitude.entity.AttemptMode;
import com.genzai.aptitude.entity.AttemptStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AptitudeAttemptSummaryDto {
    private UUID id;
    private AptitudeTrack track;
    private AttemptMode mode;
    private Integer totalQuestions;
    private Integer correctCount;
    private Double scorePct;
    private Integer timeLimitSeconds;
    private Integer totalTimeSpentSeconds;
    private AttemptStatus status;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private List<AptitudeQuestionReviewDto> questions;
}
