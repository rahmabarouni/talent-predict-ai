"""Job matching service.

Compares a candidate's scored skills against predefined job requirement profiles
and calculates a job_match_score (0–100) with improvement recommendations.
"""

from __future__ import annotations

from typing import Any


# Predefined full-stack developer job requirements with minimum expected scores
JOB_PROFILES: dict[str, list[dict[str, Any]]] = {
    "Full-Stack Developer": [
        {"name": "JavaScript", "min_level": "Intermediate", "weight": 1.2},
        {"name": "TypeScript", "min_level": "Intermediate", "weight": 1.0},
        {"name": "React", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Angular", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Node.js", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Python", "min_level": "Beginner", "weight": 0.8},
        {"name": "Java", "min_level": "Beginner", "weight": 0.8},
        {"name": "SQL", "min_level": "Intermediate", "weight": 0.9},
        {"name": "PostgreSQL", "min_level": "Beginner", "weight": 0.7},
        {"name": "Docker", "min_level": "Beginner", "weight": 0.8},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.9},
        {"name": "REST", "min_level": "Intermediate", "weight": 0.9},
        {"name": "CI/CD", "min_level": "Beginner", "weight": 0.6},
        {"name": "AWS", "min_level": "Beginner", "weight": 0.6},
    ],
    "Backend Developer": [
        {"name": "Java", "min_level": "Advanced", "weight": 1.3},
        {"name": "Spring Boot", "min_level": "Intermediate", "weight": 1.2},
        {"name": "Python", "min_level": "Intermediate", "weight": 1.0},
        {"name": "SQL", "min_level": "Advanced", "weight": 1.1},
        {"name": "PostgreSQL", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Docker", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Kubernetes", "min_level": "Beginner", "weight": 0.7},
        {"name": "REST", "min_level": "Advanced", "weight": 1.0},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
        {"name": "CI/CD", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Microservices", "min_level": "Intermediate", "weight": 0.9},
    ],
    "Frontend Developer": [
        {"name": "JavaScript", "min_level": "Advanced", "weight": 1.3},
        {"name": "TypeScript", "min_level": "Intermediate", "weight": 1.2},
        {"name": "React", "min_level": "Advanced", "weight": 1.2},
        {"name": "Angular", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Vue", "min_level": "Intermediate", "weight": 0.9},
        {"name": "HTML", "min_level": "Advanced", "weight": 1.0},
        {"name": "CSS", "min_level": "Advanced", "weight": 1.0},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
        {"name": "REST", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Figma", "min_level": "Beginner", "weight": 0.6},
    ],
    "Mobile Developer (iOS)": [
        {"name": "Swift", "min_level": "Advanced", "weight": 1.4},
        {"name": "SwiftUI", "min_level": "Intermediate", "weight": 1.2},
        {"name": "Objective-C", "min_level": "Beginner", "weight": 0.7},
        {"name": "Xcode", "min_level": "Advanced", "weight": 1.1},
        {"name": "REST", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Figma", "min_level": "Beginner", "weight": 0.6},
        {"name": "CI/CD", "min_level": "Beginner", "weight": 0.6},
    ],
    "Mobile Developer (Android)": [
        {"name": "Kotlin", "min_level": "Advanced", "weight": 1.4},
        {"name": "Jetpack Compose", "min_level": "Intermediate", "weight": 1.2},
        {"name": "Java", "min_level": "Intermediate", "weight": 0.9},
        {"name": "REST", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Figma", "min_level": "Beginner", "weight": 0.6},
        {"name": "CI/CD", "min_level": "Beginner", "weight": 0.6},
        {"name": "Firebase", "min_level": "Beginner", "weight": 0.7},
    ],
    "Mobile Developer (Cross-Platform)": [
        {"name": "React Native", "min_level": "Advanced", "weight": 1.3},
        {"name": "Flutter", "min_level": "Advanced", "weight": 1.3},
        {"name": "Dart", "min_level": "Intermediate", "weight": 1.1},
        {"name": "JavaScript", "min_level": "Intermediate", "weight": 1.0},
        {"name": "TypeScript", "min_level": "Intermediate", "weight": 0.9},
        {"name": "REST", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Firebase", "min_level": "Beginner", "weight": 0.7},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Figma", "min_level": "Beginner", "weight": 0.6},
    ],
    "DevOps Engineer": [
        {"name": "Docker", "min_level": "Advanced", "weight": 1.3},
        {"name": "Kubernetes", "min_level": "Advanced", "weight": 1.3},
        {"name": "Terraform", "min_level": "Intermediate", "weight": 1.2},
        {"name": "AWS", "min_level": "Intermediate", "weight": 1.1},
        {"name": "CI/CD", "min_level": "Advanced", "weight": 1.2},
        {"name": "Linux", "min_level": "Advanced", "weight": 1.1},
        {"name": "Bash", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Python", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Ansible", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Prometheus", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Grafana", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Nginx", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Helm", "min_level": "Intermediate", "weight": 0.9},
    ],
    "Cloud Engineer": [
        {"name": "AWS", "min_level": "Advanced", "weight": 1.4},
        {"name": "Azure", "min_level": "Intermediate", "weight": 1.1},
        {"name": "GCP", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Terraform", "min_level": "Advanced", "weight": 1.3},
        {"name": "Kubernetes", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Docker", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Python", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Bash", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Networking", "min_level": "Intermediate", "weight": 1.0},
        {"name": "CI/CD", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Linux", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Serverless", "min_level": "Intermediate", "weight": 0.9},
    ],
    "Site Reliability Engineer": [
        {"name": "Linux", "min_level": "Advanced", "weight": 1.3},
        {"name": "Python", "min_level": "Advanced", "weight": 1.2},
        {"name": "Go", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Kubernetes", "min_level": "Advanced", "weight": 1.3},
        {"name": "Docker", "min_level": "Advanced", "weight": 1.2},
        {"name": "Prometheus", "min_level": "Advanced", "weight": 1.2},
        {"name": "Grafana", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Terraform", "min_level": "Intermediate", "weight": 1.0},
        {"name": "CI/CD", "min_level": "Advanced", "weight": 1.1},
        {"name": "Bash", "min_level": "Advanced", "weight": 1.1},
        {"name": "Distributed Systems", "min_level": "Intermediate", "weight": 1.1},
        {"name": "OpenTelemetry", "min_level": "Intermediate", "weight": 0.9},
    ],
    "Data Engineer": [
        {"name": "Python", "min_level": "Advanced", "weight": 1.3},
        {"name": "SQL", "min_level": "Advanced", "weight": 1.3},
        {"name": "Apache Spark", "min_level": "Intermediate", "weight": 1.2},
        {"name": "Apache Airflow", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Kafka", "min_level": "Intermediate", "weight": 1.0},
        {"name": "PostgreSQL", "min_level": "Intermediate", "weight": 0.9},
        {"name": "AWS", "min_level": "Intermediate", "weight": 1.0},
        {"name": "BigQuery", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Snowflake", "min_level": "Intermediate", "weight": 0.9},
        {"name": "dbt", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Docker", "min_level": "Beginner", "weight": 0.7},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Pandas", "min_level": "Advanced", "weight": 1.1},
        {"name": "NumPy", "min_level": "Intermediate", "weight": 0.8},
    ],
    "Data Scientist": [
        {"name": "Python", "min_level": "Advanced", "weight": 1.4},
        {"name": "Machine Learning", "min_level": "Advanced", "weight": 1.4},
        {"name": "Scikit-learn", "min_level": "Advanced", "weight": 1.2},
        {"name": "Pandas", "min_level": "Advanced", "weight": 1.2},
        {"name": "NumPy", "min_level": "Advanced", "weight": 1.1},
        {"name": "SQL", "min_level": "Intermediate", "weight": 1.0},
        {"name": "TensorFlow", "min_level": "Intermediate", "weight": 0.9},
        {"name": "PyTorch", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Matplotlib", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Jupyter", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Statistics", "min_level": "Advanced", "weight": 1.2},
        {"name": "R", "min_level": "Beginner", "weight": 0.7},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.7},
    ],
    "Machine Learning Engineer": [
        {"name": "Python", "min_level": "Advanced", "weight": 1.4},
        {"name": "PyTorch", "min_level": "Advanced", "weight": 1.3},
        {"name": "TensorFlow", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Machine Learning", "min_level": "Advanced", "weight": 1.4},
        {"name": "Deep Learning", "min_level": "Advanced", "weight": 1.3},
        {"name": "Scikit-learn", "min_level": "Advanced", "weight": 1.1},
        {"name": "MLflow", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Docker", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Kubernetes", "min_level": "Beginner", "weight": 0.7},
        {"name": "AWS", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Apache Spark", "min_level": "Beginner", "weight": 0.7},
        {"name": "SQL", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
        {"name": "ONNX", "min_level": "Beginner", "weight": 0.7},
    ],
    "AI Engineer": [
        {"name": "Python", "min_level": "Advanced", "weight": 1.4},
        {"name": "LLMs", "min_level": "Advanced", "weight": 1.4},
        {"name": "LangChain", "min_level": "Intermediate", "weight": 1.2},
        {"name": "RAG", "min_level": "Intermediate", "weight": 1.2},
        {"name": "OpenAI API", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Vector Databases", "min_level": "Intermediate", "weight": 1.1},
        {"name": "PyTorch", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Hugging Face", "min_level": "Intermediate", "weight": 1.1},
        {"name": "FastAPI", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Docker", "min_level": "Intermediate", "weight": 0.9},
        {"name": "AWS", "min_level": "Beginner", "weight": 0.7},
        {"name": "Prompt Engineering", "min_level": "Advanced", "weight": 1.2},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
    ],
    "Data Analyst": [
        {"name": "SQL", "min_level": "Advanced", "weight": 1.4},
        {"name": "Python", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Pandas", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Excel", "min_level": "Advanced", "weight": 1.1},
        {"name": "Tableau", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Power BI", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Statistics", "min_level": "Intermediate", "weight": 1.1},
        {"name": "PostgreSQL", "min_level": "Intermediate", "weight": 0.9},
        {"name": "BigQuery", "min_level": "Beginner", "weight": 0.8},
        {"name": "Matplotlib", "min_level": "Beginner", "weight": 0.7},
        {"name": "Google Analytics", "min_level": "Beginner", "weight": 0.6},
        {"name": "Git", "min_level": "Beginner", "weight": 0.6},
    ],
    "Security Engineer": [
        {"name": "Linux", "min_level": "Advanced", "weight": 1.3},
        {"name": "Python", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Penetration Testing", "min_level": "Advanced", "weight": 1.4},
        {"name": "Networking", "min_level": "Advanced", "weight": 1.3},
        {"name": "OWASP", "min_level": "Advanced", "weight": 1.2},
        {"name": "Cryptography", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Bash", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Docker", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Kubernetes", "min_level": "Beginner", "weight": 0.7},
        {"name": "SIEM", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Zero Trust", "min_level": "Intermediate", "weight": 1.0},
        {"name": "DevSecOps", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Cloud Security", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.7},
    ],
    "Blockchain Developer": [
        {"name": "Solidity", "min_level": "Advanced", "weight": 1.4},
        {"name": "Ethereum", "min_level": "Advanced", "weight": 1.3},
        {"name": "Web3.js", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Ethers.js", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Hardhat", "min_level": "Intermediate", "weight": 1.1},
        {"name": "JavaScript", "min_level": "Intermediate", "weight": 1.0},
        {"name": "TypeScript", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Rust", "min_level": "Beginner", "weight": 0.8},
        {"name": "Cryptography", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Smart Contracts", "min_level": "Advanced", "weight": 1.3},
        {"name": "IPFS", "min_level": "Beginner", "weight": 0.7},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
    ],
    "Embedded Systems Engineer": [
        {"name": "C", "min_level": "Advanced", "weight": 1.4},
        {"name": "C++", "min_level": "Advanced", "weight": 1.3},
        {"name": "Assembly", "min_level": "Intermediate", "weight": 1.1},
        {"name": "RTOS", "min_level": "Intermediate", "weight": 1.2},
        {"name": "Linux", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Bash", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Debugging", "min_level": "Advanced", "weight": 1.2},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Python", "min_level": "Beginner", "weight": 0.6},
        {"name": "CMake", "min_level": "Intermediate", "weight": 0.9},
    ],
    "Game Developer": [
        {"name": "C++", "min_level": "Advanced", "weight": 1.4},
        {"name": "C#", "min_level": "Advanced", "weight": 1.3},
        {"name": "Unity", "min_level": "Advanced", "weight": 1.3},
        {"name": "Unreal Engine", "min_level": "Intermediate", "weight": 1.2},
        {"name": "Linear Algebra", "min_level": "Advanced", "weight": 1.2},
        {"name": "Physics Simulation", "min_level": "Intermediate", "weight": 1.0},
        {"name": "OpenGL", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Vulkan", "min_level": "Beginner", "weight": 0.8},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Blender", "min_level": "Beginner", "weight": 0.6},
        {"name": "Lua", "min_level": "Beginner", "weight": 0.6},
    ],
    "Platform Engineer": [
        {"name": "Kubernetes", "min_level": "Advanced", "weight": 1.4},
        {"name": "Terraform", "min_level": "Advanced", "weight": 1.3},
        {"name": "Go", "min_level": "Intermediate", "weight": 1.2},
        {"name": "Python", "min_level": "Intermediate", "weight": 1.0},
        {"name": "AWS", "min_level": "Advanced", "weight": 1.2},
        {"name": "Helm", "min_level": "Advanced", "weight": 1.2},
        {"name": "CI/CD", "min_level": "Advanced", "weight": 1.2},
        {"name": "Linux", "min_level": "Advanced", "weight": 1.1},
        {"name": "Docker", "min_level": "Advanced", "weight": 1.2},
        {"name": "ArgoCD", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Prometheus", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Grafana", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Istio", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Vault", "min_level": "Intermediate", "weight": 0.9},
    ],
    "QA Engineer": [
        {"name": "Selenium", "min_level": "Advanced", "weight": 1.3},
        {"name": "Playwright", "min_level": "Intermediate", "weight": 1.2},
        {"name": "Cypress", "min_level": "Intermediate", "weight": 1.2},
        {"name": "Jest", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Python", "min_level": "Intermediate", "weight": 1.0},
        {"name": "JavaScript", "min_level": "Intermediate", "weight": 0.9},
        {"name": "REST", "min_level": "Intermediate", "weight": 1.0},
        {"name": "SQL", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Jira", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Git", "min_level": "Intermediate", "weight": 0.8},
        {"name": "CI/CD", "min_level": "Beginner", "weight": 0.7},
        {"name": "Postman", "min_level": "Intermediate", "weight": 0.9},
        {"name": "BDD", "min_level": "Intermediate", "weight": 0.9},
    ],
    "UI/UX Designer": [
        {"name": "Figma", "min_level": "Advanced", "weight": 1.4},
        {"name": "Adobe XD", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Sketch", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Prototyping", "min_level": "Advanced", "weight": 1.3},
        {"name": "User Research", "min_level": "Advanced", "weight": 1.3},
        {"name": "HTML", "min_level": "Beginner", "weight": 0.7},
        {"name": "CSS", "min_level": "Beginner", "weight": 0.7},
        {"name": "Accessibility", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Design Systems", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Miro", "min_level": "Intermediate", "weight": 0.8},
        {"name": "Zeplin", "min_level": "Beginner", "weight": 0.7},
    ],
    "Database Administrator": [
        {"name": "PostgreSQL", "min_level": "Advanced", "weight": 1.4},
        {"name": "MySQL", "min_level": "Advanced", "weight": 1.3},
        {"name": "SQL", "min_level": "Advanced", "weight": 1.4},
        {"name": "Oracle DB", "min_level": "Intermediate", "weight": 1.1},
        {"name": "SQL Server", "min_level": "Intermediate", "weight": 1.1},
        {"name": "MongoDB", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Redis", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Linux", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Bash", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Backup & Recovery", "min_level": "Advanced", "weight": 1.2},
        {"name": "Performance Tuning", "min_level": "Advanced", "weight": 1.3},
        {"name": "Replication", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Docker", "min_level": "Beginner", "weight": 0.6},
    ],
    "Technical Lead": [
        {"name": "System Design", "min_level": "Advanced", "weight": 1.4},
        {"name": "Code Review", "min_level": "Advanced", "weight": 1.3},
        {"name": "Agile", "min_level": "Advanced", "weight": 1.2},
        {"name": "Microservices", "min_level": "Advanced", "weight": 1.2},
        {"name": "Docker", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Kubernetes", "min_level": "Intermediate", "weight": 1.0},
        {"name": "CI/CD", "min_level": "Advanced", "weight": 1.1},
        {"name": "Git", "min_level": "Advanced", "weight": 1.1},
        {"name": "REST", "min_level": "Advanced", "weight": 1.0},
        {"name": "SQL", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Cloud Architecture", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Mentoring", "min_level": "Advanced", "weight": 1.2},
        {"name": "Jira", "min_level": "Intermediate", "weight": 0.8},
    ],
    "Solution Architect": [
        {"name": "System Design", "min_level": "Advanced", "weight": 1.4},
        {"name": "Cloud Architecture", "min_level": "Advanced", "weight": 1.4},
        {"name": "AWS", "min_level": "Advanced", "weight": 1.3},
        {"name": "Microservices", "min_level": "Advanced", "weight": 1.2},
        {"name": "Domain-Driven Design", "min_level": "Advanced", "weight": 1.2},
        {"name": "API Gateway", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Security", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Terraform", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Event-Driven Architecture", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Kafka", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Networking", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Cost Optimization", "min_level": "Intermediate", "weight": 0.9},
    ],
    "Scrum Master": [
        {"name": "Scrum", "min_level": "Advanced", "weight": 1.5},
        {"name": "Agile", "min_level": "Advanced", "weight": 1.4},
        {"name": "Kanban", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Jira", "min_level": "Advanced", "weight": 1.2},
        {"name": "Confluence", "min_level": "Advanced", "weight": 1.1},
        {"name": "Coaching", "min_level": "Advanced", "weight": 1.3},
        {"name": "Conflict Resolution", "min_level": "Advanced", "weight": 1.2},
        {"name": "Stakeholder Management", "min_level": "Intermediate", "weight": 1.1},
        {"name": "Lean", "min_level": "Intermediate", "weight": 0.9},
        {"name": "Miro", "min_level": "Intermediate", "weight": 0.8},
    ],
    "Product Manager": [
        {"name": "Product Strategy", "min_level": "Advanced", "weight": 1.5},
        {"name": "Agile", "min_level": "Advanced", "weight": 1.3},
        {"name": "User Research", "min_level": "Advanced", "weight": 1.3},
        {"name": "Roadmapping", "min_level": "Advanced", "weight": 1.3},
        {"name": "Jira", "min_level": "Advanced", "weight": 1.1},
        {"name": "Data Analysis", "min_level": "Intermediate", "weight": 1.1},
        {"name": "SQL", "min_level": "Beginner", "weight": 0.7},
        {"name": "A/B Testing", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Figma", "min_level": "Beginner", "weight": 0.7},
        {"name": "Stakeholder Management", "min_level": "Advanced", "weight": 1.2},
        {"name": "OKRs", "min_level": "Intermediate", "weight": 1.0},
        {"name": "Mixpanel", "min_level": "Beginner", "weight": 0.7},
        {"name": "Amplitude", "min_level": "Beginner", "weight": 0.7},
    ],
}
_LEVEL_ORDER = {"Beginner": 1, "Intermediate": 2, "Advanced": 3, "Expert": 4}


def _level_value(level: str) -> int:
    return _LEVEL_ORDER.get(level, 0)


def match_job(
    skills: list[dict[str, Any]],
    experience_score: int,
    profile_name: str = "Full-Stack Developer",
) -> dict[str, Any]:
    """Compare candidate skills against a job profile.

    Returns:
        {
            "job_profile": str,
            "job_match_score": int (0-100),
            "matched_skills": [...],
            "missing_skills": [...],
            "recommendations": [str, ...],
        }
    """
    profile = JOB_PROFILES.get(profile_name, JOB_PROFILES["Full-Stack Developer"])
    skill_lookup: dict[str, dict[str, Any]] = {}
    for s in skills:
        skill_lookup[s["name"].lower()] = s

    total_weight = sum(req["weight"] for req in profile)
    earned_weight = 0.0
    matched: list[str] = []
    missing: list[str] = []
    recommendations: list[str] = []

    for req in profile:
        candidate_skill = skill_lookup.get(req["name"].lower())
        if candidate_skill:
            matched.append(req["name"])
            candidate_level = _level_value(candidate_skill.get("level", "Beginner"))
            required_level = _level_value(req["min_level"])
            if candidate_level >= required_level:
                earned_weight += req["weight"]
            else:
                # Partial credit
                ratio = candidate_level / max(required_level, 1)
                earned_weight += req["weight"] * ratio
                recommendations.append(
                    f"Improve {req['name']} from {candidate_skill.get('level', 'Beginner')} "
                    f"to {req['min_level']} level"
                )
        else:
            missing.append(req["name"])
            recommendations.append(f"Learn {req['name']} (required at {req['min_level']} level)")

    # Bonus for extra skills not in the profile
    extra_skills = [
        s["name"]
        for s in skills
        if s["name"].lower() not in {r["name"].lower() for r in profile}
    ]
    extra_bonus = min(10, len(extra_skills) * 1.5)

    raw_score = (earned_weight / max(total_weight, 1)) * 85 + extra_bonus
    # Factor in experience score slightly
    job_match_score = min(100, round(raw_score * 0.9 + experience_score * 0.1))

    # Add general recommendations
    if not any("Docker" in r for r in recommendations) and "docker" not in skill_lookup:
        recommendations.append("Add Docker/containerization skills to strengthen DevOps capabilities")
    if not any("backend" in r.lower() for r in recommendations) and len(matched) < len(profile) * 0.5:
        recommendations.append("Add more backend projects to demonstrate full-stack capability")

    return {
        "job_profile": profile_name,
        "job_match_score": job_match_score,
        "matched_skills": matched,
        "missing_skills": missing,
        "recommendations": recommendations[:8],  # cap at 8
    }
