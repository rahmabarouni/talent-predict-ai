package com.talentpredict.shared.sms;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class SmsService {

    @Value("${twilio.account-sid:}")
    private String twilioAccountSid;

    @Value("${twilio.auth-token:}")
    private String twilioAuthToken;

    @Value("${twilio.from-number:}")
    private String twilioFromNumber;

    @Value("${twilio.whatsapp-from:}")
    private String twilioWhatsappFrom;

    @Value("${twilio.whatsapp-number:}")
    private String twilioWhatsappNumber;

    public void send2FACode(String toPhone, String code) {
        if (toPhone == null || toPhone.isBlank()) {
            return;
        }

        if (twilioAccountSid != null && !twilioAccountSid.isBlank()
                && twilioAuthToken != null && !twilioAuthToken.isBlank()
                && twilioFromNumber != null && !twilioFromNumber.isBlank()) {
            try {
                String message = "Votre code de vérification TalentPredict est : " + code;
                String body = "To=" + URLEncoder.encode(toPhone, StandardCharsets.UTF_8)
                        + "&From=" + URLEncoder.encode(twilioFromNumber, StandardCharsets.UTF_8)
                        + "&Body=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

                String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";
                String auth = twilioAccountSid + ":" + twilioAuthToken;
                String basic = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .header("Authorization", "Basic " + basic)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
                log.info("2FA SMS sent to {}", toPhone);
            } catch (Exception ex) {
                log.warn("Failed to send 2FA SMS to {}", toPhone, ex);
            }
        } else {
            log.info("SMS not configured — 2FA code for {}: {}", toPhone, code);
        }
    }

    public void sendResetToken(String toPhone, String resetLink, String token) {
        if (toPhone == null || toPhone.isBlank()) {
            log.info("SMS reset skipped: no phone provided");
            return;
        }

        // Send via Twilio if configured; otherwise log and skip
        if (twilioAccountSid != null && !twilioAccountSid.isBlank()
                && twilioAuthToken != null && !twilioAuthToken.isBlank()
                && twilioFromNumber != null && !twilioFromNumber.isBlank()) {
            try {
                String message = "Lien de réinitialisation: " + resetLink;
                String body = "To=" + URLEncoder.encode(toPhone, StandardCharsets.UTF_8)
                        + "&From=" + URLEncoder.encode(twilioFromNumber, StandardCharsets.UTF_8)
                        + "&Body=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

                String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";
                String auth = twilioAccountSid + ":" + twilioAuthToken;
                String basic = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Accept", "application/json")
                        .header("Authorization", "Basic " + basic)
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();

                HttpResponse<String> response = HttpClient.newHttpClient()
                        .send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 201 || response.statusCode() == 200) {
                    log.info("Password reset SMS sent to {} via Twilio", toPhone);
                } else {
                    log.warn("Twilio returned {} — body: {}", response.statusCode(), response.body());
                }
                return;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                log.warn("SMS sending interrupted for {} — token fallback: {}", toPhone, token, ex);
                return;
            } catch (IOException ex) {
                log.warn("Failed to send SMS via Twilio to {} — token fallback: {}", toPhone, token, ex);
                return;
            }
        }

        log.info("SMS not configured — token for {}: {}", toPhone, token);
    }

    /**
     * Send a WhatsApp message via Twilio. `toPhone` should be the E.164 number (e.g. +337...) or already
     * prefixed with "whatsapp:". The configured `twilio.whatsapp-from` or `twilio.from-number` will be used
     * as the sender and must be the Twilio WhatsApp-enabled sender (e.g. whatsapp:+1415...)
     */
    public void sendWhatsAppMessage(String toPhone, String message) {
        if (toPhone == null || toPhone.isBlank()) {
            log.info("WhatsApp message skipped: no phone provided");
            return;
        }

        if (twilioAccountSid == null || twilioAccountSid.isBlank()
                || twilioAuthToken == null || twilioAuthToken.isBlank()) {
            log.warn("Twilio not configured — cannot send WhatsApp to {}", toPhone);
            return;
        }

        String from = twilioWhatsappFrom != null && !twilioWhatsappFrom.isBlank()
            ? twilioWhatsappFrom
            : (twilioWhatsappNumber != null && !twilioWhatsappNumber.isBlank()
                ? twilioWhatsappNumber
                : twilioFromNumber);

        if (from == null || from.isBlank()) {
            log.warn("Twilio WhatsApp 'from' not configured — cannot send to {}", toPhone);
            return;
        }

        try {
            String to = toPhone.startsWith("whatsapp:") ? toPhone : "whatsapp:" + toPhone;
            String fromPref = from.startsWith("whatsapp:") ? from : "whatsapp:" + from;

            String body = "To=" + URLEncoder.encode(to, StandardCharsets.UTF_8)
                    + "&From=" + URLEncoder.encode(fromPref, StandardCharsets.UTF_8)
                    + "&Body=" + URLEncoder.encode(message, StandardCharsets.UTF_8);

            String url = "https://api.twilio.com/2010-04-01/Accounts/" + twilioAccountSid + "/Messages.json";
            String auth = twilioAccountSid + ":" + twilioAuthToken;
            String basic = java.util.Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                log.info("WhatsApp message sent to {} via Twilio", toPhone);
            } else {
                log.warn("Twilio WhatsApp returned {} — body: {}", response.statusCode(), response.body());
            }

        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("WhatsApp sending interrupted for {}", toPhone, ex);
        } catch (IOException ex) {
            log.warn("Failed to send WhatsApp to {}", toPhone, ex);
        }
    }
}