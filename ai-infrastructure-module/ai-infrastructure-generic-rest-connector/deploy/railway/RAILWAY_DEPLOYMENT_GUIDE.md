# Generic REST Connector on Railway

Status: draft (2026-03-25)

This guide deploys `ai-infrastructure-generic-rest-connector` to Railway and makes it callable from AI Fabric Runtime.

## 1) Pick a Dockerfile

Option A: Base Dockerfile (bring your own routing config)
- Dockerfile: `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/Dockerfile`
- You must provide `actions-routing.yml` yourself (either bake it into the image or switch to the Railway Dockerfile below).

Option B: Railway Dockerfile (bakes a template routing config)
- Dockerfile: `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile`
- Bakes `deploy/railway/actions-routing.yml` into the image at `/config/actions-routing.yml`
- Sets `REST_CONNECTOR_ROUTING_CONFIG_LOCATION=file:/config/actions-routing.yml` by default

## 2) Railway-required port behavior

The service listens on `server.port=${PORT:8082}` so Railway can inject `PORT`.

## 3) Health check

Use:
- `GET /actuator/health`

## 4) Minimum environment variables (recommended)

Inbound auth (recommended):
- `CONNECTOR_API_KEY=<strong-secret>`
- Optional: `CONNECTOR_API_KEY_HEADER=X-AIFABRIC-API-KEY`

If you want to temporarily allow unauthenticated access (dev only):
- `CONNECTOR_ALLOW_UNAUTHENTICATED=true`
- Also set `CONNECTOR_API_KEY_ENABLED=false` (otherwise startup validation still requires a key value)

Upstream (only required once you add action routes):
- `UPSTREAM_BASE_URL=https://your-api.example.com`
- `UPSTREAM_AUTH_TYPE=NONE` or `API_KEY`
- `UPSTREAM_AUTH_HEADER=Authorization`
- `UPSTREAM_AUTH_VALUE=Bearer <token>`

## 5) Add your action routes

Routes live in the routing config file referenced by:
- `REST_CONNECTOR_ROUTING_CONFIG_LOCATION`

For the Railway Dockerfile default, edit:
- `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/actions-routing.yml`

Add entries under:
- `actions:`

Each action route must set either:
- `url: https://...` (absolute), or
- `path: /...` (relative, requires `connector.upstream.base-url`)

## 6) Point runtime at the connector

In AI Fabric Runtime env vars:
- `ACTIONS_CONNECTOR_BASE_URL=https://<your-railway-connector>.up.railway.app`
- `ACTIONS_CONNECTOR_API_KEY=<same as CONNECTOR_API_KEY>`

Common failure mode:
- `URI is not absolute` means the base URL is missing `https://`.

