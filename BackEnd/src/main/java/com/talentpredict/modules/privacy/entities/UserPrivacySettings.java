package com.talentpredict.modules.privacy.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.talentpredict.modules.user.entities.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_privacy_settings", indexes = {
        @Index(name = "idx_user_privacy_user_id", columnList = "user_id", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPrivacySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Builder.Default
    @Column(name = "marketing_emails_consent", nullable = false)
    private boolean marketingEmailsConsent = false;

    @Builder.Default
    @Column(name = "analytics_consent", nullable = false)
    private boolean analyticsConsent = true;

    @Builder.Default
    @Column(name = "profile_visibility_consent", nullable = false)
    private boolean profileVisibilityConsent = true;

    @Builder.Default
    @Column(name = "data_processing_consent", nullable = false)
    private boolean dataProcessingConsent = true;

    @Builder.Default
    @Column(name = "consent_version", nullable = false, length = 30)
    private String consentVersion = "v1";

    @Column(name = "consent_updated_at")
    private Instant consentUpdatedAt;

    @Builder.Default
    @Column(name = "data_retention_days", nullable = false)
    private Integer dataRetentionDays = 365;

    @Column(name = "delete_requested_at")
    private Instant deleteRequestedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
