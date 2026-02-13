# LLM Orchestration Model Evaluation

## Executive Summary

The AI-Fabric-Framework **already supports self-hosting an LLM for orchestration** through
the OpenAI provider's configurable `baseUrl`. Any server exposing an OpenAI-compatible
`/chat/completions` endpoint (vLLM, Ollama, llama.cpp, Text Generation Inference) can be
plugged in with zero code changes. The framework also has a dedicated
`OrchestrationLlmConfig` that decouples the orchestration model from the generation model,
so a small, fast, fine-tuned model can handle orchestration while a larger model handles
RAG answer generation.

---

## 1. What the Orchestration LLM Actually Does

The orchestration pipeline (`DefaultOrchestrationPipeline`) executes 10 ordered steps.
The LLM is invoked in these steps:

| Step | Purpose | LLM Task |
|------|---------|----------|
| **IntentExtractionStep** (order 50) | Classify user query | Produce structured JSON: intent type, action, params, vector space, confidence |
| **ProgressiveIntentExtractionEngine** | Fallback ladder | Up to 5 strategies: compound, repair, completion, multi-step |
| **SmartSuggestionsStep** (order 80) | Generate follow-up suggestions | Short text generation |
| **ResponseSanitizationStep** (order 90) | Clean LLM output | Light text processing |

**Critical requirement:** The orchestration LLM must reliably produce **structured JSON**
matching a specific schema (intents array with type, action, confidence, vectorSpace, etc.).
Temperature is set to **0.1** for consistency.

### Example orchestration output schema

```json
{
  "intents": [
    {
      "type": "ACTION | INFORMATION | OUT_OF_SCOPE | CONFIRMATION_POSITIVE",
      "intent": "canonical_intent_name",
      "confidence": 0.95,
      "action": "action_name",
      "actionParams": { "key": "value" },
      "vectorSpace": "policies",
      "requiresRetrieval": true,
      "optimizedQuery": "rewritten search query"
    }
  ],
  "orchestrationStrategy": "DIRECT_ACTION | RETRIEVE_AND_GENERATE | ADMIT_UNKNOWN"
}
```

---

## 2. Current Architecture Support

### Already built-in

The framework **already has** everything needed to use a self-hosted model for
orchestration:

**Separate orchestration LLM config** (`AIProviderConfig.java:204-230`):
```yaml
ai:
  providers:
    orchestration:
      llm-provider: openai        # provider name
      model: my-fine-tuned-model   # model identifier
      temperature: 0.1             # low for deterministic output
      max-tokens: 2000
      timeout: 30
```

**Purpose-based routing** (`AICoreService.java:337-343`):
```java
private GenerationDefaults resolveDefaultsForPurpose(LlmPurpose purpose) {
    return switch (purpose) {
        case ORCHESTRATION -> aiProviderConfig.resolveOrchestrationLlmDefaults();
        case GENERATION   -> aiProviderConfig.resolveGenerationLlmDefaults();
        default           -> aiProviderConfig.resolveLlmDefaults();
    };
}
```

**Configurable baseUrl** on the OpenAI provider (`OpenAIProvider.java:85`):
```java
String url = normalizeBaseUrl(config.getBaseUrl()) + "/chat/completions";
```

This means you can point it at `http://localhost:8000/v1` (vLLM),
`http://localhost:11434/v1` (Ollama), or any OpenAI-compatible endpoint.

### Configuration to use a self-hosted model

```yaml
ai:
  providers:
    # Global LLM (e.g., Claude for RAG generation)
    llm-provider: anthropic

    # Orchestration uses self-hosted model
    orchestration:
      llm-provider: openai
      model: mistral-nemo-instruct-2407   # or your fine-tuned variant
      temperature: 0.1
      max-tokens: 2000
      timeout: 15

    openai:
      enabled: true
      api-key: "not-needed"                # vLLM/Ollama don't require real keys
      base-url: http://localhost:8000/v1   # self-hosted server
      model: mistral-nemo-instruct-2407
```

---

## 3. Model Evaluation for Orchestration

### Requirements checklist

| Requirement | Weight | Notes |
|-------------|--------|-------|
| Reliable structured JSON output | **Critical** | Must produce valid JSON matching the intent schema |
| Classification accuracy | **Critical** | ACTION vs INFORMATION vs OUT_OF_SCOPE |
| Inference speed (low latency) | **High** | Orchestration is on the hot path; every ms counts |
| Small memory footprint | **High** | Must run alongside the app on reasonable hardware |
| Fine-tuning support | **Medium** | Ability to train on domain-specific intent patterns |
| Instruction following | **High** | Must respect the 15-rule system prompt |
| Multi-language support | **Low-Medium** | Depends on user base |

### Recommended models (ranked)

#### Tier 1 - Best fit for orchestration (recommended)

| Model | Parameters | VRAM | Why |
|-------|-----------|------|-----|
| **Mistral Nemo 12B Instruct** | 12B | ~8 GB (Q4) | Best JSON reliability at this size. Apache 2.0 license. Excellent instruction following. Purpose-built for structured tasks. **Top recommendation.** |
| **Qwen 2.5 7B/14B Instruct** | 7-14B | 5-10 GB (Q4) | Outstanding structured output. Strong multilingual. Apache 2.0. Matches GPT-3.5 on classification benchmarks. |
| **Phi-3.5 Mini Instruct** | 3.8B | ~3 GB (Q4) | Microsoft's small model punches above its weight on structured tasks. MIT license. Fastest inference of all candidates. Ideal if hardware is constrained. |

#### Tier 2 - Strong alternatives

| Model | Parameters | VRAM | Why |
|-------|-----------|------|-----|
| **Llama 3.1 8B Instruct** | 8B | ~6 GB (Q4) | Meta's workhorse. Good JSON output with constrained decoding. Large community and fine-tuning ecosystem. |
| **Gemma 2 9B Instruct** | 9B | ~6 GB (Q4) | Google's compact model. Strong reasoning for its size. Good structured output. |
| **Mistral 7B Instruct v0.3** | 7B | ~5 GB (Q4) | Proven performer. Slightly older but very well understood. Huge fine-tuning community. |

#### Tier 3 - For larger hardware budgets

| Model | Parameters | VRAM | Why |
|-------|-----------|------|-----|
| **Mixtral 8x7B Instruct** | 46.7B (12.9B active) | ~26 GB (Q4) | MoE architecture gives large-model quality at smaller-model speed. Excellent JSON and classification. |
| **Llama 3.1 70B Instruct** | 70B | ~40 GB (Q4) | Near-GPT-4 quality. Overkill for orchestration but available if quality is paramount. |
| **DeepSeek-V2-Lite** | 16B | ~10 GB (Q4) | Strong reasoning, MoE-based efficiency. Good for complex multi-intent extraction. |

### Top recommendation: **Mistral Nemo 12B Instruct**

Reasons:
1. **JSON reliability** - Mistral models are known for structured output adherence
2. **Right size** - 12B is large enough for nuanced classification but small enough for fast inference (~30-50 tokens/sec on a single GPU)
3. **Apache 2.0** - Full commercial use, no restrictions
4. **Fine-tunable** - Excellent LoRA/QLoRA support via Hugging Face, Axolotl, or Unsloth
5. **Context window** - 128K tokens (more than enough for the enriched orchestration prompt)
6. **Instruction following** - Strong adherence to system prompts with many rules

---

## 4. Serving Infrastructure

### Recommended serving stack

| Tool | Purpose | Why |
|------|---------|-----|
| **vLLM** | Model serving | OpenAI-compatible API out of the box. Continuous batching, PagedAttention, speculative decoding. Best throughput. |
| **Ollama** | Dev/testing | Dead-simple setup (`ollama run mistral-nemo`). Good for development. Not optimized for production throughput. |
| **Text Generation Inference (TGI)** | Alternative to vLLM | Hugging Face's server. Good Docker support. Grammar-constrained decoding for guaranteed valid JSON. |

### vLLM setup example (production)

```bash
# Pull and serve the model
pip install vllm

vllm serve mistralai/Mistral-Nemo-Instruct-2407 \
  --host 0.0.0.0 \
  --port 8000 \
  --max-model-len 8192 \
  --gpu-memory-utilization 0.90 \
  --quantization awq \
  --guided-decoding-backend outlines  # JSON schema enforcement
```

### Docker Compose addition

```yaml
services:
  orchestration-llm:
    image: vllm/vllm-openai:latest
    ports:
      - "8000:8000"
    volumes:
      - ./models:/root/.cache/huggingface
    environment:
      - HUGGING_FACE_HUB_TOKEN=${HF_TOKEN}
    command: >
      --model mistralai/Mistral-Nemo-Instruct-2407
      --max-model-len 8192
      --quantization awq
      --guided-decoding-backend outlines
    deploy:
      resources:
        reservations:
          devices:
            - driver: nvidia
              count: 1
              capabilities: [gpu]
```

---

## 5. Fine-Tuning Strategy

### Can you fine-tune for orchestration? YES.

The orchestration task is **ideal for fine-tuning** because:

1. **Well-defined input/output format** - System prompt + user query in, structured JSON out
2. **Classifiable** - Intent types are a closed set (ACTION, INFORMATION, OUT_OF_SCOPE, CONFIRMATION)
3. **Domain-specific** - Your available actions, vector spaces, and entity types are fixed
4. **Measurable** - JSON schema validation + classification accuracy are easy to evaluate

### Fine-tuning approach

**Method:** LoRA / QLoRA (parameter-efficient, ~1-5% of total parameters trained)

**Data collection strategy:**
1. Log orchestration requests and responses from your current cloud LLM (GPT-4o-mini / Claude)
2. Filter for high-confidence, correct extractions (confidence > 0.9)
3. Include edge cases: multi-intent, out-of-scope, confirmation flows
4. Target **1,000-5,000 high-quality examples** for initial training

**Training data format (OpenAI-style JSONL):**
```json
{
  "messages": [
    {"role": "system", "content": "<full orchestration system prompt with actions/knowledge>"},
    {"role": "user", "content": "Cancel my order #12345 and show me the refund policy"},
    {"role": "assistant", "content": "{\"intents\":[{\"type\":\"ACTION\",\"intent\":\"cancel_order\",\"confidence\":0.95,\"action\":\"cancel_order\",\"actionParams\":{\"orderId\":\"12345\"}},{\"type\":\"INFORMATION\",\"intent\":\"refund_policy\",\"confidence\":0.92,\"vectorSpace\":\"policies\",\"requiresRetrieval\":true}],\"orchestrationStrategy\":\"RETRIEVE_AND_GENERATE\"}"}
  ]
}
```

**Tools:**
- **Unsloth** - 2x faster LoRA training, memory efficient (recommended)
- **Axolotl** - Flexible YAML-based training config
- **Hugging Face TRL** - SFTTrainer with LoRA adapters

**Estimated training:**
- 3,000 examples, 3 epochs on Mistral Nemo 12B with QLoRA
- ~2-4 hours on a single A100 (40 GB) or ~6-8 hours on an RTX 4090 (24 GB)
- Cost on cloud: ~$5-15 on Lambda Labs or RunPod

### Evaluation metrics

| Metric | Target | How to measure |
|--------|--------|----------------|
| JSON validity rate | > 99% | Parse every output; schema-validate |
| Intent type accuracy | > 95% | Compare against labeled test set |
| Action name accuracy | > 93% | Exact match on action field |
| Parameter extraction F1 | > 90% | F1 on extracted actionParams |
| Latency p95 | < 500ms | Measure end-to-end with vLLM |

---

## 6. Deployment Architecture

```
                     +---------------------------+
                     |   AI-Fabric Application    |
                     |                           |
                     |  OrchestrationLlmConfig   |
                     |  provider: openai          |
                     |  baseUrl: localhost:8000   |
                     +--------+------------------+
                              |
                   LlmPurpose.ORCHESTRATION
                              |
                     +--------v------------------+
                     |  vLLM / TGI Server        |
                     |  Mistral Nemo 12B (AWQ)   |
                     |  + LoRA adapter            |
                     |  Port 8000                |
                     |  /v1/chat/completions     |
                     +---------------------------+
                              |
                         Single GPU
                     (RTX 4090 / A100 / L4)
```

### Hardware requirements

| Configuration | GPU | VRAM | Throughput | Cost (cloud/mo) |
|--------------|-----|------|------------|-----------------|
| **Dev/Testing** | RTX 3060 12GB | 12 GB | ~15 tok/s | Local |
| **Small Production** | RTX 4090 | 24 GB | ~40 tok/s | ~$300 |
| **Production** | A100 40GB | 40 GB | ~60 tok/s | ~$800 |
| **High Throughput** | A100 80GB | 80 GB | ~80 tok/s | ~$1200 |

---

## 7. Cost-Benefit Analysis

### Current state (cloud API)

Assuming orchestration handles ~100K requests/month at ~800 tokens per request:
- GPT-4o-mini: ~$12/month (input) + ~$48/month (output) = **~$60/month**
- Claude Haiku: ~$20/month (input) + ~$100/month (output) = **~$120/month**

### Self-hosted (Mistral Nemo 12B on RTX 4090)

- Cloud GPU rental: ~$300/month
- Can handle ~100K+ requests/month easily
- **Break-even at ~250K-500K requests/month vs GPT-4o-mini**

### When self-hosting makes sense

| Factor | Cloud API | Self-Hosted |
|--------|-----------|-------------|
| Volume < 200K req/mo | Cheaper | More expensive |
| Volume > 500K req/mo | More expensive | Cheaper |
| Data privacy | Data leaves your infra | Data stays local |
| Latency | 200-800ms (network + inference) | 50-200ms (inference only) |
| Customization | Prompt engineering only | Fine-tuning + constrained decoding |
| Reliability | Depends on provider uptime | You control availability |
| Fine-tuning | Limited or expensive | Full control |

---

## 8. Implementation Roadmap

### Phase 1 - Validate with Ollama (no code changes)

1. Install Ollama, pull `mistral-nemo`
2. Configure `application.yml` to point orchestration at `http://localhost:11434/v1`
3. Run integration tests against the self-hosted model
4. Measure JSON validity rate and classification accuracy

### Phase 2 - Collect training data

1. Add logging to `IntentQueryExtractor` to capture input/output pairs
2. Run with cloud LLM (GPT-4o-mini) for 2-4 weeks
3. Curate 3,000+ high-quality training examples
4. Build evaluation test set (500+ labeled examples)

### Phase 3 - Fine-tune

1. Fine-tune Mistral Nemo 12B with QLoRA on collected data
2. Evaluate against test set (target: >95% intent accuracy, >99% JSON validity)
3. Iterate on training data quality if metrics are below target

### Phase 4 - Production deployment

1. Serve fine-tuned model via vLLM with AWQ quantization
2. Enable guided decoding (JSON schema enforcement) as safety net
3. Configure orchestration provider to point at vLLM
4. Keep cloud LLM as fallback (`enable-fallback: true`)

---

## 9. Summary

| Question | Answer |
|----------|--------|
| Can we self-host an LLM for orchestration? | **Yes.** The framework already supports it via `OrchestrationLlmConfig` + OpenAI provider's configurable `baseUrl`. Zero code changes needed. |
| Can we fine-tune it? | **Yes.** The orchestration task (structured JSON classification) is ideal for LoRA fine-tuning. 3K-5K examples are sufficient. |
| Which model? | **Mistral Nemo 12B Instruct** - best balance of JSON reliability, speed, size, and fine-tunability. Apache 2.0 licensed. |
| What serving stack? | **vLLM** for production, **Ollama** for development. |
| When does it pay off? | At >500K requests/month for cost. Immediately for latency, privacy, and customization. |
