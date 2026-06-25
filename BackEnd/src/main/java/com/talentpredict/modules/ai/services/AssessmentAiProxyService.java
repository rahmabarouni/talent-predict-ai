package com.talentpredict.modules.ai.services;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Proxy service: routes GitHub Analyzer and Scenario Simulator requests
 * through Spring Boot to the Python FastAPI AI service.
 */
@Service
@RequiredArgsConstructor
@Slf4j

public class AssessmentAiProxyService {

    @Value("${talentpredict.ai.base-url:}")
    private String aiBaseUrl;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    // ── GitHub Analyzer ─────────────────────────────────────────────────────

    public Map<String, Object> analyzeGithub(String username, List<String> claimedSkills) {
        String url = aiBaseUrl + "/api/test/github/analyze";
        log.info("Proxying GitHub analyze request for user: {}", username);

        try {
            Map<String, Object> payload = Map.of(
                "username", username,
                "claimed_skills", claimedSkills
            );
            HttpEntity<Map<String, Object>> request = buildJsonEntity(payload);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("GitHub analyze proxy failed: {}", e.getMessage());
            return Map.of("status", "error", "message", "AI service unavailable: " + e.getMessage());
        }
    }

    // ── Scenario Simulator ──────────────────────────────────────────────────

    public Map<String, Object> generateScenario(String role, String level) {
        String url = aiBaseUrl + "/api/test/scenario/generate";
        log.info("Proxying scenario generation for role={}, level={}", role, level);

        try {
            Map<String, Object> payload = Map.of("role", role, "level", level);
            HttpEntity<Map<String, Object>> request = buildJsonEntity(payload);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("Scenario generate proxy failed: {}", e.getMessage());
            return Map.of(
                "scenario_title", "Project Deadline Crisis",
                "scenario_description", "Your team's deadline was cut in half due to an upcoming board meeting. "
                    + "As the lead, describe exactly how you would communicate this to your team and what you would do.",
                "skills_tested", List.of("Negotiation", "Stress Management", "Leadership")
            );
        }
    }

    public Map<String, Object> evaluateScenarioResponse(
            String scenario, String candidateResponse) {
        String url = aiBaseUrl + "/api/test/scenario/evaluate";
        log.info("Proxying scenario evaluation");

        try {
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("scenario", scenario);
            payload.put("response", candidateResponse);
            HttpEntity<Map<String, Object>> request = buildJsonEntity(payload);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("Scenario evaluate proxy failed: {}", e.getMessage());
            return Map.of(
                "error", "Evaluation service temporarily unavailable.",
                "scores", Map.of("empathy", 50, "assertiveness", 50, "pragmatism", 50, "communication_clarity", 50)
            );
        }
    }



    public Map<String, Object> generateCareerPrediction(
            String candidateId, String fullName, List<Map<String, Object>> skills, 
            List<Map<String, Object>> testResults, String targetRole) {
        String url = aiBaseUrl + "/api/career/prediction";
        log.info("Proxying career prediction request for user: {}", fullName);

        try {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("candidate_id", candidateId);
            payload.put("full_name", fullName);
            payload.put("skills", skills);
            payload.put("test_results", testResults);
            payload.put("target_role", targetRole);
            payload.put("language", "fr");

            HttpEntity<Map<String, Object>> request = buildJsonEntity(payload);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("Career prediction proxy failed: {}", e.getMessage());
            return Map.of(
                "analysis", "Service d'analyse IA temporairement indisponible. Veuillez réessayer plus tard.",
                "recommendations_soft", "Leadership, Adaptabilité",
                "recommendations_tech", "Développement continu",
                "confidence_score", 0.5
            );
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private HttpEntity<Map<String, Object>> buildJsonEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse AI response JSON: {}", e.getMessage());
            return Map.of("raw", json);
        }
    }
}
