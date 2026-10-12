package com.genzai.aptitude.dto;

import com.genzai.aptitude.entity.AptitudeTrack;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackSummaryDto {
    private AptitudeTrack track;
    private String title;
    private String description;
    private long totalQuestions;
    private List<String> topics;
}
