# Shopify Companion Customer Capabilities Guide

Status: customer-facing capability guide for the current live Shopify Companion build (2026-04-19)

This document describes what Shopify Companion can do now in the current live product shape.

It is intentionally narrower than the roadmap and implementation plans. Use it when explaining the product to:

- design partners
- internal sales or solution teams
- merchants evaluating the current build

Do not use it to describe future or planned capabilities that are not live yet.

Related internal documents:

- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_SUBSCRIPTION_AND_GO_LIVE_FLOW.md`

---

## 1) Product Summary

Shopify Companion is a read-first shopping assistant for Shopify stores.

It currently provides:

- a shopper-facing assistant on the storefront
- a merchant-facing embedded Shopify admin app
- a merchant playground for testing live assistant behavior
- a platform-backed deployment and verification path behind the scenes

The current product posture is:

- product discovery
- product information
- policy explanation
- bounded availability checks

It is not a transaction bot.

---

## 2) Shopper-Facing Capabilities Available Now

### 2.1 Product discovery

Shoppers can use the assistant to:

- list products
- find products by name or general shopping intent
- browse available catalog items through natural-language prompts
- inspect basic product details returned from the live store catalog

Examples:

- `List available products`
- `Show me snowboard products`
- `Help me find the right product for travel`

### 2.2 Product detail lookup

The assistant can return product information pulled from the connected Shopify store, including:

- product title
- handle
- storefront URL
- vendor
- product type
- variant titles
- basic availability signals
- inventory quantity when available from the store data

### 2.3 Availability checks

The assistant supports bounded availability lookups.

Current rule:

- if the shopper asks for availability without a clear SKU or specific item identifier, the assistant asks for clarification
- if a SKU is provided and the product exists, the assistant can return the live availability result

This is intentional. The current build does not guess which item the shopper means.

### 2.4 Policy answers

The assistant can answer store policy questions from synced Shopify policy content.

Examples:

- `What is the store refund policy?`
- `Explain the privacy policy`
- `What store policies are available?`

Current policy support is read-only and explanation-oriented.

### 2.5 Theme-app-embed storefront delivery

The shopper assistant is delivered through the Shopify theme app embed.

That means:

- the assistant can appear on the storefront after the merchant enables the embed
- the storefront uses the same platform-backed deployment that the merchant validates during onboarding

---

## 3) Merchant-Facing Capabilities Available Now

### 3.1 Embedded Shopify admin app

Merchants currently get:

- an embedded app inside Shopify admin
- install and auth flow
- source-category settings
- source preflight visibility
- sync and readiness visibility
- webhook status visibility
- billing posture visibility
- merchant playground for live testing

### 3.2 Source-category controls

Merchants can currently enable or disable bounded source categories:

- products
- collections
- pages
- policies

These are product-level controls, not raw data-engineering controls.

Merchants do not configure:

- chunking strategy
- embedding settings
- vectorization schema
- low-level plugin wiring

### 3.3 Source readiness and sync visibility

The merchant/admin experience currently includes:

- source preflight checks
- sync-state visibility
- readiness visibility
- storefront enablement status

This helps the merchant confirm whether the store is actually ready for live use.

### 3.4 Merchant playground

The merchant app includes a playground that uses the same bridge-backed runtime contract as the storefront widget.

This lets the merchant test:

- product listing
- policy questions
- bounded availability checks
- general shopper-facing prompt behavior

before relying on the live storefront surface.

### 3.5 Billing posture

The current live build runs in:

- `FREE / ACTIVE`

That means:

- no merchant billing approval is currently required for the active companion flow

### 3.6 Webhook health visibility

The merchant and operator surfaces currently expose webhook-subscription diagnostics.

Current expected live status is:

- `READY`
- `9/9` required topics present

This is important because product sync and store lifecycle behavior depend on healthy webhook subscriptions.

---

## 4) Capabilities That Are Explicitly Not Included Yet

The current live product does not support:

- checkout execution
- cart mutation
- order placement
- refund execution
- return execution
- customer account support workflows
- broad support-ticket automation
- review-provider integration
- AI-initiated store writes
- autonomous multistep agent behavior

It also does not expose arbitrary merchant configuration of:

- raw plugin internals
- runtime secrets
- low-level routing
- vector database settings

Those are intentionally outside the customer-facing scope.

---

## 5) Current Product Boundaries

The best way to describe the current product is:

- a shopping assistant
- a product discovery and policy assistant
- a bounded, safer read-first AI layer for Shopify stores

The wrong way to describe it is:

- a full ecommerce operations agent
- a customer-support automation replacement
- a checkout bot
- a store-management agent

---

## 6) Suggested Customer-Facing Positioning

Recommended short description:

`Shopify Companion helps shoppers discover products, understand policies, and check product information through a storefront assistant backed by live Shopify store data.`

Recommended merchant-facing description:

`The current build gives merchants a safer read-first AI assistant they can install, validate, test in Shopify admin, and expose on the storefront through a theme app embed.`

---

## 7) Example Prompts That Match the Current Build

Good examples:

- `List available products`
- `Show me snowboard options`
- `What is the refund policy?`
- `Explain the privacy policy`
- `Check availability for SKU sku-managed-1`

Examples that should not be used as product promises:

- `Add this item to my cart`
- `Place the order for me`
- `Refund this purchase`
- `Cancel the customer order`
- `Answer all support cases automatically`

---

## 8) Operational Prerequisites For A Successful Merchant Demo

For the current live product to work correctly in a merchant demo, all of the following should be true:

- the Shopify app is installed
- required Shopify scopes are approved
- source preflight is ready
- the deployment release is applied and verified
- the storefront app embed is enabled
- webhook diagnostics are healthy

If those conditions are not met, the customer experience will look incomplete even if the product code is correct.

---

## 9) Current Truth Statement

As of the current live build, Shopify Companion is ready to demonstrate:

- product listing
- product discovery
- basic product detail retrieval
- bounded SKU-based availability checks
- policy explanation
- merchant-side readiness, sync, and webhook visibility

It is not yet positioned as a full-service commerce agent.
