Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase2.yaml.
Implement tickets P2.1-A (Tenant booking flow — hold → confirm, no payments), P2.1-B (Reviews — listings & agencies, responses), and P2.3-B (Project notes — lightweight, polling).

Plan first: list files, DTOs, endpoints, Liquibase changesets, FE routes, and acceptance criteria.
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template. 
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Phase 2 / Batch 2.1 (Tickets: P2.1-A, P2.1-B, P2.3-B)

## 0) Summary
- Goal: Enable tenant-driven reservation flow (without payments), capture post-stay reviews, and add lightweight project notes (polling).
- Tickets covered: P2.1-A, P2.1-B, P2.3-B
- Non-goals / out of scope: Payments, realtime notes, OTA sync, moderation for reviews beyond simple response.

## 1) Backend changes
- Entities (new/updated): 
  - Review {id, subjectType (LISTING|AGENCY), subjectId, authorId, rating, text, createdAt, responseText?, responseAt?}
  - ProjectNote {id, projectId, authorUserId, text, createdAt, updatedAt}
  - Booking (extend): add status HOLD|CONFIRMED, holdExpiresAt, guestDetailsJson
- Liquibase changesets:
  - db/changelog/V2xx__reviews.yaml
  - db/changelog/V2xx__project_notes.yaml
  - db/changelog/V2xx__bookings_hold_fields.yaml
- Endpoints (method → path → purpose):
  - POST /api/bookings/hold → availability check; create HOLD with TTL
  - POST /api/bookings/confirm → turn HOLD into CONFIRMED, persist guestDetails
  - POST /api/reviews → create review (enforce eligibility & one-per-completed-stay)
  - GET  /api/reviews?subjectType&subjectId → list + aggregates
  - POST /api/reviews/{id}/response → agency official response (one, editable)
  - CRUD: /api/projects/{id}/notes, /api/projects/{id}/notes/{noteId}
- DTOs & mappers:
  - BookingHoldRequest {listingId, startDate, endDate}
  - BookingConfirmRequest {holdId, guestDetails}
  - ReviewCreateRequest {subjectType, subjectId, rating, text}
  - ReviewResponseRequest {text}
  - ProjectNoteCreateRequest {text}; ProjectNoteUpdateRequest {text}
- Security/RBAC:
  - Holds: TENANT (or OWNER/MANAGER on behalf); Confirm: same actor who owns hold or owner/manager
  - Reviews: listing reviews only by tenants with completed booking; agency response by AGENCY on that subject
  - Notes: project participants (OWNER, assigned PM, AGENCY staff on project)
- Validation rules:
  - Dates valid and start < end; TTL default 15m; rating 1..5; text length ≤ 2000
- Error model (problem+json codes):
  - ERR_HOLD_EXPIRED, ERR_OVERLAP, ERR_REVIEW_NOT_ELIGIBLE, ERR_DUPLICATE_REVIEW, ERR_FORBIDDEN, ERR_NOTE_NOT_FOUND

## 2) Frontend changes (Next.js App Router)
- Routes/pages:
  - /listing/[id]/book → date picker → guest details → confirm (no payment)
  - Reviews module on listing & agency public pages
  - /projects/[id]/notes → list + create/edit/delete with polling
- Components:
  - DateRangePicker, BookingGuestForm, ReviewsList, ReviewForm, ReviewResponseBox, NotesList, NoteEditor
- Data fetching (React Query keys):
  - "booking:hold", "booking:confirm", "reviews:list:{type}:{id}", "projects:notes:{id}"
- Forms & validation (RHF + Zod):
  - BookingGuestForm schema (name/email/phone adults/kids)
  - Review schema (rating 1..5, text)
  - Note schema (text required)
- Uploads/media: N/A
- Accessibility notes: Keyboardable date picker, proper form labels, aria-live for notes list updates.

## 3) OpenAPI & Types
- Springdoc annotations for all new endpoints.
- Regenerate FE types from /v3/api-docs.json and update API client.

## 4) Tests
- BE unit/slice:
  - AvailabilityService overlap guard; HoldService TTL expiry
  - ReviewService eligibility + one-per-stay
  - NotesService CRUD & permissions
- BE integration (Testcontainers):
  - HOLD → auto-expire → cannot confirm (409)
  - HOLD → confirm → booking visible to owner
  - Review lifecycle & aggregates
- FE unit:
  - Zod schemas; ReviewsList rendering; NoteEditor behaviors
- E2E happy-path (Playwright):
  - Tenant books HOLD → confirm; leaves review → appears on listing; notes added & visible via polling
- Coverage target: ≥70% new code

## 5) Seed & Sample Data (dev only)
- Seed a PUBLIC listing with availability and a completed booking; an agency for response tests.
- Run via `make seed`.

## 6) Acceptance Criteria Matrix
- P2.1-A — Tenant booking flow (hold → confirm; no payments):
  - [ ] Endpoint `POST /api/bookings/hold { listingId, startDate, endDate }` checks availability and creates HOLD with TTL (15 min default)
  - [ ] Holds expire automatically; expired holds cannot be confirmed (409 w/ problem+json)
  - [ ] Double-booking guard prevents overlapping holds/confirmed bookings
  - [ ] Endpoint `POST /api/bookings/confirm { holdId, guestDetails }` transitions to CONFIRMED
  - [ ] Owner sees upcoming bookings; tenant sees confirmation state
  - [ ] RBAC: tenant can create holds; owner/manager can also create on behalf; unauthorized blocked
  - [ ] Audit events on hold create/expire/confirm

- P2.1-B — Reviews (listings & agencies; responses):
  - [ ] Entity `Review {subjectType, subjectId, authorId, rating, text}` persisted
  - [ ] Endpoint `POST /api/reviews` enforces one review per completed booking (listings) and appropriate author checks (agencies via completed project/booking if applicable)
  - [ ] Endpoint `GET /api/reviews?subjectType&subjectId` returns list + aggregates (avg rating, count)
  - [ ] Agencies can post an official response (single, editable by agency)
  - [ ] Reviews surface on public listing/agency pages with star average
  - [ ] Validation: rating 1..5; text length guard; RBAC on author/respondent
  - [ ] Problem+json for invalid subject, duplicate review, or forbidden

- P2.3-B — Project notes (lightweight; polling):
  - [ ] Entity `ProjectNote { projectId, authorUserId, text, createdAt }`
  - [ ] Endpoints CRUD: `POST /api/projects/{id}/notes`, `GET /api/projects/{id}/notes` (ordered desc), `PATCH /api/projects/{id}/notes/{noteId}` (author/admin), `DELETE ...` (author/admin)
  - [ ] FE page `/projects/[id]/notes` polls every 10–15s; optimistic create/edit/delete
  - [ ] RBAC: project owner, assigned PM, agency staff on that project can read/write; others 403
  - [ ] Problem+json for forbidden, note not found, or validation errors

## 7) Risks & Rollback
- Risks: race on overlapping holds; review spam; polling load
- Mitigations: transactional overlap check; per-user rate limit; ETags for notes list
- Rollback plan: disable holds/notes via feature flags; revert changesets

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

