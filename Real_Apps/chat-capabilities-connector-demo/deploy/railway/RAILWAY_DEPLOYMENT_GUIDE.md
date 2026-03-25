# Railway Deployment Guide — `chat-capabilities-connector-demo` (Customer Connector)

This guide deploys **only** the Customer Connector app:
- module: `Real_Apps/chat-capabilities-connector-demo`
- Dockerfile: `Real_Apps/chat-capabilities-connector-demo/Dockerfile`
- port: uses `server.port: ${PORT:8096}` so Railway can inject `PORT`

If you also want to deploy **AI Fabric Runtime** on Railway, deploy it as a separate Railway service and then configure the runtime to call this connector via `ACTIONS_CONNECTOR_BASE_URL`.

---

## 0) What you get

- Public HTTPS base URL for the connector (domain APIs + `POST /actions/execute`)
- Optional connector API key auth for `/actions/execute`
- Optional persistent storage (H2 file DB) via a Railway Volume

---

## 1) Create the Railway service

1. Create a new Railway project.
2. Add a new service from your GitHub repo (or “Deploy from Repo”).
3. Choose **Dockerfile** deploy.
4. Set **Dockerfile path** to:
   - `Real_Apps/chat-capabilities-connector-demo/Dockerfile`

Railway should detect the app listens on the injected `PORT` env var.

---

## 2) Add a Volume (recommended)

This demo uses an H2 file DB:
- `spring.datasource.url=jdbc:h2:file:./data/chat-capabilities.db`

To persist across restarts:
1. Create a Railway **Volume**
2. Mount it at:
   - `/app/data`

This matches the container workdir (`/app`) and the configured relative `./data/...` path.

---

## 3) Configure environment variables

### 3.1 Required (none)

The connector can run with defaults.

### 3.2 Recommended

- `APP_DEMO_SEED_DATA=true`
  - Seeds demo catalog + coupon on first start.
  - Set to `false` if you want a clean DB.

- `CORS_ALLOWED_ORIGINS=https://your-frontend-domain`
  - Only required if you call the connector APIs from a browser frontend on another domain.

### 3.3 Secure `/actions/execute` (recommended for any non-local deployment)

Enable API key protection for the Customer Connector actions endpoint:

- `CONNECTOR_AUTH_API_KEY=...` (secret)
- `CONNECTOR_AUTH_API_KEY_HEADER=X-AIFABRIC-API-KEY` (optional)

By default, destructive connector admin endpoints (like `POST /api/admin/demo/reset`) are **public** (demo utility behavior).
If you want admin reset endpoints to require the same API key as `/actions/execute`, set:

- `CONNECTOR_ADMIN_AUTH_ENABLED=true`

Optional: if you want `POST /api/admin/demo/reset` to also clear runtime vectors, configure the connector with the runtime admin key:

- `CONNECTOR_RUNTIME_ADMIN_API_KEY=...` (should match the runtime’s `APP_ADMIN_API_KEY`)
- Optional: `CONNECTOR_RUNTIME_ADMIN_API_KEY_HEADER=X-ADMIN-API-KEY`

Then configure AI Fabric Runtime to send the same header/value:
- `ACTIONS_CONNECTOR_API_KEY=...`
- `ai.actions.connector.apiKey.header` should match the header name (if you changed it)

### 3.4 Event-based indexing (Connector → Runtime Data Sync)

Only enable this when you also deploy Runtime and you want the connector to push product/policy/review changes into the runtime’s vector DB.

- `CONNECTOR_INDEXING_ENABLED=true`
- `CONNECTOR_INDEXING_RUNTIME_BASE_URL=https://<your-runtime-service>.railway.app`
- `CONNECTOR_INDEXING_API_KEY=...` (if your runtime data-sync API is protected)

If you are deploying **only** the connector, keep:
- `CONNECTOR_INDEXING_ENABLED=false` (default)

---

## 4) Health checks / smoke test

After deploy:

- Health:
  - `GET /actuator/health`

- Swagger UI:
  - `GET /swagger-ui/index.html`

---

## 5) Hooking up AI Fabric Runtime (quick notes)

If Runtime is deployed elsewhere (Railway / Docker / K8s), configure:

- `ACTIONS_CONNECTOR_BASE_URL=https://<your-connector-service>.railway.app`
- `ACTIONS_CONNECTOR_API_KEY=...` (must match `CONNECTOR_AUTH_API_KEY` if enabled)

---

## 6) Common issues

- **Service boots but can’t be reached**
  - Verify Railway set `PORT` and the service is exposing it.

- **H2 database resets after redeploy**
  - Add a Railway Volume mounted at `/app/data`.

- **`/actions/execute` returns `UNAUTHORIZED`**
  - Ensure `CONNECTOR_AUTH_API_KEY` is set.
  - Ensure Runtime sends the same header/value.
