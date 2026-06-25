package com.talentpredict.modules.assessment.services;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.talentpredict.modules.assessment.dto.CampaignDto;
import com.talentpredict.modules.assessment.entities.Campaign;
import com.talentpredict.modules.assessment.repositories.CampaignRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;

    @Transactional(readOnly = true)
    public List<CampaignDto> listCampaigns() {
        return campaignRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public CampaignDto findById(UUID id) {
        return campaignRepository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
    }

    @Transactional
    public CampaignDto saveCampaign(CampaignDto request) {
        Campaign entity = request.id() != null
                ? campaignRepository.findById(request.id()).orElseGet(Campaign::new)
                : new Campaign();

        if (entity.getId() == null && request.id() != null) {
            entity.setId(request.id());
        }

        apply(entity, request);
        Campaign saved = campaignRepository.save(entity);
        return toDto(saved);
    }

    @Transactional
    public CampaignDto update(UUID id, CampaignDto request) {
        Campaign entity = campaignRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found"));
        
        apply(entity, request);
        Campaign saved = campaignRepository.save(entity);
        return toDto(saved);
    }

    @Transactional
    public void delete(UUID id) {
        if (!campaignRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Campaign not found");
        }
        campaignRepository.deleteById(id);
    }

    private void apply(Campaign entity, CampaignDto request) {
        entity.setName(trimOrNull(request.name()));
        entity.setTemplateId(trimOrNull(request.templateId()));
        entity.setTemplateName(trimOrNull(request.templateName()));
        entity.setChannel(trimOrNull(request.channel()));
        entity.setTargetGroup(trimOrNull(request.targetGroup()));
        entity.setStatus(trimOrNull(request.status()));
        entity.setScheduledAt(request.scheduledAt());
        entity.setRecipientCount(defaultInt(request.recipientCount()));
        entity.setSentCount(defaultInt(request.sentCount()));
        entity.setFailedCount(defaultInt(request.failedCount()));
        entity.setOpenRate(request.openRate());
        entity.setClickRate(request.clickRate());
        entity.setPaused(defaultBool(request.isPaused()));
    }

    private CampaignDto toDto(Campaign entity) {
        return new CampaignDto(
                entity.getId(),
                entity.getName(),
                entity.getTemplateId(),
                entity.getTemplateName(),
                entity.getChannel(),
                entity.getTargetGroup(),
                entity.getRecipientCount(),
                entity.getStatus(),
                entity.getScheduledAt(),
                entity.getSentCount(),
                entity.getFailedCount(),
                entity.getOpenRate(),
                entity.getClickRate(),
                entity.getPaused());
    }

    private String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Integer defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Boolean defaultBool(Boolean value) {
        return Boolean.TRUE.equals(value);
    }
}
