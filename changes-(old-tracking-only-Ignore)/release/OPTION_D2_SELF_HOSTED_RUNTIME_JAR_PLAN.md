# Option D2 — Self‑Hosted AI Fabric Runtime (Runnable JAR) — Plan

## Status
Implemented ✅

## Goal
Ship a **domain-agnostic**, **self-hosted** Spring Boot runtime JAR that:
- exposes a stable orchestration API (`POST /api/chat/query`)
- supports **dev-first** usage with **embedded Lucene** vector DB
- supports **file-based action contracts** + connector execution (Relay/customer endpoints)
- supports **push-based ingestion** (data sync) so developers can load data without extra services

Primary use case:
- a new Chat UI (any language) calls this runtime over HTTP to validate the “product core”.

---

## Non-goals (this deliverable)
- Managed hosting / control plane (Phase E)
- Shopify App specifics (handled separately)
- Multi-tenant runtime (v1 decision is **one runtime per customer**)
- Production-grade auth/RBAC/SSO (we add minimal hooks, but don’t build enterprise IAM here)

---

## Deliverables

### 1) New runnable module
Add a new Maven module:
- `ai-infrastructure-module/ai-fabric-runtime`

It produces:
- `ai-fabric-runtime-<version>.jar` (Spring Boot runnable JAR)

### 2) HTTP API surface (stable)
Expose endpoints:
- `POST /api/chat/query` (primary orchestration entrypoint; same request contract as the demo)
- `POST /api/chat/suggestions` (optional but useful for UI)
- Conversation endpoints when chat-session is enabled:
  - `GET /api/chat/conversations`
  - `GET /api/chat/conversations/{conversationId}`
  - `DELETE /api/chat/conversations/{conversationId}`

Notes:
- Keep the request/response JSON compatible with:
  - `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`

### 3) Dev environment defaults (least headache)
Runtime ships with defaults that make it runnable locally:
- Vector DB: Lucene (`ai.vector-db.type=lucene`)
- Storage: H2 file DB for sessions/history
- Ingestion: data sync push API enabled (`ai.data-sync.enabled=true`)
- Curated pack: optional (unset by default; user may set `ai.curated.pack`)

Security defaults:
- Provide **explicit dev defaults** for required policy hooks:
  - `EntityAccessPolicy` (allow-all for dev)
  - `ChatSessionAccessControlPolicy` (basic identifier checks for dev)
- Make these defaults **configurable** so production deployments can disable them and provide strict policies.

### 4) Action contracts (productized, domain-agnostic)
Include connector-backed actions support:
- `ai-infrastructure-actions-connector`

Behavior:
- If no `ai.actions.sources` configured → no connector actions loaded.
- If sources are configured but `ai.actions.connector.baseUrl` is missing → fail fast (already implemented).

### 5) Retrieval choices (documents-only external retrieval optional)
Include documents-only retrieval connector support:
- `ai-infrastructure-retrieval-connector` (disabled by default)

### 6) Documentation
Add a runtime quickstart and config reference:
- `docs/RUNTIME_JAR_QUICKSTART.md`

Must cover:
- build + run jar
- enable OpenAI
- Lucene index path + embedding dimensions
- define vector spaces (`ai-entity-config.yml`)
- ingest via `/api/ai/data-sync/*`
- define actions contract + point to a Relay

---

## Module dependencies (initial v1 dev baseline)
The runtime JAR should include:
- `ai-fabric-provider-starter` (core)
- `ai-fabric-provider-openai` (dev baseline provider)
- `ai-infrastructure-rag`
- `ai-infrastructure-vector-lucene`
- `ai-infrastructure-chat-session`
- `ai-infrastructure-data-sync`
- `ai-infrastructure-actions-connector`
- `ai-infrastructure-retrieval-connector` (optional, but included)
- Swagger UI (springdoc) for API discovery

Rationale:
- This yields a single runnable backend that can do: **chat + RAG + actions + ingestion** with minimal setup.

---

## Implementation steps (what we will do)

### Step A — Scaffold the runtime module
- Add module folder + `pom.xml`
- Add `AIFabricRuntimeApplication` main class
- Add default `application.yml` (dev-first)
- Add default `ai-entity-config.yml` (generic `document` vector space)

### Step B — Expose `/api/chat/query` runtime controller
- Implement a controller that:
  - builds `OrchestrationContext` (userId/sessionId/conversationId/position/mode/attachments)
  - calls `RAGOrchestrator.orchestrate(query, context)`
  - returns `OrchestrationResult` under a stable wrapper response

### Step C — Add dev policy defaults (required beans)
- Add configuration that provides (when missing and enabled by property):
  - `EntityAccessPolicy`
  - `ChatSessionAccessControlPolicy`

### Step D — Add CORS support for external UIs
- Reuse the same pattern as the demo:
  - `app.cors.allowed-origins`
  - `app.cors.allowed-origin-patterns`

### Step E — Docs + examples
- Add `docs/RUNTIME_JAR_QUICKSTART.md`
- Include curl or HTTP examples for:
  - query endpoint
  - data sync upsert
  - enabling connector actions

### Step F — Build gates
- `mvn -f ai-infrastructure-module/pom.xml -DskipTests install`
- (Optional) add a minimal `@SpringBootTest` context-load test for the runtime module

---

## Acceptance criteria
- Runtime JAR builds and starts locally with Lucene + H2.
- `/api/chat/query` responds (with a clear error if provider not enabled).
- `/api/ai/data-sync/vector-spaces` works when `ai.data-sync.enabled=true`.
- Connector actions can be loaded from a file-based catalog and executed via a configured base URL.
