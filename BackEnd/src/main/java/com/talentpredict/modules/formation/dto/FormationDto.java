package com.talentpredict.modules.formation.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.talentpredict.modules.formation.entities.Formation;

import lombok.Data;

public class FormationDto {
    @Data
    public static class FormationRequest {
        private String titre;
        private String description;
        private Formation.TypeFormation type;
        private Integer duree;
        private String fournisseur;
        private String url;
        private LocalDateTime dateDebut;
        private Formation.StatutFormation statut;
    }

    @Data
    public static class ReviewNotesRequest {
        private String reviewNote;
        private String nextAction;
        private String reviewedBy;
    }

    @Data
    public static class MiniTestSubmissionRequest {
        private Integer score;
        private Integer correctAnswers;
        private Integer totalQuestions;
        private Integer passingScore;
        private String notes;
    }

    @Data
    public static class FormationResponse {
        private UUID id;
        private UUID userId;
        private String candidatName;
        private String titre;
        private String description;
        private Formation.TypeFormation type;
        private Integer duree;
        private String fournisseur;
        private String url;
        private Formation.StatutFormation statut;
        private LocalDateTime dateProposition;
        private LocalDateTime dateDebut;
        private LocalDateTime dateFin;
        private Integer progression;
        private String reviewNote;
        private String nextAction;
        private String reviewedBy;
        private LocalDateTime reviewedAt;
        private Integer miniTestScore;
        private Boolean miniTestPassed;
        private LocalDateTime miniTestTakenAt;
        private String miniTestNotes;
        private String certificateUrl;
        private LocalDateTime certificateUploadedAt;
        private LocalDateTime requestedAt;
        private String adminNote;
    }
}
