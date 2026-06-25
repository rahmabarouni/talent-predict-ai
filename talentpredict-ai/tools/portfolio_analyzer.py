"""Portfolio website analyzer tool.

Fetches a portfolio URL via HTTP GET, parses the HTML with BeautifulSoup,
and extracts text content plus detected technology keywords.
"""

from __future__ import annotations

import logging
import re
from typing import Any
from urllib.parse import urlparse

import httpx
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)

# Technology keywords to detect in portfolio page content
PORTFOLIO_TECH_KEYWORDS: list[str] = [
    # Languages
    "Python", "Java", "JavaScript", "TypeScript", "C#", "C++", "C",
    "Go", "Golang", "Rust", "Ruby", "PHP", "Swift", "Kotlin", "Scala",
    "R", "MATLAB", "Julia", "Perl", "Lua", "Haskell", "Elixir", "Dart",
    "Zig", "Groovy", "Bash", "Shell", "PowerShell", "SQL", "Solidity",
    "Objective-C", "COBOL", "Fortran", "Assembly", "VBA", "Apex", "ABAP",

    # Frontend Frameworks & Libraries
    "React", "Angular", "Vue", "Svelte", "SvelteKit", "Next.js", "Nuxt",
    "Gatsby", "Remix", "Astro", "SolidJS", "Qwik", "Ember.js", "Preact",
    "HTMX", "Alpine.js", "Lit", "Web Components", "jQuery",

    # CSS Frameworks & UI Libraries
    "Tailwind CSS", "Tailwind", "Bootstrap", "Material UI", "Chakra UI",
    "shadcn/ui", "Ant Design", "Mantine", "DaisyUI", "Radix UI", "Bulma",
    "Styled Components", "Emotion", "Framer Motion", "GSAP",
    "Three.js", "D3.js", "CSS", "Sass", "LESS", "HTML",

    # Backend Frameworks
    "Spring Boot", "Django", "Flask", "FastAPI", "Express", "Node.js",
    "NestJS", "Fastify", "Koa", "Rails", "Laravel", "Symfony",
    "CodeIgniter", "Phoenix", "Ktor", "Quarkus", "Gin", "Fiber",
    "Actix", "Axum", ".NET", "ASP.NET", "Blazor", "Strapi",

    # Mobile
    "Flutter", "React Native", "Expo", "Ionic", "SwiftUI", "UIKit",
    "Jetpack Compose", "Capacitor", ".NET MAUI", "Xamarin",

    # Databases & Storage
    "PostgreSQL", "MySQL", "MariaDB", "MongoDB", "Redis", "Elasticsearch",
    "SQLite", "Cassandra", "DynamoDB", "Firestore", "Firebase",
    "CockroachDB", "Neo4j", "InfluxDB", "ClickHouse", "Snowflake",
    "BigQuery", "Redshift", "Oracle DB", "SQL Server", "Supabase",
    "PlanetScale", "Neon", "Turso", "FaunaDB",

    # ORMs & Query Builders
    "Prisma", "TypeORM", "Sequelize", "Drizzle ORM", "SQLAlchemy",
    "Hibernate", "Active Record", "GORM",

    # DevOps & Cloud
    "Docker", "Kubernetes", "Helm", "ArgoCD", "Istio",
    "AWS", "Azure", "GCP", "Vercel", "Netlify", "Heroku",
    "Cloudflare", "DigitalOcean", "Render", "Fly.io",
    "Terraform", "Ansible", "Pulumi", "Vagrant",
    "CI/CD", "DevOps", "DevSecOps", "SRE",
    "GitHub Actions", "GitLab CI", "CircleCI", "Jenkins",
    "Prometheus", "Grafana", "Datadog", "Sentry", "OpenTelemetry",
    "Nginx", "Apache", "Caddy", "Linux", "Serverless",

    # APIs & Protocols
    "GraphQL", "REST", "gRPC", "tRPC", "WebSockets", "OpenAPI",
    "Swagger", "JWT", "OAuth", "MQTT",

    # Messaging & Streaming
    "Kafka", "RabbitMQ", "NATS", "Celery", "Amazon SQS", "Apache Pulsar",

    # AI & Machine Learning
    "TensorFlow", "PyTorch", "Scikit-learn", "Keras", "JAX",
    "Pandas", "NumPy", "SciPy", "Matplotlib", "Plotly",
    "Machine Learning", "Deep Learning", "NLP", "Computer Vision",
    "LLMs", "Generative AI", "RAG", "Prompt Engineering",
    "LangChain", "LlamaIndex", "Hugging Face", "OpenAI API",
    "Anthropic API", "Ollama", "CrewAI", "LangGraph", "AutoGen",
    "MLflow", "Weights & Biases", "Ray", "Apache Spark", "PySpark",
    "Apache Airflow", "Prefect", "XGBoost", "LightGBM",
    "OpenCV", "spaCy", "ONNX",

    # Vector Databases
    "Pinecone", "Weaviate", "Qdrant", "ChromaDB", "Milvus",

    # Testing
    "Jest", "Vitest", "Cypress", "Playwright", "Selenium",
    "Pytest", "RSpec", "JUnit", "Mocha", "Storybook", "TDD", "BDD",

    # Build Tools & Runtimes
    "Vite", "Webpack", "Turborepo", "Nx", "Bun", "Deno",
    "Gradle", "Maven", "Cargo", "npm", "Yarn", "pnpm",

    # Version Control
    "Git", "GitHub", "GitLab", "Bitbucket",

    # Architecture & Patterns
    "Microservices", "Monorepo", "Event-Driven Architecture",
    "Domain-Driven Design", "CQRS", "Clean Architecture",
    "API Gateway", "Service Mesh", "SOLID",

    # Security
    "OWASP", "Penetration Testing", "Zero Trust", "Cryptography",
    "SOC 2", "DevSecOps",

    # Design & UX
    "Figma", "UI/UX", "Adobe XD", "Sketch", "Miro", "Zeplin",
    "Design Systems", "Accessibility", "WCAG", "Wireframing",
    "Prototyping", "User Research",

    # Project & Analytics Tools
    "Jira", "Confluence", "Notion", "Linear", "Asana",
    "Tableau", "Power BI", "Looker", "Google Analytics",
    "Mixpanel", "Amplitude", "PostHog",

    # Methodology
    "Agile", "Scrum", "Kanban", "Lean", "DevOps",
    "Pair Programming", "Code Review",

    # CMS & E-commerce
    "WordPress", "Shopify", "Contentful", "Sanity", "Webflow",
    "Storyblok", "Payload CMS",

    # Blockchain & Web3
    "Ethereum", "Solana", "Solidity", "Hardhat", "Ethers.js",
    "Web3.js", "IPFS", "Smart Contracts", "Web3", "Blockchain",
    "NFT", "DeFi",
]


def _validate_url(url: str) -> str:
    """Ensure the URL has a scheme and is HTTP/HTTPS."""
    if not url.startswith(("http://", "https://")):
        url = "https://" + url
    parsed = urlparse(url)
    if parsed.scheme not in ("http", "https"):
        raise ValueError(f"Invalid URL scheme: {parsed.scheme}")
    if not parsed.netloc:
        raise ValueError("URL has no host.")
    return url


def _extract_text(html: str) -> str:
    """Strip HTML tags and return visible text."""
    soup = BeautifulSoup(html, "html.parser")
    # Remove script and style tags
    for tag in soup(["script", "style", "noscript"]):
        tag.decompose()
    text = soup.get_text(separator=" ", strip=True)
    # Collapse whitespace
    text = re.sub(r"\s+", " ", text)
    return text


def _detect_technologies(text: str) -> list[str]:
    """Find known tech keywords in the page text."""
    found: list[str] = []
    for kw in PORTFOLIO_TECH_KEYWORDS:
        escaped = re.escape(kw)
        if re.search(rf"\b{escaped}\b", text, re.IGNORECASE):
            if kw not in found:
                found.append(kw)
    return found


def _extract_meta(html: str) -> dict[str, str]:
    """Pull useful meta tags (description, keywords, og:title)."""
    soup = BeautifulSoup(html, "html.parser")
    meta: dict[str, str] = {}
    title_tag = soup.find("title")
    if title_tag:
        meta["title"] = title_tag.get_text(strip=True)
    for tag in soup.find_all("meta"):
        name = (tag.get("name") or tag.get("property") or "").lower()
        content = tag.get("content", "")
        if name in ("description", "keywords", "og:title", "og:description") and content:
            meta[name] = content
    return meta


async def analyze_portfolio(url: str) -> dict[str, Any]:
    """Scrape a portfolio website and return detected technologies and summary."""
    try:
        url = _validate_url(url)
    except ValueError as exc:
        return {"error": str(exc), "technologies": [], "url": url}

    try:
        async with httpx.AsyncClient(
            timeout=20,
            follow_redirects=True,
            headers={"User-Agent": "TalentPredict-Bot/1.0"},
        ) as client:
            resp = await client.get(url)
            resp.raise_for_status()
            html = resp.text
    except httpx.HTTPError as exc:
        logger.warning("Failed to fetch portfolio %s: %s", url, exc)
        return {"error": f"Could not fetch portfolio: {exc}", "technologies": [], "url": url}

    page_text = _extract_text(html)
    technologies = _detect_technologies(page_text)
    meta = _extract_meta(html)

    return {
        "url": url,
        "title": meta.get("title", ""),
        "description": meta.get("description", ""),
        "technologies": technologies,
        "page_text_preview": page_text[:2000],  # truncate for token budget
    }
