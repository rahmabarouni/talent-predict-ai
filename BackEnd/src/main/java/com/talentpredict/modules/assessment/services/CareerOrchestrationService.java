package com.talentpredict.modules.assessment.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpredict.modules.ai.entities.Prediction;
import com.talentpredict.modules.ai.repositories.PredictionRepository;
import com.talentpredict.modules.skills.entities.Skill;
import com.talentpredict.modules.skills.repositories.SkillRepository;
import com.talentpredict.modules.user.entities.Profile;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.ProfileRepository;
import com.talentpredict.modules.user.repositories.UserRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareerOrchestrationService {


    private final TalentPredictAiProxyService aiProxyService;
    private final SkillRepository skillRepository;
    private final PredictionRepository predictionRepository;
    private final ProfileRepository profileRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;



    public JsonNode generateLearningPlan(JsonNode body, User actor) {
        UUID candidateId = resolveCandidateId(body, actor);
        assertSelfOrRecruiter(actor, candidateId);

        Map<String, Object> payload = objectToMap(body);
        enrichLearningPlanPayload(payload, candidateId);

        try {
            JsonNode aiResponse = aiProxyService.postJson("/api/career/learning-plan", payload);
            JsonNode finalPlan = ensureSoftCoverageInLearningPlan(aiResponse, payload);
            
            // Save to profile for future fallbacks
            try {
                profileRepository.findByUser_Id(candidateId).ifPresent(p -> {
                    p.setLastLearningPlanJson(finalPlan.toString());
                    profileRepository.save(p);
                });
            } catch (Exception e) {
                log.error("Failed to save learning plan to profile: {}", e.getMessage());
            }

            return finalPlan;
        } catch (Exception ex) {
            log.error("Learning plan generation failed: {}", ex.getMessage());
            
            // Try fallback to last saved plan
            try {
                Profile profile = profileRepository.findByUser_Id(candidateId).orElse(null);
                if (profile != null && profile.getLastLearningPlanJson() != null) {
                    log.info("Returning last saved learning plan for candidate {}", candidateId);
                    return objectMapper.readTree(profile.getLastLearningPlanJson());
                }
            } catch (Exception e) {
                log.error("Failed to retrieve last saved plan: {}", e.getMessage());
            }

            log.warn("No saved plan found. Returning backend fallback plan.");
            return buildLearningPlanFallback(payload);
        }
    }

    private JsonNode buildLearningPlanFallback(Map<String, Object> payload) {
        String targetRole = firstNonBlank(payload.get("targetRole"), payload.get("target_role"), "Software Engineer");
        String experienceLevel = normalizeLearningExperienceLevel(firstNonBlank(
                payload.get("experienceLevel"), payload.get("experience_level"), "junior"));
        String language = normalizeLanguage(firstNonBlank(payload.get("preferredLanguage"), payload.get("language"), "en"));
        String timezone = firstNonBlank(payload.get("timezone"), "Africa/Tunis");

        OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));
        String generatedAt = now.toString();

        List<Map<String, Object>> weakSkills = normalizeWeakSkillsForFallback(payload);
        if (weakSkills.isEmpty()) {
            weakSkills = List.of(
                    weakSkillRow("Problem Solving", 4.0, requiredLevelForExperience(experienceLevel)),
                    weakSkillRow("Communication", 4.0, requiredLevelForExperience(experienceLevel)));
        }

        List<Map<String, Object>> breakdown = new ArrayList<>();
        List<Map<String, Object>> formations = new ArrayList<>();
        List<String> mainGaps = new ArrayList<>();

        int totalCurrent = 0;
        int totalGap = 0;

        int phaseCursor = 1;
        for (Map<String, Object> weak : weakSkills) {
            String skill = firstNonBlank(weak.get("name"), "Skill");
            int currentLevel = Math.max(1, Math.min(10, (int) Math.round(parseScoreOnTen(weak.get("score")))));
            int requiredLevel = Math.max(currentLevel + 1, Math.max(6, toInt(weak.get("required_level"), currentLevel + 2)));
            requiredLevel = Math.min(10, requiredLevel);
            int gap = Math.max(0, requiredLevel - currentLevel);
            String priority = toPriority(gap);

            totalCurrent += currentLevel;
            totalGap += gap;

            Map<String, Object> gapRow = new HashMap<>();
            gapRow.put("skill", skill);
            gapRow.put("current_level", currentLevel);
            gapRow.put("required_level", requiredLevel);
            gapRow.put("gap", gap);
            gapRow.put("priority", priority);
            breakdown.add(gapRow);

            mainGaps.add(skill);

                List<Map<String, Object>> courses = recommendedCoursesForFallback(skill, currentLevel, phaseCursor);

            Map<String, Object> formation = new HashMap<>();
            formation.put("skill", skill);
            formation.put("priority", priority);
            formation.put("current_level", currentLevel);
            formation.put("required_level", requiredLevel);
            formation.put("courses", courses);
            formations.add(formation);

            phaseCursor = Math.min(3, phaseCursor + 1);
        }

        int readiness = breakdown.isEmpty() ? 0 : Math.max(10,
                Math.min(95, (int) Math.round((totalCurrent / (double) (breakdown.size() * 10)) * 100.0)));
        int estimatedWeeks = Math.max(4, Math.min(16, totalGap * 2));
        String estimatedReadyDate = now.plusWeeks(estimatedWeeks).toString();

        List<Map<String, Object>> roadmap = buildRoadmapFallback(breakdown);
        List<Map<String, Object>> dailyPlan = buildDailyPlanFallback(breakdown);

        Map<String, Object> result = new HashMap<>();

        result.put("meta", Map.of(
                "generated_at", generatedAt,
                "language", language,
                "timezone", timezone,
                "target_role", targetRole,
                "experience_level", experienceLevel,
                "estimated_ready_date", estimatedReadyDate));

        result.put("summary", Map.of(
                "profile_evaluation", String.format(
                        "Plan de secours genere automatiquement: focus sur %s pour accelerer votre progression vers %s.",
                        String.join(", ", mainGaps),
                        targetRole),
                "main_gaps", mainGaps,
                "strengths", List.of("Plan structure", "Progression quotidienne", "Cours recommends"),
                "overall_readiness_pct", readiness));

        result.put("skill_gap_analysis", Map.of(
                "readiness_score", readiness,
                "estimated_weeks_to_ready", estimatedWeeks,
                "breakdown", breakdown));

        result.put("roadmap", roadmap);
        result.put("formations", formations);
        result.put("assessments", List.of());
        result.put("reinforcement", List.of());
        result.put("project_plan", Map.of(
                "title", targetRole + " mini capstone",
                "description", "Projet court pour appliquer les competences prioritaires.",
                "covers_skills", mainGaps,
                "tech_stack", mainGaps,
                "difficulty", "medium",
                "estimated_hours", 12,
                "features", List.of("Feature principale", "Tests de base", "README"),
                "steps", List.of(
                        Map.of("step", 1, "title", "Setup", "description", "Initialiser le projet", "estimated_hours", 2),
                        Map.of("step", 2, "title", "Build", "description", "Implementer les fonctionnalites", "estimated_hours", 8),
                        Map.of("step", 3, "title", "Review", "description", "Tester et documenter", "estimated_hours", 2)),
                "deployment_target", "GitHub"));
        result.put("milestones", List.of());
        result.put("re_evaluation", Map.of(
                "trigger_after_days", 14,
                "quiz_score_threshold", 60,
                "re_evaluate_skills", mainGaps,
                "next_checkpoint_date", LocalDate.now().plusDays(14).toString()));
        result.put("daily_plan", dailyPlan);
        result.put("weekly_checkins", List.of());
        result.put("market_alignment", Map.of(
                "top_hiring_companies", List.of("Startups", "Scaleups", "Tech consulting"),
                "avg_salary_range", "Depends on region and seniority",
                "most_requested_skills", mainGaps,
                "job_search_keywords", List.of(targetRole, targetRole + " junior"),
                "time_to_first_interview_weeks", Math.max(2, estimatedWeeks / 2)));
        result.put("mentor_profile", Map.of(
                "ideal_mentor_type", "senior-dev",
                "ideal_mentor_skills", mainGaps,
                "recommended_communities", List.of(
                        Map.of("name", "Dev.to", "url", "https://dev.to", "description", "Partage de pratique quotidienne"),
                        Map.of("name", "Stack Overflow", "url", "https://stackoverflow.com", "description", "Q&A technique"))));

        return objectMapper.valueToTree(result);
    }

    private JsonNode ensureSoftCoverageInLearningPlan(JsonNode responseNode, Map<String, Object> payload) {
        if (responseNode == null || responseNode.isNull()) {
            return responseNode;
        }

        List<Map<String, Object>> softWeakSkills = normalizeWeakSkillsForFallback(payload)
                .stream()
                .filter(row -> isLikelySoftSkillName(firstNonBlank(row.get("name"))))
                .toList();

        if (softWeakSkills.isEmpty()) {
            return responseNode;
        }

        Map<String, Object> response = objectToMap(responseNode);
        List<Map<String, Object>> formations = asMutableMapList(response.get("formations"));

        Set<String> existingFormations = new HashSet<>();
        for (Map<String, Object> formation : formations) {
            String name = firstNonBlank(formation.get("skill")).toLowerCase(Locale.ROOT);
            if (hasText(name)) {
                existingFormations.add(name);
            }
        }

        List<Map<String, Object>> addedFormations = new ArrayList<>();
        int phaseRef = Math.min(3, Math.max(1, formations.size() + 1));

        for (Map<String, Object> weak : softWeakSkills) {
            String skill = firstNonBlank(weak.get("name"));
            String key = skill.toLowerCase(Locale.ROOT);
            if (!hasText(skill) || existingFormations.contains(key)) {
                continue;
            }

            int currentLevel = Math.max(1, Math.min(10, (int) Math.round(parseScoreOnTen(weak.get("score")))));
            int requiredLevel = Math.max(currentLevel + 1, Math.max(6, toInt(weak.get("required_level"), currentLevel + 2)));
            requiredLevel = Math.min(10, requiredLevel);
            int gap = Math.max(0, requiredLevel - currentLevel);

            Map<String, Object> formation = new HashMap<>();
            formation.put("skill", skill);
            formation.put("priority", toPriority(gap));
            formation.put("current_level", currentLevel);
            formation.put("required_level", requiredLevel);
            formation.put("courses", recommendedCoursesForFallback(skill, currentLevel, phaseRef));
            addedFormations.add(formation);

            existingFormations.add(key);
            phaseRef = Math.min(3, phaseRef + 1);
        }

        if (addedFormations.isEmpty()) {
            return responseNode;
        }

        formations.addAll(addedFormations);
        response.put("formations", formations);

        Map<String, Object> skillGapAnalysis = mutableMap(response.get("skill_gap_analysis"));
        List<Map<String, Object>> breakdown = asMutableMapList(skillGapAnalysis.get("breakdown"));

        Set<String> existingBreakdown = new HashSet<>();
        for (Map<String, Object> row : breakdown) {
            String name = firstNonBlank(row.get("skill")).toLowerCase(Locale.ROOT);
            if (hasText(name)) {
                existingBreakdown.add(name);
            }
        }

        for (Map<String, Object> formation : addedFormations) {
            String skill = firstNonBlank(formation.get("skill"));
            String key = skill.toLowerCase(Locale.ROOT);
            if (!hasText(skill) || existingBreakdown.contains(key)) {
                continue;
            }

            int currentLevel = toInt(formation.get("current_level"), 1);
            int requiredLevel = toInt(formation.get("required_level"), Math.max(6, currentLevel + 1));
            int gap = Math.max(0, requiredLevel - currentLevel);

            Map<String, Object> row = new HashMap<>();
            row.put("skill", skill);
            row.put("current_level", currentLevel);
            row.put("required_level", requiredLevel);
            row.put("gap", gap);
            row.put("priority", firstNonBlank(formation.get("priority"), toPriority(gap)));
            breakdown.add(row);

            existingBreakdown.add(key);
        }

        skillGapAnalysis.put("breakdown", breakdown);
        response.put("skill_gap_analysis", skillGapAnalysis);

        return objectMapper.valueToTree(response);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> asMutableMapList(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : collection) {
            if (item instanceof Map<?, ?> map) {
                rows.add(new HashMap<>((Map<String, Object>) map));
            }
        }
        return rows;
    }

    private List<Map<String, Object>> normalizeWeakSkillsForFallback(Map<String, Object> payload) {
        Object source = hasItems(payload.get("weak_skills")) ? payload.get("weak_skills") : payload.get("weakSkills");
        if (!(source instanceof Collection<?> collection)) {
            return List.of();
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object row : collection) {
            if (!(row instanceof Map<?, ?> raw)) {
                continue;
            }
            String name = firstNonBlank(raw.get("name"));
            if (!hasText(name)) {
                continue;
            }

            Map<String, Object> weak = new HashMap<>();
            weak.put("name", name.trim());
            weak.put("score", parseScoreOnTen(raw.get("score")));
            weak.put("required_level", toInt(raw.get("required_level"), 7));
            normalized.add(weak);
        }
        return normalized;
    }

    private Map<String, Object> weakSkillRow(String name, double score, int requiredLevel) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("score", score);
        row.put("required_level", requiredLevel);
        return row;
    }

    private Map<String, Object> courseRow(
            String skill,
            String platformKey,
            String platformName,
            int durationHours,
            String level,
            int phaseRef,
            String reason) {
        return courseRow(skill, skill, platformKey, platformName, durationHours, level, phaseRef, reason);
        }

        private Map<String, Object> courseRow(
            String skill,
            String searchQuery,
            String platformKey,
            String platformName,
            int durationHours,
            String level,
            int phaseRef,
            String reason) {
        String url = searchUrlForPlatform(platformKey, searchQuery);

        Map<String, Object> row = new HashMap<>();
        row.put("id", skill.toLowerCase(Locale.ROOT).replace(" ", "-") + "-" + platformKey + "-fallback");
        row.put("title", skill + " - " + platformName + " path");
        row.put("platform", platformName);
        row.put("url", url);
        row.put("provider", platformName);
        row.put("duration_hours", durationHours);
        row.put("level", level);
        row.put("phase_ref", phaseRef);
        row.put("reason", reason);
        return row;
    }

    private String searchUrlForPlatform(String platformKey, String query) {
        String encodedQuery = firstNonBlank(query, "skill").trim().replace(" ", "+");

        return switch (platformKey) {
            case "udemy" -> "https://www.udemy.com/courses/search/?q=" + encodedQuery;
            case "coursera" -> "https://www.coursera.org/search?query=" + encodedQuery;
            case "edx" -> "https://www.edx.org/search?q=" + encodedQuery;
            case "linkedin-learning" -> "https://www.linkedin.com/learning/search?keywords=" + encodedQuery;
            default -> "https://www.google.com/search?q=" + encodedQuery;
        };
    }

    private String officialDocumentationUrl(String skill) {
        String normalized = firstNonBlank(skill).toLowerCase(Locale.ROOT);
        if (!hasText(normalized)) {
            return "";
        }

        Map<String, String> docs = new HashMap<>();
        docs.put("java", "https://docs.oracle.com/en/java/");
        docs.put("spring", "https://spring.io/guides");
        docs.put("python", "https://docs.python.org/3/tutorial/");
        docs.put("javascript", "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide");
        docs.put("typescript", "https://www.typescriptlang.org/docs/");
        docs.put("react", "https://react.dev/learn");
        docs.put("angular", "https://angular.dev/overview");
        docs.put("node", "https://nodejs.org/en/docs/guides/");
        docs.put("docker", "https://docs.docker.com/get-started/");
        docs.put("kubernetes", "https://kubernetes.io/docs/tutorials/");
        docs.put("sql", "https://www.w3schools.com/sql/");
        docs.put("git", "https://git-scm.com/doc");
        docs.put("tensorflow", "https://www.tensorflow.org/learn");
        docs.put("jest", "https://jestjs.io/docs/getting-started");
        docs.put("github", "https://docs.github.com/");

        for (Map.Entry<String, String> entry : docs.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        return "";
    }

    private String softSkillLearningQuery(String skill) {
        String normalized = firstNonBlank(skill).toLowerCase(Locale.ROOT);
        if (normalized.contains("communication")) {
            return "effective communication skills";
        }
        if (normalized.contains("lead")) {
            return "leadership and influence";
        }
        if (normalized.contains("team") || normalized.contains("collaboration")) {
            return "teamwork and collaboration";
        }
        if (normalized.contains("time")) {
            return "time management and productivity";
        }
        if (normalized.contains("negotiation")) {
            return "negotiation skills";
        }
        if (normalized.contains("presentation")) {
            return "public speaking and presentation skills";
        }
        return firstNonBlank(skill, "professional soft skills");
    }

    private List<Map<String, Object>> recommendedCoursesForFallback(String skill, int currentLevel, int phaseRef) {
        String level = toCourseLevel(currentLevel);
        List<Map<String, Object>> courses = new ArrayList<>();

        if (isLikelySoftSkillName(skill)) {
            String query = softSkillLearningQuery(skill);
            courses.add(courseRow(skill, query, "coursera", "Coursera", 10, level, phaseRef,
                    "Parcours cible pour developper cette soft skill de maniere structuree."));
            courses.add(courseRow(skill, query, "linkedin-learning", "LinkedIn Learning", 8, level, phaseRef,
                    "Modules courts et applicables rapidement en contexte professionnel."));
            courses.add(courseRow(skill, query, "udemy", "Udemy", 9, level, phaseRef,
                    "Pratique guidee avec exercices concrets et mise en situation."));
            return courses;
        }

        courses.add(courseRow(skill, "udemy", "Udemy", 10, level, phaseRef,
                "Cours pratique pour fermer rapidement le gap technique principal."));
        courses.add(courseRow(skill, "coursera", "Coursera", 12, level, phaseRef,
                "Parcours structure pour renforcer la pratique et la theorie."));

        String docsUrl = officialDocumentationUrl(skill);
        if (hasText(docsUrl)) {
            Map<String, Object> official = new HashMap<>();
            official.put("id", skill.toLowerCase(Locale.ROOT).replace(" ", "-") + "-official-fallback");
            official.put("title", skill + " - Official docs learning path");
            official.put("platform", "Official Docs");
            official.put("url", docsUrl);
            official.put("provider", "Official Documentation");
            official.put("duration_hours", 8);
            official.put("level", level);
            official.put("phase_ref", phaseRef);
            official.put("reason", "Source officielle fiable pour consolider les fondamentaux et bonnes pratiques.");
            courses.add(official);
        } else {
            courses.add(courseRow(skill, "edx", "edX", 10, level, phaseRef,
                    "Alternative academique pour varier les exercices et consolidations."));
        }

        return courses;
    }

    private List<Map<String, Object>> buildRoadmapFallback(List<Map<String, Object>> breakdown) {
        List<Map<String, Object>> roadmap = new ArrayList<>();
        if (breakdown.isEmpty()) {
            roadmap.add(Map.of(
                    "phase", 1,
                    "title", "Foundations",
                    "duration_weeks", 2,
                    "focus_skills", List.of("Problem Solving"),
                    "goals", List.of("Reviser les fondamentaux"),
                    "exit_criteria", List.of("Completer les cours proposes")));
            return roadmap;
        }

        int phase = 1;
        for (Map<String, Object> row : breakdown) {
            if (phase > 3) {
                break;
            }
            String skill = firstNonBlank(row.get("skill"), "Skill");
            roadmap.add(Map.of(
                    "phase", phase,
                    "title", "Phase " + phase + " - " + skill,
                    "duration_weeks", 2,
                    "focus_skills", List.of(skill),
                    "goals", List.of("Renforcer " + skill),
                    "exit_criteria", List.of("Terminer les modules et appliquer dans un mini projet")));
            phase += 1;
        }
        return roadmap;
    }

    private List<Map<String, Object>> buildDailyPlanFallback(List<Map<String, Object>> breakdown) {
        List<Map<String, Object>> daily = new ArrayList<>();
        if (breakdown.isEmpty()) {
            return daily;
        }

        for (int day = 1; day <= 7; day++) {
            Map<String, Object> row = breakdown.get((day - 1) % breakdown.size());
            String skill = firstNonBlank(row.get("skill"), "Skill");

            List<Map<String, Object>> tasks = new ArrayList<>();
            tasks.add(dailyTaskRow("Lire un module sur " + skill, "read", 30));
            tasks.add(dailyTaskRow("Pratiquer un exercice sur " + skill, "practice", 45));

            daily.add(Map.of(
                    "day", day,
                    "phase_ref", Math.min(3, Math.max(1, day / 3 + 1)),
                    "focus_skill", skill,
                    "tasks", tasks,
                    "estimated_total_hours", 2,
                    "tip", "Avancez par petites iterations et validez chaque acquis."));
        }

        return daily;
    }

    private Map<String, Object> dailyTaskRow(String task, String type, int durationMinutes) {
        Map<String, Object> row = new HashMap<>();
        row.put("task", task);
        row.put("type", type);
        row.put("course_id", null);
        row.put("duration_minutes", durationMinutes);
        return row;
    }

    private String toPriority(int gap) {
        if (gap >= 5) {
            return "critical";
        }
        if (gap >= 3) {
            return "high";
        }
        if (gap >= 1) {
            return "medium";
        }
        return "low";
    }

    private String toCourseLevel(int currentLevel) {
        if (currentLevel <= 3) {
            return "beginner";
        }
        if (currentLevel <= 6) {
            return "intermediate";
        }
        return "advanced";
    }

    private int toInt(Object value, int fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private double parseScoreOnTen(Object value) {
        if (value == null) {
            return 4.0;
        }

        try {
            double parsed = Double.parseDouble(value.toString().trim());
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                return 4.0;
            }
            return Math.max(1.0, Math.min(10.0, parsed));
        } catch (NumberFormatException ex) {
            return 4.0;
        }
    }

    private UUID resolveCandidateId(JsonNode body, User actor) {
        if (body != null && body.hasNonNull("candidate_id")) {
            try {
                return UUID.fromString(body.get("candidate_id").asText());
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "candidate_id must be a valid UUID", ex);
            }
        }

        if (actor == null || actor.getId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "candidate_id is required");
        }
        return actor.getId();
    }

    private void assertSelfOrRecruiter(User actor, UUID candidateId) {
        if (actor.getRole() == User.Role.ADMIN) {
            return;
        }

        if (!actor.getId().equals(candidateId)) {
            throw new org.springframework.security.access.AccessDeniedException("candidate_id mismatch");
        }
    }

    private void enrichLearningPlanPayload(Map<String, Object> payload, UUID candidateId) {
        payload.put("candidate_id", candidateId.toString());

        User candidate = userRepository.findById(candidateId).orElse(null);
        Profile profile = profileRepository.findByUser_Id(candidateId).orElse(null);
        List<Skill> allSkills = skillRepository.findByUserId(candidateId);
        List<Skill> techSkills = allSkills.stream()
                .filter(skill -> skill.getType() == Skill.TypeSkill.TECH)
                .toList();
        List<Skill> softSkills = allSkills.stream()
                .filter(skill -> skill.getType() == Skill.TypeSkill.SOFT)
                .toList();

        String resolvedRole = resolveRole(candidate, profile);
        String inferredLevel = inferLevel(profile, allSkills);
        String mappedExperienceLevel = toLearningExperienceLevel(inferredLevel);

        String targetRole = firstNonBlank(
            payload.get("targetRole"),
            payload.get("target_role"),
            resolvedRole);
        payload.put("targetRole", targetRole);
        payload.put("target_role", targetRole);

        String experienceLevel = normalizeLearningExperienceLevel(firstNonBlank(
            payload.get("experienceLevel"),
            payload.get("experience_level"),
            mappedExperienceLevel));
        payload.put("experienceLevel", experienceLevel);
        payload.put("experience_level", experienceLevel);

        payload.put("hoursPerDay", parseHoursPerDay(payload.get("hoursPerDay"), 1.5));

        String preferredLanguage = normalizeLanguage(firstNonBlank(
            payload.get("preferredLanguage"),
            payload.get("language"),
            "en"));
        payload.put("preferredLanguage", preferredLanguage);
        payload.put("language", preferredLanguage);

        String learningStyle = normalizeLearningStyle(firstNonBlank(
            payload.get("learningStyle"),
            payload.get("preferred_learning_style"),
            "mixed"));
        payload.put("learningStyle", learningStyle);
        payload.put("preferred_learning_style", learningStyle);

        String timezone = firstNonBlank(payload.get("timezone"), "Africa/Tunis");
        payload.put("timezone", timezone);

        List<Map<String, Object>> generatedWeakSkills = toWeakSkillsPayload(
            candidateId,
            techSkills,
            softSkills,
            experienceLevel);

        List<Map<String, Object>> providedWeakSkills = normalizeWeakSkillsForFallback(payload);
        List<Map<String, Object>> finalWeakSkills = providedWeakSkills.isEmpty()
            ? generatedWeakSkills
            : mergeProvidedAndGeneratedWeakSkills(providedWeakSkills, generatedWeakSkills);

        payload.put("weakSkills", finalWeakSkills);
        payload.put("weak_skills", finalWeakSkills);
    }

    private String resolveRole(User candidate, Profile profile) {
        if (profile != null && hasText(profile.getTitreProfessionnel())) {
            return profile.getTitreProfessionnel().trim();
        }
        if (candidate != null && hasText(candidate.getPosition())) {
            return candidate.getPosition().trim();
        }
        return "Software Engineer";
    }

    private String inferLevel(Profile profile, List<Skill> skills) {
        int experienceYears = profile != null && profile.getExperienceAns() != null ? profile.getExperienceAns() : 0;

        double averageSkillLevel = skills.stream()
                .map(Skill::getNiveau)
                .filter(value -> value != null)
                .mapToInt(Integer::intValue)
                .average()
                .orElse(0);

        if (experienceYears >= 10 || averageSkillLevel >= 4.4) {
            return "EXPERT";
        }
        if (experienceYears >= 6 || averageSkillLevel >= 3.4) {
            return "ADVANCED";
        }
        if (experienceYears >= 2 || averageSkillLevel >= 2.2) {
            return "INTERMEDIATE";
        }
        return "BEGINNER";
    }

    private String toLearningExperienceLevel(String level) {
        return switch (level) {
            case "BEGINNER" -> "beginner";
            case "INTERMEDIATE" -> "junior";
            case "ADVANCED" -> "mid";
            case "EXPERT" -> "senior";
            default -> "junior";
        };
    }

    private String normalizeLearningExperienceLevel(String level) {
        String normalized = level == null ? "junior" : level.trim().toLowerCase();
        if (normalized.equals("beginner") || normalized.equals("junior")
                || normalized.equals("mid") || normalized.equals("senior")) {
            return normalized;
        }
        return "junior";
    }

    private String normalizeLanguage(String language) {
        String normalized = language == null ? "en" : language.trim().toLowerCase();
        if (normalized.equals("en") || normalized.equals("fr") || normalized.equals("ar")) {
            return normalized;
        }
        return "en";
    }

    private String normalizeLearningStyle(String learningStyle) {
        String normalized = learningStyle == null ? "mixed" : learningStyle.trim().toLowerCase();
        if (normalized.equals("video") || normalized.equals("reading")
                || normalized.equals("hands-on") || normalized.equals("mixed")) {
            return normalized;
        }
        return "mixed";
    }

    private List<Map<String, Object>> mergeProvidedAndGeneratedWeakSkills(
            List<Map<String, Object>> provided,
            List<Map<String, Object>> generated) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        mergeWeakSkillRows(merged, provided);
        mergeWeakSkillRows(merged, generated);
        return merged.values().stream().limit(12).toList();
    }

    private boolean containsSoftWeakSkill(Collection<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            String name = firstNonBlank(row.get("name"));
            if (isLikelySoftSkillName(name)) {
                return true;
            }
        }
        return false;
    }

    private boolean isLikelySoftSkillName(String skillName) {
        if (!hasText(skillName)) {
            return false;
        }

        String normalized = skillName.trim().toLowerCase(Locale.ROOT);
        Set<String> keywords = Set.of(
                "communication",
                "collaboration",
                "teamwork",
                "team",
                "leadership",
                "negotiation",
                "presentation",
                "conflict",
                "empathy",
                "adaptability",
                "discipline",
                "time management",
                "problem solving",
                "soft skill");

        if (keywords.contains(normalized)) {
            return true;
        }

        for (String keyword : keywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }

        return false;
    }

    private List<Map<String, Object>> toWeakSkillsPayload(
            UUID candidateId,
            List<Skill> techSkills,
            List<Skill> softSkills,
            String experienceLevel) {
        int baselineRequired = requiredLevelForExperience(experienceLevel);
        Map<String, Map<String, Object>> uniqueSkills = new LinkedHashMap<>();

        mergeWeakSkillRows(uniqueSkills, toWeakRowsFromSkills(techSkills, baselineRequired, 6));
        mergeWeakSkillRows(uniqueSkills, toWeakRowsFromSkills(softSkills, baselineRequired, 4));

        if (softSkills.isEmpty()) {
            mergeWeakSkillRows(uniqueSkills, toWeakRowsFromLatestSoftPrediction(candidateId, baselineRequired, 4));
        }

        if (!containsSoftWeakSkill(uniqueSkills.values())) {
            Map<String, Object> genericSoft = new HashMap<>();
            genericSoft.put("name", "Communication");
            genericSoft.put("score", 4.0);
            genericSoft.put("required_level", baselineRequired);
            uniqueSkills.putIfAbsent("communication", genericSoft);
        }

        if (!uniqueSkills.isEmpty()) {
            return uniqueSkills.values().stream().limit(10).toList();
        }

        List<Map<String, Object>> fallback = new ArrayList<>();
        Map<String, Object> genericTech = new HashMap<>();
        genericTech.put("name", "Problem Solving");
        genericTech.put("score", 4.0);
        genericTech.put("required_level", baselineRequired);
        fallback.add(genericTech);

        Map<String, Object> genericSoft = new HashMap<>();
        genericSoft.put("name", "Communication");
        genericSoft.put("score", 4.0);
        genericSoft.put("required_level", baselineRequired);
        fallback.add(genericSoft);

        return fallback;
    }

    private List<Map<String, Object>> toWeakRowsFromSkills(List<Skill> skills, int baselineRequired, int maxCount) {
        return skills.stream()
                .filter(skill -> hasText(skill.getNom()))
                .sorted(Comparator.comparingInt((Skill skill) -> skill.getNiveau() == null ? 1 : skill.getNiveau()))
                .limit(maxCount)
                .map(skill -> {
                    int niveau = skill.getNiveau() == null ? 1 : Math.max(1, Math.min(5, skill.getNiveau()));
                    double score = Math.round((niveau * 2.0) * 10.0) / 10.0;
                    int requiredLevel = Math.max(baselineRequired, (int) Math.ceil(score + 1.0));
                    requiredLevel = Math.min(10, requiredLevel);

                    Map<String, Object> row = new HashMap<>();
                    row.put("name", skill.getNom().trim());
                    row.put("score", score);
                    row.put("required_level", requiredLevel);
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> toWeakRowsFromLatestSoftPrediction(
            UUID candidateId,
            int baselineRequired,
            int maxCount) {
        return predictionRepository.findFirstByUserIdOrderByDatePredictionDesc(candidateId)
                .map(Prediction::getAnalyse)
                .map(this::extractSoftWeaknessesFromAnalyse)
                .orElse(List.of())
                .stream()
                .limit(maxCount)
                .map(name -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("name", name);
                    row.put("score", 3.0);
                    row.put("required_level", baselineRequired);
                    return row;
                })
                .toList();
    }

    private List<String> extractSoftWeaknessesFromAnalyse(String analyseText) {
        if (!hasText(analyseText)) {
            return List.of();
        }

        String topWeaknesses = extractSection(analyseText, "TOP_FAIBLESSES");
        String keyWeaknesses = extractSection(analyseText, "AXES_AMELIORATION");
        String source = hasText(topWeaknesses) ? topWeaknesses : keyWeaknesses;

        if (!hasText(source)) {
            return List.of();
        }

        return Arrays.stream(source.split(","))
                .map(String::trim)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private String extractSection(String text, String sectionName) {
        String marker = sectionName + ":\n";
        int start = text.indexOf(marker);
        if (start == -1) {
            return "";
        }

        start += marker.length();
        int end = text.indexOf("\n\n", start);
        String section = end == -1 ? text.substring(start) : text.substring(start, end);
        return section.trim();
    }

    private void mergeWeakSkillRows(Map<String, Map<String, Object>> target, List<Map<String, Object>> rows) {
        for (Map<String, Object> row : rows) {
            Object rawName = row.get("name");
            if (!hasText(rawName)) {
                continue;
            }

            String name = rawName.toString().trim();
            String key = name.toLowerCase(Locale.ROOT);
            if (target.containsKey(key)) {
                continue;
            }

            target.put(key, row);
        }
    }

    private int requiredLevelForExperience(String experienceLevel) {
        return switch (experienceLevel) {
            case "beginner" -> 5;
            case "junior" -> 7;
            case "mid" -> 8;
            case "senior" -> 9;
            default -> 7;
        };
    }

    private double parseHoursPerDay(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }

        try {
            double parsed = Double.parseDouble(value.toString().trim());
            if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
                return defaultValue;
            }
            return Math.max(0.5, Math.min(8.0, parsed));
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private String firstNonBlank(Object... values) {
        if (values == null) {
            return "";
        }

        for (Object value : values) {
            if (hasText(value)) {
                return value.toString().trim();
            }
        }

        return "";
    }

    private boolean hasText(Object value) {
        if (value == null) {
            return false;
        }
        String text = value.toString().trim();
        return !text.isEmpty();
    }

    private boolean hasItems(Object value) {
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new HashMap<>((Map<String, Object>) map);
        }
        return new HashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectToMap(JsonNode node) {
        if (node == null || node.isNull()) {
            return new HashMap<>();
        }
        return objectMapper.convertValue(node, Map.class);
    }

}
