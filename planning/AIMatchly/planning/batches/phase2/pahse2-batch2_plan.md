# PLAN — Batch 2.2 (Tickets: P2.1-C, P2.1-D)

## 0) Summary
- **Goal**: Implement consultation booking system and credits system foundation for AIMatchly platform
- **Architecture note**: Integrated scheduling system with Cal.com/Calendly and comprehensive credits tracking system
- **Tickets covered**: P2.1-C (Consultation Booking System), P2.1-D (Credits System Foundation)
- **Non-goals / out of scope**: Payment integration, advanced analytics, complex notification rules

## 1) Backend changes
- **Entities (new/updated)**: 
  - `Consultation` (id, buyerId, agencyId, briefId, method, status, paymentStatus, scheduledAt, duration, notes, createdAt, updatedAt)
  - `CreditAccount` (id, ownerType, ownerId, balance, updatedAt)
  - `CreditTxn` (id, accountId, type, amount, reason, refType, refId, idempotencyKey, createdAt)
  - `SchedulerIntegration` (id, agencyId, provider, config, active, createdAt, updatedAt)

- **Liquibase changesets**:
  - `V012__consultation_system.yaml` - Create consultations, scheduler_integrations tables
  - `V013__credits_system.yaml` - Create credit_accounts, credit_txns tables

- **Endpoints (method → path → purpose)**:
  - `POST /api/consultations` → Create consultation request
  - `GET /api/consultations` → List consultations
  - `POST /api/consultations/{id}/accept` → Accept consultation
  - `POST /api/consultations/{id}/cancel` → Cancel consultation
  - `POST /api/integrations/scheduler/webhook` → Scheduler webhook
  - `GET /api/credits/balance` → Get credit balance
  - `POST /api/credits/spend` → Spend credits
  - `POST /api/credits/grant` → Grant credits
  - `GET /api/credits/history` → Get credit history
  - `POST /api/agencies/{id}/scheduler` → Configure scheduler

- **Consultation Booking Features**:
  - Embedded scheduler integration (Cal.com/Calendly)
  - ICS email invites
  - Consultation context from brief/project
  - Webhook analytics and tracking
  - Native request system with proposed slots

- **Credits System Features**:
  - Credit balance tracking
  - Idempotent credit transactions
  - Credit earning rules (Proof Pack +10, fast response +2, etc.)
  - Credit spending rules (apply to project 5 credits, etc.)
  - Credit history and audit trail

- **DTOs & mappers**:
  - `ConsultationDto`, `ConsultationMapper` (MapStruct)
  - `CreditAccountDto`, `CreditAccountMapper` (MapStruct)
  - `CreditTxnDto`, `CreditTxnMapper` (MapStruct)
  - `SchedulerIntegrationDto`, `SchedulerIntegrationMapper` (MapStruct)
  - `CreateConsultationRequest`, `SpendCreditsRequest`, `GrantCreditsRequest`

- **Services**:
  - `ConsultationService` - Consultation management
  - `SchedulerService` - Scheduler integration
  - `CreditService` - Credits management
  - `WebhookService` - Webhook handling
  - `NotificationService` - Enhanced for consultations

- **Validation rules**:
  - Consultation validation
  - Credit transaction validation
  - Scheduler configuration validation
  - Webhook validation

- **Error model (problem+json codes)**:
  - `400` - Validation errors
  - `401` - Unauthorized
  - `403` - Forbidden (insufficient credits)
  - `404` - Consultation not found
  - `409` - Duplicate transaction
  - `422` - Scheduler integration failed

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**:
  - `/consultations` - Consultation management
  - `/consultations/[id]` - Consultation details
  - `/consultations/new` - Create consultation
  - `/credits/balance` - Credit balance
  - `/credits/history` - Credit history
  - `/agency/scheduler` - Scheduler configuration

- **Components (reusing existing)**:
  - **Forms**: Extend `ValidationWizard` from `/components/forms/forms-wizard/ValidationWizard/` for consultation creation
  - **Cards**: Use `MainCard`, `SubCard` from `/components/ui-component/cards/` for consultation display
  - **Tables**: Reuse table patterns from `/components/users/list/Style1/UserList.tsx` for credit history
  - **Status indicators**: Use `Chip` component from `/components/ui-component/extended/Chip.tsx` for consultation status
  - **Scheduler**: Integrate Cal.com/Calendly embedded components

- **Data fetching (React Query keys)**:
  - `['consultations']` - User's consultations
  - `['consultations', consultationId]` - Specific consultation
  - `['credits', 'balance']` - Credit balance
  - `['credits', 'history']` - Credit history
  - `['scheduler', 'config']` - Scheduler configuration

- **Forms & validation (RHF + Zod)**:
  - Consultation creation form
  - Scheduler configuration form
  - Credit spending form
  - Webhook configuration form

- **Consultation Features**:
  - Embedded scheduler integration
  - ICS email invite generation
  - Consultation context display
  - Status tracking and management

- **Credits Features**:
  - Credit balance display
  - Credit history with filtering
  - Credit earning rules display
  - Credit spending interface

- **Accessibility notes**: 
  - ARIA labels for consultation forms
  - Keyboard navigation support
  - Screen reader friendly scheduler integration
  - Credit balance accessibility

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all endpoints
  - `@ApiResponse` for error codes
  - `@SecurityRequirement` for JWT authentication
  - `@Tag` for endpoint grouping

- **Regenerate FE types**:
  - Generate TypeScript types from OpenAPI spec
  - Create new types for consultation and credits functionality
  - Update existing types for enhanced features

## 4) Tests
- **BE unit/slice**:
  - `ConsultationServiceTest` - Consultation management
  - `SchedulerServiceTest` - Scheduler integration
  - `CreditServiceTest` - Credits management
  - `WebhookServiceTest` - Webhook handling
  - `ConsultationControllerTest` - Consultation endpoints
  - `CreditControllerTest` - Credit endpoints

- **BE integration (Testcontainers)**:
  - `ConsultationControllerIntegrationTest` - Consultation flow
  - `CreditControllerIntegrationTest` - Credit management flow
  - `WebhookControllerIntegrationTest` - Webhook handling

- **FE unit**:
  - `ConsultationForm.test.tsx` - Consultation form testing
  - `SchedulerIntegration.test.tsx` - Scheduler integration testing
  - `CreditBalance.test.tsx` - Credit balance testing
  - `CreditHistory.test.tsx` - Credit history testing

- **E2E happy-path (Playwright)**:
  - Consultation creation → Scheduler integration → Booking flow
  - Credit management → Spending → History tracking
  - Webhook handling → Status updates

- **Coverage target**: 80%+ for consultation and credit services, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Sample consultations in different statuses
  - Credit accounts with initial balances
  - Credit transaction history
  - Scheduler integration configurations
  - Webhook test data

- **How to run**:
  - `mvn liquibase:update` - Apply migrations
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"` - Run with seed data

## 6) Acceptance Criteria Matrix
- **P2.1-C**:
  - [ ] AC#1: Embedded scheduler integration with Cal.com/Calendly
  - [ ] AC#2: ICS email invites with consultation context
  - [ ] AC#3: Webhook analytics and tracking
  - [ ] AC#4: Native request system with proposed slots
  - [ ] AC#5: Consultation status management

- **P2.1-D**:
  - [ ] AC#1: Credit balance tracking and display
  - [ ] AC#2: Idempotent credit transactions
  - [ ] AC#3: Credit earning rules implementation
  - [ ] AC#4: Credit spending rules implementation
  - [ ] AC#5: Credit history and audit trail

## 7) Risks & Rollback
- **Risks**:
  - Scheduler integration complexity
  - Credit system accuracy and consistency
  - Webhook reliability
  - Frontend integration challenges

- **Mitigations**:
  - Comprehensive scheduler testing
  - Extensive credit system validation
  - Webhook error handling and retries
  - Progressive frontend enhancement

- **Rollback plan**:
  - Disable scheduler integration
  - Revert to basic consultation system
  - Rollback credit system
  - Restore basic consultation functionality

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
  │   ├── entity/Consultation.java, CreditAccount.java, CreditTxn.java, SchedulerIntegration.java
  │   ├── dto/ConsultationDto.java, CreditAccountDto.java, CreditTxnDto.java, SchedulerIntegrationDto.java
  │   ├── mapper/ConsultationMapper.java, CreditAccountMapper.java, CreditTxnMapper.java, SchedulerIntegrationMapper.java
  │   ├── controller/ConsultationController.java, CreditController.java, WebhookController.java
  │   ├── service/ConsultationService.java, SchedulerService.java, CreditService.java, WebhookService.java
  │   └── repository/ConsultationRepository.java, CreditAccountRepository.java, CreditTxnRepository.java, SchedulerIntegrationRepository.java
  ├── src/main/resources/
  │   └── db/changelog/V012__consultation_system.yaml, V013__credits_system.yaml
  └── pom.xml
  
  frontend/
  ├── src/
  │   ├── components/consultation/ConsultationForm.tsx (extend ValidationWizard)
  │   ├── components/consultation/SchedulerIntegration.tsx (extend existing form components)
  │   ├── components/credits/CreditBalance.tsx (extend existing card components)
  │   ├── components/credits/CreditHistory.tsx (extend existing table components)
  │   ├── app/(dashboard)/consultations/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/consultations/[id]/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/consultations/new/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/credits/balance/page.tsx (extend dashboard layout patterns)
  │   ├── app/(dashboard)/credits/history/page.tsx (extend dashboard layout patterns)
  │   └── app/(dashboard)/agency/scheduler/page.tsx (extend dashboard layout patterns)
  └── package.json
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: New consultation and credit endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(consultation): implement consultation booking system with scheduler integration [P2.1-C]`
  - `feat(credits): add credits system foundation with tracking and management [P2.1-D]`