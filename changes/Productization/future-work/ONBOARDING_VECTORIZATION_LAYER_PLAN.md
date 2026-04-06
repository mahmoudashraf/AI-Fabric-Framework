# Onboarding Vectorization Layer Plan

Status: planning document (2026-04-04)

This document replaces the broader "data migration" framing for Wave 4 Track B.

Verification closure for Track B is tracked separately in:

- `VECTORIZATION_AND_TENANT_SCOPED_VERIFICATION_HARDENING_PLAN.md`

The goal is narrower and more product-accurate:

- index current customer data into the deployment's configured AI entities
- write that indexed output into the deployment's selected or provisioned vector database
- support the onboarding phase, where bulk vectorization matters most

This is not a broad ETL platform plan.

---

## 1) Product Goal

The platform should support **onboarding vectorization** as a first-class capability.

That means:

- selecting a deployment
- connecting to the customer's source data
- mapping source data into the deployment's configured AI entities
- bulk-indexing into the deployment's active vectorization path

This capability is most important:

- at the beginning of onboarding
- when the customer needs initial indexed knowledge or entity data loaded

It is less important later, because after onboarding the product should rely more on:

- live writes
- runtime indexing
- ongoing application-driven updates

---

## 2) Scope Definition

Track B should be scoped as:

- **indexing customer data into AI Fabric deployments**

It should not be scoped as:

- broad enterprise ETL
- operational-system replication
- generalized migration of every downstream application state
- a replacement for customer integration platforms

The default target is:

- the deployment's configured AI entity model
- the deployment's selected or provisioned vector database

So if a deployment is configured for:

- `product`
- `policy`
- `review`

then vectorization should map source data into exactly those entities and index through the deployment's existing runtime indexing path.

Bootstrap indexing should also be supported when:

- the deployment has no indexed coverage yet for one or more configured entities
- the deployment is newly provisioned and needs its first searchable dataset
- the current deployment snapshot is active but its configured entities are still effectively unindexed

Bootstrap should therefore be decided from the deployment's scoped indexed coverage for its configured entities, not from the mere existence of a physical vector space or shared provider resource.

Best-practice bootstrap coverage algorithm:

1. resolve the active deployment snapshot and its configured entities
2. resolve the active vectorization plan revision and source connection
3. estimate expected source rows per configured entity from source discovery, count query, or plan metadata
4. resolve current indexed coverage per configured entity in the deployment's target path
5. mark bootstrap required only where:
   - expected source rows are greater than zero
   - and indexed coverage for that entity is zero or below the platform's minimum healthy threshold
6. do not keep an entity in `BOOTSTRAP_REQUIRED` when the source side is currently empty
7. surface `SOURCE_EMPTY` or equivalent explanatory reason when there is nothing to index yet

Expected source rows should normally come from discovery executed through the runner, then stored in the platform.

Recommended source-count flow:

1. the customer configures the source connection and vectorization plan in the platform
2. the platform creates a discovery task for the active plan revision
3. an eligible runner pulls that discovery task using the deployment-scoped execution identity
4. the source adapter inspects the source using real customer connectivity and auth
5. the adapter returns per-entity row counts or estimates
6. the runner posts the discovery result back to the platform
7. the platform stores the discovery snapshot and uses it for bootstrap and reindex decisions

Expected row counts should therefore come from one of these sources, in order of preference:

- exact source-side count
- source-reported pagination or count metadata
- estimated count from adapter discovery
- operator-provided plan metadata as a fallback

The platform should persist, per configured entity:

- expected row count
- count method such as `EXACT`, `SOURCE_REPORTED`, `ESTIMATED`, `OPERATOR_PROVIDED`, or `UNKNOWN`
- discovery timestamp
- plan revision identity

---

## 3) Multi-Tenancy And Deployment Rules

Vectorization must respect the deployment model already in place.

Rules:

- one vectorization plan targets one deployment
- the deployment's current `Customer -> Tenant -> Deployment` binding is authoritative
- if the deployment uses tenant-scoped shared vector infrastructure, vectorization must respect that automatically
- if the deployment uses dedicated infrastructure, vectorization must write only to that dedicated target
- the vectorization layer must not invent a second tenancy model

The deployment's current provider and storage posture should determine the effective target automatically.

When deployment configuration changes in a way that affects indexed output, the customer should be able to choose whether to reindex.

Examples:

- entity add, remove, or rename
- searchable field changes for an entity
- embeddable field changes for an entity
- metadata field name or type changes for an entity
- per-entity text composition or chunking changes
- embedding or vectorization-affecting deployment changes
- target vector database or scoped target-handle changes

The customer choice should be explicit:

- no reindex
- reindex selected configured entities
- full deployment reindex

For Track B, entity-scoped reindex means the customer chooses one or more deployment entities such as `product` or `policy` to reindex.

It does not mean record-level selective reindex across arbitrary prior writes.

---

## 4) Runner Model

The architecture should be based on **deployment-scoped execution identity**.

Why:

- source connectivity is customer-specific
- auth and network posture are customer-specific
- onboarding indexing is bursty and temporary
- we want the runner to be provisioned with customer connectivity much like the connector is provisioned for customer integration

Recommended model:

- each run is always bound to one deployment-scoped execution identity
- new eligible deployments default to `PLATFORM_MANAGED_AUTO`
- in that default mode, the platform auto-provisions a managed runner for the deployment when needed
- that managed runner pulls work from the platform
- the platform may delete it after indexing is completed
- the platform may recreate it later if another indexing pass is needed

The same runner model should support:

- initial bootstrap indexing when configured entities are not yet indexed
- explicit reindex runs after relevant config changes

Supported runner modes should be:

- `PLATFORM_MANAGED_AUTO`
- `PLATFORM_MANAGED_NONE`
- `CUSTOMER_MANAGED_REMOTE`

This keeps the architecture flexible:

- the isolation model is deployment-scoped execution identity
- auto-provisioned managed runners are the default operator experience
- customer-managed remote execution remains a first-class enterprise mode

This makes runner execution:

- deployment-scoped
- customer-connectivity aware
- auto-provisioned by default for eligible deployments
- optionally ephemeral when platform-managed

The product should not require a permanently shared runner pool as its only model, and it should not require a dedicated infrastructure instance for every future deployment mode either.

---

## 5) Lifecycle Model

The platform remains the control plane and source of truth.

The runner remains the execution worker.

Platform owns:

- plan creation
- plan revisioning
- run creation
- start
- pause
- resume
- cancel
- retry
- status authority

Runner owns:

- polling and claiming work
- heartbeats
- source reads
- indexing execution
- coarse checkpoint reporting
- technical outcome reporting

Important network rule:

- runners pull from the platform
- the platform does not directly call runners

Runner eligibility should be enforced through a deployment-scoped registration and session model:

- the runner receives a deployment-scoped registration token during provisioning or through customer-managed runner configuration
- the runner exchanges that registration token for a short-lived runner session bound to:
  - deployment identity
  - runner instance identity
  - product version
  - compatibility version
- the runner uses the short-lived session, not the long-lived registration token, for poll, claim, heartbeat, and status updates
- each claimed run is protected by a lease renewed by heartbeat
- the platform can expire or revoke registration and session credentials and fence stale runners off from new claims
- expired leases must be reclaimable by the platform

Runner control-plane auth and source connectivity auth should be treated as two different channels:

1. control-plane auth

- registration token
- short-lived runner session
- claim lease

This is used only for the runner talking to the platform.

2. source connectivity and auth material

- resolved connection descriptor
- source auth material or local secret aliases
- plan revision and entity scope

This is used only for the runner talking to the customer source system.

Recommended delivery flow:

1. runner uses registration token to establish a short-lived runner session
2. runner claims a run or discovery task
3. runner fetches a resolved execution bundle for that task
4. the execution bundle contains:
   - non-secret connection descriptor
   - source adapter type
   - pagination mode
   - entity scope and plan revision
   - source auth material or local secret aliases
5. the runner builds source clients from that bundle and executes

The runner image itself should not permanently embed customer source credentials.

Runner status should be visible in the platform as:

- `CURRENT`
- `OUTDATED`
- `INCOMPATIBLE`

---

## 6) Provisioning Model

The runner should be treated as part of the provisioning layer.

Recommended posture:

- runtime and connector remain the serving plane
- vectorization runner is a temporary execution-plane component
- provisioning should be able to create:
  - runtime
  - connector
  - optional vectorization runner

The vectorization runner should be provisioned only when needed:

- onboarding import
- major re-index wave
- customer-requested refresh
- bootstrap indexing when configured entities are still unindexed

And should be removable afterwards.

Token management should support both runner postures:

- platform-managed runner:
  - one-click reprovision or replace runner should be supported
  - registration-token rotation usually means reprovision or redeploy of that runner
  - the platform may hand the runner a short-lived resolved execution bundle for source access at run start
- customer-managed remote runner:
  - the customer can update the token in their environment and restart or reconnect the runner without changing the deployment runtime
  - outdated or incompatible remote runners should be flagged, not silently auto-migrated
  - source credentials should preferably stay in the customer boundary through local secret aliases or local secret resolution

Runner version metadata should include:

- product version
- compatibility version
- deployment binding

---

## 7) Tracking Model

Vectorization tracking should stay intentionally coarse at the control-plane level.

We do **not** need full fine-grained per-artifact receipt tracking in Track B.

The platform should track progress roughly through:

- source page numbers
- id ranges such as `0-1000`
- batch counters
- source cursors
- rough success and failure counts

This gives enough operational visibility for onboarding without turning Track B into a full historical reconciliation database.

So Track B should start with:

- coarse checkpointing
- coarse progress tracking
- coarse failure buckets
- coarse run reason tracking such as bootstrap or reindex
- entity-level scope choice for reindex

Not with:

- full artifact-level rollback receipts
- full previous-state reconstruction

At the same time, the ingestion boundary itself must be idempotent.

The runner and runtime data-sync contract should therefore enforce:

- stable source record identity from the source adapter
- stable target entity identity for the mapped deployment entity
- deterministic chunk identity when chunking is used
- idempotent upsert semantics at the runtime boundary
- retry-safe deduplication for repeated batches or rerun overlap

This is mandatory even though the platform chooses coarse run tracking.

---

## 8) Rollback Posture

Rollback should not be the primary design goal for this layer.

Reasons:

- indexing is expensive
- embedding and token costs matter
- deletes plus full re-index is often the operationally cleaner answer
- most onboarding issues should be handled by:
  - fix plan or mapping
  - rerun indexing
  - patch or update indexed entities if needed

So the platform should:

- avoid promising rich rollback in Track B
- support delete and rerun where necessary
- support targeted update or patch flows later when the product needs them

The preferred recovery posture is:

- cancel
- adjust plan
- rerun

not:

- sophisticated compensating rollback logic

---

## 9) Deployment Snapshot And Reindex Boundary

Reindex decisions should be tied to the applied deployment snapshot, not to arbitrary draft edits.

There are three different config classes to distinguish:

1. Deployment snapshot changes

- entity model changes
- selected vector database or storage posture changes
- runtime indexing contract changes
- other applied deployment config that changes indexed output semantics

These should require publish and apply first.

After the new deployment snapshot is active, the platform should decide whether to offer bootstrap or reindex for that snapshot.

2. Vectorization plan changes

- source mapping changes
- source query changes
- source pagination changes
- selected entity scope for a run

These should not require deployment redeploy.

The runner should pull the approved plan revision at run start.

3. Runner registration or source-connectivity changes

- runner registration token rotation
- runner-side connectivity config
- customer-managed runner environment changes

These should require runner restart, reconnect, or reprovision as needed, but not deployment runtime redeploy by default.

If a config change affects indexed output and the customer chooses not to reindex, the deployment should be marked as vectorization out of date until a successful run aligns indexed state with the active deployment snapshot.

The customer should not silently flip `OUT_OF_DATE` back to `IN_SYNC`.

If the customer or operator has external evidence that indexed state is already current, the safer option is a separate audited state such as `MANUALLY_CONFIRMED` or `EXTERNALLY_SYNCED`, with:

- reason
- actor
- timestamp
- optional expiry or review deadline

That keeps verified sync distinct from manual override.

---

## 10) Indexed-Output Semantics Matrix

The platform should explicitly distinguish which changes mark the active deployment snapshot as `OUT_OF_DATE`.

Changes that should mark the active deployment snapshot `OUT_OF_DATE` after apply:

- entity added, removed, or renamed
- searchable fields changed for an entity
- searchable field weights or inclusion rules changed
- embeddable fields changed for an entity
- embeddable field weights or inclusion rules changed
- metadata field names changed
- metadata field types changed
- entity-level text composition changed
- entity-level chunking or segmentation changed
- embedding provider changed
- embedding model changed
- embedding dimensions changed
- target vector provider changed
- target provider scope handle changed
  - namespace
  - collection
  - class
  - tenant
  - database
- storage posture or tenant-scoped target resolution changed
- runtime indexing contract hash changed for the active release

Changes that should not mark the deployment snapshot `OUT_OF_DATE`, but should make the latest vectorization plan revision newer than the last successful run:

- source query changed
- source filter changed
- source pagination strategy changed
- source cursor strategy changed
- source-to-entity mapping changed
- transform logic changed in the vectorization plan
- selected entity scope for a run changed
- batch size or concurrency changed

Changes that should not affect vectorization sync state at all:

- prompts
- actions and routes
- runtime auth or connector auth unrelated to indexing input or target resolution
- runner token rotation by itself

This keeps deployment-snapshot drift separate from plan-revision drift.

---

## 11) Verification Posture

Verification is still important, but should now be split into:

- Track B core execution proof already modeled in this document
- explicit verification-closure work defined in `VECTORIZATION_AND_TENANT_SCOPED_VERIFICATION_HARDENING_PLAN.md`

Verification closure should eventually compare:

- source data shape and rough counts
- indexed entity counts
- resolved target coverage for configured entities
- deployment entity coverage
- tenant-isolation evidence for shared tenant-scoped deployments

Before deep verification, the platform should at least detect obvious bootstrap conditions:

- configured deployment entities with missing indexed coverage
- indexed state absent or clearly empty for the active deployment snapshot

and offer a bootstrap vectorization action.

Admin-based verification should also become a first-class operator capability. The platform should support:

- read-only verification for control-plane, runner, and discovery readiness
- bounded active verification for sample vectorization
- tenant-isolation verification for shared-storage deployment pairs
- hosted and GitHub parity for the same proof flows

This verification-closure work should follow the core vectorization implementation, but Track B should not be called fully complete until it lands.

So:

- core execution and progress first
- verification closure immediately after core execution

---

## 12) Recommended Product Model

Recommended new platform entities:

- `VectorizationPlan`
- `VectorizationPlanImpact`
- `VectorizationSourceConnection`
- `VectorizationRun`
- `VectorizationRunStep`
- `VectorizationCheckpoint`
- `VectorizationFailureBucket`
- `VectorizationSyncState`
- `VectorizationRunnerRegistration`
- `VectorizationRunnerSession`
- optional later: `VectorizationVerificationRun`

Recommended relationships:

- one deployment can have many vectorization plans
- one plan can have many runs
- one run targets one deployment snapshot
- one run is executed by one claimed runner at a time
- one run should carry a reason such as:
  - `BOOTSTRAP`
  - `REINDEX`
  - `REFRESH`
- one deployment snapshot should expose a vectorization sync state such as:
  - `BOOTSTRAP_REQUIRED`
  - `SOURCE_EMPTY`
  - `IN_SYNC`
  - `OUT_OF_DATE`
  - `REINDEX_DEFERRED`
  - `MANUALLY_CONFIRMED`
  - `RUNNING`
  - `FAILED`

---

## 13) Source Strategy

Recommended first source adapter categories:

- `FILE`
  - CSV
  - JSON
  - JSONL
- `REST_API`
  - generic endpoint + auth + pagination
- `SQL`
  - read-only query or view based extraction

The product should not assume:

- bespoke connector per source system

It should prefer:

- a small number of general-purpose source adapters

Those adapters are also the normal source of discovery metadata such as:

- schema preview
- sample rows
- pagination shape
- expected per-entity row counts or estimates

---

## 14) Ingestion Boundary

Preferred target path:

- vectorization runner -> runtime data-sync API

Benefits:

- same entity rules as the deployment
- same indexing behavior
- same vectorization path
- same selected/provider-backed vector database
- same multi-tenancy and storage posture

The vectorization layer should not bypass deployment invariants by writing directly to vector providers as the default path.

The runtime data-sync contract should be treated as an idempotent upsert boundary.

That means the vectorization payload must carry enough stable identity for runtime to:

- upsert the same logical entity content repeatedly without duplicate accumulation
- replace prior chunk sets for the same logical entity when the indexed-output contract changes
- keep retries safe under repeated delivery

---

## 15) Track B Build Order

Track B should build in this order:

1. vectorization domain model in the platform
2. vectorization source connection model and secret references
3. bootstrap detection based on deployment-scoped entity coverage, including zero-source behavior, plus plan revisioning and preview workspace
4. deployment-scoped execution identity, runner modes, and platform-managed auto provisioning with registration, session, and lease control
5. idempotent runtime data-sync contract, coarse checkpointing, lifecycle controls, and vectorization sync-state tracking
6. config-change impact analysis and customer-selected entity-scope or full reindex flow
7. later verification against source and indexed target state

---

## 16) Summary

The right Track B goal is:

- **Vectorization Layer**, not broad migration

The right operating posture is:

- onboarding-heavy
- deployment-scoped execution identity
- customer-connectivity aware
- pull-based runners
- default managed auto-provisioning for eligible deployments
- configurable runner mode
- bootstrap indexing when configured entities are not yet indexed for the active deployment snapshot
- explicit customer choice on reindex after config changes
- runner registration, session, and lease control
- applied-snapshot reindex decisions
- idempotent runtime data-sync upsert semantics
- coarse tracking first
- verification closure through the dedicated hardening plan before Track B is treated as fully complete
- limited rollback ambitions
