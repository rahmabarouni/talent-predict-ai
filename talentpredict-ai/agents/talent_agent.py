
from __future__ import annotations
import json
import logging
import os
from typing import Any

import anthropic
import httpx  #for API calls to Ollama 

from memory.agent_memory import memory
from prompts.agent_prompt_template import SYSTEM_PROMPT, TOOL_DEFINITIONS
from services.job_matching import match_job
from services.scoring_engine import score_skills
from services.skill_extractor import extract_skills
from tools.cv_parser import analyze_cv_text
from tools.github_analyzer import analyze_github
from tools.portfolio_analyzer import analyze_portfolio

logger = logging.getLogger(__name__)

MAX_ITERATIONS = 10  # safety limit for the agentic loop


def _is_ollama() -> bool:
    """Check if the configured base URL is Ollama (local)."""
    base = (os.getenv("ANTHROPIC_BASE_URL") or "").strip()
    return "11434" in base or "ollama" in base.lower()


def _extract_linkedin_username(linkedin_url: str | None) -> str | None:
    """Extract a LinkedIn handle from a profile URL."""
    if not linkedin_url:
        return None

    cleaned = linkedin_url.strip()
    if not cleaned:
        return None

    cleaned = cleaned.replace("https://", "").replace("http://", "")
    if cleaned.startswith("www."):
        cleaned = cleaned[4:]

    if "linkedin.com/" in cleaned:
        cleaned = cleaned.split("linkedin.com/", 1)[1]

    cleaned = cleaned.split("?", 1)[0].split("#", 1)[0].strip("/")
    if not cleaned:
        return None

    parts = [p for p in cleaned.split("/") if p]
    if not parts:
        return None

    if parts[0].lower() in {"in", "pub"} and len(parts) > 1:
        return parts[1]

    return parts[0]

def _build_user_message(
    github_username: str | None,
    portfolio_url: str | None,
    cv_text: str | None,
    linkedin_url: str | None = None,
    linkedin_content: str | None = None,
) -> str:
    """Construct the initial user message describing available sources."""
    parts = ["Analyze this developer candidate's hard skills.\n"]
    if github_username:
        parts.append(f"GitHub username: {github_username}")
    if portfolio_url:
        parts.append(f"Portfolio URL: {portfolio_url}")
    if cv_text:
        parts.append(f"CV text (extracted from PDF):\n{cv_text[:3000]}")
    if linkedin_url:
        parts.append(f"LinkedIn profile URL: {linkedin_url}")
    if not any([github_username, portfolio_url, cv_text, linkedin_url, linkedin_content]):
        parts.append("No sources provided. Return an error message.")
    parts.append(
        "\nPlease analyze all available sources using the tools, then produce "
        "the final structured skill analysis. If LinkedIn URL or content was provided, "
        "include the LINKEDIN ANALYSIS section in your report."
    )
    return "\n".join(parts)


async def _dispatch_tool(name: str, input_args: dict[str, Any]) -> Any:
    """Route a tool call from Claude to the appropriate Python function."""
    if name == "analyze_github":
        return await analyze_github(input_args["username"])
    if name == "analyze_cv":
        return analyze_cv_text(input_args["cv_text"])
    if name == "analyze_portfolio":
        return await analyze_portfolio(input_args["url"])
    if name == "extract_and_score_skills":
        raw_skills = extract_skills(
            input_args.get("github_data", {}),
            input_args.get("cv_data", {}),
            input_args.get("portfolio_data", {}),
        )
        github_data = input_args.get("github_data", {})
        scored, experience_score = score_skills(raw_skills, github_data)
        return {"skills": scored, "experience_score": experience_score}
    if name == "match_job_requirements":
        return match_job(
            input_args.get("skills", []),
            input_args.get("experience_score", 0),
        )
    return {"error": f"Unknown tool: {name}"}


async def _run_direct_pipeline(
    github_username: str | None,
    portfolio_url: str | None,
    cv_text: str | None,
    linkedin_url: str | None = None,
    linkedin_content: str | None = None,
) -> dict[str, Any]:
    """Run analysis by calling tools directly (no LLM tool orchestration).

    This is used for local models (Ollama) that are unreliable at function
    calling. The tools are executed in the known correct order, and the LLM
    is only used at the end for a brief summary.
    """
    logger.info("Running direct pipeline (Ollama mode)")
    data_sources: list[str] = []

    # Step 1: Gather raw data from all available sources
    github_data: dict[str, Any] = {}
    if github_username:
        try:
            github_data = await analyze_github(github_username)
            if not github_data.get("error"):
                data_sources.append("github")
            else:
                logger.warning("GitHub analysis error: %s", github_data.get("error"))
        except Exception as exc:
            logger.error("GitHub analysis failed: %s", exc)

    cv_data: dict[str, Any] = {}
    if cv_text:
        try:
            cv_data = analyze_cv_text(cv_text)
            if not cv_data.get("error"):
                data_sources.append("cv")
        except Exception as exc:
            logger.error("CV analysis failed: %s", exc)

    portfolio_data: dict[str, Any] = {}
    if portfolio_url:
        try:
            portfolio_data = await analyze_portfolio(portfolio_url)
            if not portfolio_data.get("error"):
                data_sources.append("portfolio")
        except Exception as exc:
            logger.error("Portfolio analysis failed: %s", exc)

    if linkedin_url or linkedin_content:
        data_sources.append("linkedin")

    # Step 2: Extract and score skills
    raw_skills = extract_skills(github_data, cv_data, portfolio_data)
    scored_skills, experience_score = score_skills(raw_skills, github_data)

    # Step 3: Match job requirements
    job_result = match_job(scored_skills, experience_score)

    # Step 4: Build top languages from GitHub data
    top_languages: list[str] = []
    if github_data and not github_data.get("error"):
        lang_stats = github_data.get("language_stats", {})
        top_languages = sorted(lang_stats, key=lambda k: lang_stats[k], reverse=True)[:5]

    repos_analyzed = github_data.get("repositories_analyzed", 0) if github_data else 0

    # Step 5: Generate summary using Ollama (simple, no tools)
    summary = await _generate_ollama_summary(
        github_username, github_data, cv_data, portfolio_data,
        scored_skills, experience_score, linkedin_content,
    )

    # Step 6: Build linkedin analysis text
    linkedin_analysis = ""
    if linkedin_content:
        linkedin_analysis = f"LinkedIn content provided for {github_username or 'candidate'}."

    return {
        "candidate": github_data.get("name") or github_username or "unknown",
        "data_sources": data_sources,
        "summary": summary,
        "skills": scored_skills,
        "experience_score": experience_score,
        "repositories_analyzed": repos_analyzed,
        "top_languages": top_languages,
        "linkedin_analysis": linkedin_analysis,
        "job_match": {
            "profile": job_result.get("job_profile", ""),
            "score": job_result.get("job_match_score", 0),
            "matched_skills": job_result.get("matched_skills", []),
            "missing_skills": job_result.get("missing_skills", []),
        },
    }


async def _generate_ollama_summary(
    github_username: str | None,
    github_data: dict[str, Any],
    cv_data: dict[str, Any],
    portfolio_data: dict[str, Any],
    scored_skills: list[dict[str, Any]],
    experience_score: int,
    linkedin_content: str | None = None,
) -> str:
    """Generate a brief profile summary using Ollama (no tools, simple prompt)."""
    base_url = os.getenv("OLLAMA_BASE_URL", "http://localhost:11434").rstrip("/") + "/v1"
    model = os.getenv("OLLAMA_MODEL", "llama3.2:latest")

    skill_names = [s["name"] for s in scored_skills[:10]]
    top_langs = []
    if github_data and not github_data.get("error"):
        lang_stats = github_data.get("language_stats", {})
        top_langs = sorted(lang_stats, key=lambda k: lang_stats[k], reverse=True)[:5]

    prompt = (
        f"Write a 2-sentence professional summary for a developer.\n"
        f"Username: {github_username or 'unknown'}\n"
        f"Top skills: {', '.join(skill_names) if skill_names else 'none detected'}\n"
        f"Top languages: {', '.join(top_langs) if top_langs else 'unknown'}\n"
        f"Experience score: {experience_score}/100\n"
        f"Repos analyzed: {github_data.get('repositories_analyzed', 0)}\n"
        f"Reply with ONLY the summary text, nothing else."
    )

    try:
        async with httpx.AsyncClient(timeout=60) as client:
            resp = await client.post(
                f"{base_url.rstrip('/')}/chat/completions",
                headers={"Content-Type": "application/json"},
                json={
                    "model": model,
                    "max_tokens": 200,
                    "messages": [{"role": "user", "content": prompt}],
                },
            )
            resp.raise_for_status()
            data = resp.json()
            return data["choices"][0]["message"].get("content", "").strip()
    except Exception as exc:
        logger.warning("Ollama summary generation failed: %s", exc)
        if skill_names:
            return f"Developer with expertise in {', '.join(skill_names[:5])}. Experience score: {experience_score}/100."
        return "Developer profile analyzed."


async def run_agent(
    github_username: str | None = None,
    portfolio_url: str | None = None,
    cv_text: str | None = None,
    linkedin_url: str | None = None,
    linkedin_content: str | None = None,
) -> dict[str, Any]:
    """Run the TalentPredict agent and return a structured analysis."""

    linkedin_username = _extract_linkedin_username(linkedin_url)

    # Check memory cache — skip if fresh content sources are provided (CV, portfolio,
    # LinkedIn text) since those inputs may have changed since the last run.
    cache_key = github_username or "unknown"
    if not cv_text and not portfolio_url and not linkedin_content:
        cached = await memory.get(cache_key)
        if cached:
            logger.info("Returning cached result for %s", cache_key)
            return cached

    api_key = os.getenv("ANTHROPIC_API_KEY", "")

    # For Ollama, use the direct pipeline (no LLM tool orchestration) because
    # small local models are unreliable at function calling.
    if _is_ollama():
        result = await _run_direct_pipeline(
            github_username, portfolio_url, cv_text, linkedin_url, linkedin_content
        )
        if "error" not in result:
            await memory.set(
                cache_key,
                result,
                portfolio_url=portfolio_url,
                linkedin_username=linkedin_username,
                summary=result.get("summary", ""),
            )
        return result

    # Anthropic requires an API key when not using Ollama.
    if not api_key:
        return {"error": "ANTHROPIC_API_KEY is not configured."}

    user_message = _build_user_message(
        github_username, portfolio_url, cv_text, linkedin_url, linkedin_content
    )

    result = await _run_agent_anthropic(api_key, user_message, github_username)

    if "error" not in result:
        await memory.set(
            cache_key,
            result,
            portfolio_url=portfolio_url,
            linkedin_username=linkedin_username,
            summary=result.get("summary", ""),
        )
    return result

async def _run_agent_anthropic(
    api_key: str,
    user_message: str,
    github_username: str | None,
) -> dict[str, Any]:
    """Run the agent via the native Anthropic API."""
    client = anthropic.Anthropic(api_key=api_key)

    messages: list[dict[str, Any]] = [
        {"role": "user", "content": user_message},
    ]

    # Agentic tool-use loop
    for iteration in range(MAX_ITERATIONS):
        logger.info("Agent iteration %d (Anthropic)", iteration + 1)

        try:
            response = client.messages.create(
                model=os.getenv("ANTHROPIC_MODEL", "claude-sonnet-4-20250514"),
                max_tokens=4096,
                system=SYSTEM_PROMPT,
                tools=TOOL_DEFINITIONS,
                messages=messages,
            )
        except anthropic.AuthenticationError as exc:
            logger.error("Authentication failed: %s", exc)
            return {"error": "Clé API invalide. Vérifiez ANTHROPIC_API_KEY dans le fichier .env."}
        except anthropic.APIError as exc:
            logger.error("API error: %s", exc)
            return {"error": f"Erreur API Anthropic: {exc}"}

        # Check if the response contains tool_use blocks
        tool_use_blocks = [b for b in response.content if b.type == "tool_use"]

        if not tool_use_blocks:
            # Agent is done — extract final text response
            text_parts = [b.text for b in response.content if b.type == "text"]
            final_text = "\n".join(text_parts)
            return _parse_final_response(final_text, github_username)

        # Process each tool call
        tool_results: list[dict[str, Any]] = []
        for tool_block in tool_use_blocks:
            logger.info("Calling tool: %s", tool_block.name)
            try:
                tool_output = await _dispatch_tool(tool_block.name, tool_block.input)
            except Exception as exc:
                logger.error("Tool %s failed: %s", tool_block.name, exc)
                tool_output = {"error": str(exc)}

            tool_results.append(
                {
                    "type": "tool_result",
                    "tool_use_id": tool_block.id,
                    "content": json.dumps(tool_output, default=str),
                }
            )

        # Add assistant message + tool results to conversation
        messages.append({"role": "assistant", "content": response.content})
        messages.append({"role": "user", "content": tool_results})

    # If we exhausted iterations, return what we have
    return {"error": "Agent exceeded maximum iterations without completing analysis."}


def _parse_final_response(
    text: str,
    github_username: str | None,
) -> dict[str, Any]:
    """Parse the LLM's final text into a normalized JSON structure."""
    default: dict[str, Any] = {
        "candidate": github_username or "unknown",
        "data_sources": [],
        "summary": "",
        "skills": [],
        "experience_score": 0,
        "repositories_analyzed": 0,
        "top_languages": [],
        "linkedin_analysis": "",
        "job_match": {
            "profile": "",
            "score": 0,
            "matched_skills": [],
            "missing_skills": [],
            "recommendations": [],
        },
    }

    # Extract JSON from the response (handles code fences, surrounding text)
    json_start = text.find("{")
    json_end = text.rfind("}") + 1
    if json_start < 0 or json_end <= json_start:
        default["raw_analysis"] = text
        return default

    try:
        parsed = json.loads(text[json_start:json_end])
    except json.JSONDecodeError:
        default["raw_analysis"] = text
        return default

    # Normalize candidate field (could be string or dict)
    candidate = parsed.get("candidate", github_username or "unknown")
    if isinstance(candidate, dict):
        default["candidate"] = (
            candidate.get("github_username")
            or candidate.get("name")
            or github_username
            or "unknown"
        )
        default["summary"] = candidate.get("summary", "")
        default["data_sources"] = candidate.get("data_sources", [])
    else:
        default["candidate"] = candidate

    # Extract data_sources
    if not default["data_sources"]:
        default["data_sources"] = parsed.get("data_sources", [])

    # Extract summary
    if not default["summary"]:
        default["summary"] = parsed.get("summary", "")

    # Normalize skills (could be top-level or nested under skill_analysis)
    skills = parsed.get("skills", [])
    if not skills and "skill_analysis" in parsed:
        sa = parsed["skill_analysis"]
        skills = sa.get("skills", [])
        if not default["experience_score"]:
            default["experience_score"] = sa.get("overall_experience_score", 0)

    normalized_skills = []
    for s in skills:
        normalized_skills.append({
            "name": s.get("name", "Unknown"),
            "level": s.get("level", "Beginner"),
            "score": s.get("score", 0),
            "sources": s.get("sources", []),
        })
    default["skills"] = normalized_skills

    # Experience score
    default["experience_score"] = (
        parsed.get("experience_score")
        or parsed.get("overall_experience_score")
        or default["experience_score"]
    )

    # LinkedIn analysis (when LinkedIn URL or content was provided)
    default["linkedin_analysis"] = parsed.get("linkedin_analysis") or ""

    # Repos analyzed
    default["repositories_analyzed"] = parsed.get("repositories_analyzed", 0)

    # Top languages
    default["top_languages"] = parsed.get("top_languages", [])

    # Normalize job_match (could be top-level or nested)
    jm = parsed.get("job_match", {})
    if jm:
        default["job_match"] = {
            "profile": jm.get("profile", ""),
            "score": jm.get("score") or jm.get("match_score") or jm.get("job_match_score", 0),
            "matched_skills": jm.get("matched_skills", []),
            "missing_skills": jm.get("missing_skills", []),
            "recommendations": jm.get("recommendations", []),
        }
    else:
        default["job_match"]["score"] = parsed.get("job_match_score", 0)
        default["job_match"]["recommendations"] = parsed.get("recommendations", [])

    return default
