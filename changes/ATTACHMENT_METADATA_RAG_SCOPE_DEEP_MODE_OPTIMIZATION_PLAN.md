# Attachment Metadata in RAG Search + Deep Mode Attachment Usage (Structured Scope/Hints) — Plan

## Status
Proposed

## Motivation
We want RAG retrieval to become *attachment-aware* **without** degrading retrieval quality by:
- concatenating full attachment text/content into the retrieval query, or
- relying on domain-specific heuristics (e.g., “sku”, “orders”, “products” keys).

In particular, “Deep mode uses attachments” should mean:
- attachments influence retrieval via **structured scope/hints** (filters, routing inputs, query expansion context),
- not by appending attachment content into the retrieval query string.

This plan is designed to align with:
- `changes/ATTACHMENT_GROUNDING_END_TO_END_FIX_PLAN.md`
- `changes/MANUAL_DEEP_SEARCH_AND_PINNED_CONTEXT_EXTRACTION_OPTIMIZATION_PLAN.md`
- `Final_Documentation/Development_Guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md` (domain-agnostic + greenfield)

---

## Goals
1) **Use attachment metadata in retrieval** (RAG + Advanced RAG) in a bounded, structured way.
2) Ensure deep mode can leverage attachments **without** concatenating attachment text into the retrieval query string.
3) Keep implementation **domain-agnostic**:
   - no “sku/product/orders” key matching in core code,
   - all semantics either generic (`id`, `vectorSpace`) or config-driven.
4) Make the behavior **observable** in debug metadata.
5) Keep behavior **safe** (PII + prompt-injection aware) and bounded (size limits).

## Non-goals
- Implement cross-vector-space “relation scoping” (e.g., product ↔ review) via hardcoded metadata keys.
- Add new LLM calls purely for “hint synthesis” (unless explicitly enabled in deep mode via existing query expansion).
- Append full attachment content into the retrieval query as a shortcut.

---

## Current Behavior (problem framing)
Today, attachments mainly affect:
- intent extraction (because pinned context is injected into the LLM-visible prompt),
- generation grounding (because pinned targets can be injected into generation prompt),
but **retrieval** often still uses:
- a short, ambiguous query (e.g., “price?”),
- and does not consistently use attachment metadata to scope or disambiguate retrieval.

Deep mode (Advanced RAG) currently can receive “context”, but we do not have a stable contract for:
- passing pinned targets as a structured “scope”, and
- enforcing that deep-mode retrieval is attachment-aware without query concatenation.

---

## Proposed Design

### A) Introduce a structured `RAGScope` payload (generic, bounded)
Add a small “RAG scope” structure that can be attached to retrieval requests (both basic and advanced):

**RAGScope (conceptual)**
- `targets[]`:
  - `id` (string; required)
  - `vectorSpace` (string; optional/unknown allowed)
  - `metadata` (scalar-only, bounded map; values stringified/truncated)
- `activeTargetIds[]` (ids explicitly pinned by UI; bounded)
- `source` (`ACTIVE_ATTACHMENTS` | `STORED_PINNED_TARGETS` | `MIXED`)
- `truncated` (boolean; whether scope was truncated due to limits)

**Key constraints**
- No unbounded content fields.
- No raw attachment `contentSnippet` in retrieval scope (that is generation context, not retrieval query).
- All metadata is best-effort + bounded (max keys, max value length).

### B) Populate `RAGScope` from resolved targets (authoritative)
Use the existing authoritative model:
- Active attachments → `resolvedTargets` (authoritative)
- Stored pinned targets (short window) → `resolvedTargets` (authoritative)

Build `RAGScope` from `PipelineContext.resolvedTargets` and attach it to:
1) **Basic RAG**: `RAGRequest.context` (under a dedicated key, e.g., `ragScope`)
2) **Advanced RAG**: `AdvancedRAGRequest.metadata` and/or `AdvancedRAGRequest.filters` (see C/D)

This makes attachment metadata available for retrieval *without* modifying the retrieval query string.

### C) Deep mode uses attachments via “expansion context” (not concatenation)
Deep mode already performs query expansion. We can make expansion smarter by providing the `RAGScope` as **structured context** to the expansion prompt:

- Extend the advanced expansion prompt template input to include `authoritative_context` (derived from `RAGScope`).
- The expansion prompt must:
  - prefer IDs/names/identifiers from the pinned scope when generating expanded queries,
  - never copy full pinned content text verbatim into expanded queries,
  - never include PII (emails/phones/addresses).

This keeps “deep mode uses attachments” true, while avoiding direct concatenation of attachment text into the retrieval query.

### D) Optional: Scoped retrieval filters (best-effort, provider-agnostic)
Where supported, use the `RAGScope.targets[].id` to scope retrieval using filters:

- For basic RAG and advanced RAG:
  - set `RAGRequest.filters` / `AdvancedRAGRequest.filters` with a generic structure, e.g.:
    - `{"_ragScopeTargetIds": ["30","85"]}`
    - `{"_ragScopeVectorSpaces": ["product"]}`

**Important:** This does *not* assume domain-specific metadata keys.

Provider behavior:
- If the vector backend supports server-side filtering → apply it.
- Otherwise → filter client-side after retrieval (bounded top-K) and report filtering in debug metadata.

This can be phased in:
- Phase 1: only attach `RAGScope` (no filtering)
- Phase 2: apply best-effort filtering where possible

### E) Observability in orchestrator result
Add debug metadata fields (non-sensitive, bounded):
- `metadata.ragScope.provided=true/false`
- `metadata.ragScope.targetCount`
- `metadata.ragScope.source`
- `metadata.ragScope.truncated`
- `metadata.ragScope.filtersApplied=true/false` (if/when D is implemented)

---

## Mode/Policy Gating
This optimization should be enabled via orchestration policy/modes (curated packs), not ad-hoc request flags:

- `navigator`:
  - attach `RAGScope` for observability and future-proofing
  - do **not** apply scoped filters unless explicitly enabled
- `navigator_deep` (deep mode):
  - attach `RAGScope`
  - pass `RAGScope` into expansion prompt as authoritative context
  - (optional) enable scoped filters if provider supports it and it improves precision

Configuration surface (conceptual):
```yaml
ai:
  orchestration:
    modes:
      navigator:
        rag:
          attachment-scope:
            enabled: true
            apply-filters: false
      navigator_deep:
        rag:
          attachment-scope:
            enabled: true
            apply-filters: true   # optional / provider-dependent
            use-in-query-expansion: true
```

---

## Safety / Security
1) **PII:** The scope builder must avoid including unsafe fields and must truncate values.
2) **Prompt injection:** The scope passed into deep-mode query expansion must be rendered as data, not instructions.
3) **Bounds:**
   - max targets
   - max metadata keys per target
   - max total scope size

---

## Implementation Steps (phased)

### Phase 1 — Structured scope plumbing (no filtering)
1) Implement `RAGScope` builder (from `resolvedTargets`).
2) Attach it to:
   - `RAGRequest.context["ragScope"]`
   - `AdvancedRAGRequest.metadata["ragScope"]`
3) Add orchestrator debug metadata (`metadata.ragScope.*`).
4) Tests:
   - Unit: when attachments are active, `RAGRequest.context` contains `ragScope` and retrieval query does not contain attachment contentSnippet.
   - Unit: deep mode request includes `ragScope` in advanced request metadata.

### Phase 2 — Deep mode query expansion uses scope
1) Extend advanced expansion prompt to accept `authoritative_context` (rendered from `ragScope`).
2) Update curated prompt bundles for deep mode.
3) Tests:
   - Unit/RealAPI: deep mode expanded queries show use of pinned identifiers (not raw content dumps).
   - Ensure expanded queries do not include emails/phones/addresses.

### Phase 3 — Best-effort scoped filters (optional)
1) Introduce generic `_ragScopeTargetIds` filters and apply them where supported.
2) For providers without server-side filtering, apply client-side filtering after retrieval and report `filtersApplied=false` + `filteredClientSide=true`.
3) Tests:
   - Unit: filters appear in request when enabled by mode.
   - Integration: verify scoping improves precision for target-dependent follow-ups.

---

## Validation / How to Test (manual)
1) In `navigator_deep`, attach a product and ask a vague follow-up (“price?”, “summarize specs”):
   - confirm `metadata.ragScope.targetCount > 0`
   - confirm retrieval query string remains short (not full attachment text)
   - confirm deep mode uses the scope in expansion (debug shows `expandedQueries` referencing pinned identifiers)
2) In `navigator`, verify scope is present for debugging but does not change behavior unless configured.

---

## Open Questions (explicitly deferred)
1) Cross-vector-space relation scoping (e.g., product → reviews):
   - requires a configured relation/link schema or metadata filtering conventions
   - should be designed as a separate plan (no heuristics in core).

