package com.talentpredict.modules.skills.services;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.talentpredict.modules.auth.services.AuthServiceImpl;
import com.talentpredict.modules.skills.dto.SkillDto;
import com.talentpredict.modules.skills.entities.Skill;
import com.talentpredict.modules.skills.repositories.SkillRepository;
import com.talentpredict.modules.user.entities.User;
import com.talentpredict.shared.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

/**
 * Skills Module - Skill Management Service
 * Core functionality: Manage account skills, validate competencies, and track skill development
 */
@Service
@RequiredArgsConstructor

public class SkillService {
    
    private final SkillRepository skillRepository;
    private final AuthServiceImpl authServiceImpl;
    
    @Transactional
    public SkillDto.Response creerSkill(UUID accountId, SkillDto.CreateRequest createRequest) {
        User user = authServiceImpl.getUserById(accountId);
        
        Skill skill = new Skill();
        skill.setUser(user);
        skill.setNom(createRequest.getNom());
        skill.setType(createRequest.getType());
        skill.setNiveau(createRequest.getNiveau());
        skill.setDescription(createRequest.getDescription());
        skill.setSource(createRequest.getSource());
        skill.setValidee(false);
        
        Skill saved = skillRepository.save(skill);
        return convertToResponse(saved);
    }

    public SkillDto.Response getSkillById(UUID skillId) {
        Skill skill = skillRepository.findById(skillId)
            .orElseThrow(() -> new ResourceNotFoundException("Skill non trouvé avec l'ID: " + skillId));
        return convertToResponse(skill);
    }
    
    public List<SkillDto.Response> getSkillsByUser(UUID userId) {
        return skillRepository.findByUserId(userId)
            .stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
    
    public List<SkillDto.Response> getSkillsByUserAndType(UUID userId, Skill.TypeSkill type) {
        return skillRepository.findByUserIdAndType(userId, type)
            .stream()
            .map(this::convertToResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional
    public SkillDto.Response validerSkill(UUID skillId) {
        Skill skill = skillRepository.findById(skillId)
            .orElseThrow(() -> new ResourceNotFoundException("Skill non trouvé avec l'ID: " + skillId));
        skill.setValidee(true);
        return convertToResponse(skillRepository.save(skill));
    }
    
    @Transactional
    public void supprimerSkill(UUID skillId) {
        if (!skillRepository.existsById(skillId)) {
            throw new ResourceNotFoundException("Skill non trouvé avec l'ID: " + skillId);
        }
        skillRepository.deleteById(skillId);
    }

    @Transactional
    public void supprimerSkillsParUser(UUID userId) {
        skillRepository.deleteByUserId(userId);
    }

    @Transactional
    public int remplacerSkillsParUser(UUID userId, List<SkillDto.CreateRequest> createRequests) {
        skillRepository.deleteByUserId(userId);

        if (createRequests == null || createRequests.isEmpty()) {
            return 0;
        }

        User user = authServiceImpl.getUserById(userId);
        Map<String, Skill> deduplicated = new LinkedHashMap<>();

        for (SkillDto.CreateRequest req : createRequests) {
            if (req == null || req.getNom() == null || req.getNom().isBlank() || req.getType() == null) {
                continue;
            }

            String normalizedName = req.getNom().trim();
            int level = req.getNiveau() == null ? 1 : Math.max(1, Math.min(5, req.getNiveau()));
            String key = normalizedName.toLowerCase() + "::" + req.getType().name();

            Skill existing = deduplicated.get(key);
            if (existing != null && existing.getNiveau() >= level) {
                continue;
            }

            Skill skill = new Skill();
            skill.setUser(user);
            skill.setNom(normalizedName);
            skill.setType(req.getType());
            skill.setNiveau(level);
            skill.setDescription(req.getDescription());
            skill.setSource(req.getSource());
            skill.setValidee(false);
            deduplicated.put(key, skill);
        }

        if (deduplicated.isEmpty()) {
            return 0;
        }

        return skillRepository.saveAll(new ArrayList<>(deduplicated.values())).size();
    }
    
    private SkillDto.Response convertToResponse(Skill skill) {
        SkillDto.Response response = new SkillDto.Response();
        response.setId(skill.getId());
        if (skill.getUser() != null) {
            response.setUserId(skill.getUser().getId());
        }
        response.setNom(skill.getNom());
        response.setType(skill.getType());
        response.setNiveau(skill.getNiveau());
        response.setDescription(skill.getDescription());
        response.setSource(skill.getSource());
        response.setDateEvaluation(skill.getDateEvaluation());
        response.setValidee(skill.getValidee());
        return response;
    }
}
