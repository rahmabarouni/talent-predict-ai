package com.talentpredict.modules.ai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SoftSkillsAnalysisRequestDto {

    // Accept both camelCase and snake_case from Angular
    @JsonAlias({"full_name", "fullName"})
    private String fullName;

    @JsonAlias({"email"})
    private String email;

    @JsonAlias({"github_username", "githubUsername"})
    private String githubUsername;

    @JsonAlias({"cv_base64", "cvBase64"})
    private String cvBase64;

    @JsonAlias({"cv_filename", "cvFileName"})
    private String cvFileName;

    @JsonAlias({"cv_text", "cvText", "extracted_cv_text"})
    private String cvText;

    @JsonAlias({"linkedin_url", "linkedinUrl"})
    private String linkedinUrl;

    @JsonAlias({"linkedin_content", "linkedinContent"})
    private String linkedinContent;

    // PCM questions — accept both lowercase and uppercase
    // Use Integer (wrapper) instead of primitive int to allow null/missing values
    @JsonAlias({"q1", "Q1"})
    private Integer q1;

    @JsonAlias({"q2", "Q2"})
    private Integer q2;

    @JsonAlias({"q3", "Q3"})
    private Integer q3;

    @JsonAlias({"q4", "Q4"})
    private Integer q4;

    @JsonAlias({"q5", "Q5"})
    private Integer q5;

    @JsonAlias({"q6", "Q6"})
    private Integer q6;

    @JsonAlias({"q7", "Q7"})
    private Integer q7;

    @JsonAlias({"q8", "Q8"})
    private Integer q8;

    @JsonAlias({"q9", "Q9"})
    private Integer q9;

    @JsonAlias({"q10", "Q10"})
    private Integer q10;

    @JsonAlias({"q11", "Q11"})
    private Integer q11;

    @JsonAlias({"q12", "Q12"})
    private Integer q12;

    @JsonAlias({"q13", "Q13"})
    private Integer q13;

    @JsonAlias({"q14", "Q14"})
    private Integer q14;

    @JsonAlias({"q15", "Q15"})
    private Integer q15;

    @JsonAlias({"q16", "Q16"})
    private Integer q16;

    @JsonAlias({"q17", "Q17"})
    private Integer q17;

    @JsonAlias({"q18", "Q18"})
    private Integer q18;
}
