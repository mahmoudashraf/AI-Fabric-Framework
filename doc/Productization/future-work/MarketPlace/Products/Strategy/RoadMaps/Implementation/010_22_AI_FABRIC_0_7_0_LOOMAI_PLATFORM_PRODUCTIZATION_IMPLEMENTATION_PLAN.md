# 010.22 AI Fabric 0.7.0 Behavior-Aware Platform Composition Implementation Plan

Status: implementation plan created on 2026-09-15, corrected on 2026-09-18 to make customer products customer-owned and reuse the existing Marketplace/V04 primitives, aligned on 2026-09-19 with the released AI Fabric `0.7.0` declarative bounded-chain contract, and updated after Gate A completed across the hosted fleet. No productization item is complete merely because AI Fabric exposes the underlying primitive. Completion requires the private runtime, existing Platform workflow, hosted verification, and operations described here.

Canonical architecture:

- [010.21 Consolidated LoomAI Customer Product And Deployment Behavior Architecture](010_21_CONSOLIDATED_LOOMAI_AI_ENABLEMENT_PRODUCT_PROFILE_AND_DEPLOYMENT_ARCHITECTURE.md)

Release foundation:

- AI Fabric `0.7.0`
- Maven group `io.github.loom-ai-labs`
- tag `ai-fabric-framework-v0.7.0`
- release commit `5b075b66384dc5b756b3b3dd12efaf896ce9a50b`
- framework documentation commit `7ac32985`
- published upgrade notes `docs/release-notes/LOOMAI_PLATFORM_AI_FABRIC_0_7_0_UPGRADE_NOTES.md`
- published migration runbook `docs/Framework-Dev-Guides/application-patterns/LOOMAI_AI_FABRIC_0_7_DECLARATIVE_CHAIN_MIGRATION_RUNBOOK.md`
- published adoption prompt `docs/Framework-Dev-Guides/developer-workflows/LOOMAI_AI_FABRIC_0_7_DECLARATIVE_CHAIN_ADOPTION_PROMPT.md`

This document is the executable delivery companion to `010.21`. It does not create a Platform-owned customer-product catalogue, a `Product Profile` lifecycle, a second deployment lifecycle, or product claims from framework demos.

## 1. Objective

Make the complete released AI Fabric capability set usable through the generic LoomAI Platform while preserving these decisions:

1. Platform users create and own their AI-enabled products.
2. Conversational Assistant, Agentic Specialist Team, and Smart Brain are supported deployment behavior types, not LoomAI-owned customer products.
3. Resolver and Human Review are controlled execution extensions.
4. RAG, indexing, documents, MCP, PII, relationship query, behavior analysis, migration, providers, vectors, structured output, and UI are capability packs, managed services, or channels.
5. Shopify, ProdUS, support, incident, churn, CRM, and similar domains are customer solutions, Marketplace templates, or reference deployments.
6. Platform remains the deterministic control plane.
7. Each deployed runtime remains its own data plane.
8. Customer applications retain identity, authorization, domain validation, transactions, and system-of-record authority.
9. Greenfield rules apply. Do not add compatibility readers, old release fallbacks, duplicate control surfaces, placeholder implementations, or product-domain text matching.
10. A capability may be offered as Platform-supported only after an exact hosted V04 composition passes its applicable verification packs.
11. Existing Marketplace lifecycles, deployment templates, curated modules, profiles, managed services, V04 drafts/versions, release, verification, assignment, and export/import are reused as the implementation spine. A governed `SPECIALIST` contribution type is the only planned Marketplace extension needed to package reusable specialist and official `SpecialistChain` resources.

## 2. Supported Platform Behaviors And Customer Product Possibilities

### 2.1 Primary deployment behavior types

| Deployment behavior type | Customer-product value | Activation | Coordination | Initial maturity |
| --- | --- | --- | --- | --- |
| `CONVERSATIONAL` | A customer can build a product where a person asks and receives an answer, clarification, structured result, or governed next step | Authenticated interactive request | One bounded orchestration turn with optional backend-owned session | Hosted-proven through Shopify and ProdUS; generic Marketplace template required |
| `AGENTIC_SPECIALIST_TEAM` | A customer can build a product where a bounded manager coordinates approved specialists for a larger task | Authenticated interactive or trusted application request | Exact-version manager and read-only workers through an official declarative `SpecialistChain`, a reviewed Java definition when richer application logic is required, or a fixed plan | AI Fabric available; LoomAI deployment productization required |
| `SMART_BRAIN` | A customer can build a product where trusted events and schedules initiate proactive analysis without a fabricated chat turn | Trusted application, CloudEvent, or managed schedule | Exact specialist, bounded read-only chain, or explicitly selected fixed read plan | AI Fabric primitives available; LoomAI deployment productization required |

### 2.2 Execution extensions

| Extension | Purpose | Compatible deployment behaviors | Boundary |
| --- | --- | --- | --- |
| Governed Resolver | Convert an approved recommendation into one registered, validated, confirmed write | Conversational Assistant and selected Agentic Specialist Team deployments | Application executes and reconciles the side effect |
| Human Review | Route a bounded proposal or result to an authorized reviewer | Conversational Assistant, Agentic Specialist Team, or a separate review boundary fed by Smart Brain | Model never selects or impersonates the reviewer |

Smart Brain execution remains read-only in the first released contract. It may emit a typed result or create an application-owned review request through a separately authorized boundary. It may not execute an event-triggered write.

### 2.3 Capability and managed-service packs

| Reusable capability area | AI Fabric foundation | LoomAI Platform/runtime responsibility |
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

### 3.2 Implemented but not generally reusable through the Platform

- Shopify package/tier/profile mapping is a strong vertical precedent but remains Shopify-specific commercial configuration, not the generic customer-product model.
- Max Mode, docked composer, inline/result-card concepts, and query-once exist but are not one stable reusable channel package.
- Managed MCP infrastructure is hosted-proven, but every customer-facing server/tool pack still needs its own live proof.
- Exact specialist execution is hosted-proven. AI Fabric `0.7.0` adds official declarative bounded-chain resources, but chains remain disabled in the private runtime until the separate schema and canary gates pass.
- Current curated runtime behavior is limited to the default, commerce, support, and deployment-knowledge paths.

### 3.3 Missing Platform contracts

- built-in Deployment Behavior Type field/contract and compatibility registry;
- behavior-aware Marketplace `TEMPLATE` manifest and bootstrap support;
- governed Marketplace `SPECIALIST` contributions for exact specialist, schema, prompt-reference, and official `SpecialistChain` resource bundles;
- V04 composition provenance recording exact template/plugin/profile/service references and hashes;
- runtime capability inventory tied to existing deployment source artifacts and verified runtime readback;
- reusable behavior/plugin Verification Pack registry bound through the existing release path;
- reusable Agentic Specialist Team runtime components and data-plane API;
- deployment-local Smart Brain ingress, durable operations, result storage, scheduling, and delivery;
- reusable Resolver and Human Review packages;
- generic Platform operations for PII, governance, relationship, behavior, migration, and document ingestion;
- entitlement, quota, billing, support, and offboarding rules for the new deployment capabilities.

### 3.4 Current evidence status

`010.21` records the source and hosted fleet baseline separately. Historical rollout detail remains in the working/private handoff records.

Current operational truth is:

- all supported private runtime families now use AI Fabric `0.7.0` from immutable private commit `2ee86b7761aa4f0d81224cc4da30a5e2b4c7a264`;
- specialist chains remain disabled across source defaults, generated deployment configuration, and live runtime readback;
- all seven supported staging/production runtime-family releases are `APPLIED_VERIFIED`, healthy, and backed by terminal-success Coolify deployments;
- ProdUS is assigned to verified deployment `dep-f6abfa06`;
- staging and production canonical, Partner, Thinker, direct-specialist, isolation, and ProdUS grounded retrieval checks passed;
- aggregate release suites still report failure only for the explicitly owner-deferred Shopify retrieval-quality stage;
- that exception permits the framework rollout but must not be relabeled as a fully green aggregate release gate.

### 3.5 Mandatory AI Fabric `0.7.0` adoption gates

The framework runbook deliberately separates release adoption from chain productization. Do not collapse these gates into one deployment:

| Gate | Scope | Current status | Exit evidence |
| --- | --- | --- | --- |
| A. Base release | Upgrade every private dependency/default to `0.7.0`; keep `AI_EXECUTION_SPECIALIST_CHAINS_ENABLED=false`; add no chain resource, table, secret, or route | **COMPLETE** at private commit `2ee86b7761aa4f0d81224cc4da30a5e2b4c7a264` | Central-only package proof, exact runtime JAR inventory, seven verified V04 releases, hosted version/health, direct deployment-knowledge success, missing-boundary denial, two-tenant/two-deployment isolation, ProdUS retrieval, and existing capability regression passed |
| B. Schema preparation | Add deployment-local `ai_specialist_chain_execution` migration while chains remain disabled | Not started | Migration applies idempotently, runtime still starts with chains disabled, no secret or chain resource required, rollback/redeploy evidence recorded |
| C. Declarative mechanics canary | Package one reviewed one-worker chain using `deployment-knowledge-specialist@1`, stable private secrets, JDBC persistence, and deployment-local route/status projection | Not started; blocked on Gate B and a reviewed Gate C start decision | Offline validation, startup/readback hashes, direct baseline parity, trusted-context denial, replay/restart/cancel/definition-drift/provider-failure canaries |
| D. Product chain | Introduce a real multi-specialist chain only when a genuinely distinct second read-only worker exists | Product decision pending | At least two distinct responsibilities, exact closed targets, hosted V04 proof, Marketplace/V04 packaging, full Agentic gate matrix |

The required stop after Gate A has occurred and its hosted evidence is recorded. Gate B and Gate C require a new reviewed start decision. A one-worker Gate C canary proves mechanics only; it is not an Agentic Specialist Team market claim.

## 4. Capability Maturity Model

Every behavior/capability offered through a reusable template/plugin composition must move through these states in order:

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
| `PLATFORM_SELECTABLE` | A supported behavior or published Marketplace template/plugin can compile the capability into an existing V04 draft |
| `HOSTED_PROVEN` | Packaged staging runtime passes deterministic, real-provider, security, restart, and product canaries |
| `MARKET_READY` | Entitlements, quotas, billing, support, privacy, documentation, claims, and production rollout are approved |

Rules:

- States cannot be skipped.
- A framework demo proves only `FRAMEWORK_AVAILABLE`.
- A runtime flag does not prove `PLATFORM_SELECTABLE`.
- A healthy container does not prove `HOSTED_PROVEN`.
- Platform claims must use the maturity of the exact V04 composition and verification evidence, not the highest maturity of any underlying primitive.

## 5. Target Platform Architecture

### 5.1 Reuse-first customer deployment composition

```text
customer-owned product intent
  + one built-in Deployment Behavior Type contract
  + optional published Marketplace TEMPLATE plugin version
  + installed DATA / ACTION / INFERENCE_PROFILE / SPECIALIST plugin versions
  + allowed Execution Extensions
  + approved Channel Bindings
  + one existing deployment source artifact and runtime capability inventory
  + one curated module and runtime profile
  + one inference profile
  + optional vector profile
  + one deployment topology template
  + one deployment target profile
  + managed-service bindings
  + applicable verification-pack IDs
  -> existing V04 Deployment Draft
  = existing immutable V04 Deployment Version with composition provenance
  -> existing Release
  -> existing Verification
  -> existing Consumer Assignment
```

Composition is an operation performed by the existing Marketplace bootstrap/compiler and deployment services. It is not a separately persisted or published Platform object. The V04 Deployment Version is the only immutable deployable definition.

### 5.2 Runtime capability inventory on existing source artifacts

Extend the existing `DeploymentSourceArtifact` metadata and post-apply runtime readback with a content-hashed capability manifest. Do not add a customer-facing runtime product or separate release lifecycle.

The inventory identifies:

- private runtime image digest and source commit;
- AI Fabric version;
- directly packaged AI Fabric modules;
- exact Java component IDs;
- curated modules and prompt packs;
- specialist manifests and effective hashes;
- official declarative chain resources and hashes, plus any reviewed Java definitions used for richer application-owned mappings;
- application-owned migrations;
- supported endpoint classes;
- supported activation sources;
- required managed services and secrets;
- capability and limit inventory;
- baseline runtime verification-pack IDs.

All behavior types use one private runtime source line. A new behavior must not create a copied runtime fork. Build variants are allowed only for a reviewed security or footprint requirement, and remain source artifacts in the same existing promotion and verification model.

Initial capability postures:

| Posture | Purpose | Initial state |
| --- | --- | --- |
| Conversational inventory | Current interactive orchestration, sessions, RAG, Data Sync, actions, MCP, query-once | Derive from current hosted artifact/readback and formalize |
| Agentic inventory | Conversational capabilities plus exact specialist teams and durable chains | Add to the same runtime source after behavior-aware compilation exists |
| Smart Brain inventory | Trusted event/schedule activation, durable read jobs/chains, operations and output delivery | Add after the Agentic durability foundation |

### 5.3 Application-owned components

Some AI Fabric features cannot be safely enabled through a generic Platform flag. LoomAI must supply reviewed private components:

- official `SpecialistChain` manifests where bounded JSON mapping/projection is sufficient;
- reviewed Java chain definitions only where application-owned invariants require richer typed logic;
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

Do not invent a LoomAI chain language, dynamic target discovery, arbitrary scripts, arbitrary URLs, raw SQL, or generic execute-anything endpoints. Use only the released `ai.fabric/v1` `SpecialistChain` contract, validated against the exact runtime authoring catalogue and deployment ceilings before publication.

## 6. Minimal Extensions To Existing Platform Data

Use the next available Flyway migration numbers at implementation time. Preserve existing immutable deployment records and do not add compatibility tables for abandoned shapes.

### 6.1 Explicitly rejected parallel aggregates

Do not create:

- `behavior_product_template` or `behavior_product_template_version`;
- `product_profile` or `product_profile_version`;
- a second draft/version/publish/release lifecycle; or
- a second plugin catalogue for solution/capability packs.

The three behavior contracts are reviewed Platform code/config. Reusable customer-solution definitions are existing Marketplace `TEMPLATE` plugin versions. Immutable deployable truth is the existing V04 Deployment Version.

### 6.2 Extend existing deployment records

Add only the data needed for behavior selection and deterministic provenance:

| Existing owner | Minimal addition | Purpose |
| --- | --- | --- |
| `platform_deployments` / `DeploymentEntity` | `behavior_type` | List/filter deployments and make behavior explicit from creation |
| `platform_deployment_drafts` / draft API | `behavior_config_json` using `loomai-deployment-behavior-v1` | Validate mutable activation, coordination, extension, durability, and endpoint requirements |
| `platform_deployment_versions` / `DeploymentVersionEntity` | immutable `behavior_config_json` and `composition_provenance_json` | Preserve exact behavior and every source template/plugin/profile/service/verification reference and hash |
| `deployment_source_artifacts` / `DeploymentSourceArtifactEntity` | `capability_manifest_json` and `capability_manifest_hash` | Bind image/source identity to packaged modules, components, endpoint classes, migrations, specialists, chains, and limits |

The exact Java/storage shape may consolidate fields if the canonical V04 config already has an appropriate section, but the ownership boundaries above are mandatory.

Existing deployments receive a one-time deterministic migration from authoritative deployment inventory. Do not implement a runtime fallback that guesses behavior from prompts, endpoint use, curated-module text, or product-domain names.

### 6.3 Extend existing Marketplace `TEMPLATE` versions

Add optional validated fields under `contributions.template`:

- `deploymentBehavior.type` and `deploymentBehavior.contractVersion`;
- exact required plugin references;
- recommended plugin references;
- required runtime capability IDs;
- compatible Deployment Template/runtime/inference/vector/target constraints;
- allowed execution extensions and channels;
- verification-pack IDs; and
- unsupported claims/customer-safe boundaries.

Keep current ownership intact:

- `TEMPLATE` contributes deployment starting state, curated module, shell/security baseline, and compatibility;
- `DATA` contributes approved entities, knowledge sources, datasets, and sync behavior;
- `ACTION` contributes registered actions, MCP/webhook execution bindings, schemas, and policy metadata;
- `INFERENCE_PROFILE` contributes provider/model/embedding configuration;
- `SPECIALIST` contributes reviewed immutable specialist execution resources as defined below;
- Deployment Template owns infrastructure topology;
- Target Profile owns placement and credentials;
- managed product services own reusable service lifecycle; and
- the V04 compiler/version owns the final resolved desired state.

### 6.4 Add one governed Marketplace `SPECIALIST` contribution type

AI Fabric `0.7.0` removes the reason to encode ordinary chain topology in private Java code. Add `SPECIALIST` to the existing Marketplace plugin lifecycle rather than creating a chain catalogue or a second release system.

A published `SPECIALIST` version may contribute:

- exact `ai.fabric/v1` specialist resources;
- exact `ai.fabric/v1` `SpecialistChain` resources;
- referenced prompt and input/output JSON Schema resources with content hashes;
- required registered vector spaces, allowed actions, framework capabilities, and curated/runtime profiles;
- required application-owned migration IDs and secret names, never migration SQL or secret values;
- deployment ceilings that may only narrow the server-owned maximums;
- compatible behavior types and activation sources;
- verification-pack IDs and unsupported claims.

Publication must run the framework's offline `SpecialistChainManifestValidator` against the exact source-artifact authoring catalogue and Platform-owned ceilings. Installation compiles only published exact versions into the V04 draft. Publishing a V04 version freezes plugin identity, resource hashes, resolved exact manager/worker IDs, catalogue hash, ceilings, and verification requirements.

For a Platform-selectable deployment, apply materializes the approved resources as a read-only, content-addressed deployment artifact and configures the runtime's manifest locations to that artifact. The runtime still uses AI Fabric's single manifest loader, registry, and gateway. Startup fails closed on missing, changed, duplicate, invalid, or incompatible resources. No browser, event payload, prompt, or model may submit a manifest or choose an undeclared target at runtime.

The first Gate C mechanics canary is intentionally narrower: package one reviewed one-worker chain in the private runtime source artifact. It proves the framework/runtime mechanics but does not make `SPECIALIST` Platform-selectable or market-ready. Marketplace materialization, V04 provenance, runtime readback, and hosted multi-worker proof are still required for productization.

Use a reviewed Java `SpecialistChainDefinition` only when the released declarative mapping and bounded projection contract cannot express a genuine application-owned invariant. Record that Java component in the source-artifact capability manifest; it is not customer-authored and is not carried as Marketplace executable content.

### 6.5 Reuse existing verification and entitlement records

Verification packs are reviewed Platform code/config keyed by stable IDs and versions. Store selected IDs and results with existing deployment-version/release/verification records. Add a new persistence aggregate only if current verification-suite storage cannot retain required immutable identity after implementation discovery.

Commercial packages and Marketplace entitlements constrain selectable templates, plugins, providers, quotas, and support. They are evaluated before compilation and release, but they do not become part of Deployment Behavior Type semantics or trusted runtime authorization.

Do not store customer-supplied executable shell, SQL, expressions, code images, or migrations in Marketplace templates or V04 composition provenance. Verification implementations and runtime migrations remain reviewed Platform/private-runtime code.

## 7. Extensions To Existing Platform Backend Contracts

Exact URLs should follow current controller conventions. Prefer extending existing endpoints and services over introducing a new top-level API family.

### 7.1 Read-only behavior catalogue

Expose the three server-owned behavior contracts with:

- code and contract version;
- activation and coordination semantics;
- compatible extensions/channels;
- durability and endpoint requirements;
- required runtime capabilities;
- unsupported claims;
- baseline verification-pack IDs; and
- current maturity/hosted evidence.

There is no behavior draft, publish, retire, or customer CRUD endpoint.

### 7.2 Existing Marketplace and deployment authoring APIs

Extend current APIs to:

- show behavior metadata and compatibility on published `TEMPLATE` versions;
- show validated specialist and chain resource identities on published `SPECIALIST` versions;
- create a deployment directly with a supported behavior or bootstrap it from a published template;
- install exact required/recommended plugins through existing install records and entitlement checks;
- resolve server-owned compatible options;
- validate the active V04 draft across behavior, template/plugins, runtime capability inventory, profiles, topology, target, secrets, and verification coverage;
- preview semantic composition and infrastructure requirements;
- publish through the existing immutable V04 version endpoint; and
- show source composition provenance on deployment/version/release views.

### 7.3 Existing source-artifact and runtime inventory APIs

Extend current source-artifact/operator surfaces to:

- expose the reviewed capability manifest and hash;
- compare V04 requirements with source-artifact claims and live runtime readback;
- show exact image digest, source commit, AI Fabric version, specialist/chain source and effective hashes, catalogue hash, endpoint classes, migrations, and required secret names to authorized operators;
- project safe fields from AI Fabric `SpecialistChainManifestRuntimeStatus`, including load state and aggregate hashes;
- reject release before apply when required packaged capability is absent; and
- fail post-apply verification when safe runtime readback does not match the selected source artifact or V04 requirements.

The runtime readback must never expose secret values, protected prompts/payloads, provider responses, or application records.

### 7.4 Existing composition/compiler path

The existing `MarketplaceTemplateBootstrapService`, `DeploymentMarketplaceDraftCompilerService`, `DeploymentDraftValidationService`, `DeploymentConfigCompiler`, and V04 publish/release services must together:

1. resolve only supported behavior contracts and published exact Marketplace versions;
2. require exactly one behavior type;
3. validate behavior, activation, channel, extension, durability, runtime, provider, vector, topology, target, Marketplace, entitlement, secret, and verification compatibility;
4. validate every declarative chain bundle offline against the selected source artifact's exact authoring catalogue and Platform ceilings;
5. resolve server-owned defaults;
6. compile contributions into existing V04 config sections and one read-only content-addressed execution-resource artifact;
7. emit semantic preview and deterministic hashes;
8. record every source reference/hash in immutable composition provenance;
9. reject unresolved or unsupported capability claims; and
10. avoid live Marketplace or catalogue lookups from the deployed runtime request path.

### 7.5 Existing export/import

Extend existing export/import only where needed to include exact behavior contract, Marketplace template/plugin versions and hashes, declarative specialist/chain bundle hashes, source-artifact capability manifest/hash, curated module, profiles, managed-service bindings, channels/extensions, and verification-pack references.

Import preview must distinguish:

- portable V04 behavior/capability intent;
- target-scoped infrastructure references requiring rewrite;
- secrets requiring rebinding;
- unavailable versions or capabilities; and
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
- one immutable official `ai.fabric/v1` `SpecialistChain` resource when JSON-pointer mapping and bounded fact/evidence projection are sufficient;
- declarative input mapping from `CHAIN_INPUT` or `MANAGER_OBJECTIVE`;
- bounded target result projection;
- closed target catalogue;
- chain and deployment ceilings;
- JDBC repository and application-owned migration;
- stable distinct encryption and fingerprint secrets.

A reviewed Java chain definition is allowed only for a real application-owned invariant that the released declarative contract cannot represent. The deployment capability inventory and product claim must say which source was used.

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

Callback URLs and signing secrets come from the immutable compiled V04 configuration. Event payloads cannot select them.

Promotion behavior:

1. assignment changes route new submissions to the new verified runtime;
2. already accepted operations remain on the original runtime;
3. old runtime endpoints remain reachable for assignment-cache grace and operation draining;
4. old runtimes decommission only after jobs and result deliveries reach terminal state or an explicit operator decision is recorded.

No non-HTTP adapter is needed for V1. Add Kafka, SQS/EventBridge, Service Bus/Event Grid, Pub/Sub, NATS, RabbitMQ, or vendor bridges only through a future governed `INTEGRATION` Marketplace type with allowlisted implementations. Do not overload `DATA` or `ACTION` plugins.

## 9. Private Runtime Packaging

### 9.1 Common runtime work

- preserve one BOM and one resolved AI Fabric version;
- use the framework's shared Java/manifest registry and gateway; do not add a LoomAI chain engine;
- validate declarative resources offline and fail startup on invalid runtime resources;
- add optional modules only through reviewed runtime dependencies;
- fail startup when compiled V04 requirements exceed runtime inventory;
- emit source commit, image digest, framework version, behavior contract, composition hash, specialist/chain hashes, storage posture, and endpoint classes through safe health/readback;
- use application-owned Flyway migrations for durable execution state;
- keep schema auto-initialization disabled in production;
- keep secrets stable across replicas, restart, promotion grace, and rollback windows;
- use no locally installed framework artifact in release builds.

### 9.2 Module adoption groups

| Group | Modules/capabilities | Platform/runtime action |
| --- | --- | --- |
| Existing conversational | provider, curated modules, RAG, vector providers, chat session, Data Sync, connector actions, retrieval connector | formalize source-artifact inventory and reusable Marketplace template |
| Agentic execution | `ai-fabric-execution` direct specialists, waits, plans, conversation manager, durable jobs, official declarative chains, receipts, review | add reviewed resources/components and enable through behavior-aware V04 config |
| Privacy | PII and governance | add dependencies, policy config, storage/retention, endpoints and gates only when pack is built |
| Data intelligence | relationship query and behavior | add exact schema/event adapters, authorization and result projections |
| Knowledge operations | indexing, migration/backfill, document-reader integration | add managed source, job and lifecycle UX rather than exposing internal queues |
| Action lifecycle | local/connector/registry actions, confirmation, receipts, review | package as Resolver/Human Review extensions with system-of-record reconciliation |

### 9.3 Configuration policy

Capabilities stay disabled unless selected by a supported behavior plus published/installed Marketplace versions, compiled into V04, and provided by the exact source artifact/runtime inventory.

Configuration must be generated from server-owned catalogues. Do not expose raw environment variables, module names, specialist targets, or arbitrary capability switches to customer-facing UI.

## 10. Platform And Partner UI

### 10.1 Behavior and template catalogue

Extend the existing deployment/Marketplace creation surfaces to show:

- the three Deployment Behavior Types as execution choices;
- customer-product possibilities and supported activation model;
- maturity state;
- compatible Marketplace templates/plugins, extensions, and channels;
- required managed services and deployment topology;
- exact hosted evidence and blockers.

Framework module names belong in an operator detail view, not the primary creation choices. The UI must make clear that the user is creating the customer product; LoomAI is providing deployment capabilities.

### 10.2 Existing deployment authoring flow

Use a guided workflow:

1. name the customer product/deployment and select environment/customer/tenant;
2. choose a supported behavior directly or start from a published Marketplace `TEMPLATE`;
3. install/select server-resolved compatible `DATA`, `ACTION`, `INFERENCE_PROFILE`, and `SPECIALIST` plugins;
4. choose compatible extensions and channels;
5. choose allowed Deployment Template, provider/vector/runtime posture, managed services, and Target Profile;
6. apply account entitlements/quotas as constraints;
7. review security, durability, cost, unsupported claims, and exact source references;
8. validate the existing V04 draft;
9. preview compiled V04 changes and composition provenance;
10. publish and launch through the existing deployment workflow.

Do not present a large free-form configuration matrix. Incompatible choices should be absent or disabled with a concrete server-provided reason.

### 10.3 Runtime operations

Add product-aware views for:

- selected behavior contract, Marketplace template/plugin versions, source artifact, and composition hash;
- active endpoints and assignment;
- knowledge/indexing status;
- specialist and chain status;
- Smart Brain queues, leases, result deliveries, and dead letters;
- pending confirmations, receipts, and reviews;
- provider/vector/MCP health;
- quality and release-gate history;
- promotion draining and decommission blockers.

### 10.4 Partner experience

Partners select published Marketplace templates/plugins and customer-safe options through the same deployment workflow. They may submit declarative specialist resources for review through the Marketplace publishing lifecycle, but cannot publish them directly, add executable content, broaden the source-artifact authoring catalogue, supply authority, or bypass verification. Custom solution-template work remains reviewed and versioned before it appears as a selectable option.

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

Every behavior-aware V04 deployment must pass:

1. exact AI Fabric `0.7.0` Central-only dependency resolution;
2. private runtime unit and integration tests without skipped normal tests;
3. packaged container boot with exact source/image/framework/behavior/composition identity;
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

Status: `IN_PROGRESS`

Tasks:

- keep `010.21` ownership, reuse, `0.7.0` framework capability, and separate hosted-fleet evidence current;
- create the capability maturity registry;
- inventory current runtime modules, endpoints, migrations, secrets, and verification;
- freeze schema names and namespace ownership;
- reserve candidate capability codes;
- define acceptance evidence and owners for every later work package.

Exit:

- one reviewed source states what is framework-available, runtime-packaged, Platform-selectable, hosted-proven, and market-ready;
- no existing capability is overclaimed or lost.

### WP1: Behavior-aware existing Marketplace/V04 composition path

Status: `NOT_STARTED`

Depends on: WP0.

Tasks:

- add the built-in Deployment Behavior Type contract and minimal draft/version fields;
- extend existing `TEMPLATE` manifest validation/bootstrap and exact required-plugin installation;
- add the governed `SPECIALIST` Marketplace contribution type and lifecycle;
- compile reviewed `Specialist` and `SpecialistChain` resources into a read-only content-addressed V04 deployment artifact;
- validate chain resources with the framework validator, exact source-artifact authoring catalogue, and Platform ceilings before publish and release;
- extend source artifacts with capability manifests and verify them against live runtime readback;
- implement deterministic cross-primitive validation and compatibility resolution;
- add composition provenance, semantic preview, and hashes to existing V04 versions;
- extend existing export/import, audit, and release verification;
- extend current deployment/Marketplace creation UI;
- cover migration, manifest validation, bootstrap, compiler, authorization, V04 lifecycle, and export/import with tests.

Exit:

- a published Marketplace `TEMPLATE` bootstraps and compiles deterministically into V04 without manual environment editing;
- invalid or missing versions fail before release;
- no parallel deployment lifecycle exists.

### WP2: Generic Conversational Assistant baseline

Status: `NOT_STARTED`

Depends on: WP1.

Tasks:

- formalize the conversational capability manifest on the existing runtime source artifact/readback;
- extract generic template compatibility choices from the Shopify precedent without moving Shopify-specific logic;
- package reusable chat, session, query-once, structured result, docked composer, Max Mode, and inline bindings;
- publish one generic Grounded Knowledge Marketplace `TEMPLATE` with required plugin/verification references;
- deploy a staging canary independent of Shopify;
- run generic plus Shopify and ProdUS regression packs;
- prove promotion, assignment, export/import, and rollback.

Exit:

- `CONVERSATIONAL` is selectable as a Platform deployment behavior rather than only implicit in Shopify/ProdUS configuration;
- one generic template-backed hosted V04 deployment reaches `HOSTED_PROVEN`.

### WP3: Agentic Specialist Team

Status: `NOT_STARTED`

Depends on: WP1 and shared runtime identity/storage work. WP2 should establish the normal template/compiler/V04 release path first.

Tasks:

- select one design-partner canary, preferably deployment/incident investigation or account/support resolution;
- use the completed Gate A release as the immutable chain-disabled baseline;
- complete Gate B by adding the deployment-local PostgreSQL migration while chains remain disabled;
- complete Gate C with one private-source, one-worker declarative mechanics canary over `deployment-knowledge-specialist@1`;
- define a genuinely distinct second read-only specialist responsibility before Gate D;
- publish exact manager/workers and the official declarative `SpecialistChain` through a reviewed `SPECIALIST` plugin when released mapping/projection is sufficient;
- use private Java chain components only for a documented application invariant that the declarative contract cannot express;
- configure the JDBC repository, stable distinct secrets, workers, leases, recovery, retention, and cleanup;
- enable chains only when `AGENTIC_SPECIALIST_TEAM` V04 requirements match the selected source-artifact/runtime inventory;
- expose authenticated execute/submit/status/result/cancel/replay contracts;
- add safe lineage and Platform operation UI;
- run the complete Agentic gate matrix;
- promote one verified template-backed deployment to a controlled production canary.

Exit:

- one reusable Agentic Specialist Team Marketplace template/V04 deployment is hosted-proven with at least two exact read-only specialists;
- no dynamic topology, write worker, hidden fallback, or authority from model/client input exists.

### WP4: Smart Brain

Status: `STRATEGY_GATED`

Depends on: WP1 and durable execution lessons from WP3.

The current strategic context says not to begin Smart Brain implementation or launch before Shopify Companion has real product and commercial signal. Planning and shared Marketplace/V04 composition work may proceed. Starting this work package requires either that gate or an explicit strategy decision superseding it.

Tasks after gate approval:

- implement CloudEvents ingress and deterministic trigger registry;
- extend assignment metadata with deployment-local Smart Brain endpoint templates;
- implement durable operations, status/result/cancel/replay, retention and cleanup;
- implement deployment-local Quartz schedules;
- implement typed result store and signed-webhook outbox;
- add promotion draining and decommission guards;
- publish and prove one Behavior/Churn or Incident Intelligence Marketplace template;
- prove direct data-plane behavior with Platform unavailable after assignment;
- run the full Smart Brain gate matrix.

Exit:

- one event/scheduled template-backed V04 deployment is hosted-proven, self-contained, durable, directly addressable, read-only, and operationally supportable.

### WP5: Resolver and Human Review extensions

Status: `NOT_STARTED`

Depends on: WP1 and one compatible hosted behavior-aware deployment.

Tasks:

- convert current Thinker/Resolver evidence into a reusable extension contract;
- add exact action risk, confirmation, receipt and reconciliation bindings;
- implement one low-risk write template/deployment composition;
- implement Human Review persistence, delivery, authorization, decision and recovery UI;
- prevent Smart Brain from using the Resolver execution path directly;
- run write/review restart, replay, denial and reconciliation gates.

Exit:

- Resolver and Human Review can attach only to compatible behavior-aware V04 deployments and cannot widen authority.

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

Each reusable Marketplace template/plugin capability must include:

- immutable version and compatibility rules;
- private runtime dependencies/components;
- Platform selection and compilation;
- operator/customer UI where needed;
- entitlement and quota requirements;
- deterministic and real-service verification;
- support, failure, retention and offboarding behavior;
- honest maturity and product claims.

Exit:

- every released AI Fabric feature intended for Platform adoption has an explicit Marketplace/private-runtime/config owner and maturity state;
- no template/plugin changes the selected behavior's activation, authority, coordination, or durability contract.

### WP7: Commercial and partner readiness

Status: `NOT_STARTED`

Depends on: at least one `HOSTED_PROVEN` generic template-backed deployment.

Tasks:

- define packages, quotas, provider/vector budgets, limits and support posture;
- implement entitlements without treating entitlements as runtime authority;
- add usage/cost metering and product-aware alerts;
- add onboarding, documentation, privacy, incident, offboarding and deletion workflows;
- expose only published Marketplace templates/plugins and supported behavior choices to partners;
- create product pages and claims from verification evidence;
- run design-partner and controlled-production cohorts before broad launch.

Exit:

- the exact entitlement/tier a customer buys constrains the templates, plugins, resources, runtime behavior, verification, billing, and support they receive without becoming runtime authority or product identity.

## 14. Recommended Execution Order

```text
WP0 Truth and contracts
  -> WP1 behavior-aware existing Marketplace/V04 composition
  -> WP2 Generic Conversational Assistant
  -> WP3 Agentic Specialist Team
  -> WP5 Resolver and Human Review
  -> WP6 capability packs as target behaviors become available
  -> WP7 commercial and partner readiness

WP4 Smart Brain starts only after its strategy gate and reuses WP1/WP3 foundations.
```

Why this order:

- Behavior-aware use of the existing Marketplace/V04 path is the common bottleneck.
- Conversational Assistant proves the extended composition path with already hosted behavior and lowest runtime risk.
- Agentic Specialist Team captures the principal new `0.7.0` declarative-chain value without first building a new event transport and scheduler product.
- Smart Brain then reuses durable execution, status, replay, lineage, and operations lessons.
- Resolver and Human Review stay separate from read-only chain/Smart Brain behavior.
- Marketplace templates/plugins attach to stable behavior contracts instead of creating new bespoke runtimes.

## 15. First Implementation Batch

The `0.7.0` release-adoption batch is Gate A and is deliberately separate from productization. It is complete:

1. pin all private source and generated deployment defaults to `0.7.0`;
2. keep chains disabled everywhere;
3. add no chain migration, resource, secret, or route;
4. run all three Maven reactors normally;
5. run an empty-cache Maven Central build and inspect packaged framework JARs;
6. commit and deploy one immutable private source revision;
7. run the hosted Gate A version, health, direct-specialist, isolation, and existing-capability regression matrix;
8. stop and record evidence before Gate B or C.

Completion evidence is recorded in
`Final_Documentation/Development_Guides/LLM-guides/AI_FABRIC_0_7_0_PLATFORM_MIGRATION/README.md`.
No chain migration, resource, private chain secret, route, or selectable chain
product was introduced. The aggregate gate remains non-green only for the
owner-deferred Shopify first-product answer-quality stage; that exception does
not weaken or overstate the Gate A migration result.

The first Platform productization batch after Gate A should be deliberately narrow:

1. freeze `loomai-deployment-behavior-v1` with `CONVERSATIONAL`, `AGENTIC_SPECIALIST_TEAM`, and `SMART_BRAIN`;
2. add behavior/config/provenance fields to the existing deployment/draft/version models and perform a deterministic data migration;
3. add `SPECIALIST` as a governed contribution type and freeze its non-executable resource-bundle schema;
4. extend `TEMPLATE` manifest validation/bootstrap with behavior, required plugins/runtime capabilities, and verification-pack references;
5. add capability manifest/hash to existing deployment source artifacts and safe runtime readback;
6. implement cross-primitive validation, exact-version resolution, semantic preview, provenance hashes, and audit in existing services;
7. bootstrap and compile one generic Conversational Assistant Marketplace template into an existing V04 draft;
8. extend export/import and post-apply source-artifact/runtime comparison;
9. add focused backend tests and one Platform UI source-of-truth view;
10. build, deploy, and verify one generic template-backed staging deployment;
11. stop and review the existing-lifecycle evidence before enabling specialist chains or Smart Brain.

First-batch non-goals:

- no chain enablement;
- no Smart Brain endpoints;
- no new write action;
- no unreviewed or runtime-submitted manifests;
- no chain activation before Gates B and C;
- no broad partner self-service;
- no public product claim based only on local tests.

## 16. Release And Rollout Policy

For every work package:

1. implement and test locally;
2. build against Maven Central from an empty cache;
3. record exact private source, image digest, AI Fabric version, behavior contract, template/plugin versions, composition hash, source-artifact capability hash, and migration set;
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

## 17. Platform Productization Risks And Controls

| Risk | Control |
| --- | --- |
| Treating framework proof as a product | Enforce the five-state maturity model |
| Treating behavior types as LoomAI-owned customer products | State customer ownership in APIs/UI/docs and present behaviors as deployment capabilities |
| One product per module | Customers compose products from three behavior types and reusable Marketplace/runtime capabilities |
| Recreating the deployment platform | Extend current Marketplace bootstrap/compiler and V04 lifecycle only |
| Recreating a Product Profile catalogue | Use published `TEMPLATE` versions for reuse and V04 versions for immutable deployable truth |
| Runtime forks per vertical | One private runtime source line and immutable promoted source artifacts |
| Configuration grants authority | Build trusted context server-side and intersect capabilities fail-closed |
| Model-created workflow topology | Exact registered specialists and validated published `SpecialistChain` resources or reviewed Java-owned closed definitions only; the model may choose only within the declared target catalogue |
| Smart Brain centralizes traffic through Platform | Assignment locates deployment; customer data plane remains direct |
| Smart Brain causes automatic writes | Read-only execution; separate authorized review/resolver boundary |
| Durable execution resumes under changed behavior | Exact hashes and changed-definition rejection |
| Provider fallback hides failure | Only explicitly approved V04 provider behavior; preserve visible failure |
| Documents become a security bypass | Trusted storage, format/source policy, preview, sanitization and lifecycle gates |
| Marketplace pack widens behavior | Compatibility compiler forbids activation/authority/durability changes |
| Product UI leaks framework complexity | Customer-safe guided choices with operator-only technical evidence |
| Capability catalogue drifts from runtime | Source-artifact capability manifest plus post-apply readback comparison |

## 18. Definition Of Complete Platform Support

The program is complete when:

1. The Platform exposes reviewed built-in contracts for all three Deployment Behavior Types.
2. An existing deployment draft selects exactly one behavior and compiles published exact Marketplace versions through the existing V04 lifecycle.
3. Conversational Assistant, Agentic Specialist Team, and Smart Brain each have at least one hosted-proven reusable Marketplace template/V04 deployment, subject to the Smart Brain strategy gate.
4. Resolver and Human Review attach only through explicit compatible extensions.
5. Every adopted AI Fabric capability has an existing Marketplace/private-runtime/config owner, runtime inventory, Platform binding, verification, operations, and maturity state.
6. Shopify and ProdUS remain valid reference solution deployments without becoming the generic data model.
7. Customer-facing surfaces show behaviors and outcomes rather than Maven modules and raw configuration.
8. Every deployment advertises only endpoints and capabilities it actually provides.
9. Assignment, promotion, export/import, rollback, draining, and decommission work for every behavior.
10. Isolation, missing-boundary, provider failure, persistence failure, restart, replay, and cancellation behavior are verified where applicable.
11. Platform claims, tiers, quotas, billing, support, privacy, retention, and offboarding match the exact released deployment composition.
12. The customer application remains the final authority for identity, authorization, validation, transactions, and system-of-record writes.

## 19. Final Implementation Decision

Proceed through one behavior-aware extension of the existing Marketplace/V04 composition path and three built-in deployment behavior types.

AI Fabric `0.7.0` Gate A is the completed chain-disabled baseline. The next Platform build target is the minimal behavior/provenance extension, governed `SPECIALIST` contribution type, and one generic Conversational Assistant Marketplace template. Adopt declarative chain mechanics only through separately approved Gates B and C, then productize a bounded Agentic Specialist Team only when a genuinely distinct second worker exists. Productize Smart Brain only after its existing strategy gate, using deployment-local CloudEvents, durable read execution, direct operation APIs, and deployment-owned result delivery.

This sequence lets Platform users create their own AI products from the complete AI Fabric capability set without exposing framework internals as products, weakening authority, duplicating catalogues/lifecycles, or creating vertical runtime forks.
