# Mode/Profile + Curated Modules — Optimisations Review (from `changes/Optimisations.txt`)

## Purpose
Review each idea in `changes/Optimisations.txt`, critique it against the AI Fabric philosophy, and recommend what to implement (and how) with **modes/profiles + curated modules**.

## Evaluation criteria (framework philosophy)
- **Deterministic > heuristic**: avoid string matching (“if query contains compare…”).
- **Authoritative-first**: active attachments and pinned targets must be the primary grounding source.
- **Bounded context**: caps on tokens/bytes; no uncontrolled “dump everything”.
- **Mode-driven behavior**: modes should be explicit/allowlisted and configure behavior coherently.
- **Security-aware**: client-provided metadata is untrusted; sanitize + cap; never let UI fields steer execution invisibly.
- **Greenfield**: no backwards compatibility required; prefer clean contracts.

## Quick summary: what to implement vs defer
**Implement (near-term)**
1. “Deep mode uses attachments” → **yes**, but via **structured scope/hints**, not by concatenating attachment text into the retrieval query.
2. “Store UI custom metadata” → **yes**, but UI-only storage (never injected into LLM/tool execution).
3. Mode step/action prioritization → **yes** (extend `OrchestrationPolicy` to drive behavior, configured via curated packs).
4. Use pinned targets/attachments in extraction → **yes** (ensure extractor sees authoritative pinned context even on follow-ups without new attachments).
5. Persist doc ids for re-open → **yes** (store bounded refs + best-effort rehydrate).
6. Suggestions per mode/position → **yes** (curated prompt overlays per mode).
7. Decide generation when action result exists → **yes** (policy + intent contract; avoid extra LLM calls).
8. Read-only empty action result fallback → **yes**, but **mode-gated** and only for actions explicitly marked “read-only”; treat as “read probe → then RAG”, not “replace/hide”.
9. Cross-vector-space relation (e.g., reviews ↔ product) → **yes (v0)** via identifier-based scoping/hints; defer v1 join schema.
10. Working-set accumulation strategy → **yes** (configurable bounded “working set window”).
11. Empty action result should not override pinned targets → **yes** (already required; keep).
12. Action results as context → **yes** (structured + bounded, not raw dumps).

**Defer (needs a bigger design)**
9. Cross-vector-space “relation” (reviews ↔ product) **v1** → explicit link schema + backend-native filtering for precise joins.
14. Structured “referencedTargetIds” from LLM generation → useful for UI, but requires a **generation output contract** (JSON) and robust parsing.

**Reject / keep simple**
13. “Yes/No with no pending action” → do **not** call extra LLM/RAG; return a simple deterministic reply (no side effects).

## Detailed review (item-by-item)

### 1) “In deep mode, attachment should somehow be part of rag search query”
**Critique**
- Putting full attachment text into the retrieval query tends to **pollute embeddings**, reduce recall, and can leak prompt-injection content into retrieval.
- However, deep search should be able to **use pinned targets to scope retrieval** (e.g., “find more about *this item*”).

**Recommendation**
- Implement as **structured scoping + hinting**, not raw concatenation:
  - Scope `vectorSpace` to active attachments’ vector spaces when present.
  - Add a compact `retrievalQueryHint` derived from authoritative attachment metadata (e.g., `id`, `sku`) **only if explicitly present**.
  - Keep retrieval query itself as the user query / optimized query.

**Where**
- Core: retrieval planning in `IntentHandlingStep` and `VectorSpaceResolutionStep`.
- Curated: deep mode declared in pack YAML (`navigator_deep`) and used to enable Advanced RAG.

**Decision**: **Implement (modified)**.

---

### 2) “Store UI custom metadata for each message (layout appearance)”
**Critique**
- This is a **client UX concern**, not an orchestration concern.
- The risk is accidentally letting UI hints influence LLM/tool execution.

**Recommendation**
- Add a **bounded `ui` metadata blob** (or `clientContext`) stored with each chat turn:
  - persisted for UI rendering only
  - **not injected** into LLM prompts
  - sanitized and size-limited (no nested objects; scalar-only; allowlist keys optional)

**Where**
- Chat-session module: turn metadata storage schema + API DTOs.

**Decision**: **Implement (as UI-only metadata, not LLM context)**.

---

### 3) “Resolver mode: prioritize steps/actions per mode”
**Critique**
- Makes sense: different UX positions (support vs checkout) need different orchestration priorities.
- Danger: “mode = hidden behavior change” unless clearly surfaced and deterministic.
- Also: “prioritize steps” should not mean ad-hoc runtime reordering. Prefer a **stable pipeline** where the policy affects **decisions inside steps**, and curated packs may enable/disable optional steps.

**Recommendation**
- Extend mode/policy config to cover:
  - **action-first vs rag-first** preference
  - read-only action “probe” before RAG (if enabled)
  - whether empty action results fall back to RAG
  - whether generation is expected after action results

**Where**
- Core: `OrchestrationPolicy` + policy resolution step.
- Curated packs: YAML defines the coherent bundle.

**Decision**: **Implement**.

---

### 4) “Send resolved attachments into intent extraction; clarify difference between sending attachments vs speaking on them next call”
**Critique**
- This is a core root-cause of “LLM keeps missing context”.
- The LLM cannot decide `requiresRetrieval` / “enough info?” without seeing pinned context.

**Recommendation**
- Ensure the extractor sees authoritative pinned context **every turn**:
  - Active attachments (authoritative)
  - Stored pinned targets (short window; authoritative)
  - Mark them clearly in the prompt (“AUTHORITATIVE PINNED CONTEXT”).
- Clarify for users/developers:
  - **Sending attachments** = explicit UI grounding for this request
  - **Follow-up without attachments** = reuse pinned targets window (best-effort) to resolve “it/them”

**Where**
- Core pipeline: prompt augmentation before intent extraction.
- Chat-session: storing/reusing pinned targets.

**Decision**: **Implement** (and document).

---

### 5) “Store document IDs in history to be fetched again on open chat”
**Critique**
- Good for re-open and cross-device continuity.
- Must stay bounded and tolerate missing/stale ids.

**Recommendation**
- Persist a bounded list of **refs** (id + vectorSpace) and rehydrate on demand:
  - store up to `N` refs per turn or per session window
  - best-effort fetch by `(vectorSpace,id)` when loading a conversation

**Where**
- Chat-session persistence + optional vector rehydration service.

**Decision**: **Implement**.

---

### 6) “Suggestions should be divided by mode/position”
**Critique**
- Makes sense: navigator suggestions are queries/refinements; cart assistant suggestions are next actions/required params.

**Recommendation**
- Mode-based suggestion templates:
  - curated prompt overlays per mode
  - mode-driven limits (count, verbosity)

**Where**
- Curated prompt bundles.
- Smart suggestions step: select template by mode/policy.

**Decision**: **Implement**.

---

### 7) “Check if user query requires generation if it comes from action result”
**Critique**
- “Read action returns data” sometimes needs no generation (return structured items).
- Sometimes you need synthesis (summaries/comparisons) based on action result.

**Recommendation**
- Policy-driven default:
  - In action-heavy modes: default to **no generation** for successful read results unless user asked for synthesis.
  - In navigator modes: allow synthesis when it improves UX.
- Avoid adding extra LLM calls; reuse the existing extraction signal.

**Where**
- Intent contract + mode translation/policy.

**Decision**: **Implement**.

---

### 8) “Replace read-only empty result with RAG result”
**Critique**
- Good UX if a read action returns nothing but the knowledge base has relevant results.
- Dangerous if applied to write/mutating actions or to actions that failed due to auth/filters.
- The wording “replace” can hide what happened (action ran but user sees only RAG), which conflicts with the framework philosophy (no silent/deceptive behavior).

**Recommendation**
- Add a mode-gated behavior:
  - Only for actions explicitly labeled read-only (developer-set)
  - Only when action succeeded but returned an empty payload (not when it errored)
  - Fallback uses the user query (or optimized query), not action internals
  - Prefer “read probe → then RAG”: use RAG/generation for the final answer, but keep action attempt visibility in debug metadata/response structure.

**Where**
- Intent/action execution layer + policy.
- Curated packs to enable per mode.

**Decision**: **Implement (mode-gated + read-only only)**.

---

### 9) “Relate RAG data across vector spaces with resolved targets (reviews ↔ product ids)”
**Critique**
- Valuable, but requires a **join mechanism**:
  - explicit link fields (e.g., review.productId)
  - vector DB metadata filtering support (or scan + filter)

**Recommendation**
- **Simple v0 (ship now):**
  - Prerequisite: review documents must carry product identifiers (e.g., `productId` and/or `sku`) in metadata (and/or clearly in indexed content).
  - When a “reviews” query arrives and product targets are pinned:
    - If the backend supports metadata filtering: filter reviews by pinned `productId`/`sku`.
    - Otherwise: add an identifier-only `retrievalQueryHint` (id/sku/name tokens), not full attachment text.
  - Response honesty: if the retrieved reviews don’t contain identifiers that match the pinned product(s), the assistant must state the relation is **uncertain** (“I couldn’t verify these reviews are for that product”).
- **V1 (defer):**
  - Add an explicit link schema (foreign keys) + backend-native filters for precise, explainable joins across spaces.

**Decision**: **Implement v0 + Defer v1**.

---

### 10) “Accumulate documents in context; replace old; configurable”
**Critique**
- This is how you prevent drift and keep a stable “working set”.
- Must be bounded to avoid context blow-up.

**Recommendation**
- Introduce a working-set policy:
  - keep last `K` docs or last `T` turns
  - merge strategy: replace vs append vs decay
  - surfaced in debug metadata

**Decision**: **Implement**.

---

### 11) “Action result should not replace resolved targets if empty”
**Critique**
- Correct. Empty action results should not wipe authoritative pins.

**Recommendation**
- Keep this invariant:
  - attachments override
  - successful action results with real items can update pins
  - empty results must not override

**Decision**: **Keep / enforce (required)**.

---

### 12) “Action result as part of context”
**Critique**
- Useful for follow-ups (“cancel it”, “change address”) but can cause token bloat and PII leakage.

**Recommendation**
- Include a structured, bounded “last action summary” in the extraction context:
  - action name, success, key ids (orderNumber), and a compact items list (id + vectorSpace)
  - never dump full payloads

**Decision**: **Implement (bounded + sanitized)**.

---

### 13) “If user sent confirmation while no actions, send to LLM based on previous context”
**Critique**
- Calling LLM/RAG here is wasted cost and introduces weird side-effects.

**Recommendation**
- Deterministic behavior:
  - If no pending action/draft: reply “Nothing to confirm” (or a polite one-liner).
  - Do not trigger retrieval/actions.

**Decision**: **Reject extra LLM/RAG** (keep simple).

---

### 14) “LLM should output document IDs it mentioned for UI”
**Critique**
- Strong UX improvement (clickable citations / pinned updates).
- Requires changing generation to return structured JSON or a parallel “references” field.

**Recommendation**
- Defer until we standardize a generation output contract:
  - `answer`
  - `referencedTargetIds[]` (must be a subset of known ids from pinned targets + this-turn working set)
  - strict parsing + fallback to plain answer
- Interim (no LLM contract change): UI can render already-known references (active attachments + pinned targets + this-turn documents) and doesn’t need the LLM to “emit ids”.

**Decision**: **Defer** (design first).

## Suggested next document(s)
- A mode/policy matrix for curated packs:
  - `navigator` / `navigator_deep` / `cart_assistant` / `support_resolver`
  - for each: action priority, retrieval rules, working set window, suggestion style, deep search control
- A small “UI metadata” contract + persistence rules for chat-session.
