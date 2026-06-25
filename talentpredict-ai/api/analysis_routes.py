"""GitHub deep analysis, fraud check (consolidated), and CV authenticity."""

from __future__ import annotations

import asyncio
import logging
from typing import Any

from fastapi import APIRouter
from pydantic import BaseModel, Field


from services.github_deep_analyzer import analyze_github_deep


logger = logging.getLogger(__name__)

router = APIRouter(prefix="/api/analysis", tags=["analysis"])




# ── GitHub Deep ───────────────────────────────────────────────────────────────

class GithubDeepBody(BaseModel):
    github_username: str
    candidate_id: str
    github_data: dict[str, Any] | None = None


@router.post("/github-deep")
async def github_deep(body: GithubDeepBody) -> dict[str, Any]:
    return await analyze_github_deep(body.github_username, body.candidate_id, body.github_data)



