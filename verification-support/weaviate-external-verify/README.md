Weaviate external verification support service.

Purpose:
- restore the dedicated public Weaviate endpoint used by canonical verification rollouts
- provide a stable `/v1/.well-known/ready` and `/v1/meta` surface for platform provider-connectivity and hosted deployment verification

Operational notes:
- deploy this service on Railway with service name `weaviate-external-verify-dev`
- set `AUTHENTICATION_APIKEY_ALLOWED_KEYS` from the live `WEAVIATE_API_KEY`
- health path: `/v1/.well-known/ready`
