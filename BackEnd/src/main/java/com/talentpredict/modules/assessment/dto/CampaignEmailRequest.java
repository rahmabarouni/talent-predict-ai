package com.talentpredict.modules.assessment.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CampaignEmailRequest {

    @NotNull(message = "userId is required")
    private UUID userId;

    @NotBlank(message = "candidateUsername is required")
    private String candidateUsername;

    @NotBlank(message = "campaignContext is required")
    private String campaignContext;

    @NotBlank(message = "targetUrl is required")
    private String targetUrl;

    private String subject;

    private String body;
}
