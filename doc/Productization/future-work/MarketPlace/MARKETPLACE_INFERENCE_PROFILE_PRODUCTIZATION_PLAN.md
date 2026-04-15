# Marketplace Inference Profile Productization Plan

Status: shipped implementation baseline and operating reference (2026-04-15)

This document defines how Loom AI should productize LLM and embedding marketplace offers without breaking the platform rule that marketplace features must compile into runtime-backed contracts only.

It is intentionally strict about naming and boundary:

- do not introduce a public plugin type called `LLM_CONFIG`
- if this becomes a public marketplace type, call it `INFERENCE_PROFILE`
- it must compile into deployment `providerConfig`
- runtime must continue to consume normal provider configuration only

Related docs:

- `doc/Productization/future-work/MarketPlace/FREE_LLM_AND_EMBEDDING_DEPLOYMENT_STRATEGY.md`
- `doc/Productization/future-work/MarketPlace/MARKETPLACE_CONTROL_PLANE_COMPOSITION_PLAN.md`
- `doc/Productization/future-work/MarketPlace/README.md`

---

## 1) Executive Summary

The product idea is valid:

- market ONNX-based embeddings
- market optimized orchestration stacks
- market premium hybrid inference stacks
- let operators buy or subscribe to those as marketplace offers

The current name would be wrong if modeled as raw provider configuration:

- `LLM_CONFIG` sounds like raw provider knobs
- the product is not a raw config editor
- the product is a deployment inference package with known cost, latency, and quality behavior

Shipped product name:

- `INFERENCE_PROFILE`

Shipped contract:

- marketplace installs compile into deployment `providerConfig`
- secrets stay in platform secret management
- runtime reads only its normal provider configuration

---

## 2) Why This Is Runtime-Backed

This direction is compatible with the platform rule that public marketplace features must map to real runtime contracts.

The runtime and platform already have a concrete provider surface:

- deployment drafts and versions persist `providerConfig`
- runtime deployment provisioning exports `providerConfig` into runtime environment variables
- provider connectivity and release verification already evaluate provider readiness
- provider editing already exists in the Platform UI

That means inference offers are materially different from the removed unsupported surfaces:

- they are not arbitrary code
- they are not a second runtime engine
- they are not marketplace-only metadata
- they are a controlled way to set a real runtime-backed contract

---

## 3) Product Positioning

### 3.1 What the customer buys

The customer should buy a bounded inference package, not a raw model config.

Examples:

- `Free Local Retrieval Profile`
- `Optimized Orchestration Profile`
- `Premium Hybrid Response Profile`

Each profile should declare:

- which LLM provider is used for orchestration
- which LLM provider is used for generation
- which embedding provider is used
- whether the endpoints are platform-managed or customer-supplied
- expected quality, latency, and pricing posture

### 3.2 What the customer should not buy

Do not market these as:

- arbitrary model files
- arbitrary model-server code
- vector database products
- raw provider JSON patches

---

## 4) Recommended Public Type

Current public marketplace types remain:

- `TEMPLATE`
- `ACTION`
- `DATA`

Shipped public type:

- `INFERENCE_PROFILE`

This is supported only because it has a real compiler target and runtime-backed contract.

It should not be modeled as:

- `LLM_CONFIG`
- `MODEL`
- `PROVIDER`

Those names are too low-level and too easy to turn into an uncontrolled infrastructure surface.

---

## 5) Compiler Target

`INFERENCE_PROFILE` installs should compile into deployment `providerConfig`.

The compiler should be allowed to write only a bounded subset of provider fields.

### 5.1 Already-supported provider fields

The current platform/provider stack already supports these kinds of fields:

- `llmProvider`
- `embeddingProvider`
- `orchestrationLlmProvider`
- `orchestrationModel`
- `orchestrationTemperature`
- `orchestrationMaxTokens`
- `orchestrationTimeout`
- `generationLlmProvider`
- `generationModel`
- `generationTemperature`
- `generationMaxTokens`
- `generationTimeout`
- `openaiBaseUrl`
- `openaiModel`
- `openaiEmbeddingModel`
- `openaiEmbeddingDimensions`
- `azureEndpoint`
- `azureDeploymentName`
- `azureEmbeddingDeploymentName`
- `cohereBaseUrl`
- `cohereModel`
- `cohereEmbeddingModel`
- `geminiBaseUrl`
- `geminiModel`
- `geminiEmbeddingModel`
- `onnxModelAlias`
- `onnxModelPath`
- `onnxTokenizerPath`
- `onnxMaxSequenceLength`
- `onnxUseGpu`
- `restEmbeddingBaseUrl`
- `restEmbeddingEndpoint`
- `restEmbeddingBatchEndpoint`
- `restEmbeddingModel`
- `enableFallback`

### 5.2 Recommended inference contribution shape

The public manifest should not expose all provider fields directly.

It should declare one bounded contribution block, for example:

```json
{
  "pluginType": "INFERENCE_PROFILE",
  "contributions": {
    "inferenceProfile": {
      "profileId": "optimized-orchestration",
      "orchestration": {
        "provider": "openai-compatible",
        "endpointProfileRef": "loom-orch-local",
        "model": "llama3.1:8b",
        "maxTokens": 700,
        "temperature": 0.1
      },
      "generation": {
        "provider": "openai",
        "endpointProfileRef": "openai-cloud",
        "model": "gpt-4o-mini",
        "maxTokens": 2200,
        "temperature": 0.3
      },
      "embedding": {
        "provider": "onnx",
        "modelAlias": "bge-small-en-v1.5"
      },
      "fallbackPolicy": {
        "enabled": true
      }
    }
  }
}
```

The marketplace compiler then turns that into normal deployment `providerConfig`.

---

## 6) Resolved Provider Contract Gap

The provider contract is now complete enough for a real inference marketplace baseline.

### 6.1 What already works

The platform already supports:

- purpose-specific LLM selection for orchestration and generation
- independent embedding provider selection
- deployment provider validation
- provider connectivity probes
- runtime provisioning from deployment `providerConfig`

### 6.2 What was added

The platform now supports purpose-specific endpoint identity through named endpoint-profile references carried in deployment `providerConfig`.

The previously missing case was:

- orchestration may want `openai-compatible` pointed at a local Ollama or vLLM endpoint
- generation may also want `openai` but pointed at real OpenAI cloud
- current config had one OpenAI-style base URL block, not two named endpoint profiles

That gap is now closed through:

- `orchestrationEndpointProfile`
- `generationEndpointProfile`
- `embeddingEndpointProfile`
- runtime provider override resolution per purpose
- validation, provisioning export, provider-connectivity probes, and release verification for those fields

---

## 7) Required Runtime / Framework Support

### Wave 0: provider endpoint profile contract

Implemented.

Recommended shape:

- keep purpose-specific provider selection
- add named endpoint profile references per purpose

Example deployment-side outcome:

```json
{
  "llmProvider": "openai",
  "embeddingProvider": "onnx",
  "orchestrationLlmProvider": "openai",
  "generationLlmProvider": "openai",
  "orchestrationEndpointProfile": "loom-orch-local",
  "generationEndpointProfile": "openai-cloud",
  "embeddingEndpointProfile": "onnx-bundled"
}
```

Implemented runtime/provider changes:

- named endpoint profile config in `AIProviderConfig`
- validation that each referenced endpoint profile exists
- purpose-specific export in runtime env
- provider resolution that combines provider family, endpoint profile, and purpose-specific model overrides

### Wave 1: platform provider contract support

Implemented in:

- draft validation
- source-of-truth views
- provider connectivity probe
- Railway provisioning env export
- release verification

### Wave 2: marketplace compiler support

Implemented:

- public `INFERENCE_PROFILE` type
- manifest validation
- compatibility checks
- install form and secret requirements
- compilation into deployment `providerConfig`

### Wave 3: managed shared inference services

Implemented first-party managed inference endpoint registry.

Control-plane model:

- `PlatformManagedInferenceEndpoint`
  - endpoint id
  - provider protocol family
  - base URL
  - credential secret ref
  - managed by Loom
  - health state
  - allowed profile classes
  - pricing tier eligibility

Deployments never receive marketplace identity here.
They only receive the resolved provider endpoint and secret binding.

### Wave 4: verification and starter offers

Implemented:

- release verification for resolved inference profile fields
- hosted verification that proves:
  - orchestration endpoint is reachable
  - generation endpoint is reachable
  - embedding endpoint is reachable
  - the intended per-purpose provider split is active
- starter first-party offers

---

## 8) Secret and Billing Rules

Inference profiles need very clear ownership rules.

### 8.1 Platform-managed profile

Example:

- Loom shared orchestration endpoint
- Loom shared TEI embedding endpoint

Rules:

- customer does not provide raw provider credentials
- platform resolves internal endpoint secret refs
- billing is subscription-based or tier-included

### 8.2 Customer-supplied profile

Example:

- customer provides OpenAI or Anthropic credentials

Rules:

- install form collects `secretRef`
- platform stores only the secret reference
- profile compiles into deployment `providerConfig`
- pricing is usually `FREE` or `ONE_OFF`, because inference cost is customer-borne

---

## 9) First Three Recommended Offers

### 9.1 `mkp-inference-free-local`

- type: `INFERENCE_PROFILE`
- pricing: `FREE`
- target:
  - free tier
  - low-cost support and retrieval-heavy deployments
- shape:
  - embeddings: ONNX bundled
  - orchestration: cloud mini model or Loom shared low-cost model
  - generation: same low-cost model or operator-supplied provider

### 9.2 `mkp-inference-optimized-orchestration`

- type: `INFERENCE_PROFILE`
- pricing: `SUBSCRIPTION`
- target:
  - operator wants cheap, fast routing and better control-plane margins
- shape:
  - orchestration: Loom shared local or optimized endpoint
  - generation: cloud API
  - embeddings: ONNX or TEI

### 9.3 `mkp-inference-premium-hybrid`

- type: `INFERENCE_PROFILE`
- pricing: `SUBSCRIPTION`
- target:
  - higher-end deployments that want premium answer quality
- shape:
  - orchestration: optimized shared endpoint
  - generation: premium cloud provider
  - embeddings: premium managed endpoint or customer key

---

## 10) Verification Requirements

This feature should not be considered complete without live proof.

Required verification layers:

1. draft validation
- invalid provider families rejected
- unsupported endpoint profile references rejected
- missing secret refs rejected

2. release verification
- applied version `providerConfig` matches compiled expectation
- required provider secret bindings exist
- provider connectivity probes pass

3. hosted live verification
- query proves orchestration is active
- query proves generation is active
- embedding health path passes for embedding-backed profiles
- runtime admin overview and provider summary show expected split

4. rollout verification
- at least one canonical rollout should exercise:
  - ONNX embedding profile
  - hybrid orchestration and generation profile

Current baseline:

- canonical marketplace rollout exports and verifies inference-profile expectations
- live install-flow verification compiles template, action, data, and inference installs into one deployment
- runtime admin overview exposes `marketplaceSupport.inferenceProfileContractVersion`
- provider connectivity probes verify purpose-specific inference endpoints

---

## 11) What This Should Explicitly Not Do

- no arbitrary model-server code upload
- no arbitrary runtime plugin loading
- no per-deployment unmanaged GPU worker creation as a marketplace side effect
- no direct exposure of raw provider JSON patches to publishers
- no second apply path outside normal draft -> publish -> apply

---

## 12) Recommended Sequence

1. Keep `FREE_LLM_AND_EMBEDDING_DEPLOYMENT_STRATEGY.md` as the platform strategy doc.
2. Maintain runtime/provider endpoint-profile support as part of the provider contract.
3. Keep platform validation, connectivity, and release verification aligned with provider fields.
4. Keep `INFERENCE_PROFILE` bounded to deployment `providerConfig`.
5. Extend first-party offers only through the same manifest and install path.
6. Keep live rollout and install-flow verification in CI and operational runbooks.

Short version:

- the idea is worth doing
- the right abstraction is `INFERENCE_PROFILE`
- it should compile into `providerConfig`
- the gating prerequisite was purpose-specific endpoint-profile support in runtime and platform, and that baseline is now implemented
