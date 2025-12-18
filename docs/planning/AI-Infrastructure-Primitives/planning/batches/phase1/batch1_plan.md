# PLAN — Batch 1 (Sequence 18) (Tickets: P1.1-A (RAG System Foundation), P1.1-B (AI Core Service))

## 0) Summary
- **Goal**: Implement RAG system foundation and AI core service
- **Architecture note**: AI Infrastructure Primitives implementation
- **Tickets covered**: P1.1-A (RAG System Foundation), P1.1-B (AI Core Service)
- **Non-goals / out of scope**: Library extraction, advanced features

## 1) Backend changes
- **Services**: AI primitive services
- **DTOs & mappers**: AI primitive DTOs and MapStruct mappers
- **Error handling**: AI primitive error handling

## 2) Frontend changes (Next.js App Router)
- **Routes/pages**: None in this batch
- **Components**: None in this batch
- **Data fetching (React Query keys)**: None in this batch
- **Forms & validation (RHF + Zod)**: None in this batch
- **AI hooks**: None in this batch
- **Accessibility notes**: N/A

## 3) OpenAPI & Types
- **Springdoc annotations**: AI primitive endpoint documentation
- **Regenerate FE types**: N/A for this batch

## 4) Tests
- **BE unit/slice**: AI primitive service tests
- **BE integration (Testcontainers)**: AI primitive integration tests
- **FE unit**: N/A
- **E2E happy-path (Playwright)**: N/A
- **Coverage target**: 85%+ for AI primitive services

## 5) Seed & Sample Data (dev only)
- **What to seed**: AI primitive test data
- **How to run**: Standard Maven commands

## 6) Acceptance Criteria Matrix
- **P1.1-A (RAG System Foundation)**:
  - [ ] AC#1: Feature works correctly
  - [ ] AC#2: Integration is successful
- **P1.1-B (AI Core Service)**:
  - [ ] AC#1: Feature works correctly
  - [ ] AC#2: Integration is successful

## 7) Risks & Rollback
- **Risks**: AI primitive complexity, performance impact
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
- **File tree (added/changed)**: AI primitive service files
- **Generated diffs**: Will be provided after implementation
- **OpenAPI delta summary**: AI primitive endpoint updates
- **Proposed conventional commits (per ticket)**:
  - `feat(ai-primitives): implement P1.1-A (RAG System Foundation) [P1.1-A (RAG System Foundation)]`
  - `feat(ai-primitives): implement P1.1-B (AI Core Service) [P1.1-B (AI Core Service)]`
