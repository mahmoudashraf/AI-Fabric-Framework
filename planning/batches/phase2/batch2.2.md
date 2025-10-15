Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase2.yaml.
Implement ticket **P2.3-A (Marketplace & public profiles + SEO)**.

Plan first: list files, DTOs, endpoints, Liquibase changesets, FE routes, and acceptance criteria.
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template. 
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Phase 2 / Batch 2.2 (Ticket: P2.3-A)

## 0) Summary
- Goal:
- Tickets covered:
- Non-goals / out of scope:

## 1) Backend changes
- Entities (new/updated): 
- Liquibase changesets:
- Endpoints (method → path → purpose):
  - GET /api/marketplace/providers?category&city&rating → provider search
  - GET /api/public/providers/{id} → public provider profile
  - GET /api/public/listings/{id} → public listing detail (SSR friendly)
- DTOs & mappers:
- Security/RBAC:
  - Public endpoints readable by anonymous users; private fields excluded
- Validation rules:
- Error model (problem+json codes):

## 2) Frontend changes (Next.js App Router)
- Routes/pages:
  - /providers → marketplace search with filters, pagination
  - /providers/[id] → public provider profile (packages, rating, gallery)
  - /listings/[id] → public listing detail (reuse from Phase 1 with SEO)
- Components:
  - ProviderCard, ProviderFilters, RatingBadge, PackageList
- Data fetching (React Query keys):
  - "marketplace:providers", "public:provider", "public:listing"
- Forms & validation (RHF + Zod):
  - Filter form: category, city, minRating
- Uploads/media:
  - Use existing image components; ensure lazy loading
- Accessibility notes:
  - Semantic headings, focus order, label filters; keyboard navigable cards

## 3) OpenAPI & Types
- Springdoc annotations:
- Regenerate FE types:

## 4) Tests
- BE unit/slice:
- BE integration (Testcontainers):
  - Search filters: category, city, rating; pagination; public detail payloads
- FE unit:
  - Filter form schema; ProviderCard rendering
- E2E happy-path (Playwright):
  - Visit /providers → filter → open provider → open listing
- Coverage target:

## 5) Seed & Sample Data (dev only)
- What to seed:
  - A few APPROVED agencies with packages, ratings, cities
  - A couple PUBLIC listings
- How to run: `make seed`

## 6) Acceptance Criteria Matrix
- P2.3-A (Marketplace & public profiles + SEO):
  - [ ] `/api/marketplace/providers` supports filters (category, city, min rating) + pagination
  - [ ] `/api/public/providers/{id}` returns SSR-friendly profile (safe, public fields only)
  - [ ] `/api/public/listings/{id}` returns SSR-friendly listing (PUBLIC only; PRIVATE hidden for anon)
  - [ ] `/providers` page renders server-side, includes meta tags and canonical URL
  - [ ] `/providers/[id]` and `/listings/[id]` include SEO title/description based on entity
  - [ ] Anonymous users can browse public pages; private fields are excluded
  - [ ] Error handling returns 404 for non-existent or non-public entities

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