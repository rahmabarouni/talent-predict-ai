package com.talentpredict.modules.evaluation.entities;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.talentpredict.modules.user.entities.Profile;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "pcm_results")
public class PCMResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // infos
    @Enumerated(EnumType.STRING)
    @Column(name = "type_pcm", length = 30)
    private TypePCM typePCM;

    @Column(name = "score_travail")
    private Integer scoreTravail;

    @Column(name = "score_secondaire")
    private Integer scoreSecondaire;

    @Column(name = "score_reactif")
    private Integer scoreReactif;

    @Column(name = "score_rebelle")
    private Integer scoreRebelle;

    @Column(name = "date_evaluation")
    @Builder.Default
    private LocalDateTime dateEvaluation = LocalDateTime.now();

    // relationships
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "profile_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private Profile profile;

    // audits
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
