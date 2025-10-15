#!/bin/bash

# EasyLuxury - Start Services Script
# This script helps you start the backend and frontend services

echo "================================"
echo "EasyLuxury Services Startup"
echo "================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if .env files exist
if [ ! -f "backend/.env" ]; then
    echo -e "${RED}ERROR: backend/.env not found!${NC}"
    echo "Please create backend/.env from backend/.env.example"
    exit 1
fi

if [ ! -f "frontend/.env.local" ]; then
    echo -e "${RED}ERROR: frontend/.env.local not found!${NC}"
    echo "Please create frontend/.env.local from frontend/.env.example"
    exit 1
fi

echo -e "${GREEN}✓${NC} Environment files found"
echo ""

# Function to check if a port is in use
check_port() {
    if lsof -Pi :$1 -sTCP:LISTEN -t >/dev/null 2>&1 ; then
        return 0
    else
        return 1
    fi
}

# Check PostgreSQL
echo "Checking PostgreSQL..."
if check_port 5432; then
    echo -e "${GREEN}✓${NC} PostgreSQL is running on port 5432"
else
    echo -e "${YELLOW}!${NC} PostgreSQL not detected on port 5432"
    echo "  Please start PostgreSQL before running the services"
    echo "  Or run: docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:14"
fi
echo ""

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}ERROR: Maven not found!${NC}"
    echo "Please install Maven: https://maven.apache.org/install.html"
    exit 1
fi

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    echo -e "${RED}ERROR: Node.js not found!${NC}"
    echo "Please install Node.js: https://nodejs.org/"
    exit 1
fi

echo -e "${GREEN}✓${NC} Maven and Node.js are installed"
echo ""

echo "================================"
echo "Starting Backend (Spring Boot)"
echo "================================"
echo ""

cd backend

# Run Liquibase migrations
echo "Running database migrations..."
if mvn liquibase:update -q; then
    echo -e "${GREEN}✓${NC} Database migrations completed"
else
    echo -e "${YELLOW}!${NC} Database migrations failed (may be normal if already run)"
fi
echo ""

# Start backend in a new terminal
echo "To start the backend, open a new terminal and run:"
echo ""
echo -e "${GREEN}cd backend && mvn spring-boot:run${NC}"
echo ""
echo "Backend will be available at: http://localhost:8080"
echo "Swagger UI: http://localhost:8080/swagger-ui.html"
echo ""

cd ..

echo "================================"
echo "Starting Frontend (Next.js)"
echo "================================"
echo ""

cd frontend

# Check if node_modules exists
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
    echo -e "${GREEN}✓${NC} Dependencies installed"
else
    echo -e "${GREEN}✓${NC} Dependencies already installed"
fi
echo ""

# Start frontend in a new terminal
echo "To start the frontend, open another terminal and run:"
echo ""
echo -e "${GREEN}cd frontend && npm run dev${NC}"
echo ""
echo "Frontend will be available at: http://localhost:3000"
echo ""

cd ..

echo "================================"
echo "Summary"
echo "================================"
echo ""
echo "1. Ensure PostgreSQL is running on port 5432"
echo "2. Update backend/.env with your Supabase credentials"
echo "3. Update frontend/.env.local with your Supabase credentials"
echo "4. Run backend: cd backend && mvn spring-boot:run"
echo "5. Run frontend: cd frontend && npm run dev"
echo ""
echo "Access Points:"
echo "  - Frontend: http://localhost:3000"
echo "  - Backend: http://localhost:8080"
echo "  - Swagger: http://localhost:8080/swagger-ui.html"
echo ""
echo -e "${GREEN}Setup complete!${NC}"
