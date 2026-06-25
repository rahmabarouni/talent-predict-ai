# Documentation API TalentPredict

## 📚 Table des matières
1. [Authentification](#authentification)
2. [Utilisateurs (Users)](#utilisateurs-users)
3. [Profils (Profiles)](#profils-profiles)
4. [Soft Skills / Personnalité](#soft-skills--personnalité)
5. [Compétences (Skills)](#compétences-skills)
6. [Formations](#formations)
7. [Prédictions de Carrière](#prédictions-de-carrière)
8. [Dashboard](#dashboard)
9. [Assessment & Analyse IA (Proxies)](#assessment--analyse-ia-proxies)
10. [Tickets Jira (Obsolète)](#tickets-jira-obsolète)

> **Base URL:** `http://localhost:8081`  
> **Identifiants:** Tous les identifiants sont des `UUID` (ex: `550e8400-e29b-41d4-a716-446655440000`)

---

## 🔐 Authentification

Toutes les routes sauf `/api/auth/*` nécessitent l'en-tête suivant :
```
Authorization: Bearer {token}
```

### Inscription
Créer un nouveau compte utilisateur.

**Endpoint:** `POST /api/auth/register`

**Request Body:**
```json
{
  "firstName": "Jean",
  "lastName": "Dupont",
  "email": "jean.dupont@example.com",
  "password": "password123"
}
```

**Response:** `201 Created`
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "jean.dupont@example.com",
  "role": "USER",
  "nom": "Dupont",
  "prenom": "Jean"
}
```

### Connexion
Se connecter avec un compte existant.

**Endpoint:** `POST /api/auth/login`

**Request Body:**
```json
{
  "email": "jean.dupont@example.com",
  "password": "password123"
}
```

**Response:** `200 OK`
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "email": "jean.dupont@example.com",
  "role": "USER",
  "nom": "Dupont",
  "prenom": "Jean"
}
```

---

## 👤 Utilisateurs (Users)

Gestion des utilisateurs dans le système (portée globale et gamification).

### Lister tous les utilisateurs (Admin)
**Endpoint:** `GET /api/users`  
**PreAuthorize:** `hasRole('ADMIN')`

**Response:** `200 OK`
```json
[
  {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "firstName": "Jean",
    "lastName": "Dupont",
    "email": "jean.dupont@example.com",
    "role": "USER",
    "xp": 350,
    "level": 3
  }
]
```

### Obtenir le Leaderboard de Gamification
Liste des 10 meilleurs utilisateurs triés par XP.

**Endpoint:** `GET /api/users/leaderboard`  
**PreAuthorize:** `hasAnyRole('USER', 'ADMIN')`

**Response:** `200 OK`
```json
[
  {
    "userId": "550e8400-e29b-41d4-a716-446655440000",
    "firstName": "Jean",
    "lastName": "Dupont",
    "xp": 350,
    "level": 3
  }
]
```

### Créer un utilisateur (Admin)
**Endpoint:** `POST /api/users`  
**PreAuthorize:** `hasRole('ADMIN')`

**Request Body:**
```json
{
  "firstName": "Alice",
  "lastName": "Martin",
  "email": "alice.martin@example.com",
  "password": "password123"
}
```

**Response:** `201 Created`

### Obtenir un utilisateur par ID
**Endpoint:** `GET /api/users/{userId}`  
**PreAuthorize:** `hasAnyRole('USER', 'ADMIN')` (Vérification de propriété pour les non-admins)

### Mettre à jour un utilisateur
**Endpoint:** `PUT /api/users/{userId}`  
**PreAuthorize:** `hasAnyRole('USER', 'ADMIN')`

### Obtenir les statistiques agrégées d'un utilisateur (Admin)
Retourne les statistiques réelles d'apprentissage et d'analyse pour le panneau d'administration.

**Endpoint:** `GET /api/users/{userId}/summary`  
**PreAuthorize:** `hasRole('ADMIN')`

**Response:** `200 OK`
```json
{
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "formationsTotal": 12,
  "formationsEnCours": 3,
  "formationsTerminees": 7,
  "predictionsCount": 2,
  "latestPredictionScore": 0.85,
  "latestPredictionDate": "2026-02-02T10:30:00Z",
  "latestPredictionLabel": "COMPLETEE",
  "githubUrl": "https://github.com/jdupont",
  "linkedinUrl": "https://linkedin.com/in/jdupont"
}
```

### Supprimer un utilisateur (Admin)
**Endpoint:** `DELETE /api/users/{userId}`  
**PreAuthorize:** `hasRole('ADMIN')`

---

## 📄 Profils (Profiles)

Détails professionnels associés à un utilisateur, y compris l'analyse de CV et les liens réseaux sociaux.

### Obtenir le profil par ID Utilisateur
**Endpoint:** `GET /api/profiles/users/{id}`  
**PreAuthorize:** `hasAnyRole('USER', 'ADMIN')`

**Response:** `200 OK`
```json
{
  "id": "220e8400-e29b-41d4-a716-446655440099",
  "titreProfessionnel": "Développeur Full Stack Senior",
  "description": "Spécialisé en architectures Java/Angular.",
  "experienceAns": 6,
  "cvUrl": "http://localhost:8081/uploads/cvs/cv_123.pdf",
  "urlPhoto": "http://localhost:8081/uploads/photos/photo_123.jpg",
  "githubUrl": "https://github.com/jdupont",
  "lienLinkedin": "https://linkedin.com/in/jdupont",
  "statutAnalyse": "SUCCESS",
  "estPublie": true
}
```

### Mettre à jour le profil (partiel)
**Endpoint:** `PUT /api/profiles/users/{id}`  
**PreAuthorize:** `hasAnyRole('USER', 'ADMIN')`

### Uploader une photo de profil
**Endpoint:** `POST /api/profiles/accounts/{id}/upload-photo`  
**Consumes:** `multipart/form-data`  
**Paramètre:** `file` (MultipartFile - Images uniquement)

### Uploader et analyser un CV (PDF)
Enregistre le PDF, met à jour le lien dans le profil, et extrait automatiquement les compétences et les détails professionnels (titre, bio, années d'expérience) via LLM.

**Endpoint:** `POST /api/profiles/accounts/{id}/upload-cv`  
**Consumes:** `multipart/form-data`  
**Paramètre:** `file` (MultipartFile - Fichiers PDF uniquement)

**Response:** `200 OK`
```json
{
  "message": "Votre profil a été mis à jour et 3 nouveaux skills ont été détectés depuis votre CV",
  "status": "SUCCESS",
  "skillsAjoutes": ["Docker", "Kubernetes", "Angular"],
  "skillsDejaPresentss": ["Java", "Spring Boot"],
  "totalDetectes": 5,
  "extractedInfo": {
    "title": "Développeur Cloud",
    "experience": 5
  }
}
```

### Lancer une analyse de profil IA (Arrière-plan)
Déclenche une analyse IA asynchrone complète du profil et des compétences de l'employeur.

**Endpoint:** `POST /api/profiles/accounts/{id}/analyse-ia`  
**Response:** `200 OK` (PROCESSING)

### Suivre le statut de l'analyse IA (Polling)
**Endpoint:** `GET /api/profiles/accounts/{id}/analyse-status`  
**Response:** `200 OK`
```json
{
  "status": "SUCCESS",
  "timestamp": "2026-05-22T21:00:00Z",
  "skillsFound": 8
}
```

### Publier le profil
**Endpoint:** `POST /api/profiles/accounts/{id}/publish`

---

## 🧠 Soft Skills / Personnalité

Remplaçant l'ancien `/api/tests/*`, ce contrôleur gère l'évaluation comportementale MBTI/PCM et le simulateur de scénarios.

### Analyser un questionnaire Soft Skills
**Endpoint:** `POST /api/soft-skills/analyze`  
**PreAuthorize:** `hasAnyRole('USER', 'ADMIN')`

**Request Body:**
```json
{
  "testType": "MBTI",
  "answers": {
    "q1": "A",
    "q2": "B"
  }
}
```

**Response:** `200 OK`
```json
{
  "id": "e30e8400-e29b-41d4-a716-446655440001",
  "testType": "MBTI",
  "mbtiType": "ENTJ",
  "pcmType": "PROMOTER",
  "llmAnalysis": "Profil fort en leadership, axé sur les résultats...",
  "createdAt": "2026-05-22T21:10:00Z"
}
```

### Réévaluer une analyse Soft Skills
**Endpoint:** `POST /api/soft-skills/reevaluate`

### Sauvegarder les résultats d'un scénario de simulation
**Endpoint:** `POST /api/soft-skills/scenario/save`

### Obtenir l'historique de progression des Soft Skills
**Endpoint:** `GET /api/soft-skills/progress`

### Obtenir la dernière analyse comportementale
**Endpoint:** `GET /api/soft-skills/last`

---

## 💼 Compétences (Skills)

### Ajouter une compétence à un utilisateur
**Endpoint:** `POST /api/skills/accounts/{userId}`  
**PreAuthorize:** `hasAnyRole('USER', 'ADMIN')` (vérification de propriété)

**Request Body:**
```json
{
  "nom": "Spring Security",
  "type": "TECH",
  "niveau": 4,
  "description": "Sécurisation d'API REST"
}
```

**Response:** `201 Created`

### Lister les compétences d'un utilisateur
**Endpoint:** `GET /api/skills/accounts/{userId}`

### Filtrer les compétences par type (`SOFT`, `TECH`)
**Endpoint:** `GET /api/skills/accounts/{userId}/type/{type}`

### Valider une compétence (Admin)
**Endpoint:** `PUT /api/skills/{skillId}/valider`  
**PreAuthorize:** `hasRole('ADMIN')`

**Response:** `200 OK` (Met `validee` à `true` et octroie des XP à l'utilisateur)

### Supprimer une compétence
**Endpoint:** `DELETE /api/skills/{skillId}`

---

## 🎓 Formations

Gestion du plan de formation, progression et gamification liée à la complétion.

### Assigner une formation à un utilisateur
**Endpoint:** `POST /api/formations/utilisateur/{userId}`  
**PreAuthorize:** `hasRole('ADMIN')` ou vérification de propriété

**Request Body:**
```json
{
  "titre": "Docker pour les Développeurs",
  "description": "Maîtriser les conteneurs",
  "type": "TECH_SKILL",
  "duree": 20,
  "fournisseur": "Udemy",
  "url": "https://udemy.com/docker",
  "dateDebut": "2026-06-01T09:00:00Z"
}
```

**Response:** `201 Created`

### Lister les formations d'un utilisateur
**Endpoint:** `GET /api/formations/utilisateur/{userId}`

### Lister toutes les formations du système (Admin)
**Endpoint:** `GET /api/formations`  
**PreAuthorize:** `hasRole('ADMIN')`

### Obtenir une formation par ID
**Endpoint:** `GET /api/formations/{id}`

### Mettre à jour le statut d'une formation
Met à jour l'avancement (`PROPOSEE`, `ACCEPTEE`, `EN_COURS`, `TERMINEE`, `ANNULEE`). Le passage à `TERMINEE` valide les compétences associées et accorde des XP.

**Endpoint:** `PUT /api/formations/{formationId}/statut?statut=TERMINEE`

### Mettre à jour le pourcentage de progression
**Endpoint:** `PUT /api/formations/{formationId}/progression?progression=75`

### Ajouter des notes de révision (Admin)
**Endpoint:** `PUT /api/formations/{formationId}/review-notes`  
**PreAuthorize:** `hasRole('ADMIN')`

**Request Body:**
```json
{
  "notes": "Excellent travail sur les modules de sécurité."
}
```

### Soumettre un Mini-Test (Quiz de fin)
**Endpoint:** `PUT /api/formations/{formationId}/mini-test`  
**Request Body:**
```json
{
  "answers": {
    "q1": "A",
    "q2": "C"
  }
}
```

### Uploader un certificat de formation (PDF)
**Endpoint:** `POST /api/formations/{formationId}/certificate`  
**Consumes:** `multipart/form-data`  
**Paramètre:** `file` (MultipartFile)

### Supprimer une formation
**Endpoint:** `DELETE /api/formations/{id}`

---

## 🔮 Prédictions de Carrière

Analyses prédictives basées sur le profil actuel de l'utilisateur (compétences, MBTI, etc.) générées via OpenRouter.

### Générer une prédiction de carrière
**Endpoint:** `POST /api/predictions/users/{userId}/generer`  
**PreAuthorize:** `hasAnyRole('USER', 'ADMIN')`

**Response:** `200 OK`
```json
{
  "id": "e40e8400-e29b-41d4-a716-446655440023",
  "datePrediction": "2026-05-22T21:15:00Z",
  "analyse": "Sur la base de vos compétences Spring Boot (5) et de votre profil MBTI ENTJ, vous êtes apte à évoluer vers...",
  "recommandationSoft": "Renforcer la communication inter-équipes.",
  "recommandationTech": "Acquérir des bases solides en System Design.",
  "scoreConfiance": 0.88,
  "statut": "COMPLETEE"
}
```

### Lister les prédictions d'un utilisateur
**Endpoint:** `GET /api/predictions/users/{userId}`

### Obtenir la dernière prédiction
**Endpoint:** `GET /api/predictions/users/{userId}/derniere`

---

## 📊 Dashboard

Statistiques et données agrégées pour l'employé et l'administrateur.

### Obtenir le Dashboard Employé
Retourne l'XP, les badges, les formations en cours et les indicateurs clés de performance.

**Endpoint:** `GET /api/dashboard/users/{userId}`

**Response:** `200 OK`
```json
{
  "xp": 350,
  "level": 3,
  "formationsEnCours": 2,
  "formationsTerminees": 5,
  "skillsCount": 8,
  "dernierePredictionScore": 0.88
}
```

### Obtenir la vue d'ensemble du Dashboard RH / Admin
Données de reporting agrégées sur l'ensemble de l'entreprise (nombre total d'utilisateurs, répartition des compétences, progression globale).

**Endpoint:** `GET /api/dashboard/admin/overview`  
**PreAuthorize:** `hasRole('ADMIN')`

**Response:** `200 OK`
```json
{
  "totalUsers": 45,
  "totalFormations": 112,
  "moyenXp": 420,
  "skillDistribution": {
    "Java": 18,
    "Angular": 12,
    "Docker": 8
  }
}
```

---

## 🤖 Assessment & Analyse IA (Proxies)

Endpoints de routage Spring Boot pour communiquer de manière sécurisée avec le service IA Python sous-jacent.

### 1. Analyse Technique du GitHub (Simple)
Analyse les dépôts publics d'un utilisateur selon ses compétences déclarées.

**Endpoint:** `POST /api/assessment/github/analyze`  
**PreAuthorize:** `hasAnyRole('USER', 'ADMIN')`

**Request Body:**
```json
{
  "username": "octocat",
  "claimedSkills": ["Java", "Docker"]
}
```

### 2. Simulateur de Scénario Soft Skills
*   **Générer un scénario :** `POST /api/assessment/scenario/generate`
    *   **Body :** `{"role": "Tech Lead", "level": "Senior"}`
*   **Évaluer une réponse :** `POST /api/assessment/scenario/evaluate`
    *   **Body :** `{"scenario": "...", "response": "..."}`

### 3. Analyse de Candidat (Multipart Proxy)
Transmet les détails de profil complets (GitHub, Portfolio, LinkedIn, CV PDF) au service Python.

**Endpoint:** `POST /api/analysis/analyze-candidate`  
**Consumes:** `multipart/form-data`

### 4. Analyse de Code GitHub Approfondie (Deep Proxy)
**Endpoint:** `POST /api/analysis/github-deep`  
**Request Body:** JSON avec `candidate_id` et détails de dépôts.

### 5. Proxy Circuit-Breaker `/api/v1/analysis/*`
Toutes les requêtes ci-dessous passent par un Circuit Breaker Resilience4j nommé `aiService` pour tolérer les pannes ou les ralentissements du service Python :
*   **GitHub Proxy :** `POST /api/v1/analysis/github` (JSON payload)
*   **Career Prediction Proxy :** `POST /api/v1/analysis/career-prediction` (JSON payload)

---

## 🎫 Tickets Jira (Obsolète)

> [!WARNING]
> **Fonctionnalité Supprimée**
> Les fonctionnalités et tables de suivi de tickets Jira (`/api/tickets/*`) ont été retirées du backend en raison d'une refonte du processus d'approbation (qui se fait maintenant en interne via les notes de révision d'administration Spring Boot).

---

## 🧪 Tests de l'API avec cURL (PowerShell)

```powershell
# 1. Connexion et récupération du Token
$response = Invoke-RestMethod -Method POST -Uri "http://localhost:8081/api/auth/login" `
  -ContentType "application/json" `
  -Body '{"email":"jean.dupont@example.com","password":"password123"}'

$TOKEN = $response.token
$USER_ID = $response.id

# 2. Récupérer le Dashboard
Invoke-RestMethod -Method GET -Uri "http://localhost:8081/api/dashboard/users/$USER_ID" `
  -Headers @{ Authorization = "Bearer $TOKEN" }
```

---

## 🔗 Ressources externes
* [Documentation Spring Boot](https://spring.io/projects/spring-boot)
* [OpenRouter documentation](https://openrouter.ai/docs)
* [Resilience4j guide](https://resilience4j.readme.io/)
