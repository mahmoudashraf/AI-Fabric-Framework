# Embedding Provider Configuration Guide

## Overview

The AI Infrastructure module provides a swappable `EmbeddingProvider` abstraction, similar to `VectorDatabaseService`, allowing you to switch between different embedding generation methods:

- **ONNX Runtime** (default): Local, no API calls, free
- **REST API**: Docker/sentence-transformers container
- **OpenAI API**: Cloud service (fallback)

## Architecture

```
AIEmbeddingService
    ↓
EmbeddingProvider (interface)
    ↓
┌─────────────────────────────────────────┐
│  Implementation (selected via config)  │
├─────────────────────────────────────────┤
│  • ONNXEmbeddingProvider               │
│    (Default - local, no API calls)     │
│                                         │
│  • RestEmbeddingProvider               │
│    (Docker/REST API - flexible)        │
│                                         │
│  • OpenAIEmbeddingProvider             │
│    (Fallback - cloud API)              │
└─────────────────────────────────────────┘
```

## Configuration

### ONNX Embedding Provider (Default)

Local embedding generation using ONNX Runtime. No external API calls required.

**Configuration** (`application.yml`):

```yaml
ai:
  providers:
    embedding-provider: onnx  # Default, can be omitted
    onnx-model-path: ./models/embeddings/all-MiniLM-L6-v2.onnx
    onnx-tokenizer-path: ./models/embeddings/tokenizer.json
    onnx-max-sequence-length: 512
    onnx-use-gpu: false
```

**Model Download**:

Use the provided script to download ONNX models:

```bash
./scripts/download-onnx-model.sh
```

Or manually:

```bash
# Create models directory
mkdir -p ./models/embeddings

# Download model
wget https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/model.onnx \
  -O ./models/embeddings/all-MiniLM-L6-v2.onnx

# Download tokenizer
wget https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json \
  -O ./models/embeddings/tokenizer.json
```

**Supported Models**:

- `all-MiniLM-L6-v2` (default): 384 dimensions, ~80MB, fast
- `all-mpnet-base-v2`: 768 dimensions, ~420MB, better quality
- `all-MiniLM-L12-v2`: 384 dimensions, ~120MB, better quality than L6

**Features**:

- ✅ Local inference (no API calls)
- ✅ Free to use
- ✅ Fast (20-50ms per embedding)
- ✅ Works offline
- ✅ Privacy-friendly (data stays local)
- ❌ Requires model files (~80MB+)
- ❌ Requires proper tokenizer for best results

### REST Embedding Provider

REST API-based embedding generation. Supports Docker containers running sentence-transformers-serve or similar services.

**Configuration** (`application.yml`):

```yaml
ai:
  providers:
    embedding-provider: rest
    rest-base-url: http://localhost:8000
    rest-endpoint: /embed
    rest-batch-endpoint: /embed/batch
    rest-timeout: 30000
    rest-model: all-MiniLM-L6-v2
```

**Docker Setup**:

Run a sentence-transformers service container:

```bash
docker run -d -p 8000:8000 \
  --name sentence-transformers \
  sentence-transformers-serve \
  --model all-MiniLM-L6-v2
```

Or use your own REST API endpoint that provides embedding services.

**REST API Contract**:

**Single Embedding** (`POST /embed`):

```json
{
  "text": "Your text here",
  "model": "all-MiniLM-L6-v2"
}
```

Response:

```json
{
  "embedding": [0.1, 0.2, 0.3, ...],
  "model": "all-MiniLM-L6-v2",
  "dimensions": 384
}
```

**Batch Embedding** (`POST /embed/batch`):

```json
{
  "texts": ["Text 1", "Text 2", "Text 3"],
  "model": "all-MiniLM-L6-v2"
}
```

Response:

```json
{
  "embeddings": [
    [0.1, 0.2, 0.3, ...],
    [0.2, 0.3, 0.4, ...],
    [0.3, 0.4, 0.5, ...]
  ],
  "model": "all-MiniLM-L6-v2"
}
```

**Features**:

- ✅ Flexible (use any REST API)
- ✅ Docker-friendly
- ✅ Can use GPU-accelerated services
- ✅ Easy to scale
- ❌ Requires external service
- ❌ Network latency

### OpenAI Embedding Provider

Cloud-based embedding generation using OpenAI's API.

**Configuration** (`application.yml`):

```yaml
ai:
  providers:
    embedding-provider: openai
    openai-api-key: ${OPENAI_API_KEY}
    openai-embedding-model: text-embedding-3-small
    openai-timeout: 60
```

**Features**:

- ✅ High quality embeddings
- ✅ Large vocabulary
- ✅ Multi-language support
- ✅ Reliable and maintained
- ❌ Requires API key
- ❌ API costs per request
- ❌ Network latency
- ❌ Data sent to OpenAI

## Provider Selection

The provider is selected based on the `ai.providers.embedding-provider` configuration property:

- `onnx` (default): ONNX Runtime provider
- `rest`: REST API provider
- `openai`: OpenAI API provider

If not specified, `onnx` is used by default.

## Fallback Strategy

Currently, each provider must be explicitly configured. If a provider is not available:

- **ONNX**: Will log a warning and be unavailable until model files are provided
- **REST**: Will log a warning and retry connection on next request
- **OpenAI**: Will fail if API key is not configured

**Future Enhancement**: Implement automatic fallback chain (ONNX → REST → OpenAI)

## Usage Examples

### Code Usage

```java
@Autowired
private AIEmbeddingService embeddingService;

// Generate single embedding
AIEmbeddingRequest request = AIEmbeddingRequest.builder()
    .text("Your text here")
    .model("onnx")  // Optional, provider uses configured model
    .build();

AIEmbeddingResponse response = embeddingService.generateEmbedding(request);
List<Double> embedding = response.getEmbedding();

// Generate batch embeddings
List<String> texts = Arrays.asList("Text 1", "Text 2", "Text 3");
List<AIEmbeddingResponse> responses = embeddingService.generateEmbeddings(texts, "entityType");
```

### Provider Status

Check provider availability:

```java
@Autowired
private EmbeddingProvider embeddingProvider;

boolean available = embeddingProvider.isAvailable();
Map<String, Object> status = embeddingProvider.getStatus();
```

## Performance Comparison

| Provider | Latency | Cost | Quality | Privacy |
|----------|---------|------|---------|---------|
| ONNX     | 20-50ms | Free | Good    | Excellent (local) |
| REST     | 50-200ms| Free* | Good    | Good (local server) |
| OpenAI   | 100-500ms | $0.0001/1K tokens | Excellent | Fair (cloud) |

*Assuming self-hosted REST service

## Best Practices

1. **Development/Testing**: Use ONNX (local, fast, free)
2. **Production (Local)**: Use ONNX or REST (privacy, control)
3. **Production (Cloud)**: Use OpenAI (high quality, managed service)
4. **Hybrid**: Use ONNX for common embeddings, OpenAI for specialized use cases

## Troubleshooting

### ONNX Provider Not Available

- Check model file path in configuration
- Verify model file exists and is readable
- Check logs for initialization errors
- Download model files using the provided script

### REST Provider Not Available

- Verify REST service is running
- Check `rest-base-url` configuration
- Test REST endpoint manually:
  ```bash
  curl -X POST http://localhost:8000/embed \
    -H "Content-Type: application/json" \
    -d '{"text": "test", "model": "all-MiniLM-L6-v2"}'
  ```

### OpenAI Provider Not Available

- Verify API key is configured
- Check API key validity
- Verify network connectivity to OpenAI API
- Check rate limits and quotas

## Migration Guide

### Migrating from Direct OpenAI Calls

**Before**:

```java
// Direct OpenAI calls in AIEmbeddingService
EmbeddingResult result = openAiService.createEmbeddings(request);
```

**After**:

```java
// Provider abstraction
AIEmbeddingResponse response = embeddingProvider.generateEmbedding(request);
```

**Configuration Change**:

```yaml
# Before: No embedding provider config needed (implicit OpenAI)

# After: Explicit provider selection
ai:
  providers:
    embedding-provider: onnx  # or rest, or openai
```

## Next Steps

1. Download ONNX model files using the provided script
2. Configure `application.yml` with your preferred provider
3. Test embedding generation
4. Monitor provider status and performance

## References

- [ONNX Runtime Embeddings Guide](./ONNX_RUNTIME_EMBEDDINGS_GUIDE.md)
- [Local Embedding Options](./LOCAL_EMBEDDING_OPTIONS.md)
- [Vector Database Abstraction](./VECTOR_DATABASE_ABSTRACTION.md)


