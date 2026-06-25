package com.talentpredict.modules.ai.controllers;

import java.util.List;
import java.util.UUID;

import com.talentpredict.modules.ai.dto.PredictionDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.talentpredict.modules.ai.services.PredictionService;

import lombok.RequiredArgsConstructor;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/predictions")
@RequiredArgsConstructor
@Slf4j
public class PredictionController {
    
    private final PredictionService predictionService;
    
    @PostMapping("/users/{userId}/generer")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<PredictionDto.Response> genererPrediction(@PathVariable String userId) {
        try {
            PredictionDto.Response prediction = predictionService.genererPrediction(java.util.UUID.fromString(userId));
            return ResponseEntity.ok(prediction);
        } catch (Exception e) {
            log.error("Failed to generate prediction for user {}: {}", userId, e.getMessage(), e);
            throw e;
        }
    }


    @GetMapping("/users/{userId}")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<PredictionDto.Response>> getPredictionsByAccount(@PathVariable UUID userId) {
        List<PredictionDto.Response> predictions = predictionService.getPredictionsByUser(userId);
        return ResponseEntity.ok(predictions);
    }
    
    @GetMapping("/users/{userId}/derniere")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<PredictionDto.Response> getDernierePrediction(@PathVariable UUID userId) {
        PredictionDto.Response prediction = predictionService.getDernierePrediction(userId);
        if (prediction != null) {
            return ResponseEntity.ok(prediction);
        }
        return ResponseEntity.noContent().build();
    }
}
