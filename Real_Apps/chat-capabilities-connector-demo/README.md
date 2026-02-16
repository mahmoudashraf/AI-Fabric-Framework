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
docker compose -f Real_Apps/chat-capabilities-connector-demo/deploy/docker/docker-compose.yml up --build
```

Then:
- Connector Swagger UI: `http://localhost:8096/swagger-ui/index.html`
- Runtime Swagger UI: `http://localhost:8097/swagger-ui/index.html`

Requests file:
- Runtime: `requests/demo.runtime.http`
- Connector: `requests/demo.connector.http`

## OpenAI Setup (Runtime)

The runtime expects OpenAI for **LLM + embeddings** (required for RAG + intent extraction + actions).

```bash
export OPENAI_ENABLED=true
export OPENAI_API_KEY="..."
export OPENAI_MODEL="gpt-4o-mini"                         # optional
export OPENAI_EMBEDDING_MODEL="text-embedding-3-small"     # optional
export OPENAI_EMBEDDING_DIMENSIONS="512"                  # recommended for Lucene (max 1024)
```

Then open `requests/demo.runtime.http`.

## CORS (for https://ai-fabric.dev demo UI)

If you are calling this API from a browser-based frontend on another domain, set:

```bash
export CORS_ALLOWED_ORIGINS="https://ai-fabric.dev"
```
