# Shopify Bridge Coolify Deployment

Coolify should build the Shopify Bridge from the monorepo root with this Dockerfile path:

```text
/product-services/shopify-bridge-service/deploy/container/Dockerfile
```

Runtime configuration belongs in Coolify application environment variables. Keep secret values as runtime-only provider variables; do not configure them as build-time variables.

Provider-specific compatibility variables such as `RAILWAY_*` are not required for Coolify and should not be used by new Coolify deployments.
