# Phase E — Control Plane MVP + Provisioning Automation (Hosted, Self‑Serve) — V1

This document defines the **next product layer** on top of the existing AI Fabric runtime building blocks:
- a **multi-tenant control plane** (SaaS) for onboarding, licensing, configuration, and deployment lifecycle, and
- **provisioning automation** for isolated per-customer **data plane** runtimes and (optional) managed vector databases.

It is written to align with the platform boundaries described in:
- `changes/Productization/PRODUCTIZATION_IMPLEMENTATION_PLAN.md`
- `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`
- `changes/Productization/RELAY_IMPLEMENTATION_AND_DEPLOYMENT_GUIDE.md`
- `changes/Productization/DATA_SYNC_PUSH_API_GUIDE.md`

This is a **plan/spec** (not an implementation guide for customers).

---

## Status (as of 2026-02-13)

- **Not implemented yet:** Control plane service (API + UI) and provisioning automation.
- **Implemented in code (data plane building blocks):**
  - Packs + prompt overlays (core)
  - Actions connector + optional DB registry (+ optional Liquibase runner module)
  - Retrieval connector (documents-only external retrieval)
  - Data sync push API (optional managed ingestion)
  - Relay runnable service (customer-side connector implementation)

---

## 0) Product premise (restate constraints)

### 0.1 What you sell (the product unit)
- **Curated packs + licensing** that enable:
  - modes (orchestration behavior)
  - prompt overlays
  - routing defaults
  - feature gates and quotas

### 0.2 What customers bring (domain-agnostic contract)
- **Actions**
  - action catalog (file-based V1, DB-backed optional)
  - action implementations (HTTP endpoints in any language)
- **Vector spaces** (names + intended meaning)
- Retrieval choice:
  - **External retrieval endpoint** (documents-only; customer-owned), or
  - **Managed retrieval** (you host vector DB; customer pushes documents/entities)

### 0.3 Non-goals (MVP)
- No enterprise compliance workflows (SOC2 evidence pipelines, private link, etc.) unless required by an early design partner.
- No marketplace billing/versioning/discovery in V1 (packs can be “select from a list”).
- No multi-tenant data-plane runtime: **one isolated runtime deployment per customer environment**.

---

## 1) Architecture overview (control plane vs data plane)

### 1.1 Control plane (multi-tenant SaaS)
Responsibilities:
- auth + org/user management
- projects/environments
- pack selection + configuration (connector/retrieval/actions sources)
- **licensing/entitlements issuance** (signed token for runtimes)
- provisioning orchestration + deployment lifecycle (create/upgrade/rollback/delete)
- observability surfaces (links to logs/metrics/health)
- audit events (admin actions)

The control plane must **not** store customer business data (products/orders/etc).

### 1.2 Data plane (single-tenant, isolated per deployment)
Responsibilities:
- run orchestration (`/api/chat/query`)
- execute actions (local `@AIAction` + connector-backed actions)
- perform retrieval:
  - internal Lucene (dev), or
  - managed vector DB (prod), or
  - external retrieval connector (customer endpoint)
- optionally expose ingestion APIs when managed retrieval is enabled

Isolation principle:
- **one deployment per customer environment** (e.g., `acme/prod`, `acme/dev`)

---

## 2) Deployment models (what the control plane must support)

### Model A — Fully managed runtime (recommended “least headache”)
You provision and operate:
- data plane runtime
- managed vector DB (optional; per customer)

Customer supplies:
- connector endpoint base URL (often a customer-side Relay)
- actions contract source (files and/or DB registry)
- optional external retrieval endpoint (instead of managed vector DB)

### Model B — Customer-hosted runtime (exportable template)
Control plane still issues entitlements + config, but the customer deploys:
- runtime (your Docker image/helm chart)
- vector DB (optional)

This model is valuable later for regulated customers without you becoming their operator.

### Model C — Hybrid (common for actions)
Even in Model A, **actions are usually customer-hosted** behind a Relay:
- AI Fabric runtime (managed by you) → customer Relay → internal services

This keeps customer systems private while you host the “AI brain”.

---

## 3) Vector database strategy (dev vs prod)

### 3.1 Dev environment default (v1)
Goal: “works in 5 minutes”.
- Use **Lucene vector** by default (embedded, zero external infra).
- Package as:
  - demo runtime config, and/or
  - docker-compose for local development.

### 3.2 Prod managed retrieval (v1)
Goal: minimize operational burden while scaling.

Supported approach (per prior decision):
- **Dedicated Qdrant per merchant/environment** (strong isolation, simpler reasoning).

Provisioning options (choose one as V1 default):
1) **Qdrant Cloud** (API-provisioned cluster per customer)
   - fastest to operate (vendor managed)
   - store `QDRANT_URL` + `QDRANT_API_KEY` in secrets manager
2) **Self-hosted Qdrant on AWS** (ECS/Fargate or EKS per customer)
   - more control, more ops burden
   - requires storage + backups + upgrades

Alternative (explicitly not the default if “dedicated per merchant” is required):
- shared Qdrant cluster with per-tenant collections (cheaper, but weaker isolation)

### 3.3 BYO retrieval endpoint (documents-only)
If the customer owns retrieval:
- do not provision a vector DB
- use `ai-infrastructure-retrieval-connector` to call the customer endpoint
- enforce documents-only boundary (no generation)

---

## 4) Control plane MVP scope (what to build first)

### 4.1 Primary onboarding flow (must exist)
1) Sign up / sign in
2) Create Organization
3) Create Project
4) Create Environment (`dev`, `prod`)
5) Configure:
   - pack selection
   - connector base URL + auth mode (apiKey/hmac)
   - actions source (file upload OR DB registry enabled)
   - retrieval mode:
     - dev: Lucene
     - prod: managed Qdrant OR external retrieval endpoint
6) Provision deployment
7) Show:
   - runtime base URL
   - runtime API key(s)
   - “try it” links and curl snippets

### 4.2 Minimal UI pages (MVP)
- Auth
- Projects list + create
- Environment settings (packs, connector, retrieval, actions sources)
- Deployments view (status, version, last health, logs link)
- API keys page (rotate/revoke)

### 4.3 Must-have control plane APIs (MVP)
- `POST /orgs`, `GET /orgs/{id}`
- `POST /projects`, `GET /projects/{id}`
- `POST /projects/{id}/environments`
- `PUT /environments/{id}/config`
- `POST /environments/{id}/deployments` (provision)
- `POST /deployments/{id}/rotate-keys`
- `DELETE /deployments/{id}` (deprovision)
- `GET /packs` (supported curated packs list)
- `POST /entitlements/issue` (internal)

---

## 5) Entitlements & licensing (production-grade baseline)

### 5.1 Token shape (runtime-consumable, signed)
Control plane issues a signed **entitlements token** per deployment containing:
- deployment id, org id, environment id
- allowed pack IDs (and optional versions)
- feature gates (examples):
  - connector actions enabled
  - DB registry enabled
  - external retrieval enabled
  - managed ingestion enabled
- quotas:
  - requests/day (or minute windows)
  - max vector spaces
  - max actions
- expiration (`exp`) and issued-at (`iat`)

Runtime requirements:
- verify signature offline (fail closed)
- reject config that enables unentitled features

### 5.2 Key management
- Control plane holds signing keys (KMS-backed).
- Rotate signing keys with overlap (kid-based verification).
- Deployment secrets stored in secrets manager (no plaintext in DB).

---

## 6) Provisioning automation (AWS-first, minimal moving parts)

### 6.1 Provisioner architecture
Recommended components:
- Control plane API (writes desired state)
- Provisioner worker (async jobs; retries; idempotent)
- Job queue (SQS) + state table (Postgres)

All provisioning actions must be:
- idempotent (safe retries)
- auditable (who requested, what changed)
- reversible (best-effort delete)

### 6.2 Data plane runtime provisioning (AWS reference)
Baseline (fastest path):
- Containerize runtime (already in repo for Relay; do the same for runtime/demo later).
- Deploy runtime to one of:
  - ECS Fargate + ALB (simplest)
  - EKS + Helm (more flexible)

Runtime config injected via:
- environment variables for secrets
- config file for non-secrets (ConfigMap style)

### 6.3 Managed Qdrant provisioning (per merchant)
If managed retrieval is selected:
- Provision Qdrant (cloud or self-hosted)
- Store connection info as secrets
- Create required collections (optional; can be lazy-created by ingestion pipeline)

Backups:
- define minimum story (snapshot schedule) for prod environments

### 6.4 Networking & SSRF safety (hosted runtime)
If you host the runtime and it calls customer endpoints:
- require customer-side **Relay** for actions by default
- enforce outbound allowlists:
  - only configured connector base URL
  - block link-local/metadata IP ranges
- require HTTPS for external endpoints (prod)

This is defense-in-depth in addition to the Relay’s SSRF-safe routing.

---

## 7) “Done” criteria (MVP acceptance)

Control plane MVP is “done” when:
- A new user can self-serve a project/environment and receive:
  - runtime URL
  - runtime API key
  - pack selection applied
- A managed prod environment can provision:
  - runtime deployment
  - dedicated Qdrant (per merchant)
- Runtime enforces entitlements fail-closed.
- Deprovision removes customer runtime and (optionally) vector DB.
- Audit trail exists for provisioning actions.

---

## 8) Suggested implementation order (engineering plan)

Phase E1 — Control plane “desired state” API (no real provisioning)
- DB schema + CRUD APIs for org/project/env/config
- Packs listing + entitlements token issuance
- Manual runtime deployment (operator-run) uses exported config

Phase E2 — Provisioning worker + AWS runtime provisioning
- job queue + worker
- ECS Fargate runtime deploy + health reporting
- secrets manager integration

Phase E3 — Managed vector DB provisioning (Qdrant per merchant)
- provision Qdrant (cloud API or ECS service)
- store credentials + wire into runtime config
- basic backup story

Phase E4 — UX hardening
- onboarding UX (templates, quickstart snippets)
- key rotation
- upgrades/rollbacks

