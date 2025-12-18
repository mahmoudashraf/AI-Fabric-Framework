#!/bin/bash

# EasyLuxury Docker Deployment Script - Development
# Deploys frontend + backend + Supabase database in DEVELOPMENT mode

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}🐳 EasyLuxury Development Docker${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# Check if .env.development file exists
if [ ! -f ".env.development" ]; then
    echo -e "${RED}❌ .env.development file not found!${NC}"
    echo "Please create .env.development with your development Supabase credentials"
    exit 1
fi

# Copy development environment file
echo -e "${BLUE}Step 1/4:${NC} Setting up development environment..."
cp .env.development .env
echo -e "${GREEN}✓${NC} Development environment configured"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running. Please start Docker first.${NC}"
    exit 1
fi

echo -e "${BLUE}Step 2/4:${NC} Building Docker images..."
docker-compose -f docker-compose.dev.yml build

echo -e "${BLUE}Step 3/4:${NC} Starting services..."
docker-compose -f docker-compose.dev.yml up -d

echo -e "${BLUE}Step 4/4:${NC} Waiting for services to be healthy..."
sleep 10

# Check service health
echo -e "${BLUE}Checking service status...${NC}"

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
echo -e "${GREEN}🎉 Development Deployment completed!${NC}"
echo ""
echo -e "${BLUE}Services:${NC}"
echo -e "  Frontend: ${GREEN}http://localhost:3000${NC}"
echo -e "  Backend:  ${GREEN}http://localhost:8080${NC}"
echo -e "  MinIO:    ${GREEN}http://localhost:9000${NC} (admin: minioadmin/minioadmin)"
echo ""
echo -e "${BLUE}Development Features:${NC}"
echo -e "  🔐 Supabase Authentication: ${GREEN}Enabled${NC}"
echo -e "  🧪 Mock User Tester:        ${GREEN}Enabled${NC}"
echo -e "  🐛 Debug Mode:              ${GREEN}Enabled${NC}"
echo -e "  🏗️  Spring Profile:          ${GREEN}dev${NC}"
echo ""
echo -e "${BLUE}Useful commands:${NC}"
echo -e "  View logs:    ${YELLOW}docker-compose -f docker-compose.dev.yml logs -f${NC}"
echo -e "  Stop services: ${YELLOW}docker-compose -f docker-compose.dev.yml down${NC}"
echo -e "  Restart:      ${YELLOW}docker-compose -f docker-compose.dev.yml restart${NC}"
echo ""
