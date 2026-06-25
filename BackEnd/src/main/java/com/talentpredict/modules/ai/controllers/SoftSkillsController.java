 package com.talentpredict.modules.ai.controllers;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.talentpredict.modules.ai.dto.SoftSkillsAnalysisRequestDto;
import com.talentpredict.modules.ai.dto.SoftSkillsProgressDto;
import com.talentpredict.modules.ai.dto.SoftSkillsResultDto;
import com.talentpredict.modules.ai.services.SoftSkillsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/soft-skills")
@RequiredArgsConstructor
@Slf4j
public class SoftSkillsController {

    private final SoftSkillsService softSkillsService;

    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<SoftSkillsResultDto> analyze(
            @RequestBody SoftSkillsAnalysisRequestDto request) {
        try {
            log.info("POST /api/soft-skills/analyze — user: {}", resolveEmail());
            UUID userId = resolveUserId();
            SoftSkillsResultDto result = softSkillsService.analyze(request, userId);
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in analyze: {}", e.getMessage(), e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/reevaluate")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<SoftSkillsResultDto> reevaluate(
            @RequestBody SoftSkillsAnalysisRequestDto request) {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(softSkillsService.reevaluate(request, userId));
        } catch (Exception e) {
            log.error("Error in reevaluate: {}", e.getMessage(), e);
            throw new ResponseStatusException(
                HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    @PostMapping("/scenario/save")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Void> saveScenario(@RequestBody Map<String, Object> evaluation) {
        try {
            UUID userId = resolveUserId();
            softSkillsService.saveScenarioResult(evaluation, userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error in saveScenario: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/progress")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<List<SoftSkillsProgressDto>> getProgress() {
        try {
            UUID userId = resolveUserId();
            return ResponseEntity.ok(softSkillsService.getProgress(userId));
        } catch (Exception e) {
            log.error("Error in getProgress: {}", e.getMessage(), e);
            return ResponseEntity.ok(List.of());
        }
    }

    @GetMapping("/last")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<SoftSkillsResultDto> getLast() {
        try {
            UUID userId = resolveUserId();
            SoftSkillsResultDto result = softSkillsService.getLastAnalysis(userId);
            if (result == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            return ResponseEntity.ok(result);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error in getLast: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ── Resolve user email from JWT ────────────────────────────────────────
    private String resolveEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        Object principal = auth.getPrincipal();
        if (principal == null) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Cannot resolve email from null principal");
        }
        if (principal instanceof UserDetails ud) {
            return ud.getUsername();
        }
        if (principal instanceof String s) {
            return s;
        }
        throw new ResponseStatusException(
            HttpStatus.UNAUTHORIZED,
            "Cannot resolve email from principal: " + principal.getClass().getSimpleName());
    }

    // ── Resolve user ID from JWT email ─────────────────────────────────────
    private UUID resolveUserId() {
        return softSkillsService.findUserIdByEmail(resolveEmail());
    }
}

