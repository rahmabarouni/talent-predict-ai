package com.talentpredict.modules.assessment.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.talentpredict.modules.ai.entities.Prediction;
import com.talentpredict.modules.assessment.entities.CandidateTestResult;
import com.talentpredict.modules.skills.entities.Skill;
import com.talentpredict.modules.user.entities.Profile;
import com.talentpredict.modules.user.entities.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportGeneratorService {

    public byte[] buildPdfReport(User user, Profile profile, List<Skill> skills,
            List<CandidateTestResult> history, Prediction latestPrediction) throws IOException {
        String html = buildHtml(user, profile, skills, history, latestPrediction);
        try {
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (IOException | RuntimeException | LinkageError e) {
            log.error("HTML PDF render failed, fallback renderer will be used", e);
            return buildFallbackPdf(user, profile, skills, history, latestPrediction);
        }
    }

    private String buildHtml(User user, Profile profile, List<Skill> skills,
            List<CandidateTestResult> history, Prediction latestPrediction) {
        List<Skill> techSkills = new ArrayList<>();
        List<Skill> softSkills = new ArrayList<>();
        for (Skill s : skills) {
            if (s == null) {
                continue;
            }
            if (s.getType() == Skill.TypeSkill.SOFT) {
                softSkills.add(s);
            } else {
                techSkills.add(s);
            }
        }

        Integer latestTechScore = findLatestTechScore(history);
        String confidence = formatConfidence(latestPrediction != null ? latestPrediction.getScoreConfiance() : null);

        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><meta charset='UTF-8'><style>")
                .append("body{font-family:Arial,sans-serif;margin:0;padding:22px;color:#0f172a}")
                .append(".report-shell{border:1px solid #dbe4ef;border-radius:14px;padding:18px}")
                .append("h1{font-size:22px;margin:0 0 6px}")
                .append(".subtitle{font-size:12px;color:#475569;margin:0 0 14px}")
                .append(".section{margin-top:18px}")
                .append(".section h2{font-size:15px;color:#1d4ed8;margin:0 0 10px}")
                .append(".kpis{display:table;width:100%;table-layout:fixed;border-spacing:8px 0;margin:10px 0 12px}")
                .append(".kpi{display:table-cell;background:#f8fafc;border:1px solid #dbe4ef;border-radius:10px;padding:8px;vertical-align:top}")
                .append(".kpi .label{display:block;font-size:11px;color:#64748b}")
                .append(".kpi .value{display:block;font-size:18px;font-weight:700;color:#0f172a;margin-top:4px}")
                .append(".muted{color:#64748b;font-size:12px}")
                .append(".insight{border:1px solid #bfdbfe;background:#eff6ff;padding:10px;border-radius:10px;font-size:12px;line-height:1.45;margin-top:8px}")
                .append("table{border-collapse:collapse;width:100%;margin-top:6px}")
                .append("td,th{border:1px solid #dbe4ef;padding:6px;font-size:12px;text-align:left;vertical-align:top}")
                .append("th{background:#f1f5f9;font-weight:700}")
                .append(".empty{color:#94a3b8;font-style:italic}")
                .append("</style></head><body>");

        sb.append("<div class='report-shell'>");
        sb.append("<h1>TalentPredict - Soft + Tech Skills Report</h1>");
        sb.append("<p class='subtitle'>Combined overview of technical and soft capabilities for recruitment and development decisions.</p>");
        sb.append("<p><strong>Name:</strong> ")
                .append(escape(user.getFirstName())).append(" ").append(escape(user.getLastName())).append("</p>");
        if (profile != null && profile.getTitreProfessionnel() != null) {
            sb.append("<p><strong>Title:</strong> ").append(escape(profile.getTitreProfessionnel())).append("</p>");
        }

        sb.append("<div class='kpis'>");
        sb.append("<div class='kpi'><span class='label'>Technical skills</span><span class='value'>")
                .append(techSkills.size())
                .append("</span></div>");
        sb.append("<div class='kpi'><span class='label'>Soft skills</span><span class='value'>")
                .append(softSkills.size())
                .append("</span></div>");
        sb.append("<div class='kpi'><span class='label'>Latest technical test</span><span class='value'>")
                .append(latestTechScore != null ? latestTechScore + "%" : "n/a")
                .append("</span></div>");
        sb.append("<div class='kpi'><span class='label'>Soft prediction confidence</span><span class='value'>")
                .append(confidence)
                .append("</span></div>");
        sb.append("</div>");

        if (profile != null && profile.getRealScore() != null) {
            sb.append("<p class='muted'><strong>Profile latest test score:</strong> ").append(profile.getRealScore()).append("</p>");
        }

        sb.append("<div class='section'><h2>Soft skills AI insight</h2>");
        if (latestPrediction == null) {
            sb.append("<p class='empty'>No soft skills prediction available yet.</p>");
        } else {
            if (latestPrediction.getDatePrediction() != null) {
                sb.append("<p class='muted'><strong>Generated at:</strong> ")
                        .append(escape(latestPrediction.getDatePrediction().toString()))
                        .append("</p>");
            }

            if (latestPrediction.getAnalyse() != null && !latestPrediction.getAnalyse().isBlank()) {
                sb.append("<div class='insight'><strong>Analysis</strong><br/>")
                        .append(escapeMultiline(latestPrediction.getAnalyse()))
                        .append("</div>");
            }

            if (latestPrediction.getRecommandationSoft() != null && !latestPrediction.getRecommandationSoft().isBlank()) {
                sb.append("<div class='insight'><strong>Soft recommendation</strong><br/>")
                        .append(escapeMultiline(latestPrediction.getRecommandationSoft()))
                        .append("</div>");
            }

            if (latestPrediction.getRecommandationTech() != null && !latestPrediction.getRecommandationTech().isBlank()) {
                sb.append("<div class='insight'><strong>Technical recommendation</strong><br/>")
                        .append(escapeMultiline(latestPrediction.getRecommandationTech()))
                        .append("</div>");
            }
        }
        sb.append("</div>");

        sb.append("<div class='section'><h2>Technical skills</h2>");
        sb.append("<table><tr><th>Skill</th><th>Level (1-5)</th><th>Source</th></tr>");
        if (techSkills.isEmpty()) {
            sb.append("<tr><td colspan='3' class='empty'>No technical skills available.</td></tr>");
        }
        for (Skill s : techSkills) {
            sb.append("<tr><td>").append(escape(s.getNom())).append("</td><td>")
                    .append(s.getNiveau() != null ? s.getNiveau() : "")
                    .append("</td><td>")
                    .append(escape(s.getSource()))
                    .append("</td></tr>");
        }
        sb.append("</table></div>");

        sb.append("<div class='section'><h2>Soft skills</h2>");
        sb.append("<table><tr><th>Skill</th><th>Level (1-5)</th><th>Source</th></tr>");
        if (softSkills.isEmpty()) {
            sb.append("<tr><td colspan='3' class='empty'>No soft skills available.</td></tr>");
        }
        for (Skill s : softSkills) {
            sb.append("<tr><td>").append(escape(s.getNom())).append("</td><td>")
                    .append(s.getNiveau() != null ? s.getNiveau() : "")
                    .append("</td><td>")
                    .append(escape(s.getSource()))
                    .append("</td></tr>");
        }
        sb.append("</table></div>");

        sb.append("<div class='section'><h2>Technical test history</h2>");
        sb.append("<table><tr><th>Date</th><th>Type</th><th>Overall</th><th>Passed</th></tr>");
        if (history.isEmpty()) {
            sb.append("<tr><td colspan='4' class='empty'>No technical test history available.</td></tr>");
        }
        for (CandidateTestResult h : history) {
            sb.append("<tr><td>").append(h.getTakenAt() != null ? h.getTakenAt().toString() : "")
                    .append("</td><td>").append(h.getTestType() != null ? h.getTestType().name() : "TECH")
                    .append("</td><td>").append(h.getOverallScore() != null ? h.getOverallScore() : "")
                    .append("</td><td>").append(Boolean.TRUE.equals(h.getPassed()) ? "Yes" : "No")
                    .append("</td></tr>");
        }
        sb.append("</table></div>");
        sb.append("</div></body></html>");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String escapeMultiline(String value) {
        return escape(value).replace("\n", "<br/>");
    }

    private Integer findLatestTechScore(List<CandidateTestResult> history) {
        Integer fallback = null;
        for (CandidateTestResult item : history) {
            if (item == null) {
                continue;
            }

            if (fallback == null && item.getOverallScore() != null) {
                fallback = item.getOverallScore();
            }

            if (item.getTestType() == null || "TECH".equalsIgnoreCase(item.getTestType().name())) {
                if (item.getOverallScore() != null) {
                    return item.getOverallScore();
                }
            }
        }
        return fallback;
    }

    private static String formatConfidence(Double value) {
        if (value == null) {
            return "n/a";
        }

        double normalized = value <= 1.0 ? value * 100.0 : value;
        return Math.round(normalized) + "%";
    }

    private byte[] buildFallbackPdf(User user, Profile profile, List<Skill> skills,
            List<CandidateTestResult> history, Prediction latestPrediction) throws IOException {
        List<Skill> techSkills = new ArrayList<>();
        List<Skill> softSkills = new ArrayList<>();
        for (Skill s : skills) {
            if (s == null) {
                continue;
            }
            if (s.getType() == Skill.TypeSkill.SOFT) {
                softSkills.add(s);
            } else {
                techSkills.add(s);
            }
        }

        Integer latestTechScore = findLatestTechScore(history);

        List<String> lines = new ArrayList<>();
        lines.add("TalentPredict - Soft + Tech Skills Report");
        lines.add("");
        lines.add("Name: " + safe(user.getFirstName()) + " " + safe(user.getLastName()));
        if (profile != null && profile.getTitreProfessionnel() != null) {
            lines.add("Title: " + safe(profile.getTitreProfessionnel()));
        }
        if (profile != null && profile.getRealScore() != null) {
            lines.add("Profile latest test score: " + profile.getRealScore());
        }
        lines.add("Technical skills count: " + techSkills.size());
        lines.add("Soft skills count: " + softSkills.size());
        lines.add("Latest technical test score: " + (latestTechScore != null ? latestTechScore + "%" : "n/a"));

        if (latestPrediction != null) {
            lines.add("Soft prediction confidence: " + formatConfidence(latestPrediction.getScoreConfiance()));
            if (latestPrediction.getAnalyse() != null && !latestPrediction.getAnalyse().isBlank()) {
                lines.add("Soft AI analysis: " + clip(latestPrediction.getAnalyse(), 220));
            }
            if (latestPrediction.getRecommandationSoft() != null && !latestPrediction.getRecommandationSoft().isBlank()) {
                lines.add("Soft recommendation: " + clip(latestPrediction.getRecommandationSoft(), 200));
            }
            if (latestPrediction.getRecommandationTech() != null && !latestPrediction.getRecommandationTech().isBlank()) {
                lines.add("Technical recommendation: " + clip(latestPrediction.getRecommandationTech(), 200));
            }
        }

        lines.add("");
        lines.add("Technical skills:");
        for (Skill s : techSkills) {
            lines.add("- " + safe(s.getNom()) + " (level " + (s.getNiveau() != null ? s.getNiveau() : "?") + ")");
        }

        lines.add("");
        lines.add("Soft skills:");
        for (Skill s : softSkills) {
            lines.add("- " + safe(s.getNom()) + " (level " + (s.getNiveau() != null ? s.getNiveau() : "?") + ")");
        }

        lines.add("");
        lines.add("Technical test history:");
        for (CandidateTestResult h : history) {
            lines.add("- "
                    + (h.getTakenAt() != null ? h.getTakenAt().toString() : "n/a")
                    + " | type: " + (h.getTestType() != null ? h.getTestType().name() : "TECH")
                    + " | score: " + (h.getOverallScore() != null ? h.getOverallScore() : "n/a")
                    + " | passed: " + (Boolean.TRUE.equals(h.getPassed()) ? "Yes" : "No"));
        }

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float margin = 50;
            float y = page.getMediaBox().getHeight() - margin;
            float leading = 15;
            PDType1Font baseFont = PDType1Font.HELVETICA;

            PDPageContentStream content = new PDPageContentStream(document, page);
            content.setFont(baseFont, 11);
            content.beginText();
            content.newLineAtOffset(margin, y);

            for (String line : lines) {
                if (y <= margin) {
                    content.endText();
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    y = page.getMediaBox().getHeight() - margin;
                    content = new PDPageContentStream(document, page);
                    content.setFont(baseFont, 11);
                    content.beginText();
                    content.newLineAtOffset(margin, y);
                }

                content.showText(asciiOnly(line));
                content.newLineAtOffset(0, -leading);
                y -= leading;
            }

            content.endText();
            content.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String asciiOnly(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 32 && c <= 126) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static String clip(String value, int maxLen) {
        String safe = value == null ? "" : value.trim().replace("\n", " ");
        if (safe.length() <= maxLen) {
            return safe;
        }
        return safe.substring(0, maxLen) + "...";
    }
}
