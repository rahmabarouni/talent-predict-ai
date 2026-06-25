"""Career AI routes: interview simulation and learning plan generation."""

from __future__ import annotations

import asyncio
import io
import json
import logging
import math
import re

from datetime import date, datetime, timedelta, timezone
from typing import Any
from urllib.parse import quote_plus

import httpx
from fastapi import APIRouter
from pydantic import BaseModel, Field

from services.ollama_client import call_ollama_json

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/career", tags=["career"])


class CareerPredictionBody(BaseModel):
    candidate_id: str | None = None
    full_name: str | None = "Candidate"
    skills: list[Any] | None = Field(default_factory=list)
    test_results: list[Any] | None = Field(default_factory=list)
    target_role: str | None = ""
    language: str | None = "en"



@router.post("/prediction")
async def generate_career_prediction(body: CareerPredictionBody) -> dict[str, Any]:
    """Generate a rich AI career prediction and recommendations."""
    
    # We ask for a structured response that the Java backend can parse via markers.
    prompt = f"""You are an elite career coach and talent analyst.
Analyze the following candidate profile and provide a professional career prediction.
Return the result as valid JSON ONLY.

SCHEMA:
{{
  "personality_type": "One word personality category (e.g. Analyseur, Empathique, etc.)",
  "analysis_summary": "A detailed 2-3 paragraph analysis of the profile.",
  "soft_skills_scores": {{
     "Skill Name": 8.5,
     "Another Skill": 7.0
  }},
  "recommendations_soft": "3-4 specific soft skills to develop.",
  "recommendations_tech": "3-4 specific technical skills or tools to learn.",
  "confidence_score": 0.0 to 1.0
}}

CANDIDATE: {body.full_name or 'Candidate'} (ID: {body.candidate_id or 'unknown'})
SKILLS: {body.skills}
TEST RESULTS: {body.test_results}
TARGET ROLE: {body.target_role or 'Strategic growth'}
LANGUAGE: {body.language or 'en'}
"""

    try:
        data = await call_ollama_json(prompt, temperature=0.5)
        if isinstance(data, dict):
            # Format the 'analysis' field with markers so Java extractSection() works
            p_type = data.get("personality_type", "Analyseur")
            summary = data.get("analysis_summary", "Profil prometteur avec un fort potentiel.")
            scores_map = data.get("soft_skills_scores", {})
            
            scores_text = ""
            if isinstance(scores_map, dict):
                for k, v in scores_map.items():
                    scores_text += f"- {k}: {v}/10\n"

            formatted_analysis = (
                f"TYPE_PERSONNALITE:\n{p_type}\n\n"
                f"ANALYSE:\n{summary}\n\n"
                f"SCORES_SOFT_SKILLS:\n{scores_text}\n"
            )

            return {
                "analysis": formatted_analysis,
                "recommendations_soft": str(data.get("recommendations_soft", "Développer le leadership.")),
                "recommendations_tech": str(data.get("recommendations_tech", "Se perfectionner sur les frameworks modernes.")),
                "confidence_score": float(data.get("confidence_score", 0.85))
            }
    except Exception as e:
        logger.error(f"Career prediction failed: {e}")

    # Fallback with markers for Java
    fallback_scores = "- Leadership: 8/10\n- Adaptabilité: 7.5/10\n- Communication: 8/10\n"
    fallback_analysis = (
        "TYPE_PERSONNALITE:\nAnalyseur\n\n"
        "ANALYSE:\nBasé sur votre profil, vous démontrez une excellente capacité d'analyse et une approche structurée des problèmes.\n\n"
        f"SCORES_SOFT_SKILLS:\n{fallback_scores}\n"
    )

    return {
        "analysis": fallback_analysis,
        "recommendations_soft": "Leadership, Communication interculturelle, Gestion du stress.",
        "recommendations_tech": "Architectures Cloud, CI/CD, GraphQL.",
        "confidence_score": 0.65
    }


# ---------------------------------------------------------------------------
# Learning Plan — request model
# ---------------------------------------------------------------------------


class WeakSkill(BaseModel):
    name: str
    score: float = Field(ge=0, le=100, description="Current proficiency 0-100")


class LearningPlanBody(BaseModel):
    candidate_id: str
    target_role: str = "Software Engineer"
    experience_level: str = "intermediate"   # beginner | intermediate | advanced | expert
    weak_skills: list[WeakSkill] = Field(default_factory=list)
    language: str = "en"                     # en | fr | ar
    timezone: str = "UTC"
    preferred_learning_style: str = "video"  # video | reading | hands-on


# ---------------------------------------------------------------------------
# Helpers — deterministic fallback
# ---------------------------------------------------------------------------

_PLATFORM_SEARCH_URLS: dict[str, str] = {
    "Udemy": "https://www.udemy.com/courses/search/?q={q}",
    "Coursera": "https://www.coursera.org/search?query={q}",
    "edX": "https://www.edx.org/search?q={q}",
    "freeCodeCamp": "https://www.freecodecamp.org/news/search/?query={q}",
}

_OFFICIAL_DOCS: dict[str, str] = {
    "react": "https://react.dev/learn",
    "angular": "https://angular.dev/overview",
    "vue": "https://vuejs.org/guide/introduction",
    "typescript": "https://www.typescriptlang.org/docs/",
    "javascript": "https://developer.mozilla.org/en-US/docs/Web/JavaScript/Guide",
    "python": "https://docs.python.org/3/tutorial/",
    "java": "https://docs.oracle.com/en/java/javase/21/docs/api/",
    "spring": "https://spring.io/guides",
    "docker": "https://docs.docker.com/get-started/",
    "kubernetes": "https://kubernetes.io/docs/tutorials/",
    "git": "https://git-scm.com/doc",
    "sql": "https://www.w3schools.com/sql/",
    "postgresql": "https://www.postgresql.org/docs/current/tutorial.html",
    "mongodb": "https://www.mongodb.com/docs/manual/tutorial/",
    "fastapi": "https://fastapi.tiangolo.com/tutorial/",
    "django": "https://docs.djangoproject.com/en/stable/intro/tutorial01/",
    "node": "https://nodejs.org/en/docs/guides/",
    "aws": "https://docs.aws.amazon.com/",
    "ci/cd": "https://docs.github.com/en/actions",
    "testing": "https://jestjs.io/docs/getting-started",
}

_PRIORITY_COMMUNITIES: dict[str, dict] = {
    "react": {"name": "Reactiflux Discord", "url": "https://www.reactiflux.com/"},
    "python": {"name": "Python Discord", "url": "https://pythondiscord.com/"},
    "angular": {"name": "Angular Community", "url": "https://discord.com/invite/angular"},
    "default": {"name": "Dev.to Community", "url": "https://dev.to/"},
}


def _platform_search_url(platform: str, skill: str) -> str:
    template = _PLATFORM_SEARCH_URLS.get(platform, "https://www.google.com/search?q={q}")
    return template.format(q=quote_plus(skill))


def _skill_level_from_score(score: float) -> str:
    if score < 30:
        return "beginner"
    if score < 60:
        return "intermediate"
    return "advanced"


def _priority_from_score(score: float) -> str:
    if score < 30:
        return "high"
    if score < 60:
        return "medium"
    return "low"


def _required_level(score: float) -> int:
    """Estimate required role proficiency (1–10) from current score."""
    return max(7, min(10, round(10 - score / 20)))


def _gap(score: float) -> int:
    required = _required_level(score)
    current = round(score / 10)
    return max(0, required - current)


def _community_for_skill(skill_name: str) -> dict[str, str]:
    key = skill_name.strip().lower()
    for k, v in _PRIORITY_COMMUNITIES.items():
        if k in key:
            return v
    return _PRIORITY_COMMUNITIES["default"]


def _normalize_level(level: str) -> str:
    level = (level or "intermediate").strip().lower()
    valid = {"beginner", "intermediate", "advanced", "expert"}
    return level if level in valid else "intermediate"


def _build_learning_plan_fallback(body: LearningPlanBody) -> dict[str, Any]:
    """Deterministic fallback when the LLM is unavailable."""
    from datetime import date, timedelta

    today = date.today()
    checkpoint = (today + timedelta(days=14)).isoformat()

    # Sort weakest skills first
    sorted_skills = sorted(body.weak_skills, key=lambda s: s.score)
    skill_names = [s.name for s in sorted_skills]
    role = body.target_role.strip() or "Software Engineer"

    # Skill-gap analysis
    gap_rows: list[dict[str, Any]] = []
    critical_blockers: list[str] = []
    for s in sorted_skills:
        req = _required_level(s.score)
        cur = round(s.score / 10)
        g = max(0, req - cur)
        pri = "critical" if g >= 5 else ("high" if g >= 3 else ("medium" if g >= 1 else "low"))
        if pri == "critical":
            critical_blockers.append(s.name)
        gap_rows.append({
            "skill": s.name,
            "required_level": req,
            "current_level": cur,
            "gap": g,
            "priority": pri,
        })
    avg_score = (sum(s.score for s in sorted_skills) / len(sorted_skills)) if sorted_skills else 50
    readiness_score = max(5, min(90, round(avg_score * 0.85)))

    # Detailed phases (up to 5)
    phases: list[dict[str, Any]] = []
    num_phases = min(5, len(sorted_skills)) if sorted_skills else 3
    phase_size = math.ceil(len(sorted_skills) / num_phases) if sorted_skills else 1

    for i in range(num_phases):
        p_skills = sorted_skills[i * phase_size : (i + 1) * phase_size]
        if not p_skills and i > 0:
            break
        
        p_names = [s.name for s in p_skills] if p_skills else ["Foundations"]
        phases.append({
            "phase": i + 1,
            "title": f"Phase {i + 1}: {' & '.join(p_names[:2])}",
            "duration_weeks": 2,
            "focus_skills": p_names,
            "goals": [f"Gain deep understanding of {name}" for name in p_names],
            "success_criteria": [f"Can build a small functional module using {name}" for name in p_names],
        })

    # Formations
    formations: list[dict[str, Any]] = []
    for s in sorted_skills:
        lvl = _skill_level_from_score(s.score)
        pri = _priority_from_score(s.score)
        skill_key = s.name.strip().lower()
        doc_url = next((v for k, v in _OFFICIAL_DOCS.items() if k in skill_key), None)

        courses: list[dict[str, Any]] = [
            {
                "title": f"Complete {s.name} Bootcamp",
                "platform": "Udemy",
                "url": _platform_search_url("Udemy", s.name),
                "provider": "Udemy",
                "duration": "20-30 hours",
                "level": lvl,
                "reason": f"Comprehensive course matching your {lvl} level to close the gap quickly.",
            },
            {
                "title": f"{s.name} for Developers",
                "platform": "Coursera",
                "url": _platform_search_url("Coursera", s.name),
                "provider": "Coursera",
                "duration": "8-12 hours",
                "level": lvl,
                "reason": f"Structured curriculum with projects to build {s.name} confidence.",
            },
        ]
        if doc_url:
            courses.append({
                "title": f"Official {s.name} Documentation & Tutorial",
                "platform": "Official Docs",
                "url": doc_url,
                "provider": s.name,
                "duration": "Self-paced",
                "level": lvl,
                "reason": "Primary source — always up-to-date and directly reflects real-world API usage.",
            })

        formations.append({"skill": s.name, "priority": pri, "courses": courses})

    # Reinforcement
    reinforcement: list[dict[str, Any]] = []
    for i, s in enumerate(sorted_skills[:4]):
        diff = "easy" if s.score < 30 else ("medium" if s.score < 60 else "hard")
        reinforcement.append({
            "type": "project" if i % 2 == 0 else "practice",
            "title": f"Build a mini-{s.name} demo app",
            "skills": [s.name],
            "difficulty": diff,
            "duration_hours": 4 if diff == "easy" else (8 if diff == "medium" else 16),
            "description": (
                f"Create a minimal working application that exercises core {s.name} concepts. "
                f"Focus on real patterns used in {role} job descriptions."
            ),
            "success_metric": f"App runs, code is on GitHub, README explains what you built.",
        })

    # Capstone project
    top_skills = skill_names[:5] or ["problem-solving"]
    project_plan: dict[str, Any] = {
        "title": f"{role} Showcase Project",
        "description": (
            f"A full end-to-end project that demonstrates your ability to work as a {role}. "
            f"Combines your weakest skills ({', '.join(top_skills[:3])}) into one cohesive deliverable "
            "that can be pinned on your GitHub profile."
        ),
        "features": [
            f"Feature using {top_skills[0]}",
            "RESTful API or UI layer",
            "Basic tests and CI/CD pipeline",
            "Deployed to a free cloud platform (Render / Vercel / Railway)",
        ],
        "tech_stack": top_skills,
        "steps": [
            {"step": 1, "title": "Project setup & repo", "description": "Init repo, README, folder structure.", "estimated_days": 1},
            {"step": 2, "title": f"Core {top_skills[0]} implementation", "description": f"Build the main feature using {top_skills[0]}.", "estimated_days": 5},
            {"step": 3, "title": "Integration layer", "description": "Connect all components, add basic error handling.", "estimated_days": 3},
            {"step": 4, "title": "Testing & documentation", "description": "Write unit tests, update README with screenshots.", "estimated_days": 2},
            {"step": 5, "title": "Deployment", "description": "Deploy to free hosting, add live URL to GitHub.", "estimated_days": 1},
        ],
        "portfolio_outcome": (
            f"Proves to a reviewer that you can independently build and ship a {role} project "
            f"using {', '.join(top_skills[:3])}."
        ),
    }

    # Daily plan (14 days)
    daily_plan: list[dict[str, Any]] = []
    for day in range(1, 15):
        skill_for_day = sorted_skills[(day - 1) % max(1, len(sorted_skills))]
        phase_for_day = (day - 1) // 5 + 1
        daily_plan.append({
            "day": day,
            "phase": phase_for_day,
            "theme": f"{skill_for_day.name} — Day {((day - 1) % 5) + 1}",
            "tasks": [
                f"Watch/read 1 learning unit on {skill_for_day.name}",
                "Take notes on 3 key concepts",
                "Complete 1 hands-on exercise",
            ],
            "resources": [_platform_search_url("Udemy", skill_for_day.name)],
            "estimated_time": "2-3 hours",
            "deliverable": f"Completed exercise pushed to GitHub or saved locally.",
        })

    # Milestones
    milestones: list[dict[str, Any]] = [
        {
            "id": "m1",
            "title": f"{sorted_skills[0].name if sorted_skills else 'Foundation'} Fundamentals Unlocked",
            "trigger": "Complete Phase 1 and first reinforcement project",
            "badge": "foundations-complete",
            "phase": 1,
            "reward_message": "You've closed the most critical skill gap. Phase 2 will build on this.",
        },
        {
            "id": "m2",
            "title": "Mid-Plan Checkpoint Reached",
            "trigger": "Complete Phase 2 and pass the mid-plan assessment",
            "badge": "halfway-there",
            "phase": 2,
            "reward_message": "Core proficiency achieved. You're now hireable for junior roles targeting this stack.",
        },
        {
            "id": "m3",
            "title": "Portfolio-Ready",
            "trigger": "Deploy the capstone project with a live URL",
            "badge": "portfolio-ready",
            "phase": 3,
            "reward_message": f"Your GitHub now demonstrates a working {role} project. Start applying.",
        },
    ]

    # Assessments
    assessments: list[dict[str, Any]] = [
        {
            "skill": s.name,
            "phase": 1,
            "type": "code-challenge",
            "passing_score": 70,
            "description": f"Short challenge covering {s.name} fundamentals — 5-10 exercises.",
            "resource_url": _platform_search_url("freeCodeCamp", s.name),
        }
        for s in sorted_skills[:6]
    ]

    # Re-evaluation
    re_evaluation: dict[str, Any] = {
        "trigger_after_days": 14,
        "quiz_score_threshold": 60,
        "re_evaluate_skills": skill_names[:3],
        "next_checkpoint_date": checkpoint,
    }

    # Mentor profile
    community = _community_for_skill(skill_names[0] if skill_names else "")
    mentor_profile: dict[str, Any] = {
        "ideal_mentor_skills": skill_names[:3],
        "mentor_type": "senior-dev",
        "recommended_communities": [
            {**community, "why": "Active community with daily Q&A and code reviews."},
            {"name": "LinkedIn Learning Groups", "url": "https://www.linkedin.com/learning/", "why": "Professional network for role-specific peer learning."},
        ],
    }

    # Market alignment
    market_alignment: dict[str, Any] = {
        "top_hiring_companies": ["Startups", "Mid-size tech firms", "Consulting agencies"],
        "avg_salary_range": "$60k - $100k (varies by region and seniority)",
        "most_requested_skills_in_job_posts": skill_names[:5],
        "job_search_keywords": [role, f"Junior {role}", f"{role} Developer"],
        "time_to_first_interview_estimate": f"~8 weeks after completing this plan",
    }

    # Weekly checkins
    weekly_checkins: list[dict[str, Any]] = [
        {
            "week": w,
            "focus_skill": sorted_skills[(w - 1) % max(1, len(sorted_skills))].name if sorted_skills else role,
            "questions": [
                "What concept did you struggle with most this week?",
                "Did you complete your daily plan? If not, why?",
                "What's one thing you could teach someone else right now?",
                "Did you push any code to GitHub this week?",
            ],
        }
        for w in range(1, 4)
    ]

    # Summary
    main_gaps = [s.name for s in sorted_skills[:3]]
    profile_eval = (
        f"Candidate targeting {role} at {body.experience_level} level. "
        f"Main skill gaps are: {', '.join(main_gaps)}. "
        f"Estimated readiness score: {readiness_score}/100 — focused, structured study can close these gaps."
    )

    return {
        "generated_at": today.isoformat(),
        "candidate_id": body.candidate_id,
        "target_role": role,
        "language": body.language,
        "summary": {
            "profile_evaluation": profile_eval,
            "main_gaps": main_gaps,
            "estimated_time_to_ready": "8 weeks",
        },
        "skill_gap_analysis": {
            "target_role_requirements": gap_rows,
            "readiness_score": readiness_score,
            "critical_blockers": critical_blockers,
        },
        "roadmap": phases,
        "formations": formations,
        "reinforcement": reinforcement,
        "project_plan": project_plan,
        "daily_plan": daily_plan,
        "milestones": milestones,
        "assessments": assessments,
        "re_evaluation": re_evaluation,
        "mentor_profile": mentor_profile,
        "market_alignment": market_alignment,
        "weekly_checkins": weekly_checkins,
        "progress": {
            "started_at": today.isoformat(),
            "current_phase": 1,
            "completed_courses": [],
            "completed_projects": [],
            "overall_completion_pct": 0,
        },
        "localization": {
            "language": body.language,
            "timezone": body.timezone,
            "preferred_learning_style": body.preferred_learning_style,
        },
    }


# ---------------------------------------------------------------------------
# Learning Plan — endpoint
# ---------------------------------------------------------------------------

@router.post("/learning-plan")
async def generate_learning_plan(body: LearningPlanBody) -> dict[str, Any]:
    """Generate a personalized learning plan from a candidate's weak skills.

    Uses the local LLM (Ollama) to produce a rich, structured plan.
    Falls back to a deterministic plan when the model is unavailable.
    """
    if not body.weak_skills:
        return {
            "error": "weak_skills list is empty — provide at least one skill with its score.",
            "candidate_id": body.candidate_id,
        }

    from datetime import date as _date

    today_str = _date.today().isoformat()
    level = _normalize_level(body.experience_level)

    # Sort weakest first so the LLM focuses on the highest-priority gaps
    sorted_weak = sorted(body.weak_skills, key=lambda s: s.score)
    skills_json = [{"name": s.name, "score": s.score} for s in sorted_weak]

    prompt = f"""You are an elite AI learning coach integrated into TalentPredict.
Generate a complete personalized learning plan. Return ONLY valid JSON — no markdown, no prose.

## Candidate Profile
candidateId: {body.candidate_id}
targetRole: {body.target_role}
experienceLevel: {level}
language: {body.language}
timezone: {body.timezone}
preferredLearningStyle: {body.preferred_learning_style}
todayDate: {today_str}

## Weak Skills (sorted by priority — lowest score first)
{skills_json}

## STRICT URL RULES
- DO NOT invent URLs
- Udemy search:       https://www.udemy.com/courses/search/?q=SKILL
- Coursera search:    https://www.coursera.org/search?query=SKILL
- edX search:        https://www.edx.org/search?q=SKILL
- freeCodeCamp:       https://www.freecodecamp.org/news/search/?query=SKILL
- When you know the official documentation URL with certainty, use it
- Otherwise use the platform search URL above

## Required JSON Schema (COPY THIS EXACTLY, fill in values)
{{
  "generated_at": "{today_str}",
  "candidate_id": "{body.candidate_id}",
  "target_role": "{body.target_role}",
  "language": "{body.language}",
  "summary": {{
    "profile_evaluation": "2-3 sentence honest assessment",
    "main_gaps": ["skill1", "skill2"],
    "estimated_time_to_ready": "X weeks",
    "time_management_strategy": "A specific paragraph on how this candidate should manage their study time based on their profile."
  }},
  "skill_gap_analysis": {{
    "target_role_requirements": [
      {{"skill": "name", "required_level": 8, "current_level": 3, "gap": 5, "priority": "critical|high|medium|low"}}
    ],
    "readiness_score": 40,
    "critical_blockers": ["skill"]
  }},
  "roadmap": [
    {{
      "phase": 1,
      "title": "Phase 1 — Foundations & Time Blocking",
      "duration_weeks": 3,
      "focus_skills": ["skill1", "skill2"],
      "goals": [
        "Master the basic syntax and core concepts of {{skill1}}",
        "Establish a consistent 1.5h daily study routine"
      ],
      "time_management_tips": "Specific advice for this phase (e.g., 'Focus on deep work sessions of 45 mins')",
      "success_criteria": [
        "Can explain the lifecycle of a basic {{skill1}} application",
        "Successfully completed 5 coding challenges on {{skill1}}"
      ]
    }}
  ],
  "formations": [
    {{
      "skill": "skill name",
      "priority": "high|medium|low",
      "courses": [
        {{
          "title": "course title",
          "platform": "Udemy|Coursera|edX|freeCodeCamp|Official Docs",
          "url": "real URL",
          "provider": "instructor or org",
          "duration": "12 hours",
          "level": "beginner|intermediate|advanced",
          "reason": "why this course for this candidate"
        }}
      ]
    }}
  ],
  "reinforcement": [
    {{
      "type": "project|practice|challenge",
      "title": "project title",
      "skills": ["skill"],
      "difficulty": "easy|medium|hard",
      "duration_hours": 4,
      "description": "specific actionable description",
      "success_metric": "how to know it is done well"
    }}
  ],
  "project_plan": {{
    "title": "Capstone project title",
    "description": "what and why",
    "features": ["feature"],
    "tech_stack": ["tech"],
    "steps": [
      {{"step": 1, "title": "step title", "description": "what to do", "estimated_days": 2}}
    ],
    "portfolio_outcome": "what this proves to a reviewer"
  }},
  "daily_plan": [
    {{
      "day": 1,
      "phase": 1,
      "theme": "what today is about",
      "tasks": ["specific actionable task"],
      "resources": ["course URL"],
      "estimated_time": "2-3 hours",
      "deliverable": "what you produce today"
    }}
  ],
  "milestones": [
    {{
      "id": "m1",
      "title": "milestone title",
      "trigger": "what must be completed",
      "badge": "badge-slug",
      "phase": 1,
      "reward_message": "what this unlocks"
    }}
  ],
  "assessments": [
    {{
      "skill": "skill name",
      "phase": 1,
      "type": "quiz|code-challenge|project-review",
      "passing_score": 70,
      "description": "what the assessment covers",
      "resource_url": "URL"
    }}
  ],
  "re_evaluation": {{
    "trigger_after_days": 14,
    "quiz_score_threshold": 60,
    "re_evaluate_skills": ["skill"],
    "next_checkpoint_date": "YYYY-MM-DD"
  }},
  "mentor_profile": {{
    "ideal_mentor_skills": ["skill"],
    "mentor_type": "senior-dev|bootcamp-grad|career-changer",
    "recommended_communities": [
      {{"name": "community name", "url": "real URL", "why": "why this fits"}}
    ]
  }},
  "market_alignment": {{
    "top_hiring_companies": ["company"],
    "avg_salary_range": "$70k - $110k",
    "most_requested_skills_in_job_posts": ["skill"],
    "job_search_keywords": ["keyword"],
    "time_to_first_interview_estimate": "3 months"
  }},
  "weekly_checkins": [
    {{
      "week": 1,
      "focus_skill": "skill",
      "questions": [
        "What concept did you struggle with most this week?",
        "Did you complete your daily plan? If not, why?",
        "What's one thing you could teach someone else right now?"
      ]
    }}
  ],
  "progress": {{
    "started_at": "{today_str}",
    "current_phase": 1,
    "completed_courses": [],
    "completed_projects": [],
    "overall_completion_pct": 0
  }},
  "localization": {{
    "language": "{body.language}",
    "timezone": "{body.timezone}",
    "preferred_learning_style": "{body.preferred_learning_style}"
  }}
}}
"""

    fallback = _build_learning_plan_fallback(body)

    try:
        data = await call_ollama_json(prompt, temperature=0.4, retry_stricter=True)
        if not isinstance(data, dict):
            logger.warning("Learning plan LLM returned non-dict — using fallback")
            return fallback

        # Validate critical top-level sections; fall back to deterministic defaults
        # for any section the model failed to produce correctly.
        def _safe_list(val: Any, min_len: int = 0) -> list:
            return val if isinstance(val, list) and len(val) >= min_len else []

        def _safe_dict(val: Any) -> dict:
            return val if isinstance(val, dict) else {}

        def _string_list(val: Any, max_len: int = 0) -> list:
            if isinstance(val, list):
                res = [str(x) for x in val]
                return res[:max_len] if max_len > 0 else res
            return []

        summary = _safe_dict(data.get("summary"))
        skill_gap = _safe_dict(data.get("skill_gap_analysis"))
        roadmap = _safe_list(data.get("roadmap"), 1)
        formations = _safe_list(data.get("formations"), 1)
        reinforcement = _safe_list(data.get("reinforcement"))
        project = _safe_dict(data.get("project_plan"))
        daily = _safe_list(data.get("daily_plan"))
        milestones = _safe_list(data.get("milestones"))
        assessments = _safe_list(data.get("assessments"))
        re_eval = _safe_dict(data.get("re_evaluation"))
        mentor = _safe_dict(data.get("mentor_profile"))
        market = _safe_dict(data.get("market_alignment"))
        checkins = _safe_list(data.get("weekly_checkins"))
        progress = _safe_dict(data.get("progress"))
        loc = _safe_dict(data.get("localization"))

        # If key sections are empty the LLM output is not usable — use fallback
        if not roadmap or not formations:
            logger.warning("Learning plan LLM output missing roadmap/formations — using fallback")
            return fallback

        return {
            "generated_at": str(data.get("generated_at", today_str)),
            "candidate_id": body.candidate_id,
            "target_role": str(data.get("target_role", body.target_role)),
            "language": body.language,
            "summary": {
                "profile_evaluation": str(summary.get("profile_evaluation", fallback["summary"]["profile_evaluation"])),
                "main_gaps": _string_list(summary.get("main_gaps"), 5) or fallback["summary"]["main_gaps"],
                "estimated_time_to_ready": str(summary.get("estimated_time_to_ready", fallback["summary"]["estimated_time_to_ready"])),
            },
            "skill_gap_analysis": {
                "target_role_requirements": _safe_list(skill_gap.get("target_role_requirements"))
                    or fallback["skill_gap_analysis"]["target_role_requirements"],
                "readiness_score": max(0, min(100, int(skill_gap.get("readiness_score", fallback["skill_gap_analysis"]["readiness_score"]) or 0))),
                "critical_blockers": _string_list(skill_gap.get("critical_blockers"), 5) or fallback["skill_gap_analysis"]["critical_blockers"],
            },
            "roadmap": roadmap,
            "formations": formations,
            "reinforcement": reinforcement or fallback["reinforcement"],
            "project_plan": project or fallback["project_plan"],
            "daily_plan": daily or fallback["daily_plan"],
            "milestones": milestones or fallback["milestones"],
            "assessments": assessments or fallback["assessments"],
            "re_evaluation": re_eval or fallback["re_evaluation"],
            "mentor_profile": mentor or fallback["mentor_profile"],
            "market_alignment": market or fallback["market_alignment"],
            "weekly_checkins": checkins or fallback["weekly_checkins"],
            "progress": progress or fallback["progress"],
            "localization": loc or fallback["localization"],
        }

    except Exception as exc:
        logger.warning("Learning plan generation failed — using fallback: %s", exc)
        return fallback
