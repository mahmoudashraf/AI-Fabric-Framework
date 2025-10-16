Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase1.yaml.  
Implement tickets **P1.1-E (Smart Validation + Intelligent Caching)** and **P1.1-F (Auto-Generated Documentation + Smart Error Handling)**.  

Plan first: list files, DTOs, endpoints, Maven configuration, and acceptance criteria.  
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template.  
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Batch 3 (Sequence 20) (Tickets: P1.1-E (Smart Validation + Intelligent Caching), P1.1-F (Auto-Generated Documentation + Smart Error Handling))

## 0) Summary
- Goal: Implement smart validation with intelligent caching and auto-generated documentation
- Tickets covered: P1.1-E (Smart Validation + Intelligent Caching), P1.1-F (Auto-Generated Documentation + Smart Error Handling)
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
- P1.1-E (Smart Validation + Intelligent Caching):
  - [ ] AC#1 …
  - [ ] AC#2 …
- P1.1-F (Auto-Generated Documentation + Smart Error Handling):
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
