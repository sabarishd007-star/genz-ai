package com.genzai.aptitude.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicStatDto {
    private String topic;
    private String track;
    private long totalQuestions;
    private long correctAnswers;
    private double accuracyPct;
}
