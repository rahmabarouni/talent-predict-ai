"""TalentPredict AI Service — FastAPI application entry point."""

from __future__ import annotations

import logging
import os

from dotenv import load_dotenv
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
#cros allows us to make requests from the frontend (angular) to the backend (fastapi)  
from api.analyze_candidate_route import router as analyze_router
from api.analysis_routes import router as analysis_router
from api.test_routes import router as test_router
from api.career_routes import router as career_router
from db.database import init_db

# Load environment variables from .env file
load_dotenv()

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
)

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Create DB tables on startup (no-op if they already exist).

    Failure is non-fatal: the service runs with in-memory (L1) cache only
    when the PostgreSQL database is unavailable.
    """
    try:
        await init_db()
    except Exception as exc:
        logging.getLogger(__name__).warning(
            "DB init failed — running with in-memory cache only: %s", exc
        )
    yield


app = FastAPI(
    title="TalentPredict AI Service",
    description=(
        "AI Agent microservice that analyzes developer hard skills from "
        "GitHub, CV, and portfolio sources using ollama ."
    ),
    version="1.0.0",
    lifespan=lifespan,
)

# CORS — allow Angular dev server and configurable origins
cors_origins = os.getenv(
    "CORS_ORIGINS",
    "http://localhost:4200,http://127.0.0.1:4200,http://localhost:3000",
)
origins = [o.strip() for o in cors_origins.split(",") if o.strip()]
# modifiy string to list
app.add_middleware(
    CORSMiddleware,
    allow_origins=origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include API routes
app.include_router(analyze_router)
app.include_router(test_router)
app.include_router(analysis_router)
app.include_router(career_router)
#every router become an endpoint in the backend, for example: /api/analyze, /api/test, /api/analysis, /api/jobs, /api/career



@app.get("/health")
async def health():
    """Health check endpoint."""
    return {"status": "ok", "service": "talentpredict-ai"}


