# 010.draft1 Owned User Resource Param Resolution Design

Status: implementation in progress; generic runtime/config support landed locally on 2026-05-18, durable owned-resource table remains the next hardening slice.

Owner mode: Shopify Companion / Thinker runtime / Marketplace action param resolution design

Roadmap phase: `010.draft1` - design a production-safe way for Loom Companion and generic MCP actions to resolve shopper-owned resource parameters without stuffing private data into prompt context or asking shoppers for internal ids.

Priority: P0 design input for `010.2` and `010.3`; implementation should be split into a later execution plan after review.

Parent plans:

- [010 GTM And Partner Portal Launch Readiness](010_GTM_AND_PARTNER_PORTAL_LAUNCH_READINESS.md)
- [010.1 Shopify Companion UI Launch Readiness](010_1_SHOPIFY_COMPANION_UI_LAUNCH_READINESS.md)
- [010.2 Shopify Companion Two-Mode Surface Simplification](010_2_SHOPIFY_COMPANION_TWO_MODE_SURFACE_SIMPLIFICATION.md)
- [010.3 Shopify Companion Query Speed, Accuracy, And Reliability Optimization Plan](010_3_SHOPIFY_COMPANION_QUERY_SPEED_ACCURACY_RELIABILITY_OPTIMIZATION_PLAN.md)
- [009.1 Marketplace Config Driven MCP Capability Architecture](009_1_MARKETPLACE_CONFIG_DRIVEN_MCP_CAPABILITY_ARCHITECTURE.md)
- [009.2 MCP Execution Gateway Extraction Plan](009_2_MCP_EXECUTION_GATEWAY_EXTRACTION_PLAN.md)

Related code areas:

- `ai-infrastructure-module/ai-infrastructure-chat-session`
- `ai-infrastructure-module/ai-fabric-runtime`
- `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/IntentHandlingStep.java`
- `ai-infrastructure-module/ai-infrastructure-actions-connector`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/deployment/service/PublicConsumerBridgeChatService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension`

---

## 2026-05-18 Implementation Update

Implemented the first production-safe path without adding Shopify text matching or Shopify-specific branching to the generic runtime:

- ACTION param schemas now support `visibility`, `askUser`, and `resolveFrom`.
- Connector ACTION definitions parse and compile those fields into runtime action config.
- Runtime hides `visibility=INTERNAL|SECRET|SYSTEM` and `askUser=false` params from prompt/action metadata and shopper clarification.
- Runtime resolves missing params from trusted sources before required-param clarification:
  - `RUNTIME_CONTEXT`
  - `ATTACHMENT_METADATA`
  - `OWNED_RESOURCE` from bounded request/session metadata and normalized attachment metadata
  - `READ_ACTION` through policy-allowed read actions only
- READ_ACTION-derived params are tracked as trusted resolver output so provenance validation accepts them without accepting hallucinated public params.
- Commerce curated modes allow Shopify read actions for thinker/resolver/executor/cart-assistant read-action resolution.
- Marketplace migration `V109__shopify_owned_resource_param_resolution.sql` publishes:
  - `shopify_get_cart.cart_id` as hidden owned-resource context, never shopper-provided
  - `shopify_update_cart.cart_id`, `shopperSessionId`, and `confirmationAccepted` as hidden/system params
  - Customer Account order/store-credit read actions as grounding/read-action eligible
  - `shopify_request_return.order_number` with `READ_ACTION` fallback to `shopify_get_most_recent_order_status`

Not yet implemented in this slice:

- Durable `owned_resource_refs` table and TTL cleanup in the runtime database.
- Persisting cart handles from successful MCP `update_cart` results for later turns.
- Cross-redeploy Customer Account OAuth persistence proof, which remains deferred to the release gate.

Current behavior is still an improvement: internal params are no longer asked from shoppers, and read actions can provide owned-resource context when trusted session/attachment/read-action evidence exists. The durable store is still required before claiming full cross-turn/cross-redeploy owned-resource continuity.

## Problem

The current `show cart` behavior exposes an internal action parameter:

```text
show cart

Clarification Needed
To proceed, please provide: cart_id.
```

This is wrong for a shopper-facing product. A shopper should not know or provide `cart_id`. The runtime selected a plausible read action, `shopify_get_cart`, but the action param was modeled as a normal required user parameter instead of a trusted owned-resource parameter.

The broader issue is not cart-specific. Any question about "my" resources may require internal handles:

- current cart
- current checkout
- recent order
- customer account
- saved address
- support case
- return request
- previous MCP-created resource

Those handles should be resolved from trusted session/resource state, not extracted from user text and not overfilled into LLM prompt context.

---

## Design Decision

Introduce **owned user resource parameter resolution**.

Marketplace action params must declare where each parameter comes from:

- LLM-extracted public input
- user clarification
- attachment metadata
- owned resource store
- customer auth session
- runtime context
- action result reference
- static config
- secret reference

Thinker/runtime remains LLM-led for intent/action selection, but parameter completion is deterministic and policy-driven.

The key principle:

```text
Prompt context carries hints and small refs.
Trusted resolvers fetch handles and owned data only when an action needs them.
```

Do not store or inject full owned-user data into prompt context by default.

---

## Goals

- Prevent internal params such as `cart_id`, `shopperSessionId`, and customer tokens from being requested from shoppers.
- Let Thinker mode execute safe read actions when the action is selected by the LLM and all hidden resource params can be resolved.
- Keep runtime generic. Do not hardcode Shopify text phrases or Shopify-specific business logic into generic runtime routing.
- Keep Shopify-specific browser/session/auth behavior inside Shopify Bridge or Shopify-specific resolver adapters.
- Store short-lived owned resource handles in the same runtime deployment database, but in a separate lifecycle table.
- Support future non-Shopify MCP servers through the same param resolution model.
- Preserve auditability, TTL, tenant isolation, and fail-closed behavior.

---

## Non-Goals

- Do not implement text matching for "show cart", "my cart", "order", or similar phrases.
- Do not create a shopper-facing "get cart id" action.
- Do not store full cart/order/customer data in chat turns.
- Do not put secrets or access tokens in Marketplace manifests.
- Do not make `tools/list` runtime product truth.
- Do not bypass protected customer data gates for Customer Account MCP or Checkout MCP.

---

## Current State

Shopify storefront chat currently flows through:

```text
Shopify widget
  -> Shopify Bridge storefront chat endpoint
  -> Platform public consumer bridge
  -> runtime /api/chat/me/query
  -> ai-infrastructure chat/session/action pipeline
```

Chat turns are persisted by `ai-infrastructure-chat-session` in:

- `chat_sessions`
- `chat_turns`

The runtime identity is derived from:

```text
consumerId + shopperSessionId -> owner_id
```

This is good for conversation ownership. It should also be used as the owned-resource subject key, but owned resources need their own table and TTL.

---

## Proposed Runtime Data Model

Use the same runtime deployment database, with a separate table:

```text
owned_resource_refs
```

Suggested fields:

| Field | Purpose |
| --- | --- |
| `id` | Internal row id |
| `owner_id` | Same runtime subject id used by chat sessions |
| `deployment_id` | Runtime deployment boundary |
| `tenant_id` | Tenant boundary when available |
| `consumer_id` | Public consumer/storefront consumer boundary |
| `conversation_id` | Optional current conversation link |
| `resource_type` | Example: `shopify.cart`, `shopify.order`, `shopify.checkout` |
| `scope` | Example: `current_session`, `current_customer`, `last_created` |
| `resource_handle_ciphertext` | Encrypted handle/id/token if sensitive |
| `resource_handle_hash` | Optional lookup hash for uniqueness without plaintext |
| `safe_summary_json` | Optional bounded summary for UI/debug, not authoritative data |
| `source` | Example: `shopify-bridge`, `mcp.update_cart`, `customer-account-oauth` |
| `confidence` | Optional confidence for resolver selection |
| `expires_at` | TTL enforcement |
| `created_at` | Audit/lifecycle |
| `updated_at` | Audit/lifecycle |

Uniqueness:

```text
owner_id + deployment_id + resource_type + scope
```

For Shopify cart:

```text
owner_id = runtime subject derived from shopperSessionId
resource_type = shopify.cart
scope = current_session
resource_handle = Shopify cart id/token accepted by the downstream cart tool
source = shopify-bridge or mcp.update_cart
ttl = short-lived
```

---

## Param Schema Extension

Marketplace action params should support a `resolveFrom` declaration.

Example: owned cart id.

```json
{
  "name": "cart_id",
  "type": "STRING",
  "required": true,
  "visibility": "INTERNAL",
  "askUser": false,
  "resolveFrom": {
    "source": "OWNED_RESOURCE",
    "resourceType": "shopify.cart",
    "scope": "current_session",
    "handleField": "resource_handle"
  }
}
```

Example: public quantity.

```json
{
  "name": "quantity",
  "type": "INTEGER",
  "required": true,
  "visibility": "PUBLIC",
  "askUser": true,
  "resolveFrom": {
    "source": "LLM_EXTRACTED"
  }
}
```

Example: selected product variant from attachment metadata.

```json
{
  "name": "product_variant_id",
  "type": "STRING",
  "required": true,
  "visibility": "INTERNAL",
  "askUser": false,
  "resolveFrom": {
    "source": "ATTACHMENT_METADATA",
    "metadataKeys": ["product_variant_id", "productVariantId", "variantId", "firstAvailableVariantId"]
  }
}
```

Example: runtime session id.

```json
{
  "name": "shopperSessionId",
  "type": "STRING",
  "required": true,
  "visibility": "INTERNAL",
  "askUser": false,
  "resolveFrom": {
    "source": "RUNTIME_CONTEXT",
    "field": "sessionId"
  }
}
```

Example: customer account token.

```json
{
  "name": "customer_access_token",
  "type": "STRING",
  "required": true,
  "visibility": "SECRET",
  "askUser": false,
  "resolveFrom": {
    "source": "CUSTOMER_AUTH_SESSION",
    "authProvider": "shopify.customer-account",
    "scope": "current_customer"
  }
}
```

Allowed `resolveFrom.source` values:

- `LLM_EXTRACTED`
- `USER_CLARIFICATION`
- `ATTACHMENT_METADATA`
- `OWNED_RESOURCE`
- `CUSTOMER_AUTH_SESSION`
- `RUNTIME_CONTEXT`
- `ACTION_RESULT_REF`
- `STATIC_CONFIG`
- `SECRET_REF`

Validation rules:

- `visibility=SECRET` must not be included in traces, prompts, UI debug payloads, or chat turns.
- `askUser=false` params must never produce shopper-facing "please provide param_name" messages.
- `OWNED_RESOURCE` must declare `resourceType` and `scope`.
- `CUSTOMER_AUTH_SESSION` must declare `authProvider`.
- `ATTACHMENT_METADATA` must declare an allowlist of metadata keys.
- `SECRET_REF` must reference install-scoped or deployment-scoped secrets only.

---

## Execution Flow

### Read Action With Owned Resource

```text
1. Shopper asks: "show my cart"
2. LLM/Thinker selects shopify_get_cart
3. Runtime validates action params
4. Runtime sees cart_id resolveFrom=OWNED_RESOURCE, askUser=false
5. Runtime calls OwnedResourceResolver
6. Resolver returns cart handle for owner_id + resource_type + scope
7. Runtime calls MCP tools/call get_cart with cart_id
8. MCP result is normalized into action evidence
9. Assistant answers from normalized evidence
```

### Missing Owned Resource

```text
1. Shopper asks: "show my cart"
2. LLM/Thinker selects shopify_get_cart
3. cart_id is hidden and cannot be resolved
4. Runtime returns structured hidden failure:
   - type: CLARIFICATION_REQUIRED or ACTION_DENIED
   - public missing params: []
   - hiddenMissingContext: [{source: OWNED_RESOURCE, resourceType: shopify.cart, scope: current_session}]
5. Bridge/UI maps this to shopper-safe guidance:
   "I cannot access your active cart yet. Open your cart or add an item, then ask again."
```

No `cart_id` is exposed.

---

## Owned Resource Feeding Sources

Owned resources are fed by trusted sources only:

### Browser/Storefront Session

For anonymous resources such as current cart:

- Shopify theme app embed can discover a cart handle or cart snapshot through Shopify storefront/browser APIs.
- Bridge receives it as structured context or a resolver update.
- Bridge writes a short-lived `shopify.cart/current_session` handle into runtime owned-resource storage.

### MCP Action Results

When MCP `update_cart` creates or updates a cart:

- Normalize the returned cart id.
- Persist it as `shopify.cart/current_session`.
- Attach safe evidence to the action result.

### Customer Account OAuth

For authenticated resources:

- Store Customer Account OAuth session material in Bridge's secure auth/session store.
- Store only a scoped resolver reference in runtime owned-resource storage if needed.
- Never store raw customer access tokens in chat turns.

### Checkout MCP

For checkout resources:

- Store checkout/session handles only when returned by approved Checkout MCP operations.
- Keep terminal checkout operations behind package, auth, confirmation, and live verification gates.

---

## Shopify Cart Design

Shopify Storefront MCP supports `get_cart`, but it requires `cart_id`.

Design:

- `shopify_get_cart.cart_id` becomes an internal owned-resource param.
- The runtime must not ask the shopper for it.
- The Bridge/storefront layer is responsible for resolving or feeding the current cart resource handle.
- If no cart handle exists, the assistant should provide safe guidance rather than an internal clarification.

Important validation:

- Verify whether Shopify Ajax cart token is accepted by Storefront MCP `get_cart` as `cart_id`.
- If accepted, store that token/handle as `shopify.cart/current_session`.
- If not accepted, use MCP-returned cart ids from `update_cart` as authoritative handles and use Ajax cart only as optional safe summary evidence.

---

## Runtime Responsibilities

The generic runtime should:

- Parse `resolveFrom` metadata from compiled Marketplace action config.
- Resolve non-public params before required-param validation completes.
- Keep LLM action selection and public param extraction separate from hidden param resolution.
- Return structured hidden missing-context diagnostics for `askUser=false` params.
- Persist owned-resource refs in the runtime DB with TTL.
- Record only safe summaries and bounded refs in chat turns.
- Avoid domain-specific text matching or Shopify-specific branching.

---

## Shopify Bridge Responsibilities

Shopify Bridge should:

- Own Shopify storefront/browser session interpretation.
- Own Customer Account OAuth and protected customer data gates.
- Feed or update `shopify.cart/current_session` resource refs when it can verify the handle.
- Translate hidden missing-context responses into shopper-safe guidance.
- Never expose MCP, OAuth, PKCE, secret names, `cart_id`, `shopperSessionId`, or internal schema names to shoppers.

---

## Marketplace Responsibilities

Marketplace should:

- Validate `resolveFrom` schema.
- Compile `resolveFrom` metadata into runtime actions config.
- Reject unsupported or unsafe resolver declarations.
- Keep plugin manifests separate from install secrets.
- Allow generic MCP actions to declare owned-resource dependencies without new code per action.

---

## Security And Privacy

Required controls:

- Tenant/deployment/consumer boundary on every owned-resource lookup.
- `owner_id` must come from verified runtime identity, not request body.
- Resource handles encrypted at rest when sensitive.
- TTL required for all owned-resource refs.
- No raw owned-resource data in prompt context unless explicitly needed and bounded.
- No secret/token values in chat turns, traces, UI debug, or action evidence.
- `askUser=false` missing params cannot appear in shopper clarification text.
- Customer Account data requires authenticated customer session and protected-data posture.
- Audit every resolver write and action execution that uses an owned resource.

---

## Acceptance Criteria

For `show cart`:

- LLM/Thinker may select `shopify_get_cart`.
- If `shopify.cart/current_session` exists, runtime resolves `cart_id` and calls MCP `get_cart`.
- If no cart handle exists, no `cart_id` clarification is shown.
- The shopper receives safe guidance.
- Debug output exposes only safe resolver status, not handles or secret values.

For generic actions:

- Any action param can declare a supported `resolveFrom.source`.
- Public missing params still use normal clarification.
- Internal/secret missing params produce hidden missing-context diagnostics.
- Runtime remains generic and avoids text matching.

For storage:

- Owned-resource refs are stored in runtime DB in a separate table.
- Expired refs are ignored and eventually cleaned up.
- Chat turns continue to store conversation memory only.

---

## Implementation Slices

### Slice 1 - Schema And Validation

- Extend Marketplace ACTION param schema with `visibility`, `askUser`, and `resolveFrom`.
- Add manifest validation tests.
- Compile metadata into actions config.

### Slice 2 - Runtime Resolver Infrastructure

- Add `OwnedResourceRef` entity/table in runtime DB.
- Add `OwnedResourceResolver` SPI.
- Add parameter resolution stage before required-param clarification.
- Add hidden missing-context result shape.

### Slice 3 - Shopify Cart Resolver

- Add Bridge/storefront-owned cart feed path.
- Persist `shopify.cart/current_session` for verified handles.
- Update Shopify cart MCP plugin manifest so `cart_id` resolves from owned resource.

### Slice 4 - MCP Result Ref Persistence

- Persist cart handle from successful `update_cart` MCP evidence.
- Add action-result-to-owned-resource mapping support.

### Slice 5 - UX And Safety Shaping

- Ensure missing hidden params become shopper-safe messages.
- Ensure debug views show safe resolver status only.
- Add tests for no internal param leakage.

### Slice 6 - Live Verification

- Verify `show cart` on staging:
  - cart exists and handle resolves
  - cart missing
  - cart created via update action then read
  - expired owned-resource ref
  - cross-session isolation

---

## Open Questions

- Does Shopify Storefront MCP `get_cart` accept the Ajax cart token, or only GraphQL cart GIDs returned by Storefront Cart API/MCP?
- What TTL should Shopify cart refs use for staging and production?
- Should owned-resource refs be managed by the runtime only, or should Platform expose a generic admin/support view for resolver diagnostics?
- Should safe summaries be stored in `owned_resource_refs`, or always re-read on demand?

---

## Draft Verdict

This is the correct direction.

Thinker mode should read or execute safe read actions for owned resources, but only after hidden params are resolved from trusted owned-resource state. The fix is not text matching and not prompt stuffing. The fix is Marketplace-declared parameter source resolution plus a short-lived owned-resource store in the runtime database.
