# 009 Shopify MCP-First Tier Action Catalog Alignment

Status: implementation roadmap (created 2026-05-03; rewritten 2026-05-03; MCP-first rewrite 2026-05-04)

Owner mode: architecture/productization LLM session

Roadmap phase: `009` - make Shopify package/tier selection compile the correct MCP-backed Marketplace action catalog

Priority: P0 before expanding Shopify customer actions. The product is greenfield and does not need backward compatibility with the current Bridge hard-coded action executor.

Depends on:

- [006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md](../Implementation/006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md)
- [006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md](../Implementation/006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md)
- [006_3_GOVERNED_LOW_RISK_WRITE_EXECUTION.md](../Implementation/006_3_GOVERNED_LOW_RISK_WRITE_EXECUTION.md)
- [007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md](../Implementation/007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md)
- [008_CONTROLLED_DESIGN_PARTNER_LAUNCH_AND_MARKET_PROOF.md](../Implementation/008_CONTROLLED_DESIGN_PARTNER_LAUNCH_AND_MARKET_PROOF.md)
- [Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md](Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md)

Related guides:

- `Final_Documentation/System_Archtecture_Guides/PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/SHOPIFY_PRODUCTS_SHIPPING_ROADMAP.md`
- `doc/Productization/future-work/MarketPlace/Products/Strategy/PRODUCT_FACTORY_FACTORIZATION_CONSIDERATIONS.md`
- `Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md`
- `Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md`

---

## Rewrite Decision

The previous `009` plan assumed Shopify action behavior should remain code-backed inside Shopify Bridge.

That is no longer the target.

New decision:

> Shopify customer-facing actions should depend on Shopify MCP from day one. Bridge should not own custom Shopify action implementations when Shopify MCP exposes the customer capability.

This is greenfield. We do not need to preserve legacy Bridge action IDs, compatibility aliases, or current hard-coded GraphQL action behavior.

Bridge still matters, but its role changes.

Bridge owns:

- Shopify app install state
- store/deployment binding
- merchant billing and tier state
- customer/session auth handoff
- Customer Accounts OAuth/PKCE handling where needed
- MCP server resolution
- MCP transport and retries
- explicit confirmation
- audit trail
- rate limits
- normalized action result shape
- denial reasons and operator evidence

Bridge does **not** own:

- hard-coded Shopify product search behavior
- hard-coded Shopify policy lookup behavior
- hard-coded Shopify cart mutations
- hard-coded Shopify order/customer reads when Customer Accounts MCP covers them
- raw Shopify GraphQL action implementations as the default path

Raw Shopify API/GraphQL may remain only for:

- app installation, billing, webhook, and source-sync infrastructure
- temporary MCP fallback during migration evidence gathering
- capabilities Shopify MCP does not expose yet, after explicit architecture review

---

## Core Decision

Do not add a new Marketplace plugin type.

Use existing Marketplace plugin types:

```text
ACTION plugin
  -> declares curated customer action
  -> adapterType = mcp-tool
  -> execution.mcp.serverRef
  -> execution.mcp.toolName

DATA plugin
  -> declares indexed/background data sources where still useful

TEMPLATE plugin
  -> declares Shopify companion shell/prompt/runtime posture

INFERENCE_PROFILE plugin
  -> declares model/provider posture
```

Tier and package selection still happen through package profiles:

```text
Shopify billing tier
  -> Shopify package profile
  -> required Marketplace plugin bundle
  -> deployment Marketplace installs
  -> Marketplace draft compiler
  -> runtime action catalog
  -> Bridge governed MCP execution
  -> Shopify MCP tools/call
```

The Marketplace action catalog is the product surface. Shopify MCP is the execution surface.

---

## Clean Ownership

| Concern | Owner |
| --- | --- |
| Runtime action visibility | Marketplace `ACTION` plugins |
| Tier/package to plugin mapping | Shopify package profile |
| Runtime action catalog compilation | Platform Marketplace draft compiler |
| Shopify MCP endpoint resolution | Bridge MCP adapter |
| Shopify MCP `tools/list` verification | Bridge/platform readiness checks |
| Shopify MCP `tools/call` execution | Bridge MCP adapter |
| Customer Accounts OAuth/PKCE | Bridge |
| Tier, confirmation, audit, rate limits | Bridge |
| Product semantics and action names | Marketplace plugin manifest |
| Raw Shopify product/cart/order action logic | Shopify MCP, not Bridge |

---

## Current Code Reality

Current code does not yet match the target:

- `mkp-action-shopify-companion-read` currently routes actions to Bridge `/actions/execute`.
- `ShopifyBridgeActionExecutionService` currently implements Shopify read/cart behavior directly.
- `V72__shopify_companion_guided_commerce_actions.sql` places read and governed cart actions in the same plugin.
- `ShopifyStoreProvisioningService.resolveRequestedPluginIds(...)` currently always adds the same read action plugin.
- `DeploymentMarketplaceDraftCompilerService.applyActionPlugin(...)` compiles action metadata but does not preserve a formal `execution.mcp` contract yet.

`009` now fixes both catalog shape and execution direction:

1. split customer-facing capabilities into tier-specific Marketplace action plugins
2. make those plugins MCP-backed from the start
3. make package profiles resolve the correct MCP-backed plugin bundle
4. keep Bridge as governance/MCP adapter, not as custom Shopify action executor

---

## Target Tier Shape

### Free

```text
FREE package profile
  -> mkp-template-shopify-companion
  -> minimal data/inference posture where needed
  -> optional narrow public catalog discovery plugin
  -> no cart actions
  -> no customer-account actions
  -> no order lookup
```

Free should only expose low-risk anonymous discovery where product strategy wants it.

### Starter

```text
STARTER package profile
  -> mkp-template-shopify-companion
  -> mkp-data-shopify-catalog where indexing remains useful
  -> mkp-data-shopify-policies where indexing remains useful
  -> mkp-action-shopify-storefront-read-mcp
  -> selected inference-profile plugin
```

Starter receives MCP-backed read-only storefront intelligence.

Primary Shopify MCP surfaces:

- `/api/ucp/mcp` for UCP catalog tools
- `/api/mcp` for standard Storefront MCP policy tools

### Elite

```text
ELITE package profile
  -> Starter bundle
  -> mkp-action-shopify-cart-mcp
  -> mkp-action-shopify-customer-account-mcp when auth posture is ready
  -> optional mkp-action-shopify-checkout-mcp when approved
  -> selected higher-quality inference-profile plugin
```

Elite receives cart/customer/order customer-facing tools, gated by Bridge governance.

---

## Shopify MCP Action Plugins

### `mkp-action-shopify-storefront-read-mcp`

Purpose: anonymous/read-only shopping intelligence.

| Platform action | Shopify MCP endpoint | Shopify MCP tool |
| --- | --- | --- |
| `shopify_search_catalog` | `/api/ucp/mcp` | `search_catalog` |
| `shopify_lookup_catalog` | `/api/ucp/mcp` | `lookup_catalog` |
| `shopify_get_product` | `/api/ucp/mcp` | `get_product` |
| `shopify_search_policies` | `/api/mcp` | `search_shop_policies_and_faqs` |

Notes:

- UCP catalog calls must include the configured UCP agent profile.
- Results are treated as untrusted external content and normalized before returning to the LLM/UI.
- This replaces Bridge hard-coded product/policy read actions.

### `mkp-action-shopify-cart-mcp`

Purpose: governed cart actions.

| Platform action | Shopify MCP endpoint | Shopify MCP tool |
| --- | --- | --- |
| `shopify_get_cart` | `/api/mcp` | `get_cart` |
| `shopify_update_cart` | `/api/mcp` | `update_cart` |

Notes:

- Use one canonical cart mutation action for add/update/remove where Shopify MCP's `update_cart` is the underlying tool.
- Do not preserve legacy aliases such as `add_product_to_cart` and `add_to_cart`.
- Requires Elite.
- Requires explicit confirmation for cart mutation.
- Bridge records governed-action audit before and after MCP execution.

### `mkp-action-shopify-customer-account-mcp`

Purpose: authenticated customer account, order, and return support.

Representative platform actions:

- `shopify_get_customer_orders`
- `shopify_lookup_order`
- `shopify_get_order_status`
- `shopify_get_return_eligibility`
- `shopify_start_return_request`

Notes:

- Requires Customer Accounts MCP.
- Requires OAuth 2.0 authorization code with PKCE.
- Requires customer/session binding.
- Requires protected customer data posture before launch.
- Never expose to anonymous shoppers.
- Start as Elite or managed/enterprise only.

Pending external auth material before full staging live verification:

- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_ENABLED=true`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_PROTECTED_DATA_APPROVED=true`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_ID`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_REDIRECT_URI`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SCOPES`
- registered Shopify Customer Account OAuth redirect URI for staging
- staging test customer OAuth/PKCE token acquisition path or permission to complete the PKCE login flow
- protected customer data approval confirmation for the staging app/store

### `mkp-action-shopify-checkout-mcp`

Purpose: checkout creation/continuation where Shopify approval and product risk posture allow it.

Notes:

- Defer until cart MCP and customer-account MCP are stable.
- Start with checkout handoff/continuation URL only.
- Do not autonomously complete payment in the first implementation.

Pending external auth material before full staging live verification:

- `SHOPIFY_BRIDGE_CHECKOUT_MCP_ENABLED=true`
- `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_ID`
- `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_SECRET`
- `SHOPIFY_BRIDGE_CHECKOUT_MCP_TOKEN_URL`
- optional `SHOPIFY_BRIDGE_CHECKOUT_MCP_TERMINAL_OPERATIONS_ENABLED=true` only for explicitly approved terminal checkout tests
- confirmation whether staging checkout verification is limited to create/get/update or may include `complete_checkout` / `cancel_checkout`

---

## MCP Execution Contract

Marketplace action manifests may declare:

```json
{
  "actionId": "shopify_search_catalog",
  "displayName": "Search Shopify catalog",
  "readOnly": true,
  "anonymousAllowed": true,
  "groundingEligible": true,
  "readActionResolutionEligible": true,
  "adapterType": "mcp-tool",
  "capabilityRef": "shopify.storefront.catalog.search",
  "params": [
    {
      "name": "query",
      "type": "STRING",
      "required": true,
      "description": "Shopper search query"
    }
  ],
  "execution": {
    "adapterType": "mcp-tool",
    "mcp": {
      "serverRef": "shopify-storefront-ucp",
      "endpointKind": "UCP_CATALOG",
      "toolName": "search_catalog",
      "argumentTemplate": {
        "meta": {
          "ucp-agent": {
            "profileRef": "SHOPIFY_UCP_AGENT_PROFILE"
          }
        },
        "catalog": {
          "query": "{{params.query}}"
        }
      }
    }
  }
}
```

Rules:

- `actionId` is the platform product action.
- `toolName` is the Shopify MCP tool.
- `serverRef` resolves endpoint/auth/session configuration.
- `tools/list` verifies the declared tool exists.
- `tools/call` executes the declared tool.
- newly discovered MCP tools never auto-appear in runtime.

---

## Effective Permission Rule

A Shopify customer action is effectively available only when all checks pass:

```text
effectiveAction =
  package profile includes the MCP-backed ACTION plugin
  AND deployment has the plugin installed and enabled
  AND Marketplace compiler published it into the active runtime action catalog
  AND Bridge tier/billing policy allows it
  AND Bridge customer/session auth policy allows it
  AND Shopify MCP server exposes the declared tool
  AND required Shopify MCP auth/scopes/profile are available
  AND confirmation/audit policy passes
  AND deployment is bound to the store
```

The runtime catalog is a visibility and planning surface.

Bridge remains the governance and MCP execution authority.

Shopify MCP remains the Shopify customer-action implementation authority.

---

## Data Model Direction

### Short-Term

Store package plugin-bundle overrides in `shopify_companion_package_profiles.detailsJson`.

Starter:

```json
{
  "requiredPluginIds": [
    "mkp-template-shopify-companion",
    "mkp-action-shopify-storefront-read-mcp",
    "mkp-data-shopify-catalog",
    "mkp-data-shopify-policies",
    "mkp-inference-shared-embeddings"
  ],
  "disabledPluginIds": [
    "mkp-action-shopify-cart-mcp",
    "mkp-action-shopify-customer-account-mcp",
    "mkp-action-shopify-checkout-mcp"
  ]
}
```

Elite:

```json
{
  "requiredPluginIds": [
    "mkp-template-shopify-companion",
    "mkp-action-shopify-storefront-read-mcp",
    "mkp-action-shopify-cart-mcp",
    "mkp-action-shopify-customer-account-mcp",
    "mkp-data-shopify-catalog",
    "mkp-data-shopify-policies",
    "mkp-inference-premium-hybrid"
  ],
  "disabledPluginIds": [
    "mkp-action-shopify-checkout-mcp"
  ]
}
```

### MCP Server Config

Store MCP server refs in deployment/provider config or install config:

```json
{
  "mcpServers": [
    {
      "id": "shopify-storefront",
      "transport": "STREAMABLE_HTTP",
      "urlTemplate": "https://{{shopDomain}}/api/mcp",
      "authMode": "NONE"
    },
    {
      "id": "shopify-storefront-ucp",
      "transport": "STREAMABLE_HTTP",
      "urlTemplate": "https://{{shopDomain}}/api/ucp/mcp",
      "authMode": "NONE",
      "agentProfileSecretRef": "SHOPIFY_UCP_AGENT_PROFILE"
    },
    {
      "id": "shopify-customer-account",
      "transport": "STREAMABLE_HTTP",
      "discoveryUrlTemplate": "https://{{shopDomain}}/.well-known/customer-account-api",
      "authMode": "CUSTOMER_OAUTH_PKCE",
      "requiredCustomerScopes": [
        "customer-account-mcp-api:full"
      ]
    }
  ]
}
```

### Later

If package/profile bundle mapping becomes central and frequently edited, move plugin bundle mapping into first-class tables:

- `shopify_companion_package_profile_plugins`
- `profileKey`
- `pluginId`
- `pluginVersion`
- `required`
- `enabledByDefault`
- `reason`
- `status`

If MCP server refs become heavily reused, introduce a shared MCP connection/profile object. Do not add a top-level MCP Marketplace capability type before reuse pain is proven.

---

## Required Implementation Slices

### Slice 1 - MCP-First Catalog Inventory

Goal: prove the current custom Bridge action catalog and identify what must be removed/replaced.

Tasks:

- inspect current Shopify Marketplace `ACTION` plugin manifests
- list current route-backed Bridge actions
- list Shopify MCP tools available for target stores through `/api/mcp` and `/api/ucp/mcp`
- classify each current action as:
  - replace with Shopify MCP
  - remove
  - keep only as infrastructure/non-customer path
- document legacy action IDs that can be dropped because greenfield has no compatibility requirement

Output:

- `/tmp/loomai-009-shopify-mcp-first/current-action-replacement-inventory.md`

Acceptance:

- every current customer-facing Bridge action has an MCP-first disposition
- no implementation starts before the removal/replacement list is clear

### Slice 2 - MCP Execution Contract In Marketplace

Goal: make `ACTION` plugins capable of declaring MCP-backed execution.

Tasks:

- add manifest validation for `adapterType = mcp-tool`
- allow action contributions to declare `execution.mcp.serverRef`
- allow action contributions to declare `execution.mcp.toolName`
- allow action contributions to declare `execution.mcp.endpointKind`
- allow action contributions to declare `execution.mcp.argumentTemplate`
- compile execution metadata into `actionsConfig.actions[]`
- keep `ACTION` as the plugin type

Acceptance:

- an MCP-backed action plugin compiles without route-backed Bridge execution
- non-MCP actions still compile unchanged where unrelated to Shopify
- no new Marketplace plugin type is required

### Slice 3 - Bridge MCP Adapter

Goal: make Bridge execute compiled MCP-backed Shopify actions without owning Shopify action logic.

Tasks:

- implement Shopify MCP server resolver
- implement Streamable HTTP JSON-RPC transport
- implement `initialize`
- implement `tools/list`
- implement `tools/call`
- implement UCP agent profile injection
- normalize MCP tool responses into platform action result shape
- normalize MCP errors into operator-readable error codes
- preserve tier, confirmation, audit, and rate-limit checks before `tools/call`

Acceptance:

- Bridge can execute `shopify_search_catalog` through Shopify MCP
- Bridge can execute `shopify_get_cart` / `shopify_update_cart` through Shopify MCP
- Bridge does not call custom Shopify product/cart GraphQL for customer-facing action execution

### Slice 4 - Shopify MCP Action Plugins

Goal: replace old Shopify action plugins with MCP-backed plugins.

Tasks:

- create `mkp-action-shopify-storefront-read-mcp`
- create `mkp-action-shopify-cart-mcp`
- create `mkp-action-shopify-customer-account-mcp` after OAuth design is ready
- remove governed cart actions from any read plugin
- remove route-backed `/actions/execute` Shopify customer-action manifests
- drop legacy aliases instead of preserving them

Acceptance:

- read plugin contains only MCP-backed read-safe storefront actions
- cart plugin contains only MCP-backed governed cart actions
- customer-account plugin contains only authenticated customer actions
- no Shopify customer action plugin depends on Bridge custom GraphQL execution

### Slice 5 - Package Profile Plugin Bundle Resolution

Goal: make tier/package profile choose the MCP-backed Marketplace plugin bundle.

Tasks:

- read `requiredPluginIds` and `disabledPluginIds` from package profile `detailsJson`
- always include the selected inference-profile plugin from the resolved profile
- keep catalog/policy data plugin inclusion compatible with source selection
- prevent disabled plugin IDs from remaining enabled after package change
- disable/remove old route-backed Shopify action plugins from package profiles

Acceptance:

- Starter resolves `mkp-action-shopify-storefront-read-mcp`
- Starter does not resolve cart/customer-account/checkout plugins
- Elite resolves read plus cart plugin
- customer-account plugin is gated until auth posture is ready
- old `mkp-action-shopify-companion-read` is not used for greenfield Shopify deployments

### Slice 6 - MCP Verification And Drift Detection

Goal: avoid exposing actions whose underlying Shopify MCP tools are missing or changed.

Tasks:

- during install/package resolution, call `initialize` and `tools/list`
- verify declared MCP tools exist
- store observed tool schema/hash in readiness evidence
- warn or block on incompatible schema drift
- ignore newly discovered tools until imported into a new plugin version
- surface drift in release verification

Acceptance:

- missing `search_catalog` blocks or disables the read plugin action
- missing `update_cart` blocks or disables cart actions
- new Shopify MCP tools do not appear automatically
- release verification reports MCP drift clearly

### Slice 7 - Customer Accounts MCP Auth

Goal: support customer/order/return actions through Shopify Customer Accounts MCP without using Admin API for customer actions.

Tasks:

- implement Customer Accounts API discovery
- implement OAuth 2.0 authorization code with PKCE
- bind customer token to shopper session/customer identity
- handle token expiry/revocation
- add protected customer data readiness checks
- keep customer actions anonymous-disabled by default

Acceptance:

- anonymous shopper cannot see customer-account actions
- authenticated customer can invoke eligible customer-account MCP actions
- customer token is never exposed to runtime/plugin manifests

### Slice 8 - Verification

Goal: prove tier catalog and MCP execution agree.

Tests/proofs:

- Free does not expose cart/order/customer actions
- Starter compiles only MCP-backed read storefront actions
- Elite compiles read plus governed cart MCP actions
- downgrade from Elite to Starter removes cart/customer-action visibility
- forged Starter cart action is denied before MCP `tools/call`
- missing MCP tool is detected before launch
- Bridge action execution path uses MCP `tools/call`, not custom Shopify GraphQL

Acceptance:

- runtime catalog and Bridge tier enforcement agree for Free/Starter/Elite
- no tier relies on prompt-only behavior
- no Shopify customer-facing action is implemented with Bridge custom GraphQL

---

## What 009 Explicitly Does Not Do

`009` does not:

- preserve current Shopify action IDs or aliases
- preserve current Bridge hard-coded Shopify action executor behavior
- introduce GraphQL-in-config actions
- make Marketplace plugins executable
- add a new Marketplace plugin type
- move Shopify tokens into generic runtime/plugin manifests
- make runtime catalog the only tier enforcement layer
- open broad autonomous writes
- implement merchant/admin Shopify actions
- use Admin API as the default path for customer-facing actions

---

## Success Criteria

`009` is complete when:

- Shopify package profiles resolve tier-specific MCP-backed Marketplace plugin bundles.
- Shopify customer-facing action plugins use `adapterType = mcp-tool`.
- Starter uses Shopify Storefront/UCP MCP for read-only actions.
- Elite uses Shopify Storefront MCP for governed cart actions.
- Customer/account/order actions are planned behind Customer Accounts MCP, not Admin API.
- Bridge executes Shopify customer actions through MCP `tools/call`.
- Bridge still owns tier checks, auth/session binding, confirmation, audit, and rate limits.
- Current route-backed Shopify customer actions are removed from the greenfield package profiles.
- Marketplace compiler remains the source of runtime action catalog generation.

---

## Stop Conditions

Stop and reassess if:

- implementation depends on adding new customer-facing Shopify GraphQL action code in Bridge
- package profiles duplicate full action definitions instead of referencing plugins
- runtime becomes the only tier enforcement layer
- downgrading a package leaves cart/customer actions visible in runtime
- Free or Starter receives customer-account, order, checkout, or governed cart actions
- MCP discovery starts auto-exposing tools at runtime
- the work expands into a generic Marketplace rewrite before Shopify MCP execution is working

---

## First Technical Session Prompt

Use this prompt to start the implementation session:

```text
Read first:
- Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md
- Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md
- doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md
- doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/MCP/Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md
- doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/MCP/Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md
- Final_Documentation/System_Archtecture_Guides/PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md

Task:
Start implementing 009 Shopify MCP-First Tier Action Catalog Alignment.

Use the sequence in `009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md`.

Begin with Phase 0 / Phase 1:
- inventory current Shopify customer-facing actions and route-backed Bridge behavior
- add the Marketplace compiler contract for `adapterType = mcp-tool`
- target the first real vertical slice on `shopify_search_catalog`

Architecture decision:
- This is greenfield; do not preserve legacy Shopify action aliases.
- Do not add a new Marketplace plugin type.
- Do not add GraphQL-in-config actions.
- Do not implement Shopify customer-facing action behavior directly in Bridge.
- Use Marketplace ACTION plugins to control runtime action visibility.
- Use adapterType = mcp-tool for Shopify customer-facing actions.
- Use Shopify package profiles to resolve tier-specific plugin bundles.
- Keep Bridge as governance, auth/session, audit, and MCP execution adapter.
- Use Shopify MCP tools/call as the Shopify customer-action execution path.

First deliverables:
1. inventory current Shopify ACTION plugins and route-backed Bridge actions
2. map each current customer-facing action to Shopify MCP replacement or removal
3. inspect Shopify Storefront MCP tools for target store endpoints
4. document the new plugin split: storefront-read-mcp, cart-mcp, customer-account-mcp
5. write /tmp/loomai-009-shopify-mcp-first/current-action-replacement-inventory.md

Do not print, commit, or expose any Shopify tokens or app secrets.
Do not change public behavior before the inventory is complete.
```
