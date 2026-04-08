# Config-Driven Marketplace vs Microfrontend Plugin Architecture Plan

Status: planning document (2026-04-08)

This document compares two ways to extend the Loom AI marketplace and shell:

- a config-driven marketplace model
- a microfrontend plugin model

It makes one concrete implementation recommendation:

- the marketplace implementation baseline should be `Level 1` plus `Level 2`
- microfrontend-style plugin execution should remain a later `Level 3` option only for exceptional cases

Related docs:

- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/EXTERNAL_PLUGIN_PUBLISHER_MODEL_PLAN.md`
- `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/Auth/AUTH_IMPLEMENTATION_SEQUENCE_PLAN.md`

---

## 1) Executive Summary

There are two fundamentally different extension architectures available:

1. config-driven composition
2. executable microfrontend plugins

For Loom AI, the recommended implementation path is:

- `Level 1`: config plugins
- `Level 2`: trusted extension surfaces

And the explicit non-baseline path is:

- `Level 3`: sandboxed microfrontend extensions only later, only if the first two levels prove insufficient

This recommendation exists because the current platform architecture is built around:

- deployment drafts and published versions as the source of truth
- publish and apply governance
- strict secret boundaries
- auth-mode-aware integration
- runtime and shell remaining platform-owned

A config-driven marketplace strengthens those properties.
A microfrontend marketplace weakens them unless a much heavier security, compatibility, and sandboxing layer is added.

---

## 2) The Three Levels

### 2.1 Level 1: Config plugins

This should be the core marketplace model.

Plugin types:

- templates
- actions
- data
- shell contributions

How it works:

- plugin authors publish declarative manifests
- the platform validates and compiles them into internal contribution objects
- installs resolve into deployment drafts
- publish and apply make them live
- runtime and shell consume resolved config only

### 2.2 Level 2: Trusted extension surfaces

This should be the second implementation layer.

How it works:

- plugins reference platform-owned built-in modules
- plugins reference platform-owned built-in cards or UI block types
- plugins contribute metadata, mappings, and view-model hints
- the shell renders only through its fixed component and module registry

This gives the marketplace more expressive UI outcomes without allowing publisher-defined frontend code.

### 2.3 Level 3: Sandboxed microfrontend extensions

This should not be the baseline.

How it works:

- plugin publishers ship executable frontend bundles
- the shell or host loads those bundles in a sandboxed container
- plugins render their own UI using a versioned host bridge

This is feasible technically, but it is a different product architecture with much higher cost and risk.

---

## 3) Config-Driven Marketplace Model

The config-driven model fits the current platform architecture.

### 3.1 Core properties

- plugins are declarative
- deployment installs are version-pinned
- deployment drafts and versions remain the live source of truth
- runtime does not load arbitrary plugin code
- shell does not load arbitrary plugin code

### 3.2 What publishers can extend

- deployment bootstrap through template plugins
- action behavior through action plugins
- knowledge and search surfaces through data plugins
- shell behavior through `ShellContribution`

### 3.3 What the platform keeps ownership of

- component registry
- module registry
- runtime contracts
- auth model
- publish and apply flow
- secret storage
- verification and diagnostics

This is the architecture already supported by the marketplace and external publisher plans.

---

## 4) Microfrontend Plugin Model

The microfrontend model moves plugins from configuration into executable UI extensions.

### 4.1 Core properties

- plugins ship frontend code
- plugins render their own UI surface
- host shell needs a plugin loading and isolation layer
- compatibility must be maintained across plugin SDK versions and shell versions

### 4.2 What this enables

- custom UI beyond the built-in registry
- richer domain-specific workflows without waiting for platform-owned module types
- highly specialized partner experiences

### 4.3 What this costs

- executable code review
- stronger browser-side isolation
- plugin SDK versioning
- host-to-plugin bridge hardening
- failure isolation and degraded-mode UX
- auth and token exposure analysis
- much heavier operational support

This is not a small step up from config plugins.
It is a category shift in architecture.

---

## 5) Comparison

### 5.1 Security

Config-driven model:

- strongest posture
- no third-party code in runtime or shell
- easier to review
- easier to reason about secret boundaries

Microfrontend model:

- much riskier
- third-party code runs in the browser
- bridge, token, and host-context exposure become significant concerns

### 5.2 Release safety

Config-driven model:

- aligns with deployment drafts, published versions, and apply flow
- easier impact preview
- easier rollback

Microfrontend model:

- needs plugin bundle compatibility tracking
- needs UI-level rollout, fallback, and runtime guardrails
- one bad plugin can break rendering unless isolated well

### 5.3 Product consistency

Config-driven model:

- stronger visual and behavioral consistency
- plugins feel like first-class platform capabilities

Microfrontend model:

- faster extension flexibility
- weaker UX consistency unless heavily governed

### 5.4 Publisher power

Config-driven model:

- lower expressive ceiling
- enough for most template, action, data, and shell-mapping cases

Microfrontend model:

- highest expressive ceiling
- also highest review and support cost

### 5.5 Fit with current platform

Config-driven model:

- excellent fit

Microfrontend model:

- poor fit as a default
- only justifiable as a later exception path

---

## 6) Recommended Implementation Baseline

The marketplace implementation plan should be:

### Level 1

Core marketplace plugin types:

- `TemplateContribution`
- `ActionContribution`
- `KnowledgeSourceContribution`
- `ShellContribution`

Recommended deployment targets:

- `actionsConfig`
- `knowledgeSourceConfig`
- `shellConfig`
- deployment bootstrap request for template seeding

### Level 2

Trusted extension surfaces:

- plugins reference built-in module ids
- plugins reference built-in card or UI block types
- plugins supply metadata and view-model hints
- shell renders through fixed platform-owned registries

Examples:

- a product-search plugin maps to built-in `products` module and built-in product-card renderer
- a knowledge plugin maps to built-in `docs` or `search` module and built-in evidence-card renderer
- a CRM plugin later maps to a built-in `contacts` module only after the platform ships that module type

### Level 3

Not part of the baseline:

- sandboxed microfrontend plugin execution

This level should remain explicitly deferred.

---

## 7) Required Additions To The Current Marketplace Model

To make `Level 1` and `Level 2` real, the marketplace architecture should add a few explicit concepts.

### 7.1 `ShellContribution`

The marketplace model currently covers template, action, and data contributions clearly.

To support shell-aware plugins safely, add one internal contribution type:

- `ShellContribution`

Recommended uses:

- branding baseline
- built-in module visibility
- greeting and starter suggestions
- action presentation hints
- evidence presentation hints
- mapping to built-in cards and built-in modules

### 7.2 `shellConfig`

The deployment draft and version model should expose:

- `shellConfig`

This should be the resolved target for shell-related contributions.

### 7.3 Shell impact preview

Marketplace impact previews should include shell deltas such as:

- branding changes
- enabled modules
- greeting changes
- action presentation changes
- search and evidence presentation changes

### 7.4 Fixed module and card registries

The shell should maintain:

- a fixed built-in module registry
- a fixed built-in card or UI block registry

Plugins may reference entries in those registries.
Plugins may not add arbitrary executable renderers.

---

## 8) When Microfrontends Would Make Sense

Microfrontends should only be considered if all of the following are true:

- built-in module and card registries are no longer expressive enough
- high-value partner use cases cannot be modeled with config plus trusted extension surfaces
- the platform is willing to own a browser plugin sandbox and SDK lifecycle
- the business value is large enough to justify a much heavier review and support burden

Good examples of possible future candidates:

- a highly specialized analytics surface
- a complex industry-specific planner or builder
- a partner-owned embedded workspace that cannot be represented as built-in cards and built-in forms

Even then, it should start as:

- first-party only, or
- trusted partner only

Not as a public marketplace default.

---

## 9) If Level 3 Ever Happens

If the platform later chooses to support microfrontend plugins, the minimum safe posture should be:

1. iframe isolation first, not shared DOM or module federation first
2. typed host-to-plugin bridge only
3. no direct access to platform tokens or secret material
4. explicit capability grants
5. read-only by default
6. plugin failure isolation and timeout handling
7. strict versioned SDK contract
8. per-plugin disable and quarantine controls

Anything weaker would create unacceptable risk for a public marketplace.

---

## 10) Recommended Sequence

### Phase 0: Finish config-driven marketplace foundation

Scope:

- manifest schema
- compilation model
- install records
- exact-version installs
- publish and apply integration

### Phase 1: Add shell-aware config contributions

Scope:

- `ShellContribution`
- `shellConfig`
- shell impact preview

### Phase 2: Add trusted extension surfaces

Scope:

- built-in module registry
- built-in card and UI block registry
- plugin-to-module mapping
- plugin-to-card mapping

### Phase 3: Open external publisher support on Level 1 and Level 2

Scope:

- template plugins
- action plugins
- partner data plugins first
- shell contributions through fixed registries

### Phase 4: Re-evaluate whether Level 3 is actually needed

Decision gate:

- if Level 1 and Level 2 satisfy most product and partner use cases, stop there
- only begin Level 3 if there is a concrete, high-value, blocked use case

---

## 11) Recommendation

The recommended implementation plan is:

- adopt `Level 1: config plugins` as the core marketplace architecture
- adopt `Level 2: trusted extension surfaces` as the approved shell extensibility model
- explicitly defer `Level 3: microfrontend plugins`

This gives Loom AI:

- the marketplace flexibility it actually needs now
- a safer path for external publishers
- compatibility with current platform governance
- room to add more expressive shell behavior later without giving up control of runtime, auth, and shell stability

---

## 12) Non-Goals

This plan intentionally does not make the marketplace baseline:

- executable frontend plugins
- arbitrary shell code from publishers
- module-federation-based public plugin loading
- direct plugin access to host auth context
- direct plugin access to secrets or deployment internals

If the marketplace needs those on day 1, the architecture is aiming at the wrong level of extensibility too early.
