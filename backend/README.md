# EasyLuxury Backend

Property management platform backend built with Spring Boot 3.

## Architecture

- **Controllers** → **Facades** → **Services** → **Repositories**
- JWT Authentication via Supabase
- Role-based access control (RBAC)
- RESTful API with OpenAPI documentation

## Technology Stack

- Java 21
- Spring Boot 3.2.0
- Spring Security 6
- Spring Data JPA
- PostgreSQL
- Liquibase (database migrations)
- MapStruct (DTO mapping)
- Lombok
- SpringDoc OpenAPI 3

## Prerequisites

- Java 21 or higher
- Maven 3.8+
- PostgreSQL 14+
- Supabase account

## Setup

1. **Database Setup**
   ```bash
   # Create PostgreSQL database
   createdb easyluxury
   ```

2. **Environment Variables**
   Create a `.env` file or set environment variables:
   ```bash
   DATABASE_URL=jdbc:postgresql://localhost:5432/easyluxury
   DATABASE_USERNAME=postgres
   DATABASE_PASSWORD=your_password
   SUPABASE_URL=https://your-project.supabase.co
   SUPABASE_API_KEY=your_anon_key
   SUPABASE_SERVICE_ROLE_KEY=your_service_role_key
   ```

3. **Run Liquibase Migrations**
   ```bash
   mvn liquibase:update
   ```

4. **Build and Run**
   ```bash
   mvn clean install
   mvn spring-boot:run
   ```

## API Documentation

Once the application is running, access:
- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

## API Endpoints

### Authentication & Users
- `GET /api/users/me` - Get current user profile
- `PUT /api/users/me` - Update current user profile

### Agencies
- `POST /api/agencies` - Create agency application (OWNER role)
- `GET /api/agencies/my-agency` - Get user's agency
- `GET /api/agencies/{id}` - Get agency by ID

### Admin
- `GET /api/admin/agencies?status=PENDING` - List agencies by status (ADMIN role)
- `POST /api/admin/agencies/{id}/approve` - Approve agency (ADMIN role)
- `POST /api/admin/agencies/{id}/reject` - Reject agency (ADMIN role)

## Database Schema

### Users Table
- `id` (UUID)
- `email` (unique)
- `first_name`
- `last_name`
- `role` (ADMIN, OWNER, AGENCY_OWNER, AGENCY_MEMBER, TENANT)
- `supabase_id` (unique)
- `created_at`
- `updated_at`

### Agencies Table
- `id` (UUID)
- `name` (unique)
- `description`
- `status` (PENDING, APPROVED, REJECTED, SUSPENDED)
- `owner_id` (FK to users)
- `created_at`
- `updated_at`
- `approved_at`
- `rejected_at`
- `rejection_reason`

### Agency Members Table
- `id` (UUID)
- `agency_id` (FK to agencies)
- `user_id` (FK to users)
- `role` (OWNER, ADMIN, MEMBER)
- `joined_at`

## Development

### Running Tests
```bash
mvn test
```

### Code Generation (MapStruct)
MapStruct generates mapper implementations during compilation. Run:
```bash
mvn clean compile
```

### Database Migrations
To create a new migration:
1. Create a new YAML file in `src/main/resources/db/changelog/`
2. Add it to `db.changelog-master.yaml`
3. Run: `mvn liquibase:update`

## Project Structure

```
backend/
├── src/main/java/com/easyluxury/
│   ├── entity/           # JPA entities
│   ├── dto/              # Data Transfer Objects
│   ├── mapper/           # MapStruct mappers
│   ├── repository/       # Spring Data repositories
│   ├── service/          # Business logic services
│   ├── facade/           # Orchestration layer
│   ├── controller/       # REST controllers
│   ├── security/         # Security configuration
│   ├── exception/        # Exception handling
│   └── config/           # Application configuration
├── src/main/resources/
│   ├── db/changelog/     # Liquibase migrations
│   └── application.yml   # Application properties
└── pom.xml
```

## Security

- JWT token verification via Supabase JWKS endpoint
- Role-based access control using Spring Security `@PreAuthorize`
- CORS configuration for frontend integration
- Stateless session management

## Error Handling

The API uses RFC 7807 Problem Details for HTTP APIs:
- `400` - Validation errors
- `401` - Unauthorized (invalid JWT)
- `403` - Forbidden (insufficient role)
- `404` - Resource not found
- `409` - Conflict (duplicate resource)
- `500` - Internal server error

## License

Proprietary - EasyLuxury Platform
