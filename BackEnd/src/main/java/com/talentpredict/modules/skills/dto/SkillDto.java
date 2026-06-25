package com.talentpredict.modules.skills.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.talentpredict.modules.skills.entities.Skill;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

public class SkillDto {
    @Data
    public static class CreateRequest {

        @NotBlank(message = "Le nom du skill est requis")
        private String nom;

        @NotNull(message = "Le type est requis")
        private Skill.TypeSkill type;

        @NotNull(message = "Le niveau est requis")
        @Min(value = 1, message = "Le niveau minimum est 1")
        @Max(value = 5, message = "Le niveau maximum est 5")
        private Integer niveau;

        private String description;

        private String source; // CV, GITHUB, PYTHON_AI, PCM
    }

    @Data
    public static class Response {
        private UUID id;
        private UUID userId;
        private String nom;
        private Skill.TypeSkill type;
        private Integer niveau;
        private String description;
        private String source;
        private LocalDateTime dateEvaluation;
        private Boolean validee;
    }
}
