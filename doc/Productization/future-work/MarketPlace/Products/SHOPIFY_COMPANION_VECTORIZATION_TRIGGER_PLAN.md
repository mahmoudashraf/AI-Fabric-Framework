# Shopify Companion Indexing Trigger Plan

Status: concrete implementation plan for manual indexing and live-update triggers (2026-04-19)

Purpose:

- define how Shopify object changes should drive indexing and live updates
- align Shopify trigger behavior with the existing AI Fabric reindex and indexed-output model
- define what the store admin should control in Shopify admin
- separate what can ship now on current platform primitives from what requires deeper runner/runtime work

This plan is intentionally written as a production implementation guide, not just a feature outline. The target design must be:

- durable
- idempotent
- ordered per store and entity family
- observable
- safe for multi-tenant production traffic
- evolvable from database queue to outbox or broker transport without semantic rewrites

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

Production-ready target:

- webhook intake must acknowledge fast and never do heavy indexing work inline
- duplicate deliveries must be safe
- per-store ordering decisions must be explicit
- queue processing must support lease, retry, dead-letter handling, and operator recovery
- merchant-facing controls must remain bounded even as internal event architecture evolves

## 1.1 Architecture Principles

The implementation should follow these rules:

1. `Acknowledge fast`

- verify the webhook
- persist the intake record or event
- return success quickly
- do not block the Shopify webhook request on indexing work

2. `Idempotency before throughput`

- duplicate deliveries are expected
- the system must dedupe safely before attempting downstream work

3. `Per-aggregate ordering, not global ordering`

- ordering should be guaranteed only where it matters
- the correct aggregate is the current store plus the affected entity family

4. `Transport-neutral business logic`

- trigger evaluation, coalescing, and indexing intent generation must not depend on Kafka, Postgres polling, or any specific queue implementation

5. `Thin data retention`

- keep only the sparse indexed-object ledger and auditable event history needed for correctness and recovery
- do not build a full Shopify content mirror in platform or bridge databases

6. `Safe degradation`

- if auto indexing is unhealthy, merchants must still be able to recover with bounded manual indexing actions

## 1.2 Non-goals

This plan does not aim to:

- make the bridge a long-lived Shopify content warehouse
- expose deployment internals to merchants
- provide exactly-once delivery guarantees at the transport layer
- make deployment-snapshot reindex semantics disappear

We only need at-least-once delivery with strong idempotency and correct aggregate ordering.

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
- `manualIndexAllowed`
- `manualReindexAllowed`
- `autoIndexingEnabled`
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

### 4.3 Policy safety rules

The trigger policy model should also enforce these production rules:

- auto indexing is opt-in by default for existing stores unless explicitly enabled during rollout
- `minimumRunIntervalSeconds` must be enforced even if webhook volume spikes
- per-family policies must have safe defaults that avoid surprise reindex storms
- policy updates must be audited with actor, timestamp, and effective diff
- policy evaluation should fail closed:
  - if policy state cannot be resolved, do not enqueue auto indexing
  - keep manual indexing available unless the deployment is not ready

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
- `autoIndexingDefault`
- `sourcePoliciesJson`
- `updatedAt`
- `updatedBy`

Recommended constraints:

- one active policy row per store
- optimistic versioning on policy updates
- full audit record for policy changes

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

Recommended additions for production:

- `aggregateKey`
- `dedupeKey`
- `correlationId`
- `causationId`
- `attemptCount`
- `lastAttemptAt`
- `nextAttemptAt`
- `leaseOwner`
- `leaseExpiresAt`
- `failureCode`
- `lastErrorSummary`
- `deadLetteredAt`
- `retentionExpiresAt`

Recommended status model:

- `QUEUED`
- `LEASED`
- `COALESCED`
- `DISPATCHED`
- `COMPLETED`
- `SKIPPED`
- `FAILED`
- `DEAD_LETTERED`

Recommended indexes and constraints:

- unique constraint on `dedupeKey` within the intended dedupe scope
- index on `status, nextAttemptAt`
- index on `aggregateKey, occurredAt`
- index on `shopDomain, entityType, sourceObjectId`
- retention-oriented index on `retentionExpiresAt`

### 6.2.1 Transport-neutral event envelope

The dirty-event queue should be designed as the first transport for a more general event pipeline, not as a one-off table shape that blocks future evolution.

Add an explicit event envelope contract with fields such as:

- `eventId`
- `schemaVersion`
- `eventType`
- `eventSource`
- `shopDomain`
- `deploymentId`
- `aggregateKey`
  - recommended: `shopDomain + entityType`
- `dedupeKey`
- `correlationId`
- `causationId`
- `occurredAt`
- `availableAt`
- `payloadJson`
- `headersJson`

Rules:

- producers write the same logical event envelope regardless of transport
- consumers process the same logical event envelope regardless of transport
- queue persistence is the first transport implementation
- a future outbox or broker publisher must not change merchant-facing behavior or policy semantics
- event schema versioning must be explicit and backward-compatible within one rollout window

This is the key future-readiness boundary. We should abstract the transport, not prematurely adopt a broker.

### 6.2.2 Webhook intake requirements

The webhook intake path must be production-safe.

Required behavior:

- verify Shopify HMAC before accepting the request
- record the Shopify event id and topic
- dedupe repeated webhook deliveries
- persist the event and return success quickly
- do not fetch Shopify objects or enqueue heavy indexing work inline with the webhook request if that risks timeout

Required stored fields on webhook intake or linked event metadata:

- `shopifyWebhookId`
- `shopifyTopic`
- `shopDomain`
- `receivedAt`
- `validatedAt`
- `deliveryAttempt` if available
- payload checksum or payload reference

Security rule:

- never persist raw secrets or session tokens in webhook event payloads
- if payload retention is needed for audit or replay, store only the minimal payload or a bounded encrypted reference

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

Recommended additions:

- `firstIndexedAt`
- `lastSeenAt`
- `lastTriggerDecision`
- `lastTriggerReason`

Recommended constraints:

- unique constraint on `deploymentId, entityType, sourceObjectId`
- do not allow one store object to drift into multiple active rows for the same entity type

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

Implementation guidance:

- the coalescer should lease work with a bounded lease TTL
- processing must be safe under duplicate scheduler executions
- one aggregate key should not be processed concurrently by multiple workers unless ordering is proven irrelevant
- if a newer event supersedes an older queued event for the same aggregate, the older one should be marked `COALESCED`, not left ambiguous

### 7.3 Auto mode, later

Later, add true incremental event-driven vectorization:

- runner supports mixed `UPSERT` and `DELETE`
- delete webhook can emit a direct `DELETE`
- update webhook can emit targeted `UPSERT`
- create webhook can emit targeted `UPSERT`

That reduces cost and avoids rerunning full entity-family refreshes when only one object changed.

### 7.4 Future-ready event architecture

Do not couple Shopify webhook handling directly to Kafka or any other broker-specific producer API.

Instead split the pipeline into stable roles:

1. `Event ingestor`

- receives webhook-derived changes
- writes a transport-neutral event envelope
- performs dedupe-key assignment

2. `Coalescer`

- groups events by aggregate key
- applies debounce and minimum-run-interval rules
- emits a bounded indexing intent

3. `Intent dispatcher`

- hands off indexing intents to the current execution transport
- today: database-backed queue plus scheduler
- later: outbox publisher or broker-backed dispatcher

4. `Run enqueuer`

- converts the bounded indexing intent into a vectorization run or delta run
- keeps plan/run creation inside the existing platform vectorization model

This gives us three important properties:

- transport independence
- testable business rules outside infrastructure concerns
- a clean migration path from database queue to outbox or broker later

Recommended internal interfaces:

- `ShopifyIndexingEventIngestor`
- `ShopifyIndexingEventCoalescer`
- `ShopifyIndexingIntentDispatcher`
- `ShopifyIndexingRunEnqueuer`

Those names matter less than the separation of responsibilities.

### 7.5 Transport decision

Current recommendation:

- use a platform-owned database queue now
- use scheduled polling/coalescing now
- keep the event contract and dispatcher abstraction ready for a future broker

Do not introduce Kafka yet because it does not remove the real hard problems:

- idempotency
- coalescing
- per-store ordering
- delete/update correctness
- sparse indexed-object ledger correctness

Kafka or another broker becomes justified only if one or more of these become true:

- sustained multi-store event volume is high enough that database polling becomes the bottleneck
- multiple independent consumers need the same event stream
- replay requirements exceed what a database queue or outbox gives us
- cross-service event fan-out becomes a platform-wide primitive
- operational evidence shows queue polling or row locking is the real bottleneck rather than Shopify API or indexing throughput

### 7.6 Outbox-compatible migration path

Build the first implementation so it can evolve like this without semantic rewrites:

1. `Phase A`

- webhook -> database event queue
- scheduler/coalescer -> bounded indexing intent
- intent -> vectorization run

2. `Phase B`

- webhook -> database event queue + transactional outbox
- outbox publisher -> broker topic
- consumers continue to process the same event envelope

3. `Phase C`

- broker becomes the primary transport for downstream consumers
- database queue can remain as the authoritative intake ledger or be reduced to an outbox/audit role

Important:

- the sparse indexed-object ledger remains required in every phase
- merchant-facing trigger policies remain required in every phase
- only the transport changes; business semantics do not

## 8) Reliability, Recovery, And Operations

### 8.1 Retry model

Retries must be bounded and explicit.

Recommended behavior:

- transient failures retry with exponential backoff
- permanent failures move to `FAILED` or `DEAD_LETTERED`
- repeated failures on one aggregate must not block unrelated stores
- manual replay tooling should exist for operators

Recommended failure classes:

- `SHOPIFY_API_TRANSIENT`
- `SHOPIFY_API_AUTH`
- `BRIDGE_UNAVAILABLE`
- `POLICY_EVALUATION_FAILED`
- `RUN_ENQUEUE_FAILED`
- `INDEXING_RUN_FAILED`
- `SCHEMA_ERROR`

### 8.2 Dead-letter handling

If an event cannot be processed safely after bounded retries:

- move it to `DEAD_LETTERED`
- preserve diagnostic context
- expose it in operator diagnostics
- do not silently drop it

The merchant should see a plain-language degraded status, not raw queue internals.

### 8.3 Retention and cleanup

Recommended retention posture:

- keep recent completed/coalesced/skipped events for audit and troubleshooting
- prune or archive old completed events on a schedule
- keep dead-letter events longer than successful events
- keep indexed-object ledger rows as long as they remain relevant to live indexing correctness

Retention must be explicit; otherwise the queue tables will grow without bound.

### 8.4 Concurrency control

Required rules:

- one aggregate key should have at most one active leased processor
- one deployment should have bounded concurrent auto-index runs
- manual merchant actions should either:
  - reuse in-flight work when equivalent
  - or clearly create a new bounded run with a reason code

The system should prefer coalescing to parallelism for one store family.

## 9) Delete Semantics

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

## 10) Observability And SLOs

### 10.1 Required metrics

At minimum, record:

- webhook intake rate
- dedupe hit rate
- queued event count by status
- dead-letter count
- coalescing ratio
- time from webhook receipt to indexing intent creation
- time from webhook receipt to completed indexing run
- indexing run failure rate by reason
- per-store last successful live update time

### 10.2 Required logs and traces

Use correlation identifiers across:

- webhook intake
- bridge fetch
- event coalescing
- run enqueueing
- runner execution

Logs must allow an operator to trace one Shopify object change to one final indexing outcome.

### 10.3 Suggested service objectives

Initial objectives should be explicit even if they tighten later:

- webhook requests acknowledged within a small bounded latency budget
- live updates visible in indexing state within a bounded freshness window under normal load
- dead-letter rate below an agreed threshold

If we do not define these, we cannot operate the system properly.

## 11) Admin UX Plan

### 11.1 Merchant panel sections

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

### 11.2 UX rules

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

## 12) Platform Rules

### 12.1 Deployment-snapshot rule

If the active deployment snapshot changes in a way that requires reindex:

- mark the deployment `OUT_OF_DATE`
- do not silently claim live auto indexing has fully realigned the store
- require a proper post-apply reindex path

This must remain aligned with the framework’s indexed-output model in:

- [VectorizationIndexedOutputHashService.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/vectorization/service/VectorizationIndexedOutputHashService.java)
- [DeploymentConfigCompiler.java](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentConfigCompiler.java#L157)

### 12.2 Source-object rule

Shopify source-object changes:

- must not require draft publish/apply
- must not mutate deployment entity config
- must operate against the active deployment snapshot

### 12.3 Multi-tenant safety rule

The indexing trigger pipeline must remain tenant-safe.

Required rules:

- event dedupe and aggregate keys must never collapse data across tenants or deployments
- one store's failures must not block unrelated stores
- operator replay tooling must be scoped to the intended store and deployment
- no event consumer should be able to fetch raw data from another tenant without the same control-plane authorization already required elsewhere

## 13) Rollout And Feature Flags

Roll this out progressively.

Required flags or rollout guards:

- merchant-visible indexing labels
- auto indexing enablement
- per-family trigger policies
- indexed-field-aware update evaluation
- delete-triggered live updates

Recommended rollout order:

1. internal stores only
2. design partners
3. default-enabled for new stores
4. selective migration for existing stores

During rollout:

- auto indexing should be observable before it is default-on
- operators need a global kill switch
- per-store disablement must exist for recovery

## 14) Implementation Waves

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
- merchant-visible labels no longer require understanding internal sync mechanics

### Wave 2: Auto trigger policy and dirty queue

Ship:

- persisted auto-trigger policy
- per source-family create/delete/update toggles
- debounce/coalescing queue
- recent event decisions in UI
- transport-neutral event envelope and dispatcher boundary

Backend work:

- add policy persistence
- add dirty queue persistence
- add scheduler/coalescer
- add event envelope and dispatcher abstraction
- webhook handler records evaluable events instead of only invalidating sync

Acceptance:

- repeated product updates do not queue duplicate runs
- one quiet-window run is scheduled for the affected entity families
- the ingestion/coalescing logic can switch transport later without changing trigger-policy semantics
- queue leasing, retry, and dead-letter behavior are implemented and test-covered

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
- indexed-fingerprint computation is deterministic and version-aware

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
- operators can replay or inspect failed delta events safely

## 15) Recommended Build Order

Recommended order:

1. Wave 1
2. Wave 2 with full-refresh scoped auto reruns
3. Wave 3 field-aware updates
4. Wave 4 incremental delete/upsert path

Reason:

- Wave 1 and Wave 2 deliver merchant value on current safe platform primitives
- Wave 2 should establish the future-ready transport boundary while still using the database queue
- Wave 3 raises correctness
- Wave 4 is the deeper runner/runtime extension

## 16) Acceptance Criteria

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
- event ingestion, coalescing, and run enqueueing remain transport-neutral
- the first implementation can evolve to outbox or broker transport without changing merchant policy semantics
- webhook intake is idempotent and fast
- queue processing has bounded retry and dead-letter handling
- per-store/entity-family ordering is explicit and test-covered
- operator observability is sufficient to debug one event end to end
- retention and cleanup are implemented so queue state does not grow without bound

## 17) Recommendation

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
