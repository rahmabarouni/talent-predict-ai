package com.talentpredict.modules.reporting.services;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.talentpredict.modules.dashboard.dto.DashboardDto;
import com.talentpredict.modules.dashboard.services.DashboardService;
import com.talentpredict.modules.formation.dto.FormationDto;
import com.talentpredict.modules.skills.dto.SkillDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final DashboardService dashboardService;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public byte[] generateTalentPassport(UUID userId) {
        DashboardDto.Response data = dashboardService.getDashboard(userId);
        String html = buildTalentPassportHtml(data);
        return generatePdf(html);
    }

    public byte[] generateHrReport() {
        DashboardDto.AdminOverviewDto data = dashboardService.getAdminOverview();
        return generatePdf(buildHrReportHtml(data));
    }

    private byte[] generatePdf(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF", e);
        }
    }

    // =========================================================
    //  TALENT PASSPORT — rich HTML template
    // =========================================================
    private String buildTalentPassportHtml(DashboardDto.Response d) {

        String firstName     = esc(d.getFirstName());
        String lastName      = esc(d.getLastName());
        String scoreStr      = formatScore(safeDouble(d.getScoreEvaluationMoyen()));
        int    tests         = safeInt(d.getNombreTests());
        int    skillsSoft    = safeInt(d.getNombreSkillsSoft());
        int    skillsTech    = safeInt(d.getNombreSkillsTech());
        int    formTotal     = safeInt(d.getNombreFormationsTotal());
        int    formTerminees = safeInt(d.getNombreFormationsTerminees());
        String generated     = LocalDateTime.now().format(FMT);

        // ---- AI Prediction block ----
        String personalityType   = "N/A";
        String analyse           = "Aucune analyse disponible.";
        String recoSoft          = "Aucune recommandation disponible.";
        String recoTech          = "Aucune recommandation disponible.";
        String confidenceLabel   = "—";
        String confidenceColor   = "#94a3b8";
        String predDate          = "—";

        if (d.getDernierePrediction() != null) {
            var p = d.getDernierePrediction();
            if (p.getAnalyse() != null) {
                String pt = extractSection(p.getAnalyse(), "TYPE_PERSONNALITE");
                if (pt != null && !pt.isBlank()) personalityType = esc(pt.trim());
                String an = extractSection(p.getAnalyse(), "ANALYSE");
                if (an != null && !an.isBlank()) analyse = escMulti(an.trim());
            }
            if (p.getRecommandationSoft() != null && !p.getRecommandationSoft().isBlank())
                recoSoft = escMulti(normalizeList(p.getRecommandationSoft()));
            if (p.getRecommandationTech() != null && !p.getRecommandationTech().isBlank())
                recoTech = escMulti(normalizeList(p.getRecommandationTech()));
            if (p.getScoreConfiance() != null) {
                double sc = p.getScoreConfiance() <= 1.0 ? p.getScoreConfiance() * 100 : p.getScoreConfiance();
                confidenceLabel = String.format("%.0f%%", sc);
                confidenceColor = sc >= 75 ? "#22c55e" : sc >= 50 ? "#f59e0b" : "#ef4444";
            }
            if (p.getDatePrediction() != null)
                predDate = p.getDatePrediction().format(FMT);
        }

        // ---- Top Skills rows ----
        StringBuilder skillRows = new StringBuilder();
        List<SkillDto.Response> topSkills = d.getTopSkills();
        if (topSkills != null && !topSkills.isEmpty()) {
            for (SkillDto.Response s : topSkills) {
                int lvl = s.getNiveau() != null ? s.getNiveau() : 0;
                int pct = lvl * 20;
                String badge = s.getType() != null && s.getType().name().equals("SOFT")
                        ? "soft-badge" : "tech-badge";
                String typeLabel = s.getType() != null ? s.getType().name() : "—";
                String src = s.getSource() != null ? esc(s.getSource()) : "—";
                skillRows.append("""
                    <tr>
                      <td>%s</td>
                      <td><span class="%s">%s</span></td>
                      <td>
                        <div class="bar-bg"><div class="bar-fill" style="width:%d%%"></div></div>
                        <span class="bar-label">%d/5</span>
                      </td>
                      <td>%s</td>
                    </tr>
                """.formatted(esc(s.getNom()), badge, typeLabel, pct, lvl, src));
            }
        } else {
            skillRows.append("<tr><td colspan='4' class='empty'>Aucune compétence enregistrée.</td></tr>");
        }

        // ---- Formation rows ----
        StringBuilder formRows = new StringBuilder();
        List<FormationDto.FormationResponse> formations = d.getFormationsRecentes();
        if (formations != null && !formations.isEmpty()) {
            for (FormationDto.FormationResponse f : formations) {
                String statut = f.getStatut() != null ? f.getStatut().name().replace("_", " ") : "—";
                String statutColor = "EN COURS".equals(statut) ? "#f59e0b"
                        : "TERMINEE".equals(statut) ? "#22c55e" : "#94a3b8";
                String fournisseur = f.getFournisseur() != null ? esc(f.getFournisseur()) : "—";
                String dateDebut = f.getDateDebut() != null ? f.getDateDebut().format(FMT) : "—";
                String miniTest = Boolean.TRUE.equals(f.getMiniTestPassed()) ? "&#10003; Réussi"
                        : f.getMiniTestScore() != null ? f.getMiniTestScore() + "%" : "—";
                formRows.append("""
                    <tr>
                      <td>%s</td>
                      <td>%s</td>
                      <td><span style="color:%s;font-weight:bold">%s</span></td>
                      <td>%s</td>
                      <td>%s</td>
                    </tr>
                """.formatted(esc(f.getTitre()), fournisseur, statutColor, statut, dateDebut, miniTest));
            }
        } else {
            formRows.append("<tr><td colspan='5' class='empty'>Aucune formation enregistrée.</td></tr>");
        }

        // ---- Test history rows ----
        StringBuilder testRows = new StringBuilder();
        List<DashboardDto.TestSummaryDto> tests2 = d.getTestsRecents();
        if (tests2 != null && !tests2.isEmpty()) {
            for (DashboardDto.TestSummaryDto t : tests2) {
                String dt = t.getDateTest() != null ? t.getDateTest().format(FMT) : "—";
                String pt = t.getPersonalityType() != null ? esc(t.getPersonalityType()) : "—";
                String sc = t.getOverallScore() != null
                        ? String.format("%.0f%%", t.getOverallScore() <= 1.0
                            ? t.getOverallScore() * 100 : t.getOverallScore()) : "—";
                String sum = t.getSummary() != null ? esc(clip(t.getSummary(), 120)) : "—";
                testRows.append("""
                    <tr>
                      <td>%s</td>
                      <td><strong>%s</strong></td>
                      <td>%s</td>
                      <td class="muted-cell">%s</td>
                    </tr>
                """.formatted(dt, pt, sc, sum));
            }
        } else {
            testRows.append("<tr><td colspan='4' class='empty'>Aucun historique de test disponible.</td></tr>");
        }

        return """
        <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
          "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head>
          <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
          <title>Talent Passport</title>
          <style>
            * { margin:0; padding:0; box-sizing:border-box; }
            body { font-family:Arial,Helvetica,sans-serif; font-size:11px;
                   color:#1e293b; background:#f8fafc; padding:28px; }

            /* ---- HEADER ---- */
            .header-box { background:#1e1b4b; color:#fff; padding:22px 28px;
                          border-radius:12px; margin-bottom:20px; }
            .header-top { display:table; width:100%%; }
            .header-left { display:table-cell; vertical-align:middle; }
            .header-right { display:table-cell; text-align:right; vertical-align:middle; }
            .passport-title { font-size:22px; font-weight:bold; letter-spacing:3px;
                              color:#a5b4fc; }
            .passport-sub { font-size:11px; color:#c7d2fe; margin-top:3px; }
            .user-name { font-size:20px; font-weight:bold; color:#fff; }
            .user-meta { font-size:10px; color:#a5b4fc; margin-top:4px; }
            .badge-conf { display:inline-block; padding:5px 14px;
                          border-radius:20px; font-size:13px; font-weight:bold;
                          color:#fff; background:{{CONF_COLOR}}; margin-top:6px; }
            .persona-chip { display:inline-block; background:#6366f1;
                            color:#fff; padding:3px 12px; border-radius:12px;
                            font-size:10px; font-weight:bold; margin-top:4px; }

            /* ---- KPI GRID ---- */
            .kpi-table { width:100%%; border-spacing:10px; border-collapse:separate;
                         margin-bottom:18px; }
            .kpi-cell { background:#fff; border:1px solid #e2e8f0; border-radius:10px;
                        padding:14px; text-align:center; width:16.6%%; }
            .kpi-val { font-size:20px; font-weight:bold; color:#4338ca; }
            .kpi-lbl { font-size:9px; color:#64748b; text-transform:uppercase;
                       margin-top:4px; letter-spacing:.5px; }

            /* ---- SECTIONS ---- */
            .section { background:#fff; border:1px solid #e2e8f0; border-radius:10px;
                       padding:16px 18px; margin-bottom:16px; }
            .section-title { font-size:13px; font-weight:bold; color:#1e1b4b;
                             border-left:4px solid #6366f1; padding-left:10px;
                             margin-bottom:12px; }

            /* ---- AI ANALYSIS ---- */
            .analyse-box { background:#f0f0ff; border-left:3px solid #6366f1;
                           padding:10px 14px; border-radius:6px; font-size:10px;
                           line-height:1.55; color:#374151; margin-bottom:10px; }
            .reco-box    { background:#f0fdf4; border-left:3px solid #22c55e;
                           padding:10px 14px; border-radius:6px; font-size:10px;
                           line-height:1.55; margin-bottom:8px; }
            .reco-tech   { background:#fff7ed; border-left:3px solid #f59e0b; }
            .reco-label  { font-weight:bold; font-size:10px; margin-bottom:4px;
                           color:#1e293b; }

            /* ---- TABLES ---- */
            table.data-table { width:100%%; border-collapse:collapse; }
            table.data-table th { background:#f1f5f9; color:#475569; font-size:9px;
                                  text-transform:uppercase; padding:7px 8px;
                                  border-bottom:2px solid #e2e8f0; text-align:left; }
            table.data-table td { padding:7px 8px; border-bottom:1px solid #f1f5f9;
                                  font-size:10px; vertical-align:middle; }
            table.data-table tr:last-child td { border-bottom:none; }
            .muted-cell { color:#64748b; font-size:9px; }
            .empty { color:#94a3b8; font-style:italic; }

            /* ---- SKILL BADGES ---- */
            .soft-badge { background:#ede9fe; color:#7c3aed; padding:2px 8px;
                          border-radius:10px; font-size:9px; font-weight:bold; }
            .tech-badge { background:#dbeafe; color:#1d4ed8; padding:2px 8px;
                          border-radius:10px; font-size:9px; font-weight:bold; }

            /* ---- PROGRESS BAR ---- */
            .bar-bg   { display:inline-block; width:70px; height:6px;
                        background:#e2e8f0; border-radius:4px; vertical-align:middle; }
            .bar-fill { height:6px; background:#6366f1; border-radius:4px; }
            .bar-label{ font-size:9px; color:#64748b; margin-left:5px; }

            /* ---- FOOTER ---- */
            .footer { text-align:center; font-size:9px; color:#94a3b8;
                      margin-top:22px; border-top:1px solid #e2e8f0; padding-top:10px; }
            .confidential { display:inline-block; background:#fee2e2; color:#dc2626;
                            font-size:8px; font-weight:bold; padding:2px 8px;
                            border-radius:10px; letter-spacing:1px;
                            text-transform:uppercase; margin-bottom:4px; }
          </style>
        </head>
        <body>

          <!-- HEADER -->
          <div class="header-box">
            <div class="header-top">
              <div class="header-left">
                <div class="passport-title">TALENT PASSPORT</div>
                <div class="passport-sub">TalentPredict &#8226; Confidentiel &#8226; Genere le {{GENERATED}}</div>
              </div>
              <div class="header-right">
                <div class="user-name">{{FIRSTNAME}} {{LASTNAME}}</div>
                <div class="user-meta">ID Candidat : {{USER_ID}}</div>
                <div class="persona-chip">&#127775; {{PERSONALITY}}</div><br/>
                <div class="badge-conf">Confiance IA : {{CONFIDENCE}}</div>
              </div>
            </div>
          </div>

          <!-- KPI ROW -->
          <table class="kpi-table">
            <tr>
              <td class="kpi-cell">
                <div class="kpi-val">{{SCORE}}%%</div>
                <div class="kpi-lbl">Score Moyen</div>
              </td>
              <td class="kpi-cell">
                <div class="kpi-val">{{TESTS}}</div>
                <div class="kpi-lbl">Tests Passes</div>
              </td>
              <td class="kpi-cell">
                <div class="kpi-val">{{SKILLS_SOFT}}</div>
                <div class="kpi-lbl">Soft Skills</div>
              </td>
              <td class="kpi-cell">
                <div class="kpi-val">{{SKILLS_TECH}}</div>
                <div class="kpi-lbl">Tech Skills</div>
              </td>
              <td class="kpi-cell">
                <div class="kpi-val">{{FORM_TOTAL}}</div>
                <div class="kpi-lbl">Formations</div>
              </td>
              <td class="kpi-cell">
                <div class="kpi-val">{{FORM_DONE}}</div>
                <div class="kpi-lbl">Terminees</div>
              </td>
            </tr>
          </table>

          <!-- AI ANALYSIS -->
          <div class="section">
            <div class="section-title">&#129504; Analyse IA du Profil &mdash; derniere prediction ({{PRED_DATE}})</div>
            <div class="analyse-box">{{ANALYSE}}</div>
            <div class="reco-label">&#127775; Recommandations Soft Skills</div>
            <div class="reco-box">{{RECO_SOFT}}</div>
            <div class="reco-label">&#128187; Recommandations Techniques</div>
            <div class="reco-box reco-tech">{{RECO_TECH}}</div>
          </div>

          <!-- TOP SKILLS -->
          <div class="section">
            <div class="section-title">&#127919; Competences Cles (Top 5)</div>
            <table class="data-table">
              <tr>
                <th>Competence</th>
                <th>Type</th>
                <th>Niveau</th>
                <th>Source</th>
              </tr>
              {{SKILL_ROWS}}
            </table>
          </div>

          <!-- FORMATION HISTORY -->
          <div class="section">
            <div class="section-title">&#127979; Parcours de Formation</div>
            <table class="data-table">
              <tr>
                <th>Formation</th>
                <th>Fournisseur</th>
                <th>Statut</th>
                <th>Date debut</th>
                <th>Mini-test</th>
              </tr>
              {{FORM_ROWS}}
            </table>
          </div>

          <!-- TEST HISTORY -->
          <div class="section">
            <div class="section-title">&#128203; Historique des Evaluations</div>
            <table class="data-table">
              <tr>
                <th>Date</th>
                <th>Profil detecte</th>
                <th>Score</th>
                <th>Resume</th>
              </tr>
              {{TEST_ROWS}}
            </table>
          </div>

          <!-- FOOTER -->
          <div class="footer">
            <div class="confidential">Confidentiel</div><br/>
            Document genere automatiquement par TalentPredict AI Engine &mdash; {{GENERATED}}
            &nbsp;|&nbsp; Ce document est strictement personnel et ne peut etre partage sans autorisation.
          </div>

        </body>
        </html>
        """
        .replace("{{FIRSTNAME}}", firstName)
        .replace("{{LASTNAME}}", lastName)
        .replace("{{USER_ID}}", d.getUserId() != null ? d.getUserId().toString().substring(0, 8) + "..." : "—")
        .replace("{{SCORE}}", scoreStr)
        .replace("{{TESTS}}", String.valueOf(tests))
        .replace("{{SKILLS_SOFT}}", String.valueOf(skillsSoft))
        .replace("{{SKILLS_TECH}}", String.valueOf(skillsTech))
        .replace("{{FORM_TOTAL}}", String.valueOf(formTotal))
        .replace("{{FORM_DONE}}", String.valueOf(formTerminees))
        .replace("{{PERSONALITY}}", personalityType)
        .replace("{{CONFIDENCE}}", confidenceLabel)
        .replace("{{CONF_COLOR}}", confidenceColor)
        .replace("{{PRED_DATE}}", predDate)
        .replace("{{GENERATED}}", generated)
        .replace("{{ANALYSE}}", analyse)
        .replace("{{RECO_SOFT}}", recoSoft)
        .replace("{{RECO_TECH}}", recoTech)
        .replace("{{SKILL_ROWS}}", skillRows.toString())
        .replace("{{FORM_ROWS}}", formRows.toString())
        .replace("{{TEST_ROWS}}", testRows.toString());
    }

    // =========================================================
    //  HR REPORT
    // =========================================================
    private String buildHrReportHtml(DashboardDto.AdminOverviewDto data) {
        String generated = LocalDateTime.now().format(FMT);
        
        int totalEmp = data.getTotalEmployees();
        int tests = data.getTotalTestsCompleted();
        int formations = data.getTotalFormationsEnCours();
        int predictions = data.getTotalPredictions();
        
        // Detailed employee list
        StringBuilder empRows = new StringBuilder();
        if (data.getEmployees() != null && !data.getEmployees().isEmpty()) {
            for (DashboardDto.EmployeeSummaryDto e : data.getEmployees()) {
                String dept = e.getDepartment() != null ? esc(e.getDepartment()) : "—";
                String pos = e.getPosition() != null ? esc(e.getPosition()) : "—";
                String name = esc(e.getFirstName()) + " " + esc(e.getLastName());
                String pType = e.getPersonalityType() != null ? esc(e.getPersonalityType()) : "—";
                String email = e.getEmail() != null ? esc(e.getEmail()) : "—";
                String status = e.isActive() ? "<span style='color:#22c55e;'>Actif</span>" : "<span style='color:#ef4444;'>Inactif</span>";
                
                empRows.append("""
                    <tr>
                      <td><strong>%s</strong><br/><span style='color:#64748b;font-size:9px;'>%s</span></td>
                      <td>%s</td>
                      <td>%s</td>
                      <td style="text-align:center;">%d</td>
                      <td style="text-align:center;">%d</td>
                      <td>%s</td>
                      <td style="text-align:center;">%s</td>
                    </tr>
                """.formatted(name, email, dept, pos, e.getTestCount(), e.getFormationCount(), pType, status));
            }
        } else {
            empRows.append("<tr><td colspan='8' class='empty'>Aucun employ&eacute; trouv&eacute;.</td></tr>");
        }

        return """
        <!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
          "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
        <html xmlns="http://www.w3.org/1999/xhtml">
        <head>
          <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
          <title>Rapport Analytique RH</title>
          <style>
            @page { size: A4; margin: 15mm; }
            * { margin:0; padding:0; box-sizing:border-box; }
            body { font-family:Arial,sans-serif; color:#333; font-size:11px; line-height: 1.4; }
            .header { text-align:center; border-bottom:2px solid #0ea5e9; padding-bottom:15px; margin-bottom:20px; }
            .title { color:#0ea5e9; font-size:24px; font-weight:bold; margin-bottom:4px; }
            .subtitle { font-size:13px; color:#64748b; }
            .section { margin-top:20px; }
            .section-title { font-size:14px; color:#1e293b;
                             border-left:4px solid #0ea5e9; padding-left:8px;
                             margin-bottom:10px; font-weight:bold; }
            .kpi-value { font-size:22px; font-weight:bold; color:#0ea5e9; margin-bottom:4px; }
            .kpi-label { font-size:10px; color:#64748b; text-transform:uppercase; letter-spacing: 0.5px; }
            table.data-table { width:100%%; border-collapse:collapse; margin-top:8px; }
            table.data-table th, table.data-table td { border:1px solid #e2e8f0; padding:8px; text-align:left; font-size:10px; vertical-align:middle; }
            table.data-table th { background:#f8fafc; color:#0ea5e9; font-weight:bold; text-transform:uppercase; }
            .footer { margin-top:30px; font-size:9px; color:#94a3b8; text-align:center; border-top:1px solid #e2e8f0; padding-top:10px; }
            .empty { text-align:center; color:#94a3b8; font-style:italic; }
            .kpi-box { padding:12px; background:#f8fafc; border:1px solid #e2e8f0; text-align:center; border-radius:6px; }
          </style>
        </head>
        <body>
          <div class="header">
            <div class="title">RAPPORT ANALYTIQUE RH</div>
            <div class="subtitle">Synth&egrave;se Globale du Capital Humain &#8212; TalentPredict</div>
            <div style="font-size:10px; color:#94a3b8; margin-top:10px;">G&eacute;n&eacute;r&eacute; le %s</div>
          </div>
          
          <div class="section">
            <div class="section-title">Indicateurs Cl&eacute;s de Performance</div>
            <table style="width:100%%; border:none; margin-bottom:10px;">
              <tr>
                <td style="width:25%%; padding-right:5px;">
                  <div class="kpi-box">
                    <div class="kpi-value">%d</div>
                    <div class="kpi-label">Employ&eacute;s Actifs</div>
                  </div>
                </td>
                <td style="width:25%%; padding:0 5px;">
                  <div class="kpi-box">
                    <div class="kpi-value">%d</div>
                    <div class="kpi-label">Tests Pass&eacute;s</div>
                  </div>
                </td>
                <td style="width:25%%; padding:0 5px;">
                  <div class="kpi-box">
                    <div class="kpi-value">%d</div>
                    <div class="kpi-label">Formations</div>
                  </div>
                </td>
                <td style="width:25%%; padding-left:5px;">
                  <div class="kpi-box">
                    <div class="kpi-value">%d</div>
                    <div class="kpi-label">Pr&eacute;dictions IA</div>
                  </div>
                </td>
              </tr>
            </table>
          </div>

          <div class="section">
            <div class="section-title">D&eacute;tails du Personnel</div>
            <table class="data-table">
              <thead>
                <tr>
                  <th>Collaborateur</th>
                  <th>D&eacute;partement</th>
                  <th>Poste</th>
                  <th style="text-align:center;">Tests</th>
                  <th style="text-align:center;">Formations</th>
                  <th>Profil IA</th>
                  <th style="text-align:center;">Statut</th>
                </tr>
              </thead>
              <tbody>
                %s
              </tbody>
            </table>
          </div>
          
          <div class="footer">
            Confidentiel &#8212; TalentPredict Executive Intelligence &#8212; 2026<br/>
            Ce document contient des informations sensibles r&eacute;serv&eacute;es &agrave; l'&eacute;quipe RH.
          </div>
        </body>
        </html>
        """.formatted(generated, totalEmp, tests, formations, predictions, empRows.toString());
    }

    // =========================================================
    //  HELPERS
    // =========================================================
    private static int safeInt(Integer v) { return v == null ? 0 : v; }
    private static double safeDouble(Double v) { return v == null ? 0.0 : v; }

    private static String formatScore(double v) {
        if (v > 0 && v <= 1) v *= 100;
        return String.format(java.util.Locale.US, "%.1f", v);
    }

    private static String esc(String v) {
        if (v == null) return "";
        return v.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
                .replace("\"","&quot;").replace("'","&#39;");
    }

    private static String escMulti(String v) {
        return esc(v).replace("\n", "<br/>");
    }

    private static String extractSection(String text, String title) {
        if (text == null) return null;
        int s = text.indexOf(title + ":");
        if (s == -1) return null;
        s += title.length() + 1;
        int e = text.indexOf("\n\n", s);
        if (e == -1) e = text.indexOf("\n", s);
        if (e == -1) e = text.length();
        return text.substring(s, e).trim();
    }

    private static String normalizeList(String v) {
        if (v == null) return "";
        String t = v.trim();
        if (t.startsWith("[") && t.endsWith("]")) {
            t = t.substring(1, t.length() - 1);
            return java.util.Arrays.stream(t.split(","))
                    .map(p -> p.trim().replaceAll("^['\"]|['\"]$",""))
                    .filter(p -> !p.isBlank())
                    .reduce((a, b) -> a + "\n" + b).orElse("");
        }
        return t;
    }

    private static String clip(String v, int max) {
        if (v == null) return "";
        return v.length() <= max ? v : v.substring(0, max) + "…";
    }
}
