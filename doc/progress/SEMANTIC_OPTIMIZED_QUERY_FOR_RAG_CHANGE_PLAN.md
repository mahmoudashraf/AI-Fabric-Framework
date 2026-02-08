# Semantic `optimizedQuery` for RAG Embeddings + Correct `originalQuery` Reporting — Change Plan

## Status
Proposed

## Problem
We currently ask the intent-extraction LLM to generate `intent.optimizedQuery` using **system field names + operators** (pseudo-DSL).

This causes two recurring issues in production:

1) **Embedding query becomes non-semantic**
   - Example (bad for embeddings/semantic retrieval):
     - `list_products with price_usd < 1000 AND different_makers`
   - These strings are not natural language, and they can reduce retrieval quality (especially hybrid + semantic retrieval).

2) **`ragResponse.originalQuery` is misleading**
   - In some paths (notably fan-out), we set `ragResponse.originalQuery` to an internal “generationQuery / processedQuery”.
   - This produces logs like `"originalQuery": "list_products with price_usd < 1000 ..."` even when the user never typed that.
   - This makes debugging and UI telemetry inaccurate.

## Goals
- Make `intent.optimizedQuery` **semantic / natural language** so it is suitable for embeddings.
- Ensure `RAGResponse.originalQuery` is always the **sanitized user query** (what the user asked, with PII redaction if enabled), not an internal synthesized string.
- Keep the design **framework-level and domain-agnostic**.
- Keep it **observable** (debug shows what was embedded vs what the user asked).

## Non-goals
- Building a full query-language / filter compiler (numeric filters, field-specific constraints) in v1.
- Provider-specific retrieval rewriting.
- Domain heuristics (e.g., “products/orders/items” scanning).

---

## Design (Greenfield)

### 1) Redefine the meaning of `Intent.optimizedQuery`
**New contract (semantic):**
- `optimizedQuery` is **plain English** (semantic), optimized for retrieval embeddings.
- It should preserve constraints from the user query, but expressed naturally:
  - Good: `"budget laptops under $1000 from different brands"`
  - Good: `"Samsung tablets, budget options, include availability"`
  - Bad: `"list_products where price_usd < 1000 AND maker != null"`
  - Bad: `"search_products(category='Laptops' AND brand='Apple')"`

**When to set it:**
- Only when it improves retrieval beyond the raw user query (otherwise omit and let the system use the user query).

### 2) Keep structured hints out of the embedding string
If we need extra retrieval guidance, we keep using the existing contract:
- `metadata.retrievalQueryHint` (keywords/identifiers only; bounded; no PII)

This avoids “structured DSL pollution” in the embedding string while still improving recall.

### 3) Separate “user query”, “embedding query”, and “generation query”
We enforce a strict separation:

- **User query**: the user’s message (PII-redacted/sanitized if PII module is enabled)
- **Embedding query**: `optimizedQuery` (semantic) or user query (fallback), optionally augmented with `retrievalQueryHint`
- **Generation prompt query**: should be the **user query**, not the embedding query and not a “processedQuery” blob

This prevents:
- Confusing the LLM with internal DSL strings
- Polluting embeddings with conversation history
- Misleading debug output

### 4) Fix `RAGResponse.originalQuery` reporting (API/debug correctness)
Rule:
- `RAGResponse.originalQuery` must always be set to the (sanitized) **user query** for that turn.

We keep the embedding string visible via:
- `ragResponse.metadata.embeddingQuery`
- `ragResponse.metadata.optimizedQuery` (if needed)

### 5) Prompt updates (curated packs)
Update the curated prompts to reflect the semantic contract:

- `ai-curated-default` (compound) rule “optimizedQuery”:
  - Replace “exact system field names/operators” phrasing with:
    - “Produce a semantic, natural-language optimizedQuery for embeddings; do not output pseudo-DSL.”
- `ai-curated-default` (multi-step classify):
  - Add a single line clarifying `optimizedQuery` is natural language.

### 6) Deterministic validation (minimal)
We keep validation minimal and contract-based:
- If `optimizedQuery` is blank → ignore
- If `optimizedQuery` is too long (existing caps) → truncate/ignore
- If it contains multi-line prompt framing blocks → ignore (existing sanitization patterns should already handle most)

No domain heuristics.

---

## Implementation Notes (What would change)

### A) Prompt wording change
- File: `ai-infrastructure-module/curated/ai-curated-default/src/main/resources/prompts/intent-extraction/compound/v1/system.md`
  - Replace the current rule that instructs the model to produce DSL-like `optimizedQuery`.

### B) Orchestrator correctness
- File: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/IntentHandlingStep.java`
  - Ensure the embedding query used in `RAGRequest.query` is:
    - `intent.optimizedQuery` (semantic) if present, else the user query
    - plus `retrievalQueryHint` when present/valid (per ADR-0009)
  - Ensure `RAGResponse.originalQuery` is set from `pipelineContext.getOriginalQuery()` (user query), not from any “processedQuery/generationQuery” string.

### C) Observability
- Add debug metadata fields (non-user-facing) under `result.metadata`:
  - `ragQuery.userQuery`
  - `ragQuery.embeddingQuery`
  - `ragQuery.optimizedQueryProvided=true/false`
  - `ragQuery.retrievalQueryHintApplied=true/false`

---

## Acceptance Criteria
- The extractor produces semantic `optimizedQuery` (no pseudo-DSL) for typical ecommerce navigation queries.
- Retrieval uses the semantic embedding query (optionally with `retrievalQueryHint`).
- `ragResponse.originalQuery` matches the user’s (sanitized) input, not an internal synthesized query.
- Debug/metadata clearly exposes what was embedded vs what the user asked.

## Validation / Tests
- Prompt regression unit test:
  - Assert that the compound system prompt’s `optimizedQuery` rule explicitly says “natural language / no DSL”.
- Orchestrator unit test:
  - Given `optimizedQuery`, ensure the RAG embedding query uses it (and hint if enabled).
  - Ensure `ragResponse.originalQuery` equals the user query.

