package com.talentpredict.modules.privacy.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.talentpredict.modules.privacy.entities.UserPrivacySettings;
import com.talentpredict.modules.user.entities.User;

@Repository
public interface UserPrivacySettingsRepository extends JpaRepository<UserPrivacySettings, UUID> {

    Optional<UserPrivacySettings> findByUser(User user);
}
