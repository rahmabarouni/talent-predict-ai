"""Centralized environment settings for Ollama and HTTP clients."""

from __future__ import annotations

import json
import os


def _get_int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        return int(raw)
    except ValueError:
        return default


def _get_float(name: str, default: float) -> float:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        return float(raw)
    except ValueError:
        return default


def _get_json_dict(name: str, default: dict[str, float]) -> dict[str, float]:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        data = json.loads(raw)
        if not isinstance(data, dict):
            return default
        result: dict[str, float] = {}
        for key, value in data.items():
            try:
                result[str(key)] = float(value)
            except (TypeError, ValueError):
                continue
        return result or default
    except json.JSONDecodeError:
        return default


OLLAMA_BASE_URL = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").rstrip("/")
OLLAMA_MODEL = os.getenv("OLLAMA_MODEL", "llama3.2:latest")
OLLAMA_TIMEOUT = _get_int("OLLAMA_TIMEOUT", 90)
OLLAMA_TEMPERATURE = _get_float("OLLAMA_TEMPERATURE", 0.7)
CODE_CHALLENGE_MODEL = os.getenv("CODE_CHALLENGE_MODEL", OLLAMA_MODEL)
GITHUB_TOKEN = os.getenv("GITHUB_TOKEN", "")

