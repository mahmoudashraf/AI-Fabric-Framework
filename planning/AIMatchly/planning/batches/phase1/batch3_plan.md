# PLAN — Batch 3 (Tickets: P1.1-E, P1.1-F)

## 0) Summary
- **Goal**: Complete agency profile builder and implement basic marketplace functionality for AIMatchly platform
- **Architecture note**: Enhanced agency profile management with validation and public marketplace with search capabilities
- **Tickets covered**: P1.1-E (Agency Profile Builder), P1.1-F (Basic Marketplace & Search)
- **Non-goals / out of scope**: Advanced marketplace features, payment integration, complex analytics

## 1) Backend changes
- **Entities (new/updated)**: 
  - `Agency` (enhanced with profile completion status, verification flags)
  - `UseCase` (enhanced with validation rules, verification status)
  - `SupportPackage` (enhanced with template system)
  - `AgencyProfile` (id, agencyId, completionPercentage, lastUpdated, status)

- **Liquibase changesets**:
  - `V006__agency_profile_enhancements.yaml` - Add profile completion tracking
  - `V007__marketplace_features.yaml` - Add marketplace search and filtering

- **Endpoints (method → path → purpose)**:
  - `POST /api/agencies/{id}/use-cases` → Add use case to agency
  - `PUT /api/agencies/{id}/use-cases/{useCaseId}` → Update use case
  - `DELETE /api/agencies/{id}/use-cases/{useCaseId}` → Delete use case
  - `POST /api/agencies/{id}/support-packages` → Add support package
  - `PUT /api/agencies/{id}/support-packages/{packageId}` → Update support package
  - `DELETE /api/agencies/{id}/support-packages/{packageId}` → Delete support package
  - `POST /api/agencies/{id}/proof/complete` → Complete Proof Pack
  - `GET /api/agencies/search` → Search agencies with filters
  - `GET /api/agencies/{id}/public` → Get public agency profile
  - `GET /api/agencies/featured` → Get featured agencies

- **Profile Builder Features**:
  - Multi-step profile completion wizard
  - Use case validation (≥2 required, Loom demo validation)
  - Support package templates (Basic/Plus/Pro)
  - Proof Pack completion workflow
  - Profile completion percentage tracking

- **Marketplace Features**:
  - Agency search by stack tags, industry, location
  - Filtering by verification status, proof pack completion
  - Featured agency highlighting
  - Public agency profile display
  - Search result ranking and pagination

- **DTOs & mappers**:
  - `AgencyProfileDto`, `AgencyProfileMapper` (MapStruct)
  - `UseCaseRequest`, `UseCaseResponse` (enhanced)
  - `SupportPackageRequest`, `SupportPackageResponse` (enhanced)
  - `AgencySearchRequest`, `AgencySearchResponse`
  - `PublicAgencyDto`, `PublicAgencyMapper` (MapStruct)

- **Services**:
  - `AgencyProfileService` - Profile completion and validation
  - `UseCaseService` - Use case management with validation
  - `SupportPackageService` - Support package management
  - `MarketplaceService` - Agency search and filtering
  - `ProofPackService` - Proof Pack verification workflow

- **Validation rules**:
  - Use case validation (title, problem, intervention, outcome metric required)
  - Loom URL validation for use cases
  - Support package validation (tier, price, inclusions)
  - Profile completion validation (≥2 use cases required)
  - Search parameter validation

- **Error model (problem+json codes)**:
  - `400` - Validation errors
  - `401` - Unauthorized
  - `403` - Forbidden (not agency owner)
  - `404` - Agency/use case not found
  - `409` - Duplicate use case or support package

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**:
  - `/owner/agency/profile` - Agency profile overview
  - `/owner/agency/use-cases` - Use case management
  - `/owner/agency/use-cases/new` - Create new use case
  - `/owner/agency/use-cases/[id]/edit` - Edit use case
  - `/owner/agency/packages` - Support package management
  - `/owner/agency/packages/new` - Create new support package
  - `/owner/agency/packages/[id]/edit` - Edit support package
  - `/owner/agency/proof-pack` - Proof Pack completion
  - `/marketplace` - Public agency marketplace
  - `/agencies/[id]` - Public agency profile

- **Components (reusing existing)**:
  - **Forms**: Extend `ValidationWizard` from `/components/forms/forms-wizard/ValidationWizard/` for profile builder
  - **Cards**: Use `MainCard`, `SubCard` from `/components/ui-component/cards/` for profile sections
  - **Tables**: Reuse table patterns from `/components/users/list/Style1/UserList.tsx` for use cases and packages
  - **Search**: Extend existing search components for marketplace filtering
  - **Status indicators**: Use `Chip` component from `/components/ui-component/extended/Chip.tsx` for profile status
  - **Progress**: Use existing progress components for profile completion

- **Data fetching (React Query keys)**:
  - `['agencies', 'my-agency']` - User's agency profile
  - `['agencies', 'my-agency', 'use-cases']` - Agency use cases
  - `['agencies', 'my-agency', 'support-packages']` - Agency support packages
  - `['agencies', 'search']` - Marketplace search results
  - `['agencies', agencyId, 'public']` - Public agency profile
  - `['agencies', 'featured']` - Featured agencies

- **Forms & validation (RHF + Zod)**:
  - Use case creation/editing form with validation
  - Support package configuration form
  - Profile completion wizard
  - Marketplace search and filter forms

- **Profile Builder Features**:
  - Multi-step profile completion wizard
  - Use case management with validation
  - Support package configuration
  - Proof Pack completion checklist
  - Profile completion progress tracking

- **Marketplace Features**:
  - Agency search with filters (stack, industry, location, verification)
  - Featured agency highlighting
  - Public agency profile display
  - Search result pagination and sorting

- **Accessibility notes**: 
  - ARIA labels for form fields and search filters
  - Keyboard navigation support
  - Screen reader friendly profile sections
  - Search result accessibility

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all endpoints
  - `@ApiResponse` for error codes
  - `@SecurityRequirement` for JWT authentication
  - `@Tag` for endpoint grouping

- **Regenerate FE types**:
  - Generate TypeScript types from OpenAPI spec
  - Create new types for agency profile, marketplace search
  - Update existing types for enhanced functionality

## 4) Tests
- **BE unit/slice**:
  - `AgencyProfileServiceTest` - Profile completion and validation
  - `UseCaseServiceTest` - Use case management with validation
  - `SupportPackageServiceTest` - Support package management
  - `MarketplaceServiceTest` - Agency search and filtering
  - `ProofPackServiceTest` - Proof Pack verification workflow
  - `AgencyControllerTest` - Enhanced agency endpoints

- **BE integration (Testcontainers)**:
  - `AgencyProfileControllerIntegrationTest` - Profile management flow
  - `MarketplaceControllerIntegrationTest` - Marketplace search flow
  - `UseCaseControllerIntegrationTest` - Use case management flow

- **FE unit**:
  - `AgencyProfileBuilder.test.tsx` - Profile builder testing
  - `UseCaseForm.test.tsx` - Use case form testing
  - `SupportPackageForm.test.tsx` - Support package form testing
  - `MarketplaceSearch.test.tsx` - Marketplace search testing
  - `PublicAgencyProfile.test.tsx` - Public profile display testing

- **E2E happy-path (Playwright)**:
  - Agency profile creation → Use case addition → Support package configuration
  - Marketplace search → Agency discovery → Public profile viewing
  - Profile completion → Proof Pack verification

- **Coverage target**: 80%+ for profile services, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample agencies with complete profiles
  - Use cases with different industries and stacks
  - Support packages with different tiers
  - Featured agencies for marketplace
  - Search test data

- **How to run**:
  - `mvn liquibase:update` - Apply migrations
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"` - Run with seed data

## 6) Acceptance Criteria Matrix
- **P1.1-E**:
  - [ ] AC#1: Agency can create comprehensive profile with basics, stacks, ≥2 use-cases
  - [ ] AC#2: Use case tiles with Loom demo, outcome metrics, stacks, industries
  - [ ] AC#3: Support packages (Basic/Plus/Pro templates) with pricing
  - [ ] AC#4: Proof Pack completion workflow with verification
  - [ ] AC#5: Profile completion percentage tracking

- **P1.1-F**:
  - [ ] AC#1: Public agency gallery with filtering and search
  - [ ] AC#2: Agency detail pages (public view) with all information
  - [ ] AC#3: Search by stack tags, industry, and verification status
  - [ ] AC#4: Featured agencies highlighted in marketplace
  - [ ] AC#5: Search result ranking and pagination

## 7) Risks & Rollback
- **Risks**:
  - Profile builder complexity and user experience
  - Marketplace search performance
  - Use case validation complexity
  - Frontend component integration

- **Mitigations**:
  - Progressive profile builder enhancement
  - Search result caching and optimization
  - Comprehensive validation testing
  - Extensive component testing

- **Rollback plan**:
  - Revert profile builder enhancements
  - Disable marketplace search features
  - Rollback use case validation
  - Restore basic agency display

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
  │   ├── entity/AgencyProfile.java (enhanced)
  │   ├── dto/AgencyProfileDto.java, PublicAgencyDto.java, AgencySearchRequest.java
  │   ├── mapper/AgencyProfileMapper.java, PublicAgencyMapper.java
  │   ├── controller/AgencyProfileController.java, MarketplaceController.java
  │   ├── service/AgencyProfileService.java, MarketplaceService.java, ProofPackService.java
  │   └── repository/AgencyProfileRepository.java
  ├── src/main/resources/
  │   └── db/changelog/V006__agency_profile_enhancements.yaml, V007__marketplace_features.yaml
  └── pom.xml
  
  frontend/
  ├── src/
  │   ├── components/agency/AgencyProfileBuilder.tsx (extend ValidationWizard)
  │   ├── components/agency/UseCaseForm.tsx (extend existing form components)
  │   ├── components/agency/SupportPackageForm.tsx (extend existing form components)
  │   ├── components/marketplace/AgencySearch.tsx (extend existing search components)
  │   ├── components/marketplace/PublicAgencyProfile.tsx (extend existing card components)
  │   ├── app/(dashboard)/owner/agency/profile/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/owner/agency/use-cases/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/owner/agency/packages/page.tsx (extend dashboard layout patterns)
  │   ├── app/(minimal)/marketplace/page.tsx (extend minimal layout patterns)
  │   └── app/(minimal)/agencies/[id]/page.tsx (extend minimal layout patterns)
  └── package.json
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: Enhanced agency and marketplace endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(agency): implement comprehensive agency profile builder [P1.1-E]`
  - `feat(marketplace): add basic marketplace and search functionality [P1.1-F]`