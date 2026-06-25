package com.talentpredict.modules.privacy.dto;

import java.time.Instant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;

public class PrivacyDto {

    @Data
    @AllArgsConstructor
    public static class SettingsResponse {
        private boolean marketingEmailsConsent;
        private boolean analyticsConsent;
        private boolean profileVisibilityConsent;
        private boolean dataProcessingConsent;
        private String consentVersion;
        private Instant consentUpdatedAt;
        private Integer dataRetentionDays;
        private Instant deleteRequestedAt;
    }

    @Data
    public static class SettingsUpdateRequest {
        private Boolean marketingEmailsConsent;
        private Boolean analyticsConsent;
        private Boolean profileVisibilityConsent;
        private Boolean dataProcessingConsent;
        private String consentVersion;

        @Min(value = 30, message = "Data retention must be at least 30 days")
        @Max(value = 3650, message = "Data retention cannot exceed 3650 days")
        private Integer dataRetentionDays;
    }

    @Data
    public static class DeleteAccountRequest {
        @NotBlank(message = "Confirm phrase is required")
        private String confirmPhrase;
    }

    @Data
    @AllArgsConstructor
    public static class MessageResponse {
        private String message;
    }

    @Data
    @AllArgsConstructor
    public static class RetentionApplyResponse {
        private long deletedNotifications;
        private long deletedAuditLogs;
        private String message;
    }
}
