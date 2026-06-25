package com.talentpredict.modules.evaluation.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.talentpredict.modules.evaluation.entities.PersonalityTest;


@Repository
public interface PersonalityTestRepository extends JpaRepository<PersonalityTest, UUID> {
    List<PersonalityTest> findByUserIdOrderByDateTestDesc(UUID userId);

    long countByUserId(UUID userId);

    @Query("select t.user.id, count(t) from PersonalityTest t where t.user.id in :userIds group by t.user.id")
    List<Object[]> countGroupedByUserIds(@Param("userIds") List<UUID> userIds);

    @Query("SELECT AVG(t.score) FROM PersonalityTest t WHERE t.user.id = :userId")
    Double findAvgScoreByUserId(UUID userId);
}
