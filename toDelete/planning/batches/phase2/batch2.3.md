Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase2.yaml.
Implement tickets **P2.2-A (Cleaning packages & jobs)** and **P2.2-B (Estate agent / property manager workflows)**.

Plan first: list files, DTOs, endpoints, Liquibase changesets, FE routes, and acceptance criteria.
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template. 
Do NOT generate or edit code until I explicitly reply "OK, proceed".

# PLAN — Phase 2 / Batch 2.3 (Tickets: P2.2-A, P2.2-B)

## 0) Summary
- Goal:
- Tickets covered:
- Non-goals / out of scope:

## 1) Backend changes
- Entities (new/updated): 
- Liquibase changesets:
- Endpoints (method → path → purpose):
  - POST /api/cleaning/jobs → create job (TURNOVER|ONE_OFF)
  - GET  /api/cleaning/jobs?agencyId&status&from&to → list/filter jobs
  - POST /api/properties/{id}/apply-manager → request manager role
  - POST /api/properties/{id}/approve-manager → owner approves/denies
- DTOs & mappers:
- Security/RBAC:
  - Cleaning jobs: visible/editable to agency staff on that job; owners/managers can view jobs tied to their listings
  - Manager workflows: only property OWNER can approve/deny; applicants restricted
- Validation rules:
  - Job date/time must be in future; listing must exist & be owned by requesting party (for auto-turnover opt-in)
  - Manager application must include org/name/contact; duplicate outstanding application blocked
- Error model (problem+json codes):

## 2) Frontend changes (Next.js App Router)
- Routes/pages:
  - /cleaning/dashboard → job queue (filters, status updates, upload completion media)
  - /manager/dashboard → approvals, managed properties, bookings & cleaning overview
- Components:
  - CleaningJobCard, JobFilters, CompletionUpload, ManagerApplicantRow, ApproveDenyModal
- Data fetching (React Query keys):
  - "cleaning:jobs", "manager:my-approvals", "manager:properties"
- Forms & validation (RHF + Zod):
  - NewJobForm (type, date/time, notes)
  - ManagerApplicationForm (org/name/contact, message)
- Uploads/media:
  - Completion photos/evidence via pre-signed upload (re-use existing uploader)
- Accessibility notes:

## 3) OpenAPI & Types
- Springdoc annotations:
- Regenerate FE types:

## 4) Tests
- BE unit/slice:
- BE integration (Testcontainers):
  - Auto-create TURNOVER job on booking check-out when owner opt-in is enabled
  - Manager apply → approve → scoped permissions applied
- FE unit:
  - JobFilters & CompletionUpload validation; ApproveDenyModal behavior
- E2E happy-path (Playwright):
  - Manager applies → owner approves → manager sees property in dashboard
  - Owner enables auto-turnover → complete a booking → job appears in cleaning dashboard
- Coverage target:

## 5) Seed & Sample Data (dev only)
- What to seed:
  - A cleaning agency with staff; one owner with a listing and opt-in flag
  - A pending manager application
- How to run:

## 6) Acceptance Criteria Matrix
- P2.2-A — Cleaning packages & jobs:
  - [ ] `POST /api/cleaning/jobs` creates job with type (TURNOVER|ONE_OFF), scheduled time, notes
  - [ ] `GET /api/cleaning/jobs?agencyId` lists jobs for that agency; filter by status/date range
  - [ ] When owner enables “auto-turnover,” a job is auto-created at check-out for each CONFIRMED booking
  - [ ] Staff can upload completion photos/files; mark job COMPLETE; timestamp recorded
  - [ ] RBAC: only agency staff of assigned agency can update; owners/managers view-only
  - [ ] Problem+json for invalid dates, forbidden access, or not-found

- P2.2-B — Estate agent / property manager workflows:
  - [ ] `POST /api/properties/{id}/apply-manager` creates a pending application with org/name/contact (one outstanding per applicant)
  - [ ] `POST /api/properties/{id}/approve-manager` (or reject) changes state; only property OWNER can approve/deny
  - [ ] Approved manager gains scoped permissions on property (create manual bookings, view cleaning jobs, post notes)
  - [ ] `/manager/dashboard` shows approvals queue + managed properties overview
  - [ ] Audit entries on approve/deny
  - [ ] Problem+json for duplicate application, forbidden, or not-found

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