# TalentPredict - Explication simple et claire (Back, Front, DB, n8n)

## 1) Vue d'ensemble

TalentPredict est organise en 4 blocs principaux:

- Frontend Angular: interface utilisateur (employe et RH/admin).
- Backend Spring Boot: API REST, logique metier, securite JWT, persistance.
- Base de donnees PostgreSQL: stockage des utilisateurs, tests, predictions, formations, skills.
- n8n (workflows): orchestration IA (CV + PCM + GitHub) puis synthese finale.

Flux global:

1. Le Front envoie une requete au Back (ex: analyse soft skills).
2. Le Back appelle n8n via webhook.
3. n8n orchestre plusieurs sous-workflows et retourne un JSON final.
4. Le Back normalise et sauvegarde en DB.
5. Le Front lit les donnees via API et affiche dashboards/resultats.

## 2) Organisation DB - quelles tables, quoi, et ou

La DB principale du projet est PostgreSQL sur:

- DB applicative: talentpredict (via spring.datasource.url)
- DB n8n: n8n (initialisee dans init-databases.sql)

Remarque importante:

- Le schema de la DB applicative est cree/maj automatiquement par Hibernate (spring.jpa.hibernate.ddl-auto=update).

### 2.1 Tables coeur utilisateur

- users
  - Stocke: compte utilisateur (email, password hash, role, actif), infos RH (firstName, lastName, department, position, hireDate).
  - Cle de liaison principale: user_id dans les autres tables.

- profiles
  - Stocke: profil pro detaille (titre, description, liens LinkedIn/GitHub/CV/portfolio, stats GitHub, aiSummary).
  - Relation: 1 profil pour 1 user.

- password_reset_tokens
  - Stocke: token de reset mdp, expiration, etat used.
  - Relation: plusieurs tokens peuvent pointer vers un user.

### 2.2 Tables evaluation / soft skills

- tests_personnalite
  - Stocke: chaque test de personnalite (type_test, score, resultats texte, analyse_llm, date_test).
  - Relation: plusieurs tests pour un user.

- test_reponses (table d'ElementCollection)
  - Stocke: les reponses question par question (q1..q18) d'un test.
  - Relation: liee a tests_personnalite via test_id.

- pcm_results
  - Stocke: resultat PCM structure (type_pcm + sous-scores: travail, secondaire, reactif, rebelle).
  - Relation: lie a profile (profile_id).

- competences
  - Stocke: referentiel de competences (nom, categorie, niveau).

- competence_user
  - Stocke: score d'une competence pour un user (table de liaison user <-> competence).

### 2.3 Tables IA / recommandations

- predictions
  - Stocke: sortie IA consolidee (analyse_text, recommandation_soft/tech, score_confiance, statut, date_prediction).
  - Relation: plusieurs predictions par user.
  - C'est la table centrale pour l'historique des analyses soft skills.

- recommendations
  - Stocke: recommendation globale (titre, description, priorite, score) par user.

- recommendation_items
  - Stocke: details des recommendations (items textes) lies a recommendations.

### 2.4 Tables formations / execution

- formations
  - Stocke: formations proposees/actives/terminees (titre, progression, statut, dates).
  - Relation: liee a user et potentiellement a prediction.

- inscriptions
  - Stocke: inscription d'un user a une formation (statut, progression, note finale).

- tickets
  - Stocke: tickets Jira lies a une formation (jira_key, statut, priorite, assignee, url).

## 3) Comment marchent les workflows n8n

Workflow principal actif:

- master soft skills agent (4).json
- Webhook principal: POST /webhook/master-agent

Logique du master workflow:

1. Recoit payload (nom, email, texte CV, reponses q1..q18, github_username).
2. Node Code "Prepare & Call Sub-Workflows" appelle en parallele 3 webhooks:
   - /webhook/soft-skills-extract (analyse CV)
   - /webhook/pcm-test (analyse questionnaire PCM)
   - /webhook/github-analysis (analyse comportement GitHub)
3. Les 3 sorties sont fusionnees.
4. Node LLM (Ollama) fait la synthese finale et produit un JSON metier.
5. Node "Parse & Validate Final Response" securise le JSON:
   - nettoie/parsing
   - force une structure minimale valide
   - applique des valeurs fallback en cas de reponse LLM invalide
6. Respond to Webhook renvoie le JSON final au Backend.

### 3.1 Role des sous-workflows

- CV Parser (1).json
  - Extrait le texte CV (appel service d'extraction PDF), puis LLM pour evaluer soft skills depuis le CV.

- test PCM.json
  - Calcule des scores a partir de q1..q18 (communication, discipline, curiosite, collaboration, ownership, leadership), puis overall_score.

- GitHub Analysis (3).json
  - Appelle l'API GitHub (profil + repos), construit un resume, puis LLM evalue les soft skills.

## 4) Integration Backend <-> n8n

Point d'entree Backend:

- Controller: /api/soft-skills
  - POST /analyze
  - POST /reevaluate
  - GET /progress
  - GET /last

Service cle:

- N8nSoftSkillsService
  - Lit config:
    - n8n.base-url (defaut http://localhost:5678)
    - n8n.webhook.soft-skills (defaut /webhook/master-agent)
  - Appelle n8n avec RestTemplate.
  - Parse les formats de reponse n8n (objet/array, json/text).
  - Mappe les champs metier (overall_score, merged_soft_skills, personality_type, summary, etc.).
  - Applique des fallbacks si donnees manquantes.
  - Canonicalise le personality type vers le format PCM attendu.

Service metier de persistence:

- SoftSkillsService
  - analyze(): appelle n8n, puis persist.
  - persist():
    - cree une ligne predictions (analyse + score + statut)
    - cree des lignes skills de type SOFT (a partir de merged_soft_skills)
    - prepare recommandations
  - getLastAnalysis()/getProgress(): relit predictions et reconstruit le DTO de sortie.

## 5) Integration Front <-> Backend

Routes Angular utiles:

- /evaluation/soft-skills
- /evaluation/soft-skills-results
- /dashboard (employe)
- /admin/dashboard (RH)

Service Front principal:

- SoftSkillsService (Angular)
  - POST /api/soft-skills/analyze
  - POST /api/soft-skills/reevaluate
  - GET /api/soft-skills/progress
  - GET /api/soft-skills/last

Dashboard:

- User dashboard: GET /api/dashboard/users/{userId}
- Admin dashboard: GET /api/dashboard/admin/overview

## 6) Comment tout est lie (scenario simple)

Scenario: un employe lance un test soft skills

1. Front envoie fullName, email, githubUsername, cvText, q1..q18 au Backend.
2. Backend /api/soft-skills/analyze appelle n8n /webhook/master-agent.
3. n8n lance CV + PCM + GitHub en parallele et synthese finale.
4. Backend recoit JSON final (score, personality_type, soft skills, summary...).
5. Backend sauvegarde:
   - predictions: analyse complete + score global
   - skills: chaque soft skill en lignes (type SOFT)
6. Front redirige vers page resultats et affiche details.
7. Dashboard employe/RH relit ces donnees via endpoints dashboard.

## 7) Ce qu'il faut retenir pour ton binome

- Source de verite applicative: PostgreSQL (table predictions + skills + tests + users).
- n8n ne remplace pas la DB: il orchestre le calcul IA puis renvoie un resultat.
- Le Backend est le pivot:
  - securite
  - appel n8n
  - normalisation
  - persistence
  - exposition API au Front
- Le Front ne parle jamais directement a n8n; il passe toujours par le Backend.

## 8) Fichiers de reference (les plus utiles)

Backend:

- BackEnd/src/main/resources/application.properties
- BackEnd/src/main/java/com/talentpredict/modules/ai/controllers/SoftSkillsController.java
- BackEnd/src/main/java/com/talentpredict/modules/ai/services/N8nSoftSkillsService.java
- BackEnd/src/main/java/com/talentpredict/modules/ai/services/SoftSkillsService.java
- BackEnd/src/main/java/com/talentpredict/modules/dashboard/controllers/DashboardController.java
- BackEnd/src/main/java/com/talentpredict/modules/dashboard/services/DashboardService.java

DB (entites):

- BackEnd/src/main/java/com/talentpredict/modules/user/entities/User.java
- BackEnd/src/main/java/com/talentpredict/modules/user/entities/Profile.java
- BackEnd/src/main/java/com/talentpredict/modules/evaluation/entities/PersonalityTest.java
- BackEnd/src/main/java/com/talentpredict/modules/evaluation/entities/PCMResult.java
- BackEnd/src/main/java/com/talentpredict/modules/skills/entities/Skill.java
- BackEnd/src/main/java/com/talentpredict/modules/ai/entities/Prediction.java
- BackEnd/src/main/java/com/talentpredict/modules/formation/entities/Formation.java

n8n:

- n8n/workflows-import/master soft skills agent (4).json
- n8n/workflows-import/CV Parser (1).json
- n8n/workflows-import/test PCM.json
- n8n/workflows-import/GitHub Analysis (3).json

---

Si tu veux, je peux aussi te faire une version ultra-courte (1 page) specialement pour presentation orale (5 minutes).
