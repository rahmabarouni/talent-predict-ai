"""SQLAlchemy ORM model for the analysis result cache table."""

from __future__ import annotations

from datetime import datetime

from sqlalchemy import Column, DateTime, String, Text, func

from db.database import Base


class AnalysisCache(Base):
    """Persists the full JSON result of one candidate analysis, keyed by GitHub username."""

    __tablename__ = "analysis_cache"

    github_username = Column(String(255), primary_key=True, nullable=False)
    portfolio_url = Column(String(500), nullable=True)
    linkedin_username = Column(String(255), nullable=True)
    summary = Column(Text, nullable=True)
    result = Column(Text, nullable=False)
    created_at = Column(DateTime, server_default=func.now(), nullable=False)
    updated_at = Column(
        DateTime,
        server_default=func.now(),
        onupdate=func.now(),
        nullable=False,
    )
