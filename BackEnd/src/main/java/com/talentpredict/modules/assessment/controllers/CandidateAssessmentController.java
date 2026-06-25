package com.talentpredict.modules.assessment.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpredict.modules.ai.entities.Prediction;
import com.talentpredict.modules.ai.repositories.PredictionRepository;
import com.talentpredict.modules.assessment.entities.CandidateTestResult;
import com.talentpredict.modules.assessment.repositories.CandidateTestResultRepository;
import com.talentpredict.modules.assessment.services.ReportGeneratorService;
import com.talentpredict.modules.skills.entities.Skill;
import com.talentpredict.modules.skills.repositories.SkillRepository;
import com.talentpredict.modules.user.entities.Profile;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.ProfileRepository;
import com.talentpredict.modules.user.repositories.UserRepository;
import com.talentpredict.shared.security.UserDetailsImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/candidates")
@RequiredArgsConstructor
@Slf4j
public class CandidateAssessmentController {

    private final CandidateTestResultRepository candidateTestResultRepository;
    private final SkillRepository skillRepository;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ReportGeneratorService reportGeneratorService;
    private final PredictionRepository predictionRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/{userId}/progress")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> progress(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        assertSelfOrRecruiter(principal.getUser(), userId);
        List<CandidateTestResult> rows;
        try {
            rows = candidateTestResultRepository.findByUser_IdOrderByTakenAtDesc(userId);
        } catch (Exception e) {
            log.warn("Error loading progress for user {}: {}", userId, e.getMessage());
            return ResponseEntity.ok(java.util.List.of());
        }
        List<Map<String, Object>> list = rows.stream().map(r -> {
            Map<String, Object> m = new HashMap<>();
            m.put("taken_at", r.getTakenAt());
            m.put("overall_score", r.getOverallScore());
            m.put("passed", r.getPassed());
            m.put("test_type", r.getTestType() != null ? r.getTestType().name() : null);
            try {
                m.put("skill_scores", objectMapper.readTree(
                        r.getSkillScoresJson() != null ? r.getSkillScoresJson() : "{}"));
            } catch (java.io.IOException | RuntimeException e) {
                m.put("skill_scores", objectMapper.createObjectNode());
            }
            return m;
        }).toList();
        return ResponseEntity.ok(list);
    }


    @PostMapping("/{userId}/generate-report")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> report(
            @PathVariable UUID userId,
            @AuthenticationPrincipal UserDetailsImpl principal) throws Exception {
        assertSelfOrRecruiter(principal.getUser(), userId);
        User u = userRepository.findById(userId).orElseThrow();
        Profile p = profileRepository.findByUser_Id(userId).orElse(null);
        List<Skill> skills = readSkillsSafely(userId);
        List<CandidateTestResult> hist = readHistorySafely(userId);
        Prediction latestPrediction = readLatestPredictionSafely(u);
        byte[] pdf = reportGeneratorService.buildPdfReport(u, p, skills, hist, latestPrediction);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=report.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    private List<Skill> readSkillsSafely(UUID userId) {
        try {
            return skillRepository.findByUserId(userId);
        } catch (RuntimeException ex) {
            log.warn("Unable to fetch skills for report generation. userId={}", userId, ex);
            return List.of();
        }
    }

    private List<CandidateTestResult> readHistorySafely(UUID userId) {
        try {
            return candidateTestResultRepository.findByUser_IdOrderByTakenAtDesc(userId);
        } catch (RuntimeException ex) {
            log.warn("Unable to fetch test history for report generation. userId={}", userId, ex);
            return List.of();
        }
    }

    private Prediction readLatestPredictionSafely(User user) {
        try {
            return predictionRepository.findTopByUserOrderByDatePredictionDesc(user).orElse(null);
        } catch (RuntimeException ex) {
            log.warn("Unable to fetch latest prediction for report generation. userId={}", user.getId(), ex);
            return null;
        }
    }

    private void assertSelfOrRecruiter(User auth, UUID userId) {
        if (auth.getRole() == User.Role.ADMIN) {
            return;
        }
        if (!auth.getId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Forbidden");
        }
    }
}
