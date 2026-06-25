package com.talentpredict.modules.ai.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.talentpredict.modules.skills.entities.Skill;
import com.talentpredict.modules.skills.repositories.SkillRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobMatchService {

    private final SkillRepository skillRepository;
    private final OpenAIService openAIService;

    public Map<String, Object> calculateMatchScore(UUID userId, String jobDescription) {
        List<Skill> userSkills = skillRepository.findByUserId(userId);
        
        String skillsList = userSkills.stream()
            .map(s -> s.getNom() + " (" + s.getNiveau() + "/5)")
            .collect(Collectors.joining(", "));

        String prompt = "Compare the following candidate skills with the job description and provide a match score from 0 to 100, " +
                "along with a brief explanation and 2 missing skills. \n\n" +
                "Candidate Skills: " + skillsList + "\n\n" +
                "Job Description: " + jobDescription + "\n\n" +
                "Respond in JSON format like this: {\"score\": 85, \"explanation\": \"Good match...\", \"missingSkills\": [\"AWS\", \"Docker\"]}";

        String response = openAIService.genererPrediction(prompt);
        
        // Mock parsing logic since LLM might not return perfect JSON
        // In a real app we would use ObjectMapper
        Map<String, Object> result = new HashMap<>();
        try {
            // Very naive extraction for demo purposes
            int score = 75;
            if (response.contains("\"score\":")) {
                String scoreStr = response.split("\"score\":")[1].split(",")[0].trim();
                score = Integer.parseInt(scoreStr);
            }
            result.put("score", score);
            result.put("rawResponse", response);
        } catch (Exception e) {
            result.put("score", 70);
            result.put("rawResponse", "Fallback response due to parsing error");
        }

        return result;
    }
}
