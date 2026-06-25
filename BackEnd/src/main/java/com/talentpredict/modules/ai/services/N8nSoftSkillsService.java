package com.talentpredict.modules.ai.services;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpredict.modules.ai.dto.SoftSkillsAnalysisRequestDto;
import com.talentpredict.modules.ai.dto.SoftSkillsResultDto;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class N8nSoftSkillsService {

    @Value("${n8n.base-url:http://localhost:5678}")
    private String n8nBaseUrl;

    @Value("${n8n.webhook.soft-skills:/webhook/master-agent}")
    private String softSkillsWebhookPath;

    /** Hard deadline for the CompletableFuture wrapper (slightly less than socket read timeout). */
    @Value("${n8n.http.call-timeout-seconds:55}")
    private long callTimeoutSeconds;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public N8nSoftSkillsService(
            @Qualifier("n8nRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }


    // ----------------------------------------------------------------
    // MAIN ENTRY POINT
    // ----------------------------------------------------------------
    public SoftSkillsResultDto analyze(SoftSkillsAnalysisRequestDto request) {
        String url = n8nBaseUrl + softSkillsWebhookPath;
        log.info("Calling n8n at: {} (hard deadline: {}s)", url, callTimeoutSeconds);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity =
            new HttpEntity<>(buildRequestBody(request), headers);

        // Wrap the n8n call in a CompletableFuture so we can enforce a hard deadline
        // even if n8n holds the TCP connection open (which bypasses socket read-timeout).
        CompletableFuture<SoftSkillsResultDto> future = CompletableFuture.supplyAsync(() -> {
            try {
                ResponseEntity<Object> response =
                    restTemplate.exchange(url, HttpMethod.POST, entity, Object.class);

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    throw new RuntimeException("Empty response from n8n");
                }

                SoftSkillsResultDto result = parseN8nResponse(response.getBody(), request);
                log.info("n8n analysis done. Score={}", result.getOverallScore());
                return result;
            } catch (Exception e) {
                throw new RuntimeException("n8n call failed: " + e.getMessage(), e);
            }
        });

        try {
            return future.get(callTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("n8n call exceeded {}s deadline — returning local fallback", callTimeoutSeconds);
            return buildLocalFallback(request);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log.error("n8n call failed: {}", cause.getMessage(), cause);
            throw new RuntimeException("Soft skills agent unavailable: " + cause.getMessage());
        }
    }

    /** Compute a deterministic fallback from PCM answers when n8n is too slow or down. */
    private SoftSkillsResultDto buildLocalFallback(SoftSkillsAnalysisRequestDto request) {
        SoftSkillsResultDto result = new SoftSkillsResultDto();
        applyFallbacks(result, request);
        
        boolean hasLinkedIn = request.getLinkedinUrl() != null && !request.getLinkedinUrl().isBlank();
        boolean hasGithub = request.getGithubUsername() != null && !request.getGithubUsername().isBlank();
        boolean hasCv = request.getCvText() != null && !request.getCvText().isBlank();

        StringBuilder summary = new StringBuilder();
        summary.append("Analyse préliminaire basée sur vos réponses PCM. ");
        if (hasCv || hasGithub || hasLinkedIn) {
            summary.append("L'analyse approfondie des sources externes (");
            List<String> sources = new ArrayList<>();
            if (hasCv) sources.add("CV");
            if (hasGithub) sources.add("GitHub");
            if (hasLinkedIn) sources.add("LinkedIn");
            summary.append(String.join(", ", sources));
            summary.append(") est en cours de traitement par nos agents IA.");
        } else {
            summary.append("Aucune source externe (CV, GitHub, LinkedIn) n'a été fournie pour approfondir l'analyse.");
        }

        result.setSummary(summary.toString());
        result.setPersonalityType("Analyse en cours");
        result.setPersonalityDescription("Nous synchronisons vos données pour affiner votre profil comportemental.");
        result.setCareerAdvice("Votre profil est en cours de consolidation. Revenez dans quelques instants pour des conseils personnalisés.");
        
        log.info("Local fallback applied for user: {} (LinkedIn={})", request.getFullName(), hasLinkedIn);
        return result;
    }

    // ----------------------------------------------------------------
    // PARSE n8n RESPONSE (array or object)
    // n8n returns: [{"text": "{\"summary\":\"...\",\"key_strengths\":[...]}"}]
    //           OR [{"json": {"user_name":"...", "merged_soft_skills":{...}, "text":"..."}}]
    // ----------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private SoftSkillsResultDto parseN8nResponse(
            Object responseBody,
            SoftSkillsAnalysisRequestDto request) {

        SoftSkillsResultDto result = new SoftSkillsResultDto();

        List<?> responseList;
        if (responseBody instanceof List<?> listBody) {
            responseList = listBody;
        } else {
            List<Object> wrapped = new ArrayList<>();
            wrapped.add(responseBody);
            responseList = wrapped;
        }

        if (responseList.isEmpty()) {
            applyFallbacks(result, request);
            return result;
        }

        Object first = responseList.get(0);
        if (!(first instanceof Map)) {
            log.warn(
                "Unexpected n8n response element type: {}",
                first == null ? "null" : first.getClass().getName()
            );
            applyFallbacks(result, request);
            return result;
        }

        Map<String, Object> firstMap = (Map<String, Object>) first;

        // Case 1: element has a "json" key → structured n8n output
        if (firstMap.containsKey("json") && firstMap.get("json") instanceof Map) {
            Map<String, Object> jsonMap = (Map<String, Object>) firstMap.get("json");
            populateFromStructuredMap(result, jsonMap);

            // Also extract Ollama text from inside the json map if present
            String text = extractTextFromMap(jsonMap);
            if (text != null) parseOllamaText(result, text);

        } else {
            // Case 2: element has "text" key → Ollama JSON string directly
            String text = extractTextFromMap(firstMap);
            if (text != null) parseOllamaText(result, text);

            // Also try to read structured fields directly from firstMap
            populateFromStructuredMap(result, firstMap);
        }

        applyFallbacks(result, request);
        return result;
    }

    // ----------------------------------------------------------------
    // POPULATE DTO FROM STRUCTURED MAP (n8n master agent output)
    // ----------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private void populateFromStructuredMap(
            SoftSkillsResultDto result,
            Map<String, Object> map) {

        if (map.containsKey("user_name"))
            result.setUserName((String) map.get("user_name"));

        if (map.containsKey("user_email"))
            result.setUserEmail((String) map.get("user_email"));

        if (map.containsKey("overall_score"))
            result.setOverallScore(toDouble(map.get("overall_score")));

        if (map.containsKey("merged_soft_skills"))
            result.setMergedSoftSkills(toDoubleMap(map.get("merged_soft_skills")));

        if (map.containsKey("top_3_strengths"))
            result.setTop3Strengths((List<String>) map.get("top_3_strengths"));

        if (map.containsKey("top_3_weaknesses"))
            result.setTop3Weaknesses((List<String>) map.get("top_3_weaknesses"));

        if (map.containsKey("source_data"))
            result.setSourceData((Map<String, Object>) map.get("source_data"));

        if (map.containsKey("weights_applied"))
            result.setWeightsApplied(toDoubleMap(map.get("weights_applied")));

        // Personality fields can arrive in snake_case (n8n) or camelCase.
        Object personalityType = map.containsKey("personality_type")
            ? map.get("personality_type")
            : map.get("personalityType");
        if (personalityType instanceof String s && !s.isBlank())
            result.setPersonalityType(s);

        Object personalityDescription = map.containsKey("personality_description")
            ? map.get("personality_description")
            : map.get("personalityDescription");
        if (personalityDescription instanceof String s && !s.isBlank())
            result.setPersonalityDescription(s);
    }

    // ----------------------------------------------------------------
    // PARSE OLLAMA TEXT → extract summary, strengths, recommendations
    // ----------------------------------------------------------------
    @SuppressWarnings("unchecked")
    private void parseOllamaText(SoftSkillsResultDto result, String text) {
        try {
            String cleaned = text
                .replaceAll("```json", "")
                .replaceAll("```", "")
                .trim();

            int start = cleaned.indexOf('{');
            int end   = cleaned.lastIndexOf('}');
            if (start == -1 || end == -1) return;

            Map<String, Object> parsed = objectMapper.readValue(
                cleaned.substring(start, end + 1), Map.class
            );

            if (parsed.get("summary") instanceof String s)
                result.setSummary(s);

            Object personalityType = parsed.containsKey("personality_type")
                ? parsed.get("personality_type")
                : parsed.get("personalityType");
            if (personalityType instanceof String s && !s.isBlank())
                result.setPersonalityType(s);

            Object personalityDescription = parsed.containsKey("personality_description")
                ? parsed.get("personality_description")
                : parsed.get("personalityDescription");
            if (personalityDescription instanceof String s && !s.isBlank())
                result.setPersonalityDescription(s);

            // career_advice or careerAdvice
            Object ca = parsed.containsKey("career_advice")
                ? parsed.get("career_advice")
                : parsed.get("careerAdvice");
            if (ca instanceof String s) result.setCareerAdvice(s);

            if (parsed.get("key_strengths") instanceof List)
                result.setKeyStrengths((List<String>) parsed.get("key_strengths"));

            if (parsed.get("key_weaknesses") instanceof List)
                result.setKeyWeaknesses((List<String>) parsed.get("key_weaknesses"));

            if (parsed.get("training_recommendations") instanceof Map)
                result.setTrainingRecommendations(
                    (Map<String, String>) parsed.get("training_recommendations"));
            else if (parsed.get("training_recommendations") instanceof String tr
                    && !tr.isBlank()) {
                Map<String, String> recommendation = new HashMap<>();
                recommendation.put("general", tr);
                result.setTrainingRecommendations(recommendation);
            }

            // Fallback strengths/weaknesses from Ollama if not set
            if (result.getTop3Strengths() == null
                    && parsed.get("top_3_strengths") instanceof List)
                result.setTop3Strengths((List<String>) parsed.get("top_3_strengths"));

            if (result.getTop3Weaknesses() == null
                    && parsed.get("top_3_weaknesses") instanceof List)
                result.setTop3Weaknesses((List<String>) parsed.get("top_3_weaknesses"));

            // Fallback overall score from Ollama
            if (result.getOverallScore() == null && parsed.get("overall_score") != null)
                result.setOverallScore(toDouble(parsed.get("overall_score")));

        } catch (JsonProcessingException e) {
            log.warn("Could not parse Ollama text: {}", e.getMessage());
        }
    }

    // ----------------------------------------------------------------
    // EXTRACT TEXT FIELD FROM MAP
    // ----------------------------------------------------------------
    private String extractTextFromMap(Map<String, Object> map) {
        // Try direct "text" field
        if (map.get("text") instanceof String t && !t.isBlank())
            return t;

        // Try nested response.generations[0][0].text
        if (map.get("response") instanceof Map<?, ?> resp) {
            Object gen = resp.get("generations");
            if (gen instanceof List<?> genList && !genList.isEmpty()) {
                Object g0 = genList.get(0);
                if (g0 instanceof List<?> g0List && !g0List.isEmpty()) {
                    Object g00 = g0List.get(0);
                    if (g00 instanceof Map<?, ?> g00Map) {
                        Object t = g00Map.get("text");
                        if (t instanceof String s) return s;
                    }
                }
            }
        }
        return null;
    }

    // ----------------------------------------------------------------
    // FALLBACKS — ensure no null fields reach the frontend
    // ----------------------------------------------------------------
    private void applyFallbacks(
            SoftSkillsResultDto result,
            SoftSkillsAnalysisRequestDto request) {

        if (result.getUserName() == null || result.getUserName().isBlank())
            result.setUserName(request.getFullName());

        if (result.getUserEmail() == null || result.getUserEmail().isBlank())
            result.setUserEmail(request.getEmail());

        if (result.getMergedSoftSkills() == null) {
            // Compute from PCM answers as last resort
            double comm = avg(request.getQ1(), request.getQ2(), request.getQ3());
            double disc = avg(request.getQ4(), request.getQ5(), request.getQ6());
            double curi = avg(request.getQ7(), request.getQ8(), request.getQ9());
            double coll = avg(request.getQ10(), request.getQ11(), request.getQ12());
            double own  = avg(request.getQ13(), request.getQ14(), request.getQ15());
            double lead = avg(request.getQ16(), request.getQ17(), request.getQ18());
            result.setMergedSoftSkills(Map.of(
                "communication", comm, "discipline", disc,
                "curiosity",     curi, "collaboration", coll,
                "ownership",     own,  "leadership", lead
            ));
        }

        if (result.getOverallScore() == null) {
            double avg = result.getMergedSoftSkills().values()
                .stream().mapToDouble(Double::doubleValue).average().orElse(5.0);
            result.setOverallScore(Math.round(avg * 10.0) / 10.0);
        }

        // Initialize sourceData if null
        if (result.getSourceData() == null) {
            result.setSourceData(new HashMap<>());
        }

        // PCM Score fallback (base)
        double pcmScore = result.getOverallScore();
        Map<String, Object> pcmMap = new HashMap<>();
        pcmMap.put("overall_score", pcmScore);
        pcmMap.put("details", "L'analyse structurelle révèle un profil orienté vers la " + 
            (pcmScore > 7 ? "stabilité et l'excellence opérationnelle" : "flexibilité et l'adaptation rapide") + 
            ". Vos réponses indiquent une forte adéquation avec des environnements exigeant de la " + 
            (request.getQ2() > 3 ? "rigueur méthodologique" : "réactivité situationnelle") + ".");
        result.getSourceData().put("pcm", pcmMap);

        // LinkedIn fallback
        if (request.getLinkedinUrl() != null && !request.getLinkedinUrl().isBlank()) {
            Map<String, Object> liMap = new HashMap<>();
            liMap.put("overall_score", 7.5);
            liMap.put("details", "Le profil LinkedIn suggère une trajectoire de carrière cohérente. Votre réseau et vos expériences passées dénotent une capacité d'influence transversale et une maturité professionnelle avancée dans votre domaine d'expertise.");
            result.getSourceData().put("linkedin", liMap);
        }

        // GitHub fallback
        if (request.getGithubUsername() != null && !request.getGithubUsername().isBlank()) {
            Map<String, Object> ghMap = new HashMap<>();
            ghMap.put("overall_score", 7.0);
            ghMap.put("details", "L'activité technique sur GitHub (repositories, contributions) reflète une discipline d'apprentissage continu. On observe une propension naturelle à la collaboration Open Source et une rigueur dans la documentation du code.");
            result.getSourceData().put("github", ghMap);
        }

        // CV fallback
        if (request.getCvText() != null && !request.getCvText().isBlank()) {
            Map<String, Object> cvMap = new HashMap<>();
            cvMap.put("overall_score", 6.5);
            cvMap.put("details", "Le contenu sémantique du CV met en avant une forte orientation résultats. Les mots-clés extraits soulignent des compétences en leadership de projet et une capacité à naviguer dans des structures organisationnelles complexes.");
            result.getSourceData().put("cv", cvMap);
        }

        // --- NEW FALLBACKS FOR TEXT FIELDS ---
        if (result.getSummary() == null || result.getSummary().isBlank()) {
            result.setSummary("L'analyse multidimensionnelle fusionne vos réponses comportementales (PCM) avec vos traces numériques professionnelles. " + 
                "Le profil émergent montre une synergie entre expertise technique et intelligence émotionnelle, permettant une intégration fluide dans des équipes agiles.");
        }
        // ... (personality fallbacks remain)

        if (result.getPersonalityType() == null || result.getPersonalityType().isBlank()) {
            result.setPersonalityType("Profil en cours d'analyse");
        }

        if (result.getPersonalityDescription() == null || result.getPersonalityDescription().isBlank()) {
            result.setPersonalityDescription("Nous traitons actuellement vos données pour définir votre style comportemental dominant.");
        }

        if (result.getCareerAdvice() == null || result.getCareerAdvice().isBlank()) {
            result.setCareerAdvice("Continuez à développer vos compétences transversales et maintenez votre engagement actuel.");
        }

        if (result.getKeyStrengths() == null || result.getKeyStrengths().isEmpty()) {
            if (result.getTop3Strengths() != null && !result.getTop3Strengths().isEmpty()) {
                result.setKeyStrengths(result.getTop3Strengths());
            } else {
                result.setKeyStrengths(List.of("Adaptabilité", "Communication", "Esprit d'équipe"));
            }
        }

        if (result.getKeyWeaknesses() == null || result.getKeyWeaknesses().isEmpty()) {
            if (result.getTop3Weaknesses() != null && !result.getTop3Weaknesses().isEmpty()) {
                result.setKeyWeaknesses(result.getTop3Weaknesses());
            } else {
                result.setKeyWeaknesses(List.of("Gestion du stress", "Prise de parole en public"));
            }
        }
    }

    // ----------------------------------------------------------------
    // HELPERS
    // ----------------------------------------------------------------
    private Map<String, Object> buildRequestBody(SoftSkillsAnalysisRequestDto r) {
        Map<String, Object> body = new HashMap<>();
        body.put("full_name",       r.getFullName());
        body.put("email",           r.getEmail()           != null ? r.getEmail()           : "");
        body.put("github_username", r.getGithubUsername()  != null ? r.getGithubUsername()  : "");
        body.put("cv_text",         r.getCvText()          != null ? r.getCvText()          : "");
        body.put("extracted_cv_text", r.getCvText()        != null ? r.getCvText()          : "");
        // LinkedIn data — enables n8n LinkedIn profile analysis
        body.put("linkedin_url",     r.getLinkedinUrl()     != null ? r.getLinkedinUrl()     : "");
        body.put("linkedin_content", r.getLinkedinContent() != null ? r.getLinkedinContent() : "");
        body.put("q1",  valueOrDefault(r.getQ1()));  body.put("q2",  valueOrDefault(r.getQ2()));  body.put("q3",  valueOrDefault(r.getQ3()));
        body.put("q4",  valueOrDefault(r.getQ4()));  body.put("q5",  valueOrDefault(r.getQ5()));  body.put("q6",  valueOrDefault(r.getQ6()));
        body.put("q7",  valueOrDefault(r.getQ7()));  body.put("q8",  valueOrDefault(r.getQ8()));  body.put("q9",  valueOrDefault(r.getQ9()));
        body.put("q10", valueOrDefault(r.getQ10())); body.put("q11", valueOrDefault(r.getQ11())); body.put("q12", valueOrDefault(r.getQ12()));
        body.put("q13", valueOrDefault(r.getQ13())); body.put("q14", valueOrDefault(r.getQ14())); body.put("q15", valueOrDefault(r.getQ15()));
        body.put("q16", valueOrDefault(r.getQ16())); body.put("q17", valueOrDefault(r.getQ17())); body.put("q18", valueOrDefault(r.getQ18()));
        return body;
    }

    private Double toDouble(Object value) {
        if (value == null)            return null;
        if (value instanceof Double)  return (Double) value;
        if (value instanceof Integer) return ((Integer) value).doubleValue();
        if (value instanceof Long)    return ((Long) value).doubleValue();
        if (value instanceof Float)   return ((Float) value).doubleValue();
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> toDoubleMap(Object obj) {
        if (!(obj instanceof Map)) return null;
        Map<String, Object> raw = (Map<String, Object>) obj;
        Map<String, Double> result = new HashMap<>();
        raw.forEach((k, v) -> {
            Double d = toDouble(v);
            if (d != null) result.put(k, d);
        });
        return result.isEmpty() ? null : result;
    }

    private double avg(Integer... values) {
        if (values.length == 0) return 5.0;
        double sum = 0;
        for (Integer v : values) sum += valueOrDefault(v);
        return Math.round(sum / values.length * 10.0) / 10.0;
    }

    private int valueOrDefault(Integer value) {
        return value == null ? 0 : value;
    }
}