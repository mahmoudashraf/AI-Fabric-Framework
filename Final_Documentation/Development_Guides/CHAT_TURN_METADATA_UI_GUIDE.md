# Chat Turn Metadata — UI Developer Guide

This guide explains the **per-turn metadata** returned by the Chat Capabilities API so UIs can render richer chat history (actions vs RAG answers, working set “sources”, etc.) without brittle parsing of assistant text.

It applies to:
- `Real_Apps/chat-capabilities-demo`

---

## 1) What is “turn metadata”?

Each stored conversation turn has:
- `userQuery`
- `aiResponse`
- `timestamp`
- `metadata` (a bounded JSON object; server-produced)
- `uiMetadata` (a bounded JSON object; client-provided, UI-only)

The `metadata` object is written by the **chat-session module** during orchestration (server-side) and is intended for:
- UI rendering (badges, collapsible details, sources, etc.)
- debugging (show how a turn was produced)

The `uiMetadata` object is **persisted for UI rendering only**:
- It is never injected into LLM prompts.
- It is never used for action execution.
- It is sanitized and bounded by the backend (scalar-only best effort).

---

## 2) Prerequisites (so turns are stored)

1) Enable chat sessions in the backend:

```yml
ai:
  chat:
    enabled: true
```

2) Send a stable `conversationId` and a stable owner identifier:
- For authenticated flows: send `userId`
- For anonymous flows: send `sessionId` (the backend uses it as the owner when `userId` is absent)

3) Call the orchestrator via:
- `POST /api/chat/query`

---

## 3) API: where metadata appears

### Fetch conversations
- `GET /api/chat/conversations?ownerId=<owner>`
- `GET /api/chat/conversations/<conversationId>?ownerId=<owner>`

`turns[].metadata` is returned by the demo backend at:
- `Real_Apps/chat-capabilities-demo/src/main/java/com/ai/fabric/realapps/chat/web/ChatController.java`

Example (shape):

```json
{
  "id": "chat-...",
  "ownerId": "demo-user",
  "turns": [
    {
      "timestamp": "2026-02-01T12:10:34.373",
      "userQuery": "list my active orders",
      "aiResponse": "No active orders found.",
      "metadata": { "...": "..." },
      "uiMetadata": { "...": "..." }
    }
  ]
}
```

---

## 4) Contract: reserved keys (current)

Turn metadata is a **map**, but AI Fabric reserves a small set of keys (prefixed with `_`) for stable UI behavior.

### 4.1 `_resultType` (always present)
Type: `string`

`_resultType` mirrors the orchestrator `OrchestrationResultType` for the turn, for example:
- `INFORMATION_PROVIDED`
- `ACTION_EXECUTED`
- `CONFIRMATION_REQUIRED`
- `CLARIFICATION_REQUIRED`
- `ERROR`

Recommended UI usage:
- Render a badge per message (e.g., “Action”, “Info”, “Needs confirmation”, “Error”).

### 4.2 Action keys (only for action outcomes)
When the turn corresponds to an action flow (executed/denied/error), the following may exist:

- `_action`: `string` (action name)
- `_actionSuccess`: `boolean`
- `_actionRefs`: `object` (sanitized scalar refs extracted from the action payload)

Important notes:
- `_actionRefs` is intentionally bounded/sanitized (no free-form text, no emails, no whitespace-heavy strings) so it’s safe to render and safe to reuse in prompts.
- UI should treat `_actionRefs` as “nice-to-display” (orderNumber, sku, orderId, etc.), not as a substitute for a real domain API payload.

### 4.3 `_workingSet` (only for RAG-backed INFORMATION)
When the turn includes a successful RAG response, the chat-session module stores a small “working set” summary:

`_workingSet` shape (best-effort):

```json
{
  "vectorSpacesUsed": ["product"],
  "topDocumentRefs": [
    { "id": "30", "vectorSpace": "product", "score": 0.77, "metadata": { "sku": "SKU-BOS-20002" } }
  ],
  "documentsCount": 5
}
```

Recommended UI usage:
- Render a “Sources” collapsible panel for the turn using `topDocumentRefs`.
- If your UI already has those entities in memory (e.g., the catalog list), you can highlight the referenced items.

Important notes:
- `topDocumentRefs` is a compact reference list; it is not the full document content.
- Metadata inside `topDocumentRefs[].metadata` is bounded and sanitized.

---

## 5) UI patterns (recommended)

### 5.1 Message card layout
- Use `_resultType` to choose a card style:
  - `ACTION_EXECUTED` → “Action” card + show `_actionRefs` as key/value chips
  - `INFORMATION_PROVIDED` → “Info” card + optional “Sources” drawer from `_workingSet`
  - `CONFIRMATION_REQUIRED` / `CLARIFICATION_REQUIRED` → show prompt + render missing params / confirmation UI

### 5.2 Sources drawer (RAG)
- Show each `topDocumentRefs[]` as a clickable UI element.
- Display “best” identifiers first (prefer `metadata.sku`, else `id`).

### 5.3 Debug view (optional)
For internal debugging UI, show:
- `_resultType`
- `_action`, `_actionSuccess`
- `_workingSet.vectorSpacesUsed`

---

## 6) Storage + safety (what you can assume)

Turn metadata (`turns[].metadata`) is:
- persisted server-side as JSON (`ChatTurn.turnMetadata`)
- bounded and sanitized by the backend
- safe to ignore (UI should be resilient to missing keys)

It is not:
- guaranteed to contain domain objects
- a stable API for business logic (use domain APIs for that)

UI metadata (`turns[].uiMetadata`) is:
- persisted server-side as JSON (`ChatTurn.uiMetadata`)
- client-provided and treated as untrusted input
- sanitized and bounded by the backend (scalar-only best effort)

It is not:
- included in LLM prompt context
- intended to drive orchestration behavior

---

## 7) Related implementation references

- Turn storage: `ai-infrastructure-module/ai-infrastructure-chat-session/src/main/java/com/ai/infrastructure/chat/domain/ChatTurn.java`
- Turn metadata writing: `ai-infrastructure-module/ai-infrastructure-chat-session/src/main/java/com/ai/infrastructure/chat/pipeline/ConversationRecordingStep.java`
- UI response mapping: `Real_Apps/chat-capabilities-demo/src/main/java/com/ai/fabric/realapps/chat/web/ChatController.java`
