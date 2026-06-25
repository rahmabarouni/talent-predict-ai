package com.talentpredict.modules.user.controllers;

import java.util.ArrayList;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.talentpredict.modules.ai.services.CvAnalysisService;
import com.talentpredict.modules.ai.services.OpenRouterService;
import com.talentpredict.modules.ai.services.ProfileAnalysisOrchestrator;
import com.talentpredict.modules.skills.dto.SkillDto;
import com.talentpredict.modules.skills.services.SkillService;
import com.talentpredict.modules.user.dto.ProfileDto;
import com.talentpredict.modules.user.services.ProfileService;
import com.talentpredict.shared.services.AnalysisStatusService;
import com.talentpredict.shared.services.FileStorageService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Profile Controller — Exposes endpoints for managing employee profiles.
 */
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final ProfileService profileService;
    private final SkillService skillService;
    private final ProfileAnalysisOrchestrator profileAnalysisOrchestrator;
    private final CvAnalysisService cvAnalysisService;
    private final FileStorageService fileStorageService;
    private final AnalysisStatusService analysisStatusService;

    @Value("${app.base-url:http://localhost:8081}")
    private String appBaseUrl;


    /** GET /api/profiles/users/{id} */
    @GetMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ProfileDto.Response> getProfileByAccount(@PathVariable UUID id) {
        log.info("Fetching profile for id={}", id);
        return ResponseEntity.ok(profileService.getProfileByAccountId(id));
    }

    /** PUT /api/profiles/users/{id} — partial update */
    @PutMapping("/users/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ProfileDto.Response> updateProfileByAccount(
            @PathVariable UUID id,
            @Valid @RequestBody ProfileDto.UpdateRequest request) {
        log.info("Updating profile for id={}", id);
        return ResponseEntity.ok(profileService.updateProfileByAccountId(id, request));
    }

    /**
     * POST /api/profiles/accounts/{id}/upload-photo
     * Accepts an image file, stores it, updates profile.urlPhoto, returns updated profile.
     */
    @PostMapping("/accounts/{id}/upload-photo")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> uploadPhoto(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {

        log.info("Upload photo for account {}: '{}'", id, file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Le fichier est vide"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Seules les images sont acceptées"));
        }

        try {
            String path = fileStorageService.store(file, "photos");
            ProfileDto.UpdateRequest req = new ProfileDto.UpdateRequest();
            req.setUrlPhoto(appBaseUrl + path);
            ProfileDto.Response updated = profileService.updateProfileByAccountId(id, req);
            log.info("Photo uploaded for account {}: {}", id, path);
            return ResponseEntity.ok(updated);
        } catch (IOException | RuntimeException e) {
            log.error("Photo upload failed for account {}: {}", id, e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("message", "Erreur lors de l'upload: " + e.getMessage()));
        }
    }

    /**
     * POST /api/profiles/accounts/{id}/upload-cv
     * Stores the PDF, updates profile.cvUrl, analyzes skills with AI.
     */
    @PostMapping("/accounts/{id}/upload-cv")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> uploadCvEtAnalyser(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {

        log.info("Upload CV for account {}: '{}'", id, file.getOriginalFilename());

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", "Le fichier est vide", "status", "ERROR"
            ));
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
            return ResponseEntity.badRequest().body(Map.of(
                "message", "Seuls les fichiers PDF sont acceptés", "status", "ERROR"
            ));
        }

        // Store file and update cvUrl in profile
        try {
            String cvPath = fileStorageService.store(file, "cvs");
            ProfileDto.UpdateRequest req = new ProfileDto.UpdateRequest();
            req.setCvUrl(appBaseUrl + cvPath);
            profileService.updateProfileByAccountId(id, req);
            log.info("CV stored for account {}: {}", id, cvPath);
        } catch (IOException | RuntimeException e) {
            log.warn("CV storage failed (continuing with analysis): {}", e.getMessage());
        }

        // Analyze CV and extract EVERYTHING (Profile info + Skills)
        OpenRouterService.FullProfileExtraction extraction = cvAnalysisService.analyserCvFileComplet(file);

        // Update profile fields (Title, Bio, Years of Exp)
        try {
            ProfileDto.UpdateRequest profileUpdate = new ProfileDto.UpdateRequest();
            profileUpdate.setTitreProfessionnel(extraction.getTitreProfessionnel());
            profileUpdate.setDescription(extraction.getDescription());
            profileUpdate.setExperienceAns(extraction.getExperienceAns());
            profileService.updateProfileByAccountId(id, profileUpdate);
            log.info("Profile fields updated from CV for account {}", id);
        } catch (Exception e) {
            log.warn("Failed to update profile fields from CV: {}", e.getMessage());
        }

        List<SkillDto.CreateRequest> skillsDetectes = extraction.getSkills();

        // Save skills without duplicates
        List<String> skillsAjoutes = new ArrayList<>();
        List<String> skillsExistants = new ArrayList<>();

        List<SkillDto.Response> existingSkills = skillService.getSkillsByUser(id);
        Set<String> existingNames = new HashSet<>();
        existingSkills.forEach(s -> existingNames.add(s.getNom().toLowerCase().trim()));

        for (SkillDto.CreateRequest skill : skillsDetectes) {
            String nameNorm = skill.getNom().toLowerCase().trim();
            if (existingNames.contains(nameNorm)) {
                skillsExistants.add(skill.getNom());
            } else {
                try {
                    skillService.creerSkill(id, skill);
                    skillsAjoutes.add(skill.getNom());
                    existingNames.add(nameNorm);
                } catch (Exception e) {
                    log.warn("Skill non ajouté '{}': {}", skill.getNom(), e.getMessage());
                }
            }
        }

        log.info("CV analysé: {} ajoutés, {} déjà existants", skillsAjoutes.size(), skillsExistants.size());

        return ResponseEntity.ok(Map.of(
            "message", "Votre profil a été mis à jour et " + skillsAjoutes.size() + " nouveaux skills ont été détectés depuis votre CV",
            "status", "SUCCESS",
            "skillsAjoutes", skillsAjoutes,
            "skillsDejaPresentss", skillsExistants,
            "totalDetectes", skillsDetectes.size(),
            "extractedInfo", Map.of(
                "title", extraction.getTitreProfessionnel() != null ? extraction.getTitreProfessionnel() : "",
                "experience", extraction.getExperienceAns() != null ? extraction.getExperienceAns() : 0
            )
        ));
    }

    /** POST /api/profiles/accounts/{id}/analyse-ia — triggers full AI analysis in background */
    @PostMapping("/accounts/{id}/analyse-ia")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, String>> lancerAnalyseIA(@PathVariable UUID id) {
        log.info("Analyse IA manuelle demandée pour account: {}", id);
        profileAnalysisOrchestrator.analyserProfil(id);
        return ResponseEntity.ok(Map.of(
            "message", "Analyse IA lancée en arrière-plan. Vos skills seront mis à jour dans 15-30 secondes.",
            "status", "PROCESSING",
            "accountId", id.toString()
        ));
    }

    /** GET /api/profiles/accounts/{id}/analyse-status — poll analysis progress */
    @GetMapping("/accounts/{id}/analyse-status")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> getAnalyseStatus(@PathVariable UUID id) {
        AnalysisStatusService.AnalysisStatus status = analysisStatusService.getStatus(id);
        if (status == null) {
            return ResponseEntity.ok(Map.of("status", "IDLE"));
        }
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("status", status.getStatus());
        result.put("timestamp", status.getTimestamp().toString());
        result.put("skillsFound", status.getSkillsFound());
        if (status.getError() != null) {
            result.put("error", status.getError());
        }
        return ResponseEntity.ok(result);
    }

    /** POST /api/profiles/accounts/{id}/publish — explicitly publish profile */
    @PostMapping("/accounts/{id}/publish")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<ProfileDto.Response> publishProfile(@PathVariable UUID id) {
        log.info("Explicit profile publish requested for account: {}", id);
        return ResponseEntity.ok(profileService.publishProfile(id));
    }
}
