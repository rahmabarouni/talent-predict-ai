package com.talentpredict.modules.ai.services;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.talentpredict.modules.ai.entities.Prediction;
import com.talentpredict.modules.ai.repositories.PredictionRepository;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.auth.services.AuthServiceImpl;
import com.talentpredict.modules.skills.entities.Skill;
import com.talentpredict.modules.skills.repositories.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final PredictionRepository predictionRepository;
    private final SkillRepository skillRepository;
    private final AuthServiceImpl authService;
    private final OpenAIService openAIService;

    @Transactional
    public Prediction generateRecommendations(UUID userId) {
        User user = authService.getUserById(userId);
        List<Skill> skills = skillRepository.findByUserId(userId);

        StringBuilder profileBuilder = new StringBuilder();
        profileBuilder.append("User: ").append(user.getFirstName()).append(" ").append(user.getLastName()).append("\n");
        profileBuilder.append("Skills: \n");
        skills.forEach(s -> profileBuilder.append("- ").append(s.getNom()).append(" (Level ").append(s.getNiveau()).append(")\n"));

        String prompt = "Based on the following user profile, generate 3 specific training recommendations. Format as a simple bulleted list.\n" + profileBuilder.toString();
        
        String aiResponse = openAIService.genererPrediction(prompt);

        Prediction prediction = Prediction.builder()
                .user(user)
                .analyse("AI Generated Recommendations via Personality Test flow")
                .recommandationSoft(aiResponse)
                .scoreConfiance(0.85)
                .statut(Prediction.StatutPrediction.COMPLETEE)
                .build();

        return predictionRepository.save(prediction);
    }
    
    public List<Prediction> getUserRecommendations(UUID userId) {
        return predictionRepository.findByUserIdOrderByDatePredictionDesc(userId);
    }
}
