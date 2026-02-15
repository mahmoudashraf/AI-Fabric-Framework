# AI Fabric Runtime JAR — Quickstart (Option D2)

This quickstart targets the **self-hosted**, **domain-agnostic** runtime JAR:
- module: `ai-infrastructure-module/ai-fabric-runtime`
- API: `POST /api/chat/query`

## Build

```bash
mvn -f ai-infrastructure-module/pom.xml -pl ai-fabric-runtime -am package
```

## Run (dev)

OpenAI is **opt-in** (disabled by default). To enable orchestration + embeddings:

```bash
export OPENAI_ENABLED=true
export OPENAI_API_KEY="..."

java -jar ai-infrastructure-module/ai-fabric-runtime/target/ai-fabric-runtime-*.jar
```

Defaults:
- HTTP port: `8097` (override with `PORT`)
- Vector DB: Lucene (local index under `./data/`)
- H2 database: `./data/ai-fabric-runtime.db`
- Default vector space: `document` (see `ai-entity-config.yml`)

Swagger UI:
- `http://localhost:8097/swagger-ui/index.html`

Health:
- `http://localhost:8097/actuator/health`

Local request samples (IntelliJ HTTP client style):
- `ai-infrastructure-module/ai-fabric-runtime/requests/runtime.http`

## Docker Compose (per-customer template)

Template files:
- `ai-infrastructure-module/ai-fabric-runtime/deploy/docker/docker-compose.template.yml`
- `ai-infrastructure-module/ai-fabric-runtime/deploy/docker/customer-template/config/application.yml`
- `ai-infrastructure-module/ai-fabric-runtime/deploy/docker/customer-template/config/ai-entity-config.yml`

Typical flow for a customer:
1. Copy `deploy/docker/customer-template/` into a new customer folder (e.g. `customers/acme/`) and adjust config.
2. Copy and edit `deploy/docker/docker-compose.template.yml` into `customers/acme/docker-compose.yml`:
   - set `container_name`
   - mount `./config` + `./data`
   - set `OPENAI_*` env vars (and optionally `ACTIONS_CONNECTOR_BASE_URL`)
3. Run:

```bash
docker compose up -d
```

## Docker (build + run)

Build an image from source (build context must be repo root):

```bash
docker build -f ai-infrastructure-module/ai-fabric-runtime/Dockerfile -t ai-fabric-runtime:local .
```

Run:

```bash
docker run -d --name ai-fabric-runtime \
  -p 8097:8097 \
  -e OPENAI_ENABLED=true \
  -e OPENAI_API_KEY="..." \
  -e OPENAI_EMBEDDING_DIMENSIONS=512 \
  -v $(pwd)/ai-infrastructure-module/ai-fabric-runtime/deploy/docker/customer-template/config:/config:ro \
  -v $(pwd)/ai-infrastructure-module/ai-fabric-runtime/deploy/docker/customer-template/data:/data \
  ai-fabric-runtime:local \
  --spring.config.additional-location=file:/config/
```

## Docker Compose (dev, builds image)

The runtime module includes a dev `docker-compose.yml` that builds the image from source:
- `ai-infrastructure-module/ai-fabric-runtime/docker-compose.yml`

Run:

```bash
cd ai-infrastructure-module/ai-fabric-runtime
docker compose up -d --build
```

## Chat API

```bash
curl -sS -X POST "http://localhost:8097/api/chat/query" \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-1",
    "sessionId": "user-1-session",
    "conversationId": "chat-user-1",
    "query": "What can you do?"
  }'
```

Request contract matches:
- `Final_Documentation/Development_Guides/CHAT_CAPABILITIES_UI_MIGRATION_GUIDE.md`

## Ingest documents (push data sync)

Data sync requires embeddings to be enabled (for dev defaults, set `OPENAI_ENABLED=true`).

List supported vector spaces:

```bash
curl -sS "http://localhost:8097/api/ai/data-sync/vector-spaces"
```

Upsert a document into the `document` vector space:

```bash
curl -sS -X POST "http://localhost:8097/api/ai/data-sync/upsert" \
  -H "Content-Type: application/json" \
  -d '{
    "vectorSpace": "document",
    "id": "doc-1",
    "content": "Return policy: returns accepted within 30 days with receipt.",
    "metadata": { "source": "manual" },
    "trace": { "requestId": "req-1", "userId": "user-1", "sessionId": "user-1-session" }
  }'
```

## Connector-backed actions (optional)

1) Create an action catalog file (example path): `./config/ai-actions.yml`
2) Configure the runtime to load it and to call your Relay/Connector:

```yaml
ai:
  actions:
    sources:
      - type: file
        path: file:./config/ai-actions.yml
    connector:
      base-url: http://localhost:8099
```

Action contract format + relay model:
- `changes/Productization/ACTIONS_CONNECTOR_AND_RELAY_GUIDE.md`

## Production hardening (recommended)

Disable permissive dev defaults and provide explicit policies:

```yaml
ai:
  fabric:
    runtime:
      dev-defaults:
        enabled: false
```

When `ai.fabric.runtime.dev-defaults.enabled=false`, the runtime requires:
- a custom `EntityAccessPolicy` bean (fail-fast at startup)
- (optional) a stricter `ChatSessionAccessControlPolicy` bean
