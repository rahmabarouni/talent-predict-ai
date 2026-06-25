"""API route: POST /analyze-candidate

Accepts a GitHub username, optional portfolio URL, and optional CV PDF upload.
Delegates to the TalentPredict AI agent and returns structured JSON.
"""

from __future__ import annotations

import logging

from fastapi import APIRouter, File, Form, UploadFile
from fastapi.responses import JSONResponse

from agents.talent_agent import run_agent
from tools.cv_parser import analyze_cv

logger = logging.getLogger(__name__)

router = APIRouter()


@router.post("/analyze-candidate")
async def analyze_candidate(
    github: str = Form(..., description="GitHub username"),
    portfolio: str = Form(default="", description="Portfolio website URL (optional)"),
    cv_file: UploadFile | None = File(default=None, description="CV as PDF (optional)"),
    linkedin_url: str = Form(default="", description="LinkedIn profile URL (optional)"),
    linkedin_content: str = Form(default="", description="Pasted LinkedIn profile text (optional)"),
):
    """Analyze a developer candidate's hard skills from multiple sources.

    Input (multipart form):
        - github: GitHub username (required)
        - portfolio: portfolio URL (optional)
        - cv_file: PDF file upload (optional)
        - linkedin_url: LinkedIn profile URL (optional)
        - linkedin_content: Pasted LinkedIn profile text (optional)

    Returns structured JSON with skills, scores, and recommendations.
    """
    try:
        # Parse CV if uploaded
        cv_text: str | None = None
        cv_warning: str | None = None
        if cv_file and cv_file.filename:
            contents = await cv_file.read()
            if contents:
                cv_result = analyze_cv(contents)
                if cv_result.get("raw_text"):
                    cv_text = cv_result["raw_text"]
                else:
                    cv_warning = "Le PDF du CV ne contient pas de texte extractible (PDF scanné ou image). L'analyse du CV a été ignorée."
                    logger.warning("CV text extraction failed for %s: %s", cv_file.filename, cv_result.get("error", "no text"))

        portfolio_url = portfolio.strip() or None
        github_username = github.strip()
        linkedin_url_val = linkedin_url.strip() or None
        linkedin_content_val = linkedin_content.strip() or None
        # strip() make a new copy of string
        logger.info(
            "Analyzing candidate: github=%s, portfolio=%s, cv=%s, linkedin_url=%s",
            github_username,
            portfolio_url,
            "provided" if cv_text else "none",
            "yes" if linkedin_url_val or linkedin_content_val else "no",
        )

        result = await run_agent(
            github_username=github_username,
            portfolio_url=portfolio_url,
            cv_text=cv_text,
            linkedin_url=linkedin_url_val,
            linkedin_content=linkedin_content_val,
        )

        if "error" in result:
            return JSONResponse(status_code=422, content=result)

        if cv_warning:
            result["cv_warning"] = cv_warning

        return result
    except Exception as exc:
        logger.exception("Error analyzing candidate")
        return JSONResponse(
            status_code=500,
            content={"detail": str(exc)},
        )
