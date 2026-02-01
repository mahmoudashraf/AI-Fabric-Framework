# Optimization Profiles + Deterministic RAG Integration — Implementation Plan

## Status
Proposed

## Why this plan exists
We already have (or are proposing) multiple “optimizations” that materially change orchestration behavior:
- `DETERMINISTIC_RAG_GENERATE` (forces retrieval + generation for INFORMATION).
- `MINIMAL_FOR_RAG` intent-extraction prompt mode (LLM decides fewer fields).
- Attachments as authoritative context (UI-selected items / metadata).
- Target resolution (“it/both/this”) and a retrieval working set (reduce drift).

As an open-source framework, we need to expose these in a way that is:
- **Easy to enable** (works out of the box for common app types).
- **Hard to misconfigure** (clear precedence rules, no combinatorial explosion).
- **Transparent** (users can see what is effective at runtime).
- **Greenfield** (no legacy compatibility burden).

This plan defines a “profile + overrides” model: a few **coherent presets** for common app types, plus explicit flags for advanced users.

Related change plans:
- `changes/architecture/ATTACHMENTS_METADATA_AND_RAG_OPTIMIZATION_MASTER_PLAN.md`
- `Final_Documentation/System_Archtecture_Guides/PLAN_DETERMINISTIC_RAG_ALWAYS_GENERATE.md`
- `changes/INTENT_METADATA_RAG_QUERY_AUGMENTATION_CHANGE_PLAN.md`

---

## Core insight (from trials)
`DETERMINISTIC_RAG_GENERATE` is a strong correctness hammer for INFORMATION flows, but by itself it:
- increases cost/latency (always generates),
- can conflict with “short social replies” (“thanks”) unless the LLM-driven directAnswer path remains available in some profiles,
- can amplify drift if `vectorSpace` is missing (fan-out across all spaces) unless **attachments constrain the search space**.

Therefore: deterministic mode is best exposed as part of a **bundle** (profile) that also enables:
- `MINIMAL_FOR_RAG`,
- attachment-scoped retrieval,
- target resolution and working-set reuse.

---

## Goals
1) Provide 2–3 clear orchestration presets that cover most apps:
   - “Navigator/catalog” (deterministic, reliable)
   - “Chat assistant” (LLM-driven retrieval gating + directAnswer)
   - “Default” (framework baseline)
2) Keep the number of top-level feature flags small.
3) Define deterministic precedence rules (no “mystery behavior”).
4) Make effective configuration observable at startup and per request (debug metadata).

## Non-goals
- Supporting backward compatibility for old property keys or legacy prompt contracts.
- Adding dozens of micro-flags for every sub-behavior.

---

## Proposed configuration model

### 1) Add an optimization profile (preset)
Add a single preset selector:

```yaml
ai:
  orchestration:
    profile: DEFAULT | PRODUCTION_NAVIGATOR | PRODUCTION_CHAT
```

**Meaning**
- `DEFAULT`: minimal orchestration guarantees; closest to current behavior.
- `PRODUCTION_NAVIGATOR`: deterministic info answering for navigator/catalog use cases (reduces OUT_OF_SCOPE/ERROR sensitivity).
- `PRODUCTION_CHAT`: optimized for conversational assistants (allows “no retrieval” short replies).

### 2) Keep a small set of explicit flags (expert overrides)
These remain available for advanced users:

```yaml
ai:
  orchestration:
    information-mode: LLM_DRIVEN | DETERMINISTIC_RAG_GENERATE
  intent-extraction:
    prompt-mode: FULL_CONTRACT | MINIMAL_FOR_RAG
```

Add two “bundle-level” toggles (not micro-flags):

```yaml
ai:
  orchestration:
    attachments:
      enabled: true
      constrain-vector-spaces: true
    working-set:
      enabled: true
```

Rationale:
- Attachments + working set are the key drift reducers.
- We avoid per-subfeature flags (e.g., “append metadata tokens” vs “pinned facts”) unless a real need arises.

---

## Precedence rules (must be explicit)

### Rule A — Profile provides defaults
Profile supplies default values for all optimization-relevant knobs.

### Rule B — Explicit flags override profile defaults
If the user sets `information-mode` or `prompt-mode`, those win over the profile.

### Rule C — Deterministic mode vs directAnswer
- In `DETERMINISTIC_RAG_GENERATE`, INFORMATION **always** retrieves and generates.
- In `LLM_DRIVEN`, INFORMATION may be directAnswer (no retrieval) when the extractor sets `requiresRetrieval=false`.

Implication: to support “thanks → short reply without retrieval”, do **not** use deterministic mode in that profile.

### Rule D — Attachments constrain retrieval regardless of information mode
If attachments are present and `constrain-vector-spaces=true`, vector spaces are constrained to attachment vector spaces.
This reduces fan-out cost and drift in both deterministic and LLM-driven modes.

### Rule E — Multi-intent safety
Any “global hint” from `intentResponse.metadata` is only applied when:
- there is exactly one retrieval intent; otherwise ignore (avoid cross-intent contamination).

---

## Profile definitions (recommended defaults)

### Profile: `DEFAULT`
- `information-mode = LLM_DRIVEN`
- `prompt-mode = FULL_CONTRACT`
- `attachments.enabled = false` (or true-but-no-op, depending on API readiness)
- `working-set.enabled = false`

### Profile: `PRODUCTION_NAVIGATOR` (recommended for catalog/navigator apps)
- `information-mode = DETERMINISTIC_RAG_GENERATE`
- `prompt-mode = MINIMAL_FOR_RAG`
- `attachments.enabled = true`
- `attachments.constrain-vector-spaces = true`
- `working-set.enabled = true`

This profile aims for “always answer from the catalog if possible”, with minimal reliance on the LLM setting flags correctly.

### Profile: `PRODUCTION_CHAT`
- `information-mode = LLM_DRIVEN`
- `prompt-mode = FULL_CONTRACT` (or `MINIMAL_FOR_RAG` only if the app is retrieval-first)
- `attachments.enabled = true`
- `attachments.constrain-vector-spaces = true`
- `working-set.enabled = true`

This profile preserves the directAnswer path for social/acknowledgement messages while still reducing drift for real tasks.

---

## Implementation plan (phased, but coherent)

### Phase 0 — Instrumentation (cheap, high value)
- Add an “effective configuration snapshot” logged once at startup:
  - selected profile
  - effective information-mode
  - effective prompt-mode
  - attachments/working-set toggles
  - (optional) a warning if the combination is unusual (e.g., deterministic + full contract)

### Phase 1 — Config plumbing (profile + effective resolution)
1) Introduce `OrchestrationOptimizationProfile` enum.
2) Add `ai.orchestration.profile` property.
3) Make `information-mode` and `prompt-mode` **nullable** at the property level so we can detect overrides.
4) Add a small resolver (single source of truth):
   - `EffectiveOrchestrationPolicy resolve(profile, properties...)`
5) Update code paths that currently read properties directly to use the effective policy:
   - `IntentHandlingStep` (deterministic check)
   - `VectorSpaceResolutionStep` (deterministic fan-out behavior)
   - `EnrichedPromptBuilder` (prompt mode)

### Phase 2 — Attachments + targeting integration (ties deterministic mode to drift reduction)
Implement items from:
- `changes/architecture/ATTACHMENTS_METADATA_AND_RAG_OPTIMIZATION_MASTER_PLAN.md`

Specifically required for deterministic mode success:
- Attachments section in prompt (authoritative)
- Constrain vector spaces by attachments when present
- Target resolution (“it/both”) via attachments/working set

### Phase 3 — Working-set memory and reuse (follow-up stability)
- Persist retrieval working set refs in chat turn metadata.
- Prefer working-set reuse for follow-ups without active attachments.

### Phase 4 — (Optional) Query hint augmentation
If needed after Phase 2/3:
- Implement the explicit intent metadata key (`metadata.retrievalQueryHint`) as in:
  - `changes/INTENT_METADATA_RAG_QUERY_AUGMENTATION_CHANGE_PLAN.md`
- Also support deterministic attachment-derived query tokens (id/sku refs).

---

## Testing strategy (must prevent configuration regressions)

### 1) Unit tests
- Profile resolution:
  - profile defaults applied when overrides are null
  - explicit overrides win
- Deterministic mode behavior:
  - INFORMATION always retrieval+generation
- LLM-driven mode:
  - directAnswer path works when requiresRetrieval=false

### 2) Integration tests (realapi where relevant)
- `PRODUCTION_NAVIGATOR` profile:
  - “thanks” is allowed to answer but may still retrieve (documented trade-off) OR ensure extractor classifies it as non-INFORMATION (if supported).
  - catalog queries return INFORMATION_PROVIDED with generated answer.
- `PRODUCTION_CHAT` profile:
  - “thanks” returns directAnswer without calling RAG (requiresRetrieval=false).
  - follow-ups remain grounded via attachments/working set.

### 3) Observability assertions
- Add debug metadata fields that tests can assert without parsing logs:
  - `orchestration.profile`
  - `orchestration.informationModeEffective`
  - `orchestration.promptModeEffective`
  - `rag.vectorSpacesConstrainedByAttachments=true/false`

---

## Documentation deliverables (OSS enablement)
- Update `Final_Documentation/User_Guides/CONFIGURATION_AND_OPTIMIZATION_GUIDE.md` with:
  - “Pick a profile” quickstart
  - “Override flags” advanced section
  - “Trade-offs” (cost/latency vs conversational UX)
- Add a small “profile matrix” table:
  - DEFAULT vs PRODUCTION_NAVIGATOR vs PRODUCTION_CHAT
  - recommended for which app types

---

## Acceptance criteria
1) Framework users can enable a coherent optimization bundle with **one** config key (`ai.orchestration.profile`).
2) Advanced users can override information/prompt modes intentionally (no silent surprises).
3) Deterministic mode is no longer “dangerous” because attachments constrain retrieval domains.
4) Configuration combinations are observable and testable.
