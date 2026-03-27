# Railway Deployment Guide — `ecommerce-store` (Domain API)

This guide deploys **only** the domain API app:
- module: `Real_Apps/ecommerce-store`
- Dockerfile: `Real_Apps/ecommerce-store/Dockerfile`
- port: uses `server.port: ${PORT:8096}` so Railway can inject `PORT`

This service is intended to sit behind the **Generic REST Connector** (actions connector). It does not expose `/actions/execute`.

---

## 0) What you get

- Public HTTPS base URL for the domain APIs (products/cart/orders/etc.)
- Optional persistent storage (H2 file DB) via a Railway Volume

---

## 1) Create the Railway service

1. Create a new Railway project.
2. Add a new service from your GitHub repo (or “Deploy from Repo”).
3. Choose **Dockerfile** deploy.
4. Set **Dockerfile path** to:
   - `Real_Apps/ecommerce-store/Dockerfile`

Railway should detect the app listens on the injected `PORT` env var.

---

## 2) Add a Volume (recommended)

This demo uses an H2 file DB:
- `spring.datasource.url=jdbc:h2:file:./data/ecommerce-store.db`

To persist across restarts:
1. Create a Railway **Volume**
2. Mount it at:
   - `/app/data`

This matches the container workdir (`/app`) and the configured relative `./data/...` path.

---

## 3) Configure environment variables

### 3.1 Required (none)

The domain API can run with defaults.

### 3.2 Recommended

- `APP_DEMO_SEED_DATA=true`
  - Seeds demo catalog + coupon on first start.
  - Set to `false` if you want a clean DB.

- `CORS_ALLOWED_ORIGINS=https://your-frontend-domain`
  - Only required if you call the domain APIs from a browser frontend on another domain.

### 3.3 Event-based indexing (Domain API → Runtime Data Sync, via REST connector)

Only enable this when you also deploy Runtime and you want the domain API to push product/policy/review changes into the runtime’s vector DB.

- `CONNECTOR_INDEXING_ENABLED=true`
- `CONNECTOR_INDEXING_RUNTIME_BASE_URL=https://<your-rest-connector-service>.up.railway.app`
- `CONNECTOR_INDEXING_API_KEY=...` (if your REST connector protects `/api/ai/data-sync/*`)

The REST connector must be configured with:
- `REST_CONNECTOR_RUNTIME_PROXY_ENABLED=true`
- `REST_CONNECTOR_RUNTIME_PROXY_BASE_URL=https://<your-runtime-service>.up.railway.app`

If you are deploying **only** the domain API, keep:
- `CONNECTOR_INDEXING_ENABLED=false` (default)

### 3.4 Demo reset endpoints (UI maintenance)

This service exposes:
- `POST /api/admin/demo/reset` (preferred)
- `POST /api/admin/demo/clear` (eventful; relies on delete-index events instead of runtime vector-clear)
- `POST /api/admin/migration/clear` (legacy alias)

Optional: protect these endpoints with an API key:
- `CONNECTOR_ADMIN_AUTH_ENABLED=true`
- `CONNECTOR_ADMIN_API_KEY=...`
- Optional: `CONNECTOR_ADMIN_API_KEY_HEADER=X-AIFABRIC-API-KEY`

---

## 4) Health checks / smoke test

After deploy:

- Health:
  - `GET /actuator/health`

- Swagger UI:
  - `GET /swagger-ui/index.html`

---

## 5) Hooking up AI Fabric Runtime (quick notes)

Runtime should call the **Generic REST Connector** (actions connector), not this domain API.

Configure the REST connector:
- `UPSTREAM_BASE_URL=https://<this-domain-api-service>.up.railway.app`
- If REST connector inbound auth is enabled: `CONNECTOR_API_KEY=...` (and header)

Configure Runtime:
- `ACTIONS_CONNECTOR_BASE_URL=https://<your-rest-connector-service>.up.railway.app`
- `ACTIONS_CONNECTOR_API_KEY=...` (must match the REST connector inbound key if enabled)

---

## 6) Common issues

- **Service boots but can’t be reached**
  - Verify Railway set `PORT` and the service is exposing it.

- **H2 database resets after redeploy**
  - Add a Railway Volume mounted at `/app/data`.

- **Indexing calls fail (optional feature)**
  - Ensure `CONNECTOR_INDEXING_RUNTIME_BASE_URL` points to the REST connector.
  - Ensure the REST connector has `REST_CONNECTOR_RUNTIME_PROXY_ENABLED=true`.
