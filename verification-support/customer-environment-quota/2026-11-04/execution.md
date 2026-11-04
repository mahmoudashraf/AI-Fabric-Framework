# Customer Environment Allowance And Desk Completion

Status: LOCAL VERIFICATION PASSED; PUBLICATION / HOSTED QUALIFICATION NEXT.

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

## Bounded Limits

This allowance counts Platform-tracked owned infrastructure, not an external
provider-wide billing inventory. An untracked/out-of-band native resource needs
normal integrator reconciliation; it is not silently counted as absent proof.
After-commit Desk projection retains owner status refresh as repair after a
process interruption; this is not a new durable event/outbox service.
