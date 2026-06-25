"""GitHub profile analyzer tool.

Fetches public repositories for a given username via the GitHub REST API and
returns structured data: repo list with languages, stars, forks, and detected
frameworks/technologies.
"""

from __future__ import annotations

import logging
import os
from typing import Any

import httpx

logger = logging.getLogger(__name__)

# Known framework indicators found in repo topics, descriptions, or filenames
FRAMEWORK_INDICATORS: dict[str, list[str]] = {
    # Frontend Frameworks
    "React": ["react", "next.js", "nextjs", "gatsby", "remix", "react-dom", "create-react-app", "cra"],
    "Angular": ["angular", "ng", "ngmodule", "angular-cli", "ngrx", "nx-angular"],
    "Vue": ["vue", "nuxt", "nuxtjs", "vuex", "pinia", "vuepress", "vue-router", "vite-vue"],
    "Svelte": ["svelte", "sveltekit", "svelte-kit", "svelte-store"],
    "SolidJS": ["solid", "solidjs", "solid-start", "solid-router"],
    "Astro": ["astro", "astro-build"],
    "Qwik": ["qwik", "qwik-city"],
    "Ember.js": ["ember", "emberjs", "ember-cli", "glimmer"],
    "Preact": ["preact"],
    "HTMX": ["htmx"],
    "Alpine.js": ["alpinejs", "alpine"],
    "Lit": ["lit", "lit-element", "lit-html"],

    # CSS Frameworks & UI Libraries
    "Tailwind CSS": ["tailwind", "tailwindcss", "tailwind-css"],
    "Bootstrap": ["bootstrap", "bootstrap5", "bootstrap4"],
    "Material UI": ["material-ui", "mui", "@mui", "material-design"],
    "shadcn/ui": ["shadcn", "shadcn-ui"],
    "Chakra UI": ["chakra", "chakra-ui"],
    "Ant Design": ["antd", "ant-design"],
    "Mantine": ["mantine"],
    "DaisyUI": ["daisyui"],
    "Radix UI": ["radix", "radix-ui"],
    "Styled Components": ["styled-components", "styled-system"],
    "Framer Motion": ["framer-motion", "framer"],
    "Three.js": ["threejs", "three.js", "three", "webgl"],
    "D3.js": ["d3", "d3js", "d3-force", "d3-scale"],

    # Backend Frameworks
    "Spring Boot": ["spring-boot", "spring", "springboot", "spring-mvc", "spring-security", "spring-data"],
    "Django": ["django", "django-rest-framework", "drf", "django-orm", "wagtail"],
    "Flask": ["flask", "flask-restful", "flask-sqlalchemy", "flask-login"],
    "FastAPI": ["fastapi", "fast-api", "pydantic", "uvicorn", "starlette"],
    "Express": ["express", "expressjs", "express-router", "express-middleware"],
    "Node.js": ["node", "nodejs", "node.js"],
    "NestJS": ["nestjs", "nest", "@nestjs"],
    "Fastify": ["fastify"],
    "Koa": ["koa", "koajs"],
    "Hapi.js": ["hapi", "hapijs"],
    "Rails": ["rails", "ruby-on-rails", "ror", "activerecord", "actioncable"],
    "Laravel": ["laravel", "artisan", "eloquent", "blade"],
    "Symfony": ["symfony", "doctrine"],
    "CodeIgniter": ["codeigniter"],
    "Phoenix": ["phoenix", "plug", "ecto", "liveview"],
    "Gin": ["gin", "gin-gonic"],
    "Fiber": ["fiber", "gofiber"],
    "Echo": ["echo", "labstack"],
    "Ktor": ["ktor"],
    "Quarkus": ["quarkus"],
    "Micronaut": ["micronaut"],
    "Actix": ["actix", "actix-web"],
    "Axum": ["axum", "tokio", "tower"],
    ".NET": ["dotnet", "aspnet", "blazor", "asp-net", "csharp-dotnet", "dotnet-core", "maui", "xamarin"],
    "Strapi": ["strapi"],
    "Directus": ["directus"],

    # Mobile
    "Flutter": ["flutter", "dart", "flutter-app", "flutter-widget"],
    "React Native": ["react-native", "expo", "rn", "reactnative"],
    "SwiftUI": ["swiftui", "swift", "ios", "xcode", "uikit"],
    "Jetpack Compose": ["jetpack-compose", "compose", "android", "kotlin-android"],
    "Ionic": ["ionic", "capacitor", "cordova"],

    # Databases & ORMs
    "PostgreSQL": ["postgres", "postgresql", "pg", "psql"],
    "MySQL": ["mysql", "mariadb"],
    "MongoDB": ["mongodb", "mongoose", "mongo"],
    "Redis": ["redis", "ioredis", "redis-cache"],
    "SQLite": ["sqlite", "sqlite3"],
    "Prisma": ["prisma", "prisma-client"],
    "TypeORM": ["typeorm"],
    "Sequelize": ["sequelize"],
    "Drizzle ORM": ["drizzle", "drizzle-orm"],
    "SQLAlchemy": ["sqlalchemy", "alembic"],
    "Supabase": ["supabase"],
    "Firebase": ["firebase", "firestore", "firebase-auth"],
    "Elasticsearch": ["elasticsearch", "elastic", "opensearch"],
    "Cassandra": ["cassandra"],
    "DynamoDB": ["dynamodb", "aws-dynamodb"],
    "Neo4j": ["neo4j", "cypher", "graph-database"],
    "ClickHouse": ["clickhouse"],
    "Snowflake": ["snowflake", "snowflake-sql"],

    # DevOps & Infrastructure
    "Docker": ["docker", "dockerfile", "docker-compose", "dockerhub", "containerization"],
    "Kubernetes": ["kubernetes", "k8s", "helm", "kubectl", "kustomize", "k3s"],
    "Terraform": ["terraform", "hcl", "terraform-module", "infrastructure-as-code", "iac"],
    "Ansible": ["ansible", "ansible-playbook", "ansible-role"],
    "ArgoCD": ["argocd", "argo-cd", "argo-workflows"],
    "Prometheus": ["prometheus", "alertmanager", "prom"],
    "Grafana": ["grafana", "loki", "tempo"],
    "Istio": ["istio", "service-mesh"],
    "Pulumi": ["pulumi"],
    "GitHub Actions": ["github-actions", "gh-actions", "workflows"],
    "GitLab CI": ["gitlab-ci", "gitlab-runner", ".gitlab-ci"],
    "CircleCI": ["circleci"],
    "Jenkins": ["jenkins", "jenkinsfile"],
    "Nginx": ["nginx", "nginx-config"],
    "Vault": ["vault", "hashicorp-vault"],

    # Cloud
    "AWS": ["aws", "amazon-web-services", "cdk", "cloudformation", "lambda", "s3", "ec2", "eks", "ecs"],
    "Azure": ["azure", "az", "azure-devops", "azure-functions", "aks"],
    "GCP": ["gcp", "google-cloud", "gke", "cloud-run", "bigquery", "pubsub"],
    "Vercel": ["vercel"],
    "Netlify": ["netlify"],
    "Cloudflare": ["cloudflare", "cloudflare-workers", "wrangler"],
    "Serverless": ["serverless", "serverless-framework", "sls"],

    # AI & ML
    "TensorFlow": ["tensorflow", "tf", "keras", "tensorflow-lite", "tfjs"],
    "PyTorch": ["pytorch", "torch", "torchvision", "lightning", "pytorch-lightning"],
    "Scikit-learn": ["scikit-learn", "sklearn"],
    "Hugging Face": ["huggingface", "hugging-face", "transformers", "diffusers", "datasets"],
    "LangChain": ["langchain", "langchain-community", "langGraph", "langgraph"],
    "LlamaIndex": ["llamaindex", "llama-index", "llama_index"],
    "OpenAI API": ["openai", "gpt", "chatgpt", "gpt-4", "gpt-3"],
    "Anthropic API": ["anthropic", "claude"],
    "Ollama": ["ollama"],
    "MLflow": ["mlflow"],
    "Apache Spark": ["spark", "pyspark", "apache-spark"],
    "Apache Airflow": ["airflow", "apache-airflow", "dags"],
    "Pandas": ["pandas", "dataframe"],
    "NumPy": ["numpy", "np"],
    "Weights & Biases": ["wandb", "weights-biases"],
    "Ray": ["ray", "ray-tune", "ray-serve"],
    "Pinecone": ["pinecone"],
    "Weaviate": ["weaviate"],
    "Qdrant": ["qdrant"],
    "ChromaDB": ["chroma", "chromadb"],

    # Messaging & Streaming
    "Kafka": ["kafka", "apache-kafka", "confluent", "kafka-consumer"],
    "RabbitMQ": ["rabbitmq", "amqp"],
    "NATS": ["nats", "nats-server"],
    "Celery": ["celery", "celery-beat", "celery-worker"],

    # Testing
    "Jest": ["jest", "jest-config"],
    "Vitest": ["vitest"],
    "Cypress": ["cypress", "cypress-io"],
    "Playwright": ["playwright", "microsoft-playwright"],
    "Selenium": ["selenium", "webdriver"],
    "Pytest": ["pytest", "pytest-django", "pytest-asyncio"],

    # Build Tools & Runtimes
    "Vite": ["vite", "vite-config", "vitest"],
    "Webpack": ["webpack", "webpack-config"],
    "Turborepo": ["turborepo", "turbo"],
    "Nx": ["nx", "nrwl"],
    "Bun": ["bun", "bunjs"],
    "Deno": ["deno"],

    # APIs & Protocols
    "GraphQL": ["graphql", "apollo", "hasura", "gql", "relay", "graphene"],
    "gRPC": ["grpc", "protobuf", "proto"],
    "tRPC": ["trpc", "t3-stack"],
    "WebSockets": ["websocket", "websockets", "socket.io", "ws"],
    "OpenAPI": ["openapi", "swagger", "swagger-ui"],

    # CMS & E-commerce
    "WordPress": ["wordpress", "wp", "woocommerce"],
    "Shopify": ["shopify", "liquid"],
    "Contentful": ["contentful"],
    "Sanity": ["sanity", "sanity-io"],
    "Storyblok": ["storyblok"],
    "Payload CMS": ["payload", "payload-cms"],

    # Blockchain & Web3
    "Ethereum": ["ethereum", "solidity", "hardhat", "foundry", "truffle", "web3", "ethers"],
    "Solana": ["solana", "anchor", "web3js-solana"],
}


async def analyze_github(username: str) -> dict[str, Any]:
    """Fetch GitHub repos and return a structured analysis summary."""
    token = os.getenv("GITHUB_TOKEN", "")
    headers: dict[str, str] = {"Accept": "application/vnd.github+json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    base_url = f"https://api.github.com/users/{username}"

    async with httpx.AsyncClient(timeout=30, headers=headers) as client:
        # Fetch user profile
        profile_resp = await client.get(base_url)
        if profile_resp.status_code == 404:
            return {"error": f"GitHub user '{username}' not found."}
        profile_resp.raise_for_status()
        profile = profile_resp.json()

        # Fetch repos (up to 100, sorted by most recently updated)
        repos_resp = await client.get(
            f"{base_url}/repos",
            params={"per_page": 100, "sort": "updated", "type": "owner"},
        )
        repos_resp.raise_for_status()
        repos_raw = repos_resp.json()

    # Aggregate languages
    language_stats: dict[str, int] = {}
    repo_summaries: list[dict[str, Any]] = []
    detected_frameworks: set[str] = set()

    for repo in repos_raw:
        if repo.get("fork"):
            continue  # skip forks
        lang = repo.get("language")
        if lang:
            language_stats[lang] = language_stats.get(lang, 0) + 1

        # Detect frameworks from topics + description
        topics: list[str] = repo.get("topics") or []
        description = (repo.get("description") or "").lower()
        name_lower = repo.get("name", "").lower()
        searchable = " ".join(topics) + " " + description + " " + name_lower

        for framework, keywords in FRAMEWORK_INDICATORS.items():
            if any(kw in searchable for kw in keywords):
                detected_frameworks.add(framework)

        repo_summaries.append(
            {
                "name": repo["name"],
                "language": lang,
                "stars": repo.get("stargazers_count", 0),
                "forks": repo.get("forks_count", 0),
                "description": repo.get("description", ""),
                "topics": topics,
                "updated_at": repo.get("updated_at", ""),
            }
        )

    # Sort by stars descending
    repo_summaries.sort(key=lambda r: r["stars"], reverse=True)

    return {
        "username": username,
        "name": profile.get("name", username),
        "bio": profile.get("bio", ""),
        "public_repos": profile.get("public_repos", 0),
        "followers": profile.get("followers", 0),
        "repositories_analyzed": len(repo_summaries),
        "language_stats": language_stats,
        "detected_frameworks": sorted(detected_frameworks),
        "top_repos": repo_summaries[:15],  # return top 15 for brevity
    }
