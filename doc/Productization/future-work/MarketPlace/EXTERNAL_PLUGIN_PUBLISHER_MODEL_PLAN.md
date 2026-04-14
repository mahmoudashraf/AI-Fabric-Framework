# External Plugin Publisher Model Plan

Status: planning document (2026-04-08)

This document defines how external developers should publish marketplace plugins without introducing arbitrary third-party code execution inside the platform runtime.

It extends:

- `doc/Productization/MARKETPLACE_HIGH_LEVEL_DESIGN.md`
- `doc/Productization/future-work/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`

The central rule is:

- external publishers define declarative plugin bundles
- the platform validates and compiles those bundles into internal contribution models
- deployment drafts and published versions remain the only live source of truth
- runtime never loads arbitrary publisher code

---

## 1) Executive Summary

External plugin publishing should be built as a constrained extension system, not a general plugin runtime.

Publishers should be allowed to define:

- template plugins
- action plugins
- data plugins

But all three should target platform-owned extension points and platform-owned contracts.

That means:

- publishers author manifests, schemas, and assets
- publishers may host their own action or search endpoints outside the platform
- publishers may provide datasets through approved ingestion or provider paths
- publishers do not ship Java jars, scripts, or orchestrator code for in-process execution
- publishers do not ship arbitrary shell frontend code

This model keeps:

- runtime safety
- tenant isolation
- draft/publish/apply governance
- secret boundaries
- reviewability

It also gives a realistic path to opening the marketplace gradually without redesigning the platform later.

---

## 2) Separation Principle

External publishing should be split across three concerns:

1. Publisher-defined package
   - declarative manifest
   - schemas
   - docs and assets
   - optional connector contract metadata
2. Platform control-plane processing
   - validation
   - review
   - compatibility checks
   - compilation into internal contribution objects
   - install and entitlement state
3. Live deployment behavior
   - normal deployment draft
   - normal published version
   - normal apply and verification
   - normal runtime config

The runtime should never need to know who published a plugin in order to execute it.

Runtime should only receive the resolved action config and resolved knowledge source config for the deployment version it is running.

The shell should only receive resolved `shellConfig`, action presentation metadata, and typed runtime response contracts for the deployment version it is rendering.

---

## 3) Publisher Package Model

### 3.1 Recommended package shape

A publisher bundle should be a signed archive with a predictable layout such as:

- `plugin.yaml`
- `README.md`
- `icon.png`
- `schemas/`
- `fixtures/`
- `openapi/`

Recommended rule:

- `plugin.yaml` is the only required control file
- everything else supports validation, install UX, or documentation

### 3.2 Publisher package contents

Recommended contents by type:

- template plugin
  - bootstrap manifest
  - preview metadata
  - optional prompt or config presets
- action plugin
  - action definitions
  - JSON Schemas for inputs and outputs
  - adapter configuration
  - optional OpenAPI fragment for the publisher endpoint
- data plugin
  - knowledge source definition
  - retrieval schema or result contract
  - attribution metadata
  - provider or dataset access metadata

---

## 4) Manifest Model

The publisher-facing manifest should be strict, versioned, and small enough to review.

### 4.1 Top-level fields

Recommended top-level fields:

- `schemaVersion`
- `pluginId`
- `version`
- `type`
- `publisherId`
- `displayName`
- `description`
- `category`
- `compatibility`
- `installForm`
- `permissions`
- `contributions`
- `shellContribution` optional through fixed platform-owned registries
- `upgradePolicy`
- `support`

### 4.2 Compatibility block

Recommended compatibility fields:

- `minPlatformVersion`
- `maxPlatformVersion` optional
- `requiredCapabilities`
- `supportedDeploymentTargets`
- `supportedAuthModes`
- `supportedProviderModes`

This lets the platform reject packages that do not fit the deployment and feature set.

### 4.3 Install form block

The install form should describe what the operator must supply.

Recommended field types:

- `text`
- `url`
- `boolean`
- `select`
- `number`
- `secretRef`

Recommended rule:

- only references to secrets are allowed in manifests and install records
- secret values themselves must never enter the plugin bundle or deployment draft

### 4.4 Permission block

The manifest should explicitly declare what the plugin is allowed to contribute.

Recommended permission types:

- `contributesTemplate`
- `contributesActions`
- `contributesKnowledgeSources`
- `contributesShellPresentation`
- `requiresExternalHttpExecution`
- `requiresSharedDatasetAccess`
- `requiresDeploymentSecrets`

This block is not just documentation. It should drive validation and review.

---

## 5) Internal Contribution Model

The platform should compile publisher manifests into internal typed objects before anything reaches the deployment draft.

Recommended contribution types:

- `TemplateContribution`
- `ActionContribution`
- `KnowledgeSourceContribution`
- `ShellContribution`

Recommended flow:

1. ingest signed plugin bundle
2. parse manifest
3. validate schema and compatibility
4. compile into internal contribution objects
5. persist catalog version and compiled snapshot
6. use the compiled snapshot during install resolution

This compiler boundary is important because it lets the platform:

- normalize publisher inputs
- reject unsupported combinations
- evolve internal deployment models without forcing publishers to target internal platform tables directly
- keep shell-facing contributions bounded to fixed registries instead of arbitrary frontend code

---

## 6) Action Plugin Model

Action plugins should be the easiest external plugin type to open first.

### 6.1 What publishers define

Publishers should be allowed to define:

- action ids and labels
- input and output schemas
- read-only vs write metadata
- confirmation posture
- adapter type
- route contribution metadata
  - absolute `url`
  - relative `path`
  - HTTP method
  - non-secret request and response shaping defaults
- fixed non-secret defaults
- required operator-provided config and secret references
- optional shell-facing presentation hints that target fixed platform-owned module or card registries

### 6.2 What publishers should not define

Publishers should not be allowed to define:

- in-process Java handlers
- direct runtime prompts
- unrestricted network behavior
- direct access to platform secret values
- direct writes to deployment draft JSON
- arbitrary shell rendering code
- arbitrary module implementations

### 6.3 Approved action adapter types

Action plugins should target a small set of approved adapter types.

Recommended launch set:

- `connector-http`
- `runtime-proxy-http` only if a stable contract already exists

Recommended later additions:

- provider-native adapters where the platform owns the integration contract

The key constraint is that adapter types are platform-owned. Publishers configure them. Publishers do not invent new execution engines.

Recommended shell-facing constraint:

- action plugins may reference built-in module ids or built-in card and UI block types
- action plugins may not introduce new executable shell renderers

### 6.4 Minimal action manifest example

```yaml
schemaVersion: 1
pluginId: calendly-booking
version: 1.0.0
type: ACTION
publisherId: acme
displayName: Calendly Booking
compatibility:
  minPlatformVersion: 1.0.0
  requiredCapabilities:
    - actions
installForm:
  - id: api_key
    type: secretRef
    required: true
  - id: event_type
    type: text
    required: true
permissions:
  contributesActions: true
  requiresExternalHttpExecution: true
  requiresDeploymentSecrets: true
contributions:
  actions:
    - id: calendly_create_booking
      displayName: Create Calendly Booking
      readOnly: false
      confirmationRequired: true
      adapterType: connector-http
      route:
        method: POST
        path: /actions/execute
      inputSchemaRef: ./schemas/create-booking-input.json
      outputSchemaRef: ./schemas/create-booking-output.json
      fixedConfig:
        provider: calendly
        authSecretField: api_key
        eventTypeField: event_type
```

Recommended route resolution rule:

- publisher-defined routes should resolve into inline `actionsConfig.actions[].route`
- the deployment compiler should materialize the effective routing artifact from that resolved route contribution
- explicit deployment routing overrides should still win over plugin-provided inline route defaults

---

## 7) Data Plugin Model

Data plugins are more sensitive than action plugins because they touch retrieval quality, attribution, shared access boundaries, and recurring cost.

### 7.1 What publishers define

Publishers should be allowed to define:

- knowledge source identity
- attribution label
- query filters and operator-configurable scope fields
- source type
- expected result schema
- ranking hints
- freshness metadata
- dataset or provider references
- optional shell-facing evidence presentation hints targeting built-in shell surfaces

### 7.2 What publishers should not define

Publishers should not be allowed to define:

- custom retrieval code running inside runtime
- arbitrary reranking code
- direct database credentials embedded in the bundle
- cross-tenant data joins
- hidden post-filtering that bypasses platform isolation rules
- arbitrary shell rendering code

### 7.3 Approved data adapter types

Recommended launch set:

- `shared-index`
- `remote-search-api`
- `provider-managed-dataset`

Each adapter type should have a strict platform contract.

Example:

- `shared-index`
  - points to a platform-governed shared vector root or provider-native shared handle
- `remote-search-api`
  - points to a publisher-hosted read-only search endpoint returning the canonical search result contract
- `provider-managed-dataset`
  - points to a provider integration the platform already understands and can verify

Recommended shell-facing constraint:

- data plugins may reference built-in search, docs, and evidence views
- data plugins may not ship custom shell-side retrieval or rendering implementations

### 7.4 Data plugin rollout advice

Do not open data plugins broadly on day 1.

Recommended order:

1. first-party data plugins
2. partner-approved private data plugins
3. limited public data plugins after search-source contracts and isolation proofs are mature

---

## 8) Template Plugin Model

Template plugins should be treated as curated deployment bootstrap packages.

### 8.1 What publishers define

Publishers should be allowed to define:

- deployment bootstrap defaults
- recommended action plugins
- recommended data plugins
- prompt preset references
- provider and vector defaults where allowed
- shell baseline defaults such as built-in module visibility, greeting, and starter suggestions

### 8.2 What template plugins should not do

Template plugins should not:

- bypass normal deployment bootstrap validation
- include live secrets
- create a non-standard deployment type
- create a direct-to-live installation path
- ship arbitrary shell frontend code

Recommended rule:

- a template plugin resolves into a normal deployment bootstrap request
- the backend seeds a normal deployment draft
- the deployment then follows the standard lifecycle
- any shell-related template defaults resolve into deployment-level `shellConfig`

---

## 9) Validation Pipeline

External plugins need both automated validation and human review.

### 9.1 Automated validation

Recommended automated checks:

- manifest schema validity
- asset presence and size rules
- semantic version validation
- compatibility and capability checks
- install form validation
- contribution type validation
- JSON Schema validity
- adapter type allowlist
- forbidden field detection
- URL and endpoint linting
- upgrade policy sanity checks

### 9.2 Contract validation

For action and data plugins, the platform should run contract tests.

Recommended checks:

- request and response shape validation against canonical contracts
- auth error behavior
- timeout and retry behavior
- non-2xx response handling
- pagination or result-size limits where relevant
- canonical fixture replay

### 9.3 Human review

Recommended human review focus:

- listing quality
- misleading claims
- security posture
- tenant boundary assumptions
- data licensing posture
- operational support clarity

Automated checks should block obviously invalid packages. Human review should decide whether a valid package is safe and useful enough to publish.

---

## 10) Review And Publication Workflow

Recommended publication workflow:

1. publisher registers and verifies identity
2. publisher uploads signed plugin bundle
3. platform runs automated validation
4. platform compiles to internal contribution snapshot
5. reviewer inspects manifest, docs, and contract results
6. reviewer approves, rejects, or requests changes
7. approved plugin version enters catalog as `PUBLISHED`
8. later installs use that exact approved version snapshot

Recommended status model:

- `DRAFT`
- `SUBMITTED`
- `VALIDATED`
- `REJECTED`
- `PUBLISHED`
- `DEPRECATED`
- `REMOVED`

---

## 11) Publisher Tooling

If external publishing is real, the platform should provide a small SDK and CLI.

Recommended tooling:

- `loom plugin init`
- `loom plugin validate`
- `loom plugin pack`
- `loom plugin preview`
- `loom plugin submit`

Recommended output from `validate`:

- schema issues
- compatibility issues
- forbidden field usage
- missing docs or assets
- contract fixture failures

This reduces support burden and makes the ecosystem easier to scale.

---

## 12) Security Model

The security model should be stricter than the internal first-party marketplace path, not weaker.

Required rules:

- no arbitrary code execution inside platform runtime
- no publisher-supplied jars, scripts, or containers loaded in-process
- no arbitrary publisher-supplied shell code loaded in the browser
- no secret values in plugin bundles
- no secret values in deployment draft JSON
- no hidden direct access to other deployments or tenants
- no runtime execution path that bypasses publish and apply

For publisher-hosted endpoints:

- action endpoints must authenticate through an approved mechanism
- data endpoints must be read-only for marketplace retrieval use cases
- timeouts, retries, and rate limits must be platform-governed
- install resolution must fail closed if required secrets are missing

---

## 13) Upgrade And Versioning Model

External publishing makes upgrade safety important.

Recommended rules:

- plugin ids are stable
- versions are immutable once published
- breaking changes require a new major version
- existing installs remain pinned until an operator upgrades
- upgrade preview must show contribution changes before publish and apply

Recommended install behavior:

- each deployment install references one exact approved plugin version
- the compiler resolves from that exact version snapshot
- catalog edits do not mutate existing live installs

---

## 14) Operational Model

External plugins create support and reliability obligations.

Recommended platform features:

- plugin health signals for remote endpoints
- publisher support contact metadata
- deprecation notices
- installation diagnostics
- per-plugin audit trail
- disable or quarantine path for unsafe published versions

If a plugin's external service is down, the deployment should degrade predictably rather than corrupting the deployment draft or live config.

---

## 15) Rollout Sequence

Recommended rollout:

### Phase 0: Internal-only foundation

Scope:

- manifest schema
- compiler
- validation pipeline
- catalog persistence
- shell contribution resolution through fixed registries

### Phase 1: External template plugins

Reason:

- lowest runtime risk
- easiest review path
- validates publisher onboarding without touching live execution too deeply

### Phase 2: External action plugins

Reason:

- strong value
- clear adapter contracts
- manageable validation surface

### Phase 3: Partner-only data plugins

Reason:

- lets the platform prove retrieval contracts, attribution, and shared-access safety before broad public exposure

### Phase 4: External shell presentation through fixed registries

Reason:

- plugins can influence shell behavior safely only after built-in module and card registries are stable
- this should remain config-driven rather than executable

### Phase 5: Public data plugins

Reason:

- only after search-source contracts, tenant isolation, billing posture, and operational review are mature

### Phase 6: Billing and revenue share

Reason:

- business layer should not block the architectural foundation

---

## 16) Recommended First Build Slice

The first external publisher slice should be:

- publisher identity and review workflow
- signed plugin bundle upload
- manifest validation
- compilation into internal contribution objects
- external template plugin support

This proves the correct architecture before opening action or data plugins.

If this first slice requires runtime code loading, the boundary is wrong.

---

## 17) Non-Goals

This plan intentionally does not introduce:

- arbitrary runtime plugin execution
- arbitrary shell plugin code execution
- publisher-defined orchestrator stages
- publisher-owned reranking code inside the platform
- plugin bundles with embedded credentials
- direct writes from publisher bundles into deployment tables
- uncontrolled public data plugin onboarding on day 1

External developers should be able to extend the platform, but only through contracts the platform can validate, govern, and safely resolve into the normal deployment lifecycle.
