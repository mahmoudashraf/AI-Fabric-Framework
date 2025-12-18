# Dynamic Provider Matrix Integration Tests - Complete Guide

## Overview

This guide explains how to run integration tests dynamically with different provider combinations (LLM provider, embedding provider, and vector database provider).

**Current Available Providers:**
- **LLM Providers**: OpenAI, Azure, Anthropic, Cohere
- **Embedding Providers**: OpenAI, ONNX, Azure, REST, Cohere
- **Vector Databases**: Memory (default), Lucene, Pinecone (planned), Weaviate (planned), Qdrant, Milvus

## Quick Start

### Run All RealAPI Tests with OpenAI + ONNX

```bash
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=openai:onnx
```

### Run with Custom Combination

```bash
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=openai:openai
```

## Matrix Syntax

### Single Combination
```bash
-Dai.providers.real-api.matrix=llm-provider:embedding-provider
```

Examples:
```bash
-Dai.providers.real-api.matrix=openai:onnx
-Dai.providers.real-api.matrix=anthropic:openai
-Dai.providers.real-api.matrix=azure:azure
```

### Multiple Combinations
```bash
-Dai.providers.real-api.matrix=openai:onnx,anthropic:openai,azure:azure
```

This will run all RealAPI tests 3 times (once for each combination).

### Full Matrix (All Available)
If `-Dai.providers.real-api.matrix` is not specified:
```bash
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test
```

This discovers all available provider combinations and tests each one.

## Environment Variable Alternative

You can also use environment variables instead of Maven properties:

```bash
export AI_PROVIDERS_REAL_API_MATRIX="openai:onnx,anthropic:openai"

mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test
```

Priority order:
1. System property: `-Dai.providers.real-api.matrix`
2. Environment variable: `AI_PROVIDERS_REAL_API_MATRIX`
3. Auto-discovery (if neither is set)

## Practical Examples

### Example 1: Cost Optimization Testing
Test OpenAI LLM with free local ONNX embeddings:

```bash
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=openai:onnx
```

### Example 2: Enterprise Azure Testing
Test Azure for both LLM and embeddings:

```bash
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=azure:azure
```

### Example 3: Anthropic + OpenAI Embeddings
Test best-in-class: Claude for LLM, OpenAI for embeddings:

```bash
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=anthropic:openai
```

### Example 4: Multiple Provider Combinations
Run comprehensive testing across multiple configurations:

```bash
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix="openai:onnx,openai:openai,anthropic:openai,azure:azure"
```

This will run the entire RealAPI test suite 4 times, once for each combination.

### Example 5: Testing with Vector Database (Future)
When Pinecone/Weaviate/Qdrant are configured, you can test with different vector databases:

```bash
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix="openai:onnx" \
  -Dai.vector-db.type=pinecone
```

## Test Output

For each provider combination, you'll see output like:

```
[INFO] Tests run: 55, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 45.234 s
[INFO] LLM=openai | Embedding=onnx ..................................... SUCCESS
[INFO] 
[INFO] Tests run: 55, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 43.891 s
[INFO] LLM=anthropic | Embedding=openai .............................. SUCCESS
[INFO] 
[INFO] TOTAL: 2 combinations tested, 110 tests, 0 failures
```

## RealAPI Integration Tests Included

The following tests are run for each provider combination:

1. `RealAPIIntegrationTest` - Core API functionality
2. `RealAPIONNXFallbackIntegrationTest` - ONNX fallback behavior
3. `RealAPISmartValidationIntegrationTest` - Input validation
4. `RealAPIVectorLifecycleIntegrationTest` - Vector operations
5. `RealAPIHybridRetrievalToggleIntegrationTest` - Hybrid search
6. `RealAPIIntentHistoryAggregationIntegrationTest` - Intent tracking
7. `RealAPIActionErrorRecoveryIntegrationTest` - Error handling
8. `RealAPIActionFlowIntegrationTest` - Workflow execution
9. `RealAPIMultiProviderFailoverIntegrationTest` - Provider fallback
10. `RealAPISmartSuggestionsIntegrationTest` - Recommendations
11. `RealAPIPIIEdgeSpectrumIntegrationTest` - PII detection

**Total: 11 test classes × number of combinations = total test runs**

## Prerequisites

### OpenAI
```bash
export OPENAI_API_KEY="sk-..."
```

### Azure
```bash
export AZURE_OPENAI_API_KEY="..."
export AZURE_OPENAI_ENDPOINT="https://your-resource.openai.azure.com"
```

### Anthropic (Optional)
```bash
export ANTHROPIC_API_KEY="sk-ant-..."
```

### Vector Databases (Optional)
```bash
# Pinecone
export PINECONE_API_KEY="..."
export PINECONE_ENVIRONMENT="..."

# Weaviate
export WEAVIATE_API_KEY="..."

# Qdrant
export QDRANT_API_KEY="..."

# Milvus
export MILVUS_PASSWORD="..."
```

## Configuration Details

### application-real-api-test.yml
The base configuration supports environment variable overrides:

```yaml
ai:
  providers:
    llm-provider: ${LLM_PROVIDER:openai}          # Default: openai
    embedding-provider: ${EMBEDDING_PROVIDER:onnx} # Default: onnx
    enable-fallback: true
    
    openai:
      enabled: true
      api-key: ${OPENAI_API_KEY}
      model: gpt-4o-mini
      embedding-model: text-embedding-3-small
    
    azure:
      enabled: true
      api-key: ${AZURE_OPENAI_API_KEY}
      endpoint: ${AZURE_OPENAI_ENDPOINT}
    
    anthropic:
      enabled: true
      api-key: ${ANTHROPIC_API_KEY}
    
    onnx:
      enabled: true
      model-path: classpath:/models/embeddings/all-MiniLM-L6-v2.onnx
```

## Advanced: Creating New Provider Combinations

### Adding a New Provider Module

1. Create module: `ai-infrastructure-provider-<name>`
2. Implement `AIProvider` or `EmbeddingProvider` interface
3. Add Spring Boot auto-configuration
4. Update `application-real-api-test.yml` with configuration block
5. Re-run matrix tests with new combinations

### Selecting Specific Providers in Your Application

After running matrix tests, configure your application:

**Cost Optimization (Low Cost)**:
```yaml
ai:
  providers:
    llm-provider: openai
    embedding-provider: onnx  # Free local embeddings
```

**Enterprise (Azure)**:
```yaml
ai:
  providers:
    llm-provider: azure
    embedding-provider: azure
```

**Best Quality (Best of Both Worlds)**:
```yaml
ai:
  providers:
    llm-provider: anthropic
    embedding-provider: openai
```

## Troubleshooting

### "Provider X not available" Error
- Check that required API keys are set in environment
- Verify provider is enabled in `application-real-api-test.yml`
- Check logs for provider initialization errors

### Matrix Not Found
```bash
Error: Requested provider combinations are not available
```
Solution: Check available providers:
```bash
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=invalid:combo
```
The error message will list available combinations.

### Tests Hanging
- Increase timeout: `-Dorg.slf4j.simpleLogger.defaultLogLevel=debug`
- Check provider API limits
- Review rate limiting in configuration

## CI/CD Integration

### GitHub Actions Example

```yaml
name: Test Provider Matrix

on: [push, pull_request]

jobs:
  matrix-tests:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        providers: ['openai:onnx', 'openai:openai', 'anthropic:openai', 'azure:azure']
    
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'
      
      - name: Run Tests
        run: |
          mvn -pl integration-tests -am test \
            -Dtest=RealAPIProviderMatrixIntegrationTest \
            -Dspring.profiles.active=real-api-test \
            -Dai.providers.real-api.matrix=${{ matrix.providers }}
        env:
          OPENAI_API_KEY: ${{ secrets.OPENAI_API_KEY }}
          ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}
          AZURE_OPENAI_API_KEY: ${{ secrets.AZURE_OPENAI_API_KEY }}
          AZURE_OPENAI_ENDPOINT: ${{ secrets.AZURE_OPENAI_ENDPOINT }}
```

## Performance Notes

- **Single combination**: ~45-60 seconds
- **2 combinations**: ~90-120 seconds
- **4 combinations**: ~180-240 seconds
- **Add 2-5 seconds per test** based on API latency

Running 4 combinations in parallel in CI will give best results.

## Next Steps

1. **Pinecone Integration**: Add vector database selection to matrix
2. **Vector DB Matrix**: Test combinations like `openai:onnx:pinecone`
3. **Failover Testing**: Matrix with fallback provider configurations
4. **Performance Benchmarking**: Compare cost/speed across combinations
5. **Cost Analysis**: Calculate cost per combination based on API usage

## Support

For issues or questions about provider matrix testing:
1. Check this guide's troubleshooting section
2. Review test output logs
3. Check provider documentation for specific API errors
4. Verify credentials and rate limits

---

**Last Updated**: 2025-11-14
**Status**: Production Ready
**Supported Combinations**: OpenAI/ONNX, OpenAI/OpenAI, Anthropic/OpenAI, Azure/Azure (more coming)
