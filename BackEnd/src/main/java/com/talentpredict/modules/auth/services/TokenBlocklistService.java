package com.talentpredict.modules.auth.services;

import java.time.Instant;
import java.util.Objects;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.talentpredict.modules.auth.entities.TokenBlocklist;
import com.talentpredict.modules.auth.repositories.TokenBlocklistRepository;
import com.talentpredict.shared.security.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenBlocklistService {

    private final TokenBlocklistRepository tokenBlocklistRepository;
    private final JwtService jwtService;

    /**
     * Add a token to the blocklist (for logout or password change)
     */
    @Transactional
    public void blockToken(String token, String reason) {
        try {
            String tokenHash = jwtService.hashToken(token);
            Instant expiresAt = jwtService.extractExpiration(token).toInstant();

            TokenBlocklist blocklist = TokenBlocklist.builder()
                .tokenHash(tokenHash)
                .reason(reason)
                .expiresAt(expiresAt)
                .build();

            tokenBlocklistRepository.save(Objects.requireNonNull(blocklist, "blocklist must not be null"));
            log.info("Token blocked: reason={}", reason);
        } catch (Exception e) {
            log.error("Failed to block token", e);
        }
    }

    /**
     * Check if a token is blocked
     */
    @Transactional(readOnly = true)
    public boolean isTokenBlocked(String token) {
        try {
            String tokenHash = jwtService.hashToken(token);
            boolean blocked = tokenBlocklistRepository.findByTokenHash(tokenHash).isPresent();
            if (blocked) {
                log.warn("Attempt to use blocked token");
            }
            return blocked;
        } catch (Exception e) {
            log.error("Error checking token blocklist", e);
            return true; // Fail closed if blocklist check fails
        }
    }

    /**
     * Cleanup expired entries from blocklist (run daily)
     */
    @Scheduled(cron = "0 0 2 * * ?") // 2 AM daily
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            tokenBlocklistRepository.deleteExpiredEntries();
            log.info("Blocklist cleanup completed");
        } catch (Exception e) {
            log.error("Failed to cleanup blocklist", e);
        }
    }
}
