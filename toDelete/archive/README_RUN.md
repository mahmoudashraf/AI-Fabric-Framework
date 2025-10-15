# 🚀 Run EasyLuxury Platform

## For Cursor AI: When User Says "run"

Execute this workflow:

### Step 1: Check Prerequisites
```bash
java -version    # Expect: 17+
mvn -version     # Expect: 3.8+
node -version    # Expect: 18+
```

### Step 2: Check Environment Setup
```bash
# Check if config files exist
ls backend/.env frontend/.env.local
```

**If missing**, inform user:
```
⚠️  Environment files not found!

First-time setup required:
1. Run: ./run.sh (it will create templates)
2. Get Supabase credentials from https://supabase.com
3. Edit backend/.env and frontend/.env.local
4. Run: ./run.sh again
```

### Step 3: Execute Run Script
```bash
./run.sh
```

### Step 4: Verify Services Started
```bash
# Check backend
curl http://localhost:8080/api/health

# Check frontend
curl http://localhost:3000
```

### Step 5: Display Access Info
```
✅ Services Running:

📱 Frontend:  http://localhost:3000
🔧 Backend:   http://localhost:8080  
📚 Swagger:   http://localhost:8080/swagger-ui.html

To check status: ./status.sh
To stop: ./stop.sh
```

---

## Scripts Reference

| Script | Purpose | Usage |
|--------|---------|-------|
| `./run.sh` | Start all services | `./run.sh` |
| `./stop.sh` | Stop all services | `./stop.sh` |
| `./status.sh` | Check service status | `./status.sh` |

---

## Manual Run (If Scripts Fail)

### Terminal 1 - Backend
```bash
cd backend
mvn spring-boot:run
```

### Terminal 2 - Frontend  
```bash
cd frontend
npm run dev
```

---

## Troubleshooting Guide

### Issue: "Environment files missing"
**Solution:**
1. Run `./run.sh` to create templates
2. Visit https://supabase.com to get credentials
3. Edit `backend/.env` and `frontend/.env.local`
4. Run `./run.sh` again

### Issue: "PostgreSQL not running"
**Solution:**
```bash
# Option 1: Docker
docker run -d --name easyluxury-db \
  -e POSTGRES_DB=easyluxury \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 postgres:14

# Option 2: Local PostgreSQL
createdb easyluxury
```

### Issue: "Port already in use"
**Solution:**
```bash
# Kill process on port
lsof -ti:8080 | xargs kill -9  # Backend
lsof -ti:3000 | xargs kill -9  # Frontend

# Then run again
./run.sh
```

### Issue: "Backend won't start"
**Solution:**
```bash
# Check logs
tail -f /tmp/easyluxury-backend.log

# Common fixes:
# - Verify database is running
# - Check backend/.env has correct credentials
# - Ensure port 8080 is free
```

### Issue: "Frontend won't start"
**Solution:**
```bash
# Check logs
tail -f /tmp/easyluxury-frontend.log

# Common fixes:
# - Run: cd frontend && npm install
# - Check frontend/.env.local exists
# - Ensure port 3000 is free
```

---

## Quick Commands Cheat Sheet

```bash
# Start everything
./run.sh

# Check what's running
./status.sh

# Stop everything
./stop.sh

# View logs
tail -f /tmp/easyluxury-backend.log
tail -f /tmp/easyluxury-frontend.log

# Restart
./stop.sh && ./run.sh

# Check health
curl http://localhost:8080/api/health
```

---

## Files Created

- ✅ `HOW_TO_RUN.md` - Detailed instructions for Cursor AI
- ✅ `QUICK_START.md` - User-friendly quick start
- ✅ `run.sh` - Auto-start script
- ✅ `stop.sh` - Stop all services
- ✅ `status.sh` - Check service status
- ✅ `backend/.env.example` - Backend config template
- ✅ `frontend/.env.example` - Frontend config template

---

## First Time Setup Checklist

- [ ] Java 21+ installed
- [ ] Maven 3.8+ installed  
- [ ] Node.js 18+ installed
- [ ] PostgreSQL running OR Docker available
- [ ] Supabase account created
- [ ] Supabase credentials obtained
- [ ] `backend/.env` configured
- [ ] `frontend/.env.local` configured
- [ ] Database created (`easyluxury`)
- [ ] Run `./run.sh`

---

## Success Indicators

When everything is working:

```bash
$ ./status.sh

Backend (Spring Boot):
  Status: ✓ Running
  URL:    http://localhost:8080

Frontend (Next.js):
  Status: ✓ Running
  URL:    http://localhost:3000

PostgreSQL Database:
  Status: ✓ Running
  Port:   5432

✅ All Services Running
```

---

## What Services Do

| Service | Purpose | Port |
|---------|---------|------|
| **PostgreSQL** | Database for users, agencies, etc. | 5432 |
| **Backend** | Spring Boot REST API | 8080 |
| **Frontend** | Next.js React application | 3000 |
| **Supabase** | External auth service | N/A |

---

## Data Flow

```
User → Frontend (3000) 
     → Backend (8080) 
     → PostgreSQL (5432)
     
     Frontend ↔ Supabase (Auth)
```

---

## For More Help

- **Quick Start**: See `QUICK_START.md`
- **Full Setup**: See `SETUP_GUIDE.md`  
- **Implementation**: See `IMPLEMENTATION_SUMMARY.md`
- **Backend Docs**: See `backend/README.md`

---

**When user says "run", I will:**
1. Check prerequisites
2. Verify environment files
3. Execute `./run.sh`
4. Display access URLs
5. Provide troubleshooting if needed

✅ Ready to run!
