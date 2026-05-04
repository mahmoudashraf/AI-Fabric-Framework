# PLAN-010 — MCP Client and Server Integration

Status: planning document (2026-04-30)

This document defines how AI Fabric integrates with the **Model Context Protocol (MCP)** — both as a client (consuming external MCP servers as Actions and knowledge sources) and as a server (exposing deployment capabilities to MCP-compatible clients like Claude Desktop and Cursor).

The thesis: AI Fabric's existing Actions, DATA, and Prompt abstractions are functionally isomorphic to MCP's Tools, Resources, and Prompts. Adding MCP support is a **translation layer**, not an architectural rebuild. Three adapters land MCP citizenship in both directions and bring your superior governance (confirmation interceptors, allowlists, audit, tenant isolation) to the MCP ecosystem.

---

## 1) Executive Summary

Three deliverables, sequenced:

1. **Direction 1 — MCP Client Adapter**: a `MCPServerConnector` that connects to any MCP server (stdio, SSE, HTTP), discovers its tools/resources/prompts, and dynamically registers them as native Actions, knowledge sources, and prompt templates in the runtime. Routes all execution through existing confirmation interceptors and audit. **Unlocks the entire public MCP ecosystem (hundreds of servers) with one piece of code.**
2. **Direction 2 — MCP Server Adapter**: an MCP server endpoint per deployment that exposes the deployment's actions, knowledge sources, and prompts to external MCP clients. **Lets Claude Desktop, Cursor, or partner agents drive a tenant's Loom deployment directly.**
3. **Direction 3 — MCP Plugin Type in the Marketplace**: a new declarative plugin sub-type (`MCP_SERVER` reference) that lets plugin authors publish "an MCP server URL" as a plugin without writing any per-plugin code on AI Fabric's side. **Marketplace absorbs the entire MCP ecosystem with zero per-plugin engineering.**

Outcome: AI Fabric becomes a first-class MCP citizen — both consumer and producer — while keeping the governance, audit, and multi-tenancy that vanilla MCP servers lack.

---

## 2) Why This Plan Exists

### 2.1 Strategic context

By 2026, MCP is the de facto tool/context protocol across the major AI ecosystems (Anthropic, OpenAI, Cursor, Claude Desktop, agent frameworks). When a developer asks "do you support MCP?", the answer must be a clean yes. Anything else reads as out-of-step.

### 2.2 You are closer than you think

The runtime's existing abstractions map directly onto MCP:

| MCP concept | AI Fabric equivalent | Gap |
|---|---|---|
| Tools (callable functions) | Actions | Wire protocol only |
| Resources (read-only data) | DATA plugins / knowledge sources | Wire protocol only |
| Prompts (templates) | Prompt management with hot apply | Wire protocol only |
| Sampling (server callback to client LLM) | Not directly supported | Minor — defer |
| Server discovery / handshake | Plugin manifest / marketplace | Wire protocol only |
| Transport (stdio, SSE, HTTP) | Connector + relay | Implement adapters |

### 2.3 You are ahead in places that matter

Vanilla MCP servers do not standardize:

- Confirmation interception (your `ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md` is more advanced)
- Per-deployment / per-tenant tool allowlists
- Audit logs of tool invocation with structured failure capture
- Mode-driven tool selection (Resolver / Thinker / read-only)
- Tenant isolation across tool invocations

These become differentiators once MCP wire protocol support is in place. Position externally as: **"AI Fabric is MCP-compatible — and adds the governance MCP doesn't standardize."**

### 2.4 Why not defer

- Brand cost of saying "MCP support coming soon" is real in 2026
- Engineering cost is bounded (~6 weeks total for all three directions)
- Direction 1 alone is 2–3 weeks and unlocks the entire public ecosystem — exceptional ROI for the factory thesis in PLAN-008 §4

---

## 3) Scope

In scope:

- MCP Client adapter: stdio, SSE, HTTP transports
- Dynamic Action registration from MCP `tools/list`
- Resource subscription mapped to knowledge sources
- Prompt template ingestion
- Confirmation interceptor and audit integration on the consumed side
- MCP Server adapter exposing deployment Actions, Resources, Prompts
- Per-deployment authentication for MCP server endpoint
- Marketplace plugin sub-type `MCP_SERVER` for declarative MCP server references
- Documentation: how to consume an MCP server, how to expose a deployment, how to publish an MCP plugin

Out of scope:

- MCP sampling (server-initiated client LLM calls) — defer to a follow-up plan; minor demand
- MCP roots feature (filesystem rooting) — implement only when a customer needs it
- Building first-party MCP servers (e.g., a Loom-specific MCP server for Shopify operations) — defer to vertical product work
- Multi-server orchestration / chaining — out of v1; per-server is enough

---

## 4) MCP Concept-to-Action Mapping

### 4.1 Tool → Action

An MCP tool is a callable function with a JSON Schema describing inputs. Maps to an Action with:

- name = `mcp:{server-id}:{tool-name}`
- input schema = MCP tool's `inputSchema`
- description = MCP tool's `description`
- handler = HTTP/SSE/stdio call to the MCP server's `tools/call` endpoint
- confirmation policy = inherited from deployment's default for MCP-sourced actions (configurable per server, per tool)

### 4.2 Resource → Knowledge source

An MCP resource is a URI pointing at read-only data. Maps to a DATA plugin entry with:

- URI scheme preserved (e.g., `file://`, `https://`, `postgres://`)
- read handler = MCP server's `resources/read` endpoint
- subscription handler = MCP server's `resources/subscribe` for change notifications
- knowledge source ID = `mcp:{server-id}:{resource-uri}`

### 4.3 Prompt → Prompt template

An MCP prompt is a templated message-list with parameters. Maps to a prompt template with:

- template ID = `mcp:{server-id}:{prompt-name}`
- parameter schema = MCP prompt's `arguments`
- render handler = MCP server's `prompts/get` endpoint with parameter substitution

### 4.4 Sampling — gap to acknowledge

MCP allows servers to ask the client to make an LLM call back. Your runtime doesn't currently support this callback model. Defer:

- v1 of the client adapter does not support sampling
- if an MCP server's tool requires sampling, the tool will fail gracefully with a clear error
- log occurrences; if sampling demand emerges, add in a v2

### 4.5 Authorization — additive

Every MCP-sourced capability passes through the same allowlist and confirmation interceptors as native actions. The translation does not bypass governance.

---

## 5) Direction 1 — MCP Client Adapter

### 5.1 Architecture

```
RuntimeBootstrap
  └─ MCPClientManager (singleton)
       ├─ MCPServerRegistry (config: which MCP servers to mount)
       ├─ MCPTransportFactory (stdio | sse | http)
       ├─ MCPHandshake (capability negotiation)
       └─ For each configured MCP server:
             ├─ MCPServerConnector (one per server)
             │    ├─ tool list cache (refresh on `notifications/tools/list_changed`)
             │    ├─ resource list cache
             │    └─ prompt list cache
             ├─ DynamicActionRegistrar (registers each tool as an Action)
             ├─ DynamicKnowledgeSourceRegistrar
             └─ DynamicPromptTemplateRegistrar
```

### 5.2 Transports

Implement three transports behind a common interface:

- **stdio**: spawn a subprocess (e.g., `npx @modelcontextprotocol/server-filesystem /data`), JSON-RPC over stdin/stdout. Use Apache Commons Exec or ProcessBuilder. Stream-buffered, line-delimited.
- **SSE (Server-Sent Events)**: HTTP POST for requests, SSE for server-pushed events. Use OkHttp or Reactor Netty.
- **HTTP**: plain JSON-RPC over HTTP for simple servers. Use the same HTTP client library as the runtime's existing connectors.

Transport selection is per-server config; default is HTTP for remote servers, stdio for local subprocess servers.

### 5.3 Handshake and capability negotiation

On connector startup:

1. send MCP `initialize` with client capabilities
2. receive server capabilities
3. record what the server supports (tools? resources? prompts? subscription? logging?)
4. only register handlers for capabilities the server actually exposes

### 5.4 Dynamic registration

After handshake:

1. call `tools/list`, `resources/list`, `prompts/list`
2. for each tool: register an Action with the runtime's action registry, namespaced by server ID
3. for each resource: register a knowledge source entry; for subscribable resources, set up the subscription
4. for each prompt: register a prompt template

Re-fetch on `notifications/tools/list_changed` and similar change notifications.

### 5.5 Confirmation interceptor integration

Every registered Action passes through:

1. deployment's action allowlist (deny if not allowed)
2. confirmation interceptor chain (per `ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md`)
3. audit log emission (before and after)
4. structured failure capture (per ADR-0007) on error

The MCP-sourced Action handler is just one more handler type; existing infrastructure applies unchanged.

### 5.6 Authentication

MCP servers may require auth (Bearer token, OAuth, custom headers). Per-server config in deployment:

- static token in deployment secrets
- OAuth client credentials (token refresh handled by transport)
- custom header mapping

No auth token leaves the deployment; tokens are tenant-scoped in the runtime's secret store.

### 5.7 Per-tenant configuration

A deployment configures its MCP server set in the deployment template:

```json
{
  "mcp_servers": [
    {
      "id": "github",
      "transport": "http",
      "url": "https://mcp.github.com",
      "auth": {"type": "bearer", "secret_ref": "GITHUB_MCP_TOKEN"},
      "tool_allowlist": ["create_issue", "list_repos"],
      "default_confirmation_policy": "required"
    },
    {
      "id": "filesystem",
      "transport": "stdio",
      "command": ["npx", "@modelcontextprotocol/server-filesystem", "/data"],
      "default_confirmation_policy": "auto"
    }
  ]
}
```

### 5.8 Failure modes

- MCP server unreachable: tool calls fail with clear `MCPServerUnavailable` error; runtime continues operating with non-MCP capabilities
- Schema mismatch: tool registration fails with structured error; tool not exposed
- Long-running tool: timeout configurable per tool; default 30s; bulkhead limits concurrent calls per server

---

## 6) Direction 2 — MCP Server Adapter

### 6.1 Architecture

Each deployment exposes one MCP server endpoint. The MCP server adapter:

- accepts MCP `initialize` and negotiates capabilities (tools, resources, prompts)
- on `tools/list`: enumerates the deployment's allowed Actions (subject to caller's authorization scope)
- on `tools/call`: invokes the Action through the same execution pipeline (allowlist + confirmation + audit)
- on `resources/list` / `resources/read`: reads from the deployment's knowledge sources
- on `prompts/list` / `prompts/get`: reads from prompt management

### 6.2 Transport

HTTP/SSE only for v1; stdio is not relevant when the server is hosted (not co-resident with the client).

Endpoint: `https://tenant-{id}.runtime.loom.ai/mcp`

### 6.3 Authentication and authorization

- caller authenticates via an MCP-specific token issued by the platform (separate from the runtime's other tokens)
- tokens are scoped: which Actions the caller may invoke, which Resources may read, which Prompts may fetch
- token lifecycle managed via platform UI (issue, list, revoke)
- audit logs include the caller's token ID

### 6.4 Multi-tenancy considerations

Each deployment has its own MCP server endpoint. No cross-tenant tool exposure. The endpoint URL is tenant-scoped; tokens are tenant-scoped; capabilities are deployment-config-scoped.

### 6.5 Confirmation policies on the server side

Some Actions require human confirmation in the runtime. When invoked via MCP:

- if the calling client supports MCP `elicitation` (a future MCP spec feature), use it
- otherwise: return a structured "requires confirmation" error pointing the caller to the deployment's confirmation UI
- never auto-bypass confirmation; this is a governance hard-line

### 6.6 Discovery and documentation

Each deployment's MCP endpoint URL is published in:

- the platform UI ("Connect this deployment to Claude Desktop")
- deployment metadata API
- partner portal (so agencies can integrate merchant deployments with their own MCP-enabled tools)

---

## 7) Direction 3 — MCP Plugin Type in Marketplace

### 7.1 Manifest extension

Add `MCP_SERVER` as a sub-type of plugin (alongside ACTION, DATA, INFERENCE_PROFILE, TEMPLATE):

```json
{
  "name": "github-mcp",
  "type": "MCP_SERVER",
  "version": "1.0.0",
  "mcp": {
    "transport": "http",
    "url_template": "https://mcp.github.com",
    "default_auth": {"type": "bearer", "required_secrets": ["GITHUB_TOKEN"]},
    "advertised_tools": ["create_issue", "list_repos"],
    "advertised_resources": ["repo://*", "issue://*"],
    "default_confirmation_policy": "required"
  }
}
```

### 7.2 Installation flow

When a platform admin installs an MCP_SERVER plugin into a deployment:

1. plugin manifest is added to the deployment template
2. required secrets are surfaced for admin to supply
3. on next runtime config reload, `MCPServerConnector` mounts the server
4. tools/resources/prompts appear in the deployment's capability list

### 7.3 Verification and trust

Same Verified Plugin program (per PLAN-006 §5.4) applies:

- MCP_SERVER plugins go through the same security review as ACTION plugins
- declared tools and resources must match what the server actually exposes (verification step)
- "Verified" badge for vetted MCP servers
- unverified MCP servers are still installable but flagged in the marketplace UI

### 7.4 Why this is high-leverage

One MCP_SERVER plugin = entire MCP server's surface available in the marketplace. Hundreds of public MCP servers can be onboarded as plugins with **zero per-server engineering on AI Fabric's side**. The marketplace becomes a curation and trust layer over the MCP ecosystem.

This is the purest expression of the factory thesis: a translation layer that absorbs an external ecosystem.

---

## 8) Implementation Steps

### 8.1 Wave A — MCP Client adapter v1 (weeks 1–3)

1. add MCP client library or implement minimal JSON-RPC client (consider `io.modelcontextprotocol:mcp-java` if mature, or implement directly)
2. implement stdio transport with subprocess management
3. implement HTTP transport
4. implement handshake and capability negotiation
5. implement `tools/list` and dynamic Action registration
6. integrate with confirmation interceptor chain
7. integrate with audit pipeline
8. unit + integration tests against the reference filesystem MCP server
9. integration test against one real public MCP server (e.g., GitHub)

### 8.2 Wave B — Resources and Prompts (weeks 3–4)

10. implement `resources/list`, `resources/read`, subscription
11. map resources to knowledge source registry
12. implement `prompts/list`, `prompts/get`
13. integrate with prompt management hot apply
14. integration tests for resources and prompts

### 8.3 Wave C — SSE transport and per-tenant config (weeks 4–5)

15. implement SSE transport
16. add MCP server config to deployment template schema
17. surface MCP server config in platform UI (admin can add/remove MCP servers)
18. secret management for MCP server credentials
19. tool allowlist and per-server confirmation policy in UI

### 8.4 Wave D — MCP Server adapter (weeks 5–7)

20. design MCP server endpoint (HTTP + SSE) at `/mcp` on each runtime
21. implement `initialize`, `tools/list`, `tools/call` for the deployment's exposed Actions
22. implement `resources/list`, `resources/read` for knowledge sources
23. implement `prompts/list`, `prompts/get` for prompt templates
24. implement MCP-specific token issuance and validation
25. surface "Connect to Claude Desktop / Cursor" flow in deployment UI
26. test end-to-end with Claude Desktop

### 8.5 Wave E — Marketplace MCP plugin type (week 7–8)

27. extend plugin manifest schema with `MCP_SERVER` type
28. implement plugin installation handler that registers an MCP server in the deployment
29. update marketplace UI to show MCP_SERVER plugins
30. document plugin author guide for MCP_SERVER plugins
31. publish 3–5 reference MCP_SERVER plugins (filesystem, GitHub, Slack, Notion, Stripe) as exemplars

### 8.6 Wave F — Documentation and positioning (week 8)

32. developer docs: "Adding an MCP server to your deployment"
33. developer docs: "Connecting Claude Desktop to your deployment"
34. plugin author docs: "Publishing an MCP server as a plugin"
35. marketing page: "AI Fabric ⊕ MCP" — position the governance differentiator
36. blog post and announcement

---

## 9) Acceptance Criteria

This plan is complete when:

- a deployment can be configured to consume at least three different public MCP servers (e.g., filesystem, GitHub, Slack) and their tools appear as native Actions
- MCP-sourced Actions pass through confirmation interceptors and audit identically to native Actions
- a deployment exposes its actions as an MCP server reachable by Claude Desktop and Cursor, with tenant-scoped auth tokens
- the marketplace lists at least one MCP_SERVER plugin and platform admins can install it through the standard plugin installation flow
- developer docs and plugin author docs are published
- a public-facing announcement frames AI Fabric as MCP-compatible with the governance differentiator
- all three transports (stdio, SSE, HTTP) work in production
- per-tenant MCP server credentials are stored in the runtime secret store, not in plugin manifests

---

## 10) Dependencies

- mature Java MCP client library (or willingness to implement minimal JSON-RPC + MCP message types directly — small surface, ~1500 LOC)
- existing `ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md` interceptor chain (already in place)
- existing audit pipeline (already in place)
- existing prompt management (already in place per `PROMPT_MANAGEMENT_HOT_APPLY_PLAN.md`)
- existing knowledge source / DATA plugin registry (already in place)
- platform UI extension for MCP server configuration (small front-end work)
- Claude Desktop and Cursor available for end-to-end testing (free)

No engineering dependencies on PLAN-006 / 007 / 008 / 009. This plan is parallelizable.

---

## 11) Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| MCP wire protocol evolves and breaks the adapter | Medium | Pin to a specific MCP spec version; bump deliberately; integration tests against reference servers |
| Java MCP client library immature | Medium | Implement directly if needed — message surface is small |
| Stdio transport fragility (subprocess management, zombie processes) | Medium | Robust process lifecycle: kill on disconnect, reap zombies, restart on failure |
| Sampling demand emerges and v1 doesn't support it | Low | Document gap clearly; v2 plan when demand appears |
| MCP server with malicious tools (security risk) | Medium | Verified plugin program; clear "unverified" warning in UI; tool allowlist gates execution |
| Per-tenant credentials leak across tenants | High if mishandled | Strict tenant scoping in secret store; integration tests asserting isolation |
| MCP server returns content that confuses the LLM (prompt injection) | Medium | Treat MCP resource content as untrusted; sanitize; respect existing prompt guardrails |
| Long-running MCP tool calls exhaust connection pool | Medium | Bulkhead per server; timeout per tool; backpressure |
| Confirmation policy bypass via MCP | Low | Hard-coded: every MCP-sourced Action goes through interceptor chain; security review of execution path |

---

## 12) Estimated Effort

- Wave A MCP Client v1: 3 weeks (1 backend engineer)
- Wave B Resources and Prompts: 1.5 weeks
- Wave C SSE transport and per-tenant config: 1.5 weeks (includes UI work)
- Wave D MCP Server adapter: 2.5 weeks
- Wave E Marketplace MCP plugin type: 1 week
- Wave F Documentation and positioning: 1 week

Total elapsed time: ~8–10 weeks. Total person-effort: ~9–10 person-weeks (1 engineer + 0.3 of a frontend engineer for UI work in Waves C/D).

Minimum viable subset: Waves A + B alone (~5 weeks) gets you "AI Fabric supports MCP as a client" with full ecosystem unlock. Wave D is the second-priority deliverable for partner positioning. Waves E + F are polish and positioning.

---

## 13) Sequencing With Other Plans

### 13.1 Independence

PLAN-010 has no engineering dependencies on PLAN-006 (pricing/licensing), PLAN-007 (partner channel), PLAN-008 (operating model), or PLAN-009 / hybrid topology. It can run on its own track.

### 13.2 Synergies

- **PLAN-007 §7 partner certification** — consider adding "MCP plugin authoring" as a fellowship-track specialty (per the proposed Loom Builder Fellowship).
- **PLAN-008 §6.3 missing blocks** — add MCP support as a missing block under "channel + product polish."
- **Marketplace plan** (existing future-work folder) — extend with MCP_SERVER plugin type per Wave E.
- **Phase 4 of PLAN-008** (self-serve platform tier) — MCP support is a strong driver for build-partner-curious developers; ship before opening the platform tier publicly.

### 13.3 Recommended sequencing relative to other work

- Wave A + B can run in parallel with PLAN-007 Wave A foundations
- Wave D should ship before any partner-channel marketing emphasizes "third-party tool integration"
- Wave E should ship alongside or shortly after marketplace v1 (whatever the latter's plan is)
- Public announcement (Wave F) coordinated with PLAN-006 positioning lock-in to maintain consistent external messaging

### 13.4 Future plans this enables

- a follow-up `PLAN-011-MCP_SAMPLING_AND_SERVER_INITIATED_FLOWS` once sampling demand emerges
- a follow-up `PLAN-012-FIRST_PARTY_MCP_SERVERS_FOR_VERTICAL_PRODUCTS` to expose Loom Companion's Shopify-specific operations as a curated MCP server consumed by external clients

---

## 14) Strategic Positioning

### 14.1 The external story

Lead with the differentiator, not the catch-up:

> "AI Fabric is MCP-compatible — and adds the governance, audit, and tenant isolation MCP doesn't standardize. Plug in any MCP server, expose any deployment to Claude Desktop, and keep the controls enterprises require."

### 14.2 What this unlocks commercially

- removes the "do you support MCP?" objection in sales conversations
- makes Loom Companion plugin development trivially accessible to anyone who can run an MCP server
- positions AI Fabric as the *governed* MCP platform, not just a consumer
- accelerates partner adoption by reducing the perceived integration cost
- supports the factory thesis (PLAN-008 §4): one translation layer absorbs an entire external ecosystem

### 14.3 What this does not do

- does not replace the need for high-quality first-party Actions for vertical products like Loom Companion
- does not eliminate the value of curated, verified plugins
- does not make AI Fabric an "MCP-only" platform — native Actions remain the primary path for vertical-specific work

MCP is one input format among several. Its strategic value is ecosystem reach, not architectural primacy.
