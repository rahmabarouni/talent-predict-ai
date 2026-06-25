-- Initialize databases for TalentPredict Stack
-- This script runs automatically when PostgreSQL container starts

-- Create n8n database (will fail silently if exists)
CREATE DATABASE n8n;

-- Grant privileges to postgres user on n8n database
GRANT ALL PRIVILEGES ON DATABASE n8n TO postgres;

-- Connect to n8n database and grant schema privileges
\c n8n
GRANT ALL PRIVILEGES ON SCHEMA public TO postgres;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON TABLES TO postgres;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL PRIVILEGES ON SEQUENCES TO postgres;
