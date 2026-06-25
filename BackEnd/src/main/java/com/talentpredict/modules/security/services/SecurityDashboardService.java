 package com.talentpredict.modules.security.services;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.talentpredict.modules.auth.entities.AuditLog;
import com.talentpredict.modules.auth.entities.RefreshToken;
import com.talentpredict.modules.auth.repositories.AuditLogRepository;
import com.talentpredict.modules.auth.repositories.RefreshTokenRepository;
import com.talentpredict.modules.auth.services.AuditLogService;
import com.talentpredict.modules.security.dto.SecurityDto;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SecurityDashboardService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;


    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public SecurityDto.DashboardResponse getDashboard(User user) {
        return new SecurityDto.DashboardResponse(
                Boolean.TRUE.equals(user.getEmailVerified()),
                listActiveSessions(user),
                listLoginHistory(user));
    }

    @Transactional(readOnly = true)
    public List<SecurityDto.SessionInfo> listActiveSessions(User user) {
        List<RefreshToken> sessions = refreshTokenRepository.findActiveTokensByUser(user);
        return sessions.stream()
                .map(session -> new SecurityDto.SessionInfo(
                        session.getId(),
                        session.getDeviceId(),
                        session.getCreatedAt(),
                        session.getExpiryDate(),
                        session.isRevoked()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SecurityDto.LoginEventInfo> listLoginHistory(User user) {
        List<AuditLog> events = auditLogRepository.findTop50ByUserOrderByCreatedAtDesc(user);
        return events.stream()
                .map(event -> new SecurityDto.LoginEventInfo(
                        event.getEventType(),
                        event.getIpAddress(),
                        event.getUserAgent(),
                        event.getDeviceId(),
                        event.getCreatedAt(),
                        event.getDetails()))
                .toList();
    }

    @Transactional
        public SecurityDto.MessageResponse revokeSession(User user, UUID sessionId, String ipAddress) {
        RefreshToken session = refreshTokenRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found."));

        session.setRevoked(true);
        refreshTokenRepository.save(session);
                auditLogService.logCustomEvent(user, "SESSION_REVOKED", ipAddress,
                "Session revoked: " + sessionId, null, session.getDeviceId());
        return new SecurityDto.MessageResponse("Session revoked.");
    }

    @Transactional
        public SecurityDto.MessageResponse revokeAllSessions(User user, String ipAddress) {
        refreshTokenRepository.revokeAllUserTokens(user);
                auditLogService.logCustomEvent(user, "ALL_SESSIONS_REVOKED", ipAddress,
                "All active sessions were revoked", null, null);
        return new SecurityDto.MessageResponse("All active sessions revoked.");
    }


}
