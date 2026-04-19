# Shopify Internal Development And Full Deployment Guide

Status: internal development and deployment guide (2026-04-19)

Purpose:

- clarify which Shopify app path we should use for internal development
- clarify which path we should use for the real Shopify Companion product
- document what credentials are actually required
- document what still requires Shopify admin or partner-side action

This guide should be read with:

- `doc/Productization/future-work/Auth/SHOPIFY_APP_ARCHITECTURE_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_IMPLEMENTATION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/SHOPIFY_COMPANION_SUBSCRIPTION_AND_GO_LIVE_FLOW.md`

Current live Loom Companion Shopify app state:

- real Shopify CLI-managed app exists
- app name: `Loom Companion`
- app handle: `loom-companion`
- app client id: `939df746e3bbbb0a8ab2f31cf94bd11b`
- app URL: `https://shopify-bridge-shopify-bridge-pr-production.up.railway.app`
- OAuth callback URL: `https://shopify-bridge-shopify-bridge-pr-production.up.railway.app/auth/shopify/callback`
- app proxy URL: `https://shopify-bridge-shopify-bridge-pr-production.up.railway.app`
- target dev store: `shopping-companion-test.myshopify.com`
- platform bootstrap for the target dev store already exists:
  - customer: `cus-8ac907b8`
  - deployment: `dep-b8d3e57b`
  - consumer: `shopify-shopping-companion-test`
- remaining live gap:
  - the app still needs to be installed and approved on the target dev store
  - the theme app embed still needs to be enabled in the store theme
- latest validated Shopify app release from the repo workflow:
  - `loom-companion-8`
- current theme-shell posture:
  - `legacy` is still the default shell
  - `max-mode` is now available as an explicit theme app embed setting

Relevant official Shopify references:

- Create apps using the Dev Dashboard: <https://shopify.dev/docs/apps/build/dev-dashboard/create-apps-using-dev-dashboard>
- Get API access tokens for Dev Dashboard apps: <https://shopify.dev/apps/build/dev-dashboard/get-api-access-tokens>
- About client credentials: <https://shopify.dev/docs/apps/build/authentication-authorization/client-secrets>
- About managing webhook subscriptions: <https://shopify.dev/docs/apps/build/webhooks/subscribe>
- Scaffold an app: <https://shopify.dev/docs/apps/getting-started/create>
- Build theme app extensions: <https://shopify.dev/apps/build/online-store/theme-app-extensions/build>
- About deployment: <https://shopify.dev/docs/apps/launch/deployment>

## 1) Executive Summary

There are two valid Shopify paths, and we should stop mixing them.

Current decision:

- go directly with the real Shopify CLI-managed app path for Loom Companion
- keep the Dev Dashboard path only as an optional internal fallback

### Path A: Internal dev-store path

Use this only for:

- internal testing
- one-store development
- quick API exploration
- verifying app-install, Admin API, webhook, and theme-extension mechanics on our own dev store

Recommended mechanism:

- Dev Dashboard app
- install on the same owned dev store
- use client credentials grant to obtain short-lived Admin API access tokens

### Path B: Real Shopify Companion product path

Use this for:

- the actual Loom Companion product
- hosted Shopify Bridge service
- merchant installs
- stable OAuth-backed store authorization
- theme app extension deployment
- repeatable production rollout

Recommended mechanism:

- Shopify app created and managed through Shopify CLI / app project workflow
- hosted backend
- OAuth install flow
- persisted offline or refreshable store authorization
- Shopify CLI-managed extension deployment

This is the path we should treat as canonical for productization.
It is the path we should build now.

## 2) What The Shared Tokens Are And Are Not

The tokens that were shared in chat are not enough to define the whole deployment path.

### 2.1 CLI login token

What it is for:

- authenticating Shopify CLI to Shopify services
- working with app project tooling
- creating or linking the real Shopify app project
- deploying app configuration and theme app extensions through Shopify CLI
- running non-interactive app deploys through:
  - `SHOPIFY_CLI_PARTNERS_TOKEN`

What it is not:

- not a store Admin API access token
- not an install token
- not enough by itself to call `/{shop}/admin/api/...`

Current validated execution path:

- `SHOPIFY_CLI_PARTNERS_TOKEN=<partner-cli-token> shopify app deploy --allow-updates`
- this works for the current Loom Companion app workspace without interactive login

### 2.2 Catalog or Dev Dashboard API key/token

What it may be for:

- catalog or developer tooling APIs
- Dev Dashboard-specific integrations

What it is not:

- not automatically a store-scoped Shopify Admin API token
- not enough by itself to install the app on a store
- not enough by itself to inspect webhook subscriptions through the store Admin GraphQL API

### 2.3 What the store Admin API actually requires

For direct store Admin API calls, we need one of:

- a valid access token acquired from a Dev Dashboard app installed on that exact store using client credentials grant
- or a valid access token acquired through OAuth install for that exact app and store

If the token cannot successfully call:

- `https://{shop}.myshopify.com/admin/api/<version>/graphql.json`

then it is not the right token for Admin API verification.

## 3) Recommended Decision

Use the real Shopify app path by default.

### 3.1 Canonical path for Loom Companion

Use:

- a proper hosted Shopify app project
- Shopify CLI-managed app and extension workflow
- OAuth-backed store install flow

Why:

- our product already has a hosted Shopify Bridge backend
- the real product needs repeatable merchant install
- theme app extensions are managed and versioned through Shopify CLI deployment flow
- the product cannot depend on manually shared short-lived internal tokens
- using the real path now avoids building a temporary path we would immediately outgrow

### 3.2 Optional internal fallback

Use:

- a Dev Dashboard app
- installed on our owned development store
- client credentials grant for short-lived Admin API access tokens

Why:

- it is useful only for narrow internal Admin API experimentation
- it is not required for the real Loom Companion deployment path
- it should not become the product architecture

## 4) Internal Dev Path: Exact Steps

This is an optional internal path for owned development stores.
It is not required before building the real Shopify Companion app.

### 4.1 Create the app

In Shopify Dev Dashboard:

1. Create an app.
2. Install it on the target development store.
3. Configure the required Admin API scopes.

For our current product direction, start with:

- `read_products`
- `read_content`

Add more scopes only when the feature actually needs them.

### 4.2 Obtain client credentials

From the app settings in Dev Dashboard, capture:

- `client_id`
- `client_secret`

These are the credentials we actually need.

### 4.3 Exchange client credentials for an Admin API token

For Dev Dashboard apps on an owned development store, request a token from:

```bash
curl -X POST "https://{shop}.myshopify.com/admin/oauth/access_token" \
  -H "Content-Type: application/json" \
  --data '{
    "client_id": "<CLIENT_ID>",
    "client_secret": "<CLIENT_SECRET>",
    "grant_type": "client_credentials"
  }'
```

Expect:

- `access_token`
- expiry around 24 hours

This token should then work against:

```bash
curl -X POST "https://{shop}.myshopify.com/admin/api/2026-04/graphql.json" \
  -H "Content-Type: application/json" \
  -H "X-Shopify-Access-Token: <ACCESS_TOKEN>" \
  --data '{"query":"{ shop { name } }"}'
```

Expected result:

- HTTP `200`

If this does not return `200`, the token is not usable for store Admin API verification.

### 4.4 Configure webhook subscriptions

For admin-created or internal custom-app-style setups, configure webhook subscriptions using the GraphQL Admin API.

Do not assume CLI app config alone will do this for the internal path.

### 4.5 Build and deploy the theme app extension

Use Shopify CLI from the app project to:

- generate the theme app extension
- preview it locally
- deploy app versions and extension versions

The extension is part of the app version and must be deployed through Shopify’s app deployment flow.

### 4.6 Enable the app embed

A store admin still needs to:

- open the theme editor
- enable the Companion app embed
- save the theme change

This is part of go-live, not a background API detail.

## 5) Full Shopify Companion Product Path

This is the path we should document as canonical for shipping the product.

### 5.1 Create the hosted app project

Use Shopify CLI to create and manage the app project.

The project owns:

- app identity
- app configuration
- app versions
- theme app extension

Our hosted services remain:

- Platform backend
- Platform UI
- runtime and connector services
- Shopify Bridge service

Important:

- Shopify CLI can manage the app project, app configuration, and extension deployment
- Shopify CLI does not replace our own hosted web app deployment
- the Shopify Bridge service still needs to be deployed on our infrastructure

### 5.2 Set app URLs

Configure the Shopify app with the correct split URLs:

- App URL
  - the merchant embedded-app URL
- allowed redirection URLs
  - handled by the Shopify Bridge backend callback endpoint
- webhook callback URLs if needed
  - handled by the Shopify Bridge backend
- app proxy URL
  - pointed at the Shopify Bridge backend

This is required for real OAuth and merchant install flow.

In repo terms:

- `SHOPIFY_APP_PUBLIC_BASE_URL`
  - merchant embedded app URL
- `SHOPIFY_BRIDGE_PUBLIC_BASE_URL`
  - hosted backend URL for OAuth callback, webhooks, and storefront proxy traffic

If both are served from the same host, they can be set to the same value.

Current repo default:

- the Railway image for `product-services/shopify-bridge-service` builds the merchant UI from `ui/`
- the built files are copied into the bridge container
- the bridge service serves the embedded app shell and `/assets/**`

So the default production posture is a same-host deployment unless we later split the merchant UI into its own hosted surface.

### 5.3 Use real install auth

For the product path, use OAuth-backed store install, not manually shared temporary tokens.

The backend should persist:

- store authorization state
- access token
- refresh token if provided by the current auth flow
- scopes
- webhook verification secret material as required

### 5.4 Bind store to platform

After install:

1. create or resolve the platform customer
2. create or resolve the deployment
3. create or resolve the `consumerId`
4. bootstrap the Shopify Companion bundle
5. run source preflight
6. publish, apply, sync, verify
7. enable storefront widget

This is our actual product path.

### 5.5 Deploy the extension

Use Shopify CLI deploy flow to publish the app version that contains:

- the embedded admin app config
- the theme app extension
- the generated `shopify.app.toml`
- the CLI web process definitions (`shopify.web.toml` and `ui/shopify.web.toml`)
- the synced shared widget bundle for the optional `max-mode` storefront shell

Current repo command posture:

- `npm -C product-services/shopify-bridge-service run shopify:app:deploy`
- `npm -C product-services/shopify-bridge-service run shopify:app:release`

Both commands now:

- run workspace preflight
- rebuild `max-mode-widget`
- sync `max-mode-widget.iife.js` into the theme app extension assets
- render the Shopify app config
- then invoke Shopify CLI

This matters because the `max-mode` storefront shell depends on the synced IIFE bundle.

### 5.6 Merchant-side go-live actions

Even with correct backend deployment, a store admin still needs to:

- install the app
- approve new scopes when scopes change
- enable the app embed in the theme

CLI helps with the app project and deployment flow, but these merchant-side actions still exist.

## 6) What We Need From Administration Side

For the real Shopify CLI-managed path, we need:

1. the correct Shopify owner context for the real app
2. either:
   - a Shopify login/session in that owner context
   - or a valid Partner Dashboard CLI token exported as `SHOPIFY_CLI_PARTNERS_TOKEN`
3. authority to create or confirm the real app in Shopify
4. authority to set app URLs and redirect URLs
5. authority to install the app on the target store
6. authority to enable the app embed in the store theme

For the optional internal Dev Dashboard fallback, we would instead need:

1. access to the actual app settings for the Dev Dashboard app
2. the real `client_id`
3. the real `client_secret`
4. confirmation that the app is installed on the exact target store
5. confirmation that required Admin API scopes are enabled

The previously shared tokens are not enough for this by themselves.

## 7) Clear Recommendation For Us

For the next implementation stage:

### 7.1 Short-term

Do this:

- keep using the current platform and bridge deployment
- initialize the real Shopify app project through Shopify CLI
- connect it to the hosted Shopify Bridge service
- configure the app URLs and extension structure
- use the real app path as the default implementation path

### 7.2 Product path

This is not a second step anymore. This is the same path as 7.1.

For Loom Companion shipping, standardize on:

- hosted Shopify Bridge backend
- Shopify CLI-managed app project
- OAuth-backed store install
- CLI-managed theme app extension deployment

## 8) Current Full-Deployment State

What is already done:

- the real Shopify CLI-managed app project exists in Shopify
- app configuration and the theme app extension have been deployed through Shopify CLI
- non-interactive CLI deploy from this repo has been validated with `SHOPIFY_CLI_PARTNERS_TOKEN`
- the Shopify Bridge service is live and correctly serves:
  - embedded app shell
  - OAuth install endpoint
  - OAuth callback endpoint
  - app proxy target
- the install endpoint now returns the expected Shopify OAuth redirect
- the target dev store already has a platform-side store mapping and bootstrap:
  - customer created
  - deployment created
  - consumer binding created

What is still missing for end-to-end go-live on the target dev store:

- the store admin must install and approve the app on `shopping-companion-test.myshopify.com`
- the OAuth callback must persist the real store access token into the platform store credential path
- source preflight must run against the real store credentials
- publish/apply/sync/verify must run for the target store deployment
- the theme app embed must be enabled in the theme editor

What this means in practice:

- the remaining blocker is no longer app creation or CLI setup
- the remaining blocker is the store-admin approval path inside Shopify plus the post-install rollout steps

## 9) Exact Remaining Admin Steps

For the current live Loom Companion app, the remaining Shopify-side path is:

1. Open the install URL while logged into the target store admin:
   - `https://shopify-bridge-shopify-bridge-pr-production.up.railway.app/auth/shopify/install?shop=shopping-companion-test.myshopify.com`
2. Approve the app install and requested scopes for the store.
3. Let Shopify redirect back to:
   - `https://shopify-bridge-shopify-bridge-pr-production.up.railway.app/auth/shopify/callback`
4. Confirm the embedded app lands back on the bridge-served merchant UI.
5. In Shopify theme editor, enable the Loom Companion app embed and save the theme.

Theme editor detail:

- the embed now exposes a `Widget shell` setting
- keep `legacy` as the default production choice for now
- switch to `max-mode` only when we intentionally want to validate the shared Max Mode storefront shell on the store

After that, the remaining steps are ours:

1. verify that the platform store record now has live credentials
2. run source preflight
3. request go-live for the bootstrapped deployment
4. verify apply-time sync and post-apply verification
5. verify storefront bootstrap, suggestions, and shopper query path

Operator helper now available:

- `scripts/run-shopify-companion-rollout.sh`

Current intended usage:

```bash
PLATFORM_BASE_URL=https://ai-fabric-framework-production-324f.up.railway.app \
SHOPIFY_BRIDGE_BASE_URL=https://shopify-bridge-shopify-bridge-pr-production.up.railway.app \
SHOP_DOMAIN=shopping-companion-test.myshopify.com \
PLATFORM_SESSION_COOKIE_JAR=/tmp/platform-shopify.cookies \
PRODUCT_SERVICE_REF=shopify-bridge-prod \
bash scripts/run-shopify-companion-rollout.sh
```

What it does:

- ensures the platform store mapping exists
- bootstraps customer/deployment/consumer bindings when missing
- prints the exact live install URL
- stops cleanly if Shopify install approval is still missing
- after install, it can continue into live source preflight and go-live from the platform side

What I still need from administration side:

- one real store-admin install/approval pass on `shopping-companion-test.myshopify.com`
- one real theme-editor enablement pass for the app embed

What I do not need anymore:

- I do not need another app-creation step
- I do not need another app-creation step for the current app
- I do not need an interactive Shopify CLI login if `SHOPIFY_CLI_PARTNERS_TOKEN` is available
- I do not need the Dev Dashboard fallback path for the real deployment
