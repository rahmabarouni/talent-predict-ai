package com.talentpredict.modules.ai.services;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.talentpredict.modules.ai.dto.SoftSkillsAnalysisRequestDto;
import com.talentpredict.modules.ai.repositories.PredictionRepository;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.ProfileRepository;
import com.talentpredict.modules.user.repositories.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Periodic reevaluation scheduler.
 * - 30 days: sends reminder email to user
 * - 90 days: triggers automatic reevaluation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SoftSkillsSchedulerService {

    private final UserRepository userRepo;
    private final PredictionRepository predictionRepo;
    private final ProfileRepository profileRepo;
    private final EmailService emailService;
    private final SoftSkillsService softSkillsService;

    @Value("${scheduler.softskills.enabled:true}")
    private boolean schedulerEnabled;

    @Value("${scheduler.softskills.inactive-days-reminder:30}")
    private int reminderDays;

    @Value("${scheduler.softskills.inactive-days-reevaluation:90}")
    private int autoReevalDays;

    @Scheduled(cron = "${scheduler.softskills.cron:0 0 9 * * MON-FRI}")
    public void checkAndNotify() {
        if (!schedulerEnabled) {
            log.info("Soft skills scheduler is disabled.");
            return;
        }

        log.info("Running soft skills reevaluation check...");

        LocalDateTime reminderThreshold    = LocalDateTime.now().minusDays(reminderDays);
        LocalDateTime autoReevalThreshold  = LocalDateTime.now().minusDays(autoReevalDays);

        userRepo.findAll().forEach(user -> {
            predictionRepo
                .findFirstByUserIdOrderByDatePredictionDesc(user.getId())
                .ifPresent(last -> {
                    LocalDateTime lastDate = last.getDatePrediction();

                    if (lastDate.isBefore(autoReevalThreshold)) {
                        log.info("Auto reevaluation for userId={}", user.getId());
                        softSkillsService.reevaluate(buildDefaultRequest(user), user.getId());

                    } else if (lastDate.isBefore(reminderThreshold)) {
                        log.info("Sending reminder to userId={}", user.getId());
                        emailService.sendSoftSkillsReminder(user.getEmail(), user.getFirstName());
                    }
                });
        });

        log.info("Reevaluation check completed.");
    }

    private SoftSkillsAnalysisRequestDto buildDefaultRequest(User user) {
        SoftSkillsAnalysisRequestDto dto = new SoftSkillsAnalysisRequestDto();
        dto.setFullName(user.getFirstName() + " " + user.getLastName());
        dto.setEmail(user.getEmail());
        
        profileRepo.findByUser_Id(user.getId()).ifPresent(p -> {
            dto.setGithubUsername(extractGithubUsername(p.getGithubUrl()));
            dto.setLinkedinUrl(p.getLienLinkedin());
        });
        
        return dto;
    }

    private String extractGithubUsername(String githubUrl) {
        if (githubUrl == null || githubUrl.isBlank()) return null;
        String[] parts = githubUrl.split("/");
        return parts[parts.length - 1];
    }
}
