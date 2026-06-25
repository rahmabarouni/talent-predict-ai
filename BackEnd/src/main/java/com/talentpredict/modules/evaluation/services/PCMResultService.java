package com.talentpredict.modules.evaluation.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.talentpredict.modules.user.entities.Profile;
import com.talentpredict.modules.evaluation.entities.PCMResult;
import com.talentpredict.modules.evaluation.repositories.PCMResultRepository;
import com.talentpredict.shared.exception.ResourceNotFoundException;

@Service

public class PCMResultService {
    
    private final PCMResultRepository pcmResultRepository;
    
    public PCMResultService(PCMResultRepository pcmResultRepository) {
        this.pcmResultRepository = pcmResultRepository;
    }
    
    public PCMResult createPCMResult(PCMResult pcmResult) {
        pcmResult.setDateEvaluation(LocalDateTime.now());
        return pcmResultRepository.save(pcmResult);
    }
    
    public PCMResult updatePCMResult(UUID id, PCMResult pcmResultDetails) {
        PCMResult pcmResult = pcmResultRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PCMResult not found with id: " + id));
        
        pcmResult.setTypePCM(pcmResultDetails.getTypePCM());
        pcmResult.setScoreTravail(pcmResultDetails.getScoreTravail());
        pcmResult.setScoreSecondaire(pcmResultDetails.getScoreSecondaire());
        pcmResult.setScoreReactif(pcmResultDetails.getScoreReactif());
        pcmResult.setScoreRebelle(pcmResultDetails.getScoreRebelle());
        pcmResult.setDateEvaluation(LocalDateTime.now());

        return pcmResultRepository.save(pcmResult);
    }
    
    public PCMResult getPCMResultById(UUID id) {
        return pcmResultRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("PCMResult not found with id: " + id));
    }
    
    public List<PCMResult> getPCMResultsByProfil(Profile profile) {
        return pcmResultRepository.findByProfile(profile);
    }
    
    public List<PCMResult> getAllPCMResults() {
        return pcmResultRepository.findAll();
    }
    
    public void deletePCMResult(UUID id) {
        pcmResultRepository.deleteById(id);
    }
}
