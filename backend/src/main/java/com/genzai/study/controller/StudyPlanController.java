package com.genzai.study.controller;

import com.genzai.security.UserPrincipal;
import com.genzai.study.dto.*;
import com.genzai.study.service.StudyPlanService;
import com.genzai.study.service.StudyScheduler;
import com.genzai.user.entity.User;
import com.genzai.user.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/study", "/api/study"})
@RequiredArgsConstructor
public class StudyPlanController {

    private final StudyPlanService studyPlanService;
    private final UserRepository userRepository;

    @PostMapping("/plans/preview")
    public ResponseEntity<StudyScheduler.ScheduleResult> previewPlan(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody PreviewPlanRequest request
    ) {
        UUID userId = resolveUserId(userPrincipal);
        return ResponseEntity.ok(studyPlanService.previewPlan(userId, request));
    }

    @PostMapping("/plans")
    public ResponseEntity<StudyPlanDto> createPlan(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreatePlanRequest request
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(studyPlanService.createPlan(userId, request));
    }

    @GetMapping("/plans")
    public ResponseEntity<List<StudyPlanDto>> getPlans(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(studyPlanService.getPlans(userId));
    }

    @GetMapping("/plans/{id}")
    public ResponseEntity<StudyPlanDto> getPlanById(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(studyPlanService.getPlanById(userId, id));
    }

    @PatchMapping("/plans/{id}/tasks/{taskId}")
    public ResponseEntity<StudyTaskDto> updateTaskStatus(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskStatusRequest request
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(studyPlanService.updateTaskStatus(userId, id, taskId, request));
    }

    @PostMapping("/plans/{id}/catch-up")
    public ResponseEntity<CatchUpResponse> catchUp(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(studyPlanService.catchUp(userId, id));
    }

    @PostMapping("/plans/{id}/weak-topics")
    public ResponseEntity<Map<String, Object>> addWeakTopicRevisions(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        int added = studyPlanService.injectWeakTopicRevisions(userId, id);
        return ResponseEntity.ok(Map.of("addedTasksCount", added, "status", "SUCCESS"));
    }

    @PostMapping("/pomodoro/start")
    public ResponseEntity<PomodoroSessionDto> startPomodoro(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @RequestBody PomodoroStartRequest request
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(studyPlanService.startPomodoro(userId, request));
    }

    @PostMapping("/pomodoro/{id}/complete")
    public ResponseEntity<PomodoroSessionDto> completePomodoro(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID id
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(studyPlanService.completePomodoro(userId, id));
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
