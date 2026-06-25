package com.talentpredict.modules.auth.repositories;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.talentpredict.modules.auth.entities.AuditLog;
import com.talentpredict.modules.user.entities.User;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByUserOrderByCreatedAtDesc(User user);

    List<AuditLog> findTop50ByUserOrderByCreatedAtDesc(User user);

    default List<AuditLog> findRecentLoginsByUser(User user) {
        return findTop10ByUserAndEventTypeInOrderByCreatedAtDesc(user, Arrays.asList("LOGIN", "LOGOUT"));
    }

    List<AuditLog> findTop10ByUserAndEventTypeInOrderByCreatedAtDesc(User user, List<String> eventTypes);

    @Query("SELECT al FROM AuditLog al WHERE al.eventType = 'LOGIN_FAILED' AND al.ipAddress = :ipAddress AND al.createdAt > :since")
    List<AuditLog> findFailedLoginsFromIp(@Param("ipAddress") String ipAddress, @Param("since") Instant since);

    @Modifying
    @Query("DELETE FROM AuditLog al WHERE al.user = :user AND al.createdAt < :threshold")
    long deleteByUserAndCreatedAtBefore(@Param("user") User user, @Param("threshold") Instant threshold);
}
