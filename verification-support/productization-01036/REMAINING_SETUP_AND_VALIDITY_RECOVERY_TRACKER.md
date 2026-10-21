# Remaining Setup And Validity Recovery

Scope: complete 010.36 / 010.44 without resetting resource identities, financial
history, deployment-local accounting, or customer-owned integrations. UI
extraction stays last. This is an execution tracker, not a new product direction.

## Actual Boundary

### Latest Scope Decision And Basic Recovery

- Latest verified state, 03:03 UTC: BOTH control planes run `6fd136abd`, image
  `sha256:3f6e3a20ba7933a5c9066a14979d44951578bedafeaa4bee325945229974ba38`,
  schema 207 and health UP. Maintenance closed at 02:51 UTC with original
  tables/history/configuration preserved. The initial 133 checks (25 actual
  PostgreSQL) passed; the final completion-path correction separately passed
  24 targeted checks and package/build/hosted verification.
- The same repair now has an explicit append-only window until
  `2026-10-21T04:41:21.170Z`. Reservation revision is 19, worker held. The old
  drain deadline/binding is unchanged. A new read-only reconciliation returned
  `OWNED_POSTGRES_EMPTY_STORE_READBACK_FAILED`; no drain replay, runtime stop,
  image patch, wallet renewal or activation occurred.
- Cause: the existing production-only `loomai-retire` forced-command adapter
  permits host metadata but deliberately denies database execution. Its exact
  four-table count extension passes 24 offline Python checks and a read-only
  host preflight, but is NOT installed. A separate explicit human approval is
  pending in **Required for production**. Preserve key/account/from/host-pin,
  sudoers/sshd/firewall restrictions; do not use general admin credentials as an
  automatic substitute for the product's restricted identity.
- Fresh 61-by-5-second host samples completed 02:56 UTC. Physical ceilings and
  safety headroom are unchanged. Staging's observed 1,412 millicore peak exceeds
  the old 1,000 baseline; a conservative proposed baseline is 1,500, reducing
  available capacity rather than raising a ceiling. Product continuity already
  supports remeasured baselines. This measured policy is not yet published.
- Main's bounded adapter installer/preflight is prepared under
  `/private/tmp/01044-owned-pg-host-readback-20261021`; no host mutation has
  occurred. The existing owner has a fresh normal CUSTOMER_ADMIN session for
  continuation. Pending approval does not authorize new resources or spending.

- On 2026-10-21 the user retained the full self-service target (option 1), with
  basic cases first and difficult cases documented. The staff-assisted scope
  reduction suggested earlier was not accepted. UI extraction remains last.
- A narrow correction is being qualified: one staff-reviewed, append-only
  execution-window renewal, capped at two hours and the existing reservation
  expiry. The original plan, claim, acknowledged drain command and wallet stay
  immutable; UNKNOWN effects still require read-only reconciliation.
- For never-activated initial setup only, explicit consent to discard non-durable
  state may be combined with a fixed read-only native count of all rows in four
  owned PostgreSQL conversation/action-state tables. All four must be zero.
  The original signed incomplete snapshot is preserved separately; this is not
  complete worker fencing, retirement, or a claim that in-memory state was empty.
- Actual read-only staging preflight at approximately 02:21 UTC returned zero
  for `chat_sessions`, `ai_conversation_control`,
  `ai_conversation_orchestration_state` and `ai_specialist_execution`.
  No repair effect, wallet renewal or app activation was dispatched by this check.
- Deferred hard cases: nonempty/custom stores, active customers requiring state
  preservation, and general automatic repair of incomplete diagnostics. Those
  remain blocked. The basic path requires owned remote writers quiesced and a
  physical runtime stop before the reviewed image replacement.
- Focused integrated qualification passes 133 checks, including 25 real
  PostgreSQL cases, with zero failures/errors/skips. The bounded review's one
  finding is fixed: fresh connector/Inbox stopped status is required through
  drain cancellation. Final package and one backend-only rollout are next. No
  new hosted recovery or full customer acceptance is claimed yet.

- Previous deployed boundary (superseded above): both control planes verified on `ea8294d2dc2971f6adfb7ff61d2021c41e293786`,
  image `sha256:637b3b790e2ab54fb88d061d6068598eefac6e90b07a47ebb5ae65ce9ae90856`,
  schema 207. Latest source-only maintenance closed at 01:40 UTC, preserving
  original tables/history/configuration and the two empty validity audit tables.
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
- The same-release repair is now CLAIMED. Connector and Inbox are stopped;
  the original runtime is DRAINING at epoch1, still on its original image.
  BEGIN_DRAIN is acknowledged natively but its journal result remains UNKNOWN.
  Read-only reconciliation cannot establish complete idle-work inventory. The
  repair's execution deadline is 2026-10-21T01:53:17.065664792Z; after that,
  observation is permitted but no additional repair effect is authorized.

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
  This source fix is now deployed on both control planes. Hosted runtime repair,
  validity renewal and owner continuation remain unchecked.
- [ ] Establish the correct supported, counter-preserving allowance-validity
  renewal and runtime-artifact adoption path. Preserve the original approval,
  limits, charges, holds and store identity; any expiry change needs an explicit
  durable authorization and positive readback, not SQL repair or a fresh wallet.
  Runtime and control-plane source implementations are locally qualified below;
  no hosted renewal is claimed yet.
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

## Control-Plane Validity Source Qualification

- 197 passing checks, including 91 actual PostgreSQL cases, qualify explicit
  admin review, one-shot execution, durable UNKNOWN/ACK reconciliation, unchanged
  lifetime credit, immutable audit tables and existing allowance/coordinator
  behavior. The final 19 owned files match their isolated qualification hashes.
- Proof: `/private/tmp/01044-cp-budget-validity-extension-20261021/final-r3/qualification.actual.json`,
  SHA256 `1dd5f0303cedc210079222e071dcc28358c3d7031c6b11e139b72dbdc6c2e1d1`.
- Staff routes: `/api/deployments/provider-usage/validity-extensions/plan`, then
  exact reviewed `/{extensionId}/execute`; GET `/{extensionId}` inspects durable
  state and explicit POST `/{extensionId}/reconcile` performs only receipt GET.
  A missing/mismatched runtime receipt never releases the original intent.
- Original expiry/fingerprint remain immutable; ACK-derived effective expiry is
  separate. A fixed 30-second cross-host timestamp skew bound is tested at and
  beyond its boundary. It does not relax runtime expiry or the 24-hour extension
  ceiling. Actual signed runtime capability metadata is checked before intent.
- Main still must integrate the verified effective-artifact resolver, qualify
  combined schema207 continuation and publish/deploy. No live renewal is claimed.

## Applied-Migration Attestation Follow-Up

- The runtime now maps the two exact accounting migration descriptions to their
  stable protocol IDs. A declared capability does not imply its migration ran.
- Nine focused tests pass, including three actual PostgreSQL cases and the real
  V19-to-V20 authenticated overview projection; package passes. The prior
  112-case proof and all 20 public framework JARs remain unchanged.
- Proof: `/private/tmp/01044-runtime-budget-migration-id-followup-20261021/qualification.actual.json`,
  SHA256 `296b0f1832a05c6516c3f6db30201cece4e136f6f1428d25e7b15fb18bceeeae`.
  Runtime JAR SHA256 `cf2e1f546627ed204ec3d5d8e0474aaaa384c5852eb71f25c6093f25059445dc`.
- Main's effective-artifact, actual-migration and schema207 continuation
  integration compiles; 221 focused checks pass, including 105 actual PostgreSQL
  cases, with zero failures/errors/skips. Proof:
  `/private/tmp/01044-main-effective-artifact-integration-20261021.6g6nQh/qualification.actual.json`,
  SHA256 `51c9baa027b3ea7264abee761f3a807eda36ba53c755ce8df143edc82a0bb34b`.
  Final repair qualification and combined package remain required. This is not a
  hosted repair, renewal, green release, or completed plan.

## 2026-10-21 01:04 UTC Repair Integration

- Main owns the sequential repair implementation and rollout. The integrated
  focused batch passes 566 cases, including 116 actual PostgreSQL cases, with
  zero failures/errors/skips. Snapshot:
  `/private/tmp/01044-final-repair-r3-20261021.bYSpis`.
  Earlier fixture-constructor and ObjectProvider errors remain in the rejected
  local rounds; they are not represented as passing runs.
- Native preflight proves that this runtime advertises PRESENT indexing and
  specialist execution without full framework retirement fencing. The repair
  must not claim that a full drain passed. Explicit idle-checkpoint cold restart
  instead requires complete signed diagnostics, no active work or conversations,
  only retained terminal indexing failures, and a never-activated setup. It
  quiesces owned remote writers and physically stops the same runtime before
  changing its image. Before ingress resumes, idle counts and wallet identity,
  counters, limits and original expiry must still match.
- The four existing runtime/preview source-provenance records are updated by one
  separately journalled bulk PATCH. Their identities/flags and every unrelated
  environment record remain pinned. Installed Coolify source confirms the bulk
  endpoint handles preview and runtime records separately within that request.
- Actual read-only CP checks show no verified releases, release-backed consumer
  assignment, non-DRAFT app or retirement operation for this setup. Existing
  indexing failures identify expired or uninitialized budget authorization;
  they are retained, not deleted or relabelled. Subsequent normal indexing must
  be verified after the separate validity renewal.
- Ten individually journalled effects, exact runtime/connector native job
  receipts, one-shot dispatch, read-only UNKNOWN reconciliation and the separate
  claim/execution deadlines are covered. Original offer/version/release pins
  remain immutable. The same-release receipt does not authorize a later target
  or successor release; promotion still requires its own reviewed source path.
- This remains local qualification, not a hosted repair or finished M5-M9.
  Package, combined image publication, backend migration/rollout and actual
  repair/renewal/owner continuation precede customer activation. UI extraction
  remains last.

## 2026-10-21 01:23 UTC Hosted Backend Rollout

- CI 39525489237 and 39525489248 both pass for `f927b3ec24ed943554ebe89230551ed3765d71d1`.
  Backend-only publication skips UI. Staging job `tz43il2m6pvbpgxqzdxthndo` and
  production job `lvscpl4k3x25mdky3e9j2b52` finish healthy. Exact immutable image,
  migration207, original139 tables/raw history, environment and configuration
  preservation pass on both. Maintenance closure:
  `/private/tmp/01044-validity-recovery-rollout-20261021/maintenance.completed.json`,
  SHA256 `6c4f8a1781f82e106cde32c6bdec8440a9a13dc9c52c7f448683c0c1f55a9269`.
- Runtime image `sha256:c266a4284fdeebebf1d12f90b45b9445a5a5f7ee63defc7f2ba3123331fcad80`
  passes unstarted local package inspection. Its manifest/migration match committed
  source; all20 public framework libraries remain byte-identical at0.8.36.
  Inspection container is removed. Evidence:
  `/private/tmp/01044-validity-runtime-inspection-lwBlEfbRmpPQ/evidence.json`.
- The exact runtime image is preloaded on staging without starting a workload.
  Normal artifact registration returns201 Created, artifact `dsa-395fa10f`.
  The local helper initially expected200, but retains the successful response;
  no duplicate create is performed. Runtime repair/promotion/renewal are not yet
  claimed. Fresh61x5second host samples run after closed backend maintenance.
- User requested a candid delivery assessment in Platform - Updates. Delivered:
  scope has stretched and recovery tooling is disproportionate; freeze additions,
  finish the real customer journey and mandatory gates, defer noncritical work
  explicitly, and keep extraction last. No new framework release or unrelated
  infrastructure variant is part of this cycle.

## 2026-10-21 01:31 UTC Exact Drain Deadline Correction

- Replacement artifact `dsa-395fa10f` is staging-promoted. Repair plan
  `487f7a463aec7ae187e8943e8c6da2b65699a2356d5fb853c5a7bf30e6f77fb4` is claimed.
  Connector and Inbox stops are positively confirmed; runtime remains on its
  original image, with the acknowledged DRAINING state and epoch1. No wallet
  renewal, reset, replacement resource or replay has occurred.
- BEGIN_DRAIN returns409 because the existing PostgreSQL runtime stores deadline
  `01:53:17.065665Z`, while the plan contains `01:53:17.065664792Z`. All other
  acknowledged target/binding/fingerprint/epoch fields match. The command is
  recorded UNKNOWN and must be settled by read-only reconciliation, not POSTed
  again. Original execution deadline remains01:53UTC.
- The narrow Platform correction accepts only the exact instant or its exact
  PostgreSQL/JDBC microsecond representation, not a clock-skew window. Focused
  operations/service/journal tests pass48 cases including14 actual PostgreSQL;
  changed-by-one-microsecond deadlines still fail and reconciliation dispatches
  no command. Package/publication/rollout are next; public framework and runtime
  image remain unchanged. Snapshot:
  `/private/tmp/01044-repair-deadline-precision-20261021`.
- Fresh61x5second host measurements completed01:27UTC. They are current-fleet
  baselines only, not workload capacity certification. Publish continuity only
  after the narrow correction is deployed and the current reservation fence is
  known, without pretending these measurements occurred after a later rollout.

## 2026-10-21 01:46 UTC Actual Inventory Blocker

- Narrow correction `ea8294d2d` passes48 focused checks/14 actual PostgreSQL,
  package and backend-only CI39527864916. Both rollouts are verified healthy,
  with the exact image/source/schema and all existing data/history/configuration
  preserved. Closure:
  `/private/tmp/01044-deadline-precision-backend-rollout-20261021/maintenance.completed.json`,
  SHA256 `8bdd5558be3068463b1b8d7d3d8017003eaef9c945d2170d0bb6a0f604f25cb7`.
- The exact deadline mismatch is resolved. A single explicit read-only
  BEGIN_DRAIN reconciliation then correctly rejects `IDLE_CHECKPOINT_REQUIRED`.
  Persisted native snapshot proves zero in-flight requests, open conversations,
  pending confirmations and human work, but inventoryComplete=false and the
  background total unknown. Unsupported observations are the legacy/canonical
  action-draft stores and specialist execution. Indexing observes54 retained
  terminal failures. Zero observed counts with complete=false are NOT proof of
  absence. No guard is relaxed to reinterpret them as complete.
- These retirement implementation files are identical between original runtime
  source `d2b1a9792dc10ec801d3ce30c170763f1f2ab301` and inspected replacement
  `f927b3ec24ed943554ebe89230551ed3765d71d1`. Installing the replacement alone
  cannot solve this inventory gap. The original preflight inspected topology,
  not the full signed snapshot; Main missed this earlier. Local synthetic
  operation tests assumed complete inventory and do not establish hosted support.
- Same repair plan/claim and all original resource identities/history remain.
  No runtime stop/image/provenance mutation, allowance renewal, SQL repair,
  duplicate drain command or wallet reset occurs. No customer activation or
  production promotion is claimed. Only this never-activated canary is quiesced;
  existing public demos, ProdUS and Shopify were not stopped by this operation.
- AI-state-only backup is saved before any runtime image change:
  `/private/tmp/01044-attended-runtime-validity-recovery-20261021/AI-state.before-runtime-image.dump`;
  proof `dda916df49b821f0b280f821e13406d9b1cabc1888cb6af82fcefdf0abf63532`.
  Fresh61x5second host baselines complete01:45:46UTC after the latest closed
  rollout. They remain measurements, not admission approval or workload proof.
- Platform - Updates received Main's direct scope verdict, concrete final-product
  description and this blocker. Recommendation: stop expanding generic recovery
  exceptions without a release-boundary decision. A supported staff-assisted
  early-adopter pilot and fully autonomous lifecycle certification are different
  claims. Narrowing the original contract requires explicit agreement; mandatory
  security, ownership, accounting and isolation gates are not silently deferred.
  UI extraction remains last under the existing agreement.
- Resume by addressing supported idle-inventory evidence and checking it BEFORE
  quiescing a fresh operation. Do not repeat consumed helper commands. All current
  read-only reconcile failures, native acknowledgements and plan deadlines must
  remain visible. No new public framework release has been made.
