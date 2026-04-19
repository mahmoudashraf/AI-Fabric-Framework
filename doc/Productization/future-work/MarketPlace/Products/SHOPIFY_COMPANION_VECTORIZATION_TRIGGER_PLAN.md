# Shopify Companion Vectorization Trigger Plan

Status: concrete implementation plan for manual indexing and live-update triggers (2026-04-19)

Purpose:

- define how Shopify object changes should drive indexing and live updates
- align Shopify trigger behavior with the existing AI Fabric reindex and indexed-output model
- define what the store admin should control in Shopify admin
- separate what can ship now on current platform primitives from what requires deeper runner/runtime work

This plan should be read with:

- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/ONBOARDING_VECTORIZATION_LAYER_PLAN.md`

## 1) Executive Summary

Current Shopify behavior is not enough for the desired merchant experience.

Today:

- Shopify content webhooks are received
- the store is marked content-dirty
- an internal normalization refresh is attempted
- indexing is still triggered manually by the store admin

That is only half of the product.

Target behavior:

- the store admin can manually index all enabled data
- the store admin can manually reindex selected entity families
- the store admin can enable live auto indexing per Shopify object family
- the store admin can choose which trigger types are active:
  - create
  - delete
  - update
- for updates, the store admin can choose whether live auto indexing reacts to:
  - any update
  - only updates that change indexed fields
  - only a bounded selected subset of indexed fields

Important merchant-facing rule:

- `sync` is an internal implementation stage
- the Shopify admin app should expose `Index`, `Reindex`, and `Live updates`
- the merchant should not need to understand or operate a separate sync layer

The important platform rule is:

- deployment config changes still follow the normal draft -> publish -> apply -> reindex model
- Shopify source-object changes should not require deployment publish/apply
- Shopify source-object changes should drive indexing against the already active deployment snapshot, with any required internal normalization refresh hidden behind that action

## 2) Current State

### 2.1 What happens now

Current webhook handling:

- `products/*`, `collections/*`, `pages/*`, and `shop/update` are classified as content/config changes in [ShopifyWebhookService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/webhook/service/ShopifyWebhookService.java#L159)
- those events record webhook state and trigger incremental sync in [ShopifyWebhookService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/webhook/service/ShopifyWebhookService.java#L91)
- the platform marks the store `NOT_SYNCED` in [ShopifyStoreWebhookService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreWebhookService.java#L66)

Current vectorization trigger:

- vectorization only runs from explicit admin action in [ShopifyStoreVectorizationService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreVectorizationService.java#L151)
- the current execution mode is explicitly manual in [ShopifyStoreVectorizationService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreVectorizationService.java#L309)

Current product gap:

- the implementation still separates internal content refresh from vector indexing
- the merchant experience should converge on `Index/Reindex/Live updates`, not `Sync` plus `Vectorize`

Current sync deletion behavior:

- Shopify document sync already deletes stale tracked documents during sync in [ShopifyStoreDocumentSyncService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreDocumentSyncService.java#L267)

Current vectorization write behavior:

- the runner currently writes `UPSERT` operations only in [ConnectorDataSyncTargetWriter.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/ai-fabric-product/ai-fabric-vectorization-runner/src/main/java/com/ai/fabric/vectorization/runner/service/ConnectorDataSyncTargetWriter.java#L61)
- runtime data-sync itself does support `DELETE`, but the vectorization runner does not currently emit delete operations

### 2.2 Existing AI Fabric reindex model

The platform already distinguishes deployment-level vectorization-affecting changes:

- searchable fields
- embeddable fields
- metadata fields
- chunking
- provider config

Those are canonicalized into an indexed-output hash in [VectorizationIndexedOutputHashService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/vectorization/service/VectorizationIndexedOutputHashService.java#L24)

Deployment publish marks `reindexRequired` when entity config or provider config changes in [DeploymentService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentService.java#L1146) and [DeploymentConfigCompiler.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentConfigCompiler.java#L157)

That model must remain the source of truth for deployment-snapshot changes.

## 3) Product Requirements

### 3.1 Merchant-facing capabilities

The store admin should be able to:

- choose Shopify source categories:
  - Products
  - Collections
  - Pages
  - Policies
- manually index all currently enabled categories
- manually reindex selected entity families:
  - `product`
  - `support-policy`
- manually reindex all currently enabled entity families
- enable or disable live auto indexing
- choose trigger types per Shopify source family:
  - create
  - delete
  - update
- for update triggers, choose update sensitivity:
  - `ANY_UPDATE`
  - `INDEXED_FIELDS_ONLY`
  - `SELECTED_INDEXED_FIELDS`
- when `SELECTED_INDEXED_FIELDS` is used, choose from the effective indexed fields derived from the active deployment snapshot

### 3.2 What the merchant should not control

The store admin should not edit:

- raw marketplace plugin installs
- deployment drafts or versions
- source connection JSON
- vectorization plan JSON
- secret refs
- runner tokens
- batch size or low-level concurrency
- arbitrary custom field mappings

The merchant can choose from bounded policies. The platform still owns the technical wiring.

## 4) Trigger Model

### 4.1 Three different classes of change

The plan must separate three types of change:

1. Deployment-snapshot changes

- searchable field changes
- embeddable field changes
- chunking changes
- embedding/provider/vector target changes

These remain draft-backed and reindex-backed. They are not Shopify webhook concerns.

2. Shopify source-object changes

- product created
- product updated
- product deleted
- collection created or deleted
- page updated
- policy/store metadata changed

These should drive indexing against the active deployment snapshot without requiring publish/apply.

3. Operational runtime changes

- runner reconnection
- secret rotation
- session/token rotation

These should not by themselves trigger reindex.

### 4.2 Trigger policy model

Add a bounded per-store policy model:

- one policy record per store
- one policy block per source family:
  - `products`
  - `collections`
  - `pages`
  - `policies`

Each block should contain:

- `enabled`
- `manualSyncAllowed`
- `manualVectorizeAllowed`
- `autoVectorizationEnabled`
- `createTriggerEnabled`
- `deleteTriggerEnabled`
- `updateTriggerMode`
  - `NONE`
  - `ANY_UPDATE`
  - `INDEXED_FIELDS_ONLY`
  - `SELECTED_INDEXED_FIELDS`
- `selectedIndexedFields`
- `debounceWindowSeconds`
- `minimumRunIntervalSeconds`

Recommended defaults:

- `products`
  - create: enabled
  - delete: enabled
  - update: `INDEXED_FIELDS_ONLY`
- `collections`
  - create: enabled
  - delete: enabled
  - update: `INDEXED_FIELDS_ONLY`
- `pages`
  - create: disabled until page-create webhook support is confirmed as needed
  - delete: disabled until page-delete path exists
  - update: `INDEXED_FIELDS_ONLY`
- `policies`
  - create/delete: not independently exposed
  - update: `INDEXED_FIELDS_ONLY` via `shop/update`

## 5) Indexed-Field Awareness

### 5.1 Effective indexed fields

The update trigger must not invent a second field-definition system.

Effective watched fields should be derived from:

- the active deployment version entity config
- the active vectorization plan mapping
- the Shopify normalized source shape

For Shopify today, the current mapped fields are:

`product`

- `title`
- `content`
- metadata fields such as:
  - `handle`
  - `vendor`
  - `productType`
  - `updatedAt`
  - `storefrontUrl`

`support-policy`

- `title`
- `content`
- metadata such as:
  - `policyType`
  - `updatedAt`
  - `storefrontUrl`

The admin UI should show merchant-safe labels, for example:

- Product title
- Product description
- Vendor
- Product type
- Tags
- Collection title
- Collection description
- Page title
- Page body
- Policy title
- Policy body

But those labels must map back to effective indexed fields derived from the active deployment mapping.

### 5.2 Field-sensitive update evaluation

For `INDEXED_FIELDS_ONLY` or `SELECTED_INDEXED_FIELDS`, the system should:

1. receive the webhook
2. identify source family and object id
3. fetch the current canonical normalized record from Shopify Bridge
4. compute the effective indexed payload fingerprint for that record
5. compare it to the last indexed fingerprint for that record
6. queue live auto indexing only if the watched indexed output changed

Why this is required:

- Shopify webhook payloads are not a reliable complete source for field-level comparison
- the canonical indexed view is whatever Shopify Bridge normalizes for the runner
- the same record should be judged against what would actually be indexed, not against raw webhook shape

## 6) Data Model Changes

### 6.1 Store policy

Add a new store-level persisted policy object:

- `ShopifyStoreVectorizationPolicy`

Suggested fields:

- `shopDomain`
- `policyVersion`
- `autoVectorizationDefault`
- `sourcePoliciesJson`
- `updatedAt`
- `updatedBy`

### 6.2 Dirty-event queue

Add a queue table for coalesced source events:

- `ShopifyStoreVectorizationEventEntity`

Suggested fields:

- `id`
- `shopDomain`
- `deploymentId`
- `sourceCategory`
- `entityType`
- `sourceObjectId`
- `operation`
  - `CREATE`
  - `UPDATE`
  - `DELETE`
- `triggerReason`
  - `ANY_UPDATE`
  - `INDEXED_FIELDS_CHANGED`
  - `SELECTED_INDEXED_FIELDS_CHANGED`
  - `DELETE_TRIGGER`
  - `CREATE_TRIGGER`
- `changedFieldsJson`
- `sourceRecordVersion`
- `queuedAt`
- `status`
  - `QUEUED`
  - `COALESCED`
  - `RUNNING`
  - `COMPLETED`
  - `SKIPPED`
  - `FAILED`
- `coalescedRunId`
- `notes`

### 6.3 Minimal indexed-object ledger

Do not extend Shopify tracking into a full mirrored content store.

The thin production-safe model is a sparse indexed-object ledger that stores only:

- `shopDomain`
- `deploymentId`
- `entityType`
- `sourceObjectId`
- `sourceRecordVersion`
- `indexedOutputFingerprint`
- `lastIndexedAt`
- `lastIndexRunId`
- `lastIndexedDeploymentHash`
- `deletedAt` nullable

Important:

- do not persist raw Shopify content bodies for this ledger
- do not overload `contentFingerprint` from [ShopifyStoreDocumentSyncService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreDocumentSyncService.java#L302) with indexing semantics
- the bridge should stay stateless for content; canonical records are fetched on demand
- this ledger should live in the platform control plane, not in the bridge database

## 7) Execution Model

### 7.1 Manual merchant actions

The Shopify admin app should expose:

- `Index all enabled data`
- `Reindex all enabled data`
- `Reindex selected entity types`
- `Retry last failed auto index run`

Manual actions should be bounded to the current enabled source families and entity types.

### 7.2 Auto mode, near-term

Near-term, auto mode should use current platform primitives and remain safe:

1. webhook received
2. record webhook and mark the affected source family dirty
3. run or schedule any required bounded internal normalization refresh for that source family
4. coalesce events for a debounce window
5. if auto policy allows, queue one scoped indexing run for the affected entity types

Near-term run type:

- still uses the current vectorization run infrastructure
- still uses the current runner
- still behaves as a scoped refresh run, not a per-object in-place patch

This is the fastest production-safe path because it builds on what already exists.

### 7.3 Auto mode, later

Later, add true incremental event-driven vectorization:

- runner supports mixed `UPSERT` and `DELETE`
- delete webhook can emit a direct `DELETE`
- update webhook can emit targeted `UPSERT`
- create webhook can emit targeted `UPSERT`

That reduces cost and avoids rerunning full entity-family refreshes when only one object changed.

## 8) Delete Semantics

Delete handling needs explicit treatment.

Current state:

- internal dataset refresh can delete stale tracked documents from runtime
- vectorization runner does not emit `DELETE`

Plan:

Near-term:

- auto delete triggers should force a bounded internal refresh for the affected source family before any reindex run
- that ensures runtime stale documents are removed using the existing delete-capable refresh path

Later:

- extend the vectorization runner target writer to emit `DELETE`
- allow a pure object-level delete path without a family-level sync sweep

## 9) Admin UX Plan

### 9.1 Merchant panel sections

Add a dedicated `Indexing and live updates` section in the Shopify admin app.

Sections:

1. Scope

- source categories enabled
- effective entity types

2. Manual actions

- index all enabled data
- reindex all enabled data
- reindex selected entity types

3. Auto trigger policies

- per source family toggle
- create/delete/update trigger toggles
- update mode selector
- effective indexed fields viewer
- field selector when `SELECTED_INDEXED_FIELDS` is active

4. Current status

- content freshness state
- indexing state
- out-of-date state
- last webhook
- last auto trigger decision
- last manual run
- blocking reasons

5. Recent events

- last 20 webhook-triggered decisions
- whether each event:
  - triggered internal refresh only
  - triggered reindex
  - was skipped
  - was coalesced

### 9.2 UX rules

The merchant should never see:

- raw JSON mappings
- plan revision ids
- source connection ids
- plugin ids
- runner session ids

The merchant should see:

- plain labels
- derived indexed fields
- reasons when a trigger was skipped

## 10) Platform Rules

### 10.1 Deployment-snapshot rule

If the active deployment snapshot changes in a way that requires reindex:

- mark the deployment `OUT_OF_DATE`
- do not silently claim live auto indexing has fully realigned the store
- require a proper post-apply reindex path

This must remain aligned with the framework’s indexed-output model in:

- [VectorizationIndexedOutputHashService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/vectorization/service/VectorizationIndexedOutputHashService.java)
- [DeploymentConfigCompiler.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentConfigCompiler.java#L157)

### 10.2 Source-object rule

Shopify source-object changes:

- must not require draft publish/apply
- must not mutate deployment entity config
- must operate against the active deployment snapshot

## 11) Implementation Waves

### Wave 1: Finalize manual merchant controls

Ship:

- `Index all enabled data`
- `Reindex all enabled data`
- `Reindex selected entity types`
- clear status for content freshness vs index currency

Backend work:

- keep any content refresh step internal to the indexing action
- expose selected-entity reindex action
- return richer status and blocking reasons

Acceptance:

- merchant can fully recover current indexed state manually without platform operator help

### Wave 2: Auto trigger policy and dirty queue

Ship:

- persisted auto-trigger policy
- per source-family create/delete/update toggles
- debounce/coalescing queue
- recent event decisions in UI

Backend work:

- add policy persistence
- add dirty queue persistence
- add scheduler/coalescer
- webhook handler records evaluable events instead of only invalidating sync

Acceptance:

- repeated product updates do not queue duplicate runs
- one quiet-window run is scheduled for the affected entity families

### Wave 3: Indexed-field-aware updates

Ship:

- `INDEXED_FIELDS_ONLY`
- `SELECTED_INDEXED_FIELDS`
- UI field viewer and bounded selector

Backend work:

- derive effective indexed fields from active deployment snapshot and plan mapping
- fetch canonical normalized record on update evaluation
- compute and persist indexed output fingerprints

Acceptance:

- updates to non-indexed Shopify fields do not trigger vectorization
- updates to indexed fields do trigger vectorization

### Wave 4: True incremental delete and object-level vectorization

Ship:

- targeted object-level `UPSERT`
- targeted object-level `DELETE`
- lower-cost auto mode

Backend work:

- extend runner target writer for `DELETE`
- extend execution bundle for delta events
- allow mixed event-driven vectorization batches

Acceptance:

- delete webhooks remove indexed objects without requiring family-wide sync sweeps
- delete webhooks remove indexed objects without requiring family-wide refresh sweeps
- create/update/delete events can be handled incrementally

## 12) Recommended Build Order

Recommended order:

1. Wave 1
2. Wave 2 with full-refresh scoped auto reruns
3. Wave 3 field-aware updates
4. Wave 4 incremental delete/upsert path

Reason:

- Wave 1 and Wave 2 deliver merchant value on current safe platform primitives
- Wave 3 raises correctness
- Wave 4 is the deeper runner/runtime extension

## 13) Acceptance Criteria

The plan is complete when all of the following are true:

- store admin can manually index all enabled data and reindex selected entity families
- store admin can manually reindex all or selected entity families
- store admin can enable live auto indexing per source family
- create/delete/update triggers are configurable
- update triggers can be restricted to indexed fields
- deployment reindex semantics remain version-based and separate
- source-object changes do not require publish/apply
- stale deleted objects are removed from runtime and indexed state
- trigger decisions are auditable and visible

## 14) Recommendation

The right product shape is:

- manual merchant indexing controls first
- live auto indexing second
- field-aware update triggers third
- true incremental delete/upsert vectorization last

That matches the current framework:

- deployment-snapshot changes remain reindex-driven
- source-object changes become event-driven
- merchant controls stay bounded and understandable
- platform invariants stay intact
