package com.talentpredict.modules.notification.entities;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.talentpredict.modules.user.entities.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_notifications", indexes = {
        @Index(name = "idx_user_notifications_user_id", columnList = "user_id"),
        @Index(name = "idx_user_notifications_read_at", columnList = "read_at"),
        @Index(name = "idx_user_notifications_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotification {

    public enum NotificationType {
        SUCCESS,
        ERROR,
        INFO,
        WARNING
    }

    public enum NotificationCategory {
        STATUS_CHANGE,
        NEW_MATCH,
        SECURITY,
        PRIVACY,
        SYSTEM,
        COURSE_APPROVAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type = NotificationType.INFO;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotificationCategory category = NotificationCategory.SYSTEM;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "target_url", length = 500)
    private String targetUrl;

    @Builder.Default
    @Column(name = "email_alert", nullable = false)
    private boolean emailAlert = false;

    @Column(name = "emailed_at")
    private Instant emailedAt;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    public boolean isRead() {
        return this.readAt != null;
    }
}
