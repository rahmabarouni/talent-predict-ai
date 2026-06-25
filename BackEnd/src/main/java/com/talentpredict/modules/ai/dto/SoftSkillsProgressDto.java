package com.talentpredict.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SoftSkillsProgressDto {
    private LocalDate evaluationDate;
    private Double overallScore;
    private Map<String, Double> skills;
    private String summary;

    @JsonProperty("improvement_delta")
    private Double improvementDelta; // difference from previous evaluation
}
