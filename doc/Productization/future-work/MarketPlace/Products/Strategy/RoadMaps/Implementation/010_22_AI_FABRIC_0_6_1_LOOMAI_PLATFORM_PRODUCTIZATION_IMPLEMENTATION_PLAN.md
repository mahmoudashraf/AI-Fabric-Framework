# 010.22 AI Fabric 0.6.1 LoomAI Platform Productization Implementation Plan

Status: implementation plan created on 2026-09-15. No item in this document is complete merely because AI Fabric exposes the underlying primitive. Completion requires the LoomAI runtime, Platform workflow, hosted verification, operations, and product claim described here.

Canonical architecture:

- [010.21 Consolidated LoomAI Behavior Product, Product Profile, And Deployment Architecture](010_21_CONSOLIDATED_LOOMAI_AI_ENABLEMENT_PRODUCT_PROFILE_AND_DEPLOYMENT_ARCHITECTURE.md)

Release foundation:

- AI Fabric `0.6.1`
- Maven group `io.github.loom-ai-labs`
- tag `ai-fabric-framework-v0.6.1`
- release commit `bf6d19eed5ed0a8d8085db7cc02e0505e9973e65`

This document is the executable delivery companion to `010.21`. It does not redefine the product architecture, create a second deployment lifecycle, or make framework demos into LoomAI product claims.

## 1. Objective

Turn the complete released AI Fabric capability set into a coherent LoomAI Platform product portfolio while preserving these decisions:

1. LoomAI markets reusable behaviors, not framework modules.
2. The primary products are Conversational Assistant, Agentic Specialist Team, and Smart Brain.
3. Resolver and Human Review are controlled execution extensions.
4. RAG, indexing, documents, MCP, PII, relationship query, behavior analysis, migration, providers, vectors, structured output, and UI are capability packs, managed services, or channels.
5. Shopify, ProdUS, support, incident, churn, CRM, and similar domains are solution packs or reference deployments.
6. Platform remains the deterministic control plane.
7. Each deployed runtime remains its own data plane.
8. Customer applications retain identity, authorization, domain validation, transactions, and system-of-record authority.
9. Greenfield rules apply. Do not add compatibility readers, old release fallbacks, duplicate control surfaces, placeholder implementations, or product-domain text matching.
10. A capability becomes marketable only after its exact hosted product profile passes its verification pack.

## 2. Intended Customer Portfolio

### 2.1 Primary behavior products

| Product | Customer promise | Activation | Coordination | Initial maturity |
| --- | --- | --- | --- | --- |
| LoomAI Conversational Assistant | A person asks and LoomAI returns an answer, clarification, structured result, or governed next step | Authenticated interactive request | One bounded orchestration turn with optional backend-owned session | Behavior hosted-proven through Shopify and ProdUS; generic profile required |
| LoomAI Agentic Specialist Team | A bounded manager coordinates approved specialists for a larger task | Authenticated interactive or trusted application request | Exact-version manager and read-only workers through a closed chain or fixed plan | AI Fabric available; LoomAI productization required |
| LoomAI Smart Brain | Trusted events and schedules initiate proactive read-only analysis without a fabricated chat turn | Trusted application, CloudEvent, or managed schedule | Exact specialist, bounded read-only chain, or explicitly selected fixed read plan | AI Fabric primitives available; LoomAI productization required |

### 2.2 Execution extensions

| Extension | Purpose | Compatible products | Boundary |
| --- | --- | --- | --- |
| Governed Resolver | Convert an approved recommendation into one registered, validated, confirmed write | Conversational Assistant and selected Agentic Specialist Team profiles | Application executes and reconciles the side effect |
| Human Review | Route a bounded proposal or result to an authorized reviewer | Conversational Assistant, Agentic Specialist Team, or a separate review boundary fed by Smart Brain | Model never selects or impersonates the reviewer |

Smart Brain execution remains read-only in the first released contract. It may emit a typed result or create an application-owned review request through a separately authorized boundary. It may not execute an event-triggered write.

### 2.3 Capability and managed-service packs

| Pack | AI Fabric foundation | LoomAI product responsibility |
| --- | --- | --- |
| Grounded Knowledge | embeddings, vector search, indexing, Data Sync, RAG, external retrieval | source policy, vector profile, tenant/deployment isolation, revisions, status, quality, citations, delete/reindex operations |
| Document Knowledge Operations | indexing plus Spring AI document readers used by the real-app proof | trusted upload/storage, supported formats, preview, approval, chunk manifests, reindex/delete, retention, operator UI |
| Tool And MCP Integration | actions, connector actions, DB action registry, MCP execution | Marketplace review/install, exact server and tool binding, auth, schema drift, result projection, read/write risk policy |
| Privacy And Governance | PII and governance modules | legal/product policy, modes, encryption, retention, deletion, logging, customer claims |
| Relationship Intelligence | relationship-query module | approved domain schema, object authorization, query ceilings, safe projections, product UX |
| Behavior Intelligence | behavior module | event contract, consent, signal interpretation, retention, quality, no automatic authority |
| Knowledge Onboarding And Backfill | migration/backfill and indexing work status | source-of-truth selection, operator workflow, pause/resume/retry, reconciliation, completion evidence |
| Structured Result Contracts | schema-bound generation and bounded correction | versioned schemas, validators, result renderers, retry/cost policy, visible failure |
| Inference Reliability | provider abstractions and diagnostics | approved inference profiles, secrets, budgets, timeout policy, failure visibility, tested provider combinations |
| Managed Runtime Operations | framework health/metrics plus LoomAI deployment services | lifecycle, assignment, promotion, rollback, scaling, backup/export, alerts, support, offboarding |

## 3. Current Productization Baseline

### 3.1 Hosted and reusable today

- V04 deployment draft, validation, immutable version, release, apply, post-apply verification, assignment, promotion, and rollback evidence.
- Coolify and Railway target profiles and provider resource handles.
- Managed inference and vector profiles, including active supported Milvus/Zilliz paths.
- Config-only and sealed export/import with target-scoped rewrite.
- Marketplace `TEMPLATE`, `DATA`, `ACTION`, and `INFERENCE_PROFILE` plugin lifecycles.
- Managed MCP Execution Gateway, discovery/import, exact binding, schema drift checks, and normalized action evidence.
- Shopify Companion and ProdUS Conversational Assistant deployments.
- Curated modes, RAG, Data Sync, vectorization, query-once, and backend-mediated chat paths.
- Exact deployment-knowledge specialist with tenant/deployment isolation proof.
- Platform, Marketplace, Partner, Thinker, hosted-product, and release verification suites.

### 3.2 Implemented but not generally packaged

- Shopify package/tier/profile mapping is a strong vertical precedent but is not the generic Product Profile model.
- Max Mode, docked composer, inline/result-card concepts, and query-once exist but are not one stable reusable channel package.
- Managed MCP infrastructure is hosted-proven, but every customer-facing server/tool pack still needs its own live proof.
- Exact specialist execution is hosted-proven, but bounded multi-specialist chains are disabled in the private runtime.
- Current curated runtime behavior is limited to the default, commerce, support, and deployment-knowledge paths.

### 3.3 Missing product contracts

- immutable Behavior Product Template catalogue;
- immutable generic Product Profile catalogue and compiler;
- internal Runtime Capability Bundle inventory;
- versioned Verification Pack catalogue;
- reusable Agentic Specialist Team runtime components and data-plane API;
- deployment-local Smart Brain ingress, durable operations, result storage, scheduling, and delivery;
- reusable Resolver and Human Review packages;
- generic product operations for PII, governance, relationship, behavior, migration, and document ingestion;
- product-specific entitlements, quotas, billing, support, and offboarding for the new behaviors.

### 3.4 Evidence correction required

`010.21` still contains a historical `0.5.2` production-evidence subsection. Before implementation starts, update that subsection to the verified `0.6.1` fleet baseline while preserving historical deployment records as evidence.

Current operational truth is:

- all supported private runtime families use AI Fabric `0.6.1`;
- ProdUS is assigned to verified deployment `dep-f6abfa06`;
- staging and production canonical Platform, Marketplace, Partner, Thinker, and hosted checks passed;
- aggregate release suites still report failure only for the explicitly owner-deferred Shopify retrieval-quality stage;
- that exception permits the framework rollout but must not be relabeled as a fully green aggregate release gate.

## 4. Capability Maturity Model

Every capability and product profile must move through these states in order:

```text
FRAMEWORK_AVAILABLE
  -> RUNTIME_PACKAGED
  -> PLATFORM_SELECTABLE
  -> HOSTED_PROVEN
  -> MARKET_READY
```

Definitions:

| State | Required evidence |
| --- | --- |
| `FRAMEWORK_AVAILABLE` | Released Maven Central contract, tests, and real-app evidence exist |
| `RUNTIME_PACKAGED` | Private runtime contains exact modules/components, config, migrations, and startup validation |
| `PLATFORM_SELECTABLE` | A published Product Profile can select and compile the capability into V04 desired state |
| `HOSTED_PROVEN` | Packaged staging runtime passes deterministic, real-provider, security, restart, and product canaries |
| `MARKET_READY` | Entitlements, quotas, billing, support, privacy, documentation, claims, and production rollout are approved |

Rules:

- States cannot be skipped.
- A framework demo proves only `FRAMEWORK_AVAILABLE`.
- A runtime flag does not prove `PLATFORM_SELECTABLE`.
- A healthy container does not prove `HOSTED_PROVEN`.
- Marketing copy must use the maturity of the exact selected Product Profile Version, not the highest maturity of any underlying primitive.

## 5. Target Platform Architecture

### 5.1 Product composition

```text
one Behavior Product Template Version
  + optional Solution Pack Version
  + approved Capability Pack Versions
  + allowed Execution Extensions
  + approved Channel Bindings
  + one Runtime Capability Bundle Version
  + one inference profile
  + optional vector profile
  + one deployment topology template
  + one deployment target profile
  + one verification pack
  + one commercial package
  = one immutable Product Profile Version
  -> existing V04 Deployment Version
  -> existing Release
  -> existing Verification
  -> existing Consumer Assignment
```

The Product Profile is a product-intent layer. It compiles into the existing V04 desired-state and release path. It must not become a second runtime configuration, assignment, release, or deployment engine.

### 5.2 Runtime Capability Bundle

Add an internal, immutable `RuntimeCapabilityBundleVersion` concept. It is not a customer-facing product.

It identifies:

- private runtime image digest and source commit;
- AI Fabric version;
- directly packaged AI Fabric modules;
- exact Java component IDs;
- curated modules and prompt packs;
- specialist manifests and effective hashes;
- chain definitions, typed adapters, input mappers, and result projectors;
- application-owned migrations;
- supported endpoint classes;
- supported activation sources;
- required managed services and secrets;
- capability and limit inventory;
- baseline runtime verification pack.

Runtime bundles must use one private runtime source line. A new behavior must not create a copied runtime fork. Build variants are allowed only when a reviewed security or footprint requirement justifies them, and they must still come from the same source and verification model.

Initial bundle catalogue:

| Bundle | Purpose | Initial state |
| --- | --- | --- |
| `loomai-conversational-runtime@1` | Current interactive orchestration, sessions, RAG, Data Sync, actions, MCP, query-once | Derive from hosted runtime and formalize |
| `loomai-agentic-team-runtime@1` | Conversational capabilities plus exact specialist teams and durable chains | Build after Product Kernel |
| `loomai-smart-brain-runtime@1` | Trusted event/schedule activation, durable read jobs/chains, operations and output delivery | Build after Agentic durability foundation |

### 5.3 Application-owned components

Some AI Fabric features cannot be safely enabled through a generic Platform flag. LoomAI must supply reviewed private components:

- chain definitions;
- exact target catalogues;
- typed input adapters and target mappers;
- bounded result projectors;
- event-to-specialist mappings;
- trusted-context construction;
- action authorization and domain validation;
- reviewer authorization and delivery;
- relationship schemas and projections;
- behavior event contracts;
- document source policy and reader integration;
- database migrations and operational indexes;
- public response projection and UI renderers.

Do not invent a tenant-authored chain language, dynamic target discovery, arbitrary scripts, arbitrary URLs, raw SQL, or generic execute-anything endpoints.

## 6. Platform Data Model

Use the next available Flyway migration numbers at implementation time. Preserve existing immutable deployment records and do not add compatibility tables for abandoned shapes.

### 6.1 Behavior Product Template

Recommended tables:

- `behavior_product_template`
- `behavior_product_template_version`

Required version fields:

- stable template code;
- semantic version;
- display name and owner-facing summary;
- allowed activation sources;
- coordination contract;
- interaction posture;
- permitted execution extensions;
- durability class;
- required endpoint classes;
- required runtime capabilities;
- unsupported claims;
- baseline verification-pack reference;
- canonical content JSON;
- content hash;
- lifecycle status;
- created, validated, published, and retired audit fields.

Seed exactly:

- `loomai-conversational-assistant@1`;
- `loomai-agentic-specialist-team@1`;
- `loomai-smart-brain@1`.

### 6.2 Product Profile

Recommended tables:

- `product_profile`
- `product_profile_version`

Required version fields:

- stable profile code and semantic version;
- exact Behavior Product Template Version reference;
- optional exact Solution Pack Version reference;
- exact Capability Pack Version references;
- allowed Execution Extension references;
- exact Channel Binding references;
- exact Runtime Capability Bundle Version reference;
- commercial package reference;
- inference and vector profile constraints;
- topology and target constraints;
- exact verification-pack reference;
- canonical source JSON;
- compiled-input hash;
- lifecycle status and complete audit fields.

Lifecycle:

```text
DRAFT -> VALIDATED -> PUBLISHED -> RETIRED
```

Published versions are immutable. A semantic change creates a new version and hash.

### 6.3 Verification Pack

Recommended tables:

- `product_verification_pack`
- `product_verification_pack_version`

Store references and bounded configuration for:

- deterministic cases;
- real-provider cases;
- retrieval and expected evidence fixtures;
- activation and coordination cases;
- authorization and isolation cases;
- restart/replay/deadline cases;
- action/review cases;
- UI cases;
- operations and promotion cases;
- thresholds, blocking status, and remediation metadata.

Do not store executable shell supplied by customers in a Product Profile or Verification Pack. Verification implementations remain reviewed Platform code and scripts.

### 6.4 Capability inventory and bindings

Use normalized binding tables for exact profile-to-version references where queryability and referential integrity matter. Keep canonical JSON for deterministic hashing and export, not as the only source for lifecycle relationships.

At minimum, provide queryable bindings for:

- profile to behavior version;
- profile to runtime bundle version;
- profile to capability/solution/extension/channel versions;
- profile to verification version;
- profile to compiled deployment versions;
- profile to commercial package and entitlement policy.

## 7. Platform Backend Contracts

Exact URLs may follow existing controller conventions, but the following capability classes are required.

### 7.1 Behavior catalogue

- list behavior templates and immutable versions;
- create and update drafts;
- validate a draft;
- publish a validated version;
- retire a published version;
- show activation, coordination, durability, compatibility, and unsupported claims;
- show current maturity and hosted evidence.

### 7.2 Product Profile catalogue

- list profiles and versions;
- create/update a draft;
- resolve server-owned allowed choices;
- validate compatibility;
- preview semantic composition and requirements;
- publish or retire a version;
- compile a published profile into a new or selected deployment draft;
- show every deployment/version/release created from the profile.

### 7.3 Runtime bundle inventory

- list reviewed runtime bundle versions;
- compare required and provided capabilities;
- expose exact image digest, source commit, AI Fabric version, specialist/chain hashes, endpoint classes, migrations, and required secrets to operators;
- reject activation when a required component is absent;
- provide a bounded runtime readback used by post-apply verification.

The runtime readback must never expose secrets, prompts that are not intended for operators, protected payloads, provider responses, or application records.

### 7.4 Compilation

The compiler must:

1. resolve only published exact versions;
2. require exactly one behavior template;
3. validate behavior, activation, channel, extension, durability, runtime, provider, vector, topology, target, Marketplace, secret, and verification compatibility;
4. resolve server-owned defaults;
5. compile into existing V04 draft/config sections;
6. emit a semantic preview and deterministic compiled-input hash;
7. record every source reference;
8. reject unresolved or unsupported capability claims;
9. avoid live Marketplace or product-catalogue lookups from the deployed runtime request path.

### 7.5 Export/import

Extend existing export/import to include exact Product Profile, Behavior Template, Runtime Bundle, Solution/Capability Pack, Channel, and Verification references plus hashes.

Import preview must distinguish:

- portable product intent;
- target-scoped infrastructure references requiring rewrite;
- secrets requiring rebinding;
- unavailable versions or capabilities;
- unsupported target/runtime combinations.

Never silently replace a missing exact version with a newer or older one.

## 8. Runtime Data-Plane Contracts

### 8.1 Common rules

- Every endpoint belongs to the assigned deployment runtime.
- Platform is used for assignment discovery and lifecycle, not as a mandatory request proxy.
- Trusted identity, tenant, deployment, scopes, vector boundaries, specialist, actions, and output destinations come from authenticated backend context and compiled configuration.
- Public payload fields cannot widen authority.
- Provider and persistence failures remain visible.
- Responses expose safe typed projections, evidence references, status, and bounded diagnostics, not raw provider or worker payloads.

### 8.2 Conversational Assistant

Freeze reusable contracts for:

- authenticated chat/query;
- query-once;
- backend-owned conversation creation, continuation, reset, and expiry;
- contextual attachments;
- structured result envelopes;
- pending confirmation and review references;
- docked composer, Max Mode, inline, and result-card rendering.

The generic contract must be independent of Shopify while allowing Shopify to bind its own actions, data, entitlements, and UI cards.

### 8.3 Agentic Specialist Team

Provide deployment-local authenticated contracts for:

- synchronous execution where bounded latency permits;
- asynchronous submission;
- scoped status and typed result retrieval;
- cancellation;
- exact replay for the same protected payload;
- safe execution lineage.

The private runtime must register:

- one exact-version manager;
- at least two exact-version read-only workers;
- one immutable Java chain definition;
- input adapter;
- target input mappers;
- bounded target result projectors;
- closed target catalogue;
- chain and deployment ceilings;
- JDBC repository and application-owned migration;
- stable distinct encryption and fingerprint secrets.

No worker may recurse, discover targets dynamically, invoke writes, or receive another worker's raw output.

### 8.4 Smart Brain

Standardize V1 on CloudEvents `1.0` structured JSON over HTTPS.

Required deployment-local endpoint classes:

```text
POST /api/smart-brain/v1/triggers/{triggerCode}
GET  /api/smart-brain/v1/operations/{operationId}
POST /api/smart-brain/v1/operations/{operationId}/cancel
POST /api/smart-brain/v1/operations/{operationId}/replay
```

The exact externally advertised URLs come from assignment metadata. Accepted operations retain an absolute URL for the runtime that accepted them.

Required services:

- CloudEvent schema and size validation;
- deterministic trigger registry;
- service identity and trusted-context builder;
- idempotency and changed-payload conflict detection;
- durable specialist or chain submission;
- deployment-local PostgreSQL operation state;
- direct typed result retrieval;
- cancellation and exact replay;
- retention and cleanup;
- deployment-local Quartz/PostgreSQL schedules entering the same trigger service;
- transactional result-delivery outbox;
- optional signed CloudEvents HTTPS webhook delivery with retry and dead-letter state.

Callback URLs and signing secrets come from compiled Product Profile configuration. Event payloads cannot select them.

Promotion behavior:

1. assignment changes route new submissions to the new verified runtime;
2. already accepted operations remain on the original runtime;
3. old runtime endpoints remain reachable for assignment-cache grace and operation draining;
4. old runtimes decommission only after jobs and result deliveries reach terminal state or an explicit operator decision is recorded.

No non-HTTP adapter is needed for V1. Add Kafka, SQS/EventBridge, Service Bus/Event Grid, Pub/Sub, NATS, RabbitMQ, or vendor bridges only through a future governed `INTEGRATION` Marketplace type with allowlisted implementations. Do not overload `DATA` or `ACTION` plugins.

## 9. Private Runtime Packaging

### 9.1 Common runtime work

- preserve one BOM and one resolved AI Fabric version;
- add optional modules only through reviewed runtime dependencies;
- fail startup when compiled profile requirements exceed runtime inventory;
- emit source commit, image digest, framework version, behavior/profile versions, specialist/chain hashes, storage posture, and endpoint classes through safe health/readback;
- use application-owned Flyway migrations for durable execution state;
- keep schema auto-initialization disabled in production;
- keep secrets stable across replicas, restart, promotion grace, and rollback windows;
- use no locally installed framework artifact in release builds.

### 9.2 Module adoption groups

| Group | Modules/capabilities | Productization action |
| --- | --- | --- |
| Existing conversational | provider, curated modules, RAG, vector providers, chat session, Data Sync, connector actions, retrieval connector | formalize inventory and reusable profile |
| Agentic execution | `ai-fabric-execution` direct specialists, waits, plans, conversation manager, durable jobs, chains, receipts, review | add reviewed private components and enable per bundle |
| Privacy | PII and governance | add dependencies, policy config, storage/retention, endpoints and gates only when pack is built |
| Data intelligence | relationship query and behavior | add exact schema/event adapters, authorization and result projections |
| Knowledge operations | indexing, migration/backfill, document-reader integration | add managed source, job and lifecycle UX rather than exposing internal queues |
| Action lifecycle | local/connector/registry actions, confirmation, receipts, review | package as Resolver/Human Review extensions with system-of-record reconciliation |

### 9.3 Configuration policy

Capabilities stay disabled unless selected by a published Product Profile and provided by its exact Runtime Capability Bundle.

Configuration must be generated from server-owned catalogues. Do not expose raw environment variables, module names, specialist targets, or arbitrary capability switches to customer-facing UI.

## 10. Platform And Partner UI

### 10.1 Product catalogue

Add an operator product surface that shows:

- the three Behavior Products first;
- customer promise and supported activation model;
- maturity state;
- compatible solution/capability packs, extensions, and channels;
- required managed services and deployment topology;
- exact hosted evidence and blockers.

Framework module names belong in an operator detail view, not the primary product cards.

### 10.2 Product Profile authoring

Use a guided workflow:

1. choose behavior;
2. choose an approved solution pack;
3. choose commercial package;
4. choose from server-resolved compatible capabilities/extensions;
5. choose approved channels;
6. choose allowed provider/vector/runtime posture;
7. review security, durability, cost, and unsupported claims;
8. validate;
9. preview compiled V04 changes;
10. publish and launch through the existing deployment workflow.

Do not present a large free-form configuration matrix. Incompatible choices should be absent or disabled with a concrete server-provided reason.

### 10.3 Runtime operations

Add product-aware views for:

- selected behavior/profile/runtime bundle versions;
- active endpoints and assignment;
- knowledge/indexing status;
- specialist and chain status;
- Smart Brain queues, leases, result deliveries, and dead letters;
- pending confirmations, receipts, and reviews;
- provider/vector/MCP health;
- quality and release-gate history;
- promotion draining and decommission blockers.

### 10.4 Partner experience

Partners select published Product Profiles and customer-safe options. They cannot author executable chain definitions, broaden target catalogues, supply authority, or bypass verification. Custom solution-pack work remains reviewed and versioned before it appears as a selectable option.

## 11. Capability Taxonomy Changes

Reserve internal candidate codes during implementation and add them to the official stable external taxonomy only when the corresponding LoomAI capability reaches `HOSTED_PROVEN` and its contract is stable.

Candidate additions:

- `loomai_exact_specialist_execution`;
- `loomai_bounded_specialist_team`;
- `loomai_proactive_smart_brain`;
- `loomai_durable_ai_jobs`;
- `loomai_typed_input_wait_resume`;
- `loomai_human_review_workflows`;
- `loomai_document_knowledge_operations`;
- `loomai_knowledge_migration_backfill`;
- `loomai_inference_resilience`.

Do not rename or reuse existing stable capability codes. Adding a new stable code is allowed; silently changing the meaning of an existing code is not.

## 12. Verification Architecture

### 12.1 Universal gates

Every product profile must pass:

1. exact AI Fabric `0.6.1` Central-only dependency resolution;
2. private runtime unit and integration tests without skipped normal tests;
3. packaged container boot with exact source/image/framework/profile identity;
4. V04 compile, semantic preview, version, release, post-apply verification, assignment, export/import, and rollback;
5. real generation and embedding provider calls where claimed;
6. provider, vector, MCP, database, and callback failure visibility;
7. two-tenant and two-deployment isolation;
8. missing principal, tenant, deployment, scope, capability, vector space, specialist, and secret fail-closed cases;
9. no raw provider, worker, connector, or protected persistence payload in public results;
10. desktop and mobile UI proof for all selected channel bindings;
11. operations, monitoring, backup/export, support, retention, and offboarding review.

### 12.2 Conversational Assistant gates

- new user query and multi-turn continuation;
- clarification and no-evidence states;
- known-answer RAG with expected citations;
- read-action grounding and final generation;
- structured result validation and rendering;
- conversation owner isolation, reset, expiry, and replay policy;
- confirmation/review states only when extensions are selected;
- Shopify and ProdUS regression suites.

### 12.3 Agentic Specialist Team gates

- manager completion without a worker;
- one bounded clarification;
- one worker;
- adaptive sequential second worker;
- explicitly independent parallel workers with `ALL_REQUIRED` fan-in;
- exact supporting-result attribution;
- terminal approved read-only handoff;
- invented or unregistered target denial;
- worker authorization evaluated independently;
- required branch/provider/projector failure remains failure;
- deadline and cancellation;
- exact replay and changed-payload conflict;
- changed-definition rejection;
- restart and lease recovery;
- cross-owner, tenant, deployment, scope, evidence, and target denial;
- current authorization reevaluated on resume;
- metrics and safe lineage present.

### 12.4 Smart Brain gates

- valid and invalid CloudEvents;
- unknown trigger denial;
- payload size and schema bounds;
- service identity and tenant/deployment isolation;
- no browser credential or authority path;
- accepted operation survives restart;
- duplicate event returns exact replay without duplicate logical work;
- changed facts under the same idempotency key fail;
- direct status/result/cancel access is scope-bound;
- no chat turn is created;
- Platform can be unavailable after cached assignment resolution;
- schedule uses the same activation and idempotency service;
- signed webhook delivery, retry, dead letter, and operator replay;
- callback destination cannot come from event input;
- promotion routes new work while old work drains;
- no event/scheduled write can execute.

### 12.5 Extension and capability-pack gates

- Resolver: dry run, confirmation/rejection, trusted target, application validation, one mutation, durable receipt, exact replay, and reconciliation.
- Human Review: authorized reviewer, allowed decision set, assignment, delivery, correction, escalation, expiry, restart, and denial.
- Knowledge: create/update/delete/reindex, work-status reconciliation, counts/revision hash, expected evidence, citations, no-evidence behavior.
- Documents: format allowlist, malicious/unsupported input denial, preview/approval, chunk lifecycle, replacement, delete, retention.
- MCP: exact server/tool name, auth, schema hash, drift denial, argument policy, bounded result, read/write policy, outage.
- PII/Governance: input/output modes, encryption, logs, deletion, retention, policy denial.
- Relationship: schema allowlist, object authorization, query ceilings, no unrestricted traversal or raw persistence output.
- Behavior: consented event input, signal provenance, uncertainty, no automatic mutation, retention/deletion.
- Migration: pause/resume/cancel/retry, superseded work, dead-letter handling, source/index count reconciliation.

## 13. Delivery Work Packages

### WP0: Truth, contracts, and status model

Status: `NOT_STARTED`

Tasks:

- update `010.21` current evidence to `0.6.1`;
- create the capability maturity registry;
- inventory current runtime modules, endpoints, migrations, secrets, and verification;
- freeze schema names and namespace ownership;
- reserve candidate capability codes;
- define acceptance evidence and owners for every later work package.

Exit:

- one reviewed source states what is framework-available, runtime-packaged, Platform-selectable, hosted-proven, and market-ready;
- no existing capability is overclaimed or lost.

### WP1: Product Kernel and compiler

Status: `NOT_STARTED`

Depends on: WP0.

Tasks:

- add Behavior Product Template, Product Profile, Runtime Capability Bundle, and Verification Pack persistence and immutable versions;
- seed the three behavior products;
- implement deterministic validation and compatibility resolution;
- implement semantic preview and hashes;
- compile into existing V04 drafts;
- extend export/import and audit;
- add operator catalogue and Product Profile authoring UI;
- cover entities, lifecycle, authorization, compiler, and export/import with tests.

Exit:

- a published profile compiles deterministically into V04 without manual environment editing;
- invalid or missing versions fail before release;
- no parallel deployment lifecycle exists.

### WP2: Generic Conversational Assistant baseline

Status: `NOT_STARTED`

Depends on: WP1.

Tasks:

- formalize `loomai-conversational-runtime@1` from current hosted runtime;
- extract generic profile choices from the Shopify precedent without moving Shopify-specific logic;
- package reusable chat, session, query-once, structured result, docked composer, Max Mode, and inline bindings;
- publish one generic Grounded Knowledge profile;
- deploy a staging canary independent of Shopify;
- run generic plus Shopify and ProdUS regression packs;
- prove promotion, assignment, export/import, and rollback.

Exit:

- Conversational Assistant is selectable as a LoomAI product rather than only through Shopify/ProdUS-specific configuration;
- one generic hosted Product Profile reaches `HOSTED_PROVEN`.

### WP3: Agentic Specialist Team

Status: `NOT_STARTED`

Depends on: WP1 and shared runtime identity/storage work. WP2 should establish the normal profile/compiler release path first.

Tasks:

- select one design-partner canary, preferably deployment/incident investigation or account/support resolution;
- implement exact manager/workers and private Java chain components;
- add PostgreSQL migration, repository configuration, stable secrets, workers, leases, recovery and cleanup;
- enable chains only in `loomai-agentic-team-runtime@1` deployments;
- expose authenticated execute/submit/status/result/cancel/replay contracts;
- add safe lineage and Platform operation UI;
- run the complete Agentic gate matrix;
- promote one verified profile to a controlled production canary.

Exit:

- one reusable Agentic Specialist Team profile is hosted-proven with at least two exact read-only specialists;
- no dynamic topology, write worker, hidden fallback, or authority from model/client input exists.

### WP4: Smart Brain

Status: `STRATEGY_GATED`

Depends on: WP1 and durable execution lessons from WP3.

The current strategic context says not to begin Smart Brain implementation or launch before Shopify Companion has real product and commercial signal. Planning and shared Product Kernel work may proceed. Starting this work package requires either that gate or an explicit strategy decision superseding it.

Tasks after gate approval:

- implement CloudEvents ingress and deterministic trigger registry;
- extend assignment metadata with deployment-local Smart Brain endpoint templates;
- implement durable operations, status/result/cancel/replay, retention and cleanup;
- implement deployment-local Quartz schedules;
- implement typed result store and signed-webhook outbox;
- add promotion draining and decommission guards;
- productize one Behavior/Churn or Incident Intelligence solution pack;
- prove direct data-plane behavior with Platform unavailable after assignment;
- run the full Smart Brain gate matrix.

Exit:

- one event/scheduled profile is hosted-proven, self-contained, durable, directly addressable, read-only, and operationally supportable.

### WP5: Resolver and Human Review extensions

Status: `NOT_STARTED`

Depends on: WP1 and one compatible hosted behavior profile.

Tasks:

- convert current Thinker/Resolver evidence into a reusable extension contract;
- add exact action risk, confirmation, receipt and reconciliation bindings;
- implement one low-risk write profile;
- implement Human Review persistence, delivery, authorization, decision and recovery UI;
- prevent Smart Brain from using the Resolver execution path directly;
- run write/review restart, replay, denial and reconciliation gates.

Exit:

- Resolver and Human Review can attach only to compatible published Product Profiles and cannot widen authority.

### WP6: Capability-pack completion

Status: `NOT_STARTED`

Depends on: WP1. Individual packs may proceed after their target behavior and runtime inventory exist.

Delivery order:

1. Grounded Knowledge and Structured Results;
2. Tool And MCP Integration;
3. Document Knowledge Operations and Knowledge Backfill;
4. Privacy And Governance;
5. Relationship Intelligence;
6. Behavior Intelligence;
7. Inference Reliability and expanded provider/vector combinations.

Each pack must include:

- immutable version and compatibility rules;
- private runtime dependencies/components;
- Platform selection and compilation;
- operator/customer UI where needed;
- entitlement and quota requirements;
- deterministic and real-service verification;
- support, failure, retention and offboarding behavior;
- honest maturity and product claims.

Exit:

- every released AI Fabric feature intended for LoomAI adoption has an explicit product home and maturity state;
- no pack changes the selected behavior's activation, authority, coordination, or durability contract.

### WP7: Commercial and partner readiness

Status: `NOT_STARTED`

Depends on: at least one `HOSTED_PROVEN` generic profile.

Tasks:

- define packages, quotas, provider/vector budgets, limits and support posture;
- implement entitlements without treating entitlements as runtime authority;
- add usage/cost metering and product-aware alerts;
- add onboarding, documentation, privacy, incident, offboarding and deletion workflows;
- expose only published profiles to partners;
- create product pages and claims from verification evidence;
- run design-partner and controlled-production cohorts before broad launch.

Exit:

- the exact profile/tier a customer buys matches the runtime behavior, verification, billing and support contract they receive.

## 14. Recommended Execution Order

```text
WP0 Truth and contracts
  -> WP1 Product Kernel
  -> WP2 Generic Conversational Assistant
  -> WP3 Agentic Specialist Team
  -> WP5 Resolver and Human Review
  -> WP6 capability packs as target behaviors become available
  -> WP7 commercial and partner readiness

WP4 Smart Brain starts only after its strategy gate and reuses WP1/WP3 foundations.
```

Why this order:

- Product Kernel is the common bottleneck.
- Conversational Assistant proves the new product layer with already hosted behavior and lowest runtime risk.
- Agentic Specialist Team captures the principal new `0.6.1` value without first building a new event transport and scheduler product.
- Smart Brain then reuses durable execution, status, replay, lineage, and operations lessons.
- Resolver and Human Review stay separate from read-only chain/Smart Brain behavior.
- Capability packs attach to stable behavior contracts instead of creating new bespoke runtimes.

## 15. First Implementation Batch

The first coding batch should be deliberately narrow:

1. update `010.21` evidence to `0.6.1`;
2. define JSON Schemas for `loomai-behavior-product-template-v1`, `loomai-product-profile-v1`, and `loomai-runtime-capability-bundle-v1`;
3. add the four versioned Platform domain aggregates and migrations;
4. seed the three behavior templates and current conversational runtime bundle;
5. implement lifecycle, validation, exact-version resolution, content hashes, and audit;
6. implement semantic preview and compile one generic Conversational Assistant profile into an existing V04 draft;
7. add runtime capability readback and post-apply comparison;
8. add focused backend tests and one Platform UI source-of-truth view;
9. build, deploy, and verify one generic staging profile;
10. stop and review the Product Kernel evidence before enabling specialist chains or Smart Brain.

First-batch non-goals:

- no chain enablement;
- no Smart Brain endpoints;
- no new write action;
- no arbitrary customer-authored manifests;
- no new Marketplace plugin type;
- no broad partner self-service;
- no public product claim based only on local tests.

## 16. Release And Rollout Policy

For every work package:

1. implement and test locally;
2. build against Maven Central from an empty cache;
3. record exact private source, image digest, AI Fabric version, profile version, runtime bundle version, and migration set;
4. deploy to staging through V04 release;
5. poll the exact deployment UUID to terminal success;
6. run post-apply and behavior-specific verification;
7. run cross-tenant/deployment and missing-boundary security canaries;
8. export the verified configuration before production change;
9. promote or recreate through the supported target-scoped flow;
10. run production canaries and full applicable release gates;
11. record any owner-approved exception without relabeling a failed aggregate gate;
12. retain rollback and draining state until completion criteria are met.

No release may use skipped tests, mutable framework source, locally installed release dependencies, stale pre-apply verification, or a manually edited production-only runtime configuration.

## 17. Productization Risks And Controls

| Risk | Control |
| --- | --- |
| Treating framework proof as a product | Enforce the five-state maturity model |
| One product per module | Keep three behavior products and package modules underneath them |
| Recreating the deployment platform | Compile Product Profiles into existing V04 lifecycle |
| Runtime forks per vertical | One private runtime source line and immutable reviewed bundles |
| Configuration grants authority | Build trusted context server-side and intersect capabilities fail-closed |
| Model-created workflow topology | Exact registered specialists and Java-owned closed definitions only |
| Smart Brain centralizes traffic through Platform | Assignment locates deployment; customer data plane remains direct |
| Smart Brain causes automatic writes | Read-only execution; separate authorized review/resolver boundary |
| Durable execution resumes under changed behavior | Exact hashes and changed-definition rejection |
| Provider fallback hides failure | Only explicitly approved profile behavior; preserve visible failure |
| Documents become a security bypass | Trusted storage, format/source policy, preview, sanitization and lifecycle gates |
| Marketplace pack widens behavior | Compatibility compiler forbids activation/authority/durability changes |
| Product UI leaks framework complexity | Customer-safe guided choices with operator-only technical evidence |
| Capability catalogue drifts from runtime | Runtime bundle inventory and post-apply readback comparison |

## 18. Definition Of Complete Productization

The program is complete when:

1. The Platform has immutable versions of all three Behavior Product Templates.
2. A Product Profile selects exactly one behavior and compiles into the existing V04 lifecycle.
3. Conversational Assistant, Agentic Specialist Team, and Smart Brain each have at least one hosted-proven reusable profile, subject to the Smart Brain strategy gate.
4. Resolver and Human Review attach only through explicit compatible extensions.
5. Every adopted AI Fabric capability has a versioned pack, runtime inventory, Platform binding, verification, operations, and maturity state.
6. Shopify and ProdUS remain valid reference solution deployments without becoming the generic data model.
7. Customer-facing surfaces show behaviors and outcomes rather than Maven modules and raw configuration.
8. Every deployment advertises only endpoints and capabilities it actually provides.
9. Assignment, promotion, export/import, rollback, draining, and decommission work for every behavior.
10. Isolation, missing-boundary, provider failure, persistence failure, restart, replay, and cancellation behavior are verified where applicable.
11. Product claims, tiers, quotas, billing, support, privacy, retention, and offboarding match the exact released profile.
12. The customer application remains the final authority for identity, authorization, validation, transactions, and system-of-record writes.

## 19. Final Implementation Decision

Proceed through one shared LoomAI Product Kernel and three behavior products.

The immediate build target is the Product Kernel plus one generic Conversational Assistant profile. After that path is hosted-proven, adopt the main new AI Fabric `0.6.1` value through a bounded Agentic Specialist Team. Productize Smart Brain only after its existing strategy gate, using deployment-local CloudEvents, durable read execution, direct operation APIs, and deployment-owned result delivery.

This sequence obtains value from the complete AI Fabric capability set without exposing framework internals as products, weakening authority, duplicating the control plane, or creating vertical runtime forks.
