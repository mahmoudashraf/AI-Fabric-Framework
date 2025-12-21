# ONNX Embedding Provider - Current Implementation Approach

## Overview

The AI Infrastructure Module uses **ONNX Runtime** as the default embedding provider for local, API-free embedding generation. This document describes the current implementation approach, architecture, and design decisions. Pair it with the companion assessments in [`ONNX_PRODUCTION_READINESS_ASSESSMENT.md`](./ONNX_PRODUCTION_READINESS_ASSESSMENT.md) and the packaging plan in [`ONNX_OPTIONAL_STARTER_PLAN.md`](../../docs/ONNX_OPTIONAL_STARTER_PLAN.md) to understand both the current state and the target end state.

## Table of Contents

1. [Implementation Readiness Snapshot](#implementation-readiness-snapshot)
2. [Architecture Overview](#architecture-overview)
3. [Production Hardening Roadmap](#production-hardening-roadmap)
4. [Optional Starter Packaging Alignment](#optional-starter-packaging-alignment)
5. [Current Implementation Details](#current-implementation-details)
6. [Data Flow](#data-flow)
7. [Model Requirements](#model-requirements)
8. [Configuration](#configuration)
9. [Key Design Decisions](#key-design-decisions)
10. [Output Processing](#output-processing)
11. [Performance Characteristics](#performance-characteristics)
12. [Integration with Spring Boot](#integration-with-spring-boot)
13. [Troubleshooting](#troubleshooting)
14. [Future Improvements](#future-improvements)
15. [Summary](#summary)
16. [Related Documentation](#related-documentation)

---

## Implementation Readiness Snapshot

| Area | Status | Notes | Reference |
|------|--------|-------|-----------|
| Core embedding flow | ✅ Stable | ONNX provider returns 384-dim vectors via mean pooling and integrates with existing Spring abstraction. | [Current Implementation Details](#current-implementation-details) |
| Tokenization | 🔴 Needs upgrade | Character-based tokenizer does not match model vocabulary; replace with Hugging Face tokenizers per production assessment. | [Production Hardening Roadmap](#production-hardening-roadmap) |
| Batch processing | ⚠️ Sequential | Batched requests iterate serially; consolidate into single ONNX inference for throughput gains. | [Production Hardening Roadmap](#production-hardening-roadmap) |
| Thread safety | ⚠️ Validate | Concurrent access relies on ONNX session default safety guarantees; add stress tests and synchronization/pooling as needed. | [Production Hardening Roadmap](#production-hardening-roadmap) |
| Observability & resilience | ⚠️ Baseline only | Logging exists, but metrics, health checks, retries, and rate limiting are not yet implemented. | [Production Hardening Roadmap](#production-hardening-roadmap) |
| Packaging | 🚧 Transitioning | Core module still carries models; planned optional starter module will own runtime + assets. | [Optional Starter Packaging Alignment](#optional-starter-packaging-alignment) |

---

## Architecture Overview

### High-Level Architecture

```
Spring Boot Application
    ↓
AIInfrastructureAutoConfiguration
    ↓ (creates @Bean)
ONNXEmbeddingProvider (implements EmbeddingProvider)
    ↓
AIEmbeddingService (uses EmbeddingProvider)
    ↓
VectorSearchService / RAGService
    ↓
Vector Database (Lucene/Pinecone/Memory)
```

### Provider Abstraction

The system uses an `EmbeddingProvider` interface that allows swappable embedding implementations:

- **ONNXEmbeddingProvider** (default): Local ONNX Runtime inference
- **RestEmbeddingProvider**: RESTful API calls to Docker container
- **OpenAIEmbeddingProvider**: OpenAI API calls (fallback)

This abstraction enables switching between providers via configuration without code changes. Upcoming packaging work keeps this abstraction intact while allowing teams to opt into ONNX assets via a dedicated starter module.

---

## Production Hardening Roadmap

The prioritized roadmap below compresses findings from [`ONNX_PRODUCTION_READINESS_ASSESSMENT.md`](./ONNX_PRODUCTION_READINESS_ASSESSMENT.md) into actionable phases. Each phase should land as a focused pull request to preserve reviewability.

### Phase 1 — Stabilize Quality & Concurrency (Critical)
- **Tokenizer parity**: Integrate Hugging Face `tokenizers` (Rust bindings) or an equivalent service so ONNX inputs match the model vocabulary. Track the detailed plan in [`TOKENIZATION_IMPROVEMENT.md`](./TOKENIZATION_IMPROVEMENT.md).
- **Thread-safety verification**: Add concurrent load tests (JUnit + Testcontainers) and guard the ONNX session with synchronization or a lightweight session pool if required.
- **True batch execution**: Accept batched inputs and construct `[batch, sequence_length]` tensors to execute a single ONNX runtime call instead of iterating per request.

### Phase 2 — Resilience & Observability (Important)
- **Rate limiting & circuit breaking**: Add Spring-based rate limiting (e.g., Resilience4j) to prevent resource exhaustion under bursty load.
- **Operational telemetry**: Publish Micrometer metrics (latency, queue depth, failure counts), expose a `HealthIndicator`, and add structured logging for inference outcomes.
- **Timeouts & retries**: Bound inference time via configurable timeouts and include exponential backoff retries around transient `OrtException` scenarios.

### Phase 3 — Scale & Flexibility (Enhancements)
- **Model lifecycle management**: Support multiple models and dynamic configuration for embedding dimensions, sequence lengths, and model identifiers.
- **Performance options**: Offer GPU execution paths, INT8 quantized variants, and lazy model loading when the optional starter module is present.
- **Operability polish**: Document SLO targets, add diagnostics endpoints, and capture benchmarking baselines in the test suite.

> For a deeper explanation of risks, scores, and suggested owners, consult [`ONNX_PRODUCTION_READINESS_ASSESSMENT.md`](./ONNX_PRODUCTION_READINESS_ASSESSMENT.md).

---

## Optional Starter Packaging Alignment

The optional starter strategy defined in [`ONNX_OPTIONAL_STARTER_PLAN.md`](../../docs/ONNX_OPTIONAL_STARTER_PLAN.md) keeps the core module lean while giving adopters a turnkey ONNX setup. This section highlights how the current implementation will evolve alongside that plan.

### Packaging Objectives
- **Isolate heavy assets**: Move ONNX runtime dependencies, tokenizer files, and model binaries from `ai-infrastructure-core` into a new `ai-infrastructure-onnx-starter` module.
- **Auto-configuration opt-in**: Provide a Spring auto-configuration in the starter so dropping the dependency automatically registers `ONNXEmbeddingProvider`.
- **Integration parity**: Ensure integration tests, local dev scripts, and documentation consume the starter module instead of ad-hoc model copies.

### Action Checklist
- [ ] Create the starter Maven module and publish alongside the core artifact.
- [ ] Update `integration-tests/pom.xml` (and related scripts) to depend on the starter rather than local files.
- [ ] Document adoption steps (pom snippet, model override instructions) in this file and the main [`ONNX_OPTIONAL_STARTER_PLAN.md`](../../docs/ONNX_OPTIONAL_STARTER_PLAN.md).

### Developer Experience Notes
- Short term: continue using the embedded assets while packaging work completes.
- Mid term: once the starter is live, teams add a single dependency to enable ONNX and can override model paths via existing `ai.providers.onnx-*` properties.
- Long term: the starter module becomes the canonical place for distributing curated ONNX models, enabling versioned rollouts without inflating the core artifact.

---

## Current Implementation Details

### Class Structure

**ONNXEmbeddingProvider.java**
- Implements `EmbeddingProvider` interface
- Uses ONNX Runtime Java API (`ai.onnxruntime.*`)
- Configured as Spring `@Bean` in `AIInfrastructureAutoConfiguration`
- Marked as `@Primary` and `@Order(HIGHEST_PRECEDENCE)` to be the default

### Key Components

1. **OrtEnvironment**: Singleton ONNX Runtime environment
2. **OrtSession**: Loaded ONNX model session
3. **Simple Tokenizer**: Character-based tokenization (currently implemented)
4. **Tensor Processing**: Handles int64 inputs, 3D outputs with mean pooling

---

## Data Flow

### Embedding Generation Process

```
1. Text Input
   ↓
2. Character-based Tokenization
   ├─ Convert text → character codes
   ├─ Truncate/pad to maxSequenceLength (512)
   └─ Create input_ids array (long[])
   ↓
3. Create ONNX Input Tensors
   ├─ input_ids: [1, sequence_length] (int64)
   ├─ attention_mask: [1, sequence_length] (int64)
   └─ token_type_ids: [1, sequence_length] (int64, all zeros)
   ↓
4. ONNX Runtime Inference
   ├─ Run session.run(inputs)
   └─ Get output tensor
   ↓
5. Extract Embedding Output
   ├─ Handle 3D array: [batch, sequence, embedding_dim]
   ├─ Apply mean pooling over sequence dimension
   └─ Result: [batch, embedding_dim] → [embedding_dim]
   ↓
6. Convert to List<Double>
   ├─ Extract first (and only) batch item
   └─ Convert float[] → List<Double>
   ↓
7. Return AIEmbeddingResponse
   └─ Contains 384-dimensional embedding vector
```

### Detailed Step-by-Step

#### Step 1: Tokenization (Current Implementation)

```java
private int[] tokenize(String text) {
    if (text == null || text.isEmpty()) {
        return new int[0];
    }
    // Simple character-to-int mapping
    return text.chars().toArray();
}
```

**Note**: Current implementation uses simple character-based tokenization. This works but is not optimal. For production, a proper tokenizer should be integrated (e.g., HuggingFace tokenizers library via REST API or native Java implementation).

#### Step 2: Input Tensor Creation

The model expects three inputs, all as `int64` tensors:

```java
long[] inputIdsLong = new long[sequenceLength];
long[] attentionMaskLong = new long[sequenceLength];
long[] tokenTypeIdsLong = new long[sequenceLength];

// Fill arrays
for (int i = 0; i < sequenceLength; i++) {
    inputIdsLong[i] = i < inputIds.length ? inputIds[i] : 0;  // 0 = padding
    attentionMaskLong[i] = i < inputIds.length ? 1L : 0L;     // 1 = real token, 0 = padding
    tokenTypeIdsLong[i] = 0L;                                  // Single sequence
}

// Create ONNX tensors using LongBuffer (int64)
LongBuffer inputIdsBuffer = LongBuffer.wrap(inputIdsLong);
OnnxTensor inputIdsTensor = OnnxTensor.createTensor(ortEnvironment, inputIdsBuffer, shape);
```

**Key Points**:
- Model requires `int64` (long) inputs, not `int32`
- Must use `LongBuffer` wrapper for tensor creation
- Shape is `[1, sequenceLength]` for single-batch inference

#### Step 3: ONNX Runtime Inference

```java
Map<String, OnnxTensor> inputs = new HashMap<>();
inputs.put("input_ids", inputIdsTensor);
inputs.put("attention_mask", attentionMaskTensor);
inputs.put("token_type_ids", tokenTypeIdsToken);

// Run inference
OrtSession.Result output = ortSession.run(inputs);

// Extract output (first output by default)
OnnxValue embeddingValue = output.get(0);
```

#### Step 4: Output Processing (Critical)

The model outputs a **3D tensor** with shape `[batch=1, sequence=512, embedding_dim=384]`. This represents embeddings for each token in the sequence. We need to apply **mean pooling** to get a single embedding vector for the entire text:

```java
// Output is 3D: [batch, sequence, embedding_dim]
if (value instanceof float[][][]) {
    float[][][] tensorValue3D = (float[][][]) value;
    
    int batchSize = tensorValue3D.length;      // 1
    int seqLength = tensorValue3D[0].length;  // 512
    int embeddingDim = tensorValue3D[0][0].length;  // 384
    
    // Mean pooling: average over sequence length
    embeddings = new float[batchSize][embeddingDim];
    for (int b = 0; b < batchSize; b++) {
        for (int e = 0; e < embeddingDim; e++) {
            float sum = 0.0f;
            for (int s = 0; s < seqLength; s++) {
                sum += tensorValue3D[b][s][e];
            }
            embeddings[b][e] = sum / seqLength;
        }
    }
}
```

**Why Mean Pooling?**
- Transformer models output embeddings for each token
- We need a single embedding vector for the entire text
- Mean pooling averages all token embeddings → single vector
- This matches how sentence-transformers models work

---

## Model Requirements

### Required Model Format

1. **Format**: ONNX (`.onnx` file)
2. **Inputs**:
   - `input_ids`: `int64` tensor, shape `[batch, sequence]`
   - `attention_mask`: `int64` tensor, shape `[batch, sequence]`
   - `token_type_ids`: `int64` tensor, shape `[batch, sequence]`

3. **Output**:
   - `output` or first output: `float32` tensor, shape `[batch, sequence, embedding_dim]`
   - Example: `[1, 512, 384]` for all-MiniLM-L6-v2

### Recommended Model

**all-MiniLM-L6-v2** (currently used):
- Embedding dimension: **384**
- Max sequence length: **512**
- Model size: ~80MB
- Performance: ~100ms per embedding (CPU)
- Quality: Good for most use cases

**Model File Locations**:
- ONNX starter: `providers/ai-infrastructure-onnx-starter/src/main/resources/models/embeddings/all-MiniLM-L6-v2.onnx`
- Bundled tokenizer: `providers/ai-infrastructure-onnx-starter/src/main/resources/models/embeddings/tokenizer.json`

---

## Configuration

### Application Configuration

```yaml
ai:
  enabled: true
  providers:
    # Embedding Provider Selection
    embedding-provider: onnx  # Options: onnx (default), rest, openai
    
    # ONNX Configuration
    onnx-model-path: classpath:/models/embeddings/all-MiniLM-L6-v2.onnx
    onnx-tokenizer-path: classpath:/models/embeddings/tokenizer.json
    onnx-max-sequence-length: 512
    onnx-use-gpu: false  # Set to true if GPU available (requires onnxruntime_gpu)
```

### Spring Boot Auto-Configuration

The ONNX provider is configured in `AIInfrastructureAutoConfiguration`:

```java
@Bean
@Primary
@Order(HIGHEST_PRECEDENCE)
public EmbeddingProvider onnxEmbeddingProvider(AIProviderConfig config) {
    log.info("Creating ONNX Embedding Provider (default)");
    return new ONNXEmbeddingProvider(config);
}
```

**Key Points**:
- `@Primary`: Ensures this is the default when multiple providers exist
- `@Order(HIGHEST_PRECEDENCE)`: Loads first, preventing conflicts
- Uses `AIProviderConfig` for property binding

---

## Key Design Decisions

### 1. Why Character-Based Tokenization?

**Current Approach**: Simple character-to-int mapping
```java
return text.chars().toArray();
```

**Reason**: 
- **Simplicity**: Works immediately without external dependencies
- **No API calls**: Fully local
- **No Python dependency**: Pure Java

**Limitations**:
- Not optimal (doesn't match model's training tokenization)
- May produce slightly different embeddings than proper tokenization
- Should be replaced with proper tokenizer for production

**Future Improvement**: Integrate HuggingFace tokenizers library or use REST API for tokenization

### 2. Why int64 Inputs?

**Decision**: Model expects `int64` (long), not `int32` (int)

**Implementation**:
```java
long[] inputIdsLong = new long[sequenceLength];
LongBuffer inputIdsBuffer = LongBuffer.wrap(inputIdsLong);
OnnxTensor.createTensor(ortEnvironment, inputIdsBuffer, shape);
```

**Reason**: ONNX model exported from sentence-transformers typically uses int64 for token IDs.

### 3. Why Mean Pooling?

**Decision**: Average token embeddings over sequence length

**Reason**: 
- Sentence-transformers models output per-token embeddings
- We need a single embedding vector per text
- Mean pooling is standard for sentence embeddings
- Matches how the model was trained/optimized

**Alternative Approaches** (not currently used):
- CLS token (if available)
- Max pooling
- Attention-weighted pooling

### 4. Why Support Multiple Output Formats?

**Decision**: Handle 2D, 3D, and 4D tensor outputs

**Implementation**: Cascading type checks:
```java
if (value instanceof float[][][]) {
    // Handle 3D: [batch, sequence, embedding_dim]
} else if (value instanceof OnnxTensor) {
    // Try 3D, 4D, or 2D extraction
} else if (value instanceof float[][]) {
    // Handle 2D: [batch, embedding_dim]
}
```

**Reason**: 
- Different model exports may have different output shapes
- 3D is most common for transformer outputs
- 4D may occur with some export formats
- 2D may occur with optimized/quantized models

### 5. Why EmbeddingProvider Abstraction?

**Decision**: Interface-based design for swappable providers

**Benefits**:
- Easy switching via configuration (`ai.providers.embedding-provider`)
- Testability: Can mock `EmbeddingProvider` in tests
- Extensibility: Add new providers without changing existing code
- Consistent API: All providers implement same interface

---

## Output Processing

### Current Implementation Flow

```
ONNX Model Output
    ↓
Type Detection
    ├─ float[][][] (3D) → Mean Pooling
    ├─ OnnxTensor → Extract and Process
    └─ float[][] (2D) → Direct Use
    ↓
Mean Pooling (for 3D)
    ├─ Average over sequence dimension
    └─ Result: [batch, embedding_dim]
    ↓
Extract First Batch Item
    └─ embeddings[0] → float[]
    ↓
Convert to List<Double>
    └─ Stream conversion
    ↓
AIEmbeddingResponse
    └─ 384-dimensional embedding vector
```

### Mean Pooling Implementation

```java
// Input: [batch=1, sequence=512, embedding_dim=384]
float[][][] tensorValue3D = (float[][][]) value;

int batchSize = 1;
int seqLength = 512;
int embeddingDim = 384;

float[][] embeddings = new float[batchSize][embeddingDim];

for (int b = 0; b < batchSize; b++) {
    for (int e = 0; e < embeddingDim; e++) {
        float sum = 0.0f;
        // Sum all token embeddings for this dimension
        for (int s = 0; s < seqLength; s++) {
            sum += tensorValue3D[b][s][e];
        }
        // Average
        embeddings[b][e] = sum / seqLength;
    }
}
```

**Result**: Single 384-dimensional embedding vector representing the entire text.

---

## Performance Characteristics

### Current Performance (CPU)

- **Single Embedding**: ~100-150ms
- **Throughput**: ~6-10 embeddings/second
- **Model Loading**: ~1-2 seconds (one-time)
- **Memory**: ~200-300MB (model in memory)

### Optimization Opportunities

1. **Batch Processing**: Process multiple texts at once (currently supports but not optimized)
2. **GPU Acceleration**: Enable with `onnx-use-gpu: true` (requires `onnxruntime_gpu` dependency)
3. **Tokenization**: Replace character-based with proper tokenizer (may improve quality)
4. **Model Optimization**: Use quantized INT8 model (smaller, faster)

### Expected Improvements

| Optimization | Speedup | Complexity |
|--------------|---------|------------|
| GPU Acceleration | 5-10x | Medium |
| Batch Processing (32 items) | 3-5x | Low |
| Quantized Model (INT8) | 2-3x | Medium |
| Proper Tokenization | N/A | Medium |

---

## Integration with Spring Boot

### Auto-Configuration

The ONNX provider is automatically configured when:
1. `ai.enabled=true` (default)
2. `ai.providers.embedding-provider=onnx` (default)
3. ONNX model file exists at configured path

### Bean Lifecycle

```java
@PostConstruct
public void initialize() {
    // Load model, initialize ONNX Runtime
    // Called after Spring dependency injection
}

@PreDestroy
public void cleanup() {
    // Close ONNX session
    // Called on application shutdown
}
```

### Dependency Injection

```java
@Service
public class SomeService {
    private final EmbeddingProvider embeddingProvider;  // Injected ONNX provider
    
    public SomeService(EmbeddingProvider embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }
}
```

### Provider Selection

If multiple providers are configured, ONNX is selected because:
- `@Primary` annotation
- `@Order(HIGHEST_PRECEDENCE)` 
- `matchIfMissing = true` in `@ConditionalOnProperty`

---

## Troubleshooting

### Common Issues

#### 1. Model File Not Found

**Error**: `ONNX model file not found at: ...`

**Solutions**:
- Verify model file exists at configured path
- Check path resolution (absolute vs relative)
- Run download script: `./scripts/download-onnx-model.sh`

#### 2. Unexpected Output Format

**Error**: `Unexpected embedding output format: [[[F`

**Cause**: Model outputs 3D array `float[][][]` instead of 2D

**Solution**: Already handled in current implementation with mean pooling

#### 3. Input Type Mismatch

**Error**: `Expected int64, got int32`

**Cause**: Using `int[]` instead of `long[]` for token IDs

**Solution**: Already fixed - uses `LongBuffer` for int64 inputs

#### 4. Provider Not Available

**Error**: `Embedding provider is not available`

**Solutions**:
- Check model file exists and is readable
- Verify ONNX Runtime dependency is included
- Check initialization logs for errors

### Debug Logging

Enable debug logging to see detailed flow:

```yaml
logging:
  level:
    com.ai.infrastructure.embedding: DEBUG
```

**Log Output**:
```
Successfully processed 3D array [batch=1, sequence=512, embedding=384] with mean pooling
Successfully generated ONNX embedding with 384 dimensions in 109ms
```

---

## Future Improvements

The roadmap above informs the improvement backlog. Track high-priority initiatives with the matrix below and align each delivery with the indicated phase.

| Focus | Phase | Status | Next Step | Reference |
|-------|-------|--------|-----------|-----------|
| Tokenization parity | Phase 1 | 🔴 Planned | Integrate Hugging Face tokenizer support and extend tests to validate canonical vocab output. | [`TOKENIZATION_IMPROVEMENT.md`](./TOKENIZATION_IMPROVEMENT.md) |
| Thread safety & true batching | Phase 1 | ⚠️ In analysis | Add concurrent integration tests and introduce session pooling or synchronization plus real batch tensor execution. | [Production Hardening Roadmap (Phase 1)](#production-hardening-roadmap) |
| Observability & resilience | Phase 2 | ⚠️ Upcoming | Wire Micrometer metrics, health indicators, rate limiting, and retry policies around the provider. | [Production Hardening Roadmap (Phase 2)](#production-hardening-roadmap) |
| Packaging migration to starter | Phase 2 | 🚧 In progress | Create the `ai-infrastructure-onnx-starter` module, move assets, and update integration tests/scripts. | [`ONNX_OPTIONAL_STARTER_PLAN.md`](../../docs/ONNX_OPTIONAL_STARTER_PLAN.md) |
| Performance extensions (GPU, quantization, caching, normalization) | Phase 3 | 🟡 Backlog | Profile current latency, add GPU + INT8 execution paths, support lazy model loading, and apply L2 normalization utilities. | [Production Hardening Roadmap (Phase 3)](#production-hardening-roadmap) |

---

## Summary

The current ONNX implementation:

✅ **Works**: Successfully generates 384-dimensional embeddings  
✅ **Local**: No API calls, fully offline  
✅ **Integrated**: Seamlessly integrated with Spring Boot  
✅ **Configurable**: Easy switching via configuration  
✅ **Extensible**: Provider abstraction allows easy addition of new providers  

**Key Strengths**:
- Pure Java implementation
- No external API dependencies
- Production-ready architecture
- Flexible configuration

**Areas for Improvement**:
- **Phase 1**: Deliver tokenizer parity, thread-safety validation, and true batch execution.
- **Phase 2**: Ship metrics, health checks, rate limiting, and complete the optional starter packaging migration.
- **Phase 3**: Layer in GPU/INT8 execution, lazy model management, and default L2 normalization utilities.

---

## Related Documentation

- [ONNX Runtime Embeddings Guide](./ONNX_RUNTIME_EMBEDDINGS_GUIDE.md) - General ONNX concepts
- [Embedding Provider Configuration](./EMBEDDING_PROVIDER_CONFIGURATION.md) - Configuration details
- [User Guide](./USER_GUIDE.md) - General usage
- [Model README](../providers/ai-infrastructure-onnx-starter/src/main/resources/models/embeddings/README.md) - Model download instructions

