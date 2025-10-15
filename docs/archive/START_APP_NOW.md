# 🚀 START THE APP - Easy Luxury

## ⚠️ Prerequisites Status

| Item | Status | Action Needed |
|------|--------|---------------|
| Java 21 | ✅ Installed | None |
| Maven | ❌ Missing | **Install first** |
| Node.js 22 | ✅ Installed | None |
| PostgreSQL | ⚠️ Not detected | **Start Docker container** |
| Supabase Config | ✅ Done | None |

---

## 🔧 STEP 1: Install Maven (Required)

Choose one method:

### Option A: Using apt (Recommended)
```bash
sudo apt update
sudo apt install maven -y
mvn -version
```

### Option B: Using sdkman
```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install maven
```

---

## 🐘 STEP 2: Start PostgreSQL

```bash
docker run -d \
  --name easyluxury-db \
  -e POSTGRES_DB=easyluxury \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:14

# Wait for it to start
sleep 5
```

**Verify it's running:**
```bash
docker ps | grep easyluxury-db
```

---

## 🎯 STEP 3: Run the Application

### Terminal 1 - Backend (Spring Boot)

```bash
cd /workspace/backend

# Run database migrations (first time only)
mvn liquibase:update

# Start backend
mvn spring-boot:run
```

**Expected output:**
```
Started EasyLuxuryApplication in X.XXX seconds
```

**Backend will be available at:** http://localhost:8080

---

### Terminal 2 - Frontend (Next.js)

Open a **NEW terminal** and run:

```bash
cd /workspace/frontend

# Install dependencies (if not already done)
npm install

# Start frontend
npm run dev
```

**Expected output:**
```
▲ Next.js 14.x.x
- Local:        http://localhost:3000
```

**Frontend will be available at:** http://localhost:3000

---

## ✅ Verify Everything Works

### 1. Check Backend Health
```bash
curl http://localhost:8080/api/health
```

**Expected:**
```json
{
  "status": "UP",
  "timestamp": "...",
  "service": "Easy Luxury Backend",
  "version": "1.0.0"
}
```

### 2. Check Frontend
Open browser: http://localhost:3000

### 3. Check Swagger API Docs
Open browser: http://localhost:8080/swagger-ui.html

---

## 🧪 Test the Complete Flow

### 1. Register a User
- Go to http://localhost:3000/register
- Enter email: `test@example.com`
- Enter password: `password123`
- Click "Sign Up"

### 2. Login
- Go to http://localhost:3000/login
- Login with same credentials

### 3. Apply for Agency
- You'll be redirected to dashboard
- Go to http://localhost:3000/owner/settings
- Fill out agency application form
- Submit (status will be PENDING)

### 4. Create Admin User
Open new terminal:
```bash
docker exec -it easyluxury-db psql -U postgres -d easyluxury

-- In psql:
UPDATE users SET role = 'ADMIN' WHERE email = 'test@example.com';
\q
```

### 5. Approve Agency (as Admin)
- Refresh the page
- Go to http://localhost:3000/admin/agencies
- You'll see the pending agency
- Click "Approve" or "Reject"

---

## 🛑 How to Stop

### Stop Backend
In the terminal running the backend, press: `Ctrl + C`

### Stop Frontend
In the terminal running the frontend, press: `Ctrl + C`

### Stop PostgreSQL
```bash
docker stop easyluxury-db
```

---

## 📊 Access Points Summary

| Service | URL | Description |
|---------|-----|-------------|
| **Frontend** | http://localhost:3000 | Main application |
| **Backend API** | http://localhost:8080 | REST API |
| **Swagger UI** | http://localhost:8080/swagger-ui.html | API documentation |
| **Health Check** | http://localhost:8080/api/health | Backend status |

---

## 🐛 Troubleshooting

### Backend won't start
```bash
# Check if port 8080 is in use
lsof -ti:8080 | xargs kill -9

# Check PostgreSQL is running
docker ps | grep easyluxury-db

# Check logs
cd backend
mvn spring-boot:run
# Look for errors
```

### Frontend won't start
```bash
# Check if port 3000 is in use
lsof -ti:3000 | xargs kill -9

# Reinstall dependencies
cd frontend
rm -rf node_modules
npm install

# Try again
npm run dev
```

### Database connection error
```bash
# Restart PostgreSQL
docker stop easyluxury-db
docker rm easyluxury-db

docker run -d \
  --name easyluxury-db \
  -e POSTGRES_DB=easyluxury \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:14
```

---

## 📝 Quick Reference

### Start Everything
```bash
# Terminal 1: PostgreSQL
docker start easyluxury-db || docker run -d --name easyluxury-db \
  -e POSTGRES_DB=easyluxury -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 postgres:14

# Terminal 2: Backend
cd /workspace/backend && mvn spring-boot:run

# Terminal 3: Frontend
cd /workspace/frontend && npm run dev
```

### Check Status
```bash
# Backend
curl http://localhost:8080/api/health

# Frontend
curl http://localhost:3000

# PostgreSQL
docker ps | grep easyluxury-db
```

---

## ✅ Configuration Files

All configuration is ready:

- ✅ `backend/.env` - Supabase credentials configured
- ✅ `frontend/.env.local` - Supabase credentials configured
- ✅ Supabase project: easy-luxury
- ✅ Database: easyluxury (PostgreSQL)

---

## 🎯 Your Next Steps

**Right now, you need to:**

1. **Install Maven** (5 minutes):
   ```bash
   sudo apt update && sudo apt install maven -y
   ```

2. **Start PostgreSQL** (1 minute):
   ```bash
   docker run -d --name easyluxury-db \
     -e POSTGRES_DB=easyluxury \
     -e POSTGRES_PASSWORD=postgres \
     -p 5432:5432 postgres:14
   ```

3. **Run Backend** (Terminal 1):
   ```bash
   cd /workspace/backend
   mvn spring-boot:run
   ```

4. **Run Frontend** (Terminal 2):
   ```bash
   cd /workspace/frontend
   npm run dev
   ```

5. **Open Browser**:
   - http://localhost:3000

**That's it!** 🚀

---

## 💡 Pro Tips

- Keep both terminals open while developing
- Backend auto-reloads on Java file changes
- Frontend auto-reloads on .tsx file changes
- Check logs in the terminals if something fails
- Use Swagger UI to test backend APIs directly
- PostgreSQL data persists in Docker volume

---

**Everything is configured and ready. Just install Maven and start!** ✅
