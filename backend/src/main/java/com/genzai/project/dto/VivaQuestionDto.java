package com.genzai.project.dto;

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
public class VivaQuestionDto {
    private UUID id;
    private UUID blueprintId;
    private String question;
    private String modelAnswer;
    private String topic;
    private List<VivaAnswerResponse> previousAnswers;
}
