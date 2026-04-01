# Multi-Tenant Runtime: Strategic Plan and Market Opportunity

Status: planning document (2026-04-01)

This document defines why shared-storage multi-tenancy is a strategic priority for AI Fabric, what market opportunities it unlocks, and how it relates to the existing productization roadmap.

---

## 1) Executive Summary

The AI Fabric runtime today operates as a single-tenant system. Each deployment serves one customer with its own storage. This is fine for direct enterprise sales, but it blocks the highest-leverage go-to-market motion available to the product: **powering other platforms from behind.**

The recommended architecture is a **hybrid model: deployment per customer with shared storage.**

- each customer keeps their own runtime deployment (isolated config, prompts, actions, connectors)
- all deployments share a common vector storage layer (cost efficient, centrally managed)
- tenant isolation is enforced at the storage layer through deployment identifiers

This preserves the safety and customization benefits of per-customer deployments while eliminating the cost problem that makes small-data customers unprofitable.

This is not just an engineering feature — it is a **business model unlock** that opens three distinct revenue channels that do not exist today.

---

## 2) The Hybrid Architecture

### 2.1 Why not fully single-tenant

Fully single-tenant means each customer gets their own runtime AND their own storage. For small-data customers (car dealers with 500 vehicles, Shopify merchants with 200 products), the per-customer storage cost creates a fixed floor that makes the unit economics unworkable at scale.

800 customers with separate storage = ~£50,000/month in infrastructure. The same data in shared storage = ~£120/month.

### 2.2 Why not fully multi-tenant

Fully multi-tenant means all customers share one runtime AND one storage. This is the cheapest option, but it sacrifices:

- per-customer prompt configuration
- per-customer action definitions and connector endpoints
- per-customer LLM provider choice
- independent failure isolation (one customer's problem affects everyone)
- independent upgrade and rollout control

These are the capabilities that make AI Fabric valuable to enterprise and mid-market customers. Sharing a runtime forces complex per-tenant routing logic into the application layer for every feature.

### 2.3 The hybrid model: deployment per customer, shared storage

This is the recommended architecture:

- **runtime deployment per customer** — each customer has their own isolated runtime with their own prompts, actions, connectors, API keys, and configuration
- **shared vector storage** — all deployments read and write to a common vector database cluster, with data isolated by a deployment identifier
- **the runtime stays exactly as it is today** — no tenant routing, no shared-config complexity
- **the only change is at the storage layer** — tag data on write, filter data on read

This gives the best of both worlds:

| Concern | Hybrid Model |
|---|---|
| Per-customer config and prompts | Yes — each deployment is independent |
| Per-customer actions and connectors | Yes — each deployment has its own |
| Per-customer LLM provider | Yes — configured per deployment |
| Independent failure isolation | Yes — one deployment crashing does not affect others |
| Independent upgrades | Yes — roll out changes per customer |
| Storage cost at 800 tenants | ~£120/month total (shared cluster) |
| Data isolation | Enforced at storage layer by deployment identifier |

---

## 3) How It Works

### 3.1 Architecture overview

```
Control Plane
(manages deployments, config, tenant admin)
    │
    ├── Deployment: Paul Rigby
    │   (own runtime, prompts, actions, connectors)
    │       │
    │       ├── WRITES: tag vectors with deployment_id = "paul-rigby"
    │       └── READS: filter vectors where deployment_id = "paul-rigby"
    │
    ├── Deployment: TrustFord
    │   (own runtime, prompts, actions, connectors)
    │       │
    │       ├── WRITES: tag vectors with deployment_id = "trustford"
    │       └── READS: filter vectors where deployment_id = "trustford"
    │
    └── Deployment: Perrys
        (own runtime, prompts, actions, connectors)
            │
            ├── WRITES: tag vectors with deployment_id = "perrys"
            └── READS: filter vectors where deployment_id = "perrys"
                │
                ▼
        ┌─────────────────────────────┐
        │   SHARED VECTOR STORAGE     │
        │   (one Qdrant/Milvus cluster)│
        │                             │
        │   All vectors tagged with   │
        │   deployment_id             │
        │   Filtered on every query   │
        └─────────────────────────────┘
```

### 3.2 Write path (indexing)

When a deployment indexes data (vehicles, documents, policies), the vector is stored in the shared cluster with the deployment identifier in the metadata. The deployment does not need to know that storage is shared — it simply writes to the configured vector database endpoint.

### 3.3 Read path (search and retrieval)

When a deployment searches for data, the query automatically includes a filter for the deployment identifier. Only vectors belonging to that deployment are returned. The deployment does not need to know that other tenants' data exists in the same cluster.

### 3.4 What the runtime does NOT need to change

- prompt loading and configuration — already per-deployment
- action definitions and handler routing — already per-deployment
- connector and relay configuration — already per-deployment
- LLM provider selection — already per-deployment
- RAG orchestration logic — already per-deployment
- rate limiting and API key management — already per-deployment

The runtime remains single-tenant in behavior. Only the storage layer becomes shared.

---

## 4) Current State

### 4.1 What exists

- the control plane architecture documents describe a multi-tenant control plane with single-tenant data plane
- each customer deployment is isolated: one runtime per customer
- vector database modules use entity type as the namespace boundary, not deployment
- the relay and connector model routes by user key, not by deployment

### 4.2 What is missing

- no deployment identifier flows through the storage pipeline
- indexing does not tag vectors with a deployment identifier
- search does not filter by deployment
- vector database modules do not partition or filter by deployment

### 4.3 What does NOT need to change

- the runtime application layer (prompts, actions, connectors, orchestration)
- the LLM provider integrations
- the relay routing model
- the RAG orchestrator logic

The scope of change is limited to the storage read/write path.

---

## 5) Why This Matters Now

### 5.1 It unlocks the B2B2B channel

The most capital-efficient path to scale is not selling to hundreds of individual businesses one by one. It is selling to **one platform partner who already has hundreds of customers.**

Example: a vertical chatbot vendor like AutoConverse serves 800+ UK car dealerships. They have distribution, relationships, and CRM integrations — but lack inventory grounding, semantic search, and action-based AI capabilities. AI Fabric has exactly what they lack.

Without shared storage, serving 800 dealers means 800 separate vector database instances. That is operationally expensive and commercially impractical. With shared storage, it is 800 lightweight deployments pointing at one vector cluster.

### 5.2 It reduces infrastructure cost per customer to near zero

A single car dealer has approximately 1,500 vectors (vehicles, documents, policies). Even 800 dealers produce only ~1.2 million vectors — well within a single vector database cluster.

With shared storage:

- one vector database cluster serves all tenants
- infrastructure cost per tenant becomes fractions of a penny
- the margin on per-tenant pricing becomes extremely high

Without shared storage:

- each tenant needs a separate vector database
- infrastructure cost per tenant is a fixed floor regardless of data size
- small-data customers become unprofitable

### 5.3 It matches how SaaS products actually work

Every successful SaaS platform (Salesforce, Shopify, Intercom, HubSpot) separates compute from storage and shares storage across tenants. This is the standard architecture for high-volume, low-data-per-customer businesses.

### 5.4 It makes the Shopify vertical viable at scale

The planned Shopify vertical targets merchants — small and medium businesses with small data. A Shopify merchant might have 50–500 products. Deploying a separate vector database per merchant is not viable at Shopify scale. Shared storage is a prerequisite for the Shopify vertical to work as a real business, not just a demo.

### 5.5 It preserves what already works

Unlike full multi-tenancy, the hybrid model does not require rearchitecting the runtime. Per-customer deployments continue to work exactly as they do today. The only change is where and how they store and retrieve vectors.

---

## 6) Market Opportunities Unlocked

### 6.1 Platform partnerships (B2B2B)

Shared storage enables AI Fabric to become the **intelligence layer behind other products.** The partner handles distribution, customer relationships, and vertical-specific UI. AI Fabric handles grounding, search, retrieval, and actions.

Target partners:

- vertical chatbot vendors (automotive, real estate, hospitality, healthcare)
- CRM and helpdesk platforms that want to add AI assistant capabilities
- ecommerce platform apps and plugins
- industry-specific SaaS products that need embedded AI

Revenue model:

- per-tenant API fee (partner pays per end-customer using the grounding API)
- revenue share on premium AI features the partner upsells
- platform license (flat fee for unlimited tenants within a tier)

Estimated potential for a single partner with 800 customers at £100–£200/tenant/month: **£960K–£1.92M annual recurring revenue from one partnership.**

### 6.2 Managed AI assistant service

Shared storage enables AI Fabric to offer a **hosted assistant service** where businesses sign up, connect their data, and get a working AI assistant without managing infrastructure.

This is the "Shopify model" applied to AI assistants:

- merchant signs up
- connects product catalog, documents, and policies
- gets an embeddable AI assistant widget
- pays monthly subscription

This is not viable with separate storage per customer at small-business price points (£400–£1,200/month). It becomes highly viable when the marginal storage cost per tenant is near zero.

### 6.3 Vertical solution packaging

Shared storage enables AI Fabric to package **vertical solutions** that can be sold to many businesses in the same industry:

- automotive: dealer AI assistant (inventory search, test drive booking, finance inquiry)
- ecommerce: product discovery and comparison assistant
- hospitality: hotel/restaurant booking and FAQ assistant
- professional services: document Q&A and knowledge assistant
- real estate: property search and viewing booking assistant

Each vertical is a configuration of actions, knowledge sources, and prompts — not a separate product. Shared storage makes it economically viable to serve hundreds of small businesses per vertical.

### 6.4 Tiered product packaging

The hybrid architecture naturally creates a pricing tier model:

| Tier | Architecture | Target Customer | Price Range |
|---|---|---|---|
| Starter | Shared runtime + shared storage | Small businesses via partners | £400–£600/mo |
| Professional | Own deployment + shared storage | Mid-market, dealer groups | £800–£1,500/mo |
| Enterprise | Own deployment + dedicated storage | Large enterprise, regulated | £2,500+/mo |

The starter tier uses full multi-tenancy (shared everything) for maximum cost efficiency. The professional tier uses the hybrid model. The enterprise tier uses full isolation for customers who require it. All three tiers are served by the same platform.

### 6.5 Competitive moat against AutoConverse-type vendors

Vertical chatbot vendors like AutoConverse are already serving hundreds of businesses, but without true data grounding. They will eventually try to build or buy this capability. If AI Fabric becomes their infrastructure partner first, switching costs create a durable competitive position.

If AI Fabric does not offer economically viable multi-tenant storage, these vendors will either build their own grounding layer or partner with a competitor that does.

---

## 7) The AutoConverse Opportunity — Concrete Example

### 7.1 Context

AutoConverse is a UK-based AI chatbot provider for automotive dealerships. Key facts:

- 800+ UK dealer customers
- pricing: £100–£200/month per dealer
- integrations: Salesforce, Keyloop, Pinewood, DVLA, CAP
- installation: simple script tag on dealer websites
- weakness: does not onboard dealer inventory data; deflects most product questions to human agents with "Ask The Team" and "Find Vehicle" buttons

### 7.2 The gap

When a customer asks "give me a list of good Volvo cars" on a Paul Rigby Group dealership website, the AutoConverse chatbot responds with a "Find Vehicle" button instead of answering. It cannot search the dealer's actual inventory because it has never ingested it. It has no semantic search, no product comparison, and no data grounding.

The responses it does give (MG electric car specs) are generic manufacturer data, not from Paul Rigby's actual stock.

This is exactly what AI Fabric provides.

### 7.3 The partnership model

Instead of competing with AutoConverse for 800 dealers one by one, AI Fabric powers AutoConverse from behind:

- AutoConverse keeps: widget UI, dealer relationships, CRM integrations, billing, DVLA/CAP data
- AI Fabric provides: inventory grounding API, semantic search, product comparison, RAG over dealer docs, action framework

### 7.4 Why shared storage is required

AutoConverse would integrate with AI Fabric via API. Each dealer's data must be isolated. When AutoConverse sends a search request for Paul Rigby's customers, it must return only Paul Rigby's inventory. When it sends a request for TrustFord's customers, it must return only TrustFord's inventory.

With the hybrid model:

- each dealer can have a lightweight deployment (own prompts, actions, connector config)
- all dealers share one vector storage cluster
- data isolation is enforced by deployment identifier filtering
- onboarding a new dealer = create deployment config + ingest inventory into shared storage

Without shared storage, 800 dealers means 800 separate vector databases at ~£50,000/month. With shared storage, it is one cluster at ~£120/month.

### 7.5 Revenue potential

| Model | Price | Annual Revenue |
|---|---|---|
| Per-tenant API fee (£150/dealer/mo) | 800 × £150 | £1,440,000 |
| Revenue share (50% of £200 uplift) | 800 × £100 | £960,000 |
| Platform license (flat) | £15,000/mo | £180,000 |

Even the conservative flat-license model produces meaningful revenue from a single partnership.

---

## 8) Infrastructure Economics

### 8.1 Data size per tenant

A typical small/medium business tenant (car dealer, Shopify merchant, small retailer) has:

| Data type | Volume | Vectors |
|---|---|---|
| Products / inventory | 200–500 items | ~500 |
| Documents / policies | 20–50 pages | ~200 |
| FAQs / reviews | 50–300 items | ~300 |
| Total per tenant | | ~1,000–1,500 |

This is tiny. The data size is not the bottleneck; the storage model is.

### 8.2 Cost comparison

| Scale | Shared Storage Cost | Separate Storage Cost | Savings |
|---|---|---|---|
| 100 tenants | ~£50/mo | ~£5,000–£10,000/mo | 99% |
| 500 tenants | ~£80/mo | ~£25,000–£50,000/mo | 99.7% |
| 1,000 tenants | ~£120/mo | ~£50,000–£100,000/mo | 99.9% |
| 5,000 tenants | ~£300/mo | ~£250,000–£500,000/mo | 99.9% |

### 8.3 Margin comparison

| Model | Revenue per tenant | Infra cost per tenant | Gross margin |
|---|---|---|---|
| Shared storage (800 dealers, £150/mo) | £150 | ~£0.15 + LLM costs | ~90%+ |
| Separate storage (800 dealers, £150/mo) | £150 | ~£50–£100 | ~30–60% |

Shared storage is the difference between a high-margin SaaS business and an infrastructure-heavy services business.

### 8.4 Cost breakdown by component

At 800 tenants, the dominant costs shift:

| Component | Cost | % of Total |
|---|---|---|
| LLM API calls (responses) | £2,000–£8,000/mo | 80–90% |
| Shared vector storage | ~£120/mo | 1–2% |
| Compute (800 lightweight deployments) | £800–£2,000/mo | 10–15% |
| Embedding generation (ONNX local) | £0 | 0% |

The vector storage that drives the entire multi-tenancy decision is less than 2% of total cost. LLM calls dominate. This confirms that shared storage is the right optimization target — it removes the only cost that scales linearly with customer count without corresponding value.

---

## 9) Relationship to Existing Roadmap

### 9.1 Where shared storage fits

Shared storage is a **storage-layer capability** that amplifies the value of every other roadmap item:

| Roadmap Item | Without Shared Storage | With Shared Storage |
|---|---|---|
| Enterprise deployment admin | Manages deployments + separate DBs | Manages deployments + shared DB config |
| Prompt management | Per-deployment prompts | Per-deployment prompts (no change) |
| POC / embedded chatbot | Needs provisioned storage per demo | Instant — just write to shared cluster |
| Shopify vertical | One DB per merchant (unviable) | Shared DB for all merchants (viable) |
| Data migration | Migrate into isolated DB | Migrate into shared DB with tenant tag |
| Action grounding | Per-deployment actions (no change) | Per-deployment actions (no change) |

### 9.2 Recommended priority

Shared storage support should be treated as a **Wave 1 foundation item** alongside enterprise deployment administration. The reasoning:

- it is a prerequisite for the B2B2B channel
- it is a prerequisite for the Shopify vertical at scale
- it is a prerequisite for viable SMB/mid-market pricing
- it is narrower in scope than full multi-tenant runtime (only the storage layer changes)
- delaying it means every deployment provisioned now assumes separate storage, making migration harder later

### 9.3 Updated recommended priority sequence

1. enterprise deployment administration and unified workspace
2. **shared storage with deployment-scoped tenant isolation** ← insert here
3. prompt management with hot apply
4. POC deployment mode with embedded chatbot
5. Shopify vertical reference implementation
6. runtime action-grounded answering and deep knowledge navigation
7. confirmation interception productization
8. data migration platform
9. platform AI assistant
10. remote confirmation policy service
11. multi-cloud provisioning expansion

---

## 10) Scope Definition

### 10.1 What shared storage means

- all deployments can be configured to read from and write to a common vector database cluster
- every vector stored in the shared cluster includes a deployment identifier in metadata
- every search against the shared cluster filters by the deployment identifier
- the indexing pipeline automatically tags vectors with the deployment context
- the deployment does not need to know whether storage is shared or dedicated

### 10.2 What shared storage does NOT mean

- it does not mean shared runtime (each customer keeps their own deployment)
- it does not mean shared configuration (prompts, actions, connectors remain per-deployment)
- it does not mean removing the option for dedicated storage (enterprise customers who require physical isolation can still have their own vector database)
- it does not require changes to the LLM provider integrations
- it does not require changes to the RAG orchestration logic
- it does not require changes to the action or connector framework

### 10.3 Deployment modes

The platform should support three storage modes per deployment:

| Mode | Storage | Runtime | Use Case |
|---|---|---|---|
| Shared | Shared vector cluster | Own deployment | Default for SMB/mid-market |
| Dedicated | Own vector database | Own deployment | Enterprise, regulated industries |
| Embedded | Lucene in-process | Own deployment | Development, testing, small-scale |

The mode is a deployment configuration choice, not an architectural fork. The runtime behaves identically in all three modes.

### 10.4 Tenant isolation guarantees

- search results must never include data from another deployment
- indexing must never write data accessible to another deployment
- deletion of a deployment's data must not affect other deployments
- failure in one deployment's storage operations must not corrupt shared state
- a deployment migrating from shared to dedicated storage must be seamless

---

## 11) Risk of Not Building Shared Storage

### 11.1 Missed partnership opportunities

Vertical platform vendors (AutoConverse, and others) need a cost-efficient multi-tenant intelligence layer. If AI Fabric cannot serve hundreds of tenants from shared infrastructure, they will build their own or partner with a competitor. The window for establishing these partnerships is limited.

### 11.2 Shopify vertical becomes unviable

The Shopify vertical targets thousands of merchants with small data. A separate vector database per merchant is economically impossible at Shopify scale. Without shared storage, the Shopify vertical remains a demo, not a business.

### 11.3 Locked into enterprise-only sales

Without shared storage, AI Fabric can only serve customers large enough to justify dedicated infrastructure. This limits the addressable market to enterprise accounts with long sales cycles, while the fastest-growing segment (SMB/mid-market AI adoption) remains unreachable.

### 11.4 Storage provisioning becomes a bottleneck

If every new customer requires provisioning a new vector database, onboarding is slow, expensive, and requires infrastructure operations. With shared storage, onboarding is configuration only — create deployment, ingest data, done.

---

## 12) Success Criteria

Shared storage is successful when:

1. a new deployment can be onboarded to shared storage through configuration alone, without provisioning new infrastructure
2. 100+ deployments can share a single vector cluster with full data isolation
3. infrastructure cost per deployment is under £1/month for storage at small-data scale
4. a deployment can be migrated from shared to dedicated storage without downtime or data loss
5. a platform partner can integrate via API and serve their customers with shared storage economics
6. the Shopify vertical can onboard merchants without per-merchant vector database provisioning

---

## 13) Recommendation

Shared storage with deployment-scoped tenant isolation should be elevated to a **top-priority foundation item** because it is not a feature — it is a **business model enabler.**

The hybrid architecture — deployment per customer with shared storage — is the right default because:

- it preserves every benefit of per-customer deployments (isolation, customization, independent failure, independent upgrades)
- it eliminates the only real cost problem (per-tenant storage at small data volumes)
- it requires the narrowest possible scope of change (storage layer only, runtime untouched)
- it naturally supports tiered pricing (shared storage, dedicated storage, embedded storage)
- it matches how every successful SaaS platform operates

Without it, AI Fabric is limited to:

- direct enterprise sales (long cycles, high touch)
- separate infrastructure per customer (high cost floor)
- enterprise pricing only (cannot serve SMB/mid-market)

With it, AI Fabric unlocks:

- B2B2B partnerships (one deal = hundreds of tenants, viable economics)
- managed assistant service (self-serve onboarding, near-zero marginal cost)
- vertical solution packaging (one shared cluster per industry)
- tiered product packaging (starter / professional / enterprise)
- the Shopify vertical at real scale

The engineering scope is contained to the storage read/write path. The runtime stays exactly as it is. The business impact is transformational. This should be built alongside the control plane foundation, not after it.
