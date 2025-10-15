Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase3.yaml.
Implement tickets **P3.2-A (Analytics owner/agency/admin)**, **P3.2-B (Notifications — email + in-app)**, and **P3.2-C (RBAC hardening & audits)**.

Plan first: list files, DTOs, endpoints, Liquibase changesets, FE routes, and acceptance criteria.
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template. 
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Phase 3 / Batch 3.2 (Tickets: P3.2-A, P3.2-B, P3.2-C)

## 0) Summary
- Goal:
- Tickets covered:
- Non-goals / out of scope:

## 1) Backend changes
- Entities (new/updated):
- Liquibase changesets:
- Endpoints (method → path → purpose):
  - GET /api/analytics/owner?from&to → owner KPIs
  - GET /api/analytics/agency?from&to → agency KPIs
  - GET /api/analytics/admin?from&to → platform KPIs
  - GET /api/notifications → list user notifications (paged)
  - POST /api/notifications/{id}/read → mark read
  - GET /api/notification-preferences → get prefs
  - POST /api/notification-preferences → update prefs
  - GET /api/admin/audits → admin audit log export
- DTOs & mappers:
- Security/RBAC:
  - Analytics scoped by role/ownership
  - Notifications scoped to current user
  - /api/admin/* restricted to ADMIN
- Validation rules:
- Error model (problem+json codes):

## 2) Frontend changes (Next.js App Router)
- Routes/pages:
  - /owner/analytics, /agency/analytics, /admin/analytics
  - /notifications, /settings/notifications
- Components:
  - KpiCard, TimeRangePicker, LineChart, BarChart, NotificationsBell, NotificationList, PrefToggles
- Data fetching (React Query keys):
  - "analytics:owner", "analytics:agency", "analytics:admin"
  - "notifications:list", "notifications:prefs"
- Forms & validation (RHF + Zod):
  - Notification preferences schema (email/in-app toggles)
- Accessibility notes:

## 3) OpenAPI & Types
- Springdoc annotations:
- Regenerate FE types:

## 4) Tests
- BE unit/slice:
  - Analytics aggregation services
  - Notification service (emit/store/read)
  - RBAC policy tests for analytics endpoints
- BE integration (Testcontainers):
  - Seed bookings/projects; verify KPIs over range
  - Notification read/unread transitions; preferences filtering
  - Admin audit export returns CSV/JSON
- FE unit:
  - Pref toggles validation; KpiCard rendering
- E2E happy-path (Playwright):
  - Owner views KPIs; updates notification prefs; receives event → sees in bell menu
- Coverage target:

## 5) Seed & Sample Data (dev only)
- What to seed:
  - Bookings across date ranges, projects/milestones, a few notifications
- How to run:

## 6) Acceptance Criteria Matrix
- P3.2-A — Analytics:
  - [ ] Owner KPIs: occupancy_pct, nights_booked, avg_nightly_price, lead_time
  - [ ] Agency KPIs: offers_per_project, win_rate, completion_rate
  - [ ] Admin KPIs: top providers, listings count, platform occupancy
  - [ ] Date range filters; responses ≤ p95 400ms on dev dataset
- P3.2-B — Notifications:
  - [ ] In-app bell shows unread count; list is paginated and filterable
  - [ ] User can mark notifications read; preferences persist (email/in-app per type)
  - [ ] Events produced for bid_received, bid_accepted, milestone_due, booking_confirmed, cleaning_scheduled
  - [ ] Email sending behind feature flag; local dev stub OK
- P3.2-C — RBAC hardening & audits:
  - [ ] Fine-grained checks on sensitive endpoints; unauthorized returns 403 problem+json
  - [ ] Admin audit endpoint returns CSV/JSON; includes actor, entity, action, timestamp
  - [ ] Critical admin actions (approve agency, change roles) audited

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