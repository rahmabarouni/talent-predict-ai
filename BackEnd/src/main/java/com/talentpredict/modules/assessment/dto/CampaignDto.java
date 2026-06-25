package com.talentpredict.modules.assessment.dto;

import java.time.Instant;
import java.util.UUID;

public record CampaignDto(
        UUID id,
        String name,
        String templateId,
        String templateName,
        String channel,
        String targetGroup,
        Integer recipientCount,
        String status,
        Instant scheduledAt,
        Integer sentCount,
        Integer failedCount,
        Double openRate,
        Double clickRate,
        Boolean isPaused) {
}
