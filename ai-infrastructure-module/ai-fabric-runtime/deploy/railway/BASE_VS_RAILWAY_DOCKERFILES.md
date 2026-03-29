# Runtime Dockerfiles: Base vs Railway

This repo keeps two Dockerfiles for **AI Fabric Runtime**:

- Base:
  - `ai-infrastructure-module/ai-fabric-runtime/Dockerfile`
- Railway:
  - `ai-infrastructure-module/ai-fabric-runtime/deploy/railway/Dockerfile`

## Current contract

Both images are now **packaging-only**.

They do **not** bake ecommerce-store config into the image anymore.

For platform-managed deployments, runtime config should come from:

- `AI_ACTIONS_CATALOG_PATH`
- `AI_CONFIG_DEFAULT_FILE`
- `ACTIONS_CONNECTOR_BASE_URL`
- `ACTIONS_CONNECTOR_API_KEY`

That means the source of truth is:

- platform artifact URLs
- deployment env vars

not Dockerfile-baked demo files.

## What is still different

Behavior is intentionally very close now.

- Both create `/config` so mounted config remains possible.
- Both use the same runtime entrypoint script.
- Both are repo-root builds.
- The Railway Dockerfile simply remains the Railway-targeted path the platform already uses.

## Runtime behavior with no external config

If you run either image with no extra config:

- runtime still boots
- bundled generic `ai-entity-config.yml` is used
- connector actions are not loaded unless `AI_ACTIONS_CATALOG_PATH` is set

This is intentional:

- packaging is neutral
- platform-managed deployments provide real config
- manual/demo deployments can still mount `/config` or set explicit env vars

## Manual/demo config patterns

If you want demo or customer-specific config outside the platform:

- mount a folder to `/config`
- optionally set `SPRING_CONFIG_ADDITIONAL_LOCATION=file:/config/`
- or set the direct config env vars:
  - `AI_ACTIONS_CATALOG_PATH=file:/config/ai-actions.yml`
  - `AI_CONFIG_DEFAULT_FILE=file:/config/ai-entity-config.yml`

If you specifically want the ecommerce-store demo config, use:

- `Real_Apps/ecommerce-store/deploy/runtime/config/`

as a mounted config source, not as baked image content.
