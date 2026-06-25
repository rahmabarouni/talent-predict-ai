package com.talentpredict.modules.assessment.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.talentpredict.modules.assessment.services.CareerOrchestrationService;
import com.talentpredict.shared.security.UserDetailsImpl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/career")
@RequiredArgsConstructor
@Slf4j
public class CareerController {

    private final CareerOrchestrationService careerOrchestrationService;

    @PostMapping("/learning-plan")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<JsonNode> generateLearningPlan(
            @RequestBody(required = false) JsonNode body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
            
        if (principal == null || principal.getUser() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated");
        }
        
        JsonNode plan = careerOrchestrationService.generateLearningPlan(body, principal.getUser());
        return ResponseEntity.ok(plan);
    }
}
