# `vectorSpace` Inference & Routing Recommendation

## Problem statement
In our intent schema, `vectorSpace` indicates which knowledge base “space” (often mapped to `entityType`) should be searched during RAG. Some providers occasionally return incomplete intent JSON (even after repair), e.g.:

- `requiresRetrieval=true`
- `vectorSpace` missing/blank

If we pass that through to retrieval, we can end up with `entityType=null` (or the equivalent) and certain vector providers will hard-fail (and/or the request routes to the wrong space).

## What we do today
Current behavior is implemented in `IntentQueryExtractor`:

- If `requiresRetrieval=true` and `vectorSpace` is blank:
  1. Load `KnowledgeBaseOverview` (entity types + document counts)
  2. If only one entity type exists → use it
  3. Else try to match entity types by mention in the user query
  4. Else pick the entity type with the largest document count
  5. Else fall back to the first known entity type

This is a pragmatic “don’t crash” safety net and is logically consistent for small KBs.

## Why this becomes risky at scale
For large KBs with many entity types/spaces:

- “largest-count” can silently route most ambiguous queries into the biggest domain (systematic correctness regression).
- query-string mention matching is brittle (synonyms, multilingual, abbreviations).
- wrong space selection is often worse than “ask a clarifying question” because it produces confident-but-wrong answers.

## Routing options (when `requiresRetrieval=true` and `vectorSpace` missing)
### Option A — Keep heuristic-only (current)
- Cheap and deterministic.
- Best as a crash-prevention fallback, not a production routing strategy for multi-domain KBs.

### Option B — Clarification-first
- If `vectorSpace` is missing after repair, return a structured “need clarification” response (ask user which domain/entity type).
- Highest correctness, adds user friction.

### Option C — Bounded “search-all” fallback (fan-out)
- Choose top N candidate spaces (from `KnowledgeBaseOverview`).
- Retrieve small `topK` from each, merge + rerank.
- Continue generation with the merged context.
- Good coverage with bounded cost; requires merging/normalization logic.

### Option D — Add an explicit routing stage
- Add a router step that outputs `{vectorSpace, confidence, rationale}`.
- If confidence is low: either ask clarification (Option B) or bounded fan-out (Option C).
- Best long-term UX/correctness tradeoff, more components/telemetry.

### Option E — Make `vectorSpace` optional via a unified index + metadata filters
- Store all vectors in one index/collection (or few partitions) with `entityType` as metadata.
- Default retrieval becomes “search everything”, apply filters only when needed.
- Architectural simplification, but depends on provider limits/cost and requires migration work.

## Recommendation
### Near-term (keep current safety net, improve correctness without big architecture)
1. Keep the current heuristic as **crash prevention**, but avoid relying on “largest-count” as the final production answer when there are multiple spaces.
2. Add a configurable fallback policy:
   - If KB has exactly 1 space → auto-fill it (safe).
   - Else → prefer **bounded fan-out** (Option C) with small `N` and `topK` (defaults should be conservative).
   - If fan-out results are weak/low-similarity → ask clarification (Option B).

### Mid-term (recommended default for multi-domain)
3. Add a dedicated routing step (Option D) and use fan-out/clarification only as confidence-based fallbacks.

### Long-term (if product direction is a single unified KB)
4. Consider unified indexing (Option E) so missing `vectorSpace` is not a failure condition.

## What to measure
- % of intents missing `vectorSpace` after extraction/repair
- routing fallback usage rate (heuristic vs fan-out vs clarification)
- latency impact (p50/p95) per fallback
- retrieval quality (hit rate / similarity distributions / manual eval)
- cost impact (vector queries per request; router LLM calls if applicable)

