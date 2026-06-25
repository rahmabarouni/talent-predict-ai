package com.talentpredict.modules.formation.services;

import java.io.IOException;
import com.talentpredict.modules.formation.dto.FormationDto;
import com.talentpredict.shared.exception.ResourceNotFoundException;
import com.talentpredict.modules.formation.entities.Formation;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.formation.repositories.FormationRepository;
import com.talentpredict.modules.auth.services.AuthServiceImpl;
import com.talentpredict.shared.exception.BadRequestException;
import com.talentpredict.shared.services.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Formation Module - Formation Management Service
 * Core functionality: Manage training programs, track progress, and handle account enrollments
 */
@Service
@RequiredArgsConstructor

public class FormationService {
    
    private final FormationRepository formationRepository;
    private final com.talentpredict.modules.user.repositories.UserRepository userRepository;
    private final AuthServiceImpl authServiceImpl;
    private final FileStorageService fileStorageService;
    private final com.talentpredict.modules.notification.services.NotificationCenterService notificationService;
    
    @Transactional
    public FormationDto.FormationResponse creerFormation(UUID accountId, FormationDto.FormationRequest request) {
        User user = authServiceImpl.getUserById(accountId);

        // Guard against duplicate formations with the same title for the same user
        if (formationRepository.existsByUserIdAndTitreIgnoreCase(accountId, request.getTitre())) {
            throw new BadRequestException("Une formation avec ce titre existe déjà dans votre plan.");
        }
        
        Formation formation = new Formation();
        formation.setUser(user);
        formation.setTitre(request.getTitre());
        formation.setDescription(request.getDescription());
        formation.setType(request.getType());
        formation.setDuree(request.getDuree());
        formation.setFournisseur(request.getFournisseur());
        formation.setUrl(request.getUrl());
        formation.setDateDebut(request.getDateDebut());
        
        Formation.StatutFormation targetStatut = request.getStatut() != null ? request.getStatut() : Formation.StatutFormation.PROPOSEE;
        formation.setStatut(targetStatut);
        if (targetStatut == Formation.StatutFormation.EN_ATTENTE) {
            formation.setRequestedAt(LocalDateTime.now());
            notificationService.createCourseApprovalEvent(
                user,
                "Demande de formation envoyée",
                "Votre demande pour " + formation.getTitre() + " est en attente d'approbation",
                true
            );
            // Notify admins
            userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.ADMIN)
                .forEach(admin -> {
                    notificationService.createCourseApprovalEvent(
                        admin,
                        "Nouvelle demande d'approbation",
                        user.getFirstName() + " a demandé l'approbation pour " + formation.getTitre(),
                        true
                    );
                });
        }
        
        Formation saved = formationRepository.save(formation);
        return convertToResponse(saved);
    }
    
    @Transactional(readOnly = true)
    public List<FormationDto.FormationResponse> getFormationsByUser(UUID userId) {
        return formationRepository.findByUserId(userId)
            .stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FormationDto.FormationResponse> getAllFormations() {
        return formationRepository.findAll()
            .stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public FormationDto.FormationResponse getFormationById(UUID id) {
        Formation formation = formationRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Formation non trouvée avec l'ID: " + id));
        return convertToResponse(formation);
    }
    
    @Transactional
    public FormationDto.FormationResponse updateStatut(UUID formationId, Formation.StatutFormation statut) {
        Formation formation = formationRepository.findById(formationId)
            .orElseThrow(() -> new ResourceNotFoundException("Formation non trouvée avec l'ID: " + formationId));

        formation.setStatut(statut);

        if (statut == Formation.StatutFormation.EN_ATTENTE && formation.getRequestedAt() == null) {
            formation.setRequestedAt(LocalDateTime.now());
            if (formation.getUser() != null) {
                notificationService.createCourseApprovalEvent(
                    formation.getUser(),
                    "Demande de formation envoyée",
                    "Votre demande pour " + formation.getTitre() + " est en attente d'approbation",
                    true
                );
                // Notify admins
                userRepository.findAll().stream()
                    .filter(u -> u.getRole() == User.Role.ADMIN)
                    .forEach(admin -> {
                        notificationService.createCourseApprovalEvent(
                            admin,
                            "Nouvelle demande d'approbation",
                            formation.getUser().getFirstName() + " a demandé l'approbation pour " + formation.getTitre(),
                            true
                        );
                    });
            }
        }

        if (statut == Formation.StatutFormation.ACCEPTEE && formation.getUser() != null) {
             notificationService.createCourseApprovalEvent(
                formation.getUser(),
                "✅ Formation approuvée",
                "Votre demande pour " + formation.getTitre() + " a été approuvée",
                true
             );
        }

        if (statut == Formation.StatutFormation.REJETEE && formation.getUser() != null) {
             notificationService.createCourseApprovalEvent(
                formation.getUser(),
                "❌ Formation rejetée",
                "Votre demande pour " + formation.getTitre() + " a été rejetée",
                false
             );
        }

        if (statut == Formation.StatutFormation.EN_COURS && formation.getDateDebut() == null) {
            formation.setDateDebut(LocalDateTime.now());
        }

        if (statut == Formation.StatutFormation.TERMINEE) {
            if (formation.getProgression() == null || formation.getProgression() < 100) {
                formation.setProgression(100);
            }
            if (formation.getDateFin() == null) {
                formation.setDateFin(LocalDateTime.now());
            }
        }

        if (statut == Formation.StatutFormation.ANNULEE && formation.getDateFin() == null) {
            formation.setDateFin(LocalDateTime.now());
        }

        return convertToResponse(formationRepository.save(formation));
    }
    
    @Transactional
    public FormationDto.FormationResponse updateProgression(UUID formationId, Integer progression) {
        Formation formation = formationRepository.findById(formationId)
            .orElseThrow(() -> new ResourceNotFoundException("Formation non trouvée avec l'ID: " + formationId));

        int normalizedProgression = Math.min(100, Math.max(0, progression));
        formation.setProgression(normalizedProgression);

        if (normalizedProgression > 0 && formation.getDateDebut() == null) {
            formation.setDateDebut(LocalDateTime.now());
        }

        if (normalizedProgression > 0
                && formation.getStatut() != Formation.StatutFormation.EN_COURS
                && formation.getStatut() != Formation.StatutFormation.TERMINEE
                && formation.getStatut() != Formation.StatutFormation.ANNULEE) {
            formation.setStatut(Formation.StatutFormation.EN_COURS);
        }
        
        if (normalizedProgression >= 100) {
            formation.setStatut(Formation.StatutFormation.TERMINEE);
            if (formation.getDateFin() == null) {
                formation.setDateFin(LocalDateTime.now());
            }
        }
        
        return convertToResponse(formationRepository.save(formation));
    }

    @Transactional
    public FormationDto.FormationResponse updateReviewNotes(
            UUID formationId,
            FormationDto.ReviewNotesRequest request,
            String reviewerIdentity) {
        Formation formation = formationRepository.findById(formationId)
            .orElseThrow(() -> new ResourceNotFoundException("Formation non trouvée avec l'ID: " + formationId));

        formation.setReviewNote(cleanText(request.getReviewNote()));
        formation.setNextAction(cleanText(request.getNextAction()));

        String reviewedBy = cleanText(request.getReviewedBy());
        if (reviewedBy == null) {
            reviewedBy = cleanText(reviewerIdentity);
        }
        formation.setReviewedBy(reviewedBy);
        formation.setReviewedAt(LocalDateTime.now());

        return convertToResponse(formationRepository.save(formation));
    }

    @Transactional
    public FormationDto.FormationResponse submitMiniTest(
            UUID formationId,
            FormationDto.MiniTestSubmissionRequest request) {
        Formation formation = formationRepository.findById(formationId)
            .orElseThrow(() -> new ResourceNotFoundException("Formation non trouvée avec l'ID: " + formationId));

        if (!isFormationCompleted(formation)) {
            throw new BadRequestException("Terminez la formation avant de passer le mini-test.");
        }

        int score = resolveMiniTestScore(request);
        int passingScore = resolvePassingScore(request);

        boolean isPassing = score >= passingScore;
        formation.setMiniTestScore(score);
        formation.setMiniTestPassed(isPassing);
        formation.setMiniTestTakenAt(LocalDateTime.now());
        formation.setMiniTestNotes(cleanText(request != null ? request.getNotes() : null));

        if (isPassing && formation.getUser() != null) {
            User user = formation.getUser();
            int xpGained = 50 + (score / 2); // Base 50 XP + up to 50 more based on score
            int currentXp = user.getXp() != null ? user.getXp() : 0;
            user.setXp(currentXp + xpGained);
            user.setLevel((user.getXp() / 500) + 1); // 1 level per 500 XP
            userRepository.save(user);
        }

        return convertToResponse(formationRepository.save(formation));
    }

    @Transactional
    public FormationDto.FormationResponse uploadCertificate(UUID formationId, MultipartFile file) {
        Formation formation = formationRepository.findById(formationId)
            .orElseThrow(() -> new ResourceNotFoundException("Formation non trouvée avec l'ID: " + formationId));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Veuillez sélectionner un fichier de certificat.");
        }

        if (!isFormationCompleted(formation)) {
            throw new BadRequestException("Terminez la formation avant de téléverser un certificat.");
        }

        if (!Boolean.TRUE.equals(formation.getMiniTestPassed())) {
            throw new BadRequestException("Validez d'abord le mini-test avec un score suffisant.");
        }

        if (!isAllowedCertificateFile(file)) {
            throw new BadRequestException("Format invalide. Formats autorisés: PDF, PNG, JPG, JPEG.");
        }

        try {
            String certificateUrl = fileStorageService.store(file, "certificates");
            formation.setCertificateUrl(certificateUrl);
            formation.setCertificateUploadedAt(LocalDateTime.now());
        } catch (IOException ex) {
            throw new BadRequestException("Impossible d'enregistrer le certificat.");
        }

        return convertToResponse(formationRepository.save(formation));
    }

    @Transactional
    public void supprimerFormation(UUID id) {
        if (!formationRepository.existsById(id)) {
            throw new ResourceNotFoundException("Formation non trouvée avec l'ID: " + id);
        }
        formationRepository.deleteById(id);
    }
    
    public Long countFormationsByUser(UUID userId) {
        return formationRepository.countByUserId(userId);
    }
    
    public Long countFormationsByUserAndStatut(UUID userId, Formation.StatutFormation statut) {
        return formationRepository.countByUserIdAndStatut(userId, statut);
    }
    
    private FormationDto.FormationResponse convertToResponse(Formation formation) {
        FormationDto.FormationResponse response = new FormationDto.FormationResponse();
        response.setId(formation.getId());
        if (formation.getUser() != null) {
            response.setUserId(formation.getUser().getId());
            response.setCandidatName((formation.getUser().getFirstName() != null ? formation.getUser().getFirstName() : "") 
                + " " + (formation.getUser().getLastName() != null ? formation.getUser().getLastName() : ""));
        }
        response.setTitre(formation.getTitre());
        response.setDescription(formation.getDescription());
        response.setType(formation.getType());
        response.setDuree(formation.getDuree());
        response.setFournisseur(formation.getFournisseur());
        response.setUrl(formation.getUrl());
        response.setStatut(formation.getStatut());
        response.setDateProposition(formation.getDateProposition());
        response.setDateDebut(formation.getDateDebut());
        response.setDateFin(formation.getDateFin());
        response.setProgression(formation.getProgression());
        response.setReviewNote(formation.getReviewNote());
        response.setNextAction(formation.getNextAction());
        response.setReviewedBy(formation.getReviewedBy());
        response.setReviewedAt(formation.getReviewedAt());
        response.setMiniTestScore(formation.getMiniTestScore());
        response.setMiniTestPassed(formation.getMiniTestPassed());
        response.setMiniTestTakenAt(formation.getMiniTestTakenAt());
        response.setMiniTestNotes(formation.getMiniTestNotes());
        response.setCertificateUrl(formation.getCertificateUrl());
        response.setCertificateUploadedAt(formation.getCertificateUploadedAt());
        response.setRequestedAt(formation.getRequestedAt());
        response.setAdminNote(formation.getAdminNote());
        return response;
    }

    private int resolveMiniTestScore(FormationDto.MiniTestSubmissionRequest request) {
        if (request == null) {
            throw new BadRequestException("Les données du mini-test sont requises.");
        }

        Integer directScore = request.getScore();
        if (directScore != null) {
            if (directScore < 0 || directScore > 100) {
                throw new BadRequestException("Le score du mini-test doit être compris entre 0 et 100.");
            }
            return directScore;
        }

        Integer totalQuestions = request.getTotalQuestions();
        Integer correctAnswers = request.getCorrectAnswers();
        if (totalQuestions == null || correctAnswers == null) {
            throw new BadRequestException("Fournissez un score direct ou le couple correctAnswers/totalQuestions.");
        }

        if (totalQuestions <= 0) {
            throw new BadRequestException("Le nombre total de questions doit être supérieur à 0.");
        }

        if (correctAnswers < 0 || correctAnswers > totalQuestions) {
            throw new BadRequestException("correctAnswers doit être compris entre 0 et totalQuestions.");
        }

        return (int) Math.round((correctAnswers * 100.0d) / totalQuestions);
    }

    private int resolvePassingScore(FormationDto.MiniTestSubmissionRequest request) {
        int passingScore = request != null && request.getPassingScore() != null
                ? request.getPassingScore()
                : 70;
        if (passingScore < 1 || passingScore > 100) {
            throw new BadRequestException("Le seuil de réussite doit être compris entre 1 et 100.");
        }
        return passingScore;
    }

    private boolean isFormationCompleted(Formation formation) {
        return formation.getStatut() == Formation.StatutFormation.TERMINEE
                || (formation.getProgression() != null && formation.getProgression() >= 100);
    }

    private boolean isAllowedCertificateFile(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            return false;
        }

        String lowerName = filename.toLowerCase(Locale.ROOT);
        boolean allowedExtension = lowerName.endsWith(".pdf")
                || lowerName.endsWith(".png")
                || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg");

        if (!allowedExtension) {
            return false;
        }

        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return true;
        }

        return "application/pdf".equalsIgnoreCase(contentType)
                || "image/png".equalsIgnoreCase(contentType)
                || "image/jpeg".equalsIgnoreCase(contentType);
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
