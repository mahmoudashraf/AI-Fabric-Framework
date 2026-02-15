# Chat Capabilities Runtime Connector

This app clones the chat-capabilities domain APIs and exposes AI actions through the Customer Connector contract endpoint:

- `POST /actions/execute`
- `POST /api/connector/actions/execute` (alias)

It is intended to run side-by-side with the new `ai-fabric-runtime` Docker image. Runtime executes actions through connector contracts (not in-process action handlers).

## What changed vs chat-capabilities-demo

- Added connector action execution endpoint compatible with `changes/Productization/customer-connector-api.openapi.yml`.
- Removed local indexing annotations/config from write services in this app.
- Runtime-side contracts/config are provided under `runtime-config/`:
  - `ai-actions.yml` (action catalog)
  - `ai-entity-config.yml` (runtime entity config)

## Run with Docker

```bash
docker compose -f Real_Apps/chat-capabilities-runtime-connector/docker-compose.yml up --build
```

- Runtime: `http://localhost:8097`
- Connector app: `http://localhost:8098`

Use `requests/demo.http` for smoke tests.
