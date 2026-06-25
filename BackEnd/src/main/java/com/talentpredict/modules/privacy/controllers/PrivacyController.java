package com.talentpredict.modules.privacy.controllers;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talentpredict.modules.privacy.dto.PrivacyDto;
import com.talentpredict.modules.privacy.services.PrivacyService;
import com.talentpredict.shared.security.UserDetailsImpl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/privacy")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class PrivacyController {

    private final PrivacyService privacyService;
    private final ObjectMapper objectMapper;

    @GetMapping("/settings")
    public ResponseEntity<PrivacyDto.SettingsResponse> getSettings(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(privacyService.getSettings(principal.getUser()));
    }

    @PutMapping("/settings")
    public ResponseEntity<PrivacyDto.SettingsResponse> updateSettings(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody PrivacyDto.SettingsUpdateRequest request) {
        return ResponseEntity.ok(privacyService.updateSettings(principal.getUser(), request));
    }

    @GetMapping("/export")
    public ResponseEntity<Map<String, Object>> exportData(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(privacyService.exportUserData(principal.getUser()));
    }

    @GetMapping("/export/download")
    public ResponseEntity<String> exportDataDownload(
            @AuthenticationPrincipal UserDetailsImpl principal) throws JsonProcessingException {
        Map<String, Object> export = privacyService.exportUserData(principal.getUser());
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(export);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"talentpredict-my-data.json\"")
                .body(json);
    }

    @PostMapping("/request-deletion")
    public ResponseEntity<PrivacyDto.MessageResponse> requestDeletion(
            @AuthenticationPrincipal UserDetailsImpl principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(privacyService.requestAccountDeletion(
                principal.getUser(),
                resolveClientIp(httpRequest)));
    }

    @PostMapping("/apply-retention")
    public ResponseEntity<PrivacyDto.RetentionApplyResponse> applyRetention(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(privacyService.applyRetention(principal.getUser()));
    }

    @PostMapping("/delete-account")
    public ResponseEntity<PrivacyDto.MessageResponse> deleteAccount(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @Valid @RequestBody PrivacyDto.DeleteAccountRequest request,
            HttpServletRequest httpRequest) {
        PrivacyDto.MessageResponse response = privacyService.deleteAccount(
                principal.getUser(),
                request.getConfirmPhrase(),
                resolveClientIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
