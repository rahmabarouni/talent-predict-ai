package com.talentpredict.modules.ai.dto;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SoftSkillsResultDto {

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("user_email")
    private String userEmail;

    @JsonProperty("overall_score")
    private Double overallScore;

    @JsonProperty("merged_soft_skills")
    private Map<String, Double> mergedSoftSkills;

    @JsonProperty("top_3_strengths")
    private List<String> top3Strengths;

    @JsonProperty("top_3_weaknesses")
    private List<String> top3Weaknesses;

    // Parsed from Ollama text output
    private String summary;
    private String careerAdvice;
    private List<String> keyStrengths;
    private List<String> keyWeaknesses;

    @JsonProperty("training_recommendations")
    private Map<String, String> trainingRecommendations;

    @JsonProperty("source_data")
    private Map<String, Object> sourceData;

    @JsonProperty("weights_applied")
    private Map<String, Double> weightsApplied;

    @JsonProperty("personality_type")
    private String personalityType;

    @JsonProperty("personality_description")
    private String personalityDescription;

    @JsonProperty("parse_error")
    private Boolean parseError;

    @JsonProperty("scenario_evaluation")
    private Map<String, Object> scenarioEvaluation;
}
