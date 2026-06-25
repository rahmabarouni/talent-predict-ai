package com.talentpredict.modules.assessment.controllers;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpredict.modules.assessment.services.AssessmentPersistenceService;
import com.talentpredict.modules.assessment.services.TalentPredictAiProxyService;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.UserRepository;

import com.talentpredict.shared.security.UserDetailsImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
@Slf4j

public class TestController {

    private final TalentPredictAiProxyService aiProxyService;
    private final AssessmentPersistenceService assessmentPersistenceService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @PostMapping("/generate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JsonNode> generate(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        assertCandidateBody(principal.getUser(), body);
        JsonNode result = aiProxyService.postJson("/api/test/generate", objectToMap(body));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/evaluate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JsonNode> evaluate(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        User actor = principal.getUser();
        assertCandidateBody(actor, body);
        JsonNode result = aiProxyService.postJson("/api/test/evaluate", objectToMap(body));
        UUID candidateId = requireCandidateId(body);
        User candidate = userRepository.findById(candidateId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST, "candidate not found"));
        assessmentPersistenceService.persistMcqEvaluation(candidate, result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/code-challenge/generate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JsonNode> codeChallengeGenerate(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        Map<String, Object> payload = normalizeCodeChallengeGeneratePayload(objectToMap(body), principal.getUser());
        assertCandidateBody(principal.getUser(), objectMapper.valueToTree(payload));
        log.info("Code challenge generate payload: {}", payload);
        try {
            return ResponseEntity.ok(aiProxyService.postJson("/api/test/code-challenge/generate", payload));
        } catch (ResponseStatusException ex) {
            if (!ex.getStatusCode().is5xxServerError()) {
                throw ex;
            }
            log.warn(
                    "AI code challenge generation failed (status: {}). Serving fallback challenge.",
                    ex.getStatusCode().value());
            return ResponseEntity.ok(objectMapper.valueToTree(buildFallbackCodeChallenge(payload)));
        }
    }

    @PostMapping("/code-challenge/evaluate")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JsonNode> codeChallengeEvaluate(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        assertCandidateBody(principal.getUser(), body);
        return ResponseEntity.ok(aiProxyService.postJson("/api/test/code-challenge/evaluate", objectToMap(body)));
    }

    private void assertCandidateBody(User authUser, JsonNode body) {
        if (!body.hasNonNull("candidate_id")) {
            return;
        }
        UUID cid;
        try {
            cid = UUID.fromString(body.get("candidate_id").asText());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "candidate_id must be a valid UUID", ex);
        }
        if (authUser.getRole() == User.Role.ADMIN) {
            return;
        }
        if (!authUser.getId().equals(cid)) {
            throw new org.springframework.security.access.AccessDeniedException("candidate_id must match logged-in user");
        }
    }

    private Map<String, Object> normalizeCodeChallengeGeneratePayload(Map<String, Object> payload, User authUser) {
        String skill = toTrimmedString(payload.get("skill"));
        if (skill == null) {
            Object skills = payload.get("skills");
            if (skills instanceof Iterable<?> iterable) {
                for (Object value : iterable) {
                    skill = toTrimmedString(value);
                    if (skill != null) {
                        break;
                    }
                }
            }
        }
        if (skill == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "skill is required");
        }
        payload.put("skill", skill);

        String level = toTrimmedString(payload.get("level"));
        if (level == null) {
            level = toTrimmedString(payload.get("difficulty"));
        }
        payload.put("level", level == null ? "EXPERT" : level.toUpperCase());

        String candidateId = toTrimmedString(payload.get("candidate_id"));
        if (candidateId == null && authUser != null && authUser.getId() != null) {
            candidateId = authUser.getId().toString();
        }
        if (candidateId != null) {
            payload.put("candidate_id", candidateId);
        }

        return payload;
    }

    private UUID requireCandidateId(JsonNode body) {
        if (body == null || !body.hasNonNull("candidate_id")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "candidate_id is required");
        }
        try {
            return UUID.fromString(body.get("candidate_id").asText());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "candidate_id must be a valid UUID", ex);
        }
    }

    private String toTrimmedString(Object value) {
        if (value == null) {
            return null;
        }
        String s = value.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private Map<String, Object> buildFallbackCodeChallenge(Map<String, Object> payload) {
        String skill = toTrimmedString(payload.get("skill"));
        String level = toTrimmedString(payload.get("level"));
        if (skill == null) {
            skill = "General Programming";
        }
        if (level == null) {
            level = "EXPERT";
        }
        String language = inferLanguage(skill);

        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("challenge_id", UUID.randomUUID().toString());
        fallback.put("skill", skill);
        fallback.put("type", "fix_bugs");
        fallback.put(
                "description",
                "Implement a " + skill + " challenge at " + level.toUpperCase()
                        + " level. Handle input validation, edge cases, and deterministic output.");
        fallback.put("starter_code", fallbackStarterCode(language));
        fallback.put(
                "expected_behavior",
                "The solution should pass standard and edge-case inputs, avoid runtime errors, and keep code readable.");
        fallback.put("hints", List.of(
                "Start with explicit input validation before writing core logic.",
                "Write small helper functions for edge cases and readability."));
        fallback.put("time_limit_seconds", 600);
        fallback.put("language", language);
        return fallback;
    }

    private String inferLanguage(String skill) {
        String s = skill.toLowerCase();
        if (s.contains("python")) {
            return "python";
        }
        if (s.contains("typescript")) {
            return "typescript";
        }
        if (s.contains("java") && !s.contains("javascript")) {
            return "java";
        }
        if (s.contains("c#") || s.contains("csharp")) {
            return "csharp";
        }
        return "javascript";
    }

    private String fallbackStarterCode(String language) {
        if ("python".equals(language)) {
            return "def solve(items):\\n    # TODO: implement\\n    return []";
        }
        if ("java".equals(language)) {
            return "public class Solution {\\n"
                    + "  public static int solve(int[] items) {\\n"
                    + "    // TODO: implement\\n"
                    + "    return 0;\\n"
                    + "  }\\n"
                    + "}";
        }
        return "function solve(items) {\\n  // TODO: implement\\n  return [];\\n}";
    }



    @SuppressWarnings("unchecked")
    private Map<String, Object> objectToMap(JsonNode node) {
        return objectMapper.convertValue(node, Map.class);
    }
}
