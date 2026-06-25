package com.talentpredict.modules.auth.repositories;

import com.talentpredict.modules.auth.entities.TokenBlocklist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TokenBlocklistRepository extends JpaRepository<TokenBlocklist, UUID> {
    Optional<TokenBlocklist> findByTokenHash(String tokenHash);

    @Modifying
    @Query("DELETE FROM TokenBlocklist tb WHERE tb.expiresAt < CURRENT_TIMESTAMP")
    void deleteExpiredEntries();
}
