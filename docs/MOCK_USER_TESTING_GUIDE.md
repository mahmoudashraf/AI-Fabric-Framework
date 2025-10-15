# 🧪 Mock User Testing System

## Overview

The Mock User Testing System provides a comprehensive solution for testing your Easy Luxury application without relying on Supabase email authentication. This eliminates email bounce issues and provides consistent test data.

## 🎯 Benefits

- **No Email Dependencies**: Test authentication without sending real emails
- **Consistent Test Data**: Predefined users with different roles
- **Quick Setup**: Instant login for any user role
- **Isolated Testing**: Mock users don't affect production data
- **Performance Testing**: Built-in utilities for API performance testing

## 🏗️ Architecture

### Frontend Components
- **Mock User Data** (`/src/data/mockUsers.ts`): Predefined user data
- **Mock Auth Service** (`/src/services/mockAuthService.ts`): Authentication logic
- **Mock User Tester** (`/src/components/testing/MockUserTester.tsx`): UI for testing
- **Test Utils** (`/src/utils/mockUserTestUtils.ts`): Testing utilities

### Backend Components
- **Mock User Controller** (`/api/mock/**`): REST endpoints
- **Mock User Service**: Business logic
- **Mock Auth Filter**: Authentication bypass
- **Security Configuration**: Mock auth integration

## 🗄️ Database Management

### Clean Database for Fresh Start

When you need to start with a completely clean database:

```bash
# Clean all data from the database
./CLEAN_DATABASE.sh

# After cleanup, restart backend to reload mock users
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

**⚠️ Warning:** This command will delete ALL data from the database. Use only in development!

### Populate Mock Users

After cleaning the database, populate it with mock users:

```bash
# Feed mock users to database
curl -X POST http://localhost:8080/api/mock/feed/users

# Check status
curl http://localhost:8080/api/mock/feed/status
```

### Database vs In-Memory Users

The system maintains users in two places:
- **In-Memory**: For authentication (MockUserService)
- **Database**: For business logic (UsersFeedService)

Both use deterministic UUIDs based on email addresses to ensure consistency.

## 👥 Available Mock Users

### Admin User
- **Email**: `admin@easyluxury.com`
- **Password**: `admin123`
- **Role**: `ADMIN`
- **Permissions**: Full system access

### Property Owners
- **Email**: `john.doe@example.com`
- **Password**: `owner123`
- **Role**: `OWNER`
- **Permissions**: Manage own properties

- **Email**: `robert.smith@example.com`
- **Password**: `owner123`
- **Role**: `OWNER`

### Agency Users
- **Email**: `sarah.wilson@luxuryrealty.com`
- **Password**: `agency123`
- **Role**: `AGENCY_OWNER`
- **Permissions**: Manage agency and properties

- **Email**: `mike.chen@luxuryrealty.com`
- **Password**: `member123`
- **Role**: `AGENCY_MEMBER`
- **Permissions**: Agency member access

- **Email**: `lisa.garcia@luxuryrealty.com`
- **Password**: `member123`
- **Role**: `AGENCY_MEMBER`

### Tenants
- **Email**: `emma.johnson@example.com`
- **Password**: `tenant123`
- **Role**: `TENANT`
- **Permissions**: View and book properties

- **Email**: `david.brown@example.com`
- **Password**: `tenant123`
- **Role**: `TENANT`

## 🚀 Quick Start

### 1. Enable Mock Authentication

In your backend `application.yml`:
```yaml
app:
  mock-auth:
    enabled: true
```

### 2. Frontend Testing

#### Using the Mock User Tester Component
```tsx
import MockUserTester from '@/components/testing/MockUserTester';

// Add to your testing page
<MockUserTester />
```

#### Programmatic Testing
```typescript
import { mockAuthService } from '@/services/mockAuthService';

// Quick login as admin
await mockAuthService.loginAsAdmin();

// Quick login as owner
await mockAuthService.loginAsOwner();

// Custom login
await mockAuthService.signIn('john.doe@example.com', 'owner123');

// Check authentication
const isAuthenticated = mockAuthService.isAuthenticated();
const currentUser = mockAuthService.getCurrentUser();

// Logout
await mockAuthService.signOut();
```

### 3. Backend API Testing

#### Mock User Endpoints
```bash
# Get all mock users
GET /api/mock/users

# Get users by role
GET /api/mock/users/role/ADMIN

# Mock login
POST /api/mock/auth/login
{
  "email": "admin@easyluxury.com",
  "password": "admin123"
}

# Quick login by role
POST /api/mock/auth/login-as/ADMIN

# Get current user
GET /api/mock/auth/current

# Check auth status
GET /api/mock/auth/status

# Logout
POST /api/mock/auth/logout
```

## 🧪 Testing Scenarios

### 1. Role-Based Testing
```typescript
import { mockUserTestUtils } from '@/utils/mockUserTestUtils';

// Test all roles
await mockUserTestUtils.testAllRoles(async (role) => {
  console.log(`Testing as ${role}`);
  // Your test logic here
});

// Test specific scenarios
await mockUserTestUtils.testAdminScenario();
await mockUserTestUtils.testOwnerScenario();
await mockUserTestUtils.testAgencyScenario();
await mockUserTestUtils.testTenantScenario();
```

### 2. API Testing
```typescript
// Test API endpoint
const response = await mockUserTestUtils.testApiEndpoint('/api/properties');

// Test with authentication headers
const headers = mockUserTestUtils.getAuthHeaders();
const response = await fetch('/api/properties', { headers });

// Performance testing
const results = await mockUserTestUtils.measureApiCall('/api/properties', 'GET', null, 10);
console.log(`Average response time: ${results.averageTime}ms`);
```

### 3. Data Generation
```typescript
// Generate test data
const mockProperty = mockUserTestUtils.generateMockProperty();
const mockAgency = mockUserTestUtils.generateMockAgency();
const mockBooking = mockUserTestUtils.generateMockBooking();

// Validate mock data
const isValid = mockUserTestUtils.validateMockUser(user);
```

## 🔧 Configuration

### Frontend Configuration
```typescript
// In your environment variables
NEXT_PUBLIC_MOCK_AUTH_ENABLED=true
```

### Backend Configuration
```yaml
# application.yml
app:
  mock-auth:
    enabled: ${MOCK_AUTH_ENABLED:true}

# Environment variable
MOCK_AUTH_ENABLED=true
```

## 📊 Testing Workflows

### 1. Development Testing
```bash
# Start backend with mock auth enabled
MOCK_AUTH_ENABLED=true ./mvnw spring-boot:run

# Start frontend
npm run dev

# Access mock user tester
http://localhost:3000/mock-users
```

### 2. Automated Testing
```typescript
// Jest test example
describe('Property Management', () => {
  beforeEach(async () => {
    await mockUserTestUtils.setupAsOwner();
  });

  afterEach(async () => {
    await mockUserTestUtils.cleanup();
  });

  test('should create property', async () => {
    const mockProperty = mockUserTestUtils.generateMockProperty();
    const response = await mockUserTestUtils.testApiEndpoint('/api/properties', 'POST', mockProperty);
    expect(response.ok).toBe(true);
  });
});
```

### 3. Integration Testing
```typescript
// Test complete user flows
describe('Booking Flow', () => {
  test('tenant can book property', async () => {
    // Setup as tenant
    await mockUserTestUtils.setupAsTenant();
    
    // Create booking
    const booking = mockUserTestUtils.generateMockBooking();
    const response = await mockUserTestUtils.testApiEndpoint('/api/bookings', 'POST', booking);
    
    expect(response.ok).toBe(true);
  });
});
```

## 🛠️ Customization

### Adding New Mock Users
```typescript
// In mockUsers.ts
export const MOCK_USERS: MockUser[] = [
  // ... existing users
  {
    id: 'mock-custom-001',
    email: 'custom@example.com',
    firstName: 'Custom',
    lastName: 'User',
    role: 'OWNER',
    supabaseId: 'mock-supabase-custom-001',
    avatar: 'https://i.pravatar.cc/150?img=10',
    phone: '+1-555-0100',
    isActive: true,
  },
];

// Add credentials
export const MOCK_CREDENTIALS = {
  // ... existing credentials
  custom: { email: 'custom@example.com', password: 'custom123' },
};
```

### Backend Mock User Creation
```java
// In MockUserService.java
private void initializeMockUsers() {
    // ... existing users
    createMockUser("mock-custom-001", "custom@example.com", "Custom", "User", User.UserRole.OWNER);
}

// Add credentials
private final Map<String, MockCredentials> mockCredentials = Map.of(
    // ... existing credentials
    "custom@example.com", new MockCredentials("custom@example.com", "custom123", User.UserRole.OWNER)
);
```

## 🔍 Debugging

### Frontend Debugging
```typescript
// Check current authentication state
console.log('Is authenticated:', mockAuthService.isAuthenticated());
console.log('Current user:', mockAuthService.getCurrentUser());
console.log('Current token:', mockAuthService.getCurrentToken());

// Verify token
const user = mockAuthService.verifyToken(token);
console.log('Token valid:', user !== null);
```

### Backend Debugging
```bash
# Check mock auth status
curl -X GET http://localhost:8080/api/mock/auth/status

# List all mock users
curl -X GET http://localhost:8080/api/mock/users

# Test login
curl -X POST http://localhost:8080/api/mock/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@easyluxury.com","password":"admin123"}'
```

## 🚨 Troubleshooting

### Common Issues

1. **Mock Auth Not Working**
   - Check `MOCK_AUTH_ENABLED=true` in environment
   - Verify mock auth filter is registered in security config
   - Check logs for authentication errors

2. **Token Not Recognized**
   - Ensure token starts with `mock-token-`
   - Check if mock user service is initialized
   - Verify token format matches expected pattern

3. **User Not Found**
   - Check if mock user exists in predefined list
   - Verify credentials match exactly
   - Check for typos in email/password

4. **Permission Denied**
   - Verify user role matches required permissions
   - Check security configuration
   - Ensure mock auth filter runs before JWT filter

### Debug Commands
```bash
# Check backend logs
tail -f logs/application.log | grep -i mock

# Test API connectivity
curl -X GET http://localhost:8080/api/mock/users

# Verify authentication
curl -X GET http://localhost:8080/api/mock/auth/status
```

## 📈 Performance Considerations

- Mock users are stored in memory for fast access
- No database queries for authentication
- Minimal overhead compared to real authentication
- Suitable for development and testing environments

## 🔒 Security Notes

- Mock authentication is for testing only
- Never enable in production environments
- Mock tokens are not cryptographically secure
- Use environment variables to control activation

## 📚 Additional Resources

- [Frontend Testing Guide](../FRONTEND_DEVELOPMENT_GUIDE.md)
- [API Documentation](../API_REFERENCE.md)
- [Security Configuration](../SECURITY_GUIDE.md)
- [Testing Best Practices](../TESTING_GUIDE.md)
