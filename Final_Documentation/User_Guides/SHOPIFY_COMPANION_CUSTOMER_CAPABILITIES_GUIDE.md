# Shopify Companion Customer Capabilities Guide

Status: customer-facing capability guide for the current live Shopify Companion build (2026-04-23)

This guide describes what Shopify Companion can do now in the current live product shape.

Use it when explaining the product to:

- merchants
- design partners
- internal sales or solution teams
- launch and review stakeholders

Do not use it to describe roadmap items that are not live yet.

Related guides:

- [Shopify Companion Merchant Launch And Support Guide](./SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md)
- [Shopify Companion Developer And Store Admin Guide](../Development_Guides/SHOPIFY_COMPANION_DEVELOPER_AND_STORE_ADMIN_GUIDE.md)
- [Shopify Internal Development And Full Deployment Guide](../Development_Guides/SHOPIFY_INTERNAL_DEVELOPMENT_AND_FULL_DEPLOYMENT_GUIDE.md)

---

## 1) Product Summary

Shopify Companion is an embedded, read-first storefront intelligence product for Shopify stores.

The current live product shape is:

- embedded intelligence first
- chat as fallback and depth
- platform-backed merchant onboarding and verification
- bounded merchant controls inside Shopify admin

The current product is best described as:

- AI search for store discovery
- grounded product and policy guidance
- embedded product-page intelligence
- merchant-visible readiness, launch, and support tooling

It is not a checkout bot, support desk replacement, or autonomous commerce agent.

---

## 2) Shopper-Facing Capabilities Available Now

### 2.1 Embedded storefront surfaces

The current live storefront surface inventory includes:

- AI search
- contextual pill
- product insight
- policy strip
- product FAQ
- comparison
- launcher/chat shell for deeper follow-up

These surfaces are delivered through the Shopify theme app extension and merchant-placeable app blocks.

### 2.2 Product discovery and product guidance

Shoppers can use Companion to:

- discover products from natural-language requests
- inspect product details and availability signals
- compare products with grounded evidence
- find similar products
- get policy-aware product guidance before opening a full chat flow

### 2.3 Grounded source coverage

The current live grounding posture can draw on:

- products
- collections
- pages
- policies
- published articles
- enabled metaobject content
- compatible Shopify review and rating metafields when present

Compatible review-aware metadata currently includes supported provider patterns such as:

- Judge.me
- Stamped
- Okendo
- Loox
- Yotpo
- Shopify Product Reviews

Important boundary:

- review-aware grounding depends on compatible metadata being present in the merchant store
- Companion should not be described as supporting every review provider universally

### 2.4 Policy and return guidance

Companion can explain published store policies and use them for general return or refund guidance.

Current rule:

- Companion can explain policy-grounded return posture
- Companion cannot inspect or change a specific order
- order-specific return, refund, cancellation, or tracking issues must be handed off to the merchant support channel

### 2.5 Optional governed commerce posture

The platform now supports an optional Elite governed-commerce posture for bounded action families such as:

- add to cart
- cart update
- variant guidance

This is not the default product story for every store.

It should only be described when:

- the store is on an entitled plan
- the merchant-facing billing posture shows the capability
- the flow uses explicit confirmation and audit history

---

## 3) Merchant-Facing Capabilities Available Now

### 3.1 Embedded Shopify admin app

The merchant-facing embedded app currently exposes:

- merchant session and install state
- source-category controls
- source preflight and readiness
- bootstrap and go-live path
- storefront preview and placement guidance
- widget settings and shell profile
- webhook visibility
- billing posture and tier ladder
- merchant playground
- store intelligence health
- launch and App Review readiness
- bounded diagnostics and export tooling

### 3.2 Source-category controls

Merchants can currently enable or disable bounded source categories:

- products
- collections
- pages
- policies
- articles
- metaobjects

These are business-scope controls, not raw data-engineering controls.

Merchants do not manage:

- plugin wiring
- vector schema design
- secret refs
- runner infrastructure

### 3.3 Merchant launch and support exports

The merchant app now exports live-generated packages based on the current store posture:

- support bundle
- launch dossier
- App Store listing package
- design-partner rollout packet
- App Review guide
- review screencast script
- support runbook
- lifecycle and subscription packet

These exports are meant to keep launch, review, and support language aligned with the actual live store configuration.

### 3.4 Shopper analytics and ROI evidence

The merchant app now surfaces bounded intelligence signals such as:

- top shopper questions
- per-surface usage
- shopper journey summaries
- ROI posture
- strongest surfaces
- recommendation prompts for launch and rollout

This is meant to show merchant value evidence without pretending to be a full commerce BI suite.

### 3.5 Billing and plan posture

The current live product models a plan ladder of:

- Free
- Starter
- Elite

The active plan depends on the store.

The merchant app now shows:

- allowed surfaces
- chat fallback posture
- product cap
- sync cadence
- powered-by posture
- action packages
- confirmation and audit expectations

---

## 4) What Is Explicitly Not Included Yet

The current live product does not support:

- customer-safe order lookup
- order-status inspection
- refund execution
- cancellation execution
- returns execution against a real order
- broad support-ticket automation
- arbitrary merchant scripting
- autonomous checkout behavior
- arbitrary AI-initiated store writes

Important support boundary:

- do not describe Companion as if it can read or modify a shopper’s specific order
- do not describe it as a full support desk replacement

---

## 5) Correct Positioning

The best short description is:

`Shopify Companion adds embedded AI search, product guidance, policy answers, and grounded storefront intelligence to Shopify, with bounded merchant launch and support tooling inside Shopify admin.`

The wrong description is:

- full ecommerce operations agent
- autonomous purchase assistant
- refund and support automation platform
- order-management copilot

---

## 6) Good Example Prompts

Good shopper-facing examples:

- `Show me travel backpacks`
- `Compare these two products`
- `What is the return policy?`
- `What are the main differences between these options?`
- `Do you have something similar but lighter?`

Good merchant-facing examples:

- `Run source preflight`
- `Open storefront preview`
- `Review launch readiness`
- `Copy the support bundle`
- `Download the App Review guide`

Examples that should not be used as live product promises:

- `Where is my order right now?`
- `Refund this purchase`
- `Cancel my order`
- `Handle all support tickets automatically`
- `Complete checkout for me`
