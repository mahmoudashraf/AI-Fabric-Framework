# 010.15 - Clone-Based Production Promotion And Runtime Assignment Plan

Status: alternate clone/backup/DR plan. Superseded for normal day-to-day customer promotion by `010_16_PRACTICAL_DEV_STAGING_PRODUCTION_DEPLOYMENT_MODEL.md`. No live promotion, import, assignment switch, or provider mutation has been executed as part of this document.

Related plans:

- `010_12_PRODUCTION_DEPLOYMENT_EXECUTION_PLAN.md`
- `010_13_DEPLOYMENT_EXPORT_IMPORT_SEALED_BACKUP_RESTORE_PLAN.md`
- `010_14_CONSUMER_BOUND_RUNTIME_ASSIGNMENT_AND_DIRECT_PRIVATE_AUTH_PLAN.md`
- `010_8_SHOPIFY_COMPANION_NEXT_URGENT_STEPS_READINESS_PLAN.md`

## Purpose

The current Platform has two separate capabilities:

1. Release apply to a target profile.
2. Deployment sealed export/import clone.

For migration-heavy, backup/restore, disaster-recovery, or separate-production-identity promotion, these can be composed into one explicit workflow:

1. Keep the verified staging deployment intact.
2. Clone the verified staging deployment into a separate production deployment.
3. Apply the cloned production deployment to the production target profile.
4. Verify the production runtime, connector, vectorization runner, action catalog, and customer-facing query path.
5. Activate production by switching a stable consumer runtime assignment.
6. Roll back by switching the stable production consumer back to the previous known-good production deployment.

This is no longer the preferred normal "Go production" path when the same Platform deployment version can be applied to the production target profile and activated through a release-bound production consumer assignment.

## Current Behavior

### Deployment Release Apply

`DeploymentService.applyVersion(...)` accepts an optional `targetProfileId`.

Current behavior:

- It creates a new release for the same deployment id.
- It records the selected target profile on the release.
- It provisions through the selected provider target profile.
- It does not create a separate production deployment record.

This is useful as a low-level release operation, but it is not enough by itself for a clean staging-to-production lifecycle.

### Shopify Go-Live

`ShopifyStoreGoLiveService.goLive(...)` currently:

- validates the store is eligible;
- syncs the deployment draft;
- publishes a version;
- resolves the production target profile;
- applies the version through `applyVersionForTrustedCaller(...)`;
- leaves the store connection pointing at the same deployment id.

This proves production target profile provisioning, but it does not provide a separate production deployment identity or assignment-level rollback.

### Sealed Export/Import Clone

`DeploymentBundleExportImportService` supports `SEALED_CLONE`.

Current behavior:

- It creates a new deployment id.
- It restores draft configuration.
- It restores exportable sealed secrets.
- It restores Marketplace plugin installs.
- It restores managed product service dependency configuration.
- It restores vectorization control plane configuration.
- It can target a new environment and target profile.

This is the right primitive for production promotion, backup restore, and shift-left deployment recreation.

## Target Behavior

Production promotion should become a first-class Platform workflow:

```text
verified staging deployment
  -> sealed export
  -> production clone import
  -> production draft validation
  -> production version publish
  -> production target apply
  -> production verification
  -> stable production consumer assignment switch
```

Staging and production must be separate runtime deployments:

```text
customer-staging    -> staging deployment
customer-production -> production deployment
```

The external customer should not need to update runtime URLs when Platform replaces a runtime deployment. The customer backend should fetch the assigned runtime URL on startup and refetch only when the cached runtime is not healthy or the assignment TTL expires.

## Design Principles

- Staging remains untouched during production promotion.
- Production is a separate deployment record.
- Production activation is assignment-based, not DNS or env-file churn.
- Runtime traffic should not go through Platform on every chat request.
- Secrets are restored only through sealed export/import or target-side secret remapping.
- No plaintext secrets are returned in API responses.
- Failed production promotion must not affect the staging deployment or staging consumer.
- Rollback is a consumer assignment operation first, not a rebuild operation.
- Low-level target-profile apply remains available for operators, but product "Go production" uses this higher-level workflow.

## Proposed Platform Model

### New Promotion Entity

Create a `deployment_promotion` record to track the full workflow.

Suggested fields:

- `id`
- `sourceDeploymentId`
- `sourceDeploymentVersionId`
- `sourceReleaseId`
- `sourceEnvironment`
- `sourceConsumerId`
- `targetDeploymentId`
- `targetDraftId`
- `targetDeploymentVersionId`
- `targetReleaseId`
- `targetEnvironment`
- `targetProfileId`
- `targetConsumerId`
- `previousAssignedDeploymentId`
- `status`
- `verificationStatus`
- `activationStatus`
- `rollbackStatus`
- `requestedBy`
- `requestedAt`
- `activatedAt`
- `rolledBackAt`
- `failureReason`
- `operatorDiagnosticsRef`

Suggested statuses:

- `REQUESTED`
- `SOURCE_VERIFICATION_CHECKED`
- `EXPORTED`
- `IMPORTED_CLONE`
- `DRAFT_VALIDATED`
- `PUBLISHED`
- `APPLY_REQUESTED`
- `APPLY_VERIFIED`
- `ACTIVATION_READY`
- `ACTIVATED`
- `FAILED`
- `ROLLED_BACK`
- `CANCELLED`

### Promotion Request

Add a promotion API owned by Platform deployment orchestration.

Suggested endpoint:

```http
POST /api/deployments/{sourceDeploymentId}/production-promotions
```

Suggested request:

```json
{
  "sourceVersionId": "ver-source",
  "sourceReleaseId": "rel-source",
  "targetEnvironment": "production",
  "targetProfileId": "dtp-coolify-production",
  "targetConsumerId": "customer-production",
  "newDeploymentName": "Customer Production Runtime",
  "activationMode": "VERIFY_THEN_ASSIGN",
  "secretRestoreMode": "SEALED_CLONE",
  "requireSourceVerificationPassed": true,
  "requireProductionVerificationPassed": true
}
```

Suggested response:

```json
{
  "promotionId": "prm-...",
  "status": "REQUESTED",
  "sourceDeploymentId": "dep-staging",
  "targetDeploymentId": null,
  "targetConsumerId": "customer-production",
  "activationMode": "VERIFY_THEN_ASSIGN"
}
```

### Promotion Execution

Execution stages:

1. Lock the source deployment.
2. Verify source release is `APPLIED_VERIFIED` and verification is `PASSED`.
3. Export a sealed deployment bundle.
4. Import the bundle as `SEALED_CLONE` using the requested production environment and target profile.
5. Validate the imported draft.
6. Publish the imported draft.
7. Apply the production version to the production target profile.
8. Wait for release status `APPLIED_VERIFIED`, provisioning `ACTIVE`, verification `PASSED`.
9. Verify runtime health, connector health, vectorization runner health, action catalog exposure, and query/query-once smoke tests.
10. Switch the stable production consumer assignment to the target deployment.
11. Record evidence and expose merchant/operator-safe status.

### Activation API

Activation should be separated from provisioning where possible.

Suggested endpoint:

```http
POST /api/deployment-promotions/{promotionId}/activate
```

Rules:

- Only activate after production verification passed.
- Record previous production assignment before switching.
- Use consumer audience mode `CONSUMER_ID`.
- Do not return runtime secrets or assertion signing material.

### Rollback API

Rollback should switch assignment back to the previous known-good production deployment.

Suggested endpoint:

```http
POST /api/deployment-promotions/{promotionId}/rollback
```

Rules:

- The rollback target must be the previous assigned deployment captured before activation.
- The rollback target must still be healthy or explicitly operator-approved.
- Staging deployment is not used as rollback unless the product explicitly has no prior production deployment and a controlled fallback policy allows it.
- Rollback should not delete the failed/new production deployment automatically.

## Shopify-Specific Changes

### Current Issue

Shopify go-live currently applies the store deployment to `dtp-coolify-production` but keeps the same deployment id in the store connection.

### Target Shopify Flow

For Shopify Companion:

```text
shopify-shopping-companion-test-staging    -> staging deployment
shopify-shopping-companion-test-production -> production deployment
```

Partner/merchant "Go production" should:

1. verify staging launch readiness;
2. call clone-based production promotion;
3. create or resolve a production consumer id;
4. keep staging consumer and staging deployment untouched;
5. activate production consumer only after production verification passes;
6. show production runtime status and rollback action in Partner Portal.

### Data Model Options

Preferred greenfield model:

- Add a store runtime binding table:
  - `shopDomain`
  - `environment`
  - `consumerId`
  - `deploymentId`
  - `status`
  - `active`
  - `lastVerifiedAt`

Alternative:

- Add production fields to `ShopifyStoreConnectionEntity`.

Preferred option is the binding table because it supports:

- staging and production in parallel;
- rollback history;
- future preview environments;
- clear consumer assignment state.

## ProdUS-Specific Changes

For ProdUS:

```text
produs-staging    -> staging deployment
produs-production -> production deployment
```

ProdUS should continue using the runtime assignment endpoint:

```http
GET /api/public/consumers/{consumerId}/runtime-assignment
```

ProdUS backend should:

- cache the assignment until `cacheTtlSeconds`;
- send direct runtime requests to the assigned runtime URL;
- use private-runtime assertions with `aud` equal to the consumer id;
- refetch the assignment if runtime health fails or the cached assignment expires.

The production promotion workflow should create and verify the `produs-production` deployment separately from `produs-staging`.

## Secret Handling

Promotion must not copy plaintext secrets through request/response payloads.

Allowed secret paths:

- sealed export/import using the operator-supplied public/private key flow;
- secret references restored on the target Platform;
- target-side secret remapping where the target environment intentionally uses a different secret ref.

Required controls:

- export requires deployment admin/operator permission;
- sealed export with secrets requires elevated approval;
- import with secret restoration requires target deployment admin permission;
- audit events record secret ids/refs but never values;
- promotion status never includes decrypted secret material;
- failed promotion diagnostics redact credentials and provider tokens.

## Provider And Target Profile Handling

Promotion must require an explicit production target profile unless a product template has a single approved production profile.

Rules:

- `dtp-coolify-staging` is not valid for production activation.
- `dtp-coolify-production` must pass preflight before import/apply.
- Target profile must be active.
- Target profile must allow runtime deployments.
- Product service dependencies must be reconciled against the target profile.
- Managed vectorization runner must be included when the source deployment uses managed vectorization.

## Assignment Contract

Production activation changes the consumer assignment, not the customer application config.

Assignment response must include:

- `consumerId`
- `deploymentId`
- `runtimeBaseUrl`
- `queryEndpoint`
- `queryOnceEndpoint`
- `suggestionsEndpoint`
- `authContextEndpoint`
- `healthEndpoint`
- `privateRuntimeAudience`
- `privateRuntimeAudienceMode`
- `cacheTtlSeconds`
- `externalIntegrationReady`

Assignment response must not include:

- runtime API keys;
- assertion signing secrets;
- sealed bundle material;
- provider tokens.

## Verification Gates

Promotion cannot activate until all mandatory gates pass.

Mandatory gates:

1. Source staging release is `APPLIED_VERIFIED`.
2. Source staging verification is `PASSED`.
3. Source staging runtime and connector are healthy.
4. Sealed export preview is valid.
5. Import clone creates a new deployment id.
6. Imported draft validation passes.
7. Production target profile preflight passes.
8. Production release reaches `APPLIED_VERIFIED`.
9. Production provisioning is `ACTIVE`.
10. Production verification is `PASSED`.
11. Production runtime health is `UP`.
12. Production connector health is `UP`.
13. Managed vectorization runner health is `UP` when configured.
14. Runtime auth-context accepts the production consumer audience.
15. Query/query-once smoke passes.
16. Required MCP/read actions are exposed and callable when configured.
17. Staging assignment remains unchanged before activation.
18. Staging runtime remains healthy after production apply.
19. Production assignment switch succeeds.
20. Assignment endpoint returns the new production deployment.

Optional gates:

- load smoke;
- cost/rate-limit smoke;
- vectorization reindex proof;
- hosted UI smoke;
- rollback rehearsal before first production launch.

## Failed Promotion Behavior

Failed promotion must:

- keep source staging deployment untouched;
- keep staging consumer assignment unchanged;
- keep previous production consumer assignment unchanged;
- return merchant-safe failure guidance;
- retain operator diagnostics in audit/support surfaces;
- keep failed production resources available for inspection unless operator chooses cleanup.

Provider-level failed deployment rehearsal should be supported with a safe failure harness. A validation-only failure is useful, but it does not fully prove provider failure isolation.

## Rollback Behavior

Rollback should be assignment-first:

1. Verify previous production deployment is still healthy.
2. Switch `customer-production` back to the previous production deployment.
3. Verify assignment endpoint returns the previous deployment.
4. Verify direct runtime query passes.
5. Mark promotion `ROLLED_BACK`.
6. Keep failed/new production deployment for diagnosis or later cleanup.

If the previous production deployment is not healthy, require explicit operator approval for either:

- restore-in-place from sealed backup;
- create a fresh production clone from a known-good bundle;
- controlled fallback to staging if product policy allows it.

## UI Requirements

Partner Portal and Platform UI should show:

- source deployment id and release;
- target deployment id and release;
- target profile;
- target consumer id;
- promotion status timeline;
- blocking gates;
- verification evidence;
- activation button only when eligible;
- rollback button after activation;
- cleanup/deactivation action for failed target deployment;
- merchant-safe status copy;
- operator diagnostics only for authorized operators.

Provider internals, raw secrets, and raw Coolify tokens must not be visible in partner or merchant views.

## API Summary

Suggested new APIs:

```http
POST /api/deployments/{sourceDeploymentId}/production-promotions
GET  /api/deployment-promotions/{promotionId}
POST /api/deployment-promotions/{promotionId}/activate
POST /api/deployment-promotions/{promotionId}/rollback
POST /api/deployment-promotions/{promotionId}/cleanup-target
```

Existing APIs reused:

```http
POST /api/deployments/{deploymentId}/exports
POST /api/deployments/imports/preview
POST /api/deployments/imports
POST /api/deployments/{deploymentId}/drafts/{draftId}/publish
POST /api/deployments/{deploymentId}/versions/{versionId}/apply?targetProfileId=...
GET  /api/public/consumers/{consumerId}/runtime-assignment
```

## Implementation Slices

### Slice 1 - Promotion Orchestration Model

- Add promotion entity, repository, summary model, and audit events.
- Add source readiness checks.
- Add target profile preflight checks.
- Add status timeline.

### Slice 2 - Clone-Based Promotion Execution

- Reuse sealed export/import internals.
- Create target deployment clone.
- Restore target managed product service dependencies.
- Restore target vectorization control plane.
- Validate imported draft.

### Slice 3 - Production Apply And Verification

- Publish imported draft.
- Apply to production target profile.
- Poll/reconcile release and verification status.
- Run runtime/connector/vectorization/action/query smoke checks.

### Slice 4 - Assignment Activation

- Add activation endpoint.
- Capture previous production assignment.
- Switch production consumer assignment.
- Verify assignment and direct runtime query.

### Slice 5 - Rollback And Cleanup

- Add assignment rollback.
- Add target cleanup/deactivation path.
- Record evidence.
- Keep staging untouched.

### Slice 6 - Shopify Migration To Clone Promotion

- Replace `ShopifyStoreGoLiveService` direct same-deployment apply with promotion service.
- Introduce store runtime binding if needed.
- Keep Shopify admin/partner portal go-live behavior but change backend mechanics.
- Verify Shopify staging and production consumers separately.

### Slice 7 - ProdUS Production Promotion Path

- Use `produs-staging` as source consumer.
- Create `produs-production` as target consumer.
- Promote with clone-based flow.
- Verify direct private-runtime query with `aud=produs-production`.

## Acceptance Gates

`010_15_CLONE_PROMOTION_READY` passes when:

- staging promotion creates a separate target deployment;
- source staging deployment remains healthy and unchanged;
- target production deployment is applied and verified;
- production consumer assignment switches only after verification;
- rollback switches assignment back to previous production deployment;
- failed promotion leaves staging and previous production assignment untouched;
- no secrets appear in API responses, logs, docs, or UI;
- Shopify go-live no longer depends on same-deployment production apply;
- ProdUS can use assignment lookup for both staging and production without runtime URL env changes.

## What Not To Do

- Do not make every external chat request pass through Platform.
- Do not make production activation equal to target-profile apply only.
- Do not overwrite staging deployment state during production promotion.
- Do not expose Coolify/provider internals in merchant or partner UI.
- Do not send raw secrets in import, promotion, or assignment responses.
- Do not use deployment id as the long-term external assertion audience for greenfield production integrations.

## Review Questions

1. Should production promotion always create a new deployment, or should operators still be allowed to apply production target profile to the same deployment for internal-only environments?
2. Should Shopify use a store runtime binding table immediately, or are production fields on the existing store connection acceptable for the first migration?
3. Should activation be automatic after all gates pass, or require explicit merchant/operator confirmation?
4. Should rollback require runtime health on the previous deployment, or allow forced assignment switch with operator approval?
5. Should provider-level failed-deployment rehearsal be mandatory before first public launch for every customer, or only before public self-service launch?

## Current Plan Status

This document is a design and implementation plan only.

No live deployment was promoted, imported, activated, rolled back, or cleaned up while creating this plan.
