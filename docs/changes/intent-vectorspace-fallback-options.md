# Intent `vectorSpace` Fallback Options (Large Knowledge Bases)

## Why this document exists
Some LLM providers occasionally return **incomplete intent JSON** (especially during “JSON repair” flows), e.g. `requiresRetrieval=true` but `vectorSpace` is missing. Downstream RAG then receives `entityType=null` and certain vector DB providers (notably Milvus) will hard-fail.

We added a safety net so the framework does **not crash** in those cases. This document evaluates whether that behavior is logically aligned with AI-Fabric and proposes better flows for **large, multi-entity knowledge bases**.

## Current behavior (safety net)
When `requiresRetrieval=true` and `vectorSpace` is blank:

1. Read `KnowledgeBaseOverview` (entity types + counts).
2. If only one entity type exists → use it.
3. Else try to match entity types by mention in the user query.
4. Else pick the entity type with the largest document count.
5. Else fall back to the first known type.

Goal: ensure we never run retrieval with `entityType=null`.

This is a reasonable **crash-prevention fallback**, but it is not a great **routing strategy** at scale.

## The core design question
When routing is missing (`vectorSpace` absent), should the framework:

- **Guess** a space and continue (best-effort completion),
- **Ask** for clarification (correctness-first),
- **Search across** multiple spaces (coverage-first, bounded cost),
- **Route via a dedicated router** (best of both, more complexity), or
- **Make vectorSpace optional** by using a unified index/collection with filtering (architecture-first).

## Option 1 — Keep the current heuristic (guess from KB overview)
**Flow**
- Fill `vectorSpace` from overview (single-type / mention / largest-count).

**Pros**
- Prevents null crashes.
- Zero extra runtime calls.
- Deterministic and cheap.

**Cons (important for large KBs)**
- “Largest-count” tends to route everything to the biggest entity type (silent correctness regression).
- Query text mention matching is fragile and language-dependent.
- Hard to explain/observe; users may get irrelevant results without knowing why.

**Where it fits**
- Small KBs with 1–3 entity types.
- Early-stage dev/testing environments.

## Option 2 — Clarification-first (never guess)
**Flow**
- If `requiresRetrieval=true` but `vectorSpace` is missing after repair:
  - return a deterministic “need clarification” result (or `OUT_OF_SCOPE` with structured reason), asking the user to choose a domain/entity type.

**Pros**
- Highest correctness.
- Avoids silent misrouting.
- Predictable compute cost.

**Cons**
- Adds user friction (extra turn).
- Some integration tests might need updates to allow this behavior.

**Where it fits**
- Enterprise settings with high correctness requirements.
- Multi-tenant KBs where wrong domain is worse than asking.

## Option 3 — Bounded “search-all” fallback (fan-out to top N spaces)
**Flow**
- If `vectorSpace` missing:
  - select top N candidate spaces from `KnowledgeBaseOverview` (e.g., by count or “recently updated”)
  - perform small retrieval against each (e.g., `topK=2..5`)
  - merge + rerank by similarity score (and optionally apply a per-space normalization)
  - then continue generation.

**Pros**
- Much better coverage than a single guess.
- No extra user interaction.
- Cost is bounded and configurable (N, topK).

**Cons**
- Extra vector DB calls (N queries).
- Needs careful score normalization when mixing providers/spaces.
- Requires merging logic and observability.

**Where it fits**
- Medium/large KBs with many entity types.
- When user experience should avoid clarification turns.

## Option 4 — Router stage (explicit vectorSpace selection before retrieval)
**Flow**
- Add a “routing” step that selects `vectorSpace` from known entity types.
  - Implementation can be rules-only, LLM-based, or hybrid.
  - Router returns `{vectorSpace, confidence, rationale}`.
- If confidence is low:
  - either ask for clarification (Option 2) or fall back to bounded search-all (Option 3).

**Pros**
- Scales well with many spaces if done right.
- Allows “explainable” routing (rationale, confidence).
- Can be evaluated/monitored independently.

**Cons**
- More moving parts (new step, more telemetry).
- If LLM-based, adds latency and cost.

**Where it fits**
- Large KBs and production setups.
- Multi-domain assistants.

## Option 5 — Unified index/collection + filtering (make vectorSpace optional)
**Flow**
- Store all entities in a single vector index (or a small number of partitions) and store `entityType` as metadata.
- Retrieval is global by default; apply `entityType` filters only when needed.

**Pros**
- Simplifies the intent schema: `vectorSpace` becomes an optimization, not a requirement.
- Great UX: “search everything” works naturally.
- Easier to support “unknown domain” queries.

**Cons**
- Not always feasible depending on vector DB provider limits/cost model.
- Migration effort + operational complexity (index sizing, filtering performance, metadata schema).

**Where it fits**
- When the product vision is a single unified knowledge base.
- When vector DB/provider supports global search + metadata filtering efficiently.

## Recommendation
### Near-term (safe + scalable without big architecture changes)
1. Keep the current heuristic only as **crash prevention**, but do **not** treat “largest-count” as a final answer for production-grade routing.
2. Introduce a configurable fallback policy:
   - If KB has 1 entity type → auto-assign it (safe).
   - Else → prefer **Option 3 (bounded search-all)** with small N/topK, and record telemetry (`routingFallbackUsed=true`, `candidates=[...]`).
   - If bounded search-all yields weak results → ask clarification (Option 2).

### Mid-term (best UX/correctness tradeoff)
3. Add a dedicated router stage (Option 4) and use search-all/clarification only when router confidence is low.

### Long-term (architectural simplification)
4. Consider unified index/collection with metadata filtering (Option 5) if it aligns with product direction.

## What to measure (to validate the choice)
- % of requests missing `vectorSpace` after intent extraction/repair
- Accuracy of routed `vectorSpace` (manual eval or labeled set)
- Retrieval success rate and user satisfaction for fallback paths
- Latency added by routing/search-all (p50/p95)
- Cost impact (vector queries per request; LLM calls if router is LLM-based)

