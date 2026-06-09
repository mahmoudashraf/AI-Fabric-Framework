# Deployment Export/Import Shift-Left Exercise Guide

Status: execution explanation from the ProdUS production-Coolify staging shift-left exercise  
Audience: platform operators, deployment owners, external customer integration owners, and future implementation agents  
Related guides:

- `Final_Documentation/Development_Guides/DEPLOYMENT_EXPORT_IMPORT_SEALED_BACKUP_RESTORE_GUIDE.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_13_DEPLOYMENT_EXPORT_IMPORT_SEALED_BACKUP_RESTORE_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/010_14_CONSUMER_BOUND_RUNTIME_ASSIGNMENT_AND_DIRECT_PRIVATE_AUTH_PLAN.md`

## Purpose

This document explains the approach used to shift the ProdUS staging LoomAI deployment from the staging Coolify server to a staging runtime hosted on the production Coolify server.

The exercise intentionally used the Platform deployment export/import feature as the source of truth. It did not manually recreate runtime services, Marketplace installs, action config, data plugin config, or secret refs. The goal was to prove that deployment portability works as an operational practice, not just as a code feature.

## What Was Shifted

Source:

- Platform environment: staging
- Source deployment: `dep-7706fafb`
- Source target profile: `dtp-coolify-staging`
- Source runtime pattern: runtime + connector + vectorization runner + managed vector/data configuration + ProdUS MCP action plugins

Target:

- Platform environment: production Platform backend
- Coolify server: production server
- Imported deployment: `dep-131609ee`
- Target profile: `dtp-coolify-production`
- External integration posture: still ProdUS staging, resolved through stable consumer `produs-staging`

The target was not a public production launch. It was a shift-left staging runtime on production infrastructure.

## Core Approach

The approach combines two Platform capabilities:

1. Sealed deployment export/import
2. Stable consumer-bound runtime assignment

The export/import flow recreates the deployment configuration and sealed deployment-scoped secrets. The consumer assignment flow keeps the external customer integration stable even when the concrete deployment id changes.

This separation matters:

- Export/import moves or recreates deployment infrastructure.
- Consumer assignment tells customer backends which runtime is active.
- Customer systems use the stable consumer id as the contract and do not hardcode the deployment id as permanent config.

## Why This Was Not Manual Recreation

Manual recreation would have required separately recreating:

- deployment record,
- draft/version/release state,
- runtime/connector/vectorization service config,
- Marketplace plugin installs,
- action definitions,
- MCP server secret refs,
- data plugin/vector spaces,
- runtime private auth config,
- target profile binding,
- consumer binding.

That is brittle and not a real disaster recovery or migration practice. In this exercise, the deployment bundle was the authoritative artifact. The import created a draft, then the normal publish/apply/release verification path made it live.

## Execution Flow

### 1. Export Source Deployment

The source deployment was exported through the Platform export API using sealed backup mode.

The export bundle included:

- deployment metadata,
- draft/version/release-relevant config,
- curated runtime/module config,
- Marketplace plugin catalog/install data,
- action config,
- data plugin config,
- vectorization config,
- public consumer binding metadata,
- deployment-scoped secret refs,
- sealed secret envelope for exportable deployment secrets.

The export did not include:

- plaintext secrets,
- customer OAuth sessions,
- temporary file URLs,
- shopper/customer sessions,
- chat turns,
- raw private customer data,
- Coolify access tokens,
- operator local credentials.

### 2. Preview Import On Target Platform

The sealed bundle was imported in preview mode on the production Platform backend.

The preview checked:

- schema validity,
- manifest integrity,
- sealed secret readability with the provided private key,
- target import mode,
- target environment compatibility,
- blocking issues,
- required secret actions,
- integration impact.

The preview must be clean before import execution. If it reports blocking issues, the target import should not proceed.

### 3. Import As Draft

The bundle was imported as a sealed clone into production Platform.

The import created:

- a new deployment id,
- a new draft,
- restored deployment config,
- restored exportable deployment-scoped secrets,
- copied Marketplace/action/data configuration.

The import did not make the deployment live. That is intentional. Import creates a draft so publish/apply gates still control runtime mutation.

### 4. Publish And Apply Through Target Profile

The imported draft was validated, published, and applied through the production Coolify target profile.

This created a normal deployment release on the production Coolify server and provisioned:

- runtime service,
- connector service,
- vectorization runner service,
- related runtime env,
- action connector env,
- imported MCP secret refs,
- runtime auth configuration.

### 5. Rebind Stable Consumer

The stable consumer `produs-staging` was bound to the imported deployment.

ProdUS should not hardcode `dep-131609ee` as permanent application config. Instead, ProdUS should call the runtime assignment endpoint from its backend:

```http
GET https://loomai-platform-backend.46.225.162.106.sslip.io/api/public/consumers/produs-staging/runtime-assignment
X-Platform-API-Key: <backend-only-platform-api-key>
```

The assignment response supplies:

- current deployment id,
- runtime base URL,
- query endpoint,
- query-once endpoint,
- suggestions endpoint,
- auth-context endpoint,
- health endpoint,
- private runtime issuer,
- private runtime audience,
- cache TTL,
- readiness flag.

No runtime API key or assertion signing secret is returned by this endpoint.

### 6. Verify Runtime Directly

The runtime was verified through direct private-runtime calls, not through Platform chat proxying.

Verification included:

- runtime health,
- connector health,
- vectorization runner health,
- private-runtime auth context,
- one-time query execution,
- MCP action execution through imported action config.

The query smoke proved that the imported runtime could select a ProdUS read action and call the ProdUS MCP server with restored secret refs.

### 7. Verify Source Staging Was Untouched

The original staging deployment remained active and healthy.

This matters because clone import and target apply must not mutate source staging. The source deployment should still have:

- healthy runtime,
- healthy connector,
- healthy vectorization runner,
- latest release `APPLIED_VERIFIED`,
- verification `PASSED`,
- provisioning `ACTIVE`.

## What Was Proven

The exercise proved:

- sealed export can capture the deployment configuration and exportable deployment secrets,
- production Platform can preview and import the sealed bundle,
- imported draft can be validated, published, and applied through a different target profile,
- runtime, connector, and vectorization runner can be provisioned on production Coolify infrastructure,
- stable consumer assignment can point `produs-staging` to the newly imported deployment,
- private runtime assertions can use the stable consumer audience `produs-staging`,
- direct runtime traffic does not need Platform in the hot path,
- imported MCP secret refs can be used by runtime action execution,
- source staging remains untouched.

## What Was Not Fully Proven

The exercise did not prove every possible deployment surface.

Not fully proven:

- durable conversation persistence on the imported production-server staging deployment, because the runtime database sidecar summary still reported `exited:unhealthy`,
- long-running production traffic behavior,
- rollback/deactivation of the imported deployment,
- public Shopify launch readiness,
- customer OAuth/session continuity, because those are intentionally not exported,
- provider-level cost or rate-limit behavior under load.

These are separate gates, not failures of the export/import mechanism.

## Is This A Generic Approach?

Yes, this should be the generic approach for Platform-managed deployments, with caveats.

It is generic when a deployment is built from Platform-managed configuration:

- deployment templates,
- curated modules,
- Marketplace plugins,
- action manifests,
- data plugins,
- vectorization config,
- runtime auth config,
- deployment-scoped secrets,
- target profile provisioning.

It is not enough by itself when a deployment depends on state that is intentionally outside deployment export:

- customer OAuth sessions,
- shopper sessions,
- checkout/cart session state,
- chat history,
- tenant-private business records,
- raw scanner logs,
- private object storage files,
- temporary signed URLs,
- external provider-side state that cannot be recreated from config.

For those areas, the deployment export/import bundle should be paired with a separate data migration, session reauthorization plan, or customer-owned data export/import path.

## Standard Reuse Pattern

Use this pattern for future deployment moves:

1. Confirm the source deployment is healthy.
2. Generate a recipient public/private keypair for sealed export.
3. Export source deployment as sealed backup.
4. Preview import on target Platform/environment.
5. Resolve preview warnings before importing.
6. Import as draft.
7. Validate draft.
8. Publish version.
9. Apply version through target profile.
10. Wait for release verification to pass.
11. Rebind stable consumer to imported deployment.
12. Verify assignment returns `externalIntegrationReady=true`.
13. Run direct runtime auth-context smoke.
14. Run direct runtime query/query-once smoke.
15. Verify expected action/data/plugin behavior.
16. Verify source deployment remains untouched.
17. Update customer handover with non-secret assignment details.

## Required Platform Capabilities

The approach relies on these Platform capabilities being present and healthy:

- sealed export with secret classification,
- import preview,
- sealed clone import,
- draft validation,
- publish draft,
- apply release through target profile,
- release verification,
- Marketplace plugin rehydration,
- MCP secret ref propagation into runtime provisioning,
- stable consumer assignment,
- runtime assignment endpoint,
- direct private runtime assertion auth.

If any of these are missing, the shift should stop before target apply.

## Required Customer Integration Pattern

Customer backends should use this pattern:

- Store the Platform assignment key backend-only.
- Fetch assignment at startup.
- Cache assignment for the returned TTL.
- Sign private runtime assertions with the returned stable audience.
- Call runtime directly for chat/query traffic.
- Refresh assignment only when TTL expires, assignment revision changes, health fails, or direct runtime calls repeatedly fail at connection level.

Customer browser code should not receive:

- Platform API keys,
- runtime API keys,
- runtime assertion signing secrets,
- MCP keys,
- provider keys,
- Coolify tokens.

## Stable Consumer Audience Rule

The private runtime assertion audience should be the stable consumer id, not the concrete deployment id.

For ProdUS:

```text
aud = produs-staging
deploymentId = dep-131609ee
```

The audience proves the caller is authorized for the stable customer integration. The deployment id is audit/debug metadata resolved from current assignment.

This lets Platform rotate or restore deployments without forcing the customer backend to change its assertion audience.

## When To Use Restore In Place Instead

Use clone import plus consumer rebind when:

- moving from staging server to production server,
- testing a migrated runtime,
- validating production infrastructure without destroying the source,
- creating a parallel staging copy.

Use restore-in-place when:

- the deployment id and runtime route must be preserved,
- the old deployment is broken but still logically the same customer runtime,
- the customer integration cannot tolerate a deployment id change,
- the source environment is the same as the target environment.

Even with restore-in-place, publish/apply/release verification must still run.

## Security Properties

The exercise preserved these security properties:

- no plaintext secret values in the bundle manifest,
- no secrets printed in handover docs,
- no secrets returned by runtime assignment,
- no browser exposure of runtime or Platform credentials,
- imported deployment secret restoration happened server-side,
- runtime traffic used private backend-mediated signed assertions,
- source deployment was not mutated during clone import.

## Operational Caveats

Keep these caveats visible for future runs:

- A passing import preview is necessary but not sufficient; live runtime smoke is still required.
- A healthy runtime is not enough if action plugins need MCP secret refs; run an action smoke.
- A healthy query smoke is not enough if durable chat history matters; verify runtime database sidecar and conversation APIs.
- Consumer assignment should be updated only after target runtime verification passes.
- Source deployment should be checked after target apply to prove isolation.
- Exported sealed bundles and private decrypt keys are sensitive operational artifacts and should not be committed.

## Recommended Decision

Use this as the standard generic approach for future Platform-managed deployment shift-left, migration, backup restore, and production-infrastructure staging exercises.

The pattern is reusable because it keeps the control plane, deployment artifact, runtime data plane, and external customer contract separate:

- Platform controls deployment state and assignment.
- Export/import carries deployment config and sealed deployment-scoped secrets.
- Runtime handles direct customer traffic.
- Stable consumer assignment protects external systems from deployment id churn.

For each new deployment, verify the deployment-specific surfaces separately: data sources, action plugins, external MCP auth, vectorization runners, persistent storage, and any customer/session state intentionally excluded from export.
