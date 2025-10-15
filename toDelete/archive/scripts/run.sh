#!/bin/bash

# EasyLuxury Platform - Auto Run Script
# This script is referenced by HOW_TO_RUN.md

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}🚀 EasyLuxury Platform Startup${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# Step 1: Check prerequisites
echo -e "${BLUE}Step 1/9:${NC} Checking prerequisites..."
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ Java not found. Please install Java 21+${NC}"
    exit 1
fi
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ Maven not found. Please install Maven 3.8+${NC}"
    exit 1
fi
if ! command -v node &> /dev/null; then
    echo -e "${RED}❌ Node.js not found. Please install Node.js 18+${NC}"
    exit 1
fi
echo -e "${GREEN}✓${NC} Java, Maven, and Node.js are installed"
echo ""

# Step 2: Check environment files
echo -e "${BLUE}Step 2/9:${NC} Checking environment files..."
if [ ! -f "backend/.env" ]; then
    echo -e "${YELLOW}⚠️  backend/.env not found. Creating template...${NC}"
    cat > backend/.env << 'EOF'
# Database Configuration
DATABASE_URL=jdbc:postgresql://localhost:5432/easyluxury
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres

# Supabase Configuration (REPLACE WITH YOUR ACTUAL VALUES)
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_API_KEY=your_anon_key_here
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key_here

# Spring Profile
SPRING_PROFILES_ACTIVE=dev
EOF
    echo -e "${RED}❌ STOP: Please edit backend/.env with your Supabase credentials!${NC}"
    echo "   1. Go to https://supabase.com"
    echo "   2. Create/open your project"
    echo "   3. Go to Settings → API"
    echo "   4. Copy Project URL, anon key, and service_role key"
    echo "   5. Update backend/.env"
    echo "   6. Run this script again"
    exit 1
fi

if [ ! -f "frontend/.env.local" ]; then
    echo -e "${YELLOW}⚠️  frontend/.env.local not found. Creating template...${NC}"
    cat > frontend/.env.local << 'EOF'
# Supabase Configuration (REPLACE WITH YOUR ACTUAL VALUES)
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your_anon_key_here

# Backend API URL
NEXT_PUBLIC_API_URL=http://localhost:8080

# App Configuration
NEXT_PUBLIC_APP_NAME=EasyLuxury
NEXT_PUBLIC_APP_VERSION=1.0.0
EOF
    echo -e "${RED}❌ STOP: Please edit frontend/.env.local with your Supabase credentials!${NC}"
    echo "   Use the same credentials as backend/.env"
    echo "   Then run this script again"
    exit 1
fi
echo -e "${GREEN}✓${NC} Environment files exist"
echo ""

# Step 3: Check PostgreSQL
echo -e "${BLUE}Step 3/10:${NC} Checking PostgreSQL..."
if docker exec easyluxury-db pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} PostgreSQL is running"
elif command -v docker &> /dev/null; then
    echo -e "${YELLOW}⚠️${NC}  PostgreSQL not running. Starting Docker container..."
    if docker ps -a | grep -q easyluxury-db; then
        docker start easyluxury-db >/dev/null 2>&1
    else
        docker run -d --name easyluxury-db \
          -e POSTGRES_DB=easyluxury \
          -e POSTGRES_USER=postgres \
          -e POSTGRES_PASSWORD=postgres \
          -p 5432:5432 \
          postgres:14 >/dev/null 2>&1
    fi
    echo "Waiting for PostgreSQL to be ready..."
    sleep 5
    if docker exec easyluxury-db pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} PostgreSQL started successfully"
    else
        echo -e "${RED}❌ PostgreSQL failed to start${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ PostgreSQL not running and Docker not available${NC}"
    echo "   Please start PostgreSQL manually or install Docker"
    exit 1
fi
echo ""

# Step 4: Check MinIO
echo -e "${BLUE}Step 4/10:${NC} Checking MinIO..."
if curl -s http://localhost:9000/minio/health/live >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} MinIO is running"
elif command -v docker &> /dev/null; then
    echo -e "${YELLOW}⚠️${NC}  MinIO not running. Starting Docker container..."
    if docker ps -a | grep -q easyluxury-minio; then
        docker start easyluxury-minio >/dev/null 2>&1
    else
        docker run -d --name easyluxury-minio \
          -e MINIO_ROOT_USER=minioadmin \
          -e MINIO_ROOT_PASSWORD=minioadmin \
          -p 9000:9000 \
          -p 9001:9001 \
          minio/minio:latest server /data --console-address ":9001" >/dev/null 2>&1
    fi
    echo "Waiting for MinIO to be ready..."
    sleep 10
    if curl -s http://localhost:9000/minio/health/live >/dev/null 2>&1; then
        echo -e "${GREEN}✓${NC} MinIO started successfully"
        # Create bucket if it doesn't exist
        echo "Setting up MinIO bucket..."
        docker exec easyluxury-minio mc alias set myminio http://localhost:9000 minioadmin minioadmin >/dev/null 2>&1
        docker exec easyluxury-minio mc mb myminio/easyluxury >/dev/null 2>&1 || true
        docker exec easyluxury-minio mc anonymous set public myminio/easyluxury >/dev/null 2>&1 || true
        echo -e "${GREEN}✓${NC} MinIO bucket configured"
    else
        echo -e "${RED}❌ MinIO failed to start${NC}"
        exit 1
    fi
else
    echo -e "${RED}❌ MinIO not running and Docker not available${NC}"
    echo "   Please start MinIO manually or install Docker"
    exit 1
fi
echo ""

# Step 5: Check ports
echo -e "${BLUE}Step 5/10:${NC} Checking if ports are available..."
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️${NC}  Port 8080 is in use. Stopping existing process..."
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
    sleep 2
fi
if lsof -Pi :3000 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo -e "${YELLOW}⚠️${NC}  Port 3000 is in use. Stopping existing process..."
    lsof -ti:3000 | xargs kill -9 2>/dev/null || true
    sleep 2
fi
echo -e "${GREEN}✓${NC} Ports 3000 and 8080 are available"
echo ""

# Step 6: Run database migrations
echo -e "${BLUE}Step 6/10:${NC} Running database migrations..."
cd backend
if mvn liquibase:update -q 2>/dev/null; then
    echo -e "${GREEN}✓${NC} Database migrations completed"
else
    echo -e "${YELLOW}⚠️${NC}  Migrations may have failed (this is OK if already run)"
fi
cd ..
echo ""

# Step 7: Install frontend dependencies
echo -e "${BLUE}Step 7/10:${NC} Checking frontend dependencies..."
if [ ! -d "frontend/node_modules" ]; then
    echo "Installing frontend dependencies..."
    cd frontend
    npm install --silent
    cd ..
    echo -e "${GREEN}✓${NC} Dependencies installed"
else
    echo -e "${GREEN}✓${NC} Dependencies already installed"
fi
echo ""

# Step 8: Start backend
echo -e "${BLUE}Step 8/10:${NC} Starting backend..."
cd backend
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
mvn spring-boot:run -Dspring-boot.run.profiles=dev > /tmp/easyluxury-backend.log 2>&1 &
BACKEND_PID=$!
cd ..
echo "Backend starting (PID: $BACKEND_PID)..."
echo "Waiting for backend to be ready..."

# Wait for backend to start
for i in {1..60}; do
    if curl -s http://localhost:8080/api/health 2>/dev/null | grep -q "UP"; then
        echo -e "${GREEN}✓${NC} Backend is running"
        break
    fi
    if [ $i -eq 60 ]; then
        echo -e "${RED}❌ Backend failed to start within 60 seconds${NC}"
        echo "Check logs: tail -f /tmp/easyluxury-backend.log"
        kill $BACKEND_PID 2>/dev/null
        exit 1
    fi
    sleep 1
    echo -n "."
done
echo ""
echo ""

# Step 9: Start frontend
echo -e "${BLUE}Step 9/10:${NC} Starting frontend..."
cd frontend
npm run dev > /tmp/easyluxury-frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..
echo "Frontend starting (PID: $FRONTEND_PID)..."
echo "Waiting for frontend to be ready..."
sleep 10
echo -e "${GREEN}✓${NC} Frontend should be running"
echo ""

# Step 10: Display info
echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}✅ Services Started Successfully${NC}"
echo -e "${GREEN}================================${NC}"
echo ""
echo -e "${BLUE}Access Points:${NC}"
echo -e "  📱 Frontend:    ${GREEN}http://localhost:3000${NC}"
echo -e "  🔧 Backend API: ${GREEN}http://localhost:8080${NC}"
echo -e "  📚 Swagger UI:  ${GREEN}http://localhost:8080/swagger-ui.html${NC}"
echo -e "  💚 Health:      ${GREEN}http://localhost:8080/api/health${NC}"
echo -e "  🪣 MinIO:       ${GREEN}http://localhost:9000${NC}"
echo -e "  🖥️  MinIO Console: ${GREEN}http://localhost:9001${NC}"
echo ""
echo -e "${BLUE}Process Information:${NC}"
echo "  Backend PID:  $BACKEND_PID"
echo "  Frontend PID: $FRONTEND_PID"
echo ""
echo -e "${BLUE}View Logs:${NC}"
echo "  Backend:  tail -f /tmp/easyluxury-backend.log"
echo "  Frontend: tail -f /tmp/easyluxury-frontend.log"
echo ""
echo -e "${BLUE}Stop Services:${NC}"
echo "  Run: ./stop.sh"
echo "  Or:  kill $BACKEND_PID $FRONTEND_PID"
echo ""
echo -e "${YELLOW}Note: Keep this terminal open. Services are running in background.${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop this script (services will continue running).${NC}"
echo ""

# Save PIDs to file for stop script
echo "$BACKEND_PID" > /tmp/easyluxury-backend.pid
echo "$FRONTEND_PID" > /tmp/easyluxury-frontend.pid

# Wait for user interrupt
trap 'echo ""; echo "Services are still running. Use ./stop.sh to stop them."; exit 0' INT
wait
