"""Generate adaptive-style MCQ tests via Ollama (questions never persisted here)."""

from __future__ import annotations

import asyncio
import logging
import random
import re
import uuid
from datetime import datetime, timezone
from typing import Any

from services.ollama_client import call_ollama_json

logger = logging.getLogger(__name__)

MAX_QUESTIONS = 20
MAX_PER_SKILL = 5
OPTION_KEYS = ("A", "B", "C", "D")
MAX_SKILLS_TO_QUERY = 4  # Allow more skills for richer question variety
TOTAL_TIMEOUT_SECONDS = 90  # Longer timeout now that Ollama connectivity is fixed
MIN_QUESTIONS_TARGET = 6
DEFAULT_MIN_QUESTIONS = 8
DEFAULT_MAX_QUESTIONS = 12


def _questions_per_skill(num_skills: int, target_count: int) -> int:
    if num_skills <= 0:
        return 0
    # Over-fetch by one item per skill to compensate for invalid model rows.
    requested = (target_count + num_skills - 1) // num_skills
    return min(MAX_PER_SKILL, max(1, requested + 1))


def _resolve_target_question_count(num_skills: int, requested_count: int | None) -> int:
    if requested_count is not None:
        return min(MAX_QUESTIONS, max(4, int(requested_count)))

    dynamic_min = DEFAULT_MIN_QUESTIONS + min(2, max(0, num_skills - 2))
    dynamic_max = DEFAULT_MAX_QUESTIONS + min(3, max(0, num_skills - 2))
    dynamic_max = min(MAX_QUESTIONS, max(dynamic_min, dynamic_max))
    return random.randint(dynamic_min, dynamic_max)


def _build_batch_prompt(skills: list[str], level: str, target_count: int) -> str:
    skills_str = ", ".join(skills)
    return (
        f'Génère un tableau JSON de {target_count} QCM techniques en français sur : {skills_str} (niveau {level}).\n'
        f'Chaque objet : {{"skill":"...","question":"...","options":{{"A":"...","B":"...","C":"...","D":"..."}},"correct":"A","difficulty":"hard"}}\n'
        f'IMPORTANT: Réponds UNIQUEMENT avec le tableau JSON, sans texte avant ou après.'
    )




def _normalize_options(raw: Any) -> dict[str, str] | None:
    if isinstance(raw, dict):
        mapped: dict[str, str] = {}
        for idx, key in enumerate(OPTION_KEYS, start=1):
            value = (
                raw.get(key)
                or raw.get(key.lower())
                or raw.get(str(idx))
                or raw.get(f"option_{key.lower()}")
            )
            if value is None:
                break
            mapped[key] = str(value).strip()
        if len(mapped) == 4 and all(mapped[k] for k in OPTION_KEYS):
            return mapped

        # Some models return an object with arbitrary keys instead of A/B/C/D.
        flat_values = [str(v).strip() for v in raw.values() if str(v).strip()]
        if len(flat_values) >= 4:
            return {k: flat_values[i] for i, k in enumerate(OPTION_KEYS)}

    if isinstance(raw, list):
        flat_values = [str(v).strip() for v in raw if str(v).strip()]
        if len(flat_values) >= 4:
            return {k: flat_values[i] for i, k in enumerate(OPTION_KEYS)}

    return None


def _normalize_correct_option(raw_correct: Any, options: dict[str, str]) -> str:
    if isinstance(raw_correct, int):
        if 1 <= raw_correct <= 4:
            return OPTION_KEYS[raw_correct - 1]
        if 0 <= raw_correct <= 3:
            return OPTION_KEYS[raw_correct]

    value = str(raw_correct or "").strip().upper()
    if value in OPTION_KEYS:
        return value
    if value in ("1", "2", "3", "4"):
        return OPTION_KEYS[int(value) - 1]

    letter_match = re.search(r"\b([ABCD])\b", value)
    if letter_match:
        return letter_match.group(1)

    for key, text in options.items():
        if value and value == text.strip().upper():
            return key

    return "A"


def _normalize_difficulty(raw: Any) -> str:
    diff = str(raw or "medium").lower().strip()
    if diff in ("beginner", "easy"):
        return "easy"
    if diff in ("intermediate", "normal", "medium"):
        return "medium"
    if diff in ("advanced", "expert", "hard"):
        return "hard"
    return "medium"


def _normalize_question_item(item: Any, skill: str, q_index: int) -> dict[str, Any] | None:
    if not isinstance(item, dict):
        return None

    question_text = str(
        item.get("question")
        or item.get("prompt")
        or item.get("title")
        or ""
    ).strip()
    if not question_text:
        return None

    options = _normalize_options(item.get("options") or item.get("choices") or item.get("answers"))
    if options is None:
        return None

    raw_correct = (
        item.get("correct")
        or item.get("answer")
        or item.get("correct_answer")
        or item.get("answer_index")
    )
    correct = _normalize_correct_option(raw_correct, options)
    difficulty = _normalize_difficulty(item.get("difficulty") or item.get("level"))
    confidence_required = difficulty == "hard" or (q_index % 3 == 0)

    return {
        "id": f"q{q_index}",
        "skill": skill,
        "difficulty": difficulty,
        "type": "mcq",
        "question": question_text,
        "options": options,
        "correct": correct,
        "confidence_required": confidence_required,
    }


def _fallback_questions(skills: list[str], level: str, target_count: int | None = None) -> list[dict[str, Any]]:
    if not skills:
        return []

    count = target_count if target_count is not None else max(len(skills), MIN_QUESTIONS_TARGET)
    count = min(MAX_QUESTIONS, max(1, count))
    difficulty = "hard" if level.strip().upper() in ("ADVANCED", "EXPERT") else "medium"

    # 10 distinct templates — enough for a 20-question test with no repeats
    templates = [
        {
            "q": "En {skill}, quelle pratique est la plus efficace pour réduire les bugs en production tout en gardant le code maintenable ?",
            "options": {
                "A": "Ignorer les tests pour aller plus vite.",
                "B": "Écrire de gros fichiers mélangeant les responsabilités.",
                "C": "Utiliser des interfaces claires, des tests ciblés et la validation des entrées.",
                "D": "Dupliquer le code fonctionnel pour éviter la refactorisation.",
            },
            "correct": "C",
        },
        {
            "q": "Lors de l'optimisation des performances d'une application {skill}, quelle est la première étape recommandée ?",
            "options": {
                "A": "Réécrire toute la base de code dans un langage de plus bas niveau.",
                "B": "Profiler l'application pour identifier les goulots d'étranglement.",
                "C": "Ajouter plus de ressources matérielles sans analyser le logiciel.",
                "D": "Désactiver tous les journaux pour économiser des cycles CPU.",
            },
            "correct": "B",
        },
        {
            "q": "Concernant la sécurité en {skill}, quelle approche offre la meilleure protection ?",
            "options": {
                "A": "Faire confiance par défaut à toutes les entrées utilisateur.",
                "B": "Stocker les identifiants sensibles directement dans le code source.",
                "C": "Mettre en œuvre une stratégie multicouche avec des audits réguliers.",
                "D": "Compter uniquement sur l'obscurité du code.",
            },
            "correct": "C",
        },
        {
            "q": "Quel est le principal avantage d'une architecture modulaire dans un projet {skill} ?",
            "options": {
                "A": "Elle rend le code plus difficile à comprendre.",
                "B": "Elle permet une meilleure séparation des responsabilités et facilite les tests.",
                "C": "Elle allonge le temps nécessaire pour chaque modification.",
                "D": "Elle oblige les développeurs à travailler sur le même fichier.",
            },
            "correct": "B",
        },
        {
            "q": "Dans un projet {skill}, quelle est la meilleure stratégie pour gérer les erreurs inattendues ?",
            "options": {
                "A": "Ignorer les exceptions pour ne pas perturber le flux principal.",
                "B": "Logger toutes les erreurs et alerter l'équipe sans interrompre le service.",
                "C": "Redémarrer l'application automatiquement à chaque erreur.",
                "D": "Afficher les traces d'erreur complètes à l'utilisateur final.",
            },
            "correct": "B",
        },
        {
            "q": "Quelle approche favorise la meilleure maintenabilité d'un code {skill} sur le long terme ?",
            "options": {
                "A": "Utiliser des noms de variables courts pour minimiser la frappe.",
                "B": "Écrire le moins de commentaires possible pour alléger les fichiers.",
                "C": "Respecter des conventions de nommage cohérentes et documenter les points complexes.",
                "D": "Regrouper toute la logique métier dans un seul fichier centralisé.",
            },
            "correct": "C",
        },
        {
            "q": "Comment le contrôle de version (Git) améliore-t-il le travail en équipe sur un projet {skill} ?",
            "options": {
                "A": "Il empêche plusieurs développeurs de travailler simultanément.",
                "B": "Il permet de tracer les modifications, collaborer et revenir à un état stable.",
                "C": "Il chiffre automatiquement tout le code source.",
                "D": "Il remplace le besoin de tests automatisés.",
            },
            "correct": "B",
        },
        {
            "q": "Lors de la revue de code d'une fonctionnalité {skill}, sur quoi se concentrer en priorité ?",
            "options": {
                "A": "Le style de formatage uniquement, en ignorant la logique.",
                "B": "La lisibilité, la correction logique, la sécurité et les cas limites.",
                "C": "Uniquement le nombre de lignes pour maintenir un code compact.",
                "D": "La vitesse d'exécution brute, sans regarder la clarté.",
            },
            "correct": "B",
        },
        {
            "q": "Quelle stratégie de test est la plus adaptée pour valider la logique métier en {skill} ?",
            "options": {
                "A": "Tester uniquement l'interface utilisateur après chaque déploiement.",
                "B": "Écrire des tests unitaires isolés pour chaque composant de logique métier.",
                "C": "Ne tester qu'en production pour avoir des données réelles.",
                "D": "Déléguer tous les tests à l'équipe QA sans impliquer les développeurs.",
            },
            "correct": "B",
        },
        {
            "q": "Dans un système {skill} à forte charge, quelle technique améliore la scalabilité ?",
            "options": {
                "A": "Augmenter la taille des transactions pour réduire leur nombre.",
                "B": "Utiliser la mise en cache, le load balancing et la décomposition en services.",
                "C": "Stocker toutes les données en mémoire vive sans persistance.",
                "D": "Utiliser un seul serveur puissant pour centraliser les traitements.",
            },
            "correct": "B",
        },
    ]

    questions: list[dict[str, Any]] = []
    for idx in range(1, count + 1):
        skill = skills[(idx - 1) % len(skills)]
        template = templates[(idx - 1) % len(templates)]
        
        questions.append(
            {
                "id": f"q{idx}",
                "skill": skill,
                "difficulty": difficulty,
                "type": "mcq",
                "question": template["q"].format(skill=skill),
                "options": template["options"],
                "correct": template["correct"],
                "confidence_required": difficulty == "hard" or (idx % 3 == 0),
            }
        )
    return questions


async def generate_test(
    skills: list[str],
    level: str,
    candidate_id: str,
    skill_scores: dict[str, float] | None = None,
    question_count: int | None = None,
) -> dict[str, Any]:
    """Produce a variable-size adaptive MCQ set across skills using a single fast batch prompt."""
    skills_clean = [s.strip() for s in skills if s and s.strip()]
    if skill_scores:
        skills_clean.sort(
            key=lambda s: skill_scores.get(s, skill_scores.get(s.lower(), 0.0)),
            reverse=True,
        )
    skills_for_generation = skills_clean[:MAX_SKILLS_TO_QUERY]
    n_skills = len(skills_for_generation)
    target_count = _resolve_target_question_count(n_skills, question_count)
    
    if target_count == 0 or not skills_for_generation:
        test_id = str(uuid.uuid4())
        return {
            "test_id": test_id,
            "generated_at": datetime.now(timezone.utc).isoformat(),
            "questions": [],
            "question_count": 0,
        }

    # Ask Ollama for the full target count in a single batch call.
    # With num_predict=2048 tokens, Ollama can comfortably produce 8-12 MCQ items.
    # The fallback pool will top up any shortfall with unique pre-written questions.
    ai_target = min(target_count, 10)
    prompt = _build_batch_prompt(skills_for_generation, level, ai_target)
    raw_list = []
    try:
        data = await asyncio.wait_for(
            call_ollama_json(prompt, retry_stricter=False, json_mode=False),
            timeout=TOTAL_TIMEOUT_SECONDS,
        )
        if isinstance(data, dict):
            raw_list = [data]
        elif isinstance(data, list):
            raw_list = data
    except asyncio.TimeoutError:
        logger.warning("Ollama batch generation timeout after %ss", TOTAL_TIMEOUT_SECONDS)
    except Exception as e:
        logger.exception("Ollama batch generation failed: %s", e)

    questions: list[dict[str, Any]] = []
    q_index = 0
    
    # Process and normalize the generated questions
    for item in raw_list:
        if q_index >= MAX_QUESTIONS:
            break
        q_index += 1
        # Extract the skill mentioned in the JSON item or default to one of our target skills
        item_skill = str(item.get("skill") or "").strip()
        if not item_skill or item_skill not in skills_for_generation:
            # Assign a skill from our target list dynamically based on index
            item_skill = skills_for_generation[(q_index - 1) % len(skills_for_generation)]
        
        normalized = _normalize_question_item(item, item_skill, q_index)
        if normalized is None:
            q_index -= 1
            continue
        questions.append(normalized)

    min_target = min(max(MIN_QUESTIONS_TARGET, target_count), MAX_QUESTIONS)

    if len(questions) < min_target and skills_for_generation:
        logger.warning(
            "Generated only %s questions, topping up with fallback to reach at least %s",
            len(questions),
            min_target,
        )
        fallback_pool = _fallback_questions(skills_for_generation, level, target_count=min_target)
        for fallback_question in fallback_pool:
            if len(questions) >= min_target or len(questions) >= MAX_QUESTIONS:
                break
            questions.append(fallback_question)

    if not questions and skills_for_generation:
        logger.warning("No valid MCQs parsed from model output, using fallback questions")
        questions = _fallback_questions(skills_for_generation, level, target_count=min_target)

    if len(questions) > target_count:
        random.shuffle(questions)
        questions = questions[:target_count]

    random.shuffle(questions)

    final_questions = questions[:MAX_QUESTIONS]
    for i, q in enumerate(final_questions, start=1):
        q["id"] = f"q{i}"

    test_id = str(uuid.uuid4())
    return {
        "test_id": test_id,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "questions": final_questions,
        "question_count": len(final_questions),
    }
