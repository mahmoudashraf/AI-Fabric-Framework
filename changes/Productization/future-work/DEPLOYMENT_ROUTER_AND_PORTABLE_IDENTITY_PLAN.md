# Deployment Router and Portable Identity Plan

Status: planning document (2026-04-07)

This document covers two related capabilities:

1. **Deployment Router** — one customer deploys multiple scoped deployments, a router directs queries to the right one
2. **Portable Deployment Identity** — fixed client-facing URL survives deployment replacement, cloud provider migration, and instance swaps

---

## Part A: Deployment Router (Multi-Deployment Query Routing)

### 1) Problem

A customer may need multiple deployments, each specialised:

```
Customer: Paul Rigby Group (car dealer)

Deployment A: "Sales Assistant"
  - Knowledge: vehicle inventory, pricing, finance options
  - Actions: book test drive, request quote, check availability

Deployment B: "Service Advisor"
  - Knowledge: servicing schedules, MOT info, parts catalog
  - Actions: book service appointment, check service history

Deployment C: "General Enquiries"
  - Knowledge: opening hours, location, contact details, policies
  - Actions: submit contact form
```

Today the customer must pick one deployment per widget. The user asking "When is my car's next service?" hits the Sales Assistant and gets a poor answer.

### 2) Target: Smart Router

```
Customer's website (single widget)
         │
         ▼
┌──────────────────────────┐
│    DEPLOYMENT ROUTER     │
│                          │
│  Analyse query:          │
│  "When is my next        │
│   service due?"          │
│                          │
│  Route to: Deployment B  │
│  (Service Advisor)       │
│  Confidence: 0.92        │
└──────────┬───────────────┘
           │
    ┌──────┼──────┐
    ▼      ▼      ▼
  Deploy  Deploy  Deploy
    A       B       C
  Sales   Service  General
```

### 3) How Routing Works

The router is itself a lightweight orchestration step that runs before the main deployment:

```
1. Receive user query
2. For each deployment in the customer's router group:
   - Check deployment scope description (semantic match)
   - Check deployment action names (keyword match)
   - Optionally: run a fast vector similarity against each deployment's knowledge
3. Score each deployment
4. Forward query to highest-scoring deployment
5. If ambiguous (scores close): ask user to clarify
6. If no match: route to default deployment
```

**Routing can use the local LLM (Llama 8B / Phi-3)** — this is a classification task, not generation. Zero additional cloud API cost.

### 4) Router Configuration

```yaml
router:
  customer-id: paul-rigby-group
  entry-url: "https://paulrigby.loomai.pro/chat"
  strategy: semantic          # semantic | keyword | hybrid
  default-deployment: general-enquiries
  
  deployments:
    - id: sales-assistant
      scope: "Vehicle sales, pricing, test drives, finance, stock availability"
      keywords: ["buy", "price", "test drive", "finance", "stock", "available"]
      
    - id: service-advisor
      scope: "Vehicle servicing, MOT, repairs, parts, service booking"
      keywords: ["service", "MOT", "repair", "parts", "maintenance", "workshop"]
      
    - id: general-enquiries
      scope: "Opening hours, contact details, location, general policies"
      keywords: ["hours", "open", "contact", "where", "phone", "email"]
```

### 5) Conversation Continuity

If the user starts with a sales question then asks a service question, the router should:

1. **Maintain session context** across deployment switches
2. **Hand off conversation history** to the new deployment
3. **Inform the user transparently** ("Let me connect you with our service team for that question")

This requires a session store at the router level that holds conversation history independent of any single deployment.

### 6) Relation to B2B2B Model

For AutoConverse powering 800 dealers:

```
AutoConverse Platform
    │
    ▼
┌─────────────────────┐
│  Platform Router     │  ← Routes by dealer domain/subdomain
│  (tenant-level)      │
└──────────┬──────────┘
           │
    ┌──────┼──────┐
    ▼      ▼      ▼
  Dealer  Dealer  Dealer
    A       B       C
    │
    ▼
┌─────────────────────┐
│  Dealer Router       │  ← Routes by query type within dealer
│  (deployment-level)  │
└──────────┬──────────┘
           │
    ┌──────┼──────┐
    ▼      ▼      ▼
  Sales  Service  General
```

Two-level routing: first by tenant (which dealer), then by deployment (which assistant scope).

---

## Part B: Portable Deployment Identity

### 1) Problem

When a deployment is live at `https://paulrigby.loomai.pro/chat`:

- Replacing the deployment instance (e.g. scaling, upgrading) breaks the URL
- Migrating from Railway to AWS breaks the URL
- Rolling back to a previous version requires URL changes
- Blue/green deployment is impossible without URL management

The client-facing URL must be **permanent and independent** of the underlying deployment instance or cloud provider.

### 2) Identity Model

```
┌─────────────────────────────────────────────────────────┐
│                  DEPLOYMENT IDENTITY                    │
│                                                         │
│  Deployment Handle:  paul-rigby-sales                   │
│  Public URL:         paulrigby.loomai.pro/sales/chat    │
│  API Key:            dpk_abc123...                      │
│  Widget Embed Code:  <script data-deployment="paul..."  │
│                                                         │
│  These NEVER change.                                    │
│                                                         │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Currently Bound To:                                    │
│                                                         │
│  Instance:     deploy-inst-7f3a2b                       │
│  Provider:     Railway                                  │
│  Region:       eu-west-1                                │
│  Version:      v2.4.1                                   │
│  Status:       LIVE                                     │
│                                                         │
│  These CAN change without affecting the public URL.     │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

### 3) How It Works

The platform maintains a **deployment registry** that maps handles to instances:

```
deployment_handle  →  instance_url
─────────────────────────────────────────
paul-rigby-sales   →  https://deploy-7f3a.railway.app
paul-rigby-service →  https://deploy-8e2b.railway.app
acme-corp-support  →  https://acme-prod.aws.eu-west-1.elb.amazonaws.com
```

The platform proxy (or DNS/load balancer) resolves the handle to the current instance:

```
Client request to: paulrigby.loomai.pro/sales/chat
    │
    ▼
Platform Edge (proxy/DNS)
    │ Lookup: "paul-rigby-sales" → deploy-7f3a.railway.app
    │
    ▼
Forward to: https://deploy-7f3a.railway.app/chat
```

### 4) Deployment Operations Enabled

| Operation | Before | After |
|---|---|---|
| **Version upgrade** | Change URL, update widget | Rebind handle to new instance. Zero client changes. |
| **Rollback** | Change URL back | Rebind handle to previous instance. Instant. |
| **Cloud migration** (Railway → AWS) | Change URL, update DNS, update widget | Rebind handle to AWS instance. Zero client changes. |
| **Blue/green deploy** | Not possible | Bind handle to green instance, verify, done. |
| **Scaling** | Manual URL management | Bind handle to load balancer. Same public URL. |
| **Disaster recovery** | Rebuild everything | Rebind handle to standby instance in different region. |

### 5) Implementation

#### Registry table

```sql
CREATE TABLE deployment_identities (
    handle          VARCHAR(128) PRIMARY KEY,    -- paul-rigby-sales
    customer_id     VARCHAR(64) NOT NULL,
    tenant_id       VARCHAR(64) NOT NULL,
    public_url      VARCHAR(512) UNIQUE,         -- paulrigby.loomai.pro/sales
    api_key_hash    VARCHAR(256) NOT NULL,
    
    -- Current binding (mutable)
    instance_url    VARCHAR(512) NOT NULL,        -- https://deploy-7f3a.railway.app
    provider        VARCHAR(64),                  -- railway, aws, gcp, on-prem
    region          VARCHAR(64),
    version         VARCHAR(32),
    status          VARCHAR(32) DEFAULT 'LIVE',
    
    -- Binding history
    bound_at        TIMESTAMP NOT NULL,
    bound_by        VARCHAR(128),
    
    created_at      TIMESTAMP NOT NULL,
    updated_at      TIMESTAMP NOT NULL
);

CREATE TABLE deployment_binding_history (
    id              BIGINT PRIMARY KEY,
    handle          VARCHAR(128) NOT NULL,
    instance_url    VARCHAR(512) NOT NULL,
    provider        VARCHAR(64),
    version         VARCHAR(32),
    bound_at        TIMESTAMP NOT NULL,
    unbound_at      TIMESTAMP,
    reason          VARCHAR(256)                  -- upgrade, rollback, migration, etc.
);
```

#### Platform API

```
POST   /api/deployments/{handle}/bind     — Bind handle to new instance
POST   /api/deployments/{handle}/rollback — Rebind to previous instance
GET    /api/deployments/{handle}/history   — Binding history
GET    /api/deployments/{handle}/resolve   — Current instance URL
```

#### Edge proxy resolution

The platform edge (nginx, Cloudflare Worker, or platform gateway) resolves handles:

```
location ~ ^/deployments/(?<handle>[^/]+)/chat {
    # Lookup instance_url from registry
    # Forward request to instance_url
    # Add X-Deployment-Handle header for tracing
}
```

Or with custom domains: CNAME `paulrigby.loomai.pro` → platform edge → registry lookup → instance.

### 6) Relation to Existing Architecture

The platform already has:
- `DeploymentEntity` with lifecycle states (Draft → Published → Released → Live)
- `PlatformCustomerEntity` → `PlatformTenantEntity` → deployment hierarchy
- `DeploymentProviderSecretBindingEntity` for per-deployment secrets

Portable identity adds a **binding layer** between the stable identity (handle, URL, API key) and the mutable instance (URL, provider, version). The existing `DeploymentEntity` becomes the identity record. The new `deployment_binding` becomes the mutable pointer.

---

## Part C: Shared Public Data Sources (Marketplace Plugin)

### 1) Concept

Some vector databases contain public data that is useful across many deployments:

- **AutoTrader UK vehicle inventory** — useful for any car dealer deployment
- **UK postcode/address lookup** — useful for any UK-based assistant
- **Product safety recalls** — useful for any automotive deployment
- **Generic FAQ data** (shipping policies, return policies) — useful templates

These should be available as **optional plugins** from a marketplace, not bundled into every deployment.

### 2) Architecture

```
┌──────────────────────────────────────────────────┐
│                  MARKETPLACE                      │
│                                                   │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────┐ │
│  │ AutoTrader   │  │ UK Postcode │  │ Product  │ │
│  │ Vehicle Data │  │ Lookup      │  │ Recalls  │ │
│  │              │  │             │  │          │ │
│  │ Shared       │  │ Shared      │  │ Shared   │ │
│  │ Vector DB    │  │ Vector DB   │  │ Vector   │ │
│  │ (read-only)  │  │ (read-only) │  │ DB       │ │
│  └──────┬───────┘  └──────┬──────┘  └────┬─────┘ │
│         │                 │               │       │
└─────────┼─────────────────┼───────────────┼───────┘
          │                 │               │
          ▼                 ▼               ▼
   ┌──────────────────────────────────────────────┐
   │         DEPLOYMENT: Paul Rigby Sales         │
   │                                              │
   │  Private knowledge:  Dealer's own inventory  │
   │  Plugin knowledge:   AutoTrader UK data      │
   │  Plugin knowledge:   UK Postcode lookup      │
   │                                              │
   │  RAG query searches BOTH private + plugin    │
   │  sources, merges results, then answers       │
   └──────────────────────────────────────────────┘
```

### 3) Plugin Model

```yaml
marketplace-plugins:
  autotrader-uk:
    name: "AutoTrader UK Vehicle Data"
    description: "Search UK vehicle listings from AutoTrader"
    data-source: autotrader-connect-api
    refresh: every-8-hours        # AutoTrader API limit
    vector-collection: "marketplace_autotrader_uk"
    access: read-only
    cost: free                    # or $X/mo for premium data
    
  uk-postcodes:
    name: "UK Postcode & Address Lookup"
    description: "Search UK addresses by postcode"
    vector-collection: "marketplace_uk_postcodes"
    access: read-only
    cost: free
```

### 4) How a Deployment Uses Plugins

When a deployment installs a marketplace plugin:

1. The deployment's RAG pipeline gains an additional vector source
2. Queries search **both** private collections and plugin collections
3. Plugin data is **read-only** — the deployment cannot modify shared data
4. Plugin data is **shared infrastructure** — one vector collection serves all deployments that install the plugin
5. Results from plugin sources are tagged with their origin (so the LLM can cite "According to AutoTrader..." vs "From our inventory...")

### 5) AutoTrader Specific Implementation

AutoTrader Connect API provides:
- Vehicle stock data (make, model, year, mileage, price, images)
- Stock updates (new listings, price changes, sold vehicles)
- Maximum refresh: every 8 hours

Vectorization pipeline:
1. Platform-managed cron job fetches from AutoTrader Connect API every 8 hours
2. Vehicle data transformed into searchable text chunks
3. Indexed into shared `marketplace_autotrader_uk` collection
4. All deployments with the plugin installed can search this collection

**Cost to operate:** one Qdrant collection (~$5/mo for 100K vehicles), one cron job, one API connection. Shared across all dealer deployments.

### 6) Marketplace as Platform Feature

The marketplace is a future platform feature that enables:

| Feature | Description |
|---|---|
| **Data plugins** | Shared vector sources (AutoTrader, postcode, etc.) |
| **Action templates** | Pre-configured action definitions (Calendly booking, Stripe refund, etc.) |
| **Prompt templates** | Pre-built prompt configurations for verticals (car dealer, e-commerce, support) |
| **Widget themes** | Custom chat widget styling packs |
| **Integration packs** | Combined data + actions + prompts for a vertical (e.g. "Car Dealer Pack") |

Revenue model:
- Free plugins (community-contributed, platform-managed public data)
- Paid plugins ($X/mo — premium data sources, advanced templates)
- Revenue share for third-party plugin creators

### 7) Relation to Existing Multi-Tenant Architecture

Plugin vector collections use the same shared vector infrastructure from `TENANT_SCOPED_SHARED_VECTOR_INFRASTRUCTURE_PLAN.md`:

- Plugin collections are **platform-managed** (not customer-managed)
- Read-only access enforced at the vector service layer
- No tenant can write to or delete from plugin collections
- Plugin collections use provider-native isolation (Qdrant collections, Pinecone namespaces)

---

## Implementation Priority

| Feature | Priority | Effort | Dependencies |
|---|---|---|---|
| Portable deployment identity (registry + binding) | **P1** | 2-3 weeks | Platform backend |
| Deployment rebinding API (upgrade, rollback, migrate) | **P1** | 1 week | Registry table |
| Edge proxy / handle resolution | **P1** | 1-2 weeks | DNS/proxy infrastructure |
| Deployment router (multi-deployment query routing) | **P2** | 3-4 weeks | Local LLM for classification |
| Router session continuity (cross-deployment handoff) | **P2** | 1-2 weeks | Session store |
| Marketplace plugin model | **P2** | 2-3 weeks | Platform backend + frontend |
| Shared read-only vector sources | **P2** | 1-2 weeks | Existing vector infrastructure |
| AutoTrader data plugin | **P3** | 2 weeks | AutoTrader Connect API access |
| Action template marketplace | **P3** | 2-3 weeks | Multi-upstream action routing |
| Two-level routing (tenant + deployment) | **P3** | 2 weeks | Deployment router + tenant model |
