package com.genzai.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VivaAnswerRequest {

    @NotBlank(message = "Answer text cannot be blank")
    private String answerText;
}
