# Intent Metadata → RAG Query Augmentation — Change Plan

## Status
Proposed

## Problem
Today, the intent extractor can return a free-form metadata object (`MultiIntentResponse.metadata`, surfaced as `result.metadata.intentMetadata`). This metadata is currently **diagnostic-only** and is **not** used by retrieval.

In practice, we sometimes want the LLM to provide extra retrieval hints (keywords, entity IDs, alternate phrasing) that improve embeddings/RAG recall without overloading the main intent fields. We need a **deterministic, contract-based** way to let “intent metadata” influence the **RAG query string**.

## Goals
- Allow the intent extractor to provide retrieval hints in a **strict, bounded contract**.
- Deterministically augment the RAG query with those hints when `requiresRetrieval=true`.
- Keep the mechanism **domain-agnostic** (no “products/orders/items” heuristics).
- Keep it **safe** (avoid injecting PII or long free-form text into retrieval queries).

## Non-goals
- Treating arbitrary metadata keys as retrieval signals.
- Relying on string matching of user messages or action names to infer query hints.
- Backward compatibility (greenfield).

---

## Design (Greenfield)

### 1) Introduce an explicit metadata contract key (opt-in)
Define a single reserved key in the extractor metadata:

- `metadata.retrievalQueryHint` (string)

Rules for the extractor:
- Only populate it when `requiresRetrieval=true`.
- It must be **keywords/identifiers only** (no sentences), max 200 chars.
- Must not contain emails/phones/addresses; avoid whitespace-heavy text.

This keeps the free-form metadata object, but makes retrieval behavior depend only on a **single explicit key** (fail-closed).

### 2) Query composition rule
When handling INFORMATION intents with retrieval enabled, build the final query as:

1) `baseQuery` = `intent.optimizedQuery` if present else pipeline `effectiveQuery`.
2) `hint` = `intentResponse.metadata.retrievalQueryHint` (if valid)
3) `finalQuery`:
   - if `hint` is blank → `baseQuery`
   - else → `baseQuery + " " + hint`

Notes:
- We append with a single space so embeddings stay natural-language friendly.
- We never append hint text when retrieval is disabled (direct-answer mode).
- Multi-intent safety: only apply the hint when the extraction response contains exactly **one** retrieval intent; otherwise ignore the hint (avoid cross-intent contamination).

### 3) Safety/validation
Before using `retrievalQueryHint`:
- Reject if it contains `@` (probable email) or newlines.
- Reject if > 200 chars.
- Reject if it contains multiple whitespace runs (signals free-form text).

If rejected, ignore (fail-closed) and proceed with `baseQuery`.

### 4) Observability
Attach a small debug marker (non-functional):
- `RAGRequest.metadata.retrievalQueryHintApplied = true/false`

So logs/realapi tests can assert behavior without leaking the hint into user-visible responses.

---

## Implementation notes
- Update the intent-extraction system prompt schema to document `metadata.retrievalQueryHint`.
- In `IntentHandlingStep.handleInformation(...)`, read `PipelineContext.getIntentResponse().getMetadata()` and augment the query before building `RAGRequest`.
- Add unit tests around query composition:
  - hint present and valid → appended
  - hint present but invalid → ignored
  - requiresRetrieval=false → never appended

## Acceptance criteria
- When `metadata.retrievalQueryHint` is provided and valid, RAG searches use `finalQuery` (base + hint).
- No domain-specific key guessing exists in core.
- Invalid/unsafe hints are ignored deterministically.
