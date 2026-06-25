package com.talentpredict.modules.security.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.talentpredict.modules.security.dto.SecurityDto;
import com.talentpredict.modules.security.services.SecurityDashboardService;
import com.talentpredict.shared.security.UserDetailsImpl;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SecurityController {

    private final SecurityDashboardService securityDashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<SecurityDto.DashboardResponse> dashboard(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(securityDashboardService.getDashboard(principal.getUser()));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SecurityDto.SessionInfo>> sessions(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(securityDashboardService.listActiveSessions(principal.getUser()));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<SecurityDto.MessageResponse> revokeSession(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID sessionId,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(securityDashboardService.revokeSession(
                principal.getUser(),
                sessionId,
                resolveClientIp(httpRequest)));
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<SecurityDto.MessageResponse> revokeAllSessions(
            @AuthenticationPrincipal UserDetailsImpl principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(securityDashboardService.revokeAllSessions(
                principal.getUser(),
                resolveClientIp(httpRequest)));
    }

    @GetMapping("/login-history")
    public ResponseEntity<List<SecurityDto.LoginEventInfo>> loginHistory(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(securityDashboardService.listLoginHistory(principal.getUser()));
    }

    @GetMapping("/email-verification-status")
    public ResponseEntity<SecurityDto.MessageResponse> emailVerificationStatus(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        boolean verified = Boolean.TRUE.equals(principal.getUser().getEmailVerified());
        String message = verified ? "EMAIL_VERIFIED" : "EMAIL_NOT_VERIFIED";
        return ResponseEntity.ok(new SecurityDto.MessageResponse(message));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }




}
