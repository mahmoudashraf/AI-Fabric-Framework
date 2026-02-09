# Mode Capability Bundles — Phased Roadmap (v1 → Pro)

## Purpose
Provide a **realistic, phased** implementation sequence for mode/profile optimizations, with **explicit status** and a **complete list of missing/pending items** to review before more code changes.

**Non‑negotiable invariants (per dev guides)**
- **Navigator stays as‑is** (no “surprise” behavior changes).
- Modes are **server‑enforced** (fail‑closed) via a resolved `OrchestrationPolicy`.
- Curated packs ship **config + prompts only** (no hidden runtime logic).
- LLM prompting is **minimal + deterministic**; pipeline remains the authority.

---

## Current Status Snapshot (as of this branch)
### Phase 0 — Completed (foundation)
Implemented in code:
- Per‑request policy resolution: `OrchestrationPolicy` + `OrchestrationPolicyResolutionStep`.
- Capability bundle flags:
  - `actionsEnabled`, `retrievalEnabled`, `deepRetrievalEnabled`, `suggestionsEnabled`.
- Prompt section gating:
  - Hide action catalog when `actionsEnabled=false`.
  - Hide KB overview when `retrievalEnabled=false`.
  - Short “mode constraints” addon (only when non‑default).
- Pipeline enforcement (fail‑closed):
  - If actions disabled → ACTION intent → `CLARIFICATION_REQUIRED` (+ `suggestedMode=executor`).
  - If retrieval disabled → retrieval intent → `CLARIFICATION_REQUIRED` (+ `suggestedMode=navigator`).
  - If retrieval allowlist configured and requested spaces violate it → `CLARIFICATION_REQUIRED`.
- Curated pack (commerce):
  - `navigator` unchanged
  - `navigator_deep` (actions disabled, deep retrieval enabled, budgets configured)
  - `executor` (suggestions off, optional restricted retrieval allowlist = `policy`)
  - position routing defaults: `landing/catalog/search → navigator`, `cart → cart_assistant`
- Offline‑safe tests:
  - Provider startup probes gated by `validateOnStartup=false`
  - test default embeddings uses ONNX
- ONNX classpath model load fix (avoid temp-file extraction).

### Phase 1 — Completed (`navigator_deep` retrieval semantics)
Implemented in code:
- `rag.fanout-enabled` is enforced at runtime:
  - when `false`, multi-space fanout is not executed (single-space only).
  - when `true`, multi-space fanout is allowed and bounded by budgets.
- `deepRetrievalEnabled` now broadens retrieval when routing is missing/weak:
  - deterministic space selection uses KB doc-count ordering, bounded by `rag.max-spaces`.
- `rag.max-spaces` now caps deterministic fallback vector spaces (instead of router defaults).
- Added deep-mode retrieval observability in debug metadata (strategy, selected spaces, source, effective budgets).

Docs present:
- `changes/MVP/MODE_CAPABILITY_BUNDLES_MINIMAL_LLM_CONTEXT_PLAN.md`
- `Final_Documentation/Development_Guides/CURATED_MODES_PACKS_GUIDE.md` (commerce pack)

---

## Phase Roadmap (4 phases)

### Phase 0 — Policy Surface + Curated Defaults (FOUNDATION) ✅ DONE
**Goal:** establish a stable “policy surface” so later work is purely policy‑driven and testable.

**Delivered**
- See “Current Status Snapshot”.

**Open follow‑ups (review-only, not required for Phase 1)**
- Curated pack selection semantics (v1):
  - We support **exactly one active pack** (`ai.curated.pack=<name>`), with **no inheritance/merging**.
  - We will add a minimal `default.yml` pack that supports **only `navigator`** (no position routing; all positions resolve to `navigator`).
  - If the active pack is `default`, **all other modes are unsupported** and must fail‑closed (`CLARIFICATION_REQUIRED`) with `data.supportedModes=["navigator"]`.
  - If the app selects `commerce`, the `default` pack is **ignored** (no parent layering).

---

### Phase 1 — `navigator_deep` Retrieval Semantics (Fanout + Multi-space Strategy) ✅ DONE
**Goal:** make `navigator_deep` actually behave “deep” even when the LLM output is imperfect, while keeping `navigator` unchanged.

#### Delivered
1) **Wire `rag.fanout-enabled`**
- If `fanoutEnabled=false`:
  - Never call the fanout path.
  - Prefer a single best vectorSpace (LLM‑selected when valid; else deterministic single‑space selection).
- If `fanoutEnabled=true`:
  - Allow multi‑space fanout, bounded by budgets.

2) **Use `deepRetrievalEnabled` to broaden search strategy**
- In deep mode, when routing is missing/weak, select top-N spaces by KB doc count (deterministic),
  where `N = rag.max-spaces`, and enforce fanout-enabled.

3) **Budget-driven deterministic fallback**
- Deterministic fallback vectorSpace selection is capped by `rag.max-spaces` (when set).

4) **Deep mode observability**
- Debug metadata includes:
  - `retrievalStrategy`: `SINGLE_SPACE` | `FAN_OUT`
  - `vectorSpacesSelected`, `vectorSpacesSelectionSource`
  - effective budgets (`fanoutEnabledEffective`, `ragMaxSpacesEffective`, etc.)

#### Validation (Phase 1)
Manual:
- With `mode=navigator_deep` and no `vectorSpace` in intent output:
  - Observe multi‑space retrieval happens (up to `rag.maxSpaces`) when `fanoutEnabled=true`.
- With `fanoutEnabled=false`:
  - Observe single-space retrieval only.

Tests:
- Unit tests cover:
  - “deepRetrievalEnabled expands spaces”
  - “fanoutEnabled disables fanout”
  - “rag.maxSpaces overrides deterministic fallback cap”

---

### Phase 2 — `executor` Mode (Action-first, Retrieval Restricted & Minimal Prompting) ⏳ PENDING
**Goal:** a reliable action-oriented mode that does not confuse the LLM with RAG browsing.

#### What’s missing / needs review
- We already enforce capabilities + allowlist at runtime.
- What’s not yet “tight” is the *experience contract*:
  - prompt tuning for action-first extraction
  - clear developer-facing config guide
  - optional restricted retrieval for policies without leaking KB dump

#### Deliverables (Phase 2)
1) **Executor prompt “minimalism”**
- In `executor`, system prompt should:
  - emphasize action selection + parameter filling
  - either exclude KB overview entirely, OR
  - include only allowlisted spaces (policy) and minimal retrieval instructions.

2) **Restricted retrieval enforcement**
- Keep `retrievalVectorSpacesAllowlist` as the only supported retrieval in executor.
- If allowlist present:
  - enforce it strictly
  - apply tight budgets (UI docs + LLM context)

3) **Clear “mode errors”**
When a request is incompatible with executor:
- Return `CLARIFICATION_REQUIRED` with:
  - `suggestedMode=navigator` (for broad search)
  - a short reason code.

#### Validation (Phase 2)
Manual:
- In executor, ask “search for laptops under $1000” → should ask to switch to navigator (no RAG browse).
- In executor, ask “what is the refund policy?” → if allowlist includes `policy`, retrieval can run.

Tests:
- executor + retrieval disabled → retrieval intent → `CLARIFICATION_REQUIRED`.
- executor + allowlist configured → denied space → `CLARIFICATION_REQUIRED`.

---

### Phase 3 — “Working Set / Pinned Targets / Action Result Grounding” (Reliability) ⏳ PENDING
**Goal:** make follow-ups reliable (“cancel it”, “compare them”) without bloating prompts or relying on fragile heuristics.

#### Deliverables (Phase 3)
1) **Working set window semantics**
- Define and enforce:
  - what is stored (ids vs content)
  - TTL / window size
  - which modes enable it.

2) **Pinned targets precedence rules**
- Active UI attachments (authoritative) vs stored pinned targets (soft) vs RAG docs (working set only).

3) **Action results as grounded context**
- Write actions can contribute to pinned/working-set context in a bounded way.
- Read-only “probe → RAG fallback” (only where enabled) to avoid empty action loops.

#### Validation (Phase 3)
- Real-app flows:
  - “create order” then “cancel it” (reliable)
  - compare multiple attachments without RAG pulling unrelated items

---

### Phase 4 — Pro/Enterprise Prompt Management + Experiments ⏳ DEFERRED
**Goal:** monetize optional management tooling, not hidden logic.

Deliverables (examples):
- Prompt template externalization + overlay resolution tooling
- A/B prompt experiments + governance
- “curated policies” shipped transparently as bundles

---

## Implementation Order Recommendation
1) Phase 1 (navigator_deep semantics) — unlocks deep search reliability without touching navigator.
2) Phase 2 (executor tightening) — unlocks support/action scenarios.
3) Phase 3 (working set + grounding) — improves follow-ups.
4) Phase 4 (enterprise) — only after v1 is stable.

---

## Review Checklist (before Phase 1 coding)
- Confirm desired deep strategy when LLM omits vectorSpaces:
  - top‑N by KB doc count (deterministic), vs “always multi-space”, vs allowlist only.
- Confirm whether `fanoutEnabled` should default to:
  - enabled only in `navigator_deep`, disabled elsewhere.
- Confirm if any spaces must be excluded from deep mode (security/performance).
