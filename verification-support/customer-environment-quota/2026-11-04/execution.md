# Customer Environment Allowance And Desk Completion

Status: QUOTA AND DESK LIVE-QUALIFIED; RECORD-DELETION GUARD ROLLOUT NEXT.

## Contract

- Two provisioned environments per customer workspace, normally staging and
  production. Count a deployment/target pair once, including its owned database,
  connector and dependency services. Separate customer workspaces are isolated.
- Drafts and resource-free Engage installations do not use slots. Existing
  environments remain upgradable even if current usage exceeds a lowered limit.
- Starting an apply locks the customer row until its queued release commits.
  Concurrent requests therefore cannot both take the final available slot.
  Direct dependency worker claims use the same lock before recording a worker.
- Resource handles, current releases and dependency state are read in one SQL
  statement/snapshot, avoiding a transition between queued and provisioned state
  disappearing between independent observations.
- Occupancy comes from current release reservations, retained owned resource
  handles and dependency resources/create intents. No parallel slot ledger,
  capacity sampler, drain coordinator or recovery engine was added.
- Stopped, failed, archived or superseded owned resources still count until
  removed. Deleted handles and retired dependency graphs do not. External
  storage bindings and audit-only records are not dedicated infrastructure.
- Record-only deletion is checked both before queueing and again before purging
  metadata. Otherwise deleting a configuration could discard resource handles
  while leaving paid infrastructure running and bypass the allowance. Explicit
  staff-owned infrastructure teardown keeps its existing authorization/cleanup
  contract; the quota guard is not a new force-delete bypass.
- Staff can explicitly change a workspace allowance through an admin-only API
  with an audit reason. A limit of zero prevents new environments, not upgrades.
  No automatic eviction or deletion when a default/limit is lower than usage.
- This product allowance is independent of optional server capacity policy.
  Disabling capacity/recovery operations does not disable the workspace quota.

## Implementation

- V233 adds a default-two, nonnegative customer allowance without deleting or
  changing existing deployment/resource records.
- Shared apply path covers customer, staff, trusted integration and public
  provisioning callers. Dependency claim covers direct dependency operations.
- Owner-scoped GET `/api/customer-self-service/infrastructure-quota`; staff GET /
  PUT `/api/customer-self-service/staff/customers/{customerId}/infrastructure-quota`.
  PUT body: `{"limit": 4, "reason": "Agreed customer allowance"}`.
- Console home/deployment/setup show used/limit and links to the exact counted
  environments. Errors do not replace missing observations with zero usage.
- Desk completion is being reconciled after the durable verification transaction
  commits. Exact owner/tenant/deployment/target/version/release/run checks apply;
  a failed Desk projection must not overwrite the committed release outcome.
  Existing owner status read remains an idempotent repair path.

## Local Evidence

- First focused quota/apply/dependency run:80 passed, zero failures/errors/skips,
  including H2 +real PostgreSQL concurrent last-slot tests, full V232 ->V233
  migration, owner scope and staff-only override checks. Initial test compilation
  errors were fixed before this passing run; no failed attempt relabelled.
- Console production build passed; existing large-bundle warning remains.
- Browser component fixture passed: allowance, exact scoped links, freed slots,
  unavailable observation, customer switch/mismatched scope; no mutations or
  browser errors. API transport tests passed separately.
- Final combined quota +Desk/apply regression:210 passed, zero failures/errors/
  skips, including the final single-snapshot occupancy implementation.
- Separate Desk suite:194 passed, zero failures/errors/skips, including29 exact
  reconciliation cases and seven H2 commit/rollback/repair transaction cases.
- Focused API transport suite:34 passed, zero failed/skipped. A persistent CI
  workflow runs the bounded quota/Desk tests and allowance browser fixture.
- Hosted proof is pending below. No new framework/runtime release is required.

## Hosted Qualification

- Source8d18c87740f117f86905f683a6967858b62faaf7 published and both Platform
  backends/Consoles native source/image/health verified. CI core41930322227,
  focused quota/Desk41930322404 and storage41930322188 all SUCCESS.
- Exact Coolify jobs: staging backend l1375xpp8zjly77yogsaiyof, Console
  p4n21bjtcdyvzxdfq5w4trgd; production backend d5skh4ab67zo5dp3riqbcovq,
  Console pivcvrl8wsbvchp6f0gznl20. V233 applied on both databases.
- Actual Support owner GET returned used2/limit2: one deployment's staging and
  production, not its individual runtime/database/connector containers. Actual
  Console displayed the two exact environment links. Desktop and390x844 checks
  passed; mobile scrollWidth390, no browser errors. Screenshots alongside this
  record. Owner override PUT denied403, anonymous GET401, spoofed customerId
  parameter did not change the authenticated customer scope.
- New test configuration dep-17b01622 was created and published at full usage,
  but applying it returned409 CUSTOMER_ENVIRONMENT_LIMIT_REACHED. No release or
  provider resource was created. It was archived then removed by normal deletion
  del-39c66cd4 SUCCEEDED. No native infrastructure or source file was deleted.
  Early helper attempts used an unsupported GET path and an already-bound
  tenant; both were corrected before the successful bounded proof, not counted
  as product/quota passes.
- Actual customer created an additional DRAFT Engage installation while usage
  was2/2. Usage stayed2; the draft was deleted through the normal installation
  API. No app activation, route creation or production assignment change.
- Actual owner upgraded existing Support staging at2/2 through normal customer
  apply. Release rel-16849364, same version ver-51e6c811/artifact dsa-d3f03019,
  APPLIED_VERIFIED/PASSED. Usage remained2. Runtime job cly6hgn6fag7qiypbzssalfu
  and final connector job drd0cv64sz3lk6yj5ubh1wdk both finished. Both native
  containers are healthy and retain source83fd17427 / AI Fabric0.8.45.
- Read-only observations captured Desk staging binding changing from
  rel-2ab687cf/ENABLED to rel-16849364/APPLYING during apply, then automatically
  rel-16849364/ENABLED after verification. The harness never called the owner
  Desk status endpoint to repair it. Production binding/app remained on
  rel-8822aff7/ENABLED. No status rewrite, SQL mutation or failed-check bypass.
- Final record-deletion bypass correction:223 focused tests passed, zero
  failures/errors/skips, including real PostgreSQL metadata-purge rollback and
  H2/PostgreSQL retained-resource guards. Its backend rollout is pending.

Private canonical journal: loomai-document-storage-01059/receipts/
environment-quota-qualification.private.json. It is the current staging-release
record; the prior promotion journal remains production rel-8822aff7 evidence.

## Bounded Limits

This allowance counts Platform-tracked owned infrastructure, not an external
provider-wide billing inventory. An untracked/out-of-band native resource needs
normal integrator reconciliation; it is not silently counted as absent proof.
After-commit Desk projection retains owner status refresh as repair after a
process interruption; this is not a new durable event/outbox service.
