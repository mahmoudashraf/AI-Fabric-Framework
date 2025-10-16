# PLAN — Batch 3.2 (Tickets: P3.1-C, P3.1-D)

## 0) Summary
- **Goal**: Implement advanced application scoring and payment integration for AIMatchly platform
- **Architecture note**: Sophisticated application scoring system with transparent scoring and comprehensive payment integration with Stripe
- **Tickets covered**: P3.1-C (Advanced Application Scoring & Management), P3.1-D (Payment Integration & Credit Purchases)
- **Non-goals / out of scope**: Advanced analytics, complex project management, advanced reporting

## 1) Backend changes
- **Entities (new/updated)**: 
  - `Application` (enhanced with advanced scoring, transparent scoring breakdown)
  - `ApplicationScore` (id, applicationId, fitScore, evidenceScore, clarityScore, totalScore, breakdown, createdAt)
  - `Payment` (id, ownerId, type, amount, status, providerRef, createdAt, updatedAt)
  - `CreditPack` (id, name, credits, price, active, createdAt, updatedAt)
  - `ProPlan` (id, name, monthlyPrice, annualPrice, features, active, createdAt, updatedAt)

- **Liquibase changesets**:
  - `V018__advanced_scoring.yaml` - Add application scoring system
  - `V019__payment_system.yaml` - Create payments, credit_packs, pro_plans tables

- **Endpoints (method → path → purpose)**:
  - `POST /api/applications/{id}/score` → Score application
  - `GET /api/applications/{id}/scoring-breakdown` → Get scoring breakdown
  - `POST /api/applications/{id}/shortlist` → Shortlist application
  - `POST /api/postings/{id}/close` → Close posting
  - `POST /api/billing/credit-packs/checkout` → Checkout credit pack
  - `POST /api/billing/credit-packs/webhook` → Credit pack webhook
  - `POST /api/billing/pro-plan/checkout` → Checkout Pro Plan
  - `POST /api/billing/pro-plan/webhook` → Pro Plan webhook
  - `GET /api/billing/history` → Get billing history
  - `POST /api/billing/refund` → Process refund

- **Advanced Scoring Features**:
  - Fit (0-40), Evidence (0-40), Clarity (0-20) scoring
  - Transparent scoring breakdown to buyers
  - Auto-close on cap or deadline
  - Shortlist up to 3 for consultations
  - Scoring algorithm improvements

- **Payment Integration Features**:
  - Stripe PaymentIntents integration
  - Credit pack purchases
  - Pro Plan subscription management
  - Idempotent webhook handling
  - Billing history and receipts

- **DTOs & mappers**:
  - `ApplicationScoreDto`, `ApplicationScoreMapper` (MapStruct)
  - `PaymentDto`, `PaymentMapper` (MapStruct)
  - `CreditPackDto`, `CreditPackMapper` (MapStruct)
  - `ProPlanDto`, `ProPlanMapper` (MapStruct)
  - `ScoringRequest`, `CheckoutRequest`, `WebhookRequest`

- **Services**:
  - `ScoringService` - Advanced application scoring
  - `PaymentService` - Payment processing
  - `SubscriptionService` - Pro Plan management
  - `WebhookService` - Enhanced for payments
  - `BillingService` - Billing management

- **Validation rules**:
  - Scoring validation
  - Payment validation
  - Webhook validation
  - Billing validation

- **Error model (problem+json codes)**:
  - `400` - Validation errors
  - `401` - Unauthorized
  - `403` - Forbidden (insufficient credits)
  - `404` - Application/payment not found
  - `409` - Duplicate payment
  - `422` - Payment failed

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**:
  - `/projects/[id]/applications/scoring` - Application scoring interface
  - `/projects/[id]/applications/[id]/scoring` - Individual application scoring
  - `/billing/credits` - Credit purchase
  - `/billing/pro-plan` - Pro Plan subscription
  - `/billing/history` - Billing history
  - `/billing/receipts` - Receipt management

- **Components (reusing existing)**:
  - **Forms**: Extend `ValidationWizard` from `/components/forms/forms-wizard/ValidationWizard/` for payment
  - **Cards**: Use `MainCard`, `SubCard` from `/components/ui-component/cards/` for billing display
  - **Tables**: Reuse table patterns from `/components/users/list/Style1/UserList.tsx` for applications
  - **Charts**: Extend existing chart components for scoring visualization
  - **Status indicators**: Use `Chip` component from `/components/ui-component/extended/Chip.tsx` for payment status

- **Data fetching (React Query keys)**:
  - `['applications', 'scoring']` - Application scoring
  - `['applications', applicationId, 'scoring-breakdown']` - Scoring breakdown
  - `['billing', 'credits']` - Credit purchases
  - `['billing', 'pro-plan']` - Pro Plan subscription
  - `['billing', 'history']` - Billing history
  - `['billing', 'receipts']` - Receipts

- **Forms & validation (RHF + Zod)**:
  - Payment form with Stripe integration
  - Pro Plan subscription form
  - Scoring configuration form
  - Billing management form

- **Scoring Features**:
  - Advanced scoring algorithm
  - Transparent scoring breakdown
  - Scoring visualization
  - Shortlisting workflow

- **Payment Features**:
  - Stripe payment integration
  - Credit pack purchases
  - Pro Plan subscription
  - Billing history and receipts

- **Accessibility notes**: 
  - ARIA labels for payment forms
  - Keyboard navigation support
  - Screen reader friendly scoring display
  - Payment interface accessibility

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all endpoints
  - `@ApiResponse` for error codes
  - `@SecurityRequirement` for JWT authentication
  - `@Tag` for endpoint grouping

- **Regenerate FE types**:
  - Generate TypeScript types from OpenAPI spec
  - Create new types for scoring and payment functionality
  - Update existing types for enhanced features

## 4) Tests
- **BE unit/slice**:
  - `ScoringServiceTest` - Advanced application scoring
  - `PaymentServiceTest` - Payment processing
  - `SubscriptionServiceTest` - Pro Plan management
  - `BillingServiceTest` - Billing management
  - `ScoringControllerTest` - Scoring endpoints
  - `PaymentControllerTest` - Payment endpoints

- **BE integration (Testcontainers)**:
  - `ScoringControllerIntegrationTest` - Scoring flow
  - `PaymentControllerIntegrationTest` - Payment flow
  - `WebhookControllerIntegrationTest` - Webhook handling

- **FE unit**:
  - `ApplicationScoring.test.tsx` - Application scoring testing
  - `PaymentIntegration.test.tsx` - Payment integration testing
  - `ProPlanSubscription.test.tsx` - Pro Plan subscription testing
  - `BillingHistory.test.tsx` - Billing history testing

- **E2E happy-path (Playwright)**:
  - Application scoring → Shortlisting → Consultation booking
  - Credit purchase → Payment processing → Credit balance update
  - Pro Plan subscription → Feature activation

- **Coverage target**: 80%+ for scoring and payment services, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample application scores
  - Credit pack configurations
  - Pro Plan configurations
  - Payment test data
  - Billing history examples

- **How to run**:
  - `mvn liquibase:update` - Apply migrations
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"` - Run with seed data

## 6) Acceptance Criteria Matrix
- **P3.1-C**:
  - [ ] AC#1: Fit (0-40), Evidence (0-40), Clarity (0-20) scoring system
  - [ ] AC#2: Transparent scoring breakdown to buyers
  - [ ] AC#3: Auto-close on cap or deadline
  - [ ] AC#4: Shortlist up to 3 for consultations
  - [ ] AC#5: Scoring algorithm improvements

- **P3.1-D**:
  - [ ] AC#1: Stripe PaymentIntents integration
  - [ ] AC#2: Credit pack purchases with webhook handling
  - [ ] AC#3: Pro Plan subscription management
  - [ ] AC#4: Idempotent webhook handling
  - [ ] AC#5: Billing history and receipts

## 7) Risks & Rollback
- **Risks**:
  - Scoring algorithm complexity
  - Payment integration reliability
  - Stripe webhook handling
  - Frontend integration challenges

- **Mitigations**:
  - Comprehensive scoring testing
  - Payment error handling and fallbacks
  - Webhook retry mechanisms
  - Progressive frontend enhancement

- **Rollback plan**:
  - Disable advanced scoring
  - Revert to basic application management
  - Rollback payment integration
  - Restore basic application functionality

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
  │   ├── entity/ApplicationScore.java, Payment.java, CreditPack.java, ProPlan.java
  │   ├── dto/ApplicationScoreDto.java, PaymentDto.java, CreditPackDto.java, ProPlanDto.java
  │   ├── mapper/ApplicationScoreMapper.java, PaymentMapper.java, CreditPackMapper.java, ProPlanMapper.java
  │   ├── controller/ScoringController.java, PaymentController.java, BillingController.java
  │   ├── service/ScoringService.java, PaymentService.java, SubscriptionService.java, BillingService.java
  │   └── repository/ApplicationScoreRepository.java, PaymentRepository.java, CreditPackRepository.java, ProPlanRepository.java
  ├── src/main/resources/
  │   └── db/changelog/V018__advanced_scoring.yaml, V019__payment_system.yaml
  └── pom.xml (Stripe dependencies)
  
  frontend/
  ├── src/
  │   ├── components/scoring/ApplicationScoring.tsx (extend existing table components)
  │   ├── components/scoring/ScoringBreakdown.tsx (extend existing chart components)
  │   ├── components/payment/PaymentIntegration.tsx (extend existing form components)
  │   ├── components/payment/ProPlanSubscription.tsx (extend existing form components)
  │   ├── components/billing/BillingHistory.tsx (extend existing table components)
  │   ├── app/(dashboard)/projects/[id]/applications/scoring/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/projects/[id]/applications/[id]/scoring/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/billing/credits/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/billing/pro-plan/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/billing/history/page.tsx (extend dashboard layout patterns)
  │   └── app/(dashboard)/billing/receipts/page.tsx (extend dashboard layout patterns)
  └── package.json (Stripe dependencies)
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: New scoring and payment endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(scoring): implement advanced application scoring with transparent breakdown [P3.1-C]`
  - `feat(payment): add Stripe integration and credit purchase system [P3.1-D]`