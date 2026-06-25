package com.talentpredict.modules.auth.repositories;

import com.talentpredict.modules.auth.entities.RefreshToken;
import com.talentpredict.modules.user.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    Optional<RefreshToken> findByIdAndUser(UUID id, User user);

    Optional<RefreshToken> findByTokenAndUserIdAndRevokedFalse(String token, UUID userId);

    List<RefreshToken> findAllByUserOrderByCreatedAtDesc(User user);

    @Query("SELECT rt FROM RefreshToken rt WHERE rt.user = :user AND rt.revoked = false AND rt.expiryDate > CURRENT_TIMESTAMP")
    List<RefreshToken> findActiveTokensByUser(@Param("user") User user);

    @Modifying
    @Query("UPDATE RefreshToken SET revoked = true WHERE user = :user AND revoked = false")
    void revokeAllUserTokens(@Param("user") User user);

    @Modifying
    @Query("DELETE FROM RefreshToken rt WHERE rt.expiryDate < CURRENT_TIMESTAMP")
    void deleteExpiredTokens();
}
