# Platform UI Release Verification Architecture

Use this document to understand the platform-owned replacement path for GitHub Actions verification.

This is the architecture guide for:

- moving release verification into the platform control plane
- running verification from the admin UI
- keeping verification secure, auditable, and release-blocking
- understanding what is implemented now versus what still needs parity work

This file is safe to commit.
Do not put raw secrets here.

## 1. Goal

The target model is:

- operators trigger release verification from the platform UI
- the platform backend owns the verification workflow
- secrets stay in platform-managed storage and never move into the browser
- verification progress and output are visible in the UI
- the platform can block release progression when verification is not clean

The platform should become the source of truth for release verification.
GitHub Actions should become optional or disappear once platform parity is complete.

## 2. Design Principles

### 2.1 Platform-owned, not browser-owned

The browser must not run shell scripts directly.

The UI should only:

- dispatch a suite run
- show status
- show logs and diagnostics
- show readiness blockers

The backend owns:

- run persistence
- queueing and execution
- audit events
- secret access
- controlled repair actions

### 2.2 Release-blocking by default

The primary suite is a release gate, not a convenience tool.

That means:

- it has an explicit run record
- it has ordered stages
- it stops on blocking failures
- it can be surfaced later in release approval and apply flows

### 2.3 Repair must be explicit and narrow

Automatic repair is dangerous unless it is bounded.

The current design allows an explicit `allowControlPlaneRepair` flag.
That repair path is intentionally limited to:

- shared inference reconcile
- canonical rollout recreation

It does not:

- invent or rotate secrets
- mutate deployment content
- bypass hosted verification
- run arbitrary shell commands

### 2.4 Secret values never leave the platform

The UI can show:

- required secret names
- whether a required secret is present
- whether secret readiness blocks a rollout

The UI must not show:

- raw secret values
- decrypted tokens
- passwords

## 3. Current Implementation

### 3.1 UI surfaces

There are now two distinct verification surfaces:

- `/verification`
  - deployment-scoped hosted verification
  - use this for one deployment
- `/verification-ops`
  - platform-admin control plane for canonical verification operations
  - use this for fleet orchestration and the release suite

`/verification-ops` is intentionally separate from the existing verification page.

### 3.2 Backend suite runner

The backend now has a platform verification suite subsystem.

Core pieces:

- suite catalog
  - `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PlatformVerificationSuiteCatalog.java`
- suite service
  - `.../PlatformVerificationSuiteService.java`
- suite executor
  - `.../PlatformVerificationSuiteExecutionService.java`
- suite controller
  - `.../PlatformVerificationSuiteController.java`
- async executor
  - `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/config/AsyncExecutionConfig.java`
- persisted run tables
  - `Platfrom/backend/src/main/resources/db/migration/V56__platform_verification_suite_runs.sql`

### 3.3 Persisted model

The control plane now persists:

- suite runs
- suite stages

That gives the platform:

- run history
- stage-by-stage status
- recovery for stale active runs
- future release gating hooks

### 3.4 Admin API

Current admin endpoints:

- `GET /api/verification-suites`
- `GET /api/verification-suites/runs`
- `GET /api/verification-suites/runs/{runId}`
- `POST /api/verification-suites/{suiteKey}/runs`

Security:

- platform admin only

## 4. Current Canonical Suite

Implemented suite key:

- `canonical-release-readiness`

Current ordered stages:

1. shared inference service health
2. canonical rollout inventory
3. marketplace hosted verification
4. ecommerce hosted verification
5. qdrant hosted verification
6. pinecone hosted verification
7. milvus hosted verification
8. weaviate hosted verification

This suite is intentionally fixed-order.
It encodes the operational dependency chain instead of asking each operator or CI workflow to reconstruct it.

## 5. What The UI Now Does

The `Verification Ops` page now exposes:

- canonical fleet summary
- inference-services summary
- deployment status summary
- platform release suite dispatch
- guarded control-plane repair toggle
- latest suite run and stage-by-stage status
- recent suite runs
- platform-visible secret readiness
- hosted verification output history
- manual fallback controls for rollout recreation and deployment-only hosted verification

The page is designed so the release suite is the primary path and the manual controls are a fallback.

## 6. Security And Enterprise Boundaries

### 6.1 No arbitrary script execution from UI

The platform does not execute arbitrary repository shell scripts from the browser or from ad hoc UI input.

That is deliberate.
Enterprise-safe verification needs:

- a bounded catalog
- explicit stage types
- explicit permissions
- explicit audit events

### 6.2 Auditability

The suite service records audit events for:

- dispatch
- recovery of stale runs
- completion

This is necessary if release verification becomes approval-relevant.

### 6.3 Browser visibility is filtered

Operators can see:

- run status
- stage summaries
- hosted verification output
- readiness blockers

Operators cannot retrieve raw secret values through this surface.

### 6.4 Separation of concerns

Keep these concerns separate:

- deployment-scoped verification
- canonical fleet verification
- provider-direct verification
- platform admin regression
- Shopify verification

The UI suite should orchestrate these concerns through bounded stages.
It should not flatten them into one giant untyped script.

## 7. Why This Is Better Than GitHub Actions

GitHub Actions has been useful, but it has structural limits for this product:

- it depends on duplicated CI secrets and workflow wiring
- it is not the control plane that owns deployments and releases
- it is awkward to inspect from the product UI
- it is harder to make release-blocking inside platform workflows
- it encourages script-by-script drift

The platform-owned model improves:

- auditability
- operator ergonomics
- release gating
- secret governance
- alignment with platform truth

## 8. What Is Not Yet Full Parity

The current suite foundation does not yet replace every GitHub Actions workflow.

Major remaining parity gaps:

- platform admin live regression
- direct managed-provider verification suite
- marketplace install-flow verification
- Shopify verification flows
- release approval integration
- apply-time enforcement that a required suite passed recently enough

So the current state is:

- strong foundation in platform
- not yet full GitHub Actions retirement

## 9. Recommended Migration Path

Do not delete GitHub Actions first.
Replace it in phases.

### Phase 1

Use the platform release suite as the primary human-operated gate before release.

Keep GitHub Actions as a secondary safety net.

### Phase 2

Add new suite stage types for:

- platform admin regression
- provider-direct verification
- marketplace install flow
- Shopify verification

### Phase 3

Attach suite-pass requirements to release progression:

- block release approval if the required suite has not passed
- or block apply for release-blocking environments without fresh suite evidence

### Phase 4

Retire overlapping GitHub Actions workflows once suite parity is proven stable.

## 10. Operational Guidance For Future Sessions

When extending this architecture:

1. prefer new suite stage types over shell passthrough
2. keep repair operations explicit and bounded
3. never expose raw secret values to UI
4. persist every run and stage
5. keep stage order deterministic
6. add tests for stage execution semantics
7. update this guide and the verification restart guide

## 11. Current Source Files

Backend:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PlatformVerificationSuiteCatalog.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PlatformVerificationSuiteService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PlatformVerificationSuiteExecutionService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/PlatformVerificationSuiteController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/config/PlatformVerificationSuiteProperties.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/config/AsyncExecutionConfig.java`
- `Platfrom/backend/src/main/resources/db/migration/V56__platform_verification_suite_runs.sql`

UI:

- `Platfrom/ui/src/pages/VerificationOpsPage.tsx`
- `Platfrom/ui/src/api/platformApi.ts`
- `Platfrom/ui/src/components/HostedVerificationRunHistory.tsx`

Operational references:

- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_VERIFICATION_RESTART_GUIDE.md`
- `Final_Documentation/Development_Guides/LLM-guides/PLATFORM_VERIFICATION_AND_AUTH_TROUBLESHOOTING_GUIDE.md`
- `Final_Documentation/Development_Guides/GITHUB_ACTIONS_VERIFICATION_SUITE_GUIDE.md`
