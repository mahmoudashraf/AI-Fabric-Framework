# Vectorization And Tenant-Scoped Verification Hardening Plan

Status: execution hardening plan (2026-04-04)

This document closes the remaining proof gap after the core Wave 4 Track B implementation.

The branch now has real vectorization control-plane and runner code, but Track B should not be considered fully complete until the platform can prove, through admin-triggered verification and hosted or GitHub verification, that:

- managed runner provisioning actually completes on deployed environments
- runner registration and claim flow actually works
- source discovery succeeds with real customer connectivity
- bounded sample vectorization actually writes through the runtime data-sync path
- vectorization sync-state changes match the real indexed outcome
- shared tenant-scoped storage actually preserves isolation between deployments bound to different tenants

This plan defines that closure work.

---

## 1) Why This Plan Exists

The current implementation already covers:

- vectorization plans, revisions, and source connections
- deployment-scoped execution identity and runner modes
- platform-managed runner provisioning
- runner registration, session, and lease control
- idempotent runtime data-sync ingestion
- vectorization UI and run lifecycle controls
- tenant-scoped shared vector handle resolution and readiness checks

What is still not fully proven today:

- a live deployment that provisions a managed runner and shows it healthy
- a real source-discovery run through the runner
- a real vectorization run from source adapter through runner through connector and runtime to vector storage
- a live tenant-isolation proof against shared vector infrastructure
- admin-facing verification flows that operators can run on demand from the platform
- full hosted and GitHub verification parity for those active proofs

Track B should therefore be treated as:

- core implementation complete
- verification closure still open

---

## 2) Verification Principles

Verification hardening should follow these rules:

- reuse the existing deployment verification system instead of inventing a disconnected second verification product
- give operators and platform admins first-class verification tools in the platform UI and API
- keep verification safe by separating read-only verification from bounded active verification
- persist verification evidence in the platform database so results can be audited and compared over time
- keep the platform as the control plane and the source of truth
- keep runners pull-only; the platform should create verification work, not call runners directly
- prove real product paths:
  - source adapter
  - runner
  - connector data-sync
  - runtime indexing path
  - selected or provisioned vector backend
- prove tenant isolation through the real deployment and target path, not by synthetic config-only checks

The platform should also capture bounded timing evidence so operators can tell whether the platform is merely functioning or is performing within acceptable limits.

Recommended timing evidence:

- runner session acquisition latency
- run-claim latency
- discovery duration
- sample vectorization duration
- checkpoint interval and throughput
- verification completion duration

---

## 3) Required Proof Outcomes

Track B verification closure should prove all of the following:

### 3.1 Managed runner provisioning proof

- deployment provisioning includes the expected vectorization runner service when runner mode requires it
- the managed runner service actually deploys and reaches healthy state
- the runner registers a compatible active session for the intended deployment-scoped execution identity
- the platform shows the runner as `CURRENT`, not merely configured

### 3.2 Source discovery proof

- a real discovery task can be claimed by an eligible runner
- the adapter can connect to the configured source using the real execution bundle
- the platform receives and stores discovery results
- expected per-entity source counts or estimates appear in the platform

### 3.3 Sample vectorization execution proof

- a bounded vectorization run can be created from the platform
- the runner can claim it, read source data, and send batches through the connector or runtime data-sync path
- the run completes successfully
- indexed coverage changes for the intended configured entities
- the platform records checkpoints, timings, and final evidence

### 3.4 Sync-state and reindex proof

- bootstrap-required deployments can move to `IN_SYNC` or `SOURCE_EMPTY` after successful discovery or vectorization
- deployments with indexed-output drift can become `OUT_OF_DATE`
- deferred reindex leaves the deployment explicitly `REINDEX_DEFERRED` or equivalent
- manual override stays distinct from a verified in-sync state

### 3.5 Tenant-scoped isolation proof

- two deployments under the same customer can use the same shared provider root while remaining separately scoped by tenant
- vectorized data written for tenant A is not visible through tenant B deployment paths
- vectorized data written for tenant B is not visible through tenant A deployment paths
- verification stores both the shared-root evidence and the non-overlap evidence

### 3.6 Hosted and GitHub verification parity

- the same proofs should be invokable through hosted verification and GitHub Actions
- verification results should be visible in:
  - deployment verification UI
  - platform admin verification history
  - hosted verification logs
  - GitHub Actions logs

---

## 4) Verification Modes And Safety Boundaries

Verification should be split into explicit modes:

### 4.1 Read-only verification

Used for:

- control-plane readiness
- runner registration and compatibility checks
- source connection metadata and discovery checks when the adapter does not write
- sync-state and impact-analysis inspection

This mode should never change indexed data.

### 4.2 Active bounded verification

Used for:

- sample vectorization smoke runs
- reindex smoke for selected entities
- tenant isolation proof using sentinel records or controlled fixture data

This mode is allowed to write, but must be bounded:

- explicit operator confirmation
- explicit entity scope
- explicit source scope such as sample page, limited id range, or limited fixture dataset
- explicit run reason and verification tag
- explicit cleanup or overwrite posture when applicable

### 4.3 Canonical fixture verification

Used for internal and canonical deployments where the platform controls both the source fixture and the verification expectations.

This mode should be the strongest and most automated:

- canonical runner provisioning proof
- canonical discovery proof
- canonical sample vectorization proof
- canonical tenant-isolation proof for shared-storage stacks

---

## 5) Admin-Based Verification Model

The platform should expose verification as first-class operator actions.

Recommended verification types:

- `CONTROL_PLANE_READINESS`
- `RUNNER_PROVISIONING_SMOKE`
- `SOURCE_DISCOVERY_SMOKE`
- `SAMPLE_VECTORIZATION_SMOKE`
- `SYNC_STATE_AND_REINDEX_SMOKE`
- `TENANT_SHARED_ISOLATION_SMOKE`
- `RUNNER_COMPATIBILITY_SMOKE`

Recommended operator surfaces:

- deployment `Vectorization` workspace
- deployment `Verification` workspace
- platform-admin verification page for canonical or sweep runs

Recommended operator capabilities:

- trigger a verification type
- choose read-only versus active bounded mode where applicable
- choose entity scope for active vectorization verification
- see timings, checkpoints, failure buckets, and evidence
- rerun or compare against previous verification attempts
- see whether the result is:
  - `PASSED`
  - `FAILED`
  - `PARTIAL`
  - `CANCELLED`
  - `TIMED_OUT`

Recommended role model:

- deployment operator or platform admin can run deployment-scoped verification
- only platform admin can run platform-wide canonical sweeps and shared-tenant pair verification across multiple deployments

The platform should also support an admin sweep posture:

- run the selected verification profile across a configured canonical deployment set
- use that sweep as a platform health and regression signal
- store the sweep result as a top-level admin verification record with per-deployment child evidence

---

## 6) Hosted Verification And GitHub Parity

The existing hosted verification system should be extended, not replaced.

Required changes:

- hosted verification scripts should be able to trigger admin verification endpoints
- hosted verification should poll for the verification result and capture the final evidence
- GitHub Actions should do the same using the platform admin auth path already used by deployment verification workflows

Recommended hosted verification profile additions:

- keep the existing vector and ecommerce profiles for control-plane and deployment-shape checks
- add active vectorization verification steps when the deployment and runner mode allow them
- add a dedicated tenant-shared verification profile or tenant-isolation section for canonical shared-storage verification pairs

Recommended GitHub and hosted execution rule:

- read-only vectorization readiness checks should run broadly
- active vectorization smoke should run for canonical deployments and for deployments explicitly flagged for active verification
- tenant-isolation smoke should run for canonical shared-storage verification sets and for admin-triggered paired deployments

Verification evidence should be consistent across:

- platform admin UI
- deployment verification UI
- hosted verification logs
- GitHub Actions logs

---

## 7) Code Residency And Data Model

### 7.1 Platform backend

Recommended platform code location:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/vectorization/verification/`

Recommended packages:

- `entity/`
  - `VectorizationVerificationRunEntity`
  - `VectorizationVerificationStepEntity`
  - `VectorizationVerificationEvidenceEntity`
  - optional `VectorizationVerificationSweepEntity`
- `repository/`
- `model/`
- `service/`
  - `VectorizationVerificationService`
  - `VectorizationVerificationDispatchService`
  - `VectorizationVerificationEvidenceService`
  - `VectorizationTenantIsolationVerificationService`
  - `VectorizationVerificationHostedBridgeService`
- `web/`
  - `VectorizationVerificationController`
  - `VectorizationAdminVerificationController`

This verification domain should integrate with:

- `.../backend/vectorization/` for plans, runs, runner registration, sync-state, and discovery
- `.../backend/deployment/service/` for hosted verification and release-readiness integration
- `.../backend/tenant/` and tenant-scoped vector services for shared-root and isolation evidence

### 7.2 UI

Recommended UI surfaces:

- extend `VectorizationPage.tsx` with verification launchers, history, evidence, and timing views
- extend `VerificationPage.tsx` with vectorization-specific active proof sections
- add tenant-pair verification controls for platform admins where needed

### 7.3 Scripts and workflows

Recommended script surfaces:

- extend `scripts/verify-vector-deployment.sh`
- extend `scripts/verify-ecommerce-deployment.sh`
- optionally add a dedicated `scripts/verify-tenant-shared-vectorization.sh`

Those scripts should:

- trigger verification through platform admin APIs
- poll until completion or timeout
- print structured failure evidence
- distinguish read-only verification from active bounded verification

---

## 8) Canonical Shared-Tenant Verification Fixtures

The platform should maintain a canonical shared-tenant verification pair for each shared-storage provider path it supports.

Recommended fixture shape:

- one customer
- tenant A deployment
- tenant B deployment
- same shared provider root
- distinct scoped tenant handles
- distinct sentinel records or fixture records per tenant

The proof should include:

- shared root is the same
- tenant handles are distinct
- tenant A deployment sees only tenant A data
- tenant B deployment sees only tenant B data
- no cross-tenant leak through the deployment search or retrieval path

This should be part of:

- platform-admin verification
- hosted verification for canonical shared-storage stacks
- GitHub Actions verification for the same stacks

---

## 9) Recommended Wave 4 Execution Sequence For Verification Closure

Track B should not be marked fully complete until these items land:

61. verification domain model and admin APIs: add verification entities, admin launch APIs, evidence persistence, timing capture, and deployment or platform-admin history views
62. managed runner provisioning and discovery proof: add admin verifications that prove managed runner deployment, registration, compatibility, and discovery with real connectivity
63. bounded sample vectorization proof: add admin-triggered sample vectorization smoke that writes through data-sync, captures checkpoints and timings, and updates sync-state evidence
64. tenant-scoped isolation proof: add paired-deployment shared-root isolation verification with canonical fixtures and admin-triggered evidence views
65. hosted and GitHub parity: extend hosted verification, GitHub workflows, and platform scorecards so the same proofs are visible across operator UI, platform-admin sweeps, and automation

The rest of Wave 4 should follow only after these proof gaps are closed.

---

## 10) Completion Criteria

Track B verification closure is complete only when all of these are true:

- platform admins can trigger vectorization verification from the platform UI and API
- deployment operators can run the deployment-scoped verification types they are allowed to use
- the platform stores verification history, evidence, timings, and failure buckets
- hosted verification can trigger and display vectorization proof results
- GitHub Actions can trigger and display vectorization proof results
- at least one canonical managed-runner deployment proves:
  - runner provisioning
  - discovery
  - sample vectorization
  - sync-state transition
- at least one canonical shared-storage pair proves tenant isolation through the real deployment path

When those conditions are met, Track B can be treated as fully complete rather than only core-complete.
