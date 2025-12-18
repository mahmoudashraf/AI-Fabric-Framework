#!/bin/bash

# EasyLuxury Platform - Mocked Development Mode Startup
# Optimized for development with H2 database, MinIO, and mock AI services

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}================================${NC}"
echo -e "${BLUE}🚀 EasyLuxury Mocked Dev Mode${NC}"
echo -e "${BLUE}================================${NC}"
echo ""

# Step 1: Check prerequisites
echo -e "${BLUE}Step 1/7:${NC} Checking prerequisites..."
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

# Step 2: Setup environment files for mocked development
echo -e "${BLUE}Step 2/7:${NC} Setting up mocked development environment..."

# Create backend env.dev for mocked development
if [ ! -f "backend/env.dev" ]; then
    echo -e "${YELLOW}⚠️  backend/env.dev not found. Creating mocked development template...${NC}"
    cat > backend/env.dev << 'EOF'
# Mocked Development Environment Configuration
# Using H2 database and mock AI services

# Spring Profile
SPRING_PROFILES_ACTIVE=dev

# Mock AI Configuration (no real API keys needed)
OPENAI_API_KEY=sk-dev-mock-key
PINECONE_API_KEY=dev-mock-key

# Mock Authentication
APP_MOCK_AUTH_ENABLED=true

# Database (H2 in-memory - no external DB needed)
DATABASE_URL=jdbc:h2:mem:devdb
DATABASE_USERNAME=sa
DATABASE_PASSWORD=

# MinIO Configuration
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=easyluxury-dev

# Disable external services
SUPABASE_URL=
SUPABASE_API_KEY=
SUPABASE_SERVICE_ROLE_KEY=
EOF
    echo -e "${GREEN}✓${NC} Backend mocked development environment created"
else
    echo -e "${GREEN}✓${NC} Backend development environment exists"
fi

# Ensure frontend .env.development exists with mock settings
if [ ! -f "frontend/.env.development" ]; then
    echo -e "${YELLOW}⚠️  frontend/.env.development not found. Creating mocked template...${NC}"
    cat > frontend/.env.development << 'EOF'
# Mocked Development Environment Configuration
NODE_ENV=development
NEXT_PUBLIC_ENVIRONMENT=development
NEXT_PUBLIC_MOCK_AUTH_ENABLED=true
NEXT_PUBLIC_API_URL=http://localhost:8080/api
NEXT_PUBLIC_MOCK_USERS_ENDPOINT=/mock/users
NEXT_PUBLIC_MOCK_AUTH_ENDPOINT=/mock/auth
NEXT_PUBLIC_ENABLE_MOCK_USER_TESTER=true
NEXT_PUBLIC_DEBUG_MODE=true

# Disable Supabase in mocked mode
NEXT_PUBLIC_SUPABASE_URL=
NEXT_PUBLIC_SUPABASE_ANON_KEY=

# App Configuration
NEXT_PUBLIC_APP_NAME=EasyLuxury
NEXT_PUBLIC_APP_VERSION=1.0.0-dev-mocked

# MinIO Configuration
NEXT_PUBLIC_MINIO_ENDPOINT=http://localhost:9000
NEXT_PUBLIC_MINIO_BUCKET=easyluxury-dev
EOF
    echo -e "${GREEN}✓${NC} Frontend mocked development environment created"
else
    echo -e "${GREEN}✓${NC} Frontend development environment exists"
fi

# Ensure .env.local doesn't interfere
if [ -f "frontend/.env.local" ]; then
    echo -e "${YELLOW}⚠️  frontend/.env.local exists. Renaming to avoid conflicts...${NC}"
    mv frontend/.env.local frontend/.env.local.backup
    echo -e "${GREEN}✓${NC} Renamed .env.local to .env.local.backup"
fi

echo -e "${GREEN}✓${NC} Mocked development environment configured"
echo ""

# Step 3: Check MinIO (required for file storage)
echo -e "${BLUE}Step 3/7:${NC} Checking MinIO..."
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
            docker exec easyluxury-minio-dev mc mb myminio/easyluxury-dev >/dev/null 2>&1 || true
            docker exec easyluxury-minio-dev mc anonymous set public myminio/easyluxury-dev >/dev/null 2>&1 || true
            echo -e "${GREEN}✓${NC} MinIO bucket configured"
            break
        fi
        if [ $i -eq 20 ]; then
            echo -e "${RED}❌ MinIO failed to start within 20 seconds${NC}"
            echo "Check container logs: docker logs easyluxury-minio-dev"
            exit 1
        fi
        sleep 1
        echo -n "."
    done
    echo ""
else
    echo -e "${RED}❌ MinIO not running and Docker not available${NC}"
    echo "   MinIO is required for file storage. Please install Docker or start MinIO manually"
    exit 1
fi
echo ""

# Step 4: Check ports
echo -e "${BLUE}Step 4/7:${NC} Checking if ports are available..."
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

# Step 5: Recompile backend
echo -e "${BLUE}Step 5/7:${NC} Recompiling backend..."
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

# Step 6: Install frontend dependencies
echo -e "${BLUE}Step 6/7:${NC} Checking frontend dependencies..."
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

# Step 7: Start services
echo -e "${BLUE}Step 7/7:${NC} Starting mocked development services..."

# Start backend with mocked configuration
echo "Starting backend with H2 database and mock AI services..."
cd backend
export JAVA_HOME=/usr/local/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home

# Load backend mocked development environment variables
echo "Loading backend mocked development environment..."
if [ -f "env.dev" ]; then
  set -a
  # shellcheck disable=SC1091
  . ./env.dev
  set +a
  echo "Loaded backend/env.dev"
else
  echo "No backend env.dev file found. Using default mocked settings."
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

mvn spring-boot:run -Dspring-boot.run.profiles=${SPRING_PROFILES_ACTIVE} > /tmp/easyluxury-backend-mocked.log 2>&1 &
BACKEND_PID=$!
cd ..
echo "Backend starting with H2 database (PID: $BACKEND_PID)..."

# Wait for backend to start
echo "Waiting for backend to be ready..."
for i in {1..90}; do
    if curl -s http://localhost:8080/api/health 2>/dev/null | grep -q "UP"; then
        echo -e "${GREEN}✓${NC} Backend is running with H2 database"
        break
    fi
    if [ $i -eq 90 ]; then
        echo -e "${RED}❌ Backend failed to start within 90 seconds${NC}"
        echo "Check logs: tail -f /tmp/easyluxury-backend-mocked.log"
        echo "Last 20 lines of backend log:"
        tail -20 /tmp/easyluxury-backend-mocked.log
        kill $BACKEND_PID 2>/dev/null
        exit 1
    fi
    sleep 1
    echo -n "."
done
echo ""

# Start frontend
echo "Starting frontend with mocked authentication..."
cd frontend

# Kill any existing frontend process on port 3000
if lsof -Pi :3000 -sTCP:LISTEN -t >/dev/null 2>&1; then
    echo "Stopping existing frontend process..."
    lsof -ti:3000 | xargs kill -9 2>/dev/null || true
    sleep 2
fi

npm run dev > /tmp/easyluxury-frontend-mocked.log 2>&1 &
FRONTEND_PID=$!
cd ..
echo "Frontend starting with mocked auth (PID: $FRONTEND_PID)..."
sleep 10
echo -e "${GREEN}✓${NC} Frontend should be running"
echo ""

# Display info
echo -e "${GREEN}================================${NC}"
echo -e "${GREEN}✅ Mocked Development Services Started${NC}"
echo -e "${GREEN}================================${NC}"
echo ""
echo -e "${BLUE}Access Points:${NC}"
echo -e "  📱 Frontend:    ${GREEN}http://localhost:3000${NC}"
echo -e "  🔧 Backend API: ${GREEN}http://localhost:8080${NC}"
echo -e "  📚 Swagger UI:  ${GREEN}http://localhost:8080/swagger-ui.html${NC}"
echo -e "  💚 Health:      ${GREEN}http://localhost:8080/api/health${NC}"
echo -e "  🗄️  H2 Console:  ${GREEN}http://localhost:8080/h2-console${NC}"
echo -e "  🪣 MinIO:       ${GREEN}http://localhost:9000${NC}"
echo -e "  🖥️  MinIO Console: ${GREEN}http://localhost:9001${NC}"
echo ""
echo -e "${BLUE}Mocked Development Features:${NC}"
echo -e "  🗄️  Database:        ${GREEN}H2 In-Memory${NC}"
echo -e "  🔐 Mock Authentication: ${GREEN}Enabled${NC}"
echo -e "  🤖 Mock AI Services:   ${GREEN}Enabled${NC}"
echo -e "  🧪 Mock User Tester:   ${GREEN}Enabled${NC}"
echo -e "  🐛 Debug Mode:         ${GREEN}Enabled${NC}"
echo -e "  📁 File Storage:        ${GREEN}MinIO${NC}"
echo ""
echo -e "${BLUE}H2 Database Info:${NC}"
echo -e "  JDBC URL: jdbc:h2:mem:devdb"
echo -e "  Username: sa"
echo -e "  Password: (empty)"
echo -e "  Console: http://localhost:8080/h2-console"
echo ""
echo -e "${BLUE}Process Information:${NC}"
echo "  Backend PID:  $BACKEND_PID"
echo "  Frontend PID: $FRONTEND_PID"
echo ""
echo -e "${BLUE}View Logs:${NC}"
echo "  Backend:  tail -f /tmp/easyluxury-backend-mocked.log"
echo "  Frontend: tail -f /tmp/easyluxury-frontend-mocked.log"
echo ""
echo -e "${BLUE}Stop Services:${NC}"
echo "  Run: ./stop.sh"
echo "  Or:  kill $BACKEND_PID $FRONTEND_PID"
echo ""
echo -e "${YELLOW}Note: Keep this terminal open. Services are running in background.${NC}"
echo -e "${YELLOW}Press Ctrl+C to stop this script (services will continue running).${NC}"
echo ""

# Save PIDs to file for stop script
echo "$BACKEND_PID" > /tmp/easyluxury-backend-mocked.pid
echo "$FRONTEND_PID" > /tmp/easyluxury-frontend-mocked.pid

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
