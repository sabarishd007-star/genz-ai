package com.genzai.aptitude.controller;

import com.genzai.aptitude.dto.*;
import com.genzai.aptitude.entity.AptitudeAttempt;
import com.genzai.aptitude.service.AptitudeService;
import com.genzai.security.UserPrincipal;
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
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping({"/aptitude", "/api/aptitude"})
@RequiredArgsConstructor
public class AptitudeController {

    private final AptitudeService aptitudeService;
    private final UserRepository userRepository;

    @GetMapping("/tracks")
    public ResponseEntity<List<TrackSummaryDto>> getTracks() {
        return ResponseEntity.ok(aptitudeService.getTracks());
    }

    @PostMapping("/attempts")
    public ResponseEntity<AptitudeAttemptDetailDto> startAttempt(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody CreateAttemptRequest request
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(aptitudeService.startAttempt(userId, request));
    }

    @PostMapping("/attempts/{attemptId}/answer")
    public ResponseEntity<AnswerFeedbackDto> recordAnswer(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID attemptId,
            @Valid @RequestBody SubmitAnswerRequest request
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(aptitudeService.recordAnswer(userId, attemptId, request));
    }

    @PostMapping("/attempts/{attemptId}/submit")
    public ResponseEntity<AptitudeAttemptSummaryDto> submitAttempt(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID attemptId
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(aptitudeService.submitAttempt(userId, attemptId));
    }

    @GetMapping("/attempts/{attemptId}")
    public ResponseEntity<AptitudeAttemptSummaryDto> getAttemptDetails(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @PathVariable UUID attemptId
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(aptitudeService.getAttemptDetails(userId, attemptId));
    }

    @GetMapping("/history")
    public ResponseEntity<List<AptitudeAttempt>> getHistory(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(aptitudeService.getHistory(userId));
    }

    @GetMapping("/analytics")
    public ResponseEntity<AptitudeAnalyticsDto> getAnalytics(
            @AuthenticationPrincipal UserPrincipal userPrincipal
    ) {
        UUID userId = resolveUserId(userPrincipal);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(aptitudeService.getAnalytics(userId));
    }

    @PostMapping("/explain")
    public ResponseEntity<ExplainQuestionResponse> explainQuestion(
            @Valid @RequestBody ExplainQuestionRequest request
    ) {
        return ResponseEntity.ok(aptitudeService.explainQuestion(request.getQuestionId(), request.getUserQuery()));
    }

    private UUID resolveUserId(UserPrincipal userPrincipal) {
        if (userPrincipal != null) {
            return userPrincipal.getId();
        }
        // Dev / local demo fallback
        return userRepository.findAll().stream()
                .findFirst()
                .map(User::getId)
                .orElse(null);
    }
}
