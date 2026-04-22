# Marketplace Runtime and Framework Support Checklist

Status: execution checklist completed (2026-04-14)

Execution status:

- Wave 0 complete
- Wave 1 complete
- Wave 2 complete
- Wave 3 complete
- Wave 4 complete
- Wave 5 complete

This checklist is the execution companion to:

- `doc/Productization/future-work/MarketPlace/MARKETPLACE_RUNTIME_AND_FRAMEWORK_SUPPORT_IMPLEMENTATION_PLAN.md`

It is intentionally task-oriented.

It does not restate marketplace catalog, billing, publisher, or install-record work.
It only tracks the runtime and framework capabilities needed so resolved marketplace contributions can actually work after control-plane compilation.

---

## 1) Execution Rule

Do not build marketplace-specific behavior into runtime.

Every task below must preserve this boundary:

- runtime consumes resolved deployment artifacts only
- runtime does not read marketplace catalog state
- runtime does not evaluate install records
- shell consumes resolved `shellConfig` and typed runtime responses only
- no arbitrary plugin code in runtime
- no arbitrary plugin code in shell

---

## 2) Wave 0: Resolved Contract Foundation

Status: complete

Goal:

- define the resolved deployment-scoped contracts that marketplace compilation will target

### Tasks

- Define a deployment-scoped `knowledgeSourceConfig` artifact contract.
- Define a deployment-scoped `shellConfig` artifact contract.
- Define resolved action metadata extensions needed by runtime and shell.
- Define evidence attribution metadata fields for runtime responses.
- Define a runtime capability introspection contract:
  - supported action adapter types
  - supported knowledge-source adapter types
  - supported auth modes
  - supported shell module ids
  - supported card or UI block ids
- Extend runtime config loading to read the new resolved artifacts safely.
- Add empty/default-safe loading for deployments that do not use these artifacts.
- Define contract version identifiers for the new resolved artifact surfaces.

### Acceptance Criteria

- Runtime can boot successfully with no `knowledgeSourceConfig` and no `shellConfig`.
- Runtime can expose the resolved contract versions through admin diagnostics.
- No runtime code needs marketplace install or catalog access to operate.

### Dependencies

- none

### Blocks

- Wave 1
- Wave 2
- Wave 3
- Wave 4

---

## 3) Wave 1: Action Capability Hardening

Status: complete

Goal:

- make resolved action contributions rich enough for marketplace action plugins without inventing a new runtime action engine

### Tasks

- Extend resolved action metadata to include:
  - grounding eligibility
  - side-effect level
  - result presentation hint
  - built-in module mapping optional
  - built-in card or UI block mapping optional
  - contribution provenance metadata
- Update action config loading to populate the richer metadata.
- Expose enriched action metadata through runtime diagnostics.
- Make shell-facing response shaping able to use the presentation hints.
- Ensure governance and confirmation flows still rely on runtime policy, not shell guesses.

### Acceptance Criteria

- Existing actions continue to work without requiring new fields.
- Runtime can render or expose enriched action posture and presentation hints from resolved metadata only.
- Verification can prove that enriched action metadata loaded correctly.

### Dependencies

- Wave 0

### Can Run In Parallel With

- early Wave 3 registry definition work

---

## 4) Wave 2: Knowledge Source And Retrieval Abstraction

Status: complete

Goal:

- make data plugins possible through one narrow runtime retrieval contract

### Tasks

- Introduce a `SearchSource` or equivalent retrieval abstraction.
- Define resolved knowledge-source adapter contracts for launch:
  - deployment-private vector
  - shared-index
  - provider-managed dataset
  - remote-search-api
- Implement source loading from `knowledgeSourceConfig`.
- Adapt deployment-private vector retrieval into the new abstraction.
- Implement one shared-index source adapter as the minimum launch slice.
- Add per-source eligibility checks:
  - auth mode
  - tenant/customer-safe handle presence
  - adapter support
- Update orchestrator retrieval flow to query eligible sources through the new abstraction.
- Add merged result attribution metadata to every evidence item.
- Add fail-closed behavior for unsupported source adapters or invalid resolved source config.

### Acceptance Criteria

- Runtime can query deployment-private and shared-index sources through the same contract.
- Evidence items can identify source type, source id, and attribution label.
- Unsupported or malformed knowledge-source entries fail at load time or verification time, not silently at answer time.

### Dependencies

- Wave 0

### Highest-Risk Items

- orchestrator source selection changes
- result merge and attribution behavior
- startup validation for partially invalid source configs

### This Wave Is The Main Marketplace Runtime Blocker

- without this wave, data plugins are not meaningfully supported

---

## 5) Wave 3: Shell Config And Trusted Extension Surfaces

Status: complete

Goal:

- make shell-aware marketplace composition possible without arbitrary publisher UI code

### Tasks

- Implement resolved `shellConfig` loading.
- Define fixed built-in module registry.
- Define fixed built-in card or UI block registry.
- Define evidence block or evidence card registry.
- Add typed runtime response block metadata where needed for richer rendering.
- Map resolved action metadata into shell-facing built-in presentation hints.
- Map resolved knowledge-source metadata into shell-facing evidence presentation hints.
- Extend shell/bootstrap contract to return resolved `shellConfig`.
- Ensure widget and embedded shell paths use the same resolved shell config contract.

### Acceptance Criteria

- Template plugins can affect greeting, starter prompts, and built-in module visibility through resolved config only.
- Action and data plugins can affect presentation only through fixed platform-owned registries.
- Shell does not load arbitrary code to support marketplace-contributed behavior.

### Dependencies

- Wave 0
- benefits from Wave 1 and Wave 2 metadata contracts

### Can Run In Parallel With

- later parts of Wave 2 after the contract shape stabilizes

---

## 6) Wave 4: Diagnostics, Verification, And Fail-Closed Compatibility

Status: complete

Goal:

- make resolved marketplace-related runtime behavior observable and verifiable

### Tasks

- Extend runtime admin overview to expose:
  - loaded knowledge sources
  - supported source adapter types
  - loaded shell config summary
  - supported shell module ids
  - supported card or UI block ids
  - loaded enriched action metadata summary
- Add verification-facing runtime endpoints or overview fields for:
  - knowledge-source count and ids
  - shell config presence and contract version
  - attribution readiness
  - capability support matrix
- Add startup validation for:
  - unsupported adapter types
  - invalid shell registry references
  - missing required shared handles
  - incompatible auth-mode requirements
- Add platform verification checks against expected resolved deployment state.

### Acceptance Criteria

- Platform can compare resolved deployment expectation vs runtime loaded state.
- Runtime fails closed for unsupported resolved artifacts.
- Support can determine whether a failure is action metadata, shell config, or knowledge-source loading.

### Dependencies

- Wave 0
- whichever of Waves 1-3 are in scope

---

## 7) Wave 5: Maturity And Performance Hardening

Status: complete

Goal:

- make the runtime/framework support operationally strong enough for broader plugin usage

### Tasks

- Add source-level latency diagnostics.
- Add source health diagnostics and degraded-mode indicators.
- Add caching and ranking guardrails for multi-source retrieval.
- Add graceful degradation behavior for disabled or unhealthy sources.
- Add contract-version compatibility tests for older deployments using newer runtime builds.
- Add broader test matrix for:
  - action-only resolved contributions
  - private plus shared knowledge-source mixes
  - shell-aware template defaults
  - auth-mode variations

### Acceptance Criteria

- Multi-source retrieval remains observable and debuggable.
- Runtime can degrade safely when one source is unavailable.
- New runtime capability additions do not break deployments that do not use marketplace-related resolved artifacts.

### Dependencies

- Waves 2-4

---

## 8) Recommended First Engineering Slice

If staffing is limited, the first concrete slice should be:

1. Wave 0 contract foundation
2. Wave 1 action capability enrichment
3. Wave 2 minimal knowledge-source support:
   - `knowledgeSourceConfig`
   - `SearchSource`
   - deployment-private vector adapter
   - shared-index adapter
   - evidence attribution
4. Wave 4 minimum verification support for the above

This is the smallest slice that makes marketplace materially more real than template bootstrapping.

---

## 9) Backlog By Priority

### P0

- Wave 0 complete
- Wave 2 minimum slice complete
- Wave 4 minimum verification complete

### P1

- Wave 1 complete
- Wave 3 initial shell registry and `shellConfig`

### P2

- Wave 5 hardening
- broader source adapter coverage
- richer shell registries

---

## 10) Suggested Delivery Staffing

### Track A: Runtime Contracts

- Wave 0
- contract models
- config loading
- startup validation base

### Track B: Retrieval And Knowledge Sources

- Wave 2
- retrieval abstraction
- source adapters
- attribution

### Track C: Shell Surfaces

- Wave 3
- shell config
- registries
- typed UI contracts

### Track D: Verification And Diagnostics

- Wave 4
- admin overview
- platform verification
- supportability

Track A should start first.
Track B should start as soon as Wave 0 contracts are stable.
Track C can begin once Wave 0 shapes are stable.
Track D should start as soon as there is anything loadable to verify.

---

## 11) Definition Of Done

This runtime/framework support effort is done when:

- action plugins work through enriched resolved action metadata
- data plugins can resolve into knowledge sources queried through one runtime abstraction
- evidence can attribute plugin-provided data clearly
- shell behavior can be influenced through resolved `shellConfig` and fixed registries only
- runtime admin and platform verification can prove what is actually loaded
- unsupported resolved artifacts fail closed
- runtime still has no marketplace catalog or install awareness
