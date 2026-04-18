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

CLI helps with the app project and deployment flow, but these merchant-side actions still exist.

## 6) What We Need From Administration Side

For the real Shopify CLI-managed path, we need:

1. the correct Shopify owner context for the real app
2. a Shopify login/session in that owner context when the CLI needs interactive authentication
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

## 8) Current Blocker Summary

What is currently missing for full Shopify-side verification:

- the real Shopify CLI-managed app project in the correct owner context
- enough local disk space on this machine to run Shopify CLI scaffolding and package installation
- after the app exists, the correct store-scoped Admin API credentials for direct Shopify Admin verification when needed

What will unblock it:

- free local disk space first
- then initialize or link the real Shopify app project through Shopify CLI
- then complete the remaining Shopify owner/admin steps for install and theme enablement

## 9) Administration Help Checklist

If you want me to complete the Shopify-side verification path next, I need the following from your side:

1. confirm the Shopify owner context that should own the real app
2. be available for any required interactive Shopify login or app-owner confirmation
3. complete any Shopify-side install and theme app embed enablement steps if the CLI cannot do them headlessly
4. optionally, if we fall back to the Dev Dashboard path for a narrow verification need:
   - provide `client_id`
   - provide `client_secret`
   - confirm the exact installed store domain

Without that, I can still prepare the repo and deployment side, but not finish the Shopify-side install and go-live path end to end.
