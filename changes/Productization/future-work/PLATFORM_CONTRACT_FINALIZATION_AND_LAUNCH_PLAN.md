# Platform Contract Finalization and Launch Plan

Status: execution plan (2026-04-07, revised)

This document defines the maturation sprint before market entry. The goal is to ship a platform that is architecturally complete — not a rushed MVP that accumulates backward-compatibility debt from day one.

---

## 1) Guiding Principles

> Build a mature platform. Enter the market once, correctly.

With Codex-accelerated development (137K lines in one week), the traditional "ship MVP then iterate" calculus does not apply. Building features that would take a team months takes days. The cost of building them now is low. The cost of retrofitting them after customers are live is high.

**Principle 1: Lock every customer-facing contract before first customer.**
Once a customer pastes a widget embed code, the `data-*` attributes are frozen. Once they write an action config YAML, the schema is frozen. Once they connect a Zapier zap to a webhook payload, the shape is frozen.

**Principle 2: Build the architecture right, not just the surface.**
A correct contract on top of a broken architecture still breaks. The chat proxy lesson: even if the URL shape is right, routing all real-time chat traffic through a single proxy is structurally wrong. Getting the contract right means getting the architecture right.

**Principle 3: Additive features are only those that add NEW surfaces.**
Features that extend existing surfaces (action routing, policy engine, session model) are NOT safely additive — they reshape the contract. Build them now. Features that create entirely new surfaces (marketplace, AutoTrader plugin) are truly additive — those can wait.

---

## 2) Permanent Contracts to Lock Down

### 2.1 Deployment Identity and Connection Model

**Why permanent:** every customer's widget, DNS, integrations, and bookmarks point to this.

**Critical architecture decision: resolve-once, connect-direct.**

Chat is real-time. Routing every chat message through a proxy service is structurally wrong — it adds latency to every message, holds open connections during LLM streaming, and makes the proxy a bottleneck and single point of failure for all live conversations. The correct pattern:

1. Widget calls platform ONCE to resolve the deployment handle to a runtime URL
2. Widget connects DIRECTLY to the runtime instance for all chat messages
3. Platform is never in the chat path after resolution

```
RESOLVE (one call per session — hits platform):
  POST https://api.loomai.pro/v1/resolve
  Authorization: Bearer dpk_live_...
  Body: { "deployment": "paul-rigby-sales" }
  
  Response: {
    "runtime_url": "https://deploy-7f3a.up.railway.app",
    "session_token": "st_abc123...",
    "expires_in": 3600
  }

CHAT (all messages — direct to runtime, platform not involved):
  POST https://deploy-7f3a.up.railway.app/chat
  Authorization: Bearer st_abc123...
  Body: { "message": "Do you have a blue BMW 3 series?" }
  
  Response: streamed directly from runtime
```

**Customer-facing URLs (via Railway wildcard `*.loomai.pro`):**

```
Customer subdomains:
  https://paulrigby.loomai.pro          ← customer landing/widget page
  https://paulrigby.loomai.pro/sales    ← specific deployment scope

Platform services:
  https://api.loomai.pro/v1/...         ← platform API + resolve endpoint
  https://app.loomai.pro                ← platform admin UI
  https://widget.loomai.pro/v1/loader.js ← widget script
```

**Widget embed:**

```html
<script
  src="https://widget.loomai.pro/v1/loader.js"
  data-deployment="{handle}"
  data-key="{api-key}"
  ...
></script>
```

The widget loader calls `/v1/resolve` once, receives the runtime URL and session token, then connects directly. If the runtime URL changes (deployment rebound, cloud migration, rollback), the next session resolves to the new URL. Active sessions continue on the old instance until they end naturally.

**Design decisions:**
- Handle format: lowercase alphanumeric + hyphens, max 64 chars, globally unique
- Handle is immutable once created (rename = create new + migrate)
- Session token: short-lived (1 hour), scoped to deployment + session, validated by runtime
- API key never sent to runtime — only used for resolve. Runtime trusts session tokens.
- Railway wildcard `*.loomai.pro` for customer subdomains (unlimited, no per-customer DNS config)
- Custom domains (e.g. `chat.paulrigby.co.uk`) deferred — add Cloudflare Workers when needed

### 2.2 API Key Format

**Why permanent:** every SDK, integration guide, and customer's code references this format.

```
Format:     dpk_{environment}_{32-char-random-hex}

Examples:
  dpk_live_a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4    (production)
  dpk_test_f6e5d4c3b2a1f6e5d4c3b2a1f6e5d4c3    (testing)
  dpk_dev_00112233445566778899aabbccddeeff       (development)

Environments:  live | test | dev

Storage:    SHA-256 hash in database, raw key shown once at creation
Validation: prefix check + hash lookup
Header:     Authorization: Bearer dpk_live_...
```

**Key lifecycle:**
- Generate on deployment creation (one live + one test key)
- Revoke and regenerate without changing deployment handle
- Rate limit per key
- Separate keys per environment (live key cannot hit test deployment)

### 2.3 Widget Embed API

**Why permanent:** pasted into customer websites. Every attribute shipped becomes a commitment.

```html
<script
  src="https://widget.loomai.pro/v1/loader.js"
  data-deployment="paul-rigby-sales"
  data-key="dpk_live_a1b2c3d4..."
  data-theme="light"
  data-position="bottom-right"
  data-greeting="Hi! How can I help?"
  data-language="en"
  data-primary-color="#1a73e8"
  data-show-branding="true"
></script>
```

**Attributes to include at launch:**

| Attribute | Required | Values | Purpose |
|---|---|---|---|
| `data-deployment` | Yes | Handle string | Identifies which deployment to connect |
| `data-key` | Yes | API key | Authenticates the widget |
| `data-theme` | No | `light` / `dark` / `auto` | Widget colour scheme |
| `data-position` | No | `bottom-right` / `bottom-left` | Widget button position |
| `data-greeting` | No | String | Initial greeting message |
| `data-language` | No | ISO 639-1 code | Widget UI language |
| `data-primary-color` | No | Hex colour | Brand colour |
| `data-show-branding` | No | `true` / `false` | Show "Powered by Loom AI" |
| `data-open` | No | `true` / `false` | Start with widget open |

**Do NOT include yet** (add later as additive):
- `data-router` (deployment router — additive feature)
- `data-user-id` (authenticated user tracking — add with proper auth)
- `data-cart-integration` (Shopify-specific — add with Shopify app)

### 2.4 Action Configuration Schema

**Why permanent:** customers write these configs. Changing the schema requires migration tooling.

```yaml
# Deployment action configuration schema v1
actions:

  # Minimal action (backward compatible with current format)
  check-order-status:
    description: "Check the status of a customer order"
    path: "/orders/{{orderId}}/status"
    method: GET
    parameters:
      orderId:
        type: string
        required: true
        description: "The order ID to look up"

  # Full action with multi-upstream + auth + policies
  create-return:
    description: "Create a return request for an order"
    
    # URL resolution (pick one)
    upstream: "https://orders-api.customer.com"    # per-action upstream
    # url: "https://absolute-url.com/endpoint"     # OR absolute URL
    path: "/returns"                                # combined with upstream
    method: POST
    
    # Per-action authentication
    auth:
      type: bearer                     # bearer | api-key | basic | oauth2 | none | inherit
      token: "${ORDERS_API_TOKEN}"     # resolved from deployment secrets
    
    # Action parameters
    parameters:
      orderId:
        type: string
        required: true
      reason:
        type: string
        required: true
        enum: ["defective", "wrong-item", "changed-mind", "other"]
      refundAmount:
        type: number
        required: false
    
    # Pre-execution policies
    pre-policies:
      - type: confirmation
        message: "Create return for order {{orderId}} ({{reason}})?"
      - type: rate-limit
        max: 10
        window: 1h
        per: session
      - type: input-guard
        rules:
          - field: refundAmount
            max: 500
            exceed-action: block
            message: "Refund amount exceeds maximum. Please contact support."
    
    # Post-execution policies
    post-policies:
      - type: webhook
        url: "https://merchant.com/webhooks/returns"
        method: POST
        retry: 3
      - type: audit
        level: full

  # External API action (Calendly example)
  book-appointment:
    description: "Book a test drive appointment"
    url: "https://api.calendly.com/v2/scheduling_links"
    method: POST
    auth:
      type: bearer
      token: "${CALENDLY_TOKEN}"
    parameters:
      eventType:
        type: string
        required: true
      dateTime:
        type: string
        required: true
        format: iso8601
    pre-policies:
      - type: confirmation
        message: "Book {{eventType}} for {{dateTime}}?"
    post-policies:
      - type: webhook
        url: "${ZAPIER_WEBHOOK_URL}"
```

**Schema rules:**
- `upstream`, `url`, `auth`, `pre-policies`, `post-policies` are all optional
- Omitting them falls back to global connector defaults (backward compatible)
- All `${VAR}` references resolve from deployment-scoped secrets
- Policy types are extensible — new types can be added without schema change
- Unknown policy types are ignored (forward compatible)

### 2.5 Webhook Payload Schema

**Why permanent:** Zapier zaps, customer integrations, and automation workflows parse this shape.

```json
{
  "version": "1",
  "event": "action.executed",
  "timestamp": "2026-04-07T12:00:00.000Z",
  "deployment": {
    "handle": "paul-rigby-sales",
    "customer": "paul-rigby-group"
  },
  "action": {
    "name": "create-return",
    "method": "POST",
    "status": "success",
    "http_status": 201
  },
  "input": {
    "orderId": "ORD-12345",
    "reason": "defective"
  },
  "output": {
    "returnId": "RET-67890",
    "status": "created"
  },
  "conversation": {
    "session_id": "sess_abc123",
    "message_id": "msg_def456"
  },
  "metadata": {
    "confidence": 0.95,
    "processing_time_ms": 342,
    "model_used": "gpt-4o-mini"
  }
}
```

**Versioning:** the `"version": "1"` field allows future schema evolution. Customers can check version and handle accordingly.

**Event types to define at launch:**

| Event | Trigger |
|---|---|
| `action.executed` | Action completed successfully |
| `action.failed` | Action execution failed |
| `action.confirmed` | User confirmed a pending action |
| `action.rejected` | User rejected a pending action |
| `conversation.started` | New conversation session began |
| `conversation.ended` | Conversation session ended |

### 2.6 Embedding Dimension Decision

**Why permanent:** changing dimensions requires re-indexing every customer's entire knowledge base.

**Decision: 384 dimensions (bge-small-en-v1.5, ONNX in-process)**

Rationale:
- $0 cost — no external service dependency at launch
- Same dimensions as current MiniLM — no migration needed
- Good enough quality (MTEB ~62, up from ~56)
- Higher dimensions (1024) available later as an opt-in upgrade path (new collection, re-index)
- The vector service already supports multiple dimension sizes per provider

When a customer upgrades from free (384d) to paid (1024d), the platform triggers a re-index into a new collection. Old collection remains until migration completes. No downtime.

### 2.7 Platform API URL Structure

**Why permanent:** SDKs, documentation, partner integrations all reference these paths.

```
# ── Platform API (api.loomai.pro) ──

# Resolve — the only endpoint the widget calls
POST   /api/v1/resolve                                    ← returns runtime URL + session token

# Customer and tenant management
GET    /api/v1/customers
POST   /api/v1/customers
GET    /api/v1/customers/{customerId}
GET    /api/v1/customers/{customerId}/tenants
POST   /api/v1/customers/{customerId}/tenants
GET    /api/v1/customers/{customerId}/tenants/{tenantId}
GET    /api/v1/customers/{customerId}/tenants/{tenantId}/deployments
POST   /api/v1/customers/{customerId}/tenants/{tenantId}/deployments

# Deployment management
GET    /api/v1/deployments/{handle}
PUT    /api/v1/deployments/{handle}
DELETE /api/v1/deployments/{handle}
POST   /api/v1/deployments/{handle}/publish
POST   /api/v1/deployments/{handle}/release
POST   /api/v1/deployments/{handle}/go-live

# Deployment identity and binding
POST   /api/v1/deployments/{handle}/bind
POST   /api/v1/deployments/{handle}/rollback
GET    /api/v1/deployments/{handle}/bindings
GET    /api/v1/deployments/{handle}/bindings/history

# Keys
POST   /api/v1/deployments/{handle}/keys
DELETE /api/v1/deployments/{handle}/keys/{keyId}

# Vectorization
POST   /api/v1/deployments/{handle}/knowledge/sync
GET    /api/v1/deployments/{handle}/knowledge/status

# Future: marketplace (truly additive — new surface, no existing contracts affected)
GET    /api/v1/marketplace/plugins
POST   /api/v1/deployments/{handle}/plugins/{pluginId}/install

# ── Runtime API (direct connection, per-instance) ──
# These endpoints live on the runtime instance, NOT on the platform.
# The widget connects here directly after resolve.

POST   /chat                                               ← main chat endpoint
GET    /health                                             ← runtime health
GET    /actions                                            ← available actions for this deployment
POST   /session/validate                                   ← validate session token
```

**Versioning:** `/api/v1/` prefix on platform API. Runtime API is unversioned (the runtime instance IS the version — deployment binding controls which version serves).

**Key separation:** platform API and runtime API are on different hosts. Platform API is on `api.loomai.pro`. Runtime API is on the instance URL returned by resolve. Chat traffic never touches the platform.

### 2.8 Tenant/Customer/Deployment Hierarchy

**Already locked in Platform-V4:**

```
PlatformCustomerEntity
  └── PlatformTenantEntity (customerId FK)
       └── DeploymentEntity (tenantId FK)
            ├── Knowledge (vector collections)
            ├── Actions (config YAML)
            ├── Prompts
            ├── Secrets (provider bindings)
            └── Keys (API keys)
```

**No changes needed.** This hierarchy is correct and matches the API URL structure.

---

## 3) Build Now vs. Build Later — Corrected Assessment

The previous version of this plan labelled too many features as "additive/defer." That was wrong. Features that extend existing contracts or reshape how the platform fundamentally works are NOT safely additive — they carry backward compatibility risk. Only features that create entirely new, independent surfaces are truly additive.

### 3.1 Build Before Market Entry

These features either shape permanent contracts or define core architecture that is painful to change later:

| Feature | Why It Must Be Built Now |
|---|---|
| **Resolve-once, connect-direct** | Core connection architecture. Changing how the widget connects to runtime after customers are live is a breaking change to every widget integration. |
| **Session token model** | The session token shape (issued by platform, validated by runtime) is a permanent contract between three parties: platform, runtime, widget. |
| **Deployment identity + binding** | Handle, URL, rebind/rollback API — permanent contract. |
| **API key system** | Key format, validation, environment separation — permanent contract. |
| **Multi-upstream action routing** | Part of the action config schema. If customers write single-upstream configs now, adding multi-upstream later forces config migration. |
| **Per-action auth** | Same — part of the action config schema. |
| **Policy engine (schema + core types)** | The policy YAML shape (`pre-policies`, `post-policies`) is part of the action config contract. Build the engine and first policies (confirmation, rate-limit, input-guard, webhook) now. |
| **Webhook payload schema** | Once a customer connects a Zapier zap, the shape is frozen. Design and implement before first customer. |
| **Widget embed contract** | Every `data-*` attribute is permanent. Ship the right set. |
| **Deployment router** | The router configuration shape (deployment scopes, routing strategy) becomes permanent once a customer configures multi-deployment routing. Design the config schema now, even if only single-deployment customers exist at launch. |
| **Portable identity (binding/rollback)** | Infrastructure that enables zero-downtime upgrades and cloud migration. Hard to retrofit because it requires a registry layer between every URL and every instance. |
| **Streaming/SSE for chat** | Chat without streaming feels broken in 2026. Every competitor streams. Retrofitting streaming onto a request/response chat API changes the widget's connection model — a contract-level change. |
| **Upgrade ONNX embedding model** | Dimension decision (384d) is permanent per existing customers. Upgrade to bge-small before any data is indexed. |

### 3.2 Truly Additive (Build After Market Entry)

These features create new, independent surfaces that don't touch existing contracts:

| Feature | Why It's Safe to Defer | When to Build |
|---|---|---|
| **Marketplace / plugins** | Entirely new surface (`/api/v1/marketplace/`). No existing API, config, or widget attribute affected. | When 10+ deployments need shared data |
| **AutoTrader data source** | New vectorization source. Existing sources unaffected. | When first car dealer signs up |
| **On-prem deployment** | New deployment target. Existing cloud deployments unaffected. | When enterprise requires it |
| **OAuth2 / SAML auth** | New auth mode alongside API key. Existing keys keep working forever. | When enterprise customer requires it |
| **Local LLM orchestration** | Internal provider swap. Customer sees same API, same responses, lower cost. | When optimizing costs at scale |
| **Reranker** | Internal RAG quality boost. No customer-facing change. | When answer quality needs improvement |
| **Additional embedding providers** | Provider abstraction exists. Plug in new ones. No contract change. | When higher quality tiers needed |
| **Custom domains** | Adds Cloudflare Workers in front. Existing `*.loomai.pro` URLs unaffected. | When a customer requests their own domain |

---

## 4) Execution Plan

Given Codex-accelerated velocity, this is a maturation sprint, not a cut-corners MVP sprint. Build every feature that shapes a permanent contract. Defer only features that are genuinely independent surfaces.

### Week 1: Core Architecture + Identity + Auth

| Day | Task | Output |
|---|---|---|
| 1 | **Deployment identity system** | `deployment_identities` table, handle generation, binding registry, binding history table, rebind/rollback API |
| 1 | **API key system** | Key generation (`dpk_{env}_{hex}`), SHA-256 storage, environment separation (live/test/dev) |
| 2 | **Resolve endpoint** | `POST /api/v1/resolve` — accepts deployment handle + API key, returns runtime URL + session token |
| 2 | **Session token model** | Token generation (short-lived, scoped), token validation on runtime side, token refresh |
| 3 | **Widget embed contract** | Finalize `data-*` attributes, update loader.js to use resolve-once-connect-direct pattern |
| 3 | **Streaming/SSE for chat** | Runtime streams LLM responses to widget via SSE. Widget renders tokens as they arrive. |
| 4 | **Action config schema v1** | Update action routing parser to support `upstream`, `url`, `auth`, `pre-policies`, `post-policies` |
| 4 | **Multi-upstream routing** | URL resolution logic (absolute > per-action upstream > global upstream) |
| 5 | **Per-action auth** | Auth header builder (bearer, api-key, basic, oauth2, none, inherit) with secret resolution from deployment bindings |
| 5 | **Per-action auth: OAuth2 client credentials** | Token URL, client ID/secret, scope, token caching |

### Week 2: Policy Engine + Deployment Router + Quality

| Day | Task | Output |
|---|---|---|
| 6 | **Policy engine core** | `ActionPolicy` interface, `PolicyEngine` registry, pre/post phase execution |
| 6 | **Pre-policy: confirmation** | Refactor existing confirmation interception into policy pattern |
| 7 | **Pre-policy: rate-limit** | Rate limiter per session/user/key with configurable window |
| 7 | **Pre-policy: input-guard** | Field validation rules (min, max, enum, pattern) |
| 8 | **Post-policy: webhook** | Outbound webhook delivery (async, retry with backoff, versioned payload) |
| 8 | **Post-policy: audit** | Action execution logging to audit trail |
| 9 | **Deployment router** | Router config schema, scope-based routing, local classifier for query → deployment matching |
| 9 | **Router session continuity** | Session store at router level, conversation handoff between deployments |
| 10 | **Upgrade ONNX model** | Replace MiniLM with bge-small-en-v1.5, run integration tests, verify 384d compatibility |
| 10 | **Deploy TEI on Railway** | External embedding service (bge-large for paid tiers), verify REST/OpenAI provider connection |

### Week 3: Integration + Demo + Launch Prep

| Day | Task | Output |
|---|---|---|
| 11 | **Portable identity edge setup** | Railway wildcard `*.loomai.pro`, DNS configuration, subdomain extraction |
| 11 | **Deployment binding operations** | Bind to new instance, rollback to previous, health-check-before-bind |
| 12 | **End-to-end integration test** | Full flow: resolve → connect → chat → action → confirmation → webhook → audit |
| 12 | **Widget production build** | Minified loader.js, CDN deployment to `widget.loomai.pro`, CORS configuration |
| 13 | **Live demo deployment** | Deploy on a real store, configure actions, test with real users |
| 13 | **Record demo video** | Screen recording of full flow: widget → chat → action → confirmation → webhook |
| 14 | **Landing page at loomai.pro** | Hero section, demo video, feature comparison, pricing tiers, "Book a demo" CTA |
| 14 | **Shopify App Store submission** | Submit app for review (process takes 2-6 weeks) |

---

## 5) Definition of Done

At the end of 3 weeks, the following must be true:

**Identity and Auth:**
- [ ] Deployment identity system is live (handles, fixed URLs, binding registry, rollback)
- [ ] API keys work (generate, validate, reject invalid, environment separation)
- [ ] Resolve endpoint returns runtime URL + session token
- [ ] Session tokens validated by runtime, short-lived, scoped

**Chat Architecture:**
- [ ] Widget uses resolve-once-connect-direct pattern (no proxy in chat path)
- [ ] Chat responses stream via SSE (not request/response)
- [ ] Widget embed code is finalized and documented

**Actions:**
- [ ] Action config supports multi-upstream + per-action auth (bearer, api-key, basic, oauth2)
- [ ] Policy engine works with pre and post phases
- [ ] Pre-policies: confirmation, rate-limit, input-guard
- [ ] Post-policies: webhook (with retry), audit log
- [ ] Webhook payload schema is versioned and documented

**Routing:**
- [ ] Deployment router config schema defined
- [ ] Router can direct queries to correct deployment by scope
- [ ] Session continuity across deployment handoffs

**Embeddings:**
- [ ] ONNX model upgraded to bge-small-en-v1.5 (384d)
- [ ] TEI deployed on Railway for paid tier embeddings

**Launch:**
- [ ] Railway wildcard `*.loomai.pro` configured
- [ ] One live demo deployment on a real store
- [ ] Demo video recorded
- [ ] loomai.pro landing page is live
- [ ] Shopify App Store submission filed

---

## 6) What This Achieves

### Market position after execution

```
A mature, architecturally complete platform with:

- Resolve-once-connect-direct chat (no proxy bottleneck)
- Streaming responses (table stakes in 2026)
- Multi-upstream actions (call any API, any auth — unique in market)
- Full policy engine (confirmation, rate limits, input guards, webhooks)
- Deployment governance (lifecycle, binding, rollback)
- Deployment router (multi-scope query routing — unique in market)
- Portable identity (zero-downtime upgrades, cloud migration)
- Open-core framework
- Free + paid embedding tiers

No backward compatibility debt.
No architectural shortcuts to fix later.
Every truly additive feature (marketplace, local LLM, on-prem,
custom domains, SAML) can be added cleanly on top.
```

### Competitor comparison after execution

| Capability | Tidio | Gorgias | Siena | Yuma | Loom AI |
|---|---|---|---|---|---|
| Streaming chat | Yes | Yes | Yes | Yes | **Yes** |
| Multi-API actions (any REST endpoint) | No | No | No | No | **Yes** |
| Per-action auth (bearer, OAuth2, etc.) | No | No | No | No | **Yes** |
| Action confirmation safety | No | No | No | No | **Yes** |
| Pre-action policies (rate limit, input guard) | No | No | No | No | **Yes** |
| Post-action webhooks (Zapier for free) | Limited | Limited | No | No | **Yes** |
| Deployment governance (lifecycle + rollback) | No | No | No | No | **Yes** |
| Deployment router (multi-scope routing) | No | No | No | No | **Yes** |
| Portable deployment identity | No | No | No | No | **Yes** |
| Multi-LLM provider | No | No | No | No | **Yes** |
| Open-core framework | No | No | No | No | **Yes** |
| Omnichannel | Yes | Yes | Yes | Yes | Widget only* |
| Production customers | 300K | 15K | 17+ | 50+ | 0 → 1 |

*Omnichannel is truly additive — each new channel is a new surface that doesn't affect existing widget deployments.

**After this sprint you enter the market with a platform that is architecturally superior to every competitor.** The gaps (omnichannel, customer count) are real but addressable — and they don't carry backward compatibility risk.
