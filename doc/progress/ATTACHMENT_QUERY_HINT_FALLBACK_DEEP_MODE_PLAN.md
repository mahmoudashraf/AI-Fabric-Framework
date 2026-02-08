# Attachment Query Hint Fallback (Deep Mode) — Plan

## Status
Proposed

## Summary
Introduce an **optional, mode-gated** “attachment query hint” mechanism that can inject **bounded normalized attachment content** into the **RAG embedding query** *only as a fallback* when deep-mode retrieval fails to find usable documents.

This is intended to improve disambiguation and recall for follow-ups like “price?”, “summarize specs”, “any negative reviews on them?” when the user has active attachments/pinned targets, while avoiding the “pinned Sony pollutes Apple search” failure by keeping the default retrieval query clean.

## Motivation / Problem
We have two competing requirements:

1) **Do not pollute retrieval queries** by always concatenating pinned/attachment content (hurts recall/precision, increases prompt-injection surface, can cause wrong vector-space drift).
2) **Deep mode must still succeed** when the user message is too short/ambiguous for retrieval (“this/it/them”) and the attached content is the only grounded reference.

The safe compromise is:
- Attempt retrieval with the user’s query first (plus any safe `retrievalQueryHint`),
- only if retrieval returns **0 usable docs** do we retry once with an **attachment content hint** appended.

## Non-Goals
- Always-on “attachment content in query” (too risky, repeats earlier failures).
- Cross-vector-space relations (e.g., product → reviews) beyond simple query enrichment; that needs an explicit relation schema/strategy.
- Provider-specific server-side filtering changes (handled separately via RAG scope/filter work).

## Proposed Behavior

### High-level flow (Deep mode only)
**Attempt #1 (default):**
- Query = `userQuery` (+ optional `retrievalQueryHint` appended)
- RAG search runs normally.

If Attempt #1 produces **no usable context** (e.g., `documents=[]` or “No relevant context found”):

**Attempt #2 (fallback):**
- Query = `userQuery` (+ optional `retrievalQueryHint`) + `attachmentContentHint`
- One retry only.
- Result used for downstream generation/answer.

### Gating
New mode override (default OFF):

```yaml
ai:
  orchestration:
    modes:
      navigator_deep:
        enable-attachment-query-hint: true
```

Notes:
- Keep OFF by default in all packs.
- Enable only in curated packs that explicitly want this behavior (`navigator_deep`, or a UI-driven deep-search toggle).

### Attachment hint source (bounded, normalized)
Use only **normalized** attachment content, not raw UI input:
- source: `attachmentsNormalized[].contentText` / resolved target `contentText` (already bounded by `ai.orchestration.attachments.max-content-text-chars`)
- use only **active** attachments (based on `activeAttachmentIdsResolved`)

Bounds (defaults; tune via config):
- `maxActiveTargetsForHint`: 1–3
- `maxTotalHintChars`: 300–800
- normalize: replace newlines with spaces, collapse whitespace, trim

### Safety
- Prefer using already PII-processed normalized content when `PIIDetectionService` is present.
- Do not include the hint unless Attempt #1 clearly fails (0 usable docs).
- Never attempt more than one fallback retry.

## Observability / Debug Metadata
Add explicit metadata so we can see when/why the fallback triggered:
- `attachmentQueryHintEnabled` (effective mode/policy)
- `attachmentQueryHintApplied` (whether attempt #2 was executed)
- `attachmentQueryHintUsedAsFallback` (true for attempt #2 only)
- `attachmentQueryHintTargetsUsed` (list of target IDs used; bounded)
- `attachmentQueryHintChars` (actual chars appended)
- `attachmentQueryHintMaxTargets` / `attachmentQueryHintMaxChars` (effective limits)

## Implementation Sketch (Where)
1) **Orchestration policy surface**
   - Add `enableAttachmentQueryHint` to mode overrides and to the effective `OrchestrationPolicy`.

2) **IntentHandlingStep**
   - When in deep mode + `enableAttachmentQueryHint=true`:
     - run RAG attempt #1 normally.
     - if no usable docs, build hint from active normalized attachments and retry once.
   - Ensure this does not modify the query passed to the LLM for generation (only retrieval embedding query changes).

3) **Hint builder utility**
   - A small helper to extract active attachment text, normalize, bound, and return a safe suffix string.
   - Must not be domain-specific; treat the content as opaque text.

4) **Tests**
   - Unit test: deep mode, attempt #1 returns 0 docs → attempt #2 invoked with appended hint.
   - Unit test: attempt #1 returns docs → no retry and hint not appended.
   - Unit test: only active attachments contribute to hint; bounds enforced; debug metadata populated.

## Configuration Guidance (Curated Packs)
- Keep OFF in `navigator`.
- Enable in `navigator_deep` only, and document that it is a fallback mechanism.

## Risks / Trade-offs
- Even bounded attachment text can reduce retrieval quality if used too often; the “retry only on empty” rule is critical.
- For “broad search” queries, pinned/attachment hints should not be applied (attempt #1 should find docs).
- If a KB is sparse, attempt #1 may return empty frequently; consider tightening “usable docs” detection or adding a minimum threshold.

