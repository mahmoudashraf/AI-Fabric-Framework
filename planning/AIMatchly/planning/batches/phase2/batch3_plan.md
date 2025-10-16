# PLAN — Batch 2.3 (Tickets: P2.1-E, P2.1-F)

## 0) Summary
- **Goal**: Implement Proof Pack verification workflow and enhanced matching analytics to complete Phase 2
- **Architecture note**: Comprehensive verification system with analytics dashboard for matching performance and agency insights
- **Tickets covered**: P2.1-E (Proof Pack Verification Workflow), P2.1-F (Enhanced Matching & Analytics)
- **Non-goals / out of scope**: Advanced analytics, complex reporting, payment integration

## 1) Backend changes
- **Entities (new/updated)**: 
  - `ClaimVerification` (id, agencyId, method, status, token, verifiedAt, createdAt)
  - `ProofPackChecklist` (id, agencyId, useCaseVerified, loomVerified, outcomeVerified, artifactsVerified, completedAt, createdAt)
  - `MatchingAnalytics` (id, briefId, agencyId, score, reasons, timestamp, createdAt)
  - `AgencyPerformance` (id, agencyId, period, applications, shortlists, consultations, hires, score, createdAt)

- **Liquibase changesets**:
  - `V014__verification_system.yaml` - Create claim_verifications, proof_pack_checklists tables
  - `V015__analytics_system.yaml` - Create matching_analytics, agency_performance tables

- **Endpoints (method → path → purpose)**:
  - `POST /api/agencies/{id}/claim` → Claim agency via email/domain
  - `POST /api/agencies/{id}/proof/complete` → Complete Proof Pack
  - `GET /api/admin/verification-queue` → Get verification queue
  - `POST /api/admin/verification/{id}/approve` → Approve verification
  - `POST /api/admin/verification/{id}/reject` → Reject verification
  - `GET /api/analytics/matching-stats` → Get matching statistics
  - `GET /api/analytics/agency-performance` → Get agency performance
  - `GET /api/analytics/brief-conversion` → Get brief conversion rates
  - `GET /api/analytics/top-agencies` → Get top performing agencies

- **Proof Pack Verification Features**:
  - Agency claiming via email/domain verification
  - Proof Pack completion checklist
  - Verifier review interface
  - Verification status badges
  - Automated verification triggers

- **Enhanced Analytics Features**:
  - Matching algorithm performance metrics
  - Agency performance analytics
  - Brief-to-match conversion rates
  - Top-performing agencies insights
  - Real-time analytics dashboard

- **DTOs & mappers**:
  - `ClaimVerificationDto`, `ClaimVerificationMapper` (MapStruct)
  - `ProofPackChecklistDto`, `ProofPackChecklistMapper` (MapStruct)
  - `MatchingAnalyticsDto`, `MatchingAnalyticsMapper` (MapStruct)
  - `AgencyPerformanceDto`, `AgencyPerformanceMapper` (MapStruct)
  - `VerificationRequest`, `AnalyticsRequest`

- **Services**:
  - `VerificationService` - Proof Pack verification workflow
  - `ClaimService` - Agency claiming process
  - `AnalyticsService` - Matching and performance analytics
  - `PerformanceService` - Agency performance tracking
  - `NotificationService` - Enhanced for verification

- **Validation rules**:
  - Verification request validation
  - Proof Pack checklist validation
  - Analytics query validation
  - Performance data validation

- **Error model (problem+json codes)**:
  - `400` - Validation errors
  - `401` - Unauthorized
  - `403` - Forbidden (not verifier)
  - `404` - Verification not found
  - `409` - Already verified
  - `422` - Verification failed

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**:
  - `/owner/agency/verification` - Agency verification process
  - `/owner/agency/proof-pack` - Proof Pack completion
  - `/admin/verification` - Admin verification queue
  - `/analytics/matching` - Matching analytics
  - `/analytics/agency-performance` - Agency performance
  - `/owner/analytics` - Owner analytics dashboard

- **Components (reusing existing)**:
  - **Forms**: Extend `ValidationWizard` from `/components/forms/forms-wizard/ValidationWizard/` for verification
  - **Cards**: Use `MainCard`, `SubCard` from `/components/ui-component/cards/` for analytics display
  - **Tables**: Reuse table patterns from `/components/users/list/Style1/UserList.tsx` for verification queue
  - **Charts**: Extend existing chart components for analytics visualization
  - **Status indicators**: Use `Chip` component from `/components/ui-component/extended/Chip.tsx` for verification status

- **Data fetching (React Query keys)**:
  - `['verification', 'claim']` - Agency claiming status
  - `['verification', 'proof-pack']` - Proof Pack completion
  - `['admin', 'verification-queue']` - Admin verification queue
  - `['analytics', 'matching']` - Matching analytics
  - `['analytics', 'agency-performance']` - Agency performance
  - `['analytics', 'brief-conversion']` - Brief conversion rates

- **Forms & validation (RHF + Zod)**:
  - Agency claiming form
  - Proof Pack completion form
  - Verification review form
  - Analytics filter forms

- **Verification Features**:
  - Agency claiming workflow
  - Proof Pack completion checklist
  - Verification status tracking
  - Admin review interface

- **Analytics Features**:
  - Matching performance metrics
  - Agency performance dashboard
  - Brief conversion analytics
  - Top agencies insights

- **Accessibility notes**: 
  - ARIA labels for verification forms
  - Keyboard navigation support
  - Screen reader friendly analytics
  - Verification status accessibility

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all endpoints
  - `@ApiResponse` for error codes
  - `@SecurityRequirement` for JWT authentication
  - `@Tag` for endpoint grouping

- **Regenerate FE types**:
  - Generate TypeScript types from OpenAPI spec
  - Create new types for verification and analytics functionality
  - Update existing types for enhanced features

## 4) Tests
- **BE unit/slice**:
  - `VerificationServiceTest` - Proof Pack verification workflow
  - `ClaimServiceTest` - Agency claiming process
  - `AnalyticsServiceTest` - Matching and performance analytics
  - `PerformanceServiceTest` - Agency performance tracking
  - `VerificationControllerTest` - Verification endpoints
  - `AnalyticsControllerTest` - Analytics endpoints

- **BE integration (Testcontainers)**:
  - `VerificationControllerIntegrationTest` - Verification flow
  - `AnalyticsControllerIntegrationTest` - Analytics flow
  - `ClaimControllerIntegrationTest` - Agency claiming flow

- **FE unit**:
  - `VerificationWorkflow.test.tsx` - Verification workflow testing
  - `ProofPackCompletion.test.tsx` - Proof Pack completion testing
  - `AnalyticsDashboard.test.tsx` - Analytics dashboard testing
  - `AgencyPerformance.test.tsx` - Agency performance testing

- **E2E happy-path (Playwright)**:
  - Agency claiming → Proof Pack completion → Verification workflow
  - Analytics dashboard → Performance metrics → Insights
  - Admin verification → Approval/rejection workflow

- **Coverage target**: 80%+ for verification and analytics services, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample verification requests
  - Proof Pack completion data
  - Matching analytics data
  - Agency performance metrics
  - Verification queue test data

- **How to run**:
  - `mvn liquibase:update` - Apply migrations
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"` - Run with seed data

## 6) Acceptance Criteria Matrix
- **P2.1-E**:
  - [ ] AC#1: Agency claiming via email/domain verification
  - [ ] AC#2: Proof Pack completion checklist with validation
  - [ ] AC#3: Verifier review interface with approve/reject
  - [ ] AC#4: Verification status badges and tracking
  - [ ] AC#5: Automated verification triggers

- **P2.1-F**:
  - [ ] AC#1: Matching algorithm performance metrics
  - [ ] AC#2: Agency performance analytics dashboard
  - [ ] AC#3: Brief-to-match conversion rates
  - [ ] AC#4: Top-performing agencies insights
  - [ ] AC#5: Real-time analytics updates

## 7) Risks & Rollback
- **Risks**:
  - Verification workflow complexity
  - Analytics performance and accuracy
  - Frontend chart integration
  - Data consistency issues

- **Mitigations**:
  - Comprehensive verification testing
  - Analytics data validation
  - Progressive chart enhancement
  - Data consistency checks

- **Rollback plan**:
  - Disable verification system
  - Revert to basic analytics
  - Rollback verification features
  - Restore basic verification functionality

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
  │   ├── entity/ClaimVerification.java, ProofPackChecklist.java, MatchingAnalytics.java, AgencyPerformance.java
  │   ├── dto/ClaimVerificationDto.java, ProofPackChecklistDto.java, MatchingAnalyticsDto.java, AgencyPerformanceDto.java
  │   ├── mapper/ClaimVerificationMapper.java, ProofPackChecklistMapper.java, MatchingAnalyticsMapper.java, AgencyPerformanceMapper.java
  │   ├── controller/VerificationController.java, AnalyticsController.java
  │   ├── service/VerificationService.java, ClaimService.java, AnalyticsService.java, PerformanceService.java
  │   └── repository/ClaimVerificationRepository.java, ProofPackChecklistRepository.java, MatchingAnalyticsRepository.java, AgencyPerformanceRepository.java
  ├── src/main/resources/
  │   └── db/changelog/V014__verification_system.yaml, V015__analytics_system.yaml
  └── pom.xml
  
  frontend/
  ├── src/
  │   ├── components/verification/VerificationWorkflow.tsx (extend ValidationWizard)
  │   ├── components/verification/ProofPackCompletion.tsx (extend existing form components)
  │   ├── components/analytics/MatchingAnalytics.tsx (extend existing chart components)
  │   ├── components/analytics/AgencyPerformance.tsx (extend existing chart components)
  │   ├── app/(dashboard)/owner/agency/verification/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/owner/agency/proof-pack/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/admin/verification/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/analytics/matching/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/analytics/agency-performance/page.tsx (extend dashboard layout patterns)
  │   └── app/(dashboard)/owner/analytics/page.tsx (extend dashboard layout patterns)
  └── package.json
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: New verification and analytics endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(verification): implement Proof Pack verification workflow [P2.1-E]`
  - `feat(analytics): add enhanced matching and performance analytics [P2.1-F]`