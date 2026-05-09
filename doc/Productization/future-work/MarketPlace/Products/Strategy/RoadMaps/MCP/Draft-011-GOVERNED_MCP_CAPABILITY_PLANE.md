# PLAN-011 - Governed MCP Capability Plane

Status: strategic replacement plan
Date: 2026-05-04

This plan reviews the existing Claude-authored MCP plans and replaces them with a tighter strategy for supporting MCP without weakening the Marketplace, tier, action-catalog, and Bridge governance architecture.

The conclusion: MCP should become a first-class execution and interoperability protocol, but it should not become a separate product architecture or an unbounded dynamic action catalog.

2026-05-04 decision update:

> Shopify customer-facing actions are greenfield and should depend on Shopify MCP from day one. Bridge remains the governance/auth/session/audit/MCP-adapter boundary, but it should not implement custom Shopify product, cart, customer, order, or return action logic when Shopify MCP exposes the capability.

---

## 1. Review Of Existing MCP Plans

Reviewed:

- `MCP_INTEGRATION_STRATEGY.md`
- `PLAN-010-MCP_CLIENT_AND_SERVER_INTEGRATION.md`

### What They Get Right

- MCP matters strategically. Customers and developers will increasingly ask whether the platform can consume and expose MCP.
- Our action framework maps naturally to MCP tools.
- Our knowledge/data layer maps partially to MCP resources.
- Our prompt/template system maps partially to MCP prompts.
- The platform's real differentiator is governance: tiering, tenant isolation, confirmation, audit, and curated deployment catalogs.
- We should support both directions:
  - AI Fabric consumes MCP servers.
  - AI Fabric exposes deployments as MCP servers.

### What They Get Wrong Or Underestimate

1. **They treat MCP as a new Marketplace plugin type too early.**

   Adding `MCP_SERVER` as a top-level plugin type cuts across catalog categories, manifest validation, install preview, compilation, entitlement logic, UI filters, and release verification.

   Current Marketplace already has the right primitives:

   - `ACTION`
   - `DATA`
   - `TEMPLATE`
   - `INFERENCE_PROFILE`

   MCP tools should compile through `ACTION` plugins. MCP resources should compile through `DATA` plugins. MCP prompts should compile through prompt/template contributions when needed. A new top-level type is unnecessary for the first mature implementation.

2. **They assume dynamic `tools/list` should directly become runtime action truth.**

   That is unsafe for productized deployments. If an external MCP server changes its tools, the runtime catalog would change outside Marketplace review, tier policy, package profiles, and verification.

   Dynamic discovery is useful for import, verification, and drift detection. It should not automatically expose new runtime tools to end users.

3. **They lead with generic ecosystem unlock, not customer product value.**

   For our current product line, the highest value is Shopify customer-facing MCP:

   - Storefront MCP / UCP catalog
   - Storefront cart tools
   - Customer Accounts MCP
   - Checkout MCP where permitted

   Generic GitHub, Slack, filesystem, and database MCP servers can come later.

4. **They use stale transport language.**

   Current MCP remote transport is **Streamable HTTP**. Legacy HTTP+SSE can be supported for compatibility, but it should not be the main architecture target.

5. **They understate authentication and data-risk complexity.**

   Customer Accounts MCP requires OAuth 2.0 / PKCE and protected customer data posture. Checkout MCP has agent and partner constraints. These are product/security flows, not just protocol adapters.

6. **They make MCP server exposure too early.**

   Exposing our deployments as MCP servers is useful for developer experience and partner interoperability, but consuming Shopify MCP is more urgent for product value.

---

## 2. Strategic Thesis

MCP should be treated as a **capability transport**, not as the source of product truth.

Product truth remains:

```text
Marketplace plugin bundle
  -> package/tier profile
  -> deployment install state
  -> compiled runtime action catalog
  -> Bridge/runtime governance
```

For generic integrations, MCP becomes one execution backend behind that catalog:

```text
Compiled action
  -> adapterType = mcp-tool
  -> MCP server ref
  -> MCP tool name
  -> tools/call
```

For Shopify customer actions, MCP is not merely a future option or supplemental fallback. It is the primary execution contract.

The stable user-facing action ID stays ours. The MCP tool name is an implementation detail.

This preserves Marketplace compatibility and lets us swap execution backends later.

---

## 3. Non-Negotiable Design Principles

### 3.1 No Unreviewed Runtime Tool Expansion

`tools/list` may discover available tools, but discovered tools do not become available to the LLM until a Marketplace `ACTION` plugin explicitly declares them or an admin imports and publishes a reviewed plugin version.

### 3.2 Existing Plugin Types Stay Primary

Do not add a top-level `MCP_SERVER` plugin type in the first implementation.

Use:

- `ACTION` plugin for MCP tools.
- `DATA` plugin for MCP resources.
- `TEMPLATE` or prompt config contributions for MCP prompts.

If we later need a shared connection/package object, add a **connection profile** or **adapter profile**, not a Marketplace type that competes with `ACTION` and `DATA`.

### 3.3 Marketplace Catalog Remains Stable

The runtime action catalog should be deterministic from:

- active package profile
- installed plugin versions
- plugin manifest
- install config
- secret refs

External MCP server drift should create warnings or disable specific tools, not silently add new capability.

### 3.4 Bridge Remains Final Authority

MCP tools do not bypass:

- tenant binding
- tier entitlement
- customer/session auth
- confirmation
- audit
- rate limits
- idempotency
- product-specific policy

For Shopify, Bridge governance happens before `tools/call`, but Shopify MCP owns the customer-action implementation.

### 3.5 Curated Product Actions Beat Raw Tool Dumps

For Shopify, expose product actions such as `shopify_search_catalog`, `shopify_get_product`, `shopify_get_cart`, and `shopify_update_cart`.

Do not expose raw tool names directly unless the product intentionally chooses that. Raw MCP tool names can change or differ by server.

---

## 4. Target Architecture

```text
Marketplace ACTION plugin
  declares actionId, schema, tier posture, confirmation posture
  declares execution.adapterType = mcp-tool
  declares execution.mcp.serverRef
  declares execution.mcp.toolName
  declares execution.mcp.endpointKind

Deployment draft compiler
  compiles ACTION plugin into actionsConfig.actions[]
  copies mcp execution metadata into the compiled action

Runtime / Bridge action executor
  resolves compiled action
  checks tier, install, auth, confirmation, audit
  calls McpExecutionAdapter
  normalizes MCP result into platform ActionResult

McpExecutionAdapter
  resolves serverRef and auth
  negotiates MCP protocol
  calls tools/call
  maps errors and rate limits
  records drift and schema mismatch evidence
```

### 4.1 Manifest Shape

Example action contribution:

```json
{
  "actionId": "shopify_search_catalog",
  "displayName": "Search products",
  "description": "Search the Shopify storefront catalog.",
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
          "query": "{{params.query}}",
          "context": {
            "intent": "{{conversation.intent}}",
            "address_country": "{{shopper.country}}"
          }
        }
      }
    }
  }
}
```

The compiled runtime action still has stable name `shopify_search_catalog`. The MCP tool is `search_catalog`.

### 4.2 MCP Server References

MCP server references should live in deployment/provider config or plugin install config, not as a standalone Marketplace plugin type.

Example:

```json
{
  "mcpServers": [
    {
      "id": "shopify-storefront",
      "transport": "STREAMABLE_HTTP",
      "urlTemplate": "https://{{shopDomain}}/api/mcp",
      "authMode": "NONE",
      "scope": "SHOPIFY_STOREFRONT_PUBLIC"
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

### 4.3 Import Is Not Runtime Exposure

Admin discovery flow:

```text
Admin enters MCP server
  -> Platform calls initialize + tools/list
  -> Platform shows discovered tools
  -> Admin selects tools
  -> Platform generates ACTION plugin draft
  -> Plugin is reviewed/published/installed
  -> Deployment compiler exposes selected actions
```

This gives the convenience of MCP discovery without runtime catalog chaos.

### 4.4 Discovery Lifecycle

MCP discovery happens at different moments for different reasons.

#### Plugin Creation / Import

This is where discovery creates a draft.

```text
MCP server URL + auth
  -> initialize
  -> tools/list
  -> resources/list / prompts/list where needed
  -> admin selects capabilities
  -> admin maps raw MCP tools to stable platform action IDs
  -> admin classifies risk, tier, auth, confirmation, and exposure
  -> platform generates ACTION / DATA plugin draft
```

Creation-time discovery is allowed to create plugin draft content because a human or review process still controls what gets published.

#### Plugin Installation / Package Resolution

This is where discovery verifies the draft.

```text
published plugin install
  -> resolve install config and secret refs
  -> initialize
  -> tools/list
  -> verify declared MCP tools exist
  -> compare observed schema to stored schema/hash
  -> warn or block on incompatible drift
  -> compile only declared plugin actions into the deployment catalog
```

Installation-time discovery must not add newly discovered tools to the deployment.

#### Runtime Execution

Runtime does not use discovery for exposure.

```text
LLM sees compiled platform action
  -> action executor resolves adapterType = mcp-tool
  -> executor calls tools/call for the declared toolName
```

Runtime may use cached server capability health for fail-fast behavior, but it must not mutate the action catalog from live `tools/list`.

---

## 5. Shopify MCP Product Mapping

Shopify should be the first proof of this architecture because Shopify now provides MCP surfaces directly for customer-facing commerce.

### 5.1 Starter / Read-Only Storefront Intelligence

Plugin:

```text
mkp-action-shopify-storefront-read-mcp
```

Actions:

| Platform action | MCP endpoint | MCP tool |
|---|---|---|
| `shopify_search_catalog` | `/api/ucp/mcp` | `search_catalog` |
| `shopify_lookup_catalog` | `/api/ucp/mcp` | `lookup_catalog` |
| `shopify_get_product` | `/api/ucp/mcp` | `get_product` |
| `shopify_search_policies` | `/api/mcp` | `search_shop_policies_and_faqs` |

Tier:

- Free may use only narrow search if product strategy wants that.
- Starter gets full read-only storefront intelligence.

### 5.2 Elite / Cart And Checkout Handoff

Plugin:

```text
mkp-action-shopify-cart-mcp
```

Actions:

| Platform action | MCP endpoint | MCP tool |
|---|---|---|
| `shopify_get_cart` | `/api/mcp` | `get_cart` |
| `shopify_update_cart` | `/api/mcp` | `update_cart` |

Policy:

- Requires Elite.
- Requires explicit confirmation for cart mutations.
- Keeps our governed-action audit.
- Uses Shopify MCP as the execution authority for cart behavior, not custom Bridge code.
- Bridge remains policy authority and governs confirmation/audit before calling MCP.

### 5.3 Elite / Customer Account And Order Support

Plugin:

```text
mkp-action-shopify-customer-account-mcp
```

Actions:

| Platform action | MCP surface |
|---|---|
| `shopify_get_customer_orders` | Customer Accounts MCP |
| `shopify_lookup_order` | Customer Accounts MCP |
| `shopify_get_order_status` | Customer Accounts MCP |
| `shopify_get_return_eligibility` | Customer Accounts MCP |
| `shopify_start_return_request` | Customer Accounts MCP where supported |

Policy:

- Requires customer authentication.
- Requires OAuth 2.0 PKCE.
- Requires protected customer data posture.
- Never expose to anonymous shoppers.
- Should be Elite or managed/enterprise until the auth experience is mature.

### 5.4 Checkout MCP

Checkout MCP should be a later Elite/partner-controlled plugin.

Recommended posture:

- Use checkout creation and continuation URL first.
- Do not complete payment autonomously.
- Gate any payment completion behind partner eligibility, explicit confirmation, fraud controls, and legal review.

---

## 6. What We Are Ahead Of

The platform already has capabilities that raw MCP does not standardize:

- Marketplace-curated plugin installation.
- Tier-specific package profiles.
- Runtime action catalog compilation.
- Confirmation requirements.
- Audit trail.
- Deployment-specific enablement.
- Tenant isolation.
- Product-specific Bridge governance.
- Data/RAG pipeline and vectorization.

This means MCP is not replacing us. It is a source protocol we can govern better than generic MCP clients.

---

## 7. What We Are Missing

Current code does not have:

- MCP client transport.
- MCP `initialize` negotiation.
- MCP `tools/list` verification.
- MCP `tools/call` execution adapter.
- Streamable HTTP transport.
- Legacy SSE fallback.
- Stdio sandbox/process lifecycle for local MCP servers.
- MCP action manifest metadata.
- Stable capability-to-tool mapping.
- Drift detection between declared Marketplace actions and live MCP tools.
- Customer Accounts MCP OAuth/PKCE token flow.
- UCP agent profile handling.
- Marketplace import flow from `tools/list`.
- Deployment MCP server exposure.

---

## 8. Better Implementation Plan

### Phase 0 - Protocol And Manifest Alignment

Goal: lock the contract before writing transport code.

Tasks:

- define `adapterType = mcp-tool`
- define action `execution.mcp` manifest fields
- define deployment `mcpServers` config
- define stable `capabilityRef` naming
- define risk classes:
  - `READ_PUBLIC`
  - `READ_CUSTOMER_AUTH`
  - `WRITE_CUSTOMER_CART`
  - `WRITE_CHECKOUT`
  - `WRITE_SUPPORT_REQUEST`
- define drift behavior:
  - missing MCP tool disables action
  - schema mismatch warns or blocks based on strictness
  - new MCP tools are ignored until imported

Acceptance:

- Marketplace can validate an `ACTION` plugin that declares MCP execution metadata.
- No new plugin type is required.

### Phase 1 - MCP Client Core

Goal: call a known MCP tool from Bridge/runtime.

Tasks:

- implement JSON-RPC envelope handling
- implement `initialize`
- implement `tools/list`
- implement `tools/call`
- implement Streamable HTTP transport first
- implement legacy SSE fallback only if required by target servers
- implement stdio only for local/developer servers, behind explicit allowlist
- normalize MCP responses into platform `ActionResult`
- normalize MCP errors into platform error codes

Acceptance:

- a compiled action can call a known MCP tool through `serverRef` + `toolName`
- action results still look native to the runtime
- failed MCP calls do not break the deployment

### Phase 2 - Marketplace Compiler Support

Goal: make MCP-backed actions first-class Marketplace `ACTION` plugin contributions.

Tasks:

- update manifest validation for `adapterType = mcp-tool`
- validate `execution.mcp.serverRef`
- validate `execution.mcp.toolName`
- copy execution metadata into compiled actions config
- add install-config support for MCP server URL templates and secret refs
- add verification that declared tools exist during install or readiness checks

Acceptance:

- installing an MCP-backed ACTION plugin compiles deterministic runtime actions
- uninstalling/disabling the plugin removes those actions
- unknown/disallowed MCP tool metadata fails validation

### Phase 3 - Shopify Storefront MCP

Goal: replace current custom Shopify customer-facing read/cart action code with Shopify MCP.

Tasks:

- add Shopify Storefront MCP server resolver:
  - `/api/mcp`
  - `/api/ucp/mcp`
- add UCP agent profile config
- create `mkp-action-shopify-storefront-read-mcp`
- create `mkp-action-shopify-cart-mcp`
- use new canonical `shopify_*` action IDs; do not preserve legacy aliases
- keep Bridge tier checks and governed-action audit
- remove route-backed Bridge custom customer-action execution from greenfield package profiles

Acceptance:

- Starter exposes read-only Shopify MCP-backed actions
- Elite exposes cart actions
- current Bridge governance still blocks forged/non-tier calls
- Shopify customer-facing action execution uses MCP `tools/call`, not Bridge custom GraphQL

### Phase 4 - Customer Accounts MCP

Goal: support authenticated customer/order actions without abusing Admin API.

Tasks:

- implement Customer Accounts MCP discovery:
  - `/.well-known/customer-account-api`
  - OAuth discovery endpoint
- implement OAuth 2.0 authorization code with PKCE
- store customer access token safely with session/customer binding
- add protected customer data readiness checks
- create `mkp-action-shopify-customer-account-mcp`
- keep customer/account actions out of anonymous runtime catalogs

Acceptance:

- authenticated customer can ask for order/account actions
- anonymous shopper cannot see or execute customer-account actions
- token expiry and revocation are handled cleanly

### Phase 5 - MCP Import And Verification

Goal: make generic MCP useful without letting it bypass Marketplace.

Tasks:

- during plugin creation/import:
  - admin enters MCP server
  - platform performs `initialize` and `tools/list`
  - selected tools generate ACTION plugin draft
  - selected resources generate DATA plugin draft
  - selected prompts generate prompt/template draft where needed
  - draft stores observed MCP tool schema hash
- during plugin installation/package resolution:
  - platform re-runs `initialize` and `tools/list`
  - declared tools are verified against live MCP server capabilities
  - incompatible schema drift blocks install or release based on risk class
  - newly discovered tools are ignored until imported into a new plugin version
- during readiness/runtime health:
  - readiness checks detect drift
  - runtime fail-fast uses health state but does not expose new tools

Acceptance:

- generic MCP onboarding is fast
- runtime remains governed by published plugin manifests
- creation-time discovery can generate drafts
- installation-time discovery only verifies declared capabilities

### Phase 6 - Platform As MCP Server

Goal: expose selected deployment capabilities to external MCP clients.

Tasks:

- expose a deployment MCP endpoint using Streamable HTTP
- implement `initialize`
- implement `tools/list`
- implement `tools/call`
- optionally implement `resources/list/read`
- issue tenant-scoped MCP tokens
- map confirmation-required actions to a structured pending-confirmation result
- never bypass confirmation

Acceptance:

- Claude/Cursor/partner agents can call a deployment through MCP
- exposed tools are only the deployment's allowed compiled actions
- audit includes MCP caller identity

### Phase 7 - Resources, Prompts, And Developer Tooling

Goal: complete MCP parity after the action path is production-safe.

Tasks:

- map selected DATA plugins to MCP resources
- map selected prompt templates to MCP prompts
- support local stdio dev servers with sandbox/process controls
- add docs and examples
- add reference plugin drafts

Acceptance:

- MCP support is complete without changing the product governance model

---

## 9. Sequencing Decision

Recommended order:

1. Phase 0 - Protocol and manifest alignment.
2. Phase 1 - MCP client core.
3. Phase 2 - Marketplace compiler support.
4. Phase 3 - Shopify Storefront MCP.
5. Phase 4 - Customer Accounts MCP.
6. Phase 5 - Generic MCP import.
7. Phase 6 - Platform as MCP server.
8. Phase 7 - Resources/prompts/developer tooling.

This is intentionally different from the Claude plans. The highest-value product path is **consume Shopify MCP safely first**, then generalize.

---

## 10. Risks And Mitigations

| Risk | Mitigation |
|---|---|
| External MCP server adds a dangerous tool | Ignore new tools until imported and published through Marketplace |
| External MCP server removes/changes a tool | Readiness drift check disables action or blocks release |
| Prompt injection through MCP result/resource content | Treat all MCP output as untrusted external content |
| Customer token leakage | Customer-scoped token store, short TTL, strict session binding |
| Tier bypass | Bridge checks tier after runtime action selection and before MCP call |
| Marketplace type sprawl | Use existing `ACTION` / `DATA` / `TEMPLATE` types |
| Legacy transport churn | Build Streamable HTTP first, support legacy SSE only as compatibility fallback |
| Stdio process abuse | Allow stdio only in local/dev or sandboxed worker contexts |
| Raw tool names leak into product UX | Use stable internal `actionId` and `capabilityRef` aliases |

---

## 11. Acceptance Criteria

This strategy is done when:

- Marketplace `ACTION` plugins can declare MCP-backed actions.
- Runtime can execute MCP-backed actions through the same action pipeline as native actions.
- Shopify Storefront MCP read actions work behind Starter package profiles.
- Shopify cart MCP actions work behind Elite package profiles with confirmation and audit.
- Customer Accounts MCP has a documented OAuth/PKCE design before any customer PII action ships.
- `tools/list` is used for import and verification, not unreviewed runtime exposure.
- Platform can expose selected compiled actions as an MCP server after the client path is stable.
- Existing non-MCP actions continue to work unchanged.
- Greenfield Shopify package profiles do not install route-backed Bridge customer-action plugins.

---

## 12. Strategic Positioning

The external message should not be "we support MCP servers."

The stronger message is:

> AI Fabric supports MCP as a governed capability plane. You can consume Shopify and third-party MCP tools, expose deployments as MCP, and still keep Marketplace curation, tiering, confirmation, audit, and tenant isolation.

That is materially better than raw MCP client support.

---

## 13. Source Notes

Current source checks used for this plan:

- Shopify Storefront MCP docs: `https://shopify.dev/docs/apps/build/storefront-mcp`
- Shopify Storefront MCP server docs: `https://shopify.dev/docs/apps/build/storefront-mcp/servers/storefront`
- Shopify Customer Accounts MCP server docs: `https://shopify.dev/docs/apps/build/storefront-mcp/servers/customer-account`
- Shopify Checkout MCP docs: `https://shopify.dev/docs/agents/carts-and-checkout/checkout-mcp`
- MCP transport spec: `https://modelcontextprotocol.io/specification/2025-11-25/basic/transports`
