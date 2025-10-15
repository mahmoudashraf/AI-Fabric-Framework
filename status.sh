#!/bin/bash

# EasyLuxury Platform - Status Check Script

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}📊 EasyLuxury Service Status${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# Check Backend
echo -e "${BLUE}Backend (Spring Boot):${NC}"
if curl -s http://localhost:8080/api/health 2>/dev/null | grep -q "UP"; then
    echo -e "  Status: ${GREEN}✓ Running${NC}"
    echo "  URL:    http://localhost:8080"
    if [ -f /tmp/easyluxury-backend.pid ]; then
        echo "  PID:    $(cat /tmp/easyluxury-backend.pid)"
    fi
else
    echo -e "  Status: ${RED}✗ Not Running${NC}"
fi
echo ""

# Check Frontend
echo -e "${BLUE}Frontend (Next.js):${NC}"
if curl -s http://localhost:3000 2>/dev/null | grep -q "<!DOCTYPE html>"; then
    echo -e "  Status: ${GREEN}✓ Running${NC}"
    echo "  URL:    http://localhost:3000"
    if [ -f /tmp/easyluxury-frontend.pid ]; then
        echo "  PID:    $(cat /tmp/easyluxury-frontend.pid)"
    fi
else
    echo -e "  Status: ${RED}✗ Not Running${NC}"
fi
echo ""

# Check PostgreSQL
echo -e "${BLUE}PostgreSQL Database:${NC}"
if pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
    echo -e "  Status: ${GREEN}✓ Running${NC}"
    echo "  Port:   5432"
else
    echo -e "  Status: ${RED}✗ Not Running${NC}"
fi
echo ""

# Check MinIO
echo -e "${BLUE}MinIO (File Storage):${NC}"
if curl -s http://localhost:9000/minio/health/live >/dev/null 2>&1; then
    echo -e "  Status: ${GREEN}✓ Running${NC}"
    echo "  URL:    http://localhost:9000"
    echo "  Console: http://localhost:9001"
else
    echo -e "  Status: ${RED}✗ Not Running${NC}"
fi
echo ""

# Check Docker containers
if command -v docker &> /dev/null; then
    echo -e "${BLUE}Docker Containers:${NC}"
    if docker ps | grep -q easyluxury-db-dev; then
        echo -e "  easyluxury-db-dev: ${GREEN}✓ Running${NC}"
    else
        echo -e "  easyluxury-db-dev: ${YELLOW}Not running${NC}"
    fi
    
    if docker ps | grep -q easyluxury-minio-dev; then
        echo -e "  easyluxury-minio-dev: ${GREEN}✓ Running${NC}"
    else
        echo -e "  easyluxury-minio-dev: ${YELLOW}Not running${NC}"
    fi
    echo ""
fi

# Check ports
echo -e "${BLUE}Port Usage:${NC}"
if lsof -Pi :3000 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "  3000 (Frontend): ${GREEN}✓ In use${NC}"
else
    echo -e "  3000 (Frontend): ${YELLOW}Available${NC}"
fi

if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "  8080 (Backend):  ${GREEN}✓ In use${NC}"
else
    echo -e "  8080 (Backend):  ${YELLOW}Available${NC}"
fi

if lsof -Pi :5432 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "  5432 (Database): ${GREEN}✓ In use${NC}"
else
    echo -e "  5432 (Database): ${YELLOW}Available${NC}"
fi

if lsof -Pi :9000 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "  9000 (MinIO):     ${GREEN}✓ In use${NC}"
else
    echo -e "  9000 (MinIO):     ${YELLOW}Available${NC}"
fi

if lsof -Pi :9001 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "  9001 (MinIO Console): ${GREEN}✓ In use${NC}"
else
    echo -e "  9001 (MinIO Console): ${YELLOW}Available${NC}"
fi
echo ""

# Check environment files
echo -e "${BLUE}Configuration:${NC}"
if [ -f "backend/.env" ]; then
    echo -e "  backend/.env:       ${GREEN}✓ Exists${NC}"
else
    echo -e "  backend/.env:       ${RED}✗ Missing${NC}"
fi

if [ -f "frontend/.env.local" ]; then
    echo -e "  frontend/.env.local: ${GREEN}✓ Exists${NC}"
else
    echo -e "  frontend/.env.local: ${RED}✗ Missing${NC}"
fi
echo ""

# Summary
echo -e "${BLUE}================================${NC}"
if curl -s http://localhost:8080/api/health 2>/dev/null | grep -q "UP" && \
   curl -s http://localhost:3000 2>/dev/null | grep -q "<!DOCTYPE html>" && \
   curl -s http://localhost:9000/minio/health/live >/dev/null 2>&1; then
    echo -e "${GREEN}✅ All Services Running${NC}"
    echo ""
    echo "Access Points:"
    echo "  • Frontend: http://localhost:3000"
    echo "  • Backend:  http://localhost:8080"
    echo "  • Swagger:  http://localhost:8080/swagger-ui.html"
    echo "  • MinIO:    http://localhost:9000"
    echo "  • MinIO Console: http://localhost:9001"
else
    echo -e "${YELLOW}⚠️  Some Services Not Running${NC}"
    echo ""
    echo "To start services: ./dev.sh"
fi
echo -e "${BLUE}================================${NC}"
echo ""
