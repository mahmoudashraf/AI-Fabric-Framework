#!/bin/bash

# EasyLuxury Platform - Production Mode Startup
# Unified script to start backend and frontend in production mode

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}🚀 EasyLuxury Production Mode${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# Step 1: Check prerequisites
echo -e "${BLUE}Step 1/8:${NC} Checking prerequisites..."
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
echo -e "${BLUE}Step 2/8:${NC} Checking environment files..."

# Check backend .env.prod file
if [ ! -f "backend/.env.prod" ]; then
    echo -e "${RED}❌ backend/.env.prod not found!${NC}"
    echo "   Production requires proper environment configuration."
    echo "   Please create backend/.env.prod with your production settings."
    echo "   You can use backend/env.prod.template as a reference."
    exit 1
fi

# Check frontend .env.production file
if [ ! -f "frontend/.env.production" ]; then
    echo -e "${RED}❌ frontend/.env.production not found!${NC}"
    echo "   Production requires proper environment configuration."
    echo "   Please create frontend/.env.production with your production settings."
    exit 1
fi

# Validate backend environment variables
echo "Validating backend production configuration..."
if grep -q "your-prod-db-host" backend/.env.prod; then
    echo -e "${RED}❌ Please update DATABASE_URL in backend/.env.prod${NC}"
    exit 1
fi
if grep -q "your_prod_db_user" backend/.env.prod; then
    echo -e "${RED}❌ Please update DATABASE_USERNAME in backend/.env.prod${NC}"
    exit 1
fi
if grep -q "your_prod_db_password" backend/.env.prod; then
    echo -e "${RED}❌ Please update DATABASE_PASSWORD in backend/.env.prod${NC}"
    exit 1
fi
if grep -q "your-project.supabase.co" backend/.env.prod; then
    echo -e "${RED}❌ Please update SUPABASE_URL in backend/.env.prod${NC}"
    exit 1
fi
if grep -q "your_production_anon_key" backend/.env.prod; then
    echo -e "${RED}❌ Please update SUPABASE_API_KEY in backend/.env.prod${NC}"
    exit 1
fi
if grep -q "YOUR_SERVICE_ROLE_KEY_HERE" backend/.env.prod; then
    echo -e "${RED}❌ Please update SUPABASE_SERVICE_ROLE_KEY in backend/.env.prod${NC}"
    exit 1
fi

# Validate frontend environment variables
echo "Validating frontend production configuration..."
if grep -q "your-project.supabase.co" frontend/.env.production; then
    echo -e "${RED}❌ Please update NEXT_PUBLIC_SUPABASE_URL in frontend/.env.production${NC}"
    exit 1
fi
if grep -q "your-anon-key" frontend/.env.production; then
    echo -e "${RED}❌ Please update NEXT_PUBLIC_SUPABASE_ANON_KEY in frontend/.env.production${NC}"
    exit 1
fi

echo -e "${GREEN}✓${NC} Environment files validated"
echo ""

# Step 3: Check PostgreSQL
echo -e "${BLUE}Step 3/8:${NC} Checking PostgreSQL..."
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
echo -e "${BLUE}Step 4/8:${NC} Checking MinIO..."
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
echo -e "${BLUE}Step 5/8:${NC} Checking if ports are available..."
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
echo -e "${BLUE}Step 6/8:${NC} Running database migrations..."
cd backend
if mvn liquibase:update -q 2>/dev/null; then
    echo -e "${GREEN}✓${NC} Database migrations completed"
else
    echo -e "${YELLOW}⚠️${NC}  Migrations may have failed (this is OK if already run)"
fi
cd ..
echo ""

# Step 7: Install and build frontend
echo -e "${BLUE}Step 7/8:${NC} Preparing frontend for production..."
cd frontend
if [ ! -d "node_modules" ]; then
    echo "Installing frontend dependencies..."
    npm install --silent
fi

echo "Building Next.js application for production..."
echo "Using environment file: .env.production"

# Remove any existing .env.local to ensure clean build
rm -f .env.local

# Copy .env.production to .env.local for build
if cp .env.production .env.local && npm run build; then
    echo -e "${GREEN}✓${NC} Frontend built successfully"
else
    echo -e "${RED}❌ Frontend build failed${NC}"
    exit 1
fi
cd ..
echo ""

# Step 8: Start services
echo -e "${BLUE}Step 8/8:${NC} Starting production services..."

# Start backend
echo "Starting backend..."
cd backend
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# Load environment variables from .env.prod
if [ -f ".env.prod" ]; then
    echo "Loading environment variables from .env.prod..."
    export $(grep -v '^#' .env.prod | xargs)
fi

mvn spring-boot:run -Dspring-boot.run.profiles=prod > /tmp/easyluxury-backend.log 2>&1 &
BACKEND_PID=$!
cd ..
echo "Backend starting (PID: $BACKEND_PID)..."

# Wait for backend to start
echo "Waiting for backend to be ready..."
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

# Start frontend
echo "Starting frontend..."
cd frontend
echo "Ensuring production environment is active..."

# Remove any existing .env.local to ensure clean startup
rm -f .env.local

# Copy .env.production to .env.local for runtime
cp .env.production .env.local
npm start > /tmp/easyluxury-frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..
echo "Frontend starting (PID: $FRONTEND_PID)..."
sleep 10
echo -e "${GREEN}✓${NC} Frontend should be running"
echo ""

# Display info
echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}✅ Production Services Started${NC}"
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
echo -e "${BLUE}Production Features:${NC}"
echo -e "  🔐 Supabase Authentication: ${GREEN}Enabled${NC}"
echo -e "  🧪 Mock User Tester:        ${RED}Disabled${NC}"
echo -e "  🐛 Debug Mode:              ${RED}Disabled${NC}"
echo -e "  🏗️  Optimized Build:         ${GREEN}Enabled${NC}"
echo -e "  📁 Frontend Environment:    ${GREEN}.env.production${NC}"
echo -e "  📁 Backend Environment:     ${GREEN}.env.prod${NC}"
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
