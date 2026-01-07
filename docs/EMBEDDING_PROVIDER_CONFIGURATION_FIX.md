# Embedding Provider Configuration Fix

## Issue Description

When running integration tests via GitHub Actions with a specific embedding provider (e.g., OpenAI), the tests were incorrectly using ONNX as the embedding provider instead of the configured provider.

**Reproduction:**
- Trigger workflow: `.github/workflows/integration-tests-manual.yml`
- Select `openai` as both LLM provider and embedding provider
- Observe that tests actually run with `openai:onnx` instead of `openai:openai`

## Root Cause

The issue was caused by **Spring Boot's TestContext caching mechanism**:

1. Spring Boot caches `ApplicationContext` instances to improve test performance
2. The cache key is based on:
   - `@SpringBootTest` configuration
   - `@ActiveProfiles` 
   - `@TestPropertySource` properties
   - Other static test configuration

3. **The cache key does NOT include dynamically-set system properties**

4. When the provider matrix test runner:
   - Sets `EMBEDDING_PROVIDER=openai` via `System.setProperty()`
   - Runs the first test class
   - Spring creates and caches an ApplicationContext

5. For subsequent provider combinations:
   - The matrix runner sets different provider properties
   - But Spring reuses the cached context from step 4
   - The new provider configuration is ignored!

## Technical Details

### Configuration Flow

1. **Workflow → Script:** 
   ```bash
   run-provider-matrix-tests.sh "openai:openai:lucene:SINGLE_TABLE"
   ```

2. **Script → Maven:**
   ```bash
   mvn test -Dai.providers.real-api.matrix='openai:openai:lucene:SINGLE_TABLE'
   ```

3. **AbstractProviderMatrixIntegrationTest:**
   ```java
   // Parses matrix spec and sets system properties
   System.setProperty("EMBEDDING_PROVIDER", "openai");
   System.setProperty("ai.providers.embedding-provider", "openai");
   ```

4. **application-real-api-test.yml:**
   ```yaml
   ai:
     providers:
       embedding-provider: ${EMBEDDING_PROVIDER:onnx}  # Should read "openai"
   ```

5. **Spring Boot reads configuration at context creation time**
   - First test: Creates context with properties, caches it
   - Next test: Reuses cached context (ignores new properties!)

### Why Static Blocks Weren't Helping

Each test class had a static initializer like this:

```java
static {
    System.setProperty("EMBEDDING_PROVIDER",
        System.getProperty("EMBEDDING_PROVIDER", "onnx"));
}
```

This code says: "If EMBEDDING_PROVIDER is already set, keep it; otherwise use 'onnx'."

However, this runs **after** Spring has already created and cached the ApplicationContext for the first test, so changing the property doesn't trigger a new context creation.

## Solution

Added `@DirtiesContext(classMode = ClassMode.AFTER_CLASS)` annotation to all Real API test classes.

This annotation tells Spring to:
- Close the ApplicationContext after the test class completes
- Remove it from the cache
- Force creation of a fresh context for the next test with updated properties

### Files Modified

All Real API integration test classes in:
```
ai-infrastructure-module/integration-Testing/integration-tests/src/test/java/com/ai/infrastructure/it/
```

Modified files:
- `RealAPIIntegrationTest.java`
- `RealAPIONNXFallbackIntegrationTest.java`
- `RealAPISmartValidationIntegrationTest.java`
- `RealAPIVectorLifecycleIntegrationTest.java`
- `RealAPIHybridRetrievalToggleIntegrationTest.java`
- `RealAPIIntentHistoryAggregationIntegrationTest.java`
- `RealAPIActionErrorRecoveryIntegrationTest.java`
- `RealAPIActionFlowIntegrationTest.java`
- `RealAPIMultiProviderFailoverIntegrationTest.java`
- `RealAPISmartSuggestionsIntegrationTest.java`
- `RealAPIIntentGenerationRoutingIntegrationTest.java`
- `RealAPIPIIEdgeSpectrumIntegrationTest.java`
- `RealAPICreativeAIScenariosIntegrationTest.java`

### Example Change

```java
// Before:
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@Transactional
public class RealAPIIntegrationTest {
    // ...
}

// After:
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("real-api-test")
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class RealAPIIntegrationTest {
    // ...
}
```

## Trade-offs

### Benefits:
✅ Correctly honors provider configuration for each test run
✅ Prevents cache poisoning between provider combinations
✅ Uses Spring's official mechanism for cache management
✅ Simple, declarative solution

### Costs:
⚠️ Slightly slower test execution (context recreation overhead)
⚠️ Higher memory usage during test execution

However, these costs are acceptable because:
- Real API tests are already slow due to network calls
- Correctness is more important than speed
- Tests are run infrequently (manual trigger or PR validation)

## Alternative Solutions Considered

1. **Reflection-based cache clearing:** Too fragile, depends on Spring internals
2. **Dynamic @TestPropertySource:** Can't use dynamic values in annotations
3. **Separate test profiles per provider:** Would require many configuration files
4. **JVM forking per test:** Too slow and resource-intensive

## Verification

To verify the fix works:

1. Trigger the integration test workflow with `embedding_provider: openai`
2. Check test logs for provider configuration
3. Confirm tests use OpenAI for embeddings (not ONNX)

Look for log messages like:
```
[1/1] Running tests for: LLM=openai | Embedding=openai | VectorDB=lucene | Storage=SINGLE_TABLE
```

## References

- [Spring TestContext Framework Documentation](https://docs.spring.io/spring-framework/docs/current/reference/html/testing.html#testcontext-ctx-management)
- [@DirtiesContext Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/annotation/DirtiesContext.html)
- [GitHub Actions Workflow](.github/workflows/integration-tests-manual.yml)
