# MCP Execution Gateway Container Image

This directory contains the provider-neutral container image for the MCP Execution Gateway.

Use this Dockerfile for Coolify, Railway, local Docker, and CI builds:

```text
product-services/mcp-execution-gateway-service/deploy/container/Dockerfile
```

Deployment providers must inject gateway credentials and downstream MCP server credentials as runtime environment variables. Do not pass secrets as Docker build arguments or bake them into image layers.

Railway support is provider configuration only: create or recreate the Railway app with the repository root as the build context and this Dockerfile path. Do not add a Railway-specific Dockerfile for this service.
