# Plugin Developer Extensibility Implementation Plan

Status: planning document with default-mode taxonomy clarified (2026-04-14)

This document is the implementation-oriented companion to:

- `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_DESIGN.md`

It keeps the core ecosystem direction from that document but makes the contract, validation, versioning, and install behavior explicit enough to implement safely.

This document should be treated as the implementation baseline for external developer extensibility.
The original extensibility document remains useful as product and ecosystem framing.

Related docs:

- `doc/Productization/MARKETPLACE_HIGH_LEVEL_DESIGN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/EXTERNAL_PLUGIN_PUBLISHER_MODEL_PLAN.md`
- `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE_IMPLEMENTATION_PLAN.md`

---

## 1) Executive Summary

External developer extensibility should be built as a declarative publisher system that compiles publisher-defined packages into the platform's normal deployment model.

The platform should allow external publishers to define:

- template plugins
- action plugins
- data plugins
- later, automation plugins as the next first-class default-mode type

But it should not allow publishers to define:

- arbitrary runtime code
- arbitrary shell code
- direct deployment-table mutations
- a second live install path outside deployment draft and version governance

The core implementation rules are:

- publisher bundles are versioned and immutable after publication
- every deployment install references one exact approved plugin version
- installs compile into deployment drafts
- publish and apply remain required before live behavior changes
- publishers target platform-owned adapter contracts
- surface, policy, and analytics extensions remain bounded capability profiles, not unrestricted new plugin execution models

---

## 2) Architectural Rules

The extensibility system should follow these rules:

1. plugins are declarative packages, not executable code
2. manifests compile into internal contribution objects before touching deployment drafts
3. installs are deployment-scoped and version-pinned
4. runtime consumes resolved deployment config, not publisher packages
5. shell consumes resolved shell and action metadata, not publisher UI code
6. secret values remain in the platform secret layer
7. upgrades are operator-governed, not auto-applied silently

These rules keep third-party publishing compatible with:

- release safety
- deployment draft ownership
- auth boundaries
- tenant and customer isolation
- future billing and revenue share

---

## 3) Extension Surfaces

The shipped and supported public baseline is:

- `TEMPLATE`
- `ACTION`
- `DATA`

### 3.1 Template plugins

Template plugins define deployment bootstrap packages.

They should resolve into:

- deployment bootstrap request
- seeded deployment draft
- normal post-bootstrap lifecycle

They should not become a persistent runtime dependency.

### 3.2 Action plugins

Action plugins define one or more declarative action contributions.

They should resolve into the existing deployment action model and use approved execution adapters.

### 3.3 Data plugins

Data plugins define one or more declarative knowledge-source contributions.

They should resolve into deployment-level knowledge source configuration and use approved retrieval adapters.

### 3.4 Future shell modules

Future shell module extensibility is acceptable only through a fixed platform-owned registry.

Plugins may later reference approved built-in module types, but they should not ship arbitrary custom rendering code.

---

## 4) Publisher Package Model

### 4.1 Package shape

Recommended bundle layout:

- `plugin.yaml`
- `README.md`
- `icon.png`
- `schemas/`
- `fixtures/`
- `openapi/` optional

Recommended rule:

- `plugin.yaml` is the required control file
- additional files support validation, preview, and documentation

### 4.2 Signing and integrity

Recommended publication requirement:

- every submitted bundle is signed or uploaded through a platform-controlled publisher identity flow
- the published artifact is stored immutably

This lets the platform tie every published version to one reviewed payload.

---

## 5) Manifest Contract

The manifest should be strict, small, and reviewable.

### 5.1 Top-level fields

Recommended fields:

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
- `upgradePolicy`
- `support`

### 5.2 Compatibility block

Recommended fields:

- `minPlatformVersion`
- `maxPlatformVersion` optional
- `requiredCapabilities`
- `supportedAuthModes`
- `supportedDeploymentTargets`
- `supportedProviderModes`

The platform should reject packages that declare capabilities the target deployment cannot safely support.

### 5.3 Install form block

Recommended allowed field types:

- `text`
- `url`
- `boolean`
- `select`
- `number`
- `secretRef`

Required rule:

- secret values are never stored in the manifest
- install records keep only secret references

### 5.4 Permission block

Recommended permission flags:

- `contributesTemplate`
- `contributesActions`
- `contributesKnowledgeSources`
- `requiresExternalHttpExecution`
- `requiresSharedDatasetAccess`
- `requiresDeploymentSecrets`

This block should drive automated validation and reviewer attention.

---

## 6) Internal Compilation Model

Publisher manifests should never write directly into deployment drafts.

Recommended pipeline:

1. ingest publisher bundle
2. parse and validate manifest
3. compile into internal contribution snapshot
4. persist catalog version and compiled output
5. resolve installs from that compiled output into deployment draft contributions

Recommended contribution types:

- `TemplateContribution`
- `ActionContribution`
- `KnowledgeSourceContribution`

Recommended rule:

- deployment drafts and published versions remain the live source of truth
- catalog versions and install records are inputs to resolution, not runtime config by themselves

---

## 7) Template Plugin Contract

Template plugins are the lowest-risk external extension point.

### 7.1 What template plugins may define

- deployment bootstrap defaults
- prompt preset references
- recommended action plugins
- recommended data plugins
- shell baseline config
- provider and vector defaults where allowed

### 7.2 What template plugins may not define

- live secrets
- direct-to-live deployment creation
- non-standard deployment lifecycles
- hidden post-bootstrap control over the deployment

### 7.3 Resolution rule

A template plugin should resolve into a normal deployment bootstrap request, which the backend then converts into a normal deployment draft through the same seeding path used by internal presets.

---

## 8) Action Plugin Contract

Action plugins should be the first meaningful third-party execution-oriented plugin type.

### 8.1 What action plugins may define

- action ids and labels
- input and output schemas
- read-only vs write metadata
- confirmation posture
- approved adapter type
- route contribution metadata
  - absolute `url`
  - relative `path`
  - HTTP method
  - non-secret request and response shaping defaults
- fixed non-secret defaults
- required operator-provided config and secret references

### 8.2 What action plugins may not define

- in-process Java handlers
- arbitrary runtime prompt logic
- custom policy engines
- direct access to secret values
- unrestricted network behavior

### 8.3 Approved adapter types

Action plugins should target a small allowlist of platform-owned adapters.

Recommended launch set:

- `connector-http`
- `runtime-proxy-http` only if a stable platform contract already exists

Recommended later additions:

- provider-native adapters owned by the platform

This is the critical constraint:

- publishers configure approved adapters
- publishers do not introduce new execution engines

### 8.4 Action resolution target

Resolved action plugins should compile into the existing deployment `actionsConfig` behavior model plus any platform-owned action metadata needed for runtime and shell presentation.

Recommended route resolution rule:

- plugin-defined routes should resolve into inline `actionsConfig.actions[].route`
- the deployment compiler should materialize the effective routing artifact from that resolved route contribution
- explicit deployment routing overrides should still win over plugin-provided inline route defaults

---

## 9) Data Plugin Contract

Data plugins are more sensitive than action plugins because they affect retrieval behavior, attribution, and shared access boundaries.

### 9.1 What data plugins may define

- knowledge source identity
- attribution label
- query scope filters
- source type
- expected result contract
- ranking and freshness hints
- dataset packages and dataset ids
- dataset or provider references
- approved sync connector config for supported connector classes

### 9.2 What data plugins may not define

- custom retrieval code running in runtime
- arbitrary reranking code in runtime
- embedded database credentials
- cross-tenant joins
- hidden access patterns that bypass isolation rules
- arbitrary ingestion workers or ETL code

### 9.3 Approved data adapter types

Recommended launch set:

- `shared-index`
- `remote-search-api`
- `provider-managed-dataset`
- `external-sync-sql`
- `external-sync-folder`

Recommended rules:

- `shared-index` points to a platform-governed, plugin-owned logical shared handle
- `remote-search-api` points to a read-only publisher-hosted search endpoint that matches the platform's canonical search result contract
- `provider-managed-dataset` points to a provider integration the platform already understands and can verify
- `external-sync-sql` points to a platform-approved SQL connector definition and query mapping, not publisher-executed SQL code
- `external-sync-folder` points to a platform-approved folder or object-storage connector, not arbitrary filesystem access

### 9.4 Data rollout rule

Do not open data plugins broadly on day 1.

Recommended order:

1. first-party data plugins
2. partner-only private data plugins
3. limited public data plugins after retrieval contracts, isolation proof, and operational review are mature

### 9.5 Data resolution target

Resolved data plugins should compile into deployment-level `knowledgeSourceConfig` or equivalent search-source bindings consumed by the runtime's approved retrieval abstraction.

Required productization follow-on:

- installed `DATA` plugins must also resolve into a platform-owned dataset lifecycle
- the platform should provision plugin-owned tenant-shared dataset handles
- packaged seed datasets and approved sync connectors should populate those handles before the install is treated as fully ready

See:

- `MARKETPLACE_DATA_PLUGIN_DATASET_PRODUCTIZATION_PLAN.md`

---

## 10) Install And Apply Model

This is the area where the original conceptual doc was not strict enough.

Recommended install flow:

1. operator selects exact plugin version
2. backend checks compatibility, entitlement, role, and tenant boundaries
3. backend creates or updates deployment-scoped install record
4. operator fills user config and secret references
5. compiler resolves contribution into deployment draft
6. platform shows diff and impact preview
7. operator publishes and applies through the normal release flow
8. install becomes active only after successful apply

Recommended uninstall flow:

1. mark install removal pending
2. remove contribution from deployment draft
3. preview impact
4. publish and apply normally
5. clean up deployment-owned secret refs where safe

Required rule:

- there is no direct live install path

---

## 11) Validation And Review

External publishing needs both automated validation and human review.

### 11.1 Automated validation

Recommended automated checks:

- manifest schema validity
- semantic version validity
- compatibility and capability checks
- install form validity
- contribution type validity
- JSON Schema validity
- adapter allowlist checks
- forbidden field detection
- URL and endpoint linting
- asset presence rules

### 11.2 Contract validation

Action and data plugins should go through contract validation.

Recommended checks:

- request and response contract shape
- auth failure behavior
- timeout behavior
- non-2xx behavior
- pagination or result-size behavior where relevant
- canonical fixture replay

Important rule:

- generic endpoint reachability should not be a submission-time hard requirement if the endpoint depends on operator-supplied secrets or deployment-specific config
- reachability and live integration proof should be checked in sandbox or install-time verification flows

### 11.3 Human review

Recommended review focus:

- claim accuracy
- security posture
- tenant and data-boundary assumptions
- operational support clarity
- misleading pricing or listing content

---

## 12) Versioning And Upgrade Model

This is where the implementation posture must stay stricter than the conceptual marketplace story.

Recommended rules:

- plugin ids are stable
- published versions are immutable
- deployment installs pin to one exact approved version
- existing installs do not auto-upgrade silently
- breaking changes require a new major version
- upgrades should show impact preview before publish and apply

Recommended operator experience:

- patch and minor updates can be offered as upgrade recommendations
- platform may support bulk upgrade assistance later
- live deployments should not change until an operator explicitly upgrades and applies

This preserves release safety and deployment predictability.

---

## 13) Security And Trust Model

The platform must remain the security boundary.

Required rules:

- no arbitrary code execution in runtime or shell
- no secret values in plugin bundles
- no secret values in deployment draft JSON
- no hidden direct cross-deployment access
- no plugin path that bypasses publish and apply
- timeouts, retries, and rate limits remain platform-governed

For publisher-hosted endpoints:

- action endpoints must authenticate through approved platform-supported mechanisms
- data endpoints must be read-only for retrieval use cases
- installs must fail closed if required config or secret refs are missing

### 13.1 Publisher identity

Recommended publisher verification:

- email verification minimum
- organization verification for paid publishers
- domain or API ownership verification where relevant

### 13.2 Revocation and quarantine

The platform should support:

- disable new installs
- quarantine unsafe versions
- notify impacted operators
- preserve audit trail for revocation actions

---

## 14) Conflict And Dependency Model

Recommended rules:

- plugin-defined action ids should be namespaced or deterministically collision-safe
- plugin dependencies should be explicit
- circular dependencies are rejected
- operators should be able to see dependency and conflict warnings before publish

For data plugins:

- operator-visible source priority is acceptable
- final ranking still belongs to the runtime retrieval system

---

## 15) Developer Workflow And Tooling

The platform should provide a constrained but usable publisher workflow.

Recommended publisher tooling:

- `loom plugin init`
- `loom plugin validate`
- `loom plugin pack`
- `loom plugin preview`
- `loom plugin submit`

Recommended portal capabilities:

- publisher identity management
- plugin version management
- review feedback
- sandbox deployments
- installation and error analytics
- payout and revenue reporting later

---

## 16) Recommended Rollout Sequence

### Phase 0: Internal-only foundation

Scope:

- manifest schema
- compiler
- validation pipeline
- catalog persistence

### Phase 1: External template plugins

Reason:

- lowest runtime risk
- validates publisher onboarding and review model

### Phase 2: External action plugins

Reason:

- high value
- manageable contract surface
- compatible with existing action architecture

### Phase 3: Partner-only data plugins

Reason:

- retrieval and shared-data safety need tighter control at first

### Phase 4: Public data plugins

Reason:

- only after retrieval contracts, isolation, and operational review are mature

### Phase 5: Billing and revenue share expansion

Reason:

- business layers should not block the core architectural foundation

### Phase 6: Future shell module references through fixed registry

Reason:

- only after the shell registry is stable and still without arbitrary publisher UI code

---

## 17) Recommended First Build Slice

The first practical external publisher slice should be:

- publisher identity and review workflow
- signed bundle upload
- manifest validation
- compilation to internal contribution snapshot
- external template plugin support

This proves the correct boundary before exposing action and data plugins.

---

## 18) Non-Goals

This plan intentionally does not introduce:

- arbitrary runtime plugin execution
- arbitrary shell rendering code from publishers
- auto-applied plugin upgrades to live deployments
- publisher bundles writing directly into deployment tables
- uncontrolled public data plugin onboarding on day 1

If any of those are required for the model to work, the extensibility boundary is wrong.
