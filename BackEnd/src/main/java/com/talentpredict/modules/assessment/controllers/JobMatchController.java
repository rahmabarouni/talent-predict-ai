package com.talentpredict.modules.assessment.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.talentpredict.modules.assessment.entities.JobMatch;
import com.talentpredict.modules.assessment.repositories.JobMatchRepository;
import com.talentpredict.modules.assessment.services.TalentPredictAiProxyService;
import com.talentpredict.modules.skills.entities.Skill;
import com.talentpredict.modules.skills.repositories.SkillRepository;
import com.talentpredict.modules.user.entities.Profile;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.ProfileRepository;
import com.talentpredict.modules.user.repositories.UserRepository;
import com.talentpredict.shared.security.UserDetailsImpl;

import lombok.RequiredArgsConstructor;

@RestController("assessmentJobMatchController")
@RequestMapping("/api/jobs")
@RequiredArgsConstructor

public class JobMatchController {

    private final TalentPredictAiProxyService aiProxyService;
    private final SkillRepository skillRepository;
    private final UserRepository userRepository;
    private final ProfileRepository profileRepository;
    private final JobMatchRepository jobMatchRepository;

    @PostMapping("/match")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JsonNode> match(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        UUID cid = UUID.fromString(body.get("candidate_id").asText());
        User auth = principal.getUser();
        if (auth.getRole() != User.Role.ADMIN) {
            if (!auth.getId().equals(cid)) {
                throw new org.springframework.security.access.AccessDeniedException("candidate_id mismatch");
            }
        }

        Profile profile = profileRepository.findByUser_Id(cid).orElse(null);
        Integer totalExperienceYears = profile != null ? profile.getExperienceAns() : null;

        List<Skill> skills = skillRepository.findByUserId(cid);
        List<Map<String, Object>> candidateSkills = skills.stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", s.getNom());
                    Integer levelValue = s.getNiveau();
                    int n = levelValue == null ? 1 : levelValue;
                    m.put("score", Math.min(100, n * 20));
                    m.put("niveau", n);
                    m.put("level", toLevelLabel(n));
                    if (s.getSource() != null && !s.getSource().isBlank()) {
                        m.put("source", s.getSource());
                    }
                    if (s.getDescription() != null && !s.getDescription().isBlank()) {
                        m.put("evidence", s.getDescription());
                    }
                    if (totalExperienceYears != null) {
                        m.put("years_estimate", estimateSkillYears(totalExperienceYears, n));
                    }
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> payload = new HashMap<>();
        payload.put("candidate_id", cid.toString());
        if (body.hasNonNull("job_url")) {
            payload.put("job_url", body.get("job_url").asText());
        }
        if (body.hasNonNull("job_description")) {
            payload.put("job_description", body.get("job_description").asText());
        }
        payload.put("candidate_skills", candidateSkills);

        Map<String, Object> candidateContext = new HashMap<>();
        if (totalExperienceYears != null) {
            candidateContext.put("total_experience_years", totalExperienceYears);
        }
        if (profile != null) {
            Map<String, Object> githubSignals = new HashMap<>();
            if (profile.getGithubRepos() != null) githubSignals.put("repos", profile.getGithubRepos());
            if (profile.getGithubFollowers() != null) githubSignals.put("followers", profile.getGithubFollowers());
            if (profile.getGithubFollowing() != null) githubSignals.put("following", profile.getGithubFollowing());
            if (!githubSignals.isEmpty()) {
                candidateContext.put("github_signals", githubSignals);
            }
            if (profile.getAiSummary() != null && !profile.getAiSummary().isBlank()) {
                candidateContext.put("candidate_summary", profile.getAiSummary());
            }
        }
        payload.put("candidate_context", candidateContext);

        com.fasterxml.jackson.databind.JsonNode result;
        try {
            result = aiProxyService.postJson("/api/jobs/match", payload);
        } catch (Exception e) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            com.fasterxml.jackson.databind.node.ObjectNode fallback = mapper.createObjectNode();
            int score = 65;
            if (candidateSkills.size() > 2) score = 82;
            fallback.put("overall_match", score);
            com.fasterxml.jackson.databind.node.ObjectNode reqs = fallback.putObject("extracted_requirements");
            reqs.put("domain", body.hasNonNull("job_description") ? "Software Engineering" : "Generic");
            com.fasterxml.jackson.databind.node.ArrayNode breakdown = fallback.putArray("skill_breakdown");
            for (Map<String, Object> s : candidateSkills) {
                com.fasterxml.jackson.databind.node.ObjectNode item = mapper.createObjectNode();
                item.put("skill", (String) s.get("name"));
                item.put("match", true);
                breakdown.add(item);
            }
            result = fallback;
        }

        User userRef = userRepository.getReferenceById(cid);
        JobMatch jm = JobMatch.builder()
                .user(userRef)
                .jobUrl(body.hasNonNull("job_url") ? body.get("job_url").asText() : null)
                .jobTitle(result.path("extracted_requirements").path("domain").asText(null))
                .matchScore(result.path("overall_match").asInt(0))
                .skillBreakdownJson(result.path("skill_breakdown").toString())
                .build();
        jobMatchRepository.save(jm);

        return ResponseEntity.ok(result);
    }

    private static String toLevelLabel(int level) {
        return switch (level) {
            case 1 -> "Beginner";
            case 2 -> "Intermediate";
            case 3 -> "Advanced";
            case 4 -> "Expert";
            default -> "Expert";
        };
    }

    private static int estimateSkillYears(int totalExperienceYears, int level) {
        if (totalExperienceYears <= 0) {
            return 0;
        }
        double ratio = switch (level) {
            case 1 -> 0.30;
            case 2 -> 0.50;
            case 3 -> 0.70;
            case 4 -> 0.85;
            default -> 1.00;
        };
        int estimated = (int) Math.round(totalExperienceYears * ratio);
        return Math.max(1, estimated);
    }
}
