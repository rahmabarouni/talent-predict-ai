package com.talentpredict.modules.ai.controllers;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.talentpredict.modules.ai.dto.GithubAnalyzeRequestDto;
import com.talentpredict.modules.ai.dto.ScenarioEvaluateRequestDto;
import com.talentpredict.modules.ai.dto.ScenarioGenerateRequestDto;
import com.talentpredict.modules.ai.services.AssessmentAiProxyService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller exposing the GitHub Code Analyzer and
 * Soft-Skills Scenario Simulator to the Angular frontend.
 */
@RestController
@RequestMapping("/api/assessment")
@RequiredArgsConstructor
@Slf4j
public class AssessmentController {

    private final AssessmentAiProxyService proxyService;

    // ── GitHub Code Analyzer ────────────────────────────────────────────────

    @PostMapping("/github/analyze")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> analyzeGithub(
            @RequestBody GithubAnalyzeRequestDto request) {
        log.info("POST /api/assessment/github/analyze — username: {}", request.getUsername());
        Map<String, Object> result = proxyService.analyzeGithub(
                request.getUsername(), request.getClaimedSkills());
        return ResponseEntity.ok(result);
    }

    // ── Scenario Simulator ──────────────────────────────────────────────────
    //generate scenario based on role and level
    @PostMapping("/scenario/generate")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')") 
    public ResponseEntity<Map<String, Object>> generateScenario(
            @RequestBody ScenarioGenerateRequestDto request) {
        log.info("POST /api/assessment/scenario/generate — role: {}", request.getRole());
        Map<String, Object> result = proxyService.generateScenario(
                request.getRole(), request.getLevel());
        return ResponseEntity.ok(result);
    }
     //evaluation endpoint for scenario simulator 
    @PostMapping("/scenario/evaluate") 
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> evaluateScenario(
            @RequestBody ScenarioEvaluateRequestDto request) {
        log.info("POST /api/assessment/scenario/evaluate");
        Map<String, Object> result = proxyService.evaluateScenarioResponse(
                request.getScenario(), request.getResponse());
        return ResponseEntity.ok(result);
    }


}
