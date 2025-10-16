# PLAN — Batch 1 (Tickets: P1.1-A, P1.1-B)

## 0) Summary
- **Goal**: Establish authentication, authorization, and core domain foundation for AIMatchly platform
- **Architecture note**: Introduce a Facade layer between controllers and services to minimize controller dependencies and centralize orchestration logic
- **Tickets covered**: P1.1-A (Auth & RBAC — Supabase JWT verify), P1.1-B (Core Domain Schema)
- **Non-goals / out of scope**: Payment integration, advanced matching, project management, complex analytics

## 1) Backend changes
- **Entities (new/updated)**: 
  - `User` (id, email, firstName, lastName, role, supabaseId, createdAt, updatedAt)
  - `Agency` (id, name, description, website, location, employeeBand, summary, logoUrl, proofPackComplete, schedulerUrl, status, ownerId, createdAt, updatedAt, approvedAt, rejectedAt, rejectionReason)
  - `AgencyMember` (id, agencyId, userId, role, joinedAt)
  - `UseCase` (id, agencyId, title, problem, intervention, outcomeMetric, industries[], stacks[], loomUrl, hoursLow, hoursHigh, verified, embedding, createdAt)
  - `StackTag` (id, name, category, description)
  - `AgencyStack` (id, agencyId, stackId, depth, createdAt)
  - `Brief` (id, rawText, normalizedJson, riskFlags[], embedding, status, buyerId, createdAt, updatedAt)
  - `SupportPackage` (id, agencyId, tier, price, inclusions, createdAt, updatedAt)

- **Liquibase changesets**:
  - `V001__users.yaml` - Create users table with RBAC roles
  - `V002__agencies.yaml` - Create agencies and agency_members tables
  - `V003__core_domain.yaml` - Create use_cases, stack_tags, agency_stacks, briefs, support_packages tables

- **Endpoints (method → path → purpose)**:
  - `GET /api/users/me` → Get current user profile
  - `POST /api/auth/refresh` → Refresh JWT token
  - `POST /api/agencies` → Create agency application (OWNER role required)
  - `GET /api/agencies` → List agencies (public)
  - `GET /api/agencies/{id}` → Get agency details
  - `POST /api/agencies/{id}/use-cases` → Add use case to agency
  - `POST /api/agencies/{id}/support-packages` → Add support package to agency
  - `GET /api/stack-tags` → List available stack tags
  - `GET /api/admin/agencies?status=PENDING` → List pending agencies (ADMIN role required)
  - `POST /api/admin/agencies/{id}/approve` → Approve agency (ADMIN role required)
  - `POST /api/admin/agencies/{id}/reject` → Reject agency (ADMIN role required)

- **Facade layer (controllers depend on facades, not services)**:
  - Pattern: Controllers → Facades → Services → Repositories
  - Rules: Controllers are thin and depend on facades + DTOs only. Facades orchestrate multi-service flows; services encapsulate domain logic; repositories handle persistence.
  - Facades:
    - `UserFacade` — aggregates `AuthService`, `UserService` for profile/me and auth-related orchestration
    - `AgencyFacade` — coordinates `AgencyService`, `UseCaseService`, `SupportPackageService` for agency management flows
    - `AdminFacade` — wraps `AdminService`, `AgencyService`, `AuditService` for approve/reject operations
  - Benefits: slimmer controllers, single entrypoints per use case, clearer boundaries, easier testing and evolution of business flows

- **DTOs & mappers**:
  - `UserDto`, `UserMapper` (MapStruct)
  - `AgencyDto`, `AgencyMapper` (MapStruct)
  - `UseCaseDto`, `UseCaseMapper` (MapStruct)
  - `StackTagDto`, `StackTagMapper` (MapStruct)
  - `BriefDto`, `BriefMapper` (MapStruct)
  - `SupportPackageDto`, `SupportPackageMapper` (MapStruct)
  - `CreateAgencyRequest`, `ApproveAgencyRequest`, `RejectAgencyRequest`
  - `CreateUseCaseRequest`, `CreateSupportPackageRequest`

- **Security/RBAC**:
  - Supabase JWT verification via JWKS endpoint
  - `@PreAuthorize` annotations for role-based access
  - Custom `SecurityConfig` with JWT authentication
  - Roles: VISITOR, BUYER, AGENCY_OWNER, AGENCY_CONTRIBUTOR, ADMIN, VERIFIER

- **Validation rules**:
  - Email format validation
  - Agency name uniqueness
  - Required field validation for use cases
  - Stack tag validation
  - Brief content validation

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
  - `/owner/agency/create` - Agency creation form
  - `/owner/agency/edit` - Agency profile management
  - `/owner/agency/use-cases` - Use case management
  - `/owner/agency/packages` - Support package management
  - `/admin/agencies` - Admin agency management dashboard
  - `/marketplace` - Public agency marketplace
  - `/agencies/[id]` - Agency detail page

- **Components (reusing existing)**:
  - **Authentication**: Extend existing `AuthLogin.tsx`, `AuthRegister.tsx` from `/components/authentication/auth-forms/` for Supabase integration
  - **Multi-step forms**: Reuse `ValidationWizard` from `/components/forms/forms-wizard/ValidationWizard/` for agency creation flow
  - **Admin dashboard**: Adapt `LatestCustomerTableCard` from `/components/dashboard/Analytics/` for agency management
  - **Status indicators**: Use existing `Chip` component from `/components/ui-component/extended/Chip.tsx` with status colors (success/warning/error)
  - **Cards**: Leverage `MainCard`, `SubCard` from `/components/ui-component/cards/` for consistent layouts
  - **Tables**: Reuse table patterns from `/components/users/list/Style1/UserList.tsx` for agency listings
  - **Forms**: Extend existing form components from `/components/forms/` for agency and use case management

- **Data fetching (React Query keys)**:
  - `['users', 'me']` - Current user profile
  - `['agencies']` - Public agencies list
  - `['agencies', 'my-agency']` - User's agency (if exists)
  - `['agencies', 'pending']` - Pending agencies for admin
  - `['stack-tags']` - Available stack tags
  - `['use-cases', agencyId]` - Agency use cases
  - `['support-packages', agencyId]` - Agency support packages

- **Forms & validation (RHF + Zod)**:
  - Agency creation form with Zod schema validation (extend `ValidationWizard` pattern)
  - Use case creation form with validation
  - Support package configuration form
  - Admin approval/rejection forms (reuse `InstantFeedback` validation patterns)

- **Uploads/media**: 
  - Agency logo upload with presigned URLs
  - Loom video URL validation for use cases

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
  - `@Tag` for endpoint grouping

- **Regenerate FE types**:
  - Generate TypeScript types from OpenAPI spec
  - Update existing auth types to include Supabase integration
  - Create new types for agency, use case, and brief entities

## 4) Tests
- **BE unit/slice**:
  - `UserServiceTest` - User profile operations
  - `AgencyServiceTest` - Agency CRUD operations
  - `UseCaseServiceTest` - Use case management
  - `AuthServiceTest` - JWT verification logic
  - `AdminServiceTest` - Agency approval workflow
  - `UserFacadeTest`, `AgencyFacadeTest`, `AdminFacadeTest` - Facade orchestration and input/output mapping
  - Controller tests focus on request mapping, validation, and delegating to facades

- **BE integration (Testcontainers)**:
  - `AuthControllerIntegrationTest` - Full auth flow
  - `AgencyControllerIntegrationTest` - Agency management flow
  - `AdminControllerIntegrationTest` - Admin operations
  - `UseCaseControllerIntegrationTest` - Use case management

- **FE unit**:
  - `SupabaseAuthProvider.test.tsx` - Auth context testing (extend existing auth test patterns)
  - `AgencyCreationForm.test.tsx` - Form validation (reuse `ValidationWizard` test patterns)
  - `AdminAgencyList.test.tsx` - Admin dashboard (adapt `LatestCustomerTableCard` test patterns)
  - `UseCaseForm.test.tsx` - Use case form testing

- **E2E happy-path (Playwright)**:
  - User registration → Agency creation → Admin approval flow
  - Login → Profile access → Role-based navigation
  - Agency profile management → Use case creation

- **Coverage target**: 80%+ for auth service, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Admin user with ADMIN role
  - Sample OWNER users
  - Sample agencies in different statuses (PENDING, APPROVED, REJECTED)
  - Stack tags (React, Node.js, Python, AI/ML, etc.)
  - Sample use cases with different industries and stacks
  - Sample support packages (Basic, Plus, Pro)

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
  - [ ] AC#1: Core domain entities created with proper JPA mappings
  - [ ] AC#2: Agency creation workflow with validation
  - [ ] AC#3: Use case management with stack tag associations
  - [ ] AC#4: Support package configuration
  - [ ] AC#5: Brief entity with proper validation
  - [ ] AC#6: Database migrations applied successfully

## 7) Risks & Rollback
- **Risks**:
  - Supabase JWT verification complexity
  - Database migration issues with complex relationships
  - Existing auth system migration conflicts
  - Frontend component integration challenges

- **Mitigations**:
  - Comprehensive JWT verification testing
  - Gradual migration approach with feature flags
  - Database backup before migrations
  - Extensive component testing and validation

- **Rollback plan**:
  - Revert Liquibase changesets
  - Disable Supabase auth integration
  - Restore previous auth context
  - Rollback frontend changes

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
  ├── src/main/java/com/aimatchly/
  │   ├── entity/User.java, Agency.java, AgencyMember.java, UseCase.java, StackTag.java, Brief.java, SupportPackage.java
  │   ├── dto/UserDto.java, AgencyDto.java, UseCaseDto.java, StackTagDto.java, BriefDto.java, SupportPackageDto.java
  │   ├── mapper/UserMapper.java, AgencyMapper.java, UseCaseMapper.java, StackTagMapper.java, BriefMapper.java, SupportPackageMapper.java
  │   ├── controller/AuthController.java, AgencyController.java, AdminController.java, UseCaseController.java
  │   ├── facade/UserFacade.java, AgencyFacade.java, AdminFacade.java
  │   ├── service/AuthService.java, AgencyService.java, UseCaseService.java, AdminService.java, NotificationService.java, AuditService.java
  │   ├── security/SecurityConfig.java, SupabaseJwtAuthenticationFilter.java
  │   └── repository/UserRepository.java, AgencyRepository.java, UseCaseRepository.java, StackTagRepository.java, BriefRepository.java, SupportPackageRepository.java
  ├── src/main/resources/
  │   ├── application.yml
  │   └── db/changelog/V001__users.yaml, V002__agencies.yaml, V003__core_domain.yaml
  └── pom.xml
  
  frontend/
  ├── src/
  │   ├── contexts/SupabaseAuthContext.tsx (extend existing auth contexts)
  │   ├── components/agency/AgencyCreationForm.tsx (extend ValidationWizard)
  │   ├── components/agency/UseCaseForm.tsx (extend existing form components)
  │   ├── components/admin/AdminAgencyList.tsx (extend LatestCustomerTableCard)
  │   ├── app/(minimal)/auth/login/page.tsx (extend existing login pages)
  │   ├── app/(minimal)/auth/register/page.tsx (extend existing register pages)
  │   ├── app/(dashboard)/owner/agency/create/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/owner/agency/edit/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/admin/agencies/page.tsx (extend dashboard layout patterns)
  │   └── app/(minimal)/marketplace/page.tsx (extend minimal layout patterns)
  └── package.json (updated with Supabase dependencies)
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: New auth, agency, and core domain endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(auth): implement Supabase JWT authentication with RBAC [P1.1-A]`
  - `feat(domain): add core domain entities and agency management [P1.1-B]`