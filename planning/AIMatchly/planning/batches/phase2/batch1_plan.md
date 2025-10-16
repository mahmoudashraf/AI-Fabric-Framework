# PLAN — Batch 2.1 (Tickets: P2.1-A, P2.1-B)

## 0) Summary
- **Goal**: Implement project pack generation and invite-only project postings for AIMatchly platform
- **Architecture note**: LLM-powered project pack generation with structured application system and invite-only project management
- **Tickets covered**: P2.1-A (Project Pack Generation), P2.1-B (Invite-Only Project Postings)
- **Non-goals / out of scope**: Payment integration, advanced analytics, complex project management

## 1) Backend changes
- **Entities (new/updated)**: 
  - `Project` (id, briefId, title, objectives[], successMetrics, scopeIn[], scopeOut[], integrations[], dataConstraints, risks[], assumptions[], supportTier, priceBand, timelineWeeks, status, shareToken, createdAt, updatedAt)
  - `ProjectMilestone` (id, projectId, name, description, weeksFromStart, acceptanceTest, createdAt)
  - `ProjectPosting` (id, projectId, mode, visibleTo, capApps, anon, budgetBlind, status, createdAt, updatedAt)
  - `Application` (id, projectPostingId, agencyId, approachBullets[], referencedUseCases[], milestoneFlags, risks, priceBand, timelineWeeks, team, compliance, questions[], scores, status, createdAt, updatedAt)

- **Liquibase changesets**:
  - `V010__project_system.yaml` - Create projects, project_milestones, project_postings, applications tables
  - `V011__application_system.yaml` - Add application scoring and management

- **Endpoints (method → path → purpose)**:
  - `POST /api/projects/from-brief/{briefId}` → Generate project pack from brief
  - `GET /api/projects/{id}` → Get project details
  - `PATCH /api/projects/{id}` → Update project
  - `POST /api/projects/{id}/share` → Generate shareable link
  - `GET /p/{shareToken}` → Public project view
  - `POST /api/projects/{id}/publish` → Publish project (invite-only)
  - `GET /api/postings/{id}/eligibility` → Check agency eligibility
  - `POST /api/postings/{id}/applications` → Submit application
  - `GET /api/postings/{id}/applications` → Get applications
  - `POST /api/applications/{id}/shortlist` → Shortlist application
  - `POST /api/postings/{id}/close` → Close posting

- **Project Pack Generation Features**:
  - Auto-generated from briefs using LLM
  - Non-binding SOW-like structure
  - Milestones with acceptance tests
  - Shareable links and PDF export
  - Project pack templates and libraries

- **Invite-Only Project Postings Features**:
  - Auto-invite top 5-7 matched agencies
  - Structured application forms
  - Application scoring and ranking
  - Shortlisting workflow
  - Application caps and deadlines

- **DTOs & mappers**:
  - `ProjectDto`, `ProjectMapper` (MapStruct)
  - `ProjectMilestoneDto`, `ProjectMilestoneMapper` (MapStruct)
  - `ProjectPostingDto`, `ProjectPostingMapper` (MapStruct)
  - `ApplicationDto`, `ApplicationMapper` (MapStruct)
  - `CreateProjectRequest`, `PublishProjectRequest`, `SubmitApplicationRequest`

- **Services**:
  - `ProjectService` - Project pack generation and management
  - `ProjectMilestoneService` - Milestone management
  - `ProjectPostingService` - Project posting management
  - `ApplicationService` - Application management and scoring
  - `LLMService` - Enhanced for project pack generation

- **Validation rules**:
  - Project pack validation
  - Application form validation
  - Eligibility validation
  - Scoring validation

- **Error model (problem+json codes)**:
  - `400` - Validation errors
  - `401` - Unauthorized
  - `403` - Forbidden (not eligible)
  - `404` - Project/posting not found
  - `409` - Application already submitted
  - `422` - Project pack generation failed

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**:
  - `/projects/[id]` - Project details and management
  - `/briefs/[id]/project-pack` - Project pack generation
  - `/projects/[id]/publish` - Project publishing wizard
  - `/projects/[id]/applications` - Application management
  - `/agency/applications` - Agency application management
  - `/p/[shareToken]` - Public project view

- **Components (reusing existing)**:
  - **Forms**: Extend `ValidationWizard` from `/components/forms/forms-wizard/ValidationWizard/` for project publishing
  - **Cards**: Use `MainCard`, `SubCard` from `/components/ui-component/cards/` for project display
  - **Tables**: Reuse table patterns from `/components/users/list/Style1/UserList.tsx` for applications
  - **Status indicators**: Use `Chip` component from `/components/ui-component/extended/Chip.tsx` for project status
  - **Progress**: Use existing progress components for project milestones

- **Data fetching (React Query keys)**:
  - `['projects', projectId]` - Project details
  - `['projects', projectId, 'milestones']` - Project milestones
  - `['projects', projectId, 'applications']` - Project applications
  - `['postings', postingId, 'applications']` - Posting applications
  - `['applications', 'my-applications']` - User's applications
  - `['projects', 'public', shareToken]` - Public project view

- **Forms & validation (RHF + Zod)**:
  - Project publishing wizard with validation
  - Application submission form
  - Project pack generation form
  - Application scoring form

- **Project Pack Features**:
  - Auto-generated project pack display
  - Milestone timeline visualization
  - Shareable link generation
  - PDF export functionality

- **Application Features**:
  - Structured application forms
  - Application scoring display
  - Shortlisting workflow
  - Application status tracking

- **Accessibility notes**: 
  - ARIA labels for project forms
  - Keyboard navigation support
  - Screen reader friendly project display
  - Application form accessibility

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all endpoints
  - `@ApiResponse` for error codes
  - `@SecurityRequirement` for JWT authentication
  - `@Tag` for endpoint grouping

- **Regenerate FE types**:
  - Generate TypeScript types from OpenAPI spec
  - Create new types for project, application, and posting functionality
  - Update existing types for enhanced features

## 4) Tests
- **BE unit/slice**:
  - `ProjectServiceTest` - Project pack generation and management
  - `ProjectMilestoneServiceTest` - Milestone management
  - `ProjectPostingServiceTest` - Project posting management
  - `ApplicationServiceTest` - Application management and scoring
  - `ProjectControllerTest` - Project endpoints
  - `ApplicationControllerTest` - Application endpoints

- **BE integration (Testcontainers)**:
  - `ProjectControllerIntegrationTest` - Project management flow
  - `ApplicationControllerIntegrationTest` - Application flow
  - `ProjectPackGenerationTest` - LLM integration for project packs

- **FE unit**:
  - `ProjectPackGenerator.test.tsx` - Project pack generation testing
  - `ProjectPublishingWizard.test.tsx` - Project publishing testing
  - `ApplicationForm.test.tsx` - Application form testing
  - `ApplicationManagement.test.tsx` - Application management testing

- **E2E happy-path (Playwright)**:
  - Brief → Project pack generation → Publishing → Application flow
  - Agency application → Scoring → Shortlisting workflow
  - Public project view → Application submission

- **Coverage target**: 80%+ for project services, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample project packs with milestones
  - Project posting templates
  - Application form templates
  - Sample applications with scores
  - Project pack generation test data

- **How to run**:
  - `mvn liquibase:update` - Apply migrations
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"` - Run with seed data

## 6) Acceptance Criteria Matrix
- **P2.1-A**:
  - [ ] AC#1: Auto-generated project packs from briefs using LLM
  - [ ] AC#2: Non-binding SOW-like structure with objectives and scope
  - [ ] AC#3: Milestones with acceptance tests from library
  - [ ] AC#4: Shareable links and PDF export functionality
  - [ ] AC#5: Project pack templates and customization

- **P2.1-B**:
  - [ ] AC#1: Invite-only project postings with auto-invite top agencies
  - [ ] AC#2: Structured application forms with validation
  - [ ] AC#3: Application scoring and ranking system
  - [ ] AC#4: Shortlisting workflow with up to 3 finalists
  - [ ] AC#5: Application caps and deadline management

## 7) Risks & Rollback
- **Risks**:
  - LLM project pack generation complexity
  - Application scoring algorithm accuracy
  - Project posting workflow complexity
  - Frontend form integration

- **Mitigations**:
  - Comprehensive LLM testing and fallbacks
  - Extensive application scoring testing
  - Progressive workflow enhancement
  - Extensive component testing

- **Rollback plan**:
  - Disable project pack generation
  - Revert to basic project management
  - Rollback application system
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
  │   ├── entity/Project.java, ProjectMilestone.java, ProjectPosting.java, Application.java
  │   ├── dto/ProjectDto.java, ProjectMilestoneDto.java, ProjectPostingDto.java, ApplicationDto.java
  │   ├── mapper/ProjectMapper.java, ProjectMilestoneMapper.java, ProjectPostingMapper.java, ApplicationMapper.java
  │   ├── controller/ProjectController.java, ApplicationController.java
  │   ├── service/ProjectService.java, ProjectMilestoneService.java, ProjectPostingService.java, ApplicationService.java
  │   └── repository/ProjectRepository.java, ProjectMilestoneRepository.java, ProjectPostingRepository.java, ApplicationRepository.java
  ├── src/main/resources/
  │   └── db/changelog/V010__project_system.yaml, V011__application_system.yaml
  └── pom.xml
  
  frontend/
  ├── src/
  │   ├── components/project/ProjectPackGenerator.tsx (extend ValidationWizard)
  │   ├── components/project/ProjectPublishingWizard.tsx (extend ValidationWizard)
  │   ├── components/project/ApplicationForm.tsx (extend existing form components)
  │   ├── components/project/ApplicationManagement.tsx (extend existing table components)
  │   ├── app/(dashboard)/projects/[id]/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/projects/[id]/publish/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/projects/[id]/applications/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/agency/applications/page.tsx (extend dashboard layout patterns)
  │   └── app/(minimal)/p/[shareToken]/page.tsx (extend minimal layout patterns)
  └── package.json
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: New project and application endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(project): implement project pack generation with LLM [P2.1-A]`
  - `feat(application): add invite-only project postings and application system [P2.1-B]`