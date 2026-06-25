package com.talentpredict.modules.ai.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public void sendSoftSkillsReminder(String email, String firstName) {
        log.info("Sending soft skills reminder to {}", email);
        if (mailSender == null) {
            log.warn("Mail sender not configured, skipping email to {}", email);
            return;
        }

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(email);
            mail.setSubject("TalentPredict - Rappel d'évaluation des Soft Skills");
            mail.setText("Bonjour " + firstName + ",\n\n" +
                         "Cela fait 30 jours que vous n'avez pas mis à jour vos soft skills. " +
                         "Connectez-vous pour une nouvelle évaluation afin de maintenir votre profil à jour.\n\n" +
                         "Cordialement,\n" +
                         "L'équipe TalentPredict");
            mailSender.send(mail);
            log.info("Reminder email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send soft skills reminder to {}: {}", email, e.getMessage());
        }
    }
}
