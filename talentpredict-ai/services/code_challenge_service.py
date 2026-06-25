"""Code challenge generation and evaluation using codellama."""

from __future__ import annotations

import logging
import uuid
from datetime import datetime, timezone
from typing import Any

from config.settings import CODE_CHALLENGE_MODEL
from services.ollama_client import call_ollama_json

logger = logging.getLogger(__name__)


def _fallback_language(skill: str) -> str:
    s = (skill or "").strip().lower()
    if "python" in s:
        return "python"
    if "typescript" in s:
        return "typescript"
    if "css" in s:
        return "css"
    if "html" in s:
        return "html"
    if "java" in s and "javascript" not in s:
        return "java"
    if "c#" in s or "csharp" in s:
        return "csharp"
    if "sql" in s:
        return "sql"
    return "javascript"


def _fallback_starter_code(language: str) -> str:
    if language == "python":
        return "def solve(items):\n    # TODO: implement\n    return []"
    if language == "java":
        return (
            "public class Solution {\n"
            "  public static int solve(int[] items) {\n"
            "    // TODO: implement\n"
            "    return 0;\n"
            "  }\n"
            "}"
        )
    if language == "css":
        return ".container {\n  /* TODO: implement responsive layout */\n  display: block;\n}"
    if language == "html":
        return "<!-- TODO: implement semantic structure -->\n<div class='form-group'>\n</div>"
    if language == "sql":
        return "-- TODO: write query\nSELECT * FROM users WHERE 1=1;"
    return "function solve(items) {\n  // TODO: implement\n  return [];\n}"


async def generate_challenge(skill: str, level: str, candidate_id: str) -> dict[str, Any]:
    # Determine target language for the prompt
    target_lang = _fallback_language(skill)
    
    prompt = f"""You are an expert coding interviewer. Create ONE coding challenge for {skill} at {level} level.
The challenge must be relevant to {skill} and use {target_lang} syntax.

Return ONLY valid JSON (no markdown):
{{
  "type": "fix_bugs|complete|refactor",
  "description": "...",
  "starter_code": "...",
  "expected_behavior": "...",
  "hints": ["...", "..."],
  "language": "{target_lang}"
}}
Rules:
- Either a broken piece of code with 1-3 bugs, OR incomplete code, OR refactor task
- starter_code must be a single string with \\n for newlines
- time_limit_seconds is always 600
"""
    try:
        data = await call_ollama_json(prompt, model=CODE_CHALLENGE_MODEL, temperature=0.4)
    except Exception as e:
        logger.exception("Code challenge generation failed: %s", e)
        data = {}
    if not isinstance(data, dict):
        data = {}
    cid = str(uuid.uuid4())
    lang = str(data.get("language", target_lang)).lower()
    fallback_description = (
        f"Implémentez un défi {skill} de niveau {level}. "
        "Gérez les cas particuliers et assurez la maintenabilité du code."
    )
    fallback_expected = (
        f"La solution doit suivre les bonnes pratiques pour {skill} et atteindre le résultat attendu."
    )
    fallback_hints = [
        "Concentrez-vous sur la structure et la performance.",
        "Pensez à la façon dont ce code sera maintenu à l'avenir.",
    ]
    return {
        "challenge_id": cid,
        "skill": skill,
        "type": data.get("type", "fix_bugs"),
        "description": str(data.get("description", fallback_description)),
        "starter_code": str(data.get("starter_code", _fallback_starter_code(lang))),
        "expected_behavior": str(data.get("expected_behavior", fallback_expected)),
        "hints": list(data.get("hints") or fallback_hints)[:5],
        "time_limit_seconds": 600,
        "language": lang,
    }


_EVAL_PROMPT = """You are a rigorous senior code reviewer and technical assessor.
Evaluate the submitted solution for the coding challenge below.
Be highly critical: if the code has logical flaws, missing edge cases, or poor performance, deduct points accordingly.
If the submitted code is empty, irrelevant, or just comments, the score MUST be 0.

Challenge Description:
{description}

Expected Behavior:
{expected_behavior}

Submitted Candidate Code:
---
{submitted_code}
---

Provide a REAL evaluation. Do not be overly lenient.
Return ONLY valid JSON (no markdown) with this EXACT schema:
{{
  "correctness": 0,
  "code_quality": 0,
  "efficiency": 0,
  "readability": 0,
  "total": 0,
  "feedback": "A detailed technical review of the logic.",
  "issues_found": ["list of specific bugs or missed edge cases"],
  "strengths": ["list of what was done well"]
}}
"""


async def evaluate_submission(
    challenge_id: str,
    skill: str,
    submitted_code: str,
    description: str,
    expected_behavior: str,
    hints_used: int,
    time_spent_seconds: int,
) -> dict[str, Any]:
    prompt = _EVAL_PROMPT.format(
        description=description,
        expected_behavior=expected_behavior,
        submitted_code=submitted_code[:12000],
    )
    try:
        data = await call_ollama_json(prompt, model=CODE_CHALLENGE_MODEL, temperature=0.2)
    except Exception as e:
        logger.exception("Code evaluation failed: %s", e)
        data = {}
    
    if not isinstance(data, dict) or not data:
        # Give a detailed error fallback instead of 0 score with no feedback
        penalty = min(40, int(hints_used) * 10)
        return {
            "challenge_id": challenge_id,
            "skill": skill,
            "score": max(0, 50 - penalty),
            "breakdown": {
                "correctness": 20,
                "code_quality": 15,
                "efficiency": 10,
                "readability": 5,
                "hints_penalty": penalty,
            },
            "feedback": "Une erreur technique s'est produite lors de l'analyse IA de votre code (Modèle indisponible ou erreur de format).",
            "issues_found": ["L'évaluation automatique a échoué. Le code a été enregistré pour une vérification manuelle."],
            "strengths": ["Soumission reçue."],
            "time_spent_seconds": time_spent_seconds,
            "evaluated_at": datetime.now(timezone.utc).isoformat(),
        }

    total = int(data.get("total", 0))
    penalty = min(40, int(hints_used) * 10)
    total = max(0, total - penalty)
    return {
        "challenge_id": challenge_id,
        "skill": skill,
        "score": total,
        "breakdown": {
            "correctness": data.get("correctness", 0),
            "code_quality": data.get("code_quality", 0),
            "efficiency": data.get("efficiency", 0),
            "readability": data.get("readability", 0),
            "hints_penalty": penalty,
        },
        "feedback": data.get("feedback", ""),
        "issues_found": list(data.get("issues_found") or []),
        "strengths": list(data.get("strengths") or []),
        "time_spent_seconds": time_spent_seconds,
        "evaluated_at": datetime.now(timezone.utc).isoformat(),
    }
