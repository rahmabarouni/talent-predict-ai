"""CV parser tool.

Extracts raw text from a PDF file using pdfplumber and identifies
programming languages, frameworks, tools, and experience mentions.
"""

from __future__ import annotations

import io
import logging
import re
from typing import Any

import pdfplumber

logger = logging.getLogger(__name__)

# Broad list of tech keywords to look for (case-insensitive matching)
TECH_KEYWORDS: list[str] = [
    # Languages
    "Python", "Java", "JavaScript", "TypeScript", "C#", "C\\+\\+", "C",
    "Go", "Golang", "Rust", "Ruby", "PHP", "Swift", "Kotlin", "Scala",
    "R", "MATLAB", "Julia", "Perl", "Lua", "Haskell", "Elixir", "Erlang",
    "Clojure", "F#", "OCaml", "Dart", "Zig", "Nim", "Groovy", "VBA",
    "Bash", "Shell", "PowerShell", "SQL", "PL/SQL", "T-SQL", "NoSQL",
    "HTML", "CSS", "Sass", "LESS", "COBOL", "Fortran", "Assembly",
    "Solidity", "Vyper", "Move", "Objective-C", "ABAP", "Apex",

    # Frontend Frameworks & Libraries
    "React", "Angular", "Vue", "Svelte", "SvelteKit", "Next\\.js", "Nuxt",
    "Gatsby", "Remix", "Astro", "SolidJS", "Qwik", "Ember\\.js", "Preact",
    "HTMX", "Alpine\\.js", "Lit", "Web Components",

    # CSS Frameworks & UI
    "Tailwind CSS", "Bootstrap", "Material UI", "Chakra UI", "shadcn/ui",
    "Ant Design", "Mantine", "DaisyUI", "Radix UI", "Bulma",
    "Styled Components", "Emotion", "Framer Motion", "GSAP",
    "Three\\.js", "D3\\.js",

    # Backend Frameworks
    "Spring Boot", "Spring", "Django", "Flask", "FastAPI", "Express",
    "NestJS", "Fastify", "Koa", "Hapi\\.js", "Node\\.js", "Rails",
    "Laravel", "Symfony", "CodeIgniter", "Phoenix", "Ktor", "Quarkus",
    "Micronaut", "Gin", "Fiber", "Echo", "Actix", "Axum",
    "ASP\\.NET", "\\.NET", "Blazor", "Strapi", "Directus", "Payload CMS",

    # Mobile
    "Flutter", "React Native", "Expo", "Ionic", "Capacitor", "Cordova",
    "SwiftUI", "UIKit", "Jetpack Compose", "\\.NET MAUI", "Xamarin",

    # Databases & Storage
    "PostgreSQL", "MySQL", "MariaDB", "MongoDB", "Redis", "Elasticsearch",
    "SQLite", "Cassandra", "DynamoDB", "Firestore", "Firebase",
    "CockroachDB", "Neo4j", "InfluxDB", "TimescaleDB", "ClickHouse",
    "Snowflake", "BigQuery", "Redshift", "Oracle DB", "SQL Server",
    "CouchDB", "Couchbase", "FaunaDB", "PlanetScale", "Supabase",
    "Neon", "Turso",

    # ORMs & Query Builders
    "Prisma", "TypeORM", "Sequelize", "Drizzle ORM", "SQLAlchemy",
    "Hibernate", "GORM", "Active Record",

    # DevOps & Infrastructure
    "Docker", "Kubernetes", "Helm", "Istio", "ArgoCD", "Podman",
    "AWS", "Azure", "GCP", "Vercel", "Netlify", "Heroku", "Render",
    "Fly\\.io", "Cloudflare", "DigitalOcean",
    "Terraform", "Ansible", "Pulumi", "Vagrant", "Packer",
    "Jenkins", "GitHub Actions", "GitLab CI", "CircleCI", "Travis CI",
    "CI/CD", "DevOps", "DevSecOps", "SRE",
    "Prometheus", "Grafana", "Datadog", "Splunk", "New Relic",
    "OpenTelemetry", "Sentry", "PagerDuty",
    "Nginx", "Apache", "Caddy", "HAProxy",
    "Linux", "Ubuntu", "Debian", "Fedora", "RHEL", "Arch Linux",
    "Serverless", "AWS CDK", "CloudFormation",
    "Vault", "Consul", "Nomad",

    # APIs & Protocols
    "GraphQL", "REST", "gRPC", "tRPC", "WebSockets", "MQTT",
    "OpenAPI", "Swagger", "OAuth", "OAuth 2\\.0", "JWT", "SAML",
    "OpenID Connect",

    # Messaging & Streaming
    "Kafka", "RabbitMQ", "NATS", "Amazon SQS", "Google Pub/Sub",
    "Celery", "Redis Streams", "ActiveMQ", "Apache Pulsar",
    "Amazon Kinesis",

    # AI & Machine Learning
    "TensorFlow", "PyTorch", "Scikit-learn", "Keras", "JAX",
    "Pandas", "NumPy", "SciPy", "Matplotlib", "Seaborn", "Plotly",
    "Machine Learning", "Deep Learning", "NLP", "Computer Vision",
    "LLMs", "Generative AI", "RAG", "Prompt Engineering",
    "LangChain", "LlamaIndex", "Hugging Face", "Diffusers",
    "OpenAI API", "Anthropic API", "Ollama", "vLLM", "llama\\.cpp",
    "LangGraph", "CrewAI", "AutoGen",
    "MLflow", "DVC", "Weights & Biases", "Ray", "Dask",
    "Apache Spark", "PySpark", "Apache Airflow", "Prefect", "Dagster",
    "XGBoost", "LightGBM", "OpenCV", "spaCy", "NLTK",
    "ONNX", "Triton",

    # Vector Databases
    "Pinecone", "Weaviate", "Qdrant", "ChromaDB", "Milvus",
    "Vector Databases",

    # Testing
    "Jest", "Vitest", "Cypress", "Playwright", "Selenium",
    "Pytest", "RSpec", "JUnit", "TestNG", "Mocha", "Chai",
    "Jasmine", "Storybook", "Testing Library",
    "TDD", "BDD",

    # Build Tools & Runtimes
    "Webpack", "Vite", "Rollup", "esbuild", "Parcel", "Turbopack",
    "Turborepo", "Nx", "Bazel", "Gradle", "Maven", "Cargo",
    "Bun", "Deno", "pnpm", "Yarn", "npm", "CMake",

    # Version Control & Collaboration
    "Git", "GitHub", "GitLab", "Bitbucket",

    # Architecture & Patterns
    "Microservices", "Monorepo", "Event-Driven Architecture",
    "Domain-Driven Design", "CQRS", "Clean Architecture",
    "Hexagonal Architecture", "API Gateway", "Service Mesh",
    "SOLID Principles", "Design Patterns",

    # Security
    "OWASP", "Penetration Testing", "SOC 2", "Zero Trust",
    "Cryptography", "SIEM", "Cloud Security",

    # Methodology
    "Agile", "Scrum", "Kanban", "Lean", "SAFe",
    "Pair Programming", "Code Review", "Extreme Programming",

    # Design & Product
    "Figma", "Adobe XD", "Sketch", "Zeplin", "Miro",
    "Design Systems", "Accessibility", "WCAG",

    # Project & Analytics Tools
    "Jira", "Confluence", "Notion", "Linear", "Asana", "Trello",
    "Tableau", "Power BI", "Looker", "Google Analytics",
    "Mixpanel", "Amplitude", "PostHog", "Segment",

    # CMS & E-commerce
    "WordPress", "Shopify", "Contentful", "Sanity", "Storyblok",
    "Webflow",

    # Blockchain & Web3
    "Ethereum", "Solana", "Hardhat", "Foundry", "Ethers\\.js",
    "Web3\\.js", "IPFS", "Smart Contracts", "Web3", "Blockchain",
]


def _extract_text_from_pdf(file_bytes: bytes) -> str:
    """Extract all text from a PDF byte stream."""
    text_parts: list[str] = []
    with pdfplumber.open(io.BytesIO(file_bytes)) as pdf:
        for page in pdf.pages:
            page_text = page.extract_text()
            if page_text:
                text_parts.append(page_text)
    return "\n".join(text_parts)


def _find_technologies(text: str) -> list[str]:
    """Return deduplicated list of recognized tech keywords found in text."""
    found: list[str] = []
    for kw in TECH_KEYWORDS:
        # word-boundary match, case insensitive
        pattern = rf"\b{kw}\b"
        if re.search(pattern, text, re.IGNORECASE):
            # Use the canonical casing from TECH_KEYWORDS
            canonical = kw.replace("\\", "")  # unescape regex chars
            if canonical not in found:
                found.append(canonical)
    return found


def _extract_years_of_experience(text: str) -> int | None:
    """Try to find mentions like '5 years', '3+ years of experience'."""
    patterns = [
        r"(\d{1,2})\+?\s*(?:years?|ans?)\s*(?:of\s+)?(?:experience|expérience)",
        r"(?:experience|expérience)\s*(?:of\s+)?(\d{1,2})\+?\s*(?:years?|ans?)",
    ]
    max_years = None
    for pat in patterns:
        for match in re.finditer(pat, text, re.IGNORECASE):
            years = int(match.group(1))
            if max_years is None or years > max_years:
                max_years = years
    return max_years


def analyze_cv(cv_bytes: bytes) -> dict[str, Any]:
    """Parse a PDF CV and return extracted skills and metadata."""
    raw_text = _extract_text_from_pdf(cv_bytes)
    if not raw_text.strip():
        return {"error": "Could not extract any text from the PDF.", "technologies": [], "raw_text": ""}

    technologies = _find_technologies(raw_text)
    years = _extract_years_of_experience(raw_text)

    return {
        "technologies": technologies,
        "years_of_experience": years,
        "raw_text": raw_text[:3000],  # truncate for token budget
    }


def analyze_cv_text(cv_text: str) -> dict[str, Any]:
    """Analyze already-extracted CV text (used by the agent tool interface)."""
    if not cv_text.strip():
        return {"error": "CV text is empty.", "technologies": [], "raw_text": ""}

    technologies = _find_technologies(cv_text)
    years = _extract_years_of_experience(cv_text)

    return {
        "technologies": technologies,
        "years_of_experience": years,
        "raw_text": cv_text[:3000],
    }
