package com.talentpredict.modules.evaluation.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReevaluationScheduler {

    /**
     * S'exécute tous les jours à 2h du matin.
     * Cherche les tests vieux de plus de 90 jours et déclenche une notification
     * pour demander une réévaluation de l'utilisateur.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduleReevaluation() {
        log.info("Démarrage du job de vérification des réévaluations...");
        
        // C'est un mock - dans une vraie DB on ferait findByDateTestBefore()
        // List<PersonalityTest> testsToReevaluate = testRepository.findByDateTestBefore(ninetyDaysAgo);
        
        log.info("Job de réévaluation terminé. 0 utilisateurs notifiés.");
    }
}
