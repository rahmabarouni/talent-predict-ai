package com.talentpredict.modules.assessment.repositories;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.talentpredict.modules.assessment.entities.CandidateBadge;

@Repository
public interface CandidateBadgeRepository extends JpaRepository<CandidateBadge, UUID> {

    Optional<CandidateBadge> findByUser_IdAndSkillIgnoreCase(UUID userId, String skill);
}
