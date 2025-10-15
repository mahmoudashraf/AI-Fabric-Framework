# Users Feed Service

The `UsersFeedService` is a development-only service that automatically loads mock users into the database for testing and development purposes.

## Features

- **Automatic Initialization**: Automatically feeds users to the database on startup if no users exist
- **Manual Control**: Provides endpoints to manually trigger user feeding operations
- **Database Integration**: Uses the actual `UserRepository` to persist users to the database
- **Dev Profile Only**: Only runs in development mode (`@Profile("dev")`)

## Mock Users Included

The service creates the following mock users:

| Email | First Name | Last Name | Role |
|-------|------------|-----------|------|
| admin@easyluxury.com | Admin | User | ADMIN |
| john.doe@example.com | John | Doe | OWNER |
| sarah.wilson@luxuryrealty.com | Sarah | Wilson | AGENCY_OWNER |
| mike.chen@luxuryrealty.com | Mike | Chen | AGENCY_MEMBER |
| emma.johnson@example.com | Emma | Johnson | TENANT |
| robert.smith@example.com | Robert | Smith | OWNER |
| lisa.garcia@luxuryrealty.com | Lisa | Garcia | AGENCY_MEMBER |
| david.brown@example.com | David | Brown | TENANT |

## API Endpoints

All endpoints are available under `/api/mock/feed/` and require dev profile:

### Feed Users to Database
```http
POST /api/mock/feed/users
```
Loads all mock users into the database.

### Feed Users if Empty
```http
POST /api/mock/feed/users/if-empty
```
Loads mock users into database only if no users exist.

### Clear All Users
```http
DELETE /api/mock/feed/users
```
⚠️ **Warning**: Removes all users from the database. Use with caution.

### Get Database Status
```http
GET /api/mock/feed/status
```
Returns information about users in the database:
```json
{
  "hasUsers": true,
  "totalUsers": 8,
  "adminUsers": 1,
  "ownerUsers": 2,
  "agencyOwnerUsers": 1,
  "agencyMemberUsers": 2,
  "tenantUsers": 2
}
```

## Usage Examples

### Check Database Status
```bash
curl -X GET http://localhost:8080/api/mock/feed/status
```

### Feed Users to Database
```bash
curl -X POST http://localhost:8080/api/mock/feed/users
```

### Feed Users Only if Database is Empty
```bash
curl -X POST http://localhost:8080/api/mock/feed/users/if-empty
```

### Clear All Users (Use with Caution)
```bash
curl -X DELETE http://localhost:8080/api/mock/feed/users
```

## Integration with MockUserService

The `UsersFeedService` works alongside the existing `MockUserService`:

- **MockUserService**: Provides in-memory mock users for authentication testing
- **UsersFeedService**: Loads persistent mock users into the database for data testing

Both services use the same mock user data for consistency.

## Configuration

The service is automatically enabled when running with the `dev` profile:

```yaml
spring:
  profiles:
    active: dev
```

## Database Schema

Users are persisted with the following structure:
- `id`: UUID (auto-generated)
- `email`: Unique email address
- `first_name`: User's first name
- `last_name`: User's last name
- `role`: User role (ADMIN, OWNER, AGENCY_OWNER, AGENCY_MEMBER, TENANT)
- `supabase_id`: Mock Supabase ID for compatibility
- `created_at`: Creation timestamp
- `updated_at`: Last update timestamp

## Logging

The service provides detailed logging:
- Info level: Service initialization and major operations
- Debug level: Individual user creation details
- Warn level: Clear operations and potential issues
- Error level: Operation failures
