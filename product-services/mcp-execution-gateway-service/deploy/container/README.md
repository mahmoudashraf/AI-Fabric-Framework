# MCP Execution Gateway Container Image

This directory contains the provider-neutral container image for the MCP Execution Gateway.

Use this Dockerfile for Coolify, Railway, local Docker, and CI builds:

```text
product-services/mcp-execution-gateway-service/deploy/container/Dockerfile
```

Deployment providers must inject gateway credentials and downstream MCP server credentials as runtime environment variables. Do not pass secrets as Docker build arguments or bake them into image layers.

The legacy `deploy/railway/Dockerfile` path is kept only for existing Railway projects that still point there.
