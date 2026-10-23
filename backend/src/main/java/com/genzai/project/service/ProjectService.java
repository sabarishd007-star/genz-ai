package com.genzai.project.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genzai.exception.ResourceNotFoundException;
import com.genzai.llm.LlmProviderService;
import com.genzai.project.dto.*;
import com.genzai.project.model.*;
import com.genzai.project.repository.*;
import com.genzai.project.template.DomainBlueprintTemplates;
import com.genzai.service.UserActivityService;
import com.genzai.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final ProjectBlueprintRepository blueprintRepository;
    private final ProjectMilestoneRepository milestoneRepository;
    private final VivaQuestionRepository vivaQuestionRepository;
    private final VivaAnswerRepository vivaAnswerRepository;
    private final BlueprintValidationService validationService;
    private final VivaEvaluatorService vivaEvaluatorService;
    private final LlmProviderService llmProviderService;
    private final UserActivityService userActivityService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Track completed milestone activity logs for idempotency: "userId:milestoneId"
    private final Set<String> loggedCompletedMilestones = ConcurrentHashMap.newKeySet();

    @Transactional
    public ProjectResponse createProject(UUID userId, CreateProjectRequest request) {
        TenantContext.setTenantId(userId.toString());

        // 1. Create and persist project container
        Project project = Project.builder()
                .userId(userId)
                .title(request.getTitle())
                .domain(request.getDomain())
                .idea(request.getIdea())
                .constraints(request.getConstraints() != null ? request.getConstraints() : Collections.emptyMap())
                .status("ACTIVE")
                .build();
        project = projectRepository.save(project);

        // 2. Generate blueprint (LLM with 2 retries or Template fallback)
        BlueprintGenerationOutcome outcome = generateBlueprintWithFallback(
                request.getDomain(),
                request.getTitle(),
                request.getIdea(),
                request.getConstraints()
        );

        // 3. Persist ProjectBlueprint (version 1)
        ProjectBlueprint blueprint = ProjectBlueprint.builder()
                .projectId(project.getId())
                .userId(userId)
                .version(1)
                .content(outcome.content)
                .source(outcome.source)
                .build();
        blueprint = blueprintRepository.save(blueprint);

        // 4. Extract and persist roadmap milestones
        List<ProjectMilestone> milestones = extractAndPersistMilestones(project.getId(), userId, 1, outcome.content);

        // 5. Extract and persist viva questions
        extractAndPersistVivaQuestions(blueprint.getId(), userId, outcome.content);

        // 6. Log activity
        userActivityService.logActivity(
                userId,
                "PROJECT_CREATED",
                "Created new project: " + project.getTitle(),
                safeJson(Map.of("projectId", project.getId().toString(), "source", outcome.source))
        );

        return toProjectResponse(project, blueprint, milestones);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> getProjects(UUID userId) {
        TenantContext.setTenantId(userId.toString());
        List<Project> projects = projectRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<ProjectResponse> responses = new ArrayList<>();

        for (Project p : projects) {
            Optional<ProjectBlueprint> bpOpt = blueprintRepository.findTopByProjectIdAndUserIdOrderByVersionDesc(p.getId(), userId);
            List<ProjectMilestone> milestones = milestoneRepository.findByProjectIdAndUserIdOrderByPositionAsc(p.getId(), userId);
            responses.add(toProjectResponse(p, bpOpt.orElse(null), milestones));
        }
        return responses;
    }

    @Transactional(readOnly = true)
    public ProjectResponse getProject(UUID userId, UUID projectId) {
        TenantContext.setTenantId(userId.toString());
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        Optional<ProjectBlueprint> bpOpt = blueprintRepository.findTopByProjectIdAndUserIdOrderByVersionDesc(projectId, userId);
        List<ProjectMilestone> milestones = milestoneRepository.findByProjectIdAndUserIdOrderByPositionAsc(projectId, userId);

        return toProjectResponse(project, bpOpt.orElse(null), milestones);
    }

    @Transactional
    public ProjectResponse regenerateSection(UUID userId, UUID projectId, String section) {
        TenantContext.setTenantId(userId.toString());
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        ProjectBlueprint currentBp = blueprintRepository.findTopByProjectIdAndUserIdOrderByVersionDesc(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("No blueprint exists for project: " + projectId));

        // Create new version with updated section
        int newVersion = currentBp.getVersion() + 1;
        Map<String, Object> newContent = new LinkedHashMap<>(currentBp.getContent());

        // Refresh template section or regenerate
        Map<String, Object> freshTemplate = DomainBlueprintTemplates.getTemplateForDomain(project.getDomain(), project.getTitle(), project.getIdea());
        if (section != null && freshTemplate.containsKey(section)) {
            newContent.put(section, freshTemplate.get(section));
        }

        ProjectBlueprint newBp = ProjectBlueprint.builder()
                .projectId(projectId)
                .userId(userId)
                .version(newVersion)
                .content(newContent)
                .source(currentBp.getSource())
                .build();
        newBp = blueprintRepository.save(newBp);

        List<ProjectMilestone> milestones = milestoneRepository.findByProjectIdAndUserIdOrderByPositionAsc(projectId, userId);
        return toProjectResponse(project, newBp, milestones);
    }

    @Transactional
    public ProjectMilestone updateMilestoneStatus(UUID userId, UUID projectId, UUID milestoneId, String newStatus) {
        TenantContext.setTenantId(userId.toString());
        // Verify project ownership
        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        ProjectMilestone milestone = milestoneRepository.findByIdAndUserId(milestoneId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found with id: " + milestoneId));

        if (!milestone.getProjectId().equals(projectId)) {
            throw new ResourceNotFoundException("Milestone does not belong to project: " + projectId);
        }

        milestone.setStatus(newStatus.toUpperCase());
        milestone.setUpdatedAt(OffsetDateTime.now());
        ProjectMilestone updated = milestoneRepository.save(milestone);

        // Idempotent activity logging upon milestone completion
        if ("DONE".equalsIgnoreCase(newStatus)) {
            String idempotencyKey = userId + ":" + milestoneId;
            if (loggedCompletedMilestones.add(idempotencyKey)) {
                userActivityService.logActivity(
                        userId,
                        "PROJECT_MILESTONE_COMPLETED",
                        "Completed project milestone: " + milestone.getTitle(),
                        safeJson(Map.of("projectId", projectId.toString(), "milestoneId", milestoneId.toString()))
                );
            }
        }

        return updated;
    }

    @Transactional(readOnly = true)
    public List<VivaQuestionDto> getVivaQuestions(UUID userId, UUID projectId) {
        TenantContext.setTenantId(userId.toString());
        // Verify project ownership
        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        ProjectBlueprint bp = blueprintRepository.findTopByProjectIdAndUserIdOrderByVersionDesc(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Blueprint not found for project: " + projectId));

        List<VivaQuestion> questions = vivaQuestionRepository.findByBlueprintIdAndUserId(bp.getId(), userId);
        List<VivaQuestionDto> dtos = new ArrayList<>();

        for (VivaQuestion q : questions) {
            List<VivaAnswer> answers = vivaAnswerRepository.findByQuestionIdAndUserId(q.getId(), userId);
            List<VivaAnswerResponse> answerResponses = answers.stream().map(this::toVivaAnswerResponse).toList();

            dtos.add(VivaQuestionDto.builder()
                    .id(q.getId())
                    .blueprintId(q.getBlueprintId())
                    .question(q.getQuestion())
                    .modelAnswer(q.getModelAnswer())
                    .topic(q.getTopic())
                    .previousAnswers(answerResponses)
                    .build());
        }
        return dtos;
    }

    @Transactional
    public VivaAnswerResponse answerVivaQuestion(UUID userId, UUID projectId, UUID questionId, String answerText) {
        TenantContext.setTenantId(userId.toString());
        // Verify project ownership
        projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        VivaQuestion question = vivaQuestionRepository.findByIdAndUserId(questionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Viva question not found with id: " + questionId));

        // Evaluate server-side (never trust client scores)
        VivaEvaluatorService.EvaluationResult eval = vivaEvaluatorService.evaluateAnswer(
                userId,
                question.getQuestion(),
                question.getModelAnswer(),
                answerText
        );

        VivaAnswer answer = VivaAnswer.builder()
                .userId(userId)
                .questionId(questionId)
                .answerText(answerText)
                .scores(eval.getScores())
                .feedback(eval.getFeedback())
                .judgedBy(eval.getJudgedBy())
                .build();
        answer = vivaAnswerRepository.save(answer);

        return toVivaAnswerResponse(answer);
    }

    @Transactional(readOnly = true)
    public String exportReadme(UUID userId, UUID projectId) {
        TenantContext.setTenantId(userId.toString());
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        ProjectBlueprint bp = blueprintRepository.findTopByProjectIdAndUserIdOrderByVersionDesc(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Blueprint not found for project: " + projectId));

        Map<String, Object> content = bp.getContent();
        StringBuilder sb = new StringBuilder();

        sb.append("# ").append(project.getTitle()).append("\n\n");
        sb.append("> **Domain:** ").append(project.getDomain())
                .append(" | **Blueprint Version:** v").append(bp.getVersion())
                .append(" | **Source:** ").append(bp.getSource().equals("TEMPLATE") ? "offline template, not AI-generated" : "AI Generated (LLM)")
                .append("\n\n---\n\n");

        sb.append("## 1. Problem Statement & Overview\n\n");
        sb.append(content.getOrDefault("problemStatement", project.getIdea())).append("\n\n");

        sb.append("## 2. System Architecture Topology\n\n```mermaid\n");
        sb.append(content.getOrDefault("architectureDiagram", "graph TD;\n  Client-->Server;")).append("\n```\n\n");

        sb.append("## 3. Entity-Relationship Schema\n\n```mermaid\n");
        sb.append(content.getOrDefault("erDiagram", "erDiagram;\n  USER ||--o{ PROJECT : owns")).append("\n```\n\n");

        sb.append("## 4. Key Feature Scope\n\n");
        Map<String, Object> features = (Map<String, Object>) content.get("features");
        if (features != null) {
            sb.append("### MVP Features\n");
            List<String> mvp = (List<String>) features.getOrDefault("mvp", List.of());
            mvp.forEach(f -> sb.append("- [ ] ").append(f).append("\n"));
            sb.append("\n### Future Roadmap Features\n");
            List<String> later = (List<String>) features.getOrDefault("later", List.of());
            later.forEach(f -> sb.append("- [ ] ").append(f).append("\n"));
            sb.append("\n");
        }

        sb.append("## 5. Technology Stack\n\n| Layer | Choice | Rationale |\n|---|---|---|\n");
        List<Map<String, String>> stack = (List<Map<String, String>>) content.get("techStack");
        if (stack != null) {
            for (Map<String, String> item : stack) {
                sb.append("| ").append(item.get("layer")).append(" | `").append(item.get("choice")).append("` | ").append(item.get("reason")).append(" |\n");
            }
        }
        sb.append("\n");

        sb.append("## 6. API Specification\n\n| Method | Endpoint | Purpose |\n|---|---|---|\n");
        List<Map<String, String>> endpoints = (List<Map<String, String>>) content.get("apiEndpoints");
        if (endpoints != null) {
            for (Map<String, String> ep : endpoints) {
                sb.append("| `").append(ep.get("method")).append("` | `").append(ep.get("path")).append("` | ").append(ep.get("purpose")).append(" |\n");
            }
        }
        sb.append("\n");

        sb.append("## 7. Viva Defense Question Bank\n\n");
        List<Map<String, String>> viva = (List<Map<String, String>>) content.get("vivaQuestions");
        if (viva != null) {
            int qNum = 1;
            for (Map<String, String> q : viva) {
                sb.append("### Q").append(qNum++).append(". ").append(q.get("question")).append("\n");
                sb.append("**Topic:** `").append(q.getOrDefault("topic", "General")).append("`\n\n");
                sb.append("**Expert Model Answer:**\n> ").append(q.get("modelAnswer")).append("\n\n");
            }
        }

        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String exportJdl(UUID userId, UUID projectId) {
        TenantContext.setTenantId(userId.toString());
        Project project = projectRepository.findByIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Project not found with id: " + projectId));

        ProjectBlueprint bp = blueprintRepository.findTopByProjectIdAndUserIdOrderByVersionDesc(projectId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Blueprint not found for project: " + projectId));

        Map<String, Object> content = bp.getContent();
        List<Map<String, Object>> entities = (List<Map<String, Object>>) content.getOrDefault("entities", List.of());

        StringBuilder sb = new StringBuilder();
        sb.append("/**\n * JHipster JDL Entity Definitions for: ").append(project.getTitle()).append("\n */\n\n");

        for (Map<String, Object> ent : entities) {
            String name = (String) ent.get("name");
            List<String> attrs = (List<String>) ent.getOrDefault("attributes", List.of());

            sb.append("entity ").append(name).append(" {\n");
            for (String attr : attrs) {
                String[] parts = attr.split("\\s+");
                if (parts.length >= 2) {
                    sb.append("  ").append(parts[0]).append(" ").append(mapSqlTypeToJdl(parts[1])).append("\n");
                } else {
                    sb.append("  ").append(attr).append(" String\n");
                }
            }
            sb.append("}\n\n");
        }

        sb.append("paginate * with pagination\n");
        sb.append("service * with serviceClass\n");
        return sb.toString();
    }

    private String mapSqlTypeToJdl(String sqlType) {
        String s = sqlType.toUpperCase();
        if (s.contains("UUID")) return "UUID";
        if (s.contains("INT")) return "Integer";
        if (s.contains("BIGINT")) return "Long";
        if (s.contains("BOOL")) return "Boolean";
        if (s.contains("TIME") || s.contains("DATE")) return "Instant";
        if (s.contains("FLOAT") || s.contains("DOUBLE") || s.contains("NUMERIC")) return "BigDecimal";
        return "String";
    }

    private record BlueprintGenerationOutcome(Map<String, Object> content, String source) {}

    private BlueprintGenerationOutcome generateBlueprintWithFallback(String domain, String title, String idea, Map<String, Object> constraints) {
        // Rule: If real general LLM is available, validate against draft 2020-12 schema and retry up to 2 times
        if (llmProviderService.isGeneralLlmAvailable()) {
            String systemPrompt = """
                You are a senior enterprise software architect. Generate a complete, highly technical project blueprint.
                You MUST return strictly valid JSON matching the draft 2020-12 Project Blueprint Schema.
                Required properties: problemStatement, features (mvp[], later[]), techStack [{layer, choice, reason}],
                erDiagram (valid mermaid text), entities [{name, attributes[]}], apiEndpoints [{method, path, purpose}],
                architectureDiagram (valid mermaid text), roadmap {phases: [{name, milestones: [{title, description}]}]},
                fileTree [{path, purpose}], risks [], vivaQuestions (array of exactly 10 objects with question, modelAnswer, topic).
                Do NOT output markdown blocks or surrounding text. Output raw JSON only.
                """;

            String prompt = String.format("Domain: %s\nTitle: %s\nIdea: %s\nConstraints: %s", domain, title, idea, constraints);

            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    Optional<String> responseOpt = llmProviderService.generateGeneralCompletion(prompt, systemPrompt);
                    if (responseOpt.isPresent()) {
                        String rawJson = sanitizeJson(responseOpt.get());
                        BlueprintValidationService.ValidationResult valRes = validationService.validate(rawJson);
                        if (valRes.isValid()) {
                            Map<String, Object> parsed = objectMapper.readValue(rawJson, new TypeReference<>() {});
                            log.info("LLM generated blueprint successfully validated against schema on attempt {}", attempt);
                            return new BlueprintGenerationOutcome(parsed, "LLM");
                        } else {
                            log.warn("Attempt {} LLM blueprint schema validation failed: {}", attempt, valRes.getErrors());
                            // Feed error back to prompt for next attempt
                            prompt += "\n\nPrevious attempt failed schema validation with errors: " + String.join("; ", valRes.getErrors()) + ". Please fix these errors and output conforming JSON.";
                        }
                    }
                } catch (Exception e) {
                    log.warn("Attempt {} LLM blueprint generation exception: {}", attempt, e.getMessage());
                }
            }
        }

        // Fallback: Template fallback for 3 domains (Full-stack web, AI/ML, Mobile/Cloud)
        log.info("Falling back to schema-valid offline blueprint template for domain: {}", domain);
        Map<String, Object> template = DomainBlueprintTemplates.getTemplateForDomain(domain, title, idea);
        return new BlueprintGenerationOutcome(template, "TEMPLATE");
    }

    private List<ProjectMilestone> extractAndPersistMilestones(UUID projectId, UUID userId, int version, Map<String, Object> content) {
        List<ProjectMilestone> milestones = new ArrayList<>();
        int pos = 0;

        try {
            Map<String, Object> roadmap = (Map<String, Object>) content.get("roadmap");
            if (roadmap != null) {
                List<Map<String, Object>> phases = (List<Map<String, Object>>) roadmap.get("phases");
                if (phases != null) {
                    for (Map<String, Object> phase : phases) {
                        String phaseName = (String) phase.getOrDefault("name", "Phase");
                        List<Map<String, String>> mList = (List<Map<String, String>>) phase.get("milestones");
                        if (mList != null) {
                            for (Map<String, String> m : mList) {
                                ProjectMilestone milestone = ProjectMilestone.builder()
                                        .projectId(projectId)
                                        .userId(userId)
                                        .blueprintVersion(version)
                                        .phase(phaseName)
                                        .title(m.getOrDefault("title", "Milestone"))
                                        .description(m.getOrDefault("description", ""))
                                        .status("TODO")
                                        .position(pos++)
                                        .build();
                                milestones.add(milestoneRepository.save(milestone));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse and persist milestones: {}", e.getMessage());
        }

        return milestones;
    }

    private void extractAndPersistVivaQuestions(UUID blueprintId, UUID userId, Map<String, Object> content) {
        try {
            List<Map<String, String>> questions = (List<Map<String, String>>) content.get("vivaQuestions");
            if (questions != null) {
                for (Map<String, String> q : questions) {
                    VivaQuestion question = VivaQuestion.builder()
                            .blueprintId(blueprintId)
                            .userId(userId)
                            .question(q.get("question"))
                            .modelAnswer(q.get("modelAnswer"))
                            .topic(q.getOrDefault("topic", "Architecture"))
                            .build();
                    vivaQuestionRepository.save(question);
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse and persist viva questions: {}", e.getMessage());
        }
    }

    private ProjectResponse toProjectResponse(Project p, ProjectBlueprint bp, List<ProjectMilestone> milestones) {
        return ProjectResponse.builder()
                .id(p.getId())
                .title(p.getTitle())
                .domain(p.getDomain())
                .idea(p.getIdea())
                .constraints(p.getConstraints())
                .status(p.getStatus())
                .blueprintVersion(bp != null ? bp.getVersion() : null)
                .source(bp != null ? bp.getSource() : "TEMPLATE")
                .blueprintContent(bp != null ? bp.getContent() : Collections.emptyMap())
                .milestones(milestones)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    private VivaAnswerResponse toVivaAnswerResponse(VivaAnswer a) {
        return VivaAnswerResponse.builder()
                .id(a.getId())
                .questionId(a.getQuestionId())
                .answerText(a.getAnswerText())
                .scores(a.getScores())
                .feedback(a.getFeedback())
                .judgedBy(a.getJudgedBy())
                .createdAt(a.getCreatedAt())
                .build();
    }

    private String sanitizeJson(String raw) {
        String clean = raw.trim();
        if (clean.startsWith("```json")) {
            clean = clean.substring(7);
        }
        if (clean.startsWith("```")) {
            clean = clean.substring(3);
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length() - 3);
        }
        return clean.trim();
    }

    private String safeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }
}
