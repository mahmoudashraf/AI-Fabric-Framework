#!/bin/bash

# EasyLuxury Docker Deployment Script
# Deploys frontend + backend + Supabase database

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}🐳 EasyLuxury Docker Deployment${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# Check if .env file exists
if [ ! -f ".env" ]; then
    echo -e "${YELLOW}⚠️  .env file not found!${NC}"
    echo "Creating .env from .env.example..."
    cp .env.example .env
    echo -e "${RED}❌ Please edit .env file with your actual Supabase credentials${NC}"
    echo "   Required: SUPABASE_URL, SUPABASE_API_KEY, SUPABASE_SERVICE_ROLE_KEY"
    echo "   And: DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD"
    exit 1
fi

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running. Please start Docker first.${NC}"
    exit 1
fi

echo -e "${BLUE}Step 1/4:${NC} Building Docker images..."
docker-compose build

echo -e "${BLUE}Step 2/4:${NC} Starting services..."
docker-compose up -d

echo -e "${BLUE}Step 3/4:${NC} Waiting for services to be healthy..."
sleep 10

# Check service health
echo -e "${BLUE}Step 4/4:${NC} Checking service status..."

# Check backend health
if curl -f http://localhost:8080/actuator/health > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Backend is healthy${NC}"
else
    echo -e "${YELLOW}⚠️  Backend is starting up...${NC}"
fi

# Check frontend health
if curl -f http://localhost:3000 > /dev/null 2>&1; then
    echo -e "${GREEN}✅ Frontend is healthy${NC}"
else
    echo -e "${YELLOW}⚠️  Frontend is starting up...${NC}"
fi

echo ""
echo -e "${GREEN}🎉 Deployment completed!${NC}"
echo ""
echo -e "${BLUE}Services:${NC}"
echo -e "  Frontend: ${GREEN}http://localhost:3000${NC}"
echo -e "  Backend:  ${GREEN}http://localhost:8080${NC}"
echo -e "  MinIO:    ${GREEN}http://localhost:9000${NC} (admin: minioadmin/minioadmin)"
echo ""
echo -e "${BLUE}Useful commands:${NC}"
echo -e "  View logs:    ${YELLOW}docker-compose logs -f${NC}"
echo -e "  Stop services: ${YELLOW}docker-compose down${NC}"
echo -e "  Restart:      ${YELLOW}docker-compose restart${NC}"
echo ""
