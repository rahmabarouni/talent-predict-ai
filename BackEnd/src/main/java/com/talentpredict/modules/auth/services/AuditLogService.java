package com.talentpredict.modules.auth.services;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.talentpredict.modules.auth.entities.AuditLog;
import com.talentpredict.modules.auth.repositories.AuditLogRepository;
import com.talentpredict.modules.user.entities.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    private void saveAudit(AuditLog audit) {
        auditLogRepository.save(Objects.requireNonNull(audit, "audit must not be null"));
    }
//@Transactional annotation is used to manage database transactions declaratively
    @Transactional
    public void logLogin(User user, String ipAddress, String userAgent, String deviceId) {
        AuditLog audit = AuditLog.builder()
            .user(user)
            .email(user.getEmail())
            .eventType("LOGIN")
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .deviceId(deviceId)
            .details("Successful login")
            .build();
        saveAudit(audit);
        log.info("Audit: LOGIN for user {}", user.getEmail());
    }

    @Transactional
    public void logLogout(User user, String ipAddress) {
        AuditLog audit = AuditLog.builder()
            .user(user)
            .email(user.getEmail())
            .eventType("LOGOUT")
            .ipAddress(ipAddress)
            .details("User logout")
            .build();
        saveAudit(audit);
        log.info("Audit: LOGOUT for user {}", user.getEmail());
    }

    @Transactional
    public void logFailedLogin(String email, String ipAddress, String userAgent, String reason) {
        AuditLog audit = AuditLog.builder()
            .email(email)
            .eventType("LOGIN_FAILED")
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .details("Failed login: " + reason)
            .build();
        saveAudit(audit);
        log.warn("Audit: LOGIN_FAILED for email {}", email);
    }

    @Transactional
    public void logPasswordChange(User user, String ipAddress) {
        AuditLog audit = AuditLog.builder()
            .user(user)
            .email(user.getEmail())
            .eventType("PASSWORD_CHANGE")
            .ipAddress(ipAddress)
            .details("Password changed by user")
            .build();
        saveAudit(audit);
        log.info("Audit: PASSWORD_CHANGE for user {}", user.getEmail());
    }

    @Transactional
    public void logPasswordReset(User user, String ipAddress) {
        AuditLog audit = AuditLog.builder()
            .user(user)
            .email(user.getEmail())
            .eventType("PASSWORD_RESET")
            .ipAddress(ipAddress)
            .details("Password reset via email link")
            .build();
        saveAudit(audit);
        log.info("Audit: PASSWORD_RESET for user {}", user.getEmail());
    }

    @Transactional
    public void logAccountLocked(String email, String ipAddress) {
        AuditLog audit = AuditLog.builder()
            .email(email)
            .eventType("ACCOUNT_LOCKED")
            .ipAddress(ipAddress)
            .details("Account locked due to too many failed login attempts")
            .build();
        saveAudit(audit);
        log.warn("Audit: ACCOUNT_LOCKED for email {}", email);
    }

    @Transactional
    public void logMfaEnabled(User user, String ipAddress) {
        AuditLog audit = AuditLog.builder()
            .user(user)
            .email(user.getEmail())
            .eventType("MFA_ENABLED")
            .ipAddress(ipAddress)
            .details("MFA enabled")
            .build();
        saveAudit(audit);
        log.info("Audit: MFA_ENABLED for user {}", user.getEmail());
    }

    @Transactional
    public void logCustomEvent(
            User user,
            String eventType,
            String ipAddress,
            String details,
            String userAgent,
            String deviceId) {
        AuditLog audit = AuditLog.builder()
                .user(user)
                .email(user != null ? user.getEmail() : null)
                .eventType(eventType)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .deviceId(deviceId)
                .details(details)
                .build();
        saveAudit(audit);
        log.info("Audit: {} for user {}", eventType, user != null ? user.getEmail() : "unknown");
    }
}
