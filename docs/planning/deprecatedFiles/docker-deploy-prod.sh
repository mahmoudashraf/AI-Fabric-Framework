#!/bin/bash

# EasyLuxury Docker Deployment Script - Production
# Deploys frontend + backend + Supabase database in PRODUCTION mode

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}🐳 EasyLuxury Production Docker${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# Check if .env.production file exists
if [ ! -f ".env.production" ]; then
    echo -e "${RED}❌ .env.production file not found!${NC}"
    echo "Please create .env.production with your production Supabase credentials"
    exit 1
fi

# Copy production environment file
echo -e "${BLUE}Step 1/4:${NC} Setting up production environment..."
cp .env.production .env
echo -e "${GREEN}✓${NC} Production environment configured"

# Check if Docker is running
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}❌ Docker is not running. Please start Docker first.${NC}"
    exit 1
fi

echo -e "${BLUE}Step 2/4:${NC} Building Docker images..."
docker-compose -f docker-compose.prod.yml build

echo -e "${BLUE}Step 3/4:${NC} Starting services..."
docker-compose -f docker-compose.prod.yml up -d

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
echo -e "${GREEN}🎉 Production Deployment completed!${NC}"
echo ""
echo -e "${BLUE}Services:${NC}"
echo -e "  Frontend: ${GREEN}http://localhost:3000${NC}"
echo -e "  Backend:  ${GREEN}http://localhost:8080${NC}"
echo ""
echo -e "${BLUE}Production Features:${NC}"
echo -e "  🔐 Supabase Authentication: ${GREEN}Enabled${NC}"
echo -e "  🧪 Mock User Tester:        ${RED}Disabled${NC}"
echo -e "  🐛 Debug Mode:              ${RED}Disabled${NC}"
echo -e "  🏗️  Spring Profile:          ${GREEN}prod${NC}"
echo ""
echo -e "${BLUE}Useful commands:${NC}"
echo -e "  View logs:    ${YELLOW}docker-compose -f docker-compose.prod.yml logs -f${NC}"
echo -e "  Stop services: ${YELLOW}docker-compose -f docker-compose.prod.yml down${NC}"
echo -e "  Restart:      ${YELLOW}docker-compose -f docker-compose.prod.yml restart${NC}"
echo ""
