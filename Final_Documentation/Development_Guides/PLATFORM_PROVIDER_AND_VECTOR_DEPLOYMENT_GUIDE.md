# Platform Provider And Vector Deployment Guide

This guide explains how the Platform V2 provider workspace now drives real deployment behavior for LLM providers, embedding providers, and vector backends.

It covers:

- the supported provider/vector matrix
- what is stored in deployment draft config vs the platform secret store
- how vendor-backed templates now behave
- how Pinecone and Qdrant managed provisioning works
- how to use provider probes, including unsaved-edit preview probes
- what release verification checks before apply

## 1. Scope

The platform now supports the framework-backed matrix below in the structured `Providers` workspace.

### 1.1 LLM providers

- `openai`
- `azure`
- `anthropic`
- `cohere`
- `gemini`

### 1.2 Embedding providers

- `openai`
- `azure`
- `cohere`
- `gemini`
- `onnx`
- `rest`

### 1.3 Vector backends

- `lucene`
- `memory`
- `qdrant`
- `pinecone`
- `weaviate`
- `milvus`

The platform compiles these choices into managed Railway runtime env, validates the required non-secret fields, and checks the required secrets before release.

## 2. Source Of Truth

The platform separates provider configuration into two layers.

### 2.1 Deployment draft config

The draft stores non-secret, deployment-scoped values such as:

- selected LLM provider
- selected embedding provider
- selected vector backend
- model names
- provider base URLs
- Azure deployment names
- Qdrant host and ports
- Pinecone index name, cloud, region, metric
- Weaviate host and scheme
- Milvus host and database name
- advanced timeouts, priorities, and routing overrides

These values are edited in the `Providers` workspace and become part of the deployment draft and published version.

### 2.2 Platform secret store

Secrets do not belong in deployment drafts. They live in the platform secret workspace.

Examples:

- `OPENAI_API_KEY`
- `ANTHROPIC_API_KEY`
- `AZURE_OPENAI_API_KEY`
- `COHERE_API_KEY`
- `GEMINI_API_KEY`
- `QDRANT_API_KEY`
- `PINECONE_API_KEY`
- `WEAVIATE_API_KEY`
- `MILVUS_USERNAME`
- `MILVUS_PASSWORD`

The platform uses these secrets for:

- runtime env compilation
- vendor probe execution
- managed vector provisioning
- release verification

## 3. Template Behavior

Vendor-backed templates are no longer just informational presets. Some now default to the managed platform path.

### 3.1 Qdrant template

`dev-openai-qdrant` now defaults to:

- `vectorStrategy = qdrant`
- `qdrantManagedCollectionsEnabled = true`

This means:

- after creation, the operator still needs to set `qdrantHost`
- apply will create or reconcile one Qdrant collection per configured entity type when the target cluster is reachable

### 3.2 Pinecone templates

`dev-azure-pinecone` and `dev-openai-rest-pinecone` now default to:

- `vectorStrategy = pinecone`
- `pineconeManagedIndexEnabled = true`
- a generated default `pineconeIndexName`

This means:

- the deployment starts from a managed-index posture
- the operator should review the generated index name and region
- apply will create or reconcile the Pinecone index when `PINECONE_API_KEY` is present

## 4. Managed Vector Provisioning

The platform currently supports managed external vector provisioning in two forms.

### 4.1 Pinecone managed index

When:

- `vectorStrategy = pinecone`
- `pineconeManagedIndexEnabled = true`

The platform treats Pinecone index management as part of the deployment.

Required operator inputs:

- `pineconeIndexName`
- `pineconeRegion`
- `pineconeCloud`
- `PINECONE_API_KEY` in Secrets

What apply does:

- checks for the target index
- creates it if missing
- verifies or reconciles key metadata such as dimensions and metric

What the platform shows:

- managed provisioning summary in `Providers`
- pre-apply verification evidence
- readiness impact

### 4.2 Qdrant managed collections

When:

- `vectorStrategy = qdrant`
- `qdrantManagedCollectionsEnabled = true`

The platform treats Qdrant collection management as part of the deployment.

Required operator inputs:

- `qdrantHost`
- entity types in `Entities`
- optional `QDRANT_API_KEY` in Secrets when the cluster is protected

What apply does:

- connects to the configured Qdrant cluster
- checks for one collection per entity type
- creates missing collections using the deployment vector dimensions

Important:

- the platform manages collections inside an existing Qdrant deployment
- it does not create the Qdrant cluster/account itself

## 5. Qdrant Setup Guidance

For Qdrant Cloud, set `qdrantHost` to the full HTTPS cluster endpoint.

Example shape:

```text
https://<cluster-id>.<region>.aws.cloud.qdrant.io
```

Notes:

- if `qdrantHost` is a full `https://` URL, the platform treats that as the base endpoint
- port fields matter primarily for non-URL host usage
- if the cluster requires auth, add `QDRANT_API_KEY` in the Secrets workspace

Recommended operator flow:

1. Choose the Qdrant-backed template.
2. Open `Providers`.
3. Set the Qdrant host.
4. Add `QDRANT_API_KEY` in `Secrets` if needed.
5. Run provider probes.
6. Review `Entities` so the intended collection set is correct.
7. Publish and apply.

## 6. Pinecone Setup Guidance

For Pinecone managed index deployments:

1. Choose the Pinecone-backed template.
2. Open `Providers`.
3. Review or change `pineconeIndexName`.
4. Review `pineconeCloud`, `pineconeRegion`, `pineconeMetric`, and dimensions.
5. Add `PINECONE_API_KEY` in `Secrets`.
6. Run provider probes.
7. Publish and apply.

Important:

- the platform creates or reconciles the index inside your Pinecone account
- it does not create the Pinecone account or project for you

## 7. Other External Backends

### 7.1 REST embeddings

When `embeddingProvider = rest`, the operator must set:

- `restEmbeddingBaseUrl`
- optionally endpoint paths, model identifier, timeout, and startup validation

The platform can probe the external embedding service before apply.

### 7.2 Weaviate

When `vectorStrategy = weaviate`, the operator must set:

- `weaviateHost`
- `weaviateScheme` and `weaviatePort` as needed
- `WEAVIATE_API_KEY` in Secrets if the cluster is protected

### 7.3 Milvus

When `vectorStrategy = milvus`, the operator must set:

- `milvusHost`
- `milvusPort`
- optional `MILVUS_USERNAME` and `MILVUS_PASSWORD` in Secrets

Current Milvus support compiles deployment config and guidance, but does not yet provide a platform-safe HTTP readiness probe equivalent to Pinecone/Qdrant/Weaviate.

## 8. Provider Workspace Behavior

The `Providers` workspace now has four important behaviors.

### 8.1 Structured editor

The page exposes only the fields relevant to the chosen provider/vector stack.

### 8.2 Vendor onboarding guidance

The page now explains, for the current draft:

- which host/base URL still needs review
- which secret must exist
- what the platform will create or reconcile at apply time

### 8.3 Saved-draft vendor probes

The original vendor probe action still exists and can inspect the saved draft state.

### 8.4 Current-buffer probe preview

If the editor has unsaved changes, the probe action now uses the current browser buffer for editors instead of forcing a save first.

This is the correct flow for:

- trying a new Qdrant endpoint
- trying a new Pinecone index/region combination
- testing a REST embedding base URL
- checking a Weaviate host change

The draft remains unchanged until the operator explicitly saves.

## 9. Verification And Readiness

Provider/vector configuration now feeds real release and readiness logic.

### 9.1 Pre-apply verification

The platform now checks:

- provider secret requirements
- managed vector provisioning prerequisites
- vendor probe outcomes for external dependencies

If required vendor connectivity fails before apply, release verification can fail before rollout.

### 9.2 Production readiness

Readiness now includes provider connectivity evidence as its own area.

This gives operators visibility into:

- whether external vendor dependencies were checked
- whether connectivity is blocked or failing
- whether the deployment is ready for rollout from a provider standpoint

## 10. Recommended Operator Workflow

For any non-local provider/vector deployment:

1. Create the deployment from the closest template.
2. Open `Providers`.
3. Review the selected provider stack and any managed vendor provisioning defaults.
4. Add required secrets in `Secrets`.
5. Use vendor probes.
6. Save the provider draft once satisfied.
7. Publish a version.
8. Run or review verification.
9. Apply the version.
10. Confirm live state in `Overview`, `Diagnostics`, and `Production readiness`.

## 11. Current Limits

The current platform implementation is strong on deployment-scoped provider control, but a few boundaries remain:

- the platform manages Pinecone indexes and Qdrant collections, not vendor account/cluster creation
- Weaviate and Milvus are supported in config/governance flows, but cluster lifecycle automation is still out of scope
- vendor probes are on-demand, not background polling
- provider plugin registration and arbitrary custom providers are still future work

## 12. Related Guides

- [PLATFORM_V2_FEATURES_GUIDE.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Final_Documentation/Development_Guides/PLATFORM_V2_FEATURES_GUIDE.md)
- [RAILWAY_DEPLOYMENT_OPERATIONS_VIA_PLATFORM_GUIDE.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Final_Documentation/Development_Guides/RAILWAY_DEPLOYMENT_OPERATIONS_VIA_PLATFORM_GUIDE.md)
- [REALAPI_PROVIDER_MATRIX_TESTING_GUIDE.md](/Users/mahmoudashraf/Downloads/Projects/TheBaseRepo/Final_Documentation/Development_Guides/REALAPI_PROVIDER_MATRIX_TESTING_GUIDE.md)
