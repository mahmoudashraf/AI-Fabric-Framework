# 010.13 Deployment Export, Import, Sealed Backup, And Restore Plan

Status: implemented, deployed to staging, and live verified on Platform-V10 (2026-06-01, commit `00b53ae60`)
Primary target: Platform-managed deployments for Shopify Companion, ProdUS, and future external-customer deployments  
Related plans: 007, 009.2, 010, 010.5, 010.6, 010.7, 010.12

## Purpose

Add a production-grade Platform capability to export, import, clone, and restore managed deployments.

The feature must let an authorized operator back up or recreate a deployment comprehensively, including templates, curated modules, Marketplace plugins, runtime/provider configuration, vectorization configuration, service routes, auth contracts, and secret material.

The feature must not turn Platform into a plaintext secret leakage path. Secret values are never exported as visible JSON, never rendered in UI, never logged, and never returned unsealed from APIs.

## Implementation Status

Implemented in Platform-V10:

- Platform REST APIs for export preview, config-only export, sealed export, import preview, clone import, and restore-in-place draft creation, with deployment-admin access enforced server-side.
- Canonical deployment bundle schema `loomai.deployment-export.v1` with manifest hash, bundle hash, source metadata, deployment/draft/version/release/plugin/vector/public-API binding sections, and import guidance.
- Secret inventory classification with no secret values in config-only bundles or UI payloads.
- Sealed backup support using RSA-OAEP-SHA256 wrapped AES-256-GCM envelope encryption.
- Import/restore flow creates drafts only. It does not make imported config live and does not bypass publish/apply/release verification.
- Restore-in-place preserves the deployment id and intended external runtime contract.
- Revisions UI backup/restore panel with config-only/sealed export, bundle upload/paste, import preview, and draft creation.
- Unit tests proving sealed roundtrip, wrong-key failure, no plaintext secret leakage in exported bundles, and restore-in-place draft/secret restoration.
- Staging live verification against the ProdUS deployment `dep-7706fafb` proving config-only export, sealed export, import preview, clone draft creation, restore-in-place preview, and temporary clone archival.

Still intentionally outside this feature:

- Customer/shopper OAuth sessions, chat turns, temporary URLs, raw customer data, and checkout/session state are not exported.
- Production apply after import remains governed by the existing release gates.

## Core Decision

Implement deployment export/import as a sealed bundle system with three distinct flows:

1. **Config-only export**
   - Safe default.
   - Includes all non-secret deployment configuration and secret references.
   - Does not include secret values.

2. **Sealed backup export**
   - Privileged backup/restore flow.
   - Includes encrypted secret values inside an authenticated encrypted payload.
   - Requires elevated authorization, explicit reason, audit event, and recipient key/passphrase choice.

3. **Import/restore**
   - Always starts as validation plus draft creation.
   - Supports clone-as-new and restore-in-place.
   - Production apply still goes through release gates and target profile verification.

## Non-Goals

- Do not export customer/shopper OAuth sessions, temporary URLs, chat history, customer account tokens, checkout sessions, or raw private customer data.
- Do not expose Coolify provider internals to partner/merchant/browser UIs.
- Do not add a manual Coolify-only restore path that bypasses Platform state.
- Do not make imported deployments live automatically.
- Do not require backward compatibility with old unsupported export file shapes.

## User Stories

### Operator-Owned Backup

As a deployment operator, I can export my own deployment config and sealed deployment secrets so I can recover from accidental deletion, service recreation failure, or provider migration.

Acceptance:

- Operator has deployment admin/owner permission.
- Export includes all config needed to recreate the deployment.
- Secret values are encrypted and cannot be read from the downloaded file without decrypt authority.
- Export event is audited with deployment id, actor, export mode, reason, and redacted bundle fingerprint.

### Restore In Place

As a platform operator, I can restore an exported bundle back into the same deployment id and runtime route so an external integration such as ProdUS does not need to change its backend env.

Acceptance:

- Deployment id remains unchanged.
- Stable runtime URL remains unchanged when the route/domain is still available.
- Runtime assertion audience remains unchanged.
- Existing deployment goes through dry-run validation before mutation.
- Restore creates a new draft/version/release, not a direct live mutation.

### Clone As New

As a platform operator, I can import an exported deployment into a new deployment id for staging copy, tenant migration, reproduction, or design-partner duplication.

Acceptance:

- New deployment id is created.
- New runtime route/audience is generated unless an alias mapping is supplied.
- Import report clearly lists values the customer must change, such as base URL and assertion audience.
- Imported secrets can be restored from sealed backup or replaced/regenerated.

### Disaster Recovery

As a platform admin, I can restore a deleted or broken managed deployment from a sealed backup.

Acceptance:

- Platform validates schema version, bundle integrity, environment compatibility, target profile availability, plugin availability, and secret policy.
- Import can recreate runtime, connector, vectorization runner, Marketplace installs, data plugin config, source connections, and routes.
- Production restore requires approval and release verification.

## Key Definitions

| Term | Meaning |
| --- | --- |
| Bundle | Exported deployment artifact containing manifest, metadata, validation fingerprints, and optionally sealed secrets. |
| Config-only export | Bundle without plaintext or encrypted secret values. |
| Sealed backup | Bundle with secret values encrypted using envelope encryption. |
| Restore in place | Import into the same deployment id and intended same runtime route/auth audience. |
| Clone | Import into a new deployment id with remapped routes, audiences, and provider service records. |
| Recipient | Public key, KMS key, or passphrase policy allowed to unwrap bundle secrets. |
| Secret policy | Per-secret import/export classification: export sealed, regenerate, forbidden, environment-bound, or operator-supplied. |

## Security Principles

- Secrets are resolved only server-side by Platform services with explicit authorization.
- Plaintext secret values are never serialized into the main manifest.
- Plaintext secret values are never written to disk, logs, audit payloads, browser state, frontend telemetry, or validation reports.
- The default export is config-only.
- Sealed secret export requires stronger permission than config-only export.
- Import of sealed secrets requires decrypt authority and explicit target mode.
- Every export/import creates immutable audit events.
- Restore/import creates a draft and release candidate. It does not mutate live service config directly.
- Bundle validation must be deterministic and safe to run without decrypting secrets when possible.
- Secret restore must be idempotent and should support post-restore rotation.

## Authorization Model

### Required Capabilities

| Operation | Minimum Role | Additional Requirements |
| --- | --- | --- |
| Export config only | Deployment admin/operator for that deployment | Deployment ownership/tenant check |
| Export sealed secrets | Deployment admin plus secret-export permission | Reason required, fresh auth/session, audit event |
| Import config only as clone | Platform operator or deployment admin in target workspace | Target workspace permission |
| Import sealed secrets | Platform admin or deployment admin with restore permission | Decrypt authority, reason, audit event |
| Restore in place | Platform admin or deployment owner with restore-in-place permission | Existing deployment ownership and release-gate policy |
| Production apply after import | Existing production promotion permission | Approval/release verification |

### Ownership Rule

An operator can export sealed secrets for their own deployment if all are true:

- The actor has deployment-admin rights for the deployment.
- The deployment belongs to the actor's tenant/workspace or delegated partner scope.
- The export mode is sealed, not plaintext.
- The export reason is captured.
- Secret classifications allow sealed export.

No actor can export secrets from a deployment they do not own or administer, even if they can view non-secret deployment metadata.

## Secret Classification

### Exportable If Sealed

- Runtime trusted backend API keys.
- Runtime private assertion signing keys.
- MCP server API keys.
- Marketplace connector API keys.
- Webhook signing secrets.
- Provider API keys used by deployment-scoped runtime/connector services.
- Vectorization source access tokens.
- Internal service-to-service deployment keys when exact restore is required.

### Prefer Regenerate On Import

- Generated runtime admin keys.
- Generated connector admin keys.
- Generated deployment service API keys.
- Internal Coolify app secrets that can be reissued.
- Service auth material that does not need to preserve an external customer integration contract.

### Forbidden By Default

- Customer Account OAuth access/refresh tokens.
- Shopper/customer sessions.
- Checkout sessions.
- Temporary file URLs.
- One-time consent tokens.
- Idempotency tokens.
- Raw customer PII.
- Chat turns/history unless a separate tenant-approved data export feature exists.
- Raw scanner logs or raw evidence files.

### Environment-Bound

- Coolify application UUIDs.
- Server IP addresses.
- Runtime domains/routes.
- Database URLs.
- Qdrant/Postgres service URLs.
- Shopify app client ids/secrets and redirect URL bindings.

Environment-bound values may be exported as mapping metadata but import must either preserve them in restore-in-place or remap them in clone/import.

## Bundle Shape

### Top-Level Shape

```json
{
  "schemaVersion": "loomai.deployment-export.v1",
  "bundleId": "dxb_...",
  "exportMode": "SEALED_BACKUP",
  "createdAt": "2026-06-01T12:00:00Z",
  "createdBy": {
    "actorType": "PLATFORM_USER",
    "actorIdHash": "sha256:...",
    "workspaceId": "..."
  },
  "source": {
    "deploymentId": "dep-7706fafb",
    "deploymentName": "ProdUS AI Enablement Staging",
    "environment": "staging",
    "customerId": "produs-staging",
    "targetProfileId": "dtp-coolify-staging"
  },
  "manifest": {},
  "secretEnvelope": {},
  "integrity": {},
  "importGuidance": {}
}
```

### Manifest Shape

```json
{
  "deployment": {
    "name": "ProdUS AI Enablement Staging",
    "templateId": "dev-openai-qdrant",
    "curatedModuleId": "default",
    "customerId": "produs-staging",
    "environment": "staging",
    "description": "..."
  },
  "runtime": {
    "template": "dev-openai-qdrant",
    "curatedPack": "default",
    "supportedModes": ["thinker", "executor"],
    "baseUrl": "http://dep-7706fafb.46.224.145.148.sslip.io",
    "routes": [
      {
        "kind": "runtime",
        "host": "dep-7706fafb.46.224.145.148.sslip.io",
        "preserveInPlace": true,
        "clonePolicy": "REMAP"
      }
    ],
    "auth": {
      "mode": "PRIVATE_RUNTIME_ASSERTION",
      "acceptedIssuers": ["produs-staging-backend"],
      "acceptedAudiences": ["dep-7706fafb"],
      "authorizationHeader": "X-AIFABRIC-RUNTIME-AUTHORIZATION"
    }
  },
  "services": [
    {
      "kind": "runtime",
      "provider": "coolify",
      "sourceBranch": "Platform-V10",
      "dockerfile": "...",
      "env": [
        {
          "key": "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY",
          "valueRef": "secret://runtime/trusted-backend-api-key",
          "secretPolicy": "SEALED_EXPORTABLE"
        }
      ]
    }
  ],
  "marketplaceInstalls": [
    {
      "pluginId": "mkp-data-produs-safe-knowledge",
      "version": "0.1.1",
      "installState": "LIVE",
      "configHash": "sha256:..."
    }
  ],
  "vectorization": {
    "sourceConnections": [],
    "plans": [],
    "runners": []
  },
  "providerBindings": [],
  "releaseEvidence": {
    "latestVersionId": "ver-b0c54807",
    "latestReleaseId": "rel-37d07c7c",
    "verificationStatus": "PASSED"
  }
}
```

### Secret Envelope Shape

The secret envelope is omitted for config-only exports.

```json
{
  "format": "loomai.sealed-secrets.v1",
  "algorithm": "AES-256-GCM",
  "ciphertext": "base64url...",
  "nonce": "base64url...",
  "tag": "base64url...",
  "wrappedKeys": [
    {
      "recipientType": "OPERATOR_PUBLIC_KEY",
      "keyId": "sha256:...",
      "wrappingAlgorithm": "RSA-OAEP-SHA256",
      "wrappedDataKey": "base64url..."
    }
  ],
  "aad": {
    "bundleId": "dxb_...",
    "schemaVersion": "loomai.deployment-export.v1",
    "sourceDeploymentId": "dep-7706fafb"
  }
}
```

### Decrypted Secret Payload Shape

This payload exists only in memory during export/import.

```json
{
  "schemaVersion": "loomai.deployment-secrets.v1",
  "secrets": [
    {
      "logicalName": "runtime.trustedBackendApiKey",
      "secretName": "AI_FABRIC_RUNTIME_TRUSTED_BACKEND_API_KEY",
      "value": "...",
      "classification": "SEALED_EXPORTABLE",
      "restorePolicy": "PRESERVE_FOR_RESTORE_IN_PLACE_REGENERATE_FOR_CLONE",
      "scope": {
        "deploymentId": "dep-7706fafb",
        "serviceKind": "runtime"
      }
    }
  ]
}
```

## Encryption Design

### Envelope Encryption

1. Generate a random 256-bit content encryption key.
2. Encrypt the secret payload with AES-256-GCM.
3. Use bundle id, schema version, source deployment id, export mode, and manifest hash as additional authenticated data.
4. Wrap the content encryption key for one or more recipients.
5. Include only wrapped keys in the bundle.

### Recipient Options

| Recipient Type | Use Case | Notes |
| --- | --- | --- |
| Platform recovery public key | Platform disaster recovery | Platform private key lives outside the app database. |
| Operator-provided public key | Customer/operator-owned backup | Preferred for tenant-owned sealed exports. |
| KMS key id | Cloud/HSM-backed deployment | Future-friendly for production. |
| Passphrase-derived key | Emergency/offline backup | Allowed only with Argon2id, high cost, and warning. |

Recommended initial implementation:

- Support operator-provided RSA public key using RSA-OAEP-SHA256.
- Support Platform recovery public key from secure config.
- Defer KMS/HSM integration behind an interface.
- Avoid browser-side decryption.

### Passphrase Policy

Passphrase export should be optional and disabled by default.

If enabled:

- Use Argon2id with high memory/time cost.
- Require minimum passphrase length.
- Never store passphrase.
- Warn that passphrase loss makes secret restore impossible.

## Integrity And Authenticity

Each bundle includes:

- canonical manifest hash
- sealed secret envelope hash
- bundle hash
- optional Platform signature over canonical bundle metadata
- schema version
- source deployment fingerprint
- plugin/version fingerprints

Import must verify:

- schema version is supported
- manifest hash matches
- encrypted payload AAD matches manifest metadata
- wrapped key recipient matches supplied decrypt material
- secret payload hash matches expected envelope metadata

## Import Modes

### CONFIG_ONLY_CLONE

Creates a new deployment draft from manifest only.

Behavior:

- Secrets are not restored.
- Required secrets become missing-secret tasks.
- Runtime route and assertion audience are remapped.
- Operator supplies/regenerates secrets before apply.

### SEALED_CLONE

Creates a new deployment from manifest and decrypted sealed secrets.

Behavior:

- Deployment id changes.
- Runtime URL changes unless alias mapping is supplied.
- Assertion audience changes to new deployment id unless manually preserved through a stable alias/audience policy.
- External customers such as ProdUS must update backend env:
  - base URL
  - assertion audience
  - assertion deployment id
  - possibly accepted issuer/audience config

### RESTORE_IN_PLACE

Restores into the same deployment record.

Behavior:

- Deployment id remains the same.
- Runtime route should remain the same if route/domain is still available.
- Assertion audience remains the same.
- External customer backend does not need env changes if runtime base URL and HMAC signing key are preserved.
- Import creates a draft/version/release; it does not mutate live service config directly.
- Existing deployment state is snapshotted before restore.

This is the required mode for backing up and restoring a ProdUS-style direct runtime integration without forcing ProdUS to change its backend configuration.

### RECREATE_AND_REPOINT_ALIAS

Future mode.

Behavior:

- Creates a new deployment/runtime service.
- Repoints stable alias to the new runtime.
- External customer uses alias, not deployment-id URL.
- Enables blue/green restore without customer env changes.

This requires a stable runtime alias feature, for example:

```text
https://runtime-produs-staging.loomai.pro
```

or:

```text
https://runtime.loomai.pro/deployments/produs-staging
```

## ProdUS-Specific Implication

Current ProdUS direct runtime integration is bound to:

```text
LOOMAI_BASE_URL=http://dep-7706fafb.46.224.145.148.sslip.io
LOOMAI_ASSERTION_AUDIENCE=dep-7706fafb
assertion.deploymentId=dep-7706fafb
```

Therefore:

- Restore-in-place can preserve the customer integration contract.
- Clone-as-new cannot preserve the contract unless ProdUS changes env or LoomAI adds a stable runtime alias.
- Same `customerId=produs-staging` is not enough to preserve runtime access. The runtime URL, assertion audience, and deployment id matter.

Import reports must explicitly state:

```json
{
  "externalIntegrationImpact": {
    "requiresCustomerEnvChange": true,
    "changedValues": [
      "runtimeBaseUrl",
      "assertionAudience",
      "assertionDeploymentId"
    ]
  }
}
```

For restore-in-place:

```json
{
  "externalIntegrationImpact": {
    "requiresCustomerEnvChange": false,
    "reason": "deployment id, audience, and route preserved"
  }
}
```

## API Design

### Export Preview

```http
POST /api/deployments/{deploymentId}/export/preview
```

Request:

```json
{
  "exportMode": "CONFIG_ONLY",
  "includeReleaseEvidence": true
}
```

Response:

```json
{
  "deploymentId": "dep-7706fafb",
  "exportMode": "CONFIG_ONLY",
  "includedSections": ["deployment", "runtime", "marketplace", "vectorization"],
  "secretSummary": {
    "includedValues": 0,
    "sealedEligible": 12,
    "regenerateRecommended": 4,
    "forbidden": 3
  },
  "warnings": []
}
```

### Create Export

```http
POST /api/deployments/{deploymentId}/exports
```

Request:

```json
{
  "exportMode": "SEALED_BACKUP",
  "reason": "Monthly deployment backup before production promotion",
  "recipient": {
    "type": "OPERATOR_PUBLIC_KEY",
    "publicKeyPem": "-----BEGIN PUBLIC KEY-----..."
  },
  "includeReleaseEvidence": true,
  "includeProviderMappings": true
}
```

Response:

```json
{
  "exportId": "dexp_...",
  "bundleId": "dxb_...",
  "status": "READY",
  "downloadUrl": "/api/deployment-exports/dexp_.../download",
  "expiresAt": "2026-06-01T13:00:00Z",
  "bundleHash": "sha256:...",
  "secretEnvelopeHash": "sha256:..."
}
```

### Download Export

```http
GET /api/deployment-exports/{exportId}/download
```

The response is a file download. The server should stream it. It should not persist the file longer than the configured TTL unless a durable export artifact store is explicitly enabled.

### Import Preview

```http
POST /api/deployment-imports/preview
Content-Type: multipart/form-data
```

Fields:

- `bundle`: uploaded bundle file
- `importMode`: `CONFIG_ONLY_CLONE`, `SEALED_CLONE`, or `RESTORE_IN_PLACE`
- `targetDeploymentId`: required for restore-in-place
- `targetWorkspaceId`: required for clone
- optional decrypt material reference

Response:

```json
{
  "schemaValid": true,
  "integrityValid": true,
  "secretsReadable": false,
  "targetProfileValid": true,
  "pluginCompatibility": {
    "missing": [],
    "drifted": []
  },
  "externalIntegrationImpact": {
    "requiresCustomerEnvChange": true,
    "changedValues": ["runtimeBaseUrl", "assertionAudience"]
  },
  "blockingIssues": [],
  "warnings": []
}
```

### Execute Import

```http
POST /api/deployment-imports
Content-Type: multipart/form-data
```

Response:

```json
{
  "importId": "dimp_...",
  "status": "DRAFT_CREATED",
  "deploymentId": "dep-new123",
  "draftId": "drf-...",
  "requiresSecretActions": [
    {
      "secretName": "AI_PROVIDER_API_KEY",
      "action": "SUPPLY_OR_REGENERATE"
    }
  ],
  "nextSteps": [
    "validate draft",
    "publish version",
    "apply to target profile"
  ]
}
```

## Backend Services

### New Platform Services

| Service | Responsibility |
| --- | --- |
| `DeploymentBundleExportService` | Builds canonical manifest and export preview. |
| `DeploymentBundleSecretCollector` | Resolves exportable secret values from `PlatformSecretService` under strict policy. |
| `DeploymentBundleSealingService` | Envelope encryption, key wrapping, integrity metadata. |
| `DeploymentBundleImportService` | Validates bundle and creates clone/restore draft. |
| `DeploymentBundleValidationService` | Schema, integrity, compatibility, target-profile, plugin, and route checks. |
| `DeploymentBundleSecretRestoreService` | Restores/regenerates/schedules secrets according to policy. |
| `DeploymentBundleAuditService` | Writes audit events for preview/export/download/import/restore. |

### Controller Layer

Add a Platform backend controller, for example:

```text
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/export/web/DeploymentExportController.java
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/export/web/DeploymentImportController.java
```

Controller methods must have method-level authorization, not only class-level authorization.

### Entity Model

Recommended entities:

- `DeploymentExportEntity`
- `DeploymentImportEntity`
- `DeploymentBundleAuditEntity`

Fields:

- id
- deployment id
- actor id hash
- workspace/customer scope
- export/import mode
- status
- bundle hash
- secret envelope hash
- reason
- expiry
- created/updated timestamps
- failure code and safe message

Do not store decrypted secret payload.

## Manifest Collection Scope

Export should include enough state to recreate:

- Deployment base record.
- Active draft/version/release pointers.
- Template id and target profile id.
- Runtime curated module and prompt preset.
- Runtime env config, with secret refs.
- Connector env config, with secret refs.
- Vectorization runner env/config, with secret refs.
- Marketplace action/data/template plugin installs.
- MCP server references and discovery config.
- Action catalog and schema hashes.
- Data plugin vector spaces and source handles.
- Vectorization source connections, plans, runners, schedules, cursor source config.
- Provider bindings and model config.
- Route/domain config.
- Auth contract:
  - issuers
  - audiences
  - runtime private auth header
  - accepted scopes
  - public/private runtime mode
- Verification policy and latest release evidence.

Do not include:

- Chat turns.
- RAG vectors/documents by default.
- Indexed customer/private data.
- Temporary file URLs.
- OAuth sessions.
- Coolify deployment logs.

## Vectorization And Indexed Data

Default behavior:

- Export vectorization source/plan/runner configuration.
- Do not export indexed vector records.
- On import, recreate vectorization config and require a reindex run from the authoritative source.

Optional future behavior:

- Export vector collection snapshot only for private offline backup, not cross-tenant clone.
- Require separate data-export approval because vectors can contain sensitive embedded content.

For ProdUS:

- Export source connection to the ProdUS safe knowledge export endpoint.
- Export DATA plugin vector space definitions.
- Do not export ProdUS private workspace/finding/evidence content.
- After restore/import, run managed vectorization to repopulate the index.

## Restore And Rollback Behavior

### Before Restore In Place

Platform must create a pre-restore snapshot:

- current deployment manifest
- service config fingerprints
- secret ref inventory without values
- latest release id/version id
- route ownership
- target profile id

### During Restore

1. Validate bundle.
2. Validate target deployment id matches source when restore-in-place.
3. Validate actor permission.
4. Decrypt secret envelope if needed.
5. Create deployment draft.
6. Restore/regenerate secrets into Platform secret store.
7. Recreate service config.
8. Publish version.
9. Apply through target profile.
10. Run verification.

### Rollback

If apply fails:

- Keep previous live release active when possible.
- Mark imported release failed.
- Preserve staging/production isolation.
- Return operator-safe diagnostics.
- Return customer-safe guidance.

If restore partially updates service records:

- Use provider-level reconciliation to restore previous known-good service config.
- Do not delete existing live service until replacement is verified.

## UI Design

### Deployment Workspace

Add an Export/Import panel with:

- Export config only.
- Export encrypted backup.
- Import deployment bundle.
- Restore from bundle.
- Export/import history.

For sealed export:

- Show secret summary by classification.
- Require reason.
- Require recipient public key or Platform recovery key selection.
- Show “plaintext secrets are never downloaded” warning.

For import:

- Upload bundle.
- Show validation report.
- Select import mode:
  - clone as new
  - restore in place
- Show external integration impact.
- Show required secret actions.
- Create draft.

### Partner/Customer UI Boundary

Partner/customer UI may expose:

- config-only export
- sealed export for owned deployment if permission allows
- import preview
- import-as-clone for owned deployments

Partner/customer UI must not expose:

- Coolify app UUIDs as operational controls
- provider API internals
- plaintext secrets
- Platform-wide restore in place without elevated approval

## Import Compatibility Validation

Validation stages:

1. Schema version supported.
2. Bundle integrity valid.
3. Source deployment fingerprint valid.
4. Target workspace exists.
5. Target profile exists.
6. Template exists.
7. Curated module exists.
8. Marketplace plugins and versions exist.
9. Action schema hashes are known or policy allows re-discovery.
10. DATA vector spaces exist.
11. Provider model config is supported.
12. Required secrets can be restored, regenerated, or supplied.
13. Routes/domains can be preserved or remapped.
14. Auth audience/issuer contract is valid.
15. Environment-bound values are remapped.

Validation should produce:

- blocking issues
- warnings
- remapping plan
- secret action plan
- external customer impact
- release gate requirements

## API Error Codes

Use machine-readable errors:

| Code | Meaning |
| --- | --- |
| `DEPLOYMENT_EXPORT_FORBIDDEN` | Actor cannot export this deployment. |
| `SEALED_SECRET_EXPORT_FORBIDDEN` | Actor lacks sealed secret export permission. |
| `SECRET_CLASSIFICATION_FORBIDS_EXPORT` | One or more secret values cannot be exported. |
| `BUNDLE_SCHEMA_UNSUPPORTED` | Import bundle schema is not supported. |
| `BUNDLE_INTEGRITY_FAILED` | Hash/signature/AAD validation failed. |
| `BUNDLE_DECRYPTION_FAILED` | Secret envelope could not be decrypted. |
| `IMPORT_TARGET_PROFILE_MISSING` | Target profile does not exist. |
| `IMPORT_PLUGIN_VERSION_MISSING` | Required Marketplace plugin version is missing. |
| `IMPORT_RESTORE_IN_PLACE_ID_MISMATCH` | Bundle source deployment id differs from restore target. |
| `IMPORT_EXTERNAL_INTEGRATION_REMAP_REQUIRED` | Clone import changes runtime URL/audience. |

## Audit Events

Add audit events:

- `DEPLOYMENT_EXPORT_PREVIEWED`
- `DEPLOYMENT_CONFIG_EXPORTED`
- `DEPLOYMENT_SEALED_BACKUP_EXPORTED`
- `DEPLOYMENT_EXPORT_DOWNLOADED`
- `DEPLOYMENT_IMPORT_PREVIEWED`
- `DEPLOYMENT_IMPORT_DRAFT_CREATED`
- `DEPLOYMENT_RESTORE_IN_PLACE_REQUESTED`
- `DEPLOYMENT_RESTORE_RELEASE_APPLIED`
- `DEPLOYMENT_IMPORT_FAILED`

Audit payload must include:

- actor id hash
- deployment id
- workspace/customer id
- export/import mode
- bundle hash
- secret count by classification
- reason
- result status

Audit payload must not include:

- plaintext secrets
- encrypted secret ciphertext
- temporary URLs
- OAuth tokens

## Implementation Slices

### Slice 1: Schema And Config-Only Export

Deliver:

- Bundle DTOs and JSON schema.
- Manifest collection service.
- Config-only export preview and download.
- Unit tests for manifest shape.
- No secret values.

Verification:

```bash
mvn -f Platfrom/backend/pom.xml -Dtest='*Deployment*Export*Test' test
```

Acceptance:

- Existing ProdUS deployment can export config-only bundle.
- Bundle includes runtime default module, modes, Marketplace installs, vectorization config, and auth contract.
- Bundle does not include secret values.

### Slice 2: Secret Classification Inventory

Deliver:

- Secret classification model.
- Integration with `PlatformSecretService`.
- Export preview secret summary.
- Tests proving forbidden secrets are not exported.

Acceptance:

- Preview shows sealed-eligible, regenerate-recommended, forbidden, and environment-bound counts.
- Logs never contain secret values.

### Slice 3: Sealed Secret Export

Deliver:

- Envelope encryption service.
- Recipient public key support.
- Platform recovery public key support if configured.
- Sealed backup export endpoint.
- Download TTL.

Acceptance:

- Sealed bundle can be downloaded.
- Secret payload cannot be read from bundle without private key.
- Decrypt test verifies secret payload round trip in memory.

### Slice 4: Import Preview

Deliver:

- Bundle parser.
- Schema/integrity validation.
- Compatibility report.
- Secret action plan.
- External integration impact report.

Acceptance:

- ProdUS clone preview reports runtime URL/audience change required.
- ProdUS restore-in-place preview reports no customer env change when route/audience preserved.

### Slice 5: Config-Only Clone Import

Deliver:

- Create deployment draft from config-only bundle.
- Remap deployment id, route, target profile, and assertion audience.
- Missing secret tasks.

Acceptance:

- Imported draft can be validated.
- No secrets are restored.
- Release cannot apply until required secrets are supplied or regenerated.

### Slice 6: Sealed Restore In Place

Deliver:

- Decrypt sealed secrets server-side.
- Restore secret records according to policy.
- Create restore draft/version/release.
- Preserve deployment id/audience/route where possible.
- Pre-restore snapshot.

Acceptance:

- Restore in place of staging test deployment passes verification.
- Existing direct runtime smoke still works after restore.
- Failed restore leaves previous live release available.

### Slice 7: UI

Deliver:

- Deployment workspace export/import panel.
- Secret classification preview.
- Import validation report.
- External integration impact display.
- Audit/history view.

Acceptance:

- UI never shows plaintext secrets.
- Operator can export config-only and sealed backup.
- Operator can import preview and create draft.

### Slice 8: Production Gate And Docs

Deliver:

- Development guide.
- Operator guide.
- Private handoff procedure for recovery keys.
- Production restore runbook.

Acceptance:

- Production restore requires release gate.
- Restore evidence includes audit events, release id, verification status, and smoke results.

## Testing Strategy

### Unit Tests

- Manifest serializer produces stable canonical JSON.
- Secret classifier classifies known secret names correctly.
- Forbidden secret types are excluded.
- Envelope encryption round trip.
- Integrity failure rejects import.
- Wrong recipient key rejects import.
- Restore-in-place id mismatch rejects import.

### Integration Tests

- Export config-only for seeded deployment.
- Export sealed backup for seeded deployment.
- Import config-only clone creates draft with missing secret tasks.
- Import sealed clone creates draft with restored/regenerated secret plan.
- Restore-in-place preserves deployment id/audience.

### Live Verification

Planned live verification on staging:

1. Export ProdUS config-only bundle.
2. Export ProdUS sealed backup using operator public key.
3. Import preview as clone.
4. Confirm preview says ProdUS env changes are required for clone.
5. Import preview as restore-in-place.
6. Confirm preview says no ProdUS env changes if route/audience preserved.
7. Restore to a disposable staging deployment or controlled restore window.
8. Run runtime smoke:
   - `/actuator/health`
   - `/api/chat/me/auth-context`
   - `/api/chat/me/query`
   - `/api/chat/me/query-once`
9. Run managed vectorization reindex if vectorization config was restored.

Executed live verification on 2026-06-01:

- Deployed Platform backend and Platform UI on staging from commit `00b53ae60`.
- Verified backend health at `https://loomai-platform-backend.46.224.145.148.sslip.io/actuator/health`.
- Verified Platform UI health at `https://loomai-platform-ui.46.224.145.148.sslip.io/health`.
- Ran config-only export preview for `dep-7706fafb`: 7 secret references discovered, 0 secret values included.
- Ran config-only export for `dep-7706fafb`: bundle status `READY`, no sealed envelope, 0 included secret values.
- Ran sealed backup export for `dep-7706fafb`: bundle status `READY`, sealed envelope present, 3 exportable secret values encrypted into the envelope.
- Verified the sealed backup has an authenticated encrypted `secretEnvelope`; plaintext secret values are not rendered in the main manifest.
- Ran config-only clone import preview: schema and integrity valid, secrets not readable, customer environment changes required.
- Ran sealed clone import preview using the matching private key: schema and integrity valid, sealed secrets readable, customer environment changes required.
- Ran restore-in-place preview: schema and integrity valid, target deployment `dep-7706fafb`, customer environment changes not required.
- Ran real config-only clone import: created draft `drf-c4c4ced2` on temporary clone deployment `dep-21baf7fd`.
- Archived temporary clone deployment `dep-21baf7fd` after verification.
- Re-ran focused backend tests, full backend test suite, UI production build, and `git diff --check`.

## Release Gates

Gate status after staging verification:

- Config-only export does not leak secrets: passed.
- Sealed export round trip is proven: passed by sealed import preview with private-key decrypt.
- Import preview catches route/audience changes: passed for clone import.
- Restore-in-place preserves direct runtime integrations in staging preview: passed for `dep-7706fafb`.
- Clone import clearly reports customer env changes: passed.
- Production restore cannot bypass release verification: implemented by draft-only import/restore; production apply remains governed by existing release gates.
- Audit events exist for every export/download/import/restore: implemented through Platform audit publication.
- Docs tell operators which secrets are exportable, regenerated, forbidden, or environment-bound: passed.

Remaining production evidence:

- A real production restore/promotion should still be executed during the production deployment window before using this as the sole disaster-recovery path for production customers.

## Open Decisions

1. Which recipient key type ships first: operator RSA public key, Platform recovery key, or both?
2. Do we enable passphrase-based sealed export, or keep it disabled until KMS/recovery key support is mature?
3. Should config-only exports be downloadable by customer admins, or only partner/platform operators?
4. Should restore-in-place require two-person approval in production?
5. Do we implement stable runtime aliases before clone imports are used by external customers?

## Recommendation

Ship in this order:

1. Config-only export.
2. Secret classification preview.
3. Sealed backup export.
4. Import preview.
5. Config-only clone import.
6. Sealed restore-in-place.
7. UI and production runbook.

For ProdUS and similar direct-runtime customers, require restore-in-place for backup/restore continuity until stable runtime aliases exist.
