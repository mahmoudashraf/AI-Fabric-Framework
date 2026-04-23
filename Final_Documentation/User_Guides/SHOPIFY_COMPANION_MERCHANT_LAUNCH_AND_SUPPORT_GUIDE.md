# Shopify Companion Merchant Launch And Support Guide

Status: merchant-facing launch and support guide for the current live Shopify Companion build (2026-04-23)

This guide is for:

- Shopify store admins
- design partners
- internal launch/support teams working with a merchant

Use it to operate the current live product safely.

Related guides:

- [Shopify Companion Customer Capabilities Guide](./SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md)
- [Shopify Companion Developer And Store Admin Guide](../Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md)
- [Shopify Companion Launch, Review, And Support Exports Guide](../Development_Guides/SHOPIFY_COMPANION_LAUNCH_REVIEW_AND_SUPPORT_EXPORTS_GUIDE.md)

---

## 1) What Merchants Can Do Now

Inside the embedded Shopify admin app, the merchant can:

- connect the store and recover install/auth posture
- choose bounded source categories
- run source preflight
- bootstrap and go live
- reconcile indexing support
- run indexing and bounded reindex flows
- preview storefront placement guidance
- review tier posture and billing state
- review webhook and live-update health
- use the merchant playground
- inspect store intelligence health
- inspect launch and App Review readiness
- export launch, review, support, and lifecycle packets

The merchant should not need to edit plugins, deployment drafts, or secret material.

---

## 2) Standard Merchant Flow

### 2.1 Initial launch flow

Use this order:

1. Install or reconnect the app.
2. Confirm the merchant session resolves for the correct shop.
3. Choose the source categories that should be included.
4. Run source preflight.
5. Bootstrap the store if it is not already bootstrapped.
6. Review billing posture and tier allowance.
7. Run go-live when the store is eligible.
8. Open storefront preview and theme placement guidance.
9. Enable the theme app extension and place the intended blocks.
10. Run indexing or reindexing only after readiness and reconcile state are clean.

### 2.2 After scope changes

If the merchant changes source scope:

1. Save source settings.
2. Re-run source preflight.
3. Reconcile deployment support if requested by the app.
4. Index all enabled data.
5. Recheck storefront readiness and launch posture.

---

## 3) Understanding Source Coverage

The current merchant source categories are:

- products
- collections
- pages
- policies
- articles
- metaobjects

What they mean:

- products and collections feed shopper discovery and product guidance
- pages and policies support explanation-oriented answers
- articles and metaobjects extend richer content depth for support-policy style grounding

The product can also detect compatible review/rating metafields when present.

Important rule:

- treat review-aware grounding as detected store evidence, not as a promise that every provider is always available

---

## 4) Understanding The Storefront Surface Set

The current merchant-placeable surface set is:

- AI search
- contextual pill
- product insight
- policy strip
- product FAQ
- comparison

Use them this way:

- AI search is the Free-tier storefront wedge
- contextual pill keeps guided prompts visible inline
- product insight and policy strip help the shopper at the product decision point
- product FAQ and comparison reduce the need to open full chat for common questions
- chat remains the fallback depth path

---

## 5) Launch And Review Exports

The merchant app now provides live-generated exports for:

- support bundle
- launch dossier
- App Store listing package
- design-partner rollout packet
- App Review guide
- review screencast script
- support runbook
- lifecycle and subscription packet

Use them this way:

- use the launch dossier and App Store package when packaging the product story
- use the App Review guide and screencast script when preparing reviewer-safe material
- use the support bundle and support runbook when troubleshooting or handing off support
- use the lifecycle and subscription packet when checking install, billing, webhook, sync, and release posture together

---

## 6) Store Intelligence Health And ROI

The merchant app now surfaces bounded intelligence signals such as:

- top shopper questions
- per-surface usage
- shopper journey summaries
- ROI posture
- strongest surfaces
- rollout recommendations

Use these signals to:

- confirm that shoppers are actually using the storefront surfaces
- see which surfaces are producing the strongest evidence
- package merchant value for design partners or launch review

Do not use them as if they are a full sales or revenue reporting system.

---

## 7) Support And Return Boundaries

Shopify Companion can explain published policy posture.

It cannot currently:

- look up a specific order safely
- inspect shipping or order status
- approve or execute a refund
- cancel an order
- process a return against a real order record

Safe rule:

- use Companion for general policy-grounded guidance
- hand off any order-specific return, refund, tracking, or cancellation question to the merchant support channel

Recommended handoff language:

`I can explain the store’s published policy, but I cannot inspect or change your specific order. Please continue with the merchant support channel for order-specific help.`

---

## 8) Common Merchant Checks

Before launch:

- confirm storefront readiness is green
- confirm billing is not blocking go-live
- confirm webhook posture is ready
- confirm live updates are healthy
- confirm the intended surface set is visible in the theme
- confirm launch and App Review readiness is clean

If something looks wrong:

- export the support bundle first
- review the support runbook
- review the lifecycle and subscription packet
- escalate with those artifacts instead of guessing

---

## 9) What Not To Promise

Do not promise:

- autonomous checkout
- order lookup
- refund or cancellation execution
- universal review-provider support
- full support-desk automation

The honest current product story is:

- embedded storefront intelligence
- grounded discovery and policy guidance
- bounded merchant launch, review, and support tooling
