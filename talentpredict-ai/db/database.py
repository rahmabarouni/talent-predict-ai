"""Async SQLAlchemy engine and session factory for the AI cache database."""

from __future__ import annotations

import os

from dotenv import load_dotenv
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine
from sqlalchemy.orm import DeclarativeBase

# Ensure .env is loaded before reading DB vars (idempotent if already called)
load_dotenv()


def _build_db_url() -> str:
    """Build the async DB URL from env vars."""
    url = os.getenv("AI_DATABASE_URL", "").strip()
    if url:
        # Ensure async driver prefix
        if url.startswith("postgresql://"):
            url = url.replace("postgresql://", "postgresql+asyncpg://", 1)
        return url

    host = os.getenv("AI_DB_HOST", "localhost")
    port = os.getenv("AI_DB_PORT", "5432")
    name = os.getenv("AI_DB_NAME", "talentpredict_ai")
    user = os.getenv("AI_DB_USER", "postgres")
    password = os.getenv("AI_DB_PASSWORD", "")
    return f"postgresql+asyncpg://{user}:{password}@{host}:{port}/{name}"


DATABASE_URL = _build_db_url()

engine = create_async_engine(DATABASE_URL, echo=False, pool_pre_ping=True)
AsyncSessionLocal = async_sessionmaker(engine, class_=AsyncSession, expire_on_commit=False)


class Base(DeclarativeBase):
    pass


async def init_db() -> None:
    """Create all tables if they do not exist yet."""
    # Import models so Base.metadata is populated before create_all
    from db import models  # noqa: F401

    async with engine.begin() as conn:
        await conn.run_sync(Base.metadata.create_all)
