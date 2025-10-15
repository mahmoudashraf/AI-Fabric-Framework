Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase1.yaml.
Implement ticket **P1.2-F (Project tracking — milestones & photo updates)**.

Plan first: list files, DTOs, endpoints, Liquibase changesets, FE routes, and acceptance criteria.
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template. 
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Batch 4 (Ticket: P1.2-F)

## 0) Summary
- Goal:
- Tickets covered:
- Non-goals / out of scope:

## 1) Backend changes
- Entities (new/updated):
- Liquibase changesets:
- Endpoints (method → path → purpose):
- DTOs & mappers:
- Security/RBAC:
- Validation rules:
- Error model (problem+json codes):

## 2) Frontend changes (Next.js App Router)
- Routes/pages:
- Components:
- Data fetching (React Query keys):
- Forms & validation (RHF + Zod):
- Uploads/media:
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
- P1.2-F:
  - [ ] Can create milestone with title & optional dueDate
  - [ ] Can update milestone status along the flow: CREATED → OPEN → SCHEDULED → IN_PROGRESS → COMPLETED (invalid transitions blocked)
  - [ ] Can upload milestone photos (pre-signed upload) and see them on timeline
  - [ ] Project timeline endpoint returns milestones in chronological order with attached media
  - [ ] RBAC: only project owner, assigned PM, or bidding/accepted agency staff can post updates; others forbidden
  - [ ] Audit log record for each milestone create/update and photo attach

## 7) Risks & Rollback
- Risks:
- Mitigations:
- Rollback plan:

## 8) Commands to run (print, don’t execute yet)
```bash
docker compose up -d
make be_test
make fe_build

## 9) Deliverables
- File tree (added/changed)
- Generated diffs
- OpenAPI delta summary
- Proposed conventional commits (per ticket)

