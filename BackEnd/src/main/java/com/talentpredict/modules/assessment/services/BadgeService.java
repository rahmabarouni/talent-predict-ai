package com.talentpredict.modules.assessment.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BadgeService {

    @Value("${app.name:TalentPredict}")
    private String appName;

    public String buildSvgBadge(String skill, int score, String levelLabel) {
        String safeSkill = escapeXml(skill);
        return """
                <svg xmlns="http://www.w3.org/2000/svg" width="320" height="80" viewBox="0 0 320 80">
                  <rect width="320" height="80" rx="10" fill="#1e293b"/>
                  <text x="16" y="36" fill="#f8fafc" font-family="Segoe UI, Arial" font-size="18" font-weight="600">%s</text>
                  <text x="16" y="62" fill="#94a3b8" font-family="Segoe UI, Arial" font-size="14">%s — %d/100 — Verified by %s</text>
                </svg>
                """
                .formatted(safeSkill, levelLabel, score, escapeXml(appName));
    }

    private static String escapeXml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
