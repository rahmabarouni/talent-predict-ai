package com.talentpredict.modules.privacy.services;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.talentpredict.modules.auth.entities.AuditLog;
import com.talentpredict.modules.auth.entities.RefreshToken;
import com.talentpredict.modules.auth.repositories.AuditLogRepository;
import com.talentpredict.modules.auth.repositories.RefreshTokenRepository;
import com.talentpredict.modules.auth.services.AuditLogService;
import com.talentpredict.modules.notification.entities.UserNotification;
import com.talentpredict.modules.notification.repositories.UserNotificationRepository;
import com.talentpredict.modules.privacy.dto.PrivacyDto;
import com.talentpredict.modules.privacy.entities.UserPrivacySettings;
import com.talentpredict.modules.privacy.repositories.UserPrivacySettingsRepository;
import com.talentpredict.modules.skills.entities.Skill;
import com.talentpredict.modules.skills.repositories.SkillRepository;
import com.talentpredict.modules.user.entities.Profile;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.ProfileRepository;
import com.talentpredict.modules.user.repositories.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PrivacyService {

    private static final String DELETE_CONFIRM_PHRASE = "DELETE MY ACCOUNT";

    private final UserPrivacySettingsRepository privacySettingsRepository;
    private final ProfileRepository profileRepository;
    private final SkillRepository skillRepository;
    private final AuditLogRepository auditLogRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public PrivacyDto.SettingsResponse getSettings(User user) {
        return toSettingsResponse(getOrCreateSettings(user));
    }

    @Transactional
    public PrivacyDto.SettingsResponse updateSettings(User user, PrivacyDto.SettingsUpdateRequest request) {
        UserPrivacySettings settings = getOrCreateSettings(user);

        if (request.getMarketingEmailsConsent() != null) {
            settings.setMarketingEmailsConsent(request.getMarketingEmailsConsent());
        }
        if (request.getAnalyticsConsent() != null) {
            settings.setAnalyticsConsent(request.getAnalyticsConsent());
        }
        if (request.getProfileVisibilityConsent() != null) {
            settings.setProfileVisibilityConsent(request.getProfileVisibilityConsent());
        }
        if (request.getDataProcessingConsent() != null) {
            settings.setDataProcessingConsent(request.getDataProcessingConsent());
        }
        if (request.getDataRetentionDays() != null) {
            settings.setDataRetentionDays(request.getDataRetentionDays());
        }
        if (StringUtils.hasText(request.getConsentVersion())) {
            settings.setConsentVersion(request.getConsentVersion().trim());
        }

        settings.setConsentUpdatedAt(Instant.now());
        settings = privacySettingsRepository.save(settings);
        return toSettingsResponse(settings);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportUserData(User user) {
        UserPrivacySettings settings = getOrCreateSettings(user);
        Profile profile = profileRepository.findByUser_Id(user.getId()).orElse(null);
        List<Skill> skills = skillRepository.findByUserId(user.getId());
        List<RefreshToken> sessions = refreshTokenRepository.findAllByUserOrderByCreatedAtDesc(user);
        List<AuditLog> auditLogs = auditLogRepository.findTop50ByUserOrderByCreatedAtDesc(user);
        List<UserNotification> notifications = userNotificationRepository.findByUserOrderByCreatedAtDesc(user, Pageable.unpaged()).getContent();

        Map<String, Object> export = new LinkedHashMap<>();
        export.put("exportedAt", Instant.now().toString());
        export.put("user", toUserMap(user));
        export.put("profile", toProfileMap(profile));
        export.put("skills", toSkillsList(skills));
        export.put("activeAndHistoricalSessions", toSessionsList(sessions));
        export.put("loginAndSecurityHistory", toAuditList(auditLogs));
        export.put("notifications", toNotificationList(notifications));
        export.put("privacySettings", toPrivacyMap(settings));
        return export;
    }

    @Transactional
    public PrivacyDto.MessageResponse requestAccountDeletion(User user, String ipAddress) {
        UserPrivacySettings settings = getOrCreateSettings(user);
        settings.setDeleteRequestedAt(Instant.now());
        privacySettingsRepository.save(settings);

        auditLogService.logCustomEvent(
                user,
                "GDPR_DELETE_REQUESTED",
                ipAddress,
                "User requested account deletion",
                null,
                null);

        return new PrivacyDto.MessageResponse(
                "Account deletion request recorded. You can finalize deletion from your privacy dashboard.");
    }

    @Transactional
    public PrivacyDto.RetentionApplyResponse applyRetention(User user) {
        UserPrivacySettings settings = getOrCreateSettings(user);
        int retentionDays = settings.getDataRetentionDays() != null ? settings.getDataRetentionDays() : 365;
        Instant threshold = Instant.now().minus(retentionDays, ChronoUnit.DAYS);

        long deletedNotifications = userNotificationRepository.deleteByUserAndCreatedAtBefore(user, threshold);
        long deletedAuditLogs = auditLogRepository.deleteByUserAndCreatedAtBefore(user, threshold);

        String message = "Applied retention policy. Removed " + deletedNotifications + " notifications and "
                + deletedAuditLogs + " audit entries older than " + retentionDays + " days.";

        return new PrivacyDto.RetentionApplyResponse(deletedNotifications, deletedAuditLogs, message);
    }

    @Transactional
    public PrivacyDto.MessageResponse deleteAccount(User user, String confirmPhrase, String ipAddress) {
        if (!StringUtils.hasText(confirmPhrase)
                || !DELETE_CONFIRM_PHRASE.equalsIgnoreCase(confirmPhrase.trim())) {
            throw new IllegalArgumentException(
                    "Invalid confirmation phrase. Please type exactly: " + DELETE_CONFIRM_PHRASE);
        }

        UserPrivacySettings settings = getOrCreateSettings(user);
        settings.setDeleteRequestedAt(Instant.now());
        privacySettingsRepository.save(settings);

        refreshTokenRepository.revokeAllUserTokens(user);
        anonymizeUser(user);
        scrubProfile(user);
        userRepository.save(user);

        auditLogService.logCustomEvent(
                user,
                "GDPR_ACCOUNT_ANONYMIZED",
                ipAddress,
                "User account was anonymized via self-service deletion",
                null,
                null);

        return new PrivacyDto.MessageResponse("Account data anonymized successfully. You are now signed out.");
    }

    private UserPrivacySettings getOrCreateSettings(User user) {
        return privacySettingsRepository.findByUser(user)
                .orElseGet(() -> privacySettingsRepository.save(
                        UserPrivacySettings.builder()
                                .user(user)
                                .consentUpdatedAt(Instant.now())
                                .build()));
    }

    private PrivacyDto.SettingsResponse toSettingsResponse(UserPrivacySettings settings) {
        return new PrivacyDto.SettingsResponse(
                settings.isMarketingEmailsConsent(),
                settings.isAnalyticsConsent(),
                settings.isProfileVisibilityConsent(),
                settings.isDataProcessingConsent(),
                settings.getConsentVersion(),
                settings.getConsentUpdatedAt(),
                settings.getDataRetentionDays(),
                settings.getDeleteRequestedAt());
    }

    private Map<String, Object> toUserMap(User user) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", user.getId());
        row.put("firstName", user.getFirstName());
        row.put("lastName", user.getLastName());
        row.put("email", user.getEmail());
        row.put("phoneNumber", user.getPhoneNumber());
        row.put("department", user.getDepartment());
        row.put("position", user.getPosition());
        row.put("role", user.getRole() != null ? user.getRole().name() : null);
        row.put("isActive", user.getIsActive());
        row.put("createdAt", user.getCreatedAt());
        row.put("updatedAt", user.getUpdatedAt());
        return row;
    }

    private Map<String, Object> toProfileMap(Profile profile) {
        if (profile == null) {
            return null;
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("titreProfessionnel", profile.getTitreProfessionnel());
        row.put("description", profile.getDescription());
        row.put("experienceAns", profile.getExperienceAns());
        row.put("niveauEtudes", profile.getNiveauEtudes());
        row.put("lienLinkedin", profile.getLienLinkedin());
        row.put("githubUrl", profile.getGithubUrl());
        row.put("cvUrl", profile.getCvUrl());
        row.put("portfolioUrl", profile.getPortfolioUrl());
        row.put("githubStats", buildGithubStatsMap(profile));
        row.put("createdAt", profile.getCreatedAt());
        row.put("updatedAt", profile.getUpdatedAt());
        return row;
    }

    private Map<String, Object> buildGithubStatsMap(Profile profile) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("repos", profile.getGithubRepos());
        stats.put("followers", profile.getGithubFollowers());
        stats.put("following", profile.getGithubFollowing());
        stats.put("bio", profile.getGithubBio());
        stats.put("company", profile.getGithubCompany());
        stats.put("location", profile.getGithubLocation());
        return stats;
    }

    private List<Map<String, Object>> toSkillsList(List<Skill> skills) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Skill skill : skills) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", skill.getId());
            row.put("name", skill.getNom());
            row.put("level", skill.getNiveau());
            row.put("type", skill.getType() != null ? skill.getType().name() : null);
            row.put("source", skill.getSource());
            row.put("description", skill.getDescription());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> toSessionsList(List<RefreshToken> sessions) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (RefreshToken session : sessions) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", session.getId());
            row.put("deviceId", session.getDeviceId());
            row.put("createdAt", session.getCreatedAt());
            row.put("expiryDate", session.getExpiryDate());
            row.put("revoked", session.isRevoked());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> toAuditList(List<AuditLog> auditLogs) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AuditLog audit : auditLogs) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("eventType", audit.getEventType());
            row.put("ipAddress", audit.getIpAddress());
            row.put("userAgent", audit.getUserAgent());
            row.put("deviceId", audit.getDeviceId());
            row.put("details", audit.getDetails());
            row.put("createdAt", audit.getCreatedAt());
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> toNotificationList(List<UserNotification> notifications) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (UserNotification notification : notifications) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", notification.getId());
            row.put("type", notification.getType().name());
            row.put("category", notification.getCategory().name());
            row.put("title", notification.getTitle());
            row.put("body", notification.getBody());
            row.put("targetUrl", notification.getTargetUrl());
            row.put("emailAlert", notification.isEmailAlert());
            row.put("emailedAt", notification.getEmailedAt());
            row.put("readAt", notification.getReadAt());
            row.put("createdAt", notification.getCreatedAt());
            rows.add(row);
        }
        return rows;
    }

    private Map<String, Object> toPrivacyMap(UserPrivacySettings settings) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("marketingEmailsConsent", settings.isMarketingEmailsConsent());
        row.put("analyticsConsent", settings.isAnalyticsConsent());
        row.put("profileVisibilityConsent", settings.isProfileVisibilityConsent());
        row.put("dataProcessingConsent", settings.isDataProcessingConsent());
        row.put("consentVersion", settings.getConsentVersion());
        row.put("consentUpdatedAt", settings.getConsentUpdatedAt());
        row.put("dataRetentionDays", settings.getDataRetentionDays());
        row.put("deleteRequestedAt", settings.getDeleteRequestedAt());
        return row;
    }

    private void anonymizeUser(User user) {
        String suffix = user.getId().toString().replace("-", "").substring(0, 12);
        user.setEmail("deleted+" + suffix + "@redacted.local");
        user.setFirstName("Deleted");
        user.setLastName("User");
        user.setPhoneNumber(null);
        user.setDepartment(null);
        user.setPosition(null);
        user.setProfilePictureUrl(null);
        user.setPassword(passwordEncoder.encode(Instant.now().toString() + "#Deleted1"));
        user.setIsActive(false);
        user.setEmailVerified(false);
        user.setEmailVerifiedAt(null);

    }

    private void scrubProfile(User user) {
        profileRepository.findByUser_Id(user.getId()).ifPresent(profile -> {
            profile.setDescription(null);
            profile.setTitreProfessionnel(null);
            profile.setUrlPhoto(null);
            profile.setLienLinkedin(null);
            profile.setGithubUrl(null);
            profile.setCvUrl(null);
            profile.setPortfolioUrl(null);
            profile.setGithubBio(null);
            profile.setGithubCompany(null);
            profile.setGithubLocation(null);
            profile.setGithubAvatarUrl(null);
            profile.setGithubName(null);
            profile.setAiSummary(null);
            profileRepository.save(profile);
        });
        log.info("Profile scrubbed for anonymized user {}", user.getId());
    }
}
