package com.talentpredict.modules.assessment.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.talentpredict.modules.assessment.entities.CandidateTestResult;

@Repository
public interface CandidateTestResultRepository extends JpaRepository<CandidateTestResult, UUID> {

    List<CandidateTestResult> findByUser_IdOrderByTakenAtDesc(UUID userId);

    long countByUser_Id(UUID userId);
    
    @org.springframework.data.jpa.repository.Query("SELECT AVG(r.overallScore) FROM CandidateTestResult r WHERE r.user.id = :userId")
    Double findAvgScoreByUserId(UUID userId);
}
