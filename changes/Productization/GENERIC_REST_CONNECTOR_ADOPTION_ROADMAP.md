# Generic REST Connector Adoption Roadmap (Runtime + Demo Setup)

Status: draft (2026-03-25)

This document is a practical roadmap for using the repo’s **Generic REST API Connector** service (`ai-infrastructure-generic-rest-connector`) with **AI Fabric Runtime**.

It answers:
- How do we use the REST connector?
- Do we need a new connector demo app?
- What is the recommended phased plan (including indexing and retrieval considerations)?

Related docs:
- Generic REST connector guide: `changes/Productization/GENERIC_REST_API_CONNECTOR_GUIDE.md`
- Actions catalog and connector wiring: `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`
- Retrieval connector (documents-only): `changes/Productization/RETRIEVAL_CONNECTOR_GUIDE.md`

---

## 1) What “REST Connector” Means In This Repo

The “REST connector” is the runnable service:
- `ai-infrastructure-module/ai-infrastructure-generic-rest-connector`

It implements the **Customer Connector API** endpoint used by the runtime:
- `POST /actions/execute`

But instead of implementing business logic, it routes:
- `actionId -> upstream REST endpoint`

Routing is configured via:
- `actions-routing.yml` (file-based config)

---

## 2) Do We Need To Create A New Connector Demo App?

Usually no.

You have 3 viable setups:

1. Use the existing domain connector demo (current state)
   - Service: `Real_Apps/chat-capabilities-connector-demo`
   - Pros: includes a full demo domain (products, cart, orders) and optional runtime indexing push.
   - Cons: it is a custom app; not representative of “connect to an existing API”.

2. Use the Generic REST Connector as your connector (recommended to evaluate the “API feed” model)
   - Service: `ai-infrastructure-generic-rest-connector`
   - Upstream: your real API feed (or any existing commerce API)
   - Pros: no custom connector code for each API; you only maintain config.
   - Cons: does not implement domain state itself; it forwards to upstream which must own state.

3. Use Generic REST Connector as a translation layer in front of the existing demo app (for learning)
   - Runtime -> Generic REST Connector -> existing demo app endpoints
   - Pros: lets you test routing/templating quickly without needing a real upstream system.
   - Cons: 2 services; not the final architecture.

If you need demo-only features like “reset demo + clear runtime vectors”, keep the existing demo app as a utility.

---

## 3) What The Runtime Needs (Always)

Regardless of which connector you use, **the runtime must know the action catalog** (the contract).

Runtime action catalog source:
- `ai-actions.yml` (loaded via `ai.actions.sources`)
- Default runtime path: `file:/config/ai-actions.yml` (overridable via `AI_ACTIONS_CATALOG_PATH`)

Runtime connector base URL:
- `ai.actions.connector.base-url` (env: `ACTIONS_CONNECTOR_BASE_URL`)

Common failure mode:
- `URI is not absolute`
  - Cause: `ACTIONS_CONNECTOR_BASE_URL` missing scheme
  - Fix: set it to `https://...` not just `myhost.com`

---

## 4) What The Generic REST Connector Needs

The Generic REST Connector is configured primarily via:
- `REST_CONNECTOR_ROUTING_CONFIG_LOCATION` (default `classpath:actions-routing.yml`)

The routing config defines:
- inbound auth (API key)
- upstream base URL and upstream auth
- per-action route mappings (method/path/headers/query/body and response mapping)

Minimum working config is:
- a single action route mapping
- inbound auth configured to match what the runtime sends (or allow unauthenticated for local-only)

---

## 5) Roadmap (Phased)

### Phase 0: Baseline understanding (1-2 hours)

Goal: confirm we can run the Generic REST Connector and hit `/actions/execute`.

Tasks:
- Read: `changes/Productization/GENERIC_REST_API_CONNECTOR_GUIDE.md`
- Run generic connector locally and confirm:
  - `POST /actions/execute` works for an action that has a route mapping
  - unsupported action returns `ACTION_NOT_SUPPORTED`

### Phase 1: One action end-to-end (half day)

Goal: runtime calls a connector action and gets a successful result.

Pick one READ action (example: `list_products`).

Tasks:
- Ensure runtime loads an action catalog that includes `list_products`.
- Configure runtime:
  - `ACTIONS_CONNECTOR_BASE_URL=http://<generic-connector-host>:8082`
  - `ACTIONS_CONNECTOR_API_KEY=<value>` if inbound auth enabled on generic connector
- Configure `actions-routing.yml` route for `list_products` pointing at an upstream endpoint.

Verification:
- Runtime admin action overview shows the action exists:
  - `GET /api/admin/actions/overview`
- A chat prompt that triggers `list_products` produces `ACTION_EXECUTED success=true`

### Phase 2: Expand action surface (1-2 days)

Goal: cover your core commerce flows with config-only routing.

Tasks:
- Add route mappings for:
  - `search_products`
  - `view_product_details`
  - `add_to_cart`, `view_cart`, `remove_from_cart`
  - `create_order`
- Ensure idempotency is enabled for write actions.
- Standardize error mapping:
  - 429 -> `RATE_LIMITED`
  - timeouts/5xx -> `SERVICE_UNAVAILABLE`

### Phase 3: Decide the “knowledge” path (indexing vs retrieval connector) (1 day decision)

The Generic REST Connector only solves **actions**. You still need a plan for “knowledge”.

Option A: Runtime-owned vector index (Data Sync push API)
- Best when you want the runtime to own embeddings + vector search.
- Requires a separate ingestion pipeline that calls runtime’s data-sync push endpoints.
- You can keep `Real_Apps/chat-capabilities-connector-demo` as the ingestion demo, or write a small ingestion job/service.

Option B: Customer-owned retrieval (documents-only retrieval connector)
- Best when you already have a vector DB/search system and want runtime to call it.
- Enable runtime retrieval connector:
  - `ai.retrieval.connector.enabled=true`
  - `ai.retrieval.connector.baseUrl=<your retrieval service>`
- Implement `POST /retrieval/search` in a retrieval service (can be the same host as the generic connector, but it is a different endpoint/contract).

### Phase 4: Production hardening (ongoing)

Tasks:
- Inbound security:
  - API key (minimum)
  - HMAC signing (recommended)
  - rate limiting
- Audit logs:
  - actionId, requestId, userId, status, latency
- SSRF controls for upstream routing:
  - allowlist upstream base URLs
  - ban link-local and internal CIDRs unless explicitly allowed
- Persistence:
  - move idempotency store from in-memory to persistent store if required
  - ensure connector restarts do not break idempotency expectations

---

## 6) Recommended Next Step For Your Current Situation

Given you want to “keep using the feed from API” and avoid custom connector code:

1. Deploy `ai-infrastructure-generic-rest-connector` as a separate Railway service.
2. Point runtime’s `ACTIONS_CONNECTOR_BASE_URL` at it (with `https://`).
3. Start with 1-2 READ actions routed to your API feed.
4. Separately decide whether you want indexing into runtime (Data Sync) or retrieval connector (external documents-only retrieval).

If you want, we can add a “minimal upstream mock” profile (or reuse the existing demo app) to validate everything locally before deploying.
