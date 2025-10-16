# PLAN — Batch 3.1 (Tickets: P3.1-A, P3.1-B)

## 0) Summary
- **Goal**: Implement controlled project publishing with anonymity and unstructured ingestion system for AIMatchly platform
- **Architecture note**: Advanced project management with anonymity controls and AI-powered content ingestion from multiple sources
- **Tickets covered**: P3.1-A (Controlled Project Publishing with Anonymity), P3.1-B (Unstructured Ingestion System)
- **Non-goals / out of scope**: Payment integration, advanced analytics, complex project management

## 1) Backend changes
- **Entities (new/updated)**: 
  - `ProjectPosting` (enhanced with anonymity controls, application caps, identity reveal)
  - `Application` (enhanced with scoring system, anonymity handling)
  - `IngestionJob` (id, source, urlOrFile, text, confidence, profileId, status, createdAt, updatedAt)
  - `SourceLog` (id, entity, field, sourceItems, confidence, createdAt)
  - `AnonymitySettings` (id, projectId, hideBuyerIdentity, revealOnShortlist, ndaRequired, createdAt)

- **Liquibase changesets**:
  - `V016__controlled_publishing.yaml` - Add anonymity controls and application caps
  - `V017__ingestion_system.yaml` - Create ingestion_jobs, source_logs, anonymity_settings tables

- **Endpoints (method → path → purpose)**:
  - `POST /api/projects/{id}/publish-controlled` → Publish project with anonymity
  - `GET /api/postings/public` → Get public project postings
  - `POST /api/postings/{id}/applications` → Submit application (with anonymity)
  - `GET /api/applications/{id}/scoring` → Get application scoring
  - `POST /api/ingest/url` → Ingest content from URL
  - `POST /api/ingest/pdf` → Ingest content from PDF
  - `POST /api/ingest/image` → Ingest content from image
  - `GET /api/ingest/jobs` → List ingestion jobs
  - `POST /api/ingest/jobs/{id}/review` → Review ingestion job
  - `POST /api/ingest/jobs/{id}/approve` → Approve ingestion job

- **Controlled Publishing Features**:
  - Anonymity controls (hide buyer identity by default)
  - Application caps (7-10 applications)
  - Identity reveal on shortlist
  - Public project marketplace
  - NDA acceptance workflow

- **Ingestion System Features**:
  - URL/PDF/image ingestion with field-level citations
  - Confidence scoring and human review
  - Admin review interface with accept/edit/reject
  - SourceLog for provenance tracking
  - OCR for images and PDFs

- **DTOs & mappers**:
  - `AnonymitySettingsDto`, `AnonymitySettingsMapper` (MapStruct)
  - `IngestionJobDto`, `IngestionJobMapper` (MapStruct)
  - `SourceLogDto`, `SourceLogMapper` (MapStruct)
  - `ControlledPublishingRequest`, `IngestionRequest`, `ReviewRequest`

- **Services**:
  - `ControlledPublishingService` - Controlled project publishing
  - `AnonymityService` - Anonymity management
  - `IngestionService` - Content ingestion
  - `ReviewService` - Ingestion review
  - `LLMService` - Enhanced for ingestion

- **Validation rules**:
  - Anonymity settings validation
  - Ingestion job validation
  - Source log validation
  - Review request validation

- **Error model (problem+json codes)**:
  - `400` - Validation errors
  - `401` - Unauthorized
  - `403` - Forbidden (not eligible)
  - `404` - Project/ingestion job not found
  - `409` - Application already submitted
  - `422` - Ingestion failed

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**:
  - `/projects/[id]/publish-controlled` - Controlled publishing wizard
  - `/marketplace/projects` - Public project marketplace
  - `/projects/[id]/applications` - Application management with anonymity
  - `/owner/agency/ingestion` - Ingestion interface
  - `/admin/ingestion-review` - Admin review dashboard
  - `/ingestion/jobs/[id]` - Ingestion job details

- **Components (reusing existing)**:
  - **Forms**: Extend `ValidationWizard` from `/components/forms/forms-wizard/ValidationWizard/` for controlled publishing
  - **Cards**: Use `MainCard`, `SubCard` from `/components/ui-component/cards/` for project display
  - **Tables**: Reuse table patterns from `/components/users/list/Style1/UserList.tsx` for applications
  - **Upload**: Extend existing upload components for ingestion
  - **Status indicators**: Use `Chip` component from `/components/ui-component/extended/Chip.tsx` for status

- **Data fetching (React Query keys)**:
  - `['projects', 'controlled-publishing']` - Controlled publishing projects
  - `['postings', 'public']` - Public project postings
  - `['applications', 'anonymous']` - Anonymous applications
  - `['ingestion', 'jobs']` - Ingestion jobs
  - `['ingestion', 'jobs', jobId]` - Specific ingestion job
  - `['admin', 'ingestion-review']` - Admin review queue

- **Forms & validation (RHF + Zod)**:
  - Controlled publishing wizard with anonymity settings
  - Ingestion form with file upload
  - Application form with anonymity handling
  - Review form for admin approval

- **Controlled Publishing Features**:
  - Anonymity settings configuration
  - Application cap management
  - Identity reveal workflow
  - Public project marketplace

- **Ingestion Features**:
  - Multi-source content ingestion
  - Confidence scoring display
  - Admin review interface
  - Source provenance tracking

- **Accessibility notes**: 
  - ARIA labels for publishing forms
  - Keyboard navigation support
  - Screen reader friendly anonymity controls
  - Ingestion interface accessibility

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all endpoints
  - `@ApiResponse` for error codes
  - `@SecurityRequirement` for JWT authentication
  - `@Tag` for endpoint grouping

- **Regenerate FE types**:
  - Generate TypeScript types from OpenAPI spec
  - Create new types for controlled publishing and ingestion functionality
  - Update existing types for enhanced features

## 4) Tests
- **BE unit/slice**:
  - `ControlledPublishingServiceTest` - Controlled publishing logic
  - `AnonymityServiceTest` - Anonymity management
  - `IngestionServiceTest` - Content ingestion
  - `ReviewServiceTest` - Ingestion review
  - `ControlledPublishingControllerTest` - Controlled publishing endpoints
  - `IngestionControllerTest` - Ingestion endpoints

- **BE integration (Testcontainers)**:
  - `ControlledPublishingControllerIntegrationTest` - Controlled publishing flow
  - `IngestionControllerIntegrationTest` - Ingestion flow
  - `ReviewControllerIntegrationTest` - Review workflow

- **FE unit**:
  - `ControlledPublishingWizard.test.tsx` - Controlled publishing testing
  - `PublicProjectMarketplace.test.tsx` - Public marketplace testing
  - `IngestionInterface.test.tsx` - Ingestion interface testing
  - `AdminReviewDashboard.test.tsx` - Admin review testing

- **E2E happy-path (Playwright)**:
  - Controlled publishing → Anonymity settings → Public marketplace
  - Ingestion → Review → Approval workflow
  - Application submission → Anonymity handling

- **Coverage target**: 80%+ for controlled publishing and ingestion services, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample controlled publishing projects
  - Ingestion job templates
  - Source log examples
  - Anonymity settings configurations
  - Review queue test data

- **How to run**:
  - `mvn liquibase:update` - Apply migrations
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"` - Run with seed data

## 6) Acceptance Criteria Matrix
- **P3.1-A**:
  - [ ] AC#1: Controlled project publishing with anonymity controls
  - [ ] AC#2: Application caps (7-10 applications) with management
  - [ ] AC#3: Identity reveal on shortlist workflow
  - [ ] AC#4: Public project marketplace with filtering
  - [ ] AC#5: NDA acceptance workflow

- **P3.1-B**:
  - [ ] AC#1: URL/PDF/image ingestion with field-level citations
  - [ ] AC#2: Confidence scoring and human review interface
  - [ ] AC#3: Admin review with accept/edit/reject options
  - [ ] AC#4: SourceLog for provenance tracking
  - [ ] AC#5: OCR for images and PDFs

## 7) Risks & Rollback
- **Risks**:
  - Anonymity system complexity
  - Ingestion system reliability
  - OCR accuracy and performance
  - Frontend integration challenges

- **Mitigations**:
  - Comprehensive anonymity testing
  - Ingestion system error handling
  - OCR fallback mechanisms
  - Progressive frontend enhancement

- **Rollback plan**:
  - Disable controlled publishing
  - Revert to basic project management
  - Rollback ingestion system
  - Restore basic project functionality

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
  │   ├── entity/AnonymitySettings.java, IngestionJob.java, SourceLog.java
  │   ├── dto/AnonymitySettingsDto.java, IngestionJobDto.java, SourceLogDto.java
  │   ├── mapper/AnonymitySettingsMapper.java, IngestionJobMapper.java, SourceLogMapper.java
  │   ├── controller/ControlledPublishingController.java, IngestionController.java, ReviewController.java
  │   ├── service/ControlledPublishingService.java, AnonymityService.java, IngestionService.java, ReviewService.java
  │   └── repository/AnonymitySettingsRepository.java, IngestionJobRepository.java, SourceLogRepository.java
  ├── src/main/resources/
  │   └── db/changelog/V016__controlled_publishing.yaml, V017__ingestion_system.yaml
  └── pom.xml
  
  frontend/
  ├── src/
  │   ├── components/project/ControlledPublishingWizard.tsx (extend ValidationWizard)
  │   ├── components/project/PublicProjectMarketplace.tsx (extend existing card components)
  │   ├── components/ingestion/IngestionInterface.tsx (extend existing upload components)
  │   ├── components/ingestion/AdminReviewDashboard.tsx (extend existing table components)
  │   ├── app/(dashboard)/projects/[id]/publish-controlled/page.tsx (extend dashboard layout patterns)
  │   ├── app/(minimal)/marketplace/projects/page.tsx (extend minimal layout patterns)
  │   ├── app/(dashboard)/projects/[id]/applications/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/owner/agency/ingestion/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/admin/ingestion-review/page.tsx (extend dashboard layout patterns)
  │   └── app/(minimal)/ingestion/jobs/[id]/page.tsx (extend minimal layout patterns)
  └── package.json
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: New controlled publishing and ingestion endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(project): implement controlled project publishing with anonymity [P3.1-A]`
  - `feat(ingestion): add unstructured ingestion system with AI processing [P3.1-B]`