#!/bin/bash

# TalentPredict Stack Startup Script
# This script automates the startup of the complete TalentPredict stack

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Banner
echo -e "${BLUE}"
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║                    TalentPredict Stack Starter                 ║"
echo "║              Automated Setup & Launch Script v1.0              ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Check if Docker is installed
echo -e "${YELLOW}[1/5]${NC} Checking Docker installation..."
if ! command -v docker &> /dev/null; then
  echo -e "${RED}✗ Docker is not installed!${NC}"
  echo -e "${YELLOW}Please install Docker Desktop from https://www.docker.com/products/docker-desktop${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Docker is installed${NC}"

# Check if Docker Compose is available
echo -e "${YELLOW}[2/5]${NC} Checking Docker Compose..."
if ! command -v docker-compose &> /dev/null; then
  echo -e "${RED}✗ Docker Compose is not installed!${NC}"
  echo -e "${YELLOW}Docker Desktop includes Docker Compose. Please restart Docker Desktop.${NC}"
  exit 1
fi
COMPOSE_VERSION=$(docker-compose --version)
echo -e "${GREEN}✓ Docker Compose is available${NC}"
echo -e "   Version: $COMPOSE_VERSION"

# Check if Docker daemon is running
echo -e "${YELLOW}[3/5]${NC} Checking Docker daemon..."
if ! docker info > /dev/null 2>&1; then
  echo -e "${RED}✗ Docker daemon is not running!${NC}"
  echo -e "${YELLOW}Please start Docker Desktop.${NC}"
  exit 1
fi
echo -e "${GREEN}✓ Docker daemon is running${NC}"

# Create .env file if it doesn't exist
echo -e "${YELLOW}[4/5]${NC} Checking environment configuration..."
if [ ! -f .env ]; then
  if [ -f .env.example ]; then
    echo -e "${YELLOW}Creating .env from .env.example...${NC}"
    cp .env.example .env
    echo -e "${YELLOW}⚠ Please edit .env with your API keys and credentials${NC}"
    echo -e "${BLUE}   Important keys to configure:${NC}"
    echo -e "   • N8N_ENCRYPTION_KEY (generate: openssl rand -hex 16)"
    echo -e "   • ANTHROPIC_API_KEY (for CV analysis)"
    echo -e "   • GITHUB_TOKEN (for profile analysis)"
    echo ""
  else
    echo -e "${YELLOW}⚠ No .env.example found. Creating minimal .env...${NC}"
    cat > .env << 'EOF'
DB_PASSWORD=11111111
JWT_SECRET=TalentPredictSecretKeyForJWTAuthenticationMustBeLongEnough256Bits
N8N_ENCRYPTION_KEY=MinimumSixteenCharactersKey
ANTHROPIC_API_KEY=
GITHUB_TOKEN=
EOF
  fi
  echo -e "${GREEN}✓ Environment file created${NC}"
else
  echo -e "${GREEN}✓ Environment file exists${NC}"
fi

# Start Docker containers
echo -e "${YELLOW}[5/5]${NC} Starting Docker containers..."
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

docker-compose up -d --build

echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"

# Display startup status
echo ""
echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║              🎉 TalentPredict Stack Started! 🎉                ║${NC}"
echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
echo ""

# Wait a moment for containers to start
echo -e "${YELLOW}Waiting for services to be ready...${NC}"
sleep 5

# Display service URLs
echo -e "${GREEN}📊 Service URLs:${NC}"
echo -e "  • ${BLUE}Frontend:${NC}           http://localhost:4200"
echo -e "  • ${BLUE}Backend API:${NC}        http://localhost:8081/api"
echo -e "  • ${BLUE}n8n UI:${NC}             http://localhost:5678"
echo -e "  • ${BLUE}AI Service:${NC}         http://localhost:8000"
echo -e "  • ${BLUE}PDF Extractor:${NC}      http://localhost:3001/health"
echo -e "  • ${BLUE}PostgreSQL:${NC}         localhost:5432"
echo ""

# Display container status
echo -e "${GREEN}📦 Container Status:${NC}"
docker-compose ps

echo ""
echo -e "${YELLOW}Useful Commands:${NC}"
echo -e "  • View logs:          ${BLUE}docker-compose logs -f${NC}"
echo -e "  • View service logs:  ${BLUE}docker-compose logs -f <service>${NC}"
echo -e "  • Stop services:      ${BLUE}docker-compose down${NC}"
echo -e "  • Restart services:   ${BLUE}docker-compose restart${NC}"
echo -e "  • Rebuild containers: ${BLUE}docker-compose up -d --build${NC}"
echo ""

# Test services
echo -e "${YELLOW}Running service health checks...${NC}"
echo ""

# Test PDF Extractor
echo -n "  Testing PDF Extractor... "
if curl -s http://localhost:3001/health > /dev/null 2>&1; then
  echo -e "${GREEN}✓${NC}"
else
  echo -e "${YELLOW}⏳${NC} (still starting)"
fi

# Test Backend
echo -n "  Testing Backend... "
if curl -s http://localhost:8081/actuator/health > /dev/null 2>&1 2>&1; then
  echo -e "${GREEN}✓${NC}"
else
  echo -e "${YELLOW}⏳${NC} (still starting)"
fi

# Test Frontend
echo -n "  Testing Frontend... "
if curl -s http://localhost:4200 > /dev/null 2>&1; then
  echo -e "${GREEN}✓${NC}"
else
  echo -e "${YELLOW}⏳${NC} (still starting)"
fi

echo ""
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
echo -e "${YELLOW}💡 First Time Setup:${NC}"
echo "   1. Go to: http://localhost:5678"
echo "   2. Sign up for n8n account"
echo "   3. Import workflows from n8n-workflows/ folder"
echo "   4. Configure your API keys in .env"
echo ""
echo -e "${YELLOW}📖 For more information, see README.md${NC}"
echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
