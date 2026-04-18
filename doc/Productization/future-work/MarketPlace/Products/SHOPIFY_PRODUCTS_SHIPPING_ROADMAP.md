# Shopify Products Shipping Roadmap

Status: realistic product shipping roadmap (2026-04-17)

This document translates the current platform and marketplace state into a realistic path for shipping Shopify-facing products.

It is intentionally grounded in:

- the current platform/runtime/control-plane boundaries
- the current marketplace plugin model
- current Shopify platform requirements and app review expectations

Primary local references:

- `doc/Productization/future-work/SHOPIFY_VERTICAL_STRATEGY_AND_PRIORITY_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/CONSUMER_BOUND_DEPLOYMENT_RESOLUTION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/PRODUCT_DIRECTION_DECISION_RECORD.md`

Primary Shopify references:

- Shopify App Store requirements: <https://shopify.dev/docs/apps/launch/shopify-app-store/app-store-requirements>
- Pass app review: <https://shopify.dev/docs/apps/launch/app-store-review/pass-app-review>
- Submit app for review: <https://shopify.dev/docs/apps/launch/app-store-review/submit-app-for-review>
- Shopify API authentication: <https://shopify.dev/docs/api/usage/authentication>
- About theme app extensions: <https://shopify.dev/docs/apps/build/online-store/theme-app-extensions>
- Theme app extension configuration: <https://shopify.dev/docs/apps/build/online-store/theme-app-extensions/configuration>

---

## 1) Executive Summary

The realistic first Shopify product is:

- `Loom Companion for Shopify`

It should ship as:

- a shopper-facing shopping companion
- a merchant-facing embedded admin app
- a theme app extension for storefront injection
- a thin first-party consumer of the existing platform

It should not ship as:

- a write-heavy cart bot
- a separate Shopify-specific backend stack
- a second deployment/control-plane model

The realistic product sequence is:

1. `Loom Companion` — read-first shopper companion
2. `Loom Companion Pro` — same app, more merchant controls and richer knowledge/actions
3. `Loom AI Platform for Shopify` — white-label / platform packaging after the standalone product proves out

Deferred:

- `Deep Resolver` as a later orchestration-mode enhancement
- `Smart Brain` as a separate future product line

---

## 2) Shipping Principles

### 2.1 Product principle

Shopify is the first strong vertical wedge, not the whole company identity.

### 2.2 Architecture principle

The Shopify product must consume the platform. It must not fork it.

That means:

- deployments remain normal platform deployments
- marketplace plugins remain the composition mechanism
- storefront identity should resolve through stable `consumerId` binding
- runtime contracts stay generic

### 2.3 App-review principle

The launch product should minimize Shopify review friction.

That means:

- request only the access scopes required for core functionality
- keep V1 read-heavy
- use an embedded admin app with Shopify-recommended auth posture
- use theme app extensions for storefront widget injection
- keep the merchant UI operational and free from critical errors

These points are directly reinforced by Shopify's docs:

- App Store apps must request only necessary scopes and maintain a usable UI
- embedded apps should use App Bridge, session tokens, and Shopify-managed installation
- online-store apps should use theme app extensions

---

## 3) Target Product Ladder

### 3.1 Product 1: Loom Companion

Goal:

- help shoppers find, compare, and understand products

Primary capabilities:

- product search and filtering
- product details
- review-grounded answers
- policy Q&A
- product comparison
- similar-product discovery
- size and fit guidance where data exists

Deliberate non-goals:

- checkout execution by AI
- refunds/returns initiated by AI
- broad merchant workflow automation
- write-heavy customer support actions

### 3.2 Product 2: Loom Companion Pro

Goal:

- keep the same app but unlock more merchant control

Incremental additions:

- configurable prompt/tone
- optional custom knowledge sources
- richer quick actions
- merchant analytics and conversion attribution
- controlled safe-write expansions where justified

### 3.3 Product 3: Loom AI Platform for Shopify

Goal:

- expose the platform packaging after the opinionated app proves demand

Capabilities:

- branded or customer-owned deployment packaging
- multiple deployments or environments
- marketplace plugin installation beyond the default companion bundle
- deeper operator controls and rollout/rebind control

---

## 4) Shopify Product Shape

### 4.1 Merchant-facing surfaces

Required:

- embedded admin app for install, configuration, sync, diagnostics, and playground
- theme app extension, primarily an app embed block, for storefront injection of the shopper companion

Rationale:

- Shopify requires App Store apps to have an operational UI
- Shopify recommends embedded admin apps using App Bridge/session tokens
- Shopify requires online-store widgets to use theme app extensions rather than direct theme-code modification

### 4.2 Shopper-facing surface

Required:

- one floating or embedded shopper companion injected through a theme app extension

V1 guidance:

- use an app embed block for the floating companion
- avoid checkout-step ambitions
- let the user initiate add-to-cart or purchase flows through normal Shopify UI

### 4.3 Stable external identity

The shopper-facing app should not depend permanently on concrete deployment ids.

Use:

- `consumerId` resolution for storefront-facing deployment lookup

This supports:

- deployment swap
- rollback
- white-label evolution later

See:

- `doc/Productization/future-work/MarketPlace/CONSUMER_BOUND_DEPLOYMENT_RESOLUTION_PLAN.md`

---

## 5) V1 Scope

### 5.1 Core knowledge sources

Start with:

- products
- collections
- pages
- store policies

Add next:

- reviews, if available through supported review-app integrations

Optional later:

- size guides
- buying guides
- FAQs

Avoid at launch:

- customer PII-heavy sources
- broad order history access
- broad customer-account intelligence

### 5.2 Core actions

Start with read-focused actions:

- `search_products`
- `get_product_details`
- `compare_products`
- `check_availability`
- `find_similar`
- `explain_policy`
- `get_size_guide`

Optional later:

- limited safe customer-service reads
- carefully confirmed safe writes

Do not start with:

- refund execution
- order cancellation
- coupon application
- checkout control

### 5.3 Core UI modules

Start with:

- products
- details
- reviews
- compare
- policies

Likely one new platform-owned UI block:

- comparison card / comparison panel

---

## 6) Marketplace Packaging Model

The Shopify product should compile from existing marketplace-backed deployment capabilities, not from a custom one-off stack.

Recommended default bundle:

- `TEMPLATE`
  - Shopify shopping companion template
- `DATA`
  - commerce catalog data
  - policy data
  - review data where available
- `ACTION`
  - read-only Shopify commerce actions
- `INFERENCE_PROFILE`
  - default provider posture appropriate for the SKU

This keeps the product aligned with the current supported public plugin types.

### 6.1 Configuration boundary

The product should not treat every store-level setting as a plugin concern.

Use this boundary:

- plugins define reusable capabilities
- the Shopify admin app defines per-store usage and settings
- managed services define the shared infrastructure

#### Plugins define

- default data capabilities
- default action capabilities
- template and shell defaults
- default inference posture

#### Shopify admin app defines

- store connection and install state
- approved source-category toggles
- sync operations and sync visibility
- storefront/widget settings
- merchant-facing product settings

#### Managed services define

- Shopify Bridge Service lifecycle
- shared inference service lifecycle
- scaling, restart, recreate, decommission, and diagnostics

This keeps the app opinionated while still allowing bounded merchant control.

The platform should also expose service-understanding APIs around that boundary:

- managed service APIs for health, drift, replicas, logs, activity, and high-level dependents
- separate drill-through APIs for shop/customer/deployment/consumer mappings and sync-state summaries

The service should expose understanding of dependent usage without owning that store-domain data itself.

---

## 7) Shopify Platform Constraints That Change The Plan

These are the important Shopify constraints that materially shape the roadmap.

### 7.1 Embedded admin app is not optional

Shopify App Store apps must have a usable merchant UI, and embedded apps are expected to use App Bridge/session tokens.

Implication:

- the merchant configuration and diagnostics surface must be a real embedded app, not only a platform-side admin page

### 7.2 Theme app extensions are the storefront delivery path

Shopify's online-store guidance expects widgets to use theme app extensions.

Implication:

- the shopper companion should ship through an app embed block
- storefront installation and activation UX must be part of the launch plan

### 7.3 Scope discipline matters

Shopify explicitly expects apps to request only necessary scopes.

Implication:

- V1 should stay read-focused
- optional or later features should use optional scopes or be deferred

### 7.4 Review requires production readiness

Shopify review rejects incomplete apps, broken installs, and unusable UI.

Implication:

- shipping plan must include hard review-readiness gates
- do not submit with half-finished surfaces

### 7.5 Billing must use Shopify billing

If the public app is paid, App Store distribution requires Shopify billing.

Implication:

- billing work is required before public paid launch
- if billing is not ready, the first public version should be free or clearly staged

---

## 8) Realistic Product Phases

### Phase 0: Platform Readiness

Goal:

- lock the platform capabilities needed by the Shopify product

Required outcomes:

- consumer binding path ready for storefront use
- data-plugin sync path reliable for catalog and policy sources
- runtime/source attribution working cleanly
- theme-injected widget integration stable
- default companion template and plugins packaged
- platform APIs exist for:
  - Shopify Bridge Service health, drift, activity, and dependents
  - store-to-customer/deployment/consumer mapping inspection
  - per-store sync-state visibility

Exit criteria:

- one internal Shopify-shaped deployment can be provisioned, rebound, and verified end to end
- operators can understand both the shared Shopify Bridge and the stores bound through it from platform APIs and views

### Phase 1: Loom Companion Internal Beta

Goal:

- ship one opinionated read-first Shopify companion to internal and design-partner stores

Build:

- embedded admin app
- theme app extension with shopper companion
- catalog + policy sync
- read-only action set
- shopper conversation UX
- diagnostics and sync status

Exit criteria:

- installs cleanly on development stores
- no broken install or auth paths
- storefront widget works without manual theme edits
- answers are grounded in real store data

### Phase 2: Loom Companion Review-Ready Public App

Goal:

- reach public Shopify App Store submission quality

Build:

- final install flow
- review-safe scopes
- merchant onboarding and help text
- billing, if app is paid at launch
- app review instructions, test credentials, screencast, and QA package

Exit criteria:

- no fatal install/auth/UI errors
- merchant UI is complete and operational
- storefront app embed path is stable
- all requested scopes are justified

### Phase 3: Loom Companion Pro

Goal:

- add higher-value configuration without breaking the opinionated product

Build:

- configurable prompt/tone and branding
- additional approved knowledge sources
- richer analytics
- merchant-level controls for quick actions and surfaces

Exit criteria:

- configuration adds value without turning the app into a confusing generic platform shell

### Phase 4: Loom AI Platform for Shopify

Goal:

- expose broader platform packaging once the companion SKU proves out

Build:

- customer-visible deployment/environment model where justified
- broader marketplace install surfaces
- migration path from standalone product posture to platform posture

Exit criteria:

- this step only starts after there is evidence that merchants want flexibility beyond the opinionated app

---

## 9) Deep Resolver And Smart Brain In The Shopify Roadmap

### 9.1 Deep Resolver

Do not make it launch scope.

Enter only after the read-first companion is proven and there is clear demand for:

- multi-step investigation
- policy-aware resolution chains
- higher autonomy with bounded governance

In the Shopify roadmap, Deep Resolver is:

- a V2 or later orchestration enhancement
- not the defining property of the first product

### 9.2 Smart Brain

Do not merge it into the Shopify launch track.

It belongs to:

- a separate future product family
- potentially with Shopify as one connector among many

---

## 10) Build Tracks

### Track A: Shopify app shell

Build:

- embedded admin app
- session-token auth
- managed installation
- merchant onboarding

### Track B: storefront delivery

Build:

- theme app extension
- app embed activation flow
- storefront widget configuration

### Track C: companion deployment bundle

Build:

- companion template
- default action plugins
- default data plugins
- inference-profile defaults

### Track D: data and sync

Build:

- products/collections/pages/policies sync
- review-source integration where available
- diagnostics and resync operations

### Track E: merchant proof and review readiness

Build:

- analytics
- QA matrix
- review package
- support and install docs

---

## 11) What Makes This Realistic

This roadmap intentionally avoids three common planning failures:

- it does not assume the platform should become Shopify-specific
- it does not assume Deep Resolver is required for launch
- it does not assume the standalone app justifies a second architecture

It also reflects Shopify's real constraints:

- operational merchant UI is required
- theme app extensions are required for storefront widget distribution
- scope discipline matters
- broken install/auth/UI paths block approval

---

## 12) Recommendation

The realistic shipping order is:

1. internal platform-backed Shopify companion bundle
2. review-ready public `Loom Companion` app
3. `Loom Companion Pro`
4. broader `Loom AI Platform for Shopify` packaging

Keep these separate:

- `Deep Resolver` as a later orchestration enhancement
- `Smart Brain` as a separate future product line

That is the cleanest path that fits both the current platform and Shopify's actual shipping constraints.
