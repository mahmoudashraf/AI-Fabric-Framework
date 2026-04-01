# Multi-Tenant Runtime: Strategic Plan and Market Opportunity

Status: planning document (2026-04-01)

This document defines why multi-tenant runtime support is a strategic priority for AI Fabric, what market opportunities it unlocks, and how it relates to the existing productization roadmap.

---

## 1) Executive Summary

The AI Fabric runtime today operates as a single-tenant system. Each deployment serves one customer. This is fine for direct enterprise sales, but it blocks the highest-leverage go-to-market motion available to the product: **powering other platforms from behind.**

Multi-tenant runtime support means a single AI Fabric deployment can serve multiple end-customers, with full data isolation, from one shared infrastructure. This is not just an engineering feature — it is a **business model unlock** that opens three distinct revenue channels that do not exist today.

---

## 2) What Multi-Tenancy Means for AI Fabric

Multi-tenancy in the AI Fabric context means:

- a single runtime deployment can serve multiple end-customers (tenants)
- each tenant's data (vectors, documents, knowledge, actions) is logically isolated
- every search, retrieval, indexing, and action operation is scoped to a tenant
- tenants cannot see, access, or affect each other's data
- the platform operator manages all tenants from one deployment

This is a data-plane capability, not just a control-plane concept.

---

## 3) Current State

### 3.1 What exists

- the control plane architecture documents describe a multi-tenant control plane with single-tenant data plane
- each customer deployment is isolated: one runtime per customer
- vector database modules use entity type as the namespace boundary, not tenant
- the relay and connector model routes by user key, not by tenant or deployment

### 3.2 What is missing

- no tenant identifier flows through the runtime pipeline
- indexing does not tag vectors with a tenant or deployment identifier
- search does not filter by tenant
- the RAG orchestrator has no tenant context
- the relay does not route by tenant
- vector database modules do not partition or filter by tenant

### 3.3 Architectural intent

The existing plans acknowledge this gap. The remote access control plan explicitly notes that multi-tenant runtime is future work, and the current model assumes one deployment per customer.

---

## 4) Why Multi-Tenancy Matters Now

### 4.1 It unlocks the B2B2B channel

The most capital-efficient path to scale is not selling to hundreds of individual businesses one by one. It is selling to **one platform partner who already has hundreds of customers.**

Example: a vertical chatbot vendor like AutoConverse serves 800+ UK car dealerships. They have distribution, relationships, and CRM integrations — but lack inventory grounding, semantic search, and action-based AI capabilities. AI Fabric has exactly what they lack.

Without multi-tenancy, serving 800 dealers means 800 separate deployments. That is operationally expensive and commercially impractical. With multi-tenancy, it is one deployment, one integration, 800 tenants.

### 4.2 It reduces infrastructure cost per customer to near zero

A single car dealer has approximately 1,500 vectors (vehicles, documents, policies). Even 800 dealers produce only ~1.2 million vectors — well within a single vector database cluster.

With multi-tenancy:

- one shared vector database cluster serves all tenants
- infrastructure cost per tenant becomes fractions of a penny
- the margin on per-tenant pricing becomes extremely high

Without multi-tenancy:

- each tenant needs a separate deployment
- infrastructure cost per tenant is a fixed floor regardless of data size
- small-data customers become unprofitable

### 4.3 It matches how SaaS products actually work

Every successful SaaS platform (Salesforce, Shopify, Intercom, HubSpot) operates multi-tenant infrastructure. Single-tenant-per-customer is an enterprise concession, not a default architecture. For the mid-market and SMB segments that vertical partners serve, multi-tenancy is the only viable model.

### 4.4 It makes the Shopify vertical viable at scale

The planned Shopify vertical targets merchants — small and medium businesses with small data. A Shopify merchant might have 50–500 products. Deploying a separate AI Fabric runtime per merchant is not viable at Shopify scale. Multi-tenancy is a prerequisite for the Shopify vertical to work as a real business, not just a demo.

---

## 5) Market Opportunities Unlocked

### 5.1 Platform partnerships (B2B2B)

Multi-tenancy enables AI Fabric to become the **intelligence layer behind other products.** The partner handles distribution, customer relationships, and vertical-specific UI. AI Fabric handles grounding, search, retrieval, and actions.

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

### 5.2 Managed AI assistant service

Multi-tenancy enables AI Fabric to offer a **hosted assistant service** where businesses sign up, connect their data, and get a working AI assistant without managing infrastructure.

This is the "Shopify model" applied to AI assistants:

- merchant signs up
- connects product catalog, documents, and policies
- gets an embeddable AI assistant widget
- pays monthly subscription

This is not viable with single-tenant deployments at small-business price points (£400–£1,200/month). It becomes highly viable when the marginal infrastructure cost per tenant is near zero.

### 5.3 Vertical solution packaging

Multi-tenancy enables AI Fabric to package **vertical solutions** that can be sold to many businesses in the same industry from shared infrastructure:

- automotive: dealer AI assistant (inventory search, test drive booking, finance inquiry)
- ecommerce: product discovery and comparison assistant
- hospitality: hotel/restaurant booking and FAQ assistant
- professional services: document Q&A and knowledge assistant
- real estate: property search and viewing booking assistant

Each vertical is a configuration of actions, knowledge sources, and prompts — not a separate product. Multi-tenancy makes it economically viable to serve hundreds of small businesses per vertical from one deployment.

### 5.4 Competitive moat against AutoConverse-type vendors

Vertical chatbot vendors like AutoConverse are already serving hundreds of businesses, but without true data grounding. They will eventually try to build or buy this capability. If AI Fabric becomes their infrastructure partner first, switching costs create a durable competitive position.

If AI Fabric does not offer multi-tenancy, these vendors will either build their own grounding layer or partner with a competitor that does.

---

## 6) The AutoConverse Opportunity — Concrete Example

### 6.1 Context

AutoConverse is a UK-based AI chatbot provider for automotive dealerships. Key facts:

- 800+ UK dealer customers
- pricing: £100–£200/month per dealer
- integrations: Salesforce, Keyloop, Pinewood, DVLA, CAP
- weakness: does not onboard dealer inventory data; deflects most product questions to human agents

### 6.2 The gap

When a customer asks "give me a list of good Volvo cars" on a Paul Rigby Group dealership website, the AutoConverse chatbot responds with a "Find Vehicle" button instead of answering. It cannot search the dealer's actual inventory. It has no semantic search, no product comparison, and no data grounding.

This is exactly what AI Fabric provides.

### 6.3 The partnership model

Instead of competing with AutoConverse for 800 dealers one by one, AI Fabric powers AutoConverse from behind:

- AutoConverse keeps: widget UI, dealer relationships, CRM integrations, billing, DVLA/CAP data
- AI Fabric provides: inventory grounding API, semantic search, product comparison, RAG over dealer docs, action framework

### 6.4 Why multi-tenancy is required

AutoConverse would integrate with AI Fabric via API. Each dealer is a tenant. When AutoConverse sends a search request for Paul Rigby's customers, it must return only Paul Rigby's inventory. When it sends a request for TrustFord's customers, it must return only TrustFord's inventory.

Without multi-tenancy, this requires 800 separate AI Fabric deployments — operationally impractical. With multi-tenancy, it is one deployment serving 800 tenants through one API integration.

### 6.5 Revenue potential

| Model | Price | Annual Revenue |
|---|---|---|
| Per-tenant API fee (£150/dealer/mo) | 800 × £150 | £1,440,000 |
| Revenue share (50% of £200 uplift) | 800 × £100 | £960,000 |
| Platform license (flat) | £15,000/mo | £180,000 |

Even the conservative flat-license model produces meaningful revenue from a single partnership.

---

## 7) Infrastructure Economics

### 7.1 Data size per tenant

A typical small/medium business tenant (car dealer, Shopify merchant, small retailer) has:

| Data type | Volume | Vectors |
|---|---|---|
| Products / inventory | 200–500 items | ~500 |
| Documents / policies | 20–50 pages | ~200 |
| FAQs / reviews | 50–300 items | ~300 |
| Total per tenant | | ~1,000–1,500 |

This is tiny. The data size is not the bottleneck; the operational model is.

### 7.2 Cost at scale with multi-tenancy

| Scale | Total Vectors | Shared Vector DB Cost | Cost Per Tenant |
|---|---|---|---|
| 100 tenants | ~150K | ~£50/mo | ~£0.50/tenant/mo |
| 500 tenants | ~750K | ~£80/mo | ~£0.16/tenant/mo |
| 1,000 tenants | ~1.5M | ~£120/mo | ~£0.12/tenant/mo |
| 5,000 tenants | ~7.5M | ~£300/mo | ~£0.06/tenant/mo |

Using a shared Qdrant or Milvus cluster with metadata-based tenant filtering, the vector storage cost per tenant is effectively zero at scale. The dominant cost is LLM API calls for generating responses, not storage or retrieval.

### 7.3 Cost without multi-tenancy

| Scale | Deployment Cost Per Tenant | Total |
|---|---|---|
| 100 tenants | ~£50–£100/mo (minimum viable deployment) | £5,000–£10,000/mo |
| 1,000 tenants | ~£50–£100/mo | £50,000–£100,000/mo |

Single-tenant economics do not work for high-volume, low-data-size customers.

### 7.4 Margin comparison

| Model | Revenue per tenant | Infra cost per tenant | Gross margin |
|---|---|---|---|
| Multi-tenant (800 dealers, £150/mo) | £150 | ~£0.15 + LLM costs | ~90%+ |
| Single-tenant (800 dealers, £150/mo) | £150 | ~£50–£100 | ~30–60% |

Multi-tenancy is the difference between a high-margin SaaS business and an infrastructure-heavy services business.

---

## 8) Relationship to Existing Roadmap

### 8.1 Where multi-tenancy fits

Multi-tenancy is a **cross-cutting runtime capability** that amplifies the value of every other roadmap item:

| Roadmap Item | Without Multi-Tenancy | With Multi-Tenancy |
|---|---|---|
| Enterprise deployment admin | Manages single-tenant deployments | Manages multi-tenant deployments + tenant admin |
| Prompt management | Per-deployment prompts | Per-tenant prompt overrides within shared deployment |
| POC / embedded chatbot | One demo per deployment | Instant POC per tenant within shared deployment |
| Shopify vertical | One deployment per merchant (unviable) | One deployment for all merchants (viable) |
| Data migration | Migrate one customer at a time | Onboard tenants in bulk |
| Action grounding | Per-deployment actions | Per-tenant action configuration |

### 8.2 Recommended priority

Multi-tenancy should be treated as a **Wave 1 foundation item** alongside enterprise deployment administration, not as a later enhancement. The reasoning:

- it is a prerequisite for the B2B2B channel
- it is a prerequisite for the Shopify vertical at scale
- it is a prerequisite for viable SMB/mid-market pricing
- it is relatively contained in scope (one identifier flowing through the existing pipeline)
- delaying it means every feature built on top assumes single-tenant, making the migration harder later

### 8.3 Updated recommended priority sequence

1. enterprise deployment administration and unified workspace
2. **multi-tenant runtime support** ← insert here
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

## 9) Scope Definition

### 9.1 What multi-tenant runtime means

- a tenant identifier is a first-class concept in the runtime
- every data operation (index, search, retrieve, act) is scoped to a tenant
- tenant data is logically isolated within shared infrastructure
- the API contract supports tenant identification (header, token, or path parameter)
- vector databases partition or filter data by tenant
- the indexing pipeline tags all stored data with tenant context
- the orchestration pipeline carries tenant context through every step

### 9.2 What multi-tenant runtime does NOT mean

- it does not mean multi-tenant control plane (that is a separate, already-planned capability)
- it does not mean shared LLM context between tenants (each tenant's queries are independent)
- it does not mean removing the option for single-tenant deployments (enterprise customers who require physical isolation can still have dedicated deployments)
- it does not require changes to the LLM provider integrations (LLM calls are stateless and tenant-agnostic)

### 9.3 Tenant isolation guarantees

- search results must never include data from another tenant
- indexing must never write data accessible to another tenant
- action execution must be scoped to the tenant's configured actions
- RAG retrieval must only use the tenant's knowledge base
- failure in one tenant's operations must not affect other tenants

---

## 10) Risk of Not Building Multi-Tenancy

### 10.1 Missed partnership opportunities

Vertical platform vendors (AutoConverse, and others) need a multi-tenant intelligence layer. If AI Fabric cannot serve this role, they will build their own or partner with a competitor. The window for establishing these partnerships is limited.

### 10.2 Shopify vertical becomes unviable

The Shopify vertical targets thousands of merchants with small data. Single-tenant deployment per merchant is economically impossible at Shopify scale. Without multi-tenancy, the Shopify vertical remains a demo, not a business.

### 10.3 Locked into enterprise-only sales

Without multi-tenancy, AI Fabric can only serve customers large enough to justify a dedicated deployment. This limits the addressable market to enterprise accounts with long sales cycles, while the fastest-growing segment (SMB/mid-market AI adoption) remains unreachable.

### 10.4 Architectural debt compounds

Every feature built assuming single-tenancy becomes harder to retrofit. Prompt management, data migration, action configuration, and deployment administration all develop assumptions about one-customer-per-deployment. The longer multi-tenancy is delayed, the more expensive it becomes to add.

---

## 11) Success Criteria

Multi-tenant runtime is successful when:

1. a single AI Fabric deployment can serve 100+ tenants with full data isolation
2. onboarding a new tenant requires configuration, not a new deployment
3. infrastructure cost per tenant is under £1/month for small-data tenants
4. a platform partner can integrate via API and serve their customers without managing AI Fabric infrastructure
5. the Shopify vertical can onboard merchants without per-merchant deployment

---

## 12) Recommendation

Multi-tenant runtime support should be elevated to a **top-priority foundation item** because it is not a feature — it is a **business model enabler.**

Without it, AI Fabric is limited to:

- direct enterprise sales (long cycles, high touch)
- one deployment per customer (high infrastructure cost)
- enterprise pricing only (cannot serve SMB/mid-market)

With it, AI Fabric unlocks:

- B2B2B partnerships (one deal = hundreds of tenants)
- managed assistant service (self-serve onboarding)
- vertical solution packaging (one deployment per industry)
- viable unit economics for small-data customers
- the Shopify vertical at real scale

The engineering scope is contained. The business impact is transformational. This should be built alongside the control plane foundation, not after it.
