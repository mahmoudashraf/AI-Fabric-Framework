# 010.14 Consumer-Bound Runtime Assignment And Direct Private Auth Plan

Status: implementation in progress, created 2026-06-01, updated 2026-06-01  
Primary target: Shopify Companion Bridge, ProdUS backend-mediated integration, and future external-customer runtimes  
Related plans: 010.5, 010.6, 010.7, 010.8, 010.12, 010.13  
Non-goal: do not proxy every customer chat request through Platform.

## Purpose

Make stable consumer-bound runtime assignment the standard production integration pattern.

The Platform should remain the control plane:

- own customer/consumer/deployment binding,
- expose the current active runtime assignment,
- expose non-secret auth posture and endpoint metadata,
- audit binding changes,
- support promotion, rollback, and restore.

Customer-facing systems should be the data plane:

- resolve the active runtime assignment at startup or on failure,
- cache the runtime URL and auth hints,
- call runtime directly with private-runtime auth,
- avoid sending every request through Platform.

This plan also fixes the current Shopify Bridge issue where storefront chat still calls Platform's `/api/public/consumers/{consumerId}/bridge/chat/*` route for every query.

## Current State

### Implemented Consumer Binding

Platform already has stable consumer binding:

- `platform_consumers.consumerId` is the stable external integration id.
- `platform_consumers.boundDeploymentId` points to the current active deployment.
- Customer/admin APIs can create consumers and update bindings.
- Binding changes record history.
- Public consumer resolution exists through:
  - `GET /api/public/consumers/{consumerId}`
  - `GET /api/public/consumers/{consumerId}/status`
  - `GET /api/public/consumers/{consumerId}/credentials`

### Implemented Auth Posture Discovery

The public consumer credentials response already exposes:

- `consumerId`
- `deploymentId`
- `runtimeBaseUrl`
- runtime endpoint summary
- `PRIVATE_RUNTIME_SIGNED_ASSERTION` readiness
- `BACKEND_MEDIATED_PRIVATE_RUNTIME` preferred integration mode
- trusted backend header names
- public token posture when configured

The response intentionally does not expose raw secrets.

### Current Shopify Problem

Shopify Bridge currently uses the consumer credentials route for bootstrap readiness, but chat still goes through Platform:

```text
Storefront browser
  -> Shopify Bridge /api/storefront/.../chat/query
  -> Platform /api/public/consumers/{consumerId}/bridge/chat/query
  -> Runtime /api/chat/me/query
```

This keeps Platform in the hot path and makes runtime availability, latency, retries, and auth signing depend on Platform for every shopper request.

Target flow:

```text
Storefront browser
  -> Shopify Bridge /api/storefront/.../chat/query
  -> Runtime /api/chat/me/query
```

Platform should be called only for assignment resolution, not every chat turn.

## Design Decision

Use a stable `consumerId` as the customer-facing integration handle.

Concrete deployments can change underneath the consumer binding. External systems should not hardcode runtime deployment URLs as permanent production config. They should resolve the active runtime assignment from Platform, cache it, and refresh only when needed.

## Supported Auth Flows

### Primary Flow: Private Backend-Mediated Runtime

Use this for Shopify Bridge, ProdUS, and production external customer systems.

Requirements:

- caller is a trusted backend, not a browser,
- backend stores its own runtime API key/signing material server-side,
- backend signs private runtime assertions,
- backend calls runtime directly,
- Platform resolver returns no secrets.

Runtime auth mode:

```text
PRIVATE_RUNTIME_SIGNED_ASSERTION
```

Preferred integration mode:

```text
BACKEND_MEDIATED_PRIVATE_RUNTIME
```

### Secondary Flow: Public Browser Token Runtime

The consumer assignment contract can also describe public runtime token posture when configured, but this plan does not move Shopify or ProdUS to browser-direct runtime access.

Browser-direct runtime remains constrained to short-lived signed public tokens, approved origins, and low-privilege scopes. It is not the default for Shopify Companion or ProdUS.

## Target Runtime Assignment Contract

Add the canonical no-secret assignment endpoint:

```http
GET /api/public/consumers/{consumerId}/runtime-assignment
```

Authorization:

- `PUBLIC_API_CLIENT`, `PLATFORM_ADMIN`, `PLATFORM_OPERATOR`, or approved store/customer access evaluator.
- For external customer backends such as ProdUS, use a backend-only machine credential.
- Never expose this endpoint directly from browser code.

Response:

```json
{
  "consumerId": "produs-staging",
  "deploymentId": "dep-7706fafb",
  "runtimeBaseUrl": "https://runtime.example.com",
  "runtimeAuthMode": "PRIVATE_RUNTIME_SIGNED_ASSERTION",
  "preferredIntegrationMode": "BACKEND_MEDIATED_PRIVATE_RUNTIME",
  "issuer": "produs-staging-backend",
  "audience": "produs-staging",
  "audienceMode": "CONSUMER_ID",
  "assignmentRevision": "sha256:...",
  "cacheTtlSeconds": 300,
  "externalIntegrationReady": true,
  "endpoints": {
    "query": "https://runtime.example.com/api/chat/me/query",
    "queryOnce": "https://runtime.example.com/api/chat/me/query-once",
    "suggestions": "https://runtime.example.com/api/chat/me/suggestions",
    "conversations": "https://runtime.example.com/api/chat/me/conversations",
    "authContext": "https://runtime.example.com/api/chat/me/auth-context",
    "health": "https://runtime.example.com/actuator/health"
  },
  "guidance": "Route customer traffic through your trusted backend and call runtime directly."
}
```

Rules:

- No raw runtime API keys.
- No raw assertion signing secrets.
- No provider secrets.
- No Coolify internals.
- No connector/private service URLs unless explicitly public runtime URLs.
- `queryOnce` must be added to the endpoint summary because ProdUS uses it and it is now part of the canonical runtime contract.

## Audience Strategy

### Recommended Production Default

Use stable consumer audience:

```text
aud = consumerId
```

Include concrete deployment identity as a separate claim:

```text
deploymentId = resolved deployment id
```

Benefits:

- customer backend env does not change when deployment changes,
- runtime can still audit the concrete deployment,
- active deployment can be swapped by changing Platform binding only,
- imported/restored deployments can preserve the same consumer integration contract.

Greenfield rule: private runtime assertions use stable `consumerId` as `aud`. Concrete `deploymentId` is included only as an audit/routing claim, not as the normal assertion audience.

## Cache And Refresh Semantics

Customer backend cache key:

```text
consumerId
```

Cached values:

- deployment id,
- runtime base URL,
- endpoint URLs,
- issuer hint,
- audience hint,
- assignment revision,
- cache TTL,
- resolved timestamp.

Backend resolves assignment:

1. on startup,
2. when cache is missing,
3. after TTL in a background refresh,
4. after runtime connectivity failure,
5. after runtime `401`/`403` that may indicate stale audience,
6. after runtime `404` for an expected route,
7. after operator-triggered refresh.

Per request:

- use cached assignment,
- sign assertion locally,
- call runtime directly,
- do not call Platform unless a refresh condition is hit.

Failure handling:

1. Runtime timeout/connect failure:
   - invalidate cache,
   - resolve assignment from Platform,
   - retry once if assignment changed.
2. Runtime `401`/`403`:
   - resolve assignment,
   - regenerate assertion with returned audience,
   - retry once if assignment/audience changed.
3. Runtime `404` for canonical route:
   - resolve assignment,
   - retry once if runtime URL changed,
   - otherwise fail closed with operator-safe diagnostic.
4. Platform resolver unavailable:
   - keep using cached assignment until TTL grace expires if runtime is healthy,
   - if runtime is unhealthy and Platform unavailable, return a safe outage message.

## Shopify Bridge Fix

### Goal

Shopify Bridge must stop using Platform as the chat proxy for every storefront query.

Current hot path to remove:

```text
PlatformShopifyStoreClient.queryConsumerBridgeChat(...)
PlatformShopifyStoreClient.suggestConsumerBridgeChat(...)
```

Target hot path:

```text
ShopifyStorefrontChatService
  -> ShopifyRuntimeAssignmentCacheService
  -> ShopifyRuntimePrivateAssertionSigner
  -> ShopifyRuntimeChatClient
  -> Runtime /api/chat/me/query
```

### New Bridge Components

#### `ShopifyRuntimeAssignmentCacheService`

Responsibilities:

- resolve store `consumerId` from Platform store summary,
- call Platform assignment/credentials endpoint,
- validate `BACKEND_MEDIATED_PRIVATE_RUNTIME`,
- validate `PRIVATE_RUNTIME_SIGNED_ASSERTION`,
- cache by `consumerId`,
- expose forced refresh,
- expose assignment health/diagnostics for admin UI.

Data:

```text
consumerId
deploymentId
runtimeBaseUrl
chatQueryUrl
queryOnceUrl
suggestionsUrl
authContextUrl
issuer
audience
assignmentRevision
resolvedAt
expiresAt
```

Storage:

- start with in-memory cache plus optional persisted read-through table if Bridge restarts become frequent.
- do not persist secrets.
- do not persist shopper/customer OAuth tokens in this cache.

#### `ShopifyRuntimePrivateAssertionSigner`

Responsibilities:

- produce the same private runtime assertion shape Platform currently signs,
- use Bridge server-side secret material,
- include shopper session id,
- include deployment id,
- include stable audience returned by assignment resolver,
- include scopes required for the runtime route.

Required Bridge env:

```text
SHOPIFY_BRIDGE_RUNTIME_TRUSTED_BACKEND_API_KEY=<secret>
SHOPIFY_BRIDGE_RUNTIME_PRIVATE_ASSERTION_SIGNING_KEY=<secret>
SHOPIFY_BRIDGE_RUNTIME_PRIVATE_ASSERTION_ISSUER=shopify-bridge
```

Future 010.6 upgrade:

```text
SHOPIFY_BRIDGE_RUNTIME_ASSERTION_PRIVATE_KEY_PATH=<path>
SHOPIFY_BRIDGE_RUNTIME_ASSERTION_KEY_ID=<kid>
```

#### `ShopifyRuntimeChatClient`

Responsibilities:

- call runtime query/suggestions/query-once directly,
- apply canonical request payload,
- apply timeout/retry policy,
- add private runtime headers,
- sanitize logs,
- return runtime response unchanged enough for canonical UI/debug inspector.

Headers:

```http
X-AIFABRIC-RUNTIME-API-KEY: <trusted backend api key>
X-AIFABRIC-RUNTIME-AUTHORIZATION: Bearer <private assertion>
```

### Bridge Feature Flags

Defaults:

```text
SHOPIFY_BRIDGE_DIRECT_RUNTIME_ENABLED=true
SHOPIFY_BRIDGE_PLATFORM_CHAT_PROXY_FALLBACK_ENABLED=false
```

Fallback may exist only as an operator-controlled temporary safety switch. It must not be silently used in production because it hides the architectural regression.

### Shopify Storefront Contract

Canonical browser-facing contract:

- storefront still calls Bridge `/api/storefront/shops/{shop}/chat/query`,
- storefront still calls Bridge `/api/storefront/shops/{shop}/chat/suggestions`,
- Bridge decides runtime route server-side.

Shopify storefront code stays focused on the Bridge storefront API. Runtime assignment, private assertion signing, and runtime retry behavior stay server-side.

### Shopify Verification

Required tests:

1. Bridge resolves consumer assignment once and sends query directly to runtime.
2. Bridge does not call Platform `/bridge/chat/query` when direct runtime is enabled.
3. Bridge invalidates cache and refetches on runtime connect failure.
4. Bridge retries once against new runtime after assignment changes.
5. Bridge fails closed if assignment is not `PRIVATE_RUNTIME_SIGNED_ASSERTION`.
6. Bridge fails closed if runtime signing secret is absent.
7. Browser storefront response shape remains valid for the current widget.
8. Debug inspector still receives canonical request/response metadata.

Required live checks:

1. Open Shopify staging storefront.
2. Run a RAG query.
3. Confirm Bridge logs show direct runtime URL.
4. Confirm Platform logs do not show `/api/public/consumers/{consumerId}/bridge/chat/query` for that query.
5. Rebind staging consumer to a test runtime or simulate old runtime failure.
6. Confirm Bridge refetches assignment and retries once.

## ProdUS Direct Private Runtime Flow

ProdUS should use the same assignment contract.

Backend startup:

```text
consumerId = produs-staging
GET /api/public/consumers/produs-staging/runtime-assignment
validate externalIntegrationReady
cache assignment
```

Runtime calls:

```text
POST {assignment.endpoints.query}
POST {assignment.endpoints.queryOnce}
POST {assignment.endpoints.suggestions}
```

ProdUS keeps:

- backend-only runtime API key,
- backend-only assertion signing key/private key,
- issuer,
- stable consumer id.

ProdUS should not keep a permanent deployment-specific runtime URL as source of truth once assignment resolution is live.

ProdUS env target:

```text
LOOMAI_CONSUMER_ID=produs-staging
LOOMAI_ASSIGNMENT_URL=https://api.<platform-domain>/api/public/consumers/produs-staging/runtime-assignment
LOOMAI_RUNTIME_API_KEY=<secret>
LOOMAI_ASSERTION_ISSUER=produs-staging-backend
LOOMAI_ASSERTION_SIGNING_SECRET=<secret>
```

Deprecated after migration:

```text
LOOMAI_BASE_URL=<fixed runtime url>
LOOMAI_ASSERTION_AUDIENCE=<fixed deployment id>
```

These can remain as emergency override values, but they should not be the normal production source of truth.

## Platform Changes

### Contract Additions

Add:

```http
GET /api/public/consumers/{consumerId}/runtime-assignment
```

Add fields to runtime endpoint summary:

- `queryOnceUrl`
- `healthUrl`

Add fields to assignment response:

- `issuer`
- `audience`
- `audienceMode`
- `assignmentRevision`
- `cacheTtlSeconds`
- `externalIntegrationReady`

### Legacy Platform Chat Proxy

`POST /api/public/consumers/{consumerId}/bridge/chat/query` and `/suggestions` are not part of the production Shopify Bridge data path after this plan. They can remain as operator-only diagnostic endpoints, but the Bridge must not call them for storefront traffic.

### Assignment Revision

Compute from:

- consumer id,
- bound deployment id,
- runtime base URL,
- active version id,
- auth posture,
- issuer/audience policy.

Do not include secrets.

## Runtime Changes

Runtime should accept private assertions signed by trusted customer/backend issuer.

Required claims:

```json
{
  "sub": "consumer-session-...",
  "subjectType": "END_USER",
  "callerType": "TRUSTED_BACKEND",
  "authMode": "PRIVATE_RUNTIME_BACKEND_MEDIATED",
  "sessionId": "...",
  "deploymentId": "dep-...",
  "customerId": "...",
  "tenantId": "...",
  "iss": "produs-staging-backend",
  "aud": "produs-staging",
  "exp": "..."
}
```

Audience validation:

- require the stable consumer id as the normal production audience,
- include deployment id as a separate claim for audit and runtime scoping,
- reject unknown audiences.

## Secret Handling

Platform assignment endpoint returns no secret values.

Secret material is provisioned by one of:

1. backend env for Shopify Bridge or ProdUS,
2. managed secret injection during product deployment,
3. future sealed export/import restore,
4. future asymmetric public key registration.

Secrets must not be returned in:

- assignment endpoint,
- credentials endpoint,
- frontend bootstrap,
- logs,
- debug inspector,
- audit details.

## Security Requirements

- Consumer assignment endpoint must perform ownership/access checks.
- Consumer must be active.
- Deployment must belong to the same customer.
- Deployment must not be archived.
- Runtime URL must be a Platform-generated runtime URL or validated public runtime URL.
- Runtime URL must not be caller-supplied.
- No private connector URL exposure.
- No provider/Coolify token exposure.
- Direct runtime caller must use time-limited assertions.
- On auth failure, retry assignment refresh once, then fail closed.

## Implementation Slices

### Slice 1: Platform Assignment Contract

Files:

- `PublicConsumerProvisioningController`
- `PublicProvisioningApiService`
- `PublicRuntimeEndpointsSummary`
- new `PublicConsumerRuntimeAssignmentResponse`
- tests under `Platfrom/backend/src/test/...`

Work:

1. Add `queryOnceUrl` and `healthUrl`.
2. Add `runtime-assignment` endpoint.
3. Add assignment revision and cache TTL.
4. Add issuer/audience hints.
5. Preserve credentials discovery for admin/bootstrap readiness, but do not use it for hot-path chat routing.

Verification:

```bash
mvn -f Platfrom/backend/pom.xml -q -Dtest=PublicProvisioningApiServiceTest test
mvn -f Platfrom/backend/pom.xml -q -Dtest=PublicProvisioningApiIntegrationTest test
```

### Slice 2: Runtime Audience Policy

Files:

- runtime auth validation code,
- deployment profile/catalog auth config,
- private runtime assertion tests.

Work:

1. Require consumer-stable audience for new private runtime integrations.
2. Ensure runtime auth overview shows accepted audiences.
3. Add tests for consumer-audience private assertions.

Verification:

```bash
mvn -f ai-infrastructure-module/pom.xml -q test
mvn -f Platfrom/backend/pom.xml -q -Dtest=*Runtime*Auth* test
```

### Slice 3: Shopify Bridge Direct Runtime Client

Files:

- `PlatformShopifyStoreClient`
- `ShopifyStorefrontBootstrapService`
- `ShopifyStorefrontChatService`
- Bridge config properties

Work:

1. Add Bridge runtime assignment cache.
2. Add direct runtime HTTP client.
3. Add Bridge-side assertion signing.
4. Route query/suggestions/query-once directly to runtime.
5. Remove Platform proxy use from storefront chat.
6. Add refresh-on-failure retry.

Verification:

```bash
mvn -f product-services/shopify-bridge-service/pom.xml -q test
```

Expected test assertion:

- direct runtime enabled: no request to `/api/public/consumers/{consumerId}/bridge/chat/query`.
- assignment endpoint called once per cache TTL/startup, not once per chat request.

### Slice 4: ProdUS Handover Update

Files:

- `/Users/mahmoudashraf/Downloads/Projects/ProdUS/docs/LOOMAI_STAGING_DEPLOYMENT_HANDOVER.md`
- `Final_Documentation/Development_Guides/PRODUS_LOOMAI_STAGING_DEPLOYMENT_DEV_GUIDE.md`

Work:

1. Replace fixed runtime URL as preferred source of truth.
2. Document assignment resolver.
3. Document cache/refetch behavior.
4. Document stable consumer audience migration.
5. Keep fixed runtime URL only as emergency override.

Verification:

- ProdUS can call assignment resolver.
- ProdUS can call `/query` and `/query-once` directly using resolved runtime URL.
- Rebinding consumer changes resolved runtime without ProdUS env change.

### Slice 5: Live Staging Gate

Shopify:

1. Deploy Platform.
2. Deploy Shopify Bridge with direct runtime enabled.
3. Trigger storefront query.
4. Confirm runtime receives direct request.
5. Confirm Platform proxy route is not called for the query.
6. Run hosted Shopify release gate.

ProdUS:

1. Deploy Platform assignment endpoint.
2. Configure ProdUS `LOOMAI_CONSUMER_ID`.
3. Resolve assignment at startup.
4. Query runtime directly.
5. Rebind consumer to a test runtime or simulate stale runtime.
6. Confirm refresh/retry behavior.

## Acceptance Gates

### Platform

- Consumer assignment endpoint returns current active deployment.
- Assignment response exposes query, query-once, suggestions, auth-context, health endpoints.
- Assignment response contains no secrets.
- Binding update changes resolved deployment.
- Binding history is recorded.
- Existing credentials endpoint still works.

### Shopify

- Storefront chat works with direct runtime.
- Storefront suggestions work with direct runtime.
- Platform is not in the per-query chat hot path.
- Bridge refetches assignment only on startup, TTL refresh, or runtime failure.
- Browser-facing Shopify widget contract is unchanged.
- Debug inspector and RAG evidence remain available.

### ProdUS

- ProdUS backend can use stable `consumerId`.
- ProdUS does not need fixed runtime URL in normal config.
- ProdUS can query `/query` and `/query-once` through resolved runtime.
- Rebinding the consumer does not require ProdUS deployment config changes.

## Risks And Controls

| Risk | Control |
| --- | --- |
| Bridge receives stale runtime URL | TTL refresh plus retry-on-failure assignment refresh |
| Runtime assertion audience mismatch | Resolver returns audience hint; caller retries once after refresh |
| Secrets leak through resolver | Resolver returns only non-secret routing/auth posture |
| Silent fallback hides direct-runtime regression | Shopify Bridge direct-runtime tests assert no `/bridge/chat/*` Platform call |
| Shopify widget breaks | Keep browser-facing Bridge endpoints unchanged |
| ProdUS hardcodes deployment id forever | Use `LOOMAI_CONSUMER_ID` plus startup assignment resolution as the normal path |
| Runtime URL SSRF | Resolver only returns Platform-owned deployment runtime URL; caller never accepts user-supplied runtime URL |

## Execution Status

Implemented and pushed on `Platform-V10`:

- Platform `runtime-assignment` endpoint.
- Runtime endpoint summary now includes `queryOnceUrl` and `healthUrl`.
- Assignment response exposes stable consumer audience, issuer hints, endpoint URLs, cache TTL, assignment revision, and readiness without secrets.
- Shopify Bridge resolves assignment through Platform, caches it, signs private runtime assertions locally, and calls runtime query/suggestions directly.
- Shopify bootstrap warms assignment cache after resolving the store consumer.
- Shopify runtime security defaults include the store `consumerId` in accepted private runtime audiences.
- Legacy Platform chat proxy usage is removed from the Shopify storefront chat path. In greenfield mode, the Bridge uses Platform as assignment/control plane only.

Local verification passed:

- `mvn -f Platfrom/backend/pom.xml -q -DskipTests compile`
- `mvn -f product-services/shopify-bridge-service/pom.xml -q -DskipTests compile`
- `mvn -f Platfrom/backend/pom.xml -q -Dtest=PublicProvisioningApiServiceTest,PublicConsumerBridgeChatServiceTest,ShopifyStoreBootstrapServiceTest,ShopifyStoreGoLiveServiceTest test`
- `mvn -f product-services/shopify-bridge-service/pom.xml -q -Dtest=PlatformShopifyStoreClientTest,ShopifyStorefrontChatServiceTest test`
- `mvn -f product-services/shopify-bridge-service/pom.xml -q -Dtest=ShopifyWebhookSubscriptionServiceTest test`
- `mvn -f Platfrom/backend/pom.xml -q -DskipITs test`
- `mvn -f product-services/shopify-bridge-service/pom.xml -q test`
- `git diff --check`

Live environment verification on June 1, 2026:

- Staging Platform assignment for `shopify-shopping-companion-test` resolves to `dep-8c3e7259`, with `audienceMode=CONSUMER_ID`, `issuer=platform-consumer-bridge`, and `audience=shopify-shopping-companion-test`.
- Production runtime `dep-8c3e7259` accepts both `dep-8c3e7259` and `shopify-shopping-companion-test` as private assertion audiences, with auth overview showing zero errors and zero warnings.
- Runtime connector proxy is enabled and points to the production connector for `dep-8c3e7259`.
- Production connector is loaded from the current v38 routing artifact and uses Shopify Bridge staging as the upstream for this staging store proof.
- Runtime MCP gateway configuration is present, and live MCP-backed storefront actions no longer fail with missing `mcp-gateway.base-url`.
- Live Shopify Bridge storefront chat queries returned HTTP 200 through the direct runtime path:
  - `What is your shipping policy?` returned shipping-country policy data.
  - `Search products for wax` returned available wax products.
  - `What should I buy for travel?` returned a grounded product recommendation.

Operational note:

- The current staging-shop proof intentionally uses staging Platform artifacts with the production runtime URL assigned to `dep-8c3e7259`. Before broader production rollout, publish/apply the same artifact version through the production Platform control plane or standardize artifact hosting per environment so runtime artifact source and runtime host are environment-aligned.

Remaining outside 010.14:

- Shopify public/App Store readiness is still gated by app-scopes webhook support posture, final protected customer-data/Customer Account proof, Checkout MCP proof, and merchant launch packaging.

## Recommendation

Proceed with:

1. Platform assignment endpoint and endpoint summary upgrade.
2. Shopify Bridge direct-runtime client with no Platform proxy on the storefront chat path.
3. Stable consumer audience support for new/private integrations.
4. ProdUS handover update to resolve by `consumerId` at startup and on runtime failure.

This preserves Platform as the control plane while removing it from customer chat request execution.
