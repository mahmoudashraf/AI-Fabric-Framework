# Advanced Deployment Create Flow Optional Plan

Status: planning document (2026-04-01)

This document describes an optional future enhancement for deployment creation.

It does **not** replace the template or preset model.

Its purpose is to add a second, more flexible creation path for operators who already understand the deployment stack they want to build.

---

## 1) Why This Is Optional

The current preset-driven create flow exists for good reasons:

- it guarantees a valid initial deployment draft
- it reduces invalid provider/vector combinations
- it gives the platform a safe way to seed runtime, connector, vector, prompt, and security defaults
- it keeps the first-run operator path simple

A fully raw configuration form at creation time would weaken those guarantees.

So the right product posture is:

- keep presets as the default path
- add `Advanced create` as an optional operator path

This should be a guided bootstrap flow, not a raw JSON editor.

---

## 2) Product Goal

The optional advanced create flow should let an operator start a deployment without choosing a branded preset first.

It should ask for a small, high-signal set of decisions:

1. LLM provider
2. embedding provider
3. vector backend
4. vector provisioning mode
5. curated module
6. environment
7. deployment name

The backend should then compile those choices into the same draft model the preset flow already uses.

This preserves:

- safe defaults
- backend-owned compatibility logic
- consistent readiness and verification behavior

---

## 3) Non-Goals

This flow should **not**:

- expose every provider field during creation
- duplicate the full `Providers` workspace
- allow invalid provider/vector/provisioning combinations
- bypass backend compatibility validation
- replace preset-based creation for normal users

The advanced create flow should end at:

- `Create deployment draft with a coherent baseline`

It should not try to solve all provider tuning up front.

---

## 4) Why Templates And Presets Still Matter

Even if advanced create exists, presets still provide clear value:

- they shorten onboarding for common stacks
- they encapsulate opinionated starting points
- they attach managed-provider behavior safely
- they make demos and standard platform paths fast to repeat

So the product model should remain:

- `Preset` = best-practice starting point
- `Advanced create` = operator-guided neutral bootstrap
- `Providers/Security/Prompts/Entities/...` = post-create refinement

---

## 5) Recommended UX

### 5.1 Entry point

In the create deployment page, offer:

- `Start from preset`
- `Advanced create`

`Start from preset` remains the default selected option.

### 5.2 Advanced create steps

Recommended wizard shape:

1. choose foundation
   - LLM provider
   - embedding provider
2. choose retrieval
   - vector backend
   - vector provisioning mode
3. choose behavior seed
   - curated module
4. choose identity
   - deployment name
   - environment
5. review generated baseline

### 5.3 Review screen

Before create, the platform should summarize:

- selected provider stack
- selected vector stack
- whether vector storage is local, bring-your-own, or platform-managed
- required secrets that will be needed later
- which settings will still need operator review in `Providers`

This keeps the flow explicit and enterprise-friendly.

---

## 6) Backend Design Principle

The backend should remain the source of truth for all defaulting and compatibility rules.

That means advanced create should reuse the same backend seeding path as presets:

- provider defaults
- vector defaults
- security defaults
- prompt baseline
- curated module baseline
- readiness assumptions

The UI should submit a compact request, not handcraft the whole draft.

Recommended approach:

- create an `AdvancedDeploymentBootstrapRequest`
- backend resolves it into the existing deployment draft model
- the result becomes a normal deployment draft and follows the normal lifecycle

This avoids two competing initialization systems.

---

## 7) Compatibility Rules

Advanced create must still respect platform compatibility rules.

Examples:

- `lucene` and `memory` only support `LOCAL_MANAGED`
- `qdrant` supports `EXTERNAL_EXISTING` and `PLATFORM_MANAGED`
- `pinecone` supports `EXTERNAL_EXISTING` and `PLATFORM_MANAGED`
- `weaviate` and `milvus` currently support `EXTERNAL_EXISTING`

The UI may guide these choices, but the backend must enforce them.

---

## 8) Security And Governance

The advanced flow should preserve the same security posture as preset creation.

It should:

- never collect secrets in the create form
- show which secrets will be needed in the `Secrets` workspace
- preserve role checks for create and post-create editing
- keep artifact generation, publish/apply, and verification unchanged

It should not create a lower-governance path just because it is “advanced.”

---

## 9) Recommended Implementation Sequence

1. add an explicit create mode selector
   - `PRESET`
   - `ADVANCED`
2. add backend bootstrap request model for advanced create
3. reuse existing defaulting and compatibility code
4. add review screen with readiness hints
5. add guided post-create next steps
6. only later consider exposing more knobs at create time

---

## 10) Why This Should Stay Secondary

This is valuable, but it is not the main product differentiator.

The platform wins more from:

- strong deployment operations
- managed vector infrastructure
- customer data onboarding
- safe runtime behavior

So this advanced create flow should remain:

- useful
- well-designed
- clearly optional

rather than becoming a major product branch that fragments the core experience.

---

## 11) Completion Criteria

This optional feature is complete when:

- operators can create a deployment without choosing a branded preset
- backend still guarantees a valid seeded draft
- no create-time secrets are required
- create-time compatibility is enforced in backend logic
- the create experience stays smaller than the full `Providers` workspace
- presets remain the default and recommended create path
