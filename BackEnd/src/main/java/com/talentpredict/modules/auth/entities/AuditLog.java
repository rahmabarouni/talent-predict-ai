package com.talentpredict.modules.auth.entities;

import com.talentpredict.modules.user.entities.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_logs_user_id", columnList = "user_id"),
    @Index(name = "idx_event_type", columnList = "event_type"),
    @Index(name = "idx_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "user_id")
    private User user; // null for failed logins with invalid email

    @Column(nullable = false, length = 50)
    private String eventType; // "LOGIN", "LOGOUT", "LOGIN_FAILED", "PASSWORD_CHANGE", "PASSWORD_RESET", "MFA_ENABLED", "ACCOUNT_LOCKED"

    @Column(length = 255)
    private String email; // used for login attempts where user not found

    @Column(length = 50)
    private String ipAddress; // Client IP

    @Column(length = 500)
    private String userAgent; // Browser/device info

    @Column(length = 255)
    private String deviceId; // Unique device identifier

    @Column(length = 255)
    private String location; // Geolocation (optional, from IP)

    @Column(columnDefinition = "TEXT")
    private String details; // Additional context (JSON)

    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private Instant createdAt;
}
