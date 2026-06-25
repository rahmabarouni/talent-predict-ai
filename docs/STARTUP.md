# TalentPredict Local Startup Guide

## Architecture Simplifiée

```
Docker (port 5678):
  - n8n (workflow engine, SQLite embedded)

Local (Single Machine):
  - PostgreSQL (port 5432) - Database
  - Python AI Service (port 8000) - Talent analysis agent
  - Spring Boot Backend (port 8081) - API server (incorporates Apache PDFBox for server-side CV text extraction)
  - Angular Frontend (port 4200) - Web UI (incorporates pdfjs-dist for client-side CV text extraction)
```

> [!NOTE]
> Le service Python CV Extractor (anciennement sur le port 9000) est obsolète/déprécié. L'extraction de texte est dorénavant directement intégrée dans l'application via une approche hybride client/serveur.

## Prérequis

### 1. Installer les dépendances Python

```powershell
# Depuis la racine du projet
pip install -r talentpredict-ai\requirements.txt
```

### 2. Démarrer PostgreSQL localement

Option A - Si vous avez PostgreSQL installé:

```powershell
# Windows - Démarrer le service PostgreSQL
net start PostgreSQL-x64-16
```

Option B - Si pas encore créé:
Télécharger et installer: https://www.postgresql.org/download/windows/
Configuration:

- User: postgres
- Password: 11111111
- Database: talentpredict

### 3. Créer la base de données

```powershell
# Connexion à PostgreSQL
psql -U postgres

# Dans psql:
CREATE DATABASE talentpredict;
\q
```

---

## Démarrage en 6 étapes (6 Terminaux PowerShell):

### Terminal 1: n8n Docker

```powershell
# Depuis la racine du projet
docker-compose up n8n
```

✅ Attendez: "n8n listening on port 5678"
Access: http://localhost:5678

---

### Terminal 2: PostgreSQL

```powershell
# Vérifier que PostgreSQL est lancé
net start PostgreSQL-x64-16
```

✅ Attendez: Status = started
ou juste laissez tourneren arrière-plan

---

### [DÉPRÉCIÉ] Terminal 3: CV Extractor Service

> [!WARNING]
> **Étape Obsolète / Non Requise :** Le service CV Extractor sur le port 9000 n'est plus utilisé. Vous pouvez sauter cette étape de démarrage.

---

### Terminal 4: Python AI Service

```powershell
cd talentpredict-ai
python main.py
```

✅ Attendez: "Application startup complete"

---

### Terminal 5: Spring Boot Backend

```powershell
cd BackEnd
mvn spring-boot:run
```

✅ Attendez: "Started TalentPredictApplication"

---

### Terminal 6: Angular Frontend

```powershell
cd FrontEnd
npm start
```

✅ Attendez: "Application bundle generated successfully"
Access: http://localhost:4200

---

## Flux de Données Simplifié

### Flux Historique (Obsolète) :

```
Angular → Backend → n8n
                  ├─→ PDF Extraction Server (n8n port 3001)
                  ├─→ GitHub API
                  └─→ Ollama LLM
```

### Flux Actuel (Hybride & Proxy) :

```
                       ┌──► [Client-Side] Angular (pdfjs-dist)
                       │
                       └──► [Server-Side] Spring Boot (Apache PDFBox)
                                       │
                                       ├─► [Python AI Service :8000] (via Spring Boot Proxy :8081)
                                       │    └─► Agentic loop / Scraper / GitHub REST API
                                       │
                                       ├─► [n8n Webhook :5678]
                                       │    └─► Workflows (GitHub / PCM Analysis)
                                       │
                                       └─► [Ollama :11434 / OpenRouter] (LLM Pipeline)
```

---

## Vérification de Santé

Tester chaque endpoint:

### 1. CV Extractor (port 9000) — [DÉPRÉCIÉ]

*(Non requis car le service est obsolète)*

### 2. n8n (port 5678)

```powershell
Invoke-WebRequest -Uri "http://localhost:5678/health" -UseBasicParsing
```

Expected: Status 200 OK

### 3. Python AI (port 8000)

```powershell
Invoke-WebRequest -Uri "http://localhost:8000/health" -UseBasicParsing
```

Expected: `{"status":"healthy",...}`

### 4. Backend (port 8081)

```powershell
Invoke-WebRequest -Uri "http://localhost:8081/actuator/health" -UseBasicParsing
```

Expected: `{"status":"UP"}`

### 5. Frontend (port 4200)

```powershell
Invoke-WebRequest -Uri "http://localhost:4200" -UseBasicParsing | Select-Object -First 3
```

Expected: HTML content

---

## Configuration Backend (application.properties)

Vérifier que `BackEnd/src/main/resources/application.properties` contient:

```properties
# n8n Endpoint (NO TRAILING SPACES!)
n8n.base-url=http://localhost:5678

# CV Extractor Endpoint
cv.extractor.url=http://localhost:9000

# Ollama Endpoint
ollama.base-url=http://localhost:11434

# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/talentpredict
spring.datasource.username=postgres
spring.datasource.password=11111111
```

---

## Gestion de l'Extraction des CV (Hybride Client/Serveur)

TalentPredict n'utilise plus de service d'extraction de CV externe sur le port 9000. À la place, l'extraction de texte est gérée de manière transparente :
- **Côté Client (Frontend Angular) :** Géré par `pdfjs-dist` dans `CvExtractorService` lors du dépôt initial par le candidat.
- **Côté Serveur (Backend Spring Boot) :** Géré par `CvAnalysisService` qui utilise **Apache PDFBox** pour extraire le texte du fichier PDF téléchargé, avant de l'envoyer au LLM via OpenRouter ou Ollama.

---

## Utilitaires & Scripts

Les scripts de gestion ont été regroupés dans le dossier `scripts/` :

| Script | Description |
| --- | --- |
| `scripts/start.sh` | Script de démarrage global (Bash) |
| `scripts/manage-workflows.sh` | Import/Export des workflows n8n |
| `scripts/db/init-databases.sql` | Initialisation des bases PostgreSQL |

---

## Arrêt Propre

```powershell
# Terminal Docker
Ctrl+C

# Autres terminaux
Ctrl+C

# PostgreSQL
net stop PostgreSQL-x64-16
```

---

## Troubleshooting

| Problème                    | Solution                                     |
| --------------------------- | -------------------------------------------- |
| Port 5678 déjà utilisé      | `docker container kill talentpredict-n8n`    |
| PostgreSQL connexion échoue | Vérifier: `psql -U postgres`                 |
| n8n workflows perdus        | Réimporter depuis `n8n/workflows-import/`    |
| Ollama not found            | Exécuter: `ollama serve` (separate terminal) |

---

## État Actuel Résumé

    ✅ Architecture: n8n (Docker) + tout le reste (Local)
    ✅ n8n: SQLite intégré dans volume
    ✅ Base TalentPredict: PostgreSQL séparé
    ✅ CV Extraction: Intégration hybride (Frontend pdfjs-dist / Backend PDFBox)
    ✅ Flows: Optimisé, pas de dépendances Docker inutiles

🚀 **Status: PRÊT À DÉMARRER**
