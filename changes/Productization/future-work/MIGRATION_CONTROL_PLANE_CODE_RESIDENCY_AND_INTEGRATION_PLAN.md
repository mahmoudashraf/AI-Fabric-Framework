# Migration Control Plane Code Residency And Integration Plan

Status: architecture and code-residency companion (2026-04-03)

This document complements:

- `DATA_MIGRATION_PLATFORM_PLAN.md`
- `PLATFORM_EXECUTION_SEQUENCE_WAVE4_PLAN.md`

It answers the implementation question more concretely:

- where each Track B code set should live
- how migration integrates with the existing platform, runtime, and connector code
- how shared connectivity, auth, secret, and client primitives should be reused without collapsing migration into the action-connector model

---

## 1) Core Product Decision

Migration should be built as:

- a **platform-managed control plane**
- a **product-owned migration execution plane**
- a **product-shared integration primitive layer**
- a **small set of generic source adapters**

It should **not** be modeled as:

- a normal customer deployment
- a standard action connector
- an ad hoc script system

The correct relationship to the existing runtime and connector stack is:

- reuse the lower-level connectivity and auth patterns
- do not reuse `/actions/execute` as the migration contract
- do not force long-running migration jobs through the runtime serving path

Important code-boundary rule:

- `runtime`, `connector`, and `migration-runner` are product code
- shared connectivity, auth, credential-material, and client primitives used by them should also live in product code
- the generic framework should keep only genuinely reusable migration or indexing contracts

---

## 2) Target Architecture

The enterprise Track B architecture should be:

1. **Platform migration control plane**
   - plans, source connections, dry runs, runs, checkpoints, audit, approval, diagnostics
2. **Product-shared integration primitive layer**
   - transport/auth/client building blocks reused by runtime, connector, and migration-runner
3. **Migration execution engine**
   - product execution logic for batching, checkpointing, resume, failure buckets, and target writes
4. **Migration runner**
   - platform-hosted runner or customer-hosted runner process
5. **Target ingestion boundary**
   - primarily runtime data-sync APIs, not raw vector-store writes from the platform

The execution model should therefore be:

- UI and API in the platform
- job execution outside normal runtime request serving
- source connectivity through generic source adapters implemented in the product layer
- writes into the target deployment through a controlled target writer

---

## 3) Code Residency

### 3.1 Platform control plane code

New Track B platform code should live under:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/migration/`

Recommended package structure:

- `entity/`
  - `MigrationPlanEntity`
  - `MigrationSourceConnectionEntity`
  - `MigrationRunEntity`
  - `MigrationRunStepEntity`
  - `MigrationCheckpointEntity`
  - `MigrationErrorRecordEntity`
  - `MigrationTemplateEntity`
- `repository/`
  - Spring Data repositories for the entities above
- `model/`
  - request and response DTOs for UI and API
- `service/`
  - `MigrationPlanService`
  - `MigrationSourceConnectionService`
  - `MigrationSchemaDiscoveryService`
  - `MigrationDryRunService`
  - `MigrationExecutionDispatchService`
  - `MigrationRunLifecycleService`
  - `MigrationRunReconciliationService`
  - `MigrationTargetResolutionService`
  - `MigrationConnectionSecretResolutionService`
- `web/`
  - `MigrationController`
  - `MigrationSourceConnectionController`
  - `MigrationRunController`
  - `MigrationTemplateController`

This should be a first-class platform domain, not another sub-surface hidden inside deployment service classes.

### 3.2 UI code

New UI code should live under:

- `Platfrom/ui/src/pages/`
- `Platfrom/ui/src/components/`
- `Platfrom/ui/src/api/`

Recommended UI surface:

- `MigrationPlansPage.tsx`
- `MigrationPlanWorkspacePage.tsx`
- `MigrationConnectionsPage.tsx`
- `MigrationRunsPage.tsx`
- `MigrationRunDetailsDrawer.tsx`
- `MigrationMappingEditor.tsx`
- `MigrationDryRunResults.tsx`

Recommended API client additions:

- `Platfrom/ui/src/api/platformApi.ts`
  - migration plan CRUD
  - connection test/discovery
  - dry-run
  - run start/pause/cancel/retry
  - run detail and step history

### 3.3 Product-shared integration primitives

This should be a closed-source product module shared by runtime, connector, and migration-runner.

Recommended new module:

- `ai-infrastructure-module/ai-fabric-product-integration-core/`

Recommended packages:

- `com.ai.fabric.integration.connection`
  - normalized connection definitions
  - endpoint models
  - pagination/cursor models
- `com.ai.fabric.integration.auth`
  - `ApiKeyAuthBinding`
  - `BearerTokenAuthBinding`
  - `BasicAuthBinding`
  - `OAuthClientCredentialsBinding`
  - custom-header auth binding
- `com.ai.fabric.integration.credential`
  - resolved credential material objects
  - redaction-safe summaries
  - no direct platform DB access
- `com.ai.fabric.integration.http`
  - shared HTTP client factory
  - timeout and retry policies
  - safe URL handling
  - response redaction helpers
- `com.ai.fabric.integration.sql`
  - JDBC/query helper layer for read-only extraction
- `com.ai.fabric.integration.file`
  - CSV/JSON/JSONL readers
- `com.ai.fabric.integration.objectstore`
  - S3/Azure Blob/GCS style object readers if and when needed
- `com.ai.fabric.integration.discovery`
  - schema/sample/dataset discovery contracts

This module is where shared connectivity/auth/client behavior should live for product services.

It should **not** resolve platform secrets directly.

### 3.4 Migration execution engine

The existing framework migration module is real:

- `ai-infrastructure-module/ai-infrastructure-migration/`

It already contains:

- `DataMigrationService`
- `MigrationJob`
- `MigrationRequest`
- `MigrationJobRepository`
- `MigrationAutoConfiguration`

This module is still useful as a **framework-local backfill and reindex capability** for applications using AI Fabric outside the platform.

It should not be treated as the full enterprise migration product foundation.

The product path should instead introduce a product migration engine module such as:

- `ai-infrastructure-module/ai-fabric-migration-core/`

That product module should contain the enterprise external-source execution contracts and implementations.

Recommended packages in the product migration module:

- `com.ai.fabric.migration.adapter.source`
  - `MigrationSourceAdapter`
  - `RestApiSourceAdapter`
  - `SqlSourceAdapter`
  - `FileSourceAdapter`
- `com.ai.fabric.migration.adapter.target`
  - `MigrationTargetWriter`
  - `RuntimeDataSyncTargetWriter`
  - `ApplicationRestTargetWriter`
- `com.ai.fabric.migration.execution`
  - run loop
  - batching
  - resume/checkpoint logic
  - failure bucketing
  - replay support
- `com.ai.fabric.migration.checkpoint`
  - cursor serialization
  - last-success markers
- `com.ai.fabric.migration.mapping`
  - field mapping
  - transformation and normalization
  - vector-content composition rules
- `com.ai.fabric.migration.discovery`
  - source schema/sample contracts

If any logic from `ai-infrastructure-migration` remains genuinely framework-agnostic, it can be extracted or mirrored deliberately later.
The product should not be forced to depend on the current repository-based design as its primary migration engine.

### 3.5 Migration runner / agent

Recommended runnable service:

- `ai-infrastructure-module/ai-fabric-migration-runner/`

This service should:

- fetch a prepared migration run context
- execute the run using `ai-fabric-migration-core`
- emit step events, logs, checkpoints, and run summaries back to the platform

This should be designed as one migration-runner capability with two deployment modes:

- platform-hosted mode
  - deployed as a platform-owned internal execution service
  - likely first hosted on Railway
- customer-hosted mode
  - deployed inside the customer environment for private-network or on-prem connectivity
  - same migration-runner capability, not a separate migration product

If a separate packaging profile or runnable wrapper is needed later for customer-hosted installation, that is an operational packaging choice, not a different architecture.

The customer-hosted mode is for customer-local connectivity.
It is not a normal deployment and not an action connector.

---

## 4) What We Reuse From Existing Code

### 4.1 Platform secret boundary

The platform secret boundary stays here:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/secret/service/PlatformSecretService.java`

The platform should continue to own:

- secret storage
- secret reference validation
- audit
- display/source state (`DATABASE`, `ENV`, `MISSING`)

Migration code should not bypass this.

New migration source connections should store:

- references to platform secrets
- non-secret connection settings
- auth mode metadata

At execution time the platform should resolve those references into an execution-safe bundle.

### 4.2 Deployment secret diagnostics

The current secret reference and literal-risk pattern already exists in:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentSecretUsageService.java`

Track B should mirror this posture for migration source connections:

- detect literal credentials
- require secret placeholders/references
- surface missing secret references before run start

### 4.3 Provider connectivity and connection-test patterns

The platform already has vendor connectivity probing in:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentProviderConnectivityService.java`

Track B should reuse the same architectural pattern:

- platform-controlled connection tests
- clear `READY`, `FAILED`, `BLOCKED` style results
- redacted error messages

But it should use migration adapters instead of provider-specific deployment probe logic.

### 4.4 Runtime ingestion boundary

The existing ingestion seam already exists:

- `ai-infrastructure-module/ai-infrastructure-data-sync/src/main/java/com/ai/infrastructure/datasync/controller/DataSyncController.java`

And the generic REST connector already exposes a pass-through alias:

- `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/controller/RuntimeDataSyncProxyController.java`
- `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/src/main/java/com/ai/infrastructure/connector/rest/runtime/RuntimeProxyClient.java`

This should remain the preferred target-side write boundary for AI-indexed entities because it preserves:

- runtime validation
- entity-space rules
- indexing behavior
- embedding behavior
- vector store invariants

### 4.5 Existing migration execution kernel

The existing module:

- `ai-infrastructure-module/ai-infrastructure-migration/`

already provides:

- job state
- async execution
- repository-driven entity migration
- progress tracking

This should remain useful for framework-local backfill or reindex use cases.

Track B should not force the enterprise migration product to use it directly as the main execution core.
Instead:

- keep it as a framework-local migration/backfill capability
- build the enterprise external-source execution path in product code
- selectively extract or mirror only the parts that remain truly generic

---

## 5) How Integration Should Happen

### 5.1 Control-plane flow

The end-to-end platform flow should be:

1. user opens migration workspace for a deployment
2. platform creates or edits a `MigrationPlan`
3. platform binds the plan to:
   - one deployment
   - one target tenant
   - one source connection
4. platform stores secret references, not raw credentials
5. platform tests the connection using the source adapter and shared integration primitives
6. platform performs schema/sample discovery
7. platform stores mapping and dry-run configuration
8. platform dispatches a run to the migration runner
9. runner executes and reports progress back to the platform
10. platform shows logs, checkpoints, failures, and retry controls

### 5.2 Secret and credential flow

The secret flow should be:

1. source connection stores secret references
2. platform resolves references using `PlatformSecretService`
3. platform creates an ephemeral execution bundle for one run
4. runner receives resolved material through:
   - a signed context fetch
   - or scoped job token exchange
   - or temp-file injection for platform-hosted execution
5. runner builds auth/client objects from the shared integration primitive layer

The runner should not be given direct access to the platform secret database.

For customer-hosted runner mode, the preferred trust model is:

- outbound-only runner registration to the platform
- job polling or job claim from the customer boundary
- ephemeral execution bundle per run
- no inbound public exposure required from the customer network

### 5.3 Runner state and database model

The platform should remain the **authoritative system of record** for migration runs.

That means the authoritative job state should live in platform DB:

- run identity
- current status
- assigned runner
- heartbeat timestamp
- step history
- counters
- checkpoints
- failure buckets
- operator-visible logs and summaries

The runner does **not** need its own authoritative internal database.

Recommended runner state model:

- platform DB is authoritative
- runner holds in-memory execution state for the active leased run
- runner posts heartbeats, step transitions, checkpoint commits, and completion/failure back to the platform

Optional local runner persistence may exist later only for:

- crash-safe lease recovery
- spool or retry buffering
- local transport resilience in customer-hosted mode

But even then:

- local runner storage is not the source of truth
- platform remains the system of record

What the runner should share back to the platform:

- run accepted / run started
- runner identity and mode
- heartbeat
- step started / step completed
- batch counters
- checkpoint cursor
- per-batch or per-step failures
- final status
- redacted logs and operator summary
- any generated reconciliation artifacts that are safe to retain

### 5.4 Adapter execution flow

The adapter flow should be:

1. `MigrationSourceAdapter` receives:
   - connection settings
   - resolved credential material
   - dataset selection
   - cursor/checkpoint state
2. adapter performs:
   - test connection
   - discover datasets
   - sample schema
   - read paged records
3. mapping layer normalizes records into target payloads
4. `MigrationTargetWriter` writes through the correct target boundary
5. execution engine records:
   - run step
   - checkpoint
   - row counts
   - failure bucket

### 5.5 Target write flow

For AI-enabled entity imports, the preferred path should be:

- migration runner -> runtime data-sync API

Alternative target writers are allowed only when the use case requires them:

- application REST target
- non-vector operational API target

The migration plane should not write directly to vendor vector APIs as the default product path.

---

## 6) Relation To Existing Action Connectors

Migration should be related to the connector stack underneath, but not identical to it.

### 6.1 What should be shared

Shared lower-level concerns:

- outbound HTTP client creation
- auth header or token injection
- retry and timeout policy
- safe URL handling
- response redaction
- connector-safe logging posture

### 6.2 What should not be shared as the main contract

Migration should not reuse:

- `/actions/execute`
- action catalog registration
- action idempotency model as the main job state model
- confirmation-interceptor semantics

Reason:

- actions are request/response oriented
- migrations are long-running, batched, checkpointed, and replayable

### 6.3 Recommended implementation rule

Do this:

- build shared product transport/auth primitives
- let both connectors and migration adapters depend on those primitives

Do not do this:

- make migration a special kind of action connector

---

## 7) Customer, Tenant, and Deployment Interaction

The migration model must respect the current enterprise identity model:

- `Customer -> Tenant -> Deployment`

Rules:

- a migration plan is bound to one deployment
- that deployment belongs to exactly one tenant
- migration source connections belong to the customer boundary
- migration runs must not cross customer boundaries
- shared migration source profiles may be reused only inside the same customer boundary

This matches the Track A tenancy work already completed on this branch.

---

## 8) Security Rules

Track B should be built with these rules from the start:

- no raw credentials in migration plans
- no raw credentials in UI payloads after initial save
- no cross-customer connection reuse
- no unrestricted arbitrary URL execution
- no direct vector-store writes as the default path
- no migration execution inside normal runtime serving threads
- all run starts, pauses, cancels, retries, and connection updates are audited
- connection tests and run failures must redact sensitive upstream data

---

## 9) Recommended Track B Build Order

Build order should be:

1. create platform migration domain packages under `Platfrom/backend/.../migration`
2. create `ai-fabric-product-integration-core`
3. create `ai-fabric-migration-core`
4. create platform source-connection and dry-run APIs
5. create migration runner dispatch and run lifecycle APIs
6. add UI pages for plans, mappings, dry runs, and runs
7. add customer-hosted runner packaging and registration flow for private-network and on-prem use cases

---

## 10) Operational Deployment Modes

The migration runner should support these operational modes:

### 10.1 Platform-hosted mode

Use this when:

- the source system is reachable from the platform environment
- the customer accepts platform-managed execution
- the migration is primarily cloud-to-cloud

Expected first host:

- a platform-owned Railway service or worker

### 10.2 Customer-hosted mode

Use this when:

- the source database or API is only reachable inside customer network boundaries
- the customer requires on-prem or private-network extraction
- the customer wants source access to stay inside their environment
- residency or compliance constraints require local execution

Recommended behavior:

- the customer-hosted runner polls or claims jobs from the platform
- the runner uses platform-issued scoped execution context
- the platform remains the control plane and system of record

### 10.3 Boundary rule

The customer-hosted runner is:

- platform-orchestrated execution
- customer-boundary network placement

It is not:

- a customer deployment
- an action connector
- a second migration product

---

## 11) Concrete Recommendation

The best Track B shape for this repo is:

- platform-managed migration control plane in `Platfrom/backend/.../migration`
- shared transport and auth primitives in a new `ai-fabric-product-integration-core` module
- enterprise migration execution in `ai-fabric-migration-core`
- keep `ai-infrastructure-migration` as framework-local backfill/reindex support, not as the primary enterprise migration engine
- runtime data-sync as the primary AI ingestion target
- one migration-runner capability that can be deployed either:
  - as a platform-owned Railway service
  - or as a customer-hosted on-prem or private-network runner

That gives us:

- clear ownership boundaries
- strong secret boundaries
- reusable connectivity primitives
- no misuse of action connectors
- a path to enterprise private-network execution later
