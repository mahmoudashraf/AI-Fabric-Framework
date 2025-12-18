Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase2.yaml.  
Implement tickets **P2.1-G (Production Deployment + Monitoring)** and **P2.1-H (User Experience + Feedback)**.  

Plan first: list files, DTOs, endpoints, Maven configuration, and acceptance criteria.  
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template.  
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Batch 4 (Sequence 25) (Tickets: P2.1-G (Production Deployment + Monitoring), P2.1-H (User Experience + Feedback))

## 0) Summary
- Goal: Implement production deployment with monitoring and user experience optimization
- Tickets covered: P2.1-G (Production Deployment + Monitoring), P2.1-H (User Experience + Feedback)
- Non-goals / out of scope:

## 1) Backend changes
- Services:
- DTOs & mappers:
- Error handling:

## 2) Frontend changes (Next.js App Router)
- Routes/pages:
- Components:
- Data fetching (React Query keys):
- Forms & validation (RHF + Zod):
- AI hooks:
- Accessibility notes:

## 3) OpenAPI & Types
- Springdoc annotations:
- Regenerate FE types:

## 4) Tests
- BE unit/slice:
- BE integration (Testcontainers):
- FE unit:
- E2E happy-path (Playwright):
- Coverage target:

## 5) Seed & Sample Data (dev only)
- What to seed:
- How to run:

## 6) Acceptance Criteria Matrix
- P2.1-G (Production Deployment + Monitoring):
  - [ ] AC#1 …
  - [ ] AC#2 …
- P2.1-H (User Experience + Feedback):
  - [ ] AC#1 …
  - [ ] AC#2 …

## 7) Risks & Rollback
- Risks:
- Mitigations:
- Rollback plan:

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
- File tree (added/changed)
- Generated diffs
- OpenAPI delta summary
- Proposed conventional commits (per ticket)
