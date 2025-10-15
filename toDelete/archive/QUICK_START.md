# ⚡ Quick Start Guide

## 🎯 When You Say "run"

I will execute these commands:

```bash
./run.sh
```

This will:
1. ✅ Check prerequisites (Java, Maven, Node.js)
2. ✅ Verify/create environment files
3. ✅ Start PostgreSQL (Docker container if needed)
4. ✅ Run database migrations
5. ✅ Start backend on port 8080
6. ✅ Start frontend on port 3000
7. ✅ Display access URLs

---

## 📋 Available Commands

### Start Everything
```bash
./run.sh
```

### Check Status
```bash
./status.sh
```

### Stop Everything
```bash
./stop.sh
```

---

## 🔑 First Time Setup

### 1. Get Supabase Credentials

1. Go to https://supabase.com
2. Create a project (free tier)
3. Go to **Settings → API**
4. Copy these values:
   - **Project URL**
   - **anon public** key
   - **service_role** key

### 2. Configure Backend

Run the script first to create template:
```bash
./run.sh
```

It will create `backend/.env` - then edit it:
```bash
nano backend/.env
```

Update with your Supabase credentials:
```bash
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_API_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
SUPABASE_SERVICE_ROLE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 3. Configure Frontend

Edit `frontend/.env.local`:
```bash
nano frontend/.env.local
```

Use the same Supabase credentials:
```bash
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 4. Run Again
```bash
./run.sh
```

---

## 🌐 Access Points

Once running:

| Service | URL |
|---------|-----|
| **Frontend** | http://localhost:3000 |
| **Backend API** | http://localhost:8080 |
| **Swagger Docs** | http://localhost:8080/swagger-ui.html |
| **Health Check** | http://localhost:8080/api/health |

---

## 🧪 Test the App

### 1. Register a User
- Go to http://localhost:3000/register
- Create account with email/password

### 2. Login
- Go to http://localhost:3000/login
- Login with your credentials

### 3. Apply for Agency
- Go to http://localhost:3000/owner/settings
- Fill out agency application
- Submit (will be PENDING)

### 4. Create Admin User
```bash
psql -U postgres -d easyluxury
UPDATE users SET role = 'ADMIN' WHERE email = 'your@email.com';
\q
```

### 5. Approve Agency
- Login as admin
- Go to http://localhost:3000/admin/agencies
- Approve or reject the application

---

## 🛑 Troubleshooting

### Port Already in Use
```bash
# Kill process on port 8080 (backend)
lsof -ti:8080 | xargs kill -9

# Kill process on port 3000 (frontend)
lsof -ti:3000 | xargs kill -9

# Then run again
./run.sh
```

### PostgreSQL Not Running
```bash
# Start Docker container
docker run -d --name easyluxury-db \
  -e POSTGRES_DB=easyluxury \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:14

# Or start existing container
docker start easyluxury-db
```

### Services Won't Start
```bash
# Check status
./status.sh

# View logs
tail -f /tmp/easyluxury-backend.log
tail -f /tmp/easyluxury-frontend.log

# Clean restart
./stop.sh
./run.sh
```

---

## 📝 Development Workflow

### Backend Changes
```bash
# Backend auto-reloads with Spring Boot DevTools
# Just save your Java files
```

### Frontend Changes
```bash
# Next.js has hot-reload
# Just save your .tsx/.ts files
```

### Database Changes
```bash
# Create new migration
cd backend
# Edit src/main/resources/db/changelog/V003__your_feature.yaml
# Run migration
mvn liquibase:update
```

### View Logs
```bash
# Backend logs
tail -f /tmp/easyluxury-backend.log

# Frontend logs
tail -f /tmp/easyluxury-frontend.log
```

---

## 🔄 Common Tasks

### Restart Everything
```bash
./stop.sh
./run.sh
```

### Check What's Running
```bash
./status.sh
```

### Clean Database
```bash
# Stop everything
./stop.sh

# Drop database
psql -U postgres -c "DROP DATABASE easyluxury;"
psql -U postgres -c "CREATE DATABASE easyluxury;"

# Start and run migrations
./run.sh
```

### Update Dependencies
```bash
# Backend
cd backend
mvn clean install

# Frontend
cd frontend
npm install
```

---

## 📚 More Information

- **Full Setup Guide**: `SETUP_GUIDE.md`
- **Implementation Details**: `IMPLEMENTATION_SUMMARY.md`
- **How to Run (Detailed)**: `HOW_TO_RUN.md`
- **Backend README**: `backend/README.md`

---

## 💡 Pro Tips

1. **Keep terminal open** - Services run in background
2. **Check status often** - Use `./status.sh`
3. **View logs** - If something fails, check logs in `/tmp/`
4. **Supabase dashboard** - Monitor users at https://supabase.com
5. **Swagger UI** - Test API at http://localhost:8080/swagger-ui.html

---

## 🎯 That's It!

Just run:
```bash
./run.sh
```

And visit: http://localhost:3000

🚀 Happy coding!
