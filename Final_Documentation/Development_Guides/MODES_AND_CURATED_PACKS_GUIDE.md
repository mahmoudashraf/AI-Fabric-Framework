# Modes + Curated Packs (v1 Guide)

This guide explains how to enable and use **modes** via **curated packs**, with a focus on the v1 behavior implemented in:
- Phase 0 (policy surface + capability bundles)
- Phase 1 (`navigator_deep` retrieval semantics)
- Phase 2 (`executor` tightening)

It is intentionally practical: **what to set**, **what to expect**, and **how to debug**.

---

## 1) The mental model

### Mode = “capability bundle” + “RAG budgets”
Per request, the server resolves an `OrchestrationPolicy` with:
- capabilities:
  - `actionsEnabled`
  - `retrievalEnabled`
  - `deepRetrievalEnabled`
  - `suggestionsEnabled`
- rag budgets:
  - `fanoutEnabled`
  - `maxSpaces`
  - `topKPerSpace`
  - `maxDocumentsReturnedToClient`
  - `maxDocumentsUsedForContext`
  - `maxContextChars`
  - `retrievalVectorSpacesAllowlist`

The pipeline is the authority:
- The LLM can *propose* intents.
- The server policy decides what is *allowed* (fail‑closed).

### Curated pack = “config + prompts”
Curated packs ship:
- a pack YAML (`ai-curated/packs/<pack>.yml`)
- optional prompt overlays (prompt templates)

They do not replace pipeline logic.

---

## 2) How the effective mode is chosen

Request inputs (from `OrchestrationContext`):
- `position` (recommended when you have UI positions)
- `mode` (optional)

Resolution order:
1) If `position` is routed in the active pack → routed mode wins.
2) Otherwise, if `mode` is provided and is configured → requested mode is used.
3) Otherwise, defaults apply.

---

## 3) Modes shipped in the `commerce` pack (Real App default)

The `commerce` curated pack (`ai-curated-commerce`) configures:

### `navigator` (unchanged)
Use for:
- product discovery / semantic search
- comparisons / summaries grounded in KB + (optional) attachments

Characteristics:
- actions enabled
- retrieval enabled
- deterministic “RAG + generate” behavior (when profile defaults to it)

### `navigator_deep` (deep retrieval)
Use for:
- “go deep” queries that need broader coverage:
  - alternatives
  - reviews / complaints
  - policies

Characteristics:
- actions disabled (fail‑closed if LLM proposes actions)
- retrieval enabled
- deep retrieval enabled (fanout allowed when configured)
- bounded multi‑space retrieval (caps enforced by budgets)

### `executor` (action-first)
Use for:
- orders / returns / refunds / subscriptions
- “do X” operational flows

Characteristics:
- action catalog is included in prompting
- KB overview is suppressed to avoid “browsing” confusion
- retrieval is optional, but if enabled it is **restricted**:
  - requires `retrievalVectorSpacesAllowlist` to be configured
  - requires `vectorSpace` to be explicitly set by the LLM when `requiresRetrieval=true`

### `cart_assistant`
Use for:
- cart-specific action flows (add to cart / checkout helpers)

---

## 4) Key configuration (app-level)

### Enable a curated pack
Example:

```yaml
ai:
  curated:
    pack: commerce
```

Maven:

```xml
<dependency>
  <groupId>com.ai.fabric</groupId>
  <artifactId>ai-curated-commerce</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Override a mode
Because pack YAML is loaded with low precedence, your app can override:

```yaml
ai:
  orchestration:
    modes:
      navigator_deep:
        rag:
          max-spaces: 4
```

---

## 5) Executor “restricted retrieval” (important)

If you enable retrieval in executor, you must also provide an allowlist:

```yaml
ai:
  orchestration:
    modes:
      executor:
        retrieval-enabled: true
        rag:
          retrieval-vector-spaces-allowlist:
            - policy
```

Behavior:
- If allowlist is missing/empty and the LLM requests retrieval → `CLARIFICATION_REQUIRED` with:
  - `reason=EXECUTOR_RETRIEVAL_ALLOWLIST_REQUIRED`
  - `suggestedMode=navigator`
- If allowlist exists but the LLM omits `vectorSpace` → `CLARIFICATION_REQUIRED` with:
  - `reason=VECTOR_SPACE_REQUIRED_IN_MODE`
  - `allowedVectorSpaces=[...]`
- If the LLM requests a denied space → `CLARIFICATION_REQUIRED` with:
  - `reason=VECTOR_SPACE_NOT_ALLOWED_IN_MODE`

---

## 6) Debug fields to look at (UI / logs)

In the orchestrator response `metadata.orchestrationPolicy`:
- `profile`
- `mode`
- `position`
- `modeSource` (`POSITION` vs `REQUEST_MODE`)
- `actionsEnabled`, `retrievalEnabled`, `deepRetrievalEnabled`, `suggestionsEnabled`

In `result.data.ragResponse.metadata` (when retrieval runs):
- `embeddingQuery`
- `optimizedQuery`
- fanout selection information (when multi‑space)

---

## 7) Real App usage (chat-capabilities-demo)

The demo app already accepts:
- `position`
- `mode`

Examples live in:
- `Real_Apps/chat-capabilities-demo/requests/demo.http`

Recommended patterns:
- normal browsing: send `position=landing|catalog|search`
- deep search: send `mode=navigator_deep` (omit routed `position`)
- executor: send `mode=executor` (omit routed `position`)

