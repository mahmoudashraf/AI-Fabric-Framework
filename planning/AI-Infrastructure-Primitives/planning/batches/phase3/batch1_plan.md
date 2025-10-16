# PLAN — Batch 1 (Sequence 26) (Tickets: P3.1-A (AI Ecosystem Integration), P3.1-B (Community Building + Support))

## 0) Summary
- **Goal**: Implement AI ecosystem integration and community building
- **Architecture note**: AI Infrastructure Primitives implementation
- **Tickets covered**: P3.1-A (AI Ecosystem Integration), P3.1-B (Community Building + Support)
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
- **P3.1-A (AI Ecosystem Integration)**:
  - [ ] AC#1: Feature works correctly
  - [ ] AC#2: Integration is successful
- **P3.1-B (Community Building + Support)**:
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
  - `feat(ai-primitives): implement P3.1-A (AI Ecosystem Integration) [P3.1-A (AI Ecosystem Integration)]`
  - `feat(ai-primitives): implement P3.1-B (Community Building + Support) [P3.1-B (Community Building + Support)]`
