package com.genzai.aptitude.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerFeedbackDto {
    private UUID questionId;
    private String selectedOption;
    private Boolean isCorrect;
    private String correctAnswer;
    private String rationale;
    private boolean submitted;
}
