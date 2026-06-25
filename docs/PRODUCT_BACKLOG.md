# Product Backlog - TalentPredict AI

Plateforme d'évaluation des compétences avec IA, PCM, CV/GitHub parsing et recommandations de formations.

---

## 📋 Product Backlog (21 User Stories - 4 Sprints)

| ID | Epic | Acteur | Besoin | Objectif | Priorité | Sprint | Points |
|---|---|---|---|---|---|---|---|
| **US-001** | Auth & Sécurité | Utilisateur/RH | M'authentifier JWT + configurer confidentialité | Accès sécurisé | Haute | S1 | 13 |
| **US-008** | Profil & Dashboard | Utilisateur | Créer profil et dashboard personnel | Vue complète progression | Haute | S1 | 13 |
| **US-002** | Compétences Comportementales | Utilisateur | Passer test PCM | Profil comportemental | Haute | S2 | 21 |
| **US-003** | Analyse Technique | Utilisateur | Soumettre CV, GitHub, Portfolio | Extraction automatique | Haute | S2 | 13 |
| **US-006** | Orchestration | Développeur | Modéliser workflows n8n | Coordonner services | Haute | S2 | 21 |
| **US-004** | Scoring | Système IA | Fusionner données PCM/CV/GitHub | Score de confiance | Haute | S2 | 21 |
| **US-009** | Gestion Compétences | Utilisateur | Valider/ajouter compétences | Profil compétences | Moyenne | S2 | 13 |
| **US-011** | Gamification | Utilisateur | Badges, XP, classement | Motivation | Moyenne | S2 | 8 |
| **US-005** | Recommandations | Système | Proposer formations | Parcours personnalisé | Haute | S3 | 13 |
| **US-010** | Tests & Certificats | Utilisateur | Mini-tests, historique, certs | Valider progression | Moyenne | S3 | 13 |
| **US-012** | Admin Dashboard | RH | Dashboard + recherche candidats | Statistiques & talents | Haute | S3 | 13 |
| **US-013** | Gestion Formations | RH | Créer, approuver, assigner formations | Parcours obligatoires | Haute | S3 | 13 |
| **US-014** | Rapports & Analytics | RH | Rapports compétences & impact | Efficacité parcours | Moyenne | S3 | 13 |
| **US-015** | RGPD & Audit | Système | RGPD + audit accès | Conformité légale | Haute | S3 | 13 |
| **US-016** | Intégrations | RH | AD Sync + API REST | Écosystème IT | Moyenne | S3 | 21 |
| **US-007** | Notifications | Utilisateur | Recevoir & configurer notifs | Rester informé | Haute | S4 | 8 |
| **US-017** | Performance | DevOps | Optimiser IA + monitoring | Temps rapides | Moyenne | S4 | 13 |
| **US-018** | Mobile & WCAG | Frontend | Responsive + accessibilité | Multiplateforme | Moyenne | S4 | 21 |
| **US-020** | Tests QA | QA | Tests unitaires/intégration/E2E | Stabilité code | Haute | S4 | 21 |
| **US-021** | CI/CD | DevOps | Docker, K8s, pipeline auto | Déploiements auto | Moyenne | S4 | 21 |
| **US-019** | Localisation | Frontend | FR + EN | International | Basse | S4 | 8 |

---

## 🎯 Résumé par Sprint

| Sprint | User Stories | Objectif | Total Points |
|---|---|---|---|
| **S1** | US-001, US-008 | Auth, Profil & Dashboard | **26** |
| **S2** | US-002, US-003, US-006, US-004, US-009, US-011 | PCM, CV/GitHub, Orchestration, Scoring, Compétences, Gamification | **97** |
| **S3** | US-005, US-010, US-012, US-013, US-014, US-015, US-016 | Recommandations, Tests, Admin, Analytics, RGPD, Intégrations | **101** |
| **S4** | US-007, US-017, US-018, US-020, US-021, US-019 | Notifications, Performance, Mobile, QA, CI/CD, Localisation | **92** |
| **TOTAL** | **21 US** | **MVP Complet** | **316 points** |

---

---

## � Notes Clés

- **Story Points** : Fibonacci (5, 8, 13, 21) réalistes
- **Vélocité** : ~79 pts/sprint (équipe 6-8 devs)
- **Durée** : 8 semaines (4 sprints × 2 sem)
- **Dépendances** : S1 < S2 < S3 < S4
- **MVP** : Complet après S4
- **Raffinement** : Hebdomadaire pour détailler prochaines US
