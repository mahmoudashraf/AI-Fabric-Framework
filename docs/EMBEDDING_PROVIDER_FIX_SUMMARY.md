# Embedding Provider Configuration Fix - Summary

## Problem
When you selected **OpenAI** as the embedding provider in GitHub Actions, the tests were still running with **ONNX** as the embedding provider.

## Root Cause
**Spring Boot's ApplicationContext caching** was the culprit:
- Spring caches test contexts for performance
- The cache key is based on annotations like `@SpringBootTest`, `@ActiveProfiles`
- **Dynamic system properties (set via `System.setProperty()`) are NOT part of the cache key**
- When the provider matrix runner set `EMBEDDING_PROVIDER=openai`, Spring was still using a cached context that had `EMBEDDING_PROVIDER=onnx`

## Solution
Added `@DirtiesContext(classMode = ClassMode.AFTER_CLASS)` to all 13 Real API test classes.

This annotation forces Spring to:
✅ Close the ApplicationContext after each test class
✅ Remove it from the cache  
✅ Create a fresh context with updated properties for the next provider combination

## Files Modified

### Test Classes (13 files):
- RealAPIIntegrationTest.java
- RealAPIONNXFallbackIntegrationTest.java
- RealAPISmartValidationIntegrationTest.java
- RealAPIVectorLifecycleIntegrationTest.java
- RealAPIHybridRetrievalToggleIntegrationTest.java
- RealAPIIntentHistoryAggregationIntegrationTest.java
- RealAPIActionErrorRecoveryIntegrationTest.java
- RealAPIActionFlowIntegrationTest.java
- RealAPIMultiProviderFailoverIntegrationTest.java
- RealAPISmartSuggestionsIntegrationTest.java
- RealAPIIntentGenerationRoutingIntegrationTest.java
- RealAPIPIIEdgeSpectrumIntegrationTest.java
- RealAPICreativeAIScenariosIntegrationTest.java

### Framework Classes (1 file):
- AbstractProviderMatrixIntegrationTest.java (removed unnecessary reflection code)

## Example Change
```java
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)  // ← Added this
public class RealAPIIntegrationTest {
```

## Impact
- ✅ Tests will now correctly use the configured embedding provider
- ⚠️ Slightly slower execution (context recreation overhead)
- ⚠️ Higher memory usage during tests

## Next Steps
1. Push these changes to your branch
2. Trigger the GitHub Actions workflow again with `embedding_provider: openai`
3. Verify tests now correctly use OpenAI for embeddings

## Documentation
See `/workspace/docs/EMBEDDING_PROVIDER_CONFIGURATION_FIX.md` for detailed technical explanation.
