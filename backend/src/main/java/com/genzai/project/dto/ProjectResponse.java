package com.genzai.project.dto;

import com.genzai.project.model.ProjectMilestone;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {
    private UUID id;
    private String title;
    private String domain;
    private String idea;
    private Map<String, Object> constraints;
    private String status;
    private Integer blueprintVersion;
    private String source; // "LLM" or "TEMPLATE"
    private Map<String, Object> blueprintContent;
    private List<ProjectMilestone> milestones;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
