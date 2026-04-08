# Marketplace Domain Model And API Schema Plan

Status: planning document (2026-04-08)

This document defines the first concrete domain model, persistence model, and API shape for the marketplace workstream.

It is the natural next implementation step after:

- `doc/Productization/future-work/MarketPlace/MARKETPLACE_EXECUTION_SEQUENCE_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/EXTERNAL_PLUGIN_PUBLISHER_MODEL_PLAN.md`
- `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE_IMPLEMENTATION_PLAN.md`

This document is intentionally biased toward Wave 1 and Wave 2 execution.

That means it focuses on:

- first-party and reviewed plugin catalog storage
- deployment-scoped installs
- compiler inputs and outputs
- `shellConfig` and `ShellContribution`
- install, resolve, preview, publish, apply boundaries

It does not attempt to fully finish:

- payouts
- legal publisher contracts
- open marketplace moderation at scale

---

## 1) Executive Summary

The marketplace should persist three layers of state:

1. catalog state
2. deployment install state
3. resolved deployment state

Recommended source-of-truth rule:

- catalog tables store approved plugin definitions and immutable version snapshots
- deployment install tables store deployment-scoped install intent, config, secret references, and status
- deployment drafts and published versions store the resolved live behavior

The compiler sits between install state and deployment state.

It must output only deployment-safe, non-secret resolved behavior such as:

- `actionsConfig`
- `knowledgeSourceConfig`
- `shellConfig`

This keeps the marketplace compatible with the existing platform lifecycle:

- draft
- publish
- apply
- verify

---

## 2) Scope

This document explicitly covers:

- domain entities
- table-level persistence shape
- status models
- API endpoints
- payload contracts
- compiler input and output contracts
- shell contribution schema
- impact preview schema

This document explicitly does not cover:

- final SQL dialect details
- exact ORM class layout
- queue or job runner selection
- billing provider specifics
- legal or finance compliance workflows

---

## 3) Core Domain Model

The marketplace should model four domain groups.

### 3.1 Catalog domain

Core entities:

- `MarketplacePublisher`
- `MarketplacePlugin`
- `MarketplacePluginVersion`
- `MarketplacePluginPrice`
- `MarketplacePluginAsset`

### 3.2 Deployment install domain

Core entities:

- `DeploymentPluginInstall`
- `DeploymentPluginInstallSecretRef`
- `DeploymentPluginImpactSnapshot`

### 3.3 Compilation domain

Core entities:

- `PluginCompileInput`
- `PluginCompileOutput`
- `ActionContribution`
- `KnowledgeSourceContribution`
- `ShellContribution`

### 3.4 Resolved deployment domain

Resolved targets:

- deployment draft `actionsConfig`
- deployment draft `knowledgeSourceConfig`
- deployment draft `shellConfig`
- published version equivalents of the same fields

The marketplace should not create a separate live plugin runtime state outside those deployment records.

---

## 4) Persistence Model

### 4.1 `marketplace_publisher`

Purpose:

- verified publisher identity and support metadata

Recommended fields:

- `id`
- `publisher_key`
- `display_name`
- `publisher_type`
- `verification_status`
- `support_email`
- `support_url`
- `created_at`
- `updated_at`

Recommended `publisher_type` values:

- `FIRST_PARTY`
- `PARTNER`
- `THIRD_PARTY`
- `COMMUNITY`

Recommended `verification_status` values:

- `PENDING`
- `VERIFIED`
- `RESTRICTED`
- `SUSPENDED`

### 4.2 `marketplace_plugin`

Purpose:

- stable plugin identity across versions

Recommended fields:

- `id`
- `plugin_key`
- `publisher_id`
- `plugin_type`
- `category_key`
- `display_name`
- `short_description`
- `status`
- `created_at`
- `updated_at`

Recommended `plugin_type` values:

- `TEMPLATE`
- `ACTION`
- `DATA`

Recommended `status` values:

- `DRAFT`
- `PUBLISHED`
- `DEPRECATED`
- `REMOVED`

### 4.3 `marketplace_plugin_version`

Purpose:

- immutable version snapshot and compiled catalog payload

Recommended fields:

- `id`
- `plugin_id`
- `version`
- `schema_version`
- `release_status`
- `manifest_json`
- `compiled_snapshot_json`
- `compatibility_json`
- `artifact_hash`
- `reviewed_at`
- `created_at`

Recommended `release_status` values:

- `DRAFT`
- `SUBMITTED`
- `VALIDATED`
- `REJECTED`
- `PUBLISHED`
- `DEPRECATED`
- `REMOVED`

Important rule:

- `manifest_json` is the submitted publisher-facing source
- `compiled_snapshot_json` is the platform-owned normalized representation used during install resolution

### 4.4 `marketplace_plugin_price`

Purpose:

- pricing metadata by plugin version or plugin line

Recommended fields:

- `id`
- `plugin_id`
- `plugin_version_id` optional
- `price_model`
- `currency_code`
- `amount_minor`
- `billing_period`
- `trial_days`
- `active`
- `created_at`

Recommended `price_model` values:

- `FREE`
- `ONE_OFF`
- `RECURRING`

### 4.5 `deployment_plugin_install`

Purpose:

- deployment-scoped install intent and lifecycle

Recommended fields:

- `id`
- `deployment_id`
- `plugin_id`
- `plugin_version_id`
- `install_status`
- `user_config_json`
- `entitlement_status`
- `compiled_contribution_hash`
- `resolved_into_draft_at`
- `activated_at`
- `removed_at`
- `created_by`
- `created_at`
- `updated_at`

Recommended `install_status` values:

- `DRAFT`
- `CONFIG_REQUIRED`
- `READY_TO_RESOLVE`
- `RESOLVED_TO_DRAFT`
- `ACTIVE`
- `SUSPENDED`
- `REMOVAL_PENDING`
- `REMOVED`
- `ERROR`

Recommended `entitlement_status` values:

- `NOT_REQUIRED`
- `ACTIVE`
- `TRIAL`
- `PAST_DUE`
- `SUSPENDED`
- `CANCELLED`

Important rule:

- `user_config_json` stores only non-secret operator inputs
- secret material must not be stored here

### 4.6 `deployment_plugin_install_secret_ref`

Purpose:

- track required secret references without storing secret values

Recommended fields:

- `id`
- `install_id`
- `secret_purpose`
- `secret_ref_id`
- `required`
- `resolution_status`
- `created_at`
- `updated_at`

Recommended `resolution_status` values:

- `MISSING`
- `BOUND`
- `INVALID`
- `STALE`

### 4.7 `deployment_plugin_impact_snapshot`

Purpose:

- store previewable draft impact from the latest resolution pass

Recommended fields:

- `id`
- `install_id`
- `snapshot_status`
- `warnings_json`
- `changed_actions_json`
- `changed_knowledge_sources_json`
- `changed_shell_config_json`
- `changed_prompt_defaults_json`
- `compatibility_summary_json`
- `generated_at`

Recommended `snapshot_status` values:

- `CURRENT`
- `STALE`
- `ERROR`

---

## 5) Deployment Draft Targets

The marketplace compiler should resolve only into normal deployment draft fields.

### 5.1 `actionsConfig`

Resolved from:

- action plugin compiled contributions

Contains:

- action ids
- input and result contracts
- execution adapter references
- policy metadata
- action presentation metadata where needed

### 5.2 `knowledgeSourceConfig`

Resolved from:

- data plugin compiled contributions

Contains:

- source ids
- source types
- provider or shared-handle references
- query filters
- attribution metadata
- ranking hints

### 5.3 `shellConfig`

Resolved from:

- template, action, and data plugin `ShellContribution` fragments
- deployment-authored shell settings

Contains:

- branding
- enabled built-in modules
- greeting and starter prompts
- action presentation hints
- evidence presentation hints
- built-in module mappings
- built-in card or UI block mappings

Important rule:

- only fixed platform-owned module ids and card or UI block ids may appear in `shellConfig`

---

## 6) Compiler Contracts

### 6.1 `PluginCompileInput`

Recommended structure:

```json
{
  "deploymentId": "dep_123",
  "pluginKey": "calendly-booking",
  "pluginVersion": "1.2.0",
  "deploymentContext": {
    "customerId": "cust_1",
    "tenantId": "tenant_1",
    "deploymentTarget": "HOSTED",
    "authMode": "PRIVATE_RUNTIME",
    "enabledCapabilities": ["actions", "shell", "secrets"]
  },
  "userConfig": {
    "eventType": "demo-call"
  },
  "secretRefs": [
    {
      "purpose": "api_key",
      "secretRefId": "sec_456"
    }
  ]
}
```

### 6.2 `PluginCompileOutput`

Recommended structure:

```json
{
  "pluginKey": "calendly-booking",
  "pluginVersion": "1.2.0",
  "contributionHash": "sha256:...",
  "actions": [],
  "knowledgeSources": [],
  "shellContributions": [],
  "warnings": [],
  "compatibilityStatus": "COMPATIBLE"
}
```

Recommended `compatibilityStatus` values:

- `COMPATIBLE`
- `COMPATIBLE_WITH_WARNINGS`
- `INCOMPATIBLE`

### 6.3 `ShellContribution`

Recommended structure:

```json
{
  "branding": {
    "title": "Acme Assistant",
    "themeKey": "acme-blue"
  },
  "enabledModules": [
    {
      "moduleId": "products",
      "enabled": true
    }
  ],
  "actionPresentation": [
    {
      "actionId": "calendly_create_booking",
      "cardStyleId": "booking-result-card",
      "moduleMappingId": "actions"
    }
  ],
  "evidencePresentation": [
    {
      "sourceId": "autotrader_shared_uk",
      "cardStyleId": "vehicle-listing-card",
      "moduleMappingId": "docs"
    }
  ]
}
```

Important rule:

- `moduleId`, `cardStyleId`, and `moduleMappingId` must reference fixed platform-owned registries

---

## 7) API Shape

The API should be split into catalog APIs, deployment install APIs, and template bootstrap APIs.

### 7.1 Catalog APIs

Recommended endpoints:

- `GET /api/marketplace/plugins`
- `GET /api/marketplace/plugins/{pluginKey}`
- `GET /api/marketplace/plugins/{pluginKey}/versions/{version}`
- `GET /api/marketplace/categories`

Recommended response fields for list items:

- plugin key
- display name
- type
- category
- publisher summary
- current published version
- pricing summary
- icon URL
- short description

### 7.2 Deployment install APIs

Recommended endpoints:

- `GET /api/deployments/{deploymentId}/marketplace-installs`
- `GET /api/deployments/{deploymentId}/marketplace-installs/{installId}`
- `POST /api/deployments/{deploymentId}/marketplace-installs`
- `PUT /api/deployments/{deploymentId}/marketplace-installs/{installId}`
- `DELETE /api/deployments/{deploymentId}/marketplace-installs/{installId}`
- `POST /api/deployments/{deploymentId}/marketplace-installs/{installId}/resolve`
- `GET /api/deployments/{deploymentId}/marketplace-impact`

### 7.3 Template bootstrap API

Recommended endpoint:

- `POST /api/marketplace/templates/{pluginKey}/bootstrap`

Recommended behavior:

- accept compact bootstrap choices
- seed a normal deployment draft
- return the new deployment id and draft summary

---

## 8) Request And Response Shapes

### 8.1 `POST /api/deployments/{deploymentId}/marketplace-installs`

Recommended request:

```json
{
  "pluginKey": "calendly-booking",
  "pluginVersion": "1.2.0",
  "userConfig": {
    "eventType": "demo-call"
  },
  "secretRefs": [
    {
      "purpose": "api_key",
      "secretRefId": "sec_456"
    }
  ]
}
```

Recommended response:

```json
{
  "installId": "mpi_123",
  "installStatus": "CONFIG_REQUIRED",
  "impactSnapshotStatus": "STALE"
}
```

### 8.2 `POST /api/deployments/{deploymentId}/marketplace-installs/{installId}/resolve`

Recommended response:

```json
{
  "installId": "mpi_123",
  "installStatus": "RESOLVED_TO_DRAFT",
  "compatibilityStatus": "COMPATIBLE",
  "impactSnapshot": {
    "changedActions": [],
    "changedKnowledgeSources": [],
    "changedShellConfig": [],
    "warnings": []
  }
}
```

### 8.3 `GET /api/deployments/{deploymentId}/marketplace-impact`

Recommended response:

```json
{
  "deploymentId": "dep_123",
  "installs": [
    {
      "installId": "mpi_123",
      "pluginKey": "calendly-booking",
      "pluginVersion": "1.2.0",
      "changedActions": [],
      "changedKnowledgeSources": [],
      "changedShellConfig": [],
      "warnings": []
    }
  ]
}
```

---

## 9) Validation Rules

The backend should validate at three layers.

### 9.1 Catalog validation

- manifest schema validity
- version immutability
- compatibility declaration validity
- approved adapter type validation
- fixed-registry reference validation for shell-facing fields

### 9.2 Install validation

- deployment role and permission checks
- tenant boundary checks
- required secret reference presence
- required user config presence
- entitlement status checks

### 9.3 Resolution validation

- compiler compatibility checks
- fixed module and card registry resolution
- target deployment capability checks
- draft write safety checks

---

## 10) Access Control

Recommended access model:

- catalog browsing: role-safe read access
- install create and update: deployment-scoped operator or admin role
- resolve and impact preview: deployment-scoped operator or admin role
- publish and apply: existing deployment release permissions
- external publisher management: publisher-specific and platform-admin-specific later

Recommended rule:

- marketplace should not weaken any existing deployment role or approval boundary

---

## 11) Wave Mapping

This schema doc maps directly to the marketplace sequence.

### Wave 1

Delivered by this document:

- catalog entities
- install entities
- compiler contracts
- `shellConfig`
- impact snapshot shape

### Wave 2

Enabled by this document:

- first-party template bootstrap
- first-party action plugin install and uninstall
- shell-aware preview

### Wave 3

Enabled by this document:

- data plugin resolution
- trusted shell extension surfaces through fixed registries

### Later waves

Extended later by:

- external publisher APIs
- entitlement and billing records
- publisher review and moderation workflows

---

## 12) Recommended First Build Slice

The first concrete implementation slice should be:

1. create catalog and install persistence
2. add compiler interfaces and compiled snapshot storage
3. add draft targets for `actionsConfig`, `knowledgeSourceConfig`, and `shellConfig`
4. add install create and resolve APIs
5. add impact snapshot generation including shell deltas

This is the smallest slice that proves marketplace installation is a normal deployment-governed platform path.

---

## 13) Non-Goals

This plan intentionally does not introduce:

- executable runtime plugins
- executable shell plugins
- direct live installation without publish and apply
- secret values in marketplace persistence
- publisher-defined module renderers
- microfrontend plugin loading

If any of those become required for Wave 1 or Wave 2, the architecture is drifting away from the current marketplace baseline.
