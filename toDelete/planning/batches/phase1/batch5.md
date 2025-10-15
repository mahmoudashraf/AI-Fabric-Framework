Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase1.yaml.
Implement tickets **P1.2-G (Basic rental listing — public)** and **P1.2-H (Admin panel — users/providers/projects)**.

Plan first: list files, DTOs, endpoints, Liquibase changesets, FE routes, and acceptance criteria.
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template. 
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Batch 5 (Tickets: P1.2-G, P1.2-H)

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
- P1.2-G (Basic rental listing — public):
  - [ ] Owner can create a listing from an existing property (title, summary, nightlyPrice?, currency default GBP)
  - [ ] Visibility toggle (PRIVATE|PUBLIC); only PUBLIC listings are accessible on the public route
  - [ ] Public route `GET /api/listings/{id}` returns SSR-friendly payload (property photos, summary, price if set)
  - [ ] FE page `/listings/[id]` renders with SSR; shows “Not public” state if PRIVATE for non-owners
  - [ ] RBAC: only owner (or approved manager) can create/update their listing
  - [ ] Error handling for missing/hidden listings (404 vs 403 rules)

- P1.2-H (Admin panel):
  - [ ] Admin endpoints exist: `GET /api/admin/users`, `GET /api/admin/projects`, `GET /api/admin/agencies`
  - [ ] `/admin/agencies` lists PENDING agencies with approve/reject actions (wired to Batch 1 flows)
  - [ ] Admin UI `/admin` shows overview stats (stub metrics OK), filter/sort on tables
  - [ ] RBAC: `/api/admin/*` protected by ADMIN role only; non-admin receives 403 with problem+json
  - [ ] Audit: decision actions on agencies recorded in audit_logs

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

