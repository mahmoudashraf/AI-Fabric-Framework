# MCP Integration Strategy for AI Fabric Framework

Status: planning document (2026-04-22)

This document defines how the AI Fabric Framework adopts and integrates with the Model Context Protocol (MCP). It covers what MCP is, how it relates to the existing action framework, what integration looks like, and the build plan.

---

## 1) What MCP Is

Model Context Protocol (MCP) is an open standard created by Anthropic that defines how LLMs discover and interact with external tools, data sources, and services. It is becoming the standard wire protocol for agentic AI systems.

MCP defines three primitives:

- **Tools** — functions the LLM can call (equivalent to actions)
- **Resources** — data the LLM can read (equivalent to knowledge sources)
- **Prompts** — reusable prompt templates the LLM can use

MCP uses JSON-RPC 2.0 over stdio or HTTP+SSE transport.

### 1.1 Why MCP matters

- Claude Desktop, Cursor, Windsurf, and other AI clients natively support MCP
- The ecosystem is growing fast — hundreds of MCP servers already exist
- MCP is becoming the "USB standard" for connecting AI to external systems
- Developers expect MCP compatibility from AI platforms
- Not supporting MCP means being a closed island in an open ecosystem

### 1.2 MCP architecture

```
MCP Host (Claude Desktop, Cursor, custom app)
  └── MCP Client (manages connection)
        └── MCP Server (exposes tools, resources, prompts)
              └── External system (API, database, service)
```

---

## 2) Current Platform Architecture vs MCP

### 2.1 What the platform already has

The platform has a comprehensive action framework that is functionally similar to MCP tools but uses a proprietary protocol:

```
Platform Action Framework              MCP Equivalent
─────────────────────────              ──────────────
@AIAction annotation                   Tool definition
AIActionMetaData                       Tool schema (JSON Schema)
AIActionRegistry                       Tool discovery (list_tools)
ActionConnectorExecutor                Tool execution (call_tool)
AIActionParamSchema                    Tool input schema
ActionResult                           Tool result
ActionAccessMode (READ/WRITE)          No equivalent (MCP has no access modes)
ConfirmationInterception               No equivalent (MCP has no governance)
ConnectorActionCatalogLoader (YAML)    No equivalent (MCP is runtime discovery)
```

### 2.2 What the platform has that MCP does not

- **Access modes** — READ_ONLY, WRITE_ONLY, READ_WRITE per action
- **Confirmation governance** — actions can require user confirmation before execution
- **Counter-offer interception** — governance can propose alternatives before executing
- **Idempotency keys** — write operations are idempotent by design
- **Retry with backoff** — configurable retry for transient failures
- **Batch targets** — parameters can be populated from pinned action results
- **Action facts** — post-execution LLM context generation
- **Multi-tenant scoping** — actions are scoped per deployment/tenant

### 2.3 What MCP has that the platform does not

- **Resources** — structured data access (the platform uses knowledge sources, but not via a protocol)
- **Prompts** — reusable prompt templates exposed via protocol (the platform has prompt management, but internally)
- **Standard transport** — JSON-RPC over stdio/HTTP+SSE (the platform uses custom HTTP)
- **Ecosystem compatibility** — any MCP client can connect (the platform is self-contained)
- **Sampling** — MCP servers can request LLM completions from the host

---

## 3) Integration Strategy

The platform should integrate MCP in two directions:

### 3.1 Platform as MCP Server (expose)

The platform exposes its deployed actions, knowledge sources, and prompts as MCP servers. Any MCP-compatible client can connect to a platform deployment and use its capabilities.

```
Claude Desktop / Cursor / Custom Client
  └── MCP Client
        └── Platform MCP Server Adapter
              ├── Tools → AIActionRegistry (actions)
              ├── Resources → Knowledge Sources (products, pages, policies)
              └── Prompts → Prompt Management (deployment prompts)
```

**Why this matters:**

- Developers can test platform actions from Claude Desktop or Cursor during development
- External AI agents can call platform-deployed actions with governance
- Platform deployments become accessible to any MCP-compatible system
- Opens a new distribution channel — MCP marketplace listings

### 3.2 Platform as MCP Client (consume)

The platform's runtime can discover and call external MCP servers as action sources. If a business already has MCP servers (inventory system, CRM, custom tools), the platform can use them without building a custom bridge service.

```
Platform Runtime
  └── MCP Client Adapter
        └── External MCP Server (inventory, CRM, custom)
              └── tools/list → discovered as platform actions
              └── tools/call → executed via action framework
```

**Why this matters:**

- Businesses with existing MCP servers can connect them to the platform instantly
- Reduces the need to build custom bridge services for every integration
- Developers in the internship program can bring their own MCP servers
- The platform becomes an orchestration layer for MCP tools with added governance

---

## 4) MCP Server Adapter — Detailed Design

### 4.1 Transport

Support both MCP transports:

**Stdio transport** — for local development and CLI tools

```
platform-mcp-server --deployment <deployment-id> --stdio
```

Developer runs this locally, connects Claude Desktop or Cursor to it.

**HTTP+SSE transport** — for remote access

```
https://api.loomai.pro/mcp/<deployment-id>/sse
```

Remote MCP clients connect over HTTP. SSE for server-to-client messages.

### 4.2 Tool exposure

Map `AIActionRegistry` to MCP `tools/list` and `tools/call`:

**tools/list response:**

```json
{
  "tools": [
    {
      "name": "search_products",
      "description": "Search products in the store catalog",
      "inputSchema": {
        "type": "object",
        "properties": {
          "query": {
            "type": "string",
            "description": "Search query"
          },
          "category": {
            "type": "string",
            "description": "Filter by category"
          },
          "maxResults": {
            "type": "integer",
            "description": "Maximum results to return",
            "default": 10
          }
        },
        "required": ["query"]
      }
    },
    {
      "name": "initiate_return",
      "description": "Start a return request for an order",
      "inputSchema": {
        "type": "object",
        "properties": {
          "orderId": {
            "type": "string",
            "description": "Order ID"
          },
          "reason": {
            "type": "string",
            "description": "Reason for return",
            "enum": ["defective", "wrong_item", "changed_mind", "other"]
          }
        },
        "required": ["orderId", "reason"]
      }
    }
  ]
}
```

**tools/call mapping:**

```
MCP tools/call request
  → extract tool name and arguments
  → map to AIActionHandler.execute()
  → apply confirmation governance if required
  → return ActionResult as MCP tool result
```

### 4.3 Schema translation

The platform's `AIActionParamSchema` maps directly to JSON Schema (which MCP uses):

```
AIActionParamSchema          JSON Schema (MCP)
─────────────────            ─────────────────
name                         property key
description                  description
type (STRING)                type: "string"
type (INTEGER)               type: "integer"
type (NUMBER)                type: "number"
type (BOOLEAN)               type: "boolean"
type (ARRAY)                 type: "array" + items
type (OBJECT)                type: "object" + properties
required                     required array
allowedValues                enum
pattern                      pattern
min/max                      minimum/maximum
```

This is nearly 1:1. The adapter is a thin translation layer, not a complex transformation.

### 4.4 Governance in MCP context

MCP has no built-in governance. The platform adds it:

**For read-only actions (accessMode = READ_ONLY):**

- Execute immediately, return result
- No confirmation needed
- Standard MCP behavior

**For write actions (accessMode = WRITE_ONLY or READ_WRITE):**

Option A: **Confirmation via MCP tool result**

```json
{
  "content": [
    {
      "type": "text",
      "text": "This action requires confirmation: Initiate return for Order #1234 (reason: defective). Reply with 'confirm_return' to proceed."
    }
  ],
  "isError": false
}
```

The LLM reads the confirmation message and can call a `confirm_action` tool to proceed.

Option B: **Separate confirmation tools**

Expose confirmation as a tool pair:

```
initiate_return          → returns pending confirmation with ID
confirm_pending_action   → confirms and executes
reject_pending_action    → cancels the pending action
```

Option B is cleaner because it maps governance into MCP's native tool model.

### 4.5 Resource exposure

Map platform knowledge sources to MCP resources:

```json
{
  "resources": [
    {
      "uri": "shopify://products",
      "name": "Product Catalog",
      "description": "All products in the Shopify store",
      "mimeType": "application/json"
    },
    {
      "uri": "shopify://policies",
      "name": "Store Policies",
      "description": "Return policy, shipping policy, privacy policy",
      "mimeType": "text/plain"
    },
    {
      "uri": "shopify://pages",
      "name": "Store Pages",
      "description": "Custom pages and content",
      "mimeType": "text/html"
    }
  ]
}
```

MCP clients can read resources directly without going through actions.

### 4.6 Prompt exposure

Map deployment prompts to MCP prompts:

```json
{
  "prompts": [
    {
      "name": "shopping_companion",
      "description": "Shopping companion system prompt for this deployment",
      "arguments": [
        {
          "name": "storeName",
          "description": "Name of the store",
          "required": true
        }
      ]
    }
  ]
}
```

---

## 5) MCP Client Adapter — Detailed Design

### 5.1 How external MCP servers become platform actions

```
External MCP Server
  └── tools/list → platform discovers available tools
        └── each tool → registered as a ConnectorActionDefinition
              └── schema mapped to AIActionParamSchema
                    └── available in AIActionRegistry
                          └── LLM can call them like any other action
```

### 5.2 Discovery flow

1. Operator configures an MCP server endpoint in deployment settings:

```yaml
mcp:
  servers:
    - name: inventory-system
      transport: sse
      url: https://inventory.example.com/mcp/sse
      accessMode: READ_WRITE
      requiresConfirmation: true
    - name: crm-tools
      transport: sse
      url: https://crm.example.com/mcp/sse
      accessMode: READ_ONLY
      requiresConfirmation: false
```

2. Platform connects to each MCP server
3. Calls `tools/list` to discover available tools
4. Maps each tool to a `ConnectorActionDefinition`
5. Registers via `ConnectorActionsRegistryContributor`
6. Tools appear in the action catalog with operator-defined access modes and governance

### 5.3 Governance overlay

This is the key differentiator. MCP servers have no governance. The platform adds it:

```
External MCP tool (no governance)
  → Platform wraps it with:
      → accessMode (operator decides if it's read/write)
      → requiresConfirmation (operator decides if confirmation needed)
      → counter-offer interception (operator defines rules)
      → idempotency (platform generates keys for writes)
      → audit logging (platform logs all executions)
      → rate limiting (platform enforces per-tenant limits)
```

A raw MCP server that deletes database records becomes a governed action that requires confirmation before executing. The MCP server doesn't change. The platform adds the safety layer.

### 5.4 Refresh and reconnection

- Platform periodically re-calls `tools/list` to discover new or removed tools
- If MCP server disconnects, actions are marked unavailable (not removed)
- Automatic reconnection with exponential backoff
- Health check via MCP `ping` method

---

## 6) Architecture Components

### 6.1 New modules

```
ai-infrastructure-module/
  └── ai-infrastructure-mcp/
        ├── server/
        │   ├── McpServerAdapter.java              Main MCP server implementation
        │   ├── McpToolMapper.java                 Maps actions → MCP tools
        │   ├── McpResourceMapper.java             Maps knowledge → MCP resources
        │   ├── McpPromptMapper.java               Maps prompts → MCP prompts
        │   ├── McpGovernanceHandler.java           Confirmation flow in MCP context
        │   ├── McpStdioTransport.java             Stdio transport
        │   └── McpSseTransport.java               HTTP+SSE transport
        │
        ├── client/
        │   ├── McpClientAdapter.java              Connects to external MCP servers
        │   ├── McpToolDiscovery.java              Discovers tools from MCP servers
        │   ├── McpActionRegistryContributor.java  Registers MCP tools as actions
        │   ├── McpActionHandler.java              Executes MCP tools via platform
        │   └── McpConnectionManager.java          Connection lifecycle management
        │
        └── shared/
            ├── McpSchemaTranslator.java           AIActionParamSchema ↔ JSON Schema
            ├── McpTransportFactory.java           Creates stdio/SSE transports
            └── McpConfiguration.java              Spring configuration
```

### 6.2 Integration points

```
Existing Class                        MCP Integration Point
──────────────────────                ─────────────────────────────
AIActionRegistry                      McpToolMapper reads from here (server)
                                      McpActionRegistryContributor writes here (client)

AIActionHandler                       McpActionHandler implements this (client)

ConnectorActionsRegistryContributor   McpActionRegistryContributor follows same pattern

ActionConnectorExecutor               McpClientAdapter replaces HTTP with MCP protocol

ConfirmationInterceptionContext       McpGovernanceHandler wraps confirmation in tool calls

AIActionParamSchema                   McpSchemaTranslator converts bidirectionally
```

---

## 7) Developer Experience

### 7.1 For internship developers

MCP support means interns can:

```
1. Build an MCP server for any external system (e.g., restaurant POS)
2. Connect it to a platform deployment via configuration
3. The platform adds governance, RAG, and conversational UI automatically
4. Deploy as a product to the end business

They write: one MCP server (~200-500 lines)
They get:   a governed AI assistant with RAG, actions, and embedded UI
```

This dramatically lowers the barrier. Instead of learning the full platform action framework, they learn MCP (an open standard with extensive documentation) and the platform handles the rest.

### 7.2 For the developer network

```
Developer has an MCP server
  → Connects to LoomAI platform
  → Platform adds: governance + RAG + UI + multi-tenant + billing
  → Developer deploys as a product
  → Revenue share: 75% developer, 25% platform
```

MCP becomes the developer on-ramp to the platform.

### 7.3 For testing and development

```bash
# Developer starts local MCP server for their deployment
platform-mcp-server --deployment my-store-dev --stdio

# Connect Claude Desktop (add to claude_desktop_config.json)
{
  "mcpServers": {
    "my-store": {
      "command": "platform-mcp-server",
      "args": ["--deployment", "my-store-dev", "--stdio"]
    }
  }
}

# Now Claude Desktop can:
# - Search products in the store
# - Check order status
# - Initiate returns (with confirmation)
# - Read store policies
# All through MCP protocol with full platform governance
```

---

## 8) Competitive Advantage

### 8.1 What MCP alone does not solve

MCP is a wire protocol. It connects LLMs to tools. But it does not solve:

- **Who can call what?** (access control) → platform solves with deployment scoping
- **Should this action execute?** (governance) → platform solves with confirmation interception
- **What data grounds the answer?** (RAG) → platform solves with knowledge pipeline
- **How does the end user interact?** (UI) → platform solves with embedded surfaces
- **Who pays and how?** (billing) → platform solves with billing integration
- **How do we deploy to production?** (operations) → platform solves with deployment management

The platform is the deployment and governance layer that makes raw MCP tools production-ready.

### 8.2 Positioning

```
Raw MCP Server:     "Here are some tools an LLM can call"
Platform + MCP:     "Here is a governed, deployed, billed AI product 
                     that uses those tools safely"
```

Nobody else in the market combines MCP compatibility with:
- Confirmation governance
- Multi-tenant deployment
- RAG knowledge grounding
- Embedded UI surfaces
- Billing and plan management
- Partner/deployer network

---

## 9) Build Plan

### Phase 1: MCP Server Adapter — Expose (Week 1-2)

Build the server adapter that exposes platform actions as MCP tools.

**Deliverables:**
- McpServerAdapter with stdio transport
- McpToolMapper (actions → MCP tools)
- McpSchemaTranslator (AIActionParamSchema → JSON Schema)
- McpGovernanceHandler (confirmation as tool pairs)
- Works with Claude Desktop locally

**Validation:**
- Connect Claude Desktop to a test deployment
- Call read-only actions (search products)
- Call write actions with confirmation flow
- Verify governance works through MCP

### Phase 2: MCP Resource and Prompt Support (Week 3)

Add resource and prompt exposure.

**Deliverables:**
- McpResourceMapper (knowledge sources → MCP resources)
- McpPromptMapper (deployment prompts → MCP prompts)
- Resources readable from Claude Desktop

**Validation:**
- Read product catalog as MCP resource
- Read store policies as MCP resource
- Use deployment prompts from Claude Desktop

### Phase 3: HTTP+SSE Transport (Week 4)

Add remote transport for production access.

**Deliverables:**
- McpSseTransport
- Endpoint: `api.loomai.pro/mcp/<deployment-id>/sse`
- Authentication via deployment API key
- Rate limiting per tenant

**Validation:**
- Remote MCP client connects to deployed store
- All tools, resources, prompts accessible remotely
- Authentication and rate limiting verified

### Phase 4: MCP Client Adapter — Consume (Week 5-6)

Build the client adapter that consumes external MCP servers.

**Deliverables:**
- McpClientAdapter with SSE transport
- McpToolDiscovery
- McpActionRegistryContributor
- McpActionHandler
- McpConnectionManager
- Deployment configuration for external MCP servers

**Validation:**
- Connect a test MCP server (e.g., filesystem, database)
- Tools appear in platform action catalog
- Governance overlay applied (confirmation required for writes)
- LLM calls external tools through platform with governance

### Phase 5: Developer Experience (Week 7-8)

Polish the developer experience for the internship program and developer network.

**Deliverables:**
- CLI tool: `platform-mcp-server` for local development
- Documentation: how to connect MCP servers to the platform
- Template: starter MCP server project that interns can fork
- Configuration UI: add/manage MCP servers in admin dashboard

**Validation:**
- An intern can build an MCP server, connect it, and deploy a product in one day
- Documentation covers the full flow from MCP server to production deployment

---

## 10) Timeline Summary

```
Week 1-2:   MCP Server Adapter (expose actions as MCP tools)
Week 3:     Resources and prompts exposure
Week 4:     HTTP+SSE remote transport
Week 5-6:   MCP Client Adapter (consume external MCP servers)
Week 7-8:   Developer experience and documentation
```

Total: 8 weeks from start to full MCP support.

This can overlap with Shopify App Store launch — MCP work does not block the product launch and can run in parallel.

---

## 11) What This Enables

After MCP integration, the platform becomes:

```
Before MCP:
  Developer builds bridge service (Java, Spring, deep platform knowledge)
    → Deploys product
    → Limited to developers who know the platform

After MCP:
  Developer builds MCP server (any language, open standard, simple spec)
    → Connects to platform via configuration
    → Platform adds governance, RAG, UI, billing, deployment
    → Deploys product
    → Open to any developer who knows MCP
```

The developer on-ramp goes from "learn a proprietary Java framework" to "build an MCP server in any language." That is the difference between 50 developers using the platform and 5,000.

---

## 12) Relationship to Existing Action Framework

MCP does not replace the existing action framework. It adds a new integration surface:

```
Action Sources (all feed into AIActionRegistry):
  1. @AIAction annotations (Java, existing)
  2. YAML connector definitions (file-based, existing)
  3. MCP servers (protocol-based, new)
```

All three sources register actions in the same registry. The LLM sees one unified action catalog. The governance layer applies uniformly. The execution path differs (direct call vs. HTTP connector vs. MCP protocol) but the result structure is identical.

Developers choose the approach that fits:
- Java developers use annotations (deepest integration)
- Configuration-driven deployments use YAML (no code)
- External tools use MCP (language-agnostic, standard protocol)
