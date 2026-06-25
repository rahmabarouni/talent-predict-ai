package com.talentpredict.modules.assessment.services;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.talentpredict.modules.assessment.dto.CampaignEmailRequest;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.UserRepository;
import com.talentpredict.shared.exception.BadRequestException;
import com.talentpredict.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignEmailService {

    private final UserRepository userRepository;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    public Map<String, Object> sendCampaignEmail(CampaignEmailRequest request) {
        UUID userId = request.getUserId();
        if (userId == null) {
            throw new BadRequestException("userId is required.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate not found for id: " + userId));

        if (!hasText(user.getEmail())) {
            throw new BadRequestException("Selected candidate does not have a valid email.");
        }

        String normalizedUrl = normalizeUrl(request.getTargetUrl());
        boolean generatedFromContext = !hasText(request.getBody());

        String subject = hasText(request.getSubject())
                ? request.getSubject().trim()
                : buildSubject(request.getCampaignContext());

        String body = generatedFromContext
                ? buildBody(user, request.getCandidateUsername(), request.getCampaignContext(), normalizedUrl)
                : request.getBody().trim();

        if (mailSender == null) {
            throw new BadRequestException("Mail service is not configured on backend.");
        }

        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setTo(user.getEmail());
            mail.setSubject(subject);
            mail.setText(body);
            mailSender.send(mail);
        } catch (MailException ex) {
            log.error("Failed to send campaign email to {}", user.getEmail(), ex);
            throw new BadRequestException("Failed to send campaign email: " + ex.getMessage());
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "sent");
        response.put("recipientEmail", user.getEmail());
        response.put("subject", subject);
        response.put("generatedFromContext", generatedFromContext);
        return response;
    }

    private String normalizeUrl(String targetUrl) {
        String trimmed = targetUrl == null ? "" : targetUrl.trim();
        if (!hasText(trimmed)) {
            throw new BadRequestException("targetUrl is required.");
        }

        String withProtocol = trimmed.matches("(?i)^https?://.*") ? trimmed : "https://" + trimmed;
        try {
            return URI.create(withProtocol).toString();
        } catch (Exception ex) {
            throw new BadRequestException("targetUrl is not a valid URL.");
        }
    }

    private String buildSubject(String campaignContext) {
        String summary = campaignContext == null ? "" : campaignContext.trim().replaceAll("\\s+", " ");
        if (!hasText(summary)) {
            return "TalentPredict Campaign Update";
        }
        if (summary.length() <= 64) {
            return "TalentPredict Campaign: " + summary;
        }
        return "TalentPredict Campaign: " + summary.substring(0, 61) + "...";
    }

    private String buildBody(User user, String candidateUsername, String campaignContext, String targetUrl) {
        String displayName = ((user.getFirstName() == null ? "" : user.getFirstName())
                + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();

        if (!hasText(displayName)) {
            displayName = user.getEmail();
        }

        String username = hasText(candidateUsername)
                ? candidateUsername.trim()
                : user.getEmail().split("@")[0];

        String contextText = hasText(campaignContext)
                ? campaignContext.trim()
                : "Please check the campaign details below.";

        return String.join("\n",
                "Hi " + displayName + ",",
                "",
                "Username: " + username,
                "",
                "Campaign context:",
                contextText,
                "",
                "Reference URL: " + targetUrl,
                "",
                "Please review the link and proceed with the requested campaign action.",
                "",
                "Best regards,",
                "TalentPredict Admin Team");
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
