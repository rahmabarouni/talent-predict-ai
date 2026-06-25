package com.talentpredict.shared.services;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import lombok.Data;

/**
 * In-memory tracker for async AI analysis status.
 * Allows the frontend to poll for analysis completion/failure.
 */
@Service
public class AnalysisStatusService {

    private final Map<UUID, AnalysisStatus> statuses = new ConcurrentHashMap<>();

    public void markRunning(UUID accountId) {
        statuses.put(accountId, new AnalysisStatus("RUNNING", null, LocalDateTime.now(), 0));
    }

    public void markCompleted(UUID accountId, int skillsFound) {
        statuses.put(accountId, new AnalysisStatus("COMPLETED", null, LocalDateTime.now(), skillsFound));
    }

    public void markFailed(UUID accountId, String error) {
        statuses.put(accountId, new AnalysisStatus("FAILED", error, LocalDateTime.now(), 0));
    }

    public AnalysisStatus getStatus(UUID accountId) {
        return statuses.get(accountId);
    }

    @Data
    public static class AnalysisStatus {
        private final String status;
        private final String error;
        private final LocalDateTime timestamp;
        private final int skillsFound;
    }
}
