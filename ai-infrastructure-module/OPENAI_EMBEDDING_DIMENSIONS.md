# OpenAI Embedding Models - Dimension Support for Lucene

## Problem
Lucene vector database has a maximum dimension limit of **1024**, but:
- `text-embedding-3-small` produces **1536 dimensions** (default)
- `text-embedding-ada-002` produces **1536 dimensions**

## OpenAI Models Supporting <1024 Dimensions

### Option 1: Use `text-embedding-3-small` with Dimension Reduction
OpenAI's `text-embedding-3-small` model supports dimension reduction via the `dimensions` parameter:
- **Default**: 1536 dimensions
- **Reducible to**: 512, 768, or 1024 dimensions (minimum: 512)

**Note**: The `theokanning.openai-gpt3-java` library (version 0.18.2) may need to be checked for support of the `dimensions` parameter in `EmbeddingRequest.builder()`.

### Option 2: Use `text-embedding-3-large` with Dimension Reduction
OpenAI's `text-embedding-3-large` model also supports dimension reduction:
- **Default**: 3072 dimensions
- **Reducible to**: Any value between 256 and 3072 (including 512, 768, 1024)

### Option 3: Use ONNX Provider (Recommended for Lucene)
The ONNX provider uses `all-MiniLM-L6-v2` which produces **384 dimensions**:
- ✅ Works perfectly with Lucene's 1024 limit
- ✅ No API costs
- ✅ Faster (local processing)
- ✅ Privacy-friendly (no data sent to external APIs)

## Recommended Solution

For the test with Lucene vector database, use the **ONNX provider** which is already configured as the default:

```yaml
ai:
  providers:
    embedding-provider: onnx  # Already default in test config
```

Or if you must use OpenAI, configure `text-embedding-3-small` with dimension reduction to 512 or 768 dimensions (requires library support check).

## Current Test Configuration

The test is currently configured to use:
- **Embedding Provider**: `onnx` (default in `RealAPIVectorLifecycleIntegrationTest`)
- **ONNX Model**: `all-MiniLM-L6-v2` (384 dimensions)
- **Vector DB**: Lucene (1024 max dimensions)

**IMPORTANT**: The test command you ran was overriding the embedding provider to `openai`, which caused the dimension mismatch. The test's default configuration (ONNX) should work perfectly!

## Solution: Use ONNX Provider (Already Default)

The test already defaults to ONNX provider. To run the test successfully with Lucene, simply **remove** the `-Dai.providers.embedding-provider=openai` parameter from your command:

```bash
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  # Remove this line: "-Dai.providers.embedding-provider=openai" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE" \
  "-DforkCount=1" \
  "-DreuseForks=false" \
  "-Dlogging.level.root=WARN"
```

This will use ONNX (384 dimensions) which is compatible with Lucene's 1024 limit.

## OpenAI Model Dimension Reference

| Model | Default Dimensions | Reducible To | Min Dimensions |
|-------|-------------------|--------------|---------------|
| text-embedding-ada-002 | 1536 | N/A (fixed) | 1536 |
| text-embedding-3-small | 1536 | 512-1536 | 512 |
| text-embedding-3-large | 3072 | 256-3072 | 256 |

## Implementation Note

To use OpenAI with reduced dimensions, the `EmbeddingRequest.builder()` would need to support:
```java
EmbeddingRequest embeddingRequest = EmbeddingRequest.builder()
    .model("text-embedding-3-small")
    .input(List.of(request.getText()))
    .dimensions(512)  // Reduce to 512 dimensions
    .build();
```

Check if `com.theokanning.openai-gpt3-java:service:0.18.2` supports the `dimensions()` method in `EmbeddingRequest.builder()`.
