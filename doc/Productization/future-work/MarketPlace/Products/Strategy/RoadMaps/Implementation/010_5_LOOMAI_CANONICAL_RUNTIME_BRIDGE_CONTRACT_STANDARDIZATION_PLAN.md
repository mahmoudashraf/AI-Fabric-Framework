# 010.5 LoomAI Canonical Runtime And Bridge Contract Standardization Plan

Date: 2026-05-19

Status: implemented in code; staging live verification pending deployment/release gate.

Parent plans:

- `010_GTM_AND_PARTNER_PORTAL_LAUNCH_READINESS.md`
- `010_2_SHOPIFY_COMPANION_TWO_MODE_SURFACE_SIMPLIFICATION.md`
- `010_3_SHOPIFY_COMPANION_QUERY_SPEED_ACCURACY_RELIABILITY_OPTIMIZATION_PLAN.md`
- `010_4_SHOPIFY_COMPANION_INDEXING_ARCHITECTURE_CLEANUP_PLAN.md`

Related downstream handover:

- `/Users/mahmoudashraf/Downloads/Projects/ProdUS/docs/LOOMAI_STAGING_DEPLOYMENT_HANDOVER.md`

Implemented code paths:

- Runtime `/api/chat/me/query` now serializes the flat canonical response while keeping orchestration internals server-side.
- Platform consumer bridge rejects old public request fields and forwards canonical `context`.
- Shopify storefront surfaces send `context`, and Shopify Bridge returns the flat canonical storefront response.
- Max Mode widget consumes top-level `answer`, `safeSummary`, `sources`, and `actions`.
- ProdUS backend sends `query`, `conversationId`, and `context` to LoomAI.

## 1. Goal

Make the canonical LoomAI chat contract a platform-level runtime and bridge contract, not a product-specific adapter convention.

Both Shopify Companion and ProdUS are greenfield enough to use the same contract now. Product-specific context is allowed, but the envelope must be the same.

The canonical contract must apply to:

- direct runtime chat calls.
- Platform consumer bridge chat calls.
- Platform PoC/widget proxy chat calls.
- Shopify Bridge storefront chat calls.
- ProdUS backend-mediated chat calls.
- future generic product services using LoomAI deployments.

## 2. Why This Matters

Current platform behavior has several contract shapes:

- Runtime request already uses `query` and `conversationId`, but response still includes `message`, `sessionId`, and nested `result`.
- Platform consumer bridge forwards runtime response shapes directly.
- Shopify Bridge accepts Shopify-specific `storefrontContext` and returns/reads nested `result.sanitizedPayload`.
- ProdUS currently expects its own `message` / `sessionId` DTO shape.

Because these products are greenfield, standardize now instead of carrying compatibility aliases.

## 3. Canonical Request

All product chat surfaces must send:

```json
{
  "query": "What is blocking this product from launch?",
  "conversationId": "produs-session-123",
  "mode": "support_assistant",
  "position": "productization",
  "context": {
    "product": "produs",
    "pageType": "owner-product-workspace",
    "actorRole": "PRODUCT_OWNER",
    "productId": "<uuid>",
    "packageId": "<uuid>",
    "workspaceId": "<uuid>",
    "findingId": "<uuid>"
  },
  "attachments": []
}
```

Rules:

- `query` is required user text.
- `conversationId` is the public chat thread id.
- `mode` is optional product-selected conversation mode.
- `position` is optional product UI/surface position.
- `context` is optional but must be safe, product-owned, and backend-authorized before reaching runtime.
- `attachments` remains the generic evidence/context carrier for documents, products, pages, and owned-resource summaries.
- Do not use `message` as a request field.
- Do not use `sessionId` as a public chat request field.
- Do not use Shopify-only `storefrontContext` as a public chat request field.

### Shopify Context

Shopify must use the same `context` envelope:

```json
{
  "query": "Add ski wax to my cart",
  "conversationId": "shopify-shopper-conv-123",
  "mode": "executor",
  "position": "cart",
  "context": {
    "product": "shopify-companion",
    "shopDomain": "shopping-companion-test.myshopify.com",
    "pageType": "cart",
    "pageTitle": "Cart",
    "shopifySurfaceEntry": "launcher",
    "shopifyPageModeGroup": "cart",
    "cartId": "gid://shopify/Cart/...",
    "productId": "gid://shopify/Product/..."
  },
  "attachments": []
}
```

Shopify-specific keys may remain inside `context`, but the top-level field must be `context`, not `storefrontContext`.

### ProdUS Context

ProdUS must use the same `context` envelope:

```json
{
  "query": "What is blocking this product from launch?",
  "conversationId": "produs-session-123",
  "mode": "support_assistant",
  "position": "productization",
  "context": {
    "product": "produs",
    "pageType": "owner-product-workspace",
    "actorRole": "PRODUCT_OWNER",
    "productId": "<uuid>",
    "packageId": "<uuid>"
  },
  "attachments": []
}
```

## 4. Canonical Response

All product chat surfaces must receive:

```json
{
  "success": true,
  "type": "INFORMATION_PROVIDED",
  "answer": "Safe user-facing answer.",
  "safeSummary": "Safe user-facing answer.",
  "conversationId": "produs-session-123",
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

Rules:

- `answer` is the primary display text.
- `safeSummary` is the safe answer summary and may mirror `answer`.
- `type` is the normalized outcome type, for example `INFORMATION_PROVIDED`, `CONFIRMATION_REQUIRED`, `ACTION_EXECUTED`, `CLARIFICATION_NEEDED`, `OWNED_RESOURCE_AUTH_REQUIRED`, or `OWNED_RESOURCE_NOT_FOUND`.
- `sources` is safe evidence only.
- `actions` is safe governed-action evidence only.
- `suggestions` is safe next-prompt UI copy.
- `fallbackReason` is null for live success and set for deterministic fallback.
- `providerRequestId` is optional trace evidence.
- `metadata` is sanitized, product-safe, and optional.
- Do not expose `result.sanitizedPayload` to product frontends.
- Do not expose runtime `authContext` to product frontends.
- Do not expose private action/tool payloads unless explicitly sanitized into `actions`.

## 5. Internal Auth Naming Boundary

`sessionId` may still exist in internal auth and infrastructure layers where it means an authenticated browser/shopper/runtime security session.

Allowed internal usage:

- runtime public/private token claims.
- `X-AI-FABRIC-SHOPPER-SESSION-ID`.
- OAuth/session binding tables.
- MCP session protocol headers.
- internal audit records.

Not allowed as public chat payload:

- `POST /api/chat/me/query` request field.
- Platform consumer bridge public request field.
- Shopify storefront chat request field.
- ProdUS assistant request field.

Public chat conversation/thread identity is `conversationId`.

## 6. Implementation Scope

### Runtime

Primary files:

```text
ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/ChatRuntimeController.java
ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/dto/ChatQueryRequest.java
ai-infrastructure-module/ai-fabric-runtime/src/main/java/com/ai/fabric/runtime/web/dto/ChatQueryResponse.java
```

Required changes:

- Keep accepting `query`, `conversationId`, `mode`, `position`, `attachments`.
- Add `context` to request DTO.
- Reject `message`, `sessionId`, `storefrontContext`, `userId`, `ownerId` as unexpected public request fields.
- Return the canonical flat response.
- Keep `OrchestrationResult` internal or expose only sanitized canonical fields.
- Preserve internal conversation persistence and audit behavior.

### Platform Consumer Bridge

Primary files:

```text
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PublicConsumerBridgeChatService.java
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/PublicConsumerBridgeController.java
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/DeploymentPocChatService.java
Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/web/DeploymentController.java
```

Required changes:

- Accept the canonical request.
- Forward the canonical request to runtime.
- Convert product `context` to attachments only if runtime still needs attachments for retrieval.
- Return the canonical flat response.
- Do not forward or return `storefrontContext`.
- Do not expose runtime auth or nested runtime envelope.

### Shopify Bridge

Primary files:

```text
product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/web/ShopifyStorefrontController.java
product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront/service/ShopifyStorefrontChatService.java
product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/client/platform/PlatformShopifyStoreClient.java
product-services/shopify-bridge-service/ui
```

Required changes:

- Storefront UI sends `context`, not `storefrontContext`.
- Shopify Bridge accepts the canonical request.
- Shopify Bridge may build a Shopify context attachment internally from `context`.
- Shopify Bridge returns canonical flat response to storefront UI.
- Remove public dependence on `result.sanitizedPayload`.
- Keep Shopify-specific context keys inside `context`.
- Keep shopper/OAuth/session auth mechanisms internal.

### ProdUS

Primary files are in the ProdUS repository:

```text
/Users/mahmoudashraf/Downloads/Projects/ProdUS/backend/src/main/java/com/produs/ai/LoomAIIntegrationService.java
/Users/mahmoudashraf/Downloads/Projects/ProdUS/frontend/src/features/platform
```

Required changes:

- ProdUS follows the platform canonical request and response.
- ProdUS backend still validates tenant/resource access before calling LoomAI.
- ProdUS frontend never calls LoomAI/Platform directly.

## 7. Migration Policy

No long-term backward compatibility is required.

Allowed temporary behavior:

- internal normalizers may read old runtime envelopes during the same implementation branch while all callers are being moved.
- tests may keep legacy fixtures only to prove rejection or normalization during the transition.

Not allowed:

- permanent `message` alias.
- permanent `sessionId` public request alias.
- permanent `storefrontContext` public request alias.
- product frontend code reading `result.sanitizedPayload`.

## 8. Implementation Slices

### Slice 1: Contract DTOs And Normalizer

Introduce or update shared runtime DTOs:

- `CanonicalChatRequest`
- `CanonicalChatResponse`
- `CanonicalChatResponseNormalizer`

The normalizer should be used at runtime/bridge boundaries only, not as a reason to preserve old public contracts.

### Slice 2: Runtime Flat Response

Update runtime `/api/chat/me/query` to return canonical flat response.

Preserve:

- conversation persistence.
- action execution evidence.
- confirmation/clarification types.
- owned-resource auth-required types.
- sources/actions only after sanitization.

### Slice 3: Platform Bridge Flat Response

Update consumer bridge and PoC widget proxy to return the same canonical flat response.

Bridge responsibilities:

- resolve deployment.
- mint runtime auth.
- forward canonical request.
- sanitize/normalize canonical response.

Bridge is not a product-specific DTO compatibility layer.

### Slice 4: Shopify Canonical Request/Response

Update Shopify storefront and bridge:

- `storefrontContext` -> `context`.
- nested response reads -> flat `answer` / `safeSummary` / `type` / `actions`.
- keep Shopify context attachment generation internal.
- update tests and quality scripts.

### Slice 5: ProdUS Canonical Request/Response

Update ProdUS to call the same canonical Platform/runtime contract.

The ProdUS handover should point back to this plan as the platform source of truth.

### Slice 6: Live Verification

Verify:

- Shopify staging storefront chat.
- Shopify add-to-cart confirmation/action flow.
- Shopify read-only product/policy response flow.
- ProdUS staging bridge smoke.
- ProdUS direct private runtime smoke once issuer/signing material is configured.

## 9. Verification Commands

Runtime:

```bash
mvn -f ai-infrastructure-module/ai-fabric-runtime/pom.xml -q test
```

Platform:

```bash
mvn -f Platfrom/backend/pom.xml -q -Dtest=PublicConsumerBridge*Test test
mvn -f Platfrom/backend/pom.xml -q -Dtest=DeploymentMarketplaceDraftCompilerServiceTest test
```

Shopify Bridge:

```bash
mvn -f product-services/shopify-bridge-service/pom.xml -q test
bash -n scripts/verify-shopify-companion.sh
```

ProdUS:

```bash
cd /Users/mahmoudashraf/Downloads/Projects/ProdUS
mvn -f backend/pom.xml -q -Dtest=LoomAIIntegrationControllerTest test
npm --prefix frontend run build
```

## 10. Live Staging Gates

Do not call this complete until all relevant gates pass:

- Runtime `/api/chat/me/query` returns flat canonical response.
- Platform consumer bridge returns flat canonical response.
- Platform PoC widget proxy returns flat canonical response.
- Shopify storefront sends `context` and receives flat canonical response.
- Shopify storefront no longer depends on `result.sanitizedPayload`.
- ProdUS backend sends canonical request and receives flat canonical response.
- Old public fields `message`, `sessionId`, and `storefrontContext` are rejected or absent from product public chat payloads.
- Internal auth/session flows still work.
- Shopify customer account/cart/order-related flows still pass their existing gates.

## 11. Documentation Updates

Update:

- `Final_Documentation/Development_Guides/SHOPIFY_MCP_FIRST_AND_GATEWAY_DEVELOPMENT_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md`
- `/Users/mahmoudashraf/Downloads/Projects/ProdUS/docs/LOOMAI_STAGING_DEPLOYMENT_HANDOVER.md`
- `/Users/mahmoudashraf/Downloads/Projects/ProdUS/docs/LOOMAI_CONTRACT_STANDARDIZATION_CHANGE_PLAN.md`

The docs must say that the canonical contract is owned by LoomAI Platform, not by a single product adapter.

## 12. Open Decision

Response `metadata` should remain shallow and sanitized. If products need detailed runtime evidence, expose it through operator/debug endpoints, not the public chat response.
