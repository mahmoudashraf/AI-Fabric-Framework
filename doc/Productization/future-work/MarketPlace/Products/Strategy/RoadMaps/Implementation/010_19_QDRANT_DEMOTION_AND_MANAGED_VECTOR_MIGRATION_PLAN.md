# 010.19 Qdrant Demotion And Managed Vector Migration Plan

Status: executed 2026-06-28 for the ProdUS managed-vector cutover and Platform release-gate Qdrant demotion. Qdrant support remains available as an explicit advanced provider, and the old ProdUS Qdrant deployment remains rollback-reserved until soak/cleanup approval.

Related plans:

- [010.13 Deployment Export, Import, Sealed Backup, And Restore Plan](010_13_DEPLOYMENT_EXPORT_IMPORT_SEALED_BACKUP_RESTORE_PLAN.md)
- [010.15 Clone-Based Production Promotion And Assignment Plan](010_15_CLONE_BASED_PRODUCTION_PROMOTION_AND_ASSIGNMENT_PLAN.md)
- [010.16 Practical Dev, Staging, And Production Deployment Model](010_16_PRACTICAL_DEV_STAGING_PRODUCTION_DEPLOYMENT_MODEL.md)
- [010.17 Grounding-Eligible Read Action Post-Action Generation And LLM Facts Plan](010_17_GROUNDING_ELIGIBLE_READ_ACTION_POST_ACTION_GENERATION_AND_LLM_FACTS_PLAN.md)
- [010.18 Shopify Companion Production Release And App Listing Readiness Plan](010_18_SHOPIFY_COMPANION_PRODUCTION_RELEASE_AND_APP_LISTING_READINESS_PLAN.md)

## Purpose

Demote Qdrant from the default and release-blocking vector provider, then migrate existing Qdrant-backed deployments to another managed vector option when needed.

The immediate production target is the ProdUS staging runtime currently assigned through Platform consumer `produs-staging`. The migration must preserve a working rollback path, must not expose secrets, and must prove retrieval quality before changing any live assignment.

## Execution Update - 2026-06-28

Non-secret execution result:

- Platform release-gate demotion was implemented, committed, pushed, and deployed to production Platform backend.
- Code commit: `34af74717` (`Demote Qdrant from release-blocking vector checks`).
- Local verification passed:
  - `mvn -f Platfrom/backend/pom.xml -Dtest=PlatformVerificationSuiteScriptContextServiceTest,PlatformVerificationSuiteExecutionServiceTest test`
  - `bash -n scripts/verify-managed-vector-providers.sh`
- Production Platform backend deploy request completed through Platform core-service operations; `loomai-platform-backend` returned `RUNNING_HEALTHY` and `https://api.loomai.pro/actuator/health` returned `UP`.
- Live verification suite definitions now keep `qdrant-hosted-verification` optional/non-blocking. The full/canonical release definitions keep marketplace and ecommerce hosted checks blocking, and managed-vector provider verification is the blocking managed-vector gate.
- ProdUS pre-migration backup completed for deployment `dep-53f9ca56`:
  - config-only export: `dexp-cadcc013`, bundle `dxb-7a4c6b46`
  - config bundle hash: `sha256:SE_r4Lv2udWdBVJeRuMiUhaKbQ32sH1Yp9cprIgwj9k`
  - config manifest hash: `sha256:IcQVXb3FkWI0ZpanFM86FF2F6bU-DYW1FJgbnl0isJ0`
  - sealed backup export: `dexp-a16daf03`, bundle `dxb-59417c44`
  - sealed bundle hash: `sha256:8FebUO3jmxMe6qCr7sYvy3cGa4kK-bqNdlDU6ifw1oc`
  - sealed manifest hash: `sha256:nrOURA9MayLSDuTVxjhS_k40RwHz9v8ht3WMjz6rh4s`
  - sealed envelope hash: `sha256:0FkiF7-4_dEUbY3NeCcfmbuYosAHR5Gm8qAIF3jxvYY`
  - config-only and sealed import previews both passed.
- Baseline before cutover was green on `dep-53f9ca56` / `rel-4ed2ffc9` / `ver-908e3888`; service-module smoke returned `service-module:api-security-review`, and package-template smoke returned `package-template:security-hardening`.
- Pinecone connectivity reached `READY`, but apply/provisioning failed while creating the managed index with an upstream HTTP `400`. The migration then used the allowed fallback provider: Zilliz/Milvus.
- New ProdUS managed-vector deployment: `dep-f6abfa06`.
- New deployment version: `ver-269b9769`.
- Managed vector provider: Zilliz/Milvus, `PLATFORM_MANAGED`, dedicated storage, serverless project `proj-a58a34b87ccfe2c80d6ec2`.
- Production-staging target release: `rel-8d8f12fc`, status `APPLIED_VERIFIED`, verification `PASSED`, provisioning `ACTIVE`.
- Vectorization bootstrap run: `vrn-f459d3ff`, `processed=198`, `succeeded=198`, `failed=0`, overview `IN_SYNC`.
- Production target release: `rel-86dbe0ab`, status `APPLIED_VERIFIED`, verification `PASSED`, provisioning `ACTIVE`.
- First production apply `rel-0891a940` failed because the generated production PostgreSQL service was `exited:unhealthy` and its internal hostname could not resolve. The database service was started through Coolify, then the retry passed as `rel-86dbe0ab`.
- Production post-cutover vector counts were nonzero across all expected ProdUS spaces, including `service-module=90`, `package-template=15`, `service-category=10`, `service-dependency=23`, `milestone-template=15`, `case-pattern=15`, `ai-capability-contract=7`, `scanner-tool-description=18`, `team-profile=1`, and `solo-expert-profile=1`.
- Production service-module smoke passed with provider request id `rag-8c8b788b-2aac-409b-a2b4-bfdcf00c5b3b`, `sourcesCount=5`, top source `service-module:api-security-review`.
- Production package-template smoke passed with provider request id `rag-156ae1be-96ca-4e73-ac73-0d76fe9bd8bf`, `sourcesCount=3`, top source `package-template:security-hardening`.
- Consumer `produs-staging` is now bound to deployment `dep-f6abfa06`, release `rel-86dbe0ab`, target profile `dtp-coolify-production`.
- Assignment discovery through the existing backend-only handoff URL returns `deploymentId=dep-f6abfa06`, `externalIntegrationReady=true`, and `cacheTtlSeconds=300`.
- Old Qdrant-backed deployment `dep-53f9ca56` / `rel-4ed2ffc9` was not deleted. Keep it rollback-reserved until the soak window completes and the owner explicitly approves cleanup.
- Temporary Hetzner firewall access for local IP `38.126.93.124/32` was removed from the production Coolify firewall objects after deployment work.

Residual caveats:

- Runtime aggregate `/actuator/health` for `dep-f6abfa06` reports readiness `DOWN` / HTTP `503`, while runtime, connector, and runner liveness endpoints are `UP`; Coolify deployment gates use liveness and passed. Treat aggregate readiness as cleanup, not a failed cutover.
- The public consumer bridge smoke returns `Thinker is disabled for this deployment`; ProdUS should use the backend-mediated private runtime assignment flow, not the public bridge path.
- The custom-domain assignment URL at `https://api.loomai.pro/.../runtime-assignment` returned `401` with the current assignment key, while the existing handoff URL under `https://loomai-platform-backend.46.225.162.106.sslip.io/.../runtime-assignment` worked. If ProdUS wants to move assignment discovery to `api.loomai.pro`, rotate/recheck the scoped assignment key and host/security path separately.
- A direct authenticated ProdUS backend owner-session smoke was not available from this shell. Platform assignment, runtime, vectorization, and POC runtime smokes passed.
- Shopify package/profile default demotion was not changed in this execution; keep that as a separate product-default review.

2026-06-29 update: the aggregate runtime health and custom-domain assignment caveats are resolved. Runtime aggregate `/actuator/health` now returns HTTP `200` / `UP`, and both the sslip handoff URL and `https://api.loomai.pro/api/public/consumers/produs-staging/runtime-assignment` return HTTP `200` with the same scoped assignment key. The public consumer bridge note remains informational only; ProdUS should keep using backend-mediated private runtime assignment discovery and signed assertions.

## Core Decision

Qdrant should remain supported in code, but it should no longer be the default dependency for new deployments or the provider that blocks a general Platform release gate.

New default posture:

```text
dev/demo/simple runtime       -> lucene/local vector unless a managed provider is explicitly requested
production managed vector     -> pinecone first, then weaviate or zilliz/milvus if Pinecone is not ready
qdrant                        -> optional advanced provider, not default, not release-blocking
existing qdrant deployments   -> migrate deliberately with backup, clone/import, reindex, smoke, and rollback
```

## Required AI Fabric Release Upgrade Step

This migration must explicitly include the AI Fabric release upgrade on both Coolify environments before any vector backend cutover is accepted.

Required release posture:

- Public framework release: `ai-fabric-framework-v0.3.1` or newer.
- Private runtime/connector/vectorization product services consume `io.github.loom-ai-labs:ai-fabric-bom:0.3.1` or newer.
- Staging Coolify Platform/runtime services are redeployed or explicitly verified against the new release.
- Production Coolify Platform/runtime services are redeployed or explicitly verified against the new release.
- Release evidence records the staging release id, production release id, source commit, target profile, and post-deploy verification result.

Current production ProdUS evidence already recorded before this plan:

- active ProdUS deployment: `dep-53f9ca56`
- production release after 0.3.1 pin: `rel-4ed2ffc9`
- target profile: `dtp-coolify-production`
- status: `APPLIED_VERIFIED`
- direct health: runtime, connector, and vectorization runner returned HTTP `200` / `UP`
- service-module smoke returned top source `service-module:api-security-review`

Before executing the migration, repeat or confirm equivalent evidence on staging Coolify as well. Do not treat production-only proof as satisfying the staging release upgrade requirement.

## Provider Selection

Primary target: Pinecone managed vector storage.

Why Pinecone first:

- it is already represented in Platform verification and vector-provider support;
- it avoids the current Qdrant Cloud billing/control-plane dependency;
- a serverless index is a clear managed vector resource;
- it is a reasonable production managed option for customer deployments.

Fallback order:

1. Weaviate Cloud, if Pinecone provider connectivity or provisioning is blocked.
2. Zilliz/Milvus, if both Pinecone and Weaviate are blocked.

Do not choose a provider by preference alone. Choose the first provider whose Platform provider-connectivity check, provisioning mode, dimensions, namespace/isolation behavior, and hosted retrieval smoke all pass.

## Scope

In scope:

- demote Qdrant from default templates, package/profile recommendations, and general release-gate blocking;
- keep Qdrant available as an explicit advanced option;
- migrate ProdUS away from Qdrant using export/import or clone/restore flows;
- create a ProdUS Platform deployment config backup before any live ProdUS migration work;
- reindex ProdUS safe knowledge into the selected non-Qdrant managed vector backend;
- prove runtime retrieval returns service-module and package-template sources;
- preserve rollback to the current Qdrant-backed deployment/release until soak is complete.

Out of scope:

- deleting Qdrant support code;
- weakening tenant/shared-index filtering to make migration easier;
- hand-editing Coolify env as the primary migration mechanism;
- moving consumer secrets into frontend/browser config;
- deleting current Qdrant collections before a successful post-cutover soak.

## Current ProdUS Baseline

Known active state before migration:

- Platform consumer: `produs-staging`
- active deployment: `dep-53f9ca56`
- current green release: `rel-4ed2ffc9`
- current version: `ver-908e3888`
- target profile: `dtp-coolify-production`
- current vector strategy: Qdrant shared/external-existing
- expected ProdUS safe records: `190`
- expected indexed total with seed defaults: `195`
- must-have smoke: `API security review` returns `service-module:api-security-review`

Baseline proof to capture immediately before migration:

1. Platform source-of-truth for `dep-53f9ca56`.
2. Active consumer assignment for `produs-staging`.
3. Runtime, connector, and vectorization runner health.
4. Runtime admin overview with supported entity types.
5. Index counts by entity type.
6. Retrieval smoke for `API security review` with `service-module` hints.
7. Retrieval smoke for a package-template/security hardening query.

## Execution Plan

### Phase 0: Release And Access Readiness

Status: completed for production Platform backend and the ProdUS production/prod-staging target profiles. A separate Hetzner staging Coolify environment was not revalidated in this execution.

Steps:

1. Confirm Platform backend, Platform UI, Partner UI, runtime, connector, and vectorization runner build paths consume AI Fabric `0.3.1` or newer.
2. Deploy or confirm the new AI Fabric release on staging Coolify.
3. Deploy or confirm the new AI Fabric release on production Coolify.
4. Confirm Platform admin access, production target profile preflight, and release-apply permissions are available.
5. Confirm provider credentials for Pinecone are present only in Platform secrets or local private secret files.
6. Run provider-connectivity checks for Pinecone.
7. If Pinecone fails, run equivalent checks for Weaviate, then Zilliz/Milvus.

Acceptance:

- staging Coolify and production Coolify both report the intended release source/version through Platform release evidence or direct service health/source checks;
- no secret values are printed or committed;
- selected provider reports `READY` before draft/import work starts.

### Phase 1: ProdUS Deployment Config Backup

Status: completed.

This is mandatory before touching ProdUS live deployment state. It backs up the ProdUS deployment configuration through Platform export/import. It is not a Coolify control-plane or server backup.

Steps:

1. Export a config-only bundle for `dep-53f9ca56`.
2. Export a sealed backup bundle for `dep-53f9ca56` using the approved recipient/key policy.
3. Store both artifacts in a private operator backup location.
4. Record only non-secret evidence in handoff docs: export ids, timestamps, bundle fingerprints, source deployment id, source version/release, and private storage path names.
5. Confirm the sealed bundle import preview succeeds before relying on it.
6. Do not paste secret values, sealed payload contents, database contents, or bundle plaintext into tracked docs or chat.

Acceptance:

- config-only export exists for `dep-53f9ca56`;
- sealed export exists for `dep-53f9ca56`;
- import preview passes;
- non-secret bundle fingerprints and storage path names are recorded;
- no plaintext secret values appear in exported visible JSON, tracked docs, logs, or chat.

### Phase 2: Baseline Evidence Before Migration

Status: completed.

Steps:

1. Capture Platform source-of-truth for `dep-53f9ca56`.
2. Capture current `produs-staging` assignment response without printing the assignment key.
3. Capture runtime, connector, and vectorization runner health.
4. Capture runtime admin overview with supported entity types.
5. Capture index counts by entity type.
6. Run retrieval smoke for `API security review` with `service-module` hints.
7. Run retrieval smoke for a package-template/security hardening query.

Acceptance:

- baseline evidence proves the current Qdrant-backed deployment is healthy before migration;
- retrieval baseline includes `service-module:api-security-review`;
- evidence contains no secret values.

### Phase 3: Clone/Import ProdUS To Non-Qdrant Managed Vector Backend

Status: completed with Zilliz/Milvus fallback after Pinecone provisioning failed.

Preferred flow: clone-as-new deployment, verify, then switch assignment.

Reason:

- current `dep-53f9ca56` remains a warm rollback target;
- consumer assignment discovery already treats `deploymentId` as assignment metadata;
- ProdUS should depend on stable consumer `produs-staging`, not a hardcoded deployment id.

Steps:

1. Import the sealed/config bundle as a new draft deployment for ProdUS staging migration.
2. Use the production Coolify staging target profile first, not production, if the migration is being rehearsed.
3. Patch the new draft provider config to selected non-Qdrant provider:
   - preferred `vectorStrategy=pinecone`;
   - `vectorProvisioningMode=PLATFORM_MANAGED` if Platform can create and track the managed index;
   - otherwise `vectorProvisioningMode=EXTERNAL_EXISTING` with a Platform-secret-backed host/index/key;
   - preserve `vectorStoragePosture=SHARED` only if the selected provider path supports strict tenant/source isolation;
   - otherwise use a tenant/deployment-owned index/namespace posture.
4. Publish the draft.
5. Apply to staging target profile.
6. Run managed vectorization reindex for all ProdUS safe entity types.
7. Verify counts and retrieval.

Acceptance:

- imported clone reaches `APPLIED_VERIFIED`;
- selected provider connectivity is `READY`;
- vectorization run processes all expected ProdUS safe records with zero failures;
- runtime index overview shows nonzero counts for `service-module`, `package-template`, and all expected ProdUS spaces;
- retrieval smoke returns `service-module:api-security-review`;
- package-template/security smoke returns package-template sources.

### Phase 4: Production Cutover

Status: completed.

Preferred cutover:

1. Apply the verified non-Qdrant deployment/version to `dtp-coolify-production`.
2. Verify runtime, connector, vectorization runner, and vector index health.
3. Switch or bind the `produs-staging` consumer assignment to the new verified release/deployment.
4. Keep old Qdrant-backed `dep-53f9ca56` release `rel-4ed2ffc9` running and rollback-reserved.
5. Ask ProdUS backend to refresh assignment discovery cache, or wait for cache TTL.
6. Run ProdUS-side `/api/ai/assistant/query`, `/api/ai/assistant/query-once`, and suggestions smoke from an authenticated owner session if available.

Alternative cutover:

- If Product/Platform operators decide the deployment id must remain `dep-53f9ca56`, use restore-in-place draft creation from the backup bundle, patch provider config to the selected non-Qdrant backend, publish, apply, and verify. This is more invasive and should only be chosen if assignment-based cutover is not acceptable.

Acceptance:

- active assignment resolves to the non-Qdrant runtime;
- direct runtime health is green;
- retrieval smokes are green through Platform and ProdUS;
- no Qdrant endpoint is used by the active ProdUS runtime provider config;
- rollback to the old Qdrant-backed release is documented and tested at least as a dry-run assignment rollback.

### Phase 5: Qdrant Demotion In Platform Defaults

Status: partially completed.

Code/config work:

1. Change default dev/demo templates away from `dev-openai-qdrant` where a local/Lucene profile is enough.
2. Change production recommended package profiles from `QDRANT_SHARED` to the selected managed provider profile.
3. Keep Qdrant as an advanced/manual vector profile.
4. Update Shopify Companion package/profile defaults if they currently hardcode `QDRANT_SHARED`.
5. Update verification suite defaults:
   - general release gate should not require Qdrant hosted verification;
   - managed provider verification should allow `RUN_QDRANT=false`;
   - vector provider hosted verification should run against the selected provider for the release candidate.
6. Rename user-facing labels so products do not expose provider names unless the operator is in an infrastructure/admin surface.

Acceptance:

- new deployment creation no longer defaults to Qdrant;
- release gate can pass without Qdrant Cloud management calls;
- Qdrant remains selectable for advanced/operator deployments;
- docs and UI labels do not contradict the new default.

### Phase 6: Soak, Cleanup, And Retire

Status: in progress.

Steps:

1. Soak the new non-Qdrant ProdUS runtime for at least one business day or an agreed owner-observed test window.
2. Keep Qdrant-backed deployment resources marked `ROLLBACK_RESERVED`.
3. After soak, mark old Qdrant resources `SUPERSEDED`.
4. Only delete or detach Qdrant managed resources after an explicit operator approval and a final backup/export check.
5. Update ProdUS handover, private handoff, and working context with non-secret evidence.

Acceptance:

- no ProdUS retrieval regression during soak;
- rollback window is complete;
- stale Qdrant resources are not deleted silently;
- final handoff states the active vector provider and rollback status.

## Verification Matrix

| Gate | Required Evidence |
| --- | --- |
| AI Fabric release upgrade | staging and production Coolify services verified on `0.3.1` or newer |
| ProdUS deployment config backup | config-only export, sealed export, import preview, non-secret bundle fingerprints |
| Baseline evidence | source-of-truth, assignment, health, index counts, and retrieval smoke before migration |
| Provider connectivity | selected provider `READY`; Qdrant not required |
| Clone/import apply | new deployment or restore draft reaches `APPLIED_VERIFIED` |
| Vectorization | expected ProdUS records processed with zero failures |
| Runtime retrieval | `service-module:api-security-review` returned as source |
| ProdUS backend path | `/api/ai/assistant/query` and `/query-once` return grounded answer through assignment |
| Rollback | old Qdrant release remains available until soak is complete |

## Rollback Plan

Fast rollback path:

1. Rebind `produs-staging` assignment to the previous Qdrant-backed release/deployment.
2. Ask ProdUS backend to clear assignment cache or wait for assignment TTL.
3. Verify `API security review` smoke against old runtime.
4. Mark failed non-Qdrant release `FAILED_DIAGNOSTIC_HOLD`.
5. Keep failed provider resources for diagnosis until operator cleanup approval.

Restore rollback path:

1. Use the sealed export bundle to restore a draft from the pre-migration deployment state.
2. Publish and apply the restored draft to production target profile.
3. Run release verification and ProdUS smoke before assignment cutback.

## Required Operator Inputs

Before Codex or an operator executes the migration, provide or confirm:

- Platform admin access and production target profile apply permission work from the local/operator shell;
- selected non-Qdrant provider credentials exist in Platform secrets or private local secret files;
- recipient/key policy for sealed deployment export;
- whether `produs-staging` may move to a new deployment id through assignment discovery;
- acceptable soak duration before Qdrant resource retirement.

## Risks And Controls

| Risk | Control |
| --- | --- |
| Provider dimensions mismatch | verify embedding dimensions and index dimensions before reindex |
| Tenant/source leakage in shared index | keep strict metadata filters; use dedicated namespace/index if shared isolation is not proven |
| ProdUS still hardcodes deployment id | use assignment discovery proof; coordinate cache refresh before cutover |
| Provider provisioning succeeds but retrieval fails | require live service-module and package-template smokes before assignment cutover |
| Qdrant rollback resources deleted too early | mark old resources `ROLLBACK_RESERVED`; delete only after soak and explicit approval |
| Release gate still blocks on Qdrant | update verification defaults to demote Qdrant from general release gate |

## Completion Definition

This work is complete only when:

- staging and production Coolify are confirmed on the new AI Fabric release;
- ProdUS has config-only and sealed Platform deployment backups with a passing import preview;
- ProdUS runs on a verified non-Qdrant managed vector backend;
- assignment discovery resolves to the verified non-Qdrant runtime;
- service-module and package-template retrieval are green through the real ProdUS path;
- Qdrant is no longer a default or general release-blocking dependency;
- rollback evidence is documented and old Qdrant resources are either rollback-reserved or explicitly retired.

Execution status on 2026-06-28: the ProdUS cutover, backup, import-preview, managed-vector reindex, assignment activation, and Platform Qdrant release-gate demotion are complete. Remaining work is soak observation, aggregate readiness cleanup, optional custom-domain assignment-key alignment, direct ProdUS backend owner-session smoke, Shopify/default-profile demotion review, and explicit old-Qdrant cleanup approval.
