# Shopify MCP-First And MCP Gateway Development Guide

Status: current branch guide for Plans 009, 009.1, and 009.2 (2026-05-05)

This guide explains the Shopify MCP-first implementation and the generic MCP Execution Gateway path that came out of Plans `009`, `009.1`, and `009.2`.

Companion plans:

- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/009_1_MARKETPLACE_CONFIG_DRIVEN_MCP_CAPABILITY_ARCHITECTURE.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/009_2_MCP_EXECUTION_GATEWAY_EXTRACTION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/009_3_SHOPIFY_MCP_MARKET_READINESS_AND_RELEASE_GATE.md`
- `Final_Documentation/Development_Guides/MANAGED_PRODUCT_SERVICES_AUTH_GUIDE.md`
- `Final_Documentation/Development_Guides/MARKETPLACE_PLUGIN_MANIFEST_REFERENCE.md`
- `Final_Documentation/Development_Guides/COOLIFY_HETZNER_ADMINISTRATION_GUIDE.md`

Do not place raw Shopify, Coolify, MCP server, or Platform service secrets in this guide. Use Platform-managed secrets, local `/tmp` secret files, or the ignored private handoff.

---

## 1. Architecture

The customer-facing Shopify action path is now:

```text
Marketplace ACTION plugin
  -> deployment marketplace compiler
  -> runtime actionsConfig
  -> product host when required
  -> MCP Execution Gateway
  -> MCP initialize/tools.list/tools.call
  -> normalized action evidence
```

Shopify Bridge remains Shopify-specific. It owns Shopify install state, store binding, billing posture, storefront readiness, shopper/customer session checks, and Shopify-specific governance. It does not own customer-facing action implementations as Java switch cases.

The MCP Execution Gateway is generic. It owns MCP transport, server binding, auth profile resolution, schema drift checks, argument rendering, `tools/call`, and normalized MCP evidence.

Runtime/connector can call the MCP Gateway directly for hostless generic MCP actions. Shopify actions still route through Shopify Bridge when store, billing, customer, checkout, or Shopify-specific posture is required.

Shopify Companion indexing is source-backed, not document-push-first. The normal freshness path is Shopify Admin API through Bridge vectorization-source endpoints, Platform vectorization runner, and Runtime derived index. Do not add customer-facing Shopify action or answer-quality behavior that depends on Bridge or Platform maintaining a second durable copy of the full store catalog.

---

## 2. What Plan 009 Delivered

Plan 009 proved Shopify MCP as the product execution path.

Key behavior:

- Shopify read actions are Marketplace `ACTION` plugins using `adapterType=mcp-tool`.
- Runtime `actionsConfig` preserves `execution.mcp.serverRef`, `toolName`, `argumentTemplate`, drift policy, and response mapping.
- `shopify_search_catalog`, `shopify_search_policies`, and `shopify_get_product_details` use Shopify Storefront MCP tools.
- Customer Account and Checkout MCP plugin bundles exist and are gated by external Shopify auth/readiness material.
- The Shopify Companion Elite launch profile includes `guided-commerce` and `order-self-service` action packages. Order self-service is still governed by package entitlement, explicit confirmation, customer/checkout auth, audit, and real Marketplace/MCP action config.
- Legacy Shopify customer-facing aliases are not preserved for greenfield action execution.

Important migrations:

- `V81__shopify_mcp_search_catalog_action.sql`
- `V82__shopify_mcp_action_plugin_bundles.sql`
- `V83__shopify_customer_account_checkout_mcp_bundles.sql`

Important Platform code:

- `MarketplaceManifestService`
- `DeploymentMarketplaceDraftCompilerService`
- `ShopifyCompanionPackageProfileCatalogService`
- `ShopifyStoreProvisioningService`

Important Bridge code:

- `ShopifyBridgeActionExecutionService`
- `ShopifyMcpReadinessService`
- `McpActionExecutionGateway`

---

## 3. What Plan 009.1 Delivered

Plan 009.1 generalized MCP support through Marketplace configuration.

Implemented contract:

- Existing `ACTION` plugins can declare `contributions.mcpServers`.
- Existing `ACTION` plugins can set `adapterType=mcp-tool`.
- `execution.mcp.serverRef` and `execution.mcp.toolName` are required for MCP actions.
- MCP server metadata compiles into deterministic runtime action config.
- `tools/list` is discovery and verification evidence only. It is not runtime product truth.
- Marketplace discovery calls the generic gateway to run `initialize` and `tools/list`, then creates private draft plugins.
- Schema hashes are canonicalized to ignore descriptions, display-only metadata, and unordered arrays.
- API-key auth header names are allowlisted and never derived from user/session/runtime input.
- Response mappings use a restricted JSONPath subset.

Supported gateway auth modes for config-only MCP servers:

- `NONE`
- `STATIC_BEARER_SECRET`
- `BEARER_TOKEN_SECRET_REF`
- `API_KEY_HEADER_SECRET`
- `OAUTH2_CLIENT_CREDENTIALS`

Auth modes that still require product/host support:

- `OAUTH2_AUTH_CODE_PKCE`
- `CUSTOMER_OAUTH_PKCE`
- provider-specific customer/session delegation

---

## 4. What Plan 009.2 Delivered

Plan 009.2 extracted the generic MCP executor into a standalone managed product service:

```text
product-services/mcp-execution-gateway-service
```

Gateway capabilities:

- Streamable HTTP MCP `initialize`.
- initialized notification.
- `tools/list`.
- `tools/call`.
- JSON and SSE response handling.
- `MCP-Protocol-Version`.
- `MCP-Session-Id` capture and reuse.
- Server verification against expected tool schema hashes.
- Execution-time blocking drift guard.
- Normalized `MCP_TOOL_RESULT` evidence.
- Internal API-key protection for `/api/internal/**` and `/api/admin/**`.

Platform-managed service behavior:

- The gateway is a first-class Product Service.
- Platform can reconcile it to Coolify.
- Platform can restart, health check, read history/logs, scale desired replicas, rotate the service secret, force recreate, and decommission it.
- Force recreate clears stale Platform/Coolify linkage and can recover from a deleted or stale Coolify UUID by adopting/deleting the domain/name match before recreating from desired state.

Production is supported by target profile, but production deployment is explicit. Staging remains the implicit/default target.

Plan 009.3 makes market readiness release-gated: the Shopify MCP product is design-partner ready only after staging `full-platform-release-readiness` passes with `shopify-mcp-gateway-verification`, `shopify-companion-verification`, and `shopify-first-product-readiness-audit` included.

The hosted full suite runs `marketplace-install-flow` with `MARKETPLACE_INSTALL_FLOW_APPLY_RELEASE=false`. That keeps Marketplace plugin bootstrap, compile, publish, artifact, shared-vector posture, and cleanup release-blocking without launching a new heavyweight validation runtime on the shared staging host; the later canonical hosted verification stages remain responsible for runtime apply and live query evidence.

---

## 5. Adding A New MCP Server

Use Marketplace configuration first. Do not add Java code for a new MCP server when the server fits the config-only contract.

Config-only is enough when:

- transport is `STREAMABLE_HTTP`
- auth is one of the supported config-only modes
- the endpoint can be resolved from install config or a reviewed endpoint kind/profile
- tool arguments can be rendered from structured `argumentTemplate`
- response evidence can be normalized with the restricted mapping DSL
- no product-specific customer/session/payment/legal posture is required

Java/product-host work is still required when:

- the provider requires custom login UX
- customer OAuth/PKCE or protected customer data must be bound to platform sessions
- terminal checkout, payment, refund, or legal workflows need domain policy
- the provider uses an unsupported transport
- a new auth provider type is needed
- tool execution must be mediated by a product-specific host

Default import flow:

```text
Operator enters endpoint/auth profile
  -> Platform calls MCP Gateway discovery
  -> Gateway runs initialize and tools/list
  -> Platform creates private ACTION draft
  -> operator maps action ids, risk, tier, confirmation, auth, response mapping
  -> review/publish/install
  -> deployment compiler emits actionsConfig
  -> runtime or product host calls MCP Gateway
```

---

## 6. Managed Product Service Profiles

Customer runtimes and Platform-managed product services both use deployment target profiles, but product services have an extra gate:

```text
deployment_target_profiles.platform_services_allowed
```

Current target profile posture:

- `dtp-coolify-staging`
  - active
  - default for runtime
  - default for restartable services
  - allowed for Platform-managed product services
  - implicit target for managed product services that do not specify `targetProfileId`
- `dtp-coolify-production`
  - active
  - not default for runtime
  - not default for restartable services
  - allowed for Platform-managed product services
  - explicit target only

This prevents accidental production placement. If more than one Coolify profile allows platform services and no default restartable-services profile is selected, product-service reconciliation fails closed and requires `targetProfileId`.

The Platform Product Services creation dialog loads active Coolify target profiles and shows profiles where `platformServicesAllowed=true`. The API still accepts `targetProfileId` directly for automation.

Create an explicit production-managed MCP Gateway record with a production service ref and production target profile:

```json
{
  "serviceRef": "mcp-execution-gateway-production",
  "displayName": "MCP Execution Gateway Production",
  "productFamily": "MCP",
  "serviceKind": "MCP_EXECUTION_GATEWAY_SERVICE",
  "deploymentMode": "SHARED_PLATFORM_SERVICE",
  "tenantMode": "MULTI_TENANT_SHARED",
  "environmentScope": "production",
  "desiredReplicas": 1,
  "minReplicas": 1,
  "maxReplicas": 3,
  "healthPath": "/actuator/health",
  "serviceRoot": "product-services/mcp-execution-gateway-service",
  "dockerfilePath": "product-services/mcp-execution-gateway-service/deploy/container/Dockerfile",
  "secretName": "MANAGED_PRODUCT_MCP_EXECUTION_GATEWAY_PRODUCTION_API_KEY",
  "targetProfileId": "dtp-coolify-production"
}
```

Create an explicit production Shopify Bridge record the same way, using a separate production service ref. Do not reuse a staging compatibility service ref for production:

```json
{
  "serviceRef": "shopify-bridge-production",
  "displayName": "Shopify Bridge Production",
  "productFamily": "SHOPIFY",
  "serviceKind": "SHOPIFY_BRIDGE_SERVICE",
  "deploymentMode": "SHARED_PLATFORM_SERVICE",
  "tenantMode": "MULTI_TENANT_SHARED",
  "environmentScope": "production",
  "desiredReplicas": 1,
  "minReplicas": 1,
  "maxReplicas": 3,
  "healthPath": "/actuator/health",
  "serviceRoot": "product-services/shopify-bridge-service",
  "dockerfilePath": "product-services/shopify-bridge-service/deploy/container/Dockerfile",
  "secretName": "MANAGED_PRODUCT_SHOPIFY_BRIDGE_PRODUCTION_API_KEY",
  "targetProfileId": "dtp-coolify-production"
}
```

Production product-service records must have their own secret names. Do not share staging internal service keys with production.

---

## 7. Secret Boundaries

For the full managed product-service auth model, rotation path, and staging baseline, see `Final_Documentation/Development_Guides/MANAGED_PRODUCT_SERVICES_AUTH_GUIDE.md`.

Never store raw provider secrets in:

- Marketplace manifests
- deployment draft config
- runtime action catalogs
- LLM traces
- browser clients

Allowed secret locations:

- Platform managed secrets
- Coolify environment variables written by Platform
- ignored local handoff files
- local `/tmp` secret files with mode `600`

Gateway and Bridge internal calls use service API keys:

- Gateway: `MCP_GATEWAY_INTERNAL_API_KEY`
- Bridge: `SHOPIFY_BRIDGE_SHARED_SECRET`

External MCP provider auth is resolved server-side by the gateway from reviewed auth profile config and secret refs.

---

## 8. Local Verification

Run focused tests while editing:

```bash
mvn -f Platfrom/backend/pom.xml -q -Dtest=MarketplaceManifestServiceTest,DeploymentMarketplaceDraftCompilerServiceTest test
mvn -f Platfrom/backend/pom.xml -q -Dtest=PlatformManagedProductProvisioningServiceTest,CoolifyDeploymentProviderTest test
mvn -f product-services/mcp-execution-gateway-service/pom.xml -q test
mvn -f product-services/shopify-bridge-service/pom.xml -q -Dtest=ShopifyBridgeActionExecutionServiceTest test
mvn -f ai-infrastructure-module/pom.xml -pl ai-infrastructure-actions-connector -am -q -Dtest=ActionConnectorExecutorTest -Dsurefire.failIfNoSpecifiedTests=false test
bash -n scripts/verify-marketplace-install-flow.sh
bash -n scripts/verify-shopify-companion.sh
git diff --check
```

Before declaring completion, run:

```bash
mvn -f Platfrom/backend/pom.xml -q test
mvn -f product-services/shopify-bridge-service/pom.xml -q test
mvn -f product-services/mcp-execution-gateway-service/pom.xml -q test
```

---

## 9. Staging Live Verification

Minimum staging checks:

```bash
curl -fsS https://loomai-platform-backend.46.224.145.148.sslip.io/actuator/health
curl -fsS https://loomai-runtime.46.224.145.148.sslip.io/actuator/health
curl -fsS https://shopify-bridge-staging.46.224.145.148.sslip.io/actuator/health
curl -fsS https://mcp-execution-gateway.46.224.145.148.sslip.io/actuator/health
```

Verify gateway admin auth:

- `/api/admin/overview` without `X-MCP-GATEWAY-API-KEY` must return `401`
- `/api/admin/overview` with the configured internal key must return service identity and capabilities

Verify Marketplace discovery:

- call Platform Marketplace MCP discovery for a safe test MCP server
- confirm the response includes normalized tools and schema hashes
- confirm discovery creates or previews draft action metadata only, not live runtime actions

Verify generic execution:

- execute a hostless read-only `adapterType=mcp-tool` action directly through the gateway
- confirm `success=true`
- confirm `data.adapterType=mcp-tool`
- confirm `data.evidenceType=MCP_TOOL_RESULT`

Verify Shopify execution:

- Bridge readiness must show Shopify Storefront MCP ready
- execute `shopify_search_catalog` through Bridge
- confirm the response is normalized MCP evidence from the gateway path

---

## 10. Current External Gates

Customer Account MCP and Checkout MCP are implemented as gated Marketplace plugin bundles. Do not claim full live tool execution until the external Shopify material exists.

Customer Account MCP requires:

- Shopify Customer Account OAuth/PKCE configuration
- customer token/session binding
- protected customer data posture
- allowed customer scopes

Checkout MCP requires:

- Checkout MCP client credentials
- `/api/ucp/mcp` reachable without storefront-password redirects on the staging shop/domain
- Shopify agentic checkout readiness
- terminal-operation policy approval for any terminal checkout actions

These are external readiness gates, not reasons to add direct GraphQL action implementations back into Bridge.

### Credential Intake Behavior

Customer Account MCP is prepared as a fail-closed path:

- Before OAuth/PKCE and protected customer data posture are configured, Bridge returns `CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED`.
- After posture is configured, Bridge returns `CUSTOMER_ACCOUNT_AUTH_REQUIRED` until a customer OAuth access token is bound to the shopper session.
- When a customer token is bound, MCP Gateway supports `CUSTOMER_OAUTH_PKCE` by forwarding it as the MCP Authorization header.
- For stores that use a connected storefront/custom domain for Customer Account OAuth discovery, configure the per-store Platform setting `customerAccountMcp.storefrontDomain` through `PUT /api/shopify/stores/{shopDomain}/customer-account-config` or the Shopify Stores admin page. Bridge resolves this per-store value before using any global fallback.
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN` remains a staging/default fallback only. It must not be treated as product truth for multiple store installs.
- Bridge still stores and resolves customer sessions by the canonical `*.myshopify.com` shop even when discovery and safe return URLs use a configured storefront domain.

Checkout MCP is prepared as a managed-gateway path:

- Add the Shopify Dev Dashboard Catalog credentials as Platform secrets named `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_ID` and `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_SECRET`.
- Reconcile/recreate the managed MCP Gateway and Shopify Bridge product services.
- Platform provisioning writes the credentials only to the MCP Gateway as `MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_ID` and `MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_SECRET`, and enables gateway environment secret resolution for the `MCP_SECRET_` prefix.
- Bridge receives `SHOPIFY_BRIDGE_CHECKOUT_MCP_ENABLED=true` only when both checkout credentials exist.
- Checkout UCP execution uses direct JSON-RPC `tools/call` through the MCP Gateway. Shopify's tutorial examples do not require an MCP `initialize` round trip for Checkout MCP, and live staging returned UCP profile errors for `initialize` / `tools/list` without a tool argument profile.
- Bridge forwards a server-derived buyer IP in action trace, and the MCP Gateway maps it to Shopify's required `Shopify-Buyer-IP` header for `SHOPIFY_AGENTIC_CLIENT_CREDENTIALS`.
- Terminal checkout actions require the separate `SHOPIFY_BRIDGE_CHECKOUT_MCP_TERMINAL_OPERATIONS_ENABLED=true` flag and must stay disabled for normal staging verification.
- If Checkout MCP `tools/list` or `tools/call` fails with a redirect to `/password`, the Shopify online store password is still enabled. Unlock the staging storefront in Shopify Admin before claiming live Checkout MCP evidence.
- A storefront password can unlock a local browser/curl session for direct diagnosis, but it is not a production server-to-server credential and must not be built into Bridge or MCP Gateway as a bypass.

### Order Self-Service Policy

Do not use shopper query text to allow or block refunds, cancellations, returns, or order edits.

Bridge policy is structured:

- Storefront chat lets runtime select an action, then checks the selected action ID and page context.
- Unapproved stores deny structured order mutation action IDs with customer-safe support guidance.
- Approved stores with `order-self-service` may pass configured order self-service action IDs to the Marketplace/MCP execution path.
- Currently configured concrete order self-service action is `shopify_cancel_checkout` through Checkout MCP.
- The live Customer Account MCP Marketplace bundle currently exposes only read-only order-status actions: `shopify_get_most_recent_order_status` and `shopify_get_order_status`.
- Post-order refund/cancel/edit/return-start actions need a real discovered Shopify MCP tool plus a reviewed Marketplace action plugin before live execution; do not add direct GraphQL behavior in Bridge.
- Bridge must not hard-block future post-order action IDs once runtime selected them from the compiled Marketplace catalog. Package entitlement, confirmation, audit, MCP session auth, and the action catalog remain the gates.
