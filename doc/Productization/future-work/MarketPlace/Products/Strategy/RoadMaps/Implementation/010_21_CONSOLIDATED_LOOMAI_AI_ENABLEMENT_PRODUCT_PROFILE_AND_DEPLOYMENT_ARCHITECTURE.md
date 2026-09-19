# 010.21 Consolidated LoomAI Customer Product And Deployment Behavior Architecture

Status: canonical consolidated plan created on 2026-08-01, corrected to deployment-local Smart Brain integration on 2026-08-04, corrected on 2026-09-18 so customer AI products are composed through the existing Platform primitives rather than modeled as LoomAI-owned products or a parallel product-profile lifecycle, aligned on 2026-09-19 with AI Fabric `0.7.0` declarative bounded specialist chains, and updated after the hosted `0.7.0` Gate A rollout completed. Existing framework and hosted capabilities are evidence; new behavior-aware Marketplace contributions and runtime contracts remain planned until implemented and verified.

The historical filename is retained so existing documentation links do not break. The architecture below explicitly rejects a separate `Product Profile` aggregate.

This document consolidates and judges:

- [010.20 AI Fabric 0.5.2-Backed LoomAI Product Portfolio And Productization Plan](010_20_AI_FABRIC_0_5_2_BACKED_LOOMAI_PRODUCT_PORTFOLIO_AND_PRODUCTIZATION_PLAN.md)
- public framework proposal `docs/planning/0018-loomai-ai-enablement-product-and-deployment-template-proposal.md` in the sibling `Loom-AI-Labs/ai-fabric-framework` repository

Where those sources conflict, this document is the LoomAI Platform architecture source of truth. The public framework proposal remains useful framework-side input; it does not define LoomAI control-plane state, customer product ownership, implementation priority, or current hosted maturity.

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

Define one coherent LoomAI model that lets Platform users create and operate their own AI-enabled products from current AI Fabric functionality.

The consolidated model must:

1. use shipped AI Fabric contracts only where they actually exist;
2. use LoomAI's current Platform, Marketplace, V04 deployment, managed-service, assignment, and verification concepts instead of creating a parallel control plane;
3. distinguish existing proof, reusable productization work, and future proposals;
4. preserve provider neutrality while supporting Claude, OpenAI, Gemini, Azure, Cohere, and other verified providers through governed profiles;
5. treat outbound MCP execution and future inbound MCP authoring as different capabilities and trust boundaries;
6. keep customer application identity, authorization, validation, transactions, and system-of-record authority intact;
7. model Conversational Assistant, Agentic Specialist Team, and Smart Brain as supported deployment behavior types, not LoomAI-owned customer products;
8. let a customer product compose those behavior types with reusable templates, plugins, profiles, channels, and deployment infrastructure;
9. treat commerce, support, operations, knowledge, churn, and similar domains as customer solutions or reusable Marketplace contributions; and
10. sequence Platform delivery around real customer value rather than the order in which framework primitives were built.

## 2. Executive Decision

LoomAI is a generic AI-enablement control plane. It must not own or market the customer's domain product as a Platform product, and it must not add a second `Product Profile` aggregate beside the existing deployment lifecycle.

The Platform should add one small, server-owned **Deployment Behavior Type** discriminator to the existing V04 deployment model. It describes how a deployment is activated and coordinated. The supported initial types are:

1. **Conversational Assistant**: reactive, user-query-driven assistance using one bounded orchestration path per turn.
2. **Agentic Specialist Team**: interactive or application-invoked coordination of exact-version specialists through a bounded conversation manager, bounded multi-specialist chain, or fixed read-only plan.
3. **Smart Brain**: proactive read-only analysis initiated by a trusted application event, scheduler, API, or durable job queue without fabricating a user chat turn.

These are Platform execution capabilities. A Platform user creates the actual customer product by combining one behavior type with domain-specific templates, plugins, data, actions, prompts, schemas, UI surfaces, and application contracts.

`Governed Resolver` and `Human Review` are execution extensions, not deployment types or products. They may be enabled only for behavior types and activation sources whose released runtime contracts support them. In AI Fabric `0.7.0`, specialist-chain workers and event/scheduled durable work are read-only; do not attach automatic writes to Smart Brain deployments until a later released framework/runtime contract explicitly supports them.

The canonical composition is:

```text
customer-owned product intent
  + one built-in Deployment Behavior Type
  + optional published Marketplace TEMPLATE plugin version
  + installed DATA / ACTION / INFERENCE_PROFILE / planned SPECIALIST plugin versions
  + curated module and runtime profile
  + inference and vector configuration
  + channel and execution-extension configuration
  + existing deployment topology template
  + existing deployment target profile
  + managed-service bindings
  + applicable verification-pack IDs
  -> existing V04 Deployment Draft
  = immutable V04 Deployment Version with exact source references and hashes
  -> deterministic Release
  -> verified Consumer Assignment
```

The composition step is server-side resolution into the existing V04 draft. It is not a separately published or deployed object. Reuse comes from versioned Marketplace `TEMPLATE` plugins and the existing catalogues, while immutable operational truth remains the V04 Deployment Version.

Core decisions:

- `Deployment Behavior Type` is a Platform-owned execution contract, not a sellable LoomAI product. It defines activation source, coordination model, interaction posture, compatible extensions, required durability, endpoint classes, and behavioral verification.
- A customer product is owned by the Platform customer. Operationally, its deployed definition is the customer/tenant-owned V04 Deployment plus an immutable V04 Deployment Version.
- A reusable solution baseline is a published Marketplace `TEMPLATE` plugin version. It may select one allowed behavior type and contribute curated module, shell, security, prompt/schema, compatibility, required-plugin, runtime-capability, and verification references.
- Domain-specific semantics, adapters, schemas, prompts, fixtures, and UI result mappings are carried by Marketplace templates/plugins and reviewed private runtime artifacts. Shopify, ProdUS, support, incident, behavior/churn, and similar names are customer solutions or reference deployments, not Platform behavior types.
- RAG, managed vectorization, external retrieval, MCP, relationship query, behavior signals, PII, structured output, and document operations are capability packs or managed product services. They do not become products merely because they can be composed into a deployment.
- Docked composer, Max Mode, inline assistance, query-once, backend API, event adapter, scheduler adapter, and internal client are channel bindings.
- Existing Platform `DeploymentTemplateSummary` remains an infrastructure/provider/vector topology preset. It is not renamed into a product template.
- Existing `DeploymentTargetProfile` remains environment, region, provider, network, resource, and placement policy.
- Marketplace `TEMPLATE`, `DATA`, `ACTION`, and `INFERENCE_PROFILE` plugins remain current reusable packaging and compilation inputs. Add one governed `SPECIALIST` contribution for official non-executable specialist/chain resource bundles; behavior support extends the existing lifecycle rather than replacing it.
- Curated modules, runtime profiles, inference/vector profiles, managed product services, provider-resource handles, and verification suites remain their current Platform primitives.
- The existing Shopify package profile remains a Shopify-specific commercial/entitlement mapping. It may inform compatibility checks but must not become the generic customer-product model.
- Platform subscription and entitlement rules filter which templates, plugins, providers, resources, and limits a user may select. They do not define the semantic identity of the customer's product and are not trusted runtime authority.
- Provider choice is represented by a governed inference profile and deployment provider configuration, not a free-form provider overlay supplied by a browser or model.
- A channel binding selects an approved invocation and UI surface. It never grants identity, specialist, action, tenant, deployment, or provider authority.
- Platform is the control plane and assignment-discovery authority; it is not the mandatory Smart Brain data plane. After resolving an assignment, an authorized customer backend sends events directly to that deployment's advertised runtime endpoint.
- Every Smart Brain deployment is self-contained for event ingress, durable execution state, operation status/result access, cancellation, and configured outbound delivery. Platform must not proxy all event or result traffic through `api.loomai.pro` or maintain a central Smart Brain operation registry.
- V04 drafts/versions, releases, verification, promotion, assignment, export/import, and managed-service reconciliation remain the only operational path.
- The exact behavior type, Marketplace template/plugin versions, curated module, profiles, managed-service bindings, and verification-pack IDs are recorded in V04 version provenance and export/import. No live catalogue lookup is required on the runtime request path.
- One customer product may have development, staging, and production deployments. Existing export/import, target-scoped rewrite, verification, promotion, and assignment flows move the composition between environments; no parallel customer-product release engine is added.
- Shopify Companion and ProdUS remain hosted reference solution deployments of the Conversational Assistant behavior.
- `deployment-knowledge-specialist@1` is an important internal hosted reference and security canary. It is not the first new customer product because it is already implemented and because operator knowledge is not the strongest general market wedge.
- The first generic Marketplace solution template must prove the Conversational Assistant behavior independently of Shopify. The next two behavior-aware templates exercise Agentic Specialist Team and Smart Brain.
- Grounded Knowledge is a reusable capability/solution composition for any behavior that needs approved evidence. Document Intelligence Workbench is a managed knowledge-operations application feeding those behaviors, not a fourth deployment behavior type.
- AI Fabric supports bounded specialist systems, not unrestricted autonomous multi-agent products.

## 3. Judgment Of The Framework Proposal

### 3.1 Adopt directly

| Proposal idea | Judgment | Consolidated LoomAI use |
| --- | --- | --- |
| LoomAI is authoring, deployment, operations, and lifecycle; AI Fabric is runtime primitives | Correct | Preserve as the primary framework/product boundary |
| Product capability and deployment topology are different concerns | Correct | The V04 draft composes behavior/capability inputs while existing Deployment Template and Target Profile keep topology and placement ownership |
| Exact specialist versions and typed schemas | Correct | Package immutable manifests and schemas in compiled deployment versions |
| Provider selection must not change authority | Correct | Inference profiles may change models, budgets, and endpoints but never scopes/actions/vector authority |
| Channels must not supply trusted identity or authority | Correct | Channel bindings are invocation/UI adapters only |
| Governed writes remain proposals until confirmation/review | Correct | Use application authorization, durable receipt, execution, and reconciliation |
| Durable reads, receipts, review, and restart proof | Correct | Include only when a product has a concrete durability requirement |
| Security and verification are part of every template | Correct | Marketplace `TEMPLATE` compatibility plus V04 validation selects the applicable verification packs and release gates |
| No unrestricted agents, arbitrary tools, model-owned auth, or mutable specialist versions | Correct | Keep as explicit product boundaries |
| Claude may be a provider without becoming the security model | Correct | Provider-neutral inference profiles include Anthropic where verified |

### 3.2 Adopt with LoomAI adaptation

| Proposal idea | Required adaptation |
| --- | --- |
| `Product Blueprint` | Do not create a separate aggregate. Resolve the user's choices directly into the existing V04 draft and record exact composition provenance in the immutable V04 version |
| `Deployment Profile` | Split into existing LoomAI concepts: runtime profile, vector profile, deployment topology template, target profile, storage requirements, and managed product services |
| `Provider Overlay` | Implement through Platform-managed inference profiles, provider configuration, secret references, and provider verification; do not create an ungoverned overlay document |
| `Channel Overlay` | Implement as a bounded Channel Binding to backend API, docked composer, Max Mode, inline assistant, query-once, trusted event adapter, internal Java client, or a future remote MCP channel |
| Template package directory | Express reusable baselines through existing published Marketplace `TEMPLATE` plugin versions and V04 export/import; arbitrary scripts/SQL remain prohibited |
| Conversational authoring | Keep as a future client experience over deterministic Platform validation and draft APIs; the assistant may propose but never grant, approve, or silently deploy |
| Template catalogue | Reuse the existing Marketplace `TEMPLATE` lifecycle. A template selects one built-in behavior type and composes existing plugins/profiles; it cannot register an arbitrary new behavior type |
| MCP as connectivity | Treat MCP as one governed connectivity plane, not the only integration standard and not the authority model |

### 3.3 Correct factual or maturity mismatches

| Framework proposal statement | Current LoomAI reality |
| --- | --- |
| Outbound MCP is only partially available | LoomAI already has a standalone managed MCP Execution Gateway, Marketplace discovery/import, schema hashes and drift checks, config-driven `mcp-tool` compilation, generic non-Shopify execution, Shopify host delegation, managed lifecycle, and release-gate coverage |
| The deployment-knowledge specialist should be P0 implementation proof | Its 0.5.2 hosted, tenant/deployment-isolated proof is already complete. Keep it as a regression fixture and internal product reference |
| Start by defining another template package shape | LoomAI already has Marketplace `TEMPLATE` plugins, deployment templates, target profiles, curated modules, V04 versions, releases, export/import, and verification. Extend those exact contracts with behavior-aware metadata and compatibility instead of adding another catalogue |
| Claude Code authoring is close to an end-to-end capability | Claude Code can consume MCP, but LoomAI does not yet expose an inbound authoring/deployment MCP server. That remains a separate future product track |
| Direct AI Fabric Spring AI MCP execution is suitable after general hardening | AI Fabric `0.7.0` retains the strict declared-`serverRef` binding and bounded-result correction. LoomAI's managed Gateway remains the hosted production boundary; direct executor adoption still requires a LoomAI integration and security canary, not another framework fix |
| All proposed templates are product catalogue peers | Templates are reusable customer-solution starters. Conversational, Agentic Specialist Team, and Smart Brain are deployment behavior types; RAG, MCP, documents, UI, privacy, and similar functions are capabilities, services, extensions, or channels |

### 3.4 Defer or reject

- Do not prioritize an inbound LoomAI authoring/deployment MCP server ahead of behavior-aware V04 compilation and the first reusable Conversational Assistant Marketplace template.
- Do not make Claude Code a required channel for any product.
- Do not call Claude, MCP, or any provider the standard security boundary.
- Do not expose generic `deploy`, `execute_any_action`, `invoke_any_specialist`, arbitrary URL, raw SQL, shell, or unrestricted provider tools.
- Do not allow combinatorial overlay selection in merchant, partner, or customer UI.
- Do not package arbitrary database migrations or executable scripts inside customer-authored templates or deployment configuration.
- Do not create a product for personalized UI planning until a real customer workflow and allowlisted component catalogue justify it.
- Do not market fraud, compliance, risk, refund, or other high-impact decisions as automatic model authority.

## 4. Current Capability Reality

Maturity labels used throughout this document:

- `AI_FABRIC_AVAILABLE`: shipped public `0.7.0` contract with code/test evidence.
- `LOOMAI_HOSTED_PROVEN`: deployed LoomAI path has passed hosted verification.
- `LOOMAI_IMPLEMENTED`: code and local/live evidence exist, but reusable product packaging or broad production proof may still be incomplete.
- `PRODUCTIZATION_REQUIRED`: supporting pieces exist, but the managed customer product is not complete.
- `FUTURE`: proposal only; no current end-to-end LoomAI product contract.
- `BLOCKED`: required contract or gate is absent or failing.

### 4.1 AI Fabric 0.7.0 foundation

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
| Direct specialists, conversation managers, and fixed plans | `ai-fabric-execution` exact versions, schemas, trusted context, manager, plans, waits, delegation/handoff | Input waits and legacy manager/fixed-plan state are process-local; no dynamic registry or hot reload | `AI_FABRIC_AVAILABLE` |
| Bounded multi-specialist chains | `ai-fabric-execution` closed manager target catalogue, official `ai.fabric/v1` `SpecialistChain` YAML/JSON resources, bounded JSON input mapping/result projection, shared Java/manifest registry and gateway, sequential/independent-parallel worker selection, synthesis/handoff, JDBC checkpoints and replay | Read-only leaf workers; no cycles, recursion, nested chains, dynamic discovery, write workers, executable YAML, provider selection, or exactly-once provider invocation | `AI_FABRIC_AVAILABLE`; LoomAI product adoption is disabled pending separate schema and canary gates |
| Declarative chain authoring and readback | `SpecialistChainManifestValidator`, authoring catalogue, source-aware registration, runtime status and aggregate hashes | Platform must validate against the selected runtime catalogue/ceilings and package immutable resources; runtime status is not a product catalogue | `AI_FABRIC_AVAILABLE`; Platform binding is `PRODUCTIZATION_REQUIRED` |
| Durable proactive read execution | `ai-fabric-execution` JDBC read jobs and optional bounded chain execution for trusted application/event/scheduled sources | Exact read-only specialist or approved bounded chain; at-least-once provider execution; no event/scheduled write or open-ended graph | `AI_FABRIC_AVAILABLE` |
| Governed receipts and human review | `ai-fabric-execution` governed action proposal receipts, `ACTION_PROPOSAL` review tasks/delivery, replay, reconciliation contracts | Application owns reviewer authorization, transaction, system-of-record reconciliation, and migrations; arbitrary result review is not included | `AI_FABRIC_AVAILABLE` |
| PII | `ai-fabric-pii` | Product defines legal/policy posture, encryption, retention, and claims | `AI_FABRIC_AVAILABLE` |
| Governance | `ai-fabric-governance` | Does not replace product-specific authorization or compliance review | `AI_FABRIC_AVAILABLE` |
| Relationship query | `ai-fabric-relationship-query` | Requires approved domain schema and object authorization | `AI_FABRIC_AVAILABLE` |
| Behavior insights | `ai-fabric-behavior` | Signals are inference, not fact or automatic action authority | `AI_FABRIC_AVAILABLE` |
| Migration/backfill | `ai-fabric-migration-core` | Product owns source truth, checkpoints, and operator workflow | `AI_FABRIC_AVAILABLE` |
| Spring AI-managed MCP client execution | `ai-fabric-actions-connector` | Exact declared-server name/title binding and bounded result size are enforced; each LoomAI product still owns auth and live integration proof | `AI_FABRIC_AVAILABLE` |
| Anthropic generation | Spring AI provider integration | Anthropic is generation-only in the current AI Fabric provider path; RAG needs a separate embedding provider | `AI_FABRIC_AVAILABLE` |

### 4.2 Current LoomAI Platform foundation

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
| Agentic Specialist Team behavior | Exact specialist runtime is hosted; AI Fabric provides bounded chain contracts, but LoomAI keeps chains disabled until generic authoring, durable configuration, packaging, and hosted deployment verification exist | `PRODUCTIZATION_REQUIRED` |
| Smart Brain behavior | Framework contracts and executable demos exist; managed triggers, durable worker operations, output routing, and Platform deployment proof are not yet complete | `PRODUCTIZATION_REQUIRED` |
| Embedded UI | Max widget, docked composer concept, inline/result-card patterns, backend-mediated chat | `PRODUCTIZATION_REQUIRED` as a stable reusable package |
| Deployment Behavior Type | No built-in behavior field/contract or behavior-aware V04 validation yet | `FUTURE` until implemented |
| Behavior-aware Marketplace template composition | Existing `TEMPLATE` bootstrap/compilation exists, but does not yet declare behavior, required plugins/runtime capabilities, or verification packs | `PRODUCTIZATION_REQUIRED` |
| Specialist Marketplace composition | AI Fabric now supplies official validated specialist-chain resources; Platform has no `SPECIALIST` contribution type, deployment artifact materialization, or V04/readback binding yet | `PRODUCTIZATION_REQUIRED` |
| Inbound authoring MCP server | Existing Gateway is outbound execution, not a developer-facing Platform authoring server | `FUTURE` |

### 4.3 Current source and hosted-fleet evidence baseline

- Current framework release target: immutable Maven Central `0.7.0`, tag `ai-fabric-framework-v0.7.0`, release commit `5b075b66384dc5b756b3b3dd12efaf896ce9a50b`, documentation commit `7ac32985`.
- Private commit `2ee86b7761aa4f0d81224cc4da30a5e2b4c7a264` targets `0.7.0`, keeps chains disabled, passes the product, runtime/services, and Platform backend Maven reactors, and passes an isolated-cache Maven Central runtime build. The packaged runtime contains `ai-fabric-execution-0.7.0.jar` and no `0.6.1` framework JAR.
- Gate A hosted fleet evidence now matches that source identity. All supported staging and production runtime families were published and applied through verified V04 releases; live readback reports `0.7.0` with specialist chains disabled.
- The prior `0.6.1` rollout remains immutable historical evidence only. It is no longer the active supported fleet baseline.
- ProdUS assignment resolves to `dep-f6abfa06`; corrected managed projection reindex completed `198/198`, and the strict grounded canary returned the expected service-module evidence.
- Canonical suites passed in staging (`vsr-8df8715d`) and production (`vsr-da3641f3`); Partner and Thinker suites passed in staging (`vsr-dd47edb2`, `vsr-cbb44501`) and production (`vsr-19240cf6`, `vsr-bc98e4d9`).
- Full aggregate runs `vsr-8e8306d3` and `vsr-c9bb743e` passed stages 1-8 and remain failed only at the owner-deferred Shopify first-product answer-quality stage. They must not be relabeled green.

These facts prove the shared foundation. They do not prove the planned Agentic Specialist Team or Smart Brain Platform deployment types, nor every future template/plugin composition.

## 5. Canonical LoomAI Vocabulary And Sources Of Truth

| Concept | Meaning | Canonical owner/source |
| --- | --- | --- |
| Customer AI Product | The customer's domain product or capability built on LoomAI | Customer/Platform user; never the LoomAI infrastructure catalogue |
| Deployment Behavior Type | Built-in execution posture: `CONVERSATIONAL`, `AGENTIC_SPECIALIST_TEAM`, or `SMART_BRAIN` | Server-owned Platform enum/contract plus V04 deployment/version field |
| Marketplace Solution Template | Reusable starting composition for a customer product | Existing published Marketplace `TEMPLATE` plugin version |
| Marketplace Capability Plugin | Reusable data, action, inference, or specialist-execution contribution | Published and installed `DATA`, `ACTION`, `INFERENCE_PROFILE`, or planned `SPECIALIST` plugin version |
| Execution Extension | Optional governed behavior such as Resolver or Human Review | V04 configuration plus released AI Fabric/application contracts |
| Deployment Composition | The deterministic server-side act of resolving selected existing primitives into a V04 draft | Existing create/bootstrap/install/compiler/validation services; not a new persisted aggregate |
| Composition Provenance | Exact behavior contract, template/plugin versions and hashes, curated module, profiles, managed services, and verification IDs used to build a version | Immutable V04 Deployment Version/compiled manifest/export bundle |
| Platform Entitlement | Which templates, plugins, providers, resources, quotas, and support levels an account may use | Existing package/subscription/Marketplace entitlement services; not customer product identity or runtime authority |
| Curated Runtime Behavior | Modes, prompt preset, curated pack, and shell behavior | Existing curated module catalogue and compiled deployment config |
| Specialist | Exact `name@version` bounded AI execution contract | Immutable AI Fabric resource in a reviewed private source artifact or published `SPECIALIST` deployment bundle |
| Specialist Chain | Exact bounded manager/worker topology over a closed target catalogue | Official `ai.fabric/v1` `SpecialistChain` resource, or reviewed Java definition only where declarative mapping cannot represent an application invariant |
| Runtime Profile | Managed runtime behavior, cost, and limit posture | Existing Platform deployment/provider configuration |
| Inference Profile | Provider, model, endpoint, token, timeout, cost, fallback, and verification posture | Existing Marketplace `INFERENCE_PROFILE` and Platform provider config |
| Vector Profile | Strategy, provider, dimensions, storage posture, namespace/isolation, and reindex policy | Existing Platform vector plan/provider config and managed service bindings |
| Deployment Template | Runtime/connector/provider/vector topology preset | Existing Platform deployment template catalogue (`DeploymentTemplateSummary`) |
| Target Profile | Hosting provider, environment, region, credentials, network, resources, and placement | Existing `DeploymentTargetProfile` |
| Channel Binding | Approved invocation and UI surface | V04 configuration and runtime endpoint inventory |
| Integration Adapter Pack | Optional deployment-local transport bridge around canonical Smart Brain ingress/result contracts | Future governed Marketplace `INTEGRATION` plugin; built-in HTTPS requires no new plugin type |
| Verification Pack | Deterministic, provider, security, behavior, UI, and deployment checks | Existing Platform verification suites/registries and release records |
| Managed Product Service | Restartable shared/private infrastructure such as MCP Gateway or Shopify Bridge | Existing Platform Product Services |
| V04 Deployment Draft | Mutable customer/tenant-owned composition workspace | Existing Platform deployment draft lifecycle |
| V04 Deployment Version | Immutable customer deployment definition and runtime desired state | Existing Platform deployment version/compiler repository |
| Release | Deterministic application of one deployment version to one target | Existing Platform release lifecycle |
| Consumer Assignment | Backend-only consumer routing to one verified deployment | Existing consumer assignment service |

Source-of-truth rules:

1. Each deployment version records exactly one built-in Deployment Behavior Type and behavior-contract version.
2. A Marketplace `TEMPLATE` may choose one allowed behavior type; it cannot create arbitrary behavior semantics or widen authority.
3. Published and installed Marketplace plugin versions are reusable capability truth. Marketplace discovery remains draft evidence.
4. The composition step references existing plugin bodies, profiles, target credentials, secret references, and managed services; it does not duplicate them into another catalogue.
5. Entitlements restrict selectable infrastructure and capabilities. They do not define the customer's product or grant runtime authority.
6. V04 Deployment Versions are immutable desired-state and customer-product deployment truth.
7. Release and verification records are operational truth.
8. Consumer assignment is routing truth.
9. The customer application remains identity, domain, authorization, transaction, and system-of-record truth.

## 6. Consolidated Composition Model

### 6.1 Customer-product deployment flow

```text
customer creates a deployment directly or from a published Marketplace TEMPLATE
  -> select/derive one built-in Deployment Behavior Type
  -> select existing Deployment Template and Curated Module
  -> install compatible DATA / ACTION / INFERENCE_PROFILE / SPECIALIST plugins
  -> select compatible runtime, inference, vector, channel, target, and managed-service options
  -> existing Marketplace compiler writes contributions into the active V04 draft
  -> existing draft validation checks behavior and cross-primitive compatibility
  -> preview semantic diff and infrastructure requirements
  -> publish immutable V04 Deployment Version with composition provenance
  -> existing release applies it and provisions managed dependencies
  -> existing post-apply and behavior-specific verification runs
  -> existing assignment or promotion exposes the verified deployment
```

No LLM controls these state transitions. No `Product Profile` publish/release path is introduced.

### 6.2 Deployment Behavior Type contract

Formalize a small server-owned contract such as `loomai-deployment-behavior-v1`. It is a closed Platform contract and a V04 field, not a Marketplace plugin type or database-authored workflow language.

```yaml
schemaVersion: loomai-deployment-behavior-v1
type: SMART_BRAIN
contractVersion: 1
activation:
  allowedSources: [application, event, scheduled]
  publicInteractiveInput: false
integration:
  assignmentDiscovery: platform-control-plane
  dataPlane: deployment-local
  canonicalEventFormat: cloudevents-1.0-structured-json
  mandatoryIngressTransport: https
  mandatoryResultTransport: direct-polling
  optionalResultTransports: [signed-https-webhook]
  requiredRuntimeEndpointClasses: [trigger-submit, operation-status-result, operation-cancel]
  platformDataPlaneProxyRequired: false
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
  deliveryOwner: deployment
  destinationSource: compiled-deployment-config
requiredRuntimeCapabilities: [ai-fabric-execution]
allowedExecutionExtensions: []
baselineVerificationPackIds: [smart-brain-behavior-v1]
```

The other built-in contracts use the same shape:

- `CONVERSATIONAL` allows authenticated interactive activation and one bounded orchestration turn with optional backend-owned session state.
- `AGENTIC_SPECIALIST_TEAM` allows authenticated interactive/application activation, exact-version manager/workers, bounded input waits, and declared read-only coordination.

### 6.3 Reuse through existing Marketplace templates and plugins

Extend the existing `TEMPLATE` manifest contribution rather than add `BehaviorTemplate`, `ProductProfile`, or `SolutionPack` tables. The current template path already owns bootstrap into a `DeploymentTemplateSummary`, curated module selection, shell baseline, security baseline, install provenance, and subsequent Marketplace compilation.

Illustrative extension:

```yaml
pluginType: TEMPLATE
version: 1.0.0
contributions:
  template:
    templateId: custom-start-from-scratch
    deploymentBehavior:
      type: CONVERSATIONAL
      contractVersion: 1
    curatedModuleId: knowledge-assistant
    requiredPluginVersions:
      - pluginId: knowledge-runtime-base
        version: 1.0.0
    recommendedPluginVersions:
      - pluginId: external-document-retrieval
        version: 1.0.0
    requiredRuntimeCapabilities:
      - ai-fabric-rag
      - ai-fabric-indexing
      - ai-fabric-data-sync
    verificationPackIds:
      - conversational-baseline-v1
      - grounded-knowledge-v1
    shell: {}
    security: {}
```

Rules:

- A template references only published exact plugin versions or server-resolved compatible ranges that are pinned before V04 publication.
- `TEMPLATE` supplies defaults and compatibility requirements. The existing deployment draft remains the editable customer-owned object.
- `DATA`, `ACTION`, `INFERENCE_PROFILE`, and planned `SPECIALIST` contributions keep their compilation ownership; do not copy their manifests into the template.
- A private customer or partner can publish a tenant-scoped template through the same review/version model when that capability is implemented.
- Official capability codes remain descriptive metadata and verification claims; they do not replace concrete plugin/config references.
- Platform may present safe labels such as `Balanced` or `High quality`, but resolves exact provider and infrastructure values server-side.

AI Fabric `0.7.0` makes specialist-team topology a proper declarative resource, so LoomAI should add one governed `SPECIALIST` contribution type to the existing Marketplace lifecycle. It packages reviewed exact specialist resources, official `SpecialistChain` resources, referenced schemas/prompts and hashes, required capabilities/migrations/secret names, narrowed ceilings, compatible behavior types, and verification references. It contains no executable code, migration SQL, secret values, provider selection, or runtime-supplied topology.

Publication validates the bundle with `SpecialistChainManifestValidator` against the exact selected source-artifact authoring catalogue and Platform ceilings. V04 publication freezes the resolved resource, catalogue, source-artifact, and effective hashes. Apply materializes one read-only content-addressed deployment artifact and the runtime loads it through AI Fabric's existing manifest loader, registry, and gateway. This extends Marketplace/V04; it does not add a chain catalogue or deployment lifecycle.

The first one-worker framework mechanics canary remains a private source artifact and is not a Marketplace product claim. `SPECIALIST` becomes Platform-selectable only after deployment-artifact materialization, runtime status/hash readback, export/import, and hosted verification are implemented.

### 6.4 V04 composition provenance

Add composition provenance to the existing V04 Deployment Version/compiled manifest rather than persisting a second version object:

```yaml
compositionProvenance:
  deploymentBehavior:
    type: CONVERSATIONAL
    contractVersion: 1
  sourceTemplate:
    pluginId: grounded-company-knowledge
    pluginVersion: 1.0.0
    contentHash: sha256:...
  installedPlugins:
    - pluginId: knowledge-runtime-base
      pluginVersion: 1.0.0
      contentHash: sha256:...
  curatedModuleId: knowledge-assistant
  deploymentTemplateId: custom-start-from-scratch
  runtimeProfile: runtime-managed
  inferenceProfileRef: mkp-inference-openai@1.0.0
  vectorProfileRef: managed-milvus-default
  managedServiceBindingIds: []
  verificationPackIds: [conversational-baseline-v1, grounded-knowledge-v1]
```

The normal V04 config sections continue to carry executable desired state. Provenance explains where it came from and makes validation, export/import, diff, rollback, and audit deterministic. It contains references and hashes, never secrets, arbitrary scripts, SQL, executable expressions, or customer-authored migrations.

### 6.5 Compatibility resolution

Platform must validate at least:

- exactly one supported behavior type and contract version;
- the source `TEMPLATE` plugin is published and its selected behavior matches the draft;
- activation source, coordination mode, channel, extension, durability, and endpoint requirements are compatible with that behavior;
- Smart Brain durable jobs and bounded chain workers are read-only in the current released contract; fixed plans are not represented as durable work;
- account entitlements permit requested templates, plugins, providers, resources, quotas, and support posture without granting runtime authority;
- curated module and prompt/schema artifacts exist;
- one compatible inference profile is active;
- embedding dimensions match vector profile and provider;
- required vector spaces exist in V04 entity config or explicit document-space config;
- required Marketplace plugins are published, compatible, entitled, installed, and pinned;
- exact specialists, official declarative chain resources or reviewed Java definitions, schemas, actions, and endpoint classes exist in runtime capability inventory;
- declarative chain resources validate against the exact source-artifact authoring catalogue and Platform-owned ceilings;
- requested actions exist, have access modes, and are allowed by deployment policy;
- selected Deployment Template can host required runtime, connector, databases, workers, and managed services;
- Target Profile allows the required service classes;
- required secrets exist by reference; and
- selected verification packs cover every claimed behavior and capability.

## 7. Existing Platform Composition Path

### 7.1 Built-in Deployment Behavior Type registry

Maintain these as reviewed server-owned contracts exposed through a read-only catalogue API, not CRUD-managed product records:

| Behavior code | Customer-visible behavior | Allowed activation | Coordination contract | Current evidence and status |
| --- | --- | --- | --- | --- |
| `CONVERSATIONAL` | User asks; the deployment answers or requests a governed next step | Authenticated interactive backend request | One bounded orchestration turn with optional backend-owned conversation state | Shopify and ProdUS prove the behavior; generic Marketplace template proof remains required |
| `AGENTIC_SPECIALIST_TEAM` | A bounded manager coordinates approved specialists for a larger user/application task | Authenticated interactive or trusted application request | Exact-version read-only workers through an official declarative closed chain, reviewed Java definition where necessary, or fixed plan | AI Fabric `0.7.0` proves the framework contract; reusable LoomAI deployment proof remains required |
| `SMART_BRAIN` | The deployment analyzes trusted system facts without waiting for chat input | Trusted `APPLICATION`, `EVENT`, or `SCHEDULED` activation | Exact read-only specialist, approved bounded read-only chain, or process-local fixed read-only plan | Framework demos prove primitives; LoomAI ingress, durability, delivery, operations, and hosted verification remain required |

Behavior rules:

- Marketplace templates select a supported behavior; they do not define new execution semantics.
- `CONVERSATIONAL` continues the query-driven behavior used by Shopify and ProdUS.
- `AGENTIC_SPECIALIST_TEAM` is bounded agentic behavior, never unrestricted autonomous multi-agent execution.
- `SMART_BRAIN` is proactive but not self-authoring. Trusted deployment configuration maps events/schedules to exact specialists or plans.
- A durable queue means persisted jobs, bounded leases, recovery, status, cancellation, replay, and typed terminal results, not an endlessly self-directed agent.
- Durable single-specialist jobs, bounded chains, and process-local fixed plans remain separate claims.

### 7.2 Extend the existing compiler path

Deliver the behavior-aware composition through the current services and lifecycle:

- extend `MarketplaceManifestService` validation for behavior, required-plugin, runtime-capability, and verification references on `TEMPLATE` contributions;
- extend `MarketplaceTemplateBootstrapService` to write the selected behavior and install exact required plugin versions into the new deployment's active draft;
- keep `DeploymentMarketplaceDraftCompilerService` as the owner that compiles installed `TEMPLATE`, `DATA`, `ACTION`, `INFERENCE_PROFILE`, and planned `SPECIALIST` contributions;
- validate `SPECIALIST` resources offline and materialize their exact content-addressed deployment bundle through the existing V04 apply path;
- extend `DeploymentDraftValidationService` with cross-primitive behavior compatibility checks;
- extend `DeploymentConfigCompiler` to emit behavior config and immutable composition provenance;
- retain existing V04 publish, release, post-apply verification, assignment, promotion, rollback, and export/import flows;
- extend existing audit events to record who selected the template/behavior and which exact versions were compiled; and
- keep Shopify-specific package entitlements and installation behavior in the Shopify boundary.

Do not add `behavior_product_template`, `product_profile`, or parallel release tables. Add only the minimal fields/JSON needed on existing deployment draft/version records and Marketplace template manifests.

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

The existing Platform deployment compiler and operations UI must distinguish these source modes and their maturity explicitly:

- push Data Sync for customer-owned records: framework and hosted LoomAI foundation available;
- external documents-only retrieval where data remains in the customer connector: framework contract available;
- durable document ingestion for approved uploaded/connected files: application pattern available, managed LoomAI service still requires productization;
- migration/backfill for existing records: framework contract available, product operator workflow required;
- relationship query for approved relational questions: framework capability available, approved Marketplace template/private adapter required;
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
- Official `ai.fabric/v1` `SpecialistChain` resources for ordinary bounded JSON mapping and projection.
- Typed Java/JSON Schema input and output.
- Startup validation against registered modes, vector spaces, actions, and ceilings.
- Immutable artifact or mounted configuration.
- New deployment version for semantic changes.
- Read-only specialist first.
- Add input waits, fixed plans, bounded chains, durable jobs, writes, or review only for a concrete product requirement.

AI Fabric `0.7.0` deliberately does not provide a dynamic specialist database or safe hot reload. Platform authoring therefore compiles reviewed specialist and chain resources into a new deployment version; it does not mutate the running registry. Resolved prompts, input/output schemas, source/effective hashes, exact ordered targets, and catalogue identity participate in protected definition identity, so semantic changes require a new version and cannot resume protected work under the old hash.

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
- Two-tenant and two-deployment canaries for retrieval-capable deployments.

### 7.8 Privacy and governance

- Per-deployment PII direction and mode.
- Approved safe metadata destinations.
- Conversation/source/receipt/review retention.
- Deletion and derived-vector cleanup.
- Encryption/fingerprint secret requirements.
- Audit events without prompts, completions, raw PII, secrets, or hidden connector context.
- Product claim review for policy, compliance, risk, refunds, or other sensitive outcomes.

### 7.9 Observability and quality

Expose:

- deployment behavior type/contract version, source Marketplace template/plugin versions and hashes, and V04 config hash;
- activation source, coordination mode, extension set, durability posture, and current conversation/job/plan state as applicable;
- deployment version, release, target, source commit, and AI Fabric version;
- specialist version/content hash;
- provider and embedding readiness without secrets;
- retrieval counts and safe evidence references;
- indexing work state and convergence;
- MCP server/tool/schema drift posture;
- action proposal/confirmation/receipt/reconciliation state;
- verification-pack IDs/versions and latest results;
- stage latency, provider failure, and bounded diagnostics.

## 8. Supported Deployment Behaviors And Customer Solution Examples

The Platform catalogue exposes **how a deployment behaves** and the reusable parts available to compose it. It does not define the customer's final product. Shopify, ProdUS, support, churn, documents, MCP, and knowledge are customer solutions or reusable template/plugin compositions over the deployment behavior types below.

### 8.1 Catalogue hierarchy

| Layer | Business meaning | Examples |
| --- | --- | --- |
| Customer AI Product | The customer's domain experience and business outcome | A merchant companion, project intelligence assistant, support resolver, or churn-analysis product |
| Deployment Behavior Type | Platform-supported activation and coordination posture | Conversational Assistant, Agentic Specialist Team, Smart Brain |
| Marketplace Solution Template | Reusable deployment starter for a domain or use pattern | Grounded company knowledge, support resolution, churn intelligence |
| Execution Extension | Optional governed behavior attached to a compatible deployment | Resolver, Human Review |
| Marketplace Capability Plugin | Reusable technical or AI contribution | RAG data, Data Sync sources, MCP/actions, inference profiles |
| Channel Binding | How an authorized caller activates or experiences the behavior | Backend API, docked composer, Max Mode, inline panel, deployment-local event endpoint, scheduler |
| Integration Adapter Pack | Optional transport bridge around the canonical deployment endpoint/result contract | Kafka, SQS/EventBridge, Service Bus/Event Grid, Pub/Sub, NATS, vendor webhook |
| Deployment/Operations | How LoomAI hosts, verifies, assigns, promotes, and supports it | V04 version/release, target profile, managed vector/provider services, verification pack |

Deployment Behavior Types are Platform capabilities and creation choices, not finished products. Platform users may create many differently named customer products from the same behavior type and the same underlying infrastructure primitives.

Behavior codes such as `SMART_BRAIN` are Platform execution contracts. Marketplace template/plugin IDs identify reusable composition inputs. Official provider capability codes such as `loomai_structured_outputs` remain descriptive capability declarations; none of these namespaces replaces another.

### 8.2 Behavior Type A: Conversational Assistant

Customer-product value:

- Ask LoomAI in the product and receive a grounded answer, structured result, clarification, or governed next step using approved application context.

Core official capability bindings:

- required: `loomai_runtime_orchestration`;
- optional by deployment: `loomai_conversation_session_memory`, `loomai_grounded_rag_answers`, `loomai_read_action_grounding`, `loomai_structured_outputs`, `loomai_embedded_assistant_ui`, and compatible governed extensions.

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
- Generic behavior-aware Marketplace `TEMPLATE` plus V04 composition proof: `PRODUCTIZATION_REQUIRED`.
- Shopify public App Store/commercial readiness remains separately governed by 010.18.

### 8.3 Behavior Type B: Agentic Specialist Team

Customer-product value:

- Give a larger user or application task to a governed team of focused specialists while LoomAI keeps one bounded conversation and returns a typed, explainable result.

Core official capability bindings:

- required: `loomai_runtime_orchestration`, `loomai_thinker_resolver_workflows`, `loomai_structured_outputs`;
- optional by deployment: `loomai_conversation_session_memory`, `loomai_grounded_rag_answers`, `loomai_query_once_analysis`, `loomai_read_action_grounding`, and compatible governed extensions.

Activation and behavior:

```text
authenticated user or application request
  -> exact-version bounded chain manager or fixed coordinator
  -> COMPLETE, ASK_USER, one worker, independent parallel group,
     approved second worker, synthesis, or terminal handoff
  -> typed projections, structural attribution, and safe public result
```

Supported AI Fabric `0.7.0` composition:

- exact `name@version` specialists and typed schemas;
- backend-owned conversation manager with a closed target catalogue;
- official immutable `ai.fabric/v1` `SpecialistChain` YAML/JSON resources;
- bounded `JSON_POINTER_MAP` input mapping from `CHAIN_INPUT` or `MANAGER_OBJECTIVE` and `BOUNDED_FACT_PROJECTION` result projection;
- one shared Java/manifest registry and gateway, with source-aware runtime status and aggregate hashes;
- reviewed Java input mappers/projectors only when a real application invariant exceeds the declarative contract;
- bounded sequential and explicitly independent parallel worker use;
- optional JDBC checkpoints, leases, recovery, cancellation, retention, and exact replay;
- bounded input waits;
- fixed sequential and opt-in `ALL_REQUIRED` parallel read-only plans;
- one-level closed delegation or handoff;
- required grounding and safe evidence references;
- structurally attributed synthesis and deterministic application aggregation.

Behavior boundaries:

- no model-generated graph, arbitrary specialist discovery, recursion, unrestricted tools, or authority expansion;
- no nested chain, executable manifest expression, manifest-selected provider/model, or runtime-submitted topology;
- fixed plan state remains process-local and cannot be sold as durable chain state;
- chain workers and composed plans are read-only; writes continue through governed proposal/confirmation/review state machines outside the chain;
- durable chain mode requires an application-owned database migration and stable private encryption/fingerprint secrets;
- the application owns identity, specialist selection boundary, domain validation, and public projection.

Maturity:

- Framework contracts and executable Agentic Action Resolver proof: `AI_FABRIC_AVAILABLE`.
- Exact LoomAI specialist deployment and tenant/deployment isolation: `LOOMAI_HOSTED_PROVEN` for `deployment-knowledge-specialist@1`.
- Generic `SPECIALIST` Marketplace contribution and V04 deployment path: `PRODUCTIZATION_REQUIRED`; the `0.7.0` Gate A rollout keeps chains disabled, Gate B adds schema only, Gate C proves one-worker mechanics, and Gate D waits for a genuinely distinct second worker before any reusable team claim.

### 8.4 Behavior Type C: Smart Brain

Customer-product value:

- Let LoomAI observe trusted product events or scheduled work and proactively produce bounded analysis, recommendations, classifications, or alerts without waiting for a user chat message.

Core official capability bindings:

- required: `loomai_runtime_orchestration`, `loomai_thinker_resolver_workflows`, `loomai_structured_outputs`, `loomai_observability_evaluation`;
- optional by solution: `loomai_behavior_intelligence`, `loomai_semantic_relationship_query`, `loomai_grounded_rag_answers`, `loomai_read_action_grounding`, and `loomai_privacy_pii_controls`.

Activation and behavior:

```text
trusted application event / schedule / internal API / durable job submission
  -> resolve/cache consumer runtime assignment through Platform control plane
  -> call the assigned deployment's Smart Brain endpoint directly
  -> deployment maps facts to exact specialist and trusted execution context
  -> read-only analysis over approved current state, evidence, or read actions
  -> typed terminal result
  -> deployment persists, returns, notifies, or hands the result to a separate application-owned read-result review workflow
```

Deployment-local integration contract:

- Platform assignment discovery publishes the active deployment ID, runtime base URL, assignment revision/TTL, trusted-backend assertion contract, and Smart Brain endpoint templates.
- The customer backend caches that assignment and sends event/application traffic directly to the selected runtime. `api.loomai.pro` is not a required proxy for event submissions or results.
- Each deployment exposes its own equivalent of `POST {runtimeBaseUrl}/api/smart-brain/triggers/{triggerCode}`, `GET {runtimeBaseUrl}/api/smart-brain/operations/{operationId}`, and `DELETE {runtimeBaseUrl}/api/smart-brain/operations/{operationId}`. Exact routes become immutable only when the runtime contract is implemented and released.
- Submission returns a deployment-local operation handle and absolute status/result URL. Identical redelivery uses the same idempotency key; changed facts under that key fail with a visible conflict.
- Each deployment owns its durable job database, workers, leases, recovery, result store, delivery outbox, callback retry/dead-letter state, and operational endpoints.
- Polling reads the result directly from the deployment. Optional callbacks/notifications are sent directly by that deployment to a destination compiled into the immutable V04 deployment configuration; an event body cannot provide or override the destination.
- A customer-owned broker/outbox adapter posts to the assigned deployment. A LoomAI-managed broker or scheduler adapter, when offered, is deployed with or alongside that deployment and does not route every customer's traffic through the central Platform backend.
- New work follows the latest assignment. Accepted work remains pinned to its original deployment and operation URL; promotion keeps the previous deployment available long enough to exhaust assignment-cache grace, drain non-terminal jobs, and finish outbound deliveries before decommissioning it.

Technology baseline and adapter model:

- The mandatory universal ingress is HTTPS using CloudEvents `1.0` structured JSON (`Content-Type: application/cloudevents+json`) and a versioned trigger-specific `data` schema. Use the official CloudEvents Java SDK for parsing/serialization instead of a custom envelope parser.
- The deployment exposes the ingress and operation APIs through Spring Boot controllers and publishes their exact OpenAPI contract through runtime-assignment endpoint metadata.
- The runtime authenticates the existing trusted-backend API key/private assertion, validates the CloudEvent and trigger schema, derives payload-checked idempotency from the trusted source, event ID, trigger contract version, and canonical facts, then submits an AI Fabric JDBC durable read job before returning `202 Accepted`.
- PostgreSQL/JDBC is the deployment-local durability boundary for accepted operations, AI Fabric execution state, and outbound-delivery records. No central LoomAI broker or operation database is required.
- Direct polling is the mandatory result transport. Signed HTTPS webhook delivery is the standard optional push transport, backed by a deployment-local transactional outbox, bounded retry/backoff, terminal failed/dead-letter state, and operator retry. The existing runtime action-webhook worker is a reusable implementation pattern but is currently action-specific; Smart Brain needs a generic typed-result delivery contract rather than copied special-case code.
- A CloudEvents terminal-result envelope is used for webhook delivery. Its destination, signing secret reference, timeout, retry, and retention policy come from compiled deployment configuration, never from the triggering event.
- Customer schedulers, outboxes, and services need no LoomAI adapter when they can send HTTPS CloudEvents. LoomAI-managed schedules use Quartz through `spring-boot-starter-quartz` with a PostgreSQL JDBC job store inside that deployment; each firing creates the same canonical activation request and idempotency contract as HTTP ingress rather than a second execution path.
- Kafka, RabbitMQ, AWS SQS/EventBridge, Azure Service Bus/Event Grid, Google Pub/Sub, NATS, and vendor-webhook support are optional transport adapters. Each adapter runs inside or alongside the target deployment and converts only between its transport and the same canonical CloudEvents ingress/result contracts.
- Domain event types, JSON schemas, and deterministic trigger-to-specialist mappings belong to the source Marketplace template/private runtime artifact and immutable V04 configuration. Transport adapters are domain-neutral and cannot select identity, tenant, deployment, specialist, provider, scopes, or output authority.
- The current Marketplace supports only `TEMPLATE`, `ACTION`, `DATA`, and `INFERENCE_PROFILE`; do not misclassify event adapters as `DATA` or `ACTION`. When the first non-HTTP adapter is productized, add a governed `INTEGRATION` plugin type with exact `EVENT_SOURCE_ADAPTER` and `RESULT_SINK_ADAPTER` contributions, allowlisted implementation references, config schemas, secret references, topology requirements, and verification packs.

Supported AI Fabric `0.7.0` composition:

- `APPLICATION`, `EVENT`, and `SCHEDULED` service/system execution sources;
- durable read jobs persisted before dispatch;
- bounded leases, startup recovery, scoped status/cancel/replay, encrypted request/result state, and typed terminal snapshots;
- stable idempotency binding for duplicate event delivery;
- optional bounded multi-specialist read-only chains with exact-version targets, typed projections, JDBC checkpoints, and structural attribution;
- process-local fixed sequential/parallel read-only plans for a single running invocation;
- behavior-event and relationship analysis capability packs where selected.

Behavior boundaries:

- the customer application or deployment-local managed LoomAI adapter owns the event broker/scheduler integration; immutable deployment configuration owns trigger mapping and output destinations;
- Platform owns assignment, release, verification, and endpoint publication but is not the centralized Smart Brain event/result transport;
- durable execution is at-least-once read execution, not exactly-once provider invocation;
- durable single-specialist jobs, bounded durable chains, and process-local fixed plans are separate supported postures;
- no open-ended graph, cycle, recursive worker transition, fabricated user message, automatic write, event-triggered write, or self-selected ongoing objective;
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

It may extend compatible Conversational Assistant or Agentic Specialist Team deployments. AI Fabric `0.7.0` does not support write-capable chain workers or event/scheduled durable write jobs, so it must not turn Smart Brain into an automatic mutation engine.

#### Human Review

Adds a durable version-bound review task, safe dispatch evidence, separately
authenticated reviewer context, and policy-bound approve, reject, correct,
request-information, or escalate decisions. It is an Execution Extension, not
a Deployment Behavior Type, chat mode, or customer product.

Primary official capability bindings: `loomai_safety_governance`,
`loomai_governed_action_execution`, and `loomai_structured_outputs` for the
reviewed action proposal and safe outcome.

The LoomAI Platform team owns implementing, verifying, and publishing the
versioned extension through the existing V04 lifecycle. The customer opts it
into a compatible deployment and remains authoritative for reviewer identity,
role/scope policy, separation of duty, domain authorization, and the system of
record. The model may propose and explain but cannot choose the review policy,
reviewer, dispatcher, recipient, escalation target, decision, or execution
authority.

The released AI Fabric `0.7.0` durable review source is exactly
`ACTION_PROPOSAL`. The framework supplies `ReviewDecisionGateway`, immutable
review policies, safe task views, JDBC task/dispatch repositories, optimistic
decisions, exact replay, recovery, expiry, and governed continuation. LoomAI
must supply the application-owned migrations, stable distinct secrets,
policy/authorizer/dispatcher beans, trusted reviewer context, safe
deployment-local APIs, optional managed Review Inbox, and operational proof.

The first supported profile is a Conversational deployment that creates one
grounded governed action proposal. The application selects the immutable
review policy, persists the task before dispatch, authenticates the reviewer,
and submits the decision through the framework gateway. Approval never invokes
an action handler directly: it delegates to `ActionProposalCoordinator`, which
revalidates current authority, schema, protected parameters, and preflight
before one known outcome. Rejection and expiry cannot mutate. Correction
creates a schema-bound successor; information and escalation use registered
schemas/handlers/policies.

Agentic reuse comes only after the Specialist Team is hosted-proven; workers
remain read-only and can only hand a proposal to the application-owned action
and review boundary. Smart Brain may later feed a separate application-owned
read-result review workflow, but its event job remains read-only and cannot
call Resolver automatically. Arbitrary answers, analyses, chain results, or
Smart Brain results must not be disguised as `ACTION_PROPOSAL`; reusable
general result review requires a released framework source contract or an
explicitly versioned and separately verified application workflow.

Governed Resolver and `ACTION_PROPOSAL` review primitives are
`AI_FABRIC_AVAILABLE`; each deployed solution requires its own application
action/reviewer contracts and hosted verification before external readiness
claims. General read-result review is not currently a framework-proven
capability.

### 8.6 Customer solution templates and reference deployments

| Customer solution/template/reference | Primary behavior | Included value | Current posture |
| --- | --- | --- | --- |
| Shopify Commerce Companion | Conversational Assistant | Grounded product discovery, storefront UI, commerce read tools, Knowledge Sync, optional governed commerce actions | Hosted controlled reference; public launch blockers remain in 010.18 |
| ProdUS Project Intelligence | Conversational Assistant | Project/service knowledge, grounded analysis, query-once and backend-mediated runtime integration | Hosted customer/reference deployment, not a separate deployment behavior type |
| Grounded Company Knowledge | Conversational first; optional Specialist Team or Smart Brain | Approved records or customer-owned retrieval, citations, no-evidence behavior, source status | Retrieval foundation hosted; reusable Marketplace template needs productization |
| Account And Support Resolution | Conversational or Specialist Team plus optional Resolver/Review | Diagnose account state, ask for missing typed input, recommend or propose bounded resolution | Framework/reference-app proven; reusable customer-solution template needs hosted proof |
| Deployment And Incident Intelligence | Agentic Specialist Team or Smart Brain | Read approved deployment/log/tool evidence and produce bounded diagnosis | Exact deployment specialist hosted; generic incident pack needs productization |
| Behavior And Churn Intelligence | Smart Brain | Convert product events into persisted churn, sentiment, trend, recommendation, and alert outputs | Live AI Fabric demo evidence; LoomAI V04 composition and operations proof required |
| Relationship/CRM Intelligence | Conversational Assistant or Smart Brain | Bounded questions and analysis over approved entity relationships | AI Fabric capability available; reusable template/plugin composition requires productization |
| MCP Operations | Any compatible behavior | Use reviewed external tools through exact server/tool/schema/auth bindings | Managed Gateway infrastructure hosted; every tool pack requires live proof |
| Document Knowledge Operations | Feeds any behavior needing durable knowledge | Upload/connect, preview, approve, parse, chunk, index, reindex, delete, and source status | Application pattern only; managed LoomAI service remains productization work |

A Platform user owns the customer product name and domain promise. Reusing a solution template does not create a new Platform product or a new deployment lifecycle.

### 8.7 Capability and channel classification

| Customer solution pattern | Canonical Platform composition |
| --- | --- |
| Grounded Knowledge Assistant | Conversational behavior plus a Grounded Company Knowledge `TEMPLATE` and compatible `DATA`/inference plugins |
| Document Intelligence Workbench | Managed knowledge-operations application/capability service |
| Embedded Assistant Experience | UI channel pack shared by compatible deployment behavior types |
| Bounded Investigation Specialist | Agentic Specialist Team solution configuration |
| Governed Resolution Assistant | Resolver execution extension plus domain template/actions |
| MCP Operations Assistant | MCP/action plugins attached to a compatible deployment behavior |
| Relationship And Behavior Intelligence | Smart Brain or Conversational template using relationship/behavior capabilities |
| Private Enterprise Copilot Factory | Future guided authoring over existing templates, plugins, drafts, versions, and releases |

Grounding, vectorization, external retrieval, MCP, actions, privacy, relationship query, behavior analysis, structured output, sessions, and UI rendering remain important Platform capabilities. Customers use them to build products; LoomAI should not present each module or behavior type as the customer's product identity.

### 8.8 Readiness and external claims

| Deployment behavior type | Supported Platform claim now | Claim requiring additional productization |
| --- | --- | --- |
| Conversational Assistant | LoomAI has hosted, verified conversational deployments and can deliver managed scoped deployments | Generic reusable Marketplace template, behavior-aware V04 provenance, and guided creation flow |
| Agentic Specialist Team | AI Fabric `0.7.0` supports validated declarative bounded multi-specialist chains; LoomAI has hosted exact-specialist security proof | A ready reusable team template requires `SPECIALIST`/V04 packaging plus hosted manager/worker, persistence, replay, failure, and promotion canaries |
| Smart Brain | AI Fabric `0.7.0` supports trusted proactive read execution, durable jobs, and declarative bounded read-only chains; executable behavior demos exist | A ready LoomAI template requires managed trigger binding, durable store/worker or chain, output routing, monitoring, recovery, and hosted release proof |

External copy must say `governed`, `bounded`, `approved`, or `configured` where relevant. Do not claim unrestricted agents, continuous self-directed goals, arbitrary analysis, automatic high-impact decisions, exactly-once model calls, or open-ended/dynamic/recursive workflows. Claim bounded durable multi-specialist execution only after the exact V04 deployment composition passes hosted durability and security gates.

### 8.9 Customer AI Product Factory

Outcome:

- Let Platform users launch isolated, branded products by selecting a supported behavior and approved Marketplace templates/plugins, channels, profiles, and targets through the existing managed lifecycle.

This is guided composition over proven Platform primitives, not a blank autonomous-agent builder. Shared deployment infrastructure is `LOOMAI_HOSTED_PROVEN`; the generic self-service factory remains `FUTURE` until at least two behavior-aware Marketplace templates have hosted V04 deployment proof and support operations.

## 9. Runtime Composition Postures

These are compiler requirements selected by behavior type plus template/plugin composition. They are not customer-visible products or free-form toggles.

| Runtime posture | Primary behavior use | AI Fabric composition | Required durable state | Current caveat |
| --- | --- | --- | --- | --- |
| Read-only grounded | Any behavior | RAG + generation + vector/retrieval provider | Vector/source lifecycle as selected | Evidence must be scoped, sanitized, and quality-tested |
| Interactive conversation | Conversational Assistant | Ordinary orchestration + chat session | Configured session storage | Backend owns conversation identity, history, pending work, and reset |
| Interactive specialist | Agentic Specialist Team | Read specialist + conversation manager + typed wait | Chat session as selected | Input waits and manager state are process-local; avoid restart-survival claims |
| Bounded specialist chain | Agentic Specialist Team or Smart Brain | Official declarative `SpecialistChain` or reviewed Java definition + closed exact-version read-only worker catalogue + manager directives + bounded mapping/projection | Optional `ai_specialist_chain_execution`, stable encryption/fingerprint secrets, leases and retention | No recursion, nesting, cycles, dynamic targets, executable YAML, write workers, or exactly-once provider claim |
| Governed write | Conversational Assistant or Agentic Specialist Team extension | Specialist/action proposal + confirmation | `ai_action_proposal_receipt`, stable secrets, application transaction | Side effect remains application-owned |
| Human review | Conversational governed action first; Agentic only after hosted proof | `ACTION_PROPOSAL` review task + safe dispatch + governed receipt | `ai_review_task`, `ai_review_dispatch`, action receipt storage, stable review secrets | Reviewer identity/authorization are customer-application-owned; arbitrary read-result review is a separate future contract |
| Durable event read | Smart Brain | Deployment-local event/schedule endpoint + exact specialist job or bounded specialist chain | `ai_specialist_execution` or `ai_specialist_chain_execution`, stable encryption/fingerprint secrets, local result/delivery outbox | Direct deployment data plane; at-least-once read execution, not exactly-once provider calls or event writes |
| Process-local fixed plan | Agentic Specialist Team or Smart Brain | Fixed sequential/parallel read-only plan | Bounded in-process checkpoints | Exact-version, deterministic topology; not a durable graph |
| Live data RAG | Any retrieval-enabled behavior | Data Sync + indexing worker + vector provider | Indexing queue/work plus vector storage | Query work status; do not infer completion from vector presence |
| Tenant-isolated SaaS | Any multi-tenant behavior | Trusted auth + scoped retrieval/actions + canaries | Customer identity source and scoped data | Missing boundary must fail before retrieval/action |

Platform should combine only postures permitted by the selected behavior type and installed template/plugins. It should not expose every cross-capability combination as a toggle matrix.

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

- Existing deployment creation, Marketplace compilation, V04 validation, and Platform UI/API must work without Claude Code.
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

### 11.2 Direct framework MCP executor in AI Fabric 0.7.0

AI Fabric `0.7.0` retains the required MCP boundary correction:

- a declared `serverRef` must match the exact remote server name or title;
- no exact match fails closed instead of searching unrelated clients exposing
  the same tool name;
- oversized results are rejected before projection or model context; and
- nested backend-owned read parameters are resolved before required-parameter
  validation.

Decision:

- LoomAI production continues to use the managed Gateway because that is the
  currently hosted and operationally governed execution plane.
- Direct Spring AI-managed MCP execution is no longer framework-blocked by
  ambiguous server selection, but each adopting V04 deployment still needs
  exact-server, duplicate-tool-name, auth, result-size, outage, and tenant
  security canaries.
- Do not retain or add a private workaround for the pre-`0.6.1` fallback.

This correction expands a possible future deployment-local MCP posture; it
does not require replacing the managed Gateway.

### 11.3 Future inbound LoomAI authoring MCP server

This is a new product service, not an extension of the existing outbound Gateway.

Purpose:

- expose stable, bounded Platform authoring and operations tools to approved developer clients;
- never expose raw framework internals or unrestricted operational authority.

Read-only first tools:

```text
platform_get_solution_template_catalog
platform_get_authoring_catalog
platform_validate_deployment_draft
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
platform_compile_marketplace_installs
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
- direct mutation of an active specialist or immutable V04 deployment version.

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
  -> assistant proposes Marketplace template/plugin bindings, specialist manifest, and schemas
  -> local tests and server-side validators run
  -> Platform creates a draft and semantic preview
  -> developer/operator reviews capability, infrastructure, cost, security, and verification requirements
  -> authenticated approval creates/releases immutable version
  -> exact deployment UUID is polled to terminal state
  -> post-apply verification and assignment occur
```

Assistant may:

- propose source-controlled Marketplace manifests, specialist manifests, and schemas;
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
- rewrite immutable deployment/plugin/specialist versions;
- switch consumer assignment before post-apply evidence passes.

Platform UI and ordinary APIs remain first-class authoring channels. The MCP authoring channel must reuse them rather than becoming a second control plane.

## 13. Security And Governance Requirements

1. Identity, subject, customer, tenant, deployment, caller, issuer, audience, and scopes come from authenticated backend context.
2. Deployment behavior, Marketplace templates/plugins, V04 configuration, and specialists request or narrow capability; they cannot grant it.
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
16. Semantic template/plugin, deployment, or specialist changes create new immutable versions and hashes.
17. Rollback stops new invocations while preserving unresolved durable work for authorized recovery.
18. High-impact decisions remain human/application authority.
19. Public event payloads contain domain facts only. They cannot select identity, tenant, deployment, specialist, scopes, provider, vector space, action, or output authority.
20. Smart Brain event/scheduled work and specialist-chain workers remain read-only in AI Fabric `0.7.0`; process-local fixed plans cannot be represented as durable chain state.
21. Declarative chain resources use only the official `ai.fabric/v1` contract, pass offline and startup validation, and cannot carry executable code, authority, provider selection, secrets, or dynamic targets.
21. Smart Brain customer traffic goes directly to the assigned deployment endpoint after control-plane assignment discovery. Platform must not become a mandatory proxy, centralized job store, or result relay.
22. Callback destinations, signing secrets, retry policy, and retention compile into deployment-owned configuration; the request body cannot supply them.
23. Promotion preserves accepted operation URLs and keeps the old deployment available until assignment-cache grace, jobs, and outbound deliveries are drained.

## 14. Verification Architecture

Every Deployment Behavior Type contract has baseline verification-pack IDs. Each immutable V04 Deployment Version records those packs plus the checks required by its exact templates/plugins, extensions, channels, providers, topology, and target.

| Gate | Required proof |
| --- | --- |
| Behavior static validation | Exact built-in behavior type/contract version, activation sources, coordination mode, extension compatibility, durability, and unsupported-claim policy |
| Draft composition validation | Exactly one behavior type; published exact template/plugin versions; official capability codes; entitlement/runtime/vector/channel/topology compatibility |
| Framework dependency | Central-only build resolves exactly AI Fabric `0.7.0` and intended optional modules |
| Compilation | Marketplace packs, curated behavior, V04 entities, actions, vector spaces, specialists, schemas, and secrets-by-ref compile |
| Unit | Adapters, validators, projectors, parameter/result policy, and failure projection |
| Integration | Only modules used by the deployment: retrieval, sessions, actions, receipts, review, behavior, relationship, indexing, storage |
| Packaged runtime | Container starts with exact source/composition/framework identity and production-like configuration |
| Provider | Real configured generation and embedding calls where claimed; failure visible |
| Knowledge | Create/update/delete/reindex, work-status lifecycle, counts, revision hash, expected evidence |
| RAG quality | Golden queries, expected sources, no-evidence correctness, citations, forbidden claims |
| Security | Cross-principal, subject, tenant, deployment, scope, evidence, action, receipt, and review denial |
| Session | Owner-scoped history/pending work, reset, expiry, replay, cross-owner denial |
| Agentic team | Exact manager/workers, no-worker/clarification/one-worker/sequential/independent-parallel/synthesis/handoff bounds, typed projections, independent worker authority, replay/restart where durable, fixed-plan mappings, and deterministic aggregation |
| Smart Brain activation | Assignment advertises deployment-local OpenAPI endpoints; CloudEvents structured JSON and trigger-data schema validation; direct event/application/scheduled mapping; no Platform data-plane proxy; no fabricated chat; service identity; read-only enforcement; output destination authorization |
| Smart Brain durability | Deployment-local PostgreSQL/JDBC persist-before-dispatch, duplicate replay, changed-facts conflict, lease recovery, restart, direct status/result/cancel, encryption, retention, typed terminal result, signed-webhook outbox retry/dead letter |
| Integration adapter | Exact allowlisted implementation/version, canonical CloudEvents parity, deployment-local topology, secret isolation, broker redelivery/idempotency, outage/backpressure, result-sink delivery, and no authority widening |
| Specialist | Exact version/hash, typed I/O, capability intersection, grounding, waits/jobs as selected |
| MCP | Exact server/tool binding, live auth, schema drift, arguments, output sanitation, unavailable server, tool failure |
| Write/review | Dry-run, confirmation/rejection, immutable receipt, restart, idempotent replay, reconciliation |
| Privacy | Input/output PII mode, storage, logging, retention, deletion, derived evidence cleanup |
| UI | Docked, Max, inline, query-once, cards, denial/failure, desktop/mobile/accessibility |
| Deployment | Exact deployment UUID terminal result, health, post-apply verification, target scope, assignment |
| Export/promotion | V04 version/composition provenance, target-scoped rewrite, import preview, rollback |
| Operations | Monitoring, alert, support, incident, backup/export, offboarding, owner runbooks |
| Commercial | Entitlements, quotas, billing, listing/claims, privacy and support match live behavior |

Behavior and pack proof examples:

- Conversational Assistant: an authenticated user query returns an answer, clarification, structured result, or governed next step without leaking raw internal envelopes.
- Agentic Specialist Team: the manager stays inside a closed exact-version target catalogue and may complete, ask once, invoke one worker, invoke an explicitly independent group, choose an approved second worker, synthesize, or terminate through one allowed handoff; durable claims require exact replay/restart proof.
- Smart Brain: assignment discovery is used only to locate/authenticate the runtime; a trusted event and its result travel directly between customer and deployment, create no chat turn, survive restart where promised, replay identical delivery, reject changed facts, and cannot write.
- Grounded Company Knowledge pack: a known evidence ID must be top-k and appear in generated answer citations; update/delete changes evidence only after work reaches successful terminal state.
- Resolver extension: confirmation survives restart where promised, executes once, and reconciles.
- MCP pack: discovered schema hash matches installed binding or execution fails before `tools/call`.
- Personalized output pack: every returned component ID exists in the allowlist; no executable code appears.

## 15. Delivery Sequence

### P-1: AI Fabric 0.7.0 Gate A base adoption - COMPLETE

1. Upgrade every private framework dependency, generated deployment default, and active assertion to `0.7.0`.
2. Keep `AI_EXECUTION_SPECIALIST_CHAINS_ENABLED=false` and add no chain resource, table, secret, or route.
3. Run all three private Maven reactors without skipped tests.
4. Resolve and package from an isolated Maven Central cache; prove the runtime contains only `0.7.0` AI Fabric JARs.
5. Commit and deploy one immutable private revision through the existing V04 release path.
6. Verify version/health, direct deployment-knowledge behavior, missing-boundary denial, two-tenant/two-deployment isolation, and all existing capability regressions.
7. Stop and record Gate A evidence before Gate B schema work or Gate C chain mechanics.

Exit:

- current behavior is preserved on hosted `0.7.0` with chains disabled;
- the source target and hosted fleet report the same immutable framework/private identities;
- no chain persistence or secret is required by the base release.

Completion record:

- immutable private source: `2ee86b7761aa4f0d81224cc4da30a5e2b4c7a264`;
- seven supported runtime-family releases reached `APPLIED_VERIFIED` with live `0.7.0` readback and chains disabled;
- direct specialist, missing-boundary, two-tenant/two-deployment isolation, and ProdUS grounded retrieval passed;
- canonical staging and production suites passed;
- aggregate suites retain the separately owned Shopify answer-quality failure;
- no Gate B migration, Gate C resource/secret/route, or Gate D product claim was introduced.

### P0: Behavior-aware existing deployment spine and Conversational baseline

1. Preserve Shopify Companion's passed stages and close the deferred
   answer-quality defect before describing the full production release gate as
   green.
2. Define the closed `loomai-deployment-behavior-v1` contract and add its type/version to existing V04 draft/version state.
3. Register `CONVERSATIONAL`, `AGENTIC_SPECIALIST_TEAM`, and `SMART_BRAIN` as server-owned activation/coordination contracts.
4. Add a governed Marketplace `SPECIALIST` contribution for immutable non-executable AI Fabric specialist/chain resource bundles.
5. Extend the existing Marketplace `TEMPLATE` contribution with behavior, required-plugin, runtime-capability, and verification references.
6. Bootstrap and compile one generic Conversational Marketplace template through the existing Marketplace install and V04 draft/version/release lifecycle.
7. Add semantic preview showing behavior, source template/plugin versions, extensions, channels, durability, topology, and boundaries.
8. Use Shopify and ProdUS as regression references, not as the generic behavior schema.

Exit:

- one generic Conversational template can bootstrap, compile, stage, verify, promote, assign, export/import, and roll back without manual runtime environment editing;
- Platform UI clearly distinguishes customer product name, deployment behavior, Marketplace template/plugins, channels, and deployment topology.

### P1: Agentic Specialist Team

1. Treat completed P-1 Gate A as the immutable baseline; do not alter its chain-disabled release records.
2. Complete Gate B: add the application-owned JDBC chain migration while chains remain disabled, then prove normal startup/redeploy and migration idempotency.
3. Complete Gate C: package one reviewed one-worker `ai.fabric/v1` `SpecialistChain` mechanics canary over `deployment-knowledge-specialist@1`, validate it with the runtime authoring catalogue/ceilings, configure two distinct stable private encryption/fingerprint secrets, and expose only deployment-local safe operations/readback.
4. Prove direct-specialist parity, one-worker execution, trusted-context denial, exact replay, changed-payload conflict, restart recovery, cancellation/deadline, definition-drift rejection, provider failure, and runtime source/effective hashes. Do not call this a multi-specialist product.
5. Define a genuinely distinct second read-only specialist responsibility before Gate D. Do not invent a duplicate worker for a demo.
6. Publish the manager, exact workers, schemas/prompts, and official declarative chain through a reviewed `SPECIALIST` version; use Java chain code only for a documented application invariant that the declarative contract cannot express.
7. Compile the exact `SPECIALIST` version and content-addressed resource bundle into V04, then prove no-worker, clarification, sequential, explicitly independent parallel, invented-target denial, required-branch failure, synthesis attribution, terminal handoff, current-authorization recovery, retention, and bounded structured correction.
8. Keep one fixed sequential/parallel plan only where deterministic static topology is the better behavior contract; do not present it as chain state.
9. Deploy one hosted Agentic Specialist Team V04 composition and repeat two-tenant/two-deployment, missing-boundary, provider-failure, rollback-with-chains-off, export/import, and promotion canaries.

Exit:

- the reusable team template is hosted-proven without dynamic topology, arbitrary specialist selection, shared hidden conversations, recursive workers, or write-capable chains/plans;
- durable chain limits and process-local fixed-plan limits are separately visible in product claims and operations.

### P2: Smart Brain

1. Define OpenAPI contracts for deployment-local submit, status/result, and cancellation endpoints. Standardize `APPLICATION`, `EVENT`, and `SCHEDULED` ingress on CloudEvents `1.0` structured JSON with versioned trigger-specific data schemas.
2. Extend consumer runtime assignment metadata with the Smart Brain endpoint templates and required trusted-backend/private-assertion posture; keep Platform out of the event and result data path.
3. Map each trigger inside immutable deployment configuration to one exact read-only specialist, trusted subject/tenant/deployment context, scopes, and output contract.
4. Provision each deployment's durable execution storage, stable encryption/fingerprint secrets, workers, leases, recovery, status, cancellation, replay, retention, and cleanup.
5. Add deployment-owned result storage, mandatory direct polling, and a transactional delivery outbox for signed CloudEvents HTTPS callbacks/notifications and authorized human-review routing without automatic mutation.
6. Define promotion draining: new assignments receive new submissions, accepted operation URLs remain valid on their original runtime, and the previous deployment remains available through assignment-cache grace and terminal delivery.
7. Prove direct operation with Platform unavailable after assignment resolution, duplicate delivery, changed-facts conflict, restart recovery, lease expiry, provider failure, cross-tenant denial, callback retry/dead letter, promotion draining, and terminal result projection.
8. After the durable single-specialist path is green, choose deliberately between a process-local fixed analysis plan and a bounded durable read-only specialist chain; do not describe either as an open-ended graph.
9. If a bounded chain is selected, reuse the reviewed chain migration/secrets and run the full chain matrix under event/scheduled trusted context before enabling that trigger.
10. Publish and prove one Behavior/Churn or Incident Intelligence Marketplace template on the Smart Brain behavior.

Exit:

- one event/scheduled V04 deployment accepts and returns work directly through its deployment URL without a Platform data-plane hop, runs without a chat turn, survives restart where promised, exposes one typed terminal result, and passes release/promotion/operations gates;
- no event or scheduled path can propose or execute a write.

### P3: Governed Resolver and Human Review extensions

1. Productize Human Review first for a Conversational deployment with one
   low-risk registered governed action proposal; do not make Agentic or Smart
   Brain a prerequisite.
2. Pin the first release to AI Fabric `ReviewSourceType.ACTION_PROPOSAL` and
   configure application-owned JDBC task/dispatch migrations, stable distinct
   review secrets, one immutable policy, reviewer authorizer, and safe inbox
   dispatcher.
3. Expose deployment-local safe inbox, detail, decision, information, outcome,
   and operator-recovery contracts. LoomAI may provide a managed Review Inbox,
   while customer-owned UIs use the same API.
4. Prove approve/reject, separation of duty, tenant/deployment isolation,
   restart, exact replay, changed-decision conflict, policy/source drift,
   dispatch failure, expiry, retention, rollback, and `OUTCOME_UNKNOWN`.
5. Ensure approval delegates only to `ActionProposalCoordinator`, which
   revalidates current action authority and preflight; Review code never calls
   an action handler directly.
6. Add schema-bound correction, request-information, escalation, and external
   safe-reference dispatch only after their handlers and successor policies
   are registered and verified.
7. Bind Resolver only to compatible Conversational Assistant or later hosted-
   proven Agentic Specialist Team deployments, with durable receipt and
   system-of-record reconciliation.
8. Reuse Human Review with Agentic only after the real team passes its own
   gates; workers remain read-only and cannot select a reviewer or execute.
9. Allow Smart Brain results to create application-owned read-result review
   only through a separate authorized contract. Keep event execution read-only,
   block direct Resolver use, and never overload `ACTION_PROPOSAL`.

Exit:

- one Conversational V04 deployment has hosted-proven durable action-proposal
  review with customer-owned reviewer authority and a generic safe inbox;
- denial/expiry never executes, approval cannot replace trusted facts, retries
  do not duplicate side effects, reviewer authority is independent, and
  reconciliation is terminal;
- arbitrary answers/results are not claimed as framework-backed Human Review.

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

### P5: Additional customer solution templates

Publish Marketplace solution templates from customer evidence while reusing one of the three built-in behavior types:

- account/support resolution;
- incident/deployment intelligence;
- relationship/CRM intelligence;
- another commerce platform;
- project/architecture analysis;
- another event-driven analysis domain.

Exit:

- every solution adds only domain adapters, approved schemas, prompts, template/plugin bindings, UI cards, and quality fixtures;
- a runtime fork or new activation model requires explicit architectural review.

### P6: Provider, MCP, and operational expansion

1. Expand verified inference/vector combinations without changing behavior semantics or authority.
2. Continue using the managed MCP Gateway exact binding.
3. Add more reviewed MCP/action template and plugin compositions with per-plugin auth, drift, read/write, outage, and quality proof.
4. Raise the strict-server public framework fix only if direct Spring AI executor use is required.
5. Add quotas, cost controls, alerts, backup/restore, retention, support, and offboarding per deployed composition and entitlement.
6. After the built-in HTTPS contract is hosted-proven, add a governed Marketplace `INTEGRATION` plugin type and the first deployment-local broker adapter. Do not overload `DATA` or `ACTION`, and do not accept arbitrary adapter code/images.

Exit:

- each supported provider/connector combination passes the selected behavior and plugin verification packs and preserves visible failure.

### P7: Optional inbound authoring MCP and enterprise factory

Prerequisites:

- all three behavior contracts are stable and at least two behavior-aware Marketplace templates have reusable hosted V04 deployment proof;
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

### Workstream A: Behavior-aware use of existing Platform primitives

- server-owned Deployment Behavior Type enum/contract and read-only catalogue API;
- minimal behavior/provenance fields on existing deployment draft/version/config models;
- behavior-aware extensions to existing Marketplace `TEMPLATE` manifests and bootstrap;
- continued use of existing `DATA`, `ACTION`, and `INFERENCE_PROFILE` installs plus the planned governed `SPECIALIST` contribution under the same compiler ownership;
- official capability-code and cross-primitive compatibility validation;
- entitlement-filtered option catalogue without making entitlement product identity;
- existing deployment/Marketplace REST and UI extensions;
- existing audit and export/import extensions.

### Workstream B: Compiler integration

- resolve curated module, Marketplace packs, inference/vector/runtime profiles;
- enforce behavior activation, coordination, extension, durability, channel, and template/plugin compatibility;
- validate topology/target/service requirements;
- compile V04 draft and semantic diff;
- preserve deterministic version/release behavior;
- no runtime catalogue lookup per request.

### Workstream C: Behavior and composition verification packs

- schema and fixture storage;
- behavior baseline suites plus exact Marketplace template/plugin and V04 configuration bindings;
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

- assignment-advertised, deployment-local application/event/scheduled endpoint contracts;
- deterministic trigger-to-specialist/plan mapping;
- event-contract versions, idempotency, service identity, and scope policy;
- durable execution database, encryption/fingerprint secrets, workers, leases, recovery, status, cancellation, replay, retention, and cleanup;
- deployment-owned result store, delivery outbox, and persisted insight/callback/notification/review output sinks;
- assignment-cache grace, old-deployment draining, and accepted-operation URL continuity;
- direct-data-plane, Platform-outage, no-chat-turn, no-event-write, restart, duplicate-delivery, callback-retry, and cross-tenant canaries.

### Workstream F: Specialist, Resolver, and Human Review operations

- manifest/schema authoring;
- conversation-manager and fixed-plan authoring with exact targets and ceilings;
- exact-version deployment packaging;
- trusted adapters;
- execution status/input waits/jobs;
- exact immutable review-policy registration and `ACTION_PROPOSAL` source
  validation;
- application-owned task/dispatch migrations, stable secrets, recovery,
  expiry, retention, and draining;
- customer-authority-backed reviewer context, scopes, separation of duty, and
  current-authorization revalidation;
- deployment-local safe inbox/detail/decision/information/outcome APIs;
- optional LoomAI managed Review Inbox plus customer-owned UI and safe-
  reference dispatcher support;
- proposal/confirmation/review/receipt/reconciliation UI with no raw receipt,
  executable parameter, identity fingerprint, or handler-result exposure;
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

No framework blocker is currently confirmed for the base `0.7.0` migration.
The earlier request for an official declarative bounded-chain YAML/JSON contract
is fulfilled by `ai.fabric/v1` `SpecialistChain`, its offline validator,
source-aware registry/readback, and the shared chain gateway.
The historical `AF-MCP-STRICT-SERVER-REF` issue is resolved by the immutable
release's exact remote server name/title binding and bounded result handling.
Any new framework gap must still be reproduced against `0.7.0` before LoomAI
adds a product-side workaround.

### LoomAI Platform gaps, not framework blockers

- built-in Deployment Behavior Type contract and V04 persistence/provenance;
- behavior-aware Marketplace `TEMPLATE` manifest, bootstrap, compatibility, and verification references;
- Marketplace `SPECIALIST` contribution validation, review, immutable V04 resource materialization, provenance, export/import, and runtime hash readback;
- generic Conversational Assistant Marketplace template independent of Shopify-specific packaging;
- generic template/runtime/vector/verification compatibility beyond Shopify;
- hosted reusable bounded-chain/multi-specialist team template and V04 deployment proof;
- Smart Brain deployment-local trigger/status/result endpoints, assignment endpoint publication, durable worker operations, direct output delivery, promotion draining, and hosted deployment proof;
- reusable embedded assistant package;
- knowledge/document operator UX;
- specialist authoring/deployment UI;
- reusable behavior/plugin verification-pack registry and release binding;
- inbound authoring MCP server;
- customer-level billing/support/onboarding for the new deployment capabilities.

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

## 18. Platform Success Measures

The consolidated architecture succeeds when:

1. A Platform user creates and names the customer product, then chooses Conversational Assistant, Agentic Specialist Team, or Smart Brain as its deployment behavior.
2. The user starts directly or from an approved Marketplace `TEMPLATE`, then selects compatible plugins, extensions, channels, and runtime/infrastructure posture.
3. Platform resolves those safe choices into the existing complete, reviewable V04 draft.
4. The same template can use another approved inference profile without changing activation, coordination, authority, or customer-product semantics.
5. A Marketplace template/plugin cannot silently change behavior activation, topology, durability, identity, or authority.
6. Backend API, UI, application-event, and scheduled surfaces invoke only their declared trusted behavior.
7. A capability absent from deployment/runtime inventory cannot be invented by a model, client, template, plugin, or Marketplace discovery result.
8. Cross-user, tenant, deployment, activation-source, specialist, and output-destination canaries fail closed.
9. Knowledge changes converge and quality tests detect stale or missing evidence.
10. A Smart Brain job reaches the assigned deployment directly, survives and replays only where promised, exposes its typed terminal result from that deployment, and never fabricates a chat turn or executes a write.
11. A confirmed write executes under application authority and replays safely where durability is promised.
12. A live MCP tool is bound and governed like a registered action.
13. Provider, MCP, retrieval, indexing, policy, validation, persistence, trigger, queue, and deployment failures remain visible.
14. Behavior contract, template/plugin versions and hashes, deployment version/release, specialist hash, source commit, framework version, provider posture, and verification evidence are observable.
15. Smart Brain event and result traffic continues against a cached healthy assignment when Platform is temporarily unavailable; Platform is not a mandatory data-plane hop.
16. A second customer solution template launches on an existing behavior and a second behavior type launches without forking the private runtime or creating a new deployment workflow.

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

## 19. Definition Of A Releasable Behavior-Aware Customer Deployment

A Deployment Behavior Type is Platform-selectable only when:

- its activation sources, coordination contract, interaction posture, output contract, extensions, durability, and unsupported claims are explicit;
- every runtime behavior maps to shipped AI Fabric or an explicitly owned LoomAI/application component;
- deterministic and real-provider tests prove its normal, denial, failure, timeout, replay, restart, and isolation behavior;
- external language distinguishes bounded behavior from unrestricted autonomous agents;
- support and operations can identify the exact behavior contract version and current execution state.

A customer-owned V04 deployment built from that behavior and any Marketplace template/plugins is releasable only when:

- its customer promise and boundaries are explicit;
- it selects exactly one supported Deployment Behavior Type and only compatible extensions, templates, plugins, capabilities, and channels;
- official LoomAI capability codes are valid;
- every capability maps to shipped AI Fabric or an explicitly owned LoomAI component;
- Platform can compile, preview, deploy, verify, promote, assign, export/import, roll back, and retire it;
- the customer application retains identity, authorization, validation, transactions, and system-of-record authority;
- all promised UI, failure, denial, no-evidence, confirmation, and review states exist;
- intended live providers and integrations pass quality/security gates;
- tenant/deployment isolation and missing-boundary behavior are proved;
- event/scheduled deployments prove assignment-advertised deployment endpoints, direct trusted trigger mapping, deployment-local durable state/result delivery, promotion draining, and no-write boundaries where applicable;
- support, billing, privacy, incident, rollback, and offboarding ownership exists;
- external claims match the exact live deployment version, entitlements, and verification evidence and do not rely only on framework demos.

## 20. Immediate Work Queue

### Immediate P0

- Preserve the completed AI Fabric `0.7.0` Gate A evidence and require a separate reviewed decision before Gate B schema work or Gate C chain mechanics.
- Review and approve the vocabulary/source-of-truth model in this document.
- Define `loomai-deployment-behavior-v1` as a closed server-owned contract and V04 field.
- Register the three behavior types and their activation/coordination/durability matrices.
- Extend existing Marketplace `TEMPLATE` parsing/bootstrap with behavior, required-plugin, runtime-capability, and verification references.
- Define the governed `SPECIALIST` contribution schema and exact offline validation/materialization boundaries, without enabling a chain.
- Define composition provenance on the existing immutable V04 Deployment Version and export/import bundle.
- Add compiler preview from one generic Conversational Marketplace template into an existing V04 draft.
- Bind a reusable Conversational Assistant verification pack.
- Preserve Shopify's passed release stages, close the deferred answer-quality
  defect, and do not claim a green full production gate before its rerun passes.

### Next P1

- Deploy a generic Conversational Assistant design-partner deployment from a Grounded Company Knowledge Marketplace template.
- Build and host-canary an `AGENTIC_SPECIALIST_TEAM` deployment with one manager and at least two exact read-only specialists.
- Build and host-canary a `SMART_BRAIN` deployment with assignment-advertised deployment-local endpoints, a trusted event, durable read job, restart recovery, direct status/result access, and a deployment-owned typed output sink.
- Freeze conversational and manager contracts plus Smart Brain CloudEvents/OpenAPI ingress, direct polling, cancellation/replay, and deployment-owned signed-webhook output contracts.
- Add behavior/template/plugin selection and V04 composition-provenance views in Platform UI.

### Later gated items

- Conversational `ACTION_PROPOSAL` Human Review with one low-risk governed
  action first; Resolver remains separately selectable, and Agentic attachment
  comes only after the team is hosted-proven.
- Grounded Company Knowledge, Document Knowledge Operations, and non-Shopify MCP packs.
- Customer-facing specialist-team Marketplace templates.
- Behavior/churn and incident Smart Brain Marketplace templates.
- Read-only inbound LoomAI authoring MCP server.
- Enterprise/partner product factory.

## 21. Final Recommendation

Proceed by extending the existing deployment and Marketplace primitives with a small built-in Deployment Behavior Type contract. Do not build a parallel LoomAI product catalogue, Product Profile aggregate, version lifecycle, or release engine.

The final positioning is:

> LoomAI is a generic AI-enablement Platform. It lets customers create their own products using three governed deployment behaviors: Conversational Assistant, Agentic Specialist Team, and Smart Brain. Customers compose those behaviors with versioned Marketplace templates/plugins and existing runtime, provider, vector, topology, target, managed-service, verification, release, and assignment capabilities. AI Fabric supplies reusable runtime contracts. Each deployment is its own data plane, and the customer application remains the authority.

Treat:

- AI Fabric as the reusable runtime foundation;
- Deployment Behavior Type as a Platform activation/coordination capability, not a customer product;
- existing Marketplace `TEMPLATE`, `DATA`, `ACTION`, and `INFERENCE_PROFILE` versions as reusable composition inputs;
- planned Marketplace `SPECIALIST` versions as reviewed non-executable AI Fabric specialist/chain resource bundles inside that same lifecycle;
- existing V04 Deployment Version as the immutable customer deployment definition and composition-provenance owner;
- Shopify, ProdUS, support, churn, incident, commerce, and other domains as customer solutions, templates, or reference deployments;
- RAG, vectorization, MCP, actions, privacy, documents, relationship query, behavior signals, and UI as capabilities, managed services, extensions, or channels;
- Marketplace as reusable template and capability packaging;
- V04 version/release as immutable operational truth;
- MCP as a governed connectivity plane;
- Claude as an optional provider and future client channel;
- Platform as the deterministic generic AI-enablement control plane;
- each deployment as the self-contained runtime data plane for its chat, indexing, Smart Brain ingress, operation state/results, and outbound delivery;
- Shopify and ProdUS as current Conversational Assistant reference proofs;
- exact specialists, bounded multi-specialist chains, fixed plans, and conversation managers as the bounded foundation of Agentic Specialist Team;
- trusted event/schedule adapters plus exact durable read jobs or bounded read-only chains as the bounded foundation of Smart Brain;
- Resolver and Human Review as controlled extensions, not autonomous authority.
