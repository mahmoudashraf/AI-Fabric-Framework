# Shopify Companion Implementation Plan

Status: detailed implementation plan (2026-04-18)

This document is the canonical detailed implementation plan for the first Shopify-facing product built on the current platform and marketplace baseline.

It should be read with:

- `doc/Productization/future-work/MarketPlace/Products/PRODUCT_DIRECTION_DECISION_RECORD.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md`
- `doc/Productization/future-work/SHOPIFY_VERTICAL_STRATEGY_AND_PRIORITY_PLAN.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/CONSUMER_BOUND_DEPLOYMENT_RESOLUTION_PLAN.md`

It supersedes older Shopify implementation notes when they conflict with the current marketplace/control-plane architecture.

Relevant Shopify references:

- App Store requirements: <https://shopify.dev/docs/apps/launch/shopify-app-store/app-store-requirements>
- Pass app review: <https://shopify.dev/docs/apps/launch/app-store-review/pass-app-review>
- Shopify authentication: <https://shopify.dev/docs/api/usage/authentication>
- Theme app extensions: <https://shopify.dev/docs/apps/build/online-store/theme-app-extensions>
- Theme app extension configuration: <https://shopify.dev/docs/apps/build/online-store/theme-app-extensions/configuration>

---

## 1) Executive Summary

The first Shopify product to ship should be:

- `Loom Companion for Shopify`

It should be:

- a shopper-facing shopping companion
- a merchant-facing embedded Shopify admin app
- a theme app extension for storefront injection
- a first-party consumer of the existing platform and marketplace

It should not be:

- a write-heavy cart bot
- a second Shopify-specific runtime stack
- a bypass around deployment drafts, versions, or marketplace composition

The correct launch shape is:

1. a Shopify app backend that owns install/auth/sync and merchant UI
2. a platform-backed deployment bundle assembled from marketplace-backed capabilities
3. a storefront shopper companion delivered through a theme app extension
4. stable storefront identity through `consumerId` resolution
5. backend-mediated private-runtime traffic as the default storefront auth posture

---

## 2) Product Definition

### 2.1 Product posture

Shopify Companion is a read-first shopper decision-support product.

Core user value:

- help shoppers find products
- compare options
- understand policies
- learn from reviews and product details
- stay in control of purchase decisions

### 2.2 Merchant value

Merchant value for V1 is:

- fewer unanswered product questions
- better product discovery
- better policy comprehension
- better shopper confidence
- a safer and easier-to-approve AI surface than a transaction bot

### 2.3 Non-goals for launch

Do not include in V1:

- AI-initiated checkout
- AI-initiated refunds or returns
- broad customer-service write automation
- Deep Resolver-style multi-step autonomous resolution
- Smart Brain-style background agent behavior

---

## 3) Hard Architecture Rules

These rules are not optional.

### 3.1 Platform rule

Shopify Companion must consume the existing platform.

That means:

- deployments remain normal platform deployments
- product behavior compiles into deployment config
- runtime consumes resolved config only
- no Shopify-specific runtime fork

### 3.2 Marketplace rule

Shopify Companion must be packaged through current supported public plugin types:

- `TEMPLATE`
- `ACTION`
- `DATA`
- `INFERENCE_PROFILE`

No new public marketplace plugin types are required for launch.

### 3.3 External identity rule

The storefront should not permanently depend on concrete deployment ids.

Use:

- `consumerId`

to resolve the current deployment for the storefront-facing product.

### 3.4 Storefront delivery rule

Use:

- Shopify embedded admin app for merchant operations
- Shopify theme app extension for storefront widget delivery

Do not rely on:

- manual theme edits as the primary install path
- a hidden standalone platform admin page as the merchant product UI

### 3.5 Security rule

Do not expose:

- Shopify admin credentials
- platform admin credentials
- deployment secrets
- private runtime secrets

to the browser.

---

## 4) Product Components

The shipped product has four concrete components.

### 4.1 Shopify embedded admin app

Purpose:

- installation
- onboarding
- sync and health
- widget settings
- merchant diagnostics
- billing

Auth posture:

- embedded app
- session tokens
- Shopify-managed installation

### 4.2 Shopify app backend

Purpose:

- OAuth and token storage
- webhook receiver
- sync orchestration
- platform/deployment provisioning calls
- consumer binding
- merchant-facing admin APIs

This backend is the Shopify-specific control surface, not a replacement runtime.

### 4.3 Platform deployment bundle

Purpose:

- actual assistant behavior
- knowledge source composition
- action catalog
- shell defaults
- inference posture

This is the real product core.

### 4.4 Storefront companion surface

Purpose:

- shopper conversation
- product context attachment
- comparison and review surfaces
- policy answers

Delivered through:

- theme app extension app embed
- optional product page app block later if needed

---

## 5) Canonical Deployment Bundle

Shopify Companion should have its own opinionated first-party bundle, even if it reuses existing generic commerce components underneath.

### 5.1 Bundle shape

Recommended first-party bundle:

- `mkp-template-shopify-companion`
- `mkp-action-shopify-companion-read`
- `mkp-data-shopify-catalog`
- `mkp-data-shopify-policies`
- `mkp-data-shopify-reviews` later or beta
- `mkp-inference-shopify-companion-default`

### 5.2 Why a dedicated bundle is needed

Existing starter catalog entries prove marketplace mechanics, but they are too generic for product packaging.

We need:

- Shopify Companion naming and posture
- shopper-facing shell defaults
- read-first action defaults
- product/review/policy knowledge defaults
- a predictable install and verification path

### 5.3 Reuse rule

The dedicated bundle may internally reuse existing manifest fragments and code paths from:

- `mkp-template-commerce-shell`
- `mkp-action-shopify-admin`
- `mkp-data-commerce-catalog`
- `mkp-data-policy-folder`

But the product surface should still be packaged as one opinionated Shopify Companion bundle.

---

## 6) Launch Scope

### 6.1 Knowledge sources

Required at launch:

- products
- collections
- pages
- store policies

Target next:

- review data from supported review-app integrations

Possible later:

- buying guides
- FAQs
- size guides

Avoid at launch:

- customers
- orders as indexed knowledge
- PII-heavy shopper datasets

### 6.2 Actions

Required launch actions:

- `search_products`
- `get_product_details`
- `compare_products`
- `check_availability`
- `find_similar`
- `explain_policy`
- `get_size_guide` if product data supports it

Optional beta actions:

- `lookup_reviews`
- `lookup_collection`

Do not ship at launch:

- `cancel_order`
- `refund_order`
- `apply_coupon`
- `checkout`
- AI-initiated cart mutation as a primary flow

### 6.3 Shell/UI modules

Required launch modules:

- products
- details
- reviews
- compare
- policies

Required launch UI work:

- one comparison card or comparison panel
- shopper-safe product detail rendering
- evidence/source rendering for product, review, and policy answers

---

## 7) Shopify App Architecture

### 7.1 Merchant-side architecture

The Shopify app consists of:

- Partner App configuration in Shopify
- embedded admin app frontend
- Shopify app backend
- theme app extension

### 7.2 Platform-side architecture

The platform side consists of:

- customer record
- deployment record
- consumer binding
- marketplace-backed bundle installation
- normal publish/apply lifecycle

### 7.3 Runtime traffic rule

The Shopify app backend should own:

- install/auth/sync/admin operations

Default launch model:

1. Shopify app backend provisions or binds the deployment and consumer.
2. The storefront companion talks to the Shopify app backend.
3. The Shopify app backend resolves `consumerId` and current deployment posture.
4. The Shopify app backend forwards shopper traffic to the private runtime.

This is the correct default because the current auth model does not make consumer credential resolution browser-public by default.

Optional later variant:

1. Shopify app backend still provisions or binds the deployment and consumer.
2. Shopify app backend resolves `consumerId` and prepares a browser-safe public-runtime bootstrap.
3. The shopper widget talks directly to the explicitly enabled public runtime surface for that deployment.

The public-runtime variant is a later optimization, not the default launch posture.

---

## 8) Shopify Auth, Scopes, and Permissions

### 8.1 Admin app auth posture

Use:

- embedded admin app
- session tokens
- token exchange
- Shopify-managed installation

This aligns with Shopify's current recommended app auth posture.

### 8.1.1 Storefront chat auth posture

Default launch posture:

- `PRIVATE_RUNTIME_BACKEND_MEDIATED`

That means:

- browser talks to Shopify app backend
- Shopify app backend resolves `consumerId`
- Shopify app backend calls the private runtime

Optional later variant:

- `PUBLIC_RUNTIME_ANONYMOUS`
- `PUBLIC_RUNTIME_AUTHENTICATED`

Those variants are valid only when the deployment explicitly enables a public runtime posture and the Shopify app backend returns browser-safe bootstrap information for it.

### 8.2 Launch scope posture

Keep Shopify scopes minimal and read-heavy.

Recommended launch scope set should be limited to what is required for:

- product retrieval
- inventory/availability retrieval
- content and policy retrieval

Likely launch scope family:

- product read
- inventory read
- content read

Use optional or later expansion for:

- orders
- customers
- write-heavy admin actions

### 8.3 Review integration rule

Review sources should not expand Shopify scopes unless truly necessary.

Preferred approach:

- integrate supported review providers through their own bounded connector path
- keep review support optional or beta until one review-provider path is stable

---

## 9) Merchant Admin Product

### 9.1 Required screens

Required merchant UI:

- onboarding
- overview
- playground
- sync
- storefront widget
- knowledge sources
- actions
- environments
- diagnostics
- billing if paid launch

### 9.2 Onboarding flow

Required steps:

1. install app
2. create or bind customer/deployment
3. install Shopify Companion bundle
4. create consumer binding
5. run initial sync
6. enable theme app extension
7. validate playground
8. validate storefront preview

### 9.3 Merchant-facing diagnostics

Required diagnostics:

- sync status
- last successful sync time
- data-source counts
- widget enabled status
- deployment health
- action/knowledge source status
- copyable support bundle

---

## 10) Storefront Delivery

### 10.1 Delivery mechanism

Use:

- theme app extension

Primary launch delivery:

- app embed block

Optional later:

- product-page app block

### 10.2 Theme extension constraints

The plan must respect Shopify theme app extension constraints:

- extension assets and configuration must fit Shopify extension limits
- theme app extensions cannot render on checkout pages
- storefront logic must stay within app embed / app block boundaries

Therefore:

- do not plan checkout-step companion behavior for V1
- do not assume unrestricted storefront injection

### 10.3 Storefront widget behavior

The storefront widget should:

- mount through the theme app extension
- read safe storefront context
- attach current product context when appropriate
- render product and comparison results cleanly
- keep any cart transition user-initiated

Existing accelerant:

- `max-mode-widget/src/integrations/shopify.ts`

This should be treated as a useful implementation seed, not as a complete product architecture.

---

## 11) Data Sync And Knowledge Pipeline

### 11.1 Source ingestion model

The Shopify app backend should:

- read Shopify admin data
- transform it into the platform's data/plugin ingestion shape
- push or reconcile it into the deployment's data-plugin-backed dataset path

### 11.2 Launch ingestion strategy

Use:

- full initial sync
- webhook-driven incremental updates
- manual resync from merchant UI

### 11.3 Source priorities

Priority order:

1. products
2. collections
3. pages
4. policies
5. reviews

### 11.4 Dataset packaging

The product should compile these into deployment-scoped or plugin-owned dataset handles using the current `DATA` plugin lifecycle.

This keeps Shopify sync aligned with:

- install records
- dataset lifecycle
- release verification
- tenant/customer-safe shared storage rules

---

## 12) Action Catalog Strategy

### 12.1 Launch strategy

Launch with a narrow read-first action set.

The action catalog should support:

- product discovery
- product detail lookup
- availability lookup
- comparison
- similarity discovery
- policy explanation support where action-backed lookup is needed

### 12.2 Safe write rule

Write actions are not forbidden forever.

But for V1:

- they are not core product posture
- they should not be App Store launch blockers
- they should remain out of the default shopper companion flow

### 12.3 Future write expansion

If later added, write behaviors must remain:

- bounded
- explicit
- confirmation-aware
- operationally reviewable

---

## 13) Inference And Runtime Posture

### 13.1 Launch inference posture

Use one opinionated default inference profile for launch.

It should optimize for:

- low operational friction
- predictable answer quality
- strong evidence-backed answering

### 13.2 Deployment posture

Recommended launch posture:

- one stable deployment per merchant/product environment
- `consumerId` as the storefront-facing identity
- stable rebinding later if deployment replacement is needed

### 13.3 Deep Resolver posture

Do not make Deep Resolver part of the launch runtime contract.

Instead:

- keep the runtime one-shot/read-first at launch
- add Deep Resolver later as a bounded orchestration-mode enhancement if proven necessary

---

## 14) Billing And Packaging

### 14.1 Launch packaging decision

Two acceptable launch states:

- free public app while billing is unfinished
- paid public app using Shopify billing or managed pricing

Not acceptable:

- off-platform billing for an App Store-distributed paid app

### 14.2 Product ladder

Launch ladder:

1. `Loom Companion`
2. `Loom Companion Pro`
3. `Loom AI Platform for Shopify`

Each later tier should reuse the same product core rather than rebuilding the stack.

---

## 15) Verification Strategy

### 15.1 Platform verification

Required:

- deployment draft compilation verification
- publish/apply verification
- knowledge-source verification
- shell config verification
- action catalog verification
- consumer binding verification

### 15.2 Shopify product verification

Required:

1. install app on dev store
2. complete embedded admin onboarding
3. run initial sync
4. confirm deployment bundle installs and compiles correctly
5. confirm consumer binding resolves correctly
6. enable theme app extension
7. validate storefront widget on product page and generic page
8. validate grounded answer quality for:
   - product search
   - product comparison
   - policy answer
9. validate resync and webhook update path
10. validate uninstall and cleanup posture

### 15.3 Review-readiness verification

Before App Store submission, require:

- no critical UI errors in merchant app
- no broken install flow
- no broken theme-app-extension flow
- no unjustified scopes
- billing path correct if app is paid
- clear reviewer instructions and test credentials

---

## 16) Implementation Waves

### Wave 0: Product Contract And Bundle Definition

Build:

- canonical Shopify Companion product bundle definition
- companion-specific marketplace template and default plugin bundle
- merchant/customer/deployment/consumer naming rules
- launch scope and non-goals frozen

Acceptance:

- one canonical product bundle exists
- no ambiguity remains about launch posture
- product packaging does not depend on ad hoc deployment setup

### Wave 1: Shopify App Skeleton And Auth

Build:

- Partner App configuration
- embedded admin app shell
- Shopify auth/session-token path
- Shopify app backend shell
- merchant install record and shop identity persistence

Acceptance:

- merchants can install the app on a dev store
- embedded admin shell loads reliably
- auth works in normal and incognito browser conditions

### Wave 2: Platform Bootstrap And Consumer Binding

Build:

- customer creation or selection rule
- deployment creation from Shopify Companion bundle
- consumer creation and binding
- deployment status and credentials lookup for the product

Acceptance:

- app install can create a real platform deployment
- storefront identity can resolve through a stable `consumerId`
- deployment replacement does not require storefront code changes

### Wave 3: Data Plugins And Sync

Build:

- Shopify source readers for products, collections, pages, policies
- initial sync pipeline
- incremental update pipeline
- merchant sync controls and diagnostics
- optional review-provider integration design or beta path

Acceptance:

- indexed source counts are correct
- resync works
- incremental updates land correctly
- diagnostics are merchant-readable

### Wave 4: Storefront Theme App Extension

Build:

- app embed block
- safe widget loader/config contract
- storefront context injection
- product-page attachment behavior
- preview and activation guidance in admin app

Acceptance:

- widget can be enabled without manual theme edits
- widget loads reliably on supported storefront pages
- no checkout-page dependency exists

### Wave 5: Read-First Action Catalog

Build:

- Shopify Companion read action set
- action-grounded answer flows
- comparison support
- availability support

Acceptance:

- companion can answer core product questions with grounded results
- comparison is product-useful, not a stub
- no launch dependency on write flows remains

### Wave 6: Companion UX Hardening

Build:

- comparison card/panel
- review-grounded rendering
- policy answer UX
- shopper-safe sources rendering
- merchant playground improvements

Acceptance:

- shopper experience feels like a companion, not a raw chatbot
- merchant can demo value quickly from admin and storefront

### Wave 7: Billing, Analytics, And Review Package

Build:

- Shopify billing if paid launch
- merchant usage analytics
- basic conversion/engagement attribution
- reviewer guide, screencast, and test package

Acceptance:

- app can be submitted without billing-policy issues
- merchant-facing usage posture is understandable
- review package is complete

### Wave 8: Design Partner And Launch Hardening

Build:

- pilot merchant rollout
- support runbooks
- reliability fixes from real store testing
- uninstall and cleanup verification

Acceptance:

- multiple real stores complete onboarding
- launch blockers from real store behavior are resolved
- support burden is manageable

---

## 17) Recommended Team Tracks

Parallel tracks that can run with reasonable coordination:

- `Track A`: Shopify admin app and auth
- `Track B`: platform bootstrap, deployment bundle, consumer binding
- `Track C`: sync and data plugins
- `Track D`: storefront extension and shopper UX
- `Track E`: review-readiness, billing, and launch operations

---

## 18) Risks

### 18.1 Scope creep into transaction bot behavior

Risk:

- product drifts from companion into autonomous cart assistant

Mitigation:

- enforce read-first launch contract
- make write expansion a separate explicit decision

### 18.2 Shopify-specific architecture drift

Risk:

- Shopify app becomes a parallel backend stack

Mitigation:

- keep deployment, runtime, and marketplace composition on the main platform

### 18.3 Review-provider explosion

Risk:

- too many review-app integrations block launch

Mitigation:

- ship without review-provider breadth if necessary
- support one bounded provider path first or defer to beta

### 18.4 App review failure

Risk:

- broken merchant UI, bad scopes, or bad billing path delay release

Mitigation:

- make review-readiness a first-class wave

---

## 19) Recommendation

Build Shopify Companion as:

- a narrow read-first standalone Shopify product
- powered by the existing platform and marketplace
- delivered through an embedded admin app plus theme app extension
- bound to a stable consumer-facing identity

Do not:

- block launch on Deep Resolver
- overbuild write automation
- split the architecture into a separate Shopify stack

This is the most realistic path to shipping a real Shopify product while preserving the platform as the long-term main asset.
