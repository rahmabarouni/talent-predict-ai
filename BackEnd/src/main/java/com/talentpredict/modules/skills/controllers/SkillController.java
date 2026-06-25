package com.talentpredict.modules.skills.controllers;

import com.talentpredict.modules.skills.dto.SkillDto;
import com.talentpredict.modules.skills.entities.Skill;
import com.talentpredict.modules.skills.services.SkillService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;

    @PostMapping("/accounts/{userId}")
    @PreAuthorize("#userId == principal.user.id or hasRole('ADMIN')")
    public ResponseEntity<SkillDto.Response> creerSkill(
            @PathVariable UUID userId,
            @Valid @RequestBody SkillDto.CreateRequest createRequest) {
        SkillDto.Response response = skillService.creerSkill(userId, createRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/accounts/{userId}")
    @PreAuthorize("#userId == principal.user.id or hasRole('ADMIN')")
    public ResponseEntity<List<SkillDto.Response>> getSkillsByUser(@PathVariable UUID userId) {
        List<SkillDto.Response> skills = skillService.getSkillsByUser(userId);
        return ResponseEntity.ok(skills);
    }

    @GetMapping("/accounts/{userId}/type/{type}")
    @PreAuthorize("#userId == principal.user.id or hasRole('ADMIN')")
    public ResponseEntity<List<SkillDto.Response>> getSkillsByType(
            @PathVariable UUID userId,
            @PathVariable Skill.TypeSkill type) {
        List<SkillDto.Response> skills = skillService.getSkillsByUserAndType(userId, type);
        return ResponseEntity.ok(skills);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @skillService.getSkillById(#id).userId == principal.user.id")
    public ResponseEntity<SkillDto.Response> getSkillById(@PathVariable UUID id) {
        SkillDto.Response response = skillService.getSkillById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{skillId}/valider")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SkillDto.Response> validerSkill(@PathVariable UUID skillId) {
        SkillDto.Response response = skillService.validerSkill(skillId);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{skillId}")
    @PreAuthorize("hasRole('ADMIN') or @skillService.getSkillById(#skillId).userId == principal.user.id")
    public ResponseEntity<Void> supprimerSkill(@PathVariable UUID skillId) {
        skillService.supprimerSkill(skillId);
        return ResponseEntity.noContent().build();
    }
}
