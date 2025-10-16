# PLAN — Batch 2 (Tickets: P1.1-C, P1.1-D)

## 0) Summary
- **Goal**: Implement brief intake and normalization system with basic matching algorithm for AIMatchly platform
- **Architecture note**: LLM integration for brief normalization and embedding generation, with explainable matching algorithm
- **Tickets covered**: P1.1-C (Brief Intake & Normalization), P1.1-D (Basic Matching Algorithm)
- **Non-goals / out of scope**: Advanced matching features, project pack generation, payment integration

## 1) Backend changes
- **Entities (new/updated)**: 
  - `Brief` (id, rawText, normalizedJson, riskFlags[], embedding, status, buyerId, createdAt, updatedAt)
  - `MatchResult` (id, briefId, agencyId, score, reasons[], createdAt)
  - `BriefQuestion` (id, question, type, required, options[], order)

- **Liquibase changesets**:
  - `V004__brief_system.yaml` - Create briefs, match_results, brief_questions tables
  - `V005__matching_algorithm.yaml` - Add matching algorithm configuration

- **Endpoints (method → path → purpose)**:
  - `POST /api/briefs` → Create new brief from intake form
  - `GET /api/briefs/{id}` → Get brief details
  - `POST /api/briefs/{id}/confirm` → Confirm normalized brief
  - `POST /api/match/{briefId}` → Generate matches for brief
  - `GET /api/match/{briefId}` → Get match results
  - `GET /api/brief-questions` → Get intake form questions
  - `POST /api/briefs/{id}/normalize` → Normalize brief with LLM

- **LLM Integration**:
  - GPT-4o-mini for brief normalization (temperature 0.2, JSON Schema enforced)
  - text-embedding-3-large for brief and use-case embeddings
  - Normalization schema with risk flags for ambiguous fields
  - Embedding generation for matching algorithm

- **Matching Algorithm**:
  - Stack fit: 0-40 points (Jaccard similarity weighted by depth)
  - Use-case similarity: 0-30 points (cosine similarity vs agency top-3 tiles)
  - Domain fit: 0-20 points (industry + size + verified status)
  - Proof signal: 0-10 points (badge + verified tiles)
  - Total score: 0-100 points
  - Explainable reasons for each match

- **DTOs & mappers**:
  - `BriefDto`, `BriefMapper` (MapStruct)
  - `MatchResultDto`, `MatchResultMapper` (MapStruct)
  - `BriefQuestionDto`, `BriefQuestionMapper` (MapStruct)
  - `CreateBriefRequest`, `ConfirmBriefRequest`, `MatchRequest`

- **Services**:
  - `BriefService` - Brief CRUD and normalization
  - `MatchingService` - Matching algorithm implementation
  - `LLMService` - LLM integration for normalization and embeddings
  - `EmbeddingService` - Vector operations and similarity calculations

- **Validation rules**:
  - Brief intake form validation (8 required questions)
  - Normalized brief validation
  - Risk flags validation
  - Matching score validation (0-100)

- **Error model (problem+json codes)**:
  - `400` - Validation errors
  - `401` - Unauthorized
  - `404` - Brief not found
  - `422` - Normalization failed
  - `500` - LLM service error

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**:
  - `/briefs/new` - Brief intake form
  - `/briefs/[id]` - Brief details and status
  - `/briefs/[id]/confirm` - Normalization confirmation
  - `/briefs/[id]/matches` - Match results display
  - `/owner/briefs` - User's briefs management

- **Components (reusing existing)**:
  - **Forms**: Extend `ValidationWizard` from `/components/forms/forms-wizard/ValidationWizard/` for brief intake
  - **Cards**: Use `MainCard`, `SubCard` from `/components/ui-component/cards/` for brief display
  - **Tables**: Reuse table patterns from `/components/users/list/Style1/UserList.tsx` for match results
  - **Progress**: Use existing progress components for form steps
  - **Status indicators**: Use `Chip` component from `/components/ui-component/extended/Chip.tsx` for brief status

- **Data fetching (React Query keys)**:
  - `['briefs']` - User's briefs list
  - `['briefs', briefId]` - Specific brief details
  - `['briefs', briefId, 'matches']` - Brief match results
  - `['brief-questions']` - Intake form questions
  - `['agencies', 'matched', briefId]` - Matched agencies for brief

- **Forms & validation (RHF + Zod)**:
  - Brief intake form with 8 questions (company size, industry, tools, outcome, constraints, budget, urgency, preferred stack)
  - Normalization confirmation form
  - Match result display with explainable reasons

- **LLM Integration**:
  - Brief normalization preview with risk flags
  - Embedding generation status
  - Matching progress indicator

- **Accessibility notes**: 
  - ARIA labels for form fields
  - Keyboard navigation support
  - Screen reader friendly match explanations
  - Progress indicators for multi-step process

## 3) OpenAPI & Types
- **Springdoc annotations**:
  - `@Operation` for all endpoints
  - `@ApiResponse` for error codes
  - `@SecurityRequirement` for JWT authentication
  - `@Tag` for endpoint grouping

- **Regenerate FE types**:
  - Generate TypeScript types from OpenAPI spec
  - Create new types for brief, match result, and LLM integration
  - Update existing types for matching functionality

## 4) Tests
- **BE unit/slice**:
  - `BriefServiceTest` - Brief CRUD and normalization
  - `MatchingServiceTest` - Matching algorithm logic
  - `LLMServiceTest` - LLM integration and error handling
  - `EmbeddingServiceTest` - Vector operations and similarity
  - `BriefControllerTest` - Brief endpoint testing
  - `MatchControllerTest` - Matching endpoint testing

- **BE integration (Testcontainers)**:
  - `BriefControllerIntegrationTest` - Full brief creation flow
  - `MatchingControllerIntegrationTest` - Matching algorithm integration
  - `LLMIntegrationTest` - LLM service integration

- **FE unit**:
  - `BriefIntakeForm.test.tsx` - Brief intake form testing
  - `BriefConfirmation.test.tsx` - Normalization confirmation testing
  - `MatchResults.test.tsx` - Match results display testing
  - `BriefList.test.tsx` - Brief management testing

- **E2E happy-path (Playwright)**:
  - Brief intake → Normalization → Confirmation → Matching flow
  - Match results display with explainable reasons
  - Brief management and status tracking

- **Coverage target**: 80%+ for matching service, 70%+ overall

## 5) Seed & Sample Data (dev only)
- **What to seed**:
  - Brief intake questions (8 standard questions)
  - Sample briefs in different statuses
  - Sample match results with explanations
  - Test agencies with use cases for matching

- **How to run**:
  - `mvn liquibase:update` - Apply migrations
  - `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"` - Run with seed data

## 6) Acceptance Criteria Matrix
- **P1.1-C**:
  - [ ] AC#1: Brief intake form with 8 required questions
  - [ ] AC#2: LLM normalization with risk flags
  - [ ] AC#3: Buyer confirmation of normalized summary
  - [ ] AC#4: Embeddings generation for briefs
  - [ ] AC#5: Brief status tracking (DRAFT, NORMALIZED, CONFIRMED)

- **P1.1-D**:
  - [ ] AC#1: Matching algorithm calculates scores 0-100
  - [ ] AC#2: Explainable reasons for each match
  - [ ] AC#3: Top 5-7 agencies returned per brief
  - [ ] AC#4: Performance: p95 matching < 800ms at 1k agencies
  - [ ] AC#5: Matching service tests achieve ≥80% coverage

## 7) Risks & Rollback
- **Risks**:
  - LLM service reliability and rate limits
  - Embedding generation performance
  - Matching algorithm accuracy
  - Frontend form complexity

- **Mitigations**:
  - LLM service error handling and fallbacks
  - Embedding caching and optimization
  - Extensive matching algorithm testing
  - Progressive form enhancement

- **Rollback plan**:
  - Disable LLM integration
  - Revert to basic matching
  - Rollback frontend form changes
  - Restore previous brief handling

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
  │   ├── entity/Brief.java, MatchResult.java, BriefQuestion.java
  │   ├── dto/BriefDto.java, MatchResultDto.java, BriefQuestionDto.java
  │   ├── mapper/BriefMapper.java, MatchResultMapper.java, BriefQuestionMapper.java
  │   ├── controller/BriefController.java, MatchController.java
  │   ├── service/BriefService.java, MatchingService.java, LLMService.java, EmbeddingService.java
  │   ├── integration/LLMIntegration.java, EmbeddingIntegration.java
  │   └── repository/BriefRepository.java, MatchResultRepository.java, BriefQuestionRepository.java
  ├── src/main/resources/
  │   ├── application.yml (LLM configuration)
  │   └── db/changelog/V004__brief_system.yaml, V005__matching_algorithm.yaml
  └── pom.xml (LLM dependencies)
  
  frontend/
  ├── src/
  │   ├── components/brief/BriefIntakeForm.tsx (extend ValidationWizard)
  │   ├── components/brief/BriefConfirmation.tsx (extend existing form components)
  │   ├── components/brief/MatchResults.tsx (extend existing table components)
  │   ├── app/(minimal)/briefs/new/page.tsx (extend minimal layout patterns)
  │   ├── app/(minimal)/briefs/[id]/page.tsx (extend minimal layout patterns)
  │   ├── app/(minimal)/briefs/[id]/confirm/page.tsx (extend minimal layout patterns)
  │   ├── app/(minimal)/briefs/[id]/matches/page.tsx (extend minimal layout patterns)
  │   └── app/(dashboard)/owner/briefs/page.tsx (extend dashboard layout patterns)
  └── package.json (updated with LLM integration dependencies)
  ```
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: New brief and matching endpoints
- **Proposed conventional commits (per ticket)**:
  - `feat(brief): implement brief intake and normalization system [P1.1-C]`
  - `feat(matching): add basic matching algorithm with explainable results [P1.1-D]`