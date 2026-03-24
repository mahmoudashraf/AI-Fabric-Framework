# Runtime Dockerfiles: Base vs Railway (Baked Demo Config)

This repo contains two different Dockerfiles for **AI Fabric Runtime**. They both use **Java 21**, but they serve different purposes.

## Files

- Base runtime Dockerfile:
  - `ai-infrastructure-module/ai-fabric-runtime/Dockerfile`
  - Entry point script: `ai-infrastructure-module/ai-fabric-runtime/docker-entrypoint.sh`

- Railway-friendly runtime Dockerfile (bakes in connector-demo config):
  - `ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile`

## What’s Different (Behaviorally)

### 1) Configuration loading

**Base Dockerfile**

- Starts the runtime jar with no extra Spring config locations:
  - `java -jar /app/runtime.jar`
- Uses the runtime’s bundled `application.yml`:
  - Default entity config: `ai.config.default-file: ai-entity-config.yml` (classpath)
  - Actions contract source defaults to: `${AI_ACTIONS_CATALOG_PATH:file:/config/ai-actions.yml}` and is `optional: true`
- Result: if you do not mount `/config/ai-actions.yml`, the runtime still boots, but your connector demo actions are not loaded.

**Railway Dockerfile**

- Copies the connector-demo runtime config folder into the image at:
  - `/config/`
- Starts the runtime with:
  - `--spring.config.additional-location=file:/config/`
- Result: the runtime loads:
  - `/config/application.yml`
  - `/config/ai-actions.yml`
  - `/config/ai-entity-config.yml`

This is the most common reason the runtime appears “missing actions”: the **base** image doesn’t include the demo action catalog unless you mount it.

### 2) Where `ai-actions.yml` and `ai-entity-config.yml` live

**Railway Dockerfile**

- In-container paths are:
  - `/config/ai-actions.yml`
  - `/config/ai-entity-config.yml`
- The demo runtime config in this repo is sourced from:
  - `Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config/`

**Base Dockerfile**

- You decide where the files live.
- Recommended pattern:
  - Mount a folder to `/config`
  - Set `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/config/` (or pass `--spring.config.additional-location=file:/config/`)
  - Or minimally just mount `/config/ai-actions.yml` and rely on `AI_ACTIONS_CATALOG_PATH` default.

### 3) Persistence paths and Railway volumes

**Base Dockerfile**

- Default runtime config uses relative paths under the working directory (`/app`):
  - H2: `jdbc:h2:file:./data/ai-fabric-runtime.db`
  - Lucene: `./data/lucene-vector-index-*`
- Railway guide recommends mounting a volume at:
  - `/app/data`

**Railway Dockerfile**

- Demo runtime config uses absolute `/data/...` paths.
- Railway guide recommends mounting a volume at:
  - `/data`

### 4) Entrypoint and permissions

**Base Dockerfile**

- Installs `gosu` and uses `docker-entrypoint.sh` to:
  - `chown` mounted volumes when running as root
  - drop to UID `10001` before launching Java

**Railway Dockerfile**

- Runs as `USER 10001` directly.
- Creates `/data` and `/config` and sets ownership during build.

## Typical Symptoms and the Dockerfile Choice

### Symptom: runtime only “knows” a tiny set of actions (e.g., vector maintenance)

Likely cause: you deployed the **base** runtime Dockerfile without mounting `/config/ai-actions.yml` (or without pointing `AI_ACTIONS_CATALOG_PATH` at your action catalog).

Fix options:

- Use the Railway Dockerfile (baked config), or
- Use the base Dockerfile and mount `/config/ai-actions.yml`, or
- Use the base Dockerfile and set `AI_ACTIONS_CATALOG_PATH` to wherever your catalog is mounted.

### Symptom: action execution fails with `URI is not absolute`

Likely cause: an env var base URL is missing a scheme.

Example:

- Bad: `ACTIONS_CONNECTOR_BASE_URL=ai-fabric-framework-production-a247.up.railway.app`
- Good: `ACTIONS_CONNECTOR_BASE_URL=https://ai-fabric-framework-production-a247.up.railway.app`

## Example: Running the Base Runtime With Demo Config Mounted

From the repo root:

```bash
docker build -f ai-infrastructure-module/ai-fabric-runtime/Dockerfile -t ai-fabric-runtime:base .

docker run --rm -p 8097:8097 \
  -v "$(pwd)/Real_Apps/chat-capabilities-connector-demo/deploy/runtime/config:/config" \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION="file:/config/" \
  -e ACTIONS_CONNECTOR_BASE_URL="http://host.docker.internal:8096" \
  ai-fabric-runtime:base
```

## Example: Running the Railway Runtime Locally (Baked Config)

```bash
docker build -f ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile -t ai-fabric-runtime:railway .
docker run --rm -p 8097:8097 -e ACTIONS_CONNECTOR_BASE_URL="http://host.docker.internal:8096" ai-fabric-runtime:railway
```

## Related Docs

- `ai-infrastructure-module/ai-fabric-runtime/deploy/railway/RAILWAY_DEPLOYMENT_GUIDE.md`
- `Real_Apps/chat-capabilities-connector-demo/deploy/railway/RAILWAY_DEPLOYMENT_GUIDE.md`

