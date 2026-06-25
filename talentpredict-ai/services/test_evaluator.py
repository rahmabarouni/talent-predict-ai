import statistics
import logging
from typing import Any

from services.ollama_client import call_ollama

logger = logging.getLogger(__name__)


def _confidence_mult(correct: bool, confidence: str) -> float:
    c = (confidence or "medium").lower()
    if correct:
        if c == "high":
            return 1.2
        if c == "low":
            return 0.9
        return 1.0
    if c == "high":
        return -0.1
    return 0.0


def _time_adjustment(difficulty: str, time_spent: float | None, correct: bool) -> float:
    """Slight bonus/penalty on hard questions."""
    if time_spent is None or difficulty != "hard" or not correct:
        return 0.0
    if time_spent < 15:
        return 0.05
    if time_spent > 80:
        return -0.05
    return 0.0


def _per_question_weight(
    correct: bool,
    confidence: str,
    difficulty: str,
    time_spent: float | None,
) -> float:
    base = 1.0 if correct else 0.0
    cm = _confidence_mult(correct, confidence)
    if correct:
        w = base * cm
    else:
        w = base + cm
    w += _time_adjustment(difficulty, time_spent, correct)
    return max(w, 0.0)


def evaluate_answers(
    answers: list[dict[str, Any]],
    skill_weights: dict[str, float] | None = None,
) -> dict[str, Any]:
    """Compute real_score, per-skill scores, confidence_accuracy, speed_score."""
    by_skill: dict[str, list[dict[str, Any]]] = {}
    for a in answers:
        sk = str(a.get("skill", "unknown"))
        by_skill.setdefault(sk, []).append(a)

    skill_scores: dict[str, float] = {}
    conf_hits = 0
    conf_total = 0
    speed_samples: list[float] = []

    for skill, items in by_skill.items():
        weights: list[float] = []
        for a in items:
            sel = str(a.get("selected", "")).upper().strip()
            corr = str(a.get("correct", "")).upper().strip()
            correct = sel == corr and sel in ("A", "B", "C", "D")
            conf = str(a.get("confidence", "medium"))
            diff = str(a.get("difficulty", "medium")).lower()
            if diff not in ("easy", "medium", "hard"):
                diff = "medium"
            ts = a.get("time_spent_seconds")
            try:
                tval = float(ts) if ts is not None else None
            except (TypeError, ValueError):
                tval = None
            if tval is not None:
                speed_samples.append(min(120.0, max(0.0, tval)))
            wq = _per_question_weight(correct, conf, diff, tval)
            weights.append(wq)
            if conf in ("high", "low"):
                conf_total += 1
                if (conf == "high" and correct) or (conf == "low" and not correct):
                    conf_hits += 1

        max_w = len(items) * 1.2 + 0.05 * len(items)
        raw = sum(weights)
        skill_scores[skill] = round((raw / max_w * 100) if max_w > 0 else 0.0, 1)

    weights_map = skill_weights or {}
    total_w = 0.0
    acc = 0.0
    for sk, sc in skill_scores.items():
        w = float(weights_map.get(sk, weights_map.get(sk.lower(), 1.0)))
        total_w += w
        acc += sc * w
    real_score = round(acc / total_w, 1) if total_w > 0 else 0.0

    confidence_accuracy = (
        round(conf_hits / conf_total * 100, 1) if conf_total > 0 else 100.0
    )
    if speed_samples:
        avg = statistics.mean(speed_samples)
        speed_score = round(max(0.0, 100.0 - avg), 1)
    else:
        speed_score = 50.0

    passed = real_score >= 60.0

    return {
        "real_score": int(round(real_score)),
        "skill_scores": {k: int(round(v)) for k, v in skill_scores.items()},
        "confidence_accuracy": int(round(confidence_accuracy)),
        "speed_score": int(round(speed_score)),
        "passed": passed,

        "summary": "",
    }


async def generate_result_summary(skill_scores: dict[str, int], weak_threshold: int = 60) -> str:
    """Short narrative via Ollama."""
    weak = [k for k, v in skill_scores.items() if v < weak_threshold]
    strong = [k for k, v in skill_scores.items() if v >= 75]
    prompt = f"""Summarize in one sentence (max 40 words) this candidate's skill test: strong: {strong}, weak: {weak}.
Output ONLY the summary sentence."""
    try:
        text = await call_ollama(prompt)
        return text.strip()
    except Exception as e:
        logger.warning("Failed to generate test summary: %s", e)
        parts = []
        if strong:
            parts.append(f"Strong in {', '.join(strong[:4])}")
        if weak:
            parts.append(f"needs improvement in {', '.join(weak[:4])}")
        return ". ".join(parts) + "." if parts else "Test completed."
