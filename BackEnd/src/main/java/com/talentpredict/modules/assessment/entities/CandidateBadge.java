package com.talentpredict.modules.assessment.entities;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.talentpredict.modules.user.entities.User;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "candidate_badges", uniqueConstraints = {
    @UniqueConstraint(name = "uk_user_skill_badge", columnNames = { "user_id", "skill" })
})
public class CandidateBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    @ToString.Exclude
    private User user;

    @Column(nullable = false, length = 200)
    private String skill;

    @Column(name = "score")
    private Integer score;

    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "badge_svg_url", length = 1000)
    private String badgeSvgUrl;
}
