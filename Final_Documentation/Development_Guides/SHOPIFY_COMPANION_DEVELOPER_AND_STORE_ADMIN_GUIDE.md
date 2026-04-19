# Shopify Companion Developer And Store Admin Guide

Status: developer and merchant-admin operating guide (2026-04-19)

Purpose:

- define the current Shopify Companion operating model
- separate developer/operator responsibilities from store-admin responsibilities
- document what the Shopify admin app should expose
- make the vectorization flow explicit end to end

This guide should be read with:

- `Final_Documentation/Development_Guides/SHOPIFY_INTERNAL_DEVELOPMENT_AND_FULL_DEPLOYMENT_GUIDE.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_SUBSCRIPTION_AND_GO_LIVE_FLOW.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md`

## 1) Executive Summary

Shopify Companion now has two operational surfaces:

- platform/operator surface
- Shopify merchant-admin surface

The boundary is intentional:

- store admins choose business scope and run bounded merchant actions
- the platform manages plugins, deployment draft compilation, releases, secrets, and runner infrastructure

For vectorization specifically:

- store admins choose which Shopify source categories should be included
- the platform reconciles the deployment to install or enable the required plugins
- the platform configures the vectorization source connection and plan
- the store admin can trigger vectorization for the current enabled scope

Store admins do not edit raw plugins or deployment wiring directly.

---

## 2) Current Live Architecture

### 2.1 Main components

Platform backend:

- owns deployment lifecycle
- owns marketplace plugin installs and draft compilation
- owns vectorization source connections, plans, runs, and runner registrations
- owns Shopify store binding records and platform customer/deployment/consumer mapping

Shopify Bridge service:

- owns Shopify install/auth/session handling
- owns merchant-facing embedded Shopify admin app
- owns storefront bridge endpoints and theme extension integration
- owns normalized Shopify vectorization source endpoints for products and support-policy data

Shopify admin app:

- merchant-facing bounded control plane
- source selection
- preflight, sync, vectorization, storefront activation, playground, support bundle

Theme app embed:

- storefront launcher and shopper assistant UI

### 2.2 Current vectorization shape

The live Shopify vectorization path is:

- source connection adapter type: `REST_API`
- source auth mode: `API_KEY`
- source auth header: `X-BRIDGE-API-KEY`
- source data provider: Shopify Bridge admin endpoints

This is deliberate. The vectorization runner should not contain Shopify-specific source-adapter logic.

### 2.3 Current source-category to entity-type mapping

- `products` -> contributes to `product`
- `collections` -> contributes to `product`
- `pages` -> contributes to `support-policy`
- `policies` -> contributes to `support-policy`

### 2.4 Current required deployment plugins

For the full current Shopify Companion vectorization path:

- `mkp-action-shopify-companion-read`
- `mkp-inference-shared-embeddings`
- `mkp-data-shopify-catalog`
- `mkp-data-shopify-policies`

The merchant does not install these manually. The platform reconciles them from store scope.

---

## 3) Developer And Operator Guide

### 3.1 Core responsibility split

Developers/operators own:

- Shopify app project and extension deployment
- Shopify Bridge service deployment
- platform backend deployment
- platform-managed product service setup
- deployment release/apply behavior
- secret material
- plugin manifests and plugin compilation behavior
- vectorization runner compatibility and platform cleanup logic

Store admins own:

- store installation approval
- bounded source-category selection
- bounded merchant actions inside the Shopify admin app
- theme app embed enablement

### 3.2 Repo areas that matter

Platform backend:

- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/shopify/`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/vectorization/`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/productservice/`
- `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/`

Shopify Bridge service:

- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/`
- `product-services/shopify-bridge-service/ui/`
- `product-services/shopify-bridge-service/extensions/companion-theme-app-extension/`

Vectorization product modules:

- `ai-fabric-product/ai-fabric-vectorization-core/`
- `ai-fabric-product/ai-fabric-vectorization-runner/`

### 3.3 Shopify admin source selection model

The source toggles are the only merchant-facing data-scope controls:

- Products
- Collections
- Pages
- Policies

When those change:

1. the Shopify store record is updated
2. required deployment plugin support is reconciled
3. the vectorization source connection and plan are reconciled to the current scope
4. the merchant can queue vectorization for current data

### 3.4 Draft and apply behavior

Important rule:

- store-admin actions should not expose raw draft/version/apply controls

Platform behavior:

- marketplace install changes compile into the active deployment draft automatically
- source connection and vectorization plan changes are persisted directly by the platform
- release/apply remains platform-managed

Practical implication:

- developers/operators must treat plugin composition and live deployment release as platform concerns
- merchants should only see bounded actions such as `Reconcile deployment support` and `Vectorize current data`

### 3.5 Live vectorization flow

Current expected flow:

1. store is installed and bootstrapped
2. merchant selects bounded source categories
3. platform reconciles plugin support
4. platform reconciles vectorization source connection and plan
5. source connection becomes `REST_API`
6. runner registration is `ACTIVE`
7. merchant queues vectorization
8. run completes and checkpoints are recorded

Expected healthy live signals:

- source connection status: `READY`
- source adapter type: `REST_API`
- plan status: `ACTIVE`
- runner registration status: `ACTIVE`
- latest run status: `COMPLETED`

### 3.6 Live verification commands

Primary scripts:

- `scripts/verify-shopify-companion.sh`
- `scripts/verify-shopify-companion-uninstall.sh`
- `scripts/run-shopify-companion-rollout.sh`

Useful direct API checks:

- `GET /api/shopify/stores/{shopDomain}/vectorization`
- `POST /api/shopify/stores/{shopDomain}/vectorization/reconcile`
- `POST /api/shopify/stores/{shopDomain}/vectorization/vectorize-now`
- `GET /api/deployments/{deploymentId}/vectorization`
- `GET /api/deployments/{deploymentId}/vectorization/runs/{runId}`
- `GET /api/deployments/{deploymentId}/railway-logs?service=vectorizationRunner`

### 3.7 Common developer/operator failure modes

Old adapter type still present:

- symptom: `sourceAdapterType=SHOPIFY-STORE`
- meaning: source connection has not been reconciled to the new REST path
- action: rerun Shopify vectorization reconcile

Inline secret validation failure:

- symptom: `Inline secret values are blocked. Use secret references instead.`
- meaning: connection config or request payload still uses a secret-shaped field name/value pattern
- action: use secret refs only and neutral config keys

Fresh run succeeds but sync state stays `RUNNING`:

- meaning: old stale runs still exist on superseded plan revisions
- action: the platform cleanup path should close superseded in-flight runs once a newer run completes successfully

No storefront launcher:

- action: check app embed enablement on the current theme and storefront bridge configuration

Webhook diagnostics degraded:

- action: inspect Shopify webhook subscriptions from the merchant app and bridge admin APIs

### 3.8 What developers/operators should not delegate to store admins

Do not ask store admins to:

- install raw marketplace plugins
- edit deployment drafts
- apply deployment versions manually
- rotate bridge or platform secrets
- manage vectorization runner tokens or sessions
- edit vectorization mapping JSON
- tune batch size or low-level execution config

Those belong to the platform and product implementation, not merchant operations.

---

## 4) Store Admin Guide

### 4.1 What the Shopify admin app is for

The Shopify admin app is the merchant control surface for the current store.

It should let the merchant:

- connect and recover the app install for the current shop
- choose which approved Shopify content categories should be used
- run bounded readiness and sync actions
- verify storefront activation
- test the live assistant behavior
- download a support bundle

It should not behave like a deployment IDE or plugin editor.

### 4.2 What the store admin should be allowed to do

Current and recommended merchant controls:

Install and connection:

- reconnect the Shopify app if install recovery is required
- view merchant session and install state

Source scope:

- enable or disable:
  - Products
  - Collections
  - Pages
  - Policies
- save source settings

Readiness and sync:

- run source preflight
- sync now
- request go-live

Vectorization:

- view vectorization summary
- reconcile deployment support
- vectorize current data
- view last run status and blocking reasons

Storefront:

- view storefront activation preview
- open theme editor
- open storefront
- edit bounded launcher settings:
  - launcher label
  - welcome message

Diagnostics:

- view billing posture
- view webhook subscription health
- use merchant playground
- copy or download support bundle

### 4.3 What the store admin should not be allowed to do

Do not expose these in Shopify admin:

- raw plugin install/update/delete
- deployment version apply controls
- deployment draft editing
- source connection JSON
- vectorization mapping config
- chunking or batch tuning
- secret refs or secret names
- runner registration and runner token controls
- platform customer or deployment remapping

The rule is:

- merchants choose business scope
- the platform translates that into technical deployment state

### 4.4 Recommended merchant flow

For a new store:

1. Install or reconnect the Shopify app.
2. Confirm the current store is connected.
3. Choose source categories.
4. Save source settings.
5. Run source preflight.
6. Bootstrap the store if it is not already bootstrapped.
7. Request go-live.
8. Enable the theme app embed.
9. Open the storefront once.
10. Use `Sync now` if needed.
11. Use `Vectorize current data` when the deployment vectorization summary is ready.
12. Validate answers in the merchant playground.

For an existing live store after scope changes:

1. change source categories
2. save source settings
3. reconcile deployment support if the vectorization panel indicates drift or missing support
4. vectorize current data
5. recheck last run status and storefront behavior

### 4.5 What the vectorization buttons mean

`Reconcile deployment support`

- ensures the deployment has the required Shopify data plugins for the current source selection
- ensures the vectorization source connection and plan match the current store scope
- should be used after source-category changes or when the app shows missing or drifted deployment support

`Vectorize current data`

- queues a deployment vectorization run for the currently enabled Shopify scope
- should be used after reconcile is healthy and blocking reasons are empty

What the merchant should understand:

- this action is bounded to current enabled categories
- it does not expose plugin internals
- it does not let the merchant rewire the vectorization system

### 4.6 How source categories map to knowledge

If the merchant enables:

- `Products` or `Collections`
  - the platform prepares vectorization for `product`

- `Pages` or `Policies`
  - the platform prepares vectorization for `support-policy`

This mapping is fixed product behavior, not merchant-authored schema design.

### 4.7 How to interpret the key statuses

Source readiness:

- indicates whether the chosen Shopify categories passed bounded preflight checks

Sync status:

- indicates whether Shopify content has been synced into the platform runtime flow

Vectorization summary:

- indicates whether the deployment is ready to run vectorization for the current scope

Storefront ready:

- indicates whether the assistant is ready to operate on the storefront path

Webhook subscriptions:

- indicates whether required Shopify subscriptions are present and healthy

Billing:

- indicates whether billing blocks launch for the current store plan posture

### 4.8 Merchant troubleshooting

If `Reconcile deployment support` fails:

- use the support bundle
- do not ask the merchant to edit plugin internals

If `Vectorize current data` is blocked:

- read the blocking reasons shown in the app
- usually this means the deployment support or runner state is not yet ready

If storefront is not ready:

- verify the app embed is enabled on the current theme
- verify the storefront activation preview

If the assistant answers with stale results:

- run `Sync now`
- if scope changed, also run `Vectorize current data`

---

## 5) Recommended Product Boundary

This is the correct long-term product posture:

- Shopify admin app = merchant-facing bounded control plane
- platform = plugin, deployment, release, secret, and runner control plane

That keeps the merchant UI usable and safe.

If we expose raw plugins or low-level deployment mechanics in Shopify admin, we will turn a product surface into an infrastructure console. That would be the wrong design.

---

## 6) Final Recommendation

Keep the Shopify admin app focused on:

- scope selection
- readiness
- sync
- vectorization trigger
- storefront activation
- testing
- support

Keep the platform responsible for:

- plugin installation and compilation
- deployment releases
- secrets
- vectorization connection shape
- runner lifecycle
- stale-run cleanup and recovery

That is the clean separation for the current system and for future growth.
