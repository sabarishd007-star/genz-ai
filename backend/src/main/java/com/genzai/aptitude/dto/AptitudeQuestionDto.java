package com.genzai.aptitude.dto;

import com.genzai.aptitude.entity.AptitudeTrack;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AptitudeQuestionDto {
    private UUID id;
    private AptitudeTrack track;
    private String topic;
    private String difficulty;
    private String question;
    private List<String> options;
}
