# Navigator Deep: Force Retrieval When Targets Present (Even If LLM Says `requiresRetrieval=false`)

## Status
- **Draft** (for review)

## Problem
In real e‑commerce UX, users often:
- attach/select one or more products, then ask follow‑ups like **“negative reviews?”**, **“return policy?”**, **“compare storage?”**
- *without* repeating “this/it/them” in the message
- *and* often without re‑sending attachments on the next 1–3 turns (relying on the chat memory / pinned targets window).

Today, in `navigator_deep`:
- the extractor may set `requiresRetrieval=false` (especially when it believes authoritative context is sufficient),
- which can incorrectly skip RAG in cases where the answer actually lives in other spaces (e.g., `review`, `policy`) or requires deeper grounding.

Additionally, we currently have a backend optimization that can skip retrieval when pinned targets are present (to **minimize RAG**).
In `navigator_deep`, this optimization is harmful for cross-space follow-ups (e.g., product attachments → reviews/policies),
because it can suppress retrieval even when the user’s question clearly needs it.

This causes the observed failure mode:
- Assistant replies “I don’t have enough info / no reviews in context” **instead of** retrieving reviews/policies.

## Goal (MVP)
Make `navigator_deep` behave like “deep lookup assistant”:
- If the request is INFORMATION and there are active attachments or recent stored pinned targets,
- **prefer retrieval** when the user query is likely target-dependent even if the LLM forgets to set `requiresRetrieval=true`.

## Non‑Goals
- No string matching / keyword heuristics in backend (“negative reviews”, “policy”, etc.).
- No change to normal `navigator` behavior.
- No broad “always RAG” for all messages (avoid cost and noise).

## Proposed Behavior

### Definitions
- **Active targets**: resolved targets from the current request attachments (explicit user selection).
- **Stored pinned targets**: resolved targets seeded from prior turns within the configured TTL/window (implicit memory).
- **Minimize RAG**: a backend optimization that may skip retrieval when pinned targets appear to cover the request.

### When `forceRetrievalWhenTargetsPresent` capability is enabled
For `INFORMATION` intents:
1. If `requiresRetrieval=true`: proceed as usual.
2. If `requiresRetrieval=false` **and** there are **active targets**:
   - Override to `requiresRetrieval=true` (deep retrieval is allowed),
   - Run Advanced RAG / fan-out as configured by the effective policy.
3. If `requiresRetrieval=false` **and** there are **stored pinned targets** (no active attachments this turn):
   - Override to `requiresRetrieval=true` **only if** the stored targets are still within TTL/window,
   - Use stored pinned targets as the target set for deep retrieval within the configured TTL/window.

### Minimize RAG (Skip-Retrieval) Gating
When `minimizeRagWhenPinnedTargetsCoverRequest=false`, **disable** the “minimize RAG” skip-retrieval heuristic:
- Even if pinned targets are present,
- and even if the extractor chose a vector space that matches pinned targets,
- do **not** skip retrieval purely due to pinned targets.

Rationale:
- Deep mode is explicitly “most capable” and should be able to retrieve across spaces and deepen grounding.

### Guardrails (to avoid “cross-topic pollution”)
- Do **not** override when the user message is a pure acknowledgement / social turn **and** the extractor produced a `directAnswer`.
- Do **not** override if policy says `retrievalEnabled=false`.
- Respect `vectorSpace` allowlist rules if `retrievalAllowlistRequired=true`.

## Configuration / Gating (Policy-driven)
Add a policy capability flag (mode override):

```yaml
ai:
  orchestration:
    modes:
      navigator_deep:
        force-retrieval-when-targets-present: true
        force-retrieval-consider-stored-targets: true   # default true for deep, but explicit
        minimize-rag-when-pinned-targets-cover-request: false
```

Defaults:
- `force-retrieval-when-targets-present`: **false** globally
- enabled explicitly in `navigator_deep` only (curated pack level).
- `minimize-rag-when-pinned-targets-cover-request`: **true** globally (minimize cost in normal modes), but **false** in `navigator_deep`.

## Implementation Sketch

### Where to implement
`IntentHandlingStep` (INFORMATION path), after intent normalization and policy resolution:
- Determine:
  - policy capability flags (do not branch on `mode` in core)
  - `hasActiveTargets` (resolved targets source == request attachments)
  - `hasStoredTargets` (resolved targets source == stored pinned targets)
  - `llmRequiresRetrieval`
  - `isAckLike` (safe condition based on extracted `directAnswer` presence + requiresGeneration=false + requiresRetrieval=false)
- If deep + targets present + llmRequiresRetrieval=false + not ack-like → flip `requiresRetrieval=true`.
- If deep → ensure “minimize RAG” skip heuristic is disabled (do not short-circuit retrieval due to pinned targets).

### Debug/Observability
Add metadata flags to the orchestration result:
- `metadata.retrievalForced=true`
- `metadata.retrievalForcedReason=ACTIVE_TARGETS|STORED_TARGETS`
- `metadata.retrievalForcedMode=navigator_deep`
- `metadata.minimizeRagHeuristicEnabled=true|false`
- `metadata.retrievalSkipped=true|false` + `metadata.retrievalSkipReason=...` (already present in some flows; ensure it’s consistent)

### Interaction with target-hint expansion
This change is complementary:
- forcing retrieval ensures RAG runs
- target-hint expansion remains gated by `requiresTargetResolution` (separate decision)

## Testing Plan
### Integration (Real app)
1) Attach product A, ask: “negative reviews?” → should retrieve from `review` space and answer.
2) Next turn (no attachments): “and return policy?” → should retrieve from `policy` space (if allowlisted) or ask for space if required by policy.
3) Topic shift after 3+ turns: ensure stored targets expire and deep retrieval is not incorrectly forced.

### Automated tests (core)
- When deep flag enabled + active targets + llm says requiresRetrieval=false → retrieval runs and metadata shows forced retrieval.
- When deep flag enabled + stored targets + llm says requiresRetrieval=false → retrieval runs only within TTL.
- When directAnswer present (ack) → no forced retrieval.

## Risks
- Cost increase in deep mode for target-dependent follow-ups.
- If stored pinned targets are too sticky, user topic changes can trigger wrong retrieval. TTL/window must stay small and debuggable.
