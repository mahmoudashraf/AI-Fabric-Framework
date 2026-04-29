# Shopify Companion Developer And Store Admin Guide

Status: developer and merchant-admin operating guide (2026-04-23)

Purpose:

- define the current Shopify Companion operating model
- separate developer/operator responsibilities from store-admin responsibilities
- document what the Shopify admin app should expose
- make the vectorization flow explicit end to end

This guide should be read with:

- `Final_Documentation/Development_Guides/SHOPIFY_INTERNAL_DEVELOPMENT_AND_FULL_DEPLOYMENT_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_READINESS_AUDIT_DEVELOPER_GUIDE.md`
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_READINESS_AUDIT_OPERATOR_GUIDE.md`
- `Final_Documentation/Development_Guides/SHOPIFY_COMPANION_LAUNCH_REVIEW_AND_SUPPORT_EXPORTS_GUIDE.md`
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_MERCHANT_LAUNCH_AND_SUPPORT_GUIDE.md`
- `doc/Productization/future-work/MarketPlace/Products/Companion/SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/Companion/SHOPIFY_COMPANION_VECTORIZATION_TRIGGER_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/Companion/SHOPIFY_COMPANION_SUBSCRIPTION_AND_GO_LIVE_FLOW.md`
- `Final_Documentation/User_Guides/SHOPIFY_COMPANION_CUSTOMER_CAPABILITIES_GUIDE.md`

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
- the store admin should trigger bounded indexing or reindexing for the current enabled scope

Store admins do not edit raw plugins or deployment wiring directly.

Important wording:

- the current implementation still has an internal normalization/sync stage
- that stage should remain a platform concern
- the merchant-facing product language should be `Index`, `Reindex`, and `Live updates`, not `Sync`

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
- preflight, indexing, live updates, storefront activation, playground, support bundle
- store intelligence health
- launch and App Review readiness
- tier ladder and billing posture
- launch, review, support, and lifecycle export packets

Theme app embed:

- storefront launcher and shopper assistant UI
- merchant-placeable AI search, contextual pill, product insight, policy strip, product FAQ, and comparison blocks

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
- `articles` -> contributes to `support-policy`
- `metaobjects` -> contributes to `support-policy`

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
- Articles
- Metaobjects

When those change:

1. the Shopify store record is updated
2. required deployment plugin support is reconciled
3. the vectorization source connection and plan are reconciled to the current scope
4. the merchant can queue indexing or reindexing for current data

### 3.4 Draft and apply behavior

Important rule:

- store-admin actions should not expose raw draft/version/apply controls

Platform behavior:

- marketplace install changes compile into the active deployment draft automatically
- source connection and vectorization plan changes are persisted directly by the platform
- release/apply remains platform-managed

Practical implication:

- developers/operators must treat plugin composition and live deployment release as platform concerns
- merchants should only see bounded actions such as `Reconcile deployment support`, `Index all enabled data`, and `Reindex selected types`

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
- policy version is present and source-family policy blocks are visible
- effective indexed fields are visible for the enabled Shopify scope
- live-update automation summary is healthy or explicitly explains the backlog
- recent Shopify live-update events are visible for operator recovery

### 3.6 Live verification commands

Primary scripts:

- `scripts/verify-shopify-companion.sh`
- `scripts/verify-shopify-companion-uninstall.sh`
- `scripts/run-shopify-companion-rollout.sh`

GitHub Actions entrypoint:

- `.github/workflows/shopify-companion-verification.yml`

Recommended workflow modes:

- `verify`: non-destructive live verification for the configured shop
- `rollout`: platform-side bootstrap / source preflight / go-live progression
- `uninstall_verify`: destructive uninstall verification for a disposable shop mapping only

Current non-destructive verification coverage:

- platform store vectorization summary
- bounded source-family trigger policy visibility
- effective indexed field visibility
- automation queue/dead-letter health visibility
- recent live-update event visibility
- bridge admin vectorization source-page reachability when the bridge admin key is configured

Recommended repository variables for the workflow:

- `PLATFORM_BASE_URL`
  default: `https://ai-fabric-framework-production-324f.up.railway.app`
- `PLATFORM_LOGIN_EMAIL`
  default: `admin@gmail.com`
- `SHOPIFY_BRIDGE_BASE_URL`
  default: `https://shopify-bridge-shopify-bridge-pr-production.up.railway.app`
- `SHOPIFY_COMPANION_SHOP_DOMAIN`
  default: `shopping-companion-test.myshopify.com`
- `SHOPIFY_COMPANION_DISPOSABLE_SHOP_DOMAIN`
  default: empty; must be set explicitly for uninstall verification
- `SHOPIFY_PRODUCT_SERVICE_REF`
  default: `shopify-bridge-prod`
- `SHOPIFY_EMBEDDED_HOST`
  default: empty; set only when merchant-session coverage is needed

Required repository secrets for the workflow:

- preferred: `PLATFORM_API_KEY`
- fallback session auth: `PLATFORM_LOGIN_PASSWORD`
  default: no committed default; keep the password in GitHub secrets only

Optional repository secrets that enable deeper verification coverage:

- `SHOPIFY_BRIDGE_ADMIN_API_KEY`
  default: empty; set to the same secret value configured on the deployed bridge as `SHOPIFY_BRIDGE_SHARED_SECRET`
- `SHOPIFY_ADMIN_ACCESS_TOKEN`
  default: empty
- `SHOPIFY_MERCHANT_AUTHORIZATION`
  default: empty

Bridge admin key rule:

- `SHOPIFY_BRIDGE_ADMIN_API_KEY` is only for Shopify Bridge operator/admin verification endpoints under `/api/admin/*`.
- The verification script sends it using `SHOPIFY_BRIDGE_ADMIN_API_KEY_HEADER`; the default header is `X-BRIDGE-API-KEY`.
- It is not the Shopify store Admin API token. Use `SHOPIFY_ADMIN_ACCESS_TOKEN` for Shopify Admin API coverage.
- If the value is missing, optional bridge admin checks are skipped. If it is wrong, `/api/admin/*` returns `401`. If the deployed bridge has no admin key configured, `/api/admin/*` returns `503`.
- Keep this value only in GitHub/Railway/local secrets or the private handoff; do not paste it in chat or logs.

Governed support scope rule:

- the Shopify app manifest now needs `read_orders` for customer-safe order lookup
- after deploying a scope change, re-open the install URL for the shop so Shopify records the new grant on the live install
- `scripts/run-shopify-companion-rollout.sh` now stops on `PENDING_SCOPE_GRANT` and prints the exact scope-grant URL instead of letting launch work continue under a false green posture

Secret placement rule:

- keep URLs, domains, refs, login email, and embedded host in workflow inputs or repository variables
- keep API keys, bearer tokens, and passwords in repository secrets

Important workflow guardrails:

- `verify` is the default safe mode
- `uninstall_verify` is blocked unless `allow_destructive_uninstall=true`
- `uninstall_verify` also requires `confirm_destructive_shop_domain` to exactly match the resolved shop domain
- disposable uninstall targets should be provided through `SHOPIFY_COMPANION_DISPOSABLE_SHOP_DOMAIN` or an explicit workflow input, never by reusing the main live verification shop accidentally

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

Fresh run succeeds but content freshness still shows `RUNNING`:

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
- run bounded readiness and indexing actions
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
  - Articles
  - Metaobjects
- save source settings

Readiness and launch:

- run source preflight
- review launch and App Review readiness
- review store intelligence health
- request go-live

Indexing:

- view vectorization summary
- reconcile deployment support
- index all enabled data
- reindex selected types
- view last run status and blocking reasons
- configure live update trigger rules when that feature ships

Storefront:

- view storefront activation preview
- open theme editor
- open storefront
- edit bounded launcher settings:
  - launcher label
  - welcome message

Diagnostics:

- view billing posture
- review the tier ladder and governed-action posture
- view webhook subscription health
- use merchant playground
- copy or download support bundle
- copy or download launch dossier, App Store package, App Review guide, review screencast script, support runbook, design-partner rollout packet, and lifecycle/subscription packet

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
10. Use `Index all enabled data` when the deployment vectorization summary is ready.
11. Reindex selected types only when you intentionally want to rebuild part of the enabled scope.
12. Validate answers in the merchant playground.

For an existing live store after scope changes:

1. change source categories
2. save source settings
3. reconcile deployment support if the vectorization panel indicates drift or missing support
4. index all enabled data
5. recheck last run status and storefront behavior

### 4.5 What the indexing buttons mean

`Reconcile deployment support`

- ensures the deployment has the required Shopify data plugins for the current source selection
- ensures the vectorization source connection and plan match the current store scope
- should be used after source-category changes or when the app shows missing or drifted deployment support

`Index all enabled data`

- queues a deployment indexing run for the currently enabled Shopify scope
- should be used after reconcile is healthy and blocking reasons are empty

`Reindex selected types`

- queues a bounded rebuild for selected enabled entity families
- should be used when the merchant wants to refresh only part of the current scope

What the merchant should understand:

- this action is bounded to current enabled categories
- it does not expose plugin internals
- it does not let the merchant rewire the vectorization system

### 4.6 How source categories map to knowledge

If the merchant enables:

- `Products` or `Collections`
  - the platform prepares vectorization for `product`

- `Pages`, `Policies`, `Articles`, or `Metaobjects`
  - the platform prepares vectorization for `support-policy`

This mapping is fixed product behavior, not merchant-authored schema design.

### 4.7 How to interpret the key statuses

Source readiness:

- indicates whether the chosen Shopify categories passed bounded preflight checks

Content freshness:

- indicates whether recent Shopify changes have been absorbed by the platform’s internal normalization layer

Vectorization summary:

- indicates whether the deployment is ready to run indexing for the current scope

Storefront ready:

- indicates whether the assistant is ready to operate on the storefront path

Webhook subscriptions:

- indicates whether required Shopify subscriptions are present and healthy

Billing:

- indicates whether billing blocks launch for the current store plan posture

Lifecycle and subscription packet:

- summarizes install, billing, webhook, sync, and release posture in one bounded merchant-visible export

Store intelligence health:

- summarizes shopper signal, surface usage, journey evidence, and ROI posture without exposing raw infrastructure internals

### 4.8 Merchant troubleshooting

If `Reconcile deployment support` fails:

- use the support bundle
- do not ask the merchant to edit plugin internals

If `Index all enabled data` or `Reindex selected types` is blocked:

- read the blocking reasons shown in the app
- usually this means the deployment support or runner state is not yet ready

If storefront is not ready:

- verify the app embed is enabled on the current theme
- verify the storefront activation preview

If the assistant answers with stale results:

- run `Index all enabled data`
- if the store is already indexed and only part of the enabled scope changed, use `Reindex selected types`

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
- indexing and reindexing
- live update trigger policy
- storefront activation
- testing
- support
- launch, review, and lifecycle packaging

Keep the platform responsible for:

- plugin installation and compilation
- deployment releases
- secrets
- vectorization connection shape
- runner lifecycle
- stale-run cleanup and recovery

That is the clean separation for the current system and for future growth.
