package com.talentpredict.modules.evaluation.services;

import com.talentpredict.modules.assessment.repositories.CandidateTestResultRepository;
import com.talentpredict.modules.evaluation.dto.HiPoDto;
import com.talentpredict.modules.evaluation.entities.PCMResult;
import com.talentpredict.modules.evaluation.repositories.PCMResultRepository;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HiPoService {

    private final UserRepository userRepository;
    private final CandidateTestResultRepository assessmentRepo;
    private final PCMResultRepository pcmRepo;

    @Transactional(readOnly = true)
    public HiPoDto calculateHiPoForUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return calculateHiPo(user);
    }

    @Transactional(readOnly = true)
    public List<HiPoDto> calculateHiPoForAllUsers() {
        return userRepository.findByRole(User.Role.USER).stream()
                .map(this::calculateHiPo)
                .collect(Collectors.toList());
    }

    private HiPoDto calculateHiPo(User user) {
        // 1. Calculate Performance (0-100)
        Double perfScore = assessmentRepo.findAvgScoreByUserId(user.getId());
        if (perfScore == null) perfScore = 0.0;

        // 2. Calculate Potential (0-100)
        Double potScore = 0.0;
        if (user.getProfile() != null) {
            List<PCMResult> pcmResults = pcmRepo.findByProfile(user.getProfile());
            if (pcmResults != null && !pcmResults.isEmpty()) {
                double totalPcm = 0;
                for (PCMResult pcm : pcmResults) {
                    // Assuming PCM scores represent a strong personality indicator, we sum them
                    // and normalize or cap to 100 for the potential score calculation
                    int sum = (pcm.getScoreTravail() != null ? pcm.getScoreTravail() : 0) +
                              (pcm.getScoreSecondaire() != null ? pcm.getScoreSecondaire() : 0) +
                              (pcm.getScoreReactif() != null ? pcm.getScoreReactif() : 0) +
                              (pcm.getScoreRebelle() != null ? pcm.getScoreRebelle() : 0);
                    totalPcm += Math.min(100.0, sum); // Cap individual PCM sums at 100
                }
                potScore = totalPcm / pcmResults.size();
            } else if (user.getProfile().getRealScore() != null) {
                // Fallback to realScore if PCM doesn't exist
                potScore = Double.valueOf(user.getProfile().getRealScore());
            }
        }

        // 3. Final HiPo Score (Weighted average)
        Double finalScore = (perfScore * 0.6) + (potScore * 0.4);

        // 4. 9-Box Grid categorization
        String category;
        String nineBoxPosition;
        boolean isHiPo = false;
        
        if (perfScore >= 80 && potScore >= 80) {
            category = "High Potential (HiPo)";
            nineBoxPosition = "Top Right";
            isHiPo = true;
        } else if (perfScore >= 80 && potScore >= 50) {
            category = "High Performer";
            nineBoxPosition = "Middle Right";
        } else if (perfScore >= 80 && potScore < 50) {
            category = "Specialist / Subject Matter Expert";
            nineBoxPosition = "Bottom Right";
        } else if (perfScore >= 50 && potScore >= 80) {
            category = "Rough Diamond / Rising Star";
            nineBoxPosition = "Top Center";
        } else if (perfScore >= 50 && potScore >= 50) {
            category = "Core Employee";
            nineBoxPosition = "Middle Center";
        } else if (perfScore >= 50 && potScore < 50) {
            category = "Effective Employee";
            nineBoxPosition = "Bottom Center";
        } else if (perfScore < 50 && potScore >= 80) {
            category = "Enigma / Inconsistent Player";
            nineBoxPosition = "Top Left";
        } else if (perfScore < 50 && potScore >= 50) {
            category = "Dilemma / Needs Improvement";
            nineBoxPosition = "Middle Left";
        } else {
            category = "Underperformer";
            nineBoxPosition = "Bottom Left";
        }

        return HiPoDto.builder()
                .userId(user.getId())
                .fullName(user.getFirstName() + " " + user.getLastName())
                .performanceScore(Math.round(perfScore * 100.0) / 100.0)
                .potentialScore(Math.round(potScore * 100.0) / 100.0)
                .finalHiPoScore(Math.round(finalScore * 100.0) / 100.0)
                .category(category)
                .nineBoxPosition(nineBoxPosition)
                .isHiPo(isHiPo)
                .recommendation(generateRecommendation(nineBoxPosition))
                .build();
    }

    private String generateRecommendation(String nineBoxPosition) {
        return switch (nineBoxPosition) {
            case "Top Right" -> "Provide stretch assignments and leadership coaching. Ready for next-level roles.";
            case "Middle Right" -> "Focus on developing broader leadership skills to match high performance.";
            case "Bottom Right" -> "Reward and retain as critical expert. Consider mentoring others.";
            case "Top Center" -> "Identify and remove blockers to performance. High potential is present.";
            case "Middle Center" -> "Provide targeted development. Solid contributor.";
            case "Bottom Center" -> "Monitor performance and consider lateral moves or role adjustments.";
            case "Top Left" -> "Urgent performance management. Investigate root causes of low performance despite potential.";
            case "Middle Left" -> "Provide clear feedback and a performance improvement plan.";
            case "Bottom Left" -> "Consider reassignment or exit. Both potential and performance are low.";
            default -> "No specific recommendation.";
        };
    }
}
