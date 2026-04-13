# Shopify App Implementation Plan (AI Fabric) — Dev (Lucene) + Prod (Managed Vector DB)

This document describes a **high-level but detailed** implementation plan for a **Shopify App** that productizes AI Fabric for Shopify merchants while preserving the platform principles:
- **Domain-agnostic core** (AI Fabric runtime ships orchestration + prompts + safety + connectors; not business logic)
- **Curated packs + licensing** enable modes and optimizations (not domain entities)
- **Actions are contract-driven**, confirmation-aware, and can execute locally or via Connector/Relay
- **Retrieval is pluggable**:
  - AI Fabric-managed vector DB (dev/prod)
  - customer-owned **documents-only retrieval endpoint** (optional)

References:
- Actions + confirmations (V5): `Final_Documentation/Development_Guides/ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md`
- Connector + relay (actions + retrieval boundary): `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`
- Productization plan baseline: `changes/Productization/PRODUCTIZATION_IMPLEMENTATION_PLAN.md`
- Chat request contract (UI ↔ runtime): `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`

---

## 0) Goals

### 0.1 What “success” looks like (V1)
- A Shopify merchant installs the app and gets a **working dev environment** immediately:
  - embedded “playground” inside Shopify Admin
  - storefront widget option
  - semantic product discovery + “smarter than chatbot” flows
  - **Lucene vector DB** (fast, zero external dependencies)
- When the merchant upgrades to “Prod”, we provision/configure a **production vector DB** and run a full re-sync.
- Actions are safe by default:
  - missing params → clarification
  - writes → confirmation required
  - yes/no confirmations → correct execution (including interceptors)
- Merchants can optionally extend:
  - add **custom actions** (their APIs) via action contract + connector forwarding
  - add **external documents-only retrieval** (their retrieval endpoint)

### 0.2 Non-goals (V1)
- Full “multi-tenant, multi-region enterprise runtime” from day 1.
- Indexing PII-heavy datasets by default (orders/customers). Start with low-PII sources.
- Perfect “handoff DB ownership” migration tooling. Prefer “re-sync to new DB” as the handoff mechanism.

---

## 1) System Overview (What runs where)

### 1.1 Components

**A) Shopify App (Embedded Admin UI)**
- Runs inside Shopify Admin (embedded app).
- Setup wizard + health checks + “sync status” + plan/billing page.

**B) Shopify App Backend (Your service)**
Primary responsibilities:
1. Shopify OAuth + token storage + scope management.
2. Webhook receiver + job queue + sync coordinator.
3. Implements the **Customer Connector API** for action execution:
   - `POST /actions/execute` (shopify actions + optional forwarding to merchant connector)
4. Calls AI Fabric ingestion APIs to push content for indexing (dev/prod).
5. Issues storefront widget tokens and proxies storefront chat calls (so you do not expose AI Fabric API keys to browsers).

**C) AI Fabric Runtime (Data-plane)**
- Orchestration pipeline + intent extraction + confirmations + retrieval + generation.
- Dev runtime uses `ai.vector-db.type=lucene`.
- Prod runtime uses an external vector DB provider (recommended: Qdrant/Weaviate).

**D) Optional: Customer-side Relay**
- For merchants who need actions to call internal systems without exposing them publicly.
- Still uses the same connector contract; may be adopted later.

### 1.2 Data boundaries
- Shopify tokens must never be stored in the browser; keep in Shopify App Backend.
- AI Fabric runtime should not need Shopify credentials if actions execute via connector.
- Retrieval indexing content is **merchant-owned**; ensure strong uninstall cleanup semantics.

---

## 2) Environments Model (Two environments per merchant)

### 2.1 Dev Environment (default on install)
Purpose: “Try it immediately.”

**Runtime**
- Hosted by you.
- Configuration:
  - `ai.vector-db.type=lucene`
  - Lucene index on a persistent volume.
  - Curated pack enabled by license (likely `commerce` or a new `shopify` pack).

**Data**
- Run a **lightweight initial sync** (subset) to show value quickly:
  - first N products (or last updated)
  - policies/pages (small)
- Optionally include demo-only “seed prompts”/modes for onboarding UX.

**Lifecycle**
- Dev environment can be auto-paused after inactivity to reduce cost.
- Dev data can be re-synced at any time (idempotent upserts).

### 2.2 Prod Environment (after plan upgrade)
Purpose: stable performance + durable storage.

**Runtime**
- Hosted by you OR by the merchant (BYOC later).

**Vector DB**
Recommended default: **Qdrant** (or Weaviate) because:
- supports metadata filtering + scan (helps governance / cleanup / retention)
- can be self-hosted via AWS template or consumed as managed service

**Migration**
- Do not “migrate Lucene indexes”. Instead:
  - create/configure prod vector DB
  - trigger full sync job from Shopify → ingestion API (reindex)

---

## 3) Vector DB Provisioning (Prod)

This is the core “prod readiness” decision. Provide a default path and keep alternatives.

### 3.1 Option A — AI Fabric-managed vector DB (recommended for Shopify merchants)
**What it means**
- You operate the vector DB (or pay for a managed vendor) and allocate capacity per merchant.

**Chosen V1 implementation: dedicated Qdrant per merchant**
- Provision a **dedicated Qdrant instance** (or dedicated managed Qdrant “cluster/project”) per merchant **Prod** environment.
- Configure the merchant’s AI Fabric prod runtime with that Qdrant endpoint + API key.
- Uninstall cleanup becomes trivial (delete the Qdrant instance / drop the project / wipe the volume).

**Why this is the default**
- Simplest data isolation story (no cross-tenant leakage risk).
- Simplifies deletion semantics and support (“blast radius = 1 merchant”).
- Lets you keep the runtime and orchestration logic fully domain-agnostic.

**Operational model (how you run it)**
- Kubernetes: one namespace per merchant, run Qdrant as a `StatefulSet` + PV.
- ECS/Fargate: one Qdrant service per merchant + persistent storage (EBS via EC2/ECS or EFS where acceptable).
- Managed vendor: create a dedicated Qdrant resource per merchant via provider API (preferred when available).

**Pros**
- Minimal merchant ops.
- Fast onboarding.

**Cons**
- You own uptime + scaling.
- “Handoff later” is a re-sync operation, not a DB transfer.

### 3.2 Option B — Customer-managed vector DB (AWS template)
**What it means**
- You provide an AWS IaC template (Terraform/CloudFormation/CDK) to deploy Qdrant (recommended) in the merchant’s AWS/VPC.
- Merchant owns infrastructure and credentials.

**Pros**
- Clean “handoff”: they own it from day 1.
- Fits regulated customers later.

**Cons**
- Higher merchant friction; not great for most Shopify SMB.

### 3.3 Option C — Bring-your-own managed vendor (Qdrant Cloud / Pinecone / Weaviate Cloud)
**What it means**
- Merchant (or your integrator team) supplies endpoint + API key.
- Runtime auto-creates collections/classes as needed.

**Pros**
- You avoid operating DB servers.
- Merchant can choose vendor.

**Cons**
- Merchant friction (accounts, keys, pricing surprises).

### 3.4 Recommendation (V1)
- Default: **Option A** (AI Fabric-managed) with **dedicated Qdrant per merchant**.
- Add Option C as “advanced”.
- Add Option B for “Pro/Enterprise later”.

---

## 4) Data Sync (Shopify → Ingestion Push API)

### 4.1 Principle
Shopify merchants do not push to your ingestion API directly.
Your **Shopify App Backend** performs sync:
- reads Shopify via Admin API
- transforms to AI Fabric ingestion payloads
- pushes to AI Fabric runtime ingestion endpoint(s)

### 4.2 Sources to index (V1 default)
Start low-PII:
- **Products** (title, description, tags, vendor, type, options, variants summary)
- **Collections** (title, description)
- **Pages / Policies** (shipping, returns, warranty, privacy)

Avoid indexing by default (PII-heavy):
- Customers
- Orders (unless explicitly enabled + strong PII/compliance story)

### 4.3 Vector spaces (customer-defined, defaults provided)
Ship defaults (merchant can enable/disable):
- `product`
- `collection`
- `page`
- `policy`

Notes:
- In the framework, “vectorSpace” maps to `entityType`.
- The curated pack should list the allowed vectorSpace values in the knowledge base overview so the model doesn’t invent new ones.

### 4.4 Proposed ingestion API (V1 contract)
This is the generic “push indexing” API described in the productization plan.

Recommended endpoints (illustrative):
- `POST /api/sync/upsert`
- `POST /api/sync/delete`
- `POST /api/sync/batchUpsert` (preferred for Shopify bulk)

Upsert payload (example):
```json
{
  "vectorSpace": "product",
  "id": "gid://shopify/Product/123",
  "content": "Sony WH-1000XM5 — ...",
  "metadata": {
    "tenantId": "my-shop.myshopify.com",
    "title": "Sony WH-1000XM5",
    "sku": "SKU-123",
    "handle": "sony-wh-1000xm5",
    "url": "/products/sony-wh-1000xm5",
    "priceMin": 249,
    "currency": "USD",
    "tags": ["headphones", "sony"]
  }
}
```

Delete payload (example):
```json
{ "vectorSpace": "product", "id": "gid://shopify/Product/123", "metadata": { "tenantId": "my-shop.myshopify.com" } }
```

Requirements:
- Upserts must be idempotent.
- Deletes must succeed even if the vector is missing (idempotent).
- Dev/prod sync jobs share the same shape; only target differs.

### 4.5 Sync modes

**Initial sync**
- Trigger after install + after prod enablement.
- Use Shopify GraphQL bulk operations where possible.
- Emit progress events: total items, processed, failed, last error.

**Incremental sync (webhooks)**
- Subscribe to resource changes:
  - products create/update/delete
  - collections create/update/delete
  - pages/policies update
- Webhook handler enqueues jobs; jobs perform upsert/delete to ingestion API.

**Resync**
- Merchant presses “Resync now” → full reindex job.
- Used as a recovery mechanism.

### 4.6 Uninstall cleanup
On `app/uninstalled` webhook:
- revoke tokens
- disable storefront widget tokens
- delete indexed vectors for the merchant:
  - easiest: if vectors are isolated per tenant (collection/namespace), drop that scope
  - else: scan by `tenantId` → delete per entityId
- delete chat sessions (if enabled) for that tenant

---

## 5) Actions (Shopify + Optional Merchant Extensions)

### 5.1 Principle: Actions are contract-driven and confirmation-aware
All action behavior must match V5:
- clarify missing params
- require confirmation for writes
- resolve yes/no via confirmation interceptors

Reference: `Final_Documentation/Development_Guides/ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md`

### 5.2 Default Shopify action catalog (V1)
Ship a default action catalog as a file-based contract (examples; names are illustrative):

READ actions (no confirmation):
- `search_products` (returns `_items` + `_count`)
- `get_product_details`
- `compare_products`

WRITE actions (confirmation required):
- `add_to_cart`
- `remove_from_cart`
- `apply_discount_code`
- `create_checkout_link` (recommended over “place order”)

Notes:
- Prefer “checkout link” flow: Shopify handles payment + fraud + finalization.
- If you later support orders, do it as actions-only (not indexed) with explicit PII rules.

### 5.3 Execution: Connector API implemented by Shopify App Backend
AI Fabric runtime calls:
- `POST /actions/execute` on the Shopify App Backend

The backend:
- validates actionId is registered
- validates params (defense-in-depth; runtime already validates)
- calls Shopify Admin/Storefront APIs
- returns `ActionResult` in the canonical contract

Reference: `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`

### 5.4 Merchant custom actions (optional, V1 “power user”)
Support custom actions without changing the runtime “single connector base URL” model:
- Shopify App Backend remains the connector base URL.
- Merchant config optionally includes:
  - `customConnectorBaseUrl` (their endpoint)
  - `customActionsContract` (file upload)
- Execution routing:
  - if actionId is in Shopify default set → execute Shopify
  - else → forward to merchant connector

Collision handling:
- If merchant uploads an action that collides with a Shopify default action name:
  - fail fast on registration (reject upload) OR require explicit override flag (recommended: reject)

---

## 6) Storefront Integration (No API keys in the browser)

### 6.1 Two supported UX surfaces
1) **Shopify Admin playground** (embedded app tab)
2) **Storefront widget** (theme app extension / script tag)

### 6.2 Proxy pattern (recommended)
To avoid exposing AI Fabric runtime keys to the browser:
- Storefront widget calls a Shopify App Proxy endpoint (or your backend directly with signed token).
- Shopify App Backend calls AI Fabric runtime `POST /api/chat/me/query` for the verified caller contract.

The backend injects:
- correct `tenantId` (shop domain)
- `sessionId` + `userId` strategy
- optional Shopify context (cartId, customerId) in metadata/attachments

Reference request contract:
- `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`

---

## 7) Security, Safety, and Compliance (Startup-friendly baseline)

### 7.1 Shopify security basics
- Verify webhook HMAC signatures.
- Store Shopify access tokens encrypted (KMS/Secrets Manager).
- Minimal scopes; upgrade scopes only when enabling advanced features.

### 7.2 AI Fabric action safety
- Writes require confirmation (default).
- Idempotency keys for write actions.
- Strict parameter validation (runtime + connector).

### 7.3 PII posture
V1 default:
- index only low-PII content (products/pages/policies)
- keep orders/customers out of vector DB unless explicitly enabled

If enabling orders later:
- ensure PII module + redaction policies are on
- do not leak sensitive fields in confirmations or logs

### 7.4 Data deletion guarantees
- Uninstall cleanup is mandatory and tested:
  - vectors
  - chat sessions (if enabled)
  - connector tokens

Governance module can later formalize retention/deletion enumeration:
- `Final_Documentation/User_Guides/GOVERNANCE_AND_COMPLIANCE_GUIDE.md`

---

## 8) Observability & Supportability (V1 minimum)

Shopify app UI should show:
- “Dev environment status” (UP/DOWN, last deploy)
- “Prod environment status” (if enabled)
- Sync status:
  - last full sync time
  - webhook queue lag
  - failed items + retry count
- Action execution logs (bounded, redacted):
  - actionId, success/failure, latency
- Retrieval stats:
  - vector counts per vectorSpace
  - last index update

---

## 9) Implementation Phases (Recommended)

### Phase 1 — Dev MVP (Lucene)
- Shopify app install → provision dev runtime (Lucene)
- Initial sync for products + policies/pages
- Embedded admin playground + basic storefront widget
- Default Shopify action set via connector execution

### Phase 2 — Prod enablement + vector DB provisioning
- Add plan/billing gate → create prod environment
- Choose default prod vector DB strategy (recommended: AI Fabric-managed Qdrant)
- Full re-sync to prod
- Hardening: retries, rate limits, job durability

### Phase 3 — Merchant extensions
- Optional custom actions contract upload + forwarding connector
- Optional external documents-only retrieval endpoint config

### Phase 4 — Hardening + scale
- Multi-region
- Isolation strategy improvements (shared DB vs dedicated)
- Governance/retention policies
- Better handoff tooling (re-sync, export logs, etc.)

---

## 10) Open Questions (to resolve early)
- Prod vector DB isolation: **decided** → dedicated Qdrant per merchant.
- What is the default curated pack for Shopify? (`commerce` vs new `shopify` pack)
- Do we support carts/checkout in V1 storefront widget, or keep V1 to “product discovery + support” only?
- Do we store chat sessions per merchant by default, or keep memory stateless?
