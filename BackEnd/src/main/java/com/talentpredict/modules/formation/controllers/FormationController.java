package com.talentpredict.modules.formation.controllers;

import java.util.UUID;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.talentpredict.modules.formation.dto.FormationDto;
import com.talentpredict.modules.formation.entities.Formation;
import com.talentpredict.modules.formation.services.FormationService;
import com.talentpredict.shared.security.UserDetailsImpl;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/formations")
@RequiredArgsConstructor
public class FormationController {
    
    private final FormationService formationService;
    
    @PostMapping("/utilisateur/{userId}")
    @PreAuthorize("#userId == principal.user.id or hasRole('ADMIN')")
    public ResponseEntity<FormationDto.FormationResponse> createFormation(
            @PathVariable UUID userId,
            @RequestBody FormationDto.FormationRequest request) {
        FormationDto.FormationResponse response = formationService.creerFormation(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/utilisateur/{userId}")
    @PreAuthorize("#userId == principal.user.id or hasRole('ADMIN')")
    public ResponseEntity<List<FormationDto.FormationResponse>> getFormationsByUser(@PathVariable UUID userId) {
        List<FormationDto.FormationResponse> formations = formationService.getFormationsByUser(userId);
        return ResponseEntity.ok(formations);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FormationDto.FormationResponse>> getAllFormations() {
        List<FormationDto.FormationResponse> formations = formationService.getAllFormations();
        return ResponseEntity.ok(formations);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @formationService.getFormationById(#id).userId == principal.user.id")
    public ResponseEntity<FormationDto.FormationResponse> getFormationById(@PathVariable UUID id) {
        FormationDto.FormationResponse formation = formationService.getFormationById(id);
        return ResponseEntity.ok(formation);
    }
    
    @PutMapping("/{formationId}/statut")
    @PreAuthorize("hasRole('ADMIN') or @formationService.getFormationById(#formationId).userId == principal.user.id")
    public ResponseEntity<FormationDto.FormationResponse> updateStatut(
            @PathVariable UUID formationId,
            @RequestParam Formation.StatutFormation statut) {
        FormationDto.FormationResponse response = formationService.updateStatut(formationId, statut);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{formationId}/progression")
    @PreAuthorize("hasRole('ADMIN') or @formationService.getFormationById(#formationId).userId == principal.user.id")
    public ResponseEntity<FormationDto.FormationResponse> updateProgression(
            @PathVariable UUID formationId,
            @RequestParam Integer progression) {
        FormationDto.FormationResponse response = formationService.updateProgression(formationId, progression);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{formationId}/review-notes")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FormationDto.FormationResponse> updateReviewNotes(
            @PathVariable UUID formationId,
            @RequestBody FormationDto.ReviewNotesRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        String reviewerIdentity = resolveReviewerIdentity(principal);
        FormationDto.FormationResponse response = formationService.updateReviewNotes(
                formationId,
                request,
                reviewerIdentity);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{formationId}/mini-test")
    @PreAuthorize("hasRole('ADMIN') or @formationService.getFormationById(#formationId).userId == principal.user.id")
    public ResponseEntity<FormationDto.FormationResponse> submitMiniTest(
            @PathVariable UUID formationId,
            @RequestBody FormationDto.MiniTestSubmissionRequest request) {
        FormationDto.FormationResponse response = formationService.submitMiniTest(formationId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{formationId}/certificate")
    @PreAuthorize("hasRole('ADMIN') or @formationService.getFormationById(#formationId).userId == principal.user.id")
    public ResponseEntity<FormationDto.FormationResponse> uploadCertificate(
            @PathVariable UUID formationId,
            @RequestParam("file") MultipartFile file) {
        FormationDto.FormationResponse response = formationService.uploadCertificate(formationId, file);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @formationService.getFormationById(#id).userId == principal.user.id")
    public ResponseEntity<Void> deleteFormation(@PathVariable UUID id) {
        formationService.supprimerFormation(id);
        return ResponseEntity.noContent().build();
    }

    private String resolveReviewerIdentity(UserDetailsImpl principal) {
        if (principal == null || principal.getUser() == null) {
            return null;
        }

        String firstName = principal.getUser().getFirstName() != null
                ? principal.getUser().getFirstName().trim()
                : "";
        String lastName = principal.getUser().getLastName() != null
                ? principal.getUser().getLastName().trim()
                : "";

        String fullName = (firstName + " " + lastName).trim();
        if (!fullName.isEmpty()) {
            return fullName;
        }

        String email = principal.getUser().getEmail();
        if (email != null && !email.isBlank()) {
            return email.trim();
        }

        return null;
    }
}
