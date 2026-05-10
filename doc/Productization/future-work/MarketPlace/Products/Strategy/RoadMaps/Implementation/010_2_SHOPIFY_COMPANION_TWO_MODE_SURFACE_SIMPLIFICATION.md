# 010.2 Shopify Companion Two-Mode Surface Simplification

Status: implementation roadmap (created 2026-05-10)

Owner mode: product simplification / storefront mode routing / merchant configuration LLM session

Roadmap phase: `010.2` - simplify Loom Companion into two customer-understandable Shopify storefront modes: shopping help and account/order resolution.

Priority: P0 follow-on to `010` and `010.1`. This plan does not replace the GTM, partner portal, merchant launch, staging, or production-promotion work. It makes the product easier to sell, configure, verify, and launch.

Parent plans:

- [010 GTM And Partner Portal Launch Readiness](010_GTM_AND_PARTNER_PORTAL_LAUNCH_READINESS.md)
- [010.1 Shopify Companion UI Launch Readiness](010_1_SHOPIFY_COMPANION_UI_LAUNCH_READINESS.md)

Related implementation references:

- [006 Thinker Resolver Governed Issue Resolution Blueprint](006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md)
- [006.4 Productized Resolution Assistant Readiness And Rollout](006_4_PRODUCTIZED_RESOLUTION_ASSISTANT_READINESS_AND_ROLLOUT.md)
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md`
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md`
- `Final_Documentation/User_Guides/THINKER_RESOLVER_USER_GUIDE.md`
- `Final_Documentation/Development_Guides/THINKER_RESOLVER_DEVELOPER_GUIDE.md`

Related code:

- `max-mode-widget`
- `product-services/shopify-bridge-service/ui`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/storefront`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/service/ShopifyStoreWidgetSettingsService.java`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/thinker`

---

## Executive Decision

Use one Shopify product with two simple modes.

```text
Loom Companion
  -> Companion Thinker: shopping pages
  -> Companion Resolver: account and order resolution pages
```

The customer-facing explanation is:

```text
Companion Thinker helps shoppers choose.
Companion Resolver helps customers resolve account, order, delivery, return, and support questions.
```

Do not sell Thinker and Resolver as separate Shopify apps, separate storefront widgets, or separate products during the 010 launch. They are two operating modes inside Loom Companion.

Do not expose raw technical modes such as `navigator`, `navigator_deep`, `thinker_deep`, `cart_assistant`, or `executor` to shoppers. Merchant and partner UI can use friendly labels, with raw mode details reserved for advanced/operator diagnostics.

---

## Product Boundary

### Companion Thinker

Customer-facing label:

```text
Shopping Assistant
```

Internal product label:

```text
Companion Thinker
```

Purpose:

- help shoppers decide what to buy
- answer product, collection, comparison, policy, FAQ, and content questions
- use grounded store content, RAG, Storefront MCP/read-action evidence, and product/page context
- stay read-first and shopper-safe

Primary pages:

- home / landing
- search
- collection
- product
- product FAQ
- comparison
- policy/content/blog/pages

Primary surfaces:

- AI search dock
- contextual pill
- product insight card
- product FAQ
- comparison block
- policy strip
- full shopping chat/depth panel

Allowed posture:

- product discovery
- product detail answers
- product comparison
- policy explanation
- grounded recommendations
- missing-evidence explanation
- support handoff suggestion when the shopper asks for account/order-specific help

Not allowed:

- refund/cancellation execution
- order modification
- checkout completion
- account-specific protected-data exposure
- arbitrary support-ticket automation

### Companion Resolver

Customer-facing label:

```text
Account & Order Assistant
```

Internal product label:

```text
Companion Resolver
```

Purpose:

- help customers resolve account, order, delivery, return, and support questions
- use customer/account/order surfaces only when Shopify auth, scopes, protected-data posture, package, support handoff, and live verification allow it
- create support handoff/escalation records where governed policy allows it

Primary pages:

- account
- order status
- contact/support
- returns/help
- cart or checkout-adjacent help only where the store has an entitled and verified action posture

Primary surfaces:

- order lookup block
- account/order help panel
- support handoff form
- return/refund policy explanation
- evidence-backed support escalation
- full resolution chat/depth panel when entitled and verified

Allowed current launch posture:

- explain order, shipping, returns, and refund policy from grounded store content
- perform customer-safe read-only order lookup only when store auth/scopes are ready
- create a support handoff/escalation with confirmation, audit, evidence, and assignment checks
- fail closed with merchant-safe guidance when auth, package, protected-data, or verification gates are missing

Not allowed for public launch claims:

- autonomous refund execution
- cancellation execution
- address changes
- order edits
- payment detail exposure
- unrestricted support-desk replacement

---

## Tier Packaging

### Free

Customer-facing package:

```text
AI Search
```

Product posture:

- Companion Thinker Lite
- AI search only
- shopping pages only
- no Companion Resolver
- no account/order lookup
- no governed actions

### Starter

Customer-facing package:

```text
Shopping Assistant
```

Product posture:

- Companion Thinker
- shopping pages and embedded shopping surfaces
- product discovery, product detail answers, product FAQ, comparison, policy/content answers
- partner/merchant evidence and verification
- no governed account/order resolution actions

### Elite

Customer-facing package:

```text
Shopping + Account Assistant
```

Product posture:

- Companion Thinker + Companion Resolver
- all Starter shopping surfaces
- account/order/support resolution surfaces when gates pass
- customer-safe order lookup only when Shopify auth/scopes/live verification are ready
- support handoff/escalation with policy, confirmation, evidence, audit, and assignment checks
- blocked capabilities show remediation instead of requiring founder intervention

---

## Page Routing Rules

Default page routing:

| Page group | Customer mode | Internal posture |
|---|---|---|
| home / landing | Shopping Assistant | Companion Thinker |
| search | Shopping Assistant | Companion Thinker |
| collection | Shopping Assistant | Companion Thinker |
| product | Shopping Assistant | Companion Thinker |
| product FAQ / comparison | Shopping Assistant | Companion Thinker |
| policy / content / blog | Shopping Assistant | Companion Thinker |
| cart | Account & Order Assistant only when gated; otherwise Shopping Assistant with handoff language | Companion Resolver gated fallback |
| account | Account & Order Assistant | Companion Resolver |
| order status | Account & Order Assistant | Companion Resolver |
| contact / support | Account & Order Assistant | Companion Resolver |
| returns / help | Account & Order Assistant | Companion Resolver |

Rules:

- shopper pages decide the mode automatically
- shoppers do not need a raw mode selector
- merchant UI configures surfaces and readiness, not low-level orchestration terms
- partner UI verifies the selected page routing and evidence for staging/production
- operator UI may inspect raw modes, policy, evidence, and execution state

---

## Configuration Model

Merchant-facing configuration should reduce to two cards:

```text
Shopping Assistant
Account & Order Assistant
```

### Shopping Assistant Settings

Merchant-visible controls:

- enabled / disabled
- enabled shopping surfaces
- source categories
- Knowledge Sync status
- shopping-page preview
- product FAQ / comparison placement
- policy/content coverage

Advanced/operator-backed controls:

- raw mode id
- read-action/RAG budgets
- action catalog details
- vector spaces
- runtime diagnostics

### Account & Order Assistant Settings

Merchant-visible controls:

- enabled / disabled
- readiness state
- customer account auth status
- Shopify scope status
- protected-data posture where applicable
- order lookup block placement
- support handoff destination
- support escalation availability
- blocked capability remediation

Advanced/operator-backed controls:

- Resolver preview/execution policy
- dry-run and confirmation contracts
- action family allowlist
- audit/export internals
- provider/runtime diagnostics

---

## Current Code Reality

The codebase already has most of the primitives:

- widget config supports `defaultConversationMode`, `allowedConversationModes`, `pageModeMappings`, and `enabledSurfaces`
- Shopify Bridge storefront bootstrap resolves page-aware effective modes
- Bridge currently promotes default/deep shopping modes to `thinker_deep` when chat fallback is enabled
- Thinker/Resolver persistence, policy, dry-run, and support escalation execution exist
- order lookup surface exists as a gated widget surface

Known simplification gaps:

- `ShopifyStoreWidgetSettingsService` does not currently allow `thinker_deep` as a merchant-configurable mode
- Shopify merchant UI still exposes technical mode labels such as `Navigator`, `Deep`, `Assistant`, and `Resolver`
- widget mode selector can expose raw mode labels to shoppers
- Resolver is still visually tied to `executor` instead of the simpler `Account & Order Assistant` concept
- page routing is available, but the canonical business routing is not yet expressed as the simple Thinker/Resolver page model
- account/order resolution copy must avoid refund/cancellation/support-desk automation claims

---

## Implementation Slices

### Slice 0 - Current-State Audit

Inspect:

- merchant widget settings UI
- storefront bootstrap mode resolution
- widget mode selector behavior
- allowed conversation mode validation
- page routing defaults
- order lookup/support surfaces
- package profile/tier gates

Deliver:

- exact current page routing map
- exact current merchant UI labels
- exact places where raw technical modes are shopper-visible
- exact backend validation gaps for `thinker_deep`
- exact Resolver/account-order gating gaps

### Slice 1 - Canonical Product Labels

Implement or update copy so normal merchant/partner/shopper surfaces use:

- `Shopping Assistant`
- `Account & Order Assistant`
- `Companion Thinker`
- `Companion Resolver`

Rules:

- shopper-facing UI should prefer `Shopping Assistant` and `Account & Order Assistant`
- merchant/partner UI may show `Companion Thinker` and `Companion Resolver` as branded mode names
- raw mode ids stay in advanced/operator diagnostics only

### Slice 2 - Backend Mode Validation And Routing

Implement or verify:

- `thinker_deep` is accepted by Shopify widget settings where entitled
- default shopping pages route to Companion Thinker
- account/order/support pages route to Companion Resolver only where entitled and verified
- if Resolver gates fail, account/order/support pages fail closed to safe handoff/policy guidance
- package profile truth controls available modes and surfaces

### Slice 3 - Merchant Configuration Simplification

Implement or verify merchant UI cards:

- Shopping Assistant
- Account & Order Assistant

Each card must show:

- enabled state
- available surfaces
- current readiness
- blocked capabilities
- remediation path
- preview/test action

Raw `Navigator`, `Deep`, `Assistant`, `Resolver`, `executor`, or `thinker_deep` selectors should move behind an Advanced section or disappear from normal merchant setup.

### Slice 4 - Storefront UI Simplification

Implement or verify:

- shopper does not see raw technical mode labels
- shopping pages show Shopping Assistant behavior
- account/order/support pages show Account & Order Assistant behavior only where gated
- Resolver surfaces explain what they can and cannot resolve
- no refund/cancellation/order-edit claims appear unless separately implemented and verified

### Slice 5 - Verification Packs

Create or update verification scenarios:

Shopping Assistant:

- search query
- product detail question
- comparison
- product FAQ
- policy/content question
- missing-evidence answer

Account & Order Assistant:

- order lookup ready path
- order lookup blocked path
- return/refund policy explanation
- support handoff creation
- support escalation evidence bundle
- revoked/unauthorized account-order access fails closed

---

## Release Gates

### `010_2_MODE_BOUNDARY_READY`

Required:

- product copy defines only two normal modes
- raw technical mode ids are not shopper-visible
- merchant normal setup shows Shopping Assistant and Account & Order Assistant
- package/tier mapping is clear and self-service

### `010_2_SHOPPING_ASSISTANT_READY`

Required:

- shopping pages route to Companion Thinker
- shopping surfaces produce grounded answers/evidence
- shopping mode remains read-first
- account/order-specific requests are redirected to safe Resolver/handoff posture

### `010_2_ACCOUNT_ORDER_ASSISTANT_READY`

Required:

- account/order/support pages route to Companion Resolver only when entitled and verified
- blocked auth/scope/protected-data states fail closed with remediation
- support handoff/escalation creates real governed records where allowed
- no refund/cancellation/order-edit execution is claimed or exposed

---

## Non-Goals

- Do not create a second Shopify app.
- Do not create separate Thinker and Resolver storefront widgets.
- Do not expose raw technical modes to shoppers.
- Do not market refunds, cancellations, order edits, payment help, or full support-desk automation.
- Do not add new broad write-action families in this plan.
- Do not make Resolver an unrestricted chatbot action executor.

---

## Final Output Of 010.2

010.2 is complete when Loom Companion can be explained, configured, routed, and verified as:

```text
Companion Thinker for shopping pages.
Companion Resolver for account and order resolution pages.
```

The implementation should leave merchants with two understandable setup choices, partners with two verification paths, and shoppers with one coherent assistant that behaves differently by page context without exposing internal mode names.
