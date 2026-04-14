# AI Application Shell Architecture Implementation Plan

Status: planning document (2026-04-08)

This document is the implementation-oriented companion to:

- `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE.md`

It keeps the core product vision from that document but makes the architectural boundaries explicit so the shell can be built safely on top of the platform's existing deployment, auth, and marketplace model.

This document should be treated as the implementation baseline.
The original shell document remains useful as product vision and positioning.

Related docs:

- `doc/Productization/future-work/Auth/AUTH_IMPLEMENTATION_SEQUENCE_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/EXTERNAL_PLUGIN_PUBLISHER_MODEL_PLAN.md`
- `doc/Productization/future-work/PLATFORM_AI_ASSISTANT_DEPLOYMENT_PLAN.md`

---

## 1) Executive Summary

The AI Application Shell should be built as a universal deployment-scoped UI surface that renders conversation, results, governance flows, and context views from one configuration-driven bundle.

It is not:

- a separate runtime
- a second policy engine
- a direct replacement for host application auth
- a plugin code execution environment

The shell should be:

- one shared frontend bundle
- deployment-config driven
- auth-mode aware
- component-registry based
- compatible with marketplace composition

The most important implementation rules are:

- the shell consumes resolved deployment configuration, not raw marketplace catalog state
- the shell must align to the platform auth foundation, with private-runtime integration as the default production posture
- plugins may influence shell behavior only through platform-approved configuration and fixed component or module registries
- the shell must not load arbitrary plugin UI code

---

## 2) Architectural Rules

The shell should follow these rules:

1. one universal shell bundle, no per-customer builds
2. deployment-specific behavior comes from resolved runtime and shell configuration
3. auth mode is determined by deployment configuration and resolved bootstrap state
4. the shell renders only platform-owned component types
5. marketplace plugins contribute metadata and capabilities through deployment config, not by injecting code into the shell
6. the shell remains a UI surface; orchestration, policy, and business execution stay in runtime and connector layers

These rules keep the shell compatible with:

- current deployment drafts and versions
- current release safety and apply flows
- current auth sequencing
- future marketplace composition

---

## 3) Product Model

The shell should be treated as a deployment-scoped application surface with three main responsibilities:

- conversation and evidence rendering
- governed action interaction
- context and domain surface presentation

The shell should not directly own:

- action execution policy decisions
- secret handling
- plugin catalog logic
- deployment publish and apply behavior
- cross-deployment data access

It should consume those outcomes from the platform and runtime.

---

## 4) Delivery Model

### 4.1 One bundle, many deployments

The shell should ship as one platform-maintained frontend bundle.

Recommended delivery forms:

- CDN-hosted loader for script-tag embed
- npm package for React and deeper integration

Recommended rule:

- both delivery forms use the same component registry and the same bootstrap contract

### 4.2 Deployment-scoped bootstrap

Each shell instance should bootstrap against one deployment context.

The bootstrap response should include:

- deployment identifier or handle
- auth mode
- shell configuration
- runtime access posture
- session bootstrap metadata
- feature and module availability

The bootstrap response should not include:

- live secrets
- raw marketplace catalog data
- unrestricted service endpoints

---

## 5) Auth And Connectivity Model

This document must follow the shared auth foundation.

### 5.1 Default production posture

The default production posture should be private-runtime mode:

- browser -> host backend or packaged app backend
- host backend -> private runtime
- runtime -> private connector

In this mode, the shell does not connect directly to runtime.

Instead, the shell talks to the trusted host surface, which then calls runtime using the shared auth model.

This is the default recommended posture for:

- storefront integrations
- customer applications
- packaged app backends such as Shopify-style wrappers

### 5.2 Explicit opt-in direct mode

Public-runtime browser access should remain an explicit opt-in mode.

Only in that mode should the shell bootstrap with:

- public runtime URL
- short-lived browser-safe bearer token
- deployment-approved feature subset for browser-direct access

This mode must be clearly modeled as a deployment capability, not an accidental consequence of a public URL existing.

### 5.3 Packaged integration mode

Packaged integrations should usually default to the private-runtime posture and consume the same auth foundation.

The shell must not invent a packaged-app-specific auth stack.

### 5.4 Auth-safe bootstrap contract

Recommended bootstrap fields:

- `authMode`
- `sessionBootstrapMode`
- `runtimeAccessMode`
- `hostBridgeRequired`
- `anonymousAllowed`
- `authenticatedUserContextAllowed`

The shell should derive behavior from these fields rather than assuming browser-direct runtime access.

---

## 6) Shell Configuration Model

The shell should be configured by a typed deployment-scoped `ShellConfig` model.

Recommended source-of-truth rule:

- `ShellConfig` belongs to the deployment draft and published version model
- marketplace installs may contribute to it only through internal `ShellContribution` fragments emitted by template, action, and data plugins
- the shell must never read raw marketplace install records or catalog entries directly

Recommended sections:

- branding
- layout
- enabled modules
- feature toggles
- greeting and starter prompts
- attachment capabilities
- action presentation hints
- search and evidence presentation hints
- governance UI behavior
- host integration capabilities

### 6.1 Branding

Recommended branding fields:

- title
- logo URL
- theme variables
- border radius
- typography token selection
- shell placement rules

### 6.2 Module visibility

Recommended module fields:

- built-in module ids
- enabled or disabled status
- default open target
- mobile and desktop visibility hints

Recommended rule:

- only platform-owned built-in module ids may appear in `ShellConfig`

### 6.3 Feature toggles

Recommended feature fields:

- history enabled
- attachments enabled
- debug or trace inspector enabled
- quick actions enabled
- inline confirmations enabled

### 6.4 Action presentation hints

The shell should receive presentation metadata for actions, but not raw policy logic.

Recommended fields:

- action display name
- icon or visual hint
- read-only vs write indicator
- confirmation requirement hint
- result presentation hint

Recommended source:

- these hints may come from resolved action metadata and marketplace-emitted `ShellContribution` fragments

### 6.5 Knowledge and evidence presentation hints

Recommended fields:

- search source attribution label
- evidence card style hint
- preferred search module visibility
- source trust or freshness hint for display

Recommended source:

- these hints may come from resolved knowledge-source metadata and marketplace-emitted `ShellContribution` fragments

---

## 7) Component Registry

The shell should render from a fixed platform-owned component registry.

Recommended built-in component types:

- conversation messages
- result cards
- evidence cards
- clarification forms
- confirmation prompts
- counter-offer prompts
- action outcome summaries
- context panel entries
- timeline or history entries later

Recommended rule:

- backend responses identify component type or UI block type
- shell maps those identifiers to built-in renderers
- plugins cannot ship arbitrary rendering code

This gives the platform extensibility without turning the shell into a code sandbox.

### 7.1 Fixed module and card registries

The shell should also maintain:

- a fixed built-in module registry
- a fixed built-in card or UI block registry

Recommended rule:

- marketplace plugins may reference entries in those registries through resolved `shellConfig`
- marketplace plugins may not register arbitrary executable renderers at runtime

### 7.2 Future expansion

Future component types are acceptable, but they should be added through platform releases, not by loading publisher-defined frontend code.

Recommended future additions:

- charts
- maps
- payment surfaces
- richer structured forms

---

## 8) Shell State Model

The shell should maintain only UI and session state, not source-of-truth business state.

Recommended local shell state:

- current conversation id
- current UI thread state
- open module or panel state
- cached bootstrap config
- local attachment selection
- optimistic UI state for confirmations and forms

Source-of-truth state should remain on platform or runtime surfaces:

- conversation persistence
- action execution results
- cart or host-domain state
- deployment capabilities
- identity and authorization

This avoids the shell becoming a second business state manager.

---

## 9) Runtime Response Contract

The shell should render from a typed runtime response contract rather than inferring everything from plain text.

Recommended response sections:

- conversation messages
- structured UI blocks
- evidence references
- governance state
- suggested follow-up actions
- context panel hints

Recommended rule:

- plain text remains valid
- richer rendering appears when runtime returns typed blocks or evidence
- governance flows should always come from explicit runtime state, not frontend guesswork

This lets the shell stay predictable while still supporting rich AI-native interactions.

---

## 10) Marketplace Integration

Marketplace should influence the shell only through resolved deployment configuration and runtime response contracts.

### 10.1 Shell contribution model

Marketplace should not introduce a separate public shell-plugin runtime.

Instead:

- template, action, and data plugins may emit internal `ShellContribution` fragments
- those fragments resolve into deployment-level `shellConfig`
- the shell renders only through fixed platform-owned module, card, and component registries

### 10.2 Action plugins

Action plugins may contribute:

- new actions to the deployment action catalog
- action presentation metadata
- optional mapping to existing built-in modules
- optional mapping to existing built-in card or UI block styles

Action plugins should not contribute:

- arbitrary shell code
- arbitrary new card renderers
- direct shell-side execution logic

### 10.3 Data plugins

Data plugins may contribute:

- new knowledge sources
- new evidence attribution labels
- new search-source visibility in existing search and documents views
- optional mapping to built-in evidence and document presentation styles

Data plugins should not contribute:

- custom retrieval algorithms running in the shell
- direct shell-side access to shared data stores

### 10.4 Template plugins

Template plugins may seed shell defaults such as:

- branding baseline
- enabled built-in modules
- greeting content
- starter suggestions
- layout and evidence-display defaults where supported by `shellConfig`

But after bootstrap, the deployment should behave like any normal deployment using normal shell configuration.

### 10.5 Shell-aware release safety

If a marketplace install changes shell behavior, those deltas should appear in the normal deployment diff and publish or apply preview.

Recommended shell preview areas:

- branding and greeting changes
- enabled module changes
- action presentation changes
- evidence presentation changes

### 10.6 Future shell module extensibility

If marketplace later supports plugin-defined shell modules, the safe version is:

- plugins reference a platform-owned module type id
- the shell renders a built-in module for that type
- plugins provide config and data hints only

The unsafe version would be:

- publishers shipping arbitrary UI code to run inside the shell

That should remain out of scope.

---

## 11) Host Integration Model

The shell should support host-aware integration through platform-defined bridges.

Recommended host integration capabilities:

- push current page context
- push signed user context
- push selected entity attachments
- receive shell events such as open, close, action completed, or handoff requested

Recommended rule:

- host integrations use typed bridge contracts
- host integrations do not bypass runtime auth and authorization

Examples:

- a Shopify page adapter pushes current product context
- a custom host app pushes authenticated user context
- a CRM page pushes selected account or ticket context

---

## 12) Desktop, Mobile, And Embedded Modes

The shell should support three presentation modes from the same bundle:

- overlay widget
- full-screen or bottom-sheet mobile shell
- embedded application surface

The component registry should stay the same across all three.

Only layout, panel behavior, and density should change.

This avoids fragmenting the product into multiple frontend architectures.

---

## 13) Security And Governance

The shell must inherit platform governance instead of bypassing it.

Required rules:

- no secrets stored in client configuration
- no implied direct runtime access without explicit auth-mode approval
- no shell-owned privilege escalation path
- write actions must render governed runtime-backed confirmation state
- deployment-scoped capability visibility must remain role and auth aware

Recommended rule:

- if a capability is not authorized, the shell should not simulate it optimistically as available

---

## 14) Operational Model

The shell should be operationally simple:

- versioned bundle delivery
- backwards-compatible bootstrap contract where possible
- telemetry for bootstrap success, render failures, auth failures, and module usage
- deployment-scoped feature flags only through resolved config

Recommended rollout support:

- bundle major version pinning
- deployment-safe fallback rendering when unknown response blocks appear
- traceable bootstrap failures with deployment and auth-mode context

---

## 15) Recommended Implementation Sequence

### Phase 0: Normalize the current shell contract

Scope:

- document the current component registry
- define typed bootstrap contract
- define typed runtime response contract
- separate shell config from host integration config

### Phase 1: Auth-aligned shell bootstrap

Scope:

- private-runtime first bootstrap posture
- explicit public-runtime opt-in bootstrap
- host bridge contract for private-runtime mode

Acceptance criteria:

- shell behavior differs safely by auth mode
- browser-direct runtime access is not assumed by default

### Phase 2: Deployment-config driven modules and governance

Scope:

- built-in module registry
- governed confirmation and clarification rendering
- deployment-scoped action presentation metadata

### Phase 3: Marketplace-aware resolved configuration

Scope:

- marketplace-contributed actions visible through normal action metadata
- marketplace-contributed knowledge sources visible through normal evidence views
- template-driven shell defaults at deployment bootstrap time

### Phase 4: Embedded and packaged integrations

Scope:

- React package parity with script-tag bootstrap
- richer host integration bridge
- packaged app compatibility on the same auth foundation

### Phase 5: Future fixed-registry module expansion

Scope:

- new platform-owned module types
- plugin references to approved module types
- no arbitrary publisher UI code

---

## 16) Recommended First Build Slice

The first implementation slice should be:

- define `ShellConfig`
- define bootstrap auth-mode contract
- define fixed built-in module registry
- align script-tag and React delivery around the same bootstrap path
- ensure private-runtime is the default modeled integration posture

This proves the shell can become a serious deployment surface without drifting away from the current auth and control-plane model.

---

## 17) Non-Goals

This plan intentionally does not introduce:

- a second runtime
- plugin-defined frontend code execution
- default browser-direct runtime access for all deployments
- shell-managed secret storage
- shell-owned publish and apply behavior
- plugin-specific frontend bundles per deployment

If any of those become required, the shell boundary is wrong.
