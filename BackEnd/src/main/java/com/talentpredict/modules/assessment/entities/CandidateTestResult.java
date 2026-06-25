package com.talentpredict.modules.assessment.entities;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.talentpredict.modules.user.entities.User;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "candidate_test_results")
public class CandidateTestResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private User user;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "skill_scores", columnDefinition = "TEXT")
    private String skillScoresJson;


    @CreationTimestamp
    @Column(name = "taken_at", nullable = false, updatable = false)
    private Instant takenAt;

    @Column(name = "passed")
    private Boolean passed;

    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", length = 20)
    private TestType testType;
}
