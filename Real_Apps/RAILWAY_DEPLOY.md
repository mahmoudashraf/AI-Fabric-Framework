# Railway Deployment (Monorepo, build from `main`)

These apps depend on the **AI Fabric code in this repo**, so the Docker build compiles the framework (`ai-infrastructure-module`) first, then builds the selected app.

## Railway setup (per app)

1) Railway → **New Project → Deploy from GitHub repo**
2) Create **one Service per app**
3) Keep the **repo root** as build context
4) Set Dockerfile path to `Real_Apps/<app>/Dockerfile`

## Ports

All apps use `server.port: ${PORT:...}` so Railway can inject `PORT`.

## Environment variables (by app)

### No external keys/services (easy demos)
- `behavior-churn-signals` (stub LLM)
- `relationship-query-crm-insights` (stub LLM)
- `migration-enabled-product-catalog` (local lucene + simple embedding)
- `smart-faq-assistant` (local lucene + onnx embedding)

### Requires LLM key
- `it-support-action-bot`:
  - `OPENAI_ENABLED=true`
  - `OPENAI_API_KEY=...`
- `chat-capabilities-demo`:
  - `OPENAI_ENABLED=true`
  - `OPENAI_API_KEY=...`
- `sub-management-hub` / `sub-management-hub-simple`:
  - `COHERE_ENABLED=true`
  - `COHERE_API_KEY=...`

### Requires encryption secret
- `privacy-first-customer-facing-support`:
  - `AI_PII_ENCRYPTION_SECRET=...`

### Requires Postgres + Qdrant + OpenAI
- `cloud-qdrant-openai-vector-search`:
  - `AI_PROVIDERS_OPENAI_API_KEY=...`
  - Postgres via JDBC:
    - `SPRING_DATASOURCE_URL=jdbc:postgresql://...`
    - `SPRING_DATASOURCE_USERNAME=...`
    - `SPRING_DATASOURCE_PASSWORD=...`
  - Qdrant:
    - `AI_PROVIDERS_QDRANT_HOST=...`
    - `AI_PROVIDERS_QDRANT_GRPC_PORT=6334`
    - Optional: `AI_PROVIDERS_QDRANT_API_KEY=...`
