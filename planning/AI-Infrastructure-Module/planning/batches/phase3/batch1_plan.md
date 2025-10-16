# PLAN — Batch 1 (Sequence 10) (Tickets: P3.1-A (Implement Behavioral AI System), P3.1-B (Add Smart Data Validation with AI))

## 0) Summary
- **Goal**: Implement behavioral AI system and smart data validation with AI
- **Architecture note**: Advanced AI features implementation
- **Tickets covered**: P3.1-A (Implement Behavioral AI System), P3.1-B (Add Smart Data Validation with AI)
- **Non-goals / out of scope**: Library extraction, frontend integration

## 1) Backend changes
- **Services**: AI services for advanced features
- **DTOs & mappers**: AI-specific DTOs and MapStruct mappers
- **Error handling**: Advanced AI error handling

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**: None in this batch
- **Components**: None in this batch
- **Data fetching (React Query keys)**: None in this batch
- **Forms & validation (RHF + Zod)**: None in this batch
- **AI hooks**: None in this batch
- **Accessibility notes**: N/A

## 3) OpenAPI & Types
- **Springdoc annotations**: AI endpoint documentation
- **Regenerate FE types**: N/A for this batch

## 4) Tests
- **BE unit/slice**: AI service tests
- **BE integration (Testcontainers)**: AI integration tests
- **FE unit**: N/A
- **E2E happy-path (Playwright)**: N/A
- **Coverage target**: 85%+ for AI services

## 5) Seed & Sample Data (dev only)
- **What to seed**: AI test data
- **How to run**: Standard Maven commands

## 6) Acceptance Criteria Matrix
- **P3.1-A (Implement Behavioral AI System)**:
  - [ ] AC#1: Feature works correctly
  - [ ] AC#2: Integration is successful
- **P3.1-B (Add Smart Data Validation with AI)**:
  - [ ] AC#1: Feature works correctly
  - [ ] AC#2: Integration is successful

## 7) Risks & Rollback
- **Risks**: AI complexity, performance impact
- **Mitigations**: Testing, monitoring, optimization
- **Rollback plan**: Disable features, revert changes

## 8) Commands to run (print, don't execute yet)
```bash
# Backend setup
cd backend
mvn clean install
mvn spring-boot:run

# Frontend setup
cd frontend
npm install
npm run dev

# Testing
mvn test
npm run test
npm run test:e2e
```

## 9) Deliverables
- **File tree (added/changed)**: AI service files
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: AI endpoint updates
- **Proposed conventional commits (per ticket)**:
  - `feat(ai): implement P3.1-A (Implement Behavioral AI System) [P3.1-A (Implement Behavioral AI System)]`
  - `feat(ai): implement P3.1-B (Add Smart Data Validation with AI) [P3.1-B (Add Smart Data Validation with AI)]`
