package com.talentpredict.modules.notification.dto;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

public class NotificationDto {

    @Data
    public static class CreateRequest {
        private UUID targetUserId;

        @NotBlank(message = "Title is required")
        private String title;

        @NotBlank(message = "Body is required")
        private String body;

        private String type = "INFO";
        private String category = "SYSTEM";
        private Boolean emailAlert = Boolean.FALSE;
        private String targetUrl;
    }

    @Data
    public static class StatusChangeEventRequest {
        @NotNull(message = "Target user id is required")
        private UUID targetUserId;

        @NotBlank(message = "From status is required")
        private String fromStatus;

        @NotBlank(message = "To status is required")
        private String toStatus;

        private String context;
        private Boolean emailAlert = Boolean.TRUE;
    }



    @Data
    public static class NewMatchEventRequest {
        @NotNull(message = "Target user id is required")
        private UUID targetUserId;

        @NotBlank(message = "Role is required")
        private String role;

        private Integer matchScore;
        private String details;
        private Boolean emailAlert = Boolean.TRUE;
    }

    /** Sent by ADMIN → delivers a real in-app notification to a specific user. */
    @Data
    public static class DirectMessageRequest {
        @NotNull(message = "Target user id is required")
        private UUID targetUserId;

        @NotBlank(message = "Title is required")
        @Size(max = 180, message = "Title must be 180 chars or fewer")
        private String title;

        @NotBlank(message = "Body is required")
        private String body;

        /** INFO | SUCCESS | WARNING | ERROR — defaults to INFO */
        private String type = "INFO";

        private String targetUrl;
        private Boolean emailAlert = Boolean.FALSE;
    }

    @Data
    @AllArgsConstructor
    public static class Response {
        private UUID id;
        private String type;
        private String category;
        private String title;
        private String body;
        private boolean read;
        private Instant createdAt;
        private Instant readAt;
        private boolean emailAlert;
        private Instant emailedAt;
        private String targetUrl;
    }

    @Data
    @AllArgsConstructor
    public static class CountResponse {
        private long unreadCount;
    }

    @Data
    @AllArgsConstructor
    public static class MessageResponse {
        private String message;
    }
}
