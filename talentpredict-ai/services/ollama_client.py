"""Shared Ollama HTTP client with JSON extraction and one retry on parse failure."""

from __future__ import annotations

import json
import logging
import re
from typing import Any

import httpx
import json5

from config.settings import (
    OLLAMA_BASE_URL,
    OLLAMA_MODEL,
    OLLAMA_TEMPERATURE,
    OLLAMA_TIMEOUT,
)

logger = logging.getLogger(__name__)

_JSON_BLOCK = re.compile(r"(\[[\s\S]*\]|\{[\s\S]*\})")


def _extract_chat_completion_text(data: dict[str, Any]) -> str:
    """Extract text from OpenAI-compatible chat completion payload."""
    choices = data.get("choices") or []
    if not choices:
        return ""

    first = choices[0] if isinstance(choices[0], dict) else {}
    message = first.get("message") if isinstance(first.get("message"), dict) else {}
    content = message.get("content")

    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts: list[str] = []
        for block in content:
            if isinstance(block, dict):
                text = block.get("text")
                if isinstance(text, str):
                    parts.append(text)
        return "".join(parts)

    text = first.get("text")
    return text if isinstance(text, str) else ""


def extract_json_array_or_object(text: str) -> str | None:
    """Find first JSON array or object in LLM output (handles extra prose/markdown)."""
    if not text:
        return None
    t = text.strip()
    # Strip markdown code fences
    if "```" in t:
        parts = re.split(r"```(?:json)?\s*", t, flags=re.IGNORECASE)
        if len(parts) >= 2:
            t = parts[1].split("```", 1)[0].strip()
    m = _JSON_BLOCK.search(t)
    if m:
        return m.group(1).strip()
    return None




async def call_ollama(
    prompt: str,
    *,
    model: str | None = None,
    temperature: float | None = None,
    top_p: float = 0.9,
    json_mode: bool = False,
) -> str:
    """Call local model server and return response text.

    Supports both:
    - Ollama native: POST /api/generate
    - OpenAI-compatible: POST /v1/chat/completions
    """
    prompt = f"{prompt}\n\nIMPORTANT: All generated text content MUST be in French."
    m = model or OLLAMA_MODEL
    temp = OLLAMA_TEMPERATURE if temperature is None else temperature
    generate_url = f"{OLLAMA_BASE_URL}/api/generate"
    generate_payload = {
        "model": m,
        "prompt": prompt,
        "stream": False,
        "options": {"temperature": temp, "top_p": top_p, "num_predict": 2048, "num_ctx": 4096},
    }
    if json_mode:
        generate_payload["format"] = "json"

    timeout = httpx.Timeout(OLLAMA_TIMEOUT)
    async with httpx.AsyncClient(timeout=timeout) as client:
        try:
            response = await client.post(generate_url, json=generate_payload)
            response.raise_for_status()
            data = response.json()
            return data.get("response", "")
        except httpx.HTTPStatusError as exc:
            # Some local providers expose only OpenAI-compatible /v1 endpoints.
            if exc.response.status_code != 404:
                raise

            error_text = (exc.response.text or "").lower()
            if "model" in error_text and "not found" in error_text:
                raise

            logger.warning(
                "Model server returned 404 for %s; retrying with /v1/chat/completions",
                generate_url,
            )
            chat_url = f"{OLLAMA_BASE_URL}/v1/chat/completions"
            chat_payload = {
                "model": m,
                "messages": [{"role": "user", "content": prompt}],
                "temperature": temp,
                "top_p": top_p,
                "max_tokens": 2048,
                "stream": False,
            }
            if json_mode:
                chat_payload["response_format"] = {"type": "json_object"}

            chat_response = await client.post(chat_url, json=chat_payload)
            chat_response.raise_for_status()
            chat_data = chat_response.json()
            text = _extract_chat_completion_text(chat_data)
            if not text:
                raise ValueError("Empty content from /v1/chat/completions response")
            return text


async def call_ollama_json(
    prompt: str,
    *,
    model: str | None = None,
    temperature: float | None = None,
    retry_stricter: bool = True,
    json_mode: bool = True,
) -> Any:
    """Call Ollama and parse JSON; retry once with stricter instruction if parse fails."""
    text = await call_ollama(prompt, model=model, temperature=temperature, json_mode=json_mode)
    try:
        return parse_json_lenient(text)
    except (json.JSONDecodeError, TypeError, ValueError) as e:
        logger.warning("JSON parse failed (first attempt): %s", e)
        if not retry_stricter:
            raise
        strict = (
            f"{prompt}\n\n"
            "CRITICAL: Output ONLY valid JSON. No markdown, no prose, no code fences."
        )
        text2 = await call_ollama(strict, model=model, temperature=0.3, json_mode=json_mode)
        return parse_json_lenient(text2)

def parse_json_lenient(text):
    # Strip markdown code blocks
    text = re.sub(r"```(?:json)?\s*", "", text).strip()
    text = re.sub(r"```\s*$", "", text).strip()

    # Replace smart/curly quotes with straight quotes
    text = text.replace("\u201c", '"').replace("\u201d", '"')
    text = text.replace("\u2018", "'").replace("\u2019", "'")

    # Try direct parse first
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # Try to find and parse the first complete JSON object or array
    for match in re.finditer(r"(\{|\[)", text):
        start = match.start()
        opener = match.group(1)
        closer = "}" if opener == "{" else "]"
        depth = 0
        in_string = False
        escape = False
        for i, ch in enumerate(text[start:], start):
            if escape:
                escape = False
                continue
            if ch == "\\" and in_string:
                escape = True
                continue
            if ch == '"':
                in_string = not in_string
                continue
            if in_string:
                continue
            if ch == opener:
                depth += 1
            elif ch == closer:
                depth -= 1
                if depth == 0:
                    candidate = text[start:i+1]
                    try:
                        return json.loads(candidate)
                    except json.JSONDecodeError:
                        try:
                            return json5.loads(candidate)
                        except Exception:
                            break

    raise ValueError(f"No valid JSON found in response: {text[:200]}")