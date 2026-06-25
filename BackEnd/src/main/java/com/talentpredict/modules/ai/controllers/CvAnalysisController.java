package com.talentpredict.modules.ai.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.talentpredict.modules.ai.services.CvAnalysisService;
import com.talentpredict.modules.skills.dto.SkillDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cv")
@RequiredArgsConstructor
public class CvAnalysisController {

    private final CvAnalysisService cvAnalysisService;

    @PostMapping("/analyze/file")
    @PreAuthorize("hasRole('USER') or hasRole('ADMIN')")
    public ResponseEntity<List<SkillDto.CreateRequest>> analyzeCvFile(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(cvAnalysisService.analyserCvFile(file));
    }

}
