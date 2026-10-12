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
public class ExplainQuestionResponse {
    private UUID questionId;
    private String explanation;
    private String source; // CACHED | LLM | STORED_RATIONALE
    private String modelUsed;
    private boolean isFallback;
}
