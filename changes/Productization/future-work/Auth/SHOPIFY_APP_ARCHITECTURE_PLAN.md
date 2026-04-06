# Shopify App Architecture Plan for Customer Runtime Integration

Status: detailed planning document (2026-04-06)

This document defines the recommended product architecture for packaging AI Fabric customer deployments as a Shopify app.

The goal is to make Shopify integration:

- sellable
- installable
- secure
- operationally repeatable

without changing the core security principles already defined for customer-facing runtime integrations.

This is not an internal platform assistant plan.

It is a merchant-facing customer integration plan.

---

## 1) Executive Summary

The recommended Shopify product shape is:

- a public Shopify app
- a Shopify app backend as the security bridge
- a merchant-to-deployment mapping inside AI Fabric
- a storefront chat surface embedded by Shopify extension mechanisms
- a private runtime and private connector behind the app backend by default

Recommended request shape:

- browser -> Shopify storefront surface
- Shopify storefront surface -> Shopify app backend
- Shopify app backend -> private runtime
- runtime -> private connector
- runtime or connector -> customer authorization endpoint when needed

This keeps:

- deployment credentials out of the browser
- store API credentials out of the browser
- the connector private
- customer identity grounded in Shopify-authenticated context

The Shopify app backend becomes the trust boundary that translates Shopify identity and tenancy into AI deployment identity.

---

## 2) Why a Shopify App Is the Right Packaging Model

If this integration is to be sold broadly, the merchant needs:

- installable onboarding
- merchant-specific configuration
- merchant billing
- shop-to-deployment binding
- theme/storefront integration
- secure traffic between the storefront and the AI deployment

A Shopify app provides the right product packaging for all of those concerns.

It also gives a clean place to own:

- merchant installation lifecycle
- shop token management
- storefront request verification
- deployment lookup
- usage metering and billing

---

## 3) Goals

This plan should achieve all of the following:

- support merchant install and configuration through a real Shopify app
- expose storefront chat without exposing runtime or connector secrets to the browser
- map each shop to its own AI Fabric deployment
- support both anonymous storefront chat and logged-in customer chat
- preserve deployment-scoped store credentials and secrets
- make auth and authorization explicit and supportable
- keep the product shape extensible for future vertical variants

---

## 4) Non-Goals

This plan does not require:

- browser-direct connector access
- browser-held `CONNECTOR_API_KEY`
- per-page custom embedding code as the primary integration model
- the Shopify storefront becoming the direct caller of the connector
- replacing Shopify as the source of truth for merchant install state

---

## 5) Recommended App Components

### 5.1 Public Shopify app

This is the merchant-facing product package.

It should handle:

- install
- uninstall
- app listing
- merchant access
- billing and plan gating

### 5.2 Embedded admin app

This is where the merchant manages:

- AI Fabric deployment linkage
- chat appearance settings
- allowed storefront surfaces
- public vs authenticated mode
- usage status
- health and verification state

### 5.3 Storefront chat surface

Recommended storefront surface options:

- theme app extension
- app embed block

This surface is responsible only for the UI shell and browser messaging to the Shopify app backend.

It should not contain deployment secrets.

### 5.4 Shopify app backend

This is the most important component.

It is responsible for:

- verifying Shopify-originated requests
- resolving the merchant shop
- resolving the mapped AI Fabric deployment
- determining anonymous vs authenticated customer context
- calling the private runtime
- optionally issuing short-lived runtime-scoped tokens if later needed

### 5.5 AI Fabric deployment mapping layer

Each installed shop should map to:

- one deployment by default
- optionally one environment-specific deployment if that becomes a requirement later

The mapping must be explicit and operationally visible.

### 5.6 Private runtime and private connector

By default, the Shopify app should call:

- private runtime
- private connector via runtime

This is the safest first productized model.

---

## 6) Recommended Deployment Mapping Model

Each merchant shop should have a first-class mapping record containing:

- `shopDomain`
- `deploymentId`
- merchant/account id
- app install state
- billing tier
- allowed chat mode
- optional customer auth mode
- operational status

Recommended default:

- one merchant shop -> one customer deployment

This keeps:

- secrets isolated
- data isolated
- behavior predictable

---

## 7) Authentication Layers

### 7.1 Merchant admin authentication

Merchant admin access is owned by Shopify app auth.

This governs:

- install
- settings
- billing
- configuration UI

### 7.2 Storefront request authentication

The storefront chat UI itself should not authenticate directly to the runtime.

Instead:

- browser talks to the Shopify app backend
- Shopify app backend verifies the shop/storefront context
- Shopify app backend calls the runtime as a trusted service caller

### 7.3 Customer identity authentication

If the shopper is logged in, the Shopify app backend should translate that verified customer identity into the AI request context sent to runtime.

If the shopper is anonymous, the backend should create or maintain an anonymous session identity.

### 7.4 Runtime service authentication

The Shopify app backend must authenticate to the runtime using service-to-service auth.

Recommended options:

- signed service JWT
- mTLS
- deployment-scoped service key

---

## 8) Recommended Chat Flows

### 8.1 Anonymous storefront chat

1. Shopper opens storefront chat.
2. Storefront UI sends request to Shopify app backend.
3. Backend verifies shop mapping.
4. Backend establishes anonymous customer context.
5. Backend calls private runtime with:
   - service auth
   - deployment id
   - anonymous subject/session context
6. Runtime handles the request using anonymous policy.
7. Runtime calls connector only for allowed low-risk actions.

### 8.2 Logged-in storefront customer chat

1. Logged-in customer opens storefront chat.
2. Storefront UI sends request to Shopify app backend.
3. Backend verifies shop and customer identity.
4. Backend calls private runtime with:
   - service auth
   - deployment id
   - verified customer subject/session context
5. Runtime uses verified subject for chat ownership and authz.
6. Protected retrieval or actions go through authorization checks.
7. Connector executes upstream calls with deployment-owned credentials.

### 8.3 Merchant admin configuration flow

1. Merchant installs the app.
2. Merchant opens embedded admin UI.
3. Merchant selects:
   - existing deployment
   - or platform-provisioned deployment bootstrap flow
4. Merchant configures chat mode and appearance.
5. Mapping is stored and verified.
6. Storefront surface becomes active.

---

## 9) Authorization Model

### 9.1 Shopify is not the AI policy engine

Shopify remains the source of truth for:

- merchant/shop identity
- customer identity

But AI authorization still needs a dedicated policy evaluation model.

Examples:

- anonymous user can ask product questions
- authenticated customer can ask about own orders
- no customer can trigger privileged merchant/admin operations without explicit support

### 9.2 Recommended policy ownership

Short term:

- Shopify app backend can provide the customer-facing policy logic

Longer term:

- policy can be externalized behind a dedicated authz service if the product grows

### 9.3 Fail-closed behavior

Any sensitive retrieval or write path must fail closed when:

- authz service is unavailable
- subject context is missing
- deployment mapping is invalid

---

## 10) Secret Model

### 10.1 Secrets that stay in AI Fabric

These should stay deployment-scoped inside AI Fabric:

- upstream store credentials if AI Fabric connector is calling store APIs
- runtime internal credentials
- connector internal credentials

### 10.2 Secrets that stay in the Shopify app backend

These may live with the app backend depending on product boundary:

- Shopify app installation tokens
- app signing secrets
- session validation material

### 10.3 Secrets that must never reach the browser

- connector API keys
- runtime service credentials
- merchant admin secrets
- store admin API tokens

---

## 11) Why the Backend Bridge Matters

The Shopify app backend is the cleanest place to:

- normalize Shopify identity
- hide AI deployment topology
- hide internal service credentials
- enforce shop-to-deployment mapping
- absorb auth evolution without changing the storefront snippet

Without this backend bridge, the browser would need to know too much about:

- runtime location
- token exchange
- deployment identity
- credential handling

That creates unnecessary exposure and product friction.

---

## 12) Easy Integration Strategy

The easiest secure merchant experience should be:

1. Merchant installs Shopify app.
2. Merchant completes onboarding in embedded admin UI.
3. Merchant enables storefront chat surface.
4. App backend handles the rest.

The merchant should not need to:

- paste connector API keys into theme code
- manually wire browser auth to runtime
- reason about internal deployment credentials

---

## 13) Future Option: Public Runtime Mode

This plan should not block a future option where the browser can call a public runtime directly.

If that mode is later added, the Shopify app backend should still remain able to:

- mint short-lived customer tokens
- handle anonymous session bootstrap
- provide the integration fallback path

But that should remain a secondary mode, not the default Shopify app posture.

---

## 14) Operational Requirements

The app backend should have visibility into:

- shop install state
- deployment mapping health
- runtime health
- connector health
- verification status
- billing and entitlement status

Support tooling should make it easy to answer:

- which shop maps to which deployment
- whether storefront chat is anonymous or authenticated
- whether the runtime is healthy
- whether upstream actions are enabled

---

## 15) Suggested Implementation Sequence

1. Define the Shopify app backend as the trust boundary.
2. Add merchant shop -> deployment mapping model.
3. Build embedded admin UI for onboarding and deployment selection.
4. Build storefront UI surface that talks only to the app backend.
5. Add service-authenticated backend -> private runtime calls.
6. Add anonymous and authenticated customer context translation in the backend.
7. Add runtime-side verified subject handling and authz hardening.
8. Add deployment health and verification visibility in the merchant admin app.
9. Add billing/entitlement enforcement.

---

## 16) Verification Requirements

The Shopify productization path should be considered complete only when all of the following are verified:

- merchant install and uninstall lifecycle
- shop-to-deployment mapping correctness
- anonymous storefront chat works end-to-end
- authenticated storefront chat works end-to-end
- browser never sees internal deployment credentials
- runtime rejects forged identity inputs
- connector remains private
- deployment health can be diagnosed from the merchant/admin side

---

## 17) Completion Criteria

This plan is complete when AI Fabric can be offered as a Shopify app where:

- a merchant installs the app
- the merchant links or provisions an AI deployment
- storefront chat appears with minimal merchant effort
- the browser never receives internal deployment secrets
- customer identity is correctly conveyed to runtime
- authorization remains explicit and supportable

