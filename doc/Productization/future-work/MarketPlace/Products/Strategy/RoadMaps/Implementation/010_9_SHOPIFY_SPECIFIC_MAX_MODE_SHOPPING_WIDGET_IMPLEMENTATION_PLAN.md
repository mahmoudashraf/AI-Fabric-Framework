# 010.9 Shopify-Specific Max Mode Shopping Widget Implementation Plan

Status: proposed implementation plan, created 2026-05-23

Parent plans:

- [010.1 Shopify Companion UI Launch Readiness](010_1_SHOPIFY_COMPANION_UI_LAUNCH_READINESS.md)
- [010.2 Shopify Companion Two-Mode Surface Simplification](010_2_SHOPIFY_COMPANION_TWO_MODE_SURFACE_SIMPLIFICATION.md)
- [010.3 Shopify Companion Query Speed, Accuracy, And Reliability Optimization Plan](010_3_SHOPIFY_COMPANION_QUERY_SPEED_ACCURACY_RELIABILITY_OPTIMIZATION_PLAN.md)
- [010.5 LoomAI Canonical Runtime Bridge Contract Standardization Plan](010_5_LOOMAI_CANONICAL_RUNTIME_BRIDGE_CONTRACT_STANDARDIZATION_PLAN.md)
- [010.8 Shopify Companion Next Urgent Steps Readiness Plan](010_8_SHOPIFY_COMPANION_NEXT_URGENT_STEPS_READINESS_PLAN.md)

Input design materials:

- `/Users/mahmoudashraf/Downloads/chatmax_max_mode_shopify_design_spec.md`
- `/Users/mahmoudashraf/Downloads/chatmax_max_mode_optimized_images/chatmax_max_mode_optimized.html`
- `/Users/mahmoudashraf/Downloads/chatmax_max_mode_optimized_images/chatmax_max_mode_optimized_01_overview.png`
- `/Users/mahmoudashraf/Downloads/chatmax_max_mode_optimized_images/chatmax_max_mode_optimized_02_architecture.png`
- `/Users/mahmoudashraf/Downloads/chatmax_max_mode_optimized_images/chatmax_max_mode_optimized_03_idle_discovery.png`
- `/Users/mahmoudashraf/Downloads/chatmax_max_mode_optimized_images/chatmax_max_mode_optimized_04_browsing_results.png`
- `/Users/mahmoudashraf/Downloads/chatmax_max_mode_optimized_images/chatmax_max_mode_optimized_05_product_focus.png`
- `/Users/mahmoudashraf/Downloads/chatmax_max_mode_optimized_images/chatmax_max_mode_optimized_06_comparison.png`
- `/Users/mahmoudashraf/Downloads/chatmax_max_mode_optimized_images/chatmax_max_mode_optimized_07_policy_info.png`
- `/Users/mahmoudashraf/Downloads/chatmax_max_mode_optimized_images/chatmax_max_mode_optimized_08_cart_buying.png`
- `/Users/mahmoudashraf/Downloads/chatmax_max_mode_optimized_images/chatmax_max_mode_optimized_09_mobile_bottom_sheet.png`
- `/Users/mahmoudashraf/Downloads/ChatGPT Image May 23, 2026, 04_16_45 PM.png`
- `/Users/mahmoudashraf/Downloads/ChatGPT Image May 23, 2026, 04_24_11 PM (1).png`
- `/Users/mahmoudashraf/Downloads/ChatGPT Image May 23, 2026, 04_24_11 PM (2).png`
- `/Users/mahmoudashraf/Downloads/ChatGPT Image May 23, 2026, 04_24_12 PM (3).png`
- `/Users/mahmoudashraf/Downloads/ChatGPT Image May 23, 2026, 04_24_12 PM (4).png`
- `/Users/mahmoudashraf/Downloads/ChatGPT Image May 23, 2026, 04_24_12 PM (5).png`

Official Shopify references checked:

- Shopify Ajax Cart API: `https://shopify.dev/docs/api/ajax/reference/cart`
- Shopify API authentication and Storefront API access tokens: `https://shopify.dev/docs/api/usage/authentication`
- Shopify app proxies: `https://shopify.dev/docs/apps/build/online-store/app-proxies`
- Shopify app proxy authentication: `https://shopify.dev/docs/api/shopify-app-react-router/latest/authenticate/public/app-proxy`

## Goal

Build a Shopify-specific Max Mode widget that turns Loom Companion into a full shopping experience, not a generic assistant panel.

The widget should use Shopify directly when the operation is a normal same-store shopper UI operation, and use LoomAI/Bridge/AI when the operation requires language understanding, grounding, protected data, governed actions, or audit.

The result should feel like the supplied proposal:

- desktop: a three-zone shopping workspace
- mobile: a full-screen or tall bottom-sheet shopping assistant
- every state: shoppable, context-aware, and action-ready

## Product Position

This is a Shopify-specific product surface. It should not force ProdUS or other generic runtime deployments to adopt Shopify UI assumptions.

Implementation should keep the existing generic Max Mode infrastructure reusable, but introduce a first-class Shopify workspace shell selected by Shopify config.

Recommended config switch:

```ts
host: {
  experience: "shopify-shopping-workspace",
  assistantLabel: "Max Mode",
  showUtilityPanel: true
}
```

The Shopify theme extension should initialize this experience by default for Loom Companion stores.

## Design Interpretation

### Canonical Desktop Target

Use the optimized desktop concept images as the main implementation reference:

- `chatmax_max_mode_optimized_01_overview.png`: six desktop states
- `chatmax_max_mode_optimized_02_architecture.png`: context rail, conversation workspace, action panel
- `chatmax_max_mode_optimized_03_idle_discovery.png`: discovery/first open
- `chatmax_max_mode_optimized_04_browsing_results.png`: browsing/results
- `chatmax_max_mode_optimized_05_product_focus.png`: product focus
- `chatmax_max_mode_optimized_06_comparison.png`: comparison
- `chatmax_max_mode_optimized_07_policy_info.png`: policy/info
- `chatmax_max_mode_optimized_08_cart_buying.png`: cart/buying

Desktop structure:

```text
Top bar:
  Store name | search | Max Mode | cart | close

Body:
  Left rail | Center conversation | Right action panel

Bottom:
  Sticky composer inside the center column
```

### Canonical Mobile Target

Use the mobile proposal images as the mobile implementation reference:

- `ChatGPT Image May 23, 2026, 04_16_45 PM.png`: mobile multi-state overview
- `ChatGPT Image May 23, 2026, 04_24_11 PM (1).png`: discovery/first open
- `ChatGPT Image May 23, 2026, 04_24_11 PM (2).png`: browse/results
- `ChatGPT Image May 23, 2026, 04_24_12 PM (3).png`: product focus
- `ChatGPT Image May 23, 2026, 04_24_12 PM (4).png`: comparison
- `ChatGPT Image May 23, 2026, 04_24_12 PM (5).png`: cart/buying
- `chatmax_max_mode_optimized_09_mobile_bottom_sheet.png`: bottom-sheet behavior and storefront context

Mobile structure:

```text
Header:
  close | Max Mode | menu

Content:
  one active shopping state at a time
  stacked cards and horizontally scrollable product strips where useful

Bottom:
  sticky composer above safe-area
```

## Existing Code Foundation

Current relevant files:

- `max-mode-widget/src/components/MaxModeView.tsx`
- `max-mode-widget/src/components/MaxModePage.tsx`
- `max-mode-widget/src/components/MaxModeView/MaxModeMainContent.tsx`
- `max-mode-widget/src/components/MaxModeView/MaxModeComposerBar.tsx`
- `max-mode-widget/src/components/Chat/MessageList.tsx`
- `max-mode-widget/src/components/Chat/MessageBubble.tsx`
- `max-mode-widget/src/components/DesktopContextPanel.tsx`
- `max-mode-widget/src/components/MobileContextSheet.tsx`
- `max-mode-widget/src/components/ActionResultRenderer.tsx`
- `max-mode-widget/src/hooks/useMaxModeController.ts`
- `max-mode-widget/src/hooks/useChatFlow.ts`
- `max-mode-widget/src/hooks/useCartController.ts`
- `max-mode-widget/src/integrations/shopify.ts`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-max-mode-shell.js`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/snippets/companion-widget-bootstrap.liquid`

Current strengths:

- the widget already has chat, composer, RAG/source documents, cart controller, product detail panel, mobile context sheet, confirmation handling, and debug inspector
- the Shopify theme extension already bootstraps page, product, collection, and shell context
- `max-mode-widget/src/integrations/shopify.ts` already has same-origin Shopify cart helpers and product-page extraction

Current gap:

- the widget is still shaped like generic chat with optional panels
- the new proposal requires a Shopify state-aware shopping workspace

## Direct Shopify vs AI Decision Matrix

### Use Shopify Directly From The UI

Use direct Shopify calls when the shopper is taking an explicit UI action against public/current-session storefront state and no AI reasoning, protected data, or marketplace governance is needed.

Allowed direct UI operations:

- read current cart with locale-aware `cart.js`
- add a known selected variant to cart with locale-aware `cart/add.js`
- change/remove current cart lines with locale-aware `cart/change.js` or `cart/update.js`
- redirect shopper to Shopify checkout
- read current page/product/collection context from Liquid bootstrapped data attributes
- read public product JSON for the current product or selected public products when same-origin access is available
- open product, collection, policy, and cart URLs in the Shopify storefront
- maintain local recently viewed products in browser storage

Rules:

- only use direct cart mutation when the user clicked an explicit UI control such as `Add`, `Remove`, `Quantity`, or `Checkout`
- direct add-to-cart must use a validated numeric Shopify variant ID
- direct UI must show selected variant, price, and stock/availability state before mutation
- direct UI must never use Admin API credentials, private Storefront tokens, Customer Account tokens, Bridge API keys, or MCP credentials
- direct UI must use Shopify locale-aware roots, not hardcoded `/cart.js` when `window.Shopify.routes.root` is available

### Use LoomAI / Bridge / AI

Use LoomAI when the operation needs language understanding, retrieval, recommendation, governed execution, audit, protected customer data, or cross-source reasoning.

AI/Bridge operations:

- natural language product search and recommendations
- product comparison reasoning
- policy Q&A grounded in indexed policy/store data
- “what should I buy” style intent understanding
- multi-product recommendation and ranking
- customer-owned order, return, refund, store-credit, or account flows
- any action requiring Customer Account OAuth
- any action requiring MCP tools/call
- any action requiring confirmation and audit
- any answer requiring RAG evidence/debug trace
- support fallback or escalation guidance

Rules:

- AI selects and explains, UI presents and confirms
- AI must not silently mutate cart or checkout
- Bridge remains the governed action/audit boundary for natural-language actions
- protected customer data never moves through browser-only direct calls

### Hybrid Operations

Some operations need both.

Recommended hybrid examples:

1. Product recommendation:

```text
User asks naturally -> Bridge/AI retrieves and ranks products -> UI renders product cards -> shopper clicks View/Add/Compare
```

2. Direct UI add-to-cart:

```text
Shopper clicks Add on a product card with known variant -> UI calls Shopify Ajax cart -> UI updates cart panel
```

3. Natural-language add-to-cart:

```text
User asks "Add Selling Plans Ski Wax to my cart" -> Bridge/AI resolves product/variant and asks confirmation -> after confirmation either:
  A. Bridge executes governed MCP action and returns canonical result, or
  B. Bridge returns a browser-executable cart operation with audit token; UI performs Shopify Ajax and posts the result back for audit
```

Option A is safer with the current architecture. Option B can be added later if Shopify MCP cart tools remain weaker than browser Ajax for current-session cart state.

## Shopify-Specific Architecture

### New UI Shell

Add a Shopify-specific shell under `max-mode-widget/src/shopify/`:

```text
max-mode-widget/src/shopify/
  ShopifyShoppingWorkspace.tsx
  ShopifyWorkspaceHeader.tsx
  ShopifyDesktopWorkspace.tsx
  ShopifyMobileWorkspace.tsx
  ShopifyContextRail.tsx
  ShopifyConversationWorkspace.tsx
  ShopifyActionPanel.tsx
  ShopifyWorkspaceState.ts
  ShopifyDirectClient.ts
  ShopifyEvidenceAdapter.ts
  components/
    ProductCard.tsx
    ProductSpotlightCard.tsx
    ProductGalleryCard.tsx
    VariantSelector.tsx
    ComparisonTable.tsx
    PolicyDetailsCard.tsx
    CartSummaryCard.tsx
    SuggestedAddOnCard.tsx
    StockBadge.tsx
    PriceBlock.tsx
```

`MaxModeView` should select the Shopify shell only when the host config requests it. Generic Max Mode remains available for non-Shopify deployments.

### State Model

Introduce a UI-only Shopify workspace state:

```ts
type ShopifyWorkspaceState =
  | "discovery"
  | "browsing"
  | "product_focus"
  | "comparison"
  | "policy"
  | "cart_buying";
```

State should be derived from structured signals, not text matching.

State inputs:

- `host.requestContext.pageType`
- `host.requestContext.shopifyPageModeGroup`
- `selectedProduct`
- `isCartView`
- `cartData`
- latest canonical response type
- latest RAG documents
- latest action result
- explicit UI interaction such as clicking Compare, Cart, Policy, Product

Initial state mapping:

| Shopify context | Initial state |
|---|---|
| home/index | `discovery` |
| collection/search | `browsing` |
| product | `product_focus` |
| cart | `cart_buying` |
| account/order/support | `policy` or governed account flow |
| article/page/blog | `policy` |

### Data Adapters

Add adapters that transform current canonical data into commerce UI view models.

```ts
type ShopifyProductViewModel = {
  id: string;
  productGid?: string;
  variantId?: number;
  variantGid?: string;
  title: string;
  url?: string;
  imageUrl?: string;
  price?: string;
  compareAtPrice?: string;
  savingsLabel?: string;
  available?: boolean;
  stockLabel?: string;
  vendor?: string;
  productType?: string;
  sku?: string;
  options?: ShopifyProductOptionViewModel[];
  rating?: string;
  reviewCount?: number;
  evidenceSource: "liquid" | "cart" | "rag" | "action" | "shopify-direct";
};
```

Adapter sources:

- Liquid bootstrap context
- Shopify direct product/cart responses
- Bridge canonical `sources`
- Bridge canonical `ragResponse.documents`
- Bridge canonical `actions`
- current cart state

Never invent price, availability, rating, shipping, or discount data. If a field is missing, hide that specific UI detail or mark it as unknown.

## UI State Details

### Discovery

Reference:

- `chatmax_max_mode_optimized_03_idle_discovery.png`
- `ChatGPT Image May 23, 2026, 04_24_11 PM (1).png`

Layout:

- left rail: collections and quick asks
- center: store-aware welcome card and starter prompts
- right panel: product spotlight and trending products
- mobile: welcome card, collection grid, quick asks, featured pick, recently viewed

Data:

- collections from shell config or Bridge bootstrap
- starter prompts from runtime shell config
- featured product from latest indexed/store product evidence or configured fallback
- recently viewed from browser storage

Direct Shopify:

- open product/collection links
- store recently viewed locally

AI:

- quick asks
- recommendations
- policy questions

### Browsing / Results

Reference:

- `chatmax_max_mode_optimized_04_browsing_results.png`
- `ChatGPT Image May 23, 2026, 04_24_11 PM (2).png`

Layout:

- left rail: filters, sort, price, stock
- center: user query, AI response, product cards, follow-up chips
- right panel: product spotlight with variant controls and CTAs
- mobile: user bubble, AI response, horizontal product cards, filter chips, spotlight card

Direct Shopify:

- open product URLs
- add exact selected variant from card after user click
- optional public product JSON for currently selected product details

AI:

- product search and ranking
- “best for” explanation
- filter-aware recommendations if filters are expressed as query context

### Product Focus

Reference:

- `chatmax_max_mode_optimized_05_product_focus.png`
- `ChatGPT Image May 23, 2026, 04_24_12 PM (3).png`

Layout:

- left rail: selected product summary, variants, stock, view in store
- center: product explanation and recommendation reasoning
- right panel: gallery, details, shipping facts
- mobile: gallery, price/sale, rating, variants, reasoning card, Compare/View CTAs

Direct Shopify:

- read current product Liquid/page JSON
- variant selection
- view in store
- explicit add button for known selected variant

AI:

- recommendation reasoning
- “is this good for me” questions
- compare/similar-products queries

### Comparison

Reference:

- `chatmax_max_mode_optimized_06_comparison.png`
- `ChatGPT Image May 23, 2026, 04_24_12 PM (4).png`

Layout:

- left rail: selected products and AI pick
- center: comparison answer and table
- right panel: visual comparison and verdict
- mobile: stacked selected product cards, comparison table, AI verdict, follow-up prompts

Direct Shopify:

- open product pages
- add exact selected variant only after explicit user action

AI:

- comparison reasoning
- verdict
- follow-up prompts

### Policy / Info

Reference:

- `chatmax_max_mode_optimized_07_policy_info.png`

Layout:

- left rail: Shipping, Returns, Payment, FAQ
- center: conversational answer
- right panel: structured policy details
- mobile: stacked policy answer and structured details

Direct Shopify:

- open policy pages when available

AI:

- grounded policy Q&A from indexed policy data
- source-backed answers through RAG
- uncertainty handling when policy evidence is missing

### Cart / Buying

Reference:

- `chatmax_max_mode_optimized_08_cart_buying.png`
- `ChatGPT Image May 23, 2026, 04_24_12 PM (5).png`

Layout:

- left rail: current picks and add-ons
- center: purchase intent, confirmation, success banner
- right panel: cart summary, checkout CTA, suggested add-ons
- mobile: cart summary card, secure checkout, delivery/trust cards, add-ons

Direct Shopify:

- read cart
- add known selected variant after explicit click
- remove/change quantity
- checkout redirect

AI:

- “what else do I need” recommendations
- natural-language add-to-cart resolution
- confirmation-required action flow
- support fallback when cart operation fails

## Direct Shopify Client

Add `ShopifyDirectClient` with locale-aware routes.

Required methods:

```ts
getCart(): Promise<ShopifyCartViewModel>
addVariant(variantId: number, quantity: number): Promise<ShopifyCartViewModel>
changeLine(lineKeyOrVariantId: string | number, quantity: number): Promise<ShopifyCartViewModel>
updateCart(updates: Record<string, number>): Promise<ShopifyCartViewModel>
getCurrentProduct(handle: string): Promise<ShopifyProductViewModel | null>
goToCheckout(): void
```

Implementation rules:

- prefer `window.Shopify.routes.root` for locale-aware paths
- fall back to `/` only when route root is unavailable
- never call cross-origin Shopify cart endpoints
- never send LoomAI secrets or Bridge secrets to Shopify Ajax endpoints
- normalize errors into shopper-safe UI errors

## Bridge / AI Contract Usage

The widget should continue to use the canonical Bridge chat contract:

```json
{
  "query": "What should I buy for travel?",
  "conversationId": "chat-...",
  "position": "landing",
  "mode": "thinker_deep",
  "context": {
    "pageType": "index",
    "shopifyPageModeGroup": "landing",
    "shopifyEffectiveConversationMode": "thinker_deep"
  }
}
```

Response usage:

- top-level `answer`/`safeSummary`: center chat answer
- top-level `sources`: RAG/source documents
- top-level `ragResponse`: context/debug panel and document cards
- top-level `actions`: action cards, confirmation, customer-account connect, next-step buttons
- top-level `debug`: debug inspector only when enabled

The widget must not depend on legacy `result.sanitizedPayload` as its primary contract. It can retain an adapter only for old nested shapes while Shopify-specific rendering uses canonical top-level fields.

## Shopify App Proxy Position

Use app proxy or Bridge when browser direct access is not enough.

Good app proxy uses:

- server-side Storefront GraphQL without exposing private tokens
- Shopify-authenticated storefront dynamic data
- customer-aware storefront proxy context where Shopify supplies `logged_in_customer_id`
- lightweight JSON/Liquid data feeds for widget bootstrap

Do not use app proxy as a loophole for unreviewed protected customer data or private Admin operations.

## Security And Privacy Rules

- no Admin API token in browser
- no private Storefront token in browser
- no Customer Account token in browser unless it is a Shopify-approved customer-bound browser token for the intended flow
- no Bridge admin API key in browser
- no MCP secret in browser
- no debug secrets in shopper UI
- no protected customer data in local storage
- cart and recently viewed local state may be stored with short-lived/browser-local scope
- debug mode remains store/operator gated

## Implementation Slices

### Slice 0: Current-State Audit

Deliverables:

- inventory current widget components and what can be reused
- inventory current Shopify theme bootstrap data
- inventory direct Shopify helpers in `max-mode-widget/src/integrations/shopify.ts`
- confirm current deployed storefront payload contract

Verification:

```bash
npm --prefix max-mode-widget run typecheck
bash -n scripts/verify-shopify-companion-max-widget-live.sh
```

### Slice 1: Shopify Workspace Shell Selection

Deliverables:

- add `host.experience = "shopify-shopping-workspace"`
- add `ShopifyShoppingWorkspace`
- keep generic `MaxModeView` path for non-Shopify deployments
- update Shopify theme shell to request the Shopify workspace by default

Verification:

```bash
npm --prefix max-mode-widget run typecheck
npm --prefix max-mode-widget run build:iife
```

### Slice 2: Shopify Direct Client And View Models

Deliverables:

- add `ShopifyDirectClient`
- normalize cart, product, variant, price, availability, and URL models
- use locale-aware Shopify Ajax paths
- map Liquid bootstrap data into view models

Verification:

- unit tests for route building
- unit tests for cart response normalization
- unit tests for product response normalization
- live browser smoke for cart read on staging

### Slice 3: Desktop Three-Zone Workspace

Deliverables:

- top bar
- left context rail
- center conversation workspace
- right action panel
- desktop discovery, browsing, product, comparison, policy, and cart panels

Reference targets:

- images 7-14
- optimized HTML architecture and state screens

Verification:

- Playwright desktop screenshots at `1440x900` and `1600x1200`
- no overlap between rails, chat, composer, and panels
- composer always visible
- panel content remains scrollable without covering checkout/product CTAs

### Slice 4: Mobile Shopping Workspace

Deliverables:

- mobile header
- full-screen or tall bottom-sheet shell
- mobile discovery, browsing, product, comparison, policy, and cart states
- sticky composer above safe-area

Reference targets:

- images 1-6 and 15

Verification:

- Playwright mobile screenshots at iPhone-sized viewports
- keyboard-safe composer behavior
- minimum 44px touch targets
- no obscured checkout/add buttons

### Slice 5: Rich Commerce Renderers

Deliverables:

- product result cards
- product spotlight
- variant selector
- comparison table
- AI verdict card
- policy detail card
- cart summary card
- suggested add-ons
- trust/status badges

Data sources:

- RAG documents
- action results
- cart state
- Liquid product context
- direct Shopify product/cart calls

Verification:

- renderer tests with complete and partial data
- missing price/stock/rating fields hide cleanly
- no fake price/shipping/rating fallback

### Slice 6: Direct Cart UI And Governed Chat Action Split

Deliverables:

- direct add/remove/change/checkout for explicit UI clicks
- chat-driven cart intents remain governed through Bridge confirmation
- cart panel refreshes after direct Shopify cart mutation
- optional audit event for direct UI cart action through Bridge storefront event endpoint

Verification:

- direct click add known variant updates cart
- remove item updates cart
- checkout CTA opens Shopify checkout
- natural-language add-to-cart still follows confirmation/governed path

### Slice 7: Debug, RAG, And Release Evidence

Deliverables:

- debug inspector works with Shopify workspace
- RAG/source panel works in both desktop and mobile
- action diagnostics remain available only in debug mode
- no secrets in debug output

Verification:

```bash
npm --prefix max-mode-widget run typecheck
npm --prefix max-mode-widget run build:iife
bash product-services/shopify-bridge-service/deploy/shopify/sync-max-mode-widget.sh
bash -n scripts/verify-shopify-companion-max-widget-live.sh
bash -n scripts/verify-shopify-companion.sh
```

### Slice 8: Live Staging Verification

Deliverables:

- deploy current widget and Bridge to staging
- verify `https://shop-staging.loomai.pro/?country=US`
- run answer-quality repeat gate
- record screenshot/evidence output

Verification:

```bash
SHOPIFY_BRIDGE_BASE_URL=https://shopify-bridge-staging.46.224.145.148.sslip.io \
SHOP_DOMAIN=shopping-companion-test.myshopify.com \
scripts/verify-shopify-companion-answer-quality-repeats.sh
```

## Test Query Coverage

Minimum live query set after implementation:

- `Show me your best sellers`
- `What should I buy for travel?`
- `Find ski wax products for me`
- `Show me ski products under $100`
- `Tell me more about this product`
- `Compare these two products`
- `What is your shipping policy?`
- `What is your return policy?`
- `Add Selling Plans Ski Wax to my cart`
- `What's in my cart?`
- `I want to return my last order`
- `Did your MCP tool fail or can you still help me find products?`

Expected proof:

- correct UI state selected
- RAG/action evidence shown when available
- direct Shopify operations only for explicit UI cart controls
- AI/Bridge operations for natural language and protected/governed flows
- no internal provider/runtime/MCP wording in shopper-visible answers

## Release Acceptance

Pass this plan only when:

- Shopify workspace is enabled by default for Loom Companion Shopify stores
- desktop matches the three-zone design closely enough for design review
- mobile matches the full-screen/bottom-sheet design closely enough for design review
- direct Shopify cart UI works on staging
- AI chat still works on staging
- RAG and debug panels still work
- current answer-quality repeat gate passes
- no generic ProdUS/runtime widget behavior is broken

## Open Decisions

1. Direct browser Storefront API

Default recommendation: do not use browser Storefront GraphQL in the first slice unless a public Storefront access token is intentionally configured and only public data is requested. Prefer Liquid bootstrap, Shopify Ajax cart, Bridge, or app proxy.

2. Natural-language add-to-cart execution

Default recommendation: keep natural-language cart mutation governed through Bridge for launch. Add browser-executable audited cart operations later only if Shopify MCP cart tools remain weaker than same-session Ajax cart operations.

3. Ratings/reviews

Default recommendation: render ratings only when supplied by indexed evidence, action evidence, or an installed reviews integration. Do not invent review counts.

4. Shipping estimates

Default recommendation: show only policy-level shipping facts or checkout/cart-provided shipping facts. Do not claim exact shipping rates until Shopify can calculate them for the shopper context.
