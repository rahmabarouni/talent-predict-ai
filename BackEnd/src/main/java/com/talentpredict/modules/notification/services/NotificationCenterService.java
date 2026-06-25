package com.talentpredict.modules.notification.services;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.talentpredict.modules.notification.dto.NotificationDto;
import com.talentpredict.modules.notification.entities.UserNotification;
import com.talentpredict.modules.notification.repositories.UserNotificationRepository;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.UserRepository;
import com.talentpredict.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationCenterService {

    private final UserNotificationRepository userNotificationRepository;
    private final UserRepository userRepository;
    private final NotificationSseService notificationSseService;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Transactional(readOnly = true)
    public Page<NotificationDto.Response> listForUser(User user, boolean unreadOnly, Pageable pageable) {
        Page<UserNotification> notifications = unreadOnly
                ? userNotificationRepository.findByUserAndReadAtIsNullOrderByCreatedAtDesc(user, pageable)
                : userNotificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);

        return notifications.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public long unreadCount(User user) {
        return userNotificationRepository.countByUserAndReadAtIsNull(user);
    }

    @Transactional
    public NotificationDto.Response createFromRequest(User actor, NotificationDto.CreateRequest request) {
        User target = resolveTargetUser(actor, request.getTargetUserId());

        UserNotification created = createNotification(
                target,
                parseType(request.getType()),
                parseCategory(request.getCategory()),
                request.getTitle(),
                request.getBody(),
                request.getTargetUrl(),
                Boolean.TRUE.equals(request.getEmailAlert()));

        return toResponse(created);
    }

    @Transactional
    public NotificationDto.Response createStatusChangeEvent(User actor, NotificationDto.StatusChangeEventRequest request) {
        User target = resolveTargetUser(actor, request.getTargetUserId());
        String title = "Application status updated";
        String body = "Your status changed from " + request.getFromStatus() + " to " + request.getToStatus() + "."
                + (StringUtils.hasText(request.getContext()) ? " " + request.getContext().trim() : "");

        UserNotification created = createNotification(
                target,
                UserNotification.NotificationType.INFO,
                UserNotification.NotificationCategory.STATUS_CHANGE,
                title,
                body,
                "/dashboard",
                Boolean.TRUE.equals(request.getEmailAlert()));

        return toResponse(created);
    }



    @Transactional
    public NotificationDto.Response createNewMatchEvent(User actor, NotificationDto.NewMatchEventRequest request) {
        User target = resolveTargetUser(actor, request.getTargetUserId());
        String title = "New match found";
        String scorePart = request.getMatchScore() != null ? " with match score " + request.getMatchScore() + "%." : ".";
        String body = "A new role match is available for " + request.getRole() + scorePart
                + (StringUtils.hasText(request.getDetails()) ? " " + request.getDetails().trim() : "");

        UserNotification created = createNotification(
                target,
                UserNotification.NotificationType.SUCCESS,
                UserNotification.NotificationCategory.NEW_MATCH,
                title,
                body,
                "/dashboard",
                Boolean.TRUE.equals(request.getEmailAlert()));

        return toResponse(created);
    }

    /**
     * Admin → send a direct in-app notification to any user.
     * The notification is persisted and SSE-pushed immediately if the user is online.
     */
    @Transactional
    public NotificationDto.Response sendDirectMessage(User admin, NotificationDto.DirectMessageRequest request) {
        if (admin.getRole() != User.Role.ADMIN) {
            throw new IllegalArgumentException("Only admins can send direct messages.");
        }

        User target = userRepository.findById(request.getTargetUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found."));

        UserNotification created = createNotification(
                target,
                parseType(request.getType()),
                UserNotification.NotificationCategory.SYSTEM,
                request.getTitle(),
                request.getBody(),
                request.getTargetUrl(),
                Boolean.TRUE.equals(request.getEmailAlert()));

        return toResponse(created);
    }

    @Transactional
    public NotificationDto.Response createCourseApprovalEvent(User target, String title, String body, boolean isSuccess) {
        UserNotification created = createNotification(
                target,
                isSuccess ? UserNotification.NotificationType.SUCCESS : UserNotification.NotificationType.WARNING,
                UserNotification.NotificationCategory.SYSTEM,
                title,
                body,
                "/formations",
                false);
        return toResponse(created);
    }

    @Transactional
    public NotificationDto.Response markRead(User user, UUID notificationId) {
        UserNotification notification = userNotificationRepository.findByIdAndUser(notificationId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));

        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notification = userNotificationRepository.save(notification);
        }

        return toResponse(notification);
    }

    @Transactional
    public int markAllRead(User user) {
        return userNotificationRepository.markAllRead(user, Instant.now());
    }

    @Transactional
    public void deleteNotification(User user, UUID notificationId) {
        UserNotification notification = userNotificationRepository.findByIdAndUser(notificationId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found."));
        userNotificationRepository.delete(notification);
    }

    @Transactional
    public long clearUserNotifications(User user) {
        return userNotificationRepository.deleteByUser(user);
    }

    private User resolveTargetUser(User actor, UUID requestedTargetUserId) {
        if (requestedTargetUserId == null || requestedTargetUserId.equals(actor.getId())) {
            return actor;
        }

        boolean canTargetOtherUsers = actor.getRole() == User.Role.ADMIN;
        if (!canTargetOtherUsers) {
            throw new IllegalArgumentException("You can only create notifications for your own account.");
        }

        return userRepository.findById(requestedTargetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Target user not found."));
    }

    private UserNotification createNotification(
            User user,
            UserNotification.NotificationType type,
            UserNotification.NotificationCategory category,
            String title,
            String body,
            String targetUrl,
            boolean emailAlert) {

        UserNotification notification = UserNotification.builder()
                .user(user)
                .type(type)
                .category(category)
                .title(title)
                .body(body)
                .targetUrl(targetUrl)
                .emailAlert(emailAlert)
                .build();

        notification = userNotificationRepository.save(notification);
        sendEmailAlertIfRequested(notification);
        
        NotificationDto.Response response = toResponse(notification);
        notificationSseService.sendNotification(user.getId(), response);
        
        return notification;
    }

    private void sendEmailAlertIfRequested(UserNotification notification) {
        if (!notification.isEmailAlert()) {
            return;
        }

        if (mailSender == null) {
            log.info("Mail sender is not configured. Notification email skipped for user={}",
                    notification.getUser().getEmail());
            return;
        }

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                SimpleMailMessage mail = new SimpleMailMessage();
                mail.setTo(notification.getUser().getEmail());
                mail.setSubject("TalentPredict notification - " + notification.getTitle());
                mail.setText(notification.getBody()
                        + "\n\nOpen TalentPredict to view details.\n\n- TalentPredict");
                mailSender.send(mail);
                
                // Need to update emailedAt in a transaction, but we are in a background thread now.
                // Doing it simply for now, if it fails, it's just a timestamp.
                notification.setEmailedAt(Instant.now());
                userNotificationRepository.save(notification);
            } catch (RuntimeException ex) {
                log.warn("Failed to send notification email to {}", notification.getUser().getEmail(), ex);
            }
        });
    }

    private UserNotification.NotificationType parseType(String rawType) {
        if (!StringUtils.hasText(rawType)) {
            return UserNotification.NotificationType.INFO;
        }

        try {
            return UserNotification.NotificationType.valueOf(rawType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid notification type: " + rawType);
        }
    }

    private UserNotification.NotificationCategory parseCategory(String rawCategory) {
        if (!StringUtils.hasText(rawCategory)) {
            return UserNotification.NotificationCategory.SYSTEM;
        }

        try {
            return UserNotification.NotificationCategory.valueOf(rawCategory.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid notification category: " + rawCategory);
        }
    }

    private NotificationDto.Response toResponse(UserNotification notification) {
        return new NotificationDto.Response(
                notification.getId(),
                notification.getType().name(),
                notification.getCategory().name(),
                notification.getTitle(),
                notification.getBody(),
                notification.getReadAt() != null,
                notification.getCreatedAt(),
                notification.getReadAt(),
                notification.isEmailAlert(),
                notification.getEmailedAt(),
                notification.getTargetUrl());
    }
}
