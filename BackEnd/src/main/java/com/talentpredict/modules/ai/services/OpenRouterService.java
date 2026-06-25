package com.talentpredict.modules.ai.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpredict.modules.skills.dto.SkillDto;
import com.talentpredict.modules.skills.entities.Skill;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service principal d'appel à Claude via OpenRouter API.
 * OpenRouter utilise le même format qu'OpenAI (compatible).
 * URL: https://openrouter.ai/api/v1/chat/completions
 */
@Service
@Slf4j
@RequiredArgsConstructor

public class OpenRouterService {

    @Value("${openrouter.apikey:}")
    private String apiKey;

    @Value("${openrouter.api.url:https://openrouter.ai/api/v1/chat/completions}")
    private String apiUrl;

    @Value("${openrouter.model:anthropic/claude-sonnet-4-5}")
    private String model;

    @Value("${openrouter.max-retries:2}")
    private int maxRetries;

    @Value("${openrouter.backoff-base-ms:800}")
    private long backoffBaseMs;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private void backoff(int attempt) {
        long base = Math.max(100L, backoffBaseMs);
        long delayMillis = Math.min(5000L, base * attempt);
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(delayMillis));
    }

    // ================================================================
    //  MÉTHODE CENTRALE : Envoyer un prompt à Claude via OpenRouter
    // ================================================================

    public String executePrompt(String prompt) {
        int attempts = Math.max(1, maxRetries);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                // 1. Headers HTTP requis par OpenRouter
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("Authorization", "Bearer " + apiKey);
                headers.set("HTTP-Referer", "https://talentpredict.app");
                headers.set("X-Title", "TalentPredict");

                // 2. Body de la requête (format OpenAI-compatible)
                Map<String, Object> message = new HashMap<>();
                message.put("role", "user");
                message.put("content", prompt);

                Map<String, Object> body = new HashMap<>();
                body.put("model", model);
                body.put("max_tokens", 1500);
                body.put("temperature", 0.3);
                body.put("messages", List.of(message));

                // 3. Envoi de la requête
                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
                ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

                // Check for error in response body (Ollama CUDA errors)
                String responseBody = response.getBody();
                if (responseBody == null || responseBody.isBlank()) {
                    log.warn("LLM returned empty body (attempt {}/{})", attempt, attempts);
                    if (attempt < attempts) {
                        backoff(attempt);
                        continue;
                    }
                    return null;
                }
                if (responseBody != null && responseBody.contains("\"error\"")) {
                    JsonNode errorCheck = objectMapper.readTree(responseBody);
                    if (errorCheck.has("error")) {
                        String errMsg = errorCheck.path("error").path("message").asText("unknown error");
                        log.warn("LLM returned error (attempt {}/{}): {}", attempt, attempts, errMsg);
                        if (attempt < attempts) {
                            backoff(attempt);
                            continue;
                        }
                        return null;
                    }
                }

                // 4. Extraction du contenu depuis la réponse
                JsonNode root = objectMapper.readTree(responseBody);
                String content = root
                    .path("choices").get(0)
                    .path("message")
                    .path("content")
                    .asText();

                log.info("LLM a repondu ({} caracteres, attempt {})", content.length(), attempt);
                return content;

            } catch (java.io.IOException | RuntimeException e) {
                log.error("Erreur appel LLM (attempt {}/{}): {}", attempt, attempts, e.getMessage());
                if (attempt < attempts) {
                    backoff(attempt);
                }
            }
        }
        log.error("LLM: echec apres {} tentatives", attempts);
        return null;
    }

    // ================================================================
    //  ANALYSE CV (texte extrait du PDF)
    // ================================================================

    public List<SkillDto.CreateRequest> extraireSkillsDuTexteCV(String texteCV) {
        if (texteCV == null || texteCV.isBlank()) return List.of();
        FullProfileExtraction extraction = extraireProfilCompletDuTexteCV(texteCV);
        return extraction.getSkills();
    }

    /**
     * DTO interne pour porter l'extraction complète du CV
     */
    @lombok.Data
    public static class FullProfileExtraction {
        private String titreProfessionnel;
        private String description;
        private Integer experienceAns;
        private List<SkillDto.CreateRequest> skills = new ArrayList<>();
    }

    /**
     * Analyse complète du CV pour remplir le profil utilisateur + skills
     */
    public FullProfileExtraction extraireProfilCompletDuTexteCV(String texteCV) {
        FullProfileExtraction result = new FullProfileExtraction();
        if (texteCV == null || texteCV.isBlank()) return result;

        String texteLimite = texteCV.length() > 4000 ? texteCV.substring(0, 4000) : texteCV;

        String prompt = """
            Tu es un expert RH et recruteur technique de haut niveau.
            Voici le contenu textuel d'un CV:
            ---
            %s
            ---
            
            Analyse ce CV et extrais les informations suivantes pour un profil professionnel.
            
            Réponds UNIQUEMENT avec ce JSON exact, sans texte avant ou après, sans markdown:
            {
              "titreProfessionnel": "Développeur Fullstack Senior",
              "description": "Expert en Java/Spring et React avec 8 ans d'expérience dans le secteur bancaire...",
              "experienceAns": 8,
              "skills": [
                {
                  "nom": "Java",
                  "type": "TECH",
                  "niveau": 4,
                  "description": "Maîtrise de Spring Boot et architectures microservices"
                },
                {
                  "nom": "Leadership",
                  "type": "SOFT",
                  "niveau": 3,
                  "description": "Gestion d'une équipe de 5 développeurs"
                }
              ]
            }
            
            Règles STRICTES:
            1. titreProfessionnel: Le titre de poste actuel ou principal (court, max 60 chars).
            2. description: Une bio professionnelle captivante basée sur son parcours (entre 150 et 400 chars).
            3. experienceAns: Nombre total d'années d'expérience cumulées (entier).
            4. skills:
               - type: exactement "TECH" ou "SOFT"
               - niveau: entier entre 1 et 5
               - Maximum 15 skills au total
            5. Si une info est manquante, mets null ou une liste vide.
            """.formatted(texteLimite);

        String response = executePrompt(prompt);
        if (response == null || response.isBlank()) return result;

        try {
            String json = response.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();
            JsonNode root = objectMapper.readTree(json);
            
            result.setTitreProfessionnel(root.path("titreProfessionnel").asText(null));
            result.setDescription(root.path("description").asText(null));
            if (root.has("experienceAns") && !root.get("experienceAns").isNull()) {
                result.setExperienceAns(root.path("experienceAns").asInt());
            }
            
            JsonNode skillsNode = root.path("skills");
            if (skillsNode.isArray()) {
                List<SkillDto.CreateRequest> skillsList = new ArrayList<>();
                for (JsonNode node : skillsNode) {
                    try {
                        SkillDto.CreateRequest skill = new SkillDto.CreateRequest();
                        skill.setNom(node.path("nom").asText(""));
                        skill.setType(com.talentpredict.modules.skills.entities.Skill.TypeSkill.valueOf(
                            node.path("type").asText("TECH").toUpperCase()
                        ));
                        int niveau = node.path("niveau").asInt(1);
                        skill.setNiveau(Math.max(1, Math.min(5, niveau)));
                        skill.setDescription(node.path("description").asText("Extrait du CV"));
                        
                        if (!skill.getNom().isBlank()) {
                            skillsList.add(skill);
                        }
                    } catch (Exception e) {
                        log.warn("Erreur parsing un skill du CV: {}", e.getMessage());
                    }
                }
                result.setSkills(skillsList);
            }
        } catch (Exception e) {
            log.error("Erreur parsing extraction complète CV: {}", e.getMessage());
            // Fallback to basic skill parsing if full parse fails
            result.setSkills(parseSkillsFromJson(response));
        }
        
        return result;
    }

    // ================================================================
    //  ANALYSE GITHUB (langages depuis les repos)
    // ================================================================

    public List<SkillDto.CreateRequest> extraireSkillsDepuisLanguages(Map<String, Integer> langageCounts) {
        if (langageCounts == null || langageCounts.isEmpty()) return List.of();

        // Formate la liste: "Java: 8 repos, Python: 3 repos, ..."
        StringBuilder langagesStr = new StringBuilder();
        langageCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(e -> langagesStr.append(e.getKey()).append(": ").append(e.getValue()).append(" repos\n"));

        String prompt = """
            Un développeur a utilisé ces langages/technologies dans ses projets GitHub:
            ---
            %s
            ---
            
            Pour chaque langage, déduis le niveau de maîtrise basé sur le nombre de repos.
            Ajoute aussi les frameworks/outils couramment associés si justifié par le volume.
            
            Réponds UNIQUEMENT avec ce JSON exact, sans texte avant ou après, sans markdown:
            {
              "skills": [
                {
                  "nom": "JavaScript",
                  "type": "TECH",
                  "niveau": 4,
                  "description": "Détecté via GitHub - 12 repositories"
                }
              ]
            }
            
            Règles STRICTES:
            - type: toujours "TECH" (GitHub = compétences techniques)
            - niveau basé sur: 1 repo=1, 2-3=2, 4-6=3, 7-10=4, 11+ repos=5
            - Maximum 12 skills
            """.formatted(langagesStr.toString());

        String response = executePrompt(prompt);
        return parseSkillsFromJson(response);
    }

    // ================================================================
    //  ANALYSE TEST PCM (soft skills depuis l'analyse de personnalité)
    // ================================================================

    public List<SkillDto.CreateRequest> extraireSkillsDuPCM(String analysePCM) {
        if (analysePCM == null || analysePCM.isBlank()) return List.of();

        String prompt = """
            Tu es un expert en développement RH et psychologie du travail.
            
            Voici l'analyse de personnalité PCM d'un employé:
            ---
            %s
            ---
            
            Basé sur ce profil de personnalité, identifie les soft skills naturels de cette personne
            (ceux qui découlent directement de son profil psychologique).
            
            Réponds UNIQUEMENT avec ce JSON exact, sans texte avant ou après, sans markdown:
            {
              "skills": [
                {
                  "nom": "Empathie",
                  "type": "SOFT",
                  "niveau": 4,
                  "description": "Profil Empathique PCM - forte capacité d'écoute active"
                }
]
            }
            
            Règles STRICTES:
            - type: toujours "SOFT" (PCM = soft skills uniquement)
            - niveau entre 1 et 5, basé sur l'intensité du trait dans le profil
            - Maximum 8 skills, uniquement ceux directement liés au profil PCM
            """.formatted(analysePCM);

        String response = executePrompt(prompt);
        return parseSkillsFromJson(response);
    }

    // ================================================================
    //  HELPER INTERNE : Parser le JSON retourné par Claude
    // ================================================================

    public List<SkillDto.CreateRequest> parseSkillsFromJson(String json) {
        if (json == null || json.isBlank()) {
            log.warn("Reponse Claude vide ou null");
            return List.of();
        }

        try {
            // Nettoyage du JSON (Claude peut parfois ajouter des ```json ... ```)
            json = json.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

            if (json.isEmpty()) {
                log.warn("Reponse Claude vide apres nettoyage");
                return List.of();
            }

            JsonNode root = objectMapper.readTree(json);
            JsonNode skillsNode = root.path("skills");

            if (skillsNode.isMissingNode() || !skillsNode.isArray()) {
                log.warn("JSON skills invalide reçu de Claude: {}", json.substring(0, Math.min(200, json.length())));
                return List.of();
            }

            List<SkillDto.CreateRequest> result = new ArrayList<>();
            for (JsonNode node : skillsNode) {
                try {
                    SkillDto.CreateRequest skill = new SkillDto.CreateRequest();
                    skill.setNom(node.path("nom").asText(""));
                    skill.setType(Skill.TypeSkill.valueOf(
                        node.path("type").asText("TECH").toUpperCase()
                    ));
                    int niveau = node.path("niveau").asInt(1);
                    skill.setNiveau(Math.max(1, Math.min(5, niveau))); // Garantit entre 1 et 5
                    skill.setDescription(node.path("description").asText("Détecté par analyse IA"));

                    if (!skill.getNom().isBlank()) {
                        result.add(skill);
                    }
                } catch (RuntimeException e) {
                    log.warn(" Skill ignoré lors du parsing: {} - {}", node, e.getMessage());
                }
            }

            log.info("{} skills extraits depuis la réponse Claude", result.size());
            return result;

        } catch (java.io.IOException | RuntimeException e) {
            log.error("Erreur parsing JSON Claude: {} | JSON reçu: {}",
                e.getMessage(),
                json.substring(0, Math.min(300, json.length()))
            );
            return List.of();
        }
    }
}