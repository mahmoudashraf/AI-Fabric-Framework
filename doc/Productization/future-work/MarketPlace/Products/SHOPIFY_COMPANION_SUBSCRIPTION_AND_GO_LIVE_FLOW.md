# Shopify Companion Subscription And Go-Live Flow

Status: sequence-style product flow document (2026-04-18)

This document explains what should happen when a Shopify store subscribes to the Companion product.

It is not a low-level API spec.
It is a product and platform execution flow that can be used by:

- product planning
- implementation planning
- merchant onboarding design
- verification and support planning

Read with:

- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md`
- `doc/Productization/future-work/MarketPlace/CONSUMER_BOUND_DEPLOYMENT_RESOLUTION_PLAN.md`

---

## 1) Executive Summary

When a Shopify store subscribes to Companion, the platform should not simply "turn on chat."

It should execute a controlled flow:

1. install and authenticate the Shopify app
2. create or resolve the platform customer boundary
3. create the Shopify Companion deployment bundle
4. install and compile the default companion capabilities
5. create and bind a stable `consumerId`
6. run initial data sync
7. publish, apply, and verify the deployment
8. enable the theme app extension
9. go live on the storefront

This preserves the platform’s core rule:

- no live behavior without draft -> publish -> apply -> verify

---

## 2) Actors

Main actors in the flow:

- `Merchant`
- `Shopify Admin`
- `Shopify App Frontend` (embedded admin app)
- `Shopify App Backend`
- `Platform Control Plane`
- `Marketplace Compiler`
- `Deployment Runtime`
- `Consumer Resolution`
- `Storefront Theme App Extension`
- `Shopper`

---

## 3) High-Level Lifecycle

The full lifecycle has five phases:

1. `Install and identity`
2. `Product provisioning`
3. `Data readiness`
4. `Go-live enablement`
5. `Ongoing operation`

---

## 4) Phase 1: Install And Identity

### 4.1 Goal

Establish the merchant and shop identity safely and land the merchant in an operational embedded app.

### 4.2 Expected result

After this phase:

- the app is installed on the store
- the app backend has the necessary Shopify identity and tokens
- the merchant can continue onboarding in the embedded admin UI

### 4.3 Sequence

```mermaid
sequenceDiagram
    participant M as Merchant
    participant SA as Shopify Admin
    participant AF as Shopify App Frontend
    participant AB as Shopify App Backend

    M->>SA: Install Loom Companion app
    SA->>AB: Complete Shopify install/auth flow
    AB->>AB: Persist shop identity and tokens
    SA->>AF: Open embedded admin app
    AF->>AB: Load merchant app session
    AB-->>AF: Return onboarding state
```

### 4.4 Verification

This phase is not complete unless:

- the embedded app loads with no critical UI errors
- the merchant session is valid
- the shop identity is stored

---

## 5) Phase 2: Product Provisioning

### 5.1 Goal

Provision the Companion as a real platform-backed product, not just a Shopify-side feature toggle.

### 5.2 Expected result

After this phase:

- the store is mapped to a platform customer boundary
- a Companion deployment exists
- the Companion bundle is installed into that deployment draft
- a stable `consumerId` is bound to it

### 5.3 Provisioning flow

```mermaid
sequenceDiagram
    participant AF as Shopify App Frontend
    participant AB as Shopify App Backend
    participant CP as Platform Control Plane
    participant MC as Marketplace Compiler
    participant CR as Consumer Resolution

    AF->>AB: Start Companion onboarding
    AB->>CP: Resolve or create platform customer for this shop
    CP-->>AB: Customer id
    AB->>CP: Create deployment from Shopify Companion template/bundle
    CP->>MC: Install companion bundle into draft
    MC-->>CP: Compiled deployment draft contributions
    CP-->>AB: Deployment draft created
    AB->>CP: Create consumer for storefront identity
    CP->>CR: Bind consumerId -> deployment
    CR-->>CP: Binding active
    CP-->>AB: consumerId and binding summary
    AB-->>AF: Provisioning state ready
```

### 5.4 What exactly gets provisioned

At minimum:

- platform customer
- one Shopify Companion deployment
- one draft containing:
  - companion template contributions
  - read-first action contributions
  - product/policy data contributions
  - default inference profile
- one `consumerId` for storefront traffic

### 5.5 Important rule

The merchant has not gone live yet.

At this point, the system is provisioned, but not ready for storefront exposure until data and deployment verification complete.

---

## 6) Phase 3: Data Readiness

### 6.1 Goal

Populate the deployment’s knowledge sources so the companion is actually useful.

### 6.2 Expected result

After this phase:

- initial sync has succeeded
- the deployment has real product/policy data
- the merchant can test in playground

### 6.3 Sequence

```mermaid
sequenceDiagram
    participant AF as Shopify App Frontend
    participant AB as Shopify App Backend
    participant SA as Shopify Admin APIs
    participant CP as Platform Control Plane
    participant RT as Deployment Runtime

    AF->>AB: Run initial sync
    AB->>SA: Fetch products, collections, pages, policies
    SA-->>AB: Source data
    AB->>CP: Push or reconcile data into Companion deployment datasets
    CP->>RT: Apply dataset updates through runtime-backed data path
    RT-->>CP: Ingestion/sync result
    CP-->>AB: Sync status and counts
    AB-->>AF: Show progress and completion
```

### 6.4 Merchant experience

The merchant should see:

- sync progress
- indexed item counts
- errors by source category if any
- a clear "ready to test" state

### 6.5 Important rule

The companion should not be considered production-ready if the initial sync has failed or is materially incomplete.

---

## 7) Phase 4: Publish, Apply, Verify

### 7.1 Goal

Turn the drafted Companion configuration into a real live deployment version.

### 7.2 Expected result

After this phase:

- a version is published
- that version is applied
- verification passes
- the deployment is safe to expose to storefront traffic

### 7.3 Sequence

```mermaid
sequenceDiagram
    participant AB as Shopify App Backend
    participant CP as Platform Control Plane
    participant MC as Marketplace Compiler
    participant RT as Deployment Runtime

    AB->>CP: Request publish/apply for Companion deployment
    CP->>MC: Finalize resolved deployment config
    MC-->>CP: Final compiled draft
    CP->>CP: Publish deployment version
    CP->>RT: Apply published version
    RT-->>CP: Runtime active
    CP->>CP: Run post-apply verification
    CP-->>AB: Verification result
```

### 7.4 Hard rule

If verification fails:

- storefront go-live must remain blocked
- the merchant stays in repair/onboarding flow
- the system must not pretend the product is ready

---

## 8) Phase 5: Storefront Enablement

### 8.1 Goal

Expose the companion on the store through the approved Shopify storefront mechanism.

### 8.2 Expected result

After this phase:

- the merchant has enabled the theme app extension
- the storefront widget can resolve the Companion deployment through `consumerId`
- shoppers can use the companion

### 8.3 Sequence

```mermaid
sequenceDiagram
    participant AF as Shopify App Frontend
    participant AB as Shopify App Backend
    participant SA as Shopify Admin / Theme Editor
    participant TE as Theme App Extension
    participant CR as Consumer Resolution
    participant RT as Deployment Runtime

    AF->>SA: Deep-link merchant to theme app extension enablement
    SA->>TE: Enable Companion app embed
    TE->>CR: Resolve consumerId credentials/status
    CR-->>TE: Current deployment integration contract
    TE->>RT: Bootstrap runtime session using allowed posture
    RT-->>TE: Companion session ready
```

### 8.4 Storefront rule

The theme extension should depend on:

- `consumerId`

not:

- hardcoded deployment ids

This is what makes later rollout, replacement, and white-label evolution possible without breaking storefront installation.

---

## 9) Phase 6: Live Shopper Use

### 9.1 Goal

Serve grounded shopper conversations against the verified Companion deployment.

### 9.2 Sequence

```mermaid
sequenceDiagram
    participant S as Shopper
    participant TE as Theme App Extension
    participant CR as Consumer Resolution
    participant RT as Deployment Runtime

    S->>TE: Open companion and ask question
    TE->>CR: Resolve consumer-backed deployment posture if needed
    CR-->>TE: Active deployment/runtime contract
    TE->>RT: Send shopper query
    RT-->>TE: Grounded product/review/policy response
    TE-->>S: Render companion answer
```

### 9.3 Shopper-visible behavior

At minimum, the shopper should be able to:

- search products
- compare products
- ask about policies
- ask about product details
- get evidence-backed answers

---

## 10) Phase 7: Ongoing Operation

### 10.1 Goal

Keep the product healthy without reinstalling it.

### 10.2 Sequence

```mermaid
sequenceDiagram
    participant SA as Shopify Admin APIs / Webhooks
    participant AB as Shopify App Backend
    participant CP as Platform Control Plane
    participant RT as Deployment Runtime
    participant AF as Shopify App Frontend

    SA->>AB: Emit webhook or source change
    AB->>CP: Trigger incremental sync
    CP->>RT: Reconcile dataset changes
    RT-->>CP: Sync complete
    CP-->>AB: Updated sync status
    AB-->>AF: Merchant sees updated health/status
```

### 10.3 Operational capabilities

The merchant should be able to:

- view sync health
- resync manually
- view widget enablement
- view diagnostics
- later upgrade plan/tier

---

## 11) Failure Paths

### 11.1 Install/auth failure

If Shopify install/auth fails:

- merchant remains outside onboarding
- no provisioning should begin

### 11.2 Provisioning failure

If deployment or bundle provisioning fails:

- no consumer binding should be treated as live
- merchant remains in onboarding repair state

### 11.3 Sync failure

If initial sync fails:

- merchant can still access admin UI
- storefront go-live should remain blocked or clearly marked unhealthy

### 11.4 Verification failure

If publish/apply verification fails:

- the product must not be considered live
- the embedded admin app should show the failure clearly

### 11.5 Theme enablement failure

If the merchant does not enable the theme app extension:

- the deployment may still be healthy
- but the storefront product is not live

This distinction should be visible in the admin UI.

---

## 12) Sequence Diagram Summary

The canonical merchant subscription flow is:

```mermaid
sequenceDiagram
    participant M as Merchant
    participant SA as Shopify
    participant AF as Embedded App
    participant AB as Shopify Backend
    participant CP as Platform
    participant MC as Marketplace Compiler
    participant CR as Consumer Binding
    participant RT as Runtime
    participant TE as Theme Extension

    M->>SA: Install app
    SA->>AB: Complete install/auth
    SA->>AF: Open embedded app
    AF->>AB: Start onboarding
    AB->>CP: Resolve/create customer
    AB->>CP: Create Companion deployment
    CP->>MC: Install companion bundle into draft
    MC-->>CP: Draft contributions compiled
    AB->>CP: Create and bind consumerId
    AF->>AB: Start initial sync
    AB->>SA: Fetch Shopify data
    AB->>CP: Reconcile datasets
    AB->>CP: Publish/apply deployment
    CP->>RT: Apply version
    CP->>CP: Verify release
    AF->>SA: Enable theme app extension
    TE->>CR: Resolve consumerId
    CR-->>TE: Active deployment contract
    TE->>RT: Start shopper session
```

---

## 13) Recommendation

The correct mental model is:

- subscription does not directly mean "chat is live"
- subscription means "provision product, sync data, verify deployment, then expose storefront"

This is the right flow because it preserves:

- the platform deployment lifecycle
- marketplace composition rules
- rollout safety
- stable storefront identity

That is how Shopify Companion should go live.
