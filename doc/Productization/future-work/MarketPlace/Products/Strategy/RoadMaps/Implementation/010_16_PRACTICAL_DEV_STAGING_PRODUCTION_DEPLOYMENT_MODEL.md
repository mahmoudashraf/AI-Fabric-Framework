# 010.16 - Practical Dev, Staging, And Production Deployment Model

Status: practical model implemented in Platform control-plane code on 2026-06-03. No live deployment, promotion, import, export, provider delete, or Coolify operation was executed while implementing this document.

Implementation record:

- Added release-bound consumer assignment fields so a consumer can resolve a verified release/target profile instead of the deployment's latest active runtime.
- Removed the one-consumer-per-deployment database uniqueness assumption so `customer-staging` and `customer-production` can share a deployment lineage while pointing at different releases.
- Added `dtp-coolify-prod-staging` as an active, non-default Coolify target profile that uses the production Coolify credential with customer project grouping and `staging` environment intent.
- Added practical promotion API endpoints under `/api/deployments/{deploymentId}/practical-promotion/*` for plan, production apply request, production consumer activation, assignment rollback, and orphan-resource scan/mark.
- Added provider resource lifecycle marking for `ACTIVE`, `SUPERSEDED`, `ROLLBACK_RESERVED`, `FAILED_DIAGNOSTIC_HOLD`, `ORPHANED`, `RETIRED`, and `DELETED` without performing provider stop/delete actions.
- Updated public consumer runtime assignment and bridge chat resolution to use the bound release runtime URL when a consumer is release-bound.
- Updated Platform UI API types, target profile labels, and customer consumer display so operators can see environment intent and release/profile bindings.
- Local verification passed: backend compile, focused promotion/public assignment tests, frontend build, and `git diff --check`.

Related documents:

- `010_12_PRODUCTION_DEPLOYMENT_EXECUTION_PLAN.md`
- `010_13_DEPLOYMENT_EXPORT_IMPORT_SEALED_BACKUP_RESTORE_PLAN.md`
- `010_14_CONSUMER_BOUND_RUNTIME_ASSIGNMENT_AND_DIRECT_PRIVATE_AUTH_PLAN.md`
- `010_15_CLONE_BASED_PRODUCTION_PROMOTION_AND_ASSIGNMENT_PLAN.md`

## Practical Direction

The simpler operating model should be:

```text
dev/demo       -> light, cheap, disposable
staging        -> real customer configuration, production-like enough to trust
production     -> promoted verified staging version, durable and production-owned
export/import  -> backup, disaster recovery, host migration, restore
```

The normal customer path should not depend on deployment export/import.

Export/import remains important, but it should not be the default "Go production" path unless the product is intentionally recreating a deployment on a different Platform/Coolify host.

## Validating The Current Thinking

### Use Staging Coolify As Dev/Demo Server

This is a good approach.

The staging Coolify server can continue to host:

- internal development runtime deployments;
- lightweight customer demo deployments;
- temporary proof-of-concept deployments;
- branch/release based previews;
- low-cost runtime/database/vector combinations.

This keeps active framework/runtime development separate from real customer staging and production deployments.

Dev/demo customer deployments do not need to run on the production server. They should only move there when the customer is doing real implementation work or a production-equivalent staging proof.

### Separate Runtime/Fabric Development From Customer Dev/Demo

This is also correct.

Runtime/framework development should use:

- local tests;
- local runtime if needed;
- disposable staging Coolify deployments;
- branch-targeted dev/demo deployments.

Customer dev/demo deployments should be treated as product previews, not framework release environments. They can use released branches or known-good runtime artifacts even while framework development continues elsewhere.

### Production Coolify Server With Staging And Production Environments

This is the right shape for real customer work.

The production Coolify server can have:

```text
Coolify production server
  staging environment
  production environment
```

The Platform should model these as separate target profiles, for example:

```text
dtp-coolify-dev-demo            -> staging Coolify server, light defaults
dtp-coolify-prod-staging        -> production Coolify server, staging environment
dtp-coolify-production          -> production Coolify server, production environment
```

Current known Platform posture already supports the idea that target profiles control placement and runtime defaults. `dtp-coolify-staging` keeps the runtime Docker default H2 file database, and `dtp-coolify-production` has production runtime Postgres defaults.

If `dtp-coolify-prod-staging` does not already exist, it should be added as an operator-managed target profile. That is an operational configuration step, not a runtime code feature.

### Customer Staging Deployment In Platform UI

This is the target customer/operator workflow:

1. Customer/operator creates a staging deployment in Platform UI.
2. The deployment uses a real customer staging target profile.
3. Platform provisions the runtime, connector, vectorization runner, secrets, and action configuration on the production Coolify server's staging environment.
4. Customer/operator tests the deployment with real configuration.
5. Verification evidence is recorded.
6. The same verified deployment version is promoted to production.

This avoids duplicating configuration manually.

### Promotion Should Not Be "Change The Coolify Env" In-Place

The wording "change the env to production" is directionally right, but technically it should be more precise.

Do not mutate the same Coolify app from `staging` environment to `production` environment in-place.

Preferred behavior:

```text
same Platform deployment
same verified deployment version
new release apply
different target profile
production Coolify environment
```

So the production promotion is:

```text
apply verified version to dtp-coolify-production
```

not:

```text
move existing Coolify staging app into production
```

This matters because in-place environment mutation can create unsafe state:

- stale env vars;
- confused DNS/route bindings;
- unclear rollback;
- mixed staging and production resource handles;
- hard-to-audit provider state.

The current provider model stores `targetProfileId` on releases and provider resource handles, which is the right foundation for staging and production resources to coexist under the same Platform deployment lineage.

## Recommended Environment Model

### Dev/Demo Profile

Purpose:

- fast demos;
- product exploration;
- internal development;
- disposable customer previews.

Suggested defaults:

- staging Coolify server;
- H2/file runtime chat database;
- local/simple vector where available, or shared low-cost vector;
- relaxed retention;
- no public production claims;
- no real customer production secrets;
- can track branch or released artifact depending on use case.

Example target profile:

```text
dtp-coolify-dev-demo
```

If we keep using the existing staging target profile for this purpose, its role should be documented clearly.

### Customer Staging Profile

Purpose:

- real customer implementation;
- real customer configuration;
- pre-production verification;
- production-equivalent behavior without production activation.

Suggested defaults:

- production Coolify server, `staging` environment;
- real secrets, but staging scoped where external systems support it;
- real MCP/action configuration;
- real vectorization runner;
- vector backend that matches production quality requirements;
- runtime database can be H2 if conversation durability is not being tested, but Postgres is recommended when chat history/session behavior is part of acceptance.

Example target profile:

```text
dtp-coolify-prod-staging
```

This profile should be explicit. It should not be confused with the staging server's dev/demo profile.

### Production Profile

Purpose:

- live customer production traffic;
- durable chat/session state;
- production secrets and routes;
- production monitoring and rollback.

Suggested defaults:

- production Coolify server, `production` environment;
- runtime Postgres;
- production vector backend;
- production domains/TLS;
- production-only secrets;
- production verification and rollback gates.

Existing target profile:

```text
dtp-coolify-production
```

## Normal Promotion Flow

Normal promotion should be release/profile based:

```text
customer staging deployment
  -> verified deployment version
  -> apply same version to production target profile
  -> verify production release
  -> activate/switch production consumer assignment
```

Detailed sequence:

1. Customer/operator completes staging configuration.
2. Platform publishes a deployment version.
3. Staging release applies to `dtp-coolify-prod-staging`.
4. Staging verification passes.
5. Operator/customer requests production promotion.
6. Platform applies the same version to `dtp-coolify-production`.
7. Production provisioning creates or updates production environment resources.
8. Production verification passes.
9. Platform updates the production consumer assignment.
10. External customer continues using runtime assignment lookup, not a hardcoded deployment URL.

Promotion must not leave unused runtime resources running forever.

After production activation, Platform should run a post-activation retention policy:

```text
activate production consumer
  -> keep previous production runtime for rollback window
  -> stop or mark superseded staging runtime if no longer needed
  -> delete retired resources after retention expires
```

The exact cleanup action depends on environment type and risk:

- dev/demo resources: short TTL, stop/delete aggressively.
- customer staging resources: stop after production activation when the customer no longer needs staging, then delete after a configured retention window.
- previous production resources: keep running or warm for rollback during a short rollback window, then stop/delete after rollback proof and owner approval.
- failed promotion resources: mark as failed/orphaned and keep only long enough for diagnostics, then clean up.

## Consumer Assignment Model

Consumers should represent environment intent:

```text
customer-dev-demo     -> optional preview deployment
customer-staging      -> customer staging deployment/release
customer-production   -> production deployment/release
```

The external application should not hardcode a deployment id as its long-term dependency.

It should call:

```http
GET /api/public/consumers/{consumerId}/runtime-assignment
```

Then cache the returned runtime URL until the assignment TTL expires or runtime health fails.

This keeps Platform out of every chat request while still allowing Platform to move the assigned runtime when needed.

## Where Export/Import Fits

Export/import should be treated as a backup and recovery mechanism.

Use export/import for:

- disaster recovery;
- restoring a deleted or corrupted deployment;
- moving a deployment to another Platform/Coolify host;
- cloning a customer deployment for a sandbox;
- preserving a production backup before a risky release.

Do not use export/import as the normal production promotion path when the same Platform deployment and version can be applied to a production target profile.

`010.15` remains useful as an emergency or migration-heavy option, but it should not replace the simpler mainline promotion model.

## Resource Retirement And Orphan Prevention

Production servers must not accumulate unused "ghost" deployments.

Every release/apply that creates provider resources must have a lifecycle state and a cleanup decision.

Suggested provider resource lifecycle states:

```text
ACTIVE
SUPERSEDED
ROLLBACK_RESERVED
FAILED_DIAGNOSTIC_HOLD
ORPHANED
RETIRED
DELETED
```

### After Successful Production Activation

When a new production release is verified and the production consumer assignment is switched:

1. Mark the newly assigned production resources `ACTIVE`.
2. Mark the previous production resources `ROLLBACK_RESERVED`.
3. Start a rollback retention window.
4. Mark the staging resources `SUPERSEDED` if the customer no longer needs the staging environment.
5. Stop staging resources after explicit policy or operator/customer approval.
6. Delete staging resources after the retention window expires.
7. Delete previous production resources after rollback window expires and release owner confirms the new production release is stable.

### Staging Cleanup Policy

Customer staging should not be deleted immediately by default because it can still be useful for:

- reproduction of production issues;
- testing the next configuration change;
- rollback comparison;
- support investigation.

Recommended default:

```text
production activation complete
  -> mark customer staging SUPERSEDED
  -> stop after 24-72 hours if no active staging work remains
  -> delete after 7-14 days or according to customer package policy
```

For dev/demo deployments:

```text
inactive for TTL
  -> stop
  -> delete
```

### Previous Production Cleanup Policy

Previous production should be treated more carefully than staging.

Recommended default:

```text
new production activation complete
  -> previous production ROLLBACK_RESERVED
  -> keep warm for immediate rollback window
  -> stop after rollback window if stable
  -> delete after backup/export and owner approval
```

### Failed Promotion Cleanup Policy

Failed production resources should not be silently deleted before diagnostics are captured.

Recommended default:

```text
promotion failed
  -> mark failed target resources FAILED_DIAGNOSTIC_HOLD
  -> keep staging and current production assignments unchanged
  -> capture diagnostics
  -> stop failed resources
  -> delete after operator cleanup approval
```

### Orphan Detection

Platform should have an orphan detector that compares:

- deployment releases;
- provider resource handles;
- consumer assignments;
- Coolify live applications/databases;
- target profile environment.

A resource is orphaned when it exists in the provider but is not:

- assigned to an active consumer;
- tied to an active or rollback-reserved release;
- under a diagnostic hold;
- under an explicit retention policy.

Operator UI should show:

- orphaned resource count;
- target profile;
- deployment id;
- resource kind;
- last seen health;
- proposed cleanup action.

Cleanup should be explicit and audited.

## What We Already Support

The current Platform already supports major parts of this model:

- target-profile based release apply;
- non-default explicit production target profile;
- staging default target profile;
- target-profile id stored on release and provider resource handles;
- production runtime Postgres defaults on `dtp-coolify-production`;
- H2/file runtime DB default for lighter staging previews;
- stable consumer runtime assignment endpoint;
- sealed export/import for backup/restore/migration.

## What We Need To Confirm Or Add

### 1. Production-Server Staging Target Profile

Confirm whether this exists:

```text
dtp-coolify-prod-staging
```

If not, add it with:

- production Coolify server connection;
- Coolify `staging` environment;
- customer runtime deployments allowed;
- customer managed product services allowed as needed;
- resource defaults suitable for real staging;
- clear name that distinguishes it from the staging Coolify server.

### 2. Platform UI Target Profile Selection

Customer/operator deployment creation should make the environment clear:

```text
Dev/demo
Customer staging
Production
```

Operators can see target profile details. Customers should see environment intent, not provider internals.

### 3. Promotion Uses Same Version

Promotion should require selecting or deriving a verified version from staging and applying that exact version to production.

This avoids rebuilding from a different branch or stale artifact.

### 4. Environment-Specific Assignment

Promotion should update only the production consumer assignment after production verification passes.

It should not disturb:

- dev/demo assignment;
- staging assignment;
- previous production assignment before activation.

### 5. Rollback

Rollback should prefer assignment-level rollback:

1. production consumer assignment returns to previous known-good runtime;
2. production deployment release rollback/reapply can follow if needed;
3. staging remains untouched.

### 6. Docs Need To Demote Export/Import From Mainline Promotion

`010.15` should be treated as an alternate clone/DR-oriented approach, not the normal path.

This document should be considered the preferred practical model for day-to-day customer lifecycle.

### 7. Post-Activation Cleanup And Orphan Management

Add or verify support for:

- resource status transitions from `ACTIVE` to `SUPERSEDED`, `ROLLBACK_RESERVED`, `ORPHANED`, `RETIRED`, and `DELETED`;
- configurable retention windows by target profile and customer package;
- operator/customer approval rules for stopping staging after production activation;
- previous-production rollback reservation;
- failed-promotion diagnostic hold;
- provider resource orphan scan;
- audited stop/delete actions.

This prevents the production Coolify server from accumulating unused customer staging and failed promotion resources.

## Recommended Naming

Target profiles:

```text
dtp-coolify-dev-demo
dtp-coolify-prod-staging
dtp-coolify-production
```

Consumer ids:

```text
{customer}-dev-demo
{customer}-staging
{customer}-production
```

Deployment names:

```text
{Customer} Dev Demo Runtime
{Customer} Staging Runtime
{Customer} Production Runtime
```

Coolify environments:

```text
staging
production
```

## Practical Answer To The Proposed Flow

Proposed user flow:

> Customer/operator creates staging deployments in Platform UI. It creates staging environment deployment on Coolify production server. Then operator/customer promotes the same staged deployment to production, which is just changing the env to production.

Recommended corrected version:

> Customer/operator creates a staging deployment in Platform UI. It provisions on the production Coolify server's staging environment through a customer-staging target profile. Then operator/customer promotes the same verified Platform deployment version to production by applying it through the production target profile. Platform provisions production environment resources, verifies them, and switches the production consumer assignment after verification.

That model is clean, practical, and avoids unnecessary duplication.

After activation, Platform must also retire resources:

> The new production runtime becomes active. The old production runtime is kept only for rollback retention. The staging runtime is marked `SUPERSEDED` and stopped/deleted according to customer policy if no staging work remains. Failed or abandoned resources are marked `ORPHANED` or `FAILED_DIAGNOSTIC_HOLD` and cleaned up through audited operator action.

## Decision

Use this practical model as the preferred direction:

- staging Coolify server: dev/demo and internal preview;
- production Coolify server staging environment: real customer staging;
- production Coolify server production environment: live production;
- normal promotion: same verified Platform deployment version applied to production target profile;
- export/import: backup, restore, clone, migration, disaster recovery.
- no indefinite ghost deployments: staging, previous production, and failed resources must have explicit retention, stop, delete, or orphan status.

## 2026-06-03 Live Proof Status

This model was implemented and proved live on production Platform/Coolify with a disposable deployment.

Implemented control-plane support:

- release-bound consumer assignment with `bound_release_id` and `bound_target_profile_id`;
- `dtp-coolify-prod-staging` customer-staging target profile;
- practical promotion endpoints for plan, production apply, production consumer activation, rollback, and orphan scan;
- provider resource lifecycle marking endpoint;
- public consumer/runtime assignment resolution by verified bound release.

Live proof evidence:

- disposable deployment: `dep-0f3d99cc`;
- published version: `ver-4d6e7b92`;
- customer-staging release: `rel-54d2b3de`, final `APPLIED_VERIFIED` / `PASSED`;
- production release: `rel-6696c852`, final `APPLIED_VERIFIED` / `PASSED`;
- temporary consumer: `codex-practical-e2e-20260603003939`;
- production assignment runtime: `http://dep-0f3d99cc.46.225.162.106.sslip.io`;
- staging resources were deleted through provider resource DELETE and lifecycle-marked `DELETED`;
- disposable production resources were also deleted after proof;
- hard-delete cleanup operation `del-2beb29fd` completed `SUCCEEDED`.

Execution caveats discovered and corrected:

- production Platform reaches production Coolify internally through `http://coolify:8080`; customer-staging must use that same internal base URL from the production profile, not the external public IP;
- `runtime-staging.loomai.pro` DNS is not delegated/resolving yet, so customer-staging currently uses temporary `46.225.162.106.sslip.io` domains until DNS is ready;
- live Coolify is `4.1.1` while the target profile pin remains `4.0.0`, so Platform preflight currently returns `WARNING` instead of `PASSED` even though credentials/network are usable.

## Open Questions

1. Should customer staging on the production Coolify server use H2 or Postgres by default?
   - Recommendation: H2 for short-lived demos, Postgres for real customer staging when conversation/session continuity matters.
2. Should `dtp-coolify-prod-staging` be created now?
   - Recommendation: yes, if it does not already exist.
3. Should customers see target profiles?
   - Recommendation: no. Show environment intent. Keep provider details operator-only.
4. Should promotion automatically activate production assignment?
   - Recommendation: no for first production launches. Verify first, then explicit activate.
5. Should export/import be removed from promotion docs?
   - Recommendation: no. Keep it documented as backup/DR and migration, but mark it as non-mainline for normal promotion.
6. How long should customer staging remain after production activation?
   - Recommendation: package-based retention, with a default stop after 24-72 hours and delete after 7-14 days if no active staging work remains.
7. How long should previous production remain rollback-reserved?
   - Recommendation: short rollback window for design partners, longer for public production customers, always backed by an export/backup before deletion.
