# Deployment Guide - Subscription Management Hub

## 🚀 Quick Deployment Options

### **Recommended: Railway (Easiest - Free Tier Available)**

1. **Sign up** at [railway.app](https://railway.app)
2. **Connect GitHub** repository
3. **New Project** → Deploy from GitHub
4. **Select repository** → Railway auto-detects Dockerfile
5. **Add PostgreSQL** database (one-click)
6. **Set Environment Variables:**
   ```
   SPRING_PROFILES_ACTIVE=prod
   COHERE_API_KEY=your_cohere_key
   DATABASE_URL=postgresql://... (auto-set by Railway)
   ```
7. **Deploy** - Automatic on every push to main branch

**Time to deploy:** ~5 minutes

---

### **Alternative: Render (Free Tier Available)**

1. **Sign up** at [render.com](https://render.com)
2. **New** → Web Service
3. **Connect GitHub** repository
4. **Configure:**
   - Build Command: `./mvnw clean package -DskipTests`
   - Start Command: `java -jar target/*.jar`
   - Environment: `Java`
5. **Add PostgreSQL** database (free tier)
6. **Set Environment Variables:**
   ```
   SPRING_PROFILES_ACTIVE=prod
   COHERE_API_KEY=your_cohere_key
   ```
7. **Deploy**

**Time to deploy:** ~10 minutes

---

### **Alternative: Fly.io (Free Tier Available)**

1. **Install Fly CLI:**
   ```bash
   curl -L https://fly.io/install.sh | sh
   ```

2. **Login:**
   ```bash
   fly auth login
   ```

3. **Initialize:**
   ```bash
   cd subscription-management-hub
   fly launch
   ```

4. **Add PostgreSQL:**
   ```bash
   fly postgres create
   fly postgres attach <db-name>
   ```

5. **Set secrets:**
   ```bash
   fly secrets set COHERE_API_KEY=your_key
   fly secrets set SPRING_PROFILES_ACTIVE=prod
   ```

6. **Deploy:**
   ```bash
   fly deploy
   ```

**Time to deploy:** ~15 minutes

---

## 📦 Docker Deployment

### Build Docker Image
```bash
docker build -t subscription-hub:latest .
```

### Run Locally
```bash
docker run -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e COHERE_API_KEY=your_key \
  -e DATABASE_URL=jdbc:postgresql://host:5432/db \
  subscription-hub:latest
```

### Push to Docker Hub
```bash
docker tag subscription-hub:latest yourusername/subscription-hub:latest
docker push yourusername/subscription-hub:latest
```

---

## 🔧 Environment Variables

### Required
- `COHERE_API_KEY` - Cohere API key for text generation

### Optional (with defaults)
- `SPRING_PROFILES_ACTIVE=prod` - Use production profile
- `DATABASE_URL` - PostgreSQL connection string (auto-set by platforms)
- `PORT` - Server port (default: 8080)
- `VECTOR_STORAGE_PATH` - Vector storage path (default: ./data/vectors)

---

## 🗄️ Database Setup

### PostgreSQL (Production)
The application automatically uses PostgreSQL when `DATABASE_URL` is set.

### H2 (Development)
For local development, H2 file-based database is used (no setup needed).

---

## ✅ Health Checks

The application exposes health endpoints:
- **Health:** `http://your-app-url/actuator/health`
- **Info:** `http://your-app-url/actuator/info`

---

## 🔄 Continuous Deployment

### GitHub Actions
The repository includes `.github/workflows/deploy.yml` for automated deployment.

**To enable:**
1. Add secrets to GitHub repository:
   - `RAILWAY_TOKEN` (if using Railway)
   - `RENDER_API_KEY` (if using Render)
   - `FLY_API_TOKEN` (if using Fly.io)

2. Uncomment deployment steps in `deploy.yml`

3. Push to `main` branch → Auto-deploys

---

## 📊 Monitoring

### Application Logs
- **Railway:** View in dashboard
- **Render:** View in dashboard
- **Fly.io:** `fly logs`

### Database Access
- **Railway:** PostgreSQL connection string in dashboard
- **Render:** PostgreSQL connection string in dashboard
- **Fly.io:** `fly postgres connect`

---

## 🐛 Troubleshooting

### Build Fails
- Ensure AI Infrastructure modules are built first
- Check Java 21 is available
- Verify Maven dependencies

### Application Won't Start
- Check environment variables are set
- Verify database connection
- Check logs for errors

### Vector Search Not Working
- Ensure `VECTOR_STORAGE_PATH` is writable
- Check Lucene index permissions

---

## 🎯 Recommended Setup for Demo

**Best Choice: Railway**
- ✅ Free tier available
- ✅ Auto-deploys from GitHub
- ✅ Built-in PostgreSQL
- ✅ Zero configuration
- ✅ Custom domain support

**Quick Start:**
1. Fork/clone repository
2. Connect to Railway
3. Add Cohere API key
4. Deploy (automatic)

**Total time:** ~5 minutes

---

## 📝 Notes

- **Vector Storage:** Lucene uses local file system (persists in container)
- **Database:** PostgreSQL recommended for production
- **Embeddings:** ONNX runs locally (no API needed)
- **LLM:** Cohere requires API key
