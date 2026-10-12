package com.genzai.controller;

import com.genzai.dto.DashboardSummaryDto;
import com.genzai.security.UserPrincipal;
import com.genzai.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping({"/dashboard", "/api/dashboard"})
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryDto> getDashboardSummary(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        UUID userId = (userPrincipal != null) ? userPrincipal.getId() : null;
        return ResponseEntity.ok(dashboardService.getDashboardSummary(userId));
    }
}
