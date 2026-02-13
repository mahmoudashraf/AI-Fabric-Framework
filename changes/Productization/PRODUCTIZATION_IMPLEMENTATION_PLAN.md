# Productization Implementation Plan (Hosted + Language-Agnostic) — V1

This document describes how to productize AI Fabric from a framework into a **hosted, self-serve platform** that:
- sells **curated packs + licensing** (modes, prompt overlays, routing optimizations),
- remains **domain-agnostic** (no built-in commerce entities/logic/actions),
- lets customers define:
  - their **actions** (contracts + endpoints),
  - their **vector spaces** (if they want retrieval),
  - optionally their **documents-only retrieval endpoint** (customer-owned RAG retrieval).

This plan is designed to align with existing framework contracts and developer docs in:
- `Final_Documentation/Development_Guides/`
- `Final_Documentation/System_Archtecture_Guides/`

---

## Status (as of 2026-02-13)

### Implemented in code (framework/runtime building blocks)

- Curated packs loader + prompt overlays (core)
- Phase B — external actions (file-based) + connector execution:
  - `ai-infrastructure-actions-connector`
  - optional DB-backed action registration: `ai-infrastructure-actions-registry`
  - optional “zero-config” Liquibase runner: `ai-infrastructure-actions-registry-liquibase`
- Phase C — external retrieval (documents-only):
  - `ai-infrastructure-retrieval-connector`
- Phase D — ingestion API for “managed retrieval” (opt-in):
  - `ai-infrastructure-data-sync`
- Customer-side Relay runnable service (implements the Customer Connector API):
  - `ai-infrastructure-relay`

### Not implemented yet (platform/product layers)

- Phase E — control plane MVP (projects/environments/deployments, licensing/entitlements issuance, self-serve onboarding UI)
- Hosted provisioning automation (AWS templates, per-customer vector DB provisioning + lifecycle)
- Billing/subscriptions + marketplace packaging/versioning (later)

---

## 0) Non-goals (V1)

To keep V1 focused and shippable:
- No “full marketplace” (discovery + install + billing + versioned vertical packs) on day 1.
- No multi-tenant data-plane runtime. **Each customer runtime is isolated** (one deployment per customer).
- No enterprise-only requirements (SOC2 workflows, private link, mTLS everywhere) unless needed for an early design partner.
- No domain bundles that embed business logic into AI Fabric core. Packs provide **configuration + prompts only**.

---

## 1) Product boundaries (what you sell vs what customers bring)

### 1.1 You sell: curated packs + license

Curated packs are the “product unit” that customers pay for. Packs include:
- orchestration profile + modes configuration
- position routing defaults (e.g. UI `"landing"` → mode `"navigator"`)
- prompt bundle overlays (`ai.prompts.bundle.overlays`)

Reference:
- `Final_Documentation/Development_Guides/CURATED_MODES_PACKS_GUIDE.md`

### 1.2 Customers bring: actions + data + retrieval

Customers define:
- **Action catalog**
  - file-based contracts in V1 (uploaded / mounted into the runtime)
  - DB-backed registration (optional; supported via register/deregister controller)
- **Action execution endpoints**
  - any language, any stack
  - reached via a single “Customer Connector API” base URL
- **Vector spaces** (optional)
  - only needed if they want retrieval
  - naming is a customer-owned contract (`vectorSpace` is treated as an explicit enum by the prompts)
- **Documents-only retrieval endpoint** (optional)
  - customer returns documents/chunks
  - AI Fabric performs generation and orchestration

Reference:
- Actions + confirmations model: `Final_Documentation/Development_Guides/ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md`
- Connector + relay architecture: `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`

---

## 2) Platform architecture (control plane vs data plane)

### 2.1 Control plane (multi-tenant)

The control plane is the SaaS layer that manages:
- orgs/users/auth
- projects/environments
- license/entitlements
- configuration (pack selection, connector URL, retrieval config, actions contract source)
- provisioning and upgrades for customer runtimes
- observability surfaces (logs/metrics links)

The control plane is **multi-tenant**, but it does not store customer business data.

### 2.2 Data plane runtime (isolated per customer)

The data plane is the actual AI Fabric runtime that processes user requests.

Per customer runtime includes:
- orchestration pipeline
- intent extraction + action handling + confirmations
- chat session storage (optional)
- optional internal vector DB + indexing
- connector caller (for actions + retrieval)

The runtime is **single-tenant** (one customer per deployment).

---

## 3) The “language-agnostic” contract (what any customer integrates)

V1 needs exactly three integration surfaces.

### 3.1 Orchestration API (public)

This is the API that the customer UI/backend calls to get “smart behavior”.

Recommended to align with existing request contract:
- `POST /api/chat/query`
- request fields: `query`, `conversationId`, `userId`, `sessionId`, `position`, `mode`, `attachments`, `activeAttachmentIds`

Reference:
- `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`

### 3.2 Customer Connector API (private integration boundary)

This is what AI Fabric calls to execute customer-defined actions and/or retrieve documents.

Recommended endpoints:
- `POST /actions/execute`
- `POST /retrieval/search` (documents-only retrieval)

Reference:
- `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`

### 3.3 Data ingestion API (only if AI Fabric hosts retrieval)

If the customer wants AI Fabric to manage vector search, they need a push API.

V1 should implement a generic ingestion contract (no domain coupling):
- upsert document/entity content into a `vectorSpace`
- delete by `(vectorSpace, id)`
- optional batch ingestion
- optional delta sync checkpoints (V2)

This fills the current “external sync API gap” called out in:
- `changes/release/ORCHESTRA_MODEL_VALIDATION_ANALYSIS.md`

---

## 4) Runtime capabilities to productize (V1)

### 4.1 Packs (modes + prompts + routing) are first-class

In runtime config:
- `ai.curated.pack=<packId>` is the primary enablement knob.
- Pack YAML must load with **lowest precedence**, so customer overrides are explicit and deterministic.

Reference:
- `Final_Documentation/Development_Guides/CURATED_MODES_PACKS_GUIDE.md`

### 4.2 Action system must support two backends

Unify action behavior while allowing different execution implementations:
- **Local**: `@AIAction` handlers (Java)
- **Connector**: file-defined actions executed over HTTP

Contracts must remain identical:
- missing required params → `CLARIFICATION_REQUIRED`
- confirmable actions → `CONFIRMATION_REQUIRED`
- yes/no confirmation → correct execution + interceptors

References:
- `Final_Documentation/Development_Guides/ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md`
- `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`

### 4.3 Documents-only external retrieval (customer-owned RAG)

Support a mode where:
- customer returns documents/chunks + citations
- AI Fabric generates the final answer (so packs still matter)

This avoids forcing customers to adopt your indexing pipeline on day 1.

### 4.4 Optional internal retrieval (AI Fabric-managed vector DB)

For customers who want “turnkey” retrieval:
- AI Fabric hosts a vector DB per customer
- customers push documents/entities via ingestion API
- customers define vector spaces (names) and choose which are searchable

This can be introduced as “V1 optional” and become the default later.

---

## 5) Licensing and entitlements (how curated packs become a product)

### 5.1 V1 approach: pack gating via entitlements token

Control plane issues an **entitlements token** to each runtime deployment, containing:
- allowed pack IDs (and pack versions if you version packs)
- feature gates (examples):
  - advanced RAG mode allowed
  - external retrieval allowed
  - action registration API allowed
  - chat session persistence allowed
- quotas (requests/day, tokens/day, max vector spaces, max actions)

Runtime validates this token (offline verification via signature).

### 5.2 Enforcement points (must be fail-closed)

Enforce in two places:
- **Control plane**: hide/disable unentitled features in UI (nice UX)
- **Runtime**: deny/ignore unentitled configuration (security + correctness)

Never rely on “UI-only” gating.

---

## 6) Control plane: minimal data model (V1) - control plane as service

### 6.1 Entities

Recommended entities:
- **Organization**
- **User**
- **Project**
- **Environment** (dev/stage/prod)
- **Deployment**
  - runtime version
  - status + health
  - runtime URL
  - issued API keys
- **License / Entitlements**
- **PackSelection**
  - `packId`
  - explicit overrides (optional)
- **ConnectorConfig**
  - connector base URL
  - auth method + secret refs
  - allowlist options
- **ActionsCatalogSource**
  - file upload reference in V1
  - DB source (optional; supported via actions registry API)
- **RetrievalConfig**
  - `mode = internal_vector_db | external_documents_endpoint`
  - external retrieval URL + auth refs (if external)
- **VectorSpaceConfig** (optional in V1)
  - known vector space names (for UI + validation)

### 6.2 Control plane APIs (example)

These are illustrative; naming is flexible.

- `POST /v1/projects`
- `POST /v1/projects/{id}/environments`
- `POST /v1/environments/{id}/deployments` (provision runtime)
- `PUT /v1/environments/{id}/pack` (select pack + overrides)
- `PUT /v1/environments/{id}/connector` (connector base URL + auth)
- `PUT /v1/environments/{id}/actions-catalog` (upload contract file)
- `PUT /v1/environments/{id}/retrieval` (internal vs external)
- `POST /v1/environments/{id}/rotate-keys`
- `GET /v1/deployments/{id}/health`

---

## 7) Provisioning model (how a customer gets an isolated runtime)

### 7.1 Runtime packaging

Ship an official runtime container image that contains:
- ai-infrastructure core + web endpoints + chat session (optional)
- curated pack loader
- connector caller (actions + retrieval)

The runtime should be configurable purely via:
- environment variables
- mounted config files (actions contract, prompt overlays if needed)

### 7.2 Provisioning strategy (pragmatic)

V1 recommended:
- Kubernetes (one namespace per customer) OR one “deployment per customer” in your hosting provider
- attach a small metadata DB (Postgres) per customer OR a dedicated schema in a per-customer DB instance
- vector DB per customer only if internal retrieval is enabled (Qdrant is a good default)

Make it easy to run “one runtime per prospect” first; automate later.

### 7.3 Upgrades

Runtime upgrades should be:
- controlled by control plane (roll forward/rollback)
- recorded per deployment (version, migration state)

---

## 8) V1 onboarding UX (the “sells itself” path)

The onboarding flow must end with a working integration.

### Step 1 — Create project
- pick runtime region
- get an “environment URL” + API key

### Step 2 — Choose a curated pack (licensed)
- e.g. `catalog`, `commerce`, `support` (names illustrative)
- show what the pack changes: modes + routing + prompt overlays

### Step 3 — Configure retrieval
Offer two choices:
- **External documents-only retrieval endpoint** (fastest path)
- **AI Fabric managed vector search** (requires ingestion API)

### Step 4 — Configure actions
- upload `ai-actions.yml` (action catalog contract)
- set connector base URL (or enable relay flow)
- run “validate actions” (dry-run: schema checks + connectivity checks)

### Step 5 — Try it in a playground
- call the environment runtime with:
  - `position` routing
  - attachments + activeAttachmentIds
  - confirmation flow (yes/no)

### Step 6 — Integrate
Provide:
- API key + base URL
- copy/paste snippets for Node/Python/curl
- UI embed snippet (optional; can be “later”)

---

## 9) Security baseline (hosted, not enterprise-heavy)

Even for startups, hosted action execution needs guardrails:
- **No arbitrary URLs from the model**
  - AI Fabric calls only the configured connector base URL
- **SSRF protections**
  - block link-local/private ranges unless explicitly allowed (relay is the safer default)
- **Request signing**
  - HMAC signature with timestamp/nonce (recommended)
- **Idempotency**
  - required for mutating actions
- **Timeouts + retries**
  - retries only safe with idempotency
- **PII hygiene**
  - never log secrets
  - avoid echoing sensitive params in confirmation messages by default

References:
- framework philosophy (fail-closed): `Final_Documentation/Development_Guides/AI_FABRIC_FRAMEWORK_PHILOSOPHY.md`
- actions + connector/relay: `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`

---

## 10) Implementation phases (suggested)

### Phase A — Harden the “runtime product”
- choose the public orchestration API surface (`/api/chat/query` is already documented)
- ensure packs load deterministically
- ensure debug metadata is safe and bounded

### Phase B — External actions (file-based) + connector execution
- file-based action catalog source in runtime
- connector caller + action result contract enforcement
- collision handling: fail fast on duplicates

### Phase C — External retrieval (documents-only endpoint)
- implement HTTP retrieval provider
- enforce response contract + citations

### Phase D — Ingestion API (optional in V1, required for “managed retrieval”)
- build `POST /sync/entity` / `POST /sync/doc` style APIs
- support delete + batch + minimal checkpointing

Implemented in code (opt-in module):
- `ai-infrastructure-data-sync` exposes `/api/ai/data-sync/*` endpoints for upsert/delete/batch
- Guide: `changes/Productization/DATA_SYNC_PUSH_API_GUIDE.md`

### Phase E — Control plane MVP
- project/env/deploy UI + APIs
- pack selection + entitlement issuance
- connector + actions catalog config
- “green checks” validation page

---

## References (existing repo docs)

- Packs:
  - `Final_Documentation/Development_Guides/CURATED_MODES_PACKS_GUIDE.md`
- UI request contract:
  - `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`
- Actions + confirmations:
  - `Final_Documentation/Development_Guides/ACTIONS_AND_CONFIRMATION_INTERCEPTORS_GUIDE.md`
- Actions connector + relay:
  - `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`
- Orchestration optimization:
  - `Final_Documentation/System_Archtecture_Guides/ORCHESTRATION_OPTIMIZATION_GUIDE.md`
- Normalization + pipeline rules:
  - `Final_Documentation/System_Archtecture_Guides/NORMALIZATION_AND_ORCHESTRATION_GUIDE.md`
