# Generic REST Connector on Railway

This guide deploys `ai-infrastructure-generic-rest-connector` to Railway and makes it callable from AI Fabric Runtime.

The Railway Dockerfile is now **packaging-only**. It does not bake a separate routing file into the image.

Platform-managed deployments should provide routing through:

- `REST_CONNECTOR_ROUTING_CONFIG_LOCATION=https://<platform>/api/deployments/.../artifacts/actions-routing.yml`

## 1) Recommended Dockerfile

Use:

- `ai-infrastructure-module/ai-infrastructure-generic-rest-connector/deploy/railway/Dockerfile`

The base Dockerfile also works, but the platform provisioning flow already targets the Railway path above.

## 2) Default behavior with no external routing config

If `REST_CONNECTOR_ROUTING_CONFIG_LOCATION` is not provided, the connector loads:

- `classpath:actions-routing.yml`

That classpath default is now:

- generic
- safe-by-default
- empty under `actions:`

So the connector boots, but `/actions/execute` returns `ACTION_NOT_SUPPORTED` until you provide real routes.

## 3) Minimum environment variables

For a useful deployment you typically set:

- `REST_CONNECTOR_ROUTING_CONFIG_LOCATION=https://<platform>/api/deployments/.../artifacts/actions-routing.yml`
- `CONNECTOR_API_KEY=<secret>`
- `REST_CONNECTOR_RUNTIME_PROXY_ENABLED=true`
- `REST_CONNECTOR_RUNTIME_PROXY_BASE_URL=https://<runtime>.up.railway.app`
- `REST_CONNECTOR_RUNTIME_PROXY_API_KEY=<secret>`
- `REST_CONNECTOR_RUNTIME_PROXY_API_KEY_HEADER=X-AIFABRIC-API-KEY`

Optional CORS:

- `CORS_ALLOWED_ORIGINS=https://your-ui.example.com`
- `CORS_ALLOWED_ORIGIN_PATTERNS=https://*.your-ui.example.com`
- `CORS_ALLOW_CREDENTIALS=true|false`

## 4) Manual routing without the platform

If you are not using the platform yet, you can still provide routing manually by setting:

- `REST_CONNECTOR_ROUTING_CONFIG_LOCATION=file:/config/actions-routing.yml`

and mounting `/config/actions-routing.yml`.

You can also rely on the classpath default and override behavior through env placeholders such as:

- `CONNECTOR_ALLOW_UNAUTHENTICATED`
- `CONNECTOR_API_KEY`
- `UPSTREAM_BASE_URL`
- `AUTHZ_ENABLED`
- `AUTHZ_UPSTREAM_BASE_URL`

## 5) Health and admin checks

- `GET /actuator/health`
- `GET /api/admin/overview`
- `GET /api/admin/actions/overview`
- `GET /api/admin/actions/{actionId}`

If inbound API-key auth is enabled, send the configured connector API key header.

## 6) Runtime wiring

In runtime env vars:

- `ACTIONS_CONNECTOR_BASE_URL=https://<your-railway-connector>.up.railway.app`
- `ACTIONS_CONNECTOR_API_KEY=<same as connector inbound key>`

Common failure mode:

- `URI is not absolute` means one of the configured base URLs is missing `https://`.
