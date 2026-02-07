# Pinned Targets → Embedding Query Expansion (Semantic RAG) — Change Plan

## Status
Proposed

## Problem
We have two recurring retrieval failures in ecommerce chat:

1) **The embedding query is often too structured / pseudo-DSL**
   - Example: `list_products with price_usd < 1000 AND different_makers`
   - This is not ideal for semantic embeddings and reduces recall.

2) **Pinned-target follow-ups don’t retrieve cross-space grounding**
   - When the user asks something *about pinned targets* (e.g., “any negative reviews on them?”) we often need retrieval in a different space (reviews/policies).
   - If the embedding query doesn’t include the pinned target identifiers/names, the review/policy search can miss the relevant documents.

We need a **domain-agnostic**, **bounded**, **mode-gated** way to expand the embedding query with **just enough pinned-target evidence** to improve retrieval.

---

## Goals
- Keep the embedding query **semantic** (natural language).
- When the user query is **target-dependent**, expand embeddings query using pinned targets (ids/titles/names/description snippets/metadata) within strict limits.
- Make this **configurable** and **safe-by-default** (no always-on “Sony pinned pollutes Apple search” regressions).
- Keep debug/telemetry clear:
  - user query != embedding query (but both should be visible)

## Non-goals
- Concatenating full pinned targets blocks into embedding query.
- Domain heuristics like scanning for `sku/orderId/products` keys.
- Building a full filter compiler (numeric field filters, DSL evaluation) in v1.

---

## Design

### 1) A new explicit composition step: `EmbeddingQueryComposer`
Introduce a small deterministic composer that builds the string used for embeddings / vector search:

Inputs:
- `userQuery` (sanitized user input; from `PipelineContext.originalQuery` / `processedQuery` depending on PII policy)
- `optimizedQuery` (semantic; optional; from `Intent.optimizedQuery`)
- `retrievalQueryHint` (keywords only; from extraction metadata; optional)
- `requiresTargetResolution` (from intent extraction)
- `resolvedTargets` (active attachments + stored pinned targets; already available in `PipelineContext.resolvedTargets`)
- `policy` / config

Outputs:
- `embeddingQuery` (string, used for `RAGRequest.query`)
- debug facts: `targetHintApplied`, `targetHintTargetsUsed`, `targetHintChars`

### 2) Base query selection (semantic first)
`baseQuery`:
1) Prefer `intent.optimizedQuery` (semantic, natural language) when present
2) Else use `userQuery` (sanitized user input)

This explicitly avoids `PipelineContext.effectiveQuery` as an embedding input if it contains multi-turn “history blobs” or injected pinned context.

### 3) Append retrieval hint (existing contract)
Keep the existing contract (ADR-0009):
- Always append `retrievalQueryHint` (when valid) for retrieval intents.
- This is bounded and should be PII-safe.

### 4) Append a *target hint* only when target-dependent
We only expand the embedding query with pinned targets when:
- `requiresRetrieval=true`, AND
- `requiresTargetResolution=true`, AND
- `resolvedTargets` is non-empty, AND
- `policy.rag.targetHint.enabled=true` (mode/policy gated)

Rationale:
- This avoids polluting normal catalog searches (Sony pinned targets will not pollute “Apple laptops” search).
- It relies on the LLM’s *structural* signal (`requiresTargetResolution`) rather than backend string matching.

### 5) Target hint content (bounded + configurable)
We build a **compact semantic hint** from targets.

Config (suggested):
- `ai.orchestration.rag.target-hint.enabled` (default: `false`)
- `ai.orchestration.rag.target-hint.max-targets` (default: `2` or `3`)
- `ai.orchestration.rag.target-hint.max-chars` (default: `500`)
- `ai.orchestration.rag.target-hint.include-vector-space` (default: `true`)
- `ai.orchestration.rag.target-hint.include-id` (default: `true`)
- `ai.orchestration.rag.target-hint.include-content-text` (default: `true`)
- `ai.orchestration.rag.target-hint.max-content-text-chars-per-target` (default: `120`)
- `ai.orchestration.rag.target-hint.metadata-keys-allowlist` (default: `[]`)

Domain-agnostic rule:
- We do **not** assume metadata keys exist.
- If allowlist is empty: do not include arbitrary metadata keys (prevents PII leakage by default).
- Commerce curated pack may set an allowlist (e.g., `["sku","name","title","category","brand"]`) as an explicit domain choice.

Example target hint output:
```
Targets: [product id=30 sku=SKU-BOS-20002 name="Bose Pro Headphones"] [product id=77 name="Apple Pro Display XDR"]
```

### 6) Safety
- Target hint is built from user-provided attachments / stored pinned targets.
- Before appending:
  - collapse whitespace
  - strip newlines
  - enforce max chars
  - (optional) if PII service exists, run redaction on the final embeddingQuery, or at minimum reject obvious emails (`@`) in the appended hint.

---

## Orchestrator plumbing changes

### A) Use `embeddingQuery` for vector DB, keep user query for generation
In `IntentHandlingStep` when building the RAG request:
- `RAGRequest.query` = `embeddingQuery`
- `RAGResponse.metadata.embeddingQuery` = `embeddingQuery`
- `RAGResponse.originalQuery` = `userQuery` (sanitized user query)

Generation:
- The “question” shown to the generation prompt should remain the **user query**.
- Do not feed structured embedding query into the answer LLM prompt.

### B) Observability
Attach metadata fields for debug/tracing:
- `ragQuery.userQuery`
- `ragQuery.embeddingQuery`
- `ragQuery.targetHintApplied`
- `ragQuery.targetHintTargetsUsed`
- `ragQuery.targetHintChars`

---

## Prompt updates
Update curated prompts to strengthen `requiresTargetResolution` accuracy:
- For INFORMATION intents: set `requiresTargetResolution=true` whenever the user is referring to pinned targets implicitly (without explicit identifiers).
- For reviews/policies/alternatives questions about pinned products: keep `requiresRetrieval=true` and `requiresTargetResolution=true`.

No backend string matching.

---

## Validation / Tests

1) Unit test for composer:
- With `requiresTargetResolution=false` + pinned targets → no target hint appended
- With `requiresTargetResolution=true` + pinned targets → target hint appended and bounded
- With allowlist empty → metadata excluded
- With allowlist provided → allowlisted keys included

2) Orchestrator unit test:
- Ensure `RAGRequest.query` uses `embeddingQuery`
- Ensure `RAGResponse.originalQuery` stays user query
- Ensure `RAGResponse.metadata.embeddingQuery` present

---

## Rollout
- Enable only in `navigator_deep` initially (policy-gated).
- After validating, consider enabling for `navigator` only when `requiresTargetResolution=true` to reduce cost and avoid regressions.

