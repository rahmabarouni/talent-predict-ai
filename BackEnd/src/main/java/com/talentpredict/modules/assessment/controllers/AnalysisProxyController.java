package com.talentpredict.modules.assessment.controllers;

import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpredict.modules.assessment.services.TalentPredictAiProxyService;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.shared.security.UserDetailsImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/analysis")
@RequiredArgsConstructor
@Slf4j
public class AnalysisProxyController {

    private final TalentPredictAiProxyService aiProxyService;
    private final ObjectMapper objectMapper;
    private final WebClient talentPredictAiWebClient;

    @Value("${talentpredict.ai.request-timeout-seconds:120}")
    private long requestTimeoutSeconds;

    @PostMapping("/github-deep")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JsonNode> githubDeep(
            @RequestBody JsonNode body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        assertCandidate(principal.getUser(), body);
        return ResponseEntity.ok(aiProxyService.postJson("/api/analysis/github-deep", objectToMap(body)));
    }






    /**
     * Proxy for the AI candidate profile analysis.
     * Accepts multipart/form-data (github, portfolio, cv_file, linkedin_url, linkedin_content)
     * and forwards it to the Python /analyze-candidate endpoint.
     */
    @PostMapping(value = "/analyze-candidate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JsonNode> analyzeCandidate(
            @RequestParam("github") String github,
            @RequestParam(value = "portfolio", required = false, defaultValue = "") String portfolio,
            @RequestParam(value = "cv_file", required = false) MultipartFile cvFile,
            @RequestParam(value = "linkedin_url", required = false, defaultValue = "") String linkedinUrl,
            @RequestParam(value = "linkedin_content", required = false, defaultValue = "") String linkedinContent) {
        try {
            MultiValueMap<String, Object> formData = new LinkedMultiValueMap<>();
            formData.add("github", github);
            formData.add("portfolio", portfolio);
            formData.add("linkedin_url", linkedinUrl);
            formData.add("linkedin_content", linkedinContent);
            if (cvFile != null && !cvFile.isEmpty()) {
                final byte[] bytes = cvFile.getBytes();
                final String filename = cvFile.getOriginalFilename() != null ? cvFile.getOriginalFilename() : "cv.pdf";
                ByteArrayResource resource = new ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() { return filename; }
                };
                formData.add("cv_file", resource);
            }
            String json = talentPredictAiWebClient.post()
                    .uri("/analyze-candidate")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(formData))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(java.time.Duration.ofSeconds(Math.max(10, requestTimeoutSeconds)));
            if (json == null || json.isBlank()) {
                return ResponseEntity.ok(objectMapper.createObjectNode());
            }
            return ResponseEntity.ok(objectMapper.readTree(json));
        } catch (org.springframework.web.reactive.function.client.WebClientResponseException e) {
            log.warn("AI analyze-candidate error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new org.springframework.web.server.ResponseStatusException(e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("AI analyze-candidate proxy failed: {}", e.getMessage());
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY, "AI service unavailable", e);
        }
    }

    private void assertCandidate(User auth, JsonNode body) {
        if (!body.hasNonNull("candidate_id")) {
            return;
        }
        UUID cid = UUID.fromString(body.get("candidate_id").asText());
        if (auth.getRole() == User.Role.ADMIN) {
            return;
        }
        if (!auth.getId().equals(cid)) {
            throw new org.springframework.security.access.AccessDeniedException("candidate_id mismatch");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> objectToMap(JsonNode node) {
        return objectMapper.convertValue(node, Map.class);
    }
}
