# Shipping Process & Distribution Options

## Status
Draft (review requested)

## Purpose
Describe **how AI Fabric is shipped** (artifacts and release steps) and the **supported distribution options** for:
- framework/library usage (BYO app)
- runnable runtime usage (hosted/self-hosted)
- future managed control-plane provisioning (Phase E)

This document is intentionally **implementation-aware** (matches today’s module layout) and **forward-looking** (calls out planned work).

---

## Definitions

### “Ship the product”
Shipping means producing **versioned artifacts** and **clear docs** that let a developer:
1) depend on AI Fabric modules (Maven)
2) run a reference “runtime” (Jar/Docker)
3) choose/plug a vector DB and an LLM provider
4) enable optional capabilities (actions, relay, retrieval connector) without forking

### “Core”
Core is the **request-handling runtime chain**:
`HTTP API → Orchestrator → (Retrieval + Actions + Confirmation) → Response`

Today the golden-path API contract is `POST /api/chat/query` documented in:
- `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`

---

## What we ship (artifacts)

### A) Maven libraries (the platform)
Ship versioned Maven artifacts from `ai-infrastructure-module/`:
- **Core orchestration**: `ai-fabric-core`
- **Web layer**: `ai-fabric-web` (REST controllers for AI Fabric)
- **RAG**: `ai-infrastructure-rag`
- **Providers**: OpenAI/Azure/Anthropic/… provider modules
- **Vector DB implementations**: Lucene/Qdrant/Pinecone/…
- **Optional capability modules** (opt-in):
  - actions connector / registry / liquibase runner
  - retrieval connector (documents-only)
  - relay service
  - chat session persistence
  - governance / pii

Notes:
- The repo currently contains a **draft naming/coordinates cleanup** plan:
  - `changes/release/ARTIFACT_COORDINATES_AND_NAMING_FIXES_CHANGE_PLAN.md`
  Until that is finalized, prefer depending on **starters** where possible.

### B) Starters (recommended consumption path)
Ship “convenience starters” that define safe defaults:
- `ai-fabric-provider-starter`: “core + provider-only baseline”
- `ai-fabric-starter`: “core + indexing (+ optional safety modules)”

Future (recommended):
- `ai-fabric-runtime-starter`: “core + web request API + runtime defaults”
  - goal: consumers add a single dependency and expose `/api/chat/query`

### C) Curated packs (the sellable value)
Ship curated packs as separate artifacts (prompt bundles + mode defaults), activated by:
- `ai.curated.pack=<pack>`

Example loader:
- `ai-infrastructure-module/ai-infrastructure-core/.../CuratedPackEnvironmentPostProcessor.java`

This is the natural place for “curated + license unlocks modes”:
- packs remain domain-agnostic (modes, prompts, constraints)
- licensing gates *pack activation* (planned, not required for v0)

### D) Runnable distributions

There are two “runnable” stories:

1) **Reference App** (examples): lives under `Real_Apps/*` and proves the framework end-to-end.
2) **Runtime Product** (ship target): a thin Spring Boot service that exposes the stable orchestration API
   (today: `POST /api/chat/query`) and loads capabilities via modules/starters.

For v0, the reference app is the golden path. Productization adds a dedicated runtime distribution.

#### Option D1 — “BYO app” (library-only)
You ship Maven artifacts only. Customers embed AI Fabric into their own Spring Boot app.

Pros:
- lowest operational burden for you
- customers control their own API surface and auth model

Cons:
- slower time-to-value for non-Spring teams
- harder to support (everyone integrates differently)

#### Option D2 — “AI Fabric Runtime” (Jar)
You ship a runnable Spring Boot application:
- exposes the request API (e.g., `/api/chat/query`)
- loads the orchestrator + optional modules
- configured entirely via env/properties

Pros:
- consistent support story (“run this runtime; call this API”)
- enables multi-language clients (language-agnostic HTTP)

Cons:
- you own runtime hardening (auth, rate limits, observability)

#### Option D3 — Docker image
Package Option D2 as a container image.

Pros:
- easiest self-host path for startups
- predictable deployment to ECS/K8s/Heroku-like platforms

Cons:
- you must document storage persistence + secrets injection clearly

#### Option D4 — Helm chart / IaC templates (optional)
Ship deploy templates (Helm/Terraform/CDK) for “one command deploy”.

Pros:
- reduces deployment friction

Cons:
- creates ongoing maintenance across cloud/K8s variants

Recommendation:
- keep this post-v0 unless you have a narrow “AWS-only” target

#### Option D5 — Managed offering (Phase E)
You ship:
- runtime (data plane) + managed vector DB provisioning + tenant isolation
- control plane for entitlements + provisioning automation

This is defined in:
- `changes/Productization/PHASE_E_CONTROL_PLANE_AND_PROVISIONING_PLAN.md`

Pros:
- best time-to-value for customers
- aligns with per-merchant managed vector DB plans

Cons:
- highest operational scope (support, security, billing)

---

## Supported distribution matrix (recommended)

### v0 (developer-friendly preview)
- **Run:** reference app from source (`Real_Apps/chat-capabilities-demo`)
- **Vector DB:** embedded Lucene (no external service)
- **Provider:** OpenAI (minimize support surface)
- **Ship:** tag + release notes + quickstart (optionally Maven publish later)

Source of truth:
- `changes/release/V0_RELEASE_PLAN_CHAT_CAPABILITIES_BASELINE.md`
- `docs/V0_QUICKSTART.md`

### v1+ (productization)
- **Run:** AI Fabric Runtime (Jar/Docker)
- **Vector DB:** pluggable (Lucene for dev, Qdrant/Pinecone/etc for prod)
- **Ship:** Maven artifacts + Docker image + deployment docs

Recommended default operating model (v1):
- **Runtime:** one runtime per customer (single-tenant runtime)
- **Vector DB:** dedicated Qdrant instance/cluster per merchant (strongest isolation)

---

## Shipping process (release steps)

### Step 1 — Freeze “supported set”
Pick the supported subset and test only that subset for the release.

For v0, the supported set and non-goals are defined in:
- `changes/release/V0_RELEASE_PLAN_CHAT_CAPABILITIES_BASELINE.md`

### Step 2 — Build gates (repeatable)
Minimum build gates:
- `mvn -f ai-infrastructure-module/pom.xml -DskipTests install`
- `mvn -f Real_Apps/chat-capabilities-demo/pom.xml test`

### Step 3 — Docs gates (developer path)
Must be true:
- a developer can follow the quickstart end-to-end
- the public API contract is documented and matches the runtime behavior

Primary docs:
- `docs/V0_QUICKSTART.md`
- `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`

### Step 4 — Publish artifacts (choose one)
Two acceptable v0 paths:

**A) Build-from-source only (fastest, lowest risk)**
- do not publish Maven artifacts yet
- rely on tag + docs + demo app

**B) Publish supported subset (better DX, higher risk)**
- requires coordinate consistency work:
  - `changes/release/ARTIFACT_COORDINATES_AND_NAMING_FIXES_CHANGE_PLAN.md`
- publish to one repo first (GitHub Packages/private) before Maven Central

#### Publishing targets (when we do publish)
Maven artifacts:
- **GitHub Packages** (fastest path for early adopters; good for private preview)
- **Maven Central** (best community reach; requires signing + stricter release process)
- **Private Maven repo** (for enterprise customers)

Container images:
- GHCR / Docker Hub / ECR (pick one and document it)

Release assets:
- Git tag + GitHub release notes
- optional “fat jar” runtime artifact for the runtime distribution (v1+)

### Step 5 — Publish runtime distribution (v1+)
If shipping the “runtime product”:
- publish a versioned Docker image
- publish a minimal “runtime config reference” (env vars + examples)
- optionally publish Helm/IaC templates

### Step 6 — Tag + release notes
Release should include:
- supported matrix (providers + vector DBs)
- known limitations
- link to demo video (optional but high value)

---

## Recommendations (least headache)
- For v0: optimize for a reliable “copy/paste quickstart”, not breadth (providers/vector DBs).
- Treat anything outside the supported set as “in repo, not supported”.
- Defer Maven Central publishing until coordinates are consistent (or publish a very small subset first).
- For prod: prefer a managed vector DB and a narrow deployment template (AWS-only) before supporting every cloud.

---

## Related documents
- `changes/release/V0_RELEASE_PLAN_CHAT_CAPABILITIES_BASELINE.md`
- `docs/V0_QUICKSTART.md`
- `changes/release/ARTIFACT_COORDINATES_AND_NAMING_FIXES_CHANGE_PLAN.md`
- `changes/release/MONOREPO_COMMUNITY_ENTERPRISE_SEPARATION_CHANGE_PLAN.md`
- `changes/release/PRE_RELEASE_CLEANUP_AND_SEPARATION_PLAN.md`
- `changes/Productization/PHASE_E_CONTROL_PLANE_AND_PROVISIONING_PLAN.md`
