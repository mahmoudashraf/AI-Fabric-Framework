# Platform Execution Sequence Wave 2 Plan

Status: execution-sequencing document (2026-03-31)

This document sequences the next wave after the deployment control-plane foundation.

Wave 2 focuses on faster operator iteration and proof-of-concept velocity without eroding security.

---

## 1) Sequencing Principles

- keep changes deployment-scoped and operator-safe
- persist operator context so repeated workflows stay stable
- add prompt iteration surface before any new large runtime capability
- treat POC data flows as production-grade operations (tracked, auditable, reversible)

---

## 2) Wave 2 Execution Sequence

1. Deployment workspace list personalization: persistent filters for activity, approvals, and revisions
2. Prompt management comparison view: diff current draft vs last published prompt bundle
3. Prompt management release preview: human-readable rendered prompt preview alongside config
4. POC migration wizard: guided import planning (source, entity selection, sizing, risk)
5. Assistant experience staging: operator-friendly “assistant build” view tying prompts, actions, knowledge, and POC readiness

---

## 3) First Item To Build Now

**Deployment workspace list personalization**

Goal:

- keep operator list state stable inside the deployment workspace (activity, approvals, revisions)

Scope:

- add list filters to the deployment activity view
- persist filters per operator using the platform preferences service
- rehydrate saved filters on reload

Not in scope:

- advanced saved views with names
- cross-operator sharing of views
- reporting or export surfaces

---

## 4) Completion Criteria

This item is complete when:

- deployment activity filters exist (category, actor role, text search)
- filters persist across sessions for the same operator
- preferences are stored securely server-side
- backend tests and frontend build pass

---

## 5) Execution Progress

Completed on this branch:

- deployment workspace list personalization for activity filters and persistence
- prompt management comparison view: diff current draft vs last published prompt bundle

Next in sequence:

3. Prompt management release preview: human-readable rendered prompt preview alongside config
