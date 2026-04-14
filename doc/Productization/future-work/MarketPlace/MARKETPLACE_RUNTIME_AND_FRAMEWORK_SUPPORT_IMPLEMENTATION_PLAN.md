# Marketplace Runtime and Framework Support Implementation Plan

Status: implementation baseline completed (2026-04-14)

Execution status snapshot:

- Wave 0 complete
- Wave 1 complete
- Wave 2 complete
- Wave 3 complete
- Wave 4 complete
- Wave 5 complete

Implemented outcome:

- runtime now supports resolved `knowledgeSourceConfig`, `shellConfig`, enriched action metadata, evidence attribution, search-source diagnostics, degraded retrieval behavior, and platform verification alignment without introducing marketplace awareness into runtime
- runtime prerequisites for marketplace control-plane work are now satisfied; remaining marketplace work is catalog, install, compiler, entitlement, publisher, and product UX work

This document defines the runtime and framework capabilities that must exist for marketplace plugins to be usable after control-plane resolution.

It is intentionally scoped away from:

- catalog implementation
- listing UX
- billing and subscriptions
- publisher onboarding
- deployment install records
- draft compilation logic itself

Those belong to marketplace and platform control-plane work.

This document exists to answer a narrower question:

- once marketplace has resolved plugin installs into deployment-scoped artifacts, what must the runtime, orchestrator, and shell-supporting framework be able to understand and execute safely?

Related docs:

- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/EXTERNAL_PLUGIN_PUBLISHER_MODEL_PLAN.md`
- `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/CONFIG_DRIVEN_MARKETPLACE_VS_MICROFRONTEND_PLUGIN_ARCHITECTURE_PLAN.md`
- `doc/Productization/future-work/Auth/AUTH_IMPLEMENTATION_SEQUENCE_PLAN.md`
- `doc/Productization/future-work/TENANT_SCOPED_SHARED_VECTOR_INFRASTRUCTURE_PLAN.md`

---

## 1) Executive Summary

The runtime and framework do not need to become "marketplace-aware."

They need to become capable of consuming a richer set of resolved deployment-scoped capabilities:

- enriched action capabilities
- deployment-scoped knowledge sources
- source attribution metadata
- resolved shell configuration
- fixed shell registries and typed UI contracts
- diagnostics and verification hooks for those resolved capabilities

The most important capability that had to be delivered was the ability to model and query deployment-scoped knowledge sources through one narrow retrieval abstraction.

The second major capability that had to be delivered was shell composition:

- resolved `shellConfig`
- fixed built-in module and card registries
- typed runtime response contracts that let the shell render richer marketplace-contributed capabilities without loading arbitrary publisher code

Recommended sequence:

1. define resolved artifact contracts
2. harden action capability metadata
3. add knowledge-source and retrieval-source abstractions
4. add shell configuration and fixed registries
5. add diagnostics, verification, and fail-closed compatibility enforcement

---

## 2) Scope And Boundary

This plan covers:

- runtime config and artifact loading
- orchestrator and retrieval contracts
- action metadata contracts
- shell-facing response and config contracts
- runtime diagnostics and verification hooks
- framework-side compatibility enforcement

This plan does not cover:

- plugin catalog entities
- deployment install records
- control-plane compilation from installs into drafts
- review workflow
- billing or revenue share
- public marketplace UI

Required boundary:

- runtime consumes resolved deployment artifacts only
- runtime does not read marketplace catalog state
- runtime does not evaluate install records
- shell consumes resolved `shellConfig` and typed runtime responses only
- no arbitrary plugin code in runtime
- no arbitrary plugin code in shell

---

## 3) Current Foundation

The following foundations already exist and should be reused, not replaced.

### 3.1 Auth foundation

The runtime auth model is already materially aligned with marketplace needs:

- canonical auth context
- private-runtime assertions
- public-runtime tokens
- auth-mode-aware ingress

Relevant implementation:

- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/auth/RuntimeAuthContext.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/auth/RuntimeRequestAuthResolver.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/auth/RuntimePrivateAssertionService.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/auth/RuntimePublicTokenService.java`

Marketplace should build on this directly.

### 3.2 Action catalog and execution foundation

The framework already has a stable action catalog and action execution path:

- `ConnectorActionDefinition`
- action catalog loading
- confirmation posture
- read vs write access mode
- existing governance steps

Relevant implementation:

- `ai-infrastructure-module/ai-infrastructure-actions-connector/src/main/java/com/ai/infrastructure/intent/action/connector/ConnectorActionDefinition.java`
- `ai-infrastructure-module/ai-infrastructure-actions-connector/src/main/java/com/ai/infrastructure/intent/action/connector/ConnectorActionCatalogLoader.java`

This means action plugins are not blocked by a missing execution engine.
They are blocked only by missing enriched metadata and provenance contracts.

### 3.3 Vector and provider abstraction foundation

The framework already has:

- vector database abstraction
- provider diagnostics
- tenant-scoped shared vector groundwork

Relevant implementation:

- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/VectorDatabaseService.java`
- provider-specific vector services in `victor-databases/...`

This is a strong foundation for deployment-private and shared-index retrieval.

### 3.4 Admin diagnostics pattern

The runtime already exposes good admin overview surfaces:

- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/admin/RuntimeAdminOverviewController.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/admin/VectorIndexAdminController.java`

Marketplace support should extend this pattern instead of inventing a second diagnostics plane.

---

## 4) Capability Coverage Delivered

The capabilities that originally blocked marketplace support in runtime/framework are now implemented.

### 4.1 Deployment-scoped knowledge source model

`knowledgeSourceConfig` now exists as a deployment-scoped runtime contract.

Delivered outcome:

- data plugins can resolve into a runtime-consumable deployment model
- runtime can distinguish deployment-private retrieval from resolved shared/plugin-provided retrieval
- attribution and per-source policy are explicit instead of ad hoc

### 4.2 Retrieval-source abstraction above vector-only search

The retrieval abstraction is now in place and supports:

- deployment-private vector search
- shared-index search
- provider-managed dataset search
- remote search API sources

without making the orchestrator know per-plugin logic.

### 4.3 Source attribution contract

The answer pipeline now has first-class source attribution metadata for evidence items.

Delivered fields include:

- source type
- source id
- attribution label
- trust or freshness hints
- resolved contribution id or plugin-derived source handle

### 4.4 Resolved shell configuration

Deployment-scoped `shellConfig` now exists as a first-class runtime/framework capability.

Delivered outcome:

- template plugins can safely seed shell defaults
- action plugins can influence presentation only through platform-owned registries
- data plugins can influence evidence presentation only through platform-owned registries

### 4.5 Fixed module and card registries

The shell now has trusted extension surfaces instead of free-form rendering.

The framework/runtime/shell contract now defines:

- built-in module ids
- built-in card or UI block ids
- typed mappings from resolved config into those built-ins

### 4.6 Capability and compatibility introspection

Marketplace resolution now has stable runtime/framework-declared capability surfaces such as:

- supported action adapter types
- supported knowledge-source adapter types
- supported auth modes
- supported shell module ids
- supported evidence or card types

### 4.7 Runtime-loaded contribution diagnostics

After control-plane resolution, verification can now prove:

- which knowledge sources loaded
- which action metadata loaded
- which shell config resolved
- which source attribution rules are active

This is now available for live verification and supportability.

---

## 5) Target Runtime / Framework Capability Model

The runtime/framework should support these resolved deployment-scoped capability objects.

### 5.1 Resolved action capability

This extends the current action contract with stable metadata needed by marketplace, shell, and governance.

Recommended fields:

- action id
- display name
- description
- category
- access mode
- confirmation posture
- anonymous or auth-mode eligibility
- answer grounding eligibility
- side-effect level
- result presentation hint
- built-in module mapping optional
- built-in card or UI block mapping optional
- resolved provenance metadata

### 5.2 Resolved knowledge source

This is the core new runtime contract.

Recommended fields:

- source id
- source type
- adapter type
- attribution label
- access policy
- auth mode eligibility
- ranking hints
- freshness hints
- query scope filters
- provider or shared-handle reference
- result contract type
- resolved provenance metadata

### 5.3 Resolved shell config

Recommended sections:

- branding
- layout
- enabled built-in modules
- greeting and starter prompts
- feature toggles
- action presentation hints
- search and evidence presentation hints
- governance UI behavior
- host integration capabilities

### 5.4 Evidence attribution metadata

Every evidence result returned to the shell should be able to carry:

- source type
- source id
- attribution label
- evidence card style hint
- trust or freshness hint
- contribution provenance optional

---

## 6) Sequence Waves

## Wave 0: Resolved Contract Foundation

Objective:

- define the runtime/framework contracts that marketplace resolution targets

Scope:

- define deployment-scoped resolved capability models for:
  - action capability metadata enrichment
  - `knowledgeSourceConfig`
  - `shellConfig`
  - evidence attribution metadata
- define artifact-loading rules for these models
- define runtime capability versioning and compatibility negotiation
- add empty/default-safe behavior for deployments that do not use marketplace

Implementation targets:

- runtime config loader layer
- deployment artifact model
- admin diagnostics surface contracts

Acceptance criteria:

- runtime can boot with no knowledge sources and no shell config using empty defaults
- runtime can report resolved contract versions and supported capability sets
- no marketplace catalog or install state is visible in runtime

Why first:

- all later waves need one stable resolved target model

---

## Wave 1: Action Capability Hardening

Objective:

- make action plugins fully representable through existing runtime execution, governance, and shell contracts

Scope:

- enrich action definition and resolved action metadata with:
  - grounding eligibility
  - side-effect level
  - presentation hints
  - optional module/card mapping
  - provenance metadata
- make these fields visible to:
  - orchestrator
  - shell response shaping
  - admin diagnostics
- keep execution through the existing action engine

Implementation targets:

- action definition contract
- action metadata registry
- runtime response shaping for action results
- admin overview and action overview endpoints

Acceptance criteria:

- action plugins do not require a new runtime execution engine
- shell can render action posture and result hints from resolved metadata only
- verification can prove action metadata loaded as expected

Why second:

- action plugins are the lowest-risk marketplace capability and mostly reuse current infrastructure

Parallelization:

- can be built in parallel with early shell registry work after Wave 0

---

## Wave 2: Knowledge Source And Retrieval Abstraction

Objective:

- make data plugins possible without plugin-specific runtime logic

Scope:

- introduce a framework-level retrieval abstraction such as `SearchSource`
- add `knowledgeSourceConfig` loading into runtime
- implement approved source adapters for launch:
  - deployment-private vector source
  - shared-index source
  - provider-managed dataset source
  - remote-search-api source
- merge and rank results across sources through one orchestrator-facing contract
- add per-result source attribution metadata
- enforce auth-mode and tenant/customer-safe source eligibility

Implementation targets:

- retrieval service contract
- orchestrator source selection path
- source adapter SPI
- source attribution contract
- fail-closed compatibility validation

Acceptance criteria:

- runtime can query more than one resolved source type through one contract
- answer evidence can clearly attribute plugin-provided results
- unsupported source adapters fail at load time, not at answer time
- shared data sources remain governed by tenant/customer-safe handles

Why third:

- this is the real unblocker for data plugins

Risk:

- this is the most invasive framework change in the plan

---

## Wave 3: Shell Config And Trusted Extension Surfaces

Objective:

- make shell-aware marketplace composition possible without arbitrary plugin UI code

Scope:

- add deployment-scoped `shellConfig`
- define fixed platform-owned:
  - module registry
  - card or UI block registry
  - evidence block registry
- extend runtime response contracts to return typed blocks and hints instead of only plain text
- map resolved action and knowledge-source metadata into shell-facing built-ins
- align widget/bootstrap surfaces to consume resolved shell config

Implementation targets:

- bootstrap contract
- shell config loader
- typed response block contract
- widget / shell rendering integration points

Acceptance criteria:

- shell can render richer marketplace-contributed behavior using only built-in registry ids
- template plugins can seed greeting, starters, and module visibility through resolved config
- action and data plugins can influence presentation without shipping code

Why fourth:

- it depends on the resolved models from Wave 0 and benefits from the metadata contracts from Waves 1 and 2

Parallelization:

- module/card registry work can begin before Wave 2 completes, but final integration depends on Wave 0 contracts

---

## Wave 4: Verification, Diagnostics, And Compatibility Enforcement

Objective:

- make marketplace-resolved runtime behavior supportable and safe in production

Scope:

- extend runtime admin surfaces to expose:
  - loaded knowledge sources
  - supported source adapter types
  - loaded shell config summary
  - module and card registry availability
  - loaded action metadata summary
  - attribution readiness
- add verification hooks so platform can compare resolved deployment expectation vs runtime loaded state
- add fail-closed compatibility checks for:
  - auth mode mismatch
  - unsupported adapter type
  - missing required source handles
  - unsupported shell registry ids

Implementation targets:

- runtime admin overview endpoints
- deployment verification probes
- validation and startup checks

Acceptance criteria:

- platform can verify resolved marketplace-related runtime posture without runtime knowing catalog state
- miscompiled or unsupported deployment artifacts fail before live traffic

Why fifth:

- without this, marketplace failures will be opaque and unsafe

---

## Wave 5: Maturity, Performance, And Rollout Hardening

Objective:

- make the runtime/framework support robust enough for wider plugin use

Scope:

- add source-level latency and health diagnostics
- add caching and ranking guardrails for multi-source retrieval
- add graceful degradation for disabled or unhealthy sources
- add test matrix and live verification patterns for:
  - action-only deployments
  - mixed private + shared knowledge deployments
  - shell-aware template deployments
- add forward-compatible contract versioning rules for resolved capability artifacts

Acceptance criteria:

- mixed-source retrieval remains observable and predictable
- shell and runtime degrade safely if one resolved source is unavailable
- rollout verification can isolate whether a failure is action, retrieval, shell config, or auth related

Why last:

- this wave should harden the architecture after the functional capabilities exist

---

## 7) Recommended First Build Slice

The first practical runtime/framework slice should be:

1. Wave 0
2. Wave 1
3. the minimal part of Wave 2 needed for one approved `shared-index` knowledge source
4. the minimal part of Wave 4 needed to verify it

This gives the platform:

- action plugins on a stronger metadata contract
- one real path for first-party or partner data plugins
- verification that the runtime actually loaded the resolved state

This is the smallest slice that proves marketplace is usable beyond template bootstrapping.

---

## 8) Capability Readiness By Plugin Type

### Template plugins

Runtime/framework need:

- Wave 0
- Wave 3 only if template changes shell defaults

Template plugins are otherwise mostly a control-plane/bootstrap concern.

### Action plugins

Runtime/framework need:

- Wave 0
- Wave 1
- Wave 4

Action plugins are the least blocked type.

### Data plugins

Runtime/framework need:

- Wave 0
- Wave 2
- Wave 4
- Wave 5 for broader rollout maturity

Data plugins are the most blocked type.

### Shell-aware contributions

Runtime/framework need:

- Wave 0
- Wave 3
- Wave 4

These should remain config-driven only.

---

## 9) What Should Explicitly Stay Out Of Runtime

Even after these waves, runtime/framework should still not implement:

- plugin catalog lookup
- plugin pricing or billing
- publisher identity logic
- install record evaluation
- marketplace recommendation logic
- arbitrary runtime code loading
- arbitrary shell code loading

If any marketplace feature requires those inside runtime, the architecture is drifting away from the correct boundary.

---

## 10) Recommended Ownership Split

Runtime/framework should own:

- resolved capability contracts
- retrieval-source abstraction
- source attribution contract
- shell config and registries
- diagnostics and startup validation

Platform/control plane should own:

- catalog
- installs
- compatibility computation
- compilation into deployment artifacts
- billing and entitlements
- review and publishing

This split keeps marketplace strong without making runtime marketplace-specific.

---

## 11) Completion Criteria

This runtime/framework support plan is complete when all of the following are true:

1. action plugins can compile into richer action metadata without new runtime engines
2. at least one data plugin type can compile into `knowledgeSourceConfig` and be queried through a single retrieval abstraction
3. answers can attribute plugin-provided evidence clearly
4. template, action, and data plugins can influence shell behavior only through resolved `shellConfig` and fixed registries
5. runtime admin surfaces can prove what resolved capabilities are loaded
6. verification can fail closed on unsupported adapters, auth modes, or registry ids
7. runtime still does not know marketplace catalog, pricing, or install UX state

---

## 12) Recommendation

The runtime/framework sequence is complete.

Marketplace implementation should now proceed in the control plane with this practical order:

1. Phase 0 catalog, schema, and install-record foundation
2. Phase 1 template plugins
3. Phase 2 action plugins
4. Phase 3 data plugins
5. later business-layer phases such as entitlements, external publishing, and billing

If prioritization is required:

- prioritize action plugins before broader external data plugins
- keep data plugins constrained to first-party and partner-only flows until control-plane compatibility, entitlement, and operational review are mature

Reason:

- runtime support is no longer the blocker
- the remaining implementation risk is now in control-plane compilation, governance, and product workflow
