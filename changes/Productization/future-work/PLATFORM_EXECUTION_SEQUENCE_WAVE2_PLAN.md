# Platform Execution Sequence Wave 2 Plan

Status: execution-sequencing document (2026-03-31)

This document expands Wave 2 into the same execution style used for Wave 1.

Wave 2 is the operator-iteration wave.

Its job is to reduce the time between:

- editing a deployment
- loading safe proof-of-concept data
- testing the assistant directly in the platform
- deciding whether the deployment is ready for a customer-facing validation loop

This wave should improve velocity without weakening deployment guardrails, audit, or operator-role boundaries.

---

## 1) Sequencing Principles

Wave 2 should follow these rules:

- keep everything deployment-scoped so operators stay anchored to one assistant context
- treat prompt iteration as a controlled layer on top of versioned releases, not a release replacement
- make POC data flows guided and bounded instead of leaving operators with raw JSON-only workflows
- keep proof-of-concept tooling auditable and reversible
- surface readiness signals in one place so operators do not need to mentally join prompts, knowledge, actions, and runtime state

---

## 2) Recommended Wave 2 Execution Sequence

### Track A: Operator context and prompt iteration

22. deployment workspace list personalization for activity, approvals, and revisions
23. prompt baseline comparison: saved draft vs last published prompt bundle
24. prompt release preview: rendered view of the saved draft prompt bundle before publish
25. prompt state clarity: make saved draft, published baseline, and session hot-apply posture visually unambiguous for operators

### Track B: Guided POC migration flow

26. POC migration intake wizard: source choice, entity selection, sizing, risk, and import readiness
27. POC import guardrails and run visibility: make batch limits, vector-space targeting, warnings, and recent run evidence explicit
28. POC validation loop strengthening: keep scenario reuse, trace evidence, and reset loops directly connected to imported test data

### Track C: Assistant staging and readiness

29. assistant experience staging view: tie prompts, actions, knowledge, runtime/indexing, and endpoints into one operator summary
30. assistant readiness guidance: give role-safe next-step recommendations and go/no-go warnings before customer demos or external UI integration

---

## 3) Wave 2 Scope Notes

Wave 2 should explicitly include:

- prompt comparison and preview
- session-aware operator testing
- guided proof-of-concept migration setup
- readiness signals for operator-led validation

Wave 2 should explicitly not attempt to finish:

- environment-wide prompt hot apply beyond the current session-scoped operator path
- synthetic data generation at scale
- deep external-source migration connectors
- customer-facing demo mode or white-label presentation surfaces

Those remain later expansion work after this wave.

---

## 4) Why This Wave Matters

Wave 1 made the platform deployment-centric.

Wave 2 should make it usable as a fast assistant-building loop.

Without this wave:

- prompt editing remains disconnected from release confidence
- POC migration still feels too manual
- operators still have to infer assistant readiness from multiple pages

With this wave complete:

- operators can see what prompt changes will do before publish
- proof-of-concept data loading becomes guided instead of ad hoc
- assistant readiness becomes visible from one deployment-first workspace view

---

## 5) Completion Criteria

Wave 2 is complete when:

- operator workspace filters persist for activity, approvals, and revisions
- prompt workspace clearly shows saved draft vs published baseline vs release preview
- POC workspace supports a guided migration/import flow with bounded operator-safe inputs
- assistant staging surfaces prompts, knowledge, actions, indexing, and endpoint readiness together
- backend tests and frontend build pass for every completed item

---

## 6) Execution Progress

Completed on this branch:

22. deployment workspace list personalization for activity, approvals, and revisions
23. prompt baseline comparison: saved draft vs last published prompt bundle
24. prompt release preview: rendered view of the saved draft prompt bundle before publish
25. prompt state clarity: saved draft, published baseline, editor buffer, and session hot-apply posture are explicit in the prompt workspace
26. POC migration intake wizard: source choice, entity selection, sizing, risk, and import readiness
27. POC import guardrails and run visibility: batch limits, vector-space targeting, warnings, and recent run evidence are explicit in the POC workspace
28. POC validation loop strengthening: migration, scenario reuse, reset controls, and trace evidence now live in one deployment-scoped workspace
29. assistant experience staging view: prompts, actions, knowledge, runtime/indexing, and endpoints are visible in one operator summary
30. assistant readiness guidance: deployment overview now provides go/no-go signals and role-safe next-step guidance for customer validation

Wave 2 foundations already completed earlier on this branch and in Wave 1:

- deployment-scoped prompt workspace foundation
- session-scoped prompt hot apply for the operator POC console
- deployment POC workspace foundation with embedded chat
- packaged dataset visibility, reset controls, and import run history
- scenario library and orchestration trace visibility

Still remaining in Wave 2:

- none

Next in sequence:

- Wave 2 execution is complete. Continue from the next prioritized cross-wave item in the broader roadmap.

Sequence note:

- the previous Wave 2 document understated scope and overstated item 22 as complete while approvals and revisions were still missing
- this document corrects that and aligns the wave with the broader prompt-management and POC-migration plans
