package com.genzai.project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateProjectRequest {

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Domain is required")
    private String domain;

    @NotBlank(message = "Project idea is required")
    private String idea;

    private Map<String, Object> constraints;

    private UUID contextDocumentId;
}
