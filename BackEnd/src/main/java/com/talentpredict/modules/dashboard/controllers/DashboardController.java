package com.talentpredict.modules.dashboard.controllers;

import com.talentpredict.modules.dashboard.dto.DashboardDto;
import com.talentpredict.modules.dashboard.services.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final DashboardService dashboardService;

    /**
     * TASK 2 — Employee Dashboard
     * GET /api/dashboard/users/{userId}
     * Returns the dashboard data for a specific employee.
     */
    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<DashboardDto.Response> getDashboard(@PathVariable UUID userId) {
        log.info("Fetching employee dashboard for userId={}", userId);
        DashboardDto.Response dashboard = dashboardService.getDashboard(userId);
        return ResponseEntity.ok(dashboard);
    }

    /**
     * TASK 2 — Admin (HR) Overview Dashboard
     * GET /api/dashboard/admin/overview
     * Returns aggregated data across all employees for the HR manager.
     * ADMIN role required.
     */
    @GetMapping("/admin/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DashboardDto.AdminOverviewDto> getAdminOverview() {
        log.info("Fetching admin overview dashboard");
        DashboardDto.AdminOverviewDto overview = dashboardService.getAdminOverview();
        return ResponseEntity.ok(overview);
    }
}
