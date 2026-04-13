# Deployment Options & Vector DB Support

## Status
Draft (review requested)

## Purpose
Describe the **deployment options** for AI Fabric (self-hosted and managed) with a focus on:
- the lowest-headache path for startups
- dev vs prod environments
- vector database integration options (embedded vs managed vs BYO vs external retrieval)

This document complements:
- `changes/release/SHIPPING_PROCESS_AND_DISTRIBUTION_OPTIONS.md`
- `changes/Productization/PHASE_E_CONTROL_PLANE_AND_PROVISIONING_PLAN.md`

---

## Guiding principle: minimize operational surfaces
For early productization, prefer:
- one runtime API (`/api/chat/query`)
- one supported provider path
- one dev vector DB default
- one “recommended production” vector DB path

Anything else remains opt-in and “best effort”.

---

## Deployment models (data plane)

### Model 1 — “BYO app” (library embed)
Customer embeds AI Fabric into their existing Spring Boot app.

Use when:
- the customer already has an API gateway and auth
- you want minimal hosting responsibility

Vector DB options:
- customer manages and configures it (direct module or external retrieval)

### Model 2 — “AI Fabric Runtime” (self-hosted service)
You provide a dedicated runtime app that customers deploy.

Use when:
- you want a consistent support story
- you want language-agnostic client integrations (Shopify backend, Next.js, Python, etc.)

Recommended packaging:
- Docker image (primary)
- runnable Jar (secondary)

### Model 3 — Managed AI Fabric (Phase E)
You operate:
- the runtime (or a multi-tenant fleet)
- provisioning automation (vector DB per merchant/env)
- entitlements + licensing

This is the long-term “least work for customers, most work for us” model.

---

## Environments: dev vs prod

### Dev environment (recommended)
Goal: “clone → run → try”.

Recommended defaults:
- Vector DB: **Lucene** (embedded, no external service)
- Storage: local filesystem path for Lucene indexes
- Provider: single supported provider for v0 (OpenAI) to keep setup simple

Notes:
- This environment is designed to be disposable.
- It’s okay if it’s single-node and not horizontally scalable.

### Prod environment (recommended)
Goal: “reliable, isolated, observable”.

Recommended defaults:
- Vector DB: **managed Qdrant** (or customer-managed Qdrant)
- Runtime: Docker on ECS/K8s
- Secrets: AWS Secrets Manager / Kubernetes secrets
- Observability: logs + metrics + tracing hooks

---

## Vector DB integration options

### Option A — Embedded Lucene (dev default)
How:
- include `ai-infrastructure-vector-lucene`
- store indexes on disk

Pros:
- zero external dependencies
- perfect for demos and local development

Cons:
- not ideal for large-scale multi-tenant production

### Option B — Dedicated Qdrant per merchant (prod recommendation)
You stated: **dedicated Qdrant per merchant**. There are two practical interpretations:

#### B1) Dedicated Qdrant instance/cluster per merchant (strongest isolation)
**Decision (selected):** This is the default recommendation when “most isolated per merchant” is required.

Pros:
- cleanest isolation boundary
- easiest to reason about security and noisy-neighbor risk

Cons:
- highest cost and provisioning overhead

#### B2) Shared Qdrant cluster, dedicated collection per merchant (practical default)
Pros:
- still strong logical isolation when enforced consistently
- far cheaper and easier to provision

Cons:
- requires strict naming + access controls + guardrails in code

Recommendation:
- Use **B1** for production when you want the simplest, strongest isolation story.
- Consider **B2** later only if cost/provisioning overhead becomes a blocker and you’re willing to take on
  additional multi-tenant guardrails.

### Option C — Customer-managed vector DB (BYO)
Customer provides:
- endpoint
- API key
- collection naming rules

You provide:
- runtime config surface + validation
- optional “smoke test” endpoint and health checks

Pros:
- minimal ops burden for you

Cons:
- harder support story (everyone’s setup differs)

### Option D — External retrieval endpoint (documents-only)
Customer runs their own retrieval service (vector DB + chunking + indexing), and AI Fabric calls it.

Use when:
- customer already has a RAG platform (or wants one)
- you want to be domain-agnostic and avoid owning ingestion

This aligns with the “documents-only retrieval connector” approach (Phase C).

Pros:
- clean boundary (AI Fabric orchestrates; customer retrieves)
- easiest path for regulated data boundaries

Cons:
- you must define a stable retrieval contract and handle timeouts/SLAs

---

## Provisioning automation (optional, progressive)

### Level 0 — Manual provisioning (fastest)
You publish:
- “how to create Qdrant project + API key”
- “how to configure runtime env vars”

Use when:
- v0/v1
- you want minimal moving parts

### Level 1 — Templates (AWS-first)
You publish:
- Terraform/CDK templates to provision Qdrant (or a self-hosted Qdrant on ECS/K8s)
- runtime service + secrets wiring

Use when:
- you want “least headache” for a specific cloud (AWS)

### Level 2 — Control plane MVP (Phase E)
You build:
- provisioning worker
- entitlements + tenant registry
- automated per-merchant vector DB provisioning

This is defined in:
- `changes/Productization/PHASE_E_CONTROL_PLANE_AND_PROVISIONING_PLAN.md`

---

## What we should recommend publicly (simple)

### v0 recommendation
- Dev: Lucene + OpenAI + reference app
- Prod: “not supported yet” (or “bring your own infra”)

### v1 recommendation
- Dev: Lucene
- Prod: Qdrant (managed), with per-merchant isolation (collection per merchant)

---

## Open questions to confirm (for next iteration)
Resolved decisions:
- “Dedicated Qdrant per merchant” = **per-merchant cluster/instance** (B1).
- v1 runtime = **one runtime per customer** (single-tenant runtime).
