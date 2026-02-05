# LLM Prompting Standardization (Provider‑Native Multi‑Message) — Change Plan

## Status
Implemented

**Implemented in codebase (core + providers).** The runtime now uses provider-native multi-message prompting for intent extraction (and supports it generically for any `AIGenerationRequest`), and no longer mixes conversation scaffolding into retrieval queries.

## Trigger (observed failures)
We repeatedly see:
- **RAG embedding queries polluted** with conversation scaffolding (e.g., `Conversation History:`, `---BEGIN HISTORY---`, `User:`/`Assistant:`).
- **LLM confusion** where it treats history/scaffolding as part of the current user request.
- **Inconsistent behavior** depending on truncation (sometimes history markers are present, sometimes cut), causing the system to silently degrade to “single prompt string” prompting.

Example symptoms:
- Logs show `Optimized: FOR EMBEDDINGS Conversation History: ...`
- LLM outputs “no information in context” even when RAG returned relevant docs (because the retrieved context does not match the user’s true intent due to polluted retrieval query).

## Root cause
We currently overload one internal string (`PipelineContext.effectiveQuery`) to represent **three different concerns**:
1) **Pipeline carrier** (history + query + attachments in one string)
2) **LLM input**
3) **RAG embedding input**

This “single-string everything” design causes:
- Retrieval pollution (embeddings contain non‑query text).
- Prompt confusion (models confuse history and current message).
- Fragility under truncation (markers get cut → multi‑turn parsing fails → history goes back into the user prompt or is lost).

## Goals
1) **Always use provider‑native multi‑message chat prompting** for LLM calls:
   - `system`: rules
   - `messages[]`: prior turns (user/assistant)
   - `user`: current user message (plus bounded attachment context)
2) **Never embed conversation history scaffolding into RAG queries.**
3) Keep **attachments / pinned targets** as *user context*, not system authority.
4) **Greenfield policy:** if a provider cannot support this standard, **we do not support that provider module**.

## Non‑goals
- Backward compatibility with legacy “single prompt string” providers/paths.
- Continuing to rely on `---BEGIN HISTORY---` / `---BEGIN QUERY---` markers as a runtime contract for prompting.

---

## Proposed solution (production‑ready)

### A) Introduce a typed “LLM chat input” boundary (core)
Define a single internal representation for LLM calls:
- `systemPrompt: String`
- `historyMessages: List<AIChatMessage>` (role=user|assistant, bounded)
- `currentUserMessage: String` (user query + bounded attachment context)

**Rule:** LLM providers receive `historyMessages` via their native chat format; `currentUserMessage` is the final user turn.

This boundary becomes the *only* way to send a request to an LLM (intent extraction, generation, suggestions, etc.).

### B) Stop using `effectiveQuery` as an LLM input contract
Do **not** use `PipelineContext.processedQuery`/`effectiveQuery` as an LLM input contract.

Greenfield stance:
- Treat any “carrier string” that mixes history/attachments/query as **legacy** and remove it from the execution path.
- Do **not** log such a carrier as “user input”. For debugging, log only from structured types (system prompt, history messages, current user message, retrieval query).

LLM calls must be built from structured fields:
- `PipelineContext.originalQuery` (the real current user query)
- `conversation history` (structured, bounded)
- `attachmentsNormalized` / `resolvedTargets` (structured)

### C) Conversation history becomes structured messages (not a string blob)
Greenfield stance: store chat history in a structured form, not as a text blob.

Change conversation storage + enrichment to use typed turns/messages end-to-end:
- Persist turns as a structured model (e.g., `Turn {timestamp, userText, assistantText, metadata}`) or directly as `List<AIChatMessage>`.
- `ConversationEnrichmentStep` loads structured turns and builds `historyMessages: List<AIChatMessage>` directly.
- Do **not** serialize history back into marker-based text (`---BEGIN HISTORY---`, `User:`, etc.).

Truncation becomes safe:
- We drop oldest messages to respect limits (window size / max chars),
- never cut marker strings mid-way.

### D) Attachments/pinned targets are injected as bounded **user context**
Attachments (or resolved targets) should be injected into the **current user message**, not into history, and not into system:

Recommended rendering (bounded):
```
PINNED TARGETS (user-selected candidates; authoritative):
1) vectorSpace=product id=30 metadata={sku=..., price=...} contentText="..."

User message:
compare prices
```

**Important:** This context is always treated as **user-provided**, so the model cannot override system rules.

### E) RAG retrieval query uses the real user query (+ hint), never history/attachments scaffolding
RAG embedding input must be derived from:
- `PipelineContext.originalQuery` (or the normalized user query)
- optional `intentMetadata.retrievalQueryHint` (see ADR‑0009)

Hard rule:
- Do **not** embed `Conversation History:` or `ATTACHMENTS (...)` blocks.
- Do **not** embed multi‑line prompt scaffolding.

### F) Provider enforcement (greenfield)
Add a startup validation rule:
- If an enabled provider module cannot accept multi‑message chat input, fail fast at startup with a clear error:
  - “Provider X does not support multi-message chat prompting; this framework requires provider-native chat prompting.”

This avoids silent degradation into legacy behavior.

---

## Implementation steps (high level)
1) **Core DTOs**
   - Ensure `AIGenerationRequest` supports `messages[]` (history) + `prompt` (current user turn).
   - Standardize roles (`user`, `assistant`, `system`).
2) **Pipeline / context**
   - Add typed fields to `PipelineContext` or `OrchestrationContext` for:
     - `historyMessages`
     - `currentUserQuery` (already `originalQuery`)
     - `attachmentsContext` (rendered once, bounded)
3) **ConversationEnrichmentStep**
   - Stop serializing history into `processedQuery` for LLM purposes.
   - Populate typed history messages instead.
4) **IntentExtractionStep + all LLM callers**
   - Build `AIGenerationRequest` using:
     - `systemPrompt`
     - `messages` (history)
     - `prompt` (attachments context + original user query)
5) **RAG**
   - Ensure embedding query = original user query (+ retrievalQueryHint) only.
   - Add assertions/guards to prevent scaffolding leakage.
6) **Providers**
   - Map `systemPrompt + messages + prompt` into provider-native chat formats.
   - Remove/disable any legacy “single string completion” provider paths.
7) **Docs**
   - Update developer guide: “How prompting works (multi-message only)”.
   - Document provider requirement clearly.

---

## Observability / Debugging (required)
Add debug metadata so we can prove which path was taken:
- `llmPrompting.standard = MULTI_MESSAGE`
- `llmPrompting.historyMessagesCount`
- `llmPrompting.attachmentsInjectedCount`
- `llmPrompting.currentUserMessageChars`
- `llmPrompting.carrierLogged = true|false` (optional)
- `rag.embeddingQueryContainsScaffolding = false` (guarded check)
- `rag.embeddingQuerySource = ORIGINAL_QUERY(+HINT)`

Logging rule (important):
- Never log “User’s question is: (effectiveQuery)” if it contains internal scaffolding.
- When logging is needed, render a carrier **from structured types**:
  - `systemPrompt` (bounded snippet)
  - `historyMessages` (bounded snippets)
  - `currentUserMessage` (bounded snippet)
  - `rag.embeddingQuery` (bounded snippet)

---

## Test plan
### Unit tests
- History parsing → `messages[]` correct order and roles.
- Current prompt contains pinned targets block + user query, but **not** history scaffolding.
- Retrieval query builder rejects/strips scaffolding markers.

### Integration tests (real-api and local)
- Ensure OpenAI (and other providers) requests contain:
  - system message
  - N history messages
  - 1 current user message
- Ensure embeddings call text is only user query (+ hint), never history.

---

## Acceptance criteria
1) No embeddings request contains:
   - `Conversation History:`
   - `---BEGIN HISTORY---`
   - `User:` / `Assistant:`
2) Providers always receive multi-message chat format (history is not packed into the user prompt).
3) Truncation never breaks multi-message parsing (history is bounded by dropping old turns).
4) Providers lacking multi-message support are not supported (fail fast).
