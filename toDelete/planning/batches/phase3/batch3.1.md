Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.


Load planning/phase3.yaml.
Implement tickets **P3.1-A (Stripe payments & webhooks)** and **P3.1-B (Calendar sync — iCal export/import)**.

Plan first: list files, DTOs, endpoints, Liquibase changesets, FE routes, and acceptance criteria.
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template. 
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Phase 3 / Batch 3.1 (Tickets: P3.1-A, P3.1-B)

## 0) Summary
- Goal:
- Tickets covered:
- Non-goals / out of scope:

## 1) Backend changes
- Entities (new/updated): 
- Liquibase changesets:
- Endpoints (method → path → purpose):
  - POST /api/payments/checkout { bookingId } → create/retrieve PaymentIntent
  - POST /webhooks/stripe → handle events (payment_intent.succeeded, .payment_failed, etc.)
  - GET  /api/listings/{id}/ical → export availability as .ics
  - POST /api/listings/{id}/ical-sources → add external iCal URL(s) to import & block dates
- DTOs & mappers:
- Security/RBAC:
  - Payments: only booking owner/tenant with hold can initiate checkout
  - iCal export public/private rules; import restricted to listing owner/manager
- Validation rules:
- Error model (problem+json codes):
  - ERR_BOOKING_NOT_FOUND, ERR_BOOKING_NOT_HOLD, ERR_FORBIDDEN, ERR_STRIPE_SIGNATURE_INVALID, ERR_ICAL_INVALID_URL, ERR_ICAL_PARSE_ERROR

## 2) Frontend changes (Next.js App Router)
- Routes/pages:
  - /checkout/[bookingId] → payment confirmation screen + Stripe Elements (client secret)
  - Settings UI: manage iCal sources per listing (owner-only) — could be a section under /owner/listings/[id]
- Components:
  - CheckoutSummary, StripeCardElementWrapper, IcalSourceForm, IcalSourceList
- Data fetching (React Query keys):
  - "payments:checkout:{bookingId}", "listings:icalSources:{id}"
- Forms & validation (RHF + Zod):
  - IcalSourceForm: url (https), name/label (optional)

## 3) OpenAPI & Types
- Springdoc annotations for all new endpoints
- Regenerate FE types from /v3/api-docs.json

## 4) Tests
- BE unit/slice:
  - PaymentService: create PaymentIntent, idempotency key logic, currency handling
  - IcalService: export (.ics generation), import (parse, convert to blocks)
- BE integration (Testcontainers):
  - Webhook handler with Stripe signature verification stub/mocks
  - Booking state transitions: HOLD → CONFIRMED on succeeded; HOLD remains on failed/canceled
  - iCal import scheduler job creates blocking events; re-import dedupes/updates
- FE unit:
  - IcalSourceForm validation
  - CheckoutSummary renders correct totals/states
- E2E happy-path (Playwright):
  - Create HOLD → checkout → webhook success → booking becomes CONFIRMED; availability reflects block
  - Add iCal URL → import runs → dates blocked on listing
- Coverage target:
  - ≥70% new code; ≥80% for webhook handler & date overlap logic

## 5) Seed & Sample Data (dev only)
- What to seed:
  - A listing with PUBLIC visibility and a HOLD booking
  - Stripe test keys (env), demo price data on booking
- How to run:
  - `make seed`, provide `.env` with STRIPE_* variables

## 6) Acceptance Criteria Matrix
- P3.1-A — Stripe payments & webhooks:
  - [ ] `POST /api/payments/checkout {bookingId}` returns PaymentIntent client_secret (or reuses existing intent); idempotency keys used
  - [ ] `POST /webhooks/stripe` verifies signature; on **payment_intent.succeeded**:
        - Booking transitions to **CONFIRMED**
        - Deal/commission record created/updated (amount, currency, fee/commission fields)
        - Events emitted for notifications/analytics
  - [ ] On failures/cancellations, booking remains HOLD; proper audit/event logged
  - [ ] RBAC: only the tenant who owns the HOLD (or owner/manager on behalf) can initiate checkout
  - [ ] Problem+json on invalid state, missing booking, or signature failures
  - [ ] Idempotent webhook processing (safe on retries)

- P3.1-B — Calendar sync (iCal export/import):
  - [ ] `GET /api/listings/{id}/ical` returns valid `.ics` with VEVENTs for blocked/confirmed stays
  - [ ] `POST /api/listings/{id}/ical-sources` stores external feed(s); scheduler imports events on interval and **blocks dates** (no price/guest data)
  - [ ] Import handles duplicates and updates gracefully; overlapping rules respected
  - [ ] Owner/manager can list/remove sources; non-owners forbidden
  - [ ] Error handling for invalid URLs, network issues, or parse errors (problem+json)
  - [ ] Export includes last-modified and PRODID headers; timezone handling reasonable (UTC default)

## 7) Risks & Rollback
- Risks:
  - Stripe webhook misconfig → missed confirmations
  - iCal feeds unstable → noisy blocks
- Mitigations:
  - Webhook secret + strict signature verify; dead-letter queue / retry on transient failures
  - Import with ETag/Last-Modified; backoff & logging; per-source enable/disable
- Rollback plan:
  - Feature flags for payments & iCal import; disable schedulers; revert Liquibase changesets if needed

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