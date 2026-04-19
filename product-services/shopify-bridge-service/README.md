# Shopify Bridge Service Workspace

This directory now serves two purposes:

1. the hosted `Shopify Bridge Service` backend and merchant UI
2. the Shopify CLI-managed app workspace for the real Loom Companion app

## Structure

- `src/main/java`
  - Spring Boot backend for install flow, webhook handling, platform integration, and storefront bridge endpoints
- `ui/`
  - merchant embedded app UI built with React and Polaris
- `extensions/companion-theme-app-extension/`
  - Shopify theme app extension for storefront delivery
- `deploy/shopify/`
  - Shopify CLI support files and helper scripts
- `shopify.app.toml`
  - generated local Shopify app config for Shopify CLI
  - not committed
- `shopify.web.toml`
  - backend process definition for Shopify CLI
- `ui/shopify.web.toml`
  - frontend process definition for Shopify CLI

## Local Shopify CLI flow

1. copy `deploy/shopify/.env.shopify.example` to `deploy/shopify/.env.shopify`
2. fill in the real values
3. run:

```bash
npm run shopify:preflight
npm run shopify:config:render
```

4. once the local environment is healthy and Shopify CLI is installed:

```bash
npm run shopify:app:dev
```

or:

```bash
npm run shopify:app:deploy
```

The generated `shopify.app.toml` uses two separate URLs:

- `SHOPIFY_APP_PUBLIC_BASE_URL`
  - the merchant embedded-app URL loaded by Shopify admin
- `SHOPIFY_BRIDGE_PUBLIC_BASE_URL`
  - the hosted bridge backend that receives OAuth callbacks, webhooks, and storefront proxy traffic

If the merchant UI is served from the same host as the backend, set both values to the same base URL.

## Important boundary

This workspace supports the real Shopify CLI-managed app path.

It does not replace:

- deployment of the hosted bridge backend
- Shopify owner-context login
- merchant install approval
- theme app embed enablement

## Current local blocker

If `npm init @shopify/app@latest` or `shopify app ...` fails with `ENOSPC`, free disk space first.
The preflight script checks for that explicitly.
