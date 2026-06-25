package com.talentpredict.modules.formation.entities;

import java.time.LocalDateTime;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.talentpredict.modules.ai.entities.Prediction;
import com.talentpredict.modules.user.entities.User;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "formations")
public class Formation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // infos
    @Column(nullable = false, length = 300)
    private String titre;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column
    private Integer duree; // En heures

    @Column(length = 200)
    private String fournisseur;

    @Column(length = 500)
    private String url;

    @Column(name = "date_proposition")
    @Builder.Default
    private LocalDateTime dateProposition = LocalDateTime.now();

    @Column(name = "date_debut")
    private LocalDateTime dateDebut;

    @Column(name = "date_fin")
    private LocalDateTime dateFin;

    @Column
    @Builder.Default
    private Integer progression = 0; // 0-100

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @Column(name = "next_action", length = 500)
    private String nextAction;

    @Column(name = "reviewed_by", length = 255)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "mini_test_score")
    private Integer miniTestScore;

    @Column(name = "mini_test_passed")
    private Boolean miniTestPassed;

    @Column(name = "mini_test_taken_at")
    private LocalDateTime miniTestTakenAt;

    @Column(name = "mini_test_notes", columnDefinition = "TEXT")
    private String miniTestNotes;

    @Column(name = "certificate_url", length = 600)
    private String certificateUrl;

    @Column(name = "certificate_uploaded_at")
    private LocalDateTime certificateUploadedAt;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "admin_note", columnDefinition = "TEXT")
    private String adminNote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TypeFormation type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StatutFormation statut = StatutFormation.PROPOSEE;

    // relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prediction_id")
    @JsonIgnore
    @ToString.Exclude
    private Prediction prediction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private User user;



    // enums
    public enum TypeFormation {
        SOFT_SKILL,
        TECH_SKILL,
        CERTIFICATION,
        WORKSHOP
    }

    public enum StatutFormation {
        PROPOSEE,
        EN_ATTENTE,
        ACCEPTEE,
        REJETEE,
        PROPOSEE_ADMIN,
        EN_COURS,
        EN_ATTENTE_VALIDATION,
        TERMINEE,
        ANNULEE
    }
}
