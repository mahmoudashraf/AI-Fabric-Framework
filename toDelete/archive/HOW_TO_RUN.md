# HOW TO RUN - EasyLuxury Platform

**Purpose:** This guide is for Cursor AI to follow when the user says "run"

---

## 🎯 Quick Run Commands

When user says **"run"**, execute these steps in order:

### Step 1: Verify Prerequisites
```bash
# Check Java
java -version
# Expected: Java 21 or higher

# Check Maven
mvn -version
# Expected: Maven 3.8+

# Check Node.js
node -version
# Expected: Node.js 18+

# Check PostgreSQL
psql --version
# OR check if Docker is available
docker --version
```

### Step 2: Check Environment Files
```bash
# Backend environment
test -f backend/.env && echo "✓ Backend .env exists" || echo "✗ Backend .env missing - CREATE IT"

# Frontend environment
test -f frontend/.env.local && echo "✓ Frontend .env.local exists" || echo "✗ Frontend .env.local missing - CREATE IT"
```

### Step 3: Start PostgreSQL (if not running)
```bash
# Check if PostgreSQL is running
pg_isready -h localhost -p 5432 2>/dev/null && echo "✓ PostgreSQL running" || echo "✗ PostgreSQL not running - START IT"

# If not running, start with Docker:
docker run -d --name easyluxury-db \
  -e POSTGRES_DB=easyluxury \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:14 2>/dev/null || echo "PostgreSQL container already exists or Docker not available"
```

### Step 4: Start MinIO (File Storage)
```bash
# Check if MinIO is running
curl -s http://localhost:9000/minio/health/live >/dev/null && echo "✓ MinIO running" || echo "✗ MinIO not running - START IT"

# If not running, start with Docker:
docker run -d --name easyluxury-minio \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  -p 9000:9000 \
  -p 9001:9001 \
  minio/minio:latest server /data --console-address ":9001" 2>/dev/null || echo "MinIO container already exists or Docker not available"

# Access MinIO Console: http://localhost:9001
# Username: minioadmin, Password: minioadmin
```

### Step 5: Run Database Migrations (first time only)
```bash
cd backend
mvn liquibase:update -q
cd ..
```

### Step 6: Start Backend (in background or new terminal)
```bash
cd backend
mvn spring-boot:run &
# Store PID for later: BACKEND_PID=$!
cd ..

# Wait for backend to start
sleep 10
```

### Step 7: Verify Backend is Running
```bash
curl -s http://localhost:8080/api/health | grep -q "UP" && echo "✓ Backend running" || echo "✗ Backend not responding"
```

### Step 8: Start Frontend (in background or new terminal)
```bash
cd frontend
npm run dev &
# Store PID for later: FRONTEND_PID=$!
cd ..

# Wait for frontend to start
sleep 5
```

### Step 8: Verify Frontend is Running
```bash
curl -s http://localhost:3000 | grep -q "<!DOCTYPE html>" && echo "✓ Frontend running" || echo "✗ Frontend not responding"
```

### Step 9: Display Access URLs
```bash
echo "================================"
echo "✓ Services Started Successfully"
echo "================================"
echo ""
echo "Frontend: http://localhost:3000"
echo "Backend:  http://localhost:8080"
echo "Swagger:  http://localhost:8080/swagger-ui.html"
echo "Health:   http://localhost:8080/api/health"
echo ""
echo "To stop services, run: ./STOP_SERVICES.sh"
```

---

## 🚨 If Environment Files Missing

### Create Backend .env
```bash
cat > backend/.env << 'EOF'
# Database Configuration
DATABASE_URL=jdbc:postgresql://localhost:5432/easyluxury
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres

# Supabase Configuration (REPLACE WITH ACTUAL VALUES)
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_API_KEY=your_anon_key_here
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key_here

# Spring Profile
SPRING_PROFILES_ACTIVE=dev
EOF

echo "⚠️  IMPORTANT: Edit backend/.env with your Supabase credentials!"
```

### Create Frontend .env.local
```bash
cat > frontend/.env.local << 'EOF'
# Supabase Configuration (REPLACE WITH ACTUAL VALUES)
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your_anon_key_here

# Backend API URL
NEXT_PUBLIC_API_URL=http://localhost:8080

# App Configuration
NEXT_PUBLIC_APP_NAME=EasyLuxury
NEXT_PUBLIC_APP_VERSION=1.0.0
EOF

echo "⚠️  IMPORTANT: Edit frontend/.env.local with your Supabase credentials!"
```

---

## 🛑 How to Stop Services

### Find and Kill Running Processes
```bash
# Stop backend
pkill -f "spring-boot:run" || echo "Backend not running"

# Stop frontend
pkill -f "next-env" || pkill -f "next dev" || echo "Frontend not running"

# Stop PostgreSQL Docker container (if used)
docker stop easyluxury-db 2>/dev/null || echo "PostgreSQL container not running"
```

---

## ⚠️ Troubleshooting Steps

### Issue: Port Already in Use

**Backend (8080):**
```bash
lsof -ti:8080 | xargs kill -9
```

**Frontend (3000):**
```bash
lsof -ti:3000 | xargs kill -9
```

**PostgreSQL (5432):**
```bash
lsof -ti:5432 | xargs kill -9
# OR
docker stop easyluxury-db
```

### Issue: Database Connection Failed
```bash
# Check PostgreSQL is running
pg_isready -h localhost -p 5432

# If not, start Docker container
docker start easyluxury-db 2>/dev/null || docker run -d --name easyluxury-db \
  -e POSTGRES_DB=easyluxury \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:14

# Wait for PostgreSQL to be ready
sleep 5
```

### Issue: Supabase Credentials Missing
```bash
echo "⚠️  You need to set up Supabase credentials!"
echo ""
echo "1. Go to https://supabase.com"
echo "2. Create a project"
echo "3. Go to Settings → API"
echo "4. Copy:"
echo "   - Project URL"
echo "   - anon public key"
echo "   - service_role key"
echo "5. Update backend/.env and frontend/.env.local"
```

### Issue: Frontend Dependencies Missing
```bash
cd frontend
npm install
cd ..
```

### Issue: Maven Build Failed
```bash
cd backend
mvn clean install -DskipTests
cd ..
```

---

## 📝 Pre-Run Checklist

Before running, verify:

- [ ] Java 21+ installed
- [ ] Maven 3.8+ installed
- [ ] Node.js 18+ installed
- [ ] PostgreSQL running OR Docker available
- [ ] `backend/.env` exists with Supabase credentials
- [ ] `frontend/.env.local` exists with Supabase credentials
- [ ] Ports 3000, 5432, and 8080 are free

---

## 🔄 Full Clean Restart

If things are broken, do a full clean restart:

```bash
# 1. Stop everything
pkill -f "spring-boot:run"
pkill -f "next dev"
docker stop easyluxury-db 2>/dev/null

# 2. Clean build artifacts
cd backend
mvn clean
cd ../frontend
rm -rf .next node_modules/.cache
cd ..

# 3. Restart PostgreSQL
docker start easyluxury-db 2>/dev/null || docker run -d --name easyluxury-db \
  -e POSTGRES_DB=easyluxury \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:14

# 4. Wait for PostgreSQL
sleep 5

# 5. Run migrations
cd backend
mvn liquibase:update
cd ..

# 6. Start backend
cd backend
mvn spring-boot:run &
cd ..

# 7. Wait for backend
sleep 10

# 8. Start frontend
cd frontend
npm run dev &
cd ..

# 9. Display status
echo "Services restarted!"
```

---

## 🎯 When User Says "run"

Execute this script:

```bash
#!/bin/bash
# Auto-run script for Cursor AI

echo "🚀 Starting EasyLuxury Platform..."
echo ""

# Step 1: Check prerequisites
echo "Step 1: Checking prerequisites..."
command -v java >/dev/null 2>&1 || { echo "❌ Java not found"; exit 1; }
command -v mvn >/dev/null 2>&1 || { echo "❌ Maven not found"; exit 1; }
command -v node >/dev/null 2>&1 || { echo "❌ Node.js not found"; exit 1; }
echo "✓ Prerequisites OK"
echo ""

# Step 2: Check environment files
echo "Step 2: Checking environment files..."
if [ ! -f "backend/.env" ]; then
    echo "❌ backend/.env not found!"
    echo "Creating template... PLEASE UPDATE WITH YOUR CREDENTIALS"
    cat > backend/.env << 'EOF'
DATABASE_URL=jdbc:postgresql://localhost:5432/easyluxury
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=postgres
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_API_KEY=your_anon_key_here
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key_here
SPRING_PROFILES_ACTIVE=dev
EOF
    echo "⚠️  STOP: Edit backend/.env before continuing!"
    exit 1
fi

if [ ! -f "frontend/.env.local" ]; then
    echo "❌ frontend/.env.local not found!"
    echo "Creating template... PLEASE UPDATE WITH YOUR CREDENTIALS"
    cat > frontend/.env.local << 'EOF'
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your_anon_key_here
NEXT_PUBLIC_API_URL=http://localhost:8080
EOF
    echo "⚠️  STOP: Edit frontend/.env.local before continuing!"
    exit 1
fi
echo "✓ Environment files OK"
echo ""

# Step 3: Check PostgreSQL
echo "Step 3: Checking PostgreSQL..."
if pg_isready -h localhost -p 5432 >/dev/null 2>&1; then
    echo "✓ PostgreSQL running"
else
    echo "⚠️  PostgreSQL not running, starting Docker container..."
    docker run -d --name easyluxury-db \
      -e POSTGRES_DB=easyluxury \
      -e POSTGRES_USER=postgres \
      -e POSTGRES_PASSWORD=postgres \
      -p 5432:5432 \
      postgres:14 2>/dev/null || docker start easyluxury-db 2>/dev/null
    
    echo "Waiting for PostgreSQL to be ready..."
    sleep 5
fi
echo ""

# Step 4: Run migrations
echo "Step 4: Running database migrations..."
cd backend
mvn liquibase:update -q 2>/dev/null || echo "⚠️  Migrations may have failed"
cd ..
echo "✓ Migrations complete"
echo ""

# Step 5: Start backend
echo "Step 5: Starting backend..."
cd backend
mvn spring-boot:run > /tmp/backend.log 2>&1 &
BACKEND_PID=$!
cd ..
echo "Backend starting (PID: $BACKEND_PID)..."
echo ""

# Step 6: Wait and verify backend
echo "Step 6: Waiting for backend..."
for i in {1..30}; do
    if curl -s http://localhost:8080/api/health | grep -q "UP"; then
        echo "✓ Backend running"
        break
    fi
    sleep 1
done
echo ""

# Step 7: Start frontend
echo "Step 7: Starting frontend..."
cd frontend
npm run dev > /tmp/frontend.log 2>&1 &
FRONTEND_PID=$!
cd ..
echo "Frontend starting (PID: $FRONTEND_PID)..."
echo ""

# Step 8: Wait and verify frontend
echo "Step 8: Waiting for frontend..."
sleep 10
echo "✓ Frontend should be running"
echo ""

# Step 9: Display info
echo "================================"
echo "✅ Services Started"
echo "================================"
echo ""
echo "📱 Frontend:  http://localhost:3000"
echo "🔧 Backend:   http://localhost:8080"
echo "📚 Swagger:   http://localhost:8080/swagger-ui.html"
echo "💚 Health:    http://localhost:8080/api/health"
echo ""
echo "Process IDs:"
echo "  Backend:  $BACKEND_PID"
echo "  Frontend: $FRONTEND_PID"
echo ""
echo "To view logs:"
echo "  Backend:  tail -f /tmp/backend.log"
echo "  Frontend: tail -f /tmp/frontend.log"
echo ""
echo "To stop services:"
echo "  kill $BACKEND_PID $FRONTEND_PID"
echo "  OR run: ./STOP_SERVICES.sh"
echo ""
```

---

## 📋 Summary for Cursor AI

When user says **"run"**, execute in this order:

1. ✅ Check prerequisites (Java, Maven, Node.js)
2. ✅ Verify environment files exist (create if missing, warn user)
3. ✅ Check PostgreSQL running (start Docker container if needed)
4. ✅ Run database migrations
5. ✅ Start backend (background process)
6. ✅ Wait and verify backend health
7. ✅ Start frontend (background process)
8. ✅ Display access URLs and instructions

**Important:** Cannot run long-lived processes directly, so provide commands for user to run in separate terminals.
