# 010.7 Runtime Query-Once Endpoint Contract Plan

Date: 2026-05-22

Status: implemented in runtime controller, shared chat-session pipeline, and focused/broader runtime tests on 2026-05-22. Live deployment verification pending until the managed runtime is redeployed with this code.

Parent plans:

- `010_GTM_AND_PARTNER_PORTAL_LAUNCH_READINESS.md`
- `010_5_LOOMAI_CANONICAL_RUNTIME_BRIDGE_CONTRACT_STANDARDIZATION_PLAN.md`

Related handovers:

- `/Users/mahmoudashraf/Downloads/Projects/ProdUS/docs/LOOMAI_STAGING_DEPLOYMENT_HANDOVER.md`
- `Final_Documentation/Development_Guides/PRODUS_LOOMAI_STAGING_DEPLOYMENT_DEV_GUIDE.md`

## 1. Decision Under Review

Add a dedicated one-time AI answer endpoint:

```http
POST /api/chat/me/query-once
```

Keep the existing conversational endpoint:

```http
POST /api/chat/me/query
```

Greenfield contract rule:

- `/api/chat/me/query-once` never persists conversation turns.
- `/api/chat/me/query` is the persistent conversational endpoint when conversation storage is enabled.
- Product backends choose the endpoint based on UX intent, not by relying on generated ephemeral conversation ids.
- Runtime propagates this as internal orchestration metadata `queryPersistenceMode=NEVER_PERSIST` for `/query-once` and `queryPersistenceMode=PERSIST_IF_AVAILABLE` for `/query`; clients do not send a `persistConversation` flag.
- The shared chat-session pipeline honors `NEVER_PERSIST` by skipping persisted conversation history loading/session auto-creation and by skipping conversation turn recording.

## 2. Current Runtime State

Current runtime exposes:

- `POST /api/chat/me/query`
- `POST /api/chat/me/suggestions`
- `GET /api/chat/me/conversations`
- `GET /api/chat/me/conversations/{conversationId}`
- `DELETE /api/chat/me/conversations/{conversationId}`

Current code path:

- `ChatRuntimeController.query()` delegates to `handleQuery(...)`.
- `handleQuery(...)` executes orchestration and returns a canonical `ChatQueryResponse`.
- The controller does not currently make the persistence contract explicit.
- Conversation history endpoints are separate and gated by `chat:conversations`.

This means one-time callers can technically call `/api/chat/me/query`, but the storage semantics are not obvious from the API. A generated ephemeral `conversationId` is not a clean non-persistence contract.

## 3. Goal

Provide a clear, privacy-first API contract for one-time AI answers used by embedded product screens, workflow helpers, productization assistants, and backend utility calls.

The endpoint must:

- reuse the same orchestration pipeline as `/api/chat/me/query`.
- return the same canonical response shape.
- use the same private/public runtime auth model.
- never write user or assistant turns to conversation storage.
- not expose a product-specific or ProdUS-specific contract.
- not break Shopify Companion or existing ProdUS staging flows.

## 4. Non-Goals

Do not:

- add product-specific endpoints.
- add a second answer response shape.
- replace `/api/chat/me/query`.
- remove conversation history APIs.
- add a `persistConversation` flag as the primary greenfield contract.
- rely on text matching, client conventions, or generated ephemeral ids to control persistence.

## 5. API Contract

### One-Time Query

```http
POST /api/chat/me/query-once
Content-Type: application/json
X-AIFABRIC-RUNTIME-API-KEY: <runtime-api-key>
X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer <rpa1-token>
```

Request:

```json
{
  "query": "Which package template is appropriate for launch readiness?",
  "conversationId": "optional-correlation-id",
  "mode": "support_assistant",
  "position": "productization",
  "context": {
    "pageType": "owner-product-workspace",
    "actorRole": "PRODUCT_OWNER"
  },
  "attachments": []
}
```

Rules:

- `query` is required.
- `conversationId` is optional and used only for trace/correlation in `query-once`.
- Runtime may generate a response `conversationId` if omitted, but it must not create a persisted conversation.
- Runtime must not use a query-once `conversationId` to load persisted chat memory; treat it as correlation only.
- `mode`, `position`, `context`, `attachments`, and `promptPreview` follow the same validation rules as `/api/chat/me/query`.
- The endpoint must reject unsupported legacy fields exactly like `/api/chat/me/query`.
- If a future request DTO adds `persistConversation`, `/api/chat/me/query-once` must reject `persistConversation=true`.

Response:

```json
{
  "success": true,
  "type": "INFORMATION_PROVIDED",
  "answer": "Safe user-facing answer.",
  "safeSummary": "Safe user-facing answer.",
  "conversationId": "optional-correlation-id",
  "mode": "support_assistant",
  "position": "productization",
  "sources": [],
  "actions": [],
  "suggestions": [],
  "fallbackReason": null,
  "providerRequestId": "rag-...",
  "metadata": {}
}
```

Response shape must be identical to `/api/chat/me/query`.

### Conversational Query

```http
POST /api/chat/me/query
```

Rules:

- This remains the chat endpoint.
- It may persist turns when conversation storage is configured.
- It should be the only answer endpoint whose semantics allow conversation history writes.
- Product UIs that show chat history should use this endpoint and pass a stable `conversationId`.

### Suggestions

```http
POST /api/chat/me/suggestions
```

Rules:

- This remains a suggestion generator only.
- It is not a one-time answer endpoint.
- It does not replace `query-once`.

## 6. Auth And Scopes

Use the existing query scope:

```text
chat:query
```

Rationale:

- `query-once` performs the same answer generation operation as `query`.
- A new `chat:query-once` scope would increase auth surface without meaningful security separation at this stage.
- Conversation history access remains separately gated by `chat:conversations`.

If rate limiting later needs separate budgets, implement endpoint-aware rate limits rather than a new auth scope.

## 7. Implementation Plan

### Runtime

Files:

- `ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/ChatRuntimeController.java`
- `ai-infrastructure-module/ai-fabric-runtime/src/test/java/com/ai/fabric/runtime/...`

Steps:

1. Add:

```java
@PostMapping("/me/query-once")
public ResponseEntity<ChatQueryResponse> queryOnce(...)
```

2. Reuse `handleQuery(...)` through an explicit execution option:

```java
handleQuery(request, servletRequest, "/api/chat/me/query-once", QueryPersistenceMode.NEVER_PERSIST)
```

3. Update the existing query endpoint to call:

```java
handleQuery(request, servletRequest, "/api/chat/me/query", QueryPersistenceMode.PERSIST_IF_AVAILABLE)
```

4. Keep orchestration behavior identical between endpoints.

5. Ensure no conversation gateway write path is called for `query-once`, even if conversation persistence is added later.

6. Preserve current canonical response fields.

### Platform Bridge And Product Services

No immediate product service code change is required for compatibility.

Future usage:

- ProdUS page helper calls should use `/api/chat/me/query-once`.
- ProdUS chat panel should use `/api/chat/me/query`.
- Shopify Companion chat/max-mode should continue using `/api/chat/me/query`.
- Backend utility calls and smoke checks that should not create user history should use `/api/chat/me/query-once`.

### Documentation

Update:

- `Final_Documentation/Development_Guides/PRIVATE_RUNTIME_CUSTOMER_INTEGRATION_GUIDE.md`
- `Final_Documentation/Development_Guides/PRODUS_LOOMAI_STAGING_DEPLOYMENT_DEV_GUIDE.md`
- `/Users/mahmoudashraf/Downloads/Projects/ProdUS/docs/LOOMAI_STAGING_DEPLOYMENT_HANDOVER.md`

## 8. Test Plan

Runtime tests:

- `POST /api/chat/me/query-once` returns the same canonical response shape as `/api/chat/me/query`.
- `query-once` requires `chat:query`.
- `query-once` rejects unexpected legacy fields.
- `query-once` accepts `mode`, `position`, `context`, and `attachments`.
- `query-once` does not call conversation write/persistence APIs.
- `query` keeps existing behavior.
- Conversation list/get/delete endpoints remain unchanged.

Integration/live tests:

- ProdUS direct private runtime `query-once` call returns a grounded answer.
- ProdUS direct private runtime `query` call still returns a grounded answer.
- Shopify Companion query path remains unchanged.
- Runtime admin auth overview lists `/api/chat/me/query-once` as a supported route if route listing is maintained.

Suggested commands:

```bash
mvn -f ai-infrastructure-module/pom.xml -q -pl ai-fabric-runtime -am test
mvn -f Platfrom/backend/pom.xml -q test
```

Live smoke:

```bash
curl -fsS \
  -H "Content-Type: application/json" \
  -H "X-AIFABRIC-RUNTIME-API-KEY: ${LOOMAI_RUNTIME_API_KEY}" \
  -H "X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer ${LOOMAI_PRIVATE_ASSERTION}" \
  -X POST \
  "http://dep-7706fafb.46.224.145.148.sslip.io/api/chat/me/query-once" \
  --data '{
    "query": "Which package template is appropriate for launch readiness?",
    "mode": "support_assistant",
    "position": "productization",
    "context": {"pageType": "owner-product-workspace"}
  }'
```

## 9. Acceptance Gates

- `query-once` is live on managed runtime deployments.
- `query-once` and `query` share the same answer response contract.
- `query-once` has no conversation persistence side effects.
- Existing Shopify Companion chat behavior is unchanged.
- Existing ProdUS direct runtime chat behavior is unchanged.
- ProdUS can choose `query-once` for one-time page helpers without introducing local fallback behavior.
- Docs clearly tell integrators which endpoint to use.

## 10. Review Questions

1. Should `query-once` require only `chat:query`, or should we introduce `chat:query-once` later for strict tenant budgets?
2. Should `query-once` allow `conversationId` as a correlation id, or should it use only runtime-generated `providerRequestId`?
3. Should response `conversationId` be omitted for `query-once` when the caller does not provide one, or should runtime still generate one for shape consistency?
4. Should Platform consumer bridge expose a matching public `/bridge/chat/query-once` path now, or wait until ProdUS needs bridge fallback for one-time calls?

## 11. Recommendation

Proceed with `POST /api/chat/me/query-once`.

Keep `/api/chat/me/query` for conversational chat and `/api/chat/me/suggestions` for suggestion generation.

Do not add `persistConversation` as the primary greenfield contract. A separate endpoint is clearer for privacy, product integration, audit, and future rate limiting.
