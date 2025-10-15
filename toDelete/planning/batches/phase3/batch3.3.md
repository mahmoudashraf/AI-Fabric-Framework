Use docs/PROJECT_GUIDELINES.yaml as the single source of truth.
Use docs/FRONTEND_DEVELOPMENT_GUIDE.md as the UI Development guide.

Load planning/phase3.yaml.
Implement tickets P3.3-A (AI style recommendations — rules-based) and P3.3-B (Realtime upgrades — WS/SSE for notes/bids).

Plan first: list files, DTOs, endpoints, Liquibase changesets, FE routes, and acceptance criteria.
Wait for my approval before coding.

⚠️ IMPORTANT: Output ONLY the PLAN using the following Markdown Checklist template.
Do NOT generate or edit code until I explicitly reply "OK, proceed".

PLAN — Phase 3 / Batch 3.3 (Tickets: P3.3-A, P3.3-B)
0) Summary

Goal:
Tickets covered:
Non-goals / out of scope:

1) Backend changes
Entities (new/updated):
Liquibase changesets (if needed):
Endpoints (method → path → purpose):

POST /api/styles/recommend { propertyId } → ranked packages with rationale
GET /api/projects/{id}/notes/stream → SSE or WS channel for notes
GET /api/projects/{id}/bids/stream → SSE or WS channel for bids

DTOs & mappers:
Security/RBAC:
Recommend: owner/manager access to the property
Streams: only participants (owner, assigned PM, agency staff on project)

Validation rules:
Error model (problem+json codes):

2) Frontend changes (Next.js App Router)
Routes/pages:
/owner/properties/[id]/style/recommend
Components:
RecommendationList with rationale chips, RealtimeNoteFeed, RealtimeBidFeed
Data fetching (React Query keys) & realtime:
Mutations to trigger recommend; EventSource/WebSocket client for streams
Feature flags:
features.ai_recommendations, features.realtime_streams
Accessibility notes:

3) OpenAPI & Types
Springdoc annotations (recommend only; streams documented as text/event-stream if SSE)
Regenerate FE types

4) Tests
BE unit/slice:
Recommendation scoring: deterministic vectors; tie-breakers; filters by type/budget
BE integration:
AuthZ on recommend; SSE/WS handshake auth; backpressure/heartbeat
FE unit:
RecommendationList renders ordered scores; rationale chips
E2E happy-path (Playwright):
Owner triggers recommend → sees ranked list; project notes/bids update in realtime without refresh
Coverage target:

5) Seed & Sample Data (dev only)
What to seed:
Several style packages with attributes; a property with size/location/budget for test vectors
How to run:

6) Acceptance Criteria Matrix
P3.3-A — AI style recommendations (rules-based):
 POST /api/styles/recommend returns ranked [{stylePackageId, score, rationale[]}]
 Deterministic output for the same inputs; unit tests with fixed vectors
 Honors property attributes (size, location, budget, purpose)
 Feature-flagged; safe fallback to pre-approved list

P3.3-B — Realtime upgrades (WS/SSE):
 Notes and bids update live via SSE or WebSocket channels
 Auth required; only project participants can subscribe
 Retry/backoff on disconnect; offline fallback to polling
 Server sends heartbeats; cleans up idle connections

7) Risks & Rollback
Risks:
Mitigations:
Rollback plan:

8) Commands to run (print, don’t execute yet)
docker compose up -d
make be_test
make fe_build

9) Deliverables
File tree (added/changed)
Generated diffs
OpenAPI delta summary
Proposed conventional commits (per ticket)