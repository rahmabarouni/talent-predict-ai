# TalentPredict — AI Agent Architecture Documentation

> Complete technical reference for the AI agent microservice and its integration with the Java backend and Angular frontend.

---

## Table of Contents

1. [System Overview](#1-system-overview)
2. [AI Microservice (Python / FastAPI)](#2-ai-microservice-python--fastapi)
   - [Directory Structure](#21-directory-structure)
   - [Entry Point & CORS](#22-entry-point--cors)
   - [API Endpoint](#23-api-endpoint-post-analyze-candidate)
   - [Agent Core (`talent_agent.py`)](#24-agent-core-talent_agentpy)
   - [Tool Definitions](#25-tool-definitions)
   - [Tools Implementation](#26-tools-implementation)
   - [Services](#27-services)
   - [Memory Cache](#28-memory-cache)
   - [System Prompt](#29-system-prompt)
   - [LLM Backend Selection](#210-llm-backend-selection)
   - [Configuration (.env)](#211-configuration-env)
   - [Output Schema](#212-output-schema)
3. [Java Backend Integration](#3-java-backend-integration)
   - [Module Structure](#31-module-structure)
   - [ProfileController — API Endpoints](#32-profilecontroller--api-endpoints)
   - [ProfileAnalysisOrchestrator — 5-Step Pipeline](#33-profileanalysisorchestrator--5-step-pipeline)
   - [PythonAiClient — Bridge to Python](#34-pythonaiclent--bridge-to-python)
   - [OpenRouterService — Claude via Java](#35-openrouterservice--claude-via-java)
   - [CvAnalysisService](#36-cvanalysisservice)
   - [GithubAnalysiService](#37-githubanalasiservice)
   - [PredictionService & PredictionController](#38-predictionservice--predictioncontroller)
   - [Async Configuration](#39-async-configuration)
   - [Analysis Status Tracking](#310-analysis-status-tracking)
   - [Key Backend Configuration Properties](#311-key-backend-configuration-properties)
4. [Angular Frontend Integration](#4-angular-frontend-integration)
   - [Environment Configuration](#41-environment-configuration)
   - [AiAnalysisService](#42-aianalysisservice)
   - [CandidateAnalysis Model](#43-candidateanalysis-model)
   - [GithubAnalyzerComponent](#44-githubanalyzercomponent)
   - [UserProfileComponent](#45-userprofilecomponent)
   - [DashboardService](#46-dashboardservice)
5. [Full Data Flow Diagrams](#5-full-data-flow-diagrams)
   - [Flow A — Direct Frontend → Python Analysis](#flow-a--direct-frontend--python-analysis)
   - [Flow B — Backend-Triggered Full Analysis](#flow-b--backend-triggered-full-analysis)
6. [Database Entities](#6-database-entities)
7. [Deployment](#7-deployment)

---

## 1. System Overview

TalentPredict uses **two parallel AI pipelines** to analyze candidate profiles:

| | Flow A (Proxy-Based) | Flow B (Orchestrated) |
|---|---|---|
| **Trigger** | Angular → Java Proxy | Angular → Java Orchestrator |
| **Entry** | `POST /api/analysis/analyze-candidate` (Java) | `POST /api/profiles/accounts/{id}/analyse-ia` (Java) |
| **LLM** | Claude via Python agentic loop | OpenRouter (Claude) + optional Python |
| **Sources** | GitHub · CV PDF · Portfolio · LinkedIn | CV · GitHub · LinkedIn · PCM personality test |
| **Result** | `CandidateAnalysis` JSON to frontend | Skills persisted in DB + profile summary |
| **Status** | Synchronous response | Async — frontend polls status endpoint |

```
┌──────────────────────────────────────────────────────────┐
│                    Angular Frontend                       │
│  GithubAnalyzerComponent ──► Java Proxy :8081 ──────────►│──► Python :8000
│  UserProfileComponent ──────► Java Orchestrator :8081 ──►│──► Python :8000
│  DashboardComponent ◄─────── predictions ───────────────│◄── Java   :8081
└──────────────────────────────────────────────────────────┘
```

---

## 2. AI Microservice (Python / FastAPI)

### 2.1 Directory Structure

```
talentpredict-ai/
├── main.py                          # FastAPI app, CORS, router mounting
├── Dockerfile                       # python:3.12-slim, port 8000
├── requirements.txt
├── .env.example
├── agents/
│   └── talent_agent.py              # Main agentic loop (Anthropic / OpenRouter / Ollama)
├── api/
│   └── analyze_candidate_route.py   # POST /analyze-candidate endpoint
├── memory/
│   └── agent_memory.py              # Thread-safe in-memory result cache
├── prompts/
│   └── agent_prompt_template.py     # SYSTEM_PROMPT + TOOL_DEFINITIONS (5 tools)
├── services/
│   ├── job_matching.py              # Matches skills against job profiles
│   ├── scoring_engine.py            # Assigns levels (Beginner→Expert) + scores
│   └── skill_extractor.py          # Deduplicates + canonicalizes skills
└── tools/
    ├── cv_parser.py                 # PDF text extraction (pdfplumber)
    ├── github_analyzer.py           # GitHub REST API analysis
    └── portfolio_analyzer.py       # HTTP scraper (httpx + BeautifulSoup)
```

---

### 2.2 Entry Point & CORS

**File:** `talentpredict-ai/main.py`

```python
app = FastAPI(title="TalentPredict AI Service", version="1.0.0")

app.add_middleware(CORSMiddleware,
    allow_origins=env("CORS_ORIGINS", "http://localhost:4200,http://localhost:3000").split(","),
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(analyze_router)  # mounts /analyze-candidate

@app.get("/health")
async def health():
    return {"status": "ok", "service": "talentpredict-ai"}
```

CORS is configured via the `CORS_ORIGINS` environment variable. By default permits
the Angular dev server (`localhost:4200`) and any local port 3000.

---

### 2.3 API Endpoint: `POST /analyze-candidate`

**File:** `talentpredict-ai/api/analyze_candidate_route.py`

Accepts **multipart/form-data**:

| Field | Type | Required | Description |
|---|---|---|---|
| `github` | `string` (Form) | Yes | GitHub username |
| `portfolio` | `string` (Form) | No | Portfolio website URL |
| `cv_file` | `UploadFile` (File) | No | PDF resume |
| `linkedin_url` | `string` (Form) | No | LinkedIn profile URL |
| `linkedin_content` | `string` (Form) | No | Pasted LinkedIn text |

**Processing:**
1. Reads CV bytes → `analyze_cv(cv_bytes)` if PDF was uploaded
2. Calls `run_agent(github_username, portfolio_url, cv_text, linkedin_url, linkedin_content)`
3. Returns structured `CandidateAnalysis` JSON

**Error codes:**
- `422` — agent raised a known error
- `500` — uncaught exception

---

### 2.4 Agent Core (`talent_agent.py`)

**File:** `talentpredict-ai/agents/talent_agent.py`

Public API:
```python
async def run_agent(
    github_username: str | None = None,
    portfolio_url:   str | None = None,
    cv_text:         str | None = None,
    linkedin_url:    str | None = None,
    linkedin_content: str | None = None,
) -> dict[str, Any]
```

**Cache check first:** if `AgentMemory` already has a result for `github_username`, it is returned immediately without calling the LLM.

**Agentic loop (Anthropic / OpenRouter):**

```
1. Build initial message list:
   [SYSTEM_PROMPT] + [user message describing inputs]

2. Loop (max 10 iterations = MAX_ITERATIONS):
   a. Send messages to LLM → get response
   b. If stop_reason == "end_turn" and no tool_use blocks → done
   c. For each tool_use block:
      - dispatch to Python function (_dispatch_tool)
      - append tool_result to conversation
   d. Repeat

3. _parse_final_response(last_text) → extract JSON block from Claude's output

4. Store in AgentMemory
```

**Tool dispatcher:**
```python
"analyze_github"           → analyze_github(username)
"analyze_cv"               → analyze_cv_text(cv_text)
"analyze_portfolio"        → analyze_portfolio(url)
"extract_and_score_skills" → extract_skills(...) + score_skills(...)
"match_job_requirements"   → match_job(skills, experience_score)
```

---

### 2.5 Tool Definitions

**File:** `talentpredict-ai/prompts/agent_prompt_template.py`

Five tools exported as `TOOL_DEFINITIONS` (Anthropic format; auto-converted to OpenAI format for OpenRouter):

| Tool | Parameters | Purpose |
|---|---|---|
| `analyze_github` | `username: str` | Fetch repos, languages, frameworks from GitHub API |
| `analyze_cv` | `cv_text: str` | Parse resume text for skills and experience years |
| `analyze_portfolio` | `url: str` | Scrape portfolio website for technology mentions |
| `extract_and_score_skills` | `github_data`, `cv_data`, `portfolio_data` | Consolidate all sources → canonical skill list with levels |
| `match_job_requirements` | `skills`, `experience_score` | Compare against job profiles, generate recommendations |

---

### 2.6 Tools Implementation

#### `tools/github_analyzer.py`

```python
async def analyze_github(username: str) -> dict
```

- `GET https://api.github.com/users/{username}` → name, bio, followers, public_repos
- `GET https://api.github.com/users/{username}/repos?per_page=100&sort=updated&type=owner`
  - Skips forks
  - Aggregates language occurrence counts
  - Detects frameworks from topics, descriptions, and repo names using `FRAMEWORK_INDICATORS`
    (React, Angular, Vue, Spring Boot, Django, Flask, FastAPI, Express, Docker, Kubernetes,
    TensorFlow, PyTorch, Rails, Laravel, .NET, Flutter, Svelte, …)
- Returns top 15 repos sorted by stars
- `GITHUB_TOKEN` env var raises rate limit from 60 to 5000 req/hr

**Return shape:**
```json
{
  "username": "...", "name": "...", "bio": "...",
  "public_repos": 0, "followers": 0,
  "repositories_analyzed": 0,
  "language_stats": { "Python": 12, "JavaScript": 5 },
  "detected_frameworks": ["React", "Docker"],
  "top_repos": [{ "name": "...", "language": "...", "stars": 0, "topics": [] }]
}
```

---

#### `tools/cv_parser.py`

Two entry points:
- `analyze_cv(cv_bytes: bytes)` — raw PDF bytes, extracts with `pdfplumber`
- `analyze_cv_text(cv_text: str)` — pre-extracted string (used by agent tool dispatcher)

Both call:
- `_find_technologies(text)` — regex scan for ~60 tech keywords (Python, Java, React, Angular, Docker, Kubernetes, AWS, Spring Boot, …)
- `_extract_years_of_experience(text)` — matches patterns like `"5 years of experience"`, `"3+ ans d'expérience"`

Text is truncated to 3000 characters for token budget.

---

#### `tools/portfolio_analyzer.py`

```python
async def analyze_portfolio(url: str) -> dict
```

- Validates URL (adds `https://` if missing)
- `httpx.AsyncClient` fetch: timeout=20s, follows redirects, `User-Agent: TalentPredict-Bot/1.0`
- Strips HTML with `BeautifulSoup`, extracts visible text
- Scans for ~50 tech keywords (`PORTFOLIO_TECH_KEYWORDS`)
- Extracts meta tags: title, description, og:title, og:description, keywords
- Returns `page_text_preview` truncated to 2000 chars

---

### 2.7 Services

#### `services/skill_extractor.py`

```python
def extract_skills(github_data, cv_data, portfolio_data) -> list[dict]
```

- Merges skills from all three sources into a `skill_map` dict
- `_canonicalize(name)` resolves aliases via a 100+ entry `_ALIASES` dict
  (e.g. `js` → `JavaScript`, `spring` → `Spring Boot`, `k8s` → `Kubernetes`)
- Each skill: `{ name, sources: [sorted], occurrence_count }`
- Returns sorted by `occurrence_count` desc, then alphabetically

---

#### `services/scoring_engine.py`

```python
def score_skills(skills, github_data=None) -> tuple[list[dict], int]
```

Level determination heuristic:

| Condition | Level | Score range |
|---|---|---|
| 3+ sources | Expert | 85–100 |
| 2+ sources, 4+ occurrences | Advanced | 80–100 |
| 2+ sources | Advanced | 70–89 |
| 1 source, 3+ occurrences OR 5+ GitHub repos | Intermediate | 50–69 |
| 1 source, 2 occurrences | Intermediate | 45 |
| 1 source, 1 occurrence | Beginner | 25 |

**Experience score** = weighted average of all skill scores × 1.1, capped at 100.

---

#### `services/job_matching.py`

```python
def match_job(skills, experience_score, profile_name="Full-Stack Developer") -> dict
```

Three built-in job profiles: `Full-Stack Developer`, `Backend Developer`, `Frontend Developer`.

Each profile defines required skills with `min_level` and `weight`. Scoring:
1. Full `weight` if candidate level ≥ required minimum, partial credit otherwise
2. Bonus (up to 10 pts) for extra skills beyond the profile requirements
3. Final score = `(earned/total × 85 + bonus) × 0.9 + experience_score × 0.1`, capped at 100

Returns: `job_profile`, `job_match_score`, `matched_skills`, `missing_skills`, `recommendations` (max 8)

---

### 2.8 Memory Cache

**File:** `talentpredict-ai/memory/agent_memory.py`

Thread-safe in-process cache using `threading.Lock`:

```python
class AgentMemory:
    def get(self, username: str) -> dict | None
    def set(self, username: str, data: dict) -> None
    def clear(self, username: str | None = None) -> None
    def list_keys(self) -> list[str]

memory = AgentMemory()  # module-level singleton
```

A cache hit on `username` skips the entire LLM call and returns the stored result immediately.

---

### 2.9 System Prompt

**File:** `talentpredict-ai/prompts/agent_prompt_template.py`

`SYSTEM_PROMPT` defines the agent as an **Elite Career Intelligence Analyst** with:

- Input protocols for each source: LinkedIn (URL or pasted text), GitHub, CV, Portfolio
- Strict behavioral rules: no fabrication, language-adaptive (responds in user's language), no filler phrases
- Mandatory JSON output block at the end of every response (see [§2.12](#212-output-schema))

---

### 2.10 LLM Backend Selection

The agent auto-detects the LLM backend at runtime from environment variables:

| Condition | Backend | Method |
|---|---|---|
| `ANTHROPIC_API_KEY` starts with `sk-ant-` | Anthropic native | `_run_agent_anthropic()` using `anthropic.Anthropic()` SDK |
| `ANTHROPIC_API_KEY` starts with `sk-or-` | OpenRouter | `_run_agent_openrouter()` using OpenAI-compatible `/chat/completions` |
| `ANTHROPIC_BASE_URL` contains `11434` or `ollama` | Ollama (local) | `_run_direct_pipeline()` — no function-calling, tools run sequentially |

For **Ollama**, the agent runs all tools directly then asks the local model for a short summary only — it does not use the tool-calling protocol since most local models don't support it reliably.

---

### 2.11 Configuration (.env)

```ini
# ── Option A: Anthropic native ──────────────────────────────
ANTHROPIC_API_KEY=sk-ant-...
ANTHROPIC_MODEL=claude-sonnet-4-20250514

# ── Option B: OpenRouter ─────────────────────────────────────
ANTHROPIC_BASE_URL=https://openrouter.ai/api/v1
ANTHROPIC_API_KEY=sk-or-...
ANTHROPIC_MODEL=anthropic/claude-sonnet-4

# ── Option C: Ollama (local, no API key) ────────────────────
ANTHROPIC_BASE_URL=http://localhost:11434/v1
ANTHROPIC_MODEL=llama3.2

# ── Common ───────────────────────────────────────────────────
GITHUB_TOKEN=ghp_...                  # optional, raises rate limit to 5000/hr
CORS_ORIGINS=http://localhost:4200,http://localhost:3000
```

---

### 2.12 Output Schema

Every successful analysis returns a JSON object matching this shape:

```jsonc
{
  "candidate": "github_username",
  "data_sources": ["github", "cv", "portfolio", "linkedin"],
  "summary": "1–2 sentence professional summary.",
  "skills": [
    {
      "name": "Python",
      "level": "Expert",          // Beginner | Intermediate | Advanced | Expert
      "score": 92,                // 0–100
      "sources": ["github", "cv"] // where the skill was detected
    }
  ],
  "experience_score": 78,         // 0–100 overall experience
  "repositories_analyzed": 24,
  "top_languages": ["Python", "TypeScript", "Java"],
  "linkedin_analysis": "...",     // full LinkedIn block, or empty string
  "job_match": {
    "profile": "Full-Stack Developer",
    "score": 81,
    "matched_skills": ["React", "Spring Boot"],
    "missing_skills": ["Kubernetes"],
    "recommendations": ["Deepen Kubernetes knowledge for DevOps roles"]
  },
  "raw_analysis": "..."           // present only if JSON parse failed
}
```

---

## 3. Java Backend Integration

### 3.1 Module Structure

```
BackEnd/src/main/java/com/talentpredict/modules/ai/
├── controllers/
│   └── PredictionController.java
├── dto/
│   └── PredictionDto.java
├── entities/
│   ├── Prediction.java
│   ├── Recommendation.java
│   └── RecommendationItem.java
├── repositories/
│   ├── PredictionRepository.java
│   ├── RecommendationRepository.java
│   └── RecommendationItemRepository.java
└── services/
    ├── ProfileAnalysisOrchestrator.java  ← main coordinator
    ├── PythonAiClient.java               ← HTTP bridge to Python
    ├── CvAnalysisService.java
    ├── GithubAnalysiService.java
    ├── OpenRouterService.java
    ├── OpenAIService.java                ← legacy (PredictionService only)
    ├── PredictionService.java
    └── RecommendationService.java
```

---

### 3.2 ProfileController — API Endpoints

**File:** `BackEnd/.../modules/user/controllers/ProfileController.java`

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/profiles/users/{id}` | Fetch user profile |
| `PUT` | `/api/profiles/users/{id}` | Update profile fields |
| `POST` | `/api/profiles/accounts/{id}/upload-photo` | Upload profile photo |
| `POST` | `/api/profiles/accounts/{id}/upload-cv` | Upload PDF → parse → insert skills |
| `POST` | `/api/profiles/accounts/{id}/analyse-ia` | Trigger full async AI analysis |
| `GET` | `/api/profiles/accounts/{id}/analyse-status` | Poll analysis status |
| `GET` | `/api/profiles/test-claude` | Debug: test OpenRouter connectivity |

**CV Upload flow (`/upload-cv`):**
1. Validate PDF extension
2. Store via `FileStorageService.store(file, "cvs")` → update `profile.cvUrl`
3. `CvAnalysisService.analyserCvFile(file)` → extract skill list via Claude
4. Deduplicate vs existing skills → insert new ones
5. Return `{ message, status: "SUCCESS", skillsAjoutes, skillsDejaPresentss, totalDetectes }`

**AI analysis trigger (`/analyse-ia`):**
- Calls `ProfileAnalysisOrchestrator.analyserProfil(id)` → returns `202` immediately (async `@Async`)
- Frontend polls `/analyse-status` until `status` is `"COMPLETED"` or `"FAILED"`

---

### 3.3 ProfileAnalysisOrchestrator — 5-Step Pipeline

**File:** `BackEnd/.../modules/ai/services/ProfileAnalysisOrchestrator.java`

Runs on the `aiAnalysisExecutor` thread pool. Marks status `RUNNING` at start, `COMPLETED` or `FAILED` at end.

```
Step 1 — CV Analysis
  cvAnalysisService.analyserCvUrl(profile.getCvUrl())
  → PDFBox text extraction → OpenRouter (Claude) → skills tagged source="CV"

Step 2 — GitHub Analysis
  gitHubAnalysiService.analyserGitHubComplet(profile.getGithubUrl())
  → GitHub REST API → language counts → OpenRouter (Claude) → skills tagged source="GITHUB"
  → Saves GitHub stats (repos, followers, bio, avatar) to profile entity

Step 2b — Python AI Client
  pythonAiClient.analyzeProfile(profile)
  → POST to Python /analyze-candidate (github + linkedin_url)
  → skills tagged source="PYTHON_AI"

Step 3 — PCM Personality Test
  personalityTestService.getLatestTestByUser(id)
  → openRouterService.extraireSkillsDuPCM(lastTest.getAnalyseLlm())
  → soft skills tagged source="PCM"

Step 4 — Upsert Skills
  skillService.supprimerSkillsParUser(id)   // only if new skills were found
  upsertSkills()                             // skip exact duplicates (by normalized name)

Step 5 — AI Profile Summary
  openRouterService.executePrompt()          // 3–4 sentence French professional summary
  profileService.updateGithubStats()         // persist summary + GitHub stats
```

---

### 3.4 PythonAiClient — Bridge to Python

**File:** `BackEnd/.../modules/ai/services/PythonAiClient.java`

```java
@Value("${talentpredict.ai.base-url:}")
private String baseUrl;

public List<SkillDto.CreateRequest> analyzeProfile(ProfileDto.Response profile)
```

- Extracts GitHub username from full URL (`github.com/username` → `username`)
- `POST {baseUrl}/analyze-candidate` as multipart/form-data with `github` and `linkedin_url` fields
- Parses `skills[]` array from JSON response
- Maps Python level strings → Java integer niveau:

| Python level | Java niveau |
|---|---|
| `"Beginner"` | `1` |
| `"Intermediate"` | `2` |
| `"Advanced"` | `3` |
| `"Expert"` | `5` |

- **Graceful degradation:** returns empty list if `baseUrl` is blank or Python service is unreachable

---

### 3.5 OpenRouterService — Claude via Java

**File:** `BackEnd/.../modules/ai/services/OpenRouterService.java`

Config:
```properties
openrouter.apikey=sk-or-...
openrouter.model=anthropic/claude-sonnet-4-5
openrouter.api.url=https://openrouter.ai/api/v1/chat/completions
```

**`executePrompt(prompt)`** — core method with 3-retry, 2s × attempt exponential backoff.
Headers: `Authorization: Bearer`, `HTTP-Referer`, `X-Title: TalentPredict`. Temperature: 0.3, max_tokens: 1500.

Three specialized skill-extraction methods:

| Method | Input | Output | Purpose |
|---|---|---|---|
| `extraireSkillsDuTexteCV(texteCV)` | CV text (≤4000 chars) | Tech + Soft skills | HR expert extracting skills from resume |
| `extraireSkillsDepuisLanguages(counts)` | `Map<String,Integer>` (lang→repos) | TECH skills | Infers level from repo count: 1=1, 2–3=2, 4–6=3, 7–10=4, 11+=5 |
| `extraireSkillsDuPCM(analysePCM)` | PCM personality analysis text | SOFT skills | Derives natural soft skills from psychometric profile |

All three parse the response as `{ "skills": [{ "nom", "type", "niveau", "description" }] }`.

---

### 3.6 CvAnalysisService

**File:** `BackEnd/.../modules/ai/services/CvAnalysisService.java`

Two entry points:
- `analyserCvFile(MultipartFile file)` — uploaded directly from frontend
- `analyserCvUrl(String cvUrl)` — URL stored in profile

Uses **Apache PDFBox** (`Loader.loadPDF()` + `PDFTextStripper`). Text capped at 4000 characters.

Local file optimization: if `cvUrl` starts with `appBaseUrl + "/uploads/"`, reads directly from disk (avoids HTTP round-trip).

---

### 3.7 GithubAnalysiService

**File:** `BackEnd/.../modules/ai/services/GithubAnalysiService.java`

```java
public GitHubAnalysisResult analyserGitHubComplet(String githubUrl)
```

`GitHubAnalysisResult` fields: `skills`, `publicRepos`, `followers`, `following`, `bio`, `company`, `location`, `avatarUrl`, `name`.

Pipeline:
1. `fetchUserProfile(username, result)` — `GET https://api.github.com/users/{username}`
2. `recupererLangagesGitHub(username)` — `GET .../repos?per_page=30&sort=updated&type=public`, skip forks, count languages
3. `openRouterService.extraireSkillsDepuisLanguages(langageCounts)` — Claude infers skill levels

Optional `GITHUB_TOKEN` environment variable for auth header.
Username parsing supports: `github.com/username`, `https://github.com/username/repo`, plain `username`.

---

### 3.8 PredictionService & PredictionController

**File:** `BackEnd/.../modules/ai/services/PredictionService.java`
**File:** `BackEnd/.../modules/ai/controllers/PredictionController.java`

Generates career predictions using **OpenAI GPT-4** (legacy path):

1. Loads personality tests + skills → builds profile string
2. `openAIService.genererPrediction(profileString)` → LLM text
3. `scoreConfiance = min(1.0, skillCount × 0.1 + testCount × 0.15 + 0.4)`
4. Splits output on keyword `"Recommandations"` → soft/tech sections
5. Persists to `predictions` table

**REST endpoints (all require `ROLE_USER` or `ROLE_ADMIN`):**

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/predictions/users/{userId}/generer` | Generate new prediction |
| `GET` | `/api/predictions/users/{userId}` | All predictions (newest first) |
| `GET` | `/api/predictions/users/{userId}/derniere` | Latest prediction only |

---

### 3.9 Async Configuration

**File:** `BackEnd/.../shared/config/AsyncConfig.java`

```java
// General task pool
taskExecutor:     corePool=5,  maxPool=10, queueCapacity=100, prefix="TalentPredict-"

// Dedicated AI analysis pool
aiAnalysisExecutor: corePool=3, maxPool=10, queueCapacity=25, prefix="ai-analysis-"
// Does NOT wait for in-flight tasks on shutdown
```

---

### 3.10 Analysis Status Tracking

**File:** `BackEnd/.../shared/services/AnalysisStatusService.java`

```java
// Backed by ConcurrentHashMap<UUID, AnalysisStatus>
void markRunning(UUID accountId)
void markCompleted(UUID accountId, int skillsFound)
void markFailed(UUID accountId, String error)
AnalysisStatus getStatus(UUID accountId)
// Returns status string: "IDLE" | "RUNNING" | "COMPLETED" | "FAILED"
```

`AnalysisStatus` fields: `status`, `error`, `timestamp`, `skillsFound`.

---

### 3.11 Key Backend Configuration Properties

| Property | Default | Purpose |
|---|---|---|
| `talentpredict.ai.base-url` | _(empty)_ | Python AI microservice URL for `PythonAiClient` |
| `openrouter.apikey` | _(empty)_ | API key for Claude via OpenRouter |
| `openrouter.model` | `anthropic/claude-sonnet-4-5` | Model for all Java-side Claude calls |
| `openrouter.api.url` | `https://openrouter.ai/api/v1/chat/completions` | LLM API endpoint |
| `openai.api.key` | _(empty)_ | Legacy OpenAI key (PredictionService only) |
| `openai.model` | `gpt-4` | Legacy OpenAI model |
| `github.token` | _(empty)_ | GitHub PAT for higher API rate limit |
| `file.upload-dir` | `uploads` | Local file storage path |
| `app.base-url` | `http://localhost:8081` | Base URL for constructing file paths |

---

## 4. Angular Frontend Integration

### 4.1 Environment Configuration

**`FrontEnd/src/environments/environment.ts`** (development):
```typescript
export const environment = {
  production: false,
  apiUrl: 'http://localhost:8081/api',     // Java backend
  aiServiceUrl: 'http://localhost:8000',   // Python AI microservice
  openAiEnabled: true,
};
```

**`FrontEnd/src/environments/environment.prod.ts`** (production):
```typescript
export const environment = {
  production: true,
  apiUrl: 'https://api.talentpredict.com/api',
  aiServiceUrl: 'https://ai.talentpredict.com',
  openAiEnabled: true,
};
```

---

### 4.2 AiAnalysisService

**File:** `FrontEnd/src/app/modules/skills/services/ai-analysis.service.ts`

Calls the Python microservice **via the Spring Boot proxy** (to automatically attach JWT authentication headers):

```typescript
@Injectable({
  providedIn: 'root'
})
export class AiAnalysisService {
  private http = inject(HttpClient);
  // Route through Spring Boot proxy (/api/analysis/analyze-candidate)
  // so that JWT auth headers are automatically attached by the interceptor.
  private baseUrl = `${environment.apiUrl}/analysis`;

  analyzeCandidate(
    github: string,
    portfolio?: string,
    cvFile?: File,
    linkedinUrl?: string,
    linkedinContent?: string
  ): Observable<CandidateAnalysis> {
    const formData = new FormData();
    formData.append('github', github);
    if (portfolio) {
      formData.append('portfolio', portfolio);
    }
    if (cvFile) {
      formData.append('cv_file', cvFile, cvFile.name);
    }
    if (linkedinUrl) {
      formData.append('linkedin_url', linkedinUrl);
    }
    if (linkedinContent) {
      formData.append('linkedin_content', linkedinContent);
    }

    return this.http.post<CandidateAnalysis>(
      `${this.baseUrl}/analyze-candidate`,
      formData
    );
  }
}
```

---

### 4.3 CandidateAnalysis Model

**File:** `FrontEnd/src/app/core/models/candidate-analysis.model.ts`

```typescript
export interface CandidateSkill {
  name: string;
  level: 'Beginner' | 'Intermediate' | 'Advanced' | 'Expert';
  score: number;      // 0–100
  sources: string[];  // ["github", "cv", "portfolio", "linkedin"]
}

export interface JobMatch {
  profile: string;
  score: number;
  matched_skills: string[];
  missing_skills: string[];
  recommendations: string[];
}

export interface CandidateAnalysis {
  candidate: string;
  data_sources: string[];
  summary: string;
  skills: CandidateSkill[];
  experience_score: number;
  repositories_analyzed: number;
  top_languages: string[];
  linkedin_analysis?: string;
  job_match: JobMatch;
  raw_analysis?: string;  // fallback if JSON parse fails
  error?: string;
}
```

---

### 4.4 GithubAnalyzerComponent

**File:** `FrontEnd/src/app/modules/skills/components/github-analyzer/github-analyzer.component.ts`

Angular 17+ standalone component using signals. Route: `/skills/github`.

**Reactive signals:**
```typescript
githubUsername   = signal('');
portfolioUrl     = signal('');
linkedinUrl      = signal('');
linkedinContent  = signal('');
cvFile           = signal<File | null>(null);
analyzing        = signal(false);
error            = signal<string | null>(null);
analysis         = signal<CandidateAnalysis | null>(null);
analysisComplete = signal(false);
profile          = signal<ProfileResponse | null>(null);
```

**On init (`ngOnInit`):**
1. Loads user profile via `authService.getProfile()`
2. Auto-populates `githubUsername`, `linkedinUrl`, `portfolioUrl` from profile fields
3. If GitHub username is found → **auto-triggers analysis immediately**

**`analyzeGithubProfile()`:**
```typescript
aiService.analyzeCandidate(username, portfolio, cv, linkedin, linkedinPaste)
  .subscribe({
    next: result => {
      analysis.set(result);
      analysisComplete.set(true);
    },
    error: err => error.set('Vérifiez que le service AI est actif'),
  });
```

**UI helper methods:**

| Method | Purpose |
|---|---|
| `getLevelColor(level)` | Expert=#10b981, Advanced=#6366f1, Intermediate=#f59e0b, Beginner=#94a3b8 |
| `getLevelWidth(score)` | Returns `"${score}%"` for progress bars |
| `getSourceColor(source)` | github=#24292f, linkedin=#0a66c2, cv=#dc2626, portfolio=#059669 |
| `getSourceLabel(source)` | "GitHub" / "LinkedIn" / "CV" / "Portfolio" |

---

### 4.5 UserProfileComponent

**File:** `FrontEnd/src/app/modules/dashboard/components/user-profile/user-profile.component.ts`

Contains the AI analysis trigger:

```typescript
goToCompetences(): void {
  this.router.navigate(['/skills/github']);  // → GithubAnalyzerComponent
}
```

**CV upload flow:**
```typescript
onCvSelected(event)  // validates PDF extension
saveProfile()        // updateProfile() → doFileUploads()
// uploadCv(userId, file) → POST /api/profiles/accounts/{id}/upload-cv
//   → backend auto-parses CV and returns skills count
```

Editable profile fields: `titreProfessionnel`, `description`, `experienceAns`, `niveauEtudes`,
`lienLinkedin`, `githubUrl`, `portfolioUrl`.

---

### 4.6 DashboardService

**File:** `FrontEnd/src/app/modules/dashboard/services/dashboard.service.ts`

```typescript
getEmployeeDashboard(userId: string): Observable<EmployeeDashboardResponse>
// GET /api/dashboard/users/{userId}
// Response includes: nomComplet, nombreTests, nombreSkillsSoft, nombreSkillsTech,
//                   topSkills, formationsRecentes, dernierePrediction (AI prediction)
```

The `dernierePrediction` field surfaces the latest AI career prediction on the dashboard.

---

## 5. Full Data Flow Diagrams

### Flow A — Frontend → Spring Boot Proxy → Python Analysis

```
User clicks "Analyser" (or auto-trigger on route load)
         │
         ▼
GithubAnalyzerComponent.analyzeGithubProfile()
   │  POST multipart/form-data (github, portfolio, cv_file, linkedin_url, linkedin_content)
   ▼
[Java Backend :8081]  POST /api/analysis/analyze-candidate
   │  (attaches JWT headers and forwards request to the AI microservice)
   ▼
[Python :8000]  POST /analyze-candidate
   │
   ├─ Step 1: analyze_cv(cv_bytes)  ─────────────────► pdfplumber text extraction
   │
   └─ Step 2: run_agent(github, portfolio_url, cv_text, linkedin_url, linkedin_content)
              │
              ├─ Check AgentMemory(github_username) → cache hit? return immediately
              │
              └─ Agentic loop (max 10 turns):
                   LLM call → parse tool_use blocks:
                   ┌─ analyze_github(username) ──────────► GitHub REST API
                   ├─ analyze_cv(cv_text) ───────────────► pdfplumber scan
                   ├─ analyze_portfolio(url) ────────────► httpx + BeautifulSoup
                   ├─ extract_and_score_skills(...) ─────► skill_extractor + scoring_engine
                   └─ match_job_requirements(...) ───────► job_matching
                             │
                   _parse_final_response() ─── extract JSON from Claude output
                             │
                   AgentMemory.set(username, result)
         │
         ▼
[Angular] analysis signal updated → UI renders:
   • Executive summary
   • Skill matrix (name, level color, score bar, source tags)
   • Experience score
   • Top languages
   • LinkedIn analysis block
   • Job match card (score, matched/missing skills, recommendations)
```

---

### Flow B — Backend-Triggered Full Analysis

```
User clicks "Lancer l'analyse IA" in UserProfileComponent
         │
         ▼
Angular: POST /api/profiles/accounts/{id}/analyse-ia
         │
         ▼ (202 Accepted — async)
[Java :8081]  ProfileAnalysisOrchestrator.analyserProfil(id)
   runs on ai-analysis- thread pool
         │
   AnalysisStatusService.markRunning(id)
         │
   ┌─ Step 1: CvAnalysisService.analyserCvUrl(cvUrl)
   │           PDFBox → text → OpenRouter (Claude) → skills [source=CV]
   │
   ├─ Step 2: GithubAnalysiService.analyserGitHubComplet(githubUrl)
   │           GitHub API → language counts → OpenRouter (Claude) → skills [source=GITHUB]
   │           → save repos, followers, bio, avatar to profile
   │
   ├─ Step 2b: PythonAiClient.analyzeProfile(profile)
   │            POST {python_base_url}/analyze-candidate
   │            → map Python levels → Java niveaux → skills [source=PYTHON_AI]
   │
   ├─ Step 3: PersonalityTestService → PCM test text
   │           OpenRouterService.extraireSkillsDuPCM() → soft skills [source=PCM]
   │
   ├─ Step 4: skillService.supprimerSkillsParUser(id)
   │           upsertSkills() — deduplicated by normalized name
   │
   └─ Step 5: OpenRouterService.executePrompt() → French professional summary (3–4 sentences)
              profileService.updateGithubStats() → persist
         │
   AnalysisStatusService.markCompleted(id, skillsFound)
         │
         ▼
[Angular] polls GET /api/profiles/accounts/{id}/analyse-status
          { status: "COMPLETED", skillsFound: 24 }
          → navigate to profile / show success notification
```

---

## 6. Database Entities

### `predictions`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `analyse` | TEXT | Full LLM-generated career analysis |
| `recommandation_soft` | TEXT | Soft skill recommendations |
| `recommandation_tech` | TEXT | Tech skill recommendations |
| `score_confiance` | DOUBLE | 0–1 confidence score |
| `statut` | ENUM | `EN_ANALYSE`, `COMPLETEE`, `VALIDEE`, `APPLIQUEE` |
| `date_prediction` | DATETIME | |
| `user_id` | UUID | FK → users |

### `recommendations`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `titre` | VARCHAR | |
| `description` | TEXT | |
| `score` | DOUBLE | |
| `priorite` | INT | |
| `date_generation` | DATETIME | |
| `user_id` | UUID | FK → users |

### `recommendation_items`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `contenu` | TEXT | |
| `texte` | VARCHAR | |
| `priorite` | INT | |
| `recommendation_id` | UUID | FK → recommendations |

---

## 7. Deployment

### Running Locally

**Python AI service:**
```bash
cd talentpredict-ai
cp .env.example .env        # fill in API keys
pip install -r requirements.txt
uvicorn main:app --reload --port 8000
```

**Java backend:**
```bash
cd BackEnd
# Set in application.properties:
#   talentpredict.ai.base-url=http://localhost:8000
#   openrouter.apikey=sk-or-...
mvn spring-boot:run
```

**Angular frontend:**
```bash
cd FrontEnd
npm install
ng serve          # http://localhost:4200
```

### Docker (AI service only)

```bash
cd talentpredict-ai
docker build -t talentpredict-ai .
docker run -p 8000:8000 \
  -e ANTHROPIC_API_KEY=sk-ant-... \
  -e ANTHROPIC_MODEL=claude-sonnet-4-20250514 \
  -e GITHUB_TOKEN=ghp_... \
  -e CORS_ORIGINS=http://localhost:4200 \
  talentpredict-ai
```

### Health Check

```bash
curl http://localhost:8000/health
# → {"status":"ok","service":"talentpredict-ai"}
```

### Port Reference

| Service | Port | Protocol |
|---|---|---|
| Angular dev server | 4200 | HTTP |
| Java Spring Boot | 8081 | HTTP |
| Python FastAPI (AI) | 8000 | HTTP |
