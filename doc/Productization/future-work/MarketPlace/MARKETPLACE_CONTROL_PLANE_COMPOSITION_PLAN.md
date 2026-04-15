# Marketplace Control-Plane Composition Plan

Status: implementation-baseline document with phases 0-5 first slice landed and default-mode taxonomy clarified (2026-04-14)

This document turns the marketplace high-level design into an implementation shape that can be built as a separate feature stream.

It is intentionally opinionated about one core rule:

- the marketplace must be a control-plane composition layer
- it must not become a new runtime plugin-loading system

Related docs:

- `doc/Productization/MARKETPLACE_HIGH_LEVEL_DESIGN.md`
- `doc/Productization/future-work/ADVANCED_DEPLOYMENT_CREATE_FLOW_OPTIONAL_PLAN.md`
- `doc/Productization/future-work/PLATFORM_EXECUTION_SEQUENCE_WAVE3_PLAN.md`
- `doc/Productization/future-work/PLATFORM_EXECUTION_SEQUENCE_WAVE4_PLAN.md`
- `Final_Documentation/User_Guides/PLATFORM_CONFIG_AND_SECRETS_MANAGEMENT_GUIDE.md`

---

## 1) Executive Summary

The marketplace should be built as a separate feature by treating plugins as declarative packaging that compiles into the platform's existing deployment model.

Runtime/framework prerequisites for this plan are now in place:

- deployment-scoped `knowledgeSourceConfig`
- deployment-scoped `shellConfig`
- runtime search-source abstraction
- evidence attribution
- action presentation metadata
- runtime diagnostics and release verification for resolved marketplace capabilities

That means:

- plugin definitions live in the marketplace catalog
- deployment-scoped plugin installs live in marketplace installation records
- the platform resolves those installs into the existing deployment draft and published version model
- runtime and orchestrator consume only the resolved deployment config

This keeps the marketplace separate from the current platform core in the right way:

- no arbitrary code loading in runtime
- no second apply path outside the standard draft -> publish -> apply lifecycle
- no secrets stored in plugin manifests or deployment drafts
- no special runtime awareness of billing, catalog publishing, or marketplace ownership

Implemented control-plane baseline:

- catalog persistence and versioned manifests
- deployment-scoped install records
- template bootstrap flow
- action plugin compilation into `actionsConfig`
- data plugin compilation into `knowledgeSourceConfig`
- install live-state and readiness tracking
- compatibility and install-form validation
- operator marketplace workspace in the platform UI

Recommended product rule:

- `template plugins` bootstrap new deployment drafts
- `action plugins` compile into the existing `actionsConfig` model, including inline route contributions that the compiler resolves into the effective routing artifact
- `data plugins` compile into the existing deployment-scoped `knowledgeSourceConfig` model that runtime already reads through one narrow search-source abstraction
- `automation plugins` should be the next default-mode first-class plugin type and should compile into platform-owned workflow and eventing surfaces rather than arbitrary background code
- template, action, and data plugins may also emit internal `ShellContribution` fragments that compile into deployment-scoped `shellConfig`
- surface, policy, and analytics behavior should be represented as bounded capability profiles attached to those plugin types, not as separate arbitrary-code plugin classes

---

## 2) Separation Principle

The marketplace should sit above the platform control plane, not inside the runtime execution core.

Recommended boundary:

1. Marketplace owns:
   - catalog
   - plugin definition schema and validation
   - install records
   - compatibility checks
   - subscription and entitlement state
   - compilation into deployment draft contributions
2. Platform deployment model owns:
   - deployment drafts
   - published versions
   - release previews
   - apply flows
   - secrets
   - verification and audit
   - resolved `shellConfig`
3. Runtime, orchestrator, and shell own:
   - normal action execution
   - normal retrieval and answer generation
   - source attribution

The runtime should not know:

- marketplace listing metadata
- catalog categories
- plugin pricing
- install UX state
- publisher identity

Runtime should only know the resolved action config and resolved knowledge source bindings for the live deployment version.

The shell should only know the resolved `shellConfig`, action presentation metadata, and runtime response contracts for the live deployment version.

---

## 3) Plugin Type Model

Public marketplace plugin types must stay aligned with runtime-backed contracts.

Current supported public types:

- `TEMPLATE`
- `ACTION`
- `DATA`

Unsupported public marketplace surfaces that do not compile into runtime-backed contracts are being removed. They are not part of the forward implementation baseline.

### 3.1 Template plugins

Template plugins are deployment bootstrap presets.

They should:

- create a new deployment bootstrap request
- resolve through the same backend seeding path as other deployment presets
- produce a normal deployment draft
- follow the normal publish and apply lifecycle afterward

Template plugins are closest to the advanced create flow, not to runtime extensibility.

### 3.2 Action plugins

Action plugins are declarative packages that contribute one or more actions to a deployment.

They resolve into the same existing action model already used by custom actions:

- action ids
- descriptions
- input contracts
- routing or execution config
- read-only vs write metadata
- confirmation policy
- auth and connector references

Route rule:

- action plugins may contribute an inline action `route`
- that route may use an absolute `url` or a relative `path`
- the deployment compiler remains the source of truth for generating the effective routing artifact
- explicit deployment routing overrides still win over plugin-provided inline route defaults

The install record stores marketplace-specific information.
The deployment draft stores the resolved action behavior.

### 3.3 Data plugins

Data plugins are declarative packages that contribute one or more deployment-external knowledge sources to a deployment.

They should not add custom retrieval code into runtime.

They resolve into a deployment-level `knowledgeSourceConfig` contribution model that describes:

- source identity
- source type
- attribution label
- entitlement requirement
- query scope filters
- access policy
- provider handle or plugin-owned shared collection reference

Required ownership rule:

- a `DATA` plugin owns its own logical dataset boundary
- deployments that install the same plugin may reuse that dataset boundary under the same tenant
- other plugins must not share that dataset boundary by default

Required product rule:

- `knowledgeSourceConfig` alone is not enough
- the platform must also own dataset lifecycle for installed `DATA` plugins
- packaged seed datasets and approved external sync connectors should populate the plugin-owned dataset handle before the release is considered fully ready

See:

- `MARKETPLACE_DATA_PLUGIN_DATASET_PRODUCTIZATION_PLAN.md`

### 3.4 Shell-facing contributions

The marketplace should not introduce a separate public `SHELL` plugin type.

Instead:

- template, action, and data plugins may emit internal shell contribution fragments
- those fragments compile into deployment-level `shellConfig`
- the shell renders only through fixed platform-owned module and component registries

---

## 4) Recommended Domain Model

### 4.1 Catalog-side entities

Recommended core entities:

- `MarketplacePlugin`
  - stable plugin id
  - type: `TEMPLATE`, `ACTION`, `DATA`
  - publisher
  - category
  - listing metadata
- `MarketplacePluginVersion`
  - semantic version
  - definition payload
  - compatibility rules
  - release status
- `MarketplacePluginPrice`
  - free, one-off, recurring
  - billing metadata
  - availability rules

These entities belong to the marketplace catalog.
They are not deployment config.

### 4.2 Deployment-scoped entities

Recommended installation entities:

- `DeploymentPluginInstall`
  - install id
  - deployment id
  - plugin id
  - plugin version
  - install status
  - user-config snapshot
  - required secret references
  - entitlement or subscription status
  - compiled contribution hash
- `DeploymentPluginInstallSecretRef`
  - install id
  - secret purpose
  - secret reference id
  - missing or satisfied status
- `DeploymentPluginImpactSnapshot`
  - install id
  - resolved draft impact preview
  - changed actions
  - changed knowledge sources
  - changed shell configuration
  - changed prompts or defaults
  - compatibility warnings

These entities belong to the control plane.
They should be auditable and version-aware.

### 4.3 Draft and version entities

Deployment drafts and published versions remain the source of truth for live behavior.

Recommended rule:

- install records are inputs to compilation
- deployment drafts and versions are the resolved outputs

The draft or version should only contain non-secret resolved behavior, consistent with existing platform rules.

That means:

- action contribution config belongs in deployment draft/version
- knowledge source contribution config belongs in deployment draft/version
- shell contribution config belongs in deployment draft/version as resolved `shellConfig`
- literal secrets do not belong in deployment draft/version

---

## 5) Compilation Model

The compiler is the key to building marketplace separately.

Recommended resolution pipeline:

1. load the selected plugin definition version
2. load deployment-scoped install state
3. validate compatibility against the target deployment
4. validate required secrets and user-provided fields
5. resolve fixed config plus user config into a typed contribution
6. write the contribution into the deployment draft
7. surface release impact preview through the normal revisions and apply UX

Recommended contribution targets:

- template plugin -> deployment bootstrap request -> normal deployment draft
- action plugin -> `actionsConfig`
- data plugin -> `knowledgeSourceConfig`
- template, action, and data plugins -> internal `ShellContribution` fragments -> resolved `shellConfig`

Important rule:

- runtime artifacts and live service config must be generated from deployment versions, not directly from marketplace install records

This preserves one source of truth for production behavior.

---

## 6) Installation And Apply Flow

### 6.1 Action or data plugin install flow

Recommended flow:

1. operator selects plugin and version from the catalog
2. backend checks deployment compatibility, role, tenant boundary, and entitlement posture
3. backend creates or updates a `DeploymentPluginInstall` in draft state
4. operator fills user-config fields and secret references
5. marketplace compiler resolves the install into the deployment draft
6. platform shows impact preview using the existing draft, diff, and apply UX, including shell-facing deltas where relevant
7. operator publishes and applies through the normal release flow
8. install becomes `ACTIVE` only after successful apply and verification posture

This keeps installation separate from runtime mutation.

### 6.2 Uninstall flow

Recommended flow:

1. operator requests uninstall
2. backend marks install as `REMOVAL_PENDING`
3. compiler removes the contribution from the deployment draft
4. platform shows impact preview
5. operator publishes and applies normally
6. after successful apply:
   - install becomes `REMOVED`
   - deployment-owned secrets can be cleaned up if safe
   - shared data is unlinked, not deleted

### 6.3 Template flow

Recommended flow:

1. operator selects a template plugin
2. backend creates a compact template bootstrap request
3. backend resolves it into the normal deployment draft model
4. operator completes remaining config and secrets through the standard workspaces
5. deployment follows the standard publish and apply path

This should reuse the same backend seeding principle already recommended for advanced deployment creation.

---

## 7) API Shape

The marketplace API should be split into catalog APIs and deployment install APIs.

### 7.1 Catalog APIs

Recommended endpoints:

- `GET /api/marketplace/plugins`
- `GET /api/marketplace/plugins/{pluginId}`
- `GET /api/marketplace/plugins/{pluginId}/versions/{version}`
- `GET /api/marketplace/categories`

These are read-oriented catalog surfaces.

### 7.2 Deployment install APIs

Recommended endpoints:

- `GET /api/deployments/{deploymentId}/marketplace-installs`
- `POST /api/deployments/{deploymentId}/marketplace-installs`
- `PUT /api/deployments/{deploymentId}/marketplace-installs/{installId}`
- `DELETE /api/deployments/{deploymentId}/marketplace-installs/{installId}`
- `POST /api/deployments/{deploymentId}/marketplace-installs/{installId}/resolve`
- `GET /api/deployments/{deploymentId}/marketplace-impact`

Recommended API behavior:

- `POST` and `PUT` change install intent and draft contributions
- they do not directly mutate the live deployment
- publish and apply remain separate existing platform actions

### 7.3 Template bootstrap API

Recommended endpoint:

- `POST /api/marketplace/templates/{pluginId}/bootstrap`

Recommended behavior:

- accept compact user choices
- resolve through the same backend seeding path as presets
- create a normal deployment draft

---

## 8) Minimal Runtime And Orchestrator Change

The marketplace should require only one meaningful new runtime abstraction for data plugins.

Recommended runtime addition:

- introduce a `SearchSource` or equivalent retrieval-source contract

Each live deployment version should resolve to one list of enabled search sources, such as:

- deployment-private vector spaces
- deployment-private knowledge bases
- marketplace shared data sources

Recommended retrieval flow:

1. orchestrator selects eligible search sources
2. runtime queries each eligible source through the same abstract contract
3. results are merged and ranked
4. final answer includes source attribution

Recommended evidence metadata:

- source type
- source id
- plugin id when the source came from a marketplace data plugin
- attribution label
- trust or ranking hints

Important non-goals:

- no runtime catalog lookup
- no runtime subscription billing logic
- no plugin-specific execution container
- no arbitrary plugin code loading

### 8.1 Action plugin runtime impact

Action plugins should not need a new runtime action system.

They should use the existing action execution path and existing policy model.

The only likely addition is richer metadata on actions that already aligns with broader runtime-quality plans, such as:

- `readOnly`
- `answerGroundingEligible`
- `sideEffectLevel`

Those are useful with or without marketplace.

---

## 9) Security And Governance

Marketplace should inherit the platform's existing governance boundaries instead of introducing weaker shortcuts.

Required rules:

- plugin definitions never contain live customer secrets
- deployment drafts never contain literal plugin credential values
- install flow must use the platform secret store for required secrets
- publish and apply remain the only path to live behavior changes
- marketplace installs must respect deployment role checks and approval posture
- tenant and customer boundaries must still govern shared data access

Recommended additional rule for data plugins:

- a shared knowledge source must be customer-safe by construction
- if a data provider cannot support safe cross-deployment sharing at the intended boundary, the platform should not model it as a shared marketplace data plugin

Recommended additional rule for shell-facing contributions:

- plugins may reference only fixed platform-owned module ids and built-in card or UI block registries
- plugins may not inject arbitrary executable frontend code

---

## 10) What Can Be Built In Parallel

Marketplace can be split into safe parallel tracks.

### 10.1 Can be built mostly separately now

- plugin definition schema and validation
- first-party catalog and listing UI
- deployment install records and impact previews
- template bootstrap flow
- action plugin compilation into `actionsConfig`

These mostly depend on existing draft, version, secret, and apply primitives that already exist.

### 10.2 Depends on already-implemented runtime retrieval support

- data plugin compilation into `knowledgeSourceConfig`
- search-source resolution in runtime
- attribution for shared plugin evidence

The runtime-side retrieval support is already implemented.
The remaining work is control-plane resolution, compatibility enforcement, and product workflow.

### 10.3 Should remain later

- payout and revenue-share settlement
- broad open ecosystem support

Those are marketplace-business layers, not the first architectural milestone.

---

## 11) Phased Rollout

### Phase 0: Schema and catalog foundation

Scope:

- plugin manifest schema
- validation pipeline
- catalog persistence
- internal first-party listings only

Acceptance criteria:

- platform can store and validate versioned marketplace plugin definitions
- catalog can list templates, action plugins, and data plugins
- no additional runtime/framework prerequisite is required before entering Phase 1

Status:

- implemented

### Phase 3B: Automation plugins

Scope:

- deployment-scoped automation install records
- workflow trigger, action, and template contributions
- compilation into platform-owned workflow and eventing config
- operator-visible impact preview and governance

Acceptance criteria:

- installed marketplace automation plugins compile into normal deployment-governed workflow behavior
- workflow triggers and actions remain observable and auditable
- automation plugins may reuse existing action and data contributions without bypassing platform governance

Status:

- implemented

### Phase 1: Template plugins

Scope:

- template plugin bootstrap flow
- recommended add-on listing
- deployment draft seeding through marketplace templates

Acceptance criteria:

- operator can create a valid deployment draft from a marketplace template
- resulting deployment behaves like any other deployment afterward

Status:

- implemented

### Phase 2: Action plugins

Scope:

- deployment plugin install records
- user-config fields and secret references
- compiler into `actionsConfig`
- inline route contribution support for absolute `url` and relative `path`
- explicit deployment routing override support over plugin-provided route defaults
- impact preview and uninstall path

Acceptance criteria:

- installed marketplace actions compile into normal deployment action config
- installed marketplace actions can express inline route contributions without requiring raw routing-table mutation by operators
- publish and apply are unchanged
- runtime does not know what a marketplace action plugin is

Status:

- implemented

### Phase 3: Data plugins

Scope:

- `knowledgeSourceConfig`
- runtime search-source abstraction
- shared-source attribution
- install, unlink, and preview flows
- plugin-owned dataset handle model
- packaged dataset seeding
- approved external sync connectors such as SQL and folder-backed imports
- apply-time dataset readiness

Acceptance criteria:

- installed marketplace data sources are queried as normal retrieval sources
- answer evidence clearly attributes plugin-provided data
- uninstall unlinks the source without deleting shared provider data
- starter data plugins return real content without manual live seeding
- plugin-created data plugins can declare approved sync connectors without adding custom ingestion code

Status:

- implemented for config compilation and runtime retrieval support
- dataset lifecycle productization remains the next control-plane slice

### Phase 4: Billing and entitlements

Scope:

- one-off and recurring purchase state
- grace, suspend, and cancel lifecycle
- deployment install entitlement enforcement

Acceptance criteria:

- unpaid or lapsed installs resolve predictably without corrupting deployment config
- runtime still consumes only resolved deployment behavior, not raw billing state

Status:

- implemented

### Phase 5: Third-party publishing

Scope:

- publisher accounts
- review workflow
- policy checks
- first external publisher submission and publication flow

Acceptance criteria:

- third-party plugin definitions are reviewed and published without weakening platform governance

Status:

- implemented for the first practical external slice
- payout and revenue-share business workflow remains later

---

## 12) Recommended First Build Slice

The first practical slice should be:

- internal first-party template plugins
- internal first-party action plugins
- deployment-scoped install records
- compiler into normal deployment drafts
- no billing
- no third-party publishing
- no arbitrary runtime plugin system

This slice is now implementation-ready because the runtime/framework support baseline is already complete.

This slice proves the core architectural decision:

- marketplace is packaging and composition
- deployment versions remain the live source of truth

It also avoids the biggest failure mode:

- accidentally creating a second, lower-governance platform path that bypasses draft, publish, apply, and secrets management

---

## 13) Non-Goals

This plan intentionally does not introduce:

- arbitrary Java or script plugin loading in runtime
- plugin-specific sidecar services per deployment
- direct live install without publish and apply
- a second secret store
- third-party ecosystem complexity in the first release

If the marketplace requires those to function, the boundary is wrong.
