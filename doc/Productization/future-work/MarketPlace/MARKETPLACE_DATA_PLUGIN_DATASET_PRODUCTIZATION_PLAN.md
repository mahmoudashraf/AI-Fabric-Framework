# Marketplace Data Plugin Dataset Productization Plan

Status: implementation-baseline delivered on branch, pending deployed live proof refresh (2026-04-15)

## 1) Scope

This plan covers the missing product layer for marketplace `DATA` plugins:

- plugin-owned dataset storage boundaries
- packaged seed datasets
- external sync connectors
- tenant/plugin dataset lifecycle
- apply-time readiness and verification

This plan does not change the runtime boundary:

- runtime still consumes resolved `knowledgeSourceConfig`
- runtime still does not know marketplace catalog or install records
- runtime still does not load publisher code

---

## 2) Problem Statement

Current branch state:

- `DATA` plugins can compile into `knowledgeSourceConfig`
- runtime can query `deployment-private-vector` and `shared-index`
- multi-source retrieval and attribution work
- installed `DATA` plugins resolve into `marketplaceDatasetConfig`
- apply now triggers dataset handle creation plus seed or sync before release verification
- starter plugins such as `mkp-data-help-center`, `mkp-data-commerce-catalog`, and `mkp-data-policy-folder` can populate their own plugin-scoped tenant-shared dataset handles
- repeated applies skip unchanged ready datasets
- changed datasets reindex predictably with tracked-document cleanup
- external SQL and folder-backed datasets now support scheduled resync on active verified deployments using the platform default cadence

Remaining gap is operational proof after deploy, not the control-plane design.

---

## 3) Target Model

Each `DATA` plugin owns its own logical storage boundary.

Required rule:

- deployments do not share one generic marketplace data pool
- each plugin gets a distinct platform-managed dataset boundary
- deployments that install the same plugin read from that plugin-owned dataset
- other plugins do not read or write that dataset by default

Recommended scope model:

- storage scope: `PLUGIN_SCOPED`
- sharing scope: `TENANT_SHARED`

Recommended handle shape:

- `plugin/<pluginId>/tenant/<tenantId>/<datasetId>/<entityType>`

Important implementation rule:

- this should usually be a logical boundary such as collection, namespace, or provider-native scoped handle
- it should not require a completely separate physical vector cluster per plugin by default

---

## 4) Supported Dataset Ingestion Modes

Launch should support these dataset modes for `DATA` plugins.

### 4.1 Packaged Seed Dataset

Use for:

- first-party starter plugins
- approved partner plugins with static content bundles

Example:

- `mkp-data-help-center`
- `mkp-data-commerce-catalog`

Characteristics:

- versioned corpus artifact shipped with the plugin version
- imported and vectorized by the platform
- idempotent reseed when dataset version changes

### 4.2 External Sync Connector: SQL

Use for:

- plugin datasets sourced from platform-approved relational systems
- structured records that should be normalized into searchable documents

Example sources:

- PostgreSQL
- MySQL
- SQL Server

Required model:

- plugin declares approved SQL connector usage
- install config provides connection reference and query mapping config
- platform sync job reads rows, normalizes them, and vectorizes them into the plugin-owned dataset handle

Required guardrail:

- no raw arbitrary SQL against unknown networks by default
- connectors must use platform-approved connection definitions and secret refs

### 4.3 External Sync Connector: Folder Of Files

Use for:

- policy document bundles
- help-center markdown/html/pdf exports
- curated documentation drops

Required launch forms:

- local platform-managed folder reference
- object-storage folder reference

Required model:

- plugin declares file-sync capability
- install config provides approved folder handle or storage reference
- platform ingests supported file types, chunks content, enriches metadata, and vectorizes the result into the plugin-owned dataset handle

Required guardrail:

- no arbitrary filesystem access from publisher bundles
- only platform-approved folder or object-storage connectors may be used

### 4.4 Later, Not Launch

Do not treat these as launch requirements:

- arbitrary web crawling
- arbitrary publisher-hosted ingestion code
- arbitrary long-running sync workers per plugin
- unrestricted SaaS connectors

---

## 5) Manifest Contract Additions

`DATA` plugin manifests should gain a first-class dataset package model.

Recommended additions:

- `datasets`
  - `datasetId`
  - `entityType`
  - `storageScope`
  - `sharingScope`
  - `handleTemplate`
  - `ingestionMode`
  - `vectorizationProfile`
  - `updateStrategy`
  - `seedDatasetRef` for packaged datasets
  - `syncConnectorTypes` for connector-backed datasets

Recommended launch values:

- `storageScope=PLUGIN_SCOPED`
- `sharingScope=TENANT_SHARED`
- `ingestionMode=PACKAGED_SEED | EXTERNAL_SYNC_SQL | EXTERNAL_SYNC_FOLDER`
- `updateStrategy=UPSERT_BY_ID`

`knowledgeSources` contributions should reference the dataset package they resolve through.

Recommended rule:

- `knowledgeSources` remain the retrieval-facing contract
- `datasets` become the control-plane ingestion and storage contract

---

## 6) Control-Plane Data Model

Add dataset lifecycle tables.

Recommended launch set:

- `platform_marketplace_plugin_datasets`
  - plugin version dataset definitions resolved from the manifest
- `platform_marketplace_dataset_handles`
  - tenant/plugin resolved storage handle records
- `platform_marketplace_dataset_sync_runs`
  - seed and sync executions
- `platform_marketplace_dataset_documents`
  - optional document index tracking only if needed for cleanup and differential sync

Minimum fields for handle records:

- `id`
- `plugin_id`
- `plugin_version_id`
- `dataset_id`
- `customer_id`
- `tenant_id`
- `storage_scope`
- `sharing_scope`
- `handle_ref`
- `entity_type`
- `status`
- `dataset_hash`
- `last_sync_at`
- `last_error`

Minimum fields for sync runs:

- `id`
- `dataset_handle_id`
- `release_id`
- `deployment_id`
- `sync_type`
- `status`
- `document_count`
- `started_at`
- `completed_at`
- `error_message`

---

## 7) Apply-Time Lifecycle

This is the productization-critical sequence.

Required apply flow for deployments with installed `DATA` plugins:

1. resolve installed data plugin contributions into deployment draft
2. publish version normally
3. start apply normally
4. ensure plugin-owned tenant-shared dataset handle exists
5. run initial seed or sync if dataset is not ready or dataset version changed
6. vectorize imported content into the plugin-owned handle
7. only then mark dataset-ready for the release
8. run post-apply verification against real retrieval

Important rule:

- deployment release readiness must include dataset readiness when installed `DATA` plugins require it

Recommended behavior on uninstall:

1. unlink the knowledge source contribution from the deployment draft
2. publish and apply normally
3. keep the tenant/plugin dataset handle if other active installs still reference it
4. only garbage-collect data when no live install requires that dataset anymore

---

## 8) Connector Productization Rules

External sync connectors should be platform-owned connector classes, not publisher-owned code.

Launch connector classes:

- `SQL_QUERY`
- `FILE_FOLDER`

What plugin creators define:

- approved connector type
- dataset schema mapping
- normalization hints
- metadata mapping
- vectorization profile reference

What plugin creators do not define:

- raw executable ingestion code
- arbitrary network clients
- arbitrary filesystem traversal logic
- arbitrary scheduler implementations

Install config should provide:

- connection secret refs
- folder or object-storage reference
- approved query or mapping config
- sync frequency policy if allowed

Platform should provide:

- connector execution
- normalization
- chunking
- vectorization
- retries
- observability

---

## 9) Verification Requirements

Config-only verification is not enough for `DATA` plugins.

Required live checks:

- dataset handle exists and is ready
- at least one sync run succeeded
- at least one real query returns documents from the installed data plugin source
- result metadata includes:
  - `knowledgeSourceId`
  - `knowledgeSourceAdapterType`
  - `knowledgeSourceHandleRef`
- diagnostics show the expected source succeeded

Recommended rollout proof:

- canonical marketplace rollout should seed at least one temporary document into a plugin-owned dataset handle
- hosted verification should prove retrieval from:
  - the plugin-owned `shared-index` source
  - and any deployment-private source when relevant

---

## 10) Recommended Build Sequence

### Wave 1: Packaged Seed Productization

Scope:

- manifest dataset package model
- dataset handle table
- seed run table
- packaged dataset artifacts for first-party starter plugins
- apply-time seeding hook
- live verification

Acceptance criteria:

- `mkp-data-help-center` works out of the box on a fresh installation
- `mkp-data-commerce-catalog` works out of the box on a fresh installation

### Wave 2: SQL Sync Connector

Scope:

- approved SQL connector definition
- install-form config for connection refs and query mapping
- sync job execution
- normalization and vectorization

Acceptance criteria:

- a plugin creator can define a SQL-backed `DATA` plugin without adding custom ingestion code
- platform can run initial sync and prove retrieval against the plugin-owned dataset handle

### Wave 3: Folder Sync Connector

Scope:

- folder and object-storage connector definitions
- file discovery
- parsing and chunking
- vectorization

Acceptance criteria:

- a plugin creator can define a file-backed `DATA` plugin using approved folder references
- platform can sync, vectorize, and verify retrieval

### Wave 4: Ongoing Dataset Operations

Scope:

- change detection
- differential sync
- scheduled external resync with a platform default cadence
- cleanup and retention rules

Acceptance criteria:

- repeated applies do not reseed unnecessarily
- changed datasets reindex predictably
- active verified deployments can resync external SQL or folder datasets without republishing

---

## 11) Recommended Product Boundary

Worth doing now:

- packaged seed datasets
- SQL sync connector
- folder sync connector
- apply-time dataset readiness
- live verification against real retrieved content

Not worth doing now:

- arbitrary crawler plugins
- unrestricted connector marketplace
- publisher-owned ingestion workers
- arbitrary remote ETL code

---

## 12) Relation To Existing Marketplace Plans

This plan tightens the `DATA` plugin model already described in:

- `MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `PLUGIN_DEVELOPER_EXTENSIBILITY_IMPLEMENTATION_PLAN.md`
- `EXTERNAL_PLUGIN_PUBLISHER_MODEL_PLAN.md`

Interpretation rule:

- `DATA` plugin config and runtime retrieval support are already implemented
- dataset lifecycle, ingestion, and connector productization are implemented on this branch
- live proof should validate:
  - apply-time dataset seeding or sync
  - `marketplace_dataset_sync_matches_expected`
  - real retrieval from installed data-plugin sources after apply
