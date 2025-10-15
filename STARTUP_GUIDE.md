# EasyLuxury Platform - Simplified Startup Scripts

## Overview

The EasyLuxury platform now uses a simplified script structure to eliminate confusion and redundancy. Instead of multiple overlapping scripts, we now have **4 core scripts** that handle all startup scenarios.

## Core Scripts

### 🚀 `./dev.sh` - Development Mode
**Start everything in development mode with mock authentication**

- ✅ Backend with dev profile
- ✅ Frontend with mock auth enabled
- ✅ Mock user tester enabled
- ✅ Debug mode enabled
- ✅ Auto-creates environment templates
- ✅ Starts PostgreSQL and MinIO containers
- ✅ Runs database migrations
- ✅ Installs dependencies

**Usage:**
```bash
./dev.sh
```

### 🏭 `./prod.sh` - Production Mode
**Start everything in production mode with Supabase authentication**

- ✅ Backend with prod profile
- ✅ Frontend with Supabase auth
- ✅ Production build optimization
- ✅ Mock features disabled
- ✅ Validates production environment
- ✅ Starts PostgreSQL and MinIO containers
- ✅ Runs database migrations
- ✅ Builds optimized frontend

**Usage:**
```bash
./prod.sh
```

### 📊 `./status.sh` - Service Status
**Check the status of all running services**

- ✅ Backend health check
- ✅ Frontend availability
- ✅ Database status
- ✅ Port usage
- ✅ Configuration files
- ✅ Docker containers

**Usage:**
```bash
./status.sh
```

### 🛑 `./stop.sh` - Stop Services
**Stop all running EasyLuxury services**

- ✅ Stops backend process
- ✅ Stops frontend process
- ✅ Cleans up PID files
- ✅ Provides Docker stop commands

**Usage:**
```bash
./stop.sh
```

## Quick Start

### Development
```bash
# Start everything in development mode
./dev.sh

# Check status
./status.sh

# Stop when done
./stop.sh
```

### Production
```bash
# Start everything in production mode
./prod.sh

# Check status
./status.sh

# Stop when done
./stop.sh
```

## Environment Configuration

### Development Environment
The `dev.sh` script automatically creates:
- `backend/.env` with dev profile
- `frontend/.env.local` with mock auth enabled

### Production Environment
The `prod.sh` script requires:
- `backend/.env` with production settings
- `frontend/.env.local` with Supabase credentials

## Access Points

Once started, access your application at:

- **Frontend**: http://localhost:3000
- **Backend API**: http://localhost:8080
- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/api/health
- **MinIO**: http://localhost:9000
- **MinIO Console**: http://localhost:9001

## Logs

View service logs:
```bash
# Backend logs
tail -f /tmp/easyluxury-backend.log

# Frontend logs
tail -f /tmp/easyluxury-frontend.log
```

## Legacy Scripts (Deprecated)

The following scripts are now **deprecated** and should not be used:

- ❌ `run.sh` - Use `./dev.sh` instead
- ❌ `RUN_BACKEND.sh` - Use `./dev.sh` or `./prod.sh`
- ❌ `RUN_FRONTEND.sh` - Use `./dev.sh` or `./prod.sh`
- ❌ `RUN_FRONTEND_DEV.sh` - Use `./dev.sh` instead
- ❌ `RUN_FRONTEND_PROD.sh` - Use `./prod.sh` instead
- ❌ `START_SERVICES.sh` - Use `./dev.sh` or `./prod.sh`

## Database Management

### Clean Database
```bash
./CLEAN_DATABASE.sh
```

This script clears all data from the database for development purposes.

## Troubleshooting

### Port Conflicts
If ports 3000 or 8080 are in use, the scripts will automatically stop existing processes.

### Missing Dependencies
The scripts check for and install required dependencies automatically.

### Environment Issues
- Development: Scripts create templates automatically
- Production: Scripts validate required configuration

### Service Not Starting
1. Check logs: `tail -f /tmp/easyluxury-backend.log`
2. Check status: `./status.sh`
3. Restart: `./stop.sh && ./dev.sh` (or `./prod.sh`)

## Migration from Old Scripts

If you were using the old scripts:

1. **From `run.sh`**: Use `./dev.sh`
2. **From `RUN_FRONTEND_DEV.sh`**: Use `./dev.sh`
3. **From `RUN_FRONTEND_PROD.sh`**: Use `./prod.sh`
4. **From `RUN_BACKEND.sh`**: Use `./dev.sh` or `./prod.sh`

The new scripts provide the same functionality with better error handling, clearer output, and unified configuration.
