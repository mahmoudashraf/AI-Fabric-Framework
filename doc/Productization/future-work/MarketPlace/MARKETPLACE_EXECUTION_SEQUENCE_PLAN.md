# Marketplace Execution Sequence Plan

Status: execution-sequencing document (2026-04-08)

This document turns the current marketplace planning set into one concrete implementation sequence.

It assumes the marketplace architecture is now settled around these decisions:

- marketplace is a control-plane composition layer
- plugins compile into deployment drafts and published versions
- publish and apply remain required before live behavior changes
- external publishers are declarative and version-pinned
- shell extensibility is `Level 1` plus `Level 2`
- sandboxed microfrontend plugins are explicitly not part of the baseline

Related implementation-baseline docs:

- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/EXTERNAL_PLUGIN_PUBLISHER_MODEL_PLAN.md`
- `doc/Productization/future-work/MarketPlace/AI_APPLICATION_SHELL_ARCHITECTURE_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/PLUGIN_DEVELOPER_EXTENSIBILITY_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/CONFIG_DRIVEN_MARKETPLACE_VS_MICROFRONTEND_PLUGIN_ARCHITECTURE_PLAN.md`

---

## 1) Current Position Before Marketplace Execution

The planning set is now coherent enough to execute.

What is already decided and should be treated as fixed baseline:

- marketplace plugins are declarative, not executable runtime plugins
- the three public plugin types remain `TEMPLATE`, `ACTION`, and `DATA`
- shell-facing behavior is contributed through internal `ShellContribution` fragments
- `ShellContribution` resolves into deployment-level `shellConfig`
- shell rendering stays inside fixed platform-owned module, card, and component registries
- external publisher installs are exact-version, reviewed, and operator-governed
- shell auth must align to the shared auth foundation, with private-runtime posture as the default production path

What is not yet productized and therefore shapes the sequence:

- marketplace catalog and install persistence
- compiler from plugin definitions into deployment drafts
- first-party template and action plugin install flow
- shell impact preview in deployment diff and apply UX
- data-plugin retrieval integration through the runtime search-source abstraction
- external publisher portal, validation, and review workflow
- billing, entitlements, and open marketplace operations

Numbering note:

- this is a separate marketplace workstream
- item numbering starts at `1` inside this sequence

---

## 2) Sequencing Principles

The marketplace sequence should follow these rules:

- preserve one live source of truth: deployment drafts and published versions
- do not introduce a second live install path outside publish and apply
- prove internal first-party plugin installation before opening third-party publishing
- add shell-facing contributions through fixed registries before considering richer UI extensibility
- add data plugins only after the runtime search-source abstraction is stable
- keep secrets in the platform secret layer and out of plugin bundles and deployment draft JSON
- make every plugin change previewable in the normal deployment diff and apply UX
- keep billing and revenue share later than the core compiler and install model
- treat sandboxed microfrontend plugins as explicitly out of scope for the baseline waves

---

## 3) Recommended Execution Sequence

### Wave 1: Marketplace control-plane foundation

Goal:

- create the core marketplace domain model and compiler without touching runtime behavior more than necessary

Primary focus:

- catalog
- versioning
- install records
- draft compilation
- shell contribution target model

Ordered scope:

1. plugin manifest schema and validation baseline
2. marketplace catalog entities and version persistence
3. deployment-scoped install records, secret references, and impact snapshot entities
4. compiler baseline for template, action, data, and internal shell contributions
5. deployment draft targets for `actionsConfig`, `knowledgeSourceConfig`, and `shellConfig`
6. exact-version install pinning and immutable published plugin version snapshots

Wave 1 completion signal:

- the platform can store reviewed plugin definitions and resolve them into draft-safe internal contribution outputs without changing live deployments directly

### Wave 2: First-party marketplace install flow

Goal:

- prove that first-party plugins can be installed, previewed, published, applied, and uninstalled through the normal platform lifecycle

Primary focus:

- internal catalog UX
- template bootstrap
- action plugin install flow
- shell-aware diffing

Ordered scope:

7. internal marketplace catalog read APIs and operator listing UI
8. template plugin bootstrap flow through the normal deployment seeding path
9. action plugin install flow with user-config fields and secret references
10. action plugin compilation into `actionsConfig`
11. shell contribution preview in deployment diff, release preview, and apply impact views
12. uninstall and removal flow with cleanup posture for deployment-owned secret references

Wave 2 completion signal:

- first-party template and action plugins work end to end with no special live mutation path outside draft, publish, and apply

### Wave 3: Runtime search-source and trusted shell extension surfaces

Goal:

- add data-plugin execution support and make shell contributions first-class through fixed registries

Primary focus:

- runtime retrieval abstraction
- `knowledgeSourceConfig`
- built-in shell registries
- `Level 2` trusted extension surfaces

Ordered scope:

13. runtime `SearchSource` or equivalent retrieval abstraction
14. data plugin compilation into `knowledgeSourceConfig`
15. evidence attribution and merged retrieval behavior for plugin-provided data
16. built-in shell module registry
17. built-in card or UI block registry
18. resolved `shellConfig` support for module mapping, card mapping, branding, greeting, and evidence presentation hints
19. shell bootstrap and runtime response contract alignment for marketplace-contributed shell behavior

Wave 3 completion signal:

- data plugins can surface through normal retrieval and evidence flows, and shell-aware plugin behavior works only through fixed built-in registries

### Wave 4: External publisher foundation and private beta

Goal:

- open the marketplace to controlled external publishers without weakening review, release safety, or shell and runtime boundaries

Primary focus:

- publisher identity
- bundle submission
- validation
- review workflow
- sandbox installs

Ordered scope:

20. publisher identity, verification, and signed-bundle submission model
21. publisher manifest validation, contract validation, and review workflow
22. publisher CLI and sandbox testing path
23. invite-only external template plugin support
24. invite-only external action plugin support
25. partner-only external data plugin support
26. unsafe-version quarantine, revocation, diagnostics, and support metadata

Wave 4 completion signal:

- selected external publishers can ship reviewed, version-pinned plugins through the same compiler and deployment lifecycle as first-party plugins

### Wave 5: Commercialization and open marketplace expansion

Goal:

- add the commercial and scale layers only after the install, retrieval, shell, and publisher foundations are already stable

Primary focus:

- billing
- entitlements
- upgrade operations
- open publication

Ordered scope:

27. one-off and recurring pricing records plus install entitlement state
28. entitlement-aware install resolution, suspend, cancel, and grace handling
29. operator upgrade assistance and shell-aware impact previews for plugin version changes
30. public data-plugin rollout after retrieval and isolation posture are proven
31. broader verified publisher onboarding and open marketplace moderation
32. revenue share, payout operations, and marketplace analytics

Wave 5 completion signal:

- the marketplace can support paid plugins, governed upgrades, and broader publisher participation without weakening platform control-plane guarantees

---

## 4) Scope Notes

The marketplace sequence should explicitly include:

- first-party marketplace install flow
- compiler-backed plugin resolution
- shell-aware contribution handling through `shellConfig`
- fixed built-in module and card registries
- external publisher review and validation
- partner-first data plugin rollout
- later billing and commercial operations

The marketplace sequence should explicitly not attempt to finish:

- arbitrary runtime plugin loading
- arbitrary shell frontend code from publishers
- microfrontend or module-federation marketplace plugins as the baseline
- direct live install without publish and apply
- public open marketplace on day 1

Those remain outside the baseline sequence.

---

## 5) Completion Criteria

The marketplace sequence should be considered complete only when:

- plugin definitions are versioned, validated, and reviewable
- installs are deployment-scoped, exact-version, and compile into deployment drafts
- publish and apply remain the only path to live plugin behavior
- template, action, data, and shell-facing contributions all resolve into the normal deployment model
- shell behavior can be influenced only through `shellConfig` and fixed built-in registries
- external publishers can submit reviewed plugins without introducing executable runtime or shell code
- billing and entitlement posture can disable or suspend installs predictably without corrupting deployment config

---

## 6) Recommended First Build Slice

The first implementation slice should be:

1. plugin manifest schema
2. catalog and install entities
3. compiler baseline
4. `shellConfig` as deployment state
5. first-party template bootstrap
6. first-party action plugin install flow

This is the smallest slice that proves the architecture is real instead of just conceptual.

It also avoids the most common failure mode:

- spending time on publisher onboarding, billing, or shell over-flexibility before the basic install-to-draft-to-apply path is stable

---

## 7) What Not To Do Next

Avoid spending the next wave on:

- public third-party publishing before internal first-party install flows are proven
- billing before install compilation and upgrade safety exist
- microfrontend plugin loading as a shortcut for shell extensibility
- custom shell renderers before fixed built-in registries exist
- data plugin expansion before the runtime search-source abstraction is stable

The main bottleneck now is not marketplace polish.

The bottleneck is making plugin installation a safe, previewable, deployment-governed platform path.
