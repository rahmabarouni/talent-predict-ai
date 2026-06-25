package com.talentpredict.modules.ai.services;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpredict.modules.skills.dto.SkillDto;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service d'analyse des repositories GitHub d'un utilisateur.
 * Utilise l'API publique GitHub (gratuite, sans token pour les repos publics).
 * Limite: 60 requêtes/heure sans token (suffisant pour l'usage normal).
 */
@Service
@Slf4j
@RequiredArgsConstructor

public class GithubAnalysiService {

    private final OpenRouterService openRouterService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${github.token:}")
    private String githubToken;

    /**
     * Result containing both skills and GitHub profile stats.
     */
    @Data
    public static class GitHubAnalysisResult {
        private List<SkillDto.CreateRequest> skills;
        // GitHub user profile data
        private Integer publicRepos;
        private Integer followers;
        private Integer following;
        private String bio;
        private String company;
        private String location;
        private String avatarUrl;
        private String name;
    }

    // ================================================================
    //  POINT D'ENTRÉE : Analyse complète depuis l'URL GitHub
    // ================================================================

    public List<SkillDto.CreateRequest> extraireSkillsGitHub(String githubUrl) {
        return analyserGitHubComplet(githubUrl).getSkills();
    }

    public GitHubAnalysisResult analyserGitHubComplet(String githubUrl) {
        GitHubAnalysisResult result = new GitHubAnalysisResult();
        result.setSkills(List.of());

        try {
            String username = extraireUsername(githubUrl);
            if (username == null || username.isBlank()) {
                log.warn(" URL GitHub invalide ou username non trouvé: {}", githubUrl);
                return result;
            }

            log.info(" Analyse GitHub pour l'utilisateur: {}", username);

            // 1. Fetch user profile data (followers, repos count, bio, etc.)
            fetchUserProfile(username, result);

            // 2. Fetch repos and extract languages
            Map<String, Integer> langageCounts = recupererLangagesGitHub(username);

            if (langageCounts.isEmpty()) {
                log.warn(" Aucun langage trouvé pour GitHub user: {}", username);
                return result;
            }

            log.info("Langages GitHub détectés: {}", langageCounts);

            // 3. Convert languages to skills via LLM
            result.setSkills(openRouterService.extraireSkillsDepuisLanguages(langageCounts));

        } catch (Exception e) {
            log.error(" Erreur analyse GitHub pour {}: {}", githubUrl, e.getMessage());
        }

        return result;
    }

    // ================================================================
    //  FETCH GITHUB USER PROFILE
    // ================================================================

    private void fetchUserProfile(String username, GitHubAnalysisResult result) {
        try {
            String apiUrl = "https://api.github.com/users/" + username;

            HttpHeaders headers = buildGitHubHeaders();

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.GET, entity, String.class
            );

            JsonNode user = objectMapper.readTree(response.getBody());

            result.setPublicRepos(user.path("public_repos").asInt(0));
            result.setFollowers(user.path("followers").asInt(0));
            result.setFollowing(user.path("following").asInt(0));
            result.setBio(nullIfEmpty(user.path("bio").asText("")));
            result.setCompany(nullIfEmpty(user.path("company").asText("")));
            result.setLocation(nullIfEmpty(user.path("location").asText("")));
            result.setAvatarUrl(nullIfEmpty(user.path("avatar_url").asText("")));
            result.setName(nullIfEmpty(user.path("name").asText("")));

            log.info(" GitHub profil: {} | repos={}, followers={}, location={}",
                result.getName(), result.getPublicRepos(), result.getFollowers(), result.getLocation());

        } catch (java.io.IOException | RuntimeException e) {
            log.warn(" Impossible de récupérer le profil GitHub: {}", e.getMessage());
        }
    }

    private String nullIfEmpty(String value) {
        return (value == null || value.isBlank() || value.equals("null")) ? null : value;
    }

    // ================================================================
    //  APPEL API GITHUB : Récupère les langages des repos publics
    // ================================================================

    private Map<String, Integer> recupererLangagesGitHub(String username) {
        try {
            // API GitHub publique - 30 derniers repos mis à jour
            String apiUrl = "https://api.github.com/users/" + username
                + "/repos?per_page=30&sort=updated&type=public";

            HttpHeaders headers = buildGitHubHeaders();

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(
                apiUrl, HttpMethod.GET, entity, String.class
            );

            // Compter les occurrences de chaque langage
            Map<String, Integer> langageCounts = new LinkedHashMap<>();
            JsonNode repos = objectMapper.readTree(response.getBody());

            for (JsonNode repo : repos) {
                // Ignorer les forks (on veut les projets personnels)
                boolean isFork = repo.path("fork").asBoolean(false);
                if (isFork) continue;

                String language = repo.path("language").asText("");
                if (!language.isBlank() && !language.equals("null")) {
                    langageCounts.merge(language, 1, Integer::sum);
                }
            }

            return langageCounts;

        } catch (java.io.IOException | RuntimeException e) {
            log.error(" Erreur appel API GitHub: {}", e.getMessage());
            return Map.of();
        }
    }

    // ================================================================
    //  HELPER : Build GitHub API headers with optional token
    // ================================================================

    private HttpHeaders buildGitHubHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Accept", "application/vnd.github.v3+json");
        headers.set("User-Agent", "TalentPredict-App/1.0");
        if (githubToken != null && !githubToken.isBlank()) {
            headers.set("Authorization", "Bearer " + githubToken);
        }
        return headers;
    }

    // ================================================================
    //  HELPER : Extraire le username depuis différents formats d'URL
    // ================================================================

    private String extraireUsername(String githubUrl) {
        try {
            if (githubUrl == null) return null;

            // Nettoyer l'URL
            String cleaned = githubUrl.trim()
                .replace("https://", "")
                .replace("http://", "")
                .replace("www.", "");

            // Formats supportés:
            // github.com/username
            // github.com/username/
            // github.com/username/repo
            if (cleaned.startsWith("github.com/")) {
                cleaned = cleaned.substring("github.com/".length());
            }

            // Prendre seulement le premier segment (le username)
            String[] parts = cleaned.split("/");
            if (parts.length > 0 && !parts[0].isBlank()) {
                return parts[0].trim();
            }

            return null;
        } catch (Exception e) {
            log.error("Erreur extraction username GitHub depuis: {}", githubUrl);
            return null;
        }
    }
}