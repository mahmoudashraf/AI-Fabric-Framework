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

## 2) What The Shared Tokens Are And Are Not

The tokens that were shared in chat are not enough to define the whole deployment path.

### 2.1 CLI login token

What it is for:

- authenticating Shopify CLI to Shopify services
- working with app project tooling

What it is not:

- not a store Admin API access token
- not an install token
- not enough by itself to call `/{shop}/admin/api/...`

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

Use a split strategy.

### 3.1 For immediate internal development

Use:

- a Dev Dashboard app
- installed on our owned development store
- client credentials grant for short-lived Admin API access tokens

Why:

- fastest way to test Shopify Admin API access
- good enough for internal webhook and store-data experimentation
- good enough for validating our internal bridge and verification scripts

### 3.2 For the actual Shopify Companion product

Use:

- a proper hosted Shopify app project
- Shopify CLI-managed app and extension workflow
- OAuth-backed store install flow

Why:

- our product already has a hosted Shopify Bridge backend
- the real product needs repeatable merchant install
- theme app extensions are managed and versioned through Shopify CLI deployment flow
- the product cannot depend on manually shared short-lived internal tokens

## 4) Internal Dev Path: Exact Steps

This is the fastest valid path for owned development stores.

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

### 5.2 Set app URLs

Configure the Shopify app with the deployed Shopify Bridge service URLs:

- App URL
- allowed redirection URLs
- webhook callback URLs if needed

This is required for real OAuth and merchant install flow.

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

### 5.6 Merchant-side go-live actions

Even with correct backend deployment, a store admin still needs to:

- install the app
- approve new scopes when scopes change
- enable the app embed in the theme

## 6) What We Need From Administration Side

If we continue with the internal dev-store path, we need:

1. access to the actual app settings for the Dev Dashboard app
2. the real `client_id`
3. the real `client_secret`
4. confirmation that the app is installed on the exact target store
5. confirmation that required Admin API scopes are enabled

The previously shared tokens are not enough for this by themselves.

If we move to the full product path, we additionally need:

1. the Shopify app to be created in the intended owner context
2. permission to configure app URLs against the deployed Shopify Bridge service
3. permission to manage versions and extensions through Shopify CLI
4. a store admin who can complete install and enable the app embed
5. later, app review/admin access as needed for production launch

## 7) Clear Recommendation For Us

For the next implementation stage:

### 7.1 Short-term

Do this:

- keep using the current platform and bridge deployment
- create one proper Dev Dashboard app for the owned dev store
- get the real `client_id` and `client_secret`
- exchange them for a real Admin API token
- use that to finish webhook-subscription live verification

### 7.2 Product path

Do not treat the Dev Dashboard token flow as the final product architecture.

For Loom Companion shipping, standardize on:

- hosted Shopify Bridge backend
- Shopify CLI-managed app project
- OAuth-backed store install
- CLI-managed theme app extension deployment

## 8) Current Blocker Summary

What is currently missing for full Shopify-side verification:

- a valid Admin API token for the exact installed store

What will unblock it:

- `client_id` and `client_secret` for the installed Dev Dashboard app on the target dev store
- or a real OAuth-issued store Admin token for the exact app and store

## 9) Administration Help Checklist

If you want me to complete the Shopify-side verification path next, I need the following from your side:

1. confirm which path we are using now:
   - internal Dev Dashboard path
   - or full hosted app path
2. if internal Dev Dashboard path:
   - provide `client_id`
   - provide `client_secret`
   - confirm the exact installed store domain
3. if full hosted app path:
   - provide access to the app configuration owner context
   - or complete the app-creation and install steps and hand me the app identity details

Without that, we can keep the platform side live and verified, but not complete the direct Shopify Admin API verification path.
