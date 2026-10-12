package com.genzai.aptitude.dto;

import com.genzai.aptitude.entity.AptitudeTrack;
import com.genzai.aptitude.entity.AttemptMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAttemptRequest {

    @NotNull(message = "Track is required")
    private AptitudeTrack track;

    @Builder.Default
    private AttemptMode mode = AttemptMode.PRACTICE;

    private String topic;

    @Min(value = 1, message = "Minimum questions is 1")
    @Max(value = 50, message = "Maximum questions is 50")
    @Builder.Default
    private Integer questionCount = 10;

    @Builder.Default
    private Integer timeLimitSeconds = 0;
}
