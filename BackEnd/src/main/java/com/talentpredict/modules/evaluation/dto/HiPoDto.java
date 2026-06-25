package com.talentpredict.modules.evaluation.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HiPoDto {
    private UUID userId;
    private String fullName;
    private Double performanceScore;
    private Double potentialScore;
    private Double finalHiPoScore;
    private String category;
    private String recommendation;
    private String nineBoxPosition;
    private Boolean isHiPo;
}
