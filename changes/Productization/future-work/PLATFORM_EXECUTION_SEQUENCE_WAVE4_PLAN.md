# Platform Execution Sequence Wave 4 Plan

Status: execution-sequencing document (2026-04-03)

This document defines Wave 4 as the enterprise expansion wave after the now-completed control-plane and managed-vector foundation work.

Current completed execution state on this branch:

- Wave 1: complete
- Wave 2: complete
- Wave 3: complete
- Wave 3.5: complete
- Wave 4 Track A: complete

Wave 4 should now move the product from:

- strong deployment control plane

to:

- stronger tenant and customer activation foundations
- stronger operator assistance
- stronger customer-specific platform isolation
- stronger deployment target reach

It should do this without diluting the product's core positioning as an enterprise AI deployment control plane.

---

## 1) Current Position Before Wave 4

What is already complete and should be treated as prerequisite:

- unified deployment workspace and enterprise administration foundation
- approvals, assignments, access administration, and destructive-operation guardrails
- prompt-management foundation and session-scoped hot-apply groundwork
- POC workspace, test-data import/reset, and scenario library groundwork
- release verification gates, diagnostics, remediation, and readiness workflows
- secret/config separation and provider/service navigation
- managed vector database request path with platform-managed vendor support where applicable
- verified deployment stacks and platform-hosted verification operations
- stable `Customer -> Tenant -> Deployment` identity, tenant binding UI, and customer-admin tenant management
- provider-native shared vector handle resolution for Pinecone, Qdrant, Weaviate, and Milvus
- tenant-scoped verification, diagnostics, cleanup posture, migration compatibility, and customer-boundary enforcement for shared vector roots

What is still missing and should now shape the next major execution wave:

- Track B verification closure for vectorization and tenant-scoped proof
- a productized platform assistant deployment
- deployment-scoped provider secret overrides
- provider-neutral deployment target profiles and multi-cloud expansion

Numbering note:

- Wave 3.5 consumed items `43` through `52`
- Wave 4 therefore starts at item `53`

---

## 2) Sequencing Principles

Wave 4 should follow these rules:

- build on the completed Wave 3.5 managed vector foundation instead of reopening it
- start with customer and tenant plus shared-resource foundations because deployment ids are not durable enough to serve as the enterprise isolation boundary
- allow shared storage only when provider-native isolation exists and is verified; otherwise keep the deployment on dedicated infrastructure
- keep shared storage inside one customer boundary; never mix tenants from different customers in the same shared-storage scope
- keep the first Wave 4 tracks platform-heavy: tenant model, vectorization, operator assistance, secret scope, then deployment targets
- use the platform assistant as a real dogfooding deployment, not a hardcoded support widget
- add deployment-scoped provider secret overrides only after the tenant/shared-resource model and early vectorization slices make multi-customer credential isolation materially useful
- refactor provisioning around deployment target profiles before broad provider expansion
- keep optional create-flow enhancements outside the core Wave 4 sequence
- keep runtime answer-quality work as later runtime tuning, not a core platform-wave blocker
- keep the confirmation-interception ladder and remote policy service as a Wave 5 runtime and business-logic track, not a Wave 4 platform-wave blocker
- keep Shopify, broad shared-runtime architecture, and open-core strategy as later strategic tracks, not Wave 4 blockers

---

## 3) Recommended Wave 4 Execution Sequence

### Track A: Customer, tenant, and shared resource foundation

Status on this branch: complete.

Track A should be executed against:

- `TENANT_SCOPED_SHARED_VECTOR_INFRASTRUCTURE_PLAN.md`

53. customer and tenant identity foundation: introduce a stable `Customer -> Tenant -> Deployment` model independent of deployment lifecycle, with one deployment bound to exactly one tenant, customer ownership boundaries, tenant-aware audit references, and tenant-binding UI for deployments
54. provider-native shared vector isolation model: add provider-native isolation contracts per supported backend for shared infrastructure, such as Pinecone namespace, Qdrant collection or equivalent, Weaviate tenant or class boundary, and Milvus or Zilliz database or collection boundary, plus provider UI visibility for effective scoped resource handles
55. tenant-scoped shared-resource lifecycle, verification, and migration compatibility: add create, reuse, reconcile, cleanup, backup and restore posture, dedicated-to-shared compatibility at tenant and resource scope, and verification and diagnostics UI for tenant-scoped shared resources

Track A must be built around these rules:

- `Customer -> Tenant -> Deployment` is the durable enterprise ownership and isolation model
- one deployment belongs to exactly one tenant
- a customer may own multiple tenants and tenant-bound deployments
- tenant identity, not deployment id, is the durable enterprise data isolation boundary
- shared storage is allowed only when the provider exposes a real isolation primitive the platform can model, provision, verify, and clean up
- shared storage must not cross customer boundaries
- runtime-side tagging and post-filtering are not the primary enterprise isolation strategy
- if a provider cannot support safe shared isolation, the supported posture remains dedicated storage
- operators must be able to configure tenant binding and storage posture through the platform UI, not through hidden config only
- the UI must expose resolved provider scope handles and tenant-isolation verification status

### Track B: Vectorization layer and onboarding indexing execution

Status on this branch: core implementation complete; verification closure still open.

Track B should be executed against:

- `ONBOARDING_VECTORIZATION_LAYER_PLAN.md`
- `VECTORIZATION_LAYER_CODE_RESIDENCY_AND_INTEGRATION_PLAN.md`
- `VECTORIZATION_AND_TENANT_SCOPED_VERIFICATION_HARDENING_PLAN.md`

56. vectorization domain model foundation: introduce vectorization plans, revisions, source connections, runs, sync-state tracking, runner-mode and compatibility metadata, indexed-output semantics hashing, coarse checkpoints, failure buckets, and run reasons such as bootstrap or reindex as deployment-linked platform entities
57. source connection, bootstrap detection, and execution-identity model: add secure source-connection definitions, connection testing, deployment-scoped indexed-coverage detection for configured entities, zero-source handling, and deployment-scoped execution identity with configurable runner modes
58. vectorization mapping, preview, and reindex choice workspace: add source-to-entity mapping, transformation rules, vector-content composition, preview or dry-run evidence against the deployment's configured entities, and customer-selected entity-scope or full reindex choices when config changes affect indexed output
59. deployment-scoped vectorization execution plane: default new eligible deployments to `PLATFORM_MANAGED_AUTO`, support `PLATFORM_MANAGED_NONE` and `CUSTOMER_MANAGED_REMOTE`, protect execution with registration, short-lived session, and lease control, allow managed runners to be reprovisioned or deleted after indexing completes, and detect `CURRENT`, `OUTDATED`, and `INCOMPATIBLE` runner status
60. lifecycle, idempotent ingestion, bootstrap, and later verification posture: add platform-owned lifecycle commands, idempotent runtime data-sync upsert semantics, coarse page or range checkpoints, failure buckets, bootstrap vectorization when configured deployment entities are not yet indexed, out-of-date sync status when reindex is deferred, audited manual-confirm override, rerun-oriented recovery, and a later verification path comparing indexed state against source data

### Track B Verification Closure: Live proof and admin verification

Status on this branch: open.

61. verification domain model and admin APIs: add vectorization verification records, step evidence, timing capture, deployment-scoped operator verification, and platform-admin verification sweeps
62. managed runner provisioning and discovery proof: add live proof for managed runner deployment, registration, compatibility, and discovery against real source connectivity
63. bounded sample vectorization execution proof: add admin-triggered active vectorization smoke that writes through runtime data-sync, captures checkpoints and timings, and proves sync-state transitions
64. tenant-scoped isolation proof: add paired-deployment verification for shared-root tenant isolation with canonical fixtures and real deployment-path evidence
65. hosted and GitHub verification parity: extend hosted verification, GitHub Actions, and platform verification UI so the same vectorization and tenant-isolation proofs can be run and inspected end to end

### Track C: Platform assistant as a first-class deployment

Current sequencing note:

- Track C remains valid Wave 4 scope
- Track B verification closure and Track D are now complete on branch
- the next active Wave 4 execution target is Track C
- the detailed execution baseline is `PLATFORM_ASSISTANT_TRACK_C_EXECUTION_PLAN.md`

66. platform assistant template and bootstrap path: add a dedicated platform-assistant deployment template with curated sources, actions, and safer defaults
67. platform assistant source providers and scoped retrieval: expose guides, deployment metadata, releases, diagnostics, verification, and audit summaries as assistant sources
68. deployment-scoped assistant UI and read-only action layer: add a real assistant page and deployment side panel with citations, scoped answers, and bounded read-only platform actions
69. approval-aware platform assistant actions: add preview, confirmation, approval, audit, and permission-aware execution for sensitive administrative assistant actions

Track C should be hardened around this concrete product shape:

- it is an operator-facing assistant surface, not an end-customer chatbot
- it must be a real platform-managed deployment, not a hardcoded chatbot service bolted onto the UI
- it should be treated as part of the platform itself:
  - create if missing
  - restore if archived
  - reconcile or re-apply if not up or running
- it should have:
  - a simple first-party `Assistant` page as the first shipped UI
  - deployment-aware context when the user is inside a deployment workspace
  - optional later shell-level entry affordances and richer deployment-scoped side panels
- its first curated baseline should be:
  - a wired `support` curated module backed by `ai-curated-support`
- its first platform integration should be:
  - assistant connector upstream pointing at the platform API
  - action-first bounded read and write platform actions
  - a dedicated platform assistant authorization endpoint checked before privileged execution
- its curated source set should start with:
  - deployment metadata and workspace summaries
  - release history
  - verification evidence
  - diagnostics summaries
  - audit summaries
  - guides and runbooks later where they add clear operator value
- its action model should start with:
  - bounded read platform actions
  - bounded write platform actions where current-user permission and approval already allow them
- it must operate as the current authenticated user:
  - never as a hidden super-admin
  - never beyond the user's effective permissions
  - never exposing secret values
  - never by trusting raw user or role fields passed from the chat payload

### Track D: Deployment-scoped provider secret overrides

Status on this branch: complete.

Current sequencing note:

- Track D followed Track B verification closure and is now complete on branch
- Track C is now the next active execution target
- the detailed planning baseline is `DEPLOYMENT_SCOPED_PROVIDER_SECRET_OVERRIDES_PLAN.md`
- the Track D design review is now closed on the major points: dedicated binding-table persistence, explicit fallback modes, deployment-admin bind-only scope, paired Milvus credential handling, and cleanup ownership defaults
- the branch now includes precedence-aware resolution, deployment override bindings, Secrets and Providers surfaces, hard-delete cleanup, and local plus live regression coverage for override behavior

70. secret scope foundation and precedence model: add global, deployment-override, deployment-managed, and environment-fallback scopes with explicit resolution precedence
71. deployment override references, diagnostics, and audit: add deployment-level provider secret references, effective resolution visibility, and fallback-aware diagnostics
72. secrets workspace and cleanup support for overrides: add override management in Secrets, effective source visibility in Providers, and hard-delete cleanup for deployment-owned overrides

Track D is intentionally late in the wave:

- it matters for enterprise customer isolation
- it should follow the tenant/shared-resource foundation
- it should land before broader provider-neutral target expansion bakes in more global-secret assumptions

### Track E: Deployment target profiles and multi-cloud expansion

73. deployment target profile model: refactor from global provisioning mode to per-deployment target profiles with one platform default target
74. provider-neutral deployment service contract and OCI image release model: compile runtime, REST connector, artifacts, env, and health expectations into a provider-neutral deployment request
75. first optional non-Railway provider target: add AWS App Runner as the first managed alternative target after the target-profile refactor is stable
76. second managed container target: add Azure Container Apps after the provider-neutral deployment contract is proven

---

## 4) Wave 4 Scope Notes

Wave 4 should explicitly include:

- customer and tenant identity foundations that outlive deployment replacement
- provider-native shared vector isolation where the vendor supports it
- full vectorization control-plane modeling for onboarding indexing
- a realistic vectorization engine built around product-owned source adapters for files, REST APIs, and SQL
- deployment-scoped ephemeral vectorization runners provisioned with customer connectivity
- admin-triggered vectorization and tenant-scoped verification that proves real runner provisioning, discovery, bounded execution, and shared-storage isolation
- product-shared connectivity, auth, credential-material, and client primitives that serve runtime, connector, and vectorization-runner without collapsing vectorization into the action-connector contract
- a platform-managed assistant deployment for dogfooding and operator productivity, with a dedicated assistant UI and deployment-scoped side panels
- deployment-scoped provider secret overrides as a late support capability for multi-customer isolation
- target-profile-based multi-cloud expansion

Wave 4 should explicitly not attempt to finish:

- runtime-side tenant emulation as the main shared-storage model
- connectors for every source system
- optional advanced deployment-create wizard work
- self-hosted cloud fallback for vector databases
- runtime action-grounded answering and deep knowledge navigation as a full product track
- confirmation-interception ladder and remote policy service as a full product track
- generic plugin loading of arbitrary customer Java into runtime
- GCP, ECS, EKS, or AKS in the first multi-cloud increment
- broad customer-facing white-label assistant surfaces
- Shopify-first product branching
- broad shared-runtime architecture
- open-core or framework release-packaging strategy

Those remain valid future directions, but they should not be mixed into the first enterprise expansion wave.

Recommended next-wave follow-up after Wave 4:

- Wave 5 should take the confirmation-interception ladder:
  - config-driven confirmation interception first
  - remote confirmation policy service second
- later runtime-tuning work should take action-grounded answering and deep knowledge navigation

---

## 5) Why This Is The Right Wave 4 Shape

### 5.1 Tenant and shared-resource foundations come first

Tenant and shared-resource work should come first because:

- it is the narrowest high-leverage change needed to unlock better unit economics and partner scale
- it builds directly on the completed managed-vector foundation from Wave 3.5
- enterprise isolation needs a stable customer and tenant boundary, not a deployment id that can change on rollout or replacement
- the `Customer -> Tenant -> Deployment` model is the right foundation for shared storage, vectorization, audit, and customer-owned tenant administration
- it creates the correct control-plane model for later vectorization, secret-scope, and provider-target work
- it forces the platform to model provider-native isolation honestly instead of relying on application-side conventions

### 5.2 Vectorization follows immediately after tenant and shared-resource foundations

Vectorization should follow immediately after Track A because:

- it is the most direct bridge from deployment operations to customer activation
- it helps customers get real indexed data into a deployment faster
- it leverages the POC and import groundwork already delivered in earlier waves
- it benefits from having the correct tenant and resource model in place first
- it should be implemented through a product vectorization layer with a few source patterns, not endless bespoke connectors

Track B should not be considered fully complete until the verification-closure work proves:

- live managed runner provisioning
- live discovery
- live bounded vectorization execution
- tenant-scoped shared-storage isolation
- platform-admin and hosted or GitHub verification parity

### 5.3 Platform assistant follows after tenant and vectorization foundations

The platform assistant should follow because:

- it dogfoods the deployment model against real platform data
- it helps operators understand increasingly complex platform state
- it benefits directly from stronger vectorization and tenant foundations
- it creates a concrete operator-copilot surface inside the platform without changing the product category

### 5.4 Deployment-scoped provider secret overrides land late in the wave

Deployment-scoped provider secret overrides should land late in the wave because:

- they become much more valuable once tenant, shared-resource, and multi-customer deployment patterns are active
- they are important for customer-owned billing isolation, but they do not block the first tenant or vectorization slices
- they should be in place before broad provider-neutral target expansion bakes in more global-secret assumptions

### 5.5 Multi-cloud stays late in the wave

Multi-cloud should be the last track in the wave because:

- it has high architectural leverage, but is easy to over-expand too early
- it depends on getting target profiles and provider-neutral contracts right first
- it should be driven by product clarity, not by raw provider count

---

## 6) Completion Criteria

Wave 4 is complete when:

- the platform has a stable `Customer -> Tenant -> Deployment` model independent of deployment replacement
- every deployment is bound to exactly one tenant
- shared vector infrastructure is only supported where provider-native isolation primitives are modeled, verified, and operable
- shared vector infrastructure never crosses customer boundaries
- tenant-scoped shared-resource lifecycle, reconciliation, verification, and cleanup exist at the right resource boundary
- vectorization plans can be modeled, previewed, executed, and reconciled through the platform instead of ad hoc scripts
- a vectorization execution plane exists outside the serving runtime
- the platform assistant is a real platform-managed deployment with scoped retrieval, bounded actions, a dedicated assistant page, and deployment-scoped side panels
- approval-aware assistant actions are authorization-safe and auditable
- deployments can optionally use deployment-scoped provider secret overrides with clear fallback and audit
- deployments can target provider-neutral target profiles instead of one global provisioning mode
- at least one non-Railway provider target is usable through the same deployment control-plane abstractions
- backend tests, frontend build, and provider-specific verification paths exist for each completed track

---

## 7) Recommended Starting Item

The first item to build in Wave 4 should be:

- **53. customer and tenant identity foundation**

This is the best first item because it creates the durable control-plane identity model that later shared-resource isolation, vectorization, secret scope, and provider-target work can all build on.

It includes:

- stable customer and tenant identity records
- deployment-to-tenant linkage
- customer ownership boundaries
- tenant-aware audit and resource references
- groundwork for tenant-scoped shared-resource verification and lifecycle management

It does not yet include:

- provider-native resource provisioning details per backend
- vectorization plans or runner execution
- provider-secret override UI
- runtime answer-quality tuning
- confirmation interception and remote policy behavior
- multi-cloud target-profile work

Those follow immediately after the customer and tenant identity foundation.

---

## 8) Immediate Follow-up After The First Item

After item `53` is complete, the next items should be:

1. provider-native shared vector isolation model
2. tenant-scoped shared-resource lifecycle and compatibility
3. vectorization domain model foundation

This keeps Wave 4 anchored in:

- enterprise-safe isolation
- business-model leverage
- customer onboarding value
- platform-managed execution

---

## 9) Execution Progress

Current status:

- Wave 4 execution has not started yet on this reset branch state
- the next work item should begin from `53`
