# Vectorization Layer Code Residency And Integration Plan

Status: architecture and code-residency companion (2026-04-04)

This document complements:

- `ONBOARDING_VECTORIZATION_LAYER_PLAN.md`
- `PLATFORM_EXECUTION_SEQUENCE_WAVE4_PLAN.md`

It answers these implementation questions:

- where the active Track B code should live
- how the vectorization layer integrates with platform, runtime, connector, and provisioning
- how per-deployment runners should behave
- what should be tracked now versus later

---

## 1) Core Product Decision

Track B is a **vectorization layer**, not a broad migration platform.

The goal is:

- index current customer data into the deployment's configured AI entities
- write through the deployment's selected or provisioned vector database
- support onboarding-time bulk vectorization

It should not be modeled as:

- general ETL
- operational database replication
- a generic customer-deployment type
- an action connector

Important boundary rule:

- `runtime`, `connector`, and `vectorization-runner` are product code
- shared connectivity, auth, credential-material, and client primitives used by them should also live in product code
- the generic framework should keep only genuinely reusable indexing and local backfill capabilities

---

## 2) Target Architecture

The active Track B architecture should be:

1. **Platform vectorization control plane**
   - plans, revisions, source connections, runs, checkpoints, lifecycle commands, audit
2. **Product-shared integration primitive layer**
   - connectivity/auth/client building blocks shared by runtime, connector, and vectorization-runner
3. **Product vectorization execution core**
   - source adapters, mapping, batching, checkpointing, write orchestration
4. **Per-deployment vectorization runner**
   - provisioned with deployment-specific customer connectivity
   - pull-only execution worker
5. **Target ingestion boundary**
   - runtime data-sync APIs using the deployment's existing entity and vectorization path

The execution model should therefore be:

- UI and API in the platform
- runner provisioned per deployment when needed
- runner deleted after indexing completes
- runner recreated later if another bulk vectorization pass is needed

---

## 3) Code Residency

### 3.1 Platform control plane code

New Track B platform code should live under:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/vectorization/`

Recommended package structure:

- `entity/`
  - `VectorizationPlanEntity`
  - `VectorizationPlanRevisionEntity`
  - `VectorizationSourceConnectionEntity`
  - `VectorizationRunEntity`
  - `VectorizationRunStepEntity`
  - `VectorizationCheckpointEntity`
  - `VectorizationFailureBucketEntity`
- `repository/`
- `model/`
- `service/`
  - `VectorizationPlanService`
  - `VectorizationSourceConnectionService`
  - `VectorizationDryRunService`
  - `VectorizationRunnerProvisioningService`
  - `VectorizationRunLifecycleService`
  - `VectorizationRunDispatchService`
  - `VectorizationRunReconciliationService`
  - `VectorizationTargetResolutionService`
  - `VectorizationSecretResolutionService`
- `web/`
  - `VectorizationController`
  - `VectorizationConnectionController`
  - `VectorizationRunController`

This should be a first-class platform domain.

### 3.2 UI code

New UI code should live under:

- `Platfrom/ui/src/pages/`
- `Platfrom/ui/src/components/`
- `Platfrom/ui/src/api/`

Recommended UI surface:

- `VectorizationPlansPage.tsx`
- `VectorizationPlanWorkspacePage.tsx`
- `VectorizationConnectionsPage.tsx`
- `VectorizationRunsPage.tsx`
- `VectorizationRunDetailsDrawer.tsx`
- `VectorizationMappingEditor.tsx`
- `VectorizationDryRunResults.tsx`

### 3.3 Product-shared integration primitives

Recommended new module:

- `ai-infrastructure-module/ai-fabric-product-integration-core/`

Recommended packages:

- `com.ai.fabric.integration.connection`
- `com.ai.fabric.integration.auth`
- `com.ai.fabric.integration.credential`
- `com.ai.fabric.integration.http`
- `com.ai.fabric.integration.sql`
- `com.ai.fabric.integration.file`
- `com.ai.fabric.integration.discovery`

This module should contain:

- endpoint and pagination models
- auth bindings
- resolved credential material objects
- safe HTTP client factory
- timeout and retry policy
- response redaction helpers
- SQL extraction helpers
- file readers
- source discovery contracts

It should not resolve platform secrets directly.

### 3.4 Product vectorization core

Recommended new module:

- `ai-infrastructure-module/ai-fabric-vectorization-core/`

Recommended packages:

- `com.ai.fabric.vectorization.adapter.source`
  - `VectorizationSourceAdapter`
  - `RestApiSourceAdapter`
  - `SqlSourceAdapter`
  - `FileSourceAdapter`
- `com.ai.fabric.vectorization.adapter.target`
  - `VectorizationTargetWriter`
  - `RuntimeDataSyncTargetWriter`
- `com.ai.fabric.vectorization.execution`
  - batching
  - coarse checkpointing
  - lifecycle-aware execution
  - failure bucketing
- `com.ai.fabric.vectorization.mapping`
  - mapping source data to deployment entities
  - transformation
  - vector-content composition
- `com.ai.fabric.vectorization.discovery`
  - schema/sample/dataset discovery

### 3.5 Framework-local migration module

The existing framework module:

- `ai-infrastructure-module/ai-infrastructure-migration/`

should remain a framework-local backfill or reindex utility.

It is not the primary active foundation for Track B anymore.

### 3.6 Vectorization runner

Recommended runnable service:

- `ai-infrastructure-module/ai-fabric-vectorization-runner/`

This runner should:

- pull eligible work from the platform
- fetch run context and resolved execution material
- execute using `ai-fabric-vectorization-core`
- report heartbeats, coarse checkpoints, failures, and final status back to the platform

This runner should be:

- provisioned per deployment
- provisioned with customer connectivity like the connector
- ephemeral by default

It is not:

- a permanent shared runner pool by default
- a customer-facing deployment type
- an action connector

---

## 4) Provisioning And Placement

The vectorization runner should be treated as part of the provisioning layer.

Recommended provisioning model:

- runtime and connector remain the serving plane
- vectorization runner is an optional temporary execution component
- provisioning should be able to create:
  - runtime
  - connector
  - optional vectorization runner

Provisioning inputs for the runner should include:

- deployment id
- customer/tenant binding
- source connectivity settings
- runner auth and control-plane registration config

The runner should be deletable after indexing completes.

---

## 5) Control-Plane And Runner Integration

### 5.1 Lifecycle authority

The platform is the lifecycle command authority.

Platform owns:

- create run
- start
- pause
- resume
- cancel
- retry

Runner owns:

- claim
- heartbeat
- execute
- cooperate with lifecycle requests
- report technical outcomes

### 5.2 Pull-only network model

The runner should always pull.

That means:

- platform does not speak directly to the runner
- runner registers or heartbeats to the platform
- runner polls or claims work
- runner fetches run context
- runner pushes status updates back to the platform

### 5.3 Status model

Recommended platform intent states:

- `QUEUED`
- `PAUSE_REQUESTED`
- `RESUME_REQUESTED`
- `CANCEL_REQUESTED`
- `RETRY_REQUESTED`

Recommended runner execution states:

- `CLAIMED`
- `RUNNING`
- `PAUSED`
- `FAILED`
- `COMPLETED`
- `CANCELLED`

---

## 6) Tracking Model

The platform remains the authoritative system of record.

The runner does not need an authoritative internal DB.

For Track B, the platform should track **coarse execution state**, not full artifact receipts.

Recommended platform-tracked fields:

- run identity
- plan revision
- deployment id
- runner id
- current status
- heartbeat timestamp
- current source page or cursor
- current source id range where applicable
- batch counters
- rough indexed count
- rough failed count
- failure buckets
- operator-visible logs and summaries

Optional local runner persistence may exist later for resilience, but not as the source of truth.

---

## 7) Rollback And Recovery

Rollback should not be a core Track B promise.

Reasons:

- indexing is expensive
- token cost matters
- rerun or patch is often preferable
- full fine-grained rollback tracking adds too much complexity for this layer

Recommended Track B recovery posture:

- cancel run
- adjust plan or mapping
- rerun
- optionally patch/update indexed entities later

Track B should not start with:

- rich compensating rollback
- full write receipt ledger

---

## 8) Verification Posture

Verification should come later than basic execution.

Later verification should compare:

- source-side rough counts
- indexed counts
- vector spaces
- deployment entity coverage

Track B should first deliver:

- working onboarding vectorization
- lifecycle control
- coarse progress visibility

Then later:

- source-vs-index verification

---

## 9) Respecting Deployment Entity And Tenancy Model

Vectorization must always use:

- the deployment's current configured entities
- the deployment's current target vectorization path
- the deployment's current multi-tenancy/shared-storage posture

So if the deployment is configured with:

- `product`
- `policy`
- `review`

the vectorization runner must map into those entity spaces, not invent a separate schema.

And if shared storage is enabled for that deployment's tenant posture, vectorization must respect it automatically through the deployment target resolution path.

---

## 10) Recommended Track B Build Order

1. platform vectorization domain model
2. source connection model and secret references
3. plan revisions and dry-run preview
4. deployment-scoped runner provisioning
5. pull-only lifecycle flow and coarse checkpoints
6. later verification against source and indexed target state

---

## 11) Concrete Recommendation

The active Track B implementation should be:

- platform vectorization control plane in `Platfrom/backend/.../vectorization`
- shared product connectivity/auth/client code in `ai-fabric-product-integration-core`
- product vectorization execution in `ai-fabric-vectorization-core`
- deployment-scoped ephemeral runner in `ai-fabric-vectorization-runner`
- runtime data-sync as the default target ingestion boundary

This gives us:

- a goal aligned with onboarding reality
- respect for current deployment entity and tenancy configuration
- customer-connectivity-aware execution
- no need to turn Track B into a generic migration or rollback platform
