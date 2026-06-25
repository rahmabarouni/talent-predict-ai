package com.talentpredict.modules.assessment.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.talentpredict.modules.assessment.entities.JobMatch;

@Repository
public interface JobMatchRepository extends JpaRepository<JobMatch, UUID> {

    List<JobMatch> findByUser_IdOrderByCreatedAtDesc(UUID userId);
}
