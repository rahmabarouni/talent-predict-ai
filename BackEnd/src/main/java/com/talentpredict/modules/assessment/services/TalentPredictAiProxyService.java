package com.talentpredict.modules.assessment.services;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j

public class TalentPredictAiProxyService {

    private final WebClient talentPredictAiWebClient;
    private final ObjectMapper objectMapper;

    /**
     * Timeout for AI service (set to 30s for a smooth user experience)
     */
    private static final long AI_TIMEOUT_SECONDS = 30;

    public JsonNode postJson(String path, Object body) {
        try {
            String json = talentPredictAiWebClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body == null ? Map.of() : body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(AI_TIMEOUT_SECONDS))
                    .block();
            
            if (json == null || json.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(json);
        } catch (WebClientResponseException e) {
            log.warn("AI service error: {} {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ResponseStatusException(e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (RuntimeException e) {
            if (e.getCause() instanceof TimeoutException || e.getMessage().contains("Timeout")) {
                log.error("AI service timeout after {}s", AI_TIMEOUT_SECONDS);
                // Return 503 Service Unavailable with retry suggestion
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, 
                    "{\"error\": \"AI service unavailable\", \"retryAfter\": 30}");
            }
            log.error("AI proxy failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unavailable", e);
        } catch (java.io.IOException e) {
            log.error("AI mapping failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Data processing error", e);
        }
    }
}
