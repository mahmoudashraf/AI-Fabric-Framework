# Remaining Setup And Validity Recovery

Scope: complete 010.36 / 010.44 without resetting resource identities, financial
history, deployment-local accounting, or customer-owned integrations. UI
extraction stays last. This is an execution tracker, not a new product direction.

## Actual Boundary

- Both control planes are verified on `2a7f7fad397e7c6412f8806fcdb589339012bfce`,
  image `sha256:5d00f3299608217748e889a77029b2fe3df0f71eafcbbe3dc35ba22a5997a20b`,
  schema 206. Source-only maintenance is closed with original state preserved.
- The same customer runtime, connector, AI-state PostgreSQL, Inbox application
  and Inbox PostgreSQL exist. Inbox deployment `s9nzu0yu3yj9cawkmguuhwld` finished
  healthy at 2026-10-20 23:31:45 UTC. No customer business database was created.
- Release `rel-b7206a67` then failed `FRESH_MEASURED_CAPACITY_REQUIRED`.
  Reservation `c4d054ec-f700-4cd6-9127-ba74132c9096` is `OUTCOME_UNKNOWN`, revision
  18. The Inbox node is `INITIALIZING`, initialized=false, configuration revision
  2 already provisioned. This must not be treated as an absent resource.
- The existing allocation `pua-9a083459-f4a4-41a4-a414-4299ed870570` is initialized
  ACTIVE but its authorization expiry was 23:30 UTC. Initialization state is not
  proof of current eligibility. No lack of OpenAI credit or user material is
  established. Do not ask for more money, reset counters, or issue replacement
  credit to disguise this expired time window.

## Execution Order

- [x] Diagnose and fix PostgreSQL URL protocol, startup budget and already-running
  database reconciliation; qualify critical tests and verify both control planes.
- [x] Collect a real five-minute current-fleet baseline. It is not a workload
  capacity certification and must satisfy the next policy's freshness boundary.
- [ ] Publish measured policy continuity through the existing reviewed API with
  unchanged capacity, quotas, reservation identity and financial history.
- [x] Implement and locally qualify explicit continuation from a provisioned, uninitialized dependency
  checkpoint. Verify exact live ownership, image, environment, health and terminal
  deployment history before reusing it. Do not redeploy an unchanged healthy
  service. Existing initialization operation keys must remain idempotent.
  This source fix is not deployed yet; combined runtime/CP integration and hosted
  continuation remain unchecked.
- [ ] Establish the correct supported, counter-preserving allowance-validity
  renewal and runtime-artifact adoption path. Preserve the original approval,
  limits, charges, holds and store identity; any expiry change needs an explicit
  durable authorization and positive readback, not SQL repair or a fresh wallet.
  No implementation or hosted renewal is claimed yet.
- [ ] Reconcile the same release through source synchronization and verification;
  activate the existing consumer and app only after those gates pass.
- [ ] Run real stock/document compound queries, scoped reads, confirmed writes,
  protected sign-in/cancellation/account switching, and human Inbox handover.
- [ ] Complete production promotion with independent source indexing, operations
  acceptance, functional UI acceptance, and finally reversible UI extraction.

## Independent Work

- Functional receipt-handling UI `592a9f9de479ce33fd859f897a39bd867f653f37` has
  qualified unit/browser/build evidence and successful UI-only CI. Both staging
  and production are now verified on image
  `sha256:b270da722cadf9fe0a898ec47d04cc9128a57caf69378caec17d67f66297efe3`.
  Production job `q7nxhiona0ut0ka0hndjm9or` finished; readback proof
  `3616e7761f2780c7b53835dfc8a05332f792ca06af2855dea3e9ca21640358c3`
  is under `/private/tmp/01044-known-receipt-ui-rollout-20261020/production`.
  Saved/effective configuration, domains and API runtime configuration are
  unchanged. This is not UI extraction or full hosted customer acceptance.
- Reviewed-project Supabase synthetic identities are prepared privately. They
  are not Platform owner identities or customer business records.
- Main owns sequential integration and all hosted mutation. Parallel work is
  limited to bounded independent source analysis or qualified deliverables.

## 2026-10-20 23:55 UTC Active Implementation

- Main implements explicit provisioned-checkpoint continuation. It rechecks owned
  app/database identities, image, configured environment, capacity, health,
  volumes and terminal native job history without PATCH, start or redeploy.
  Initialization retains the original deterministic operation key. The real
  installed history response uses numeric `application_id`; detailed job readback
  carries the application UUID. Both must match the owned application.
- Focused qualification is in progress, including actual PostgreSQL. Earlier
  compile/test-wiring errors were corrected; they are not counted as passes.
  No new backend rollout or customer continuation has occurred in this cycle.
- Two bounded product changes proceed independently: append-only runtime-ledger
  validity extensions and explicit control-plane review/acknowledgement. They may
  extend the existing allocation's finite time window, not its limits, unused
  credit, counters, approval fingerprint or stopped state. Lost responses require
  receipt readback, never an automatic POST replay. Public AI Fabric is unchanged.
- The qualified new product runtime also needs an explicit reviewed artifact
  adoption path for this failed release. Current immutable offer/release pins
  must not be silently edited or bypassed. Source analysis is in progress.
- Actual current-fleet samples completed 23:43:39/40 UTC. Staging CPU p50/p95/max
  was 525/796/1013 millicores on eight cores; minimum available RAM 8.88 GiB,
  disk 88.67 GiB. Production was 195/310/600 millicores on four cores, RAM
  11.67 GiB and disk 122.46 GiB. Swap is excluded from capacity. These are not
  workload admission certification. Fresh admission evidence will be collected
  after the next combined backend rollout rather than repeatedly published
  before code is ready.

## Checkpoint Source Qualification

- Final isolated unit run: 115 passing checks, zero failures/errors/skips.
  The 36 passing PostgreSQL continuation cases have byte-identical admission
  production/test classes in the final copy. Combined scope is 151 checks.
- Proof: `/private/tmp/01044-final-checkpoint-unit-20261021.xb735R/qualification.actual.json`,
  SHA256 `2904de2f0bf0ca5eb364d36675c29b737ff2fe0a81e70db0259ea26a675df2a3`.
- That PostgreSQL run used schema206. The combined schema207 migration/regression
  and final backend package/CI are still required; this is not a hosted pass.

## Runtime Validity Source Qualification

- Product ledger/runtime: 112 passing checks, including 38 actual PostgreSQL
  cases. V19-to-V20 migration preserves the existing ACTIVE binding, approval,
  usage and outstanding holds. Package passes; public framework dependencies
  remain exactly 0.8.36. No public framework build or release was performed.
- Proof: `/private/tmp/01044-product-budget-validity-extension-manifest-20261021/qualification.actual.json`,
  SHA256 `7de6ac70e709b20c1d7335f1a06ad40a0b081200eb3984021dba141bf3067e0d`.
  Main verified all 15 frozen code/test/manifest files against the worktree.
- This is not a live renewal. Image publication, reviewed artifact adoption,
  control-plane integration and exact hosted receipt verification remain pending.
