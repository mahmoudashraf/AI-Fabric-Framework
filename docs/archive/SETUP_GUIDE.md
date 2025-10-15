# EasyLuxury Setup Guide

Complete setup instructions for Plan 1 implementation.

## Prerequisites

- **Java 21** or higher
- **Node.js 18+** and npm
- **PostgreSQL 14+**
- **Maven 3.8+**
- **Supabase Account** (for authentication)
- **Git**

## Quick Start with Docker

If you have Docker and Docker Compose installed:

```bash
# 1. Set environment variables
cp backend/.env.example backend/.env
# Edit backend/.env with your Supabase credentials

# 2. Start services
docker-compose up -d

# 3. Run migrations
cd backend
mvn liquibase:update

# 4. Access the application
# Backend: http://localhost:8080
# Swagger UI: http://localhost:8080/swagger-ui.html
```

## Manual Setup

### 1. Supabase Setup

1. **Create Supabase Project**
   - Go to https://supabase.com
   - Create a new project
   - Wait for the project to be provisioned

2. **Get Credentials**
   - Go to Project Settings → API
   - Copy:
     - Project URL (e.g., `https://xxxxx.supabase.co`)
     - `anon` public key
     - `service_role` secret key

3. **Configure Authentication**
   - Go to Authentication → Settings
   - Enable Email authentication
   - (Optional) Enable OAuth providers (Google, Facebook, Apple)

### 2. Backend Setup

```bash
# Navigate to backend directory
cd backend

# Create PostgreSQL database
createdb easyluxury

# Or use psql
psql -U postgres
CREATE DATABASE easyluxury;
\q

# Configure environment
cp .env.example .env
# Edit .env with your credentials
nano .env

# Set these values:
DATABASE_URL=jdbc:postgresql://localhost:5432/easyluxury
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=your_postgres_password
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_API_KEY=your_anon_key
SUPABASE_SERVICE_ROLE_KEY=your_service_role_key

# Run database migrations
mvn liquibase:update

# Build the project
mvn clean install

# Run the backend
mvn spring-boot:run

# Backend will start on http://localhost:8080
```

**Verify Backend:**
- Open http://localhost:8080/swagger-ui.html
- You should see the API documentation
- Check http://localhost:8080/actuator/health

### 3. Frontend Setup

```bash
# Navigate to frontend directory
cd frontend

# Install dependencies (Supabase already installed)
npm install

# Configure environment
cp .env.example .env.local
# Edit .env.local
nano .env.local

# Set these values:
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your_anon_key
NEXT_PUBLIC_API_URL=http://localhost:8080

# Run the frontend
npm run dev

# Frontend will start on http://localhost:3000
```

**Verify Frontend:**
- Open http://localhost:3000
- You should see the landing page
- Navigate to http://localhost:3000/login

### 4. Create Admin User

Since the first user needs to be an admin, you'll need to manually create one:

**Option A: Via Supabase Dashboard**
1. Register a user at http://localhost:3000/register
2. Go to Supabase Dashboard → Authentication → Users
3. Copy the user's UUID
4. Go to SQL Editor and run:

```sql
-- Update the user role to ADMIN
UPDATE users 
SET role = 'ADMIN' 
WHERE supabase_id = 'user-uuid-from-supabase';
```

**Option B: Via Database**
1. Register a user
2. Connect to your database:
```bash
psql -U postgres -d easyluxury
```
3. Run:
```sql
UPDATE users SET role = 'ADMIN' WHERE email = 'your-email@example.com';
```

### 5. Test the Application

#### Test Authentication
1. Go to http://localhost:3000/register
2. Create a new account with email and password
3. Login at http://localhost:3000/login
4. Verify you're redirected to dashboard

#### Test Agency Application (Owner)
1. Login as a non-admin user
2. Navigate to http://localhost:3000/owner/settings
3. Fill out agency application:
   - Agency Name: "Test Agency"
   - Description: "This is a test agency"
4. Submit
5. Verify you see "PENDING" status

#### Test Agency Approval (Admin)
1. Logout and login as admin user
2. Navigate to http://localhost:3000/admin/agencies
3. You should see the pending agency
4. Click "Approve" or "Reject"
5. For rejection, provide a reason
6. Verify the agency status updates

## Troubleshooting

### Backend Issues

**Problem: Database connection failed**
```
Solution:
1. Verify PostgreSQL is running: systemctl status postgresql
2. Check database exists: psql -U postgres -l
3. Verify credentials in .env
```

**Problem: Liquibase migration failed**
```
Solution:
1. Check liquibase.properties
2. Verify database user has CREATE privileges
3. Run: mvn liquibase:status to see pending changes
```

**Problem: JWT verification failed**
```
Solution:
1. Verify SUPABASE_URL is correct (no trailing slash)
2. Check SUPABASE_API_KEY matches your project
3. Ensure user exists in backend users table
```

### Frontend Issues

**Problem: Cannot connect to backend**
```
Solution:
1. Verify backend is running on port 8080
2. Check NEXT_PUBLIC_API_URL in .env.local
3. Check browser console for CORS errors
```

**Problem: Supabase authentication fails**
```
Solution:
1. Verify Supabase credentials in .env.local
2. Check Supabase project is active
3. Verify email authentication is enabled in Supabase dashboard
```

**Problem: React Query errors**
```
Solution:
1. Clear browser cache and cookies
2. Check Network tab for API call failures
3. Verify JWT token is being sent in Authorization header
```

## Development Workflow

### Backend Development

```bash
# Run in dev mode with hot reload
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# Run tests
mvn test

# Format code
mvn spring-javaformat:apply
```

### Frontend Development

```bash
# Run dev server
cd frontend
npm run dev

# Run tests
npm run test

# Type check
npm run type-check

# Lint
npm run lint
```

### Database Migrations

**Create a new migration:**

1. Create file: `backend/src/main/resources/db/changelog/V003__your_feature.yaml`

2. Add to master changelog:
```yaml
# db.changelog-master.yaml
databaseChangeLog:
  - include:
      file: db/changelog/V001__users.yaml
  - include:
      file: db/changelog/V002__agencies.yaml
  - include:
      file: db/changelog/V003__your_feature.yaml  # NEW
```

3. Run migration:
```bash
mvn liquibase:update
```

## Environment Variables Reference

### Backend (.env)
| Variable | Description | Example |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL JDBC URL | `jdbc:postgresql://localhost:5432/easyluxury` |
| `DATABASE_USERNAME` | Database user | `postgres` |
| `DATABASE_PASSWORD` | Database password | `your_password` |
| `SUPABASE_URL` | Supabase project URL | `https://xxxxx.supabase.co` |
| `SUPABASE_API_KEY` | Supabase anon key | `eyJhbGc...` |
| `SUPABASE_SERVICE_ROLE_KEY` | Supabase service role key | `eyJhbGc...` |
| `SPRING_PROFILES_ACTIVE` | Spring profile | `dev` or `prod` |

### Frontend (.env.local)
| Variable | Description | Example |
|----------|-------------|---------|
| `NEXT_PUBLIC_SUPABASE_URL` | Supabase project URL | `https://xxxxx.supabase.co` |
| `NEXT_PUBLIC_SUPABASE_ANON_KEY` | Supabase anon key | `eyJhbGc...` |
| `NEXT_PUBLIC_API_URL` | Backend API base URL | `http://localhost:8080` |

## API Documentation

Once the backend is running, access:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

## Next Steps

After successful setup:

1. ✅ Create admin user
2. ✅ Test authentication flow
3. ✅ Test agency application workflow
4. ✅ Test admin approval workflow
5. 📝 Add seed data for development
6. 📝 Write unit tests
7. 📝 Write integration tests
8. 📝 Set up CI/CD pipeline

## Additional Resources

- [Backend README](backend/README.md) - Detailed backend documentation
- [Implementation Summary](IMPLEMENTATION_SUMMARY.md) - Complete feature list
- [Plan 1 Document](planning/batches/phase1/batch1_plan.md) - Original plan
- [UI Adaptation Guide](docs/UI%20Adaptation/) - Frontend patterns

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Review the implementation summary
3. Check API documentation in Swagger UI
4. Review backend logs for errors

---

**Setup Complete!** You should now have both frontend and backend running and communicating with each other.
