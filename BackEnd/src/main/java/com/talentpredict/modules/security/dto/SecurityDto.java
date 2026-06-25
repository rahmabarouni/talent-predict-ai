package com.talentpredict.modules.security.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


import lombok.AllArgsConstructor;
import lombok.Data;

public class SecurityDto {

    @Data
    @AllArgsConstructor
    public static class SessionInfo {
        private UUID id;
        private String deviceId;
        private Instant createdAt;
        private Instant expiresAt;
        private boolean revoked;
    }

    @Data
    @AllArgsConstructor
    public static class LoginEventInfo {
        private String eventType;
        private String ipAddress;
        private String userAgent;
        private String deviceId;
        private Instant createdAt;
        private String details;
    }

    @Data
    @AllArgsConstructor
    public static class DashboardResponse {
        private boolean emailVerified;

        private List<SessionInfo> activeSessions;
        private List<LoginEventInfo> loginHistory;
    }



    @Data
    @AllArgsConstructor
    public static class MessageResponse {
        private String message;
    }
}
