# Architecture TalentPredict

## 📐 Vue d'ensemble

TalentPredict est une plateforme modulaire d'évaluation des compétences, d'analyse de profil et de prédiction de formation basée sur une architecture Spring Boot (port `8081`) moderne avec intégrations d'agents IA, d'Ollama (Llama 3.2), d'OpenRouter (Claude) et de workflows n8n.

```
┌─────────────────────────────────────────────────────────────┐
│                      Client (Angular)                        │
│                    http://localhost:4200                     │
└────────────────────────┬────────────────────────────────────┘
                         │ REST API (JWT)
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              Backend Spring Boot (Port 8081)                 │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              Controllers Layer                        │  │
│  │  Auth │ Users │ Profiles │ Soft-Skills │ Formations  │  │
│  │  Skills │ Predictions │ Dashboard │ Proxies          │  │
│  └──────────────────┬───────────────────────────────────┘  │
│                     │                                        │
│  ┌──────────────────▼───────────────────────────────────┐  │
│  │              Services Layer (Modules & Shared)        │  │
│  │  Business Logic + Circuit Breaker (Resilience4j)      │  │
│  └──────────────────┬───────────────────────────────────┘  │
│                     │                                        │
│  ┌──────────────────▼───────────────────────────────────┐  │
│  │              Repositories Layer                       │  │
│  │  Spring Data JPA                                     │  │
│  └──────────────────┬───────────────────────────────────┘  │
└────────────────────┬┴──────────────────────────────────────┘
                     │
         ┌───────────┼───────────┬────────────┐
         │           │           │            │
         ▼           ▼           ▼            ▼
   ┌─────────┐ ┌───────────┐ ┌─────────┐ ┌──────────┐
   │PostgreSQL││OpenRouter/ │ │ Ollama  │ │   n8n    │
   │ (5432)  │ │ Claude API│ │ (11434) │ │  (5678)  │
   └─────────┘ └───────────┘ └─────────┘ └──────────┘
```

---

## 🏗️ Structure des packages

Le backend Java est conçu selon une architecture modulaire pour découpler les domaines métiers tout en conservant des composants partagés.

```
com.talentpredict/
├── modules/                        # Modules Métier Découplés
│   ├── ai/                         # Services d'analyse IA et de prédiction
│   │   ├── controllers/            # CvAnalysisController, PredictionController, SoftSkillsController...
│   │   ├── entities/               # Prediction
│   │   └── services/               # CvAnalysisService, OpenRouterService, SoftSkillsService...
│   │
│   ├── assessment/                 # Évaluations techniques, badges et campagnes
│   │   ├── controllers/            # CandidateAssessmentController, CampaignController, AnalysisProxyController...
│   │   ├── entities/               # Campaign, CandidateBadge, CandidateTestResult, JobMatch
│   │   └── services/               # TalentPredictAiProxyService...
│   │
│   ├── auth/                       # Authentification, réinitialisation et journalisation
│   │   ├── controllers/            # AuthController
│   │   ├── entities/               # RefreshToken, AuditLog, EmailVerificationToken, TokenBlocklist...
│   │   └── services/               # AuthService, TokenService...
│   │
│   ├── dashboard/                  # Données agrégées pour employés et RH (Admin)
│   │   ├── controllers/            # DashboardController
│   │   └── services/               # DashboardService
│   │
│   ├── evaluation/                 # Évaluation de personnalité MBTI & PCM
│   │   ├── entities/               # PersonalityTest, PCMResult
│   │   └── repositories/           # PersonalityTestRepository, PCMResultRepository
│   │
│   ├── formation/                  # Suivi des formations et mini-tests (Quiz)
│   │   ├── controllers/            # FormationController
│   │   ├── entities/               # Formation
│   │   └── services/               # FormationService
│   │
│   ├── notification/               # Système de notifications utilisateurs
│   │   ├── controllers/            # NotificationController
│   │   └── entities/               # UserNotification
│   │
│   ├── privacy/                    # Paramètres de confidentialité des données
│   │   ├── controllers/            # PrivacyController
│   │   └── entities/               # UserPrivacySettings
│   │
│   ├── reporting/                  # Rapports et exports de données
│   │   └── controllers/            # ReportingController
│   │
│   ├── security/                   # Contrôle d'accès par module
│   │   └── controllers/            # SecurityController
│   │
│   ├── skills/                     # Gestion des compétences (Soft & Tech)
│   │   ├── controllers/            # SkillController
│   │   ├── entities/               # Skill
│   │   └── services/               # SkillService
│   │
│   └── user/                       # Profils professionnels, XP de gamification
│       ├── controllers/            # UserController, ProfileController
│       ├── entities/               # User, Profile
│       └── services/               # UserService, ProfileService
│
├── shared/                         # Composants Transversaux Partagés
│   ├── config/                     # Configurations globales (CORS, Async, WebClient, Security...)
│   ├── exception/                  # Centralisation de la gestion des erreurs (GlobalExceptionHandler)
│   ├── security/                   # Services et filtres JWT (JwtAuthenticationFilter, UserDetailsImpl...)
│   ├── services/                   # Services utilitaires (FileStorageService, AnalysisStatusService...)
│   └── sms/                        # Intégration Twilio (Envoi de SMS / 2FA)
│
└── TalentPredictApplication.java    # Point d'entrée de l'application
```

---

## 🔄 Flux de données principaux

### 1. Authentification & Sécurité JWT
```
Client (Angular) → AuthController.login()
                → AuthenticationManager (Spring Security)
                → UserDetailsServiceImpl.loadUserByUsername()
                → UserRepository.findByEmail()
                → JwtService.generateToken()
                → AuthResponse (avec token JWT & Infos utilisateur)
```

### 2. Analyse de CV et Extraction de Compétences
```
Client (Angular) → ProfileController.uploadCvEtAnalyser() [POST /api/profiles/accounts/{id}/upload-cv]
                → FileStorageService (stockage physique du PDF)
                → CvAnalysisService.analyserCvFileComplet() (lecture via Apache PDFBox)
                → OpenRouterService.analyserCvFileComplet() (envoi du texte extrait au LLM Claude 3.5)
                → Mise à jour de Profile (titre, bio, experienceAns)
                → Enregistrement des compétences détectées dans SkillRepository (sans doublons)
                → Retour des résultats structurés au client
```

### 3. Évaluation Soft Skills (MBTI / PCM)
```
Client (Angular) → SoftSkillsController.analyze() [POST /api/soft-skills/analyze]
                → SoftSkillsService.analyze()
                → Appel de l'agent n8n / Agent LLM local
                → Enregistrement du test de personnalité (PersonalityTest) & PCMResult
                → Attribution de points d'XP via UserService (Gamification)
                → Retour des résultats d'analyse comportementale
```

### 4. Génération de prédiction de carrière (Proxy Circuit-Breaker)
Pour garantir la tolérance aux pannes, le flux transite via Resilience4j.
```
Client (Angular) → AnalysisProxyController.predictCareer() [POST /api/v1/analysis/career-prediction]
                → Intercepteur Resilience4j (Circuit Breaker 'aiService' + Time Limiter)
                → AssessmentAiProxyService.generateCareerPrediction()
                → Requête HTTP vers le microservice Python (talentpredict-ai sur port 8000)
                → Si succès : Retour des recommandations et formations à proposer
                → Si échec / timeout (5s) : Déclenchement de la méthode de fallback (retourne un statut 503 géré proprement)
```

---

## 🔐 Sécurité

L'application est entièrement sécurisée à l'aide de **Spring Security** et de tokens JWT sans état (Stateless).

### Niveaux d'accès
*   **Public (Sans Token) :** `/api/auth/**` (Register, Login, Google/GitHub social logins)
*   **USER :** Consultation et mise à jour de son propre profil, ajout de compétences, suivi de ses formations, passage de tests soft skills, génération de ses prédictions.
*   **ADMIN (RH) :** Accès à `/api/users` global, accès aux résumés utilisateurs (`/api/users/{id}/summary`), validation officielle des compétences, attribution/gestion globale des formations, accès au Dashboard de reporting global (`/api/dashboard/admin/overview`).

---

## 💾 Modèle de données (PostgreSQL)

Les entités ont été renommées et structurées en anglais conformément au code backend réel. Les tables Jira obsolètes ont été complètement supprimées du schéma actif.

```
                  ┌──────────────────────┐
                  │         User         │
                  └──────────┬───────────┘
                             │ 1
                             ├──────────────────────────┐
                             │ 1                        │ 1
                  ┌──────────▼───────────┐    ┌─────────▼────────────┐
                  │       Profile        │    │ UserPrivacySettings  │
                  └──────────────────────┘    └──────────────────────┘
                             │ 1
        ┌────────────────────┼────────────────────┬────────────────────┐
        │ N                  │ N                  │ N                  │ N
┌───────▼───────┐    ┌───────▼───────┐    ┌───────▼───────┐    ┌───────▼───────┐
│     Skill     │    │   Formation   │    │PersonalityTest│    │  Prediction   │
└───────────────┘    └────────────────┘    └───────┬───────┘    └───────────────┘
                                                   │ 1
                                       ┌───────────▼───────────┐
                                       │       PCMResult       │
                                       └───────────────────────┘
```

---

## 🔌 Intégrations externes

1.  **OpenRouter (Claude 3.5 Sonnet) / Ollama (Llama 3.2) :** Utilisés pour la génération de résumés, l'extraction de compétences des CV et la génération de scénarios comportementaux interactifs.
2.  **n8n Workflows :** Agent orchestrateur de tâches asynchrones pour l'évaluation de personnalité MBTI/PCM.
3.  **Twilio SMS API :** Utilisé pour l'envoi de messages d'alertes ou de double authentification.
4.  **Social Login (Google & GitHub) :** Authentification OAuth2 intégrée directement.
5.  **Service d'Analyse IA Python (Port 8000) :** Microservice externe gérant les analyses poussées de code GitHub et les algorithmes prédictifs complexes de carrière.

---

## 🚀 Déploiement & Configuration

### Variables d'environnement clés (`.env`)
```properties
# Base URLs
APP_BASE_URL=http://localhost:8081
FRONTEND_BASE_URL=http://localhost:4200

# Base de données PostgreSQL
DB_URL=jdbc:postgresql://localhost:5432/talentpredict
DB_USERNAME=postgres
DB_PASSWORD=********

# Sécurité & Social OAuth
JWT_SECRET=TP_JWT_...
GOOGLE_CLIENT_ID=...
GITHUB_CLIENT_ID=...

# Orchestrateur n8n & Ollama
N8N_BASE_URL=http://localhost:5678
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=llama3.2:latest

# Service d'analyse IA Python (talentpredict-ai)
AI_SERVICE_BASE_URL=http://localhost:8000
AI_SERVICE_API_KEY=TP_AI_...

# LLM APIs
OPENROUTER_API_KEY=...
ANTHROPIC_API_KEY=...
```

---

## 📈 Tolérance aux pannes (Circuit Breaker)

Les appels vers le service Python s'exécutent au sein d'une sandbox Resilience4j.
*   **Time Limiter :** Timeout fixé à 5 secondes pour éviter de bloquer les threads Tomcat du backend.
*   **Circuit Breaker :** Si le service Python rencontre des erreurs répétées (ex: 50% d'échecs sur les 10 dernières requêtes), le circuit s'ouvre, redirigeant instantanément tous les appels suivants vers la méthode `fallbackAnalysis`. Cela évite les temps d'attente utilisateur et soulage le microservice défaillant.
