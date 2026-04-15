# Free LLM and Embedding Deployment Strategy

Status: platform infrastructure strategy document (2026-04-15)

This document is a platform and operations strategy, not the marketplace product contract.

Use it for:

- shared inference infrastructure direction
- cost and tier economics
- model-service deployment strategy
- provider-stack rollout sequencing

Do not use it as the marketplace taxonomy source.

Marketplace productization for this area now lives in:

- `doc/Productization/future-work/MarketPlace/MARKETPLACE_INFERENCE_PROFILE_PRODUCTIZATION_PLAN.md`

That companion plan is now the shipped product contract for marketplace inference offers and defines the product boundary:

- public marketplace name should be `INFERENCE_PROFILE`
- it must compile into deployment `providerConfig`
- marketplace must not expose arbitrary runtime or model-server code

This document defines how Loom AI can deploy free, open-source LLM and embedding models as external services to power a genuinely free tier and reduce per-tenant costs across all paid tiers.

---

## 1) Problem Statement

Current cost structure per tenant conversation:

| Component | Current Cost | Who Pays |
|---|---|---|
| Embedding (ONNX in-process) | $0 | Nobody — local MiniLM |
| LLM orchestration (intent, routing) | $0.01-0.05 | Loom AI pays cloud API |
| LLM generation (final answer) | $0.03-0.10 | Loom AI pays cloud API |
| Vector DB query | ~$0.0001 | Loom AI infra |
| **Total per conversation** | **$0.04-0.15** | |

At $49/mo starter with 5K conversations, LLM costs alone are $200-750/mo — the tier loses money.

**Goal:** reduce per-conversation cost to $0.001-0.01 for free and starter tiers by deploying open-source models as external services.

---

## 2) Architecture: Separate Model Services

### Current state

The ONNX embedding model (all-MiniLM-L6-v2, 86MB) is bundled in the runtime JAR at `ai-infrastructure-onnx-starter/src/main/resources/models/embeddings/`. This works for small models but does not scale to larger models (1-4GB) or GPU-accelerated inference.

### Target state

```
┌─────────────────────────────────────────────────────────┐
│                    LOOM AI PLATFORM                     │
│                                                         │
│  ┌─────────────┐    ┌──────────────┐    ┌────────────┐ │
│  │ Runtime A    │    │ Runtime B    │    │ Runtime C  │ │
│  │ (Customer X) │    │ (Customer Y) │    │ (Cust Z)   │ │
│  └──────┬───────┘    └──────┬───────┘    └─────┬──────┘ │
│         │                   │                   │       │
│         └───────────────────┼───────────────────┘       │
│                             │                           │
│                    ┌────────▼────────┐                  │
│                    │  Provider       │                  │
│                    │  Selection      │                  │
│                    └───┬────┬────┬───┘                  │
│                        │    │    │                      │
└────────────────────────┼────┼────┼──────────────────────┘
                         │    │    │
              ┌──────────┘    │    └──────────┐
              ▼               ▼               ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
   │ Railway:     │  │ Railway:     │  │ Cloud API:   │
   │ Embedding    │  │ LLM          │  │ OpenAI /     │
   │ Service      │  │ Orchestrator │  │ Anthropic    │
   │ (bge-large)  │  │ (Llama 8B)   │  │ (GPT-4)     │
   │ FREE         │  │ FREE         │  │ PAID         │
   └──────────────┘  └──────────────┘  └──────────────┘
```

Key principle: **model services are shared infrastructure, not per-customer.** One embedding service and one LLM service serve all tenants.

---

## 3) Embedding Service Deployment

### 3.1 What exists in codebase

The framework already has two providers that support external embedding services:

**RestEmbeddingProvider** (`ai-infrastructure-provider-rest`)
- Connects to any HTTP service exposing `/embed`, `/embed/batch`, `/health`
- Request format: `{"text": "...", "model": "..."}`
- Response format: `{"embedding": [0.1, 0.2, ...]}`
- Auto-detects dimensions from first response
- Batch support with fallback to sequential
- Location: `com.ai.infrastructure.provider.rest.RestEmbeddingProvider`

**OpenAIEmbeddingProvider** (`ai-infrastructure-provider-openai`)
- Connects to any OpenAI-compatible `/v1/embeddings` endpoint
- Supports custom `base-url` — can point to self-hosted services
- Supports dimension reduction for text-embedding-3 models
- Location: `com.ai.infrastructure.provider.openai.OpenAIEmbeddingProvider`

### 3.2 Recommended deployment: Hugging Face TEI on Railway

**Why TEI (Text Embeddings Inference):**
- Production-grade inference server by Hugging Face
- Exposes OpenAI-compatible `/v1/embeddings` endpoint
- Built-in batching, caching, and request queuing
- Supports CPU and GPU inference
- Docker image ready to deploy

**Railway Dockerfile:**

```dockerfile
FROM ghcr.io/huggingface/text-embeddings-inference:cpu-1.6
CMD ["--model-id", "BAAI/bge-large-en-v1.5", "--port", "8080"]
```

**Railway configuration:**
- Port: 8080
- RAM: 2-4GB depending on model
- No GPU required for embedding models up to 1.5GB
- Health check: `GET /health`

### 3.3 Connection from runtime — zero code changes

Use the existing OpenAI provider pointed at Railway:

```yaml
ai:
  providers:
    embedding-provider: openai
    openai:
      base-url: "https://embedding-service-production.up.railway.app/v1"
      api-key: "not-required"
      embedding-model: "BAAI/bge-large-en-v1.5"
      embedding-dimensions: 1024
```

Or use the existing REST provider:

```yaml
ai:
  providers:
    embedding-provider: rest
    rest:
      enabled: true
      validate-on-startup: true
      base-url: "https://embedding-service-production.up.railway.app"
      endpoint: "/embed"
      batch-endpoint: "/embed/batch"
      model: "bge-large-en-v1.5"
```

Both paths require zero code changes. Configuration only.

### 3.4 Model selection per tier

| Tier | Model | Dimensions | RAM | Railway Cost | Quality (MTEB) |
|---|---|---|---|---|---|
| Free | ONNX in-process (bge-small-en-v1.5) | 384 | 0 (bundled) | $0 | ~62 |
| Starter | Railway: bge-base-en-v1.5 | 768 | 1GB | ~$10/mo | ~64 |
| Growth | Railway: bge-large-en-v1.5 | 1024 | 2GB | ~$20/mo | ~65 |
| Business | Railway: gte-large-en-v1.5 | 1024 | 3GB | ~$30/mo | ~66 |
| Enterprise | Customer's API key (OpenAI, Cohere, etc.) | Varies | 0 | Customer pays | ~67+ |

**Important:** the Railway services are shared across all tenants on that tier. One $20/mo service handles all Growth-tier tenants.

### 3.5 Upgrade the bundled ONNX model

Replace the current `all-MiniLM-L6-v2.onnx` (2021, MTEB ~56) with `bge-small-en-v1.5` (2024, MTEB ~62):

- Same 384 dimensions — no vector DB migration needed
- Same ONNX format — no code changes
- 130MB vs 86MB — negligible size increase
- ~10% better retrieval quality for free

Steps:
1. Download `bge-small-en-v1.5` from Hugging Face and convert to ONNX
2. Replace files in `ai-infrastructure-onnx-starter/src/main/resources/models/embeddings/`
3. Update `application.properties` with new model metadata
4. Run existing embedding integration tests to verify

For multilingual support, consider `bge-m3` (100+ languages, 1024 dimensions, 2.5GB — deploy on Railway, not in-process).

---

## 4) LLM Orchestration Service Deployment

### 4.1 What the orchestration layer does

The framework already separates orchestration from generation via `AIProviderConfig`:

```java
// Orchestration: intent classification, entity extraction, action routing
private OrchestrationLlmConfig orchestration;

// Generation: final answer composition using RAG context
private GenerationLlmConfig generation;
```

Configuration:

```yaml
ai:
  providers:
    llm-provider: openai
    orchestration:
      llm-provider: openai       # Can be different from generation
      model: gpt-4o-mini         # Cheap model for classification
      temperature: 0.1           # Low temp for consistent routing
      max-tokens: 500            # Short outputs for routing decisions
    generation:
      llm-provider: openai
      model: gpt-4o              # Quality model for answers
      temperature: 0.7
      max-tokens: 2000
```

### 4.2 Adding a local LLM provider for orchestration

**Deployment option: Ollama on Railway or dedicated VPS**

Ollama exposes an OpenAI-compatible API at `/v1/chat/completions`. The existing OpenAI provider can connect to it with a custom `base-url`.

**Railway/VPS Dockerfile:**

```dockerfile
FROM ollama/ollama:latest

# Pre-pull model at build time
RUN ollama serve & sleep 5 && ollama pull llama3.1:8b && pkill ollama

EXPOSE 11434
CMD ["serve"]
```

**Connection — use existing OpenAI provider pointed at Ollama:**

```yaml
ai:
  providers:
    orchestration:
      llm-provider: openai
      model: llama3.1:8b
    openai:
      base-url: "https://llm-service-production.up.railway.app/v1"
      api-key: "not-required"
    generation:
      llm-provider: openai        # Keep cloud API for generation
      model: gpt-4o
    openai:                        # Generation still uses real OpenAI
      api-key: "${OPENAI_API_KEY}"
```

**Problem:** the current config structure uses a single `openai` block. Pointing orchestration at Ollama and generation at OpenAI requires either:

a) A second OpenAI-compatible provider config (e.g. `openai-local`)
b) Using the REST provider for the local LLM
c) Adding base-url override to OrchestrationLlmConfig

**Recommended approach (a):** add a `local` provider that reuses the OpenAI-compatible protocol. This is a small code change — register a second OpenAI provider instance with different base-url.

### 4.3 Model selection for orchestration

| Model | Size | GPU Required | Monthly Cost (VPS) | Orchestration Quality |
|---|---|---|---|---|
| Phi-3 Mini (3.8B) | 2.3GB | No (CPU OK) | ~$20/mo | Good for classification |
| Llama 3.1 8B | 4.7GB | Recommended | ~$50/mo | Very good |
| Mistral 7B | 4.1GB | Recommended | ~$50/mo | Very good |
| Qwen2.5 7B | 4.4GB | Recommended | ~$50/mo | Very good, strong multilingual |
| Llama 3.1 70B | 40GB | Required (A100) | ~$300/mo | Near GPT-3.5 quality |

**Recommendation:** Start with **Llama 3.1 8B** on a GPU VPS (~$50/mo). Handles intent classification, entity extraction, and action routing at near-zero per-request cost. Use GPU VPS (Lambda Labs, Vast.ai, RunPod) rather than Railway for LLM inference — Railway's CPU-only plans are too slow for LLM generation.

### 4.4 Hybrid routing strategy

```
User Message
    │
    ▼
┌─────────────────────────┐
│ Local Llama 8B           │  Cost: ~$0.001/request
│ (Ollama on VPS)          │
│                          │
│ 1. Classify intent       │
│ 2. Extract entities      │
│ 3. Route to action       │
│ 4. Decide RAG strategy   │
│ 5. Assess complexity     │
│    └─ Simple? → Answer   │
│    └─ Complex? → Forward │
└──────────┬───────────────┘
           │
    ┌──────┴──────┐
    │             │
  Simple       Complex
  (70%)        (30%)
    │             │
    ▼             ▼
┌─────────┐  ┌──────────┐
│ Local    │  │ Cloud    │
│ Llama 8B │  │ GPT-4o   │
│ $0/req   │  │ $0.05/req│
└─────────┘  └──────────┘

Blended cost: (0.7 × $0.001) + (0.3 × $0.05) = $0.016/conversation
vs current:   $0.04-0.15/conversation
Savings:      60-90%
```

---

## 5) Reranker Service (Quality Multiplier)

### 5.1 What a reranker does

After vector search returns top-N candidates, a reranker re-scores them using cross-attention (more accurate than cosine similarity). This dramatically improves RAG answer quality.

```
Without reranker:
  Query → Vector search → Top 5 (some irrelevant) → LLM → mediocre answer

With reranker:
  Query → Vector search → Top 20 → Reranker → Top 5 (all relevant) → LLM → great answer
```

### 5.2 Deployment

Deploy as a TEI service on Railway (TEI supports reranking natively):

```dockerfile
FROM ghcr.io/huggingface/text-embeddings-inference:cpu-1.6
CMD ["--model-id", "BAAI/bge-reranker-v2-m3", "--port", "8081"]
```

### 5.3 Integration point

The reranker sits between vector search and LLM generation. It would be called from the RAG orchestration layer after vector results are returned but before context is assembled for the LLM.

This requires a new service interface:

```java
public interface RerankerProvider {
    List<ScoredDocument> rerank(String query, List<Document> candidates, int topK);
}
```

And a REST implementation pointing at the TEI reranker service.

### 5.4 Cost

| Model | RAM | Railway Cost | Quality Impact |
|---|---|---|---|
| ms-marco-MiniLM-L-6-v2 | 256MB | ~$5/mo | Good |
| bge-reranker-v2-m3 | 1.5GB | ~$15/mo | Excellent, multilingual |

---

## 6) Complete Free-Tier Infrastructure Stack

```
┌─────────────────────────────────────────────────────────┐
│                 LOOM AI FREE STACK                       │
│                                                         │
│  Embeddings:     ONNX bge-small (in-process)    → $0   │
│  Reranker:       Railway bge-reranker            → $15  │
│  Orchestration:  VPS Ollama Llama 8B             → $50  │
│  Generation:     Local Llama 8B (simple queries) → $0*  │
│  Vector DB:      Shared Qdrant cluster           → $50  │
│                                                         │
│  Total shared infra:                            ~$115/mo │
│  Per-tenant cost at 100 tenants:               ~$1.15/mo│
│  Per-tenant cost at 500 tenants:               ~$0.23/mo│
│                                                         │
│  * Complex queries on free tier: rate-limited,          │
│    use Llama 8B only (no cloud API fallback)            │
└─────────────────────────────────────────────────────────┘
```

### Comparison: free tier cost per tenant

| Platform | Cost Model | Per-Tenant Cost |
|---|---|---|
| Tidio | $29/mo minimum per account | $29/mo |
| Gorgias | $1/AI resolution | $50-200/mo at volume |
| Siena AI | $0.90/conversation | $90/mo at 100 convos |
| **Loom AI Free** | Shared infra, local models | **$0.23-1.15/mo** |

This is the cost moat. No competitor can match this because they all depend on cloud LLM APIs.

---

## 7) Implementation Sequence

### Phase 1: Upgrade Bundled ONNX Model (1-2 days)

No external service needed. Pure improvement.

1. Convert `bge-small-en-v1.5` to ONNX format
2. Replace `all-MiniLM-L6-v2.onnx` in resources
3. Update tokenizer and model metadata
4. Run existing integration tests
5. Verify dimension compatibility (both 384d — no migration)

### Phase 2: Deploy External Embedding Service on Railway (1 week)

1. Create Railway project with TEI Dockerfile
2. Deploy with `bge-large-en-v1.5` model
3. Configure a test deployment to use REST or OpenAI provider pointed at Railway
4. Benchmark: latency, throughput, quality comparison vs in-process ONNX
5. Document the deployment process for the platform operations guide

### Phase 3: Deploy Ollama LLM Service (1-2 weeks)

1. Provision a GPU VPS (Lambda Labs, RunPod, or Vast.ai)
2. Deploy Ollama with Llama 3.1 8B
3. Add a `local` provider configuration option (second OpenAI-compatible endpoint)
4. Implement complexity classifier in orchestration layer (simple vs complex routing)
5. Configure orchestration to use local LLM, generation to use cloud API
6. Benchmark: orchestration accuracy, latency, cost savings

### Phase 4: Add Reranker Service (1 week)

1. Deploy TEI reranker on Railway
2. Implement `RerankerProvider` interface
3. Add REST-based reranker implementation
4. Integrate into RAG orchestration between vector search and context assembly
5. Benchmark: RAG answer quality with vs without reranker

### Phase 5: Production Configuration Per Tier (1 week)

1. Define tier-to-provider mapping in platform deployment configuration
2. Free tier: ONNX in-process + local Llama only
3. Starter: Railway embedding + local Llama orchestration + GPT-4o-mini generation
4. Growth: Railway embedding + cloud orchestration + GPT-4o generation
5. Enterprise: customer's own API keys for everything
6. Document per-tier capabilities and limitations

---

## 8) Code Changes Required

| Change | Scope | Files Affected | Effort |
|---|---|---|---|
| Replace ONNX model file | Config only | 2-3 resource files | Trivial |
| Point REST/OpenAI provider at Railway | Config only | YAML files | Trivial |
| Add second OpenAI-compatible provider for local LLM | Small code change | AIProviderConfig, new LocalLlmAutoConfiguration | Small |
| Complexity classifier for query routing | New logic | OrchestrationLlmConfig, RAGOrchestrator | Medium |
| RerankerProvider interface + REST impl | New feature | New interface, new service, RAG pipeline modification | Medium |
| Per-tier provider mapping in platform | Platform feature | Deployment config, platform backend | Medium |

**Total new code: ~500-800 lines across 6-8 files.**

The embedding service deployment requires zero code changes — it is purely infrastructure and configuration.

---

## 9) Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Local LLM quality not sufficient for orchestration | Misrouted queries, wrong actions | A/B test against cloud API. Start with orchestration only, not generation. Fall back to cloud on low-confidence classifications. |
| Railway embedding service latency | Slower search experience | TEI has built-in request batching and caching. Deploy in same region as runtime. Accept 50-100ms latency vs 5ms in-process. |
| GPU VPS reliability | LLM service downtime | Use existing fallback mechanism (`enable-fallback: true`). If local LLM is down, fall back to cloud API automatically. |
| Model version drift | Embedding dimension mismatch when upgrading | Never change dimensions for existing tenants. Offer model upgrade as a migration (re-index required). |
| ONNX model conversion issues | Incompatible model format | Use Hugging Face optimum library for reliable ONNX export. Test with existing integration test suite. |

---

## 10) Outcome

After full implementation:

| Metric | Before | After |
|---|---|---|
| Cost per conversation (free tier) | $0.04-0.15 | $0.001-0.005 |
| Cost per conversation (starter) | $0.04-0.15 | $0.005-0.02 |
| Embedding quality (MTEB) | ~56 (MiniLM) | ~62-66 (bge/gte) |
| RAG answer quality | Baseline | +15-25% with reranker |
| Embedding cost per tenant | $0 (already free) | $0 (stays free) |
| External service dependency | Cloud APIs required | Optional — local stack works standalone |
| Free tier viability | Not viable (loses money) | Viable at $0.23-1.15/tenant/mo |

**The free tier becomes the primary acquisition channel.** Merchants start free, see value, upgrade for higher quality models and cloud API access. No competitor can offer a genuinely free AI assistant tier because they all depend on cloud LLM APIs for every request.
