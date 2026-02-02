# ADR-0009: Always Append `retrievalQueryHint` to RAG Retrieval Queries

## Status
Accepted

## Context
The intent extraction step can optionally emit a `metadata.retrievalQueryHint` string intended to improve retrieval recall (e.g., short identifiers, keywords, disambiguators).

Historically, the orchestration layer only applied this hint when **exactly one** intent required retrieval. This was meant to avoid ambiguous “global hint” usage during multi-intent requests.

In practice, this restriction reduced the usefulness of the hint in real user flows, especially when:
- multiple retrieval intents are extracted from one user message, and
- the hint is still helpful as a general disambiguator for the retrieval turn.

## Decision
When `MultiIntentResponse.metadata.retrievalQueryHint` is present and passes basic safety validation, the orchestrator **always appends it** to the retrieval query used for RAG (for every retrieval call in that pipeline execution), and sets `retrievalQueryHintApplied=true` in request metadata.

Safety validation remains in place to reduce accidental leakage and prompt/format injection:
- max length (200 chars)
- no newlines
- no `@` character
- no consecutive whitespace

Curated intent-extraction prompt guidance is updated to remove the “only when exactly one intent uses retrieval” restriction, while keeping the “never include emails/phones/addresses” rule.

## Consequences
### Positive
- Increases the chances that retrieval uses high-signal identifiers/keywords, improving recall and reducing “wrong domain” hits.
- Removes a non-obvious behavioral cliff: multi-intent requests no longer silently lose retrieval-hint benefits.
- Keeps behavior deterministic and transparent via `retrievalQueryHintApplied`.

### Negative / Trade-offs
- The hint is global (response-level metadata), not per-intent; appending it to every retrieval call can sometimes reduce precision when intents span different domains.
- The hint is LLM-produced; it may still be inaccurate or overly broad, even if it passes basic safety checks.
- This decision does not solve cross-vector-space “relation scoping” (e.g., reviews constrained to pinned products); that requires explicit metadata filtering/link semantics.

## Alternatives Considered
1. **Keep “exactly one retrieval intent” gating**
   - Safer for multi-intent ambiguity, but reduced usefulness in common flows.
2. **Per-intent retrieval hints**
   - Cleaner semantics, but requires an output contract change and prompt + parsing updates.
3. **Append hint only when optimizedQuery is empty**
   - Helps some cases, but still produces confusing “why did my hint disappear?” behavior.

## Implementation Notes
- Orchestrator logic lives in:
  - `ai-infrastructure-module/ai-infrastructure-core/.../IntentHandlingStep.java` (`applyRetrievalQueryHint(...)` / `resolveValidRetrievalQueryHint(...)`).
- Curated prompt guidance updated under:
  - `ai-infrastructure-module/curated/*/src/main/resources/prompts/intent-extraction/...`
