# Shopify Companion Context And Attachment Plan

Status: implementation-ready child plan linked to builder-mode roadmap (2026-04-24)

This document defines how Shopify Companion should handle shopper page context and card-level attachments when embedded intelligence surfaces hand work off to the Max widget.

It exists because the current code already has two real primitives:

- Shopify page/product/collection context flowing from the theme app extension into bridge chat
- Max widget attachment support for attached items inside the widget state

but it does **not** yet define one clean product contract for:

- page context versus selected target attachments
- attach controls on embedded intelligence cards
- optional attach controls on theme-native product/article cards

Read with:

- [SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md](../Strategy/SHOPIFY_COMPANION_BUILDER_MODE_SHIPPING_ROADMAP.md)
- [SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md](SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md)
- [SHOPIFY_COMPANION_FETCH_ONLY_INTELLIGENCE_PLAN.md](SHOPIFY_COMPANION_FETCH_ONLY_INTELLIGENCE_PLAN.md)
- [SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md](SHOPIFY_COMPANION_MAX_MODE_WIDGET_REFACTOR_PLAN.md)
- [SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md](SHOPIFY_COMPANION_SHELL_MODE_ENABLEMENT_PLAN.md)

---

## 1) Code-Validated Current State

What is already real:

- the Shopify theme app extension already extracts safe `pageType`, `pageTitle`, `product`, and `collection` context from Liquid/data attributes
- embedded intelligence surfaces already send that context with wrapper-originated requests
- the Shopify bridge already converts `storefrontContext` into a bounded runtime attachment for chat/suggestions
- the Max widget already supports pending attachments and attached items inside widget state
- the Max widget already has a public `attachProduct(...)` entry point in the storefront bundle

What is not real yet:

- a documented distinction between `page context` and `attached target`
- a generic host attachment API for non-product objects such as article, policy, or document
- attach controls on Companion-rendered cards
- any automatic attach icon on theme-native product cards or article cards

Operational truth:

- product detail pages already have automatic page context
- collection/search/listing cards do **not** automatically become attachments unless they are explicitly instrumented

---

## 2) Product Decision

The correct contract is:

- `page context` tells Max where the shopper currently is
- `attached target` tells Max what exact object the shopper wants to focus on

These are not the same thing.

Examples:

- shopper is on a product page
  - page context: that product page
  - no extra attached target required unless the shopper explicitly pins something else
- shopper is on a collection page and taps paperclip on one product card
  - page context: current collection page
  - attached target: that exact product
- shopper is reading a blog/article card and taps attach
  - page context: current page
  - attached target: that exact article/document

Decision rules:

- reuse the existing Max widget attachment system; do not create a parallel Shopify-only attachment store
- keep automatic page context for wrapper-originated prompts
- add explicit attach controls only where a concrete card/object exists
- do not assume attach icons appear automatically on every theme-native card
- keep attachment handoff compatible with the same prompt-first, text-first LLM wrapper model rather than inventing a second reasoning path

---

## 3) Contract

### 3.1 Page context

Page context remains a bounded Shopify wrapper payload.

Canonical fields:

- `pageType`
- `pageTitle`
- `product`
  - `id`
  - `handle`
  - `title`
  - `vendor`
  - `type`
  - `variantId`
  - `sku`
  - `priceCents`
- `collection`
  - `id`
  - `handle`
  - `title`
- `shopifyShellModeProfile`
- `shopifySurfaceEntry`

Rule:

- page context is sent automatically with wrapper prompts and shell handoff
- page context is not user-visible as a removable attachment chip unless the widget deliberately chooses to expose it later

### 3.2 Attached target

Attached targets are explicit shopper-selected objects added to the existing Max attachment model.

Minimum supported attachment types for first implementation:

- `product`
- `document`

Recommended first-wave document subtypes:

- article
- policy
- source

Canonical attachment shape:

```json
{
  "type": "product",
  "data": {
    "id": "gid://shopify/Product/123",
    "handle": "sony-alpha-1",
    "sku": "SKU-SON-60884",
    "title": "Sony Alpha 1",
    "priceCents": "649900",
    "imageUrl": "https://...",
    "storefrontUrl": "https://shop/products/sony-alpha-1"
  },
  "metadata": {
    "sourceSurface": "ai-search",
    "pageType": "collection",
    "pageTitle": "Cameras"
  }
}
```

Document shape:

```json
{
  "type": "document",
  "data": {
    "id": "article-123",
    "title": "Buying Guide: Best Sony Cameras",
    "content": "Short excerpt or resolved content",
    "documentType": "article",
    "url": "https://shop/blogs/guides/sony-cameras"
  },
  "metadata": {
    "sourceSurface": "grounding-sources",
    "pageType": "collection"
  }
}
```

### 3.3 Host API

Do not replace existing attachment support.

Required rule:

- preserve the current Max attachment behavior and compatibility with `attachProduct(...)`

Recommended host API:

- keep `window.MaxMode.attachProduct(product)` as a compatibility helper
- add `window.MaxMode.attachItem(item)` as the canonical generic host API

Behavior:

- `attachItem(...)` adds a pending attachment into the existing Max attachment state
- duplicate attachments are ignored safely
- `attach and ask` is implemented by `attachItem(...)` followed by `sendMessage(...)`

---

## 4) Surface Behavior Rules

### 4.1 Wrapper click

When a shopper clicks an embedded intelligence wrapper action:

- keep using page context
- keep sending `shopifySurfaceEntry`
- do not silently convert every click into a pinned attachment

### 4.2 Attach click

When a shopper taps the attach icon on a concrete card:

- add that object to the Max widget as an explicit attachment
- keep the page context unchanged
- optionally open/focus Max

Recommended UX variants:

- `Add to Max`
- `Add to Max and Ask`

Avoid:

- ambiguous paperclip-only behavior without label in admin demos/docs

### 4.3 Automatic attach icon behavior

Product rule:

- attach icons appear only on explicitly instrumented cards

That means:

- Companion-rendered cards: yes, once implemented
- theme-native product cards: no, not automatically
- theme-native article cards: no, not automatically

Theme-native rollout requires explicit instrumentation.

---

## 5) Implementation Scope

### Phase 1: Contract And Companion-Owned Cards

Must add:

- explicit documented `page context + attached target` contract
- generic `attachItem(...)` support while preserving existing `attachProduct(...)`
- attach controls on Companion-rendered product cards
- attach controls on Companion-rendered document/source/article cards where object identity is available
- `attach and ask` behavior using the same attachment system

Primary target surfaces:

- AI search matched products
- comparison cards
- grounding/source cards that resolve to concrete documents

Not required in Phase 1:

- automatic theme-wide attach icons
- merchant-configurable attach placement on arbitrary theme cards

### Phase 2: Theme-Native Card Instrumentation

Optional, bounded follow-on:

- define a theme snippet/helper contract for product cards and article cards
- expose safe data attributes for attachable objects
- render attach controls only when the theme opts in

This is not automatic and should not be implied by the Phase 1 product claim.

---

## 6) File Ownership

### Shopify theme app extension wrapper

Owns:

- page context extraction
- card-level attach button wiring for Companion-rendered cards
- calling `window.MaxMode.attachItem(...)` or compatibility helper
- `attach and ask` choreography

Primary files:

- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-app-embed.js`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/assets/companion-embedded-surfaces.js`

### Max widget

Owns:

- attachment state
- pending attachment storage
- duplicate attachment handling
- attachment chip rendering and removal
- compatibility between `attachProduct(...)` and generic `attachItem(...)`

Primary files:

- `max-mode-widget/src/entries/iife.ts`
- `max-mode-widget/src/mount.ts`
- relevant widget state/hooks/components inside `max-mode-widget/src`

### Shopify bridge

Owns:

- bounded page-context attachment normalization
- continued use of `storefrontContext` for wrapper-originated prompts

Non-goal for this plan:

- do not move explicit card attachments into a second bridge-owned attachment pipeline unless the shared widget/runtime contract requires it later

---

## 7) Verification Requirements

Must verify:

- product pages still send automatic page context
- collection/search/list pages do not claim per-card attach behavior unless attach controls are actually rendered
- Companion-rendered product cards can attach a product to Max
- Companion-rendered document/source cards can attach a document to Max when concrete object identity exists
- duplicate attach clicks do not create duplicate chips
- `attach and ask` preserves both page context and attached target
- existing `attachProduct(...)` behavior remains functional

Recommended additions:

- theme extension JS tests where available
- widget attachment-state tests
- Shopify verification checklist entry for companion-card attach controls

---

## 8) Acceptance Criteria

- one implementation-ready contract exists for `page context + attached target`
- existing Max widget attachment support is reused, not replaced
- product/detail pages keep automatic page context
- attach controls are available on at least one Companion-rendered product card surface
- attach controls are available on at least one Companion-rendered document/source surface when object identity is available
- docs explicitly state that theme-native card attach is opt-in instrumentation, not automatic magic

---

## 9) What We Should Not Do

Do not:

- overload page context to represent a selected card target
- invent a second Shopify-only attachment store
- claim attach icons appear on every product card automatically
- make attach support dependent on legacy chat UI
- make merchant-facing claims about article/policy attach before concrete card instrumentation exists
