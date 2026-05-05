# Managed Product Services Auth Guide

This guide defines the auth model for Platform-managed product services such as the MCP Execution Gateway and Shopify Bridge.

## 1. Auth Layers

Managed product services use four separate auth layers:

- Platform operator auth
- Deployment provider auth
- Product service internal API auth
- External vendor or MCP server auth

Do not collapse these into one key. Each layer has a different blast radius and rotation path.

## 2. Platform Operator Auth

Platform operator auth is used by humans and automation calling Platform APIs.

Primary endpoints:

- `POST /api/platform/auth/login`
- `GET /api/platform/auth/session`
- `GET /api/platform/secrets`
- `GET /api/product-services`
- `POST /api/product-services/{serviceRef}/reconcile`
- `POST /api/product-services/{serviceRef}/restart`
- `POST /api/product-services/{serviceRef}/force-recreate`
- `PUT /api/product-services/{serviceRef}/rotate-secret`

The default Platform auth header is:

```text
X-PLATFORM-API-KEY
```

Session login can also be used when enabled. Platform API keys are control-plane credentials. They must not be reused as product service internal keys, vendor keys, or MCP server keys.

## 3. Deployment Provider Auth

Provider auth lets Platform manage the target infrastructure.

For Coolify, target profiles point to provider credentials, and provider credentials point to Platform secret names:

```text
dtp-coolify-staging -> dpc-coolify-staging -> COOLIFY_STAGING_API_TOKEN
dtp-coolify-production -> dpc-coolify-production -> COOLIFY_PRODUCTION_API_TOKEN
```

These secrets are resolved by Platform during reconcile, restart, force recreate, decommission, provider verification, and drift checks.

The provider token is not injected into the product service container.

## 4. Product Service Internal API Auth

Each managed product service record has a `secretName`. Platform resolves that secret and injects it into the deployed service as the service's private API key.

If a service is created without a `secretName`, Platform generates a managed secret name:

```text
MANAGED_PRODUCT_<SERVICE_REF>_API_KEY
```

Use explicit names for production so staging and production never share internal service keys.

Recommended staging names:

```text
MANAGED_PRODUCT_MCP_EXECUTION_GATEWAY_API_KEY
MANAGED_PRODUCT_SHOPIFY_BRIDGE_PROD_API_KEY
```

Recommended production names:

```text
MANAGED_PRODUCT_MCP_EXECUTION_GATEWAY_PRODUCTION_API_KEY
MANAGED_PRODUCT_SHOPIFY_BRIDGE_PRODUCTION_API_KEY
```

### MCP Execution Gateway

For `serviceKind=MCP_EXECUTION_GATEWAY_SERVICE`, Platform injects the resolved service secret as:

```text
MCP_GATEWAY_INTERNAL_API_KEY
```

The default inbound header is:

```text
X-MCP-GATEWAY-API-KEY
```

The Gateway requires this key for:

```text
/api/internal/**
/api/admin/**
```

Health and info endpoints stay public for infrastructure checks:

```text
/actuator/health
/actuator/info
```

### Shopify Bridge

For `serviceKind=SHOPIFY_BRIDGE_SERVICE`, Platform injects the resolved service secret as:

```text
SHOPIFY_BRIDGE_SHARED_SECRET
SHOPIFY_BRIDGE_PLATFORM_ADMIN_API_KEY
```

The default Bridge admin header is:

```text
X-BRIDGE-API-KEY
```

Bridge admin endpoints require this key:

```text
/api/admin/**
```

When a managed MCP Gateway exists, Platform also injects the Gateway binding into the Bridge:

```text
SHOPIFY_BRIDGE_MCP_GATEWAY_BASE_URL
SHOPIFY_BRIDGE_MCP_GATEWAY_API_KEY
SHOPIFY_BRIDGE_MCP_GATEWAY_API_KEY_HEADER=X-MCP-GATEWAY-API-KEY
SHOPIFY_BRIDGE_MCP_GATEWAY_EXECUTE_PATH=/api/internal/mcp/actions/execute
```

This is the required path for Shopify Bridge to call the generic MCP Gateway instead of implementing customer-facing action behavior directly.

## 5. Shopify App And Webhook Auth

Shopify Bridge also needs Shopify app material. Platform resolves these from Platform secrets and injects them only when present:

```text
SHOPIFY_APP_API_KEY -> SHOPIFY_BRIDGE_SHOPIFY_API_KEY
SHOPIFY_APP_API_SECRET -> SHOPIFY_BRIDGE_SHOPIFY_API_SECRET
SHOPIFY_WEBHOOK_SHARED_SECRET -> SHOPIFY_BRIDGE_WEBHOOK_SHARED_SECRET
```

If `SHOPIFY_WEBHOOK_SHARED_SECRET` is missing, provisioning falls back to `SHOPIFY_APP_API_SECRET` for the deployed webhook shared secret.

Do not confuse these with `SHOPIFY_BRIDGE_SHARED_SECRET`. The bridge shared secret is the Bridge admin/internal API key. Shopify app secrets authenticate Shopify app and webhook flows.

## 6. External MCP Server Auth

Marketplace MCP server auth is config-driven, but raw values do not belong in plugin manifests.

Allowed manifest-side pattern:

```yaml
contributions:
  mcpServers:
    - serverRef: example_mcp
      url: https://example.com/mcp
      auth:
        mode: API_KEY_HEADER_SECRET
        headerName: X-MCP-API-KEY
        secretRef: MCP_SECRET_EXAMPLE_API_KEY
```

The Platform discovery path resolves `secretRef`, `valueSecretRef`, `tokenSecretRef`, and `clientSecretRef` through Platform secrets before calling the Gateway.

The Gateway supports these auth modes:

- `NONE`
- `BEARER_TOKEN_SECRET_REF`
- `STATIC_BEARER_SECRET`
- `API_KEY_HEADER_SECRET`
- `OAUTH2_CLIENT_CREDENTIALS`

API-key header names are allowlisted. The managed Gateway defaults to:

```text
X-API-KEY
X-MCP-API-KEY
X-LOOM-MCP-KEY
```

Blocked header names include platform, bridge, gateway, cookie, host, forwarded, and authorization-control headers. Use a reviewed MCP-specific header instead of arbitrary plugin-declared headers.

## 7. Rotation

Use Platform to rotate product service internal keys:

```http
PUT /api/product-services/{serviceRef}/rotate-secret
```

Then reconcile or restart the service so the new value is injected into Coolify env.

For a Bridge-to-Gateway rotation:

1. Rotate the Gateway service secret.
2. Reconcile/restart the Gateway.
3. Reconcile/restart the Bridge so `SHOPIFY_BRIDGE_MCP_GATEWAY_API_KEY` is refreshed.

For provider token rotation:

1. Rotate the token in Coolify.
2. Update the matching Platform secret, such as `COOLIFY_STAGING_API_TOKEN`.
3. Run provider verification before product-service reconciliation.

## 8. Staging Baseline

Current staging managed product-service baseline:

```text
mcp-execution-gateway
  serviceKind: MCP_EXECUTION_GATEWAY_SERVICE
  environmentScope: staging
  baseUrl: https://mcp-execution-gateway.46.224.145.148.sslip.io
  healthPath: /actuator/health
  secretName: MANAGED_PRODUCT_MCP_EXECUTION_GATEWAY_API_KEY

shopify-bridge-prod
  serviceKind: SHOPIFY_BRIDGE_SERVICE
  environmentScope: staging
  baseUrl: https://shopify-bridge-staging.46.224.145.148.sslip.io
  healthPath: /actuator/health
  secretName: MANAGED_PRODUCT_SHOPIFY_BRIDGE_PROD_API_KEY
```

The service ref `shopify-bridge-prod` is a compatibility name in the current staging record. New production records should use explicit production refs and production secret names.

## 9. Verification

Run public health checks:

```bash
curl -fsS https://loomai-platform-backend.46.224.145.148.sslip.io/actuator/health
curl -fsS https://mcp-execution-gateway.46.224.145.148.sslip.io/actuator/health
curl -fsS https://shopify-bridge-staging.46.224.145.148.sslip.io/actuator/health
```

Check Platform secret metadata without printing values:

```bash
curl -fsS -b "$PLATFORM_COOKIE_JAR" \
  https://loomai-platform-backend.46.224.145.148.sslip.io/api/platform/secrets \
  | jq '[.[] | {name,present,source,required}]'
```

Check product-service records:

```bash
curl -fsS -b "$PLATFORM_COOKIE_JAR" \
  https://loomai-platform-backend.46.224.145.148.sslip.io/api/product-services \
  | jq '[.[] | {serviceRef,serviceKind,environmentScope,baseUrl,secretName,secretConfigured,status,lastReconcileStatus,driftStatus}]'
```

Check managed service readiness through Platform:

```bash
curl -fsS -b "$PLATFORM_COOKIE_JAR" \
  https://loomai-platform-backend.46.224.145.148.sslip.io/api/product-services/mcp-execution-gateway/health

curl -fsS -b "$PLATFORM_COOKIE_JAR" \
  https://loomai-platform-backend.46.224.145.148.sslip.io/api/product-services/shopify-bridge-prod/health
```

Do not print raw secret values during verification.
