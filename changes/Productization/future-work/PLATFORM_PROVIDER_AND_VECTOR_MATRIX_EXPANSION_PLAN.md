# Platform Provider And Vector Matrix Expansion Plan

## Purpose

Expand the platform so deployment drafts, validation, secret governance, provisioning plans, and verification flows reflect the full provider and vector backend matrix already supported by AI Fabric.

This plan keeps the platform as the source of truth for production deployments instead of limiting operators to a smaller subset than the runtime can actually run.

## Framework-Supported Matrix

### LLM providers

- `openai`
- `azure`
- `anthropic`
- `cohere`
- `gemini`

### Embedding providers

- `onnx`
- `openai`
- `azure`
- `cohere`
- `gemini`
- `rest`

### Vector backends

- `lucene`
- `memory`
- `qdrant`
- `pinecone`
- `weaviate`
- `milvus`

## Current Platform Gap

Today the platform only exposes and compiles a narrow managed subset:

- LLM: `openai`, `anthropic`
- Embeddings: `openai`, `onnx`
- Vector: `lucene`, `qdrant`

This creates four problems:

1. The provider editor under-represents what the runtime can execute.
2. Validation and readiness checks do not understand the broader provider matrix.
3. Provisioning plans do not compile all required env vars and secret dependencies.
4. Secret usage, service config, and verification can look healthy while unsupported provider choices are silently impossible in managed deployment flow.

## Wave 4 Scope

Wave 4 should make the provider stack fully first-class in platform-managed deployments.

### Operator-visible outcomes

- Provider editor exposes every framework-supported provider and vector backend.
- The platform asks only for the non-secret fields required by the selected combination.
- Secret dependencies are explicit in the secret workspace, readiness scorecard, and verification runs.
- Railway apply compiles correct runtime env for every supported option.
- Source-of-truth and diagnostics show the real chosen stack, not a reduced approximation.

### Security outcomes

- Credentials stay in the platform secret store, not deployment drafts.
- Optional credentials remain optional, but visible.
- Provider-specific connection settings are validated before publish/apply.
- Deployment verification blocks missing required credentials and malformed provider config early.

## Implementation Sequence

### Item 43. Provider catalog normalization

- Expand managed provider constants and supported sets to match framework support.
- Centralize default model names, default embedding models, default dimensions, secret-name resolution, and vector backend config helpers.
- Remove provider-specific assumptions spread across multiple services.

### Item 44. Backend validation expansion

- Validate required fields for:
  - Azure endpoint, deployment name, embedding deployment name when selected
  - REST embedding base URL when selected
  - Qdrant host
  - Pinecone index and endpoint/environment requirements
  - Weaviate host
  - Milvus host
- Preserve permissive support for optional fields and optional secrets.

### Item 45. Secret governance expansion

- Add platform secret catalog entries for:
  - `AZURE_OPENAI_API_KEY`
  - `COHERE_API_KEY`
  - `GEMINI_API_KEY`
  - `PINECONE_API_KEY`
  - `WEAVIATE_API_KEY`
  - `MILVUS_USERNAME`
  - `MILVUS_PASSWORD`
- Reflect these in secret usage, release verification, service config, and readiness.

### Item 46. Railway provisioning env compilation

- Compile runtime env vars for:
  - Azure
  - Cohere
  - Gemini
  - REST embeddings
  - Pinecone
  - Weaviate
  - Milvus
  - Memory vector backend
- Keep existing OpenAI, Anthropic, Lucene, and Qdrant behavior stable.

### Item 47. Provider workspace UI expansion

- Expand the Providers workspace to expose the full matrix.
- Show only the provider/vector-specific fields needed for the selected stack.
- Keep secrets out of this page; show non-secret connection and model settings only.
- Improve summary text so operators understand what is platform-managed and what remains secret-managed.

### Item 48. Verification and source-of-truth parity

- Verify required secrets for all providers and vector backends.
- Expose broader provider stack requirements in service-config model and production readiness.
- Ensure source-of-truth and diagnostics show the selected provider/vector backend accurately.

### Item 49. Template and opinionated preset follow-up

- Add curated template variants only after the generic provider stack is fully supported.
- Keep provider matrix support separate from template proliferation.

## Non-Goals For This Slice

- Multi-provider fallback policy editing in the UI
- Per-purpose LLM routing editors for orchestration vs generation
- Custom provider plugin registration
- Full benchmark/scorecard automation across providers
- Non-Railway provider-specific deployment automation

## Design Rules

1. The platform must not present a provider choice that cannot be compiled into a real deployment plan.
2. Required secrets must be surfaced in secret usage, readiness, and verification, not hidden inside provisioning logic.
3. Provider-specific fields belong in deployment draft config only when they are non-secret and deployment-scoped.
4. Defaults should be safe and explicit, not magical.
5. The provider workspace should stay structured; raw YAML-style editing is a later feature.

## Recommended Delivery Order

1. `43` provider catalog normalization
2. `44` backend validation expansion
3. `45` secret governance expansion
4. `46` Railway provisioning env compilation
5. `47` provider workspace UI expansion
6. `48` verification and source-of-truth parity
7. `49` template/preset follow-up
8. `50` advanced provider tuning and purpose-specific LLM routing
9. `51` provider/vendor connectivity probes from the platform workspace

## First Implementation Slice

The first code slice should complete items `43` through `48` together for a single coherent outcome:

- the platform supports the full framework matrix in draft/edit/apply flow
- the provider page exposes the new options
- the backend validates them
- the runtime env is compiled correctly
- verification and secret governance understand the same matrix

That is the minimum platform-level implementation that makes these options real instead of aspirational.

## Follow-On Slices

### Item 50. Advanced provider tuning and purpose-specific LLM routing

- Expose `enableFallback` in the provider workspace.
- Expose orchestration and generation LLM overrides so deployments can choose a different provider/model for intent work vs response generation.
- Expose advanced provider tuning already supported by AI Fabric:
  - validate-on-startup toggles
  - default max tokens
  - temperature
  - timeout
  - provider priority
  - vector vendor request timeout fields
- Ensure validation, secret readiness, service config, and Railway env compilation understand provider overrides instead of only the primary LLM provider.

### Item 51. Provider/vendor connectivity probes from the platform workspace

- Add on-demand vendor probes for the selected provider/vector stack from the saved draft.
- Keep probes secret-safe and operator-readable.
- Use probe output in provider readiness without storing credentials in deployment drafts.
