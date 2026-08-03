# 010.20 AI Fabric 0.5.2-Backed LoomAI Product Portfolio And Productization Plan

Status: portfolio input created on 2026-08-01. The framework and hosted runtime foundation are proven. The shared product kernel and individual product profiles described here still require phased productization and their own release evidence. The canonical consolidated LoomAI architecture and delivery sequence is now [010.21 Consolidated LoomAI AI Enablement Product Profile And Deployment Architecture](010_21_CONSOLIDATED_LOOMAI_AI_ENABLEMENT_PRODUCT_PROFILE_AND_DEPLOYMENT_ARCHITECTURE.md); use 010.21 where the two documents overlap.

Related documents:

- [010.16 Practical Dev, Staging, And Production Deployment Model](010_16_PRACTICAL_DEV_STAGING_PRODUCTION_DEPLOYMENT_MODEL.md)
- [010.17 Grounding-Eligible Read Action Post-Action Generation And LLM Facts Plan](010_17_GROUNDING_ELIGIBLE_READ_ACTION_POST_ACTION_GENERATION_AND_LLM_FACTS_PLAN.md)
- [010.18 Shopify Companion Production Release And App Listing Readiness Plan](010_18_SHOPIFY_COMPANION_PRODUCTION_RELEASE_AND_APP_LISTING_READINESS_PLAN.md)
- [010.19 Qdrant Demotion And Managed Vector Migration Plan](010_19_QDRANT_DEMOTION_AND_MANAGED_VECTOR_MIGRATION_PLAN.md)
- [AI Fabric Platform Product Philosophy](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_PLATFORM_PRODUCT_PHILOSOPHY.md)
- [AI Fabric Framework Philosophy](../../../../../../../../Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md)
- [LoomAI Provider Capabilities User Guide](../../../../../../../../Final_Documentation/User_Guides/LOOMAI_PROVIDER_CAPABILITIES_USER_GUIDE.md)

Public framework evidence is maintained in the sibling `Loom-AI-Labs/ai-fabric-framework` repository, especially:

- `docs/llm-context/AI_FABRIC_CAPABILITY_MAP.md`
- `docs/release-notes/0.5.2.md`
- `docs/Framework-Dev-Guides/retrieval-vectorization/DATA_SYNC_PUSH_API_GUIDE.md`
- `examples/real-apps/REAL_APP_CAPABILITIES.md`

## Purpose

Turn the capabilities now available through AI Fabric 0.5.2 into a coherent LoomAI product portfolio without confusing framework primitives with finished products.

This plan answers five questions:

1. Which customer-facing products can LoomAI credibly support?
2. Which exact AI Fabric contracts support each product?
3. What must LoomAI Platform, private runtime, UI, and product packs add?
4. In what order should the products be built and released?
5. What evidence is required before a product claim is allowed?

The plan is intentionally sequential. It does not authorize several half-finished products to launch in parallel. Shopify Companion remains the current commercial reference product. New product work should first create reusable product infrastructure, then promote one profile at a time through explicit gates.

## Executive Decision

LoomAI should productize AI Fabric through a shared **LoomAI Product Kernel** and a set of opinionated, versioned **Product Profiles**.

```text
AI Fabric 0.5.2 primitives
  -> private LoomAI runtime composition
  -> Platform-compiled product profile
  -> managed deployment and assignment
  -> product-specific UI and integration pack
  -> product quality and release evidence
```

The kernel is shared. A profile selects and configures only the capabilities that its customer problem needs.

The first reusable profile after stabilizing the existing Shopify release should be a **Grounded Knowledge Assistant**, followed by the **Document Intelligence Workbench** and the read-only **Thinker / Bounded Specialist** profile. Write-capable Resolver and broad MCP products come only after read quality, identity, confirmation, replay, and audit gates are green.

AI Fabric 0.5.2 supports bounded specialist applications. It does not justify claiming unrestricted autonomous multi-agent behavior. LoomAI should market this as controlled specialist orchestration where identity, topology, evidence scope, tools, and side effects remain bounded by the application and deployment.

## Current Proven Baseline

The portfolio starts from a real baseline rather than a proposal-only architecture:

- Public AI Fabric release `ai-fabric-framework-v0.5.2` is immutable and available from Maven Central.
- Private builds resolve framework artifacts through `io.github.loom-ai-labs:ai-fabric-bom:0.5.2`; copied public framework source must not return to the private repository.
- The private runtime archive contains `ai-fabric-execution-0.5.2.jar` and the current RAG, retrieval, indexing, Data Sync, and chat-session modules.
- Hosted two-tenant and two-deployment specialist canaries passed.
- Missing assertion, tenant, deployment, or required scope fails closed.
- Cross-deployment retrieval did not return foreign evidence.
- ProdUS deployment `dep-f6abfa06`, version `ver-aaec416e`, and release `rel-3b4a8338` are live with AI Fabric 0.5.2 and managed vector retrieval.
- Final production release-gate run `vsr-e18452e5` passed all 13 blocking stages.
- Public real apps provide executable evidence for RAG, indexing, Data Sync, document ingestion, chat sessions, PII, governed actions, MCP-style tools, behavior insights, relationship queries, and bounded specialist execution.

Real apps are framework evidence, not releasable LoomAI products. They prove that a primitive works in an application shape; they do not replace managed deployment, UX, tenant operations, support, billing, or product-quality work.

## Non-Negotiable Boundaries

### Greenfield and one-way

- Build only on the current AI Fabric 0.5.2 contract.
- Do not add old-release readers, aliases, fallback request shapes, copied modules, or compatibility branches.
- Replace obsolete product flows instead of maintaining parallel control surfaces.
- A missing generic framework contract is a framework blocker, not permission to add a private imitation.

### No dummy product behavior

- No stubs, fake providers, canned production answers, or success responses that hide unavailable capability.
- Deterministic providers are valid for tests and demos only when clearly identified as such.
- A product is not ready until the intended live model, vector, connector, auth, and deployment shape are verified.

### No text-matching domain logic

- LLMs interpret user intent and produce schema-bound values.
- Application code enforces allowed values, authorization, policy, limits, and domain validation.
- Generic framework and runtime modules must not identify product intent through hardcoded phrases such as product names, entity labels, or customer-specific keywords.
- Product-specific examples belong in prompt packs, specialist manifests, tests, and vertical adapters.

### Application authority

The customer application remains authoritative for:

- authentication and principal identity;
- tenant, customer, deployment, and subject binding;
- business authorization and object-level access;
- domain validation and transactions;
- source-of-truth data;
- final write execution and reconciliation.

The browser must never supply trusted identity, tenant, deployment, scopes, provider credentials, hidden action targets, or backend assignment keys.

### Deterministic control plane

AI may help users understand, retrieve, analyze, and propose. It must not silently decide Platform rollout state, release readiness, assignment, operator approval, or deployment policy.

## Ownership Model

| Layer | Owns | Must not own |
| --- | --- | --- |
| AI Fabric public framework | Orchestration primitives, providers, embeddings, vector contracts, indexing, RAG, sessions, action contracts, PII, governance, relationship query, behavior analysis, bounded specialist execution | LoomAI hosting, customer identity, commercial product UX, Platform rollout state, customer business transactions |
| LoomAI private runtime | HTTP/runtime projection, curated modes, specialist registration, connector integration, post-action generation, runtime auth enforcement, safe diagnostics | Platform source of truth, browser-owned secrets, customer domain authority |
| LoomAI Platform | Product definitions, deployment compilation, provider and vector provisioning, secrets, assignment, rollout, verification, audit, promotion, rollback | LLM-decided rollout state, hidden fallback deployment paths |
| LoomAI product pack | Customer promise, prompt pack, capability selection, knowledge schema, action allowlist, structured output, UI surfaces, quality pack, onboarding | Generic framework reimplementation, unrestricted authority |
| Customer application or bridge | Identity, tenant mapping, domain records, authorization, validation, transactions, final side effects | Passing trusted authority from public request bodies, assuming model output is authorization |

## AI Fabric Capability Foundation

The following is the supported technical foundation for product planning.

| Foundation | AI Fabric 0.5.2 support | LoomAI product responsibility |
| --- | --- | --- |
| Runtime orchestration and generation | `ai-fabric-core`, provider modules, `AICoreService`, provider manager | Managed runtime API, curated modes, provider profiles, safe response projection |
| Embeddings and semantic search | Core embedding/vector SPIs and provider modules | Provision selected provider, dimensions, collections, namespace, isolation, lifecycle checks |
| Indexing | `ai-fabric-indexing`, `@AIProcess`, canonical projections, durable work queue, `IndexingWorkQuery` | Product knowledge schema, private status route, auth, polling, operator recovery, source adapters |
| Push data sync | `ai-fabric-data-sync` batch/upsert/delete contracts with fail-closed access checks | Consumer-facing sync facade, trusted deployment/tenant context, replay policy, sync UI |
| RAG and evidence | `ai-fabric-rag`, bounded retrieved evidence, semantic search and generated answer flows | Product prompts, source policy, citations UI, golden queries, no-evidence behavior |
| External retrieval | `ai-fabric-retrieval-connector`, documents-only connector as a `RAGProvider` | Customer connector protocol, URL policy, metadata allowlist, credentials, health and drift checks |
| Conversation state | `ai-fabric-chat-session`, owner-scoped turns and pending action primitives | Authenticated conversation API, retention, reset, cross-owner denial, UI state |
| Local and connector actions | Core action contracts, `ai-fabric-actions-connector`, `ai-fabric-actions-registry` | Product action catalog, MCP adapter, action scopes, final connector execution, operator registration flow |
| Bounded specialists | `ai-fabric-execution`, exact `name@version`, typed schemas, trusted context, fixed plans, input waits, read jobs, governed write proposals | Immutable product manifests, deployment inventory, scope grants, backend adapters, review UX, deployment lifecycle |
| Privacy | `ai-fabric-pii` detection and sanitization | Product policy, encryption key management, retention, data subject handling, privacy claims and proof |
| Governance | `ai-fabric-governance`, compliance and operational catalog primitives | Tenant policy, audit surfaces, deletion workflows, release policy, operator evidence |
| Relationship reasoning | `ai-fabric-relationship-query`, bounded natural-language relationship planning | Approved domain schema, query allowlist, authorization, result projection, query-quality pack |
| Behavior intelligence | `ai-fabric-behavior`, behavior events and insight generation | Event contract, consent, business interpretation, product metrics, approved follow-up actions |
| Migration and backfill | `ai-fabric-migration-core`, indexing migration jobs | Customer migration workflow, checkpoints, operator UX, source-of-truth reconciliation |
| Structured HTTP exposure | `ai-fabric-web` framework controllers and typed results | Private product routes, auth filters, stable external DTOs, CORS and rate limits |

`ai-fabric-execution` is opt-in and must be added explicitly. It is not a reason to replace normal chat, RAG, or deterministic application workflows.

## LoomAI Product Kernel

The Product Kernel is the reusable managed layer every product profile should compose instead of rebuilding.

### Kernel 1: Product definition contract

Create one versioned Platform-owned product definition with at least:

```yaml
schemaVersion: loomai-product-profile-v1
code: grounded-knowledge-assistant
name: Grounded Knowledge Assistant
loomaiCapabilityCodes:
  - loomai_runtime_orchestration
  - loomai_grounded_rag_answers
  - loomai_managed_vectorization
runtime:
  curatedPack: knowledge
  modes: [assistant]
knowledge:
  vectorSpaces: [document]
  ingestionModes: [push, connector]
specialists: []
actions: []
ui:
  surfaces: [docked-composer, max-mode]
security:
  requiredScopes: [vector:document]
verificationPack: grounded-knowledge-v1
```

Rules:

- `loomaiCapabilityCodes` use only official stable codes from `loomai-provider-capabilities-v1`.
- The product profile references immutable prompt packs, schemas, and specialist versions.
- Platform validates every referenced mode, vector space, action, connector, and specialist before a deployment can apply.
- Platform compiles the profile into deployment artifacts. The runtime consumes resolved artifacts; it does not query a mutable product catalogue during a request.
- A semantic profile change creates a new profile version.

### Kernel 2: Managed deployment template

Each product profile must compile into a named managed deployment template containing:

- framework version and private service source revision;
- runtime and connector services;
- required database and vector services;
- provider and embedding configuration;
- curated prompt/mode pack;
- product capability inventory;
- specialist manifests and exact versions;
- allowed actions and vector spaces;
- trusted auth assertion contract;
- secrets by reference, never plaintext;
- health, readiness, verification, and rollback policy.

### Kernel 3: Knowledge lifecycle

Provide one common operator workflow:

```text
connect or upload source
  -> preview approved projection
  -> validate required metadata and PII policy
  -> sync/index
  -> query durable indexing status
  -> reconcile counts and revision hash
  -> run product golden queries
  -> publish knowledge readiness
```

The product UI may call this `Knowledge Sync`. Raw queue, vector, replay, and provider controls remain operator/partner concerns.

### Kernel 4: Conversation and embedded UI

Maintain one shared UI integration contract for:

- always-visible docked bottom composer;
- Max Mode full assistant experience;
- inline assistant card or panel;
- query-once analysis surfaces;
- structured result cards for products, evidence, actions, and review tasks.

The browser sends the current user message, safe page context, attachment handles, and public UI preferences. The backend owns history, pending work, identity, tenant, deployment, and allowed modes.

### Kernel 5: Specialist manifest lifecycle

Platform should author and validate exact-version specialist manifests, then package them into the runtime artifact or mounted immutable configuration.

The current framework contract is startup-loaded and immutable. Do not design a fake hot-reload catalogue. Updating a specialist requires a new deployment version and verification.

### Kernel 6: Governed action plane

One action plane should cover local actions, registered connector actions, and MCP tools:

```text
model proposes a registered action
  -> capability and scope intersection
  -> application authorization
  -> parameter validation
  -> confirmation or review when required
  -> trusted connector/application execution
  -> durable receipt and reconciliation when configured
  -> generated user answer from safe result facts
```

Read-action facts should ground the final answer. Raw connector JSON may remain available to development UI diagnostics, but production user answers must be generated from safe action evidence or use a short deterministic fallback summary.

### Kernel 7: Identity and isolation

Every private product invocation must derive trusted context in backend code and prove:

- valid runtime assertion;
- expected issuer and audience;
- consumer-to-deployment assignment;
- subject and tenant binding;
- granted and requested scope intersection;
- deployment-scoped evidence isolation;
- fail-closed missing-boundary behavior.

### Kernel 8: Product quality pack

Every product profile ships with versioned evaluation data:

- golden questions and expected evidence IDs;
- expected structured outputs;
- no-evidence and provider-failure cases;
- cross-tenant and cross-deployment denial cases;
- action proposal, cancellation, confirmation, replay, and rejection cases where applicable;
- forbidden claims and unsafe-output checks;
- latency and cost budgets;
- UI rendering fixtures.

### Kernel 9: Observability and operations

Expose safe product-level signals:

- deployment/profile/framework/source identity;
- provider attempts and bounded errors;
- retrieval source counts and evidence references;
- indexing work lifecycle and dead-letter state;
- action proposal, confirmation, receipt, and reconciliation state;
- specialist identity, version, status, and bounded diagnostics;
- quality-pack version and latest result;
- request latency by orchestration stage.

Do not log prompts, secrets, full private source payloads, encrypted originals, or hidden connector context.

### Kernel 10: Promotion and release

Every profile uses the same lifecycle:

```text
development proof
  -> staging managed deployment
  -> product verification pack
  -> design-partner proof
  -> production clone/promotion
  -> assignment switch
  -> soak
  -> old deployment decommission approval
```

## Product Portfolio

Maturity labels in this plan:

- `HOSTED_PROVEN`: capability passed a hosted LoomAI production or staging proof.
- `FRAMEWORK_PROVEN`: executable framework example/test exists, but LoomAI product packaging is incomplete.
- `PRODUCTIZATION_REQUIRED`: runtime pieces exist, but managed profile, UX, quality, or commercial operations are incomplete.
- `BLOCKED`: a required real contract is missing or a gate has failed.

| Priority | Product profile | Customer outcome | Main official capability codes | Current maturity |
| --- | --- | --- | --- | --- |
| P0 | Shopify Commerce Companion | Grounded shopping, product guidance, and governed commerce assistance | `loomai_commerce_shopping_assistant`, `loomai_embedded_assistant_ui`, `loomai_read_action_grounding`, `loomai_managed_vectorization` | `HOSTED_PROVEN`, public release blockers remain |
| P1 | Grounded Knowledge Assistant | Answers from approved company knowledge with citations | `loomai_grounded_rag_answers`, `loomai_managed_vectorization`, `loomai_external_retrieval_connector` | `HOSTED_PROVEN`, reusable profile required |
| P1 | Embedded Assistant Shell | Always-available docked composer, Max Mode, inline assistance | `loomai_embedded_assistant_ui`, `loomai_conversation_session_memory`, `loomai_runtime_auth_assignment` | `PRODUCTIZATION_REQUIRED` |
| P1 | Document Intelligence Workbench | Upload, preview, chunk, index, reindex, delete, and ask over documents | `loomai_transient_document_understanding`, `loomai_contextual_attachments`, `loomai_managed_vectorization` | `FRAMEWORK_PROVEN` |
| P2 | Thinker / Bounded Specialist | Evidence-backed diagnosis using an exact approved specialist | `loomai_thinker_resolver_workflows`, `loomai_query_once_analysis`, `loomai_structured_outputs` | `HOSTED_PROVEN` for deployment knowledge; product profile required |
| P2 | Governed Resolver | Propose and execute low-risk actions after policy and confirmation | `loomai_governed_action_execution`, `loomai_safety_governance`, `loomai_structured_outputs` | `FRAMEWORK_PROVEN`, vertical proof required |
| P2 | MCP Operations Assistant | Use approved MCP tools without exposing unrestricted tool authority | `loomai_tool_mcp_orchestration`, `loomai_mcp_discovery_plugin_import`, `loomai_connector_action_catalog` | `FRAMEWORK_PROVEN`, private gateway exists |
| P3 | Account And Support Resolution | Explain account state, gather missing input, and propose bounded resolutions | `loomai_account_order_resolution_assistant`, `loomai_privacy_pii_controls`, `loomai_thinker_resolver_workflows` | `FRAMEWORK_PROVEN` |
| P3 | Relationship And Behavior Intelligence | Ask relational business questions and identify behavior/churn signals | `loomai_semantic_relationship_query`, `loomai_behavior_intelligence`, `loomai_structured_outputs` | `FRAMEWORK_PROVEN` |
| P4 | Private Enterprise Copilot Factory | Isolated, provider-flexible, white-label vertical assistants | `loomai_managed_runtime_deployment`, `loomai_runtime_auth_assignment`, `loomai_model_provider_abstraction`, `loomai_marketplace_capability_packs` | Shared hosting is proven; self-service productization required |

## Product Profile Details

### Profile A: Shopify Commerce Companion

Customer promise:

- Provide grounded product discovery and guidance inside the storefront.
- Render structured commerce results as useful UI, not raw tool envelopes.
- Add governed account/cart/order capabilities only when Shopify authorization and review requirements are satisfied.

AI Fabric support:

- core orchestration and provider abstraction;
- chat sessions and pending confirmations;
- RAG, indexing, and Data Sync;
- connector actions and post-action generation;
- bounded specialists for controlled diagnosis/resolution where useful.

LoomAI work:

- finish public billing, protected-data, listing, review, support, and onboarding blockers in `010.18`;
- keep Shopify MCP as the customer-facing capability source where Shopify exposes the required tool;
- keep Bridge as auth/session/governance/transport authority rather than reimplementing Shopify behavior;
- maintain Knowledge Sync, storefront shell, Max Mode, result cards, merchant controls, and quality audit.

Release gate:

- `010.18` controlled and public release gates are green;
- known-query product retrieval and policy answers pass;
- every enabled write is authorized, confirmed, audited, and reconciled;
- App Store claims match live capability exactly.

### Profile B: Grounded Knowledge Assistant

Customer promise:

- Answer questions from approved internal or customer-facing knowledge with bounded citations and explicit no-evidence behavior.

AI Fabric support:

- `ai-fabric-indexing`, `ai-fabric-data-sync`, `ai-fabric-rag`, `ai-fabric-retrieval-connector`, vector/provider modules;
- public durable `IndexingWorkQuery` status;
- executable evidence in `smart-faq-assistant`, `tenant-knowledge-portal`, `ai-fabric-live-data-sync`, and hosted ProdUS retrieval.

LoomAI work:

1. Create `grounded-knowledge-assistant@1` product profile.
2. Support push sync and documents-only external retrieval as separate explicit source modes.
3. Add knowledge preview, sync state, revision hash, counts, source health, and golden-query UI.
4. Add cited answer, insufficient evidence, access denied, and provider unavailable response surfaces.
5. Ship a reusable integration guide for backend assertion, assignment discovery, safe page context, and UI surfaces.

MVP exclusions:

- no writes;
- no autonomous browsing;
- no unapproved URL ingestion;
- no public tenant or deployment identifiers treated as trusted filters.

Release gate:

- create/update/delete converge;
- expected evidence appears for every golden query;
- stale content disappears after update/delete;
- cross-tenant/deployment evidence never reaches generation;
- citations contain only allowed metadata and URLs;
- provider failure remains visible.

### Profile C: Embedded Assistant Shell

Customer promise:

- Give a product one consistent assistant experience across a docked bottom composer, Max Mode, inline cards, and query-once analysis.

AI Fabric support:

- core orchestration;
- chat-session ownership and pending state;
- structured output and action result contracts;
- RAG and specialist execution behind the same backend conversation boundary.

LoomAI work:

1. Publish a stable frontend package or integration bundle for the existing Max widget surfaces.
2. Define the docked composer, Max Mode, inline panel, and result-card APIs.
3. Keep private runtime calls behind the customer backend.
4. Provide safe attachment handles and current-page context adapters.
5. Add theme tokens, accessibility, responsive behavior, loading, retry, denial, confirmation, review, and no-evidence states.

Release gate:

- no backend key is present in browser assets;
- owner-scoped history survives refresh when enabled;
- a new/reset conversation cannot inherit old pending work;
- all supported structured result types render without raw JSON leakage;
- keyboard, mobile, and screen-reader checks pass.

### Profile D: Document Intelligence Workbench

Customer promise:

- Turn approved files into manageable knowledge with preview, chunk visibility, sync status, reindex, delete, and grounded questions.

AI Fabric support:

- Spring AI document readers feeding canonical AI Fabric indexing projections;
- indexing lifecycle, Data Sync, RAG, migration/backfill, and safe metadata;
- `document-ingestion-workbench` executable proof.

LoomAI work:

1. Define trusted source adapters and supported file policies.
2. Build upload/connect, preview, approval, chunk manifest, indexing status, reindex, delete, and query UX.
3. Keep originals in approved product storage; vectors remain derived and rebuildable.
4. Add malware/content-type/size/page/count policy before parsing.
5. Use Spring AI readers where supported; add only opinionated lifecycle, policy, and product UX around them.
6. Separate transient attachments from durable knowledge ingestion.

Release gate:

- unsupported or unsafe files fail closed;
- re-upload deletes stale chunks and indexes the new revision;
- source deletion removes every derived chunk;
- required tenant/source metadata is present before indexing;
- PII policy is enforced before provider execution;
- known document questions return the expected source pages/chunks.

### Profile E: Thinker / Bounded Specialist

Customer promise:

- Run an exact approved specialist that diagnoses a bounded problem using approved evidence and returns a typed result.

AI Fabric support:

- `ai-fabric-execution` exact `name@version` identity;
- typed input/output schemas;
- trusted execution context and capability intersection;
- fixed sequential/parallel read-only plans, bounded delegation/handoff, input waits, conversation manager, and durable read jobs;
- hosted `deployment-knowledge-specialist@1` isolation proof.

LoomAI work:

1. Create a Platform specialist authoring form backed by the framework manifest schema.
2. Validate referenced modes, actions, vector spaces, schemas, and ceilings before draft publication.
3. Package immutable manifests into a new deployment version.
4. Show specialist version, required input, evidence, status, and safe diagnostics in operator UI.
5. Start with read-only specialists and exact application selection.

Release gate:

- invalid manifest fails before deployment activation;
- the public request cannot choose specialist, provider, vector space, scope, or authority;
- schema-invalid output is explicit failure;
- missing evidence produces the declared safe outcome;
- durable jobs survive restart and enforce scoped idempotency/replay;
- two-tenant/two-deployment canaries pass for each profile using retrieval.

Not supported:

- model-generated workflow graphs;
- recursive or unrestricted specialist discovery;
- arbitrary model-selected tools, endpoints, credentials, or providers;
- silent fallback to a different specialist.

### Profile F: Governed Resolver

Customer promise:

- Diagnose a bounded issue, propose one approved low-risk write, ask for confirmation or review, execute in the trusted application, and reconcile the outcome.

AI Fabric support:

- action registration and policy;
- chat-session pending confirmation;
- `ai-fabric-execution` governed write proposal, durable receipt, human review, replay, and reconciliation contracts;
- executable evidence in `agentic-ai-action-resolver`, `ai-fabric-account-resolver`, and action demos.

LoomAI work:

1. Continue the existing `006.x` sequence: read-only diagnosis, dry-run, low-risk writes, then productized rollout.
2. Define action risk classes and mandatory confirmation/review policies.
3. Bind hidden/system-owned targets from trusted backend context.
4. Build review, approval, rejection, information request, expiry, replay, and reconciliation UI.
5. Require application validation immediately before execution, even after model extraction and user confirmation.

Release gate:

- no write executes from model output alone;
- confirmation is bound to immutable specialist/profile/action facts;
- changed or expired facts fail closed;
- repeated confirmation does not duplicate the side effect;
- application system-of-record reconciliation reaches a terminal state;
- denial and provider failure remain non-executing.

### Profile G: MCP Operations Assistant

Customer promise:

- Let a product use an approved set of MCP tools while LoomAI governs discovery, parameters, access mode, confirmation, execution, and user-facing answers.

AI Fabric support:

- `ai-fabric-actions-connector` and connector action catalogs;
- DB-backed action registry when dynamic publication is a real product requirement;
- action access modes, confirmation, safe result projection, and execution specialists;
- executable evidence in `mcp-operations-assistant` and `db-action-registry-lab`.

LoomAI work:

1. Import MCP discovery into a reviewable Marketplace `ACTION` plugin draft.
2. Require operator approval before publishing tools to a deployment.
3. Classify every tool as read, write, or destructive.
4. Bind credentials and hidden context in the private gateway.
5. Feed safe read results to final generation as LLM facts.
6. Keep raw tool diagnostics available only to authorized development/operator surfaces.

Release gate:

- only the deployment allowlist is discoverable/executable;
- write/destructive tools require declared confirmation or review;
- unknown tools and connector outages are explicit failures;
- hidden connector context is absent from user output;
- tenant, target, and scope are rechecked at execution;
- no tool can grant itself additional authority.

### Profile H: Account And Support Resolution

Customer promise:

- Explain account/support state, ask for missing supported information, and resolve permitted low-risk issues without exposing private account data.

AI Fabric support:

- bounded specialists, typed input waits, RAG, relationship query, chat sessions, PII, governed actions, durable receipts, and human review;
- evidence in `ai-fabric-account-resolver`, `privacy-first-customer-facing-support`, and `it-support-action-bot`.

LoomAI work:

- define one narrow vertical first, such as subscription/account resolution;
- build trusted account adapters and action policies;
- classify support data and apply PII redaction/encryption/retention policy;
- provide customer-safe summaries and escalation paths;
- keep large refunds, irreversible changes, identity recovery, and high-risk decisions in human-owned workflows.

Release gate:

- second-user/account access tests pass;
- missing account authority fails before retrieval or action;
- required parameters are extracted into typed fields and validated;
- warn-only business validation is allowed only for explicitly user-owned values and never for trusted resource IDs or hidden targets;
- high-risk operations cannot enter automatic execution.

### Profile I: Relationship And Behavior Intelligence

Customer promise:

- Ask bounded questions over relational business data and convert behavior events into explainable product/customer signals.

AI Fabric support:

- `ai-fabric-relationship-query` natural-language planning over approved JPA relationships;
- `ai-fabric-behavior` event analysis and insight generation;
- structured outputs and governed follow-up action patterns;
- evidence in `relationship-query-crm-insights` and `behavior-churn-signals`.

LoomAI work:

1. Publish explicit domain schema and entity allowlists per vertical.
2. Define event contracts, consent, retention, and signal semantics.
3. Separate observed facts, model inference, confidence, and recommended action.
4. Add product-owner dashboards and quality sets for important business questions.
5. Keep pricing, employment, credit, health, or other high-impact decisions outside automatic action.

Release gate:

- generated queries cannot access unregistered entities or fields;
- tenant/object authorization is applied before result projection;
- signals show evidence and confidence rather than being presented as fact;
- no recommendation executes without the governed action path;
- offline and live-provider quality suites meet declared thresholds.

### Profile J: Private Enterprise Copilot Factory

Customer promise:

- Launch an isolated, branded assistant for a specific enterprise domain using managed deployment, approved providers, private knowledge, and optional governed integrations.

AI Fabric support:

- provider abstraction, all optional capability modules, bounded specialists, and stable application contracts.

LoomAI work:

1. Turn the shared kernel into guided Platform product creation.
2. Offer opinionated templates rather than an unrestricted module configurator.
3. Support tenant/deployment assignment, private assertions, managed providers, data regions where actually available, branding, domains, quality packs, promotion, and rollback.
4. Package vertical knowledge schemas, specialists, actions, UI cards, and evaluations as Marketplace capability packs.
5. Provide partner implementation workflows without exposing operator secrets or unrestricted customer access.

Release gate:

- a clean staging-to-production lift-and-shift passes with no manual runtime env editing;
- export/import preserves product intent and rewrites target-scoped handles;
- customer-specific secrets remain in Platform secret references;
- isolation and release gates pass for the target deployment;
- support, ownership, billing, retention, and incident responsibilities are explicit.

## Implementation Workstreams

### Workstream 0: Freeze the product contract

Deliverables:

- `loomai-product-profile-v1` schema;
- stable product profile IDs and versioning rules;
- mapping to official LoomAI capability codes;
- maturity and claim rules;
- framework/runtime/Platform/customer ownership matrix.

Acceptance:

- invalid or unknown capabilities fail validation;
- every capability maps to a real module, hosted contract, or explicit product-only responsibility;
- no product profile can silently enable an action, vector space, or specialist.

### Workstream 1: Build the Product Kernel compiler

Deliverables:

- Platform product-profile persistence and API;
- compiler from profile to V04 deployment version artifacts;
- curated pack, vector-space, action, connector, specialist, provider, and UI manifests;
- immutable compiled-output hash;
- product profile diff/preview before apply.

Acceptance:

- the same profile version produces byte-stable semantic artifacts;
- compile errors are operator-readable;
- runtime does not need mutable Platform catalogue access per request;
- deployment apply uses the normal deterministic release lifecycle.

### Workstream 2: Standardize knowledge sources

Deliverables:

- push Data Sync source;
- external documents-only retrieval source;
- durable upload/document source;
- relational query source where applicable;
- source preview, policy, revision, status, count, and delete contracts.

Acceptance:

- each source has one owner and one lifecycle;
- vectors are derived and rebuildable;
- update/delete convergence is proved;
- per-work status uses the public indexing query contract;
- source outage and provider failure are visible.

### Workstream 3: Standardize UI integration

Deliverables:

- docked composer;
- Max Mode;
- inline assistant panel/card;
- query-once component;
- citations, product cards, action cards, confirmation, review, denial, and failure renderers;
- backend integration SDK/guide.

Acceptance:

- all surfaces use one backend conversation contract;
- no private credentials reach the browser;
- result rendering is driven by stable structured contracts;
- development diagnostics do not leak into normal user UI.

### Workstream 4: Productize specialists

Deliverables:

- manifest authoring and schema validation;
- exact-version registry in compiled deployment artifacts;
- trusted adapter catalogue;
- read-only execution, input wait, conversation manager, and durable job operator views;
- specialist verification pack.

Acceptance:

- start-up validation is fail-fast;
- public users cannot enumerate or choose unrestricted specialists;
- each specialist narrows authority and cannot expand it;
- retrieval specialists repeat tenant/deployment canaries.

### Workstream 5: Productize actions and MCP

Deliverables:

- unified action inventory and risk class;
- MCP import review flow;
- connector credential binding;
- read-result grounding;
- confirmation/review and durable receipt UI;
- execution and reconciliation diagnostics.

Acceptance:

- every action has an owner, schema, access mode, scopes, timeout, and result projection;
- all hidden targets come from trusted backend state;
- write replay is idempotent under the configured receipt contract;
- no raw action JSON is the normal final answer.

### Workstream 6: Privacy, governance, and lifecycle

Deliverables:

- PII profile by product;
- retention and deletion policy;
- audit event taxonomy;
- source, conversation, specialist, action, and review cleanup workflows;
- customer-facing privacy and operator evidence.

Acceptance:

- PII handling is tested in both input and output directions selected by the profile;
- deletion proves derived evidence cleanup;
- audit records contain identifiers and outcomes, not secrets/private payloads;
- privacy claims match implemented behavior.

### Workstream 7: Product verification framework

Deliverables:

- reusable product verification-pack schema;
- per-profile golden query sets;
- known evidence fixtures;
- action and specialist scenarios;
- live-provider rows;
- UI fixture checks;
- Platform release-gate integration.

Acceptance:

- every product claim has a named automated or controlled proof;
- skipped live checks are visible and block claims that depend on them;
- a current source revision is proved independently for backend and frontend;
- gate output tells the operator what failed and what to do next.

### Workstream 8: Commercial and operational packaging

Deliverables:

- onboarding, pricing/entitlements, quotas, support, incident, privacy, and offboarding definitions;
- product capability and boundary pages;
- partner implementation packet;
- design-partner feedback and quality dashboard;
- release and rollback runbook.

Acceptance:

- technical readiness is not confused with market readiness;
- support owner and escalation path exist;
- billing claims match enabled product features;
- rollback and customer data exit are rehearsed.

## Execution Sequence

### Wave 0: Shared foundation

1. Freeze `loomai-product-profile-v1`.
2. Build profile compilation and preview into V04 deployment versions.
3. Build the reusable product verification-pack contract.
4. Normalize product identity, framework version, source commit, and quality-pack metadata in runtime health/evidence.
5. Keep the existing production release gate green after each shared change.

Exit: one no-write knowledge profile can be compiled, deployed to staging, verified, promoted, assigned, and rolled back without manual runtime env editing.

### Wave 1: Current product and reusable knowledge/UX

1. Finish remaining Shopify Companion public release blockers.
2. Extract the reusable Grounded Knowledge Assistant profile from proven ProdUS/commerce knowledge behavior.
3. Publish the shared docked composer and Max Mode integration contract.
4. Add product-level quality packs and UI results.

Exit: Shopify remains green and a second non-commerce knowledge assistant is deployed from the same kernel.

### Wave 2: Document and transient context

1. Productize the Document Intelligence Workbench.
2. Separate durable ingestion from transient attachment analysis.
3. Add source preview, revision, deletion, and lifecycle evidence.
4. Reuse Spring AI readers rather than rebuilding parser capabilities.

Exit: one customer document set passes upload, reindex, delete, citation, tenant, and PII gates.

### Wave 3: Read-only specialists

1. Productize the existing deployment knowledge specialist authoring/deployment path.
2. Add one customer-facing read-only Thinker profile.
3. Add typed input waits and bounded conversation manager only where required.
4. Add durable read jobs for one real event/scheduler use case.

Exit: exact-version specialist, schema, evidence, restart, replay, and isolation gates pass in staging and production.

### Wave 4: Governed resolution and MCP

1. Complete Resolver dry-run.
2. Add one low-risk write with application validation and confirmation.
3. Add durable receipt and reconciliation.
4. Productize MCP import, approval, allowlist, and safe read-result grounding.
5. Add human review before expanding write risk.

Exit: no duplicate side effects, no unconfirmed writes, and no unrestricted tool discovery in live canaries.

### Wave 5: New verticals

Choose one vertical using market evidence, not technical novelty:

- account/support resolution;
- relationship intelligence;
- behavior/churn intelligence;
- another commerce platform.

Exit: the chosen vertical uses the shared kernel with only domain adapters, prompts/manifests, knowledge schema, UI cards, and quality pack added. A need to fork the runtime is a design failure unless a real product boundary is documented.

### Wave 6: Enterprise and partner product factory

1. Add guided product creation from approved templates.
2. Add private domain/branding/provider/deployment choices that map to real operational modes.
3. Package approved vertical capabilities in Marketplace packs.
4. Add partner-scoped implementation and verification workflows.
5. Prove export/import, promotion, assignment, rollback, offboarding, and data deletion.

Exit: a new enterprise product can be launched through a repeatable Platform workflow with no copied framework code, secret leakage, or bespoke deployment path.

## Mandatory Release Gates

| Gate | Required proof |
| --- | --- |
| Dependency gate | Central-only build resolves AI Fabric 0.5.2 and packaged runtime contains only intended framework versions |
| Contract gate | Product profile, manifest, schemas, vector spaces, actions, and scopes validate before apply |
| Unit/integration gate | Product code, runtime, connector, Platform compiler, and profile tests pass |
| Packaged-runtime gate | Built container boots with intended provider/module/profile and reports source identity |
| Auth gate | Missing/invalid assertion fails; issuer, audience, consumer, tenant, deployment, and scopes are server-owned |
| Isolation gate | Two-user, two-tenant, and two-deployment negative canaries pass where applicable |
| Knowledge gate | Create/update/delete/reindex converge; counts and revision hashes reconcile |
| RAG gate | Golden queries return expected evidence; no-evidence and provider failure remain explicit |
| Session gate | History and pending work are owner-scoped, bounded, expiring, and reset-safe |
| Specialist gate | Exact version, typed input/output, capability intersection, bounded plans, and safe diagnostics pass |
| Action gate | Read facts ground answers; writes require policy plus confirmation/review; replay is safe |
| Privacy gate | Selected PII, retention, deletion, and logging behavior is proved |
| UI gate | All promised surfaces and states render on desktop/mobile with no secret/raw diagnostic leakage |
| Deployment gate | Staging apply, verification, production promotion, assignment, health, and rollback evidence pass |
| Product quality gate | Profile quality pack meets declared accuracy, citation, safety, latency, and cost thresholds |
| Operational gate | Monitoring, support, incident, backup/export, offboarding, and owner runbooks exist |
| Commercial gate | Entitlements, billing, claims, listing, privacy, and support posture match the live product |

## Product Metrics

Shared technical metrics:

- successful request rate by product profile and mode;
- p50/p95 latency split across orchestration, provider, retrieval, connector, and action stages;
- provider failure and explicit fallback rate;
- indexing work completion, retry, dead-letter, and convergence time;
- retrieval expected-source hit rate and no-evidence correctness;
- cross-scope denial count;
- specialist completion, input-wait, rejection, and replay rate;
- action proposal, confirmation, rejection, execution, reconciliation, and duplicate-prevention rate;
- PII detection/redaction events without storing sensitive payloads;
- cost per successful product outcome.

Product metrics:

- user task completion;
- answer helpfulness and evidence trust;
- time saved compared with the current workflow;
- successful activation and retained usage;
- support incidents per active deployment;
- conversion or business outcome only where attribution is honest and consented.

## Framework Blocker Process

When product work expects behavior AI Fabric does not expose:

1. Confirm the need is generic framework capability rather than LoomAI hosting, UI, or domain behavior.
2. Reproduce the missing contract in the public framework repository.
3. Write the expected public API, security boundary, failure semantics, and test before adding a private workaround.
4. Add focused tests and a real-app proof in the public repository.
5. Release an immutable framework version through normal CI and Maven Central.
6. Upgrade the private runtime through the BOM and rebuild from an empty Maven cache.
7. Repeat product canaries and the full Platform release gate.

Allowed private work:

- LoomAI runtime HTTP projection;
- Platform compilation and deployment workflow;
- customer/domain adapters;
- private connector/MCP transport;
- product UI, prompt packs, manifests, policies, quality packs, and operations.

Disallowed compensation:

- copied public framework source;
- duplicate private gateway for a missing generic contract;
- relaxed tenant/vector filtering;
- request-owned identity or authority;
- text-matching core behavior;
- fake endpoint or success stub;
- silent provider, retrieval, or execution fallback.

## Definition Of A Releasable LoomAI Product

A profile is a LoomAI product only when all of the following are true:

1. The customer problem and product promise are narrow and explicit.
2. Every claimed capability maps to an official LoomAI capability code.
3. Every technical capability maps to a current AI Fabric module or a clearly owned LoomAI component.
4. Platform can compile, deploy, verify, promote, assign, observe, export, restore, and retire it.
5. The customer application retains identity, authorization, validation, transactions, and source-of-truth authority.
6. The UI supports all normal, loading, no-evidence, denied, failed, confirmation, and review states promised by the product.
7. The product quality pack passes against the intended live providers and integrations.
8. Tenant/deployment isolation and missing-boundary failures are proved.
9. Support, billing, privacy, incident, rollback, and offboarding responsibilities are documented.
10. Product claims match the exact live deployment and are not inferred from framework demos.

## Immediate Work Queue

### P0

- Define and test `loomai-product-profile-v1`.
- Add product profile compilation to V04 deployment versions.
- Define the reusable product verification-pack schema.
- Keep Shopify Companion release work moving under `010.18`.
- Extract `grounded-knowledge-assistant@1` from current hosted retrieval behavior.
- Freeze the shared docked composer and Max Mode integration contract.

### P1

- Build knowledge source/status/revision UI and APIs.
- Deploy one non-commerce Grounded Knowledge Assistant design-partner instance.
- Productize the Document Intelligence Workbench.
- Add Platform specialist manifest authoring and validation for read-only profiles.

### P2

- Complete Thinker read-only product profile and quality pack.
- Complete Resolver dry-run and one low-risk confirmed write.
- Productize MCP import/review/allowlist and action-result grounding.

### P3

- Select one new vertical from customer evidence.
- Add enterprise/private product factory only after two profiles prove reuse of the shared kernel.
- Add Marketplace product packs and partner workflows only for capabilities with live release evidence.

## Completion Decision

This plan is complete when LoomAI has:

- one shared product kernel;
- Shopify Companion operating on it without regression;
- one reusable Grounded Knowledge Assistant deployed for a non-commerce customer;
- one document lifecycle product proof;
- one read-only exact-version specialist product proof;
- one governed low-risk write proof with durable confirmation/reconciliation;
- one approved MCP capability pack;
- product-level verification integrated into the full Platform release gate;
- a repeatable enterprise staging-to-production launch using only managed Platform workflows.

Until those outcomes are demonstrated, describe LoomAI as having the supporting provider capabilities, not as having every portfolio product generally available.
