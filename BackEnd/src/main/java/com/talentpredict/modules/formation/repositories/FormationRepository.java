package com.talentpredict.modules.formation.repositories;

import com.talentpredict.modules.formation.entities.Formation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;


@Repository
public interface FormationRepository extends JpaRepository<Formation, UUID> {
    List<Formation> findByUserId(UUID userId);
    boolean existsByUserIdAndTitreIgnoreCase(UUID userId, String titre);
    long countByUserId(UUID userId);
    long countByUserIdAndStatut(UUID userId, Formation.StatutFormation statut);

    @Query("select f.user.id, count(f) from Formation f where f.user.id in :userIds group by f.user.id")
    List<Object[]> countGroupedByUserIds(@Param("userIds") List<UUID> userIds);

    @Query("select f.user.id, count(f) from Formation f where f.user.id in :userIds and f.statut = :statut group by f.user.id")
    List<Object[]> countGroupedByUserIdsAndStatut(
        @Param("userIds") List<UUID> userIds,
        @Param("statut") Formation.StatutFormation statut
    );
}
