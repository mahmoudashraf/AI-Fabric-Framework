# Railway Deployment Guide — `ai-fabric-runtime`

This guide deploys **AI Fabric Runtime** as a Railway service.

For a deeper explanation of what changes between the two Dockerfiles (config loading, volumes, and common failure modes), see:
- `ai-infrastructure-module/ai-fabric-runtime/deploy/railway/BASE_VS_RAILWAY_DOCKERFILES.md`

You have two deployment options:

1) **Runtime (base)** — use the default runtime config bundled in the jar  
2) **Runtime (connector demo config baked-in)** — recommended if you want the runtime to work with:
   - the connector demo actions contract (`ai-actions.yml`)
   - the connector demo entity config (`ai-entity-config.yml`)
   - `ACTIONS_CONNECTOR_BASE_URL` env bindings

---

## Option 1: Runtime (base, no external config)

### 1) Create Railway service

1. Create a new Railway project.
2. Add a new service from your repo.
3. Choose **Dockerfile** deploy.
4. Set Dockerfile path to:
   - `ai-infrastructure-module/ai-fabric-runtime/Dockerfile`

### 2) Add a Volume (recommended)

Runtime uses:
- H2 DB: `./data/ai-fabric-runtime.db`
- Lucene vector index: `./data/lucene-vector-index-*`

Add a Railway **Volume** mounted at:
- `/app/data`

If you still see write permission errors on Railway volumes, use **Option 2** (mount at `/data`) or override:
- `SPRING_DATASOURCE_URL=jdbc:h2:file:/data/ai-fabric-runtime.db`
- `AI_VECTOR_DB_LUCENE_INDEX_PATH=/data/lucene-vector-index-512` (match your embedding dimensions)

### 3) Configure environment variables

Runtime can boot without OpenAI enabled, but for chat + embeddings you typically set:

- `OPENAI_ENABLED=true`
- `OPENAI_API_KEY=...` (secret)
- Optional:
  - `OPENAI_MODEL=gpt-4o-mini`
  - `OPENAI_EMBEDDING_MODEL=text-embedding-3-small`
  - `OPENAI_EMBEDDING_DIMENSIONS=512`

If you host a browser UI:
- `CORS_ALLOWED_ORIGINS=https://your-ui-domain`

---

## Option 2: Runtime (connector demo config baked-in)

Use this when you want runtime to run with the **same config used by**:
- `Real_Apps/chat-capabilities-connector-demo/deploy/docker/docker-compose.yml`

### 1) Create Railway service

1. Create a new Railway project (or reuse the same project as the connector).
2. Add a new service from your repo.
3. Choose **Dockerfile** deploy.
4. Set Dockerfile path to:
   - `ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile`

This Dockerfile bakes config files into the image at `/config` and starts runtime with:
- `--spring.config.additional-location=file:/config/`

### 2) Add a Volume (recommended)

This config uses:
- H2 DB at `/data/ai-fabric-runtime.db`
- Lucene index at `/data/lucene-vector-index-*`

Add a Railway **Volume** mounted at:
- `/data`

### 3) Configure environment variables (required for demo)

LLM + embeddings:
- `OPENAI_ENABLED=true`
- `OPENAI_API_KEY=...` (secret)

Connector actions:
- `ACTIONS_CONNECTOR_BASE_URL=https://<your-connector-service>.railway.app`
- If connector protects `/actions/execute` with API key:
  - `ACTIONS_CONNECTOR_API_KEY=...` (must match the connector’s `CONNECTOR_AUTH_API_KEY`)

Optional CORS:
- `CORS_ALLOWED_ORIGINS=https://your-ui-domain`

### 4) Smoke test

- Health:
  - `GET /actuator/health`
- Swagger:
  - `GET /swagger-ui/index.html`

---

## Notes / gotchas

- **Railway `PORT`**
  - Runtime uses `server.port: ${PORT:8097}`, so Railway can inject `PORT`.

- **Persistence**
  - If you do not mount a Volume, H2 + Lucene indexes reset on redeploy/restart.

- **Connector networking**
  - Set `ACTIONS_CONNECTOR_BASE_URL` to the public HTTPS URL of the connector service.
  - If you deploy both runtime and connector on Railway, they can still talk over public HTTPS (simplest).
