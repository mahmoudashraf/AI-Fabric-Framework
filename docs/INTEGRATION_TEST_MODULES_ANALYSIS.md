# Integration Test Modules Analysis - Provider Configuration

## Summary

**Only the `integration-tests` module needs the `@DirtiesContext` fix.**

The other two integration test modules (`relationship-query-integration-tests` and `behavior-integration-tests`) use different approaches that don't suffer from the same Spring context caching issue.

---

## Module-by-Module Analysis

### 1. ✅ FIXED: `integration-tests` 

**Location:** `ai-infrastructure-module/integration-Testing/integration-tests`

**Issue:** ❌ Tests were running with ONNX instead of configured embedding provider

**Root Cause:**
- Uses `AbstractProviderMatrixIntegrationTest` framework
- Sets provider configuration via `System.setProperty()` dynamically
- Spring caches ApplicationContext based on annotations only
- Dynamic system properties are NOT part of cache key
- Result: Cached context from first run ignores new provider settings

**Solution Applied:** ✅ Added `@DirtiesContext(classMode = ClassMode.AFTER_CLASS)` to 13 test classes

**Files Modified:**
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

---

### 2. ✅ OK: `relationship-query-integration-tests`

**Location:** `ai-infrastructure-module/integration-Testing/relationship-query-integration-tests`

**Status:** ✅ No fix needed - Already working correctly

**Why It Works:**
- Uses **environment variables** (not system properties)
- Shell script sets: `export AI_INFRASTRUCTURE_EMBEDDING_PROVIDER="openai"`
- Environment variables are process-wide, set before JVM starts
- Spring reads from environment at context creation time
- Environment variables are stable across entire test run

**Configuration Flow:**
```bash
# Shell script
export AI_INFRASTRUCTURE_EMBEDDING_PROVIDER="openai"
mvn failsafe:integration-test
```

```yaml
# application-realapi.yml
ai:
  providers:
    embedding-provider: ${AI_INFRASTRUCTURE_EMBEDDING_PROVIDER:${EMBEDDING_PROVIDER:onnx}}
```

**Test Classes (3):**
- FinancialFraudRealApiIntegrationTest.java
- ECommerceRealApiIntegrationTest.java
- LawFirmRealApiIntegrationTest.java

**Key Differences from integration-tests:**
- ✅ Uses environment variables (stable)
- ✅ No static blocks setting properties
- ✅ No dynamic system property changes
- ✅ Uses Failsafe instead of provider matrix framework

---

### 3. ⚠️ INCOMPLETE: `behavior-integration-tests`

**Location:** `ai-infrastructure-module/integration-Testing/behavior-integration-tests`

**Status:** ⚠️ Provider configuration is NOT IMPLEMENTED

**Current State:**
- Shell script accepts provider matrix input: `"openai:openai"`
- Shell script sets environment variables
- **BUT:** No corresponding Spring profile or configuration to read them!

**Problems:**
1. No `application-realapi.yml` file (only `application.yml`)
2. Hardcoded providers in `application.yml`:
   ```yaml
   ai:
     providers:
       embedding-provider: onnx  # ← Always uses ONNX!
       provider: openai
   ```
3. Maven profile "realapi" doesn't exist in pom.xml
4. Environment variables are set but never read

**Workflow Expects:**
```bash
run-behavior-realapi-tests.sh "openai:openai:lucene"
```

**Actual Behavior:**
```yaml
# Always uses these hardcoded values:
embedding-provider: onnx
provider: openai
```

**To Fix This Module (Optional Future Work):**

Create `application-realapi.yml`:
```yaml
ai:
  providers:
    embedding-provider: ${AI_INFRASTRUCTURE_EMBEDDING_PROVIDER:${EMBEDDING_PROVIDER:onnx}}
    llm-provider: ${AI_INFRASTRUCTURE_LLM_PROVIDER:${LLM_PROVIDER:openai}}
  vector-db:
    type: ${AI_INFRASTRUCTURE_VECTOR_DATABASE:${VECTOR_DB:lucene}}
```

Update test classes:
```java
@ActiveProfiles("realapi")  // Instead of "integration"
```

**Test Classes (5):**
- BehaviorTrendBoundaryRealApiIT.java
- BehaviorAnalyticsRealApiIT.java
- BehaviorProcessingRealApiIT.java
- BehaviorSentimentChurnRealApiIT.java
- BehaviorLLMErrorResilienceRealApiIT.java

---

## Comparison Table

| Module | Provider Config | Spring Caching Issue? | Fix Needed? | Status |
|--------|----------------|----------------------|-------------|---------|
| **integration-tests** | System properties (dynamic) | ❌ Yes | ✅ Yes | **FIXED** |
| **relationship-query-integration-tests** | Environment variables (static) | ✅ No | ❌ No | **OK** |
| **behavior-integration-tests** | Not implemented | ⚠️ N/A | ⚠️ Optional | **INCOMPLETE** |

---

## Key Takeaways

### Why `integration-tests` Needed Fixing

**Problem Pattern:**
```java
// Step 1: AbstractProviderMatrixIntegrationTest dynamically sets property
System.setProperty("EMBEDDING_PROVIDER", "openai");

// Step 2: First test class runs
@SpringBootTest  // Creates & caches context with "openai"
public class TestA { }

// Step 3: Matrix runner changes property for next combination
System.setProperty("EMBEDDING_PROVIDER", "onnx");

// Step 4: Second test class runs
@SpringBootTest  // Reuses cached context! Still has "openai"
public class TestB { }
```

**Solution:**
```java
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)  // Forces fresh context
```

### Why Other Modules Don't Need Fixing

**relationship-query-integration-tests:**
- Environment variables are set BEFORE Maven starts
- They don't change during test execution
- Spring's cache key doesn't matter because config never changes

**behavior-integration-tests:**
- Configuration doesn't work at all currently
- Would need implementation work, not just @DirtiesContext
- Not actually using dynamic provider configuration

---

## Recommendations

### Immediate (Required)
✅ **Already done:** Applied `@DirtiesContext` fix to `integration-tests` module

### Future (Optional)
⚠️ **Consider:** Implementing provider configuration for `behavior-integration-tests` module
- Create `application-realapi.yml`
- Update test annotations to use "realapi" profile
- Test with different provider combinations

### Best Practice Going Forward
When creating new integration test modules:
- ✅ **Prefer:** Environment variables (like relationship-query)
- ❌ **Avoid:** Dynamic system properties during test execution
- ✅ **Use:** `@DirtiesContext` if you must change configuration between tests

---

## Testing the Fix

Run the workflow with different embedding providers:

```bash
# Test 1: OpenAI embedding
gh workflow run integration-tests-manual.yml \
  --field embedding_provider=openai \
  --field llm_provider=openai

# Test 2: ONNX embedding  
gh workflow run integration-tests-manual.yml \
  --field embedding_provider=onnx \
  --field llm_provider=openai
```

Verify in logs:
```
[1/3] Running tests for: LLM=openai | Embedding=openai | VectorDB=lucene | Storage=PER_TYPE_TABLE
```

The embedding provider should match what you selected, not always default to ONNX.
