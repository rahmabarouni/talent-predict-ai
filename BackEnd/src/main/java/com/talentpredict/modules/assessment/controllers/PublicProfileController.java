package com.talentpredict.modules.assessment.controllers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.talentpredict.modules.skills.entities.Skill;
import com.talentpredict.modules.skills.repositories.SkillRepository;
import com.talentpredict.modules.user.entities.Profile;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.modules.user.repositories.ProfileRepository;

import lombok.RequiredArgsConstructor;

@RestController("assessmentPublicProfileController")
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicProfileController {

    private final ProfileRepository profileRepository;
    private final SkillRepository skillRepository;

    @GetMapping("/profiles/{slug}")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> publicProfile(@PathVariable String slug) {
        Profile p = profileRepository.findByPublicSlug(slug)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Profile not found"));
        User u = p.getUser();
        List<Skill> skills = skillRepository.findByUserId(u.getId());
        Map<String, Object> out = new HashMap<>();
        out.put("publicSlug", p.getPublicSlug());
        out.put("firstName", u.getFirstName());
        out.put("lastName", u.getLastName());
        out.put("title", p.getTitreProfessionnel());
        out.put("bio", p.getDescription());
        out.put("githubUrl", p.getGithubUrl());
        out.put("linkedinUrl", p.getLienLinkedin());
        out.put("realScore", p.getRealScore());
        out.put("testPassed", p.getTestPassed());
        out.put("skills", skills.stream()
                .map(s -> Map.of(
                        "nom", s.getNom(),
                        "niveau", s.getNiveau() != null ? s.getNiveau() : Integer.valueOf(0)))
                .collect(Collectors.toList()));
        return ResponseEntity.ok(out);
    }

}
