# Platform Contract Finalization and Launch Plan

Status: execution plan (2026-04-07)

This document defines the final sprint before first customer launch. It separates permanent contracts (surfaces that cannot change after customers go live) from additive internals (features that can be safely added later). The goal is to lock down every customer-facing contract, implement the critical internals, and ship.

---

## 1) Guiding Principle

> Build everything that becomes permanent. Defer everything that is additive.

Once a customer pastes a widget embed code into their website, the `data-*` attributes are frozen. Once a customer writes an action config YAML, the schema is frozen. Once a customer connects a Zapier zap to a webhook payload, the shape is frozen.

Everything behind those surfaces — routing algorithms, LLM selection, reranker pipelines, marketplace logic — can change freely at any time without affecting customers.

---

## 2) Permanent Contracts to Lock Down

### 2.1 Deployment Identity Contract

**Why permanent:** every customer's widget, DNS, integrations, and bookmarks point to this.

```
Public URL pattern:
  https://{customer-slug}.loomai.pro/{deployment-slug}/chat
  https://widget.loomai.pro/v1/deployments/{handle}/chat

Widget embed:
  <script
    src="https://widget.loomai.pro/v1/loader.js"
    data-deployment="{handle}"
    data-key="{api-key}"
    ...
  ></script>

API endpoint:
  POST /api/v1/deployments/{handle}/chat
  Authorization: Bearer dpk_live_...
```

**Design decisions to make:**
- Handle format: lowercase alphanumeric + hyphens, max 64 chars, globally unique
- Handle is immutable once created (rename = create new + migrate)
- Public URL supports both platform subdomain and customer custom domain (CNAME)

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
GET    /api/v1/deployments/{handle}/resolve
GET    /api/v1/deployments/{handle}/history

# Deployment runtime
POST   /api/v1/deployments/{handle}/chat
GET    /api/v1/deployments/{handle}/health
GET    /api/v1/deployments/{handle}/actions

# Keys
POST   /api/v1/deployments/{handle}/keys
DELETE /api/v1/deployments/{handle}/keys/{keyId}

# Vectorization
POST   /api/v1/deployments/{handle}/knowledge/sync
GET    /api/v1/deployments/{handle}/knowledge/status

# Future: marketplace (additive, not needed at launch)
GET    /api/v1/marketplace/plugins
POST   /api/v1/deployments/{handle}/plugins/{pluginId}/install
```

**Versioning:** `/api/v1/` prefix. When breaking changes are needed (unlikely if designed well), introduce `/api/v2/` alongside. V1 stays forever.

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

## 3) Additive Features (Safe to Build After Launch)

These features can be added without breaking any customer-facing contract:

| Feature | Why It's Safe | When to Build |
|---|---|---|
| Deployment router | New layer in front of existing deployments. Single-deployment customers unaffected. | When a customer needs multiple deployments |
| Marketplace plugins | New feature, new API endpoints under `/api/v1/marketplace/`. No existing surface changes. | When 10+ deployments could share data |
| Reranker | Internal RAG quality improvement. No customer-facing API change. | When answer quality needs improvement |
| Local LLM orchestration | Swaps internal provider. Customer sees same API, better latency, lower cost. | When optimizing costs |
| OAuth2 / SAML auth | New auth modes alongside API key. Existing keys keep working. | When enterprise customer requires it |
| Additional embedding providers | Provider abstraction exists. New providers plug in. | When free tier needs better quality |
| Post-action policy types | New types added to policy engine. Existing `webhook` and `audit` keep working. `Unknown types ignored` rule ensures forward compat. | When customers request specific policies |
| AutoTrader data source | New vectorization source. Existing sources unaffected. | When first car dealer signs up |
| On-prem deployment | New deployment target. Cloud deployments unaffected. | When enterprise requires it |
| WebSocket/streaming | New transport alongside HTTP. HTTP keeps working. | When real-time use cases emerge |

---

## 4) Two-Week Execution Plan

### Week 1: Lock All Contracts + Core Implementation

| Day | Task | Output |
|---|---|---|
| 1 | **Deployment identity system** | `deployment_identities` table, handle generation, binding API, resolution endpoint |
| 1 | **API key system** | Key generation (`dpk_{env}_{hex}`), SHA-256 storage, validation middleware |
| 2 | **Widget embed contract** | Finalize `data-*` attributes, update `widget.loomai.pro/v1/loader.js` |
| 2 | **Auth enforcement in runtime** | API key validation on every `/chat` request, reject without valid key |
| 3 | **Action config schema v1** | Update action routing parser to support `upstream`, `url`, `auth` fields |
| 3 | **Multi-upstream routing** | URL resolution logic (absolute > per-action upstream > global upstream) |
| 4 | **Per-action auth** | Auth header builder (bearer, api-key, basic, oauth2, none, inherit) |
| 4 | **Webhook payload schema** | Define event types, build webhook delivery service (async, with retry) |
| 5 | **Pre-policy: confirmation** | Refactor existing confirmation into policy engine pattern |
| 5 | **Post-policy: webhook** | Implement outbound webhook delivery on action execution |

### Week 2: Quality + Demo + Ship

| Day | Task | Output |
|---|---|---|
| 6 | **Pre-policy: rate-limit** | In-memory rate limiter per session/user/key |
| 6 | **Pre-policy: input-guard** | JSON Schema-style field validation |
| 7 | **Upgrade ONNX model** | Replace MiniLM with bge-small-en-v1.5, run integration tests |
| 7 | **Deploy TEI on Railway** | External embedding service, configure one test deployment to use it |
| 8 | **Portable identity edge proxy** | Handle resolution at platform edge (nginx or Cloudflare Worker) |
| 8 | **Deployment binding + rollback** | Bind/unbind API, binding history table |
| 9 | **Live demo deployment** | Deploy on a real Shopify store, configure actions, test end-to-end |
| 9 | **Record demo video** | Screen recording of the live deployment in action |
| 10 | **Landing page at loomai.pro** | Hero section, demo video, pricing tiers, "Book a demo" CTA |
| 10 | **Shopify App Store submission** | Submit app for review (process takes 2-6 weeks) |

---

## 5) Definition of Done

At the end of 2 weeks, the following must be true:

- [ ] Deployment identity system is live (handles, fixed URLs, binding registry)
- [ ] API keys work (generate, validate, reject invalid)
- [ ] Widget embed code is finalized and documented
- [ ] Action config supports multi-upstream + per-action auth
- [ ] Pre-policies work (confirmation, rate-limit, input-guard)
- [ ] Post-policies work (webhook delivery with retry)
- [ ] Webhook payload schema is documented and versioned
- [ ] ONNX model upgraded to bge-small-en-v1.5
- [ ] One live demo deployment on a real store
- [ ] loomai.pro landing page is live
- [ ] Shopify App Store submission filed

---

## 6) What This Achieves

### Market position after execution

```
BEFORE:
  137K lines of code, 40+ plan documents, 0 customers, 0 production deployments.

AFTER:
  All customer-facing contracts locked and production-safe.
  No backward compatibility risk for future features.
  One live demo with video proof.
  A website where people can find you.
  Shopify App Store review in progress.
  Every additive feature (router, marketplace, reranker, local LLM)
  can be added without breaking a single customer.
```

### Competitor comparison after execution

| Capability | Tidio | Gorgias | Siena | Loom AI |
|---|---|---|---|---|
| Multi-API actions (any REST endpoint) | No | No | No | **Yes** |
| Per-action auth (bearer, OAuth2, etc.) | No | No | No | **Yes** |
| Action confirmation safety | No | No | No | **Yes** |
| Webhook policies (Zapier for free) | Limited | Limited | No | **Yes** |
| Deployment governance (lifecycle) | No | No | No | **Yes** |
| Portable deployment identity | No | No | No | **Yes** |
| Multi-LLM provider | No | No | No | **Yes** |
| Open-core framework | No | No | No | **Yes** |
| Rate limiting per action | Basic | Basic | No | **Yes** |
| Input validation per action | No | No | No | **Yes** |
| Omnichannel | Yes | Yes | Yes | Widget only |
| Production customers | 300K | 15K | 17+ | 0 → 1 |
| Automation rate metrics | 67% | 60% | 80% | TBD |

**After this sprint you have 10 features no competitor offers and 1 live deployment to prove it.** The two gaps (omnichannel and customer count) are addressable — omnichannel is additive (add WhatsApp later), and customer count starts at 1 and grows.
