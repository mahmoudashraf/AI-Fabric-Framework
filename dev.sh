#!/bin/bash

# EasyLuxury Platform - Development Mode Startup
# Unified script to start backend and frontend in development mode

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}🚀 EasyLuxury Development Mode${NC}"
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

# Ensure .env.local doesn't interfere with development mode
if [ -f "frontend/.env.local" ]; then
    echo -e "${YELLOW}⚠️  frontend/.env.local exists. Renaming to avoid conflicts...${NC}"
    mv frontend/.env.local frontend/.env.local.backup
    echo -e "${GREEN}✓${NC} Renamed .env.local to .env.local.backup"
fi

# Ensure .env.development exists with correct mock auth settings
if [ ! -f "frontend/.env.development" ]; then
    echo -e "${YELLOW}⚠️  frontend/.env.development not found. Creating template...${NC}"
    cat > frontend/.env.development << 'EOF'
# Development Environment Configuration
NODE_ENV=development
NEXT_PUBLIC_ENVIRONMENT=development
NEXT_PUBLIC_MOCK_AUTH_ENABLED=true
NEXT_PUBLIC_API_URL=http://localhost:8080/api
NEXT_PUBLIC_MOCK_USERS_ENDPOINT=/mock/users
NEXT_PUBLIC_MOCK_AUTH_ENDPOINT=/mock/auth
NEXT_PUBLIC_ENABLE_MOCK_USER_TESTER=true
NEXT_PUBLIC_DEBUG_MODE=true

# Supabase Configuration (DISABLED IN DEV)
NEXT_PUBLIC_SUPABASE_URL=
NEXT_PUBLIC_SUPABASE_ANON_KEY=

# App Configuration
NEXT_PUBLIC_APP_NAME=EasyLuxury
NEXT_PUBLIC_APP_VERSION=1.0.0-dev
EOF
    echo -e "${GREEN}✓${NC} Frontend development environment created"
else
    echo -e "${GREEN}✓${NC} Frontend development environment exists"
fi
echo -e "${GREEN}✓${NC} Environment files configured for mock authentication"
echo ""

# Step 3: Check PostgreSQL (Skip for dev mode with H2)
echo -e "${BLUE}Step 3/8:${NC} Checking database configuration..."
echo -e "${YELLOW}⚠️${NC}  Development mode uses H2 in-memory database (no PostgreSQL needed)"
echo -e "${GREEN}✓${NC} Database configuration OK for development"
echo ""

# Skip PostgreSQL setup for dev mode
if false; then
if docker exec easyluxury-db-dev pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} PostgreSQL is running"
elif command -v docker &> /dev/null; then
    echo -e "${YELLOW}⚠️${NC}  PostgreSQL not running. Starting Docker container..."
    
    # Stop any existing backend container that might be failing
    if docker ps -a | grep -q easyluxury-backend-dev; then
        echo "Stopping existing backend container..."
        docker stop easyluxury-backend-dev >/dev/null 2>&1
    fi
    
    if docker ps -a | grep -q easyluxury-db-dev; then
        echo "Starting existing PostgreSQL container..."
        docker start easyluxury-db-dev >/dev/null 2>&1
    else
        echo "Creating new PostgreSQL container..."
        docker run -d --name easyluxury-db-dev \
          -e POSTGRES_DB=easyluxury \
          -e POSTGRES_USER=postgres \
          -e POSTGRES_PASSWORD=postgres \
          -p 5432:5432 \
          postgres:14 >/dev/null 2>&1
    fi
    
    echo "Waiting for PostgreSQL to be ready..."
    # Wait up to 30 seconds for PostgreSQL to be ready
    for i in {1..30}; do
        if docker exec easyluxury-db-dev pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
            echo -e "${GREEN}✓${NC} PostgreSQL started successfully"
            break
        fi
        if [ $i -eq 30 ]; then
            echo -e "${RED}❌ PostgreSQL failed to start within 30 seconds${NC}"
            echo "Check container logs: docker logs easyluxury-db-dev"
            exit 1
        fi
        sleep 1
        echo -n "."
    done
    echo ""
else
    echo -e "${RED}❌ PostgreSQL not running and Docker not available${NC}"
    echo "   Please start PostgreSQL manually or install Docker"
    exit 1
fi
fi
echo ""

# Step 4: Check MinIO (Optional for development)
echo -e "${BLUE}Step 4/8:${NC} Checking MinIO..."
if curl -s http://localhost:9000/minio/health/live >/dev/null 2>&1; then
    echo -e "${GREEN}✓${NC} MinIO is running"
elif command -v docker &> /dev/null; then
    echo -e "${YELLOW}⚠️${NC}  MinIO not running. Starting Docker container..."
    if docker ps -a | grep -q easyluxury-minio-dev; then
        docker start easyluxury-minio-dev >/dev/null 2>&1
    else
        docker run -d --name easyluxury-minio-dev \
          -e MINIO_ROOT_USER=minioadmin \
          -e MINIO_ROOT_PASSWORD=minioadmin \
          -p 9000:9000 \
          -p 9001:9001 \
          minio/minio:latest server /data --console-address ":9001" >/dev/null 2>&1
    fi
    echo "Waiting for MinIO to be ready..."
    # Wait up to 20 seconds for MinIO to be ready
    for i in {1..20}; do
        if curl -s http://localhost:9000/minio/health/live >/dev/null 2>&1; then
            echo -e "${GREEN}✓${NC} MinIO started successfully"
            # Create bucket if it doesn't exist
            echo "Setting up MinIO bucket..."
            docker exec easyluxury-minio-dev mc alias set myminio http://localhost:9000 minioadmin minioadmin >/dev/null 2>&1
            docker exec easyluxury-minio-dev mc mb myminio/easyluxury >/dev/null 2>&1 || true
            docker exec easyluxury-minio-dev mc anonymous set public myminio/easyluxury >/dev/null 2>&1 || true
            echo -e "${GREEN}✓${NC} MinIO bucket configured"
            break
        fi
        if [ $i -eq 20 ]; then
            echo -e "${YELLOW}⚠️${NC}  MinIO failed to start within 20 seconds"
            echo -e "${YELLOW}⚠️${NC}  Continuing without MinIO (file uploads may not work)"
            break
        fi
        sleep 1
        echo -n "."
    done
    echo ""
else
    echo -e "${YELLOW}⚠️${NC}  MinIO not running and Docker not available"
    echo -e "${YELLOW}⚠️${NC}  Continuing without MinIO (file uploads may not work)"
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

# Step 6: Build AI Infrastructure Module
echo -e "${BLUE}Step 6/10:${NC} Building AI Infrastructure Module..."
cd ai-infrastructure-module
echo "Cleaning and installing AI module to local repository..."
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
if mvn clean install -DskipTests -q; then
    echo -e "${GREEN}✓${NC} AI Infrastructure Module built and installed"
else
    echo -e "${RED}❌ AI Infrastructure Module build failed${NC}"
    echo "Check the output above for compilation errors"
    exit 1
fi
cd ..
echo ""

# Step 7: Recompile backend
echo -e "${BLUE}Step 7/10:${NC} Recompiling backend..."
cd backend
echo "Cleaning and compiling backend..."
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
if mvn clean compile -q; then
    echo -e "${GREEN}✓${NC} Backend compilation completed"
else
    echo -e "${RED}❌ Backend compilation failed${NC}"
    echo "Check the output above for compilation errors"
    exit 1
fi
cd ..
echo ""

# Step 8: Skip database migrations (H2 auto-creates tables)
echo -e "${BLUE}Step 8/10:${NC} Skipping database migrations..."
echo -e "${YELLOW}⚠️${NC}  H2 in-memory database auto-creates tables on startup"
echo -e "${GREEN}✓${NC} Database setup handled by Hibernate"
echo ""

# Step 9: Install frontend dependencies
echo -e "${BLUE}Step 9/10:${NC} Checking frontend dependencies..."
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

# Step 10: Start services
echo -e "${BLUE}Step 10/10:${NC} Starting services..."

# Start backend
echo "Starting backend..."
cd backend
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# Load backend development environment variables (including OPENAI_API_KEY)
echo "Loading backend env (.env.dev or .env) ..."
if [ -f ".env.dev" ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env.dev
  set +a
  echo "Loaded backend/.env.dev"
elif [ -f ".env" ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
  echo "Loaded backend/.env"
else
  echo "No backend env file found. Proceeding with current environment."
fi

# Ensure dev profile and mock auth are enabled
export SPRING_PROFILES_ACTIVE=${SPRING_PROFILES_ACTIVE:-dev}
export APP_MOCK_AUTH_ENABLED=${APP_MOCK_AUTH_ENABLED:-true}

# Kill any existing backend process on port 8080
if lsof -Pi :8080 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "Stopping existing backend process..."
    lsof -ti:8080 | xargs kill -9 2>/dev/null || true
    sleep 2
fi

mvn spring-boot:run -Dspring-boot.run.profiles=${SPRING_PROFILES_ACTIVE} > /tmp/easyluxury-backend.log 2>&1 &
BACKEND_PID=$!
cd ..
echo "Backend starting (PID: $BACKEND_PID)..."

# Wait for backend to start
echo "Waiting for backend to be ready..."
for i in {1..90}; do
    if curl -s http://localhost:8080/actuator/health 2>/dev/null | grep -q "UP"; then
        echo -e "${GREEN}✓${NC} Backend is running"
        break
    fi
    if [ $i -eq 90 ]; then
        echo -e "${RED}❌ Backend failed to start within 90 seconds${NC}"
        echo "Check logs: tail -f /tmp/easyluxury-backend.log"
        echo "Last 20 lines of backend log:"
        tail -20 /tmp/easyluxury-backend.log
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

# Kill any existing frontend process on port 3000
if lsof -Pi :3000 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "Stopping existing frontend process..."
    lsof -ti:3000 | xargs kill -9 2>/dev/null || true
    sleep 2
fi

npm run dev > /tmp/easyluxury-frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..
echo "Frontend starting (PID: $FRONTEND_PID)..."
sleep 10
echo -e "${GREEN}✓${NC} Frontend should be running"
echo ""

# Display info
echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}✅ Development Services Started${NC}"
echo -e "${GREEN}================================${NC}"
echo ""
echo -e "${BLUE}Access Points:${NC}"
echo -e "  📱 Frontend:    ${GREEN}http://localhost:3000${NC}"
echo -e "  🔧 Backend API: ${GREEN}http://localhost:8080${NC}"
echo -e "  📚 Swagger UI:  ${GREEN}http://localhost:8080/swagger-ui.html${NC}"
echo -e "  💚 Health:      ${GREEN}http://localhost:8080/actuator/health${NC}"
echo -e "  🪣 MinIO:       ${GREEN}http://localhost:9000${NC}"
echo -e "  🖥️  MinIO Console: ${GREEN}http://localhost:9001${NC}"
echo ""
echo -e "${BLUE}Development Features:${NC}"
echo -e "  🔐 Mock Authentication: ${GREEN}Enabled${NC}"
echo -e "  🧪 Mock User Tester:    ${GREEN}Enabled${NC}"
echo -e "  🐛 Debug Mode:          ${GREEN}Enabled${NC}"
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

# In background mode, exit immediately after starting services
if [ "$RUN_IN_BACKGROUND" -eq 1 ]; then
  echo -e "${GREEN}Background mode enabled. Exiting script while services continue running.${NC}"
  exit 0
fi

# Cleanup function to restore .env.local if it was backed up
cleanup() {
    if [ -f "frontend/.env.local.backup" ]; then
        echo -e "${YELLOW}🔄 Restoring frontend/.env.local from backup...${NC}"
        mv frontend/.env.local.backup frontend/.env.local
        echo -e "${GREEN}✓${NC} Restored .env.local"
    fi
    echo "Services are still running. Use ./stop.sh to stop them."
}

# Wait for user interrupt
trap 'cleanup; exit 0' INT
wait
