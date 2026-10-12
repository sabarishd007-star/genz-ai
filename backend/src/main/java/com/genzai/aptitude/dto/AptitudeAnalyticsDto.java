package com.genzai.aptitude.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AptitudeAnalyticsDto {
    private long totalAttempts;
    private long totalPracticed;
    private double overallAccuracyPct;
    private Map<String, Double> trackAccuracy;
    private List<TopicStatDto> weakTopics;
    private List<TopicStatDto> strongTopics;
    private List<TopicStatDto> allTopics;
}
