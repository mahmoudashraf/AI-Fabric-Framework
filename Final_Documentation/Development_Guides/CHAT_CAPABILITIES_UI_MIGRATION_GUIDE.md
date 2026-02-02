# Chat Capabilities Demo — UI Migration Guide (V5 request contract)

This guide documents the **request payload** and **position list** for the Real App:
- `Real_Apps/chat-capabilities-demo`

It is intended for frontend/UI clients calling:
- `POST /api/chat/query`

---

## 1) Endpoint

### `POST /api/chat/query`

**Request body (JSON)**

```json
{
  "query": "string (required)",
  "userId": "string (recommended for persisted conversations)",
  "sessionId": "string (recommended)",
  "conversationId": "string (recommended; stable per chat thread)",
  "position": "string (recommended; drives orchestration mode via routing)",
  "mode": "string (optional; only used if position is missing or not routed)",
  "uiMetadata": { "anyScalar": "..." },
  "attachments": [
    {
      "id": "string (required)",
      "vectorSpace": "string (recommended; e.g. \"product\")",
      "contentText": "string (optional; bounded, best-effort grounding text)",
      "metadata": { "anyScalar": "..." },
      "source": "string (optional)",
      "url": "string (optional)",
      "imageUrl": "string (optional)"
    }
  ],
  "activeAttachmentIds": ["string (ids from attachments)"]
}
```

**Response body**

The response contract stays the same shape:

```json
{
  "success": true,
  "message": null,
  "conversationId": "chat-...",
  "userId": "demo-user",
  "sessionId": "demo-session",
  "result": { "... OrchestrationResult ..." }
}
```

---

## 2) Positions to use (Chat Capabilities Demo)

This app uses the curated pack:
- `ai.curated.pack: commerce`

That pack defines **position routing**:

| UI position | Send `"position"` | Routed mode | Intended behavior |
|---|---|---|---|
| Landing / catalog browsing | `"landing"` | `navigator` | RAG-first discovery (deterministic retrieval + generation) |
| Cart / checkout flows | `"cart"` | `cart_assistant` | Action-oriented flows (purchase order, add-to-cart, etc.) |

Notes:
- **Position routing wins over `mode`.** If you send both, the routed mode for the position is applied.
- If you send a `position` that is not configured, the request falls back to normal mode resolution (unless the app enables strict routing).

Temporary demo behavior:
- The current demo backend forces `.mode("navigator")` server-side (see `Real_Apps/chat-capabilities-demo/src/main/java/com/ai/fabric/realapps/chat/web/ChatController.java`).
- `position` / `mode` are accepted in the request contract but are not used until multi-mode UX is re-enabled.

---

## 3) When to send attachments

Send `attachments` + `activeAttachmentIds` when the user is interacting with a **specific UI object** (e.g., a selected product card) and the user’s message is ambiguous:
- “Add it to my cart”
- “Buy this”
- “Compare these”

This enables deterministic resolution without relying on the LLM to “guess” the target.

Recommended UI pattern:
- Include attachments for everything visible/selected in the UI list/cards.
- Set `activeAttachmentIds` to what the user currently selected/focused on.
- Set `vectorSpace` to the entity type configured in your `ai-entity-config.yml` (for the demo catalog: `product`) when known.
- Include `contentText` when available so the LLM can answer from pinned context without extra retrieval.

---

## 4) Conversation persistence (UI expectations)

If you want the backend to store and later return chat history via `/api/chat/conversations`:
- Send a stable `conversationId` for the thread (example: `chat-<userId>` or a generated UUID stored client-side).
- Send `userId`, and use `ownerId=<userId>` when calling conversation history endpoints.

---

## 5) Examples (copy/paste)

### A) Landing (RAG) query

```json
{
  "userId": "user-1",
  "sessionId": "user-1-session",
  "conversationId": "chat-user-1",
  "position": "landing",
  "query": "I'm looking for wireless headphones under $250. Recommend options from the catalog."
}
```

### B) Cart (action) request

```json
{
  "userId": "user-1",
  "sessionId": "user-1-session",
  "conversationId": "chat-user-1",
  "position": "cart",
  "query": "Create a purchase order for sku SKU-0001 quantity 2, ship to 1 Market St, SF, email alice@example.com."
}
```

### C) Cart (action) using attachments (deterministic target)

```json
{
  "userId": "user-1",
  "sessionId": "user-1-session",
  "conversationId": "chat-user-1",
  "position": "cart",
  "query": "Add it to my cart",
  "attachments": [
    {
      "id": "SKU-0002",
      "vectorSpace": "product",
      "contentText": "Compact Mechanical Keyboard - 65% mechanical keyboard with Bluetooth connectivity.",
      "metadata": { "sku": "SKU-0002", "category": "Keyboards" },
      "source": "ui-card"
    }
  ],
  "activeAttachmentIds": ["SKU-0002"]
}
```

For more runnable examples, see:
- `Real_Apps/chat-capabilities-demo/requests/demo.http`

---

## 6) Related endpoints (UI)

- `GET /api/chat/conversations?ownerId=<userId>`
- `GET /api/chat/conversations/<conversationId>?ownerId=<userId>`
- `DELETE /api/chat/conversations/<conversationId>?ownerId=<userId>`

Conversation history response includes per-turn UI metadata:
- `turns[].metadata` (server-produced; see `Final_Documentation/Development_Guides/CHAT_TURN_METADATA_UI_GUIDE.md`)
- `turns[].uiMetadata` (client-provided; see `Final_Documentation/Development_Guides/CHAT_TURN_METADATA_UI_GUIDE.md`)

---

## 7) CORS + Deployment notes

If your UI is hosted on a different domain than the API:
- Set `CORS_ALLOWED_ORIGINS` (exact origin) or `CORS_ALLOWED_ORIGIN_PATTERNS` (patterns) in the backend environment.

Local default port:
- `http://localhost:8096` (the app uses `PORT` when deployed)
