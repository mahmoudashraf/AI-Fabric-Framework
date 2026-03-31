# Platform Execution Sequence Wave 4 Plan

Status: execution-sequencing document (2026-03-31)

This document defines Wave 4 as the enterprise expansion wave.

Wave 1 made the platform deployment-centric.

Wave 2 made it useful for rapid iteration and proof-of-concept work.

Wave 3 made it materially operable for production rollout and governed platform operations.

Wave 3.5 should make the deployment request path easier by adding **managed vector database** as a first-class deployment option before Wave 4 starts.

Wave 4 should move the product from:

- strong deployment control plane

to:

- stronger onboarding platform
- stronger operator assistance layer
- stronger customer-specific extensibility
- stronger infrastructure reach

It should do this without diluting the product’s core positioning as an enterprise AI deployment control plane.

---

## 1) Sequencing Principles

Wave 4 should follow these rules:

- treat managed vector database as a Wave 3.5 prerequisite, not as part of the main Wave 4 tracks
- keep the deployment control plane as the product center, even while adding migration, assistant, and multi-cloud capabilities
- treat migration as a first-class onboarding and activation capability, not as a hidden implementation script
- keep migration realistic by building a generic migration engine around a few source patterns first, not bespoke connectors for every source type
- use the platform assistant as a real dogfooding deployment, not a hardcoded support widget
- keep customer-specific business logic out of the core runtime process when external service boundaries are safer
- refactor provisioning around deployment target profiles before adding more cloud providers
- prefer enterprise-safe expansion over breadth-first feature sprawl
- keep Shopify and other verticals as validation pressure for the platform, not as the primary organizing abstraction for this wave

---

## 2) Recommended Wave 4 Execution Sequence

### Wave 3.5 prerequisite: Managed vector database request path

Before Wave 4 starts, complete the managed vector DB foundation described in:

- `MANAGED_VECTOR_DATABASE_DEPLOYMENT_PLAN.md`

That phase should introduce:

- vector provisioning mode
- platform-managed versus external-existing vector posture
- first-class managed vector resource tracking
- the first fully productized managed-vendor path starting with Qdrant

### Track A: Migration control plane and managed execution

43. migration domain model foundation: introduce migration templates, plans, source connections, runs, checkpoints, and error records as deployment-linked platform entities
44. migration source connection and secret model: add secure source-connection definitions, connection testing, schema/sample discovery, and reusable source profiles for a small set of generic source adapters
45. migration mapping and dry-run workspace: add source-to-entity mapping, transformation rules, vector-content composition, validation, and dry-run evidence
46. migration execution plane foundation: add a separate migration runner or job model so migrations execute outside runtime serving traffic
47. managed migration observability and reconciliation: add run-step history, checkpoint visibility, failure buckets, replay/retry controls, and operator reconciliation workflows

### Track B: Platform assistant as a first-class deployment

48. platform assistant template and bootstrap path: add a dedicated platform-assistant deployment template with curated sources, actions, and safer defaults
49. platform assistant source providers and deployment-scoped retrieval: expose docs, deployment metadata, releases, verification, diagnostics, and audit summaries as assistant sources
50. deployment-scoped assistant UI and read-only action layer: add a real assistant page and deployment side panel with citations, scoped answers, and bounded read-only platform actions
51. approval-aware platform assistant actions: add preview, confirmation, approval, audit, and permission-aware execution for sensitive administrative assistant actions

### Track C: Remote business logic and enterprise extensibility

52. confirmation policy ladder: formalize no-interception, config-driven interception, and remote-policy-service modes as platform-managed choices
53. remote confirmation policy service contract and deployment model: add service contract, security model, diagnostics, and deployment linkage for customer-specific confirmation logic
54. policy-service observability and failure semantics: make external policy-service health, latency, decision traces, and fail-safe behavior visible and governable

### Track D: Multi-cloud target profiles and provider expansion

55. deployment target profile model: refactor from global provisioning mode to per-deployment target profiles with one platform default target
56. provider-neutral deployment service contract: compile runtime, REST connector, artifacts, env, and health expectations into a provider-neutral deployment request
57. OCI image source model and release contract: separate app config from infrastructure target details and support provider-neutral image-based deployment
58. first optional non-Railway provider: add AWS App Runner as the first managed alternative target
59. second managed container target: add Azure Container Apps after the target-profile and provider-neutral refactors are stable

---

## 3) Wave 4 Scope Notes

Wave 4 should explicitly include:

- full migration control-plane modeling
- a realistic migration engine built around generic source patterns such as files, REST APIs, and SQL
- managed migration execution outside serving runtime
- a platform-managed assistant deployment for dogfooding and operator productivity
- externalized enterprise business-logic extension for confirmation flows
- target-profile-based multi-cloud expansion

Wave 4 should explicitly not attempt to finish:

- connectors for every source system
- generic plugin loading of arbitrary customer Java into runtime
- GCP, ECS, EKS, or AKS in the first multi-cloud increment
- broad customer-facing white-label assistant surfaces
- vertical-specific Shopify-first product branching
- generic eval-platform ambitions that pull the product away from deployment control

Those remain valid future directions, but they should not be mixed into the first enterprise expansion wave.

---

## 4) Why This Wave Matters

Wave 4 is the point where the product can move from:

- strong internal deployment operations

to:

- stronger enterprise onboarding
- stronger in-product operator guidance
- stronger customer-specific extensibility
- stronger infrastructure choice

Without this wave:

- deployment operations are strong, but customer onboarding still depends too much on manual migration work
- the product still needs a separate Wave 3.5 pass to make managed vector storage easy in the deployment request path
- the platform can manage deployments, but it does not yet fully use its own model to operate a platform assistant
- advanced business-specific confirmation behavior still forces custom runtime coupling
- provider reach remains mostly Railway-first

With this wave complete:

- the platform becomes much stronger for implementation teams onboarding real customers
- migration becomes productized without requiring a connector for every source system
- the product proves itself by running its own assistant inside the same deployment model
- enterprise customization becomes safer through external policy-service seams
- infrastructure choice starts to become productized instead of hardcoded

---

## 5) Why This Is The Right Wave 4 Shape

This wave is intentionally ordered to avoid premature cloud breadth.

### 5.1 Managed vector DB comes before Wave 4

Managed vector DB should happen before Wave 4 because:

- it improves the core deployment request flow directly
- it removes infrastructure friction before migration and assistant features build on top
- it is a deployment-product enhancement, not a broader expansion track

### 5.2 Migration comes first inside Wave 4

Migration should come before the assistant and multi-cloud expansion because:

- it is the most direct bridge from platform operations to customer activation
- it helps customers get real data into a deployment faster
- it creates a stronger onboarding and implementation wedge
- it should be implemented through a generic engine with a few source patterns, not by committing the platform to endless per-system connector work

### 5.3 Platform assistant comes next

The platform assistant should follow migration because:

- it dogfoods the deployment model
- it helps operators understand increasingly complex deployment state
- it improves platform usability without changing the product category

### 5.4 Remote policy service follows after that

Remote policy service should follow the assistant because:

- it is valuable, but more scenario-specific
- it benefits from a stronger action, approval, and diagnostics model already present in the platform
- it is better treated as advanced enterprise extensibility than as a core onboarding primitive

### 5.5 Multi-cloud comes last in this wave

Multi-cloud should be the last track in the wave because:

- it has high architectural leverage, but it is easy to over-expand too early
- it depends on getting target profiles and provider-neutral contracts right first
- it should be driven by product clarity, not by raw provider count

---

## 6) Completion Criteria

Wave 4 is complete when:

- managed vector DB already exists as a completed Wave 3.5 capability
- deployments can own migration plans, connection configs, runs, and reconciliation history
- migrations execute through a dedicated execution plane instead of the serving runtime
- the platform assistant is a real platform-managed deployment with scoped retrieval and bounded actions
- approval-aware assistant actions are authorization-safe and auditable
- confirmation interception can call a remote policy service using a productized contract and deployment model
- deployments can target provider-neutral target profiles instead of one global provisioning mode
- at least one non-Railway provider target is usable through the same deployment control plane abstractions
- backend tests, frontend build, and provider-specific verification paths exist for each completed track

---

## 7) Recommended Starting Item

The first item to build in Wave 4 should be:

- **43. migration domain model foundation**

This is the best first item because it creates the platform-side data model that later migration UI, execution, observability, and assistant support can all build on.

It includes:

- deployment-linked migration entities
- migration templates and plans
- source connections
- run and checkpoint records
- error record scaffolding

It should assume a narrow first source shape:

- files
- generic REST APIs
- SQL sources

It does not yet include:

- real migration runner execution
- broad source connector implementations
- dry run UI
- replay/reconciliation workflows

Those follow immediately after the domain model.

---

## 8) Immediate Follow-up After The First Item

After item 43 is complete, the next items should be:

1. source connection and secret model
2. mapping and dry-run workspace
3. separate migration execution plane

This keeps Wave 4 anchored in:

- customer onboarding value
- platform-managed execution
- enterprise-safe operational boundaries

---

## 9) Sequence Notes

- this wave aligns with the existing future-work documents:
  - `MANAGED_VECTOR_DATABASE_DEPLOYMENT_PLAN.md`
  - `DATA_MIGRATION_PLATFORM_PLAN.md`
  - `PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md`
  - `REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md`
  - `MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md`
- it also aligns with the prioritization logic in `IMPLEMENTATION_PRIORITIZATION_ROADMAP.md`
- it preserves the enterprise AI deployment control plane position described in `GO_TO_MARKET_POSITIONING_AND_GAP_ANALYSIS.md`
- it intentionally keeps runtime-quality expansion, vertical-specific branching, and broader cloud breadth outside the first pass of Wave 4

---

## 10) Execution Progress

Completed on this branch:

- none

Next in sequence:

- 43. migration domain model foundation
