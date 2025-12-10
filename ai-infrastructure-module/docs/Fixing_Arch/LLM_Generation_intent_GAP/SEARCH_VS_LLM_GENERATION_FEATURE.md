# Search vs LLM Generation Feature Implementation Guide

## Executive Summary

This document outlines the implementation of a critical feature gap: **differentiating between search-only queries and queries requiring LLM-based generation with context filtering**.

Currently, all `INFORMATION` intents are treated identically (RAG search only). This feature will:
1. Add `requiresGeneration` flag to `Intent` class
2. Implement LLM context filtering based on `include-in-rag` configuration
3. Return raw search results for search-only queries
4. Return LLM-generated responses with filtered context for generation queries

**Impact**: Enable the system to respect `include-in-rag: false` configuration fields by hiding them from LLM while keeping them searchable.

---

## Problem Statement

### Current Behavior (GAP)

```
User: "Show me products under $60"
  ↓
handleInformation() ALWAYS does RAG search
  ↓
Returns raw results with ALL fields (including costPrice: 50.00)
  ↓
User sees: {name: "...", description: "...", costPrice: 50.00} ✅

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

User: "Should I buy this product?"
  ↓
handleInformation() ALWAYS does RAG search (no LLM generation!)
  ↓
Returns raw results (same as above - NOT proper RAG response)
  ↓
Configuration with include-in-rag: false is IGNORED ❌
```

### Desired Behavior (After Implementation)

```
User: "Show me products under $60"
  ↓
Intent extracted: type=INFORMATION, requiresGeneration=false
  ↓
handleInformation() → Search only
  ↓
Returns raw results with ALL searchable fields
  ↓
User sees: {name: "...", description: "...", costPrice: 50.00} ✅

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

User: "Should I buy this product?"
  ↓
Intent extracted: type=INFORMATION, requiresGeneration=true
  ↓
handleInformation() → Search + LLM
  ├─ Search retrieves product
  ├─ Filter by include-in-rag flags
  ├─ Send filtered context to LLM
  └─ Return LLM response
  ↓
LLM context: {name: "...", description: "..."} (NO costPrice!)
  ↓
LLM gives unbiased recommendation ✅
```

---

## Implementation Plan

### Phase 1: Add `requiresGeneration` Flag to Intent

**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/Intent.java`

**Changes**:
1. Add `requiresGeneration` boolean field
2. Add normalization logic to detect generation-requiring queries
3. Add getter method with default fallback

**Code**:
```java
@JsonAlias({"requires_generation"})
private Boolean requiresGeneration;

/**
 * Detect if this intent requires LLM generation.
 * Heuristics: queries asking for opinion, recommendation, or analysis
 */
public void normalize() {
    // Existing code...
    if (requiresRetrieval == null) {
        requiresRetrieval = type == IntentType.INFORMATION || type == IntentType.COMPOUND;
    }
    
    // NEW: Auto-detect if generation needed
    if (requiresGeneration == null) {
        requiresGeneration = detectIfGenerationNeeded();
    }
    
    // Existing code...
}

private boolean detectIfGenerationNeeded() {
    if (intent == null || intent.isBlank()) {
        return false;
    }
    
    String intentLower = intent.toLowerCase();
    
    // Keywords indicating need for LLM generation
    return intentLower.matches(".*\\b(should|recommend|opinion|advise|compare|analyze|evaluate|assess)\\b.*");
}

public boolean requiresGenerationOrDefault(boolean fallback) {
    return requiresGeneration != null ? requiresGeneration : fallback;
}
```

---

### Phase 2: Add `include-in-rag` Support to Entity Configuration

**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/SearchableFieldConfig.java`

**Ensure** the following fields exist:
```java
@Data
@Builder
public class SearchableFieldConfig {
    private String name;
    private boolean includeInRag;  // ← Should already exist
    private boolean enableSemanticSearch;
    private double weight;
}
```

---

### Phase 3: Add LLM Context Filtering Logic

**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/RAGOrchestrator.java`

**Add new method**:
```java
/**
 * Filter search results to only include fields marked with include-in-rag: true
 * 
 * @param searchResponse the raw search response from RAG
 * @param entityType the entity type to load configuration from
 * @return filtered map suitable for LLM context
 */
private Map<String, Object> filterContextForLLM(
    AISearchResponse searchResponse, 
    String entityType) {
    
    try {
        AIEntityConfig config = configurationLoader.getEntityConfig(entityType);
        if (config == null) {
            log.warn("No config found for entity type: {}, returning unfiltered context", entityType);
            return convertToMap(searchResponse);
        }
        
        Map<String, Object> filteredContext = new LinkedHashMap<>();
        
        if (searchResponse.getDocuments() != null) {
            for (Map<String, Object> document : searchResponse.getDocuments()) {
                Map<String, Object> filteredDoc = new LinkedHashMap<>();
                
                for (Map.Entry<String, Object> field : document.entrySet()) {
                    SearchableFieldConfig fieldConfig = config.getSearchableField(field.getKey());
                    
                    // Include field if it has include-in-rag: true (default true)
                    if (fieldConfig == null) {
                        // Unknown fields: include by default (safe default)
                        filteredDoc.put(field.getKey(), field.getValue());
                    } else if (fieldConfig.isIncludeInRag()) {
                        // Explicitly marked for inclusion
                        filteredDoc.put(field.getKey(), field.getValue());
                    }
                    // else: exclude (include-in-rag: false)
                }
                
                filteredContext.put(
                    String.valueOf(document.get("id")), 
                    Collections.unmodifiableMap(filteredDoc)
                );
            }
        }
        
        return Collections.unmodifiableMap(filteredContext);
        
    } catch (Exception e) {
        log.error("Error filtering context for LLM", e);
        // Fallback: return unfiltered (fail-open for safety)
        return convertToMap(searchResponse);
    }
}

private Map<String, Object> convertToMap(AISearchResponse response) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (response != null && response.getDocuments() != null) {
        for (Map<String, Object> doc : response.getDocuments()) {
            result.put(String.valueOf(doc.get("id")), doc);
        }
    }
    return result;
}
```

---

### Phase 4: Update `handleInformation()` to Support Both Flows

**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/RAGOrchestrator.java`

**Update method**:
```java
private OrchestrationResult handleInformation(Intent intent, String userId) {
    String query = StringUtils.hasText(intent.getIntent()) ? 
        intent.getIntent() : intent.getIntentOrAction();
    
    RAGRequest ragRequest = RAGRequest.builder()
        .query(query)
        .entityType(intent.getVectorSpace())
        .limit(DEFAULT_RAG_LIMIT)
        .threshold(DEFAULT_RAG_THRESHOLD)
        .metadata(Map.of(
            "source", "orchestrator",
            "userId", userId,
            "requiresGeneration", intent.requiresGenerationOrDefault(false)
        ))
        .build();

    RAGResponse ragResponse = ragService.performRag(ragRequest);

    Map<String, Object> data = new LinkedHashMap<>();
    data.put("documents", ragResponse.getDocuments());
    data.put("ragResponse", ragResponse);

    // FLOW 1: Search-only (no LLM generation)
    if (!intent.requiresGenerationOrDefault(false)) {
        log.debug("Returning search results without LLM generation (intent: {})", intent.getIntent());
        data.put("answer", ragResponse.getResponse());
        
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true)
            .message(ragResponse.getResponse())
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }

    // FLOW 2: Search + LLM generation (with context filtering)
    try {
        log.debug("Generating LLM response with filtered context (intent: {})", intent.getIntent());
        
        // Filter context based on include-in-rag flags
        Map<String, Object> filteredContext = filterContextForLLM(
            ragResponse, 
            intent.getVectorSpace()
        );
        
        // Generate LLM response with filtered context
        String llmResponse = generateLLMResponse(query, filteredContext);
        
        data.put("answer", llmResponse);
        data.put("filteredContext", filteredContext);
        data.put("contextFiltered", true);
        
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true)
            .message(llmResponse)
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
            
    } catch (Exception e) {
        log.error("Error generating LLM response, falling back to search results", e);
        // Fallback to search-only on LLM error
        data.put("answer", ragResponse.getResponse());
        data.put("fallbackReason", e.getMessage());
        
        return OrchestrationResult.builder()
            .type(OrchestrationResultType.INFORMATION_PROVIDED)
            .success(true)
            .message(ragResponse.getResponse())
            .data(Collections.unmodifiableMap(data))
            .nextSteps(extractNextSteps(intent))
            .build();
    }
}

private String generateLLMResponse(String query, Map<String, Object> context) {
    // Implementation depends on your LLM service
    // This is a placeholder showing the interface
    
    StringBuilder prompt = new StringBuilder();
    prompt.append("Based on the following context:\n\n");
    
    context.forEach((id, doc) -> {
        if (doc instanceof Map) {
            prompt.append(formatDocument((Map<?, ?>) doc));
        }
    });
    
    prompt.append("\n\nAnswer this query: ").append(query);
    
    // Call your LLM service here
    // return llmService.generateResponse(prompt.toString());
    return "LLM response would be generated here";
}

private String formatDocument(Map<?, ?> doc) {
    StringBuilder sb = new StringBuilder();
    doc.forEach((key, value) -> 
        sb.append(key).append(": ").append(value).append("\n")
    );
    return sb.toString();
}
```

---

## Testing Strategy

### Test Cases

1. **Basic Search-Only Query**
   - Query: "Show me wallets under $60"
   - Expected: `requiresGeneration=false`, raw results returned with all fields

2. **LLM Generation Query**
   - Query: "Should I buy this wallet?"
   - Expected: `requiresGeneration=true`, LLM generates response with filtered context

3. **Context Filtering**
   - Setup: `costPrice` with `include-in-rag: false`
   - Verify: costPrice NOT in LLM context but present in search results

4. **Unknown Fields**
   - Setup: Document with fields not in config
   - Expected: Unknown fields included in LLM context (fail-open)

5. **Missing Config**
   - Setup: Entity type without config
   - Expected: Fallback to unfiltered context (safe default)

6. **LLM Generation Error**
   - Setup: LLM service throws exception
   - Expected: Fallback to search results, error logged

---

## Integration Test Implementation

### Test File: `SearchVsLLMGenerationIntegrationTest.java`

**Location**: `ai-infrastructure-core/src/test/java/com/ai/infrastructure/intent/orchestration/SearchVsLLMGenerationIntegrationTest.java`

**See accompanying test file for full implementation**

---

## Configuration Example

### YAML Configuration

```yaml
ai-entities:
  product:
    searchable-fields:
      - name: name
        include-in-rag: true          # ✅ Send to LLM
        enable-semantic-search: true
        weight: 2.0
      
      - name: description
        include-in-rag: true          # ✅ Send to LLM
        enable-semantic-search: true
        weight: 1.5
      
      - name: costPrice
        include-in-rag: false         # ❌ Hide from LLM
        enable-semantic-search: true  # ✅ But searchable!
        weight: 1.0
      
      - name: retailPrice
        include-in-rag: true          # ✅ Send to LLM
        enable-semantic-search: true
        weight: 1.0
      
      - name: internalMargin
        include-in-rag: false         # ❌ Hide from LLM
        enable-semantic-search: false # ❌ Not searchable either
        weight: 0.0
```

---

## Performance Considerations

1. **Context Filtering Overhead**: O(n) where n = number of fields
   - Typically < 1ms for reasonable field counts
   - Cache entity configs to avoid repeated lookups

2. **LLM Generation Latency**: 
   - Search-only queries unaffected (~200ms)
   - LLM generation adds ~500-2000ms (depends on LLM provider)

3. **Memory Impact**:
   - Filtered context typically 10-30% smaller than raw results
   - Significant benefit for large documents with many hidden fields

---

## Rollout Strategy

### Phase 1: Soft Launch (Days 1-3)
- Deploy code with feature flag disabled
- Monitor for any issues
- Enable for 5% of traffic

### Phase 2: Gradual Rollout (Days 4-7)
- Increase to 25% of traffic
- Monitor metrics: latency, error rate, LLM generation success
- Test with real users in pilot program

### Phase 3: Full Rollout (Days 8+)
- 100% of traffic
- Continue monitoring
- Gather feedback from users

---

## Monitoring & Metrics

### Key Metrics to Track

1. **Query Type Distribution**
   - % of search-only queries
   - % of LLM generation queries

2. **Performance Metrics**
   - Latency: search-only vs LLM generation
   - LLM generation success rate
   - Context filtering overhead

3. **Configuration Validation**
   - % of fields with include-in-rag: false
   - Fields most commonly hidden from LLM

4. **Error Rates**
   - LLM generation failures
   - Fallbacks to search-only
   - Config loading errors

---

## Success Criteria

- ✅ 95%+ of LLM generation queries complete successfully
- ✅ Search-only queries maintain < 250ms latency
- ✅ LLM generation queries complete within 3 seconds
- ✅ No regression in existing search-only functionality
- ✅ Context filtering correctly applies include-in-rag: false
- ✅ All integration tests pass
- ✅ Zero production incidents in first week

---

## Rollback Plan

If issues occur:

1. **Immediate**: Disable feature flag to 0%
2. **Investigation**: Review logs, metrics, error patterns
3. **Fix**: Address root cause
4. **Retest**: Verify fix in staging
5. **Relaunch**: Restart rollout from Phase 1

---

## Future Enhancements

1. **Machine Learning-based Intent Detection**
   - Replace regex heuristics with ML model
   - Higher accuracy in generation detection

2. **Caching Filtered Contexts**
   - Cache filtered results for common queries
   - Reduce computation overhead

3. **Custom Generation Rules**
   - Allow per-entity-type generation strategies
   - Support domain-specific logic

4. **A/B Testing**
   - Compare search-only vs LLM generation quality
   - Measure user satisfaction

5. **Query Rewriting**
   - Auto-rewrite search queries for better LLM prompts
   - Improve generation quality

---

## References

- Intent.java: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/Intent.java`
- RAGOrchestrator.java: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/RAGOrchestrator.java`
- SearchableFieldConfig.java: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/SearchableFieldConfig.java`

