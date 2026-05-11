# 010.3 Shopify Companion Query Speed, Accuracy, And Reliability Optimization Plan

Status: active optimization plan, first live probe pass completed on staging 2026-05-11

Parent plans:

- [010 GTM And Partner Portal Launch Readiness](010_GTM_AND_PARTNER_PORTAL_LAUNCH_READINESS.md)
- [010.1 Shopify Companion UI Launch Readiness](010_1_SHOPIFY_COMPANION_UI_LAUNCH_READINESS.md)
- [010.2 Shopify Companion Two-Mode Surface Simplification](010_2_SHOPIFY_COMPANION_TWO_MODE_SURFACE_SIMPLIFICATION.md)

Scope:

- Shopify staging store: `shopping-companion-test.myshopify.com`
- Staging storefront: `https://shop-staging.loomai.pro/?country=US`
- Bridge staging: `https://shopify-bridge-staging.46.224.145.148.sslip.io`
- Package posture: Free retained but disabled; Elite is default and active
- Customer-facing modes: shopping pages use Companion Thinker; account/cart/support pages use Companion Resolver/executor posture

## Current Live Result

Live probe files:

- `/tmp/loomai-shopify-query-probes-20260511-rerun.json`
- `/tmp/loomai-shopify-query-probes-20260511-final.json`
- `/tmp/loomai-shopify-query-probes-20260511-sanitized-smoke.json`

Staging now passes the critical release behavior checks:

- no `500` responses in the final smoke
- no `Thinker deep diagnosis requires Shopify Companion Elite` conflict
- storefront bootstrap reports `ELITE`, `ACTIVE`, `thinker_deep` for shopping pages, `executor` for account/cart/support pages
- account/order/support page runtime selections no longer leak into cart actions
- unsupported order mutation actions are denied only when runtime selects an order-mutation action ID that is not covered by an approved store action package
- public storefront chat responses do not expose runtime auth context, raw document metadata, parameter schemas, deployment IDs, tenant IDs, or provider internals

## Query Set Used

| ID | Mode | Surface | Page | Query |
| --- | --- | --- | --- | --- |
| `shopping-search-wax` | `thinker_deep` | `max-mode` | `index/search` | `Search products for wax` |
| `shopping-under-20` | `thinker_deep` | `max-mode` | `search` | `What products are available under $20?` |
| `shopping-policy-return` | `thinker_deep` | `max-mode` | `policy` | `What is the return policy?` |
| `comparison-ski-wax` | `navigator_deep` | `comparison` | `product` | `Compare Selling Plans Ski Wax with other winter products and tell me when to choose each one.` |
| `source-gap-certification` | `thinker_deep` | `product-faq` | `product` | `Does Selling Plans Ski Wax have a medical allergy certification?` |
| `account-order-lookup` | `executor` | `max-mode` | `account` | `Where is my order? My order number is 1001 and my email is shopper@example.com.` |
| `governed-cart-add` | `executor` | `max-mode` | `cart` | `Add Selling Plans Ski Wax to my cart.` |
| `unsafe-refund-cancel` | `executor` | `max-mode` | `account` | `Cancel my order and refund it now.` |
| `internal-implementation-safe-answer` | `thinker_deep` | `max-mode` | `index` | `Explain how your vectorization runtime provider works.` |
| `case-noise-catalog` | `thinker_deep` | `max-mode` | `search` | `SEARCH PRODUCTS FOR WAX!!!` |

Final smoke subset:

- search catalog
- order lookup
- cart confirmation
- unsupported order-mutation action policy

## Fixes Applied During This Probe Cycle

### Elite entitlement consistency

Problem:

- Bridge bootstrap and billing reported Elite, but Platform Thinker still treated missing or legacy `FREE` billing state as non-Elite.

Fix:

- `ThinkerResolverService` now treats missing or legacy `FREE` billing as the Elite launch default because Free is retained but disabled.
- Added integration coverage proving a legacy Free store can capture thinker evidence under the new launch posture.

### Order/account action policy

Problem:

- Account/order-page executor calls could route into cart action selection and ask for `shopperSessionId`.

Fix:

- Bridge now lets runtime choose an action, then enforces a structured page/action policy.
- If runtime selects a cart action while the request context is account/order/support, Bridge returns order lookup block guidance or support guidance instead of exposing cart/session internals.
- Added storefront chat test coverage.

### Package-gated order self-service action policy

Problem:

- Return/cancel/order-edit requests must not be controlled by shopper text matching, and they must not bypass merchant/package approval, explicit confirmation, customer/checkout auth, or audit when a runtime catalog selects a mutation action.

Fix:

- Bridge no longer blocks shopper text before runtime action selection.
- Bridge denies only structured order-mutation action IDs selected by runtime policy when the store is not entitled to an approved order self-service action package.
- Elite now includes the `order-self-service` action package by default. When that package is present, Bridge allows the governed order self-service action IDs through to the Marketplace/MCP execution path and still depends on the configured action plugin, MCP server, session auth, explicit confirmation, and audit trail.
- Current concrete Marketplace-configured order self-service actions are `shopify_start_return_request` through Customer Account MCP and `shopify_cancel_checkout` through Checkout MCP. Post-order refund/cancel/edit actions remain discovery/tool-gated until a real Shopify MCP tool and plugin action are introduced; do not invent tool names or add direct GraphQL behavior in Bridge.
- Added storefront chat test coverage.

### Public response sanitization

Problem:

- Runtime responses exposed `authContext`, raw document metadata, runtime/provider diagnostics, parameter schemas, and internal IDs through the public storefront API.

Fix:

- Bridge strips runtime auth context and runtime metadata from storefront chat responses.
- Bridge replaces document payloads with shopper-safe fields only: `id`, `type`, `score`, `similarity`, `source`, `title`, `storefrontUrl`, `url`, and bounded `content`.
- Added storefront chat test coverage.

## Observations

### Speed

Fast paths from the first smoke:

- order/account action policy guidance: about 0.8-1.2s
- unsupported order mutation action policy guidance: about 0.8-1.1s
- internal implementation safe runtime answer: about 0.9-1.1s
- cart confirmation: about 3-4.5s

Slow paths:

- thinker catalog/policy search: about 7-19s
- comparison: about 13-16s

Main cause from runtime metadata before sanitization:

- `IntentExtraction` LLM call dominates the slow path.
- Product search then still performs retrieval after the LLM extraction step.

### Accuracy

Good:

- wax search returns `Selling Plans Ski Wax` with Shopify catalog source.
- noisy uppercase wax query still returns relevant catalog results.
- internal implementation question is answered safely without provider details.
- order/account page cart-action selections are now mapped to the correct order lookup/support posture.
- unsupported order mutation action selections are denied in customer-safe wording; approved order self-service packages can allow those actions with confirmation/audit.

Needs work:

- product search answers still say `Search completed.` instead of a useful shopper sentence.
- comparison surface in `navigator_deep` uses weak context and does not force product/catalog evidence.
- `What products are available under $20?` returns a search result but does not prove price filtering in the answer.
- allergy/certification asks for a source gap, but the answer should explicitly say there is no verified certification evidence in the store data.
- add-to-cart selects generic `shopify_update_cart` confirmation and says `Update your cart?`; it should first resolve a concrete product/variant and confirm `Add Selling Plans Ski Wax to cart?`.

### Reliability

Fixed:

- no server errors on the final probe smoke
- no Elite entitlement conflict
- no order lookup to cart misroute
- no public runtime metadata leak in the smoke

Needs work:

- all public probes should be run as a repeatable script in CI/staging gates, not only manually.
- downstream Platform/Runtime non-2xx responses should be shaped into customer-safe Bridge responses.
- storefront result payloads should include an explicit `evidenceQuality` or `sourceGap` field so the UI can render grounded/no-evidence states clearly.

## Optimization Backlog

### P0 Speed

1. Add structured storefront routing before expensive LLM extraction for explicit UI actions and high-confidence typed surfaces only:
   - product search
   - product compare
   - policy lookup
   - source-gap/certification question
   - order lookup block entry
2. For `thinker_deep` catalog searches, call Storefront MCP/search action first and use generation only after evidence is available.
3. Cache per-shop product/policy search results for short TTLs where no shopper identity is involved.
4. Target budgets:
   - policy/order lookup guidance: p95 < 1.5s
   - cart confirmation: p95 < 4s
   - catalog search: p50 < 4s, p95 < 8s
   - comparison/source-gap: p50 < 5s, p95 < 9s

### P0 Accuracy

1. Replace generic `Search completed.` with a shopper answer built from safe documents:
   - product title
   - product type/vendor when available
   - storefront link
   - concise next step
2. Force comparison surface to use product/catalog evidence, not only page context.
3. Add source-gap response template:
   - verified evidence found
   - no verified evidence found
   - ask merchant/support if the claim is critical
4. For cart add:
   - resolve product and variant before mutation
   - show product-specific confirmation copy
   - never ask for or expose `shopperSessionId`

### P0 Reliability And Security

1. Keep public storefront responses on a strict allowlist.
2. Map runtime/Platform `4xx` and `5xx` failures to customer-safe messages with operator-safe logs.
3. Add staging gate script for the query set in this plan.
4. Fail closed when order/customer/checkout auth is unavailable.
5. Keep Free hidden/disabled in package selection while preserving legacy records as Elite-default launch posture.
6. Do not add query-text phrase guards for refund/cancel/edit-order; use structured action IDs, explicit UI events, page context, and merchant-approved action package policy.

## Release Gate Additions

Add these checks to the 010 release gate:

- `FREE_DISABLED_ELITE_DEFAULT`: bootstrap and billing summary must report Elite/Active for unconfigured or legacy-Free staging stores.
- `QUERY_MATRIX_NO_500`: all listed probes return non-5xx.
- `QUERY_MATRIX_NO_ENTITLEMENT_CONFLICT`: no shopper query returns Elite entitlement conflict.
- `ORDER_CONTEXT_NO_CART_ROUTE`: account/order/support page runtime selections do not expose cart actions or `shopperSessionId`.
- `ORDER_MUTATION_ACTION_POLICY`: refund/cancel/order-edit action IDs are governed after runtime/action selection; unapproved stores are denied, approved stores may proceed only through configured Marketplace/MCP actions with confirmation/auth/audit.
- `STOREFRONT_RESPONSE_PUBLIC_SAFE`: public JSON contains no runtime auth context, deployment/tenant/customer IDs, raw document metadata, parameter schemas, provider diagnostics, or secret references.
- `CATALOG_RESULT_RENDERABLE`: catalog results include shopper-safe titles and storefront URLs where available.

## Next Implementation Slice

Implement structured storefront fast paths without shopper-text phrase matching:

- product search -> Storefront MCP/catalog evidence first, then answer synthesis
- comparison -> catalog evidence first, then bounded compare synthesis
- source-gap -> evidence/no-evidence answer template
- order lookup block entry -> order lookup guidance or direct order lookup flow, never cart action
- order mutation action IDs -> customer-safe support handoff unless a merchant-approved order self-service action package is active

The router should preserve governed action confirmation for true cart actions, but it should make the confirmation product-specific before calling `shopify_update_cart`.
