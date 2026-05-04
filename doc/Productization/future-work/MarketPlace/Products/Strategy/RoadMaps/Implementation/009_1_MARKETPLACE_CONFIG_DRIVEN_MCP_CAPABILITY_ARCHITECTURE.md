# 009.1 Marketplace Config-Driven MCP Capability Architecture

Status: architecture change plan (created 2026-05-04)

Parent plan: [009 Shopify MCP-First Implementation Sequence](009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md)

Roadmap phase: `009.1` - generalize the Shopify MCP-first execution path so new MCP servers and tools can be onboarded through Marketplace configuration instead of new product code.

Priority: P0 follow-on after the first Shopify MCP vertical slice. This plan should start before broad third-party MCP support is promised externally.

Source strategy drafts:

- [../MCP/Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md](../MCP/Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md)
- [../MCP/Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md](../MCP/Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md)

Reference implementation:

- `ShopifyMcpClient`
- `ShopifyStorefrontMcpActionAdapter`
- `ShopifyCustomerAccountMcpActionAdapter`
- `ShopifyCheckoutMcpActionAdapter`
- `DeploymentMarketplaceDraftCompilerService`
- Shopify MCP Marketplace migrations `V81`, `V82`, and `V83`

---

## Purpose

Plan 009 proved the correct product direction:

```text
Marketplace ACTION plugin
  -> compiled runtime action catalog
  -> governed Bridge MCP adapter
  -> MCP tools/call
  -> normalized action evidence
```

The current implementation is still partly provider-specific:

- Bridge has explicit Shopify server refs.
- Bridge has explicit Shopify action routing.
- Bridge has provider-specific argument construction.
- New auth/session patterns still require code.

009.1 turns that into a generic governed MCP capability plane where the default path for a new MCP server is:

```text
MCP server discovery
  -> Marketplace plugin draft
  -> review/publish
  -> install with secret refs/server binding
  -> compile actionsConfig
  -> generic Bridge MCP executor
```

Adding another MCP tool should usually mean publishing or updating a Marketplace plugin, not writing Java.

---

## Core Decision

Do not add a new top-level Marketplace plugin type for MCP tools.

Use existing plugin categories:

- `ACTION` plugins for MCP tools.
- `DATA` plugins for MCP resources when resources become productized.
- `TEMPLATE` plugins for MCP prompts or workflow templates where useful.

The Marketplace manifest is the product truth.

MCP `tools/list` is discovery and verification evidence only. It must never directly become shopper-visible or agent-visible runtime truth.

---

## Target Config-Driven Contract

### Marketplace Plugin Manifest

An MCP-backed `ACTION` plugin may declare:

```json
{
  "pluginId": "mkp-action-example-crm-mcp",
  "pluginType": "ACTION",
  "contributions": {
    "mcpServers": [
      {
        "serverRef": "example-crm",
        "transport": "STREAMABLE_HTTP",
        "endpointUrlTemplate": "{{install.baseUrl}}/mcp",
        "authProfileRef": "example-crm-api-key",
        "allowedTools": [
          "search_contacts",
          "create_ticket"
        ],
        "verification": {
          "mode": "INITIALIZE_AND_TOOLS_LIST",
          "schemaDriftPolicy": "BLOCK_RELEASE"
        }
      }
    ],
    "actions": [
      {
        "actionId": "crm_search_contacts",
        "displayName": "Search CRM contacts",
        "adapterType": "mcp-tool",
        "readOnly": true,
        "anonymousAllowed": false,
        "riskClass": "READ_ONLY_EXTERNAL",
        "params": [
          {
            "name": "query",
            "type": "STRING",
            "required": true
          }
        ],
        "execution": {
          "adapterType": "mcp-tool",
          "mcp": {
            "serverRef": "example-crm",
            "toolName": "search_contacts",
            "toolSchemaHash": "sha256:observed-at-import",
            "argumentTemplate": {
              "query": "{{params.query}}",
              "tenantId": "{{deployment.tenantId}}"
            },
            "responseMapping": {
              "evidenceType": "MCP_TOOL_RESULT",
              "resultPath": "$",
              "citationsPath": "$.citations"
            }
          }
        }
      }
    ]
  }
}
```

This schema is additive to current `ACTION` manifests. Existing connector HTTP actions must keep compiling unchanged.

### Install Configuration

Install config provides values but not product behavior:

```json
{
  "baseUrl": "https://crm.example.com",
  "secretRefs": {
    "example-crm-api-key": "secret://deployment/EXAMPLE_CRM_API_KEY"
  }
}
```

Rules:

- Secrets are referenced, never copied into manifests or runtime action catalogs.
- URL templates can use install/deployment/store variables only from an allowlist.
- A plugin can require one or more auth profiles.
- A plugin cannot declare arbitrary headers that mirror user input unless the header is explicitly allowed by the auth profile.

### Compiled Runtime Action

The compiler emits deterministic action metadata:

```json
{
  "actionId": "crm_search_contacts",
  "adapterType": "mcp-tool",
  "execution": {
    "adapterType": "mcp-tool",
    "mcp": {
      "serverRef": "example-crm",
      "toolName": "search_contacts",
      "argumentTemplate": {},
      "responseMapping": {},
      "schemaDriftPolicy": "BLOCK_RELEASE"
    }
  }
}
```

Runtime does not receive secret values. Runtime can decide that an action exists, but Bridge resolves endpoint/auth and performs the actual MCP call.

---

## Generic Bridge MCP Runtime

Bridge gets a generic executor:

```text
McpActionExecutionService
  -> McpServerBindingResolver
  -> McpAuthProviderRegistry
  -> McpTransportClient
  -> McpArgumentTemplateRenderer
  -> McpGovernancePolicyEvaluator
  -> McpResultNormalizer
  -> McpAuditRecorder
```

### Server Binding Resolver

Resolves:

- `serverRef`
- endpoint URL
- transport
- auth profile
- secret refs
- tenant/deployment/store/session context

Resolution must be deterministic and auditable.

### Auth Provider Registry

Supported auth profiles for the first generic release:

| Auth mode | Config-only? | Notes |
| --- | --- | --- |
| `NONE` | yes | Public or anonymous MCP servers only. |
| `STATIC_BEARER_SECRET` | yes | Bearer token from deployment/store secret ref. |
| `API_KEY_HEADER_SECRET` | yes | Header name must be declared by the plugin and approved by validation. |
| `OAUTH2_CLIENT_CREDENTIALS` | yes after token service exists | Token URL, client id secret ref, client secret ref, scopes, cache TTL. |
| `OAUTH2_AUTH_CODE_PKCE` | partly | Generic token storage can be shared, but provider-specific login UX may still need code. |
| `CUSTOMER_OAUTH_PKCE` | partly | Requires session/customer binding and protected data posture. Shopify Customer Accounts is the first example. |

New auth modes are the main reason a future MCP server should still require code.

### Transport Client

Initial generic transport support:

- `STREAMABLE_HTTP`

Deferred:

- SSE compatibility where still needed.
- server-side stdio for trusted internal/local servers only.
- WebSocket or vendor-specific transports only after explicit review.

### Argument Template Renderer

Templates may reference only:

- `params.*`
- `deployment.*` allowlisted fields
- `store.*` allowlisted fields
- `shopper.*` allowlisted non-secret fields
- `session.*` allowlisted fields
- `context.*` explicitly provided by Bridge/runtime

Templates may not reference raw secrets. Secrets are consumed only by auth providers.

Renderer requirements:

- render structured JSON, not string-concatenated JSON
- validate rendered arguments against the MCP tool input schema when available
- block missing required params before calling MCP
- record redacted arguments in audit/evidence

### Result Normalizer

Generic result normalization should support:

- raw MCP content capture
- text/image/resource-link content separation
- JSON extraction by JSONPath where configured
- evidence type mapping
- citation/resource link extraction
- error classification
- redaction policy

Default behavior:

- preserve the MCP result as external evidence
- mark all MCP output as untrusted
- do not treat external text as platform instructions

---

## Import And Review Flow

### Creation-Time Discovery

```text
Operator enters MCP server URL/auth profile
  -> Platform calls initialize
  -> Platform calls tools/list
  -> Platform displays tool names, descriptions, and input schemas
  -> Operator selects tools
  -> Operator maps each raw tool to a stable platform actionId
  -> Operator classifies read/write, risk, tier, confirmation, auth, and exposure
  -> Platform generates a private ACTION plugin draft
```

Creation-time discovery can generate a draft, but it cannot publish or install by itself.

### Review-Time Checks

Marketplace review validates:

- no dynamic tool names
- no unapproved auth header names
- no secret literal values
- no unsupported transport
- no dangerous anonymous write action
- tool schema hash is present for imported tools
- risk class and confirmation match write behavior
- package/tier policy is explicit

### Install-Time Verification

```text
Install plugin
  -> resolve server binding and auth profile
  -> initialize
  -> tools/list
  -> verify declared toolName exists
  -> compare input schema hash
  -> compile declared actions only
```

Newly discovered tools are ignored until imported into a new plugin version.

### Release-Time Drift Detection

Drift policy levels:

| Policy | Behavior |
| --- | --- |
| `WARN_ONLY` | Release continues with operator warning. |
| `DISABLE_ACTION` | Action compiles disabled/unavailable. |
| `BLOCK_RELEASE` | Release cannot apply until plugin or server is reconciled. |

Default:

- read-only low-risk tools: `DISABLE_ACTION`
- governed writes: `BLOCK_RELEASE`
- customer/PII tools: `BLOCK_RELEASE`

---

## Governance Model

Marketplace classification remains mandatory:

- `readOnly`
- `anonymousAllowed`
- `requiresConfirmation`
- `riskClass`
- `requiredTier`
- `requiredSession`
- `requiredCustomerScopes`
- `rateLimitPolicyRef`
- `redactionPolicyRef`
- `auditCategory`

Bridge enforces governance immediately before `tools/call`.

This prevents tier bypass if a runtime catalog or external MCP server drifts.

---

## When New MCP Support Requires Code

No code should be needed when all are true:

- transport is already supported
- auth mode is already supported
- server endpoint can be resolved from install/deployment config
- arguments can be rendered with the generic template engine
- results can be normalized with generic response mapping
- governance fits existing risk/session/confirmation policies

Code is still required when any are true:

- new transport is needed
- new auth/token/session flow is needed
- server discovery is non-standard
- action requires custom confirmation UX
- action requires provider-specific state machine behavior
- output needs custom redaction or domain-specific evidence parsing beyond generic mappings
- platform needs to expose a new internal context variable to templates
- action semantics affect billing, subscriptions, payments, legal state, or protected customer data in a new way

This is the practical line between config-driven Marketplace support and product engineering work.

---

## Implementation Sequence

### Phase 0: Contract Lock

Output:

- final manifest extension for `contributions.mcpServers`
- final `execution.mcp` schema
- final auth profile schema
- allowed template context list
- schema-hash and drift-policy rules

Gate:

- existing Shopify MCP manifests still validate and compile
- existing connector HTTP actions still validate and compile

### Phase 1: Generic Marketplace Validation

Add validation for:

- server refs
- transport enum
- endpoint/discovery URL templates
- auth profile refs
- allowed tool names
- argument template object shape
- response mapping object shape
- no secret literal patterns

Gate:

- invalid MCP manifests fail review before install

### Phase 2: Server Binding And Secret Ref Compilation

Compile install config into server binding metadata without exposing secrets.

Gate:

- runtime action catalog contains enough metadata for Bridge to resolve execution
- runtime action catalog contains no secret values

### Phase 3: Generic Bridge Executor

Build `McpActionExecutionService` and route `adapterType=mcp-tool` through it.

Gate:

- one non-Shopify Streamable HTTP MCP tool can execute through config only in tests

### Phase 4: Generic Auth Providers

Implement config-driven providers:

- `NONE`
- `STATIC_BEARER_SECRET`
- `API_KEY_HEADER_SECRET`
- `OAUTH2_CLIENT_CREDENTIALS`

Gate:

- adding a bearer/API-key/client-credentials MCP server does not require new Bridge code

### Phase 5: Import Wizard / Private Draft Generator

Implement operator import:

- initialize
- tools/list
- tool selection
- action ID mapping
- risk/auth/tier classification
- private plugin draft generation

Gate:

- imported tools are drafts, not live actions

### Phase 6: Drift Verification

Add install/release/readiness drift checks.

Gate:

- removed or schema-incompatible tools cannot silently remain executable
- new tools cannot silently appear

### Phase 7: Refactor Shopify To Generic Profiles

Move Shopify Storefront/UCP, Customer Account, and Checkout MCP adapters toward generic server bindings where possible.

Keep provider-specific code only for:

- Shopify install/store binding
- Customer Accounts customer-token/session binding
- Checkout terminal-operation policy
- Shopify-specific readiness probes where the server does not support normal `tools/list`

Gate:

- Shopify remains live-verified after generic executor migration

### Phase 8: Resources And Prompts

After ACTION tools are stable:

- map selected MCP resources to `DATA` plugin contributions
- map selected MCP prompts to `TEMPLATE` plugin contributions
- keep the same import/review/install/drift lifecycle

Gate:

- resources/prompts cannot bypass Marketplace curation

---

## Acceptance Criteria

009.1 is complete when:

- A new Streamable HTTP MCP server with supported auth can be imported into a private Marketplace `ACTION` plugin draft.
- The plugin can be reviewed, published, installed, compiled, and executed without adding provider-specific Bridge code.
- Generic Bridge execution handles endpoint resolution, auth, argument rendering, `tools/call`, result normalization, audit, rate limits, and denial states.
- `tools/list` is used for import and drift verification, not runtime exposure.
- Schema drift blocks, disables, or warns according to the plugin's declared policy.
- Runtime action catalogs remain deterministic and secret-free.
- Existing non-MCP connector HTTP actions still work.
- Existing Shopify MCP actions continue to work after genericization.

---

## Non-Goals

- Do not expose every tool from an MCP server automatically.
- Do not let third-party MCP servers define platform action names at runtime.
- Do not add a top-level `MCP_SERVER` plugin type for tool execution.
- Do not support arbitrary local stdio MCP servers in tenant runtime without sandboxing.
- Do not put secret values into Marketplace manifests or runtime action catalogs.
- Do not treat MCP output as trusted instructions.

---

## Security Requirements

- All MCP output is untrusted external content.
- Tool names are static and declared.
- Tool argument schemas are verified where available.
- Secret refs are resolved only by Bridge/server-side services.
- Confirmation-required tools must complete platform confirmation before `tools/call`.
- Customer/PII tools require session binding and redaction.
- Payment/checkout/irreversible tools require explicit terminal-operation enablement.
- Audit records include serverRef, toolName, actionId, schema hash, redacted args, result status, and caller/session identity.

---

## Open Decisions

- Whether generic import lives only in Platform admin first or also in partner/operator UI.
- Whether plugin drafts should store full observed MCP schemas or only normalized hashes plus selected fields.
- Whether response mapping should use JSONPath, JMESPath, or a restricted internal mapping DSL.
- Whether config-driven OAuth authorization-code flows should be generic enough for non-Shopify customer accounts in the first release.
- How to represent MCP resource links in existing evidence/citation models.

---

## References

- Parent sequence: [009 Shopify MCP-First Implementation Sequence](009_SHOPIFY_MCP_FIRST_IMPLEMENTATION_SEQUENCE.md)
- Generic architecture draft: [Draft 011 Governed MCP Capability Plane](../MCP/Draft-011-GOVERNED_MCP_CAPABILITY_PLANE.md)
- Shopify capability draft: [Draft 009 Shopify Capability Execution Plane](../MCP/Draft-009_SHOPIFY_CAPABILITY_EXECUTION_PLANE.md)
- MCP tools spec: `https://modelcontextprotocol.io/specification/2025-11-25/server/tools`
- MCP Streamable HTTP transport spec: `https://modelcontextprotocol.io/specification/2025-11-25/basic/transports`
