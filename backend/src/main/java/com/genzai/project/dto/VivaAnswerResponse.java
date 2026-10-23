package com.genzai.project.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VivaAnswerResponse {
    private UUID id;
    private UUID questionId;
    private String answerText;
    private Map<String, Object> scores;
    private String feedback;
    private String judgedBy;
    private OffsetDateTime createdAt;
}
