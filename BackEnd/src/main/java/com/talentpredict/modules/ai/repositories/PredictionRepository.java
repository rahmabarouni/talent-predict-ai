package com.talentpredict.modules.ai.repositories;

import com.talentpredict.modules.ai.entities.Prediction;
import com.talentpredict.modules.user.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface PredictionRepository extends JpaRepository<Prediction, UUID> {
    List<Prediction> findByUserIdOrderByDatePredictionDesc(UUID  userId);
    Optional<Prediction> findFirstByUserIdOrderByDatePredictionDesc(UUID userId);
    
    // Find the most recent prediction for a user entity
    Optional<Prediction> findTopByUserOrderByDatePredictionDesc(User user);
    
    // Find all predictions for a user entity ordered by date
    List<Prediction> findByUserOrderByDatePredictionDesc(User user);

    // Batch load predictions for multiple users ordered by latest first per user
    List<Prediction> findByUserIdInOrderByDatePredictionDesc(List<UUID> userIds);
}
