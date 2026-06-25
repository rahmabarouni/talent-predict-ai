//9dim
package com.talentpredict.modules.ai.services;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.talentpredict.modules.ai.dto.PredictionDto;
import com.talentpredict.modules.ai.entities.Prediction;
import com.talentpredict.modules.ai.repositories.PredictionRepository;
import com.talentpredict.modules.auth.services.AuthServiceImpl;
import com.talentpredict.modules.assessment.entities.CandidateTestResult;
import com.talentpredict.modules.assessment.repositories.CandidateTestResultRepository;
import com.talentpredict.modules.formation.dto.FormationDto;
import com.talentpredict.modules.formation.entities.Formation;
import com.talentpredict.modules.skills.entities.Skill;
import com.talentpredict.modules.skills.repositories.SkillRepository;
import com.talentpredict.modules.user.entities.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * AI Module - Prediction Service
 * Core functionality: Generate AI-powered career predictions and
 * recommendations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionService {

    private final PredictionRepository predictionRepository;
    private final CandidateTestResultRepository candidateTestResultRepository;
    private final SkillRepository skillRepository;
    private final AuthServiceImpl authServiceImpl;
    private final AssessmentAiProxyService assessmentAiProxyService;

    @Transactional
    public PredictionDto.Response genererPrediction(UUID userId) {
        log.info("Generating prediction for user: {}", userId);
        User user = authServiceImpl.getUserById(userId);

        // Récupérer les données de l'utilisateur (safe load to avoid bad data crashing the prediction)
        List<CandidateTestResult> tests;
        try {
            tests = candidateTestResultRepository.findByUser_IdOrderByTakenAtDesc(userId);
        } catch (Exception e) {
            log.warn("Could not load test results for user {} (non-fatal): {}", userId, e.getMessage());
            tests = java.util.Collections.emptyList();
        }
        List<Skill> skills = skillRepository.findByUserId(userId);

        // Construire le profil complet pour l'IA
        StringBuilder profileBuilder = new StringBuilder();
        profileBuilder.append("User: ").append(user.getFirstName()).append(" ").append(user.getLastName()).append("\n");
        profileBuilder.append("Email: ").append(user.getEmail()).append("\n\n");

        if (!tests.isEmpty()) {
            profileBuilder.append("--- Évaluations Récentes ---\n");
            tests.forEach(test -> {
                profileBuilder.append("- Type: ").append(test.getTestType())
                        .append(", Score: ").append(test.getOverallScore())
                        .append(", Date: ").append(test.getTakenAt())
                        .append("\n  Détails: ").append(test.getSkillScoresJson()).append("\n");
            });
            profileBuilder.append("\n");
        }

        if (!skills.isEmpty()) {
            profileBuilder.append("--- Compétences Détectées ---\n");
            skills.forEach(skill -> {
                profileBuilder.append("- ").append(skill.getNom())
                        .append(" (").append(skill.getType()).append(", niveau ").append(skill.getNiveau())
                        .append("/5)\n");
            });
            profileBuilder.append("\n");
        }

        // Préparer les données pour le proxy IA
        List<Map<String, Object>> skillsMap = skills.stream().map(s -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("name", s.getNom());
            m.put("type", s.getType());
            m.put("level", s.getNiveau());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> testsMap = tests.stream().map(t -> {
            Map<String, Object> m = new java.util.HashMap<>();
            m.put("type", t.getTestType());
            m.put("score", t.getOverallScore());
            m.put("date", t.getTakenAt());
            return m;
        }).collect(Collectors.toList());

        // Appeler le microservice IA via le proxy
        Map<String, Object> aiResponse = assessmentAiProxyService.generateCareerPrediction(
                userId.toString(),
                user.getFirstName() + " " + user.getLastName(),
                skillsMap,
                testsMap,
                user.getPosition()
        );

        String analyseLlm = aiResponse.get("analysis") != null ? aiResponse.get("analysis").toString() : "";
        Double scoreConfiance = aiResponse.get("confidence_score") != null 
                ? Double.valueOf(aiResponse.get("confidence_score").toString()) 
                : 0.85;

        // Créer la prédiction avec le Builder
        Prediction prediction = Prediction.builder()
                .user(user)
                .analyse(analyseLlm)
                .datePrediction(java.time.LocalDateTime.now())
                .scoreConfiance(scoreConfiance)
                .statut(Prediction.StatutPrediction.COMPLETEE)
            .recommandationSoft(normalizeRecommendation(aiResponse.get("recommendations_soft")))
            .recommandationTech(normalizeRecommendation(aiResponse.get("recommendations_tech")))
                .build();

        log.info("Saving prediction for user {} with confidence {}", userId, scoreConfiance);
        Prediction saved = predictionRepository.save(prediction);
        return convertToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PredictionDto.Response> getPredictionsByUser(UUID userId) {
        return predictionRepository.findByUserIdOrderByDatePredictionDesc(userId)
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PredictionDto.Response getDernierePrediction(UUID userId) {
        return predictionRepository.findFirstByUserIdOrderByDatePredictionDesc(userId)
                .map(this::convertToResponse)
                .orElse(null);
    }

    private PredictionDto.Response convertToResponse(Prediction prediction) {
        PredictionDto.Response response = new PredictionDto.Response();
        response.setId(prediction.getId());
        response.setDatePrediction(prediction.getDatePrediction());
        response.setAnalyse(prediction.getAnalyse());
        response.setRecommandationSoft(prediction.getRecommandationSoft());
        response.setRecommandationTech(prediction.getRecommandationTech());
        response.setScoreConfiance(prediction.getScoreConfiance());
        response.setStatut(prediction.getStatut());

        if (prediction.getFormationsProposees() != null && !prediction.getFormationsProposees().isEmpty()) {
            List<FormationDto.FormationResponse> formations = prediction.getFormationsProposees().stream()
                    .map(this::convertFormationToResponse)
                    .collect(Collectors.toList());
            response.setFormationsProposees(formations);
        }

        return response;
    }

    private FormationDto.FormationResponse convertFormationToResponse(Formation formation) {
        FormationDto.FormationResponse response = new FormationDto.FormationResponse();
        response.setId(formation.getId());
        response.setTitre(formation.getTitre());
        response.setDescription(formation.getDescription());
        response.setType(formation.getType());
        response.setDuree(formation.getDuree());
        response.setFournisseur(formation.getFournisseur());
        response.setUrl(formation.getUrl());
        response.setStatut(formation.getStatut());
        response.setProgression(formation.getProgression());
        return response;
    }

    private static String normalizeRecommendation(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String s) {
            return normalizeRecommendationString(s);
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> item == null ? "" : item.toString().trim())
                    .filter(item -> !item.isBlank())
                    .collect(Collectors.joining(", "));
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream()
                    .map(item -> item == null ? "" : item.toString().trim())
                    .filter(item -> !item.isBlank())
                    .collect(Collectors.joining(", "));
        }
        return value.toString();
    }

    private static String normalizeRecommendationString(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            String inner = trimmed.substring(1, trimmed.length() - 1).trim();
            if (inner.isEmpty()) {
                return "";
            }
            return java.util.Arrays.stream(inner.split(","))
                    .map(part -> part.trim().replaceAll("^['\"]|['\"]$", ""))
                    .filter(part -> !part.isBlank())
                    .collect(Collectors.joining(", "));
        }
        return trimmed;
    }
}
