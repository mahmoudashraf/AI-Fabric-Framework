Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase1.yaml.
Implement tickets **P1.3-A (Tenant browse listings — read-only)** and **P1.3-B (Manual booking records — date blocking)**.

Plan first: list files, DTOs, endpoints, Liquibase changesets, FE routes, and acceptance criteria.
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template. 
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Batch 6 (Tickets: P1.3-A, P1.3-B)

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
- P1.3-A (Tenant browse listings — read-only):
  - [ ] `GET /api/listings/search?location&priceMin&priceMax&style` returns PUBLIC listings only, paginated
  - [ ] Filters: location (city/text), price range (minor units), style key (e.g., FURNITURE/FINISHING)
  - [ ] Sorting: newest and price ascending (at least one)
  - [ ] SSR page `/search` shows filters, results grid/list, empty state
  - [ ] Listing detail `/listings/[id]` renders SSR and hides PRIVATE listings for non-owners (404/“not public” state)
  - [ ] Basic SEO meta for listing detail (title/description)

- P1.3-B (Manual booking records — date blocking):
  - [ ] `POST /api/bookings` creates a manual booking (owner/approved manager only)
  - [ ] `GET /api/listings/{id}/bookings` returns blocks for that listing
  - [ ] Date-overlap guard prevents conflicting bookings (inclusive of start/end rules)
  - [ ] Owner UI `/owner/bookings` to view/create blocks; listing detail shows availability (disabled dates)
  - [ ] RBAC: only listing owner/manager can create; others forbidden
  - [ ] Problem+json errors for overlap and forbidden actions

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

