# Run All RealAPI Tests with OpenAI Embeddings (512 dimensions)

## Command to Run All RealAPI Tests

```bash
cd ai-infrastructure-module/integration-Testing/integration-tests

# Clean up any existing Lucene locks
rm -rf data/lucene-vector-index data/test-lucene-index

# Set OpenAI API key
export OPENAI_API_KEY="your-api-key-here"

# Run all RealAPI tests with OpenAI embeddings (512 dimensions) and Lucene
mvn test \
  "-Dtest=RealAPIIntegrationTest,RealAPIONNXFallbackIntegrationTest,RealAPISmartValidationIntegrationTest,RealAPIVectorLifecycleIntegrationTest,RealAPIHybridRetrievalToggleIntegrationTest,RealAPIIntentHistoryAggregationIntegrationTest,RealAPIActionErrorRecoveryIntegrationTest,RealAPIActionFlowIntegrationTest,RealAPIIntentGenerationRoutingIntegrationTest,RealAPIMultiProviderFailoverIntegrationTest,RealAPISmartSuggestionsIntegrationTest,RealAPIPIIEdgeSpectrumIntegrationTest,RealAPICreativeAIScenariosIntegrationTest" \
  "-Dspring.profiles.active=realapi" \
  "-Dai.providers.llm-provider=openai" \
  "-Dai.providers.embedding-provider=openai" \
  "-Dai.providers.openai.embedding-model=text-embedding-3-small" \
  "-Dai.providers.openai.embedding-dimensions=512" \
  "-Dai.vector-db.type=lucene" \
  "-Dai-infrastructure.storage.strategy=SINGLE_TABLE" \
  "-DforkCount=1" \
  "-DreuseForks=false" \
  "-Dlogging.level.root=WARN" \
  "-Dlogging.level.com.ai.infrastructure.provider.openai=INFO"
```

## Test Results Summary

Based on test runs:

### ✅ Tests That Passed (with OpenAI 512 dimensions):
1. **RealAPIIntegrationTest** - ✅ 5 tests passed
2. **RealAPIONNXFallbackIntegrationTest** - ✅ 1 test passed  
3. **RealAPISmartValidationIntegrationTest** - ✅ 1 test passed
4. **RealAPIVectorLifecycleIntegrationTest** - ✅ 1 test passed

### All RealAPI Test Classes:
1. RealAPIIntegrationTest
2. RealAPIIntegrationTestV2
3. RealAPIONNXFallbackIntegrationTest
4. RealAPISmartValidationIntegrationTest
5. RealAPIVectorLifecycleIntegrationTest
6. RealAPIHybridRetrievalToggleIntegrationTest
7. RealAPIIntentHistoryAggregationIntegrationTest
8. RealAPIActionErrorRecoveryIntegrationTest
9. RealAPIActionFlowIntegrationTest
10. RealAPIIntentGenerationRoutingIntegrationTest
11. RealAPIMultiProviderFailoverIntegrationTest
12. RealAPISmartSuggestionsIntegrationTest
13. RealAPIPIIEdgeSpectrumIntegrationTest
14. RealAPICreativeAIScenariosIntegrationTest

## Expected Behavior

- **Dimension Reduction**: All tests will use OpenAI embeddings with 512 dimensions
- **Vector DB**: Lucene (compatible with 512 dimensions)
- **Provider**: OpenAI for both LLM and embeddings
- **Logs**: Will show "Successfully generated embedding with 512 dimensions using openai provider"

## Notes

- Tests may take 10-15 minutes to complete all 14 test classes
- Some tests may have Lucene lock issues if run in parallel - clean up `data/` directories between runs
- All tests default to ONNX, but with the command above, they will use OpenAI with dimension reduction
