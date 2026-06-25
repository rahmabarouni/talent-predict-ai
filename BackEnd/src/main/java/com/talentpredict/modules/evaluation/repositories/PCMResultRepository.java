package com.talentpredict.modules.evaluation.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.talentpredict.modules.user.entities.Profile;
import com.talentpredict.modules.evaluation.entities.PCMResult;

@Repository
public interface PCMResultRepository extends JpaRepository<PCMResult, UUID> {
    List<PCMResult> findByProfile(Profile profile);
}
