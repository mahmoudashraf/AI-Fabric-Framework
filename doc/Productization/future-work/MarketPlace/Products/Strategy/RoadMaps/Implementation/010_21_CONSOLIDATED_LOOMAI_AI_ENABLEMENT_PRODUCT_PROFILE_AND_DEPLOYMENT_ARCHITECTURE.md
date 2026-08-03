# 010.21 Consolidated LoomAI Behavior Product, Product Profile, And Deployment Architecture

Status: canonical consolidated plan created on 2026-08-01 and corrected to the behavior-first product model on 2026-08-03. Existing framework and hosted capabilities are recorded as evidence; new generic Product Profile contracts, authoring flows, and behavior templates remain planned until implemented and verified.

This document consolidates and judges:

- [010.20 AI Fabric 0.5.2-Backed LoomAI Product Portfolio And Productization Plan](010_20_AI_FABRIC_0_5_2_BACKED_LOOMAI_PRODUCT_PORTFOLIO_AND_PRODUCTIZATION_PLAN.md)
- public framework proposal `docs/planning/0018-loomai-ai-enablement-product-and-deployment-template-proposal.md` in the sibling `Loom-AI-Labs/ai-fabric-framework` repository

Where those sources conflict, this document is the LoomAI product and Platform source of truth. The public framework proposal remains useful framework-side input; it does not define LoomAI control-plane state, product priority, or current product maturity.

Related LoomAI sources:

- [010.16 Practical Dev, Staging, And Production Deployment Model](010_16_PRACTICAL_DEV_STAGING_PRODUCTION_DEPLOYMENT_MODEL.md)
- [010.17 Read Action Grounding And LLM Facts Plan](010_17_GROUNDING_ELIGIBLE_READ_ACTION_POST_ACTION_GENERATION_AND_LLM_FACTS_PLAN.md)
- [010.18 Shopify Companion Production Release And Listing Readiness](010_18_SHOPIFY_COMPANION_PRODUCTION_RELEASE_AND_APP_LISTING_READINESS_PLAN.md)
- [009.1 Marketplace Config-Driven MCP Capability Architecture](009_1_MARKETPLACE_CONFIG_DRIVEN_MCP_CAPABILITY_ARCHITECTURE.md)
- [009.2 MCP Execution Gateway Extraction Plan](009_2_MCP_EXECUTION_GATEWAY_EXTRACTION_PLAN.md)
- [AI Fabric Platform Product Philosophy](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_PLATFORM_PRODUCT_PHILOSOPHY.md)
- [AI Fabric Framework Philosophy](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md)
- [LoomAI Provider Capabilities User Guide](../../../../../../../../Final_Documentation/User_Guides/LOOMAI_PROVIDER_CAPABILITIES_USER_GUIDE.md)

## 1. Purpose

Define one coherent LoomAI model for turning current AI Fabric functionality into managed, customer-facing products.

The consolidated model must:

1. use shipped AI Fabric contracts only where they actually exist;
2. use LoomAI's current Platform, Marketplace, V04 deployment, managed-service, assignment, and verification concepts instead of creating a parallel control plane;
3. distinguish existing proof, reusable productization work, and future proposals;
4. preserve provider neutrality while supporting Claude, OpenAI, Gemini, Azure, Cohere, and other verified providers through governed profiles;
5. treat outbound MCP execution and future inbound MCP authoring as different products and trust boundaries;
6. keep customer application identity, authorization, validation, transactions, and system-of-record authority intact; and
7. define LoomAI products by reusable activation and coordination behavior rather than by one vertical task;
8. treat commerce, support, operations, knowledge, churn, and similar domains as solution packs over those behaviors; and
9. sequence product delivery around real customer value rather than the order in which framework primitives were built.

## 2. Executive Decision

LoomAI should introduce one generic, versioned **Product Profile** above its existing Platform concepts. Every Product Profile selects exactly one market-facing **Behavior Product Template** and composes only approved solution, capability, channel, infrastructure, and verification bindings around it.

The three primary LoomAI behavior products are:

1. **Conversational Assistant**: reactive, user-query-driven assistance using one bounded orchestration path per turn.
2. **Agentic Specialist Team**: interactive or application-invoked coordination of exact-version specialists through a bounded conversation manager or fixed read-only plan.
3. **Smart Brain**: proactive read-only analysis initiated by a trusted application event, scheduler, API, or durable job queue without fabricating a user chat turn.

`Governed Resolver` and `Human Review` are execution extensions, not separate vertical products. They may be enabled only for behavior templates and activation sources whose released runtime contracts support them. In AI Fabric 0.5.2, event/scheduled durable work is read-only; do not attach automatic writes to Smart Brain deployments.

The canonical composition is:

```text
Product Profile Version
  + one Behavior Product Template
  + optional Solution Pack
  + allowed Execution Extensions
  + commercial package/tier
  + curated runtime behavior
  + Marketplace capability packs
  + inference profile
  + vector profile
  + channel bindings
  + deployment topology template
  + deployment target profile
  + verification pack
  = immutable V04 Deployment Version
  -> deterministic Release
  -> verified Consumer Assignment
```

This is intentionally not an arbitrary overlay system. The Product Profile declares approved choices and compatibility rules. Platform resolves one valid combination server-side and compiles it into the existing deployment lifecycle.

Core decisions:

- `Behavior Product Template` is the market-facing product contract. It defines activation source, coordination model, interaction posture, allowed execution extensions, required durable state, and behavioral verification.
- `Product Profile` is the versioned deployable composition of exactly one Behavior Product Template plus an optional solution pack, capability packs, channels, package, provider/vector choices, topology, target, and verification.
- `Solution Pack` supplies bounded domain semantics, adapters, schemas, prompts, fixtures, and UI result mappings. Shopify, ProdUS, support, incident, behavior/churn, and similar domains are solution packs or customer deployments, not new behavior products.
- RAG, managed vectorization, external retrieval, MCP, relationship query, behavior signals, PII, structured output, and document operations are capability packs or managed product services. They do not become products merely because they can be composed into a deployment.
- Docked composer, Max Mode, inline assistance, query-once, backend API, event adapter, scheduler adapter, and internal client are channel bindings.
- Existing Platform `DeploymentTemplateSummary` remains an infrastructure/provider/vector topology preset. It is not renamed into a product template.
- Existing `DeploymentTargetProfile` remains environment, region, provider, network, resource, and placement policy.
- Marketplace `TEMPLATE`, `DATA`, `ACTION`, and `INFERENCE_PROFILE` plugins remain capability packaging and compilation inputs.
- The existing Shopify package profile is the first vertical proof of package/runtime/vector/verification mapping. It should inform a generic product-profile model, not become the generic model itself.
- Provider choice is represented by a governed inference profile and deployment provider configuration, not a free-form provider overlay supplied by a browser or model.
- A channel binding selects an approved invocation and UI surface. It never grants identity, specialist, action, tenant, deployment, or provider authority.
- V04 compiled versions, releases, verification, promotion, assignment, export/import, and managed-service reconciliation remain the only operational path.
- Shopify Companion and ProdUS remain hosted reference solution deployments of the Conversational Assistant behavior.
- `deployment-knowledge-specialist@1` is an important internal hosted reference and security canary. It is not the first new customer product because it is already implemented and because operator knowledge is not the strongest general market wedge.
- The first reusable Product Profile must prove the Conversational Assistant behavior independently of Shopify. The next two behavior templates are Agentic Specialist Team and Smart Brain.
- Grounded Knowledge is a reusable capability/solution composition for any behavior that needs approved evidence. Document Intelligence Workbench is a managed knowledge-operations application feeding those behaviors, not a fourth behavior product.
- AI Fabric supports bounded specialist systems, not unrestricted autonomous multi-agent products.

## 3. Judgment Of The Framework Proposal

### 3.1 Adopt directly

| Proposal idea | Judgment | Consolidated LoomAI use |
| --- | --- | --- |
| LoomAI is authoring, deployment, operations, and lifecycle; AI Fabric is runtime primitives | Correct | Preserve as the primary framework/product boundary |
| Product capability and deployment topology are different concerns | Correct | Product Profile remains separate from Deployment Template and Target Profile |
| Exact specialist versions and typed schemas | Correct | Package immutable manifests and schemas in compiled deployment versions |
| Provider selection must not change authority | Correct | Inference profiles may change models, budgets, and endpoints but never scopes/actions/vector authority |
| Channels must not supply trusted identity or authority | Correct | Channel bindings are invocation/UI adapters only |
| Governed writes remain proposals until confirmation/review | Correct | Use application authorization, durable receipt, execution, and reconciliation |
| Durable reads, receipts, review, and restart proof | Correct | Include only when a product has a concrete durability requirement |
| Security and verification are part of every template | Correct | Product Profile requires a verification pack and release gates |
| No unrestricted agents, arbitrary tools, model-owned auth, or mutable specialist versions | Correct | Keep as explicit product boundaries |
| Claude may be a provider without becoming the security model | Correct | Provider-neutral inference profiles include Anthropic where verified |

### 3.2 Adopt with LoomAI adaptation

| Proposal idea | Required adaptation |
| --- | --- |
| `Product Blueprint` | Use `Product Profile Version`, mapped to official LoomAI capability codes, packages, surfaces, knowledge, specialists, actions, and verification |
| `Deployment Profile` | Split into existing LoomAI concepts: runtime profile, vector profile, deployment topology template, target profile, storage requirements, and managed product services |
| `Provider Overlay` | Implement through Platform-managed inference profiles, provider configuration, secret references, and provider verification; do not create an ungoverned overlay document |
| `Channel Overlay` | Implement as a bounded Channel Binding to backend API, docked composer, Max Mode, inline assistant, query-once, trusted event adapter, internal Java client, or a future remote MCP channel |
| Template package directory | Use as an export/review representation of a Product Profile Version; Platform and Marketplace remain canonical sources, and arbitrary scripts/SQL are prohibited |
| Conversational authoring | Keep as a future client experience over deterministic Platform validation and draft APIs; the assistant may propose but never grant, approve, or silently deploy |
| Template catalogue | Define only three top-level Behavior Product Templates; represent verticals as solution packs and technical functions as capabilities/extensions/channels inside Product Profiles |
| MCP as connectivity | Treat MCP as one governed connectivity plane, not the only integration standard and not the authority model |

### 3.3 Correct factual or maturity mismatches

| Framework proposal statement | Current LoomAI reality |
| --- | --- |
| Outbound MCP is only partially available | LoomAI already has a standalone managed MCP Execution Gateway, Marketplace discovery/import, schema hashes and drift checks, config-driven `mcp-tool` compilation, generic non-Shopify execution, Shopify host delegation, managed lifecycle, and release-gate coverage |
| The deployment-knowledge specialist should be P0 implementation proof | Its 0.5.2 hosted, tenant/deployment-isolated proof is already complete. Keep it as a regression fixture and internal product reference |
| Start by defining another template package shape | LoomAI already has Marketplace plugins, deployment templates, target profiles, curated modules, V04 versions, releases, export/import, and verification. Add Product Profile above them instead of replacing them |
| Claude Code authoring is close to an end-to-end capability | Claude Code can consume MCP, but LoomAI does not yet expose an inbound authoring/deployment MCP server. That remains a separate future product track |
| Direct AI Fabric Spring AI MCP execution is suitable after general hardening | The current public executor does not fail closed when a declared `serverRef` has no matching client; it can fall back to another client exposing the tool name. LoomAI's managed Gateway is the current production boundary. Direct executor use requires a framework correction and release |
| All proposed templates are product catalogue peers | Only a distinct activation and coordination behavior defines a top-level LoomAI product. Domain proposals are solution packs; RAG, MCP, documents, UI, privacy, and similar functions are capabilities, services, extensions, or channels |

### 3.4 Defer or reject

- Do not prioritize an inbound LoomAI authoring/deployment MCP server ahead of the Behavior Product Template catalogue, generic Product Profile compiler, and first reusable Conversational Assistant profile.
- Do not make Claude Code a required channel for any product.
- Do not call Claude, MCP, or any provider the standard security boundary.
- Do not expose generic `deploy`, `execute_any_action`, `invoke_any_specialist`, arbitrary URL, raw SQL, shell, or unrestricted provider tools.
- Do not allow combinatorial overlay selection in merchant, partner, or customer UI.
- Do not package arbitrary database migrations or executable scripts inside customer-authored profiles.
- Do not create a product for personalized UI planning until a real customer workflow and allowlisted component catalogue justify it.
- Do not market fraud, compliance, risk, refund, or other high-impact decisions as automatic model authority.

## 4. Current Capability Reality

Maturity labels used throughout this document:

- `AI_FABRIC_AVAILABLE`: shipped public 0.5.2 contract with code/test evidence.
- `LOOMAI_HOSTED_PROVEN`: deployed LoomAI path has passed hosted verification.
- `LOOMAI_IMPLEMENTED`: code and local/live evidence exist, but reusable product packaging or broad production proof may still be incomplete.
- `PRODUCTIZATION_REQUIRED`: supporting pieces exist, but the managed customer product is not complete.
- `FUTURE`: proposal only; no current end-to-end LoomAI product contract.
- `BLOCKED`: required contract or gate is absent or failing.

### 4.1 AI Fabric 0.5.2 foundation

| Capability | Public module/contract | Current limit | Maturity |
| --- | --- | --- | --- |
| Orchestration and generation | `ai-fabric-core`, provider modules | Host still supplies identity, policy, product prompt/config | `AI_FABRIC_AVAILABLE` |
| Embeddings and vector search | embedding/vector SPIs and providers | Product must select and verify provider dimensions, storage, and isolation | `AI_FABRIC_AVAILABLE` |
| Indexing lifecycle | `ai-fabric-indexing`, `@AIProcess`, durable projected work, `IndexingWorkQuery` | Product owns private status API, access policy, polling, and operations | `AI_FABRIC_AVAILABLE` |
| Push Data Sync | `ai-fabric-data-sync` upsert/delete/batch | Access and projection must fail closed; accepted work is not automatically complete | `AI_FABRIC_AVAILABLE` |
| RAG | `ai-fabric-rag` | Product owns prompts, quality, source policy, and answer UX | `AI_FABRIC_AVAILABLE` |
| External document retrieval | `ai-fabric-retrieval-connector` | Documents-only evidence; generated external answers are not trusted evidence | `AI_FABRIC_AVAILABLE` |
| Chat sessions | `ai-fabric-chat-session` | Product owns authenticated conversation API, retention, and cross-owner policy | `AI_FABRIC_AVAILABLE` |
| Actions and confirmations | core actions plus connector/registry modules | Final authorization and side effects remain application-owned | `AI_FABRIC_AVAILABLE` |
| Bounded specialists and conversation manager | `ai-fabric-execution` exact versions, schemas, trusted context, manager, plans, waits, delegation/handoff | Input waits and manager/plan state are process-local; no dynamic graph or hot-reload registry | `AI_FABRIC_AVAILABLE` |
| Durable proactive read execution | `ai-fabric-execution` JDBC read jobs for trusted application/event/scheduled sources | Exact read-only specialist per durable job; at-least-once provider execution; no durable multi-step plan or event/scheduled write | `AI_FABRIC_AVAILABLE` |
| Governed receipts and human review | `ai-fabric-execution` proposal receipts, review tasks/delivery, replay, reconciliation contracts | Application owns reviewer authorization, transaction, system-of-record reconciliation, and migrations | `AI_FABRIC_AVAILABLE` |
| PII | `ai-fabric-pii` | Product defines legal/policy posture, encryption, retention, and claims | `AI_FABRIC_AVAILABLE` |
| Governance | `ai-fabric-governance` | Does not replace product-specific authorization or compliance review | `AI_FABRIC_AVAILABLE` |
| Relationship query | `ai-fabric-relationship-query` | Requires approved domain schema and object authorization | `AI_FABRIC_AVAILABLE` |
| Behavior insights | `ai-fabric-behavior` | Signals are inference, not fact or automatic action authority | `AI_FABRIC_AVAILABLE` |
| Migration/backfill | `ai-fabric-migration-core` | Product owns source truth, checkpoints, and operator workflow | `AI_FABRIC_AVAILABLE` |
| Spring AI-managed MCP client execution | `ai-fabric-actions-connector` | Strict declared-server resolution requires a public framework correction before direct production use | `BLOCKED` for direct LoomAI trust-boundary use |
| Anthropic generation | Spring AI provider integration | Anthropic is generation-only in the current AI Fabric provider path; RAG needs a separate embedding provider | `AI_FABRIC_AVAILABLE` |

### 4.2 Current LoomAI product and Platform foundation

| Capability | Current LoomAI implementation | Maturity |
| --- | --- | --- |
| V04 deployment lifecycle | Draft, validation, publish, immutable version, release, apply, post-apply verification, assignment, rollback evidence | `LOOMAI_HOSTED_PROVEN` |
| Deployment topology templates | Provider/vector presets such as OpenAI/Lucene, OpenAI/Milvus, Anthropic/Lucene, and other verified combinations | `LOOMAI_IMPLEMENTED` |
| Curated behavior | Curated modules, prompt presets, runtime `AI_CURATED_PACK`, shell config | `LOOMAI_HOSTED_PROVEN` |
| Marketplace capabilities | Template, data, action, and inference-profile plugins compiled into deployment artifacts | `LOOMAI_HOSTED_PROVEN` |
| Vertical package mapping | Shopify package/tier/runtime/vector/inference/template/verification profile entity and server-owned option catalogue | `LOOMAI_IMPLEMENTED`, Shopify-specific |
| Target placement | Coolify/Railway target profiles, environment, region, credentials, network, resources, product-service permissions | `LOOMAI_HOSTED_PROVEN` |
| Managed vectors | Platform provisioning, vectorization runner, status/revision reconciliation, managed Milvus/Zilliz and other supported providers | `LOOMAI_HOSTED_PROVEN` |
| Consumer assignment | Scoped backend-only assignment discovery and private runtime assertions | `LOOMAI_HOSTED_PROVEN` |
| Export/import and promotion | Config-only and sealed exports, import preview, target-scoped rewrite, production promotion practice | `LOOMAI_HOSTED_PROVEN` |
| MCP execution plane | Managed Gateway, Marketplace discovery/import, exact config, schema drift checks, auth profiles, normalized evidence | `LOOMAI_HOSTED_PROVEN` for infrastructure; product-by-product tools still require proof |
| Managed product services | Reconcile, health, logs/history, restart, scale, secret rotation, force recreate, decommission | `LOOMAI_HOSTED_PROVEN` |
| Product verification | Canonical suites, hosted product checks, release gate, source identity, optional/non-blocking legacy checks | `LOOMAI_HOSTED_PROVEN` |
| Conversational behavior | Shopify and ProdUS backend-mediated query/chat deployments, grounding, assignment, and verification | `LOOMAI_HOSTED_PROVEN` |
| Deployment knowledge specialist | Exact `deployment-knowledge-specialist@1`, trusted scope, hosted two-tenant/two-deployment canaries | `LOOMAI_HOSTED_PROVEN` |
| Agentic Specialist Team template | Exact specialist runtime is hosted; generic manager, multi-specialist plan, packaging, and product verification are not yet a reusable profile | `PRODUCTIZATION_REQUIRED` |
| Smart Brain template | Framework contracts and executable demos exist; managed triggers, durable worker operations, output routing, and Platform profile proof are not yet complete | `PRODUCTIZATION_REQUIRED` |
| Embedded UI | Max widget, docked composer concept, inline/result-card patterns, backend-mediated chat | `PRODUCTIZATION_REQUIRED` as a stable reusable package |
| Behavior Product Template catalogue | No generic Platform source-of-truth contract for the three behavior products yet | `FUTURE` until implemented |
| Generic Product Profile | No generic Platform source-of-truth contract above existing components yet | `FUTURE` until implemented |
| Inbound authoring MCP server | Existing Gateway is outbound execution, not a developer-facing Platform authoring server | `FUTURE` |

### 4.3 Production evidence baseline

- AI Fabric release: `ai-fabric-framework-v0.5.2`, immutable Maven Central artifacts.
- Production Platform source: `Platform-V11` commit `de7bd045` for the completed migration proof.
- ProdUS runtime: deployment `dep-f6abfa06`, V04 version `ver-aaec416e`, release `rel-3b4a8338`, verification passed.
- Hosted specialist isolation: two tenants and two deployments passed; missing assertion/scope/tenant failed closed.
- MCP Execution Gateway: production target scope, health `READY`, drift `NO_DRIFT`, HTTP `200` probe.
- Full production Platform release gate: `vsr-e18452e5`, all 13 blocking stages passed at execution time.

These facts prove the foundation. They do not make every product profile in this document generally available.

## 5. Canonical LoomAI Vocabulary And Sources Of Truth

| Concept | Meaning | Canonical owner/source |
| --- | --- | --- |
| Behavior Product Template | Market-facing reusable behavior: Conversational Assistant, Agentic Specialist Team, or Smart Brain | LoomAI behavior-template catalogue and immutable versions |
| Solution Pack | Domain-specific semantics, adapters, schemas, prompts, fixtures, and UI mappings such as Shopify commerce, ProdUS project knowledge, support resolution, or churn signals | Marketplace plus product-owned versioned solution artifacts |
| Execution Extension | Optional governed behavior such as Resolver or Human Review, enabled only where the selected activation/runtime contract supports it | Product Profile version plus AI Fabric/application contracts |
| Product Profile | Versioned deployment composition selecting exactly one Behavior Product Template plus optional solution, capability, extension, channel, provider, topology, quality, and package bindings | New generic Platform Product Profile catalogue |
| Product Profile Version | Immutable semantic definition and compiled-input hash | Platform Product Profile version store |
| Package/Tier | Commercial entitlement, quotas, support, enabled surfaces/actions | Product package catalogue; existing Shopify profile is first vertical instance |
| Capability Pack | Reusable RAG, data, vectorization, MCP, action, privacy, behavior-signal, relationship, specialist, schema, prompt, or result-rendering contribution | Marketplace plugin/version plus governed product artifacts |
| Curated Runtime Behavior | Modes, prompt preset, curated pack, shell behavior | Existing curated module catalogue and compiled deployment config |
| Specialist | Exact `name@version` bounded AI execution contract | Immutable manifest packaged in private runtime deployment artifact/config |
| Runtime Profile | Managed behavior/cost/limits posture, not infrastructure placement | Product/Profile catalogue mapped server-side |
| Inference Profile | Provider, model, endpoint, token, timeout, cost, fallback, and verification posture | Marketplace `INFERENCE_PROFILE` and Platform provider config |
| Vector Profile | Strategy, provider, dimensions, storage posture, namespace/isolation, reindex policy | Product/Profile catalogue plus Platform vector plan |
| Deployment Template | Runtime/connector/provider/vector topology preset | Existing Platform deployment template catalogue |
| Target Profile | Hosting provider, environment, region, credentials, network, resources, placement | Existing `DeploymentTargetProfile` |
| Channel Binding | Approved invocation and UI surface | Product Profile version and integration config |
| Verification Pack | Deterministic, provider, security, product quality, UI, and deployment checks | Platform verification catalogue plus product fixtures |
| Managed Product Service | Restartable shared/private service such as MCP Gateway or Shopify Bridge | Platform Product Services |
| V04 Deployment Version | Immutable compiled runtime desired state | Existing Platform deployment compiler/version repository |
| Release | Deterministic application of one deployment version to one target | Existing Platform release lifecycle |
| Consumer Assignment | Backend-only consumer routing to one verified deployment | Existing consumer assignment service |

Source-of-truth rules:

1. A Product Profile selects exactly one Behavior Product Template. Combining activation models creates a new reviewed template version; it is not an unchecked option matrix.
2. A Solution Pack cannot redefine activation source, workflow topology, trusted identity, authority, or execution durability supplied by the Behavior Product Template.
3. A Product Profile never duplicates Marketplace plugin bodies, target credentials, provider secrets, or release state.
4. A package exposes customer-safe choices. It does not expose template IDs, plugin IDs, model IDs, provider endpoints, vector hosts, or secret names unless an operator-only workflow explicitly needs them.
5. Marketplace discovery is draft evidence. Published and installed plugin versions are capability truth.
6. V04 deployment versions are runtime desired-state truth.
7. Release and verification records are operational truth.
8. Consumer assignment is routing truth.
9. The customer application remains domain and transaction truth.

## 6. Consolidated Composition Model

### 6.1 Product-level flow

```text
customer selects Behavior Product Template + optional Solution Pack + package
  -> Platform resolves one Product Profile Version
  -> Platform resolves required/optional Marketplace packs
  -> Platform resolves allowed extensions, curated behavior, inference, vector, channel, topology, and target choices
  -> server-side compatibility validation
  -> compile V04 deployment draft
  -> preview semantic diff and requirements
  -> publish immutable deployment version
  -> apply to target and provision managed dependencies
  -> post-apply verification pack
  -> assignment or promotion
```

No LLM controls these state transitions.

### 6.2 Behavior Product Template contract

Illustrative contract, to be formalized as `loomai-behavior-product-template-v1`:

```yaml
schemaVersion: loomai-behavior-product-template-v1
code: loomai-smart-brain
version: 1.0.0
name: LoomAI Smart Brain
activation:
  allowedSources: [application, event, scheduled]
  publicInteractiveInput: false
coordination:
  durableJob:
    enabled: true
    target: exact-read-only-specialist
    executionGuarantee: at-least-once-read
  fixedPlan:
    enabled: true
    allowedShapes: [sequential-read-only, parallel-all-required-read-only]
    durability: process-local
  dynamicGraphs: false
authority:
  trustedContextRequired: true
  eventMaySelectIdentityOrAuthority: false
  eventOrScheduledWritesAllowed: false
outputs:
  allowedClasses: [typed-result, persisted-insight, notification, application-review-request]
requiredAiFabricModules:
  - ai-fabric-execution
allowedExecutionExtensions: []
baselineVerificationPack: smart-brain-behavior-v1
```

The other templates use the same contract shape:

- Conversational Assistant allows authenticated interactive activation and one bounded ordinary orchestration turn with optional backend-owned session state.
- Agentic Specialist Team allows authenticated interactive/application activation, exact-version manager/workers, bounded input waits, and declared process-local read-only plans.

### 6.3 Product Profile contract

Illustrative contract, to be formalized as `loomai-product-profile-v1`:

```yaml
schemaVersion: loomai-product-profile-v1
code: grounded-company-conversation
version: 1.0.0
name: Grounded Company Conversation
behaviorProductTemplate:
  code: loomai-conversational-assistant
  version: 1.0.0
activationBindings:
  - source: interactive
    channel: backend-api
solutionPack:
  code: grounded-company-knowledge
  version: 1.0.0
executionExtensions: []
loomaiCapabilityCodes:
  - loomai_runtime_orchestration
  - loomai_grounded_rag_answers
  - loomai_managed_vectorization
  - loomai_observability_evaluation
capabilityRequirements:
  requiredMarketplacePacks:
    - knowledge-runtime-base
  optionalMarketplacePacks:
    - external-document-retrieval
runtime:
  curatedModule: knowledge-assistant
  allowedRuntimeProfiles: [balanced, high-quality]
  requiredAiFabricModules:
    - ai-fabric-rag
    - ai-fabric-indexing
    - ai-fabric-data-sync
knowledge:
  requiredVectorSpaces: [approved-company-knowledge]
  supportedSourceModes: [push-sync, external-retrieval]
specialists: []
actions: []
channels:
  required: [backend-api]
  optional: [docked-composer, max-mode, inline-assistant, query-once]
deployment:
  allowedTopologyClasses: [shared-managed-vector, dedicated-managed-vector]
  allowedEnvironmentClasses: [development, staging, production]
security:
  trustedContextRequired: true
  tenantRequired: true
  deploymentRequired: true
verificationPack: grounded-knowledge-v1
```

Rules:

- Exactly one published `behaviorProductTemplate.code@version` is required.
- Activation sources and coordination mode come from that template and cannot be widened by a solution pack, browser, model, or deployment request.
- A Conversational Assistant profile accepts authenticated interactive turns. An Agentic Specialist Team profile uses exact specialists and bounded coordination. A Smart Brain profile accepts only trusted application, event, or scheduled activation declared by its template.
- In AI Fabric 0.5.2, durable queued execution is an exact read-only specialist job. Fixed sequential/parallel plans are process-local. A profile cannot claim durable multi-step plans or event/scheduled writes.
- Only official codes from `loomai-provider-capabilities-v1` are accepted.
- Required AI Fabric modules are compiler requirements, not browser choices.
- A Product Profile can narrow available Marketplace capabilities; it cannot grant capabilities absent from installed/published inventory.
- Specialist manifests, schemas, actions, and vector spaces are exact references.
- A semantic change creates a new Product Profile Version and content hash.
- Platform may offer customer-safe profile labels such as `Balanced` or `High quality`, but resolves actual provider and infrastructure values server-side.

### 6.4 Compiled profile package

The following is an export/review shape, not a second source of truth:

```text
product-profile/
  profile.yml
  behavior/
    template-ref.yml
    activation-policy.yml
    coordination-policy.yml
    durability-policy.yml
  solution/
    pack-ref.yml
  extensions/
    bindings.yml
  capability-bindings/
    marketplace-installs.yml
    curated-runtime.yml
  specialists/
  schemas/
  knowledge/
    vector-spaces.yml
    metadata-policy.yml
    source-requirements.yml
  actions/
    allowed-actions.yml
    risk-policy.yml
  channels/
    bindings.yml
  deployment/
    topology-requirements.yml
    storage-requirements.yml
    target-policy.yml
  verification/
    deterministic-cases.yml
    provider-cases.yml
    security-canaries.yml
    ui-fixtures.yml
  docs/
    product-boundaries.md
```

Do not place secrets, arbitrary scripts, SQL, executable expressions, or customer-authored migrations in this package. Database migrations remain application/private-service build artifacts. The profile can reference a required migration set and verify it, but cannot execute arbitrary migration text.

### 6.5 Compatibility resolution

Platform must validate at least:

- exactly one published Behavior Product Template exists at the declared version;
- activation source, coordination mode, channel, extension, durability, and solution-pack bindings are compatible with that template;
- Smart Brain durable jobs are read-only and fixed plans are not represented as durable multi-step work;
- package/tier permits requested surfaces, actions, provider cost, and support posture;
- curated module and prompt pack exist;
- one compatible inference profile is active;
- embedding dimensions match vector profile and provider;
- required vector spaces exist in V04 entity config or explicit document space config;
- required Marketplace plugins are published, compatible, and installed;
- exact specialists and schemas exist in the runtime build;
- requested actions exist, have access modes, and are allowed by package/deployment;
- selected topology can host required runtime, connector, databases, workers, and managed services;
- target profile allows the required service classes;
- secrets exist by reference;
- verification pack covers every claimed capability.

## 7. LoomAI Product Kernel

### 7.1 Behavior Product Template catalogue

Platform must maintain immutable versions of these three primary behavior products:

| Template code | Customer-visible behavior | Allowed activation | Coordination contract | Current evidence and status |
| --- | --- | --- | --- | --- |
| `loomai-conversational-assistant@1` | User asks; LoomAI answers or requests a governed next step | Authenticated interactive backend request | One bounded orchestration turn with optional backend-owned conversation state | Shopify and ProdUS hosted paths prove the behavior; generic Product Profile packaging remains required |
| `loomai-agentic-specialist-team@1` | A bounded manager coordinates approved specialists for a larger user/application task | Authenticated interactive or application request declared by the profile | Exact-version specialists; manager may ask, invoke one approved read worker, or complete; optional fixed sequential/parallel read-only plan and one-level delegation/handoff | AI Fabric 0.5.2 and the Agentic Action Resolver prove the contracts; LoomAI has hosted exact-specialist proof, while a reusable multi-specialist template still requires hosted product verification |
| `loomai-smart-brain@1` | LoomAI proactively analyzes trusted system facts without waiting for a chat message | Trusted `APPLICATION`, `EVENT`, or `SCHEDULED` adapter; optional durable read-job submission | One exact read-only specialist per durable job, or a bounded process-local fixed read-only plan | AI Fabric 0.5.2 durable event execution and behavior demos prove the pattern; LoomAI trigger, queue, output, operations, and verification packaging remain required |

Template rules:

- The template owns activation and coordination semantics; a solution pack supplies only domain meaning.
- `Conversational Assistant` is the continuation of the AI Fabric 0.3 query-driven behavior and remains available in 0.5.2.
- `Agentic Specialist Team` is bounded agentic behavior. It is never described as unrestricted autonomous multi-agent execution.
- `Smart Brain` is proactive but not self-authoring. A trusted application decides which event/schedule maps to which exact specialist or fixed plan.
- A durable queue means explicit persisted jobs, bounded leases, recovery, status, cancellation, replay, and typed terminal results. It does not mean an endlessly self-directed agent.
- AI Fabric 0.5.2 does not provide a durable multi-step graph. Durable single-specialist reads and process-local fixed plans must remain separate claims.

### 7.2 Product Profile catalogue and compiler

Deliver:

- generic Product Profile and immutable version persistence;
- status lifecycle: `DRAFT`, `VALIDATED`, `PUBLISHED`, `RETIRED`;
- official capability-code validation;
- server-side allowed choice catalogue;
- semantic diff and compiled-input hash;
- compiler into existing deployment draft/config structures;
- audit of who created, validated, published, selected, and retired a version.

The generic Product Profile catalogue must not be implemented by expanding `ShopifyCompanionPackageProfileEntity` into unrelated verticals. Extract reusable concepts and keep Shopify-specific entitlements and install behavior in the Shopify product boundary.

### 7.3 Knowledge lifecycle

```text
connect/upload/push approved source
  -> preview canonical projection
  -> validate metadata, access, PII, size, and source policy
  -> durable indexing submission
  -> query `IndexingWorkQuery` through private authorized facade
  -> reconcile revision hash, entity counts, and dead-letter state
  -> run golden retrieval and answer tests
  -> publish Knowledge Ready
```

The Product Kernel must distinguish these source modes and their maturity explicitly:

- push Data Sync for customer-owned records: framework and hosted LoomAI foundation available;
- external documents-only retrieval where data remains in the customer connector: framework contract available;
- durable document ingestion for approved uploaded/connected files: application pattern available, managed LoomAI service still requires productization;
- migration/backfill for existing records: framework contract available, product operator workflow required;
- relationship query for approved relational questions: framework capability available, solution pack required;
- transient attachment analysis: separate non-indexing lifecycle and channel capability, never an implicit durable-ingestion path.

### 7.4 Conversation and UI surfaces

Standard LoomAI surfaces:

- backend application API;
- always-visible docked bottom composer;
- Max Mode;
- inline assistant panel/card;
- query-once analysis;
- structured result cards for evidence, commerce items, actions, confirmations, reviews, and failures.

The backend owns conversation ID authorization, prior turns, pending work, active specialist/dialogue owner, trusted context, and allowed modes. The browser sends current-turn input, safe page context, approved attachment handles, and public display preferences.

### 7.5 Specialist lifecycle

- Exact `name@version` manifests.
- Typed Java/JSON Schema input and output.
- Startup validation against registered modes, vector spaces, actions, and ceilings.
- Immutable artifact or mounted configuration.
- New deployment version for semantic changes.
- Read-only specialist first.
- Add input waits, fixed plans, durable jobs, writes, or review only for a concrete product requirement.

AI Fabric 0.5.2 does not provide a dynamic specialist database or safe hot reload. Platform authoring therefore compiles manifests into a new deployment version; it does not mutate the running registry.

### 7.6 Governed action lifecycle

```text
approved evidence and user input
  -> model proposes registered action and typed parameters
  -> capability/scope intersection
  -> application object authorization and validation
  -> dry-run where available
  -> confirmation or human review
  -> trusted application/MCP execution
  -> durable receipt when required
  -> system-of-record reconciliation
  -> generated user answer from safe result facts
```

Read actions ground the final answer. Development/operator UI may inspect bounded raw action evidence, but normal user UI must not end at raw JSON.

### 7.7 Identity and assignment

- Backend-only consumer assignment discovery.
- Scoped assignment credential, never Platform admin credential.
- Private runtime assertion with expected issuer/audience.
- Server-owned customer, tenant, deployment, subject, caller, and scopes.
- Exact scope intersection for specialist, action, and vector space.
- Two-tenant and two-deployment canaries for retrieval-capable profiles.

### 7.8 Privacy and governance

- Per-profile PII direction and mode.
- Approved safe metadata destinations.
- Conversation/source/receipt/review retention.
- Deletion and derived-vector cleanup.
- Encryption/fingerprint secret requirements.
- Audit events without prompts, completions, raw PII, secrets, or hidden connector context.
- Product claim review for policy, compliance, risk, refunds, or other sensitive outcomes.

### 7.9 Observability and quality

Expose:

- behavior-template, solution-pack, product-profile version and hash;
- activation source, coordination mode, extension set, durability posture, and current conversation/job/plan state as applicable;
- deployment version, release, target, source commit, and AI Fabric version;
- specialist version/content hash;
- provider and embedding readiness without secrets;
- retrieval counts and safe evidence references;
- indexing work state and convergence;
- MCP server/tool/schema drift posture;
- action proposal/confirmation/receipt/reconciliation state;
- product quality-pack version and latest result;
- stage latency, provider failure, and bounded diagnostics.

## 8. Behavior-First Product Portfolio

The market catalogue is organized by **how LoomAI behaves**, not by the customer's industry, data type, UI surface, or one specific task. Shopify, ProdUS, support, churn, documents, MCP, and knowledge are solution or capability compositions over the behavior products below.

### 8.1 Catalogue hierarchy

| Layer | Business meaning | Examples |
| --- | --- | --- |
| Behavior Product Template | The reusable LoomAI product a customer adopts | Conversational Assistant, Agentic Specialist Team, Smart Brain |
| Execution Extension | Optional governed behavior attached to a compatible template | Resolver, Human Review |
| Solution Pack | What the behavior understands and delivers for one domain | Shopify commerce, ProdUS project intelligence, support resolution, churn intelligence |
| Capability Pack | Reusable technical or AI capability | RAG, Data Sync, MCP, vectorization, relationship query, PII, structured output |
| Channel Binding | How an authorized caller activates or experiences the behavior | Backend API, docked composer, Max Mode, inline panel, event adapter, scheduler adapter |
| Deployment/Operations | How LoomAI hosts, verifies, assigns, promotes, and supports it | V04 version/release, target profile, managed vector/provider services, verification pack |

Only Behavior Product Templates are marketed as the primary LoomAI products. Other layers are described as included capabilities, solution packs, channels, or managed services.

Behavior template IDs such as `loomai-smart-brain@1` are Platform product contracts. Official provider capability codes such as `loomai_structured_outputs` remain composable capability declarations; neither namespace replaces the other.

### 8.2 Product A: LoomAI Conversational Assistant

Customer promise:

- Ask LoomAI in the product and receive a grounded answer, structured result, clarification, or governed next step using approved application context.

Core official capability bindings:

- required: `loomai_runtime_orchestration`;
- optional by profile: `loomai_conversation_session_memory`, `loomai_grounded_rag_answers`, `loomai_read_action_grounding`, `loomai_structured_outputs`, `loomai_embedded_assistant_ui`, and compatible governed extensions.

Activation and behavior:

```text
authenticated user query
  -> one bounded orchestration turn
  -> optional approved retrieval/read actions
  -> generation or structured result
  -> backend-owned conversation state where enabled
```

Composable capabilities include RAG, Data Sync, external retrieval, MCP/read actions, structured outputs, privacy controls, and governed actions. Channels include backend API, docked composer, Max Mode, inline assistant, and query-once.

Hosted evidence:

- Shopify Companion proves commerce conversation, knowledge, UI, MCP/read-action grounding, and managed lifecycle.
- ProdUS proves backend-mediated assignment, tenant/deployment-scoped retrieval, structured project knowledge, and hosted promotion/verification.

Maturity:

- Core behavior: `LOOMAI_HOSTED_PROVEN` and supportable through managed LoomAI deployment.
- Generic `loomai-conversational-assistant@1` Product Profile packaging: `PRODUCTIZATION_REQUIRED`.
- Shopify public App Store/commercial readiness remains separately governed by 010.18.

### 8.3 Product B: LoomAI Agentic Specialist Team

Customer promise:

- Give a larger user or application task to a governed team of focused specialists while LoomAI keeps one bounded conversation and returns a typed, explainable result.

Core official capability bindings:

- required: `loomai_runtime_orchestration`, `loomai_thinker_resolver_workflows`, `loomai_structured_outputs`;
- optional by profile: `loomai_conversation_session_memory`, `loomai_grounded_rag_answers`, `loomai_query_once_analysis`, `loomai_read_action_grounding`, and compatible governed extensions.

Activation and behavior:

```text
authenticated user or application request
  -> exact-version conversation manager or fixed coordinator
  -> ASK_USER, INVOKE_ONE_APPROVED_READ_SPECIALIST, or COMPLETE
  -> optional fixed sequential/parallel read-only plan
  -> deterministic aggregation and safe public result
```

Supported AI Fabric 0.5.2 composition:

- exact `name@version` specialists and typed schemas;
- backend-owned conversation manager;
- bounded input waits;
- fixed sequential and opt-in `ALL_REQUIRED` parallel read-only plans;
- one-level closed delegation or handoff;
- required grounding and safe evidence references;
- deterministic application aggregation.

Product boundaries:

- no model-generated graph, arbitrary specialist discovery, recursion, unrestricted tools, or authority expansion;
- fixed plan state is process-local in 0.5.2 and cannot be sold as a durable workflow graph;
- write-capable composed plans are unsupported;
- the application owns identity, specialist selection boundary, domain validation, and public projection.

Maturity:

- Framework contracts and executable Agentic Action Resolver proof: `AI_FABRIC_AVAILABLE`.
- Exact LoomAI specialist deployment and tenant/deployment isolation: `LOOMAI_HOSTED_PROVEN` for `deployment-knowledge-specialist@1`.
- Generic manager plus multi-specialist behavior template: `PRODUCTIZATION_REQUIRED` until compiled, deployed, and hosted-canary verified as one reusable Product Profile.

### 8.4 Product C: LoomAI Smart Brain

Customer promise:

- Let LoomAI observe trusted product events or scheduled work and proactively produce bounded analysis, recommendations, classifications, or alerts without waiting for a user chat message.

Core official capability bindings:

- required: `loomai_runtime_orchestration`, `loomai_thinker_resolver_workflows`, `loomai_structured_outputs`, `loomai_observability_evaluation`;
- optional by solution: `loomai_behavior_intelligence`, `loomai_semantic_relationship_query`, `loomai_grounded_rag_answers`, `loomai_read_action_grounding`, and `loomai_privacy_pii_controls`.

Activation and behavior:

```text
trusted application event / schedule / internal API / durable job submission
  -> backend maps facts to exact specialist and trusted execution context
  -> read-only analysis over approved current state, evidence, or read actions
  -> typed terminal result
  -> persist, notify, display, or route to authorized human review
```

Supported AI Fabric 0.5.2 composition:

- `APPLICATION`, `EVENT`, and `SCHEDULED` service/system execution sources;
- durable read jobs persisted before dispatch;
- bounded leases, startup recovery, scoped status/cancel/replay, encrypted request/result state, and typed terminal snapshots;
- stable idempotency binding for duplicate event delivery;
- process-local fixed sequential/parallel read-only plans for a single running invocation;
- behavior-event and relationship analysis capability packs where selected.

Product boundaries:

- the customer application or managed LoomAI adapter owns the event broker, scheduler, trigger mapping, and output destination;
- durable execution is at-least-once read execution, not exactly-once provider invocation;
- durable single-specialist jobs and process-local fixed plans are separate supported postures;
- no durable multi-step graph, fabricated user message, automatic write, event-triggered write, or self-selected ongoing objective;
- “any analysis” means any published specialist/solution contract with approved evidence and typed output, not arbitrary runtime self-programming.

Evidence and maturity:

- AI Fabric Agentic Action Resolver proves proactive durable event execution.
- Behavior Churn Signals proves live provider-backed event/behavior analysis and persisted insight output.
- Framework behavior: `AI_FABRIC_AVAILABLE`.
- Generic LoomAI trigger, queue, output, operations, and verification template: `PRODUCTIZATION_REQUIRED` until hosted end-to-end proof passes.

### 8.5 Execution extensions

#### Governed Resolver

Adds one registered write proposal, application validation, explicit confirmation, durable receipt where selected, execution through the trusted application/MCP boundary, and system-of-record reconciliation.

Primary official capability bindings: `loomai_governed_action_execution`, `loomai_safety_governance`, and `loomai_structured_outputs`.

It may extend compatible Conversational Assistant or Agentic Specialist Team profiles. AI Fabric 0.5.2 does not support event/scheduled durable write jobs, so it must not turn Smart Brain into an automatic mutation engine.

#### Human Review

Adds a durable version-bound review task, authorized reviewer assignment, correction/approval/rejection/information/escalation decisions, delivery evidence, and governed execution after approval.

Primary official capability bindings: `loomai_safety_governance`, `loomai_governed_action_execution` where approval can lead to a write, and `loomai_structured_outputs` for the reviewed proposal/result.

Reviewer identity and final authorization remain application-owned. Human Review may receive a Smart Brain result as a new application-owned review workflow, but the event job itself remains read-only.

Both extensions are `AI_FABRIC_AVAILABLE`; each deployed solution requires its own application action/reviewer contracts and hosted verification before external readiness claims.

### 8.6 Solution packs and reference deployments

| Solution pack/reference | Primary behavior | Included value | Current posture |
| --- | --- | --- | --- |
| Shopify Commerce Companion | Conversational Assistant | Grounded product discovery, storefront UI, commerce read tools, Knowledge Sync, optional governed commerce actions | Hosted controlled reference; public launch blockers remain in 010.18 |
| ProdUS Project Intelligence | Conversational Assistant | Project/service knowledge, grounded analysis, query-once and backend-mediated runtime integration | Hosted customer/reference deployment, not a separate behavior product |
| Grounded Company Knowledge | Conversational first; optional Specialist Team or Smart Brain | Approved records or customer-owned retrieval, citations, no-evidence behavior, source status | Retrieval foundation hosted; reusable solution pack needs productization |
| Account And Support Resolution | Conversational or Specialist Team plus optional Resolver/Review | Diagnose account state, ask for missing typed input, recommend or propose bounded resolution | Framework/reference-app proven; customer solution pack needs hosted proof |
| Deployment And Incident Intelligence | Agentic Specialist Team or Smart Brain | Read approved deployment/log/tool evidence and produce bounded diagnosis | Exact deployment specialist hosted; generic incident pack needs productization |
| Behavior And Churn Intelligence | Smart Brain | Convert product events into persisted churn, sentiment, trend, recommendation, and alert outputs | Live AI Fabric demo evidence; LoomAI Product Profile and operations proof required |
| Relationship/CRM Intelligence | Conversational Assistant or Smart Brain | Bounded questions and analysis over approved entity relationships | AI Fabric capability available; solution pack requires productization |
| MCP Operations | Any compatible behavior | Use reviewed external tools through exact server/tool/schema/auth bindings | Managed Gateway infrastructure hosted; every tool pack requires live proof |
| Document Knowledge Operations | Feeds any behavior needing durable knowledge | Upload/connect, preview, approve, parse, chunk, index, reindex, delete, and source status | Application pattern only; managed LoomAI service remains productization work |

A solution pack does not create a new top-level product name unless it introduces a genuinely different activation and coordination behavior.

### 8.7 Capability and channel classification

| Item previously treated like a product | Canonical classification |
| --- | --- |
| Grounded Knowledge Assistant | Conversational Assistant plus Grounded Company Knowledge solution/capability pack |
| Document Intelligence Workbench | Managed knowledge-operations application/capability service |
| Embedded Assistant Experience | UI channel pack shared by compatible behavior products |
| Bounded Investigation Specialist | Agentic Specialist Team solution configuration |
| Governed Resolution Assistant | Resolver execution extension plus domain solution pack |
| MCP Operations Assistant | MCP capability/solution pack attached to a behavior product |
| Relationship And Behavior Intelligence | Smart Brain or Conversational solution packs using relationship/behavior capabilities |
| Private Enterprise Copilot Factory | Future authoring, packaging, and lifecycle distribution layer over proven behavior templates |

Grounding, vectorization, external retrieval, MCP, actions, privacy, relationship query, behavior analysis, structured output, sessions, and UI rendering remain important marketable capabilities. They are described as what a behavior product can include, not as competing product identities.

### 8.8 Readiness and external claims

| Behavior product | Supported claim now | Claim requiring additional productization |
| --- | --- | --- |
| Conversational Assistant | LoomAI has hosted, verified conversational deployments and can deliver managed scoped deployments | Generic self-service/reusable `@1` Product Profile and broad package catalogue |
| Agentic Specialist Team | AI Fabric 0.5.2 supports bounded specialists; LoomAI has hosted exact-specialist security proof | A ready reusable team template requires hosted conversation-manager, multi-specialist plan, failure, and promotion canaries |
| Smart Brain | AI Fabric 0.5.2 supports trusted proactive read execution and durable jobs; executable behavior demos exist | A ready LoomAI template requires managed trigger binding, durable store/worker, output routing, monitoring, recovery, and hosted release proof |

External copy must say `governed`, `bounded`, `approved`, or `configured` where relevant. Do not claim unrestricted agents, continuous self-directed goals, arbitrary analysis, automatic high-impact decisions, exactly-once model calls, or durable multi-step workflows.

### 8.9 Private Enterprise Product Factory

Outcome:

- Launch isolated, branded solutions by selecting a proven Behavior Product Template and approved solution/capability/channel packs through the managed LoomAI lifecycle.

This is a factory over proven behavior products, not a blank autonomous-agent builder. Shared deployment infrastructure is `LOOMAI_HOSTED_PROVEN`; the generic self-service factory remains `FUTURE` until at least two behavior templates have reusable hosted Product Profiles and support operations.

## 9. Runtime Composition Postures

These are compiler requirements selected by Behavior Product Templates. They are not customer-visible products or free-form toggles.

| Runtime posture | Primary behavior use | AI Fabric composition | Required durable state | Current caveat |
| --- | --- | --- | --- | --- |
| Read-only grounded | Any behavior | RAG + generation + vector/retrieval provider | Vector/source lifecycle as selected | Evidence must be scoped, sanitized, and quality-tested |
| Interactive conversation | Conversational Assistant | Ordinary orchestration + chat session | Configured session storage | Backend owns conversation identity, history, pending work, and reset |
| Interactive specialist | Agentic Specialist Team | Read specialist + conversation manager + typed wait | Chat session as selected | Input waits and manager state are process-local; avoid restart-survival claims |
| Governed write | Conversational Assistant or Agentic Specialist Team extension | Specialist/action proposal + confirmation | `ai_action_proposal_receipt`, stable secrets, application transaction | Side effect remains application-owned |
| Human review | Compatible behavior extension | Review task + delivery + governed receipt | `ai_review_task`, `ai_review_dispatch`, receipt storage | Reviewer authorization and assignment are product/application-owned |
| Durable event read | Smart Brain | Trusted event/schedule adapter + exact specialist execution worker | `ai_specialist_execution`, stable encryption/fingerprint secrets | At-least-once read execution, not exactly-once provider calls or event writes |
| Process-local fixed plan | Agentic Specialist Team or Smart Brain | Fixed sequential/parallel read-only plan | Bounded in-process checkpoints | Exact-version, deterministic topology; not a durable graph |
| Live data RAG | Any retrieval-enabled behavior | Data Sync + indexing worker + vector provider | Indexing queue/work plus vector storage | Query work status; do not infer completion from vector presence |
| Tenant-isolated SaaS | Any multi-tenant behavior | Trusted auth + scoped retrieval/actions + canaries | Customer identity source and scoped data | Missing boundary must fail before retrieval/action |

Platform should combine only approved postures declared by a Product Profile. It should not expose every cross-product combination as a toggle matrix.

## 10. Provider And Claude Strategy

### 10.1 Provider-neutral rule

- Product behavior, authority, actions, vector spaces, and verification are provider-neutral.
- Inference profiles define provider/model/endpoint/timeouts/token/cost/fallback posture.
- Provider credentials remain Platform secret references.
- A provider change compiles a new deployment version and repeats relevant live-provider checks.
- Fallback is explicit policy with visible attempts; no silent provider substitution.

### 10.2 Claude as a model provider

AI Fabric's Spring AI provider integration can register Anthropic generation when enabled. Platform already validates Anthropic provider selection/model configuration and has an Anthropic/Lucene topology preset.

For RAG, bind a separate supported embedding provider because the current Anthropic path does not provide an AI Fabric embedding provider.

Claude can perform reasoning, schema-bound output, clarification, action proposal, and answer generation. It does not own trusted context, retrieval scope, authorization, confirmation, persistence, or projection.

### 10.3 Claude Code as a future developer channel

Claude Code can connect to remote HTTP MCP servers. That makes a future LoomAI authoring MCP server technically plausible, not currently implemented.

It remains optional and provider/client specific:

- Product Profile compiler and Platform UI/API must work without Claude Code.
- Claude Code may edit local source-controlled manifests/schemas and call authorized validation/draft tools.
- It may not grant capabilities, provide trusted tenant/scope, bypass checks, or deploy without an authenticated explicit operation.
- Workspace/project MCP configuration must not contain durable Platform admin credentials.

### 10.4 Claude Messages MCP channel

A model API MCP connector may later invoke a remote LoomAI MCP server. This is not the default application integration path. Customer products should continue to use backend-mediated LoomAI runtime APIs unless a specific channel provides better value and passes the same authority tests.

Implementation-time references:

- [Claude Code MCP documentation](https://code.claude.com/docs/en/mcp)
- [Claude MCP connector documentation](https://platform.claude.com/docs/en/agents-and-tools/mcp-connector)
- [MCP authorization specification](https://modelcontextprotocol.io/specification/2025-06-18/basic/authorization)
- [Spring AI MCP reference](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-overview.html)

These external client/protocol contracts can evolve. Recheck official documentation during implementation.

## 11. MCP Strategy

### 11.1 Outbound MCP is current LoomAI capability

The managed MCP Execution Gateway owns:

- Streamable HTTP protocol handling;
- initialization, sessions, `tools/list`, and `tools/call`;
- endpoint and exact server binding;
- supported auth profile execution;
- secret resolution inside Platform/private service boundaries;
- schema normalization, hashing, drift policy, and blocking checks;
- argument template rendering;
- response mapping, normalized evidence, and bounded failure;
- managed service health, drift, restart, placement, and lifecycle.

Marketplace owns discovery/import drafts, reviewed plugin versions, action IDs, risk/access mode, install, and compilation.

AI Fabric owns action orchestration, parameter/result contracts, confirmation policy, answer grounding, and specialist/action composition.

Product Bridges own host-specific session, billing, consent, account token, and object authorization that generic protocol code cannot infer.

### 11.2 Direct framework MCP executor follow-up

Current public `SpringAiMcpActionExecutor.findClient(...)` behavior:

- tries to match a declared server reference;
- if no matching server client is found, keeps all clients as candidates;
- may choose another client exposing the same tool name.

That is not acceptable for a declared production server binding.

Decision:

- LoomAI production continues to use the managed Gateway exact binding.
- If direct Spring AI-managed MCP client execution is required, raise a public framework change: declared `serverRef` with no exact matching client must fail closed.
- Add regression tests with two clients exposing the same tool name.
- Release immutably and upgrade through Maven Central before enabling that path.

This is not a blocker for the current managed Gateway path.

### 11.3 Future inbound LoomAI authoring MCP server

This is a new product service, not an extension of the existing outbound Gateway.

Purpose:

- expose stable, bounded Platform authoring and operations tools to approved developer clients;
- never expose raw framework internals or unrestricted operational authority.

Read-only first tools:

```text
platform_get_product_profile_catalog
platform_get_authoring_catalog
platform_validate_product_profile
platform_validate_specialist_manifest
platform_get_deployment_source_of_truth
platform_get_deployment_status
platform_get_release_evidence
platform_get_indexing_work_status
platform_get_specialist_execution
```

Mutation tools only after the read-only server passes auth and audit gates:

```text
platform_create_deployment_draft
platform_compile_product_profile
platform_run_release_checks
platform_submit_release
platform_confirm_release_apply
platform_resume_specialist_input
platform_decide_action_confirmation
platform_decide_review_task
```

Do not expose:

- `execute_any_action`;
- `invoke_any_specialist`;
- arbitrary deployment/provider/vector/action identifiers outside caller inventory;
- raw secret access;
- arbitrary URL fetch, SQL, shell, script, or expression execution;
- direct mutation of an active specialist/profile version.

Security requirements:

- remote HTTP MCP protected by an approved OAuth/resource-server posture;
- token identity maps to Platform principal and authorized customer/deployment inventory;
- tool arguments are untrusted and cannot set identity, tenant, deployment, scopes, provider credentials, action authority, or specialist authority;
- operation approval remains a Platform record, not free-text confirmation;
- rate limits, audit, idempotency, expiry, and safe output bounds;
- independent verification suite for every MCP tool.

Resources and prompts are deferred until tool authorization and stable URI design are proven.

## 12. Conversational Authoring And Deployment

Target experience:

```text
developer describes a bounded AI capability
  -> assistant inspects authorized local code/config and LoomAI catalogue
  -> assistant proposes Product Profile bindings, specialist manifest, and schemas
  -> local tests and server-side validators run
  -> Platform creates a draft and semantic preview
  -> developer/operator reviews capability, infrastructure, cost, security, and verification requirements
  -> authenticated approval creates/releases immutable version
  -> exact deployment UUID is polled to terminal state
  -> post-apply verification and assignment occur
```

Assistant may:

- propose source-controlled profile/manifests/schemas;
- select only from authorized catalogue choices;
- call validation and read-only status tools;
- request draft creation and tests;
- explain failures and required operator actions.

Assistant may not:

- invent actions, vector spaces, providers, scopes, secrets, or deployment capabilities;
- infer authority from a prompt;
- make failed checks optional;
- approve its own release;
- treat a healthy old container as completion of a new deployment;
- rewrite immutable profile/specialist versions;
- switch consumer assignment before post-apply evidence passes.

Platform UI and ordinary APIs remain first-class authoring channels. The MCP authoring channel must reuse them rather than becoming a second control plane.

## 13. Security And Governance Requirements

1. Identity, subject, customer, tenant, deployment, caller, issuer, audience, and scopes come from authenticated backend context.
2. Behavior Product Templates, Product Profiles, solution packs, and specialists request/narrow capability; they cannot grant it.
3. Activation sources, trigger mappings, actions, vector spaces, specialists, providers, output destinations, and channels resolve from Platform/application-owned catalogues.
4. Retrieval uses exact approved vector spaces and trusted tenant/deployment filters.
5. Caller metadata cannot override trusted execution or retrieval metadata.
6. Model output is schema-bound, validated, and safely projected.
7. User-owned values may use configurable warning validation where accepted by product policy; trusted resource IDs, hidden targets, identity, and authority always fail closed.
8. Writes remain proposals until application policy plus confirmation or authorized review passes.
9. Confirmation references immutable identity-bound facts and cannot replace parameters.
10. Final action authorization and domain validation repeat immediately before execution.
11. Durable receipts prevent duplicate execution under the supported idempotency contract.
12. Reconciliation checks the application system of record.
13. Provider, MCP, validation, policy, persistence, indexing, and deployment failures remain visible.
14. Secrets stay in Platform/private service boundaries and never enter prompts, manifests, browser assets, or tracked packages.
15. Logs exclude prompts, completions, raw PII, credentials, protected receipts, and hidden connector context.
16. Semantic profile/specialist changes create new immutable versions and hashes.
17. Rollback stops new invocations while preserving unresolved durable work for authorized recovery.
18. High-impact decisions remain human/application authority.
19. Public event payloads contain domain facts only. They cannot select identity, tenant, deployment, specialist, scopes, provider, vector space, action, or output authority.
20. Smart Brain event/scheduled work remains read-only in AI Fabric 0.5.2; process-local fixed plans cannot be represented as durable workflow state.

## 14. Verification Architecture

Every Behavior Product Template Version has a baseline behavior verification pack. Every Product Profile Version binds that baseline plus the checks required by its solution, capabilities, extensions, channels, providers, topology, and target.

| Gate | Required proof |
| --- | --- |
| Behavior static validation | Exact behavior-template version, activation sources, coordination mode, extension compatibility, durability, and unsupported-claim policy |
| Profile static validation | Exactly one behavior template; official capability codes; solution/package/runtime/vector/channel/topology compatibility |
| Framework dependency | Central-only build resolves exactly AI Fabric 0.5.2 and intended optional modules |
| Compilation | Marketplace packs, curated behavior, V04 entities, actions, vector spaces, specialists, schemas, and secrets-by-ref compile |
| Unit | Adapters, validators, projectors, parameter/result policy, and failure projection |
| Integration | Only modules used by profile: retrieval, sessions, actions, receipts, review, behavior, relationship, indexing, storage |
| Packaged runtime | Container starts with exact source/profile/framework identity and production-like configuration |
| Provider | Real configured generation and embedding calls where claimed; failure visible |
| Knowledge | Create/update/delete/reindex, work-status lifecycle, counts, revision hash, expected evidence |
| RAG quality | Golden queries, expected sources, no-evidence correctness, citations, forbidden claims |
| Security | Cross-principal, subject, tenant, deployment, scope, evidence, action, receipt, and review denial |
| Session | Owner-scoped history/pending work, reset, expiry, replay, cross-owner denial |
| Agentic team | Exact manager/workers, `ASK_USER`/worker/complete bounds, independent worker authority, fixed-plan mappings, deterministic aggregation, process-local limit |
| Smart Brain activation | Trusted event/application/scheduled mapping, no fabricated chat, service identity, read-only enforcement, output destination authorization |
| Smart Brain durability | Persist-before-dispatch, duplicate replay, changed-facts conflict, lease recovery, restart, status/cancel, encryption, retention, typed terminal result |
| Specialist | Exact version/hash, typed I/O, capability intersection, grounding, waits/jobs as selected |
| MCP | Exact server/tool binding, live auth, schema drift, arguments, output sanitation, unavailable server, tool failure |
| Write/review | Dry-run, confirmation/rejection, immutable receipt, restart, idempotent replay, reconciliation |
| Privacy | Input/output PII mode, storage, logging, retention, deletion, derived evidence cleanup |
| UI | Docked, Max, inline, query-once, cards, denial/failure, desktop/mobile/accessibility |
| Deployment | Exact deployment UUID terminal result, health, post-apply verification, target scope, assignment |
| Export/promotion | Profile/version evidence, target-scoped rewrite, import preview, rollback |
| Operations | Monitoring, alert, support, incident, backup/export, offboarding, owner runbooks |
| Commercial | Entitlements, quotas, billing, listing/claims, privacy and support match live behavior |

Behavior and pack proof examples:

- Conversational Assistant: an authenticated user query returns an answer, clarification, structured result, or governed next step without leaking raw internal envelopes.
- Agentic Specialist Team: the manager selects only one declared read worker or completes/asks; fixed branches return typed evidence and aggregate deterministically; restart-sensitive state is not claimed durable.
- Smart Brain: a trusted event produces no chat turn, creates one scoped read job, survives restart where promised, replays identical delivery, rejects changed facts, and cannot write.
- Grounded Company Knowledge pack: a known evidence ID must be top-k and appear in generated answer citations; update/delete changes evidence only after work reaches successful terminal state.
- Resolver extension: confirmation survives restart where promised, executes once, and reconciles.
- MCP pack: discovered schema hash matches installed binding or execution fails before `tools/call`.
- Personalized output pack: every returned component ID exists in the allowlist; no executable code appears.

## 15. Delivery Sequence

### P0: Behavior Product spine and Conversational Assistant baseline

1. Keep Shopify Companion and production release gate green.
2. Define immutable `loomai-behavior-product-template-v1` and `loomai-product-profile-v1` schemas and version/hash rules.
3. Register `loomai-conversational-assistant@1`, `loomai-agentic-specialist-team@1`, and `loomai-smart-brain@1` as distinct activation/coordination contracts.
4. Generalize product-safe package/runtime/vector/verification mapping without copying Shopify-specific install behavior.
5. Compile one Conversational Assistant Product Profile into the existing V04 draft/version/release lifecycle.
6. Add semantic preview showing behavior template, solution/capability packs, extensions, channels, durability, and boundaries.
7. Use Shopify and ProdUS as regression references, not as the generic behavior schema.

Exit:

- one generic Conversational Assistant profile can compile, stage, verify, promote, assign, export/import, and roll back without manual runtime env editing;
- external/product UI clearly distinguishes behavior product, solution pack, capability pack, channel, and deployment topology.

### P1: Agentic Specialist Team

1. Add Platform authoring/validation for exact specialist manifests, schemas, prompts, registered actions/vector spaces, and ceilings.
2. Compile an immutable conversation manager plus at least two exact read-only specialists into a V04 deployment version.
3. Prove `ASK_USER`, one approved worker invocation, `COMPLETE`, failure, denial, and replay behavior.
4. Add one fixed sequential and one bounded parallel read-only plan with deterministic aggregation.
5. Deploy one hosted Agentic Specialist Team profile and repeat two-tenant/two-deployment, missing-boundary, provider-failure, and promotion canaries.

Exit:

- the reusable team template is hosted-proven without dynamic topology, arbitrary specialist selection, shared hidden conversations, or write-capable plans;
- process-local manager/plan limits are visible in product claims and operations.

### P2: Smart Brain

1. Define trusted `APPLICATION`, `EVENT`, and `SCHEDULED` channel bindings and event-contract versioning.
2. Map each trigger deterministically to one exact read-only specialist, trusted subject/tenant/deployment context, scopes, and output contract.
3. Provision durable execution storage, stable encryption/fingerprint secrets, workers, leases, recovery, status, cancellation, replay, retention, and cleanup.
4. Add output sinks for persisted insight, application callback/notification, and authorized human-review routing without automatic mutation.
5. Prove duplicate delivery, changed-facts conflict, restart recovery, lease expiry, provider failure, cross-tenant denial, and terminal result projection.
6. Add a process-local fixed analysis plan only after the durable single-specialist path is green; do not describe it as a durable plan.
7. Productize one Behavior/Churn or Incident Intelligence solution pack on the Smart Brain behavior.

Exit:

- one event/scheduled Product Profile runs without a chat turn, survives restart where promised, exposes one typed terminal result, and passes release/promotion/operations gates;
- no event or scheduled path can propose or execute a write.

### P3: Governed Resolver and Human Review extensions

1. Bind Resolver only to compatible Conversational Assistant or Agentic Specialist Team profiles.
2. Add one low-risk registered write with trusted target, application validation, explicit confirmation, and safe result generation.
3. Add durable receipt, restart/replay proof, and system-of-record reconciliation.
4. Add Human Review as a separate application-owned workflow with reviewer authorization, delivery, decisions, escalation, expiry, and recovery.
5. Allow Smart Brain results to create application-owned review work only through a new authorized boundary; keep the originating event execution read-only.

Exit:

- denial never executes, confirmation cannot replace trusted facts, retries do not duplicate side effects, reviewer authority is independent, and reconciliation is terminal.

### P4: Shared solution, capability, and channel packs

1. Productize Grounded Company Knowledge with push Data Sync and external documents-only retrieval as distinct modes.
2. Freeze backend API, docked composer, Max Mode, inline, query-once, and structured result-card channel contracts.
3. Build Knowledge Sync/status/revision/quality operations without claiming full document ingestion.
4. Package one non-Shopify MCP server through discovery, review, publish, install, apply, and live verification.
5. Productize Document Knowledge Operations separately with trusted storage/upload, source policy, Spring AI readers, preview, approval, chunk manifest, reindex, delete, and cited query.
6. Keep transient attachments separate from durable knowledge ingestion.

Exit:

- each pack can attach to an allowed behavior without changing activation, coordination, identity, authority, or durability semantics;
- knowledge, MCP, document, and UI verification gates pass independently.

### P5: Additional solution packs

Select solution packs from customer evidence, while reusing one of the three behavior templates:

- account/support resolution;
- incident/deployment intelligence;
- relationship/CRM intelligence;
- another commerce platform;
- project/architecture analysis;
- another event-driven analysis domain.

Exit:

- every solution adds only domain adapters, approved schemas, prompts, profile/pack bindings, UI cards, and quality fixtures;
- a runtime fork or new activation model requires explicit architectural review.

### P6: Provider, MCP, and operational expansion

1. Expand verified inference/vector combinations without changing behavior semantics or authority.
2. Continue using the managed MCP Gateway exact binding.
3. Add more reviewed MCP solution packs with per-pack auth, drift, read/write, outage, and quality proof.
4. Raise the strict-server public framework fix only if direct Spring AI executor use is required.
5. Add quotas, cost controls, alerts, backup/restore, retention, support, and offboarding per behavior template.

Exit:

- each supported provider/connector combination passes the behavior template's complete verification pack and preserves visible failure.

### P7: Optional inbound authoring MCP and enterprise factory

Prerequisites:

- all three behavior template contracts are stable and at least two have reusable hosted Product Profiles;
- Platform authoring/validation APIs are stable;
- OAuth/resource-server, audit, rate limit, operation approval, and tool-level authorization designs are approved.

Sequence:

1. read-only authoring/status MCP tools;
2. validation tools;
3. draft creation;
4. release-check execution;
5. explicitly confirmed release operations;
6. Claude Code/client integration guide;
7. enterprise/partner guided product factory.

Exit:

- MCP clients cannot exceed Platform UI/API authority, and all mutations produce the same deterministic Platform records and release evidence.

## 16. Implementation Workstreams

### Workstream A: Behavior Product and Product Profile domain

- Behavior Product Template persistence, immutable versions, activation/coordination contracts, hashes, statuses;
- Product Profile persistence selecting exactly one behavior template;
- solution-pack, execution-extension, capability-pack, and channel bindings;
- official capability-code validation;
- allowed option catalogue;
- package/tier and solution-pack extension points;
- REST/admin UI;
- audit and export.

### Workstream B: Compiler integration

- resolve curated module, Marketplace packs, inference/vector/runtime profiles;
- enforce behavior-template activation, coordination, extension, durability, and channel compatibility;
- validate topology/target/service requirements;
- compile V04 draft and semantic diff;
- preserve deterministic version/release behavior;
- no runtime catalogue lookup per request.

### Workstream C: Product verification packs

- schema and fixture storage;
- behavior-template baseline suites plus solution/capability/profile bindings;
- deterministic and live-provider cases;
- quality thresholds and forbidden claims;
- activation-source, coordination, durability, restart, and output-routing canaries;
- release gate aggregation;
- operator-readable remediation.

### Workstream D: Conversational and UI channel bindings

- stable backend API contract;
- docked composer, Max Mode, inline, query-once;
- result renderer registry;
- safe page context/attachment adapters;
- backend-owned conversation, reset, pending-work, and manager ownership;
- future MCP channel as separate service.

### Workstream E: Smart Brain activation and durable operations

- trusted application/event/scheduled adapter contracts;
- deterministic trigger-to-specialist/plan mapping;
- event-contract versions, idempotency, service identity, and scope policy;
- durable execution database, encryption/fingerprint secrets, workers, leases, recovery, status, cancellation, replay, retention, and cleanup;
- persisted insight/callback/notification/review output sinks;
- no-chat-turn, no-event-write, restart, duplicate-delivery, and cross-tenant canaries.

### Workstream F: Specialist, Resolver, and Human Review operations

- manifest/schema authoring;
- conversation-manager and fixed-plan authoring with exact targets and ceilings;
- exact-version deployment packaging;
- trusted adapters;
- execution status/input waits/jobs;
- proposal/confirmation/review/receipt/reconciliation UI;
- security and restart canaries.

### Workstream G: Solution and capability packs

- Grounded Company Knowledge source types, projection, status, revision, counts, reindex/delete, quality, and citations;
- Document Knowledge Operations storage, upload policy, reader integration, preview, chunk manifests, and lifecycle;
- discovery/import UX;
- mapping/risk classification;
- versioning, install, drift and deprecation;
- product Bridge host policy where required;
- Gateway operations and live server verification;
- read-result grounding and governed writes;
- relationship and behavior signals;
- reusable structured outputs, privacy policies, prompts, schemas, adapters, result cards, and quality fixtures.

### Workstream H: Commercial and operational packaging

- entitlements, quotas, cost posture, billing;
- onboarding, support, incident, offboarding;
- partner-safe product choices and evidence;
- privacy and data lifecycle;
- product claims and maturity publication.

## 17. Framework Blockers And Product Gaps

### Confirmed framework follow-up

`AF-MCP-STRICT-SERVER-REF`:

- Scope: public `ai-fabric-actions-connector` direct Spring AI MCP executor.
- Expected: when `execution.mcp.serverRef` is declared, only an exact matching client is eligible; no match returns explicit unavailable/invalid configuration.
- Evidence: current `findClient` falls back to all clients when the server-filter result is empty.
- Impact: blocks direct executor use as LoomAI production trust boundary, but does not block the managed MCP Gateway.

### LoomAI product gaps, not framework blockers

- immutable Behavior Product Template catalogue/version contract;
- generic Product Profile catalogue/version/compiler;
- generic Conversational Assistant Product Profile independent of Shopify-specific packaging;
- generic package/runtime/vector/verification mapping beyond Shopify;
- hosted reusable conversation-manager/multi-specialist team profile;
- Smart Brain trusted trigger bindings, durable worker operations, output routing, and hosted profile proof;
- reusable embedded assistant package;
- knowledge/document operator UX;
- specialist authoring/deployment UI;
- product verification-pack domain;
- inbound authoring MCP server;
- customer-level billing/support/onboarding for new profiles.

Do not ask AI Fabric to implement these product/control-plane responsibilities.

### Blocker handling rule

For a genuine generic framework gap:

1. reproduce it in the public framework repository;
2. specify API, security, failure, and compatibility behavior;
3. add tests and real-app proof;
4. publish an immutable framework release;
5. upgrade private consumers through Maven Central;
6. rerun product canaries and full Platform gate.

No private duplicate framework implementation, copied source, text-matching special case, relaxed filter, or fake endpoint.

## 18. Product Success Measures

The consolidated architecture succeeds when:

1. A product owner first chooses Conversational Assistant, Agentic Specialist Team, or Smart Brain instead of assembling framework modules or naming a vertical as a new product architecture.
2. The owner then chooses an approved solution pack, capability packs, extensions, channels, package, and allowed runtime posture.
3. Platform resolves those product-safe choices into a complete, reviewable V04 draft.
4. The same Product Profile can use another approved inference profile without changing activation, coordination, authority, or product semantics.
5. A Solution Pack cannot silently change behavior-template activation, topology, durability, identity, or authority.
6. Backend API, UI, application-event, and scheduled surfaces invoke only their declared trusted behavior.
7. A capability absent from deployment inventory cannot be invented by a model, client, profile, solution pack, or Marketplace discovery result.
8. Cross-user, tenant, deployment, activation-source, specialist, and output-destination canaries fail closed.
9. Knowledge changes converge and quality tests detect stale or missing evidence.
10. A Smart Brain job survives and replays only where promised, exposes one typed terminal result, and never fabricates a chat turn or executes a write.
11. A confirmed write executes under application authority and replays safely where durability is promised.
12. A live MCP tool is bound and governed like a registered action.
13. Provider, MCP, retrieval, indexing, policy, validation, persistence, trigger, queue, and deployment failures remain visible.
14. Behavior/profile version and hash, deployment version/release, specialist hash, source commit, framework version, provider posture, and verification evidence are observable.
15. A second solution pack launches on an existing behavior and a second behavior template launches without forking the private runtime or creating a new deployment workflow.

Business/product measures:

- activation and time-to-first-value;
- conversational completion/clarification quality;
- specialist-team task completion, manager routing quality, and deterministic aggregation;
- Smart Brain event-to-insight latency, queue age, terminal success, duplicate suppression, and useful alert rate;
- task completion and evidence trust;
- known-answer/helpfulness quality;
- retained usage;
- support incidents per active deployment;
- cost per successful outcome;
- design-partner conversion and willingness to pay;
- honest, consented business outcome attribution.

## 19. Definition Of A Releasable Behavior Product And Product Profile

A Behavior Product Template is releasable only when:

- its activation sources, coordination contract, interaction posture, output contract, extensions, durability, and unsupported claims are explicit;
- every runtime behavior maps to shipped AI Fabric or an explicitly owned LoomAI/application component;
- deterministic and real-provider tests prove its normal, denial, failure, timeout, replay, restart, and isolation behavior;
- external language distinguishes bounded behavior from unrestricted autonomous agents;
- support and operations can identify the exact behavior-template version and current execution state.

A Product Profile built from that template is releasable only when:

- its customer promise and boundaries are explicit;
- it selects exactly one published Behavior Product Template and only compatible extensions, solution packs, capabilities, and channels;
- official LoomAI capability codes are valid;
- every capability maps to shipped AI Fabric or an explicitly owned LoomAI component;
- Platform can compile, preview, deploy, verify, promote, assign, export/import, roll back, and retire it;
- the customer application retains identity, authorization, validation, transactions, and system-of-record authority;
- all promised UI, failure, denial, no-evidence, confirmation, and review states exist;
- intended live providers and integrations pass quality/security gates;
- tenant/deployment isolation and missing-boundary behavior are proved;
- event/scheduled profiles prove trusted trigger mapping, durable-state posture, output routing, and no-write boundaries where applicable;
- support, billing, privacy, incident, rollback, and offboarding ownership exists;
- external claims match the exact live package/tier/profile and do not rely only on framework demos.

## 20. Immediate Work Queue

### Immediate P0

- Review and approve the vocabulary/source-of-truth model in this document.
- Define `loomai-behavior-product-template-v1` and `loomai-product-profile-v1` as structured schemas.
- Register the three behavior templates and their activation/coordination/durability matrices.
- Inventory which generic fields can be extracted from the Shopify package profile without moving Shopify-specific logic.
- Define Product Profile Version persistence, hash, lifecycle, and audit.
- Add compiler preview from one generic Conversational Assistant profile into an existing V04 draft.
- Bind a reusable Conversational Assistant verification pack.
- Keep Shopify and the full production release gate green.

### Next P1

- Deploy a generic Conversational Assistant design-partner profile using a Grounded Company Knowledge solution pack.
- Build and host-canary `loomai-agentic-specialist-team@1` with one manager and at least two exact read-only specialists.
- Build and host-canary `loomai-smart-brain@1` with a trusted event, durable read job, restart recovery, and typed output sink.
- Freeze conversational, manager, event/scheduled, status, cancellation, replay, and output channel contracts.
- Add Product Profile selection and source-of-truth views in Platform UI.

### Later gated items

- Resolver extension with one low-risk write and the separate Human Review extension.
- Grounded Company Knowledge, Document Knowledge Operations, and non-Shopify MCP packs.
- Customer-facing specialist-team solution packs.
- Behavior/churn and incident Smart Brain solution packs.
- Read-only inbound LoomAI authoring MCP server.
- Enterprise/partner product factory.

## 21. Final Recommendation

Proceed with Behavior Product Templates plus Product Profiles, built as a product-intent layer over LoomAI's existing Platform and Marketplace, not as a replacement deployment platform.

The final positioning is:

> LoomAI delivers three reusable governed AI behaviors: a Conversational Assistant that responds to people, an Agentic Specialist Team that coordinates bounded expertise, and a Smart Brain that reacts proactively to trusted application events and scheduled work. Solution and capability packs adapt those behaviors to commerce, project intelligence, support, operations, knowledge, behavior signals, and other domains. AI Fabric supplies reusable runtime contracts. LoomAI supplies behavior templates, product profiles, managed infrastructure, provider and storage binding, channels, verification, assignment, promotion, and lifecycle. The customer application remains the authority.

Treat:

- AI Fabric as the reusable runtime foundation;
- Behavior Product Template as the market-facing product and activation/coordination contract;
- Product Profile as the immutable deployable composition of one behavior plus approved packs and infrastructure;
- Shopify, ProdUS, support, churn, incident, commerce, and other domains as solution packs or reference deployments;
- RAG, vectorization, MCP, actions, privacy, documents, relationship query, behavior signals, and UI as capabilities, managed services, extensions, or channels;
- Marketplace as capability packaging;
- V04 version/release as immutable operational truth;
- MCP as a governed connectivity plane;
- Claude as an optional provider and future client channel;
- Platform as the deterministic product control plane;
- Shopify and ProdUS as current Conversational Assistant reference proofs;
- exact specialists, fixed plans, and conversation managers as the bounded foundation of Agentic Specialist Team;
- trusted event/schedule adapters and durable read jobs as the bounded foundation of Smart Brain;
- Resolver and Human Review as controlled extensions, not autonomous authority.
