package com.talentpredict.modules.ai.dto;

import com.talentpredict.modules.ai.entities.Prediction;
import com.talentpredict.modules.formation.dto.FormationDto;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class PredictionDto {

    @Data
    public static class Response {
        private UUID id;
        private LocalDateTime datePrediction;
        private String analyse;
        private String recommandationSoft;
        private String recommandationTech;
        private Double scoreConfiance;
        private Prediction.StatutPrediction statut;
        private List<FormationDto.FormationResponse> formationsProposees;
    }
}
