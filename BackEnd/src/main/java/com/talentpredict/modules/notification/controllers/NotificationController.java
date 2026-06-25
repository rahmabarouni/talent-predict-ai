package com.talentpredict.modules.notification.controllers;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.talentpredict.modules.notification.dto.NotificationDto;
import com.talentpredict.modules.notification.services.NotificationCenterService;
import com.talentpredict.modules.notification.services.NotificationSseService;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.shared.security.UserDetailsImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationCenterService notificationCenterService;
    private final NotificationSseService notificationSseService;

    @GetMapping
    public ResponseEntity<Page<NotificationDto.Response>> listNotifications(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetailsImpl principal,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        User currentUser = principal.getUser();
        return ResponseEntity.ok(notificationCenterService.listForUser(currentUser, unreadOnly, PageRequest.of(page, size)));
    }

    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetailsImpl principal) {
        return notificationSseService.subscribe(principal.getUser().getId());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<NotificationDto.CountResponse> unreadCount(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetailsImpl principal) {
        long count = notificationCenterService.unreadCount(principal.getUser());
        return ResponseEntity.ok(new NotificationDto.CountResponse(count));
    }

    @PostMapping
    public ResponseEntity<NotificationDto.Response> createNotification(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody NotificationDto.CreateRequest request) {
        return ResponseEntity.ok(notificationCenterService.createFromRequest(principal.getUser(), request));
    }

    @PostMapping("/events/status-change")
    public ResponseEntity<NotificationDto.Response> createStatusChangeEvent(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody NotificationDto.StatusChangeEventRequest request) {
        return ResponseEntity.ok(notificationCenterService.createStatusChangeEvent(principal.getUser(), request));
    }



    @PostMapping("/events/new-match")
    public ResponseEntity<NotificationDto.Response> createNewMatchEvent(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody NotificationDto.NewMatchEventRequest request) {
        return ResponseEntity.ok(notificationCenterService.createNewMatchEvent(principal.getUser(), request));
    }

    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<NotificationDto.Response> markRead(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID notificationId) {
        return ResponseEntity.ok(notificationCenterService.markRead(principal.getUser(), notificationId));
    }

    @PatchMapping("/read-all")
    public ResponseEntity<NotificationDto.MessageResponse> markAllRead(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetailsImpl principal) {
        int updated = notificationCenterService.markAllRead(principal.getUser());
        return ResponseEntity.ok(new NotificationDto.MessageResponse("Marked " + updated + " notifications as read."));
    }

    /**
     * Admin → send a direct in-app notification to any user.
     * The message lands instantly in the target user's notification center
     * (SSE push if they are online, and persisted for when they are offline).
     */
    @PostMapping("/admin/direct")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<NotificationDto.Response> sendDirectMessage(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody NotificationDto.DirectMessageRequest request) {
        return ResponseEntity.ok(notificationCenterService.sendDirectMessage(principal.getUser(), request));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> deleteNotification(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetailsImpl principal,
            @PathVariable UUID notificationId) {
        notificationCenterService.deleteNotification(principal.getUser(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/clear")
    public ResponseEntity<NotificationDto.MessageResponse> clearNotifications(
            @org.springframework.security.core.annotation.AuthenticationPrincipal UserDetailsImpl principal) {
        long deleted = notificationCenterService.clearUserNotifications(principal.getUser());
        return ResponseEntity.ok(new NotificationDto.MessageResponse("Cleared " + deleted + " notifications."));
    }
}
