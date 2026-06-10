# LoomAI Private Product Repository

This repository contains private LoomAI product and platform code.

The reusable AI Fabric Framework source has moved to the public sibling repository:

```text
/Users/mahmoudashraf/Downloads/Projects/ai-fabric-framework
https://github.com/Loom-AI-Labs/ai-fabric-framework
```

## Repository Boundary

This private repository keeps:

- `Platfrom/backend` and Platform UI surfaces
- `Platfrom/partner-ui`
- `product-services/shopify-bridge-service`
- `product-services/mcp-execution-gateway-service`
- `ai-infrastructure-module/ai-fabric-runtime`
- `ai-infrastructure-module/ai-infrastructure-generic-rest-connector`
- `ai-fabric-product`
- private productization, deployment, roadmap, and LLM operating context documents

This private repository no longer keeps:

- `Real_Apps`
- reusable framework library modules from `ai-infrastructure-module`
- framework release workflows
- framework provider/integration test workflows

## Local Framework Consumption

For local development, install the public framework into the local Maven repository first:

```bash
cd /Users/mahmoudashraf/Downloads/Projects/ai-fabric-framework
mvn -f ai-infrastructure-module/pom.xml -DskipTests install
```

Then build private product modules normally:

```bash
cd /Users/mahmoudashraf/Downloads/Projects/TheBaseRepo
mvn -f ai-fabric-product/pom.xml -DskipTests compile
mvn -f ai-infrastructure-module/pom.xml -DskipTests compile
mvn -f Platfrom/backend/pom.xml -DskipTests compile
mvn -f product-services/shopify-bridge-service/pom.xml -DskipTests compile
mvn -f product-services/mcp-execution-gateway-service/pom.xml -DskipTests compile
```

## Deployment Source Boundary

Deployable runtime and generic REST connector services are private product services and live in this repo:

- `ai-infrastructure-module/ai-fabric-runtime`
- `ai-infrastructure-module/ai-infrastructure-generic-rest-connector`

They consume public framework artifacts through Maven. Do not copy reusable framework source back into this private repo to solve deployment-source wiring.
