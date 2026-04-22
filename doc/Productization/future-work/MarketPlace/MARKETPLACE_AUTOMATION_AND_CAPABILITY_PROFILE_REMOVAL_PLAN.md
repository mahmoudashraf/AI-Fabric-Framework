# Marketplace Automation And Capability Profile Removal Plan

Status: planned cleanup

Date: 2026-04-15

## 1) Goal

Remove marketplace concepts that do not have a real runtime consumer:

- public plugin type: `AUTOMATION`
- public capability profile metadata:
  - `SURFACE`
  - `POLICY_LOGIC`
  - `ANALYTICS_EVENT`

After this cleanup, marketplace should expose only plugin types that map to real runtime-supported deployment behavior:

- `TEMPLATE`
- `ACTION`
- `DATA`

The governing rule is simple:

- platform must not advertise, install, compile, publish, or verify marketplace features that are not executable or consumable in runtime

## 2) Why Remove

Current state:

- `AUTOMATION` is implemented only in the control plane
- `automationConfig` is compiled and published, but runtime does not execute it
- `SURFACE`, `POLICY_LOGIC`, and `ANALYTICS_EVENT` are manifest metadata only
- runtime does not consume those capability profile labels directly

That creates false product surface area:

- catalog says more is supported than runtime really supports
- starter catalog includes a plugin that does not produce live behavior
- verification proves config shape, not runtime behavior

## 3) Scope

This removal plan covers:

- marketplace taxonomy
- manifest validation
- seeded catalog data
- install/compiler flow
- deployment config model
- artifact generation
- UI filtering and badges
- verification scripts/tests/docs

This plan does not remove:

- runtime-supported shell behavior already compiled through `shellConfig`
- runtime-supported data behavior already compiled through `knowledgeSourceConfig`
- runtime-supported action behavior already compiled through `actionsConfig` and routing

## 4) Removal Principles

1. Remove the unsupported concepts from public product surfaces first.
2. Stop accepting new unsupported marketplace definitions before deleting stored model support.
3. Clean out seeded catalog/plugin examples that depend on unsupported concepts.
4. Remove unused deployment config branches only after catalog/install flow no longer produces them.
5. Keep the end state simple: marketplace contributions must compile only into runtime-supported contracts.

## 5) Target End State

### 5.1 Public marketplace taxonomy

Allowed plugin types:

- `TEMPLATE`
- `ACTION`
- `DATA`

Removed public plugin types:

- `AUTOMATION`

Removed public capability profile labels:

- `SURFACE`
- `POLICY_LOGIC`
- `ANALYTICS_EVENT`

### 5.2 Allowed deployment config outputs

Marketplace installs may compile only into:

- `actionsConfig`
- `entityConfig`
- `knowledgeSourceConfig`
- `shellConfig`
- `marketplaceDatasetConfig`

Marketplace installs must no longer compile into:

- `automationConfig`

## 6) Current Impacted Areas

### 6.1 Backend domain and compiler

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/MarketplaceManifestService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/DeploymentMarketplaceDraftCompilerService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/DeploymentMarketplaceInstallService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/MarketplaceCatalogService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/model/MarketplacePluginPermissionsSummary.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentDraftValidationService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentConfigCompiler.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/model/DeploymentDraftResponse.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/model/UpdateDeploymentDraftRequest.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/entity/DeploymentDraftEntity.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/entity/DeploymentVersionEntity.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/DeploymentArtifactsController.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentArtifactService.java`

### 6.2 Seeded migrations and catalog data

- `Platfrom/backend/src/main/resources/db/migration/V31__deployment_automation_config.sql`
- `Platfrom/backend/src/main/resources/db/migration/V32__marketplace_automation_and_starter_plugins.sql`
- `Platfrom/backend/src/main/resources/db/migration/V33__marketplace_data_plugin_entity_contributions.sql`
- `Platfrom/backend/src/main/resources/db/migration/V34__marketplace_template_verified_authz.sql`
- `Platfrom/backend/src/main/resources/db/migration/V35__marketplace_support_template_shell_guidance.sql`
- `Platfrom/backend/src/main/resources/db/migration/V36__marketplace_dataset_productization.sql`

### 6.3 UI

- `Platfrom/ui/src/pages/MarketplacePage.tsx`
- `Platfrom/ui/src/api/platformApi.ts`

### 6.4 Verification and fixtures

- `scripts/verify-marketplace-install-flow.sh`
- `scripts/fixtures/marketplace/mkp-data-policy-folder-1.0.0.json`
- backend tests under `Platfrom/backend/src/test/java/com/ai/fabric/platform/backend/marketplace/`

### 6.5 Docs

- `doc/Productization/future-work/MarketPlace/README.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_DEFAULT_STARTER_CATALOG_PLAN.md`
- any plan that still lists `AUTOMATION` or capability profiles as productized marketplace surfaces

## 7) Implementation Sequence

### Wave 1: Freeze Unsupported Marketplace Definitions

Goal:

- stop new unsupported definitions from entering the system

Changes:

- remove `AUTOMATION` from allowed marketplace plugin types in manifest parsing
- remove `SURFACE`, `POLICY_LOGIC`, and `ANALYTICS_EVENT` from accepted public manifest capability profiles
- remove permission fields tied only to those concepts:
  - `contributesAutomation`
  - `contributesSurfaceCapabilities`
  - `contributesPolicyLogicCapabilities`
  - `contributesAnalyticsEventCapabilities`
- reject any new manifest that still declares them

Acceptance:

- publisher submission for `AUTOMATION` fails validation
- publisher submission with capability profiles fails validation
- existing supported `TEMPLATE` / `ACTION` / `DATA` manifests still validate

### Wave 2: Remove Public Product Surface

Goal:

- stop showing unsupported concepts in UI/API

Changes:

- remove `automation` category from marketplace catalog summaries
- remove `AUTOMATION` filter and labels from Marketplace UI
- remove capability profile badges from UI and API summaries
- remove starter-catalog references that describe automation as shipped

Acceptance:

- marketplace UI shows only `TEMPLATE`, `ACTION`, `DATA`
- plugin detail responses no longer expose capability profile labels
- no public catalog entry remains typed as `AUTOMATION`

### Wave 3: Clean Seeded Catalog Data

Goal:

- remove unsupported seeded examples and stale metadata

Changes:

- remove seeded plugin `mkp-automation-order-retention` from active catalog data
- strip `capabilityProfiles` from seeded manifests for supported plugin types
- strip now-unused permission fields from seeded manifests
- revise starter catalog to only include runtime-backed examples

Acceptance:

- seeded catalog contains only runtime-backed plugin types
- no seeded manifest carries capability profile labels

### Wave 4: Remove Marketplace Compilation To `automationConfig`

Goal:

- stop marketplace installs from generating unsupported deployment config

Changes:

- delete `AUTOMATION` branch from [DeploymentMarketplaceDraftCompilerService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/DeploymentMarketplaceDraftCompilerService.java)
- remove automation contribution parsing from [MarketplaceManifestService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/MarketplaceManifestService.java)
- remove automation install counters and impact reporting from marketplace install summaries
- update tests and live verifier to stop expecting automation compilation

Acceptance:

- marketplace install flow compiles only `actionsConfig`, `entityConfig`, `knowledgeSourceConfig`, `shellConfig`, and `marketplaceDatasetConfig`
- install impact no longer reports automation plugin counts or automation ids

### Wave 5: Remove Deployment-Level Automation Config Support From Platform

Goal:

- align platform deployment model with runtime-supported contracts only

Changes:

- remove `automationConfig` from:
  - deployment draft request/response models
  - draft/version entities
  - config compiler manifest output
  - artifact bundle summaries
  - artifact delivery endpoints
  - draft validation
- deprecate and then remove `ai-automation-config.json`
- add cleanup migration:
  - drop `automation_config_json` from draft/version tables
  - remove historical seeded automation plugin rows and installs

Acceptance:

- platform no longer stores or publishes `automationConfig`
- no deployment manifest contains `automationConfig`
- no artifact bundle exposes `automationArtifactUrl`

### Wave 6: Verification And Live Cleanup

Goal:

- prove the platform still works correctly after removal

Changes:

- update [verify-marketplace-install-flow.sh](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/scripts/verify-marketplace-install-flow.sh) to install only:
  - one `TEMPLATE`
  - one `ACTION`
  - one or more `DATA`
- remove automation assertions from draft/publish/apply verification
- rerun live catalog/install verification on production
- verify:
  - published manifest has no `automationConfig`
  - catalog has no `AUTOMATION`
  - installs still integrate correctly into deployment config

Acceptance:

- local regression suite passes
- live marketplace install flow passes
- live catalog exposes only supported plugin types

## 8) Existing Live Data Cleanup

We already created real rows and manifests for:

- `mkp-automation-order-retention`
- deployment drafts and versions containing `automationConfig`
- marketplace install-flow scripts and tests expecting automation

Cleanup actions needed:

- mark seeded automation plugin inactive or remove it from catalog entirely
- delete seeded automation version rows
- delete automation installs from internal test/demo deployments
- scrub `automationConfig` from validation/demo deployments used in live verification

This cleanup should be done as a dedicated platform migration plus one controlled live cleanup run.

## 9) Risks

1. Partial removal leaves orphaned UI/API fields that still imply support.
2. Removing manifest support before cleaning seeded rows can break catalog rendering.
3. Removing deployment `automationConfig` too early can break old verification scripts and tests.
4. Data cleanup must happen intentionally or live validation deployments will retain stale installs.

## 10) Recommended Order

Recommended execution order:

1. Wave 1
2. Wave 2
3. Wave 3
4. Wave 4
5. Wave 6 local verification update
6. Wave 5 schema/data cleanup
7. Wave 6 live verification

Reason:

- first stop new unsupported definitions
- then remove public exposure
- then remove compilation
- then remove stored model support

## 11) Success Criteria

The cleanup is complete when all of the following are true:

- marketplace public taxonomy is exactly `TEMPLATE`, `ACTION`, `DATA`
- no manifest parser accepts `AUTOMATION`
- no manifest parser accepts capability profile labels
- no catalog row or UI screen presents `AUTOMATION`
- no deployment draft/version/model stores `automationConfig`
- no artifact bundle serves `ai-automation-config.json`
- live marketplace verification passes using only runtime-backed plugin types
