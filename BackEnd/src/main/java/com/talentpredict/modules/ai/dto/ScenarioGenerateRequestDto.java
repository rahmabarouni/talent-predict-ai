package com.talentpredict.modules.ai.dto;

import lombok.Data;

@Data
public class ScenarioGenerateRequestDto {
    private String role;
    private String level = "Mid-Level";
}
