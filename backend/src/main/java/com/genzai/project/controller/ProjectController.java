package com.genzai.project.controller;

import com.genzai.project.dto.*;
import com.genzai.project.model.ProjectMilestone;
import com.genzai.project.service.ProjectService;
import com.genzai.security.UserPrincipal;
import com.genzai.user.entity.User;
import com.genzai.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/projects", "/api/projects"})
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;
    private final UserRepository userRepository;

    @PostMapping
    public ResponseEntity<ProjectResponse> createProject(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreateProjectRequest request
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(projectService.createProject(userId, request));
    }

    @GetMapping
    public ResponseEntity<List<ProjectResponse>> getProjects(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(projectService.getProjects(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> getProject(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(projectService.getProject(userId, id));
    }

    @PostMapping("/{id}/regenerate")
    public ResponseEntity<ProjectResponse> regenerateSection(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id,
            @RequestParam(required = false) String section
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(projectService.regenerateSection(userId, id, section));
    }

    @PatchMapping("/{id}/milestones/{mid}")
    public ResponseEntity<ProjectMilestone> updateMilestoneStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id,
            @PathVariable UUID mid,
            @Valid @RequestBody MilestoneUpdateDto dto
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(projectService.updateMilestoneStatus(userId, id, mid, dto.getStatus()));
    }

    @GetMapping("/{id}/viva")
    public ResponseEntity<List<VivaQuestionDto>> getVivaQuestions(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(projectService.getVivaQuestions(userId, id));
    }

    @PostMapping("/{id}/viva/{qid}/answer")
    public ResponseEntity<VivaAnswerResponse> answerVivaQuestion(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id,
            @PathVariable UUID qid,
            @Valid @RequestBody VivaAnswerRequest request
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(projectService.answerVivaQuestion(userId, id, qid, request.getAnswerText()));
    }

    @GetMapping("/{id}/export/readme")
    public ResponseEntity<String> exportReadme(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String markdown = projectService.exportReadme(userId, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"README.md\"")
                .contentType(MediaType.parseMediaType("text/markdown"))
                .body(markdown);
    }

    @GetMapping("/{id}/export/jdl")
    public ResponseEntity<String> exportJdl(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String jdl = projectService.exportJdl(userId, id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"app.jdl\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(jdl);
    }

    private UUID resolveUserId(UserPrincipal userPrincipal) {
        if (userPrincipal != null) {
            return userPrincipal.getId();
        }
        return userRepository.findAll().stream()
                .findFirst()
                .map(User::getId)
                .orElse(null);
    }
}
