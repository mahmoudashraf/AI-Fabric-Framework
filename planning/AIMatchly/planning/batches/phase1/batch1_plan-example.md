# PLAN — Batch 1 (Tickets: P1.1-A, P1.1-B)

## 0) Summary
- **Goal**: Implement Supabase JWT authentication with RBAC and agency activation workflow with admin approval
- **Architecture note**: Introduce a Facade layer between controllers and services to minimize controller dependencies and centralize orchestration logic
- **Tickets covered**: P1.1-A (Auth & RBAC — Supabase JWT verify), P1.1-B (Agency activation with admin approval)
- **Non-goals / out of scope**: Payment integration, OTA calendar sync, self-service booking, complex project management features

## 1) Backend changes
- **Entities (new/updated)**: 
  - `User` (id, email, firstName, lastName, role, supabaseId, createdAt, updatedAt)
  - `Agency` (id, name, description, status, ownerId, createdAt, updatedAt, approvedAt, rejectedAt, rejectionReason)
  - `AgencyMember` (id, agencyId, userId, role, joinedAt)
- **Liquibase changesets**:
  - `V001__users.yaml` - Create users table with RBAC roles
  - `V002__agencies.yaml` - Create agencies and agency_members tables
- **Endpoints (method → path → purpose)**:
  - `GET /api/users/me` → Get current user profile
  - `POST /api/auth/refresh` → Refresh JWT token
  - `POST /api/agencies` → Create agency application (OWNER role required)
  - `GET /api/admin/agencies?status=PENDING` → List pending agencies (ADMIN role required)
  - `POST /api/admin/agencies/{id}/approve` → Approve agency (ADMIN role required)
  - `POST /api/admin/agencies/{id}/reject` → Reject agency (ADMIN role required)
- **Facade layer (controllers depend on facades, not services)**:
  - Pattern: Controllers → Facades → Services → Repositories
  - Rules: Controllers are thin and depend on facades + DTOs only. Facades orchestrate multi-service flows; services encapsulate domain logic; repositories handle persistence.
  - Facades:
    - `UserFacade` — aggregates `AuthService`, `UserService` for profile/me and auth-related orchestration
    - `AgencyFacade` — coordinates `AgencyService`, `NotificationService` for create/list flows
    - `AdminFacade` — wraps `AdminService`, `AgencyService`, `AuditService` for approve/reject operations
  - Benefits: slimmer controllers, single entrypoints per use case, clearer boundaries, easier testing and evolution of business flows
- **DTOs & mappers**:
  - `UserDto`, `UserMapper` (MapStruct)
  - `AgencyDto`, `AgencyMapper` (MapStruct)
  - `AgencyMemberDto`, `AgencyMemberMapper` (MapStruct)
  - `CreateAgencyRequest`, `ApproveAgencyRequest`, `RejectAgencyRequest`
- **Security/RBAC**:
  - Supabase JWT verification via JWKS endpoint
  - `@PreAuthorize` annotations for role-based access
  - Custom `SecurityConfig` with JWT authentication
- **Validation rules**:
  - Email format validation
  - Agency name uniqueness
  - Required field validation
- **Error model (problem+json codes)**:
  - `400` - Validation errors
  - `401` - Unauthorized (invalid JWT)
  - `403` - Forbidden (insufficient role)
  - `404` - Resource not found
  - `409` - Conflict (duplicate agency name)

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**:
  - `/auth/login` - Supabase login integration
  - `/auth/register` - Supabase registration integration
  - `/owner/settings` - Agency application form
  - `/admin/agencies` - Admin agency management dashboard
- **Components (reusing existing)**:
  - **Authentication**: Extend existing `AuthLogin.tsx`, `AuthRegister.tsx` from `/components/authentication/auth-forms/` for Supabase integration
  - **Multi-step forms**: Reuse `ValidationWizard` from `/components/forms/forms-wizard/ValidationWizard/` for agency application flow
  - **Admin dashboard**: Adapt `LatestCustomerTableCard` from `/components/dashboard/Analytics/` for agency management
  - **Status indicators**: Use existing `Chip` component from `/components/ui-component/extended/Chip.tsx` with status colors (success/warning/error)
  - **Cards**: Leverage `MainCard`, `SubCard` from `/components/ui-component/cards/` for consistent layouts
  - **Tables**: Reuse table patterns from `/components/users/list/Style1/UserList.tsx` for agency listings
- **Data fetching (React Query keys)**:
  - `['users', 'me']` - Current user profile
  - `['agencies', 'pending']` - Pending agencies for admin
  - `['agencies', 'my-agency']` - User's agency (if exists)
- **Forms & validation (RHF + Zod)**:
  - Agency application form with Zod schema validation (extend `ValidationWizard` pattern)
  - Admin approval/rejection forms (reuse `InstantFeedback` validation patterns)
- **Uploads/media**: None in this batch
- **Accessibility notes**: 
  - ARIA labels for form fields
  - Keyboard navigation support
  - Screen reader friendly status indicators
 - **Page creation guidelines**:
   - Use existing pages in `src/app/(dashboard)`, `src/app/(minimal)`, and `src/app/(simple)` as references for layout, structure, and patterns
   - Create new pages to meet project requirements while following those patterns
   - Prefer composing existing components from `src/components` and the referenced pages over building new components or custom UI

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all endpoints
  - `@ApiResponse` for error codes
  - `@SecurityRequirement` for JWT authentication
- **Regenerate FE types**:
  - Generate TypeScript types from OpenAPI spec
  - Update existing auth types to include Supabase integration

## 4) Tests
- **BE unit/slice**:
  - `UserServiceTest` - User profile operations
  - `AgencyServiceTest` - Agency CRUD operations
  - `AuthServiceTest` - JWT verification logic
  - `AdminServiceTest` - Agency approval workflow
  - `UserFacadeTest`, `AgencyFacadeTest`, `AdminFacadeTest` - Facade orchestration and input/output mapping
  - Controller tests focus on request mapping, validation, and delegating to facades
- **BE integration (Testcontainers)**:
  - `AuthControllerIntegrationTest` - Full auth flow
  - `AgencyControllerIntegrationTest` - Agency management flow
  - `AdminControllerIntegrationTest` - Admin operations
- **FE unit**:
  - `SupabaseAuthProvider.test.tsx` - Auth context testing (extend existing auth test patterns)
  - `AgencyApplicationForm.test.tsx` - Form validation (reuse `ValidationWizard` test patterns)
  - `AdminAgencyList.test.tsx` - Admin dashboard (adapt `LatestCustomerTableCard` test patterns)
- **E2E happy-path (Playwright)**:
  - User registration → Agency application → Admin approval flow
  - Login → Profile access → Role-based navigation
- **Coverage target**: 80%+ for auth service, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Admin user with ADMIN role
  - Sample OWNER users
  - Sample agencies in different statuses (PENDING, APPROVED, REJECTED)
- **How to run**:
  - `mvn liquibase:update` - Apply migrations
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"` - Run with seed data

## 6) Acceptance Criteria Matrix
- **P1.1-A**:
  - [ ] AC#1: User can login via Supabase (email + social providers)
  - [ ] AC#2: `/api/users/me` returns local user profile with role information
  - [ ] AC#3: Role-based access control enforced with `@PreAuthorize`
  - [ ] AC#4: JWT verification works via Supabase JWKS endpoint
  - [ ] AC#5: Auth service tests achieve ≥80% coverage
- **P1.1-B**:
  - [ ] AC#1: OWNER users can submit agency applications
  - [ ] AC#2: Pending agencies are hidden from public marketplace
  - [ ] AC#3: ADMIN users can approve/reject agencies
  - [ ] AC#4: Agency approval/rejection decisions are audited
  - [ ] AC#5: Agency status changes trigger appropriate notifications

## 7) Risks & Rollback
- **Risks**:
  - Supabase JWT verification complexity
  - Existing auth system migration conflicts
  - Database migration issues
- **Mitigations**:
  - Comprehensive JWT verification testing
  - Gradual migration approach with feature flags
  - Database backup before migrations
- **Rollback plan**:
  - Revert Liquibase changesets
  - Disable Supabase auth integration
  - Restore previous auth context

## 8) Commands to run (print, don't execute yet)
```bash
# Backend setup
cd backend
mvn clean install
mvn liquibase:update
mvn spring-boot:run

# Frontend setup
cd frontend
npm install
npm run dev

# Testing
mvn test
npm run test
npm run test:e2e

# Docker
docker compose up -d
```

## 9) Deliverables
- **File tree (added/changed)**:
  ```
  backend/
  ├── src/main/java/com/easy/luxury/
  │   ├── entity/User.java, Agency.java, AgencyMember.java
  │   ├── dto/UserDto.java, AgencyDto.java, etc.
  │   ├── mapper/UserMapper.java, AgencyMapper.java, etc.
  │   ├── controller/AuthController.java, AgencyController.java, AdminController.java
  │   ├── facade/UserFacade.java, AgencyFacade.java, AdminFacade.java
  │   ├── service/AuthService.java, AgencyService.java, AdminService.java, NotificationService.java, AuditService.java
  │   ├── security/SecurityConfig.java, SupabaseJwtAuthenticationFilter.java
  │   └── repository/UserRepository.java, AgencyRepository.java, etc.
  ├── src/main/resources/
  │   ├── application.yml
  │   └── db/changelog/V001__users.yaml, V002__agencies.yaml
  └── pom.xml
  
  frontend/
  ├── src/
  │   ├── contexts/SupabaseAuthContext.tsx (extend existing auth contexts)
  │   ├── components/agency/AgencyApplicationForm.tsx (extend ValidationWizard)
  │   ├── components/admin/AdminAgencyList.tsx (extend LatestCustomerTableCard)
  │   ├── app/(minimal)/auth/login/page.tsx (extend existing login pages)
  │   ├── app/(minimal)/auth/register/page.tsx (extend existing register pages)
  │   ├── app/(dashboard)/owner/settings/page.tsx (extend dashboard layout patterns)
  │   └── app/(dashboard)/admin/agencies/page.tsx (extend dashboard layout patterns)
  └── package.json (updated with Supabase dependencies)
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: New auth and agency management endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(auth): implement Supabase JWT authentication with RBAC [P1.1-A]`
  - `feat(agency): add agency activation workflow with admin approval [P1.1-B]`
