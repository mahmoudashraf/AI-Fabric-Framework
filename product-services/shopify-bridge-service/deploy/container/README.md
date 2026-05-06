# Shopify Bridge Container Image

This directory contains the provider-neutral container image for the hosted Shopify Bridge backend and bundled merchant UI.

Use this Dockerfile for Coolify, Railway, local Docker, and CI builds:

```text
product-services/shopify-bridge-service/deploy/container/Dockerfile
```

Deployment providers must inject Shopify, Platform, billing, and MCP gateway credentials as runtime environment variables. Do not pass these secrets as Docker build arguments or bake them into image layers.

The legacy `deploy/railway/Dockerfile` path is kept only for existing Railway projects that still point there.
