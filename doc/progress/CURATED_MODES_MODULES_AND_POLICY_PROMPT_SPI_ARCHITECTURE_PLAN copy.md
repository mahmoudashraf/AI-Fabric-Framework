# Curated Modes via Modules (No Custom Intent Extraction) — Implementation Plan

## Status
Proposed

## Decision (agreed)
We will **not** monetize or extend the framework by swapping intent extraction engines.

Instead we will implement a clean, safe architecture where:

1) **Core (OSS)** provides:
   - a stable orchestration pipeline (single source of truth),
   - a server-authoritative `OrchestrationPolicy` (profile/mode/position aware),
   - a prompt template SPI (externalized prompts, versioned, provider-variant capable),
   - deterministic validation and fail-closed behavior.

2) **Curated modules (OSS or Pro)** provide:
   - policy presets (“modes”) and routing defaults,
   - prompt bundles (templated, versioned),
   - recommended thresholds/limits (bounded, test-backed),
   - fully transparent configs and resources.

3) **Enterprise/paid (optional)** provides:
   - management + governance + experiments (DB store, UI, approvals, audit, A/B),
   - but does **not** change core orchestration semantics in opaque ways.

This keeps the ecosystem reproducible and debuggable, aligns with the greenfield philosophy, and avoids the “secret better brain” anti-pattern.

---

## Why this is the best approach (framework + OSS trust)
### Avoids the most dangerous failure mode
If “pro intent extraction” exists, bug reports become:
> “We can’t reproduce unless you run the pro module.”

This fractures community support and kills adoption.

### Keeps safety centralized
The orchestration pipeline owns:
- action allowlisting + access control
- confirmation rules
- PII and sanitization controls
- retrieval scoping and drift prevention

These must not be bypassable by pluggable “brains”.

### Monetizes operations, not intelligence
Enterprises pay for:
- governance, auditability, controlled rollouts, A/B, RBAC
not for “secret logic”.

---

## Architecture overview

### Core layers
1) **Policy resolution** (server-authoritative):
   - profile defaults + mode overrides + position routing
2) **Prompt selection/rendering** (SPI):
   - template id resolution based on policy
   - strict placeholder validation + bounded output
3) **Stable pipeline execution**:
   - steps read the effective policy
   - steps remain deterministic and testable

### Curated modules
Curated modules do NOT inject code mid-pipeline. They only contribute:
- YAML config defaults for modes/policies (or Spring beans implementing `PolicyPresetProvider`)
- prompt templates under well-known resource paths
- optional: README/docs and realapi test profiles

---

## Core implementation (OSS)

### 1) OrchestrationPolicy as the single configuration contract
Implement (or finalize) an immutable policy object used by steps:
- `informationMode`: `LLM_DRIVEN | DETERMINISTIC_RAG_GENERATE`
- `promptMode`: `FULL_CONTRACT | MINIMAL_FOR_RAG`
- `attachments.enabled`
- `attachments.constrainVectorSpaces`
- `workingSet.enabled`
- `history.windowSize`, `history.maxChars`
- `rag.threshold`, `rag.fanOutMaxSpaces`, `rag.topKPerSpace`
- `actions.allowed` (allowlist by name/category/accessMode)
- `actions.confirmationPolicy` (confirm write, confirm read, etc.)

Key rule: **steps may not directly read scattered properties**. They read policy.

### 2) Mode/profile/position routing (server-authoritative)
Add the policy resolver step (early in pipeline):
- Accept request signals:
  - `position` (preferred)
  - `mode` (optional; allowlisted)
- Map `position → mode` from server config
- Resolve `effectivePolicy = merge(profileDefaults, modeOverrides)`

Fail-closed options:
- unknown position in strict mode → `CLARIFICATION_REQUIRED` or `ERROR`
- unknown mode → ignored or rejected (configurable)

### 3) Prompt template SPI (externalized prompts)
Follow `changes/PROMPT_TEMPLATES_EXTERNALIZATION_CHANGE_PLAN.md`, but generalize:

#### SPI
- `PromptTemplateStore`:
  - `PromptTemplate load(PromptTemplateId id)`
- `PromptRenderer`:
  - `RenderedPrompt render(template, placeholders)`
  - validates placeholders, bounds size, supports provider variants

#### Default store (OSS)
- `ClasspathPromptTemplateStore`
- Resource path conventions (example):
  - `prompts/intent-extraction/compound/v1/system.md`
  - `prompts/intent-extraction/multi-step/v1/classify.md`
  - provider variants:
    - `.../v1-openai/...`

#### Prompt selection
Policy determines prompt mode and (optionally) prompt “family”:
- `FULL_CONTRACT` vs `MINIMAL_FOR_RAG`
- plus per-mode specializations if needed later (but avoid explosion)

### 4) Validation is non-negotiable
Regardless of which curated templates are loaded:
- The same `IntentExtractionValidator` + post-processor runs.
- Unsafe/invalid outputs are repaired or fail-closed using the bounded ladder.

Curated modules can influence *inputs* to the LLM (prompts) and *policy* choices, but cannot bypass the validator.

### 5) Observability (critical for OSS support)
Attach deterministic metadata (debug only):
- `policy.profile`, `policy.mode`, `policy.position`
- `policy.informationModeEffective`, `policy.promptModeEffective`
- `prompt.templateId`, `prompt.version`, `prompt.variant`

RealAPI reports should include these so issues are reproducible.

---

## Curated Modules (OSS or Pro)

### What curated modules provide (allowed)
1) **Mode presets**
   - YAML snippets (recommended) or a small Spring `@ConfigurationProperties` contribution
2) **Prompt bundles**
   - resources in classpath under the same conventions
3) **Docs and examples**
   - “How to enable cart assistant mode”
4) **Test profiles**
   - pinned prompt versions and policy defaults for repeatability

### What curated modules must NOT do
- Replace the pipeline.
- Replace intent extraction engine with arbitrary logic.
- Disable or bypass validation, confirmation, access control, or sanitization.

### Suggested curated packs
- `ai-curated-catalog`:
  - navigator mode + deterministic info answering
- `ai-curated-commerce`:
  - cart assistant + orders/returns support modes
- `ai-curated-support`:
  - issue resolver mode + safer confirmation defaults + richer history

Each pack ships:
- `modes.*` YAML defaults
- prompt templates variants tuned for that domain/position

---

## Enterprise / Paid Add-ons (optional, recommended)
Enterprise should monetize **management**, not secret logic:

### Prompt management add-on
- DB-backed `PromptTemplateStore`
- approvals + audit trail
- per-tenant/per-app routing
- rollback + pinning

### Policy management add-on
- DB-backed `PolicyStore`
- routing rules (`position→mode`) per tenant/app
- RBAC + SSO integration

### Experimentation add-on (optional)
- A/B testing: prompt versions and policy variants
- guardrails: budget limits, auto rollback

Importantly: enterprise modules still feed into the same core policy + prompt SPI, so behavior remains inspectable and reproducible.

---

## Implementation roadmap (phased, minimal risk)

### Phase 1 — Core: policy becomes single source of truth
1) Finalize `OrchestrationPolicy` object.
2) Add `PolicyResolutionStep` and wire effective policy into `PipelineContext`.
3) Update key steps (prompt builder, vector-space resolution, intent handling) to consume policy.
4) Add debug metadata to results/snapshots.

### Phase 2 — Core: prompt externalization + store SPI
1) Implement prompt store + renderer.
2) Move existing hardcoded templates to resources (`v1`).
3) Add tests for template loading + placeholder validation.

### Phase 3 — Curated module: first pack (`ai-curated-catalog`)
1) Ship `navigator` mode preset:
   - `informationMode=DETERMINISTIC_RAG_GENERATE`
   - `promptMode=MINIMAL_FOR_RAG`
2) Ship prompt templates tuned for the mode (still JSON-only, same schema).
3) Add a realapi test profile demonstrating reproducible behavior.

### Phase 4 — Curated module: commerce/support packs
1) Add cart assistant and support resolver modes.
2) Define action allowlists and confirmation defaults.
3) Add realapi tests for position routing and action eligibility.

### Phase 5 — Enterprise add-ons (if desired)
1) DB stores for prompts/policies.
2) Admin APIs + audit.
3) UI + experiments.

---

## Testing and acceptance criteria

### Must-have tests (core)
- Policy precedence: profile < mode < position routing.
- Prompt selection: effective `promptMode` selects correct template version.
- Validator enforcement: curated prompts cannot bypass required parameter checks.
- Observability: effective policy and prompt IDs appear in debug metadata.

### Must-have tests (curated packs)
- Catalog pack produces deterministic info answers with pinned versions.
- Commerce pack restricts AVAILABLE ACTIONS to allowlisted categories in cart mode.
- Support pack enables safe read/write flows with correct confirmation defaults.

### Acceptance criteria
1) Users can enable a curated mode with **one config key** (or a position signal).
2) Behavior remains reproducible without hidden code paths.
3) Enterprise features add manageability, not secret semantics.

