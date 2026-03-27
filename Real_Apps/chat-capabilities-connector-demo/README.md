# Chat Capabilities Domain Demo (Domain API + REST Connector + Runtime)

This Real App demonstrates a 3-service setup:
- **Domain API** (products/cart/orders/etc.): this app (port `8096`)
- **Generic REST Connector** (actions connector + optional runtime proxy): `ai-infrastructure-generic-rest-connector` (port `8082`)
- **AI Fabric Runtime** (chat orchestration, modes, RAG, suggestions): `ai-fabric-runtime` (port `8097`)

The domain API exposes only domain endpoints (e.g. `/api/products`, `/api/carts`, `/api/orders`, ...). It does not expose `/actions/execute`.

The runtime is configured via:
- `deploy/runtime/config/application.yml`
- `deploy/runtime/config/ai-entity-config.yml`
- `deploy/runtime/config/ai-actions.yml`

## Run (Docker Compose, recommended)

From repo root:

```bash
OPENAI_ENABLED=true \
OPENAI_API_KEY="$(tr -d '\n' < scripts/openai.env)" \
docker compose -f Real_Apps/chat-capabilities-connector-demo/deploy/docker/docker-compose.yml up --build
```

Then:
- Domain API Swagger UI: `http://localhost:8096/swagger-ui/index.html`
- REST connector health: `http://localhost:8082/actuator/health`
- Runtime Swagger UI: `http://localhost:8097/swagger-ui/index.html`

Requests file:
- Runtime: `requests/demo.runtime.http`
- Domain API: `requests/demo.connector.http`

## Demo Reset / Migration Clear (Domain API)

UI-facing maintenance endpoints:
- `POST /api/admin/demo/reset` (preferred)
- `POST /api/admin/migration/clear` (legacy alias)

Both require a JSON body with at least:
- `{"confirm": true}`

Optional: protect these endpoints with an API key:
```bash
export CONNECTOR_ADMIN_AUTH_ENABLED=true
export CONNECTOR_ADMIN_API_KEY="..."
export CONNECTOR_ADMIN_API_KEY_HEADER="X-AIFABRIC-API-KEY"
```

## Deploy (Railway)

- Domain API (this app): `Real_Apps/chat-capabilities-connector-demo/deploy/railway/RAILWAY_DEPLOYMENT_GUIDE.md`
- REST connector: `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/RAILWAY_DEPLOYMENT_GUIDE.md`
- Runtime: `ai-infrastructure-module/ai-fabric-runtime/deploy/railway/RAILWAY_DEPLOYMENT_GUIDE.md`

## Event-Based Indexing (Products/Policies/Reviews)

In Docker Compose, the domain API can automatically index **product/policy/review** changes into the runtime (via Data Sync) using after-commit event listeners.

- Enabled by default in `deploy/docker/docker-compose.yml` via `CONNECTOR_INDEXING_ENABLED=true`.
- Disable with:

```bash
export CONNECTOR_INDEXING_ENABLED=false
```

Note: indexing calls are sent to `CONNECTOR_INDEXING_RUNTIME_BASE_URL`, which should point at the REST connector when using this 3-service setup.

## OpenAI Setup (Runtime)

The runtime expects OpenAI for **LLM + embeddings** (required for RAG + intent extraction + actions).

This repo uses `scripts/openai.env` as a local developer secret. In this setup it contains the raw API key (not `KEY=VALUE`),
so we strip the trailing newline when exporting.

```bash
export OPENAI_ENABLED=true
export OPENAI_API_KEY="$(tr -d '\n' < scripts/openai.env)"
export OPENAI_MODEL="gpt-4o-mini"                         # optional
export OPENAI_EMBEDDING_MODEL="text-embedding-3-small"     # optional
export OPENAI_EMBEDDING_DIMENSIONS="512"                  # recommended for Lucene (max 1024)
```

Then open `requests/demo.runtime.http`.

## Demo Seed Data (Domain API)

The domain API seeds a small demo catalog (including `SKU-0001` and `SKU-0002`) and a demo coupon `SAVE10` on first start.
Disable this with:

```bash
export APP_DEMO_SEED_DATA=false
```

## CORS (for https://ai-fabric.dev demo UI)

If you are calling this API from a browser-based frontend on another domain, set:

```bash
export CORS_ALLOWED_ORIGINS="https://ai-fabric.dev"
```
