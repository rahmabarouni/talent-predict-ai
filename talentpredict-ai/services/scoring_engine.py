"""Scoring engine service.

Maps raw skill data to proficiency levels (Beginner, Intermediate, Advanced,
Expert) and calculates an overall experience score (0–100).
"""

from __future__ import annotations

from typing import Any


# Level thresholds based on how many sources mention a skill and repo activity
def _determine_level(
    occurrence_count: int,
    source_count: int,
    github_repo_count: int | None = None,
) -> tuple[str, int]:
    """Return (level_label, numeric_score) for a skill.

    Scoring heuristic:
    - Expert (90-100): 3 sources + significant GitHub presence
    - Advanced (70-89): 2+ sources or strong single-source signal
    - Intermediate (40-69): at least 1 source with moderate signal
    - Beginner (10-39): mentioned once in a single source
    """
    if source_count >= 3:
        score = min(100, 85 + occurrence_count * 2)
        return "Expert", score
    if source_count >= 2:
        if occurrence_count >= 4:
            score = min(100, 80 + occurrence_count)
            return "Advanced", score
        score = min(89, 70 + occurrence_count * 2)
        return "Advanced", score
    # Single source
    if occurrence_count >= 3 or (github_repo_count and github_repo_count >= 5):
        score = min(69, 50 + occurrence_count * 3)
        return "Intermediate", score
    if occurrence_count >= 2:
        return "Intermediate", 45
    return "Beginner", 25


def score_skills(
    skills: list[dict[str, Any]],
    github_data: dict[str, Any] | None = None,
) -> tuple[list[dict[str, Any]], int]:
    """Assign proficiency levels and scores to each skill.

    Returns:
        (scored_skills, experience_score)
        where experience_score is a 0-100 aggregate.
    """
    language_stats = {}
    if github_data and not github_data.get("error"):
        language_stats = github_data.get("language_stats", {})

    scored: list[dict[str, Any]] = []
    total_score = 0

    for skill in skills:
        repo_count = language_stats.get(skill["name"])
        level, score = _determine_level(
            occurrence_count=skill.get("occurrence_count", 1),
            source_count=len(skill.get("sources", ["unknown"])),
            github_repo_count=repo_count,
        )
        scored.append(
            {
                "name": skill["name"],
                "level": level,
                "score": score,
                "sources": skill.get("sources", []),
            }
        )
        total_score += score

    # Experience score = weighted average, capped at 100
    experience_score = 0
    if scored:
        experience_score = min(100, round(total_score / len(scored) * 1.1))

    # Sort by score descending
    scored.sort(key=lambda s: -s["score"])
    return scored, experience_score
