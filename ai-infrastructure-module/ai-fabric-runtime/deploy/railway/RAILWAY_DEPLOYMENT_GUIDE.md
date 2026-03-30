# Railway Deployment Guide — `ai-fabric-runtime`

This guide deploys **AI Fabric Runtime** as a Railway service.

The Railway Dockerfile is now **packaging-only**. It does not bake ecommerce-store config into the image.

For platform-managed deployments, runtime should receive config via env vars and platform-served artifact URLs.

Related:
- `ai-infrastructure-module/ai-fabric-runtime/deploy/railway/BASE_VS_RAILWAY_DOCKERFILES.md`

## 1) Recommended Dockerfile on Railway

Use:

- `ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile`

The base Dockerfile also works, but the platform provisioning flow already targets the Railway path above.

## 2) Volume

Runtime writes local state under:

- `/app/data`
- `/data`

Recommended Railway volume mount:

- `/app/data`

If you want absolute paths instead, override datasource / Lucene env vars explicitly.

## 3) Platform-managed deployment config

For product/platform-managed deployments, runtime should be configured with:

- `AI_ACTIONS_CATALOG_PATH=https://<platform>/api/deployments/.../artifacts/ai-actions.yml`
- `AI_CONFIG_DEFAULT_FILE=https://<platform>/api/deployments/.../artifacts/ai-entity-config.yml`
- `ACTIONS_CONNECTOR_BASE_URL=https://<connector>.up.railway.app`
- `ACTIONS_CONNECTOR_API_KEY=<secret>`

Typical provider envs:

- `OPENAI_ENABLED=true`
- `OPENAI_API_KEY=...`

Optional browser CORS:

- `CORS_ALLOWED_ORIGINS=https://your-ui-domain`
- `CORS_ALLOWED_ORIGIN_PATTERNS=https://*.your-ui-domain`
- `CORS_ALLOW_CREDENTIALS=true|false`

## 4) Manual/demo deployment config

If you are not using the platform yet, you can still run runtime with mounted config:

- mount `/config`
- optionally set `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/config/`
- or set:
  - `AI_ACTIONS_CATALOG_PATH=file:/config/ai-actions.yml`
  - `AI_CONFIG_DEFAULT_FILE=file:/config/ai-entity-config.yml`

If you want the ecommerce-store demo config, mount:

- `Real_Apps/ecommerce-store/deploy/runtime/config/`

## 5) Admin endpoints

Admin endpoints:

- `GET /api/admin/overview`
- `GET /api/admin/actions/overview`
- `GET /api/admin/indexing/overview`
- `GET /api/admin/indexing/vectors?entityType=...`

Production recommendation:

- set `APP_ADMIN_API_KEY`
- optionally set `APP_ADMIN_API_KEY_HEADER` (default `X-ADMIN-API-KEY`)

Important:

- direct browser access to runtime admin endpoints without the configured admin header should return `401`
- that is expected behavior, not a deployment failure
- if a REST connector runtime proxy is used for these admin endpoints, it must send:
  - `X-ADMIN-API-KEY: <APP_ADMIN_API_KEY>`

## 6) Smoke checks

- `GET /actuator/health`
- `GET /api/admin/overview`
- `GET /api/admin/actions/overview`
- `GET /api/admin/indexing/overview`

For protected admin endpoints, include:

- `APP_ADMIN_API_KEY_HEADER` (default `X-ADMIN-API-KEY`)
- value matching `APP_ADMIN_API_KEY`

## 7) Notes

- Runtime uses `server.port=${PORT:8097}`, so Railway can inject `PORT`.
- If `ACTIONS_CONNECTOR_BASE_URL` is missing `https://`, connector action execution will fail with absolute-URI errors.
- Platform-managed deployments should treat Dockerfiles as packaging only; deployment config should come from the platform artifact URLs.
