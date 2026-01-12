# 🚀 Quick Deployment Guide

## ⚡ Fastest Way: Railway (Recommended)

**Time: ~5 minutes | Cost: Free tier available**

### Steps:

1. **Go to [railway.app](https://railway.app)** and sign up (GitHub login)

2. **New Project** → Deploy from GitHub repo

3. **Select your repository** → Railway auto-detects `Dockerfile`

4. **Add PostgreSQL:**
   - Click "+ New" → Database → PostgreSQL
   - Railway auto-connects it

5. **Set Environment Variables:**
   ```
   SPRING_PROFILES_ACTIVE=prod
   COHERE_API_KEY=your_cohere_api_key_here
   ```
   (DATABASE_URL is auto-set by Railway)

6. **Deploy** - Automatic! Railway builds and deploys on every push to `main`

**Done!** Your app will be live at `https://your-app-name.up.railway.app`

---

## 🎯 Alternative: Render (Also Easy)

**Time: ~10 minutes | Cost: Free tier available**

1. Go to [render.com](https://render.com) and sign up
2. New → Web Service → Connect GitHub
3. Select repository
4. Configure:
   - **Build Command:** `./mvnw clean package -DskipTests`
   - **Start Command:** `java -jar target/*.jar`
   - **Environment:** Java
5. Add PostgreSQL database (free tier)
6. Set environment variables:
   ```
   SPRING_PROFILES_ACTIVE=prod
   COHERE_API_KEY=your_key
   ```
7. Deploy

---

## 📦 What's Included

✅ **Dockerfile** - Multi-stage optimized build  
✅ **GitHub Actions** - Auto-deploy workflow (`.github/workflows/deploy.yml`)  
✅ **Railway config** - `railway.json`  
✅ **Render config** - `render.yaml`  
✅ **Production config** - `application-prod.yml`  
✅ **Health checks** - `/actuator/health` endpoint  

---

## 🔑 Required Environment Variables

- `COHERE_API_KEY` - Get from [cohere.com](https://cohere.com)
- `SPRING_PROFILES_ACTIVE=prod` - Use production config
- `DATABASE_URL` - Auto-set by platforms (PostgreSQL)

---

## ✅ Pre-Deployment Checklist

- [x] Dockerfile created
- [x] Production config created
- [x] Health endpoints enabled
- [x] Database configured (PostgreSQL)
- [x] Environment variables documented
- [x] GitHub Actions workflow ready
- [x] Platform configs (Railway/Render) ready

---

## 🎉 Your App is Deployment-Ready!

Just push to GitHub and connect to Railway or Render. That's it!
