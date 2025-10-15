# Frontend Development Profile Configuration Guide

This comprehensive guide explains how to use the development profile configuration for the frontend to enable mock authentication and avoid Supabase email bounce issues.

## 🎯 Overview

The frontend now supports environment-specific configurations that automatically switch between mock authentication (development) and Supabase authentication (production). This completely eliminates email bounce issues during development.

## 📁 Configuration Files

### Environment Files
- `.env.development` - Development configuration with mock auth enabled
- `.env.production` - Production configuration with Supabase auth enabled

### Configuration Service
- `src/config/authConfig.ts` - Centralized authentication configuration service

### Scripts
- `RUN_FRONTEND_DEV.sh` - Development mode runner with mock auth
- `RUN_FRONTEND_PROD.sh` - Production mode runner with Supabase auth

## 🚀 Quick Start

### Development Mode (Mock Authentication)
```bash
# Option 1: Use the development script (Recommended)
./RUN_FRONTEND_DEV.sh

# Option 2: Manual setup
export NODE_ENV=development
export NEXT_PUBLIC_MOCK_AUTH_ENABLED=true
export NEXT_PUBLIC_ENVIRONMENT=development
npm run dev
```

### Production Mode (Supabase Authentication)
```bash
# Option 1: Use the production script
./RUN_FRONTEND_PROD.sh

# Option 2: Manual setup
export NODE_ENV=production
export NEXT_PUBLIC_MOCK_AUTH_ENABLED=false
export NEXT_PUBLIC_ENVIRONMENT=production
npm run build && npm start
```

## 🔧 Configuration Options

### Development Environment Variables
```bash
# Core Configuration
NEXT_PUBLIC_API_URL=http://localhost:8080
NEXT_PUBLIC_MOCK_AUTH_ENABLED=true
NEXT_PUBLIC_ENVIRONMENT=development

# Mock Authentication Settings
NEXT_PUBLIC_MOCK_USERS_ENDPOINT=/api/mock/users
NEXT_PUBLIC_MOCK_AUTH_ENDPOINT=/api/mock/auth

# Supabase Configuration (disabled in dev)
NEXT_PUBLIC_SUPABASE_URL=
NEXT_PUBLIC_SUPABASE_ANON_KEY=

# Development Features
NEXT_PUBLIC_ENABLE_MOCK_USER_TESTER=true
NEXT_PUBLIC_DEBUG_MODE=true
```

### Production Environment Variables
```bash
# Core Configuration
NEXT_PUBLIC_API_URL=https://api.easyluxury.com
NEXT_PUBLIC_MOCK_AUTH_ENABLED=false
NEXT_PUBLIC_ENVIRONMENT=production

# Supabase Configuration (enabled in production)
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-anon-key

# Production Features
NEXT_PUBLIC_ENABLE_MOCK_USER_TESTER=false
NEXT_PUBLIC_DEBUG_MODE=false
```

## 🧪 Mock Authentication Features

### Available Mock Users
| Role | Email | Password | Description |
|------|-------|----------|-------------|
| **Admin** | `admin@easyluxury.com` | `admin123` | Full system access |
| **Owner** | `john.doe@example.com` | `owner123` | Property owner |
| **Agency Owner** | `sarah.wilson@luxuryrealty.com` | `agency123` | Real estate agency owner |
| **Agency Member** | `mike.chen@luxuryrealty.com` | `member123` | Agency staff member |
| **Tenant** | `emma.johnson@example.com` | `tenant123` | Property tenant |

### Mock User Tester Component
When `NEXT_PUBLIC_ENABLE_MOCK_USER_TESTER=true`, you can access the mock user tester at:
- **URL**: `http://localhost:3000/mock-users`
- **Features**:
  - Quick switching between different user roles
  - Perfect for testing role-based features
  - Visual user information display
  - One-click login/logout

## 🔄 How It Works

### Authentication Flow
1. **Development Mode**: 
   - Checks `authConfig.shouldUseMockAuth()` 
   - If true, uses mock authentication endpoints
   - Falls back to Supabase if mock auth fails

2. **Production Mode**:
   - Always uses Supabase authentication
   - Mock authentication is completely disabled

### Configuration Service API
The `authConfig` service provides these methods:

```typescript
// Core Methods
authConfig.shouldUseMockAuth()     // Determines if mock auth should be used
authConfig.getMockAuthUrl()        // Returns the mock auth endpoint URL
authConfig.getApiUrl()             // Returns the API base URL
authConfig.isDevelopment()         // Checks if in development mode
authConfig.isProduction()          // Checks if in production mode

// Debug Methods
authConfig.log(message, ...args)   // Debug logging (only in debug mode)
```

## 🐛 Debugging

### Enable Debug Logging
Set `NEXT_PUBLIC_DEBUG_MODE=true` to see detailed authentication logs in the browser console.

**Example Debug Output:**
```
[AuthConfig] Using mock authentication for login
[AuthConfig] Mock login successful admin@easyluxury.com
[AuthConfig] ✅ Mock user profile fetched successfully: {id: "...", email: "..."}
```

### Common Issues & Solutions

#### 1. Mock Auth Not Working
**Problem**: Mock authentication not being used
**Solution**: 
- Check `NEXT_PUBLIC_MOCK_AUTH_ENABLED=true`
- Verify `NEXT_PUBLIC_ENVIRONMENT=development`
- Ensure backend is running with dev profile

#### 2. Backend Not Responding
**Problem**: 500/403 errors from mock endpoints
**Solution**:
- Ensure backend is running: `./RUN_BACKEND.sh`
- Check backend logs for errors
- Verify dev profile is active: `mvn spring-boot:run -Dspring-boot.run.profiles=dev`

#### 3. CORS Errors
**Problem**: Cross-origin request blocked
**Solution**:
- Check backend CORS configuration
- Ensure frontend runs on `http://localhost:3000`
- Verify backend runs on `http://localhost:8080`

#### 4. Token Issues
**Problem**: "Invalid or expired mock token"
**Solution**:
- Clear localStorage: `localStorage.clear()`
- Re-login with mock credentials
- Check token format in browser dev tools

## 🔒 Security Notes

- Mock authentication is **ONLY** available in development mode
- Production builds automatically disable mock authentication
- Mock tokens are stored in localStorage (development only)
- All mock endpoints are protected by `@Profile("dev")` on the backend
- No real user data is exposed in mock authentication

## 📝 Usage Examples

### Programmatic Mock Login
```typescript
import { authConfig } from '@/config/authConfig';

async function loginAsAdmin() {
  if (authConfig.shouldUseMockAuth()) {
    const response = await fetch(`${authConfig.getMockAuthUrl()}/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ 
        email: 'admin@easyluxury.com', 
        password: 'admin123' 
      })
    });
    const data = await response.json();
    localStorage.setItem('mock_token', data.token);
  }
}
```

### Conditional Rendering
```typescript
import { authConfig } from '@/config/authConfig';

function MyComponent() {
  return (
    <div>
      {authConfig.isDevelopment() && (
        <div>
          <h3>Development Tools</h3>
          <MockUserTester />
        </div>
      )}
      
      {authConfig.isProduction() && (
        <div>
          <h3>Production Features</h3>
          <ProductionOnlyComponent />
        </div>
      )}
    </div>
  );
}
```

### Environment-Specific API Calls
```typescript
import { authConfig } from '@/config/authConfig';

async function fetchUserData() {
  const url = authConfig.shouldUseMockAuth() 
    ? `${authConfig.getMockAuthUrl()}/current`
    : `${authConfig.getApiUrl()}/users/me`;
    
  const response = await fetch(url);
  return response.json();
}
```

## 🎉 Benefits

### For Development
1. **No Email Dependencies**: Test authentication without Supabase email setup
2. **Role Testing**: Easily test different user roles and permissions
3. **Fast Iteration**: No waiting for email confirmations or password resets
4. **Debug Friendly**: Detailed logging and mock user tester component
5. **Offline Development**: Works without internet connection

### For Production
1. **Environment Isolation**: Clear separation between dev and prod configurations
2. **Production Safe**: Mock auth automatically disabled in production builds
3. **Scalable**: Easy to add new mock users or modify existing ones
4. **Maintainable**: Centralized configuration management

## 🔧 Advanced Configuration

### Custom Mock Users
To add custom mock users, modify the backend `MockUserService.java`:

```java
private final Map<String, MockCredentials> mockCredentials = Map.of(
    "admin@easyluxury.com", new MockCredentials("admin@easyluxury.com", "admin123", User.UserRole.ADMIN),
    "custom@example.com", new MockCredentials("custom@example.com", "custom123", User.UserRole.OWNER),
    // Add more users here
);
```

### Custom Environment Variables
Add custom environment variables to `.env.development`:

```bash
# Custom Development Settings
NEXT_PUBLIC_CUSTOM_FEATURE_ENABLED=true
NEXT_PUBLIC_API_TIMEOUT=5000
NEXT_PUBLIC_LOG_LEVEL=debug
```

Then access them in `authConfig.ts`:

```typescript
export interface AuthConfig {
  // ... existing properties
  customFeatureEnabled: boolean;
  apiTimeout: number;
  logLevel: string;
}
```

## 📊 Monitoring & Analytics

### Development Metrics
- Mock login success rate
- Token validation performance
- API response times
- Error frequency by endpoint

### Production Metrics
- Supabase authentication success rate
- User session duration
- Failed login attempts
- OAuth provider usage

## 🚀 Deployment

### Development Deployment
```bash
# Local development
./RUN_FRONTEND_DEV.sh

# Docker development
docker-compose -f docker-compose.dev.yml up
```

### Production Deployment
```bash
# Production build
./RUN_FRONTEND_PROD.sh

# Docker production
docker-compose -f docker-compose.prod.yml up
```

## 📚 Additional Resources

- [Backend Mock Authentication Guide](./MOCK_USER_TESTING_GUIDE.md)
- [Supabase Configuration Guide](./SUPABASE_EMAIL_CONFIGURATION.md)
- [Environment Variables Reference](./ENVIRONMENT_VARIABLES.md)
- [Troubleshooting Guide](./TROUBLESHOOTING.md)

## 🤝 Contributing

When adding new features:
1. Ensure mock authentication works in development
2. Verify production builds disable mock auth
3. Add appropriate environment variables
4. Update this guide with new features
5. Test both development and production modes

---

**Happy Coding! 🎉**

This development profile configuration makes it easy to develop and test authentication features without any email dependencies or Supabase bounce issues.
