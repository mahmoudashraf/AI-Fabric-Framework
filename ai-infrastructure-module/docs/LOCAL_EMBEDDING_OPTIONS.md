# Local Embedding Generation Without OpenAI API Calls

## Overview

Currently, the system only uses OpenAI for embedding generation, which requires API calls, costs money, and has rate limits. This document describes options for generating embeddings locally without external API calls.

---

## Available Options

### 1. **Local Sentence Transformers (Recommended)**

**Technology**: Sentence Transformers models (runs locally)

**Pros**:
- ✅ No API calls required
- ✅ Free (no API costs)
- ✅ Fast (no network latency)
- ✅ Private (data stays local)
- ✅ Works offline

**Cons**:
- ⚠️ Requires model download (~500MB per model)
- ⚠️ Requires GPU for best performance (CPU works but slower)
- ⚠️ Different embedding dimensions than OpenAI

**Popular Models**:
- `all-MiniLM-L6-v2` (384 dimensions, 80MB)
- `all-mpnet-base-v2` (768 dimensions, 420MB)
- `all-MiniLM-L12-v2` (384 dimensions, 120MB)
- `multi-qa-MiniLM-L6-cos-v1` (384 dimensions, 80MB)

**Implementation Options**:

#### A. **Java Implementation (ONNX Runtime)**
- Use ONNX Runtime for Java
- Convert PyTorch models to ONNX format
- Runs natively in Java application
- No Python dependency

#### B. **Python Bridge (subprocess)**
- Run Python with sentence-transformers library
- Java calls Python script via subprocess
- Simpler setup but requires Python environment

#### C. **REST API (Docker Container)**
- Run sentence-transformers in a Docker container
- Expose REST API endpoint
- Java makes HTTP calls to local server
- Clean separation, easy to scale

---

### 2. **Hugging Face Inference API (Free Tier)**

**Technology**: Hugging Face Inference API

**Pros**:
- ✅ Free tier available
- ✅ Many models available
- ✅ No model download needed
- ✅ Easy to use

**Cons**:
- ⚠️ Still requires API calls (but free)
- ⚠️ Rate limits on free tier
- ⚠️ Requires internet connection

**Models**:
- `sentence-transformers/all-MiniLM-L6-v2`
- `sentence-transformers/all-mpnet-base-v2`

---

### 3. **ONNX Runtime (Local Models)**

**Technology**: ONNX Runtime for Java

**Pros**:
- ✅ Pure Java implementation
- ✅ No Python dependency
- ✅ Fast inference
- ✅ Cross-platform

**Cons**:
- ⚠️ Need to convert models to ONNX format
- ⚠️ Model files still need to be downloaded
- ⚠️ Setup complexity

---

### 4. **Pre-computed Embeddings**

**Technology**: Generate embeddings once, store in database

**Pros**:
- ✅ No API calls after initial generation
- ✅ Fast retrieval
- ✅ Consistent results

**Cons**:
- ⚠️ Still need initial generation (could use local model)
- ⚠️ Storage requirements
- ⚠️ Doesn't work for dynamic content

---

### 5. **More Aggressive Caching**

**Current**: Caching is already implemented

**Improvements**:
- Persistent cache (database/file-based)
- Pre-warming cache for common queries
- Cache embeddings for similar texts

---

## Recommended Implementation: Local Sentence Transformers

### Architecture

```
AIEmbeddingService (interface)
    ↓
EmbeddingProvider (interface)
    ↓
┌─────────────────────────────────────┐
│  Implementations                   │
├─────────────────────────────────────┤
│  • OpenAIEmbeddingProvider        │
│    (Current - requires API calls)  │
│                                     │
│  • LocalSentenceTransformerProvider│
│    (New - no API calls)            │
│                                     │
│  • HuggingFaceEmbeddingProvider    │
│    (Free API tier)                  │
│                                     │
│  • ONNXEmbeddingProvider           │
│    (Local ONNX models)              │
└─────────────────────────────────────┘
```

### Configuration

```yaml
ai:
  embedding:
    provider: local  # openai, local, huggingface, onnx
    local:
      model: all-MiniLM-L6-v2  # Sentence transformer model name
      model-path: ./models/embeddings
      use-gpu: false  # Use GPU if available
      batch-size: 32
    openai:
      model: text-embedding-3-small
      api-key: ${OPENAI_API_KEY}
    huggingface:
      api-key: ${HUGGINGFACE_API_KEY}  # Optional for free tier
      model: sentence-transformers/all-MiniLM-L6-v2
```

---

## Implementation Options Comparison

| Option | Setup Complexity | Performance | Cost | Privacy | Offline |
|--------|-----------------|-------------|------|---------|---------|
| **Local Sentence Transformers** | Medium | Fast (GPU) / Medium (CPU) | Free | ✅ Private | ✅ Yes |
| **ONNX Runtime** | High | Fast | Free | ✅ Private | ✅ Yes |
| **Hugging Face API** | Low | Fast | Free (tier) | ⚠️ API calls | ❌ No |
| **OpenAI API** | Low | Fast | $$$ | ⚠️ API calls | ❌ No |
| **Pre-computed** | Low | Very Fast | Free (after init) | ✅ Private | ✅ Yes |

---

## Quick Start: Local Sentence Transformers

### Option 1: Python Bridge (Easiest)

1. **Install Python dependencies**:
```bash
pip install sentence-transformers torch
```

2. **Create Python script** (`embed_local.py`):
```python
from sentence_transformers import SentenceTransformer
import sys
import json

model_name = sys.argv[1]
text = sys.argv[2]

model = SentenceTransformer(model_name)
embedding = model.encode(text).tolist()

print(json.dumps(embedding))
```

3. **Java calls Python**:
```java
Process process = Runtime.getRuntime().exec(
    new String[]{"python", "embed_local.py", "all-MiniLM-L6-v2", text}
);
// Parse output
```

### Option 2: Docker Container (Recommended for Production)

1. **Run sentence-transformers server**:
```bash
docker run -d -p 8000:8000 \
  --name sentence-transformers \
  sentence-transformers-serve \
  --model all-MiniLM-L6-v2
```

2. **Java calls local REST API**:
```java
POST http://localhost:8000/embed
Body: {"text": "your text"}
Response: {"embedding": [0.123, -0.456, ...]}
```

### Option 3: ONNX Runtime (Pure Java)

1. **Add dependency**:
```xml
<dependency>
    <groupId>com.microsoft.onnxruntime</groupId>
    <artifactId>onnxruntime</artifactId>
    <version>1.16.0</version>
</dependency>
```

2. **Download ONNX model** (convert from PyTorch)

3. **Load and run model**:
```java
OrtEnvironment env = OrtEnvironment.getEnvironment();
OrtSession session = env.createSession("model.onnx");
// Run inference
```

---

## Next Steps

1. **Create EmbeddingProvider interface** (similar to VectorDatabaseService)
2. **Implement LocalSentenceTransformerProvider** (Python bridge or Docker)
3. **Update AIEmbeddingService** to use provider abstraction
4. **Add configuration** for provider selection
5. **Fallback strategy**: Local → HuggingFace → OpenAI

---

## Model Comparison

| Model | Dimensions | Size | Speed (CPU) | Quality |
|-------|-----------|------|-------------|---------|
| `all-MiniLM-L6-v2` | 384 | 80MB | Very Fast | Good |
| `all-MiniLM-L12-v2` | 384 | 120MB | Fast | Better |
| `all-mpnet-base-v2` | 768 | 420MB | Medium | Best |
| `multi-qa-MiniLM-L6-cos-v1` | 384 | 80MB | Very Fast | Best for Q&A |

**Recommendation**: Start with `all-MiniLM-L6-v2` (good balance of speed/quality)

---

## Cost Comparison

### OpenAI API (Current)
- `text-embedding-3-small`: $0.02 per 1M tokens
- 1M documents ≈ $20-40 (depending on text length)

### Local Models
- **Initial**: Model download (~500MB bandwidth)
- **Ongoing**: $0 (runs on your hardware)
- **Scale**: Free regardless of volume

### Hugging Face (Free Tier)
- Free tier: 1,000 requests/day
- Paid: $0.10 per 1M tokens

---

## Performance

### OpenAI API
- Latency: 200-500ms (network round-trip)
- Throughput: Limited by API rate limits

### Local Sentence Transformers
- **CPU**: 50-200ms per embedding
- **GPU**: 5-20ms per embedding
- **Batch**: 10-100x faster (processes multiple texts together)

---

## Privacy & Security

### Local Models ✅
- Data never leaves your infrastructure
- No API logs
- Compliance friendly

### API-Based ⚠️
- Data sent to external service
- May be logged
- Privacy concerns for sensitive data

---

## Recommendation

**For Development**: Use local sentence transformers (Docker container)
- Fast setup
- No API costs
- Works offline

**For Production**: Hybrid approach
- Primary: Local models (for most embeddings)
- Fallback: OpenAI API (for complex/rare cases)
- Cache: Persistent cache for common embeddings

This gives you:
- ✅ Cost savings (99% of embeddings local)
- ✅ Reliability (works offline)
- ✅ Privacy (data stays local)
- ✅ Flexibility (can fall back to OpenAI when needed)


