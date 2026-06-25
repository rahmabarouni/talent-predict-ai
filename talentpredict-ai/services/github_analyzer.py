"""GitHub repository and code analyzer for tech skills assessment."""

from __future__ import annotations

import logging
import os
from typing import Any

import httpx

from services.ollama_client import call_ollama_json

logger = logging.getLogger(__name__)

_GITHUB_API = "https://api.github.com"
_GITHUB_TOKEN = os.getenv("GITHUB_TOKEN", "")


def _auth_headers() -> dict[str, str]:
    """Return GitHub API headers with optional auth token."""
    headers = {
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }
    if _GITHUB_TOKEN:
        headers["Authorization"] = f"Bearer {_GITHUB_TOKEN}"
    return headers


async def _fetch_github_repos(username: str) -> list[dict[str, Any]]:
    """Fetch public repos for the given GitHub username."""
    url = f"{_GITHUB_API}/users/{username}/repos"
    params = {"sort": "updated", "per_page": 12, "type": "owner"}
    async with httpx.AsyncClient(timeout=15.0, headers=_auth_headers()) as client:
        response = await client.get(url, params=params)
        if response.status_code == 404:
            raise ValueError(f"Utilisateur GitHub '{username}' introuvable.")
        response.raise_for_status()
        return response.json()


def _summarize_repos(repos: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Extract key fields from GitHub repo data for LLM analysis."""
    summary = []
    for repo in repos:
        if repo.get("fork"):
            continue  # skip forks — focus on original work
        summary.append({
            "name": repo.get("name", ""),
            "language": repo.get("language") or "Unknown",
            "description": (repo.get("description") or "")[:200],
            "stargazers_count": repo.get("stargazers_count", 0),
            "size": repo.get("size", 0),
            "topics": repo.get("topics", [])[:5],
            "updated_at": (repo.get("updated_at") or "")[:10],
        })
    return summary[:10]  # limit to 10 repos for prompt size


async def analyze_github_profile(username: str, claimed_skills: list[str]) -> dict[str, Any]:
    """
    Fetches real GitHub public data and uses an LLM to verify the candidate's tech stack
    against their claimed skills.
    """
    logger.info("Analyzing GitHub profile for: %s", username)

    # Fetch real repos
    try:
        raw_repos = await _fetch_github_repos(username)
    except ValueError as e:
        return {
            "status": "error",
            "username": username,
            "message": str(e),
        }
    except Exception as e:
        logger.error("GitHub API call failed for %s: %s", username, e)
        return {
            "status": "error",
            "username": username,
            "message": f"Impossible de joindre l'API GitHub : {e}",
        }

    repo_summary = _summarize_repos(raw_repos)

    if not repo_summary:
        return {
            "status": "error",
            "username": username,
            "message": f"Aucun dépôt public original trouvé pour '{username}'.",
        }

    import json
    prompt = f"""You are a Senior Staff Software Engineer evaluating a candidate's GitHub profile.

The candidate ({username}) claims these skills: {claimed_skills}

Here are their {len(repo_summary)} most recent original public repositories:
{json.dumps(repo_summary, indent=2)}

Analyze their repositories and verify their claimed skills. Focus on architectural patterns, code quality, and consistency across projects.
Return ONLY valid JSON with this exact schema:
{{
  "verified_skills": [
    {{
      "skill": "string",
      "confidence": "low|medium|high",
      "evidence": "detailed justification",
      "depth": "beginner|intermediate|advanced"
    }}
  ],
  "missing_claimed_skills": ["skill1"],
  "additional_skills_detected": ["skill1"],
  "code_complexity_estimate": "beginner|intermediate|advanced",
  "activity_level": "low|moderate|high",
  "architectural_style": "e.g. Microservices, Monolithic, Event-driven",
  "gaps_identified": ["detailed gap 1", "detailed gap 2"],
  "summary": "Profound summary of technical maturity"
}}
"""

    try:
        analysis = await call_ollama_json(prompt, temperature=0.1, retry_stricter=True)
        return {
            "status": "success",
            "username": username,
            "repos_analyzed": len(repo_summary),
            "data": analysis,
        }
    except Exception as e:
        logger.error("LLM analysis failed for GitHub profile %s: %s", username, e)
        # Return a rule-based fallback based on detected languages
        detected_langs = list({r["language"] for r in repo_summary if r["language"] != "Unknown"})
        verified = [
            {"skill": lang, "confidence": "high", "evidence": f"Found in public repositories"}
            for lang in detected_langs
        ]
        missing = [s for s in claimed_skills if s not in detected_langs]
        return {
            "status": "partial",
            "username": username,
            "repos_analyzed": len(repo_summary),
            "data": {
                "verified_skills": verified,
                "missing_claimed_skills": missing,
                "additional_skills_detected": detected_langs,
                "code_complexity_estimate": "intermediate",
                "activity_level": "moderate",
                "summary": f"Analyse heuristique (IA indisponible) : stack technique identifiée via {len(repo_summary)} dépôts publics. Les langages {', '.join(detected_langs)} sont confirmés. Une revue manuelle est suggérée pour évaluer la complexité architecturale exacte.",
                "gaps": f"Incertitude sur les compétences : {', '.join(missing)} - non détectées dans les dépôts publics GitHub récents." if missing else "Aucune divergence majeure détectée sur les langages de base."
            },
        }
