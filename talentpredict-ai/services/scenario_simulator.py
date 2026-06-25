"""Scenario-based conflict simulator for soft skills assessment."""

from __future__ import annotations

import logging
from typing import Any

from services.ollama_client import call_ollama_json

logger = logging.getLogger(__name__)

async def generate_soft_skills_scenario(role: str, level: str) -> dict[str, Any]:
    """Generates a dynamic workplace scenario based on the role."""
    
    prompt = f"""You are an HR expert designing a behavioral assessment.
Create a realistic, challenging workplace conflict or dilemma scenario for a {level} {role}.
The scenario should not have an obvious "right" multiple-choice answer. It should test Empathy, Assertiveness, and Pragmatism.

Return ONLY JSON.
Schema:
{{
  "scenario_title": "string",
  "scenario_description": "string (2-3 sentences)",
  "skills_tested": ["Empathy", "Negotiation", "etc"]
}}"""
    
    try:
        scenario = await call_ollama_json(prompt, temperature=0.7, retry_stricter=True)
        return scenario
    except Exception as e:
        logger.error(f"Failed to generate scenario: {e}")
        return {
            "scenario_title": "Réduction du Délai de Livraison",
            "scenario_description": "Votre chef de produit vient de vous informer que la date limite d'une fonctionnalité critique que vous dirigez a été réduite de moitié en raison d'une réunion imminente avec des investisseurs. Que dites-vous et quelles sont vos prochaines étapes exactes ?",
            "skills_tested": ["Négociation", "Gestion du Stress", "Priorisation"]
        }


async def evaluate_scenario_response(scenario: str, candidate_response: str) -> dict[str, Any]:
    """Evaluates the candidate's free-text response against key psychological and professional vectors."""
    
    prompt = f"""You are an expert Organizational Psychologist evaluating a candidate's response to a workplace scenario.

Scenario provided to candidate:
"{scenario}"

Candidate's free-text response:
"{candidate_response}"

Evaluate their soft skills based on this response. Focus on actionable business communication, empathy, assertiveness, and pragmatism.
Return ONLY JSON.
Schema:
{{
  "scores": {{
    "empathy": <0-100>,
    "assertiveness": <0-100>,
    "pragmatism": <0-100>,
    "communication_clarity": <0-100>
  }},
  "strengths": ["string", "string"],
  "areas_for_improvement": ["string", "string"],
  "overall_feedback": "string (1 brief paragraph describing their approach)",
  "culture_add_profile": "string (e.g., 'A diplomatic problem solver who balances team well-being with delivery.')"
}}"""

    try:
        evaluation = await call_ollama_json(prompt, temperature=0.2, retry_stricter=True)
        return evaluation
    except Exception as e:
        logger.error(f"Failed to evaluate response: {e}")
        return {
            "error": "Échec ou délai d'attente de l'évaluation",
            "scores": {"empathy": 50, "assertiveness": 50, "pragmatism": 50, "communication_clarity": 50},
            "overall_feedback": "Impossible d'évaluer la réponse automatiquement."
        }
