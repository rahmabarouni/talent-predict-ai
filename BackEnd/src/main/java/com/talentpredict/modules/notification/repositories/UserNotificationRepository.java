package com.talentpredict.modules.notification.repositories;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.talentpredict.modules.notification.entities.UserNotification;
import com.talentpredict.modules.user.entities.User;

@Repository
public interface UserNotificationRepository extends JpaRepository<UserNotification, UUID> {

    Page<UserNotification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    Page<UserNotification> findByUserAndReadAtIsNullOrderByCreatedAtDesc(User user, Pageable pageable);

    long countByUserAndReadAtIsNull(User user);

    Optional<UserNotification> findByIdAndUser(UUID id, User user);

    @Modifying
    @Query("UPDATE UserNotification n SET n.readAt = :readAt WHERE n.user = :user AND n.readAt IS NULL")
    int markAllRead(@Param("user") User user, @Param("readAt") Instant readAt);

    long deleteByUser(User user);

    long deleteByUserAndCreatedAtBefore(User user, Instant threshold);
}
