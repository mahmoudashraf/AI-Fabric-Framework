# 010.22 AI Fabric 0.7.1 Behavior-Aware Platform Composition Implementation Plan

Status: implementation plan created on 2026-09-15, corrected on 2026-09-18 to make customer products customer-owned and reuse the existing Marketplace/V04 primitives, aligned on 2026-09-19 with AI Fabric `0.7.0`, and implemented in source on 2026-09-19 after explicit owner direction to proceed with all three deployment behaviors. The PostgreSQL durability correction shipped in AI Fabric `0.7.1` on 2026-09-20 and is now the active Platform/runtime baseline. The reviewed Platform/backend/runtime/frontend source is committed and deployed. Exact Conversational, two-worker Agentic, and Smart Brain compositions have passed hosted canaries; reusable-template lifecycle completion, Human Review hosted proof, and market-readiness work remain open. No productization item is complete merely because AI Fabric exposes the primitive or because one canary passes.

Canonical architecture:

- [010.21 Consolidated LoomAI Customer Product And Deployment Behavior Architecture](010_21_CONSOLIDATED_LOOMAI_AI_ENABLEMENT_PRODUCT_PROFILE_AND_DEPLOYMENT_ARCHITECTURE.md)

The filename retains the `0.7.0` contract lineage so existing links remain
valid. All new builds, deployments, and verification in this plan use the
compatible production patch `0.7.1`.

Release foundation:

- AI Fabric `0.7.1` current production baseline
- Maven group `io.github.loom-ai-labs`
- tag `ai-fabric-framework-v0.7.1`
- release commit `58ac80d55f0102485562942a1a9cab88208c7530`
- `0.7.1` is a compatible production patch over the `0.7.0` declarative-chain and durable-review contracts; it fixes nullable lease-owner compare-and-set transitions in PostgreSQL-backed durable specialist and review repositories
- framework documentation commit `7ac32985`
- published upgrade notes `docs/release-notes/LOOMAI_PLATFORM_AI_FABRIC_0_7_0_UPGRADE_NOTES.md`
- published migration runbook `docs/Framework-Dev-Guides/application-patterns/LOOMAI_AI_FABRIC_0_7_DECLARATIVE_CHAIN_MIGRATION_RUNBOOK.md`
- published adoption prompt `docs/Framework-Dev-Guides/developer-workflows/LOOMAI_AI_FABRIC_0_7_DECLARATIVE_CHAIN_ADOPTION_PROMPT.md`
- durable review contract `docs/Framework-Dev-Guides/application-patterns/DURABLE_HUMAN_REVIEW.md`

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
| `CONVERSATIONAL` | A customer can build a product where a person asks and receives an answer, clarification, structured result, or governed next step | Authenticated interactive request | One bounded orchestration turn with optional backend-owned session | Exact generic staging and production canaries are hosted-proven; reusable-template promotion/export/import/rollback evidence remains open |
| `AGENTIC_SPECIALIST_TEAM` | A customer can build a product where a bounded manager coordinates approved specialists for a larger task | Authenticated interactive or trusted application request | Exact-version manager and read-only workers through an official declarative `SpecialistChain`, a reviewed Java definition when richer application logic is required, or a fixed plan | Exact PostgreSQL-backed two-worker staging and production canaries are hosted-proven; full reusable-template lifecycle and market gates remain open |
| `SMART_BRAIN` | A customer can build a product where trusted events and schedules initiate proactive analysis without a fabricated chat turn | Trusted application, CloudEvent, or managed schedule | Exact specialist, bounded read-only chain, or explicitly selected fixed read plan | Exact PostgreSQL-backed production canary is live and typed durable execution passed; reusable-template delivery/restart/draining gates remain open |

### 2.2 Execution extensions

| Extension | Purpose | Compatible deployment behaviors | Boundary |
| --- | --- | --- | --- |
| Governed Resolver | Convert an approved recommendation into one registered, validated, confirmed write | Conversational Assistant and selected Agentic Specialist Team deployments | Application executes and reconciles the side effect |
| Human Review | Place a governed action proposal into a durable, separately authenticated review lifecycle | Conversational Assistant first; Agentic Specialist Team only after its own productization gates | LoomAI publishes the extension; the customer application owns reviewer identity and authorization; the model cannot select or impersonate the reviewer |

AI Fabric `0.7.0` durable review is not a generic approval wrapper around any
model output. Its released `ReviewSourceType` is `ACTION_PROPOSAL`, and approval
continues only through the governed `ActionProposalCoordinator`. Normal chat
confirmation, durable human review, and read-only result review are distinct
contracts.

Release ownership is explicit: the LoomAI Platform team implements, verifies,
and releases the built-in versioned Human Review extension through the existing
V04 lifecycle after owner approval. Compatible Marketplace `TEMPLATE` versions
may declare that extension, but Human Review is not a new plugin type. A
customer then opts it into a compatible deployment and supplies the trusted
reviewer identity, role/scope policy, and system-of-record authority. AI Fabric
supplies the reusable review mechanics; it does not publish or operate the
LoomAI product surface.

The original staged implementation hold was explicitly superseded by the owner
request to implement the complete backend and frontend now. Source therefore
includes Conversational, a real two-worker Agentic Specialist Team, Smart Brain,
and the Human Review extension. Release order is still evidence-driven:
Conversational governed-action review first, then the Agentic team after hosted
chain proof, then Smart Brain after hosted event/durability proof. Smart Brain
remains read-only and may not call the Resolver directly or disguise an
arbitrary result as an `ACTION_PROPOSAL`.

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

### 3.2 Source-complete and hosted-canary-proven in this implementation

- built-in `CONVERSATIONAL`, `AGENTIC_SPECIALIST_TEAM`, and `SMART_BRAIN` contracts, creation/draft/version persistence, deterministic migration, compatibility validation, and behavior-specific endpoint metadata;
- behavior-aware Marketplace `TEMPLATE` bootstrap plus governed `SPECIALIST` manifests, exact specialist-bundle references, deterministic compilation, and three reusable behavior templates;
- immutable V04 composition provenance and source capability manifest hashes enforced by versioning, export/import, apply, and release verification;
- a real Agentic deployment-intelligence team using the official AI Fabric `SpecialistChainGateway`, with a manager and two distinct read-only workers: deployment knowledge and runtime state;
- deployment-local Smart Brain CloudEvents ingress, registered triggers, durable operations, result polling, replay/cancel, Quartz schedules, transactional webhook delivery, and migrations;
- deployment-local `ACTION_PROPOSAL` Human Review with durable task/dispatch/receipt migrations, safe inbox/detail/decision APIs, customer-owned reviewer authorization, and a Platform Review Inbox proxy/UI;
- source-artifact capability manifests, runtime readback, scoped execution secrets, behavior operations APIs, and Platform UI pages for behavior, artifacts, operations, and review;
- local verification: private runtime `174/174`, Platform backend `757/757`, PostgreSQL 16 full migration `V1..V133`, and the Platform UI production build all pass;
- local practical proof: all three behavior templates bootstrap and publish through V04, a strict Agentic runtime fails closed when private assertion material is absent, and the correctly configured PostgreSQL runtime starts healthy with all eight runtime migrations, one ready declarative chain, no chain diagnostics, and authenticated capability-manifest readback;
- hosted proof on AI Fabric `0.7.1`: staging Conversational `dep-5c249fac`, staging Agentic `dep-17befd6c`, and staging Smart Brain mechanics `dep-10c99bd4` passed their focused canaries; production Conversational `dep-18e74807`, Agentic `dep-f56d32a9`, and Smart Brain `dep-e8a29c2b` reached verified behavior proof;
- the production Agentic canary completed a durable two-worker execution, and the production Smart Brain canary completed a typed durable PostgreSQL operation. All three production runtime applications currently report healthy liveness.

### 3.3 Remaining productization and market-readiness work

- convert the exact canary compositions into released reusable Marketplace `TEMPLATE` paths rather than relying on `custom-start-from-scratch` canary records;
- complete the remaining Agentic, Smart Brain, Human Review, isolation, restart, replay, cancellation, failure, promotion, export/import, rollback, draining, and decommission matrix not covered by the focused canaries;
- prove one released generic Conversational, Agentic, and Smart Brain template through the complete supported staging-to-production lifecycle before a broad market claim;
- finish reusable Verification Pack registration where behavior checks still exist only as focused tests;
- finish generic Platform operations for PII, governance, relationship, behavior, migration, and document ingestion;
- define entitlement, quota, billing, support, retention, backup, recovery, and offboarding rules for the new deployment capabilities.

### 3.4 Current evidence status

`010.21` records the source and hosted fleet baseline separately. Historical rollout detail remains in the working/private handoff records.

Current operational truth is:

- AI Fabric `0.7.1` tag `ai-fabric-framework-v0.7.1` resolves to release commit `58ac80d55f0102485562942a1a9cab88208c7530`; the current private rollout commits are `3bdb9ccc4`, `359a5ae03`, and `ed25d7c30`;
- the immutable private runtime image is `ghcr.io/mahmoudashraf/ai-fabric-runtime:359a5ae0306c2957a8070e9c40ca263b1710a2f6` at digest `sha256:e032e116caf58ab73be41acd5920a2faeeb41ab343a0c7aa1aca5b766325fa12`;
- staging and production Platform backends are healthy at exact backend commit `ed25d7c30`; both Platform UI hostnames answer their health and application routes;
- all three production behavior canary runtimes are active and healthy; the exact Agentic and Smart Brain durable proofs passed;
- canonical release-readiness `vsr-015a8e14` passed without control-plane repair: Marketplace `41/41` and Ecommerce `43/43`, both `IN_SYNC`; optional legacy Qdrant remains `MIGRATION_REQUIRED` and non-blocking;
- Thinker/Resolver `vsr-f3a5604d` passed after refreshing the dedicated short-lived Partner test JWT;
- the latest aggregate gate remains honestly non-green only on the owner-deferred Shopify answer-quality path, and the standalone Partner run reaches that same protected Shopify runtime exception after all preceding partner checks pass;
- production custom-domain DNS is complete: authoritative Namecheap DNS and public resolvers return `46.225.162.106` for the apex, `api`, `console`, `partners`, and `shopify-bridge`; apex HTTPS, redirect behavior, service routes, and trusted TLS were reverified on 2026-09-20.

### 3.5 Mandatory AI Fabric `0.7.x` adoption gates

The framework runbook deliberately separates release adoption from chain productization. Do not collapse these gates into one deployment:

| Gate | Scope | Current status | Exit evidence |
| --- | --- | --- | --- |
| A. Base release | Run the compatible `0.7.x` line from immutable Maven Central artifacts while preserving existing behavior | **COMPLETE** on current patch `0.7.1` | Central-only release proof, exact runtime image/digest, healthy Platform deployments, and existing-capability regression evidence |
| B. Schema preparation | Add deployment-local chain/review/operation migrations and validate them on PostgreSQL | **HOSTED CANARY COMPLETE**. Production Agentic and Smart Brain runtimes apply the deployment-local migrations and execute durable work | Reusable-template rollback/redeploy and backup/restore evidence remains required |
| C. Declarative mechanics canary | Package reviewed chain mechanics with stable private secrets, JDBC persistence, and deployment-local route/status projection | **HOSTED CANARY COMPLETE**. The exact two-worker Agentic canary completed a durable execution | Complete replay/restart/cancel/definition-drift/provider-failure matrix for the released template |
| D. Product chain | Introduce a real multi-specialist chain only when a genuinely distinct second read-only worker exists | **HOSTED CANARY COMPLETE; MARKET GATE OPEN**. Knowledge and runtime-state workers are distinct, closed, and proved live | Released Marketplace/V04 template, full lifecycle matrix, commercial/support posture, and market claim review |

The required stop after Gate A occurred and its hosted evidence is recorded. The
later explicit owner instruction to implement the complete backend/frontend
authorized source work for Gates B-D and Smart Brain, but did not waive hosted
release evidence. Focused hosted evidence now closes the mechanics-canary
portion of Gates B-D. It does not close the reusable-template, complete
lifecycle, operations, or market-readiness exits, and must not be presented as
an unrestricted-agent claim.

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

Human Review adds no central Platform review-task aggregate. The immutable V04
behavior configuration records only compatible extension enablement, exact
review policy IDs/hashes, decision/TTL/dispatcher requirements, migration and
secret names, endpoint classes, and verification-pack references. Review tasks,
dispatch receipts, protected decisions, and outcomes remain deployment-local.

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

A published `SPECIALIST` version contributes source-attested bundle references:

- exact stable bundle ID, bundle contract version, and lowercase SHA-256 content hash;
- exact specialist and optional `SpecialistChain` `name@version` references;
- required registered vector spaces, allowed actions, framework capabilities, and curated/runtime profiles;
- required application-owned migration IDs and secret names, never migration SQL or secret values;
- deployment ceilings that may only narrow the server-owned maximums;
- compatible behavior types and activation sources;
- verification-pack IDs and unsupported claims.

The actual `ai.fabric/v1` specialist, chain, prompt, and schema resources remain
reviewed private-runtime source and immutable image content. The runtime build
and startup use AI Fabric's validator, loader, registry, and gateway. Platform
publication validates only the closed source-bundle reference contract;
installation compiles exact published versions into the V04 draft. Publishing
a V04 version freezes plugin identity, bundle hash, exact manager/worker/chain
references, source-artifact identity, ceilings, and verification requirements.

For a Platform-selectable deployment, apply requires the selected immutable
source artifact's capability manifest to attest the exact bundle ID, contract,
hash, specialist/chain references, migrations, capabilities, and endpoint
classes. The release verifier compares that attestation with V04 provenance and
runtime readback. Startup fails closed on missing, changed, duplicate, invalid,
or incompatible packaged resources. No browser, Marketplace payload, event,
prompt, or model may upload a manifest or choose an undeclared target at
runtime.

The source now packages a real two-worker chain and makes its attested bundle
Platform-selectable. It is not hosted-proven or market-ready until runtime
readback, V04 provenance, and the complete hosted Agentic matrix pass.

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
6. compile contributions into existing V04 config sections and exact source-attested specialist bundle references;
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

### 8.5 Human Review execution extension

Human Review is a reusable execution extension, not a fourth Deployment
Behavior Type, a chat mode, or a customer product. Its purpose is to pause a
governed action proposal across request, process, actor, or time boundaries and
allow a separately authenticated human to make one policy-bounded decision.

#### 8.5.1 Ownership

| Owner | Responsibility |
| --- | --- |
| AI Fabric `0.7.0` | `ReviewDecisionGateway`, immutable `ReviewPolicyDefinition`, safe `ReviewTaskView`, JDBC/in-memory task and dispatch repositories, optimistic decisions, exact replay, recovery, expiry, retention, and continuation through governed action receipts |
| LoomAI private runtime | Application-owned migrations, stable secrets, reviewed policy/authorizer/dispatcher beans, trusted-context construction, safe deployment-local HTTP projection, assignment metadata, observability, and recovery operations |
| LoomAI Platform team/operator | Version and publish the extension through existing V04 composition, verify it, expose compatible choices, and provide the optional managed Review Inbox client |
| Customer application | Authenticate the reviewer, map roles/scopes and separation-of-duty rules, authorize the current decision, own domain policy and system-of-record authority, and optionally supply its own review UI/dispatcher |
| Model or specialist | Propose and explain only; never choose the policy, reviewer, recipient, dispatcher, escalation target, decision, or execution authority |

#### 8.5.2 First supported flow

The first LoomAI release is `CONVERSATIONAL` plus governed action review. It
does not review every chat answer and does not introduce a general moderation
queue.

```text
authenticated conversational request
  -> evidence-grounded registered WRITE proposal
  -> encrypted ActionProposalReceipt in PROPOSED
  -> backend selects an exact immutable ReviewPolicyId
  -> ai_review_task committed before delivery
  -> ai_review_dispatch receipt committed
  -> dispatcher sends only a safe task reference
  -> customer backend authenticates TrustedReviewerContext
  -> reviewer reads an authorized safe projection
  -> APPROVE / REJECT / CORRECT / REQUEST_INFORMATION / ESCALATE
  -> ReviewDecisionGateway rechecks task version, policy, reviewer and source
  -> APPROVE delegates to ActionProposalCoordinator
  -> current action authority, schema, protected parameters and preflight rechecked
  -> application executes at most one known governed action outcome
  -> safe decision/action receipt projected and audited
```

Approval is evidence for a decision; it is not direct execution authority.
Review HTTP code must never call an action handler itself. Rejection performs
no mutation. Correction creates a schema-valid successor proposal and review
task rather than editing history. Information requests use exact request and
response schemas and retain the original trusted source binding. Escalation
creates a deterministic successor under a registered higher-authority policy.

#### 8.5.3 Deployment-local state and API boundary

Each enabled runtime owns its review state. Platform remains the lifecycle and
assignment control plane and is not required in the reviewer decision data
path. Production uses an application-owned migration for `ai_review_task` and
`ai_review_dispatch`; framework schema auto-initialization and in-memory state
are test/demo only.

The database is a deployment resource, not the central Platform database. On
the managed Coolify path, Platform creates or reuses the PostgreSQL service
attached to that deployment, stores the generated credential through Platform
secret handling, binds `SPRING_DATASOURCE_*` only into the runtime, and records
the provider resource handle for lifecycle operations. The private runtime owns
and applies the reviewed Flyway schema for chain, Smart Brain, action-receipt,
and review state. The customer does not need to provide a database manually for
that managed path. A customer-owned database is valid only through a future or
explicitly supported target profile that preserves the same isolation,
migration, backup, restore, retention, and secret contracts.

The runtime must freeze versioned, authenticated endpoint classes for:

- reviewer inbox listing using safe `ReviewTaskView` rows;
- authorized task detail;
- one decision submission containing task ID, expected optimistic version,
  decision ID/type, and only policy-shaped data;
- source-bound information submission;
- safe terminal outcome/receipt readback; and
- operator-only recovery, expiry, retention, and dispatch diagnostics.

Exact URLs follow private runtime conventions and are advertised through the
assigned deployment where needed. The Review Inbox may be a LoomAI-managed UI
or a customer-owned UI, but both are clients of the same deployment-local
contract. Email, Slack, Teams, or ticketing dispatchers initially deliver a
safe reference/link only; they do not grant authority or accept an unverified
one-click approval.

Public review projections must not expose the linked action receipt ID,
executable parameters, raw subject/tenant/reviewer identifiers, keyed
fingerprints, encrypted payloads, credentials, authority scopes, hidden
worker output, or raw handler results.

#### 8.5.4 Identity, version, and secret controls

Every decision is bound to the exact task version, review policy ID/version and
content hash, proposal/source fingerprint, specialist/profile/schema hashes,
tenant/deployment scope, reviewer fingerprint, decision fingerprint, expiry,
and idempotency fingerprint. The application reconstructs
`TrustedReviewerContext` only from current backend authentication.

The two review encryption/fingerprint secrets must be stable, distinct from
each other and from action/job/chain secrets, at least 32 characters, and
available for the lifetime of retained rows. A changed policy, proposal,
definition, evidence binding, authority decision, preflight result, or expired
task fails closed. Current authorization is re-evaluated when the task is
read, when the decision is accepted, and again before any governed action.

#### 8.5.5 Behavior compatibility and rollout

1. **HR-1 Conversational action review:** one low-risk governed action, one
   immutable policy, approve/reject, durable restart/replay, separation of
   duty, generic Review Inbox, and customer-owned reviewer authorization.
2. **HR-2 Optional decisions and delivery:** schema-bound correction,
   request-information, escalation, external safe-reference dispatchers,
   expiry, retention, and operational recovery.
3. **HR-3 Agentic attachment:** only after the Agentic Specialist Team reaches
   hosted proof; workers remain read-only and may hand off a proposal, while
   the application creates the governed action receipt and review task.
4. **HR-4 Smart Brain result review:** only after Smart Brain productization,
   through a separately authorized application-owned read-result workflow.
   The originating event job remains read-only and cannot enter Resolver
   automatically.

AI Fabric `0.7.0` exposes only `ACTION_PROPOSAL` as a durable review source.
Before LoomAI markets reusable review of arbitrary answers, analyses, chain
results, or Smart Brain results, it must obtain a released generic review
source contract from AI Fabric or explicitly own and verify a separate
versioned application workflow. It must not overload `ACTION_PROPOSAL` or
silently duplicate `ReviewDecisionGateway`.

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

The optional managed Review Inbox must show only authorized safe task fields:
title, summary, policy/version, allowed decisions, status, creation/expiry,
task version, safe evidence/proposal presentation, and safe terminal outcome.
It must support approve, reject, and only the policy-enabled correction,
information, or escalation forms. The UI obtains a fresh optimistic task
version before submission, displays stale/expired/authorization failures, and
never exposes executable parameters or treats dispatch delivery as approval.
A Platform operator role alone never grants customer review authority; the
managed UI must present customer-configured trusted reviewer claims that the
deployment reauthorizes for the specific task and decision.

### 10.4 Partner experience

Partners select published Marketplace templates/plugins and customer-safe options through the same deployment workflow. They may request a reviewed specialist bundle addition, but cannot upload runtime manifests, publish a bundle directly, add executable content, broaden the source-artifact authoring catalogue, supply authority, or bypass verification. Custom solution-template work remains reviewed and versioned before it appears as a selectable option.

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
- Human Review: first-release source is exactly `ACTION_PROPOSAL`; arbitrary
  answer/result payloads are rejected rather than coerced into an action
  review.
- Human Review: task and dispatch records commit before delivery; a pending
  task and accepted decision survive packaged restart.
- Human Review: authorized safe inbox/detail projection, tenant/deployment
  isolation, separation of duty, required scopes, and unauthorized non-
  disclosure pass.
- Human Review: approve reaches only `ActionProposalCoordinator`, revalidates
  current action authority/preflight, and produces at most one known outcome;
  reject and expiry produce no mutation.
- Human Review: exact decision replay succeeds, changed decision/task version
  conflicts, and changed policy/source/definition/evidence fails closed.
- Human Review: correction, request-information, and escalation are available
  only when exact schemas, handlers, and successor policies are registered.
- Human Review: dispatch retry/exhaustion, decision-lease recovery, retention,
  secret rotation constraints, `OUTCOME_UNKNOWN`, and rollback/draining are
  visible and tested.
- Human Review: managed and customer-owned UIs use the same deployment-local
  contract; external channels carry safe references and never grant authority.
- Knowledge: create/update/delete/reindex, work-status reconciliation, counts/revision hash, expected evidence, citations, no-evidence behavior.
- Documents: format allowlist, malicious/unsupported input denial, preview/approval, chunk lifecycle, replacement, delete, retention.
- MCP: exact server/tool name, auth, schema hash, drift denial, argument policy, bounded result, read/write policy, outage.
- PII/Governance: input/output modes, encryption, logs, deletion, retention, policy denial.
- Relationship: schema allowlist, object authorization, query ceilings, no unrestricted traversal or raw persistence output.
- Behavior: consented event input, signal provenance, uncertainty, no automatic mutation, retention/deletion.
- Migration: pause/resume/cancel/retry, superseded work, dead-letter handling, source/index count reconciliation.

## 13. Delivery Work Packages

### WP0: Truth, contracts, and status model

Status: `COMPLETE`

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

Status: `HOSTED_CANARY_COMPLETE_REUSABLE_TEMPLATE_PENDING`

Depends on: WP0.

Tasks:

- add the built-in Deployment Behavior Type contract and minimal draft/version fields;
- extend existing `TEMPLATE` manifest validation/bootstrap and exact required-plugin installation;
- add the governed `SPECIALIST` Marketplace contribution type and lifecycle;
- compile reviewed source-attested specialist bundle references into V04 and require the selected source artifact to prove the exact bundle hash;
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

Status: `HOSTED_CANARY_COMPLETE_REUSABLE_TEMPLATE_PENDING`

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

Status: `HOSTED_CANARY_COMPLETE_FULL_GATE_PENDING`

Depends on: WP1 and shared runtime identity/storage work. WP2 should establish the normal template/compiler/V04 release path first.

Tasks:

- select one design-partner canary, preferably deployment/incident investigation or account/support resolution;
- retain the completed Gate A release as historical baseline evidence and use current patch `0.7.1` for PostgreSQL durability;
- retain the completed deployment-local PostgreSQL migration and exact two-worker hosted canary evidence for Gates B-D;
- keep the two responsibilities genuinely distinct: deployment knowledge and current runtime state;
- publish exact manager/workers and the official declarative `SpecialistChain` through a reviewed `SPECIALIST` plugin when released mapping/projection is sufficient;
- use private Java chain components only for a documented application invariant that the declarative contract cannot express;
- configure the JDBC repository, stable distinct secrets, workers, leases, recovery, retention, and cleanup;
- enable chains only when `AGENTIC_SPECIALIST_TEAM` V04 requirements match the selected source-artifact/runtime inventory;
- expose authenticated execute/submit/status/result/cancel/replay contracts;
- add safe lineage and Platform operation UI;
- run the complete Agentic gate matrix;
- convert the healthy controlled production canary into a released template-backed lifecycle proof.

Exit:

- one reusable Agentic Specialist Team Marketplace template/V04 deployment is hosted-proven with at least two exact read-only specialists;
- no dynamic topology, write worker, hidden fallback, or authority from model/client input exists.

### WP4: Smart Brain

Status: `HOSTED_CANARY_COMPLETE_FULL_GATE_PENDING`

Depends on: WP1 and durable execution lessons from WP3.

The prior strategy hold was explicitly superseded for implementation by the
owner request to build all new deployment types. This authorizes source and
staging-proof work, not a production or market launch. Launch still requires
the hosted, operational, commercial, and support evidence in this plan.

Implemented and remaining tasks:

- retain the implemented CloudEvents ingress, deterministic trigger registry, deployment-local endpoint metadata, durable operations, polling, cancel/replay, Quartz schedules, typed result store, and signed-webhook outbox;
- complete promotion draining and decommission proof;
- publish and prove one Behavior/Churn or Incident Intelligence Marketplace template;
- prove direct data-plane behavior with Platform unavailable after assignment;
- run the remaining restart, delivery retry/dead-letter, draining, export/import, rollback, and decommission matrix against the released template.

Exit:

- one event/scheduled template-backed V04 deployment is hosted-proven, self-contained, durable, directly addressable, read-only, and operationally supportable.

### WP5: Human Review and Resolver extensions

Status: `SOURCE_COMPLETE_HOSTED_PENDING`

Depends on: WP1 and the WP2 hosted Conversational baseline for HR-1. Agentic
attachment additionally depends on WP3. Smart Brain result-review routing
additionally depends on WP4 and a reviewed non-action result-review contract.

Tasks:

- inventory the released AI Fabric review contracts and pin the initial source
  to `ReviewSourceType.ACTION_PROPOSAL`;
- freeze the V04 execution-extension configuration for exact review policy IDs,
  allowed decisions, required reviewer scopes, separation of duty, TTL,
  dispatcher, migration, secret names, endpoint class, and verification pack;
- add application-owned `ai_review_task` and `ai_review_dispatch` migrations;
- configure JDBC durability, stable distinct secrets, recovery, expiry,
  retention, and safe health/readback;
- register one immutable policy, customer-authority-backed
  `ReviewerAuthorizer`, safe local-inbox dispatcher, and one low-risk governed
  action proposal produced from a Conversational deployment;
- expose deployment-local safe inbox, detail, decision, information, outcome,
  and operator recovery APIs without exposing protected receipt/action state;
- implement the optional LoomAI managed Review Inbox as a client of those APIs
  while preserving customer-owned UI support;
- prove approve/reject first, including current authorization/preflight
  revalidation and at-most-one known action outcome;
- add correction, request-information, escalation, and external safe-reference
  dispatchers only after their exact schemas/handlers/policies are registered;
- convert current Thinker/Resolver evidence into a separately selectable
  governed execution extension and preserve terminal reconciliation;
- attach Human Review to Agentic Specialist Team only after WP3 is hosted-
  proven and keep every worker read-only;
- allow Smart Brain to create only a separate application-owned read-result
  review after WP4; prevent it from calling Resolver directly or overloading
  `ACTION_PROPOSAL`;
- run restart, exact replay, changed-decision conflict, policy/source drift,
  denial, isolation, separation-of-duty, dispatch failure, expiry,
  `OUTCOME_UNKNOWN`, retention, rollback, and reconciliation gates.

Exit:

- one Conversational V04 deployment has hosted-proven durable governed-action
  review with customer-owned reviewer authorization and a generic safe inbox;
- approval reaches a domain mutation only through the governed Resolver/action
  coordinator after current revalidation, while rejection/expiry never mutate;
- Human Review and Resolver remain separately selectable compatible extensions
  and cannot widen deployment or customer authority;
- no arbitrary answer, Agentic result, or Smart Brain result is represented as
  a framework action review without a released compatible source contract.

### WP6: Capability-pack completion

Status: `IN_PROGRESS`

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
WP0/WP1 source foundation complete
  -> WP2 Generic Conversational hosted canary
  -> WP5 HR-1/HR-2 Conversational Human Review
  -> WP3 Agentic Specialist Team hosted canary
  -> WP5 HR-3 Agentic attachment and governed Resolver reuse
  -> WP4 Smart Brain hosted canary
  -> WP6 capability packs as target behaviors become available
  -> WP7 commercial and partner readiness

WP5 HR-4 read-result review starts only after WP4 and a reviewed non-action source contract.
```

Why this order:

- Behavior-aware use of the existing Marketplace/V04 path is the common bottleneck.
- Conversational Assistant proves the extended composition path with already hosted behavior and lowest runtime risk.
- Conversational Human Review reuses the released AI Fabric governed-action
  review contract without waiting for or pretending to be a specialist chain.
- Agentic Specialist Team captures the principal new `0.7.0` declarative-chain value without first building a new event transport and scheduler product.
- Smart Brain then reuses durable execution, status, replay, lineage, and operations lessons.
- Human Review and Resolver stay separately selectable, and Smart Brain cannot
  use either to turn an event job into an automatic write.
- Marketplace templates/plugins attach to stable behavior contracts instead of creating new bespoke runtimes.

## 15. Implemented Source Batch And Next Rollout Batch

The original `0.7.0` release-adoption batch is historical Gate A evidence. The
active compatible patch is `0.7.1`, released from framework commit
`58ac80d55f0102485562942a1a9cab88208c7530`. Current private source and the
immutable runtime image are committed, pushed, deployed, and live-verified.
The original Gate A sequence was:

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

The owner subsequently requested full source implementation rather than the
earlier narrow stop. The completed source batch now includes:

1. `loomai-deployment-behavior-v1` with all three behavior contracts;
2. deployment/draft/version behavior fields, immutable composition provenance,
   deterministic migrations, bundle export/import, and release guards;
3. governed `SPECIALIST` Marketplace contributions and exact bundle hashes;
4. behavior-aware `TEMPLATE` bootstrap, exact required-plugin installation,
   compatibility compilation, and source capability attestation;
5. generic Conversational, two-worker Agentic, and Smart Brain templates;
6. official declarative chain resources and the deployment-local Agentic API;
7. deployment-local Smart Brain ingress, schedules, durable operations,
   result polling, replay/cancel, and signed delivery outbox;
8. durable `ACTION_PROPOSAL` Human Review and the Platform Review Inbox client;
9. behavior, runtime-artifact, operation, and review frontend surfaces;
10. focused tests, runtime `174/174`, Platform backend `757/757`, PostgreSQL
    `V1..V133`, UI production build, and clean diff hygiene.

The focused hosted-canary batch is also complete:

1. exact private source was committed and pushed in `3bdb9ccc4`, `359a5ae03`,
   and `ed25d7c30`;
2. the immutable runtime image and digest were recorded and used by the
   behavior deployments;
3. generic Conversational passed in staging and production;
4. the two-worker Agentic team passed a durable PostgreSQL-backed execution in
   staging and production;
5. Smart Brain passed a typed durable PostgreSQL operation and remains healthy
   in the production canary;
6. canonical Marketplace and Ecommerce verification passed without repair;
7. Platform staging and production services are healthy.

The next batch is reusable productization and complete lifecycle proof:

1. publish and exercise the three reusable Marketplace `TEMPLATE` paths rather
   than retaining `custom-start-from-scratch` as the canary origin;
2. complete replay/restart/cancel/failure/isolation for the released Agentic
   template;
3. complete schedule, delivery retry/dead-letter, Platform-outage, draining,
   and decommission proof for the released Smart Brain template;
4. deploy one low-risk Conversational governed action with Human Review and
   prove approve/reject/replay/authorization/restart behavior;
5. prove export/import, promotion, rollback, draining, and decommission for
   each released behavior template;
6. close or explicitly continue the protected Shopify exception without
   weakening its verification expectations;
7. update maturity to `MARKET_READY` only for exact released compositions that
   pass commercial, support, and full lifecycle gates.

Still prohibited:

- unreviewed or runtime-submitted manifests;
- model-selected topology, reviewer identity, callback URL, or write authority;
- Smart Brain direct writes or automatic Resolver calls;
- broad partner self-service or public product claims from local tests alone.

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

Use one behavior-aware extension of the existing Marketplace/V04 composition
path and three built-in deployment behavior types. That source implementation
now exists. It does not create a second catalogue or release engine.

AI Fabric `0.7.1` is the live behavior-aware baseline. The committed source
adds the behavior/provenance contract, governed `SPECIALIST` bundles, generic
templates, a real bounded Agentic team, deployment-local Smart Brain, and
durable Human Review. Focused hosted canaries prove all three behavior types;
the next engineering work is reusable-template and complete lifecycle proof,
not another behavior abstraction.

Human Review remains customer-authorized and reaches mutation only through the
governed action coordinator. Smart Brain remains read-only, deployment-local,
and direct-to-runtime; its read results require a separate review boundary and
never enter Resolver automatically. Agentic targets remain exact, closed, and
read-only.

This sequence lets Platform users create their own AI products from the complete AI Fabric capability set without exposing framework internals as products, weakening authority, duplicating catalogues/lifecycles, or creating vertical runtime forks.
