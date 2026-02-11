# Navigator Deep: Advanced RAG for Multi‑Space Retrieval (Fan‑Out + Global Rerank)

## Status
- **Draft** (for review)

## Problem
`navigator_deep` enables **fan‑out** retrieval across multiple vector spaces (e.g., `product`, `review`, `policy`).

Today:
- fan‑out uses `RAGProvider.performRAGQuery/performRag` **per space** and merges documents naïvely,
- `AdvancedRAGProvider` is only invoked on the **single‑space** path.

Result:
- deep mode can retrieve wide data, but lacks the benefits of “advanced” orchestration across the **union** of spaces:
  - consistent query expansion/reranking strategy across all candidates,
  - global context optimization under one budget,
  - better grounding for “product + reviews + policies” questions.

## Goal (MVP)
In `navigator_deep`, when multiple vector spaces are selected:
- still fan‑out, but apply **advanced orchestration** at the aggregate layer:
  1) retrieve candidates per space,
  2) normalize/merge,
  3) rerank globally,
  4) build a single bounded context for generation.

## Non‑Goals
- No domain coupling (no “productId”, “orderId” assumptions).
- No provider‑specific hacks in core (keep provider interface stable or explicitly versioned).
- No unbounded document dumps to LLM/UI.

## Design Options

### Option A (Recommended MVP): “Advanced Fan‑Out Aggregator” (Core)
Keep `AdvancedRAGProvider` API as‑is.

Pipeline changes:
- For multi‑space retrieval in `navigator_deep`, do:
  - **Stage 1**: per-space retrieval using `RAGProvider.performRAGQuery` (or `performRag` for retrieval-only).
  - **Stage 2**: global post-processing in core:
    - unify docs list (tag with `vectorSpace`),
    - apply **global reranking** (SPI or heuristic) across the union,
    - context optimization under one budget (max docs + max chars),
    - generation.

Add a new SPI:
- `GlobalReranker` (optional):
  - input: `List<RAGDocument>` with `vectorSpace`, `similarity/score`
  - output: reordered list + optional score adjustments
  - default: stable sort by score + diversity cap per space.

Pros:
- Minimal provider surface changes.
- Works with existing RAG providers immediately.
- Keeps “advanced multi-space” deterministic and observable.

Cons:
- Not as powerful as a provider‑integrated advanced pipeline (no provider-specific rerank models unless SPI used).

### Option B: Extend `AdvancedRAGProvider` to Accept Multiple Spaces
Change API:
- `AdvancedRAGRequest.vectorSpaces: List<String>` (or `entityTypes`) and add budgets.

Provider does:
- fan‑out retrieval, query expansion, rerank, context optimization.

Pros:
- Maximum quality, provider can use best-in-class rerankers.

Cons:
- Breaking API change or parallel v2 interface.
- More work across providers and integration tests.

### Option C: Per-space AdvancedRAG then Merge (Hybrid)
- Run `AdvancedRAGProvider` once per space (with tuned budgets),
- merge expanded queries + reranked docs,
- global context optimization.

Pros:
- Uses provider’s advanced logic per space.

Cons:
- Expensive (N advanced calls).
- Hard to normalize scores across spaces.

## MVP Proposal (Option A)

### Policy/Mode gating
Add mode-level flag:

```yaml
ai:
  orchestration:
    modes:
      navigator_deep:
        advanced-fanout-enabled: true
```

Defaults:
- `false` globally
- enabled in curated `navigator_deep` only.

### Runtime flow (multi-space + deep)
1) Determine vector spaces (LLM or allowlist/fanout routing).
2) For each space:
   - perform retrieval (topKPerSpace + threshold).
3) Combine all docs:
   - attach `vectorSpace` field to each doc (already done in `tagDocumentWithVectorSpace`).
4) Apply a **diversity cap**:
   - maxDocsPerSpaceUsedForContext (default = `ragTopKPerSpace`, bounded).
5) Optional `GlobalReranker`:
   - reorder docs.
6) Build final context:
   - use top `ragMaxDocumentsUsedForContext`,
   - obey `ragMaxContextChars`.
7) Generation:
   - same as current: `prependPinnedTargetsContext(context)` then `generateRagAnswer(...)`.

### Debug metadata
Expose:
- `metadata.retrievalStrategy=FAN_OUT`
- `metadata.advancedFanoutEnabled=true`
- `metadata.globalReranker=DEFAULT|<impl>`
- `metadata.docsBySpaceCounts={product:8, review:8, policy:8}`
- `metadata.docsUsedForContextCounts={product:3, review:3, policy:2}`

## Tests
### Unit
- Multi-space deep mode produces bounded merged context (max docs + max chars).
- Diversity cap prevents one space from dominating.
- Global reranker ordering is applied (when SPI present).

### Real-api integration
- In `navigator_deep`, query “any negative reviews on this product?” with active product attachment:
  - documents returned include both `product` and `review` spaces (when available),
  - generation is grounded in the merged context,
  - debug shows `advancedFanoutEnabled=true`.

## Migration/Compatibility
- Greenfield: no backwards compatibility required.
- Default behavior unchanged unless flag enabled.

