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

You have 3 viable setups.

Option 1: Use the existing domain connector demo (current state)
- Service: `Real_Apps/chat-capabilities-connector-demo`
- Use when you want a self-contained demo domain (cart state, seeded products, demo reset tools).

Option 2: Use the Generic REST Connector as your connector (recommended for “API feed” integrations)
- Service: `ai-infrastructure-generic-rest-connector`
- Upstream: your real API feed (or any existing commerce API)
- Use when you want config-only mapping (`actionId -> REST endpoint`) and your upstream system owns state.

Option 3: Use Generic REST Connector as a translation layer in front of the existing demo app (for learning)
- Runtime -> Generic REST Connector -> existing demo app endpoints
- Use when you want to learn the routing/templating model before connecting a real upstream API.

If you need demo-only features like “reset demo + clear runtime vectors”, keep the existing demo app as a utility service.

---

## 3) Connector As A Single Point Of Contact (Bridge/Gateway Pattern)

You can treat the connector as a “single point of contact” for:
- Action execution (already supported): runtime -> connector -> upstream APIs.
- Retrieval (optional): runtime -> connector -> retrieval service.
- Indexing push (optional): upstream -> connector -> runtime Data Sync push API.

Important constraint:
- By default, `ai-infrastructure-generic-rest-connector` is **actions-only** (`/actions/execute`).
- If you want a “single base URL” demo, you can enable the built-in **runtime proxy** feature to expose a small set of alias endpoints (for the Runtime Data Sync push API). This is still a thin forwarder, not a full gateway.
- For a full “gateway connector” (actions + retrieval + indexing + multi-tenant routing), you either extend the connector further or run a separate gateway service.

Guidance:
- The runtime’s Data Sync push API should remain the canonical indexing interface.
- A connector “alias endpoint” that forwards to runtime indexing can be useful for demos and “one base URL” deployments, but it should not replace direct runtime usage long-term.

---

## 4) What The Runtime Needs (Always)

Regardless of which connector you use, **the runtime must know the action catalog** (the contract).

Runtime action catalog source:
- `ai-actions.yml` (loaded via `ai.actions.sources`)
- Default runtime path: `file:/config/ai-actions.yml` (overridable via `AI_ACTIONS_CATALOG_PATH`)

Runtime connector base URL:
- `ai.actions.connector.base-url` (env: `ACTIONS_CONNECTOR_BASE_URL`)

Common failure mode: `URI is not absolute` means your base URL is missing a scheme. Use `https://...`, not just `myhost.com`.

---

## 5) What The Generic REST Connector Needs

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

## 6) Roadmap (Phased)

### Phase 0: Baseline understanding (1-2 hours)

Goal: confirm we can run the Generic REST Connector and hit `/actions/execute`.

Tasks:
- Read: `changes/Productization/GENERIC_REST_API_CONNECTOR_GUIDE.md`
- Run the generic connector locally.
- Confirm `POST /actions/execute` works for an action that has a route mapping.
- Confirm an unsupported action returns `ACTION_NOT_SUPPORTED`.

### Phase 1: One action end-to-end (half day)

Goal: runtime calls a connector action and gets a successful result.

Pick one READ action (example: `list_products`).

Tasks:
- Ensure runtime loads an action catalog that includes `list_products`.
- Set runtime `ACTIONS_CONNECTOR_BASE_URL=http://<generic-connector-host>:8082`.
- Set runtime `ACTIONS_CONNECTOR_API_KEY=<value>` if inbound auth is enabled on the generic connector.
- Configure `actions-routing.yml` route for `list_products` pointing at an upstream endpoint.

Verification:
- Confirm runtime admin action overview (`GET /api/admin/actions/overview`) lists `list_products`.
- Confirm a chat prompt that triggers `list_products` produces `ACTION_EXECUTED success=true`.

### Phase 2: Expand action surface (1-2 days)

Goal: cover your core commerce flows with config-only routing.

Tasks:
- Add route mappings for `search_products`, `view_product_details`, `add_to_cart`, `view_cart`, `remove_from_cart`, `create_order`.
- Ensure idempotency is enabled for write actions.
- Standardize error mapping so upstream 429 becomes `RATE_LIMITED`.
- Standardize error mapping so upstream timeouts/5xx become `SERVICE_UNAVAILABLE`.

### Phase 3: Indexing/Retrieval Strategy (1 day decision)

Out of the box, the Generic REST Connector only solves **actions**. You still need a plan for “knowledge”.

Option A: Runtime-owned vector index (Data Sync push API)
- Best when you want the runtime to own embeddings + vector search.
- Requires a separate ingestion pipeline that calls runtime’s data-sync push endpoints.
- For this demo, this is the recommended path.
- You can call the runtime directly for ingestion.
- Optionally, you can enable the connector’s built-in runtime proxy so `/api/ai/data-sync/*` on the connector forwards to the runtime for “single base URL” setups, but runtime direct remains supported and preferred.

Option B: Customer-owned retrieval (documents-only retrieval connector)
- Best when you already have a vector DB/search system and want runtime to call it.
- Later (product deployments), this is the typical evolution.
- The runtime can call the retrieval service directly (`ai.retrieval.connector.baseUrl=<retrieval-service>`).
- Or the runtime can call a connector endpoint (`ai.retrieval.connector.baseUrl=<connector>`) and the connector forwards to the retrieval service. This keeps “connector = single point of contact” but adds an extra hop and must be secured/audited.

### Phase 4: Production hardening (ongoing)

Tasks:
- Inbound security: API key (minimum).
- Inbound security: HMAC signing (recommended).
- Inbound security: rate limiting.
- Audit logs: actionId, requestId, userId, status, latency (PII-safe).
- SSRF controls: allowlist upstream base URLs.
- SSRF controls: block link-local and internal CIDRs unless explicitly allowed.
- Persistence: move idempotency store from in-memory to persistent store if required.
- Persistence: ensure connector restarts do not break idempotency expectations.

---

## 7) Recommended Next Step For Your Current Situation

Given you want to “keep using the feed from API” and avoid custom connector code:

1. Deploy `ai-infrastructure-generic-rest-connector` as a separate Railway service.
2. Point runtime’s `ACTIONS_CONNECTOR_BASE_URL` at it (with `https://`).
3. Start with 1-2 READ actions routed to your API feed.
4. For this demo, use runtime-owned indexing (Data Sync push API).
5. Later, evolve to a user-owned retrieval service if you want to keep retrieval outside the runtime.

If you want, we can add a “minimal upstream mock” profile (or reuse the existing demo app) to validate everything locally before deploying.
