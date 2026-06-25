package com.talentpredict.modules.ai.controllers;

import com.talentpredict.modules.ai.services.AssessmentAiProxyService;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Fix 3 — Proxy controller to route all AI analysis calls through Spring Boot.
 * Fix 4 — Applied Circuit Breaker and Time Limiter for fault tolerance.
 */
@RestController("analysisProxyControllerAi")
@RequestMapping("/api/v1/analysis")
@RequiredArgsConstructor
@Slf4j
public class AnalysisProxyController {

    private final AssessmentAiProxyService proxyService;

    // Fix 3 — Proxies the GitHub analysis call to the Python AI service
    // Fix 4 — Circuit Breaker 'aiService' with fallback and 5s Time Limiter
    @PostMapping("/github")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackAnalysis")
    @TimeLimiter(name = "aiService")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> analyzeGithub(
            @RequestBody Map<String, Object> payload) {

        return CompletableFuture.supplyAsync(() -> {
            String username = (String) payload.get("username");
            @SuppressWarnings("unchecked")
            List<String> claimedSkills = (List<String>) payload.get("claimed_skills");

            log.info("Proxying GitHub analysis for user: {}", username);
            Map<String, Object> result = proxyService.analyzeGithub(username, claimedSkills);
            return ResponseEntity.ok(result);
        });
    }

    // Fix 3 — Proxies the career prediction call
    @PostMapping("/career-prediction")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    @CircuitBreaker(name = "aiService", fallbackMethod = "fallbackAnalysis")
    @TimeLimiter(name = "aiService")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> predictCareer(
            @RequestBody Map<String, Object> payload) {

        return CompletableFuture.supplyAsync(() -> {
            log.info("Proxying career prediction request");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> skills = (List<Map<String, Object>>) payload.get("skills");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> testResults = (List<Map<String, Object>>) payload.get("test_results");
            Map<String, Object> result = proxyService.generateCareerPrediction(
                    (String) payload.get("candidate_id"),
                    (String) payload.get("full_name"),
                    skills,
                    testResults,
                    (String) payload.get("target_role"));
            return ResponseEntity.ok(result);
        });
    }

    /**
     * Fix 4 — Fallback method triggered when the AI service is down or slow.
     * Returns a safe error response instead of crashing or hanging.
     */
    public CompletableFuture<ResponseEntity<Map<String, Object>>> fallbackAnalysis(Throwable t) {
        log.error("AI Service fallback triggered: {}", t.getMessage());
        return CompletableFuture.completedFuture(
                ResponseEntity.status(503).body(Map.of(
                        "status", "error",
                        "message",
                        "Le service d'analyse IA est temporairement indisponible. Veuillez réessayer plus tard.",
                        "error_type", t.getClass().getSimpleName())));
    }
}
