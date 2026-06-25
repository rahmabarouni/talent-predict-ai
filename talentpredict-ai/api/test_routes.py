"""MCQ and code-challenge test API."""

from __future__ import annotations

import asyncio
import logging
from datetime import datetime, timezone
from typing import Any

from fastapi import APIRouter
from pydantic import BaseModel, Field

from services.code_challenge_service import evaluate_submission, generate_challenge

from services.github_analyzer import analyze_github_profile
from services.scenario_simulator import evaluate_scenario_response, generate_soft_skills_scenario
from services.test_evaluator import evaluate_answers, generate_result_summary
from services.test_generator import generate_test

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/test", tags=["test"])

SUMMARY_TIMEOUT_SECONDS = 45

CODE_EVAL_TIMEOUT_SECONDS = 60


def _summary_fallback(skill_scores: dict[str, int], weak_threshold: int = 60) -> str:
    weak = [k for k, v in skill_scores.items() if int(v) < weak_threshold]
    strong = [k for k, v in skill_scores.items() if int(v) >= 75]
    
    msg = ""
    if strong and weak:
        msg = f"Points forts en {', '.join(strong[:3])}; axes d'amélioration en {', '.join(weak[:3])}."
    elif strong:
        msg = f"Excellente maîtrise en {', '.join(strong[:4])}."
    elif weak:
        msg = f"Nécessite un renforcement en {', '.join(weak[:4])}."
    else:
        msg = "Test complété avec succès."
        
    return f"{msg} (Note: Synthèse générée par algorithme heuristique suite à un délai de réponse de l'IA)."


def _code_eval_timeout_fallback(
    challenge_id: str,
    skill: str,
    submitted_code: str,
    hints_used: int,
    time_spent_seconds: int,
) -> dict[str, Any]:
    penalty = min(40, int(hints_used) * 10)
    score = max(0, 50 - penalty)
    return {
        "challenge_id": challenge_id,
        "skill": skill,
        "score": score,
        "breakdown": {
            "correctness": 20,
            "code_quality": 15,
            "efficiency": 10,
            "readability": 5,
            "hints_penalty": penalty,
        },
        "feedback": "L'analyse IA a été interrompue (Délai d'attente dépassé ou surcharge du modèle).",
        "issues_found": ["Évaluation automatique indisponible momentanément. Le code est préservé pour audit manuel."],
        "strengths": ["Code soumis avec succès." if submitted_code.strip() else "Aucun code soumis."],
        "time_spent_seconds": time_spent_seconds,
        "evaluated_at": datetime.now(timezone.utc).isoformat(),
    }


class GenerateBody(BaseModel):
    skills: list[str]
    level: str = "EXPERT"
    candidate_id: str
    skill_scores: dict[str, float] | None = None
    question_count: int | None = Field(default=None, ge=4, le=20)


@router.post("/generate")
async def post_generate(body: GenerateBody) -> dict[str, Any]:
    return await generate_test(
        body.skills,
        body.level,
        body.candidate_id,
        skill_scores=body.skill_scores,
        question_count=body.question_count,
    )


class AnswerItem(BaseModel):
    question_id: str
    skill: str
    selected: str
    correct: str
    confidence: str = "medium"
    time_spent_seconds: float | None = None
    difficulty: str = "medium"


class EvaluateBody(BaseModel):
    test_id: str
    candidate_id: str
    answers: list[AnswerItem]
    skill_weights: dict[str, float] | None = None



@router.post("/evaluate")
async def post_evaluate(body: EvaluateBody) -> dict[str, Any]:
    raw = [a.model_dump() for a in body.answers]
    result = evaluate_answers(raw, skill_weights=body.skill_weights)
    try:
        summary = await asyncio.wait_for(
            generate_result_summary(result["skill_scores"]),
            timeout=SUMMARY_TIMEOUT_SECONDS,
        )
    except asyncio.TimeoutError:
        logger.warning("Result summary timed out, using fallback summary")
        summary = _summary_fallback(result["skill_scores"])
    result["summary"] = summary

    return result


class CodeGenBody(BaseModel):
    skill: str
    level: str = "EXPERT"
    candidate_id: str


@router.post("/code-challenge/generate")
async def code_challenge_generate(body: CodeGenBody) -> dict[str, Any]:
    return await generate_challenge(body.skill, body.level, body.candidate_id)


class CodeEvalBody(BaseModel):
    challenge_id: str
    skill: str
    submitted_code: str
    hints_used: int = 0
    time_spent_seconds: int = 0
    description: str = ""
    expected_behavior: str = ""


@router.post("/code-challenge/evaluate")
async def code_challenge_evaluate(body: CodeEvalBody) -> dict[str, Any]:
    try:
        return await asyncio.wait_for(
            evaluate_submission(
                body.challenge_id,
                body.skill,
                body.submitted_code,
                body.description or "Coding challenge",
                body.expected_behavior or "See prompt",
                body.hints_used,
                body.time_spent_seconds,
            ),
            timeout=CODE_EVAL_TIMEOUT_SECONDS,
        )
    except asyncio.TimeoutError:
        logger.warning("Code challenge evaluation timed out, using fallback score")
        return _code_eval_timeout_fallback(
            challenge_id=body.challenge_id,
            skill=body.skill,
            submitted_code=body.submitted_code,
            hints_used=body.hints_used,
            time_spent_seconds=body.time_spent_seconds,
        )


class GithubAnalyzeBody(BaseModel):
    username: str
    claimed_skills: list[str]


@router.post("/github/analyze")
async def github_analyze(body: GithubAnalyzeBody) -> dict[str, Any]:
    try:
        return await asyncio.wait_for(
            analyze_github_profile(body.username, body.claimed_skills),
            timeout=30.0,
        )
    except asyncio.TimeoutError:
        logger.warning("GitHub analysis timed out for user: %s", body.username)
        return {
            "status": "error",
            "username": body.username,
            "message": "Analysis timed out. GitHub API or LLM may be slow. Please retry.",
        }


class ScenarioGenerateBody(BaseModel):
    role: str
    level: str = "Mid-Level"


@router.post("/scenario/generate")
async def scenario_generate(body: ScenarioGenerateBody) -> dict[str, Any]:
    return await generate_soft_skills_scenario(body.role, body.level)


class ScenarioEvaluateBody(BaseModel):
    scenario: str
    response: str



@router.post("/scenario/evaluate")
async def scenario_evaluate(body: ScenarioEvaluateBody) -> dict[str, Any]:
    result = await evaluate_scenario_response(body.scenario, body.response)

    return result

