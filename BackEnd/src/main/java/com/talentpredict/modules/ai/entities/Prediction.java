package com.talentpredict.modules.ai.entities;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.formation.entities.Formation;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "predictions")
public class Prediction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // infos
    @Column(columnDefinition = "TEXT", nullable = false, name = "analyse_text")
    private String analyse; // Analyse générée par OpenAI

    @Column(name = "recommandation_soft", columnDefinition = "TEXT")
    private String recommandationSoft;

    @Column(name = "recommandation_tech", columnDefinition = "TEXT")
    private String recommandationTech;

    @Column(name = "score_confiance")
    private Double scoreConfiance; // 0-1

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private StatutPrediction statut = StatutPrediction.EN_ANALYSE;

    @Column(name = "date_prediction", nullable = false)
    @Builder.Default
    private LocalDateTime datePrediction = LocalDateTime.now();

    // relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private User user;

    @OneToMany(mappedBy = "prediction", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @ToString.Exclude
    @Builder.Default
    private List<Formation> formationsProposees = new ArrayList<>();

    // enums
    public enum StatutPrediction {
        EN_ANALYSE,
        COMPLETEE,
        VALIDEE,
        APPLIQUEE
    }
}
