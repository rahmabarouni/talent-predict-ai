---
# Feature Map: Fraud Detection

## Files
- `talentpredict-ai/services/fraud_detector.py`
- `talentpredict-ai/api/test_routes.py`
- `talentpredict-ai/api/analysis_routes.py`
- `talentpredict-ai/config/settings.py`
- `talentpredict-ai/services/test_evaluator.py`
- `BackEnd/src/main/resources/schema.sql`
- `BackEnd/src/main/java/com/talentpredict/modules/user/services/UserServiceImpl.java`
- `BackEnd/src/main/java/com/talentpredict/modules/user/repositories/ProfileRepository.java`
- `BackEnd/src/main/java/com/talentpredict/modules/user/entities/User.java`
- `BackEnd/src/main/java/com/talentpredict/modules/user/entities/Profile.java`
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/entities/FraudCase.java`
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/entities/FraudFlags.java`
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/dto/FraudCaseDto.java`
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/repositories/FraudCaseRepository.java`
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/services/FraudCaseService.java`
- `FrontEnd/src/app/modules/skill-test/services/test-api.service.ts`
- `FrontEnd/src/app/modules/skill-test/components/skill-test-quiz/skill-test-quiz.component.ts`
- `FrontEnd/src/app/modules/skill-test/components/scenario-simulator/scenario-simulator.component.ts`
- `FrontEnd/src/app/modules/skill-test/components/scenario-simulator/scenario-simulator.component.html`
- `FrontEnd/src/app/modules/skill-test/components/scenario-simulator/scenario-simulator.component.scss`
- `FrontEnd/src/app/modules/recruiter/services/recruiter-api.service.ts`

## Functions & Methods
- `talentpredict-ai/services/fraud_detector.py → collect_signals(context)` — Build structured fraud signals without LLM.
- `talentpredict-ai/services/fraud_detector.py → ollama_fraud_verdict(signals, code_snippet)` — Ask Ollama for structured fraud assessment.
- `talentpredict-ai/api/test_routes.py → _fraud_fallback(signals)` — Heuristic fallback mechanism for fraud risk scoring on timeouts.
- `talentpredict-ai/api/analysis_routes.py → _fraud_fallback(signals)` — Consolidated fallback handler.
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/services/FraudCaseService.java → recordFraudCase(candidate, actor, source, verdict)` — Creates and saves a new fraud case entity.
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/services/FraudCaseService.java → getHistory(candidateId, limit)` — Retrieves candidate fraud history.
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/services/FraudCaseService.java → reviewCase(caseId, reviewer, decision, note)` — Updates manual review status.
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/services/FraudCaseService.java → buildKpis(topK, lookbackDays)` — Compiles KPI metrics for recruiter dashboards.
- `FrontEnd/src/app/modules/skill-test/services/test-api.service.ts → checkFraud(body)` — Calls API for independent fraud evaluation.
- `FrontEnd/src/app/modules/skill-test/components/scenario-simulator/scenario-simulator.component.ts → _stopFraudMonitoring()` — Halts background biometric/fraud polling.
- `FrontEnd/src/app/modules/recruiter/services/recruiter-api.service.ts → fraudAlerts(), fraudCaseHistory(), reviewFraudCase(), fraudKpis(), fraudCalibration()` — Frontend data fetches for the Admin/Recruiter module.

## API Endpoints
- `POST /api/analysis/fraud-check` (Python) → `fraud_check(body)` — Consolidated standalone fraud endpoint.
- `GET /recruiter/fraud-alerts` (Java) → Returns a list of pending, high-risk fraud items.
- `GET /recruiter/fraud-cases/{candidateId}` (Java) → Returns historical cases for a candidate.
- `PATCH /recruiter/fraud-cases/{caseId}/review` (Java) → Endpoint to flag/clear cases.
- `GET /recruiter/fraud-kpis` (Java) → Returns global fraud reporting KPIs.
- `GET /recruiter/fraud-calibration` (Java) → Returns metric calibration suggestions.
- `POST /assessment/fraud/check` (Java) → Proxy endpoint routing requests to the AI Service.
- Included natively inside standard test evaluations: `POST /api/test/evaluate` and `POST /api/test/scenario/evaluate` (Python).

## External API Calls
- `talentpredict-ai/services/fraud_detector.py → ollama_fraud_verdict()` calls → **Ollama LLM (local AI model)**

## Models / DB Tables
- `fraud_cases` (Table): `case_id`, `candidate_id`, `risk_level`, `fraud_score`, `flags` (JSONB), `source`, `review_status`, `reviewed_by_user_id`, `created_at`.
- `users` / `profiles` (Table): Modified with `fraud_risk` (VARCHAR) and `fraud_flags` (JSONB).
- `FraudCase` / `FraudFlags` (Java Entities).
- Frontend Interfaces: `FraudVerdict`, `FraudFlag`, `FraudCaseHistoryItem`, `FraudKpiResponse`, `FraudCalibrationResponse`.

## Dependencies on Other Features
- **GitHub / CV Integration:** The `fraud-check` logic natively compares expected CV skills against GitHub parsed skills. If the CV makes claims the GitHub profile does not substantiate, it flags this anomaly as a fraud signal.

---
# Feature Map: GitHub Authenticity

## Files
- `talentpredict-ai/services/github_deep_analyzer.py`
- `talentpredict-ai/api/analysis_routes.py`
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/controllers/AnalysisProxyController.java`
- `FrontEnd/src/app/modules/skill-test/services/test-api.service.ts`
- `FrontEnd/src/app/modules/recruiter/services/recruiter-api.service.ts`
- `FrontEnd/src/app/modules/recruiter/components/recruiter-candidate-list/recruiter-candidate-list.component.ts`
- `FrontEnd/src/app/modules/recruiter/components/recruiter-candidate-list/recruiter-candidate-list.component.html`
- `FrontEnd/src/app/modules/competences/components/tech-results/tech-results.component.ts`
- `FrontEnd/src/app/modules/competences/components/tech-results/tech-results.component.html`

## Functions & Methods
- `talentpredict-ai/services/github_deep_analyzer.py → analyze_github_deep(github_username, candidate_id, github_data)` — Fetches deep repository structure, commit quality, and architecture styles.
- `talentpredict-ai/api/analysis_routes.py → github_deep(body)` — Python API handler mapping payload to analyzer service.
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/controllers/AnalysisProxyController.java → githubDeep(body)` — Proxies the GitHub Deep request payload to the AI service.
- `FrontEnd/src/app/modules/skill-test/services/test-api.service.ts → analyzeGithubDeep(body)` — Performs deep scan lookup for the authenticated user.
- `FrontEnd/src/app/modules/recruiter/services/recruiter-api.service.ts → githubDeep(body)` — Enables recruiter-triggered evaluations.
- `FrontEnd/src/app/modules/recruiter/components/recruiter-candidate-list/recruiter-candidate-list.component.ts → runGithubDeep(row, event)` — Initiates GitHub deep scan for candidate from Recruiter dashboard.
- `FrontEnd/src/app/modules/competences/components/tech-results/tech-results.component.ts → runGithubDeepAnalysis(username, aiAnalysis)` — Invokes background AI scan automatically on the tech results page.

## API Endpoints
- `POST /api/analysis/github-deep` (Python) → `github_deep()` — Runs deep analysis workflow.
- `POST /analysis/github-deep` or `POST /api/analysis/github-deep` (Java) → Secure backend proxy mapping to the Python microservice.

## External API Calls
- `talentpredict-ai/services/github_deep_analyzer.py` calls → **GitHub API** (to fetch user repositories, source code trees, commit history, and language metrics).
- `talentpredict-ai/services/github_deep_analyzer.py` calls → **Ollama LLM** (for architectural profiling and summary generation).

## Models / DB Tables
- Relies on dynamic unstructured mapping tied to `candidate_id`. Results populate the frontend `GithubDeepRequest` interface.
- Output shapes include metrics like `github_score`, `commit_quality_score`, `code_quality_proxy_score`, `project_impact_score`, `collaboration_score`, `top_projects`, and `ai_summary`.

## Dependencies on Other Features
- Outputs from this feature act as reference variables for **Fraud Detection** (e.g., verifying if the code complexity matches CV claims).

---
# Feature Map: CV Authenticity

## Files
- `talentpredict-ai/services/cv_authenticity.py`
- `talentpredict-ai/api/analysis_routes.py`
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/controllers/AnalysisProxyController.java`
- `FrontEnd/src/app/modules/skill-test/services/test-api.service.ts`
- `FrontEnd/src/app/modules/evaluation/components/test-results/test-results.component.ts`
- `FrontEnd/src/app/modules/competences/components/tech-results/tech-results.component.ts`
- `FrontEnd/src/app/modules/competences/components/tech-results/tech-results.component.html`

## Functions & Methods
- `talentpredict-ai/services/cv_authenticity.py → collect_cv_signals(cv_text)` — Extracts timeline inconsistencies and keyword densities.
- `talentpredict-ai/services/cv_authenticity.py → ollama_cv_verdict(cv_text, signals)` — Invokes LLM to check for likelihood of generative AI usage.
- `talentpredict-ai/api/analysis_routes.py → cv_authenticity(body)` — Python API handler mapping payload to CV service.
- `BackEnd/src/main/java/com/talentpredict/modules/assessment/controllers/AnalysisProxyController.java → cvAuthenticity(body)` — Java gateway routing CV check requests.
- `FrontEnd/src/app/modules/skill-test/services/test-api.service.ts → checkCvAuthenticity(body)` — HTTP Client method wrapper.
- `FrontEnd/src/app/modules/evaluation/components/test-results/test-results.component.ts → checkAutoCvAuthenticity()` / `runCvAuthenticity(cvText)` — Auto-runs CV analysis on soft skills result viewing.
- `FrontEnd/src/app/modules/competences/components/tech-results/tech-results.component.ts → checkAutoCvAuthenticity()` / `runCvAuthenticity(cvText)` — Auto-runs CV analysis on technical results viewing.

## API Endpoints
- `POST /api/analysis/cv-authenticity` (Python) → `cv_authenticity(body)` — Deep parsing execution route.
- `POST /analysis/cv-authenticity` or `POST /api/analysis/cv-authenticity` (Java) → Core backend proxy endpoint.

## External API Calls
- `talentpredict-ai/services/cv_authenticity.py` calls → **Ollama LLM** (to evaluate textual structure, prompt-injection artifacts, and calculate AI-generation likelihood).

## Models / DB Tables
- Results evaluate to transient memory tied to `candidate_id`. 
- Frontend output maps dynamically to the `cvAuthenticityResult` object featuring: `authenticity_risk`, `ai_generated_probability`, `explanation`, `remediation`, `timeline_gaps`, and `signals`.

## Dependencies on Other Features
- ⚠️ **UNCERTAIN:** Generates flags that likely aggregate into the central **Fraud Detection** table/case entity whenever high discrepancies or generation footprints are found.

---
# Cross-Feature Summary

- **Shared AI Processing (Ollama):** All three modules heavily rely on local LLM processing to turn unstructured data into structured outputs (`ollama_fraud_verdict`, GitHub architecture proxy, CV prompt tracking).
- **Shared Error Handling (`_fraud_fallback`):** The Python codebase implements a fallback heuristic utilized simultaneously by background scenarios and generic analysis routes to prevent service locking when the LLM times out.
- **Shared API Controller (`AnalysisProxyController.java`):** Deep GitHub analysis and CV Authenticity endpoints are centrally proxied through the same Java gateway controller to handle security validations and network mapping seamlessly.
- **Coupled Fraud State:** Both GitHub Deep output and CV Authenticity signals intersect in `fraud-check` workflows. Frontend components explicitly cross-reference the gap between parsed GitHub data and claimed CV data to drive visual fraud warnings (`missing_claimed_skills`).
- **Redundant State Polling (Tight Coupling):** Both `test-results.component.ts` (Soft Skills) and `tech-results.component.ts` (Tech Skills) contain duplicate logic to pull `techIntakeContext` from `sessionStorage` and manually trigger `checkAutoCvAuthenticity()`. This is an area of tight coupling bound to local browser storage rather than a state-management pattern.
- **Shared Database Root:** Everything rolls up into the `User`/`Profile` entities. The central `fraud_cases` schema aggregates any high-severity event triggered across the platform (scenarios, CV manipulation, missing repo validations).
