package com.talentpredict.modules.ai.services;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpredict.modules.skills.dto.SkillDto;
import com.talentpredict.modules.skills.entities.Skill;
import com.talentpredict.modules.user.dto.ProfileDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Calls the TalentPredict Python AI service (Ollama/OpenRouter agent) to run
 * full profile analysis: GitHub + LinkedIn + CV, returning structured skills.
 * If the service URL is not set or the call fails, returns an empty list.
 */
@Service
@RequiredArgsConstructor
@Slf4j

public class PythonAiClient {

    @Value("${talentpredict.ai.base-url:}")
    private String baseUrl;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    /**
     * Extract GitHub username from profile URL (e.g. https://github.com/foo ->
     * foo).
     */
    public static String extractGithubUsername(String githubUrl) {
        if (githubUrl == null || githubUrl.isBlank())
            return null;
        String s = githubUrl.trim();
        if (s.endsWith("/"))
            s = s.substring(0, s.length() - 1);
        int last = s.lastIndexOf('/');
        return last >= 0 ? s.substring(last + 1) : s;
    }

    /**
     * Call Python AI /analyze-candidate with profile data (GitHub, LinkedIn).
     * Returns skills parsed from the JSON response, or empty list on failure.
     */
    public List<SkillDto.CreateRequest> analyzeProfile(ProfileDto.Response profile) {
        if (baseUrl == null || baseUrl.isBlank()) {
            log.debug("Python AI base URL not set, skipping full-profile analysis");
            return List.of();
        }

        String username = extractGithubUsername(profile.getGithubUrl());
        if (username == null || username.isBlank()) {
            log.debug("No GitHub URL in profile, skipping Python AI call");
            return List.of();
        }

        String url = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/analyze-candidate")
                .build()
                .toUriString();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("github", username);
        if (profile.getLienLinkedin() != null && !profile.getLienLinkedin().isBlank()) {
            form.add("linkedin_url", profile.getLienLinkedin().trim());
        }

        try {
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            org.springframework.http.HttpEntity<MultiValueMap<String, String>> request = new org.springframework.http.HttpEntity<>(
                    form, headers);

            String response = restTemplate.postForObject(url, request, String.class);
            if (response == null)
                return List.of();

            JsonNode root = objectMapper.readTree(response);
            if (root.has("error")) {
                log.warn("Python AI returned error: {}", root.get("error").asText());
                return List.of();
            }

            return parseSkillsFromResponse(root);
        } catch (java.io.IOException | RuntimeException e) {
            log.warn("Python AI call failed (service down or invalid response): {}", e.getMessage());
            return List.of();
        }
    }

    private List<SkillDto.CreateRequest> parseSkillsFromResponse(JsonNode root) {
        List<SkillDto.CreateRequest> list = new ArrayList<>();
        JsonNode skills = root.path("skills");
        if (!skills.isArray())
            return list;

        for (JsonNode s : skills) {
            if (!s.has("name"))
                continue;
            String name = s.get("name").asText().trim();
            if (name.isEmpty())
                continue;

            String levelStr = s.path("level").asText("Intermediate");
            int niveau = levelToNiveau(levelStr);

            SkillDto.CreateRequest req = new SkillDto.CreateRequest();
            req.setNom(name);
            req.setType(Skill.TypeSkill.TECH);
            req.setNiveau(niveau);
            req.setDescription(null);
            list.add(req);
        }
        log.info("Python AI returned {} skills", list.size());
        return list;
    }

    private static int levelToNiveau(String level) {
        if (level == null)
            return 3;
        return switch (level.toUpperCase()) {
            case "BEGINNER" -> 1;
            case "INTERMEDIATE" -> 2;
            case "ADVANCED" -> 3;
            case "EXPERT" -> 5;
            default -> 3;
        };
    }
}
