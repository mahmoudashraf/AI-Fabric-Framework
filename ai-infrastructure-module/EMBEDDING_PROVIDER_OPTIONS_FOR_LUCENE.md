# Embedding Provider Options for Lucene (≤1024 dimensions)

## Available Embedding Providers

### 1. ONNX Provider ✅ (384 dimensions)
- **Model**: `all-MiniLM-L6-v2`
- **Dimensions**: 384
- **Status**: ✅ Compatible with Lucene (1024 max)
- **Cost**: Free (local processing)
- **Speed**: Fast (10-50ms per embedding)

**Command:**
```bash
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=onnx" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE"
```

### 2. OpenAI Provider (with dimension reduction) ✅
- **Model**: `text-embedding-3-small`
- **Default Dimensions**: 1536 ❌ (too large for Lucene)
- **Reducible To**: 512, 768, or 1024 dimensions ✅
- **Status**: ✅ **NOW SUPPORTED** - Dimension reduction added to OpenAI provider
- **Cost**: Pay-per-use API calls

**Configuration**: Add `ai.providers.openai.embedding-dimensions` property to reduce dimensions.

**Command:**
```bash
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=openai" \
  "-Dai.providers.openai.embedding-model=text-embedding-3-small" \
  "-Dai.providers.openai.embedding-dimensions=512" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE"
```

**Note**: The implementation uses reflection to check if the OpenAI library supports dimension reduction. If not supported, it will fall back to default dimensions and log a warning.

### 3. Cohere Provider ❓
- **Models**: Various (check Cohere documentation)
- **Dimensions**: Varies by model
- **Status**: ❓ Need to check model dimensions
- **Cost**: Pay-per-use API calls

**Common Cohere Models:**
- `embed-english-v3.0`: 1024 dimensions ✅ (if supported)
- `embed-multilingual-v3.0`: 1024 dimensions ✅ (if supported)
- `embed-english-light-v3.0`: 384 dimensions ✅

**Command:**
```bash
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=cohere" \
  "-Dai.providers.cohere.api-key=YOUR_COHERE_API_KEY" \
  "-Dai.providers.cohere.embedding-model=embed-english-light-v3.0" \
  "-Dai.vector-db.type=lucene"
```

### 4. Azure OpenAI Provider ⚠️
- **Model**: Depends on deployment (typically `text-embedding-ada-002` or `text-embedding-3-small`)
- **Default Dimensions**: 1536 ❌ (if using ada-002 or text-embedding-3-small default)
- **Status**: ⚠️ May support dimension reduction if using text-embedding-3 models
- **Cost**: Pay-per-use API calls

**Command:**
```bash
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=azure" \
  "-Dai.providers.azure.api-key=YOUR_AZURE_KEY" \
  "-Dai.providers.azure.endpoint=YOUR_AZURE_ENDPOINT" \
  "-Dai.providers.azure.embedding-deployment-name=text-embedding-3-small" \
  "-Dai.vector-db.type=lucene"
```

**Note**: May need dimension reduction support similar to OpenAI provider.

### 5. REST Provider ✅ (Configurable)
- **Model**: Depends on external service
- **Dimensions**: Configurable (default: 384)
- **Status**: ✅ Compatible if service provides ≤1024 dimensions
- **Cost**: Depends on external service

**Command:**
```bash
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=openai" \
  "-Dai.vector-db.type=lucene"
```

## Recommended Testing Approach

### Option A: Test Cohere (if you have API key)
Cohere's `embed-english-light-v3.0` provides 384 dimensions, which is perfect for Lucene.

### Option B: Modify OpenAI Provider for Dimension Reduction
Add support for the `dimensions` parameter in `OpenAIEmbeddingProvider.java` to reduce `text-embedding-3-small` to 512 or 768 dimensions.

### Option C: Use Different Vector Database
If you need to test OpenAI with full 1536 dimensions, consider using a different vector database:
- Pinecone (supports up to 20,000 dimensions)
- Weaviate (supports up to 65,536 dimensions)
- Qdrant (supports up to 65,536 dimensions)
- Milvus (supports up to 32,768 dimensions)

## Quick Test Commands

### Test with ONNX (Default - Works):
```bash
cd ai-infrastructure-module/integration-Testing/integration-tests
export OPENAI_API_KEY="your-key"
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE"
```

### Test with Cohere (if available):
```bash
cd ai-infrastructure-module/integration-Testing/integration-tests
export OPENAI_API_KEY="your-key"
export COHERE_API_KEY="your-cohere-key"
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=cohere" \
  "-Dai.providers.cohere.embedding-model=embed-english-light-v3.0" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE"
```
