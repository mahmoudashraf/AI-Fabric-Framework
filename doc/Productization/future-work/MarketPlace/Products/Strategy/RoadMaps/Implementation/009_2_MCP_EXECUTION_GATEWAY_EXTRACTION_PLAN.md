# 009.2 MCP Execution Gateway Extraction Plan

Status: implemented, pushed, deployed as a standalone managed MCP Execution Gateway service, and live-verified on Coolify staging on 2026-05-05. Production was not deployed.

Parent plans:

- [009 Shopify MCP-First Implementation Sequence](009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md)
- [009.1 Marketplace Config-Driven MCP Capability Architecture](009_1_MARKETPLACE_CONFIG_DRIVEN_MCP_CAPABILITY_ARCHITECTURE.md)
- [007 Coolify Deployment Provider And Restartable Services](007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md)

Roadmap phase: `009.2` - extract the generic MCP execution code out of Shopify Bridge so non-Shopify MCP servers can execute through Marketplace configuration without depending on the Shopify product service.

Priority: P0 after 009.1 config-driven Storefront MCP is live-verified on staging. This plan should not replace Shopify MCP product proof; it turns the verified generic path into a reusable platform capability.

Source strategy drafts:

- [../MCP/Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md](../MCP/Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md)
- [../MCP/Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md](../MCP/Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md)

Current implementation:

- `product-services/mcp-execution-gateway-service`
- `product-services/mcp-execution-gateway-service/src/main/java/com/ai/fabric/product/mcp/gateway/client/McpStreamableHttpClient.java`
- `product-services/mcp-execution-gateway-service/src/main/java/com/ai/fabric/product/mcp/gateway/service/McpGatewayExecutionService.java`
- `product-services/shopify-bridge-service/src/main/java/com/ai/fabric/product/shopify/bridge/mcp/execution/McpActionExecutionGateway.java`
- Runtime action forwarding in `ai-infrastructure-module/ai-infrastructure-actions-connector`
- Marketplace validation/compiler support in `Platfrom/backend`
- Marketplace discovery/import support in `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/marketplace/service/MarketplaceMcpDiscoveryService.java`
- Platform-managed product service lifecycle support in `Platfrom/backend/src/main/java/com/ai/fabric/platform/backend/productservice/**`
- Product Services operator UI in `Platfrom/ui/src/pages/ProductServicesPage.tsx`

Reference protocol docs checked:

- `https://modelcontextprotocol.io/specification/2025-11-25`
- `https://modelcontextprotocol.io/specification/2025-11-25/basic/transports`
- `https://modelcontextprotocol.io/specification/2025-11-25/server/tools`
- `https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization`

---

## Purpose

009.1 proved the important architecture rule:

```text
Marketplace ACTION config
  -> runtime trace/actionConfig
  -> generic MCP gateway code
  -> MCP tools/call
  -> normalized action evidence
```

But the generic gateway currently lives inside Shopify Bridge. That is acceptable for the first Shopify vertical slice, but it creates the wrong long-term product boundary:

- CRM MCP should not depend on Shopify Bridge.
- GitHub MCP should not depend on Shopify Bridge.
- Internal operational MCP should not depend on Shopify Bridge.
- Marketplace import/drift checks should not depend on Shopify Bridge.

009.2 extracts the generic MCP client/executor into a reusable **MCP Execution Gateway**.

The target is:

```text
Marketplace ACTION plugin
  -> compiled action/server binding
  -> MCP Execution Gateway
  -> external MCP server
  -> normalized governed evidence
```

The generic runtime/connector target is:

```text
Runtime action catalog
  -> ActionConnectorExecutor / connector runtime
  -> adapterType=mcp-tool direct route
  -> MCP Execution Gateway service
  -> external MCP server
```

Shopify Bridge remains Shopify-specific. It can call the MCP Execution Gateway for Shopify actions, but it should not become the universal execution service for every MCP provider.

---

## Core Decision

Create a generic `MCP Execution Gateway`, not a "Generic Bridge".

Bridge means product-boundary mediation. Shopify Bridge mediates Shopify install state, store binding, billing, customer sessions, theme/app context, and Shopify-specific risk posture.

MCP Execution Gateway means protocol-boundary execution. It mediates MCP transport, server binding, auth profile resolution, generic policy checks, result normalization, drift evidence, and audit envelopes.

Those are different responsibilities.

## Current Implementation Status

Implemented in the repository:

- Standalone Spring Boot gateway service with internal API-key protection for `/api/internal/**` and `/api/admin/**`.
- Streamable HTTP MCP client for `initialize`, initialized notification, `tools/list`, and `tools/call`.
- JSON and SSE response handling, `MCP-Protocol-Version`, and `MCP-Session-Id` capture/reuse.
- Gateway execution for Marketplace `adapterType=mcp-tool` actions with structured argument templates and restricted response mapping.
- Discovery/import endpoint that Platform uses to create private Marketplace `ACTION` plugin drafts.
- Server verification endpoint that compares expected tool schema hashes against live `tools/list` evidence.
- Execution-time schema drift guard that blocks `tools/call` for blocking drift policies.
- Auth modes: `NONE`, bearer token secret ref/static bearer, API-key header secret with configured allowlist, and OAuth2 client credentials.
- Reviewed `endpointKind` and `profileRef` binding for Shopify Storefront/UCP, Customer Account, and Checkout MCP without action-specific endpoint code.
- Runtime/connector direct path to the gateway for hostless MCP actions.
- Shopify Bridge customer-facing action execution now depends on plugin MCP config and the gateway; legacy Bridge-owned customer action bodies are removed.
- Product Services support for creating, reconciling, restarting, force-recreating, decommissioning, checking health, and reading provider logs/history for the MCP gateway on Coolify-managed target profiles.
- Product Services UI preset for `MCP_EXECUTION_GATEWAY_SERVICE`.
- Coolify git source normalization for managed product services.
- Product Services force-recreate hardening for stale Coolify app bindings: Platform can adopt/delete the stale domain/name match, clear linkage, and recreate the gateway from desired Platform state.
- Coolify production target profile support for explicit Platform-managed product-service placement while keeping staging as the implicit/default managed-service target.

Staging live verification completed:

- Platform backend, Runtime, Shopify Bridge, and MCP Execution Gateway health checks returned `UP`.
- MCP Gateway `/api/admin/overview` returned `401` without the internal admin key and `200` with the configured key.
- Product Services force-recreated the MCP Gateway through Coolify staging and reconciled it back to `ACTIVE`/`READY` with a new Coolify app UUID.
- Platform Product Services health probed the recreated gateway successfully through `/actuator/health`.
- Platform Marketplace MCP discovery returned normalized `tools/list` evidence for a non-Shopify MCP server through the gateway.
- A generic non-Shopify `mcp-tool` action executed directly through the gateway without Shopify Bridge.
- Shopify Bridge readiness verified the Shopify Storefront MCP server and expected Storefront tools.
- `shopify_search_catalog` executed through Shopify Bridge, plugin MCP config, and the standalone gateway with normalized `MCP_TOOL_RESULT` evidence.

---

## Target Boundary

### Shopify Bridge Keeps

- Shopify app install and OAuth state.
- Shopify store/deployment binding.
- Shopify billing and package posture.
- Shopify-specific storefront readiness.
- Shopify Customer Accounts OAuth/PKCE and customer-token/session binding.
- Checkout terminal-operation policy and Shopify-specific risk denials.
- Shopify merchant/admin UI routes.
- Shopify webhooks and sync infrastructure.

Shopify Bridge does not keep customer-facing action execution implementations as a permanent responsibility. After extraction, Bridge performs Shopify host checks and calls the generic MCP Execution Gateway with the installed Marketplace plugin action config.

### MCP Execution Gateway Owns

- MCP Streamable HTTP client.
- MCP session lifecycle.
- `initialize`, `tools/list`, and `tools/call`.
- Server binding resolution from platform metadata or signed execution envelopes.
- Generic auth provider execution.
- Argument template rendering.
- Tool schema hash comparison.
- Response mapping and normalized evidence.
- Generic audit envelope creation.
- Rate-limit/idempotency hooks.
- Generic deny/error shape.

### Platform Backend Remains Source Of Truth

Platform owns:

- Marketplace plugin review and publication.
- Deployment install config.
- Secret refs.
- Compiled runtime action catalog.
- Package/tier selection.
- Release-time verification and drift policy.

The gateway must not become a second Marketplace.

---

## Deployment Shape

009.2 was delivered directly as a standalone service plus thin product-host/runtime clients. A shared Java library may still be extracted later for code reuse, but the required product boundary is now the deployable gateway service.

### Step A: Shared Java Module

Optional follow-up for code reuse:

```text
ai-infrastructure-module/ai-infrastructure-mcp-execution
```

This module should contain:

- `McpStreamableHttpClient`
- `McpActionExecutionService`
- `McpServerBindingResolver`
- `McpAuthProviderRegistry`
- `McpArgumentTemplateRenderer`
- `McpResultNormalizer`
- `McpExecutionAuditEnvelope`
- `McpSchemaHashService`

Shopify Bridge keeps its current public action routes and now calls the standalone gateway over an internal authenticated API. A shared module can reduce duplication later, but it is not required for the 009.2 product boundary.

### Step B: Standalone Product Service

Then add a deployable service:

```text
product-services/mcp-execution-gateway-service
```

This service exposes internal server-to-server APIs for Platform, runtimes, connectors, and product Bridges.

It must not expose arbitrary browser-accessible tool execution.

It must be deployable without Shopify Bridge. The runtime/connector stack must be able to call it directly for generic `adapterType=mcp-tool` actions whose compiled governance does not require a product host.

Initial endpoints:

```text
POST /api/internal/mcp/actions/execute
POST /api/internal/mcp/servers/verify
POST /api/internal/mcp/servers/tools/list
POST /api/internal/mcp/tools/call
POST /api/internal/mcp/import/discover
GET  /api/admin/overview
GET  /actuator/health
```

The standalone service allows non-Shopify MCP actions to execute without Shopify Bridge.

---

## Platform-Managed Service Requirement

The standalone gateway is a first-class Platform-managed reproducible service, not an ad hoc Coolify app.

It must be managed through the same Product Services control-plane surface used by Shopify Bridge today:

- create/register service
- reconcile desired provider state
- inspect provider deployments/history
- health probe
- logs
- restart
- scale desired replicas
- rotate internal service secret
- force recreate provider linkage
- decommission

The initial managed service identity should use these product-service fields:

```text
serviceRef: mcp-execution-gateway
displayName: MCP Execution Gateway
productFamily: MCP
serviceKind: MCP_EXECUTION_GATEWAY_SERVICE
deploymentMode: SHARED_PLATFORM_SERVICE
tenantMode: MULTI_TENANT_SHARED
environmentScope: staging
healthPath: /actuator/health
serviceRoot: product-services/mcp-execution-gateway-service
dockerfilePath: product-services/mcp-execution-gateway-service/deploy/container/Dockerfile
```

Use the provider-neutral `deploy/container/Dockerfile` path for new Platform-managed product-service records. `deploy/railway/Dockerfile` is a compatibility path only for existing Railway projects that still reference it.

Platform remains the source of truth for the service definition. Coolify is the provider runtime. Operators must not have to manually recreate the gateway from the Coolify UI for normal lifecycle work.

Force recreate semantics must match the existing Product Services page behavior for Bridge:

- require typing the `serviceRef` before clearing linkage
- clear provider linkage and base URL fields in Platform
- preserve the desired service spec, secret references, scale settings, health path, root directory, Dockerfile path, branch, and target profile
- recreate the provider app on the next reconcile/create operation
- write an auditable operator event
- fail closed if required target profile or provider credential material is missing

Reconciliation must be idempotent. Re-running reconcile on the same desired spec should not create duplicate Coolify applications.

Production profile semantics:

- `dtp-coolify-staging` remains the implicit Product Services target because it is the active restartable-services default.
- `dtp-coolify-production` is allowed for Platform-managed product services only when a service record explicitly sets `targetProfileId=dtp-coolify-production`.
- If multiple Coolify profiles allow platform services and no restartable-services default exists, reconcile fails closed and requires an explicit `targetProfileId`.
- Production services should use separate service refs and secret names such as `mcp-execution-gateway-production` and `MANAGED_PRODUCT_MCP_EXECUTION_GATEWAY_PRODUCTION_API_KEY`.

The Product Services UI should either:

- support arbitrary managed service kinds cleanly, including `MCP_EXECUTION_GATEWAY_SERVICE`, or
- add an explicit MCP Gateway preset with the fields above.

Do not put this only in deployment-provider diagnostics. Diagnostics can inspect provider resources, but day-to-day lifecycle ownership belongs in Product Services.

---

## Generic Runtime And Connector Use

The standalone gateway is the default execution target for generic MCP actions.

Connector/runtime behavior:

- If `adapterType=mcp-tool` and compiled policy says `hostRequired=false`, call the standalone gateway directly.
- If the action has a `hostServiceRef`, call that product host first. Shopify actions continue to use Shopify Bridge when store/session/billing/customer/checkout posture is required.
- Runtime catalogs remain deterministic and secret-free.
- Runtime/connector calls to the gateway use internal service authentication, deployment id, action id, params, idempotency key, and caller context.
- Runtime/connector must not send raw provider secrets, browser tokens, or arbitrary unreviewed tool configs.

The gateway response must preserve the normalized action result contract already used by connector actions:

```text
success
message
data.adapterType=mcp-tool
data.evidenceType=MCP_TOOL_RESULT
data.mcpServerRef
data.mcpToolName
data.normalizedEvidence
errorCode
```

This is what lets the existing runtime/connector pipeline use MCP actions without Shopify Bridge.

---

## Execution API Contract

### Execute Request

The gateway should accept a signed or server-authenticated request:

```json
{
  "deploymentId": "dep-123",
  "tenantId": "ten-123",
  "consumerId": "consumer-123",
  "actionId": "crm_search_contacts",
  "params": {
    "query": "alex"
  },
  "idempotencyKey": "req-123",
  "caller": {
    "type": "RUNTIME",
    "serviceRef": "runtime-dep-123"
  },
  "session": {
    "shopperSessionId": "optional",
    "customerSessionRef": "optional"
  },
  "actionConfig": {
    "adapterType": "mcp-tool",
    "execution": {
      "mcp": {
        "serverRef": "example-crm",
        "toolName": "search_contacts",
        "argumentTemplate": {
          "query": "{{params.query}}"
        },
        "responseMapping": {
          "resultPath": "$"
        }
      }
    },
    "mcpServers": {
      "example-crm": {
        "endpointUrl": "https://crm.example.com/mcp",
        "auth": {
          "mode": "API_KEY_HEADER_SECRET",
          "headerName": "X-MCP-API-KEY",
          "secretRef": "EXAMPLE_CRM_API_KEY"
        }
      }
    }
  },
  "governance": {
    "readOnly": true,
    "requiresConfirmation": false,
    "riskClass": "READ_ONLY_EXTERNAL"
  }
}
```

### Execute Response

```json
{
  "success": true,
  "message": "MCP tool result",
  "data": {
    "adapterType": "mcp-tool",
    "evidenceType": "MCP_TOOL_RESULT",
    "mcpServerRef": "example-crm",
    "mcpToolName": "search_contacts",
    "toolResult": {},
    "normalizedEvidence": {}
  },
  "errorCode": null
}
```

### Security Rule

The gateway may accept `actionConfig` in the request only when the request is trusted:

- signed by Platform, or
- sent by an authenticated internal runtime/Bridge with a deployment-scoped service credential, or
- revalidated by fetching the compiled deployment config from Platform.

Long-term preference: request contains `deploymentId` and `actionId`; gateway fetches or caches the compiled config from Platform. Inline `actionConfig` is acceptable for migration and tests, but it must not become the only trust model.

---

## Server Binding And Secret Resolution

The gateway resolves endpoint and auth from one of these sources, in order:

1. Platform-fetched compiled deployment config.
2. Signed execution envelope generated by Platform.
3. Trusted product host envelope, such as Shopify Bridge, that has already resolved product-specific context.

Secret values must never be stored in Marketplace manifests or runtime action catalogs.

Secret value resolution options:

- Platform secret-read API with gateway service identity.
- Deployment-scoped secret provider mounted into the gateway.
- Product host delegation for provider-specific tokens.

Do not pass raw secret values through browser clients, runtime LLM traces, or Marketplace manifests.

---

## Auth Modes

009.2 must preserve the 009.1 auth posture and make it reusable.

Config-only in the standalone gateway release:

- `NONE`
- `STATIC_BEARER_SECRET`
- `BEARER_TOKEN_SECRET_REF`
- `API_KEY_HEADER_SECRET`
- `OAUTH2_CLIENT_CREDENTIALS`

Still product/host-assisted:

- `OAUTH2_AUTH_CODE_PKCE`
- `CUSTOMER_OAUTH_PKCE`
- provider-specific signed session delegation

Reason: generic token storage and refresh can be shared, but login UX, customer identity binding, protected-data approval, and product-specific session policy cannot be assumed from a Marketplace manifest alone.

Header allowlist rules from 009.1 remain mandatory.

---

## Transport Compliance

Initial supported transport:

- `STREAMABLE_HTTP`

The gateway must support:

- JSON-RPC 2.0 request/response bodies.
- HTTP `POST` to the MCP endpoint.
- `Accept: application/json, text/event-stream`.
- JSON and SSE response bodies.
- `MCP-Protocol-Version`.
- `MCP-Session-Id` capture and reuse when servers assign a session.
- Session restart after `404` on a session-bound request.
- Bounded SSE response consumption and request timeouts.

Deferred:

- Stream listening by HTTP `GET`.
- Resumability with `Last-Event-ID`.
- Legacy HTTP+SSE compatibility.
- stdio for trusted local/internal servers only after sandbox design.

---

## Governance And Policy

The gateway enforces generic policy from compiled action metadata:

- action exists and is enabled
- declared toolName matches allowed server tools
- read/write classification
- confirmation receipt when required
- risk class
- anonymous/session/customer requirements
- rate limits
- idempotency
- schema drift policy
- redaction policy

The gateway cannot infer product-specific policy by itself.

Product hosts may add pre-execution policy:

```text
Shopify Bridge
  -> validate Shopify store/session/billing/customer posture
  -> call MCP Execution Gateway
```

For simple non-Shopify read-only MCP actions:

```text
Runtime or Platform
  -> call MCP Execution Gateway directly
```

For non-Shopify governed writes:

```text
Runtime
  -> Platform confirmation/governance receipt
  -> MCP Execution Gateway
```

If a product requires domain-specific session binding or legal/payment state, add a product host or policy hook. Do not force those semantics into the generic gateway.

---

## Marketplace Import Relationship

009.2 does not replace 009.1 import/review.

Import still belongs to Platform:

```text
Platform operator/admin
  -> request MCP discovery from MCP Execution Gateway
  -> create Marketplace ACTION draft
  -> review risk/auth/tier/mapping
  -> publish
  -> install
  -> compile
```

The gateway owns the technical protocol work for discovery:

- resolve reviewed endpoint/auth profile
- call `initialize`
- call `tools/list`
- normalize tool names, descriptions, schemas, and protocol evidence
- compute canonical schema hashes
- return redacted discovery evidence to Platform

Platform owns the product lifecycle:

- draft creation
- action ID mapping
- risk/session/tier classification
- review and publication
- install and release
- runtime exposure decisions

`tools/list` remains discovery and drift evidence only. It must not auto-expose tools at runtime.

---

## Migration Plan

### Phase 0: Current-State Lock

Record current live behavior:

- Shopify Bridge `shopify_search_catalog` live success.
- Synthetic config-driven MCP action live success.
- Direct Shopify MCP `initialize`, `tools/list`, and `tools/call` live success.

Gate:

- response captures exist under `/tmp/loomai-009-shopify-mcp-first/` or a new 009.2 evidence folder
- current tests pass before extraction

### Phase 1: Extract Shared Module

Move generic code from Shopify Bridge into a shared module.

Keep Shopify-specific behavior out of the shared module:

- no Shopify install entities
- no Shopify billing concepts
- no Shopify store repository
- no Shopify Customer Accounts assumptions

Gate:

- Shopify Bridge tests pass after importing the shared module
- generic gateway unit tests move with the module
- no behavior change in staging

### Phase 2: Convert Shopify Bridge To Host Adapter

Shopify Bridge should call the shared executor for config-driven MCP actions.

Keep existing Shopify action routes stable:

- `/api/admin/stores/{shopDomain}/actions/execute`
- existing storefront/governed action routes

Remove customer-facing legacy execution from Bridge as the generic execution path becomes available:

- no `list_products`, `search_products`, `get_policy`, `check_availability`, `add_product_to_cart`, `add_to_cart`, or `update_cart_quantity` Bridge-owned behavior
- no direct Admin GraphQL customer-facing action bodies
- no Bridge-local action switch as product truth
- installed Marketplace plugin config decides the action surface

Bridge may retain Shopify host-policy code for store/session/billing/customer/checkout checks, then delegate to the shared executor.

Gate:

- `shopify_search_catalog` still live-verifies on staging
- synthetic config-driven action still live-verifies on staging
- legacy aliases return `ACTION_NOT_SUPPORTED` unless explicitly approved as a temporary migration shim
- customer-facing Shopify MCP actions resolve from installed Marketplace plugin config

### Phase 3: Add Standalone Gateway Service

Create `product-services/mcp-execution-gateway-service`.

Service responsibilities:

- expose internal execution API
- authenticate Platform/runtime/Bridge callers
- resolve compiled config from Platform or signed envelope
- resolve secrets
- execute MCP
- emit normalized evidence

Platform-managed service responsibilities:

- add an MCP Gateway Product Services preset or generic service-kind support
- create/reconcile the service through the existing Product Services API
- deploy to Coolify staging through the provider-neutral target profile
- expose the same Product Services UI operations as Shopify Bridge: health, logs, deployment history, reconcile, restart, scale, rotate secret, force recreate, and decommission

Gate:

- service health works locally and on Coolify staging
- service appears in Product Services UI as a managed service
- force recreate clears linkage in Platform and reconcile recreates the Coolify app
- one non-Shopify mock MCP action executes through the standalone service
- no Shopify Bridge dependency exists in the standalone service

### Phase 4: Runtime Direct Execution Path

Add an execution route for `adapterType=mcp-tool` that calls the standalone gateway directly when the action does not require a product host.

This route belongs in the generic runtime/connector path, not in Shopify Bridge.

Gate:

- a simple read-only third-party MCP action executes without Shopify Bridge
- runtime/connector calls the gateway with internal service auth
- hosted Shopify actions still route through Shopify Bridge when `hostServiceRef` or Shopify posture is required
- runtime action catalog remains deterministic and secret-free
- missing gateway config fails closed

### Phase 5: Platform Verification And Drift

Use the standalone gateway for:

- Marketplace import discovery
- install-time `initialize` and `tools/list`
- release-time drift checks
- operator readiness probes

Gate:

- an operator can discover a supported MCP server through Platform, backed by the gateway discovery endpoint
- discovery creates a private Marketplace `ACTION` draft without making tools live
- removed tool blocks/disables/warns according to policy
- schema drift is detected by canonical hash
- new external tools do not become runtime actions automatically

### Phase 6: Coolify Staging Productization

Add Coolify staging deployment for the gateway:

- environment source of truth in Platform/Coolify
- health endpoint
- internal API key
- Platform base URL
- secret resolution credentials
- logs redaction posture
- Product Services managed record
- Product Services operator UI lifecycle controls
- force recreate, restart, scale, logs, health, and deployment history parity with Shopify Bridge

Gate:

- staging gateway deploys from `Platform-V8`
- Platform health, Bridge health, and gateway health all return `UP`
- Product Services UI shows the gateway as `ACTIVE` after reconcile
- Product Services force recreate has been tested on staging and then reconciled back to `ACTIVE`
- direct non-Shopify MCP execution live-verifies without Shopify Bridge

---

## Acceptance Criteria

009.2 is complete when:

- Generic MCP execution code no longer lives only under Shopify Bridge packages.
- Shopify Bridge consumes the shared MCP executor without owning generic MCP protocol code.
- A standalone MCP Execution Gateway service is deployable on staging.
- The gateway is registered as a Platform-managed product service and is reproducible from Platform state.
- Product Services UI can health-check, inspect logs/history, reconcile, restart, scale, rotate secret, force recreate, and decommission the gateway with parity to Shopify Bridge.
- Force recreate is live-verified on staging and does not require manual Coolify UI repair.
- A config-driven non-Shopify MCP action can execute through the standalone gateway without Shopify Bridge.
- The generic runtime/connector path can call the standalone gateway directly for hostless `adapterType=mcp-tool` actions.
- `shopify_search_catalog` remains live-verified through Shopify Bridge after extraction.
- Shopify Bridge no longer owns legacy customer-facing action implementation; it acts as Shopify host/governance adapter and delegates plugin-defined MCP actions to the generic gateway.
- Marketplace can discover a supported MCP server through the generic gateway and generate a private `ACTION` plugin draft.
- The generic gateway supports `initialize`, `tools/list`, and `tools/call` over Streamable HTTP.
- Runtime action catalogs remain deterministic and secret-free.
- Secret values are resolved server-side only.
- Existing connector HTTP actions and Shopify-specific governed paths still pass tests.
- Production is not deployed until explicitly requested.

---

## Non-Goals

- Do not create a new Marketplace plugin type.
- Do not let `tools/list` become runtime product truth.
- Do not make Shopify Bridge the universal MCP service.
- Do not make the MCP gateway a manually managed Coolify-only app outside Platform Product Services.
- Do not expose the gateway directly to browsers.
- Do not support arbitrary local stdio MCP servers before sandboxing.
- Do not make custom OAuth/customer-session flows config-only when they need product UX or protected-data posture.
- Do not move Shopify install, billing, webhook, or source-sync behavior into the generic gateway.

---

## Verification Commands

Local:

```bash
mvn -f Platfrom/backend/pom.xml -q -Dtest=MarketplaceManifestServiceTest,DeploymentMarketplaceDraftCompilerServiceTest test
mvn -f product-services/shopify-bridge-service/pom.xml -q test
mvn -f ai-infrastructure-module/pom.xml -q -pl ai-infrastructure-actions-connector,ai-infrastructure-actions-registry -am -DfailIfNoTests=false test
bash -n scripts/verify-marketplace-install-flow.sh
bash -n scripts/verify-shopify-companion.sh
git diff --check
```

After standalone service exists:

```bash
mvn -f product-services/mcp-execution-gateway-service/pom.xml -q test
curl -fsS "$MCP_EXECUTION_GATEWAY_BASE_URL/actuator/health"
```

Platform-managed service checks:

```bash
curl -fsS "$PLATFORM_BACKEND_BASE_URL/api/product-services/mcp-execution-gateway"
curl -fsS "$PLATFORM_BACKEND_BASE_URL/api/product-services/mcp-execution-gateway/health"
curl -fsS "$PLATFORM_BACKEND_BASE_URL/api/product-services/mcp-execution-gateway/railway/deployments?limit=5"
```

The deployment-history/log endpoint names are still `railway/*` in the current Product Services API even when the backing provider is Coolify. 009.2 may rename these to provider-neutral names, but it must keep existing UI behavior working during migration.

Staging:

```bash
curl -fsS https://loomai-platform-backend.46.224.145.148.sslip.io/actuator/health
curl -fsS https://loomai-shopify-bridge-staging.46.224.145.148.sslip.io/actuator/health
curl -fsS "$MCP_EXECUTION_GATEWAY_STAGING_BASE_URL/actuator/health"
```

Live product proof:

- Direct MCP `initialize` succeeds.
- Direct MCP `tools/list` succeeds.
- Direct MCP `tools/call` succeeds for a safe read-only tool.
- Bridge `shopify_search_catalog` still succeeds.
- Standalone gateway executes a non-Shopify config-driven MCP action without Shopify Bridge.

---

## Open Decisions

- Whether the standalone gateway reads compiled config directly from Platform or only accepts signed execution envelopes in v1.
- Whether gateway audit writes directly to Platform or emits events for Platform ingestion.
- Whether the first non-Shopify live MCP proof should use a local mock server, a public test MCP server, or a real CRM/helpdesk provider.
