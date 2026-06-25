"""Two-level cache for candidate analysis results.

L1: in-process dict (fast, lost on restart)
L2: PostgreSQL table `analysis_cache` (persistent across restarts)

All public methods are async to allow non-blocking DB access.
"""

from __future__ import annotations

import json
import logging
import threading
from typing import Any

from db.database import AsyncSessionLocal
from db.models import AnalysisCache

logger = logging.getLogger(__name__)


class AgentMemory:
    """In-memory + PostgreSQL cache for analysis results."""

    def __init__(self) -> None:
        self._store: dict[str, dict[str, Any]] = {}
        self._lock = threading.Lock()

    # ── L1 helpers (synchronous) ──────────────────────────────────────────────

    def _l1_get(self, username: str) -> dict[str, Any] | None:
        with self._lock:
            return self._store.get(username)

    def _l1_set(self, username: str, data: dict[str, Any]) -> None:
        with self._lock:
            self._store[username] = data

    # ── Public API (async) ───────────────────────────────────────────────────

    async def get(self, username: str) -> dict[str, Any] | None:
        """Return cached result for *username*, checking memory then DB."""
        # L1 hit
        cached = self._l1_get(username)
        if cached is not None:
            logger.debug("Cache L1 hit for %s", username)
            return cached

        # L2: database
        try:
            async with AsyncSessionLocal() as session:
                row = await session.get(AnalysisCache, username)
                if row is not None:
                    raw_result = row.result
                    if isinstance(raw_result, str):
                        data = json.loads(raw_result)
                    elif isinstance(raw_result, dict):
                        data = raw_result
                    else:
                        data = {}
                    self._l1_set(username, data)
                    logger.debug("Cache L2 (DB) hit for %s", username)
                    return data
        except Exception as exc:
            logger.warning("DB read failed for %s: %s", username, exc)

        return None

    async def set(
        self,
        username: str,
        data: dict[str, Any],
        portfolio_url: str | None = None,
        linkedin_username: str | None = None,
        summary: str | None = None,
    ) -> None:
        """Persist *data* for *username* in both L1 and the DB."""
        self._l1_set(username, data)

        try:
            async with AsyncSessionLocal() as session:
                row = await session.get(AnalysisCache, username)
                payload = json.dumps(data, default=str)
                summary_text = summary or data.get("summary", "")
                if row is None:
                    session.add(
                        AnalysisCache(
                            github_username=username,
                            portfolio_url=portfolio_url,
                            linkedin_username=linkedin_username,
                            summary=summary_text,
                            result=payload,
                        )
                    )
                else:
                    row.portfolio_url = portfolio_url
                    row.linkedin_username = linkedin_username
                    row.summary = summary_text
                    row.result = payload
                await session.commit()
            logger.debug("Cache L2 (DB) written for %s", username)
        except Exception as exc:
            logger.warning("DB write failed for %s: %s", username, exc)

    def clear(self, username: str | None = None) -> None:
        """Evict from L1 only (does not touch the DB)."""
        with self._lock:
            if username:
                self._store.pop(username, None)
            else:
                self._store.clear()

    def list_keys(self) -> list[str]:
        with self._lock:
            return list(self._store.keys())


# Singleton shared across the application
memory = AgentMemory()
