# Platform Execution Sequence Wave 3 Plan

Status: execution-sequencing document (2026-03-31)

This document defines the next wave for the current branch as a platform-first continuation.

Wave 3 is intentionally platform-scoped.

It keeps this branch focused on:

- deployment administration
- release safety
- configuration governance
- verification and diagnostics
- production operations

It does not move this branch into runtime orchestration internals yet.

That work remains valid in the broader roadmap, but should happen in a separate stream after the platform control plane is stronger for production use.

---

## 1) Sequencing Principles

Wave 3 should follow these rules:

- keep this branch platform-first and deployment-operator focused
- reduce ambiguity around draft, apply, publish, and live production state
- make configuration changes understandable before operators apply them
- separate secret management from normal configuration editing more clearly
- make failed applies and unhealthy deployments diagnosable from inside the platform
- add provider-specific operational value without turning the UI into a raw infrastructure console

---

## 2) Recommended Wave 3 Execution Sequence

### Track A: Release clarity and governed apply

31. apply and draft state clarity: make unsaved changes, saved draft changes, and unapplied release changes explicit across the deployment workspace
32. release impact preview: show which services, artifacts, environment values, secret references, and deployment links will change before apply
33. deployment configuration diff center: compare current draft, last applied release, and currently selected template/source inputs in one operator view
34. release verification gate and post-apply summary: surface required checks, failed checks, and service-by-service health after apply

### Track B: Configuration governance and secure editing

35. unified per-service configuration model: make runtime, REST connector, UI, store, and provider config visible with required-field validation by service
36. secret and config separation hardening: masked secret references, missing-secret detection, secret-usage summaries, and role-safe editing boundaries
37. auth, upstream, and CORS governance: validate connector/runtime auth headers, upstream URLs, public origins, and admin API exposure with consistent operator guidance
38. source-of-truth visibility: show template, branch, artifact source, deployment links, and generated config provenance so operators can see what produced the live deployment

### Track C: Diagnostics, remediation, and production readiness

39. deployment diagnostics workspace: release timeline, failed step visibility, extracted failure reason, log links, and known recovery hints in one place
40. provider and service navigation: expose Railway project links, service links, public endpoints, Swagger/OpenAPI links, and internal relationship mapping in the deployment workspace
41. governed remediation actions: restart, redeploy, reverify, cleanup/reset, and destructive recovery actions with role checks, confirmations, and audit
42. production readiness scorecard: one deployment-level go-live summary covering config completeness, verification state, security posture, service health, and operator ownership

---

## 3) Wave 3 Scope Notes

Wave 3 should explicitly include:

- release/apply clarity
- draft versus live change visibility
- service-by-service config governance
- secret hygiene and required-value checks
- diagnostics and remediation UX
- production readiness and operator handoff

Wave 3 should explicitly not attempt to finish:

- runtime planner changes for read-only action grounding
- deep knowledge navigation orchestration
- confirmation interceptor execution logic
- multi-cloud provisioning expansion
- customer-facing runtime product surfaces beyond operator administration

Those remain valid roadmap items, but they should not dilute this branch's platform-first purpose.

---

## 4) Why This Wave Matters

Wave 1 made the platform deployment-centric.

Wave 2 made it useful for iteration and proof-of-concept work.

Wave 3 should make it production-operable.

Without this wave:

- operators still have to guess what apply will change
- configuration mistakes still escape too easily into live environments
- verification and failure diagnosis still require too much manual joining across logs, services, and provider consoles
- the platform still feels stronger for demos than for real production operations

With this wave complete:

- operators can understand and review production-impacting changes before apply
- service configuration becomes safer and more explainable
- release failures become diagnosable from the deployment workspace
- the product becomes materially closer to an enterprise AI deployment control plane

---

## 5) Completion Criteria

Wave 3 is complete when:

- operators can clearly distinguish editor changes, saved draft changes, and live applied state
- apply flows show meaningful impact previews and config diffs before rollout
- the platform validates service config requirements and secret completeness per deployment
- deployment workspaces expose diagnostics, provider links, and governed remediation actions
- production readiness is visible from one deployment-scoped summary
- backend tests and frontend build pass for every completed item

---

## 6) Execution Progress

Completed on this branch:

- 31. apply and draft state clarity: unsaved browser edits, saved draft posture, and live applied posture are now explicit across the deployment workspace
- 32. release impact preview: the revisions workspace now compares the selected version against the live release plan, showing service, artifact, env reference, secret reference, and deployment-link impact before apply
- 33. deployment configuration diff center: the revisions workspace now compares draft, latest published, live, and template/source inputs in one operator diff view backed by a dedicated API summary
- 34. release verification gate and post-apply summary: the verification workspace now joins pre-apply readiness, rollout state, and grouped post-apply service verification in one operator screen
- 35. unified per-service configuration model: the overview workspace now exposes runtime, REST, UI, upstream/store, and provider config as one service map with required-field tracking
- 36. secret and config separation hardening: the security workspace now exposes deployment secret usage, missing required secret detection, literal credential risk alerts, and explicit role-safe editing boundaries
- 37. auth, upstream, and CORS governance: the security workspace now evaluates runtime admin exposure, connector ingress, upstream authz posture, and browser CORS with deployment-scoped operator guidance
- 38. source-of-truth visibility: the overview workspace now shows template lineage, source branch provenance, immutable artifact bundles, generated deployment targets, and live-versus-published config hashes

Next in sequence:

- 39. deployment diagnostics workspace: release timeline, failed step visibility, extracted failure reason, log links, and known recovery hints in one place

Sequence note:

- this document is the branch-specific continuation after Wave 2
- the broader runtime-quality plans remain important, but they are not the next implementation target for this platform-first branch
