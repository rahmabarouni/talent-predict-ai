package com.talentpredict.modules.ai.services;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI Service - Wrapper for AI analysis
 * Uses Ollama/Local LLM for text generation
 */
@Service
@Slf4j
public class OpenAIService {

    /**
     * Generate prediction analysis using LLM
     * @param profile User profile data
     * @return AI-generated prediction text
     */
    public String genererPrediction(String profile) {
        log.info("Generating prediction for profile");
        try {
            // For now, return a rich template response
            // In production, this would call an LLM API or a Python microservice
            StringBuilder sb = new StringBuilder();
            sb.append("### Analyse de Profil IA TalentPredict\n\n");
            sb.append("Basé sur les données récoltées, votre profil présente une solide orientation technique.\n");
            sb.append("Points forts identifiés : Adaptabilité et résolution de problèmes complexes.\n\n");
            sb.append("--- Recommandations ---\n\n");
            sb.append("Recommandations Soft Skills :\n");
            sb.append("- Développer votre leadership communicationnel pour les réunions d'équipe.\n");
            sb.append("- Approfondir la gestion du temps en mode projet agile.\n\n");
            sb.append("Recommandations Tech Skills :\n");
            sb.append("- Explorer les architectures micro-services avec Spring Boot et Docker.\n");
            sb.append("- Renforcer vos bases en TypeScript pour les frameworks modernes.\n");
            
            return sb.toString();
        } catch (Exception e) {
            log.error("Error generating prediction", e);
            return "Désolé, une erreur technique est survenue lors de l'analyse de votre profil. Recommandations : réessayez plus tard.";
        }
    }

    /**
     * Analyze personality test responses
     * @param typeTest Type of personality test
     * @param responses Test responses JSON
     * @return AI-generated analysis of personality
     */
    public String analyserTestPersonnalite(String typeTest, String responses) {
        log.info("Analyzing personality test: {}", typeTest);
        try {
            // For now, return a template response
            // In production, this would call Ollama or OpenAI
            return "Analyse personnalité: " + typeTest;
        } catch (Exception e) {
            log.error("Error analyzing personality test", e);
            return "Erreur lors de l'analyse du test de personnalité";
        }
    }
}
