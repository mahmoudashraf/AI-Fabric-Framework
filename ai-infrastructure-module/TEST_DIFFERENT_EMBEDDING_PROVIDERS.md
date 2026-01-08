# Testing Different Embedding Providers with Lucene

This guide shows how to test different embedding providers (other than ONNX) with Lucene vector database.

## Prerequisites

- Lucene supports maximum **1024 dimensions**
- All embedding providers must produce ≤1024 dimensions

## Available Providers

### 1. ONNX Provider (Default) ✅
**Dimensions**: 384  
**Status**: Works out of the box

```bash
cd ai-infrastructure-module/integration-Testing/integration-tests
export OPENAI_API_KEY="your-key"
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=onnx" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE"
```

### 2. OpenAI Provider with Dimension Reduction ✅
**Dimensions**: 512, 768, or 1024 (configurable)  
**Status**: ✅ **NEW** - Dimension reduction support added

**Test with 512 dimensions:**
```bash
cd ai-infrastructure-module/integration-Testing/integration-tests
export OPENAI_API_KEY="your-key"
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=openai" \
  "-Dai.providers.openai.embedding-model=text-embedding-3-small" \
  "-Dai.providers.openai.embedding-dimensions=512" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE"
```

**Test with 768 dimensions:**
```bash
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=openai" \
  "-Dai.providers.openai.embedding-model=text-embedding-3-small" \
  "-Dai.providers.openai.embedding-dimensions=768" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE"
```

**Test with 1024 dimensions (maximum for Lucene):**
```bash
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=openai" \
  "-Dai.providers.openai.embedding-model=text-embedding-3-small" \
  "-Dai.providers.openai.embedding-dimensions=1024" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE"
```

### 3. Cohere Provider ❓
**Dimensions**: Varies by model  
**Status**: Need to verify model dimensions

**Common Cohere models:**
- `embed-english-light-v3.0`: 384 dimensions ✅
- `embed-english-v3.0`: 1024 dimensions ✅
- `embed-multilingual-v3.0`: 1024 dimensions ✅

**Test with Cohere (if you have API key):**
```bash
cd ai-infrastructure-module/integration-Testing/integration-tests
export OPENAI_API_KEY="your-key"
export COHERE_API_KEY="your-cohere-key"
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=cohere" \
  "-Dai.providers.cohere.api-key=${COHERE_API_KEY}" \
  "-Dai.providers.cohere.embedding-model=embed-english-light-v3.0" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE"
```

### 4. Azure OpenAI Provider ⚠️
**Dimensions**: Depends on deployment  
**Status**: May need dimension reduction support

**Test with Azure (if configured):**
```bash
cd ai-infrastructure-module/integration-Testing/integration-tests
export OPENAI_API_KEY="your-key"
export AZURE_OPENAI_API_KEY="your-azure-key"
export AZURE_OPENAI_ENDPOINT="your-endpoint"
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=azure" \
  "-Dai.providers.azure.api-key=${AZURE_OPENAI_API_KEY}" \
  "-Dai.providers.azure.endpoint=${AZURE_OPENAI_ENDPOINT}" \
  "-Dai.providers.azure.embedding-deployment-name=text-embedding-3-small" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE"
```

**Note**: Azure OpenAI may need similar dimension reduction support as OpenAI provider.

### 5. REST Provider ✅
**Dimensions**: Configurable (default: 384)  
**Status**: Works if external service provides ≤1024 dimensions

**Test with REST provider:**
```bash
cd ai-infrastructure-module/integration-Testing/integration-tests
export OPENAI_API_KEY="your-key"
export REST_EMBEDDING_BASE_URL="your-rest-service-url"
mvn test "-Dtest=RealAPIVectorLifecycleIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=rest" \
  "-Dai.providers.rest.base-url=${REST_EMBEDDING_BASE_URL}" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE"
```

## Quick Reference: Dimension Limits

| Provider | Model | Dimensions | Lucene Compatible |
|----------|-------|------------|-------------------|
| ONNX | all-MiniLM-L6-v2 | 384 | ✅ Yes |
| OpenAI | text-embedding-3-small (reduced) | 512-1024 | ✅ Yes (with config) |
| OpenAI | text-embedding-3-small (default) | 1536 | ❌ No |
| OpenAI | text-embedding-ada-002 | 1536 | ❌ No |
| Cohere | embed-english-light-v3.0 | 384 | ✅ Yes |
| Cohere | embed-english-v3.0 | 1024 | ✅ Yes |
| Cohere | embed-multilingual-v3.0 | 1024 | ✅ Yes |
| REST | Configurable | Variable | ✅ Yes (if ≤1024) |

## Configuration File Alternative

You can also configure providers in `application-real-api-test.yml`:

```yaml
ai:
  providers:
    embedding-provider: openai  # or cohere, azure, rest, onnx
    openai:
      embedding-model: text-embedding-3-small
      embedding-dimensions: 512  # Reduce to 512 for Lucene
```

## Troubleshooting

### Error: "vector's dimensions must be <= [1024]"
- **Cause**: Embedding provider is producing >1024 dimensions
- **Solution**: 
  - Use ONNX (384 dims)
  - Use OpenAI with dimension reduction (512-1024 dims)
  - Use Cohere with compatible model (≤1024 dims)
  - Switch to a different vector database (Pinecone, Weaviate, etc.)

### Error: "Dimension reduction not supported"
- **Cause**: OpenAI library version doesn't support `.dimensions()` method
- **Solution**: 
  - Use ONNX provider instead
  - Upgrade OpenAI library (if newer version supports it)
  - Use Cohere or REST provider

### Error: "Provider not available"
- **Cause**: Missing API keys or configuration
- **Solution**: 
  - Set required environment variables
  - Check provider configuration in application.yml
  - Verify API keys are valid

## Testing Matrix

Run tests with different providers to compare:

```bash
# Test 1: ONNX (baseline)
-Dai.providers.embedding-provider=onnx

# Test 2: OpenAI 512 dims
-Dai.providers.embedding-provider=openai -Dai.providers.openai.embedding-dimensions=512

# Test 3: OpenAI 768 dims  
-Dai.providers.embedding-provider=openai -Dai.providers.openai.embedding-dimensions=768

# Test 4: OpenAI 1024 dims
-Dai.providers.embedding-provider=openai -Dai.providers.openai.embedding-dimensions=1024

# Test 5: Cohere (if available)
-Dai.providers.embedding-provider=cohere -Dai.providers.cohere.embedding-model=embed-english-light-v3.0
```
