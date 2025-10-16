Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase1.yaml.  
Implement tickets **P1.1-E (Implement @AICapable Annotation Framework)** and **P1.1-F (Build RAG System with Vector Database Abstraction)**.  

Plan first: list files, DTOs, endpoints, Maven configuration, and acceptance criteria.  
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template.  
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Batch 3 (Sequence 3) (Tickets: P1.1-E, P1.1-F)

## 0) Summary
- Goal:
- Tickets covered:
- Non-goals / out of scope:

## 1) Backend changes
- Annotations:
- Services:
- Dependencies:
- Configuration:
- DTOs & mappers:
- Error handling:
- Vector database abstraction:

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
- P1.1-E:
  - [ ] AC#1 …
  - [ ] AC#2 …
- P1.1-F:
  - [ ] AC#1 …
  - [ ] AC#2 …

## 7) Risks & Rollback
- Risks:
- Mitigations:
- Rollback plan:

## 8) Commands to run (print, don't execute yet)
```bash
# Maven module setup
mvn clean install
mvn spring-boot:run

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
