package com.talentpredict.modules.ai.services;

import java.io.InputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.talentpredict.modules.skills.dto.SkillDto;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service d'analyse de CV PDF.
 *
 * Deux modes supportés:
 * 1. Upload direct (MultipartFile) → fichier envoyé depuis le frontend
 * 2. URL publique (String) → PDF déjà hébergé (Cloudinary, S3, Drive public,
 * etc.)
 *
 * Le texte est extrait avec Apache PDFBox puis envoyé à Claude via
 * OpenRouterService.
 *
 * DÉPENDANCE REQUISE dans pom.xml:
 * <dependency>
 * <groupId>org.apache.pdfbox</groupId>
 * <artifactId>pdfbox</artifactId>
 * <version>3.0.3</version>
 * </dependency>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CvAnalysisService {

    private final OpenRouterService openRouterService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8081}")
    private String appBaseUrl;

    // Limite du texte envoyé à Claude (évite de dépasser la fenêtre de contexte)
    private static final int MAX_TEXTE_CV = 4000;

    // 3EZ2EUZIAE K================================================================
    // CAS 1 : CV uploadé directement (MultipartFile depuis le frontend)
    // ================================================================

    public OpenRouterService.FullProfileExtraction analyserCvFileComplet(MultipartFile file) {
        try {
            log.info("📄 Analyse CV complète depuis fichier uploadé: {}", file.getOriginalFilename());

            if (file.isEmpty()) {
                log.warn("⚠️ Fichier CV vide");
                return new OpenRouterService.FullProfileExtraction();
            }

            String texte = extraireTextePDF(file.getInputStream());
            if (texte.isBlank()) {
                log.warn(" Aucun texte extrait du CV (PDF scanné ou protégé?)");
                return new OpenRouterService.FullProfileExtraction();
            }

            log.info(" {} caractères extraits du CV", texte.length());
            return openRouterService.extraireProfilCompletDuTexteCV(texte);

        } catch (Exception e) {
            log.error(" Erreur lecture CV fichier: {}", e.getMessage());
            return new OpenRouterService.FullProfileExtraction();
        }
    }

    public List<SkillDto.CreateRequest> analyserCvFile(MultipartFile file) {
        return analyserCvFileComplet(file).getSkills();
    }

    // ================================================================
    // CAS 2 : CV depuis une URL publique (déjà stocké quelque part)
    // ================================================================

    public OpenRouterService.FullProfileExtraction analyserCvUrlComplet(String cvUrl) {
        OpenRouterService.FullProfileExtraction fallback = new OpenRouterService.FullProfileExtraction();
        if (cvUrl == null || cvUrl.isBlank())
            return fallback;

        try {
            log.info("Analyse CV complète depuis URL: {}", cvUrl);

            String texte;

            // If the URL points to our own server, read directly from disk
            if (cvUrl.startsWith(appBaseUrl + "/uploads/")) {
                String relativePath = cvUrl.substring((appBaseUrl + "/uploads/").length());
                Path filePath = Paths.get(uploadDir).toAbsolutePath().normalize().resolve(relativePath);
                log.info("CV local detecte, lecture directe: {}", filePath);

                if (!Files.exists(filePath)) {
                    log.warn("Fichier CV introuvable sur le disque: {}", filePath);
                    return fallback;
                }

                try (InputStream stream = Files.newInputStream(filePath)) {
                    texte = extraireTextePDF(stream);
                }
            } else {
                // External URL - fetch via HTTP
                java.net.URI uri = java.net.URI.create(cvUrl);
                URLConnection connection = uri.toURL().openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(15_000);
                connection.setRequestProperty("User-Agent", "TalentPredict-App/1.0");

                try (InputStream stream = connection.getInputStream()) {
                    texte = extraireTextePDF(stream);
                }
            }

            if (texte.isBlank()) {
                log.warn("Aucun texte extrait du CV (PDF scanne?)");
                return fallback;
            }

            log.info("{} caracteres extraits du CV", texte.length());
            return openRouterService.extraireProfilCompletDuTexteCV(texte);

        } catch (Exception e) {
            log.error("Erreur analyse CV: {} - {}", cvUrl, e.getMessage(), e);
            return fallback;
        }
    }

    public List<SkillDto.CreateRequest> analyserCvUrl(String cvUrl) {
        return analyserCvUrlComplet(cvUrl).getSkills();
    }

    // ================================================================
    // HELPER INTERNE : Extraction du texte depuis un PDF (PDFBox)
    // ================================================================

    private String extraireTextePDF(InputStream inputStream) throws Exception {
        try (PDDocument document = PDDocument.load(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String texte = stripper.getText(document);

            // Nettoyage basique du texte
            texte = texte.replaceAll("\\s+", " ").trim();

            // Limite pour Claude
            return texte.length() > MAX_TEXTE_CV
                    ? texte.substring(0, MAX_TEXTE_CV)
                    : texte;
        }
    }
}