# LoomAI Engage Productization Execution Record

Date: 2026-10-14. Governing scope: section 10 of
[010.36](010_36_STANDALONE_AI_WORKSPACE_IMPLEMENTATION_PLAN.md).
Source baseline: `5206acd97423ee857b5a60c1fe370633bf3fd5a9`.

## Execution Gates

- P0: in progress. Final API/schema contracts and prerequisite hardening are
  being implemented. Customer-owned S3 canary credentials are pending from the
  owner; do not substitute managed storage or claim the storage gate passed.
- P1-P5: not certified. Independent safety prerequisites may proceed; dependent
  hosted provisioning remains gated on storage, identity, notification,
  immutable dependency artifacts and measured capacity.
- P6: not started. No editor extraction or customer layout change before the
  verified functional baseline. Preserve the frozen STAFF-001 through STAFF-039
  capability register and source snapshot.
- P7: not started. Fresh production-customer lifecycle certification is required.

## Initial Readback

Staging Coolify readback found 33 applications and five databases. SSH to
staging and the direct production Coolify API were blocked from this network.
Existing shell-only capacity evidence is not admission certification for the
complete dealership topology. No resources were removed or redeployed.

## Safety Prerequisites

- Customer scope must resolve an active persisted owner and active persisted
  customer, or return `403`. A missing customer scope cannot become a global
  customer-list grant. Cross-customer and blank-owner deployment access remain
  denied.
- Existing-runtime administration must use explicit deployment, target and
  release identity. A missing or inconsistent provider URL/resource record is a
  `409`, never a deployment-wide URL fallback.
- Consumer binding mutations need database concurrency control and a persisted
  operation identity. Direct rebind, promotion and rollback share that contract;
  no wizard-only guard.

## Verification Ledger

### Source Safety Progress

- Active persisted customer-owner checks deny absent, disabled and foreign scope.
- Explicit target/release administration validates candidate membership, actual
  runtime application/server evidence and release-owned endpoints. Document
  operations no longer route through the deployment's mutable latest URL.
- Consumer assignment commands now have a row-locked binding revision,
  expected-revision requirement, persisted operation fingerprint/result, and
  complete before/after release/target history. Shopify internal callers and
  verification clients use the same concurrency contract.
- The reviewed `slot:document-storage` resolves exactly one owned configured
  binding per selected target. Releases freeze the safe configuration and
  credential revisions; drift, missing evidence and cross-target references
  block administration/verification. A staging lineage can configure production
  S3 without weakening mounted-production denial.
- Practical promotion selects the consumer's exact bound staging release, not
  latest verified. Its candidate includes immutable image/config identity,
  verification identity and target/storage fingerprints. A configured evidence
  age is mandatory. Whole-command replay preserves the original result without
  repeating lifecycle updates. No client source-artifact override is accepted.
- Shared vector provisioning derives distinct customer/tenant/deployment/target
  scopes. Registry updates no longer detach another target's active scope.
- R3 target-only retirement, R5 dedicated dependency/operator lifecycle and R7
  atomic admission/expiry ledgers exist as independently verified primitives.
  Actual interlock/readback/initialization/notice adapters and orchestration are
  still required. Missing adapters fail closed, not fake success.
- Apply, release-bound consumer rebind and document-binding mutation now join
  the durable target-retirement fence. Existing staff UI has only functional
  contract wiring; no P6 layout/editor extraction has been performed.
- Target-owned authority generation, immutable snapshots, credential revision
  checks and explicit-key assertion signing are implemented as a tested service.
  Actual provisioning, runtime administration and activation must still consume
  those snapshots; this is not proof that live targets use isolated keys.
- Invited owners have an explicit `CONFIGURATION_ONLY` bootstrap command. It
  locks the active entitlement, validates reviewed composition/name settings,
  reuses the template factory, and commits the association/receipt atomically.
  Retries do not duplicate drafts. It does not seed automatic vectorization,
  publish a version, queue an apply or create provider resources.
- Runtime retirement has a concrete private command/status/snapshot API with
  real observations, owned continuation and durable ingress/worker fences. The
  unused competing proposed runtime endpoint DTO was removed. Unknown framework
  or remote-worker state continues to block reclamation.
- Document UI mutations capture their original deployment/target/release and
  invalidate only that scope after delayed completion. Target navigation does
  not pretend to cancel a server operation or display its result on a new scope.

### Executed Checks

- Initial clean sequential backend suite: 1,025 tests passed, zero failures,
  errors or skips. This predates the expanded R3/R5/R6/R7 source.
- Consumer concurrency on PostgreSQL 16: seven tests passed.
- Whole-promotion-command H2/PostgreSQL concurrency/replay checks passed in the
  scoped run. Document-slot and promotion unit checks passed.
- First expanded backend run: 1,234 tests, one assertion failure and three
  errors. The failure was a stale two-argument vector-summary mock. The errors
  stemmed from verifying a transactional mock through its Spring proxy and
  contaminating subsequent Mockito state. The fixtures were corrected; the
  scoped repair run passed. A new clean full run is still required.
- Independent agent checks: R3 34, R5 35, R7 68 tests passed, including
  PostgreSQL tests. These do not substitute for the integrated final suite.
- R6 independent authorization checks: 58 passed; authority checks: 30 passed.
  Customer-bootstrap checks: 43 passed (26 unit, 17 H2 persistence). Template
  creation is mocked in the latter; locks, ownership, pins, rollback, replay,
  associations and audit persistence are real.
- Parent Maven runtime verification: 85 passed, covering the 22 retirement tests
  and chat ownership/auth, prompt preview, suggestions and public sessions.
  No public framework source or dependency version was changed.
- Parent browser race smoke: ten delayed-response scenarios passed with the real
  Document Knowledge page/API client and local HTTP fixtures. This is not live
  provider verification. UI TypeScript/build checks and the current parent
  production build passed, with existing runtime-config/bundle-size warnings.
- The new real-template bootstrap test initially asserted the wrong display
  value (`null` instead of `draft`); persisted active-version identity was empty.
  Its first integrated placement also reused a shared H2 fixture and duplicated
  document-plugin seeds. It now uses a dedicated test database. The failed full
  run was stopped; the clean corrected full backend run passed 1,301 tests in
  211 suites, zero failures, errors or skips. This verifies the current source
  checkpoint, not the remaining integration or hosted acceptance gates.
- Frozen staff baseline snapshot remains unchanged. Source navigation comparison
  is not browser parity certification. `git diff --check` passed.

Logs reside under `/private/tmp/01036-*`; copy final sanitized evidence to the
verification-support directory after the integrated gate passes. Source baseline
started at `5206acd97423ee857b5a60c1fe370633bf3fd5a9`; verified source foundations
are saved as a local checkpoint on `Platform-V11`. That checkpoint is not a
release and is not pushed/deployed before the remaining safety gates. No live
deployment, provider resource or assignment was changed by these checks.

### Remaining Before Admission

Finish complete target authority/binding snapshots and remaining environment-
specific callers; reconcile the runtime/platform drain contract; implement the
real R3/R5/R6/R7 adapters and reviewed apply flow. Verify immutable
dependency artifacts, real resource envelopes, notification/recovery evidence,
S3 indexing and cross-target denial. Then complete the outstanding Engage auth,
accessibility and capacity gates. P6 extraction and P7 fresh-customer hosted
certification remain later gates. The canonical 40-task checklist stays open.

### Runtime Generation Reuse Blocker

The current runtime drain singleton pins deployment/tenant/target/release and
topology. A subsequent release using the same target database cannot silently
adopt that singleton: it returns `PERSISTED_RUNTIME_GENERATION_MISMATCH` (or
topology mismatch). This is a safe refusal, not a completed upgrade lifecycle.
Before enabling the offer, implement an explicitly authorized, audited generation
transition tied to provider-stop/readback evidence and the target fence. Preserve
sessions and retained evidence, reject old worker generations, and prove same-
target repeated release and restart behavior. Do not clear the singleton, erase
evidence, accept the environment value alone, or disable retirement to hide it.
Enabled framework indexing/recovery/Quartz fences and authoritative connector/
inbox readbacks remain separately named blockers; S3 is not the only open gate.

### Owner S3 Inputs

Awaited: private bucket/region/HTTPS endpoint, staging and production prefixes,
separate prefix-limited read credentials, optional session-token expiry and
versioning status. Native AWS canary example: `eu-west-2`, endpoint
`https://s3.eu-west-2.amazonaws.com`, `dealership/staging/` and
`dealership/production/`; these are suggested values, not provisioned resources.
Keep Block Public Access enabled. Only list/read permissions are needed;
versioned reads require `s3:GetObjectVersion`. Use UTF-8 text/configured JSON
fixtures, not general PDF/Word uploads. Secret values belong only in the ignored
private auth document and managed write-only bindings, never this ledger.
