# Provider Matrix Integration Tests - Quick Reference

## TL;DR

Run integration tests with different provider combinations:

```bash
# Basic: OpenAI LLM + ONNX embeddings (default)
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=openai:onnx

# Multiple combinations (all run in sequence)
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix="openai:onnx,anthropic:openai,azure:azure"

# Auto-discover (tests all available combinations)
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test
```

## Command Structure

```bash
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=COMBINATIONS
```

### Parameters

| Parameter | Value | Example | Required |
|-----------|-------|---------|----------|
| `-pl` | `integration-tests` | Module path | Yes |
| `-am` | (flag) | Aggregate Maven | Yes |
| `-Dtest=` | `RealAPIProviderMatrixIntegrationTest` | Test class | Yes |
| `-Dspring.profiles.active=` | `real-api-test` | Spring profile | Yes |
| `-Dai.providers.real-api.matrix=` | Combinations | `openai:onnx` | Optional* |

*If omitted, auto-discovers all available combinations

## Matrix Specification Syntax

### Single Combination (Basic)
```
llm:embedding
```

Examples:
- `openai:onnx` - OpenAI LLM with local ONNX embeddings
- `anthropic:openai` - Claude with OpenAI embeddings
- `azure:azure` - Azure for both
- `openai:openai` - OpenAI for both

### Multiple Combinations
```
llm1:embedding1,llm2:embedding2,llm3:embedding3
```

Example:
```bash
-Dai.providers.real-api.matrix="openai:onnx,openai:openai,anthropic:openai,azure:azure"
```

### Extended Syntax (With Vector Database)
```
llm:embedding:vectordb
```

Examples:
- `openai:onnx:memory` - OpenAI LLM, ONNX embeddings, in-memory vector DB
- `openai:onnx:pinecone` - OpenAI LLM, ONNX embeddings, Pinecone vector DB (future)
- `azure:azure:qdrant` - Azure both, Qdrant vector DB (future)

## Available Providers

### LLM Providers
| Provider | Status | Notes |
|----------|--------|-------|
| `openai` | ✅ Ready | gpt-4o-mini |
| `anthropic` | ✅ Ready | Claude 3.5 Sonnet |
| `azure` | ✅ Ready | Azure OpenAI |
| `cohere` | ⏳ Planned | Command model |

### Embedding Providers
| Provider | Status | Notes |
|----------|--------|-------|
| `onnx` | ✅ Ready | Local (all-MiniLM-L6-v2) |
| `openai` | ✅ Ready | text-embedding-3-small |
| `azure` | ✅ Ready | text-embedding-ada-002 |
| `rest` | ✅ Ready | Custom REST endpoint |
| `cohere` | ⏳ Planned | Embed model |

### Vector Databases
| Provider | Status | Notes |
|----------|--------|-------|
| `memory` | ✅ Ready | In-memory (test/dev) |
| `lucene` | ✅ Ready | Embedded search |
| `pinecone` | ⏳ Planned | Serverless vector DB |
| `qdrant` | ✅ Ready | Open-source vector DB |
| `weaviate` | ✅ Ready | Vector database |
| `milvus` | ✅ Ready | Vector DB platform |

## Test Output

Each combination generates output like:

```
═══════════════════════════════════════════════════════════════
Provider Matrix Integration Tests - Starting
Total combinations to test: 2
─────────────────────────────────────────────────────────────
  • LLM=openai | Embedding=onnx
  • LLM=anthropic | Embedding=openai
═══════════════════════════════════════════════════════════════

[1/2] Running tests for: LLM=openai | Embedding=onnx
✓ [1] PASSED: LLM=openai | Embedding=onnx

[2/2] Running tests for: LLM=anthropic | Embedding=openai
✓ [2] PASSED: LLM=anthropic | Embedding=openai
```

## Real-World Examples

### Example 1: Cost Optimization
Test the most cost-effective configuration:

```bash
export OPENAI_API_KEY="sk-..."

mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=openai:onnx
```

**Cost**: ~$0.10-0.15 for the full test suite
- OpenAI API calls: ~$0.10
- ONNX: Free (local)

### Example 2: Enterprise Azure Deployment
Test your Azure infrastructure:

```bash
export AZURE_OPENAI_API_KEY="..."
export AZURE_OPENAI_ENDPOINT="https://your-resource.openai.azure.com"
export AZURE_DEPLOYMENT_NAME="gpt-4"
export AZURE_EMBEDDING_DEPLOYMENT_NAME="text-embedding-ada-002"

mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=azure:azure
```

### Example 3: Multi-Provider Testing Matrix
Compare multiple configurations:

```bash
export OPENAI_API_KEY="sk-..."
export ANTHROPIC_API_KEY="sk-ant-..."
export AZURE_OPENAI_API_KEY="..."
export AZURE_OPENAI_ENDPOINT="https://..."

mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix="openai:onnx,openai:openai,anthropic:openai,azure:azure"
```

**Time**: ~3-4 minutes (4 × ~45-60s per combination)
**Cost**: ~$0.40-0.60 total

### Example 4: Using Shell Script
Simpler syntax with provided script:

```bash
cd /workspace/ai-infrastructure-module/integration-tests

# Run with default
./run-provider-matrix-tests.sh

# Run specific combination
./run-provider-matrix-tests.sh "openai:onnx"

# Run multiple combinations
./run-provider-matrix-tests.sh "openai:onnx,anthropic:openai"

# With vector database
./run-provider-matrix-tests.sh "openai:onnx" "memory"
```

## Environment Variables

### Required
```bash
export OPENAI_API_KEY="sk-..."
```

### Optional (Anthropic)
```bash
export ANTHROPIC_API_KEY="sk-ant-..."
```

### Optional (Azure)
```bash
export AZURE_OPENAI_API_KEY="..."
export AZURE_OPENAI_ENDPOINT="https://your-resource.openai.azure.com"
export AZURE_DEPLOYMENT_NAME="gpt-4"
export AZURE_EMBEDDING_DEPLOYMENT_NAME="text-embedding-ada-002"
```

## Test Classes Included

All these tests run for each provider combination:

1. `RealAPIIntegrationTest` - Core API functionality
2. `RealAPIONNXFallbackIntegrationTest` - ONNX fallback behavior
3. `RealAPISmartValidationIntegrationTest` - Input validation
4. `RealAPIVectorLifecycleIntegrationTest` - Vector operations
5. `RealAPIHybridRetrievalToggleIntegrationTest` - Hybrid search toggle
6. `RealAPIIntentHistoryAggregationIntegrationTest` - Intent tracking
7. `RealAPIActionErrorRecoveryIntegrationTest` - Error handling
8. `RealAPIActionFlowIntegrationTest` - Workflow execution
9. `RealAPIMultiProviderFailoverIntegrationTest` - Provider fallback
10. `RealAPISmartSuggestionsIntegrationTest` - Recommendations
11. `RealAPIPIIEdgeSpectrumIntegrationTest` - PII detection

**Total**: 11 test classes × N combinations

## Performance Notes

| Configuration | Duration | Notes |
|---------------|----------|-------|
| Single (openai:onnx) | 45-60s | ONNX is fast locally |
| Single (openai:openai) | 50-65s | Extra API call for embeddings |
| Two combinations | 2-2.5 min | Sequential execution |
| Four combinations | 3.5-4.5 min | Full matrix |

## Troubleshooting

### Error: "No LLM provider available"
```
Error initializing AIProviderManager: No LLM provider available
```
**Fix**: Ensure required API keys are set and provider is enabled in configuration

### Error: "Requested provider combinations are not available"
```
Requested provider combinations are not available. Missing: [openai/invalid]
Available combinations: [openai/onnx, anthropic/openai, ...]
```
**Fix**: Check the available combinations and use valid names

### Tests timeout
- Increase timeout: Use `-X` flag for Maven debug output
- Check API rate limits
- Check network connectivity

### "API Key is not configured"
```
OpenAI API key not configured. Provider will be unavailable.
```
**Fix**: Set environment variable:
```bash
export OPENAI_API_KEY="sk-..."
```

## CI/CD Integration

### GitHub Actions
```yaml
jobs:
  matrix-tests:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        providers: ['openai:onnx', 'anthropic:openai', 'azure:azure']
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-java@v3
        with:
          java-version: '21'
      - run: |
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

## Quick Commands

```bash
# Cost-optimized (fastest, cheapest)
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=openai:onnx

# Enterprise Azure
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=azure:azure

# Best quality LLM
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test \
  -Dai.providers.real-api.matrix=anthropic:openai

# All available combinations
mvn -pl integration-tests -am test \
  -Dtest=RealAPIProviderMatrixIntegrationTest \
  -Dspring.profiles.active=real-api-test
```

## Next Steps

1. **Set up API credentials** (see Environment Variables above)
2. **Run a test combination** (pick one from examples)
3. **Monitor test output** for pass/fail status
4. **Compare results** across different provider combinations
5. **Select best configuration** for your use case

For complete documentation, see: `DYNAMIC_PROVIDER_MATRIX_GUIDE.md`
