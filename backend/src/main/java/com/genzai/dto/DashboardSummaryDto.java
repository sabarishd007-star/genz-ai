package com.genzai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardSummaryDto {
    private long documentsCount;
    private long studyPlansCount;
    private String aptitudeScore; // e.g. "--" or "82%"
    private int activeStreakDays;
    private String userTimezone;
}
