# 009 Shopify MCP-First Implementation Sequence

Status: implementation sequence plan (created 2026-05-04)

Owner mode: technical/productization LLM implementation session

Roadmap phase: `009` - implement Shopify MCP-backed customer capability execution through Marketplace action plugins and Bridge governance

Priority: P0 after `008` planning. This is the next architecture correction before expanding Shopify customer actions, Elite action claims, or generic MCP ecosystem support.

Source strategy drafts:

- [../MCP/Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md](../MCP/Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md)
- [../MCP/Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md](../MCP/Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md)

Related implementation roadmaps:

- [006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md](006_THINKER_RESOLVER_GOVERNED_ISSUE_RESOLUTION_BLUEPRINT.md)
- [006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md](006_1_THINKER_PHASE_1_READ_ONLY_ISSUE_RESOLUTION_PRODUCTIZATION.md)
- [006_3_GOVERNED_LOW_RISK_WRITE_EXECUTION.md](006_3_GOVERNED_LOW_RISK_WRITE_EXECUTION.md)
- [007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md](007_COOLIFY_DEPLOYMENT_PROVIDER_AND_RESTARTABLE_SERVICES.md)
- [008_CONTROLLED_DESIGN_PARTNER_LAUNCH_AND_MARKET_PROOF.md](008_CONTROLLED_DESIGN_PARTNER_LAUNCH_AND_MARKET_PROOF.md)
- [009_1_MARKETPLACE_CONFIG_DRIVEN_MCP_CAPABILITY_ARCHITECTURE.md](009_1_MARKETPLACE_CONFIG_DRIVEN_MCP_CAPABILITY_ARCHITECTURE.md)
- [009_2_MCP_EXECUTION_GATEWAY_EXTRACTION_PLAN.md](009_2_MCP_EXECUTION_GATEWAY_EXTRACTION_PLAN.md)

Related guides:

- `Final_Documentation/System_Archtecture_Guides/PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md`
- `Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md`
- `Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md`

---

## Purpose

This plan is not another strategy document.

It is the implementation order for the Shopify MCP decision already made in the two source drafts:

- Draft 011 defines the governed MCP capability plane.
- Draft 009 defines the Shopify-specific MCP action catalog and tier alignment.
- This Plan 009 defines the sequence for turning both into working product behavior.

If these documents conflict:

1. Draft 011 owns generic MCP architecture and protocol boundaries.
2. Draft 009 owns Shopify product capability mapping.
3. This implementation plan owns order, gates, and first-session execution.

Strategy changes should update the source drafts first, then this sequence.

---

## Core Decision

Shopify customer-facing capabilities are greenfield and MCP-first.

Use Shopify MCP as the customer-action execution path from day one when Shopify exposes the needed customer capability.

Bridge remains mandatory, but its job is governance, not custom Shopify customer-action implementation.

Bridge owns:

- Shopify app install state
- store/deployment binding
- merchant billing and tier state
- customer/session auth handoff
- Customer Accounts OAuth/PKCE where needed
- MCP server resolution
- MCP transport
- confirmation and risk gates
- audit trail
- rate limits
- normalized action result shape
- denial reasons and operator evidence

Bridge does not own:

- hard-coded product search actions
- hard-coded policy lookup actions
- hard-coded cart mutations
- hard-coded order/customer/return reads when Customer Accounts MCP covers them
- broad raw Shopify GraphQL action implementations as the default customer-action path

Raw Shopify API/GraphQL remains allowed only for:

- app install, billing, webhook, and source-sync infrastructure
- temporary evidence gathering where MCP coverage is not yet proven
- explicitly reviewed gaps where Shopify MCP does not expose the needed capability

---

## Non-Negotiable Boundaries

Do not add a new Marketplace plugin type for the first implementation.

Use existing Marketplace plugin categories:

- `ACTION` for MCP tools
- `DATA` later for MCP resources
- `TEMPLATE` later for MCP prompts where useful

Do not let runtime `tools/list` become product truth.

MCP discovery is allowed for:

- plugin creation/import
- install verification
- drift detection
- release evidence

MCP discovery is not allowed to auto-expose runtime tools to shoppers or agents.

Do not preserve legacy Shopify action IDs or aliases.

Use canonical greenfield action IDs such as:

- `shopify_search_catalog`
- `shopify_lookup_catalog`
- `shopify_get_product`
- `shopify_search_policies`
- `shopify_get_cart`
- `shopify_update_cart`
- `shopify_get_customer_orders`
- `shopify_lookup_order`
- `shopify_get_return_eligibility`
- `shopify_start_return_request`

Do not start with merchant/admin Shopify actions.

This plan is customer-facing only:

- catalog
- policy
- cart
- customer account
- order
- return
- checkout handoff where approved

---

## Target Product Flow

The expected final flow is:

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

Tier and package selection must not duplicate action definitions.

The action definition lives in Marketplace `ACTION` plugins.

Package profiles only decide which plugins are required, disabled, or allowed for a deployment.

Runtime only executes the compiled catalog. It does not infer Shopify tier policy independently.

---

## Target Plugin Bundles

### Starter

`mkp-action-shopify-storefront-read-mcp`

Purpose:

- read-only customer-facing store intelligence
- Storefront MCP and UCP catalog/policy tools

Initial actions:

- `shopify_search_catalog`
- `shopify_lookup_catalog`
- `shopify_get_product`
- `shopify_search_policies`

Expected endpoint families:

- `/api/ucp/mcp`
- `/api/mcp`

### Elite

`mkp-action-shopify-cart-mcp`

Purpose:

- governed cart assistance
- confirmation and audit before mutation

Initial actions:

- `shopify_get_cart`
- `shopify_update_cart`

### Elite After Customer Accounts Readiness

`mkp-action-shopify-customer-account-mcp`

Purpose:

- authenticated customer-account, order, return, and account-context actions
- OAuth/PKCE and protected customer-data posture required before exposure

Representative actions:

- `shopify_get_customer_orders`
- `shopify_lookup_order`
- `shopify_get_order_status`
- `shopify_get_return_eligibility`
- `shopify_start_return_request`

### Deferred

`mkp-action-shopify-checkout-mcp`

Purpose:

- checkout continuation only after checkout risk posture, partner constraints, and Shopify capability evidence are approved

Initial posture:

- prefer checkout handoff
- no autonomous payment or broad checkout writes

---

## Implementation Sequence

### Phase 0: Context Lock And Evidence Baseline

Read:

- this plan
- Draft 009 Shopify MCP capability execution plane
- Draft 011 governed MCP capability plane
- Shopify control/execution plane separation guide
- current Marketplace plugin compiler and action catalog code
- current Shopify Bridge action execution code

Output:

- current Shopify customer-action inventory
- list of existing route-backed Bridge actions to remove from greenfield package profiles
- list of target Shopify MCP endpoints for the dev store
- list of required secrets/env names without printing secret values
- proof that no implementation session will preserve legacy action aliases

Gate:

- no public behavior changes before the inventory exists

### Phase 1: MCP Action Contract In Marketplace

Implement manifest and compiler support for MCP-backed actions.

Required contract:

```json
{
  "type": "ACTION",
  "adapterType": "mcp-tool",
  "capabilityRef": "shopify.storefront.catalog.search",
  "execution": {
    "mcp": {
      "serverRef": "shopify-storefront",
      "endpointKind": "storefront",
      "toolName": "search_catalog",
      "schemaHash": "optional-known-hash"
    }
  }
}
```

Work:

- validate `adapterType = mcp-tool`
- validate `execution.mcp.serverRef`
- validate `execution.mcp.toolName`
- compile MCP execution metadata into the deployment action catalog
- keep existing non-MCP action compilation working
- add tests using a small fixture `ACTION` plugin

Gate:

- a deployment package can compile an MCP action without Bridge custom Shopify code

### Phase 2: Deployment MCP Server References

Add the config shape for deployment-scoped MCP server references.

Server refs may come from:

- provider/deployment config
- plugin install config
- Bridge Shopify install state

They should not require a new top-level `MCP_SERVER` plugin type.

Work:

- represent named server refs such as `shopify-storefront`, `shopify-ucp`, and `shopify-customer-account`
- bind refs to tenant/store/deployment context
- keep secrets in existing secret/config handling
- make missing refs produce explicit readiness errors

Gate:

- compiled actions can resolve a server ref without exposing raw endpoint secrets to runtime callers

### Phase 3: Bridge MCP Client Core

Implement the Bridge-side MCP adapter.

Transport priority:

- Streamable HTTP first
- legacy HTTP+SSE only as compatibility support if needed

Minimum protocol support:

- `initialize`
- `tools/list` for verification
- `tools/call` for execution

Work:

- resolve MCP server refs for the deployment/store
- attach required Shopify auth/session context
- support UCP agent profile context where needed
- normalize MCP tool results into existing action-result shape
- normalize MCP errors into governed denial/evidence shape
- apply tier, install, customer/session, confirmation, audit, and rate-limit checks before `tools/call`

Gate:

- Bridge can execute one safe read-only MCP tool through the normal governed action path

### Phase 4: Storefront Read Vertical Slice

Create and wire `mkp-action-shopify-storefront-read-mcp`.

Actions:

- `shopify_search_catalog`
- `shopify_lookup_catalog`
- `shopify_get_product`
- `shopify_search_policies`

Work:

- create plugin manifest entries with `adapterType = mcp-tool`
- map arguments conservatively to Shopify MCP tool schemas
- compile into the runtime action catalog for Starter
- remove old route-backed read action plugin from greenfield Starter package profiles
- preserve current user-facing behavior only where MCP returns equivalent evidence

Gate:

- a Starter deployment can answer catalog/policy questions using MCP-backed action evidence

### Phase 5: Package Profile Bundle Resolution

Make Shopify package profiles infer the right Marketplace plugin bundle.

Work:

- update package profile `detailsJson` or equivalent config for required/disabled plugin IDs
- ensure Starter resolves only read-only MCP plugins
- ensure Elite resolves read-only plus approved governed action plugins
- ensure downgrades remove cart/customer-account actions from compiled catalogs
- keep package profiles as references to plugins, not full action definitions

Gate:

- tier change produces the expected compiled action catalog without duplicated action definitions

### Phase 6: MCP Verification And Drift Detection

Add install and release verification for MCP-backed actions.

Work:

- call `initialize`
- call `tools/list`
- verify required tool names exist
- verify expected input/output schema shape or hash where available
- write readiness evidence
- block or disable plugin release when required tools are missing
- detect drift without auto-exposing new runtime tools

Gate:

- a changed Shopify MCP server cannot silently change the shopper-visible action catalog

### Phase 7: Elite Cart MCP

Create and wire `mkp-action-shopify-cart-mcp`.

Actions:

- `shopify_get_cart`
- `shopify_update_cart`

Work:

- enforce confirmation before mutation
- log before/after action evidence where safe
- keep rate limits and denial reasons visible to operators
- do not add legacy aliases like `add_product_to_cart`
- do not bypass the compiled action catalog

Gate:

- Elite can perform a governed cart update through Shopify MCP with confirmation and audit

### Phase 8: Customer Accounts MCP

Create the customer-account path only after auth posture is ready.

Work:

- verify Shopify Customer Accounts MCP endpoint behavior
- implement OAuth 2.0 / PKCE flow
- bind customer identity to deployment/session
- store tokens safely
- apply protected customer-data policy
- expose account/order/return actions only for authenticated sessions
- add explicit denial states for unauthenticated or unbound sessions

Gate:

- no order, return, or customer-account action is visible or executable without the required customer session binding

Pending external auth material for staging live verification:

- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_ENABLED=true`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_PROTECTED_DATA_APPROVED=true`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_ID`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CLIENT_SECRET`
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_REDIRECT_URI`
- optional `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STOREFRONT_DOMAIN` when staging Customer Account OAuth discovery must use a connected custom storefront domain
- `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SCOPES`
- optional `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_STATE_TTL` and `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_SESSION_TTL`
- optional `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_CONNECT_TIMEOUT` and `SHOPIFY_BRIDGE_CUSTOMER_ACCOUNT_MCP_READ_TIMEOUT`
- registered Shopify Customer Account OAuth redirect URI for the staging Bridge/customer flow
- a staging test customer login flow to complete the PKCE authorization and bind the customer token to the Bridge shopper session
- confirmation that the staging store is approved for the protected customer data needed by these actions

Do not paste these values into tracked docs. Store secrets in Coolify staging env or local ignored/private secret files and record only the names/paths in handoff docs.

Prepared status:

- Bridge returns `CUSTOMER_ACCOUNT_MCP_NOT_CONFIGURED` until this posture is configured.
- Bridge exposes `/api/customer-auth/start`, `/api/customer-auth/callback`, and `/api/customer-auth/session` for Shopify Customer Account OAuth/PKCE start, callback, and session status.
- Bridge stores bound customer token material encrypted at rest and indexes it only by shop plus a HMAC of the shopper session identifier.
- Bridge wires explicit HTTP connect/read timeouts for both MCP Gateway calls and Customer Account discovery/token calls.
- After posture is configured, Bridge returns `CUSTOMER_ACCOUNT_AUTH_REQUIRED` until the customer OAuth token is bound to the shopper session.
- MCP Gateway supports `CUSTOMER_OAUTH_PKCE` by attaching the bound customer OAuth token as the MCP Authorization header.

### Phase 9: Checkout And Generic MCP Follow-On

After Shopify read/cart/customer-account verticals are stable, continue with the broader Draft 011 plan.

Possible follow-ons:

- checkout handoff MCP capability
- generic MCP server import that creates private plugin drafts
- MCP resource support through `DATA` plugins
- MCP prompt support through template contributions
- Platform/deployment exposure as an MCP server

Gate:

- generic MCP import does not precede the Shopify MCP customer-action vertical slice

Pending external auth material for checkout live verification:

- `SHOPIFY_BRIDGE_CHECKOUT_MCP_ENABLED=true`
- `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_ID`
- `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_SECRET`
- optional `SHOPIFY_BRIDGE_CHECKOUT_MCP_TERMINAL_OPERATIONS_ENABLED=true` only for explicitly approved `complete_checkout` / `cancel_checkout` staging tests
- confirmation whether staging may test only non-terminal checkout create/get/update or also terminal checkout operations

Do not enable terminal checkout operations by default. Keep them behind explicit staging approval and audit evidence.

Prepared status:

- Platform secret definitions exist for `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_ID` and `SHOPIFY_BRIDGE_CHECKOUT_MCP_CLIENT_SECRET`.
- Platform-managed MCP Gateway provisioning maps those values to gateway-only `MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_ID` and `MCP_SECRET_SHOPIFY_CHECKOUT_MCP_CLIENT_SECRET` env vars after both platform secrets are configured.
- MCP Gateway supports `SHOPIFY_AGENTIC_CLIENT_CREDENTIALS` using Shopify's default JSON token request to `https://api.shopify.com/auth/access_token`; a manifest-level `auth.tokenUrl` override may be used if Shopify changes the endpoint.
- Bridge returns `CHECKOUT_MCP_NOT_CONFIGURED` until managed checkout credentials are configured, and returns `CHECKOUT_TERMINAL_OPERATION_DISABLED` for terminal checkout actions unless terminal operations are explicitly enabled.

---

## First Implementation Target

Start with the smallest real vertical slice:

```text
Marketplace ACTION plugin
  -> compiled runtime action catalog
  -> Bridge governed MCP adapter
  -> Shopify MCP search_catalog tools/call
  -> normalized action evidence in the shopper answer path
```

Target action:

- `shopify_search_catalog`

Reason:

- it is customer-facing
- it is read-only
- it proves Marketplace-to-Bridge-to-MCP execution
- it avoids Customer Accounts OAuth/PKCE risk
- it creates the foundation for policy, product lookup, cart, and customer-account actions

---

## Acceptance Criteria

Plan 009 is complete when:

- Marketplace can validate and compile `ACTION` plugins with `adapterType = mcp-tool`.
- Shopify package profiles resolve tier-specific Marketplace plugin bundles.
- Starter compiles only read-only Shopify Storefront/UCP MCP actions.
- Elite compiles governed cart actions only when the package profile enables the cart MCP plugin.
- Customer-account/order/return actions are blocked until Customer Accounts OAuth/PKCE and protected-data posture are implemented.
- Bridge executes Shopify customer-facing actions through MCP `tools/call`.
- Bridge still owns install checks, tier checks, session/customer binding, confirmation, audit, rate limits, and denial reasons.
- MCP discovery is used for import/verification/drift, not runtime auto-exposure.
- Old route-backed Shopify customer actions are removed from greenfield package profiles.
- Runtime action catalog remains generated from Marketplace/package profile truth.

Public self-serve Shopify App Store launch remains a separate gate after Plan 009 implementation. It requires:

- PR `#156` (`Platform v8`) merged or otherwise landed into the production-intended branch.
- Open PR `#156` review threads resolved, including Coolify structured upstream error handling and MCP Gateway HTTP timeout enforcement.
- Customer Account MCP and Checkout MCP claims kept behind Shopify OAuth/PKCE, protected customer data, checkout credential, and live safe-call evidence gates.
- Merchant-facing self-serve onboarding, pricing, support policy, install/recovery guidance, and App Store review collateral completed.

---

## Stop Conditions

Stop and reassess if:

- the implementation starts adding new customer-facing Shopify GraphQL action code in Bridge
- package profiles duplicate full action definitions instead of referencing plugins
- a new top-level `MCP_SERVER` plugin type becomes required for the first vertical slice
- runtime `tools/list` starts auto-exposing shopper-visible actions
- tier downgrade leaves cart, order, return, or customer-account actions visible
- Free or Starter receives governed cart, order, return, checkout, or customer-account actions
- Customer Accounts MCP starts without OAuth/PKCE and protected-data design
- checkout work starts before read/cart/customer-account evidence exists
- the work expands into generic MCP import before Shopify `shopify_search_catalog` executes end to end

---

## First Technical Session Prompt

Use this prompt to start implementation:

```text
Read first:
- Final_Documentation/Development_Guides/LLM-guides/Codex_Strategic_Context.md
- Final_Documentation/Development_Guides/LLM-guides/CODEX_WORKING_CONTEXT.md
- doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/Implementation/009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md
- doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/MCP/Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md
- doc/Productization/future-work/MarketPlace/Products/Strategy/RoadMaps/MCP/Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md
- Final_Documentation/System_Archtecture_Guides/PLAN_SHOPIFY_CONTROL_AND_EXECUTION_PLANE_SEPARATION.md

Task:
Start Plan 009 Shopify MCP-first implementation.

Primary target:
Implement the smallest real vertical slice for `shopify_search_catalog`:
Marketplace ACTION plugin -> compiled runtime action catalog -> Bridge governed MCP adapter -> Shopify MCP `tools/call` -> normalized action evidence.

Architecture rules:
- This is greenfield; do not preserve legacy Shopify action aliases.
- Do not add a new Marketplace plugin type for the first implementation.
- Do not add GraphQL-in-config actions.
- Do not implement Shopify customer-facing action behavior directly in Bridge.
- Use Marketplace ACTION plugins to control runtime action visibility.
- Use `adapterType = mcp-tool` for Shopify customer-facing actions.
- Use Shopify package profiles to resolve tier-specific plugin bundles.
- Keep Bridge as governance, auth/session, audit, rate-limit, and MCP execution adapter.
- Use Shopify MCP `tools/call` as the Shopify customer-action execution path.
- Use MCP discovery only for import, verification, and drift detection.

First deliverables:
1. inventory current Shopify customer-facing ACTION plugins and route-backed Bridge actions
2. identify compiler changes needed for `adapterType = mcp-tool`
3. add or update tests proving MCP-backed ACTION plugin metadata compiles into runtime action config
4. implement the Bridge MCP client skeleton for `initialize`, `tools/list`, and `tools/call`
5. wire a dev-safe `shopify_search_catalog` vertical slice if the target Shopify MCP endpoint and secrets are available
6. document any endpoint/auth blockers without printing secrets

Do not print, commit, or expose Shopify tokens, app secrets, customer tokens, or private endpoint credentials.
Do not change public behavior before the inventory and compiler tests are complete.
```
