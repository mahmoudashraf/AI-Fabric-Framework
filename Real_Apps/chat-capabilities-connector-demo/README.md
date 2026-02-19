# Chat Capabilities Connector Demo (AI Fabric Runtime + Customer Connector)

This Real App demonstrates how to split the original `chat-capabilities-demo` into:
- **AI Fabric Runtime** (chat orchestration, modes, RAG, suggestions): `ai-fabric-runtime` (port `8097`)
- **Customer Connector (domain system)** (products/cart/orders + action execution): this app (port `8096`)

The connector exposes:
- Domain APIs (e.g. `/api/products`, `/api/cart`, `/api/orders`, …)
- Customer Connector actions endpoint: `POST /actions/execute`

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
- Connector Swagger UI: `http://localhost:8096/swagger-ui/index.html`
- Runtime Swagger UI: `http://localhost:8097/swagger-ui/index.html`

Requests file:
- Runtime: `requests/demo.runtime.http`
- Connector: `requests/demo.connector.http`

## Deploy (Railway)

- Connector (this app): `Real_Apps/chat-capabilities-connector-demo/deploy/railway/RAILWAY_DEPLOYMENT_GUIDE.md`
- Runtime (AI Fabric): `ai-infrastructure-module/ai-fabric-runtime/deploy/railway/RAILWAY_DEPLOYMENT_GUIDE.md`

## Event-Based Indexing (Products/Policies/Reviews)

In Docker Compose, the connector can automatically index **product/policy/review** changes into the runtime (via Data Sync) using after-commit event listeners.

- Enabled by default in `deploy/docker/docker-compose.yml` via `CONNECTOR_INDEXING_ENABLED=true`.
- Disable with:

```bash
export CONNECTOR_INDEXING_ENABLED=false
```

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

## Demo Seed Data (Connector)

The connector seeds a small demo catalog (including `SKU-0001` and `SKU-0002`) and a demo coupon `SAVE10` on first start.
Disable this with:

```bash
export APP_DEMO_SEED_DATA=false
```

## CORS (for https://ai-fabric.dev demo UI)

If you are calling this API from a browser-based frontend on another domain, set:

```bash
export CORS_ALLOWED_ORIGINS="https://ai-fabric.dev"
```
