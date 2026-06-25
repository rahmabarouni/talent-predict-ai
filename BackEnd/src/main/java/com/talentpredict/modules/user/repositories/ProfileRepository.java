package com.talentpredict.modules.user.repositories;


import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.talentpredict.modules.user.entities.Profile;
import com.talentpredict.modules.user.entities.User;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    Optional<Profile> findByUser(User user);

    /** Find profile by the associated user's id (user_id column). */
    @Query("SELECT p FROM Profile p WHERE p.user.id = :userId")
    Optional<Profile> findByUser_Id(@Param("userId") UUID userId);

    Optional<Profile> findByPublicSlug(String publicSlug);


}
