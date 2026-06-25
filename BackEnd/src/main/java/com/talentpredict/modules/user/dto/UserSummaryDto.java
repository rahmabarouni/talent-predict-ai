package com.talentpredict.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lightweight summary of a user's activity stats, used in the admin
 * "Gestion des Employés" panel to avoid fake/randomised data.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryDto {

    private UUID userId;

    /** Total formations (all statuses). */
    private long formationsTotal;

    /** Formations currently active (EN_COURS). */
    private long formationsEnCours;

    /** Formations fully completed (TERMINEE). */
    private long formationsTerminees;

    /** Number of AI predictions generated for this user. */
    private long predictionsCount;

    /** Confidence score of the most recent prediction (0-1), null if none. */
    private Double latestPredictionScore;

    /** Date of the most recent prediction, null if none. */
    private LocalDateTime latestPredictionDate;

    /** Type/role suggested by the most recent prediction. */
    private String latestPredictionLabel;

    /** GitHub profile URL. */
    private String githubUrl;

    /** LinkedIn profile URL. */
    private String linkedinUrl;
}
