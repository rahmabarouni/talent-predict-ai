package com.talentpredict.modules.assessment.services;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;

import com.talentpredict.modules.assessment.entities.CandidateBadge;
import com.talentpredict.modules.assessment.entities.CandidateTestResult;

import com.talentpredict.modules.assessment.entities.TestType;
import com.talentpredict.modules.assessment.repositories.CandidateBadgeRepository;
import com.talentpredict.modules.assessment.repositories.CandidateTestResultRepository;
import com.talentpredict.modules.user.entities.Profile;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.ProfileRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j

public class AssessmentPersistenceService {

    private final CandidateTestResultRepository candidateTestResultRepository;
    private final CandidateBadgeRepository candidateBadgeRepository;
    private final ProfileRepository profileRepository;

    @Transactional
    public void persistMcqEvaluation(User user, JsonNode result) {
        CandidateTestResult row = CandidateTestResult.builder()
                .user(user)
                .overallScore(result.path("real_score").asInt())
                .skillScoresJson(result.path("skill_scores").toString())

                .passed(result.path("passed").asBoolean(false))
                .testType(TestType.MCQ)
                .build();
        candidateTestResultRepository.save(row);

        Profile profile = profileRepository.findByUser_Id(user.getId()).orElse(null);
        if (profile != null) {
            profile.setRealScore(result.path("real_score").asInt());
            profile.setSkillRealScoresJson(result.path("skill_scores").toString());
            profile.setTestPassed(result.path("passed").asBoolean(false));
            profile.setTestTakenAt(Instant.now());

            if (profile.getPublicSlug() == null || profile.getPublicSlug().isBlank()) {
                profile.setPublicSlug("p-" + UUID.randomUUID().toString().substring(0, 8));
            }
            profileRepository.save(profile);
        }

        JsonNode scores = result.path("skill_scores");
        if (scores.isObject()) {
            scores.fields().forEachRemaining(entry -> {
                String skill = entry.getKey();
                int sc = entry.getValue().asInt(0);
                if (sc >= 70) {
                    upsertBadge(user, skill, sc);
                }
            });
        }

    }

    private void upsertBadge(User user, String skill, int score) {
        CandidateBadge b = candidateBadgeRepository
                .findByUser_IdAndSkillIgnoreCase(user.getId(), skill)
                .orElse(CandidateBadge.builder().user(user).skill(skill).build());
        b.setScore(score);
        b.setIssuedAt(Instant.now());
        candidateBadgeRepository.save(b);
    }

}
