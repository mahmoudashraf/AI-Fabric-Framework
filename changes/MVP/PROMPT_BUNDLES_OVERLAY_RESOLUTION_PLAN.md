# Prompt Bundles as Modules + Overlay Resolution (Default + Curated + Provider) — Change Plan

## Status
Proposed

## Problem
Prompt text changes frequently. Maintaining prompts in two+ places (core + curated + provider variants) becomes:
- drift-prone (copies diverge),
- hard to review (noise),
- risky to ship (inconsistent rollouts),
- and painful for contributors.

We want:
- a **single “default” prompt source of truth**,
- optional curated/provider performance tuning,
- without duplicating entire prompt sets.

## Goal
Introduce a **prompt bundle module strategy** with **overlay resolution**:
- One module provides a complete, stable **default bundle**.
- Curated packs and provider-specific bundles provide **overrides only** (deltas).
- The framework resolves templates deterministically with a precedence order.

This aligns with:
- Greenfield: simplify; delete legacy prompt strings.
- Contracts over heuristics: no dynamic “latest”, no hidden fallback.
- Fail-closed: missing required templates must fail deterministically.

---

## High-level design

### 1) Keep call sites stable
All LLM call sites refer to templates using:
- `PromptTemplateKey(family, version, name)`

No prompt text lives in Java code after migration.

### 2) Bundle modules, not bundle copies
We do **not** copy full prompt trees into each curated/provider module.
Instead:
- default bundle = full set of templates
- overlays = only changed templates for that variant

### 3) Overlay resolution order (deterministic)
When loading a template for `(family, version, name)`, resolve in this order:
1) **App overrides** (templates shipped by the application itself)
2) **Curated pack overrides** (`ai-curated-*` dependency)
3) **Provider overrides** (e.g., `ai-curated-openai`, `ai-curated-anthropic`) — optional
4) **Default bundle** (always present)

This ensures:
- one “complete base”
- minimal deltas
- and deterministic override behavior

---

## Proposed packaging

### A) Default prompt bundle module (always complete)
Create a dedicated module (choose one naming convention):
- Option 1: `ai-prompts-default`
- Option 2: `ai-curated-default` (if you want all prompts to “live under curated”)

Responsibilities:
- ships the complete prompt set under `prompts/...`
- pins the “base” versions (e.g., `v1`)
- contains tests ensuring bundle completeness

### B) Curated domain packs (deltas)
Existing curated packs remain:
- `ai-curated-catalog`
- `ai-curated-commerce`
- `ai-curated-support`

They ship:
- `ai-curated/packs/<pack>.yml` (policy/mode + prompt version pinning)
- prompt overrides under `prompts/<family>/<version-pack>/<name>.md` **only when needed**

### C) Provider-tuned bundles (deltas)
Introduce optional provider bundles:
- `ai-curated-openai`
- `ai-curated-anthropic`
- `ai-curated-gemini`

They ship:
- provider-specific prompt overrides only (e.g., a tighter JSON schema instruction style)

Activation is explicit (never “auto-detect provider and swap prompts” unless configured):
```yaml
ai:
  prompts:
    provider-variant: openai
```

---

## Resolution mechanism

### 1) CompositePromptTemplateStore (new)
Add a `CompositePromptTemplateStore` that delegates to multiple stores in order:
- `AppClasspathPromptTemplateStore`
- `CuratedClasspathPromptTemplateStore`
- `ProviderClasspathPromptTemplateStore`
- `DefaultClasspathPromptTemplateStore`

Implementation detail options:
- single `ClasspathPromptTemplateStore` that can look up resources across the entire classpath (simplest), but you still want to enforce **bundle completeness** and **override visibility**.
- OR explicit “stores per bundle” for better diagnostics (recommended).

### 2) Explicit “bundle presence” gates
For production safety, do one of:
- enforce default bundle dependency is present at build time (Maven)
- enforce at startup:
  - required template keys can be loaded
  - otherwise fail startup with a clear error

### 3) Version selection
Keep versions pinned via config:
- `ai.prompts.<purpose>.version = v1` (base)
- curated pack sets e.g. `v1-commerce`
- provider variant sets e.g. `v1-openai`

Avoid exploding combinatorics by supporting:
- base version
- optional pack override version
- optional provider override version

Do **not** require a distinct “v1-commerce-openai” unless proven necessary.

---

## Suggested template families (initial inventory)

At minimum externalize:
1) Intent extraction (system + wrapper)
2) Progressive extraction ladders (completion / repair)
3) RAG answer + no-context answer
4) Post-action generation templates (if used)
5) Suggestions templates (if part of demo apps)

Each of the above becomes:
- `family`: stable namespace (e.g., `intent-extraction/system`)
- `name`: template name (e.g., `base`, `repair`, `completion`, `answer`, `no-context`)
- `version`: pinned (e.g., `v1`, `v1-commerce`, `v1-openai`)

---

## Developer experience rules

### 1) No copy/paste forks
Curated/provider modules must only add templates they actually change.

### 2) Template-level auditability
Every LLM call should record in metadata:
- `prompt.templateKey`
- `prompt.resolutionSource` (APP | CURATED | PROVIDER | DEFAULT)
- `prompt.contentHash` (sha256)

This makes regressions diagnosable.

---

## Testing strategy

### 1) Bundle completeness tests (default bundle)
- Assert all required keys exist for base `v1` across all families.

### 2) Curated pack tests
- Assert pack YAML pins the intended versions.
- Assert any overridden templates exist and render with required placeholders.

### 3) Provider bundle tests
- Same as curated tests, plus “JSON-only” compliance where applicable.

### 4) RealAPI gating
- Run a realapi provider matrix per bundle:
  - “default”
  - “commerce”
  - “support”
  - “openai provider override” (if enabled)

Fail the build if success rate dips below threshold.

---

## Rollout sequence (greenfield)
1) Add the default bundle module and move all currently-externalized templates into it.
2) Introduce composite resolution + metadata for resolution source.
3) Migrate remaining hardcoded prompts (EnrichedPromptBuilder, IntentQueryExtractor wrapper, RAG prompts, completion/repair).
4) Add curated/provider bundles only where they truly improve realapi outcomes.
5) Delete remaining hardcoded prompt strings.

---

## Trade-offs

### Pros
- One “real” source of truth (default bundle), overlays are small.
- Curated/provider tuning stays possible without forking.
- Deterministic behavior; easy to debug.

### Cons / complexity to manage
- Need to define and maintain a list of “required template keys”.
- Need clear rules on how versions compose (base vs pack vs provider).

This complexity is worth it because it prevents a much worse long-term outcome:
prompt sprawl and unreviewable drift.

