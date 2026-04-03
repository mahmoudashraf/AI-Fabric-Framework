# Platform Plan Coverage Checklist

Status: planning checkpoint document (2026-04-03)

This document consolidates the current future-work plans into one status view so the roadmap can distinguish between:

- work already completed on this branch
- plans that have partial foundations already delivered
- plans that are still missing and should drive the next execution wave
- plans that remain strategic or intentionally later

---

## 1) Executive Summary

Current execution status on this branch:

- [x] Wave 1 is complete
- [x] Wave 2 is complete
- [x] Wave 3 is complete
- [x] Wave 3.5 is complete
- [x] Wave 4 Track A is complete on branch
- [ ] Wave 4 Track B and later tracks remain open

High-level interpretation:

- the platform control plane, governance, deployment operations, diagnostics, and managed vector database foundation are now materially in place
- several earlier future-work plans were already partially absorbed by Waves 1 to 3
- the largest missing product areas are now:
  - onboarding vectorization control plane
  - platform assistant deployment
  - deployment-scoped provider secret overrides
  - deployment target profiles and multi-cloud expansion
- several other plans should remain later or optional rather than becoming part of the first Wave 4 pass

---

## 2) Execution Wave Checklist

| Execution doc | Current status | What is covered now | What remains |
| --- | --- | --- | --- |
| `PLATFORM_EXECUTION_SEQUENCE_AND_FIRST_WAVE_PLAN.md` | Complete on branch | Unified deployment workspace foundation, approvals, user administration, prompt foundations, POC and test-data foundations, deployment views, activity timeline, overview workspace | Nothing from this execution doc |
| `PLATFORM_EXECUTION_SEQUENCE_WAVE2_PLAN.md` | Complete on branch | Prompt comparison and preview clarity, POC migration intake and guardrails, assistant readiness staging | Nothing from this execution doc |
| `PLATFORM_EXECUTION_SEQUENCE_WAVE3_PLAN.md` | Complete on branch | Apply clarity, release impact and diff, verification gate, unified per-service config, secret and config separation, governance, diagnostics, remediation, production readiness | Nothing from this execution doc |
| `PLATFORM_EXECUTION_SEQUENCE_WAVE35_PLAN.md` | Complete on branch | Vector provisioning mode, managed vector resource registry, Qdrant Cloud, Pinecone, Zilliz Cloud for Milvus, Weaviate Cloud targeting, destructive controls, cleanup flows | Nothing from this execution doc |
| `PLATFORM_EXECUTION_SEQUENCE_WAVE4_PLAN.md` | Track A complete on branch | Wave 4 sequencing is now anchored in completed Track A work: `Customer -> Tenant -> Deployment`, tenant binding UI, provider-native shared vector handle resolution, verification, diagnostics, migration compatibility, and customer-boundary enforcement | Track B vectorization-layer execution is next, followed by the platform assistant, provider secret overrides, and target profiles |

---

## 3) Product Plan Coverage Matrix

### 3.1 Plans mostly or fully covered by completed waves

| Plan document | Coverage status | What is already covered | What is still missing or deferred |
| --- | --- | --- | --- |
| `ENTERPRISE_DEPLOYMENT_ADMINISTRATION_PLATFORM_PLAN.md` | Mostly covered | Unified deployment workspace, assignments, approvals, admin shell, diagnostics, remediation, delete and archive flows, access foundations, audit-heavy operator workflows | Optional enterprise refinements can continue, but this no longer needs to drive the next wave |
| `PROMPT_MANAGEMENT_HOT_APPLY_PLAN.md` | Partially covered | Prompt management foundation, baseline comparison, release preview, state clarity, session-scoped hot apply groundwork | Broader hot-apply modes, stronger governance modes, and deeper prompt and runtime resolution behavior remain future work |
| `DEPLOYMENT_TEST_DATA_MIGRATION_AND_POC_CHATBOT_PLAN.md` | Partially covered | POC workspace foundation, test-data import and reset, scenario presets, orchestration trace enrichment, migration intake groundwork | Full embedded POC chatbot UX and richer migration productization remain open |
| `MANAGED_VECTOR_DATABASE_DEPLOYMENT_PLAN.md` | Complete through Wave 3.5 | Managed vector deployment model, platform-managed versus external-existing posture, Qdrant and Pinecone managed provisioning, Milvus via Zilliz Cloud, Weaviate Cloud targeting, lifecycle cleanup, verification paths | Future vendor breadth and self-hosted fallback remain later expansion topics |

### 3.2 Plans that should drive Wave 4

| Plan document | Coverage status | What is already covered | What is still missing |
| --- | --- | --- | --- |
| `MULTI_TENANT_RUNTIME_STRATEGY_AND_MARKET_OPPORTUNITY.md` | Core Track A direction implemented on branch | The branch now has the corrected enterprise scope: `Customer -> Tenant -> Deployment`, provider-native shared vector handles, tenant-scoped lifecycle surfaces, migration compatibility, and customer-boundary enforcement | Future expansion can deepen provider-managed shared-scope provisioning, but this plan no longer blocks Track B |
| `TENANT_SCOPED_SHARED_VECTOR_INFRASTRUCTURE_PLAN.md` | Track A implemented on branch | Customer and tenant model, customer-admin tenant self-service, deployment binding UI, provider-native shared handle contract, runtime env wiring, verification and diagnostics visibility, migration compatibility, cleanup posture, registry reconciliation, and cross-customer boundary enforcement are now in place | Ongoing fit-and-finish can continue, but Track A is no longer the next execution blocker |
| `ONBOARDING_VECTORIZATION_LAYER_PLAN.md` | New active Track B plan | Reframes Track B around onboarding-time indexing into deployment-configured entities and selected/provisioned vector databases, with deployment-scoped ephemeral runners and coarse tracking | Actual implementation of Track B items `56` through `60` |
| `VECTORIZATION_LAYER_CODE_RESIDENCY_AND_INTEGRATION_PLAN.md` | New active Track B companion plan | Establishes the concrete code-residency model for platform vectorization control plane, product-shared integration primitives, product vectorization core, per-deployment runner provisioning, and pull-only runner control flow | Actual implementation of Track B items `56` through `60` |
| `DATA_MIGRATION_PLATFORM_PLAN.md` | Superseded by narrower Track B framing | POC intake and import foundations from Waves 1 and 2 create useful groundwork and the document remains useful as broad background context | The active Track B execution should follow the vectorization-layer plans instead of this broader migration framing |
| `MIGRATION_CONTROL_PLANE_CODE_RESIDENCY_AND_INTEGRATION_PLAN.md` | Superseded by narrower Track B framing | Earlier code-residency thinking remains useful as background context | The active Track B execution should follow the vectorization-layer code-residency plan instead |
| `PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md` | Not started | Assistant readiness and staging groundwork exists, but not the productized assistant deployment | Platform assistant template, scoped sources, dedicated assistant UI, deployment-scoped side panels, bounded actions, approval-aware actions |
| `DEPLOYMENT_SCOPED_PROVIDER_SECRET_OVERRIDES_PLAN.md` | Not started | Current global-secret plus deployment-managed-secret model gives it a base to extend | Global-versus-override precedence, deployment-scoped provider secret references, diagnostics, audit, cleanup, and UI. This should land as a late Wave 4 support track, not as a post-Wave-4 idea |
| `MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md` | Not started | Railway provisioning is mature; provider-neutral verification ideas exist | Target profiles, provider-neutral deployment contract, OCI image source model, AWS and Azure targets |

### 3.3 Plans partially advanced by Wave 3.5 or adjacent work

| Plan document | Coverage status | What is already covered | What is still missing |
| --- | --- | --- | --- |
| `PLATFORM_PROVIDER_AND_VECTOR_MATRIX_EXPANSION_PLAN.md` | Partially covered | Platform now supports stronger managed-vector coverage across Qdrant, Pinecone, Milvus or Zilliz, and Weaviate Cloud targeting | More provider combinations, broader matrix UX, and future provider breadth remain open |
| `VECTOR_DATABASE_MANAGED_AND_CLOUD_SELF_HOSTED_EXPANSION_PLAN.md` | Partially covered on managed side | Managed-service-first direction is now real for supported vendors | Cloud self-hosted fallback remains intentionally deferred |

### 3.4 Plans that should move to Wave 5 or later runtime-focused work

| Plan document | Coverage status | Recommendation |
| --- | --- | --- |
| `CONFIRMATION_INTERCEPTION_PRODUCTIZATION_PLAN.md` | Not started | Move to Wave 5 as the first step of the confirmation-interception ladder |
| `REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md` | Not started | Move to Wave 5 as the advanced extension after config-driven interception exists |
| `RUNTIME_ACTION_GROUNDED_ANSWERING_AND_DEEP_KNOWLEDGE_NAVIGATION_PLAN.md` | Not started | Keep as later runtime-tuning work after the more platform-heavy Wave 4 completes |

### 3.5 Plans that are valid but should remain later or optional

| Plan document | Coverage status | Recommendation |
| --- | --- | --- |
| `ADVANCED_DEPLOYMENT_CREATE_FLOW_OPTIONAL_PLAN.md` | Not started | Keep optional and secondary to preset-driven deployment creation |
| `SHOPIFY_VERTICAL_STRATEGY_AND_PRIORITY_PLAN.md` | Not started | Use as a pressure-test for roadmap choices, not as the main organizing wave |
| `FRAMEWORK_RELEASE_STRATEGY_AND_OPEN_CORE_PLAN.md` | Not started | Keep separate from Wave 4 execution; this is a packaging and business-model decision, not the next product-delivery wave |

### 3.6 Strategy and positioning documents

| Plan document | Coverage status | Role |
| --- | --- | --- |
| `GO_TO_MARKET_POSITIONING_AND_GAP_ANALYSIS.md` | Strategy anchor | Use as the filter for prioritization and messaging decisions |
| `MARKET_POSITION_EVALUATION.md` | Evaluation document | Use as an external reality check, not as a build checklist |
| `AI_ASSISTANT_PRODUCT_NORTH_STAR_AND_SCOPE.md` | Strategy anchor | Use to keep assistant-related work aligned with the product identity |
| `IMPLEMENTATION_PRIORITIZATION_ROADMAP.md` | Partially stale but still useful | Early priorities were largely delivered by Waves 1 to 3.5; keep it as rationale, not as the live execution checklist |

---

## 4) What Is Covered Now

The branch now has strong coverage in these areas:

- deployment-centric control plane foundation
- unified deployment workspace
- approvals, guardrails, and access administration
- stable `Customer -> Tenant -> Deployment` identity and binding model
- provider-native tenant-scoped shared vector handle resolution
- tenant-scoped verification, diagnostics, cleanup posture, and customer-boundary enforcement
- release management, diffing, verification gating, and diagnostics
- secret and config separation and governance visibility
- operator remediation and readiness workflows
- managed vector database request path
- managed provider support for:
  - Qdrant Cloud
  - Pinecone
  - Zilliz Cloud for Milvus
  - Weaviate Cloud targeting
- platform-hosted and GitHub-based verification workflows
- canonical rollout support and verified deployment stacks

---

## 5) What Is Missing

The biggest missing product capabilities are now:

- onboarding vectorization control plane and execution model
- a productized platform assistant deployment
- deployment-scoped provider secret overrides
- provider-neutral deployment target profiles and multi-cloud expansion

Secondary but valid future gaps:

- confirmation-interception ladder
- remote policy service
- runtime answer-quality tuning
- optional advanced deployment create flow
- broader provider and vector matrix growth
- self-hosted cloud fallback for vector services
- open-core and framework release strategy

---

## 6) Recommended Wave 4 Inputs

Wave 4 should be built from the missing product capabilities, not from the already completed control-plane work.

Recommended Wave 4 inputs:

1. `MULTI_TENANT_RUNTIME_STRATEGY_AND_MARKET_OPPORTUNITY.md` with scope corrected to the `Customer -> Tenant -> Deployment` model, tenant-scoped shared infrastructure, and provider-native isolation
2. `TENANT_SCOPED_SHARED_VECTOR_INFRASTRUCTURE_PLAN.md`
3. `ONBOARDING_VECTORIZATION_LAYER_PLAN.md`
4. `VECTORIZATION_LAYER_CODE_RESIDENCY_AND_INTEGRATION_PLAN.md`
5. `PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md`
6. `DEPLOYMENT_SCOPED_PROVIDER_SECRET_OVERRIDES_PLAN.md` as a late support track
7. `MULTI_CLOUD_PROVISIONING_EXPANSION_PLAN.md`

Wave 4 should explicitly treat these as already completed prerequisites:

- `PLATFORM_EXECUTION_SEQUENCE_AND_FIRST_WAVE_PLAN.md`
- `PLATFORM_EXECUTION_SEQUENCE_WAVE2_PLAN.md`
- `PLATFORM_EXECUTION_SEQUENCE_WAVE3_PLAN.md`
- `PLATFORM_EXECUTION_SEQUENCE_WAVE35_PLAN.md`
- `MANAGED_VECTOR_DATABASE_DEPLOYMENT_PLAN.md`

Wave 4 should explicitly treat these as later or optional:

- `RUNTIME_ACTION_GROUNDED_ANSWERING_AND_DEEP_KNOWLEDGE_NAVIGATION_PLAN.md`
- `CONFIRMATION_INTERCEPTION_PRODUCTIZATION_PLAN.md`
- `REMOTE_CONFIRMATION_POLICY_SERVICE_PLAN.md`
- `ADVANCED_DEPLOYMENT_CREATE_FLOW_OPTIONAL_PLAN.md`
- `VECTOR_DATABASE_MANAGED_AND_CLOUD_SELF_HOSTED_EXPANSION_PLAN.md`
- `FRAMEWORK_RELEASE_STRATEGY_AND_OPEN_CORE_PLAN.md`
- `SHOPIFY_VERTICAL_STRATEGY_AND_PRIORITY_PLAN.md`

---

## 7) Recommended Next Action

The next execution action should be to start item `56` from the updated `PLATFORM_EXECUTION_SEQUENCE_WAVE4_PLAN.md`.

That updated Wave 4 plan now:

- starts Wave 4 after completed Wave 3.5 work
- records Track A as completed work on this branch
- renumbers Wave 4 items so they no longer collide with `43` to `52`
- positions vectorization immediately after the tenant and shared-resource foundation
- keeps the platform assistant inside Wave 4 as a platform-facing productization track
- keeps deployment-scoped provider secret overrides in late Wave 4, before broader provider-neutral target expansion
- moves confirmation and remote policy to Wave 5 and runtime answer quality to later runtime tuning
- keeps multi-cloud late in the wave
- keeps optional and strategic plans outside the main Wave 4 checklist
