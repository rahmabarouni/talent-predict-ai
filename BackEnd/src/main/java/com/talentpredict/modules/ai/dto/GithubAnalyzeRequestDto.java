package com.talentpredict.modules.ai.dto;

import java.util.List;
import lombok.Data;

@Data
public class GithubAnalyzeRequestDto {
    private String username;
    private List<String> claimedSkills;
}
