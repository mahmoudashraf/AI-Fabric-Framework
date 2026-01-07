# AdvancedRAGService Orchestrator Integration Implementation Plan

## Executive Summary

**Objective**: Integrate `AdvancedRAGService` with the orchestrator (`IntentHandlingStep`) to enable advanced RAG features (query expansion, re-ranking, context optimization) in the orchestration pipeline.

**Approach**: Add `AdvancedRAGService` as an optional dependency to `IntentHandlingStep`, with conditional logic to use advanced features when appropriate.

**Status**: Planning Phase

---

## Current State Analysis

### Current Orchestrator Flow

**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/IntentHandlingStep.java`

**Current Implementation**:
```java
private OrchestrationResult handleInformation(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
    boolean needsGeneration = intent.requiresGenerationOrDefault(false);
    
    RAGRequest ragRequest = RAGRequest.builder()
        .query(query)
        .entityType(intent.getVectorSpace())
        .limit(DEFAULT_RAG_LIMIT)
        .threshold(DEFAULT_RAG_THRESHOLD)
        .metadata(metadata)
        .userId(context.getIdentifier())
        .build();
    
    RAGResponse ragResponse = needsGeneration
        ? ragProvider.performRAGQuery(ragRequest)  // ← Basic RAGService
        : ragProvider.performRag(ragRequest);
    
    // Returns basic RAG response
}
```

**Limitations**:
- ❌ No query expansion
- ❌ No re-ranking
- ❌ No context optimization
- ❌ No advanced features
- ❌ Only uses basic `RAGService`

---

### AdvancedRAGService Capabilities

**File**: `ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/service/AdvancedRAGService.java`

**Available Features**:
- ✅ Query expansion (LLM-based)
- ✅ Multi-strategy parallel search
- ✅ Re-ranking (semantic, hybrid, diversity)
- ✅ Context optimization (high, medium, low)
- ✅ LLM-based response generation
- ✅ Confidence scoring

**Current Usage**: Only used directly by applications, not through orchestrator.

---

## Architecture Design

### Target Architecture

```
User Query
    ↓
Orchestrator Pipeline
    ├─ SecurityAnalysisStep
    ├─ AccessControlStep
    ├─ PIIDetectionStep
    ├─ ComplianceCheckStep
    ├─ IntentExtractionStep
    └─ IntentHandlingStep  ← MODIFY THIS
        ├─ Check: Should use AdvancedRAG?
        │   ├─ YES → AdvancedRAGService.performAdvancedRAG()
        │   └─ NO  → RAGService.performRag()
        └─ Generate response (if needed)
            └─ AICoreService.generateText()  ← In orchestrator
```

---

### Decision Logic

**When to use AdvancedRAGService**:

1. **Intent metadata indicates advanced features**
   - `intent.getMetadata().get("useAdvancedRAG") == true`
   - `intent.getMetadata().get("expansionLevel") != null`
   - `intent.getMetadata().get("rerankingStrategy") != null`

2. **User preference for advanced features**
   - User profile indicates preference
   - Session context indicates need

3. **Query complexity**
   - Long queries (>50 characters)
   - Question queries (contains "?")
   - Complex queries requiring expansion

4. **Configuration flag**
   - `ai.infrastructure.rag.advanced.enabled=true`
   - `ai.infrastructure.rag.advanced.auto-enable-for-complex-queries=true`

**When to use basic RAGService**:

1. Simple queries
2. Fast retrieval needed (no LLM overhead)
3. User preference for basic search
4. Advanced features disabled

---

## Implementation Plan

### Phase 1: Extend IntentHandlingStep

#### Step 1.1: Add Dependencies

**File**: `IntentHandlingStep.java`

**Changes**:
```java
@RequiredArgsConstructor
public class IntentHandlingStep implements PipelineStep {
    
    // Existing dependencies
    private final RAGProvider ragProvider;
    private final ActionHandlerRegistry actionHandlerRegistry;
    
    // NEW: Add AdvancedRAGService (optional)
    @Autowired(required = false)
    private AdvancedRAGService advancedRAGService;
    
    // NEW: Add AICoreService for generation
    @Autowired(required = false)
    private AICoreService aiCoreService;
    
    // NEW: Add configuration
    private final AIServiceConfig serviceConfig;
}
```

**Rationale**:
- `@Autowired(required = false)` makes `AdvancedRAGService` optional
- If not available, falls back to basic `RAGService`
- `AICoreService` for LLM generation in orchestrator

---

#### Step 1.2: Add Decision Method

**File**: `IntentHandlingStep.java`

**New Method**:
```java
/**
 * Determine whether to use AdvancedRAGService or basic RAGService.
 * 
 * @param intent the extracted intent
 * @param context the orchestration context
 * @return true if AdvancedRAGService should be used
 */
private boolean shouldUseAdvancedRAG(Intent intent, OrchestrationContext context) {
    // Check 1: Explicit flag in intent metadata
    Map<String, Object> intentMetadata = intent.getMetadata();
    if (intentMetadata != null) {
        Object useAdvanced = intentMetadata.get("useAdvancedRAG");
        if (useAdvanced instanceof Boolean && (Boolean) useAdvanced) {
            return true;
        }
        
        // Check for advanced feature indicators
        if (intentMetadata.containsKey("expansionLevel") || 
            intentMetadata.containsKey("rerankingStrategy") ||
            intentMetadata.containsKey("contextOptimizationLevel")) {
            return true;
        }
    }
    
    // Check 2: AdvancedRAGService availability
    if (advancedRAGService == null) {
        return false;  // Not available, use basic
    }
    
    // Check 3: Configuration
    if (serviceConfig != null && serviceConfig.getFeatures() != null) {
        Boolean advancedEnabled = serviceConfig.getFeatures().getEnableAdvancedRAG();
        if (advancedEnabled != null && !advancedEnabled) {
            return false;  // Explicitly disabled
        }
    }
    
    // Check 4: Query complexity
    String query = intent.getIntentOrAction();
    if (query != null) {
        // Complex queries: long, questions, multiple terms
        boolean isComplex = query.length() > 50 || 
                           query.contains("?") || 
                           query.split("\\s+").length > 5;
        
        // Auto-enable for complex queries if configured
        if (isComplex && isAutoEnableForComplexQueries()) {
            return true;
        }
    }
    
    // Check 5: User preference (from context)
    if (context.getMetadata() != null) {
        Object userPrefersAdvanced = context.getMetadata().get("prefersAdvancedRAG");
        if (userPrefersAdvanced instanceof Boolean && (Boolean) userPrefersAdvanced) {
            return true;
        }
    }
    
    // Default: use basic RAG
    return false;
}

/**
 * Check if auto-enable for complex queries is configured.
 */
private boolean isAutoEnableForComplexQueries() {
    // Check configuration property
    // ai.infrastructure.rag.advanced.auto-enable-for-complex-queries
    // Default: false (conservative)
    return false;  // TODO: Read from config
}
```

---

#### Step 1.3: Add Advanced RAG Handler

**File**: `IntentHandlingStep.java`

**New Method**:
```java
/**
 * Handle INFORMATION intent using AdvancedRAGService.
 * 
 * @param intent the extracted intent
 * @param context the orchestration context
 * @param pipelineContext the pipeline context
 * @return orchestration result with advanced RAG response
 */
private OrchestrationResult handleInformationAdvanced(
        Intent intent, 
        OrchestrationContext context, 
        PipelineContext pipelineContext) {
    
    String query = resolveQuery(intent, pipelineContext);
    Map<String, Object> metadata = buildMetadata(intent, context, pipelineContext);
    
    // Build AdvancedRAGRequest from intent and context
    AdvancedRAGRequest advancedRequest = buildAdvancedRAGRequest(
        intent, context, pipelineContext, query, metadata);
    
    // Perform advanced RAG
    AdvancedRAGResponse advancedResponse = advancedRAGService.performAdvancedRAG(advancedRequest);
    
    // Build orchestration result
    Map<String, Object> data = new LinkedHashMap<>();
    data.put(DATA_KEY_ANSWER, advancedResponse.getResponse());
    data.put(DATA_KEY_DOCUMENTS, convertToRAGDocuments(advancedResponse.getDocuments()));
    data.put(DATA_KEY_RAG_RESPONSE, convertToRAGResponse(advancedResponse));
    data.put(DATA_KEY_EXPANDED_QUERIES, advancedResponse.getExpandedQueries());
    data.put(DATA_KEY_CONFIDENCE_SCORE, advancedResponse.getConfidenceScore());
    data.put(DATA_KEY_RERANKING_STRATEGY, advancedResponse.getRerankingStrategy());
    data.put(DATA_KEY_CONTEXT_OPTIMIZATION_LEVEL, advancedResponse.getContextOptimizationLevel());
    data.put(DATA_KEY_REQUIRES_GENERATION, true);
    
    String message = StringUtils.hasText(advancedResponse.getResponse())
        ? advancedResponse.getResponse()
        : MSG_SEARCH_COMPLETED;
    
    return OrchestrationResult.builder()
        .type(OrchestrationResultType.INFORMATION_PROVIDED)
        .success(Boolean.TRUE.equals(advancedResponse.getSuccess()) || advancedResponse.getSuccess() == null)
        .message(message)
        .data(Collections.unmodifiableMap(data))
        .nextSteps(extractNextSteps(intent))
        .build();
}

/**
 * Build AdvancedRAGRequest from intent and context.
 */
private AdvancedRAGRequest buildAdvancedRAGRequest(
        Intent intent,
        OrchestrationContext context,
        PipelineContext pipelineContext,
        String query,
        Map<String, Object> metadata) {
    
    Map<String, Object> intentMetadata = intent.getMetadata() != null 
        ? intent.getMetadata() 
        : new HashMap<>();
    
    // Extract advanced feature settings from intent metadata or use defaults
    Integer expansionLevel = extractInteger(intentMetadata, "expansionLevel", 2);
    String rerankingStrategy = extractString(intentMetadata, "rerankingStrategy", "hybrid");
    String contextOptimizationLevel = extractString(intentMetadata, "contextOptimizationLevel", "medium");
    Boolean enableHybridSearch = extractBoolean(intentMetadata, "enableHybridSearch", true);
    Boolean enableContextualSearch = extractBoolean(intentMetadata, "enableContextualSearch", true);
    
    // Build user context if available
    String userContext = buildUserContext(context);
    
    // Build filters from intent
    Map<String, Object> filters = extractFilters(intent, context);
    
    AdvancedRAGRequest.AdvancedRAGRequestBuilder builder = AdvancedRAGRequest.builder()
        .query(query)
        .entityType(intent.getVectorSpace())
        .maxResults(extractInteger(intentMetadata, "maxResults", DEFAULT_RAG_LIMIT * 2))
        .maxDocuments(extractInteger(intentMetadata, "maxDocuments", DEFAULT_RAG_LIMIT))
        .expansionLevel(expansionLevel)
        .rerankingStrategy(rerankingStrategy)
        .contextOptimizationLevel(contextOptimizationLevel)
        .enableHybridSearch(enableHybridSearch)
        .enableContextualSearch(enableContextualSearch)
        .metadata(metadata)
        .userId(context.getIdentifier())
        .sessionId(context.getSessionId());
    
    if (userContext != null) {
        builder.context(userContext);
    }
    
    if (filters != null && !filters.isEmpty()) {
        builder.filters(filters);
    }
    
    // Add categories if specified
    List<String> categories = extractCategories(intentMetadata);
    if (categories != null && !categories.isEmpty()) {
        builder.categories(categories);
    }
    
    return builder.build();
}

/**
 * Extract integer value from metadata with default.
 */
private Integer extractInteger(Map<String, Object> metadata, String key, Integer defaultValue) {
    Object value = metadata.get(key);
    if (value instanceof Number) {
        return ((Number) value).intValue();
    }
    if (value instanceof String) {
        try {
            return Integer.parseInt((String) value);
        } catch (NumberFormatException e) {
            // Ignore
        }
    }
    return defaultValue;
}

/**
 * Extract string value from metadata with default.
 */
private String extractString(Map<String, Object> metadata, String key, String defaultValue) {
    Object value = metadata.get(key);
    if (value instanceof String) {
        return (String) value;
    }
    if (value != null) {
        return String.valueOf(value);
    }
    return defaultValue;
}

/**
 * Extract boolean value from metadata with default.
 */
private Boolean extractBoolean(Map<String, Object> metadata, String key, Boolean defaultValue) {
    Object value = metadata.get(key);
    if (value instanceof Boolean) {
        return (Boolean) value;
    }
    if (value instanceof String) {
        return Boolean.parseBoolean((String) value);
    }
    return defaultValue;
}

/**
 * Build user context from orchestration context.
 */
private String buildUserContext(OrchestrationContext context) {
    Map<String, Object> userContext = new HashMap<>();
    
    if (context.getMetadata() != null) {
        // Extract user preferences
        Object preferences = context.getMetadata().get("preferences");
        if (preferences instanceof Map) {
            userContext.put("preferences", preferences);
        }
        
        // Extract user profile
        Object profile = context.getMetadata().get("profile");
        if (profile instanceof Map) {
            userContext.put("profile", profile);
        }
    }
    
    if (userContext.isEmpty()) {
        return null;
    }
    
    try {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(userContext);
    } catch (Exception e) {
        log.warn("Failed to serialize user context", e);
        return null;
    }
}

/**
 * Extract filters from intent and context.
 */
private Map<String, Object> extractFilters(Intent intent, OrchestrationContext context) {
    Map<String, Object> filters = new HashMap<>();
    
    // Extract from intent metadata
    if (intent.getMetadata() != null) {
        Object intentFilters = intent.getMetadata().get("filters");
        if (intentFilters instanceof Map) {
            filters.putAll((Map<String, Object>) intentFilters);
        }
    }
    
    // Extract from context metadata
    if (context.getMetadata() != null) {
        Object contextFilters = context.getMetadata().get("filters");
        if (contextFilters instanceof Map) {
            filters.putAll((Map<String, Object>) contextFilters);
        }
    }
    
    return filters.isEmpty() ? null : filters;
}

/**
 * Extract categories from intent metadata.
 */
private List<String> extractCategories(Map<String, Object> metadata) {
    Object categories = metadata.get("categories");
    if (categories instanceof List) {
        return ((List<?>) categories).stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .collect(Collectors.toList());
    }
    return null;
}
```

---

#### Step 1.4: Update handleInformation Method

**File**: `IntentHandlingStep.java`

**Changes**:
```java
private OrchestrationResult handleInformation(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
    boolean needsGeneration = intent.requiresGenerationOrDefault(false);
    boolean useAdvanced = shouldUseAdvancedRAG(intent, context);
    
    if (useAdvanced && advancedRAGService != null) {
        // Use AdvancedRAGService
        return handleInformationAdvanced(intent, context, pipelineContext);
    }
    
    // Use basic RAGService (existing logic)
    String query = resolveQuery(intent, pipelineContext);
    Map<String, Object> metadata = buildMetadata(intent, context, pipelineContext);
    
    RAGRequest ragRequest = RAGRequest.builder()
        .query(query)
        .entityType(intent.getVectorSpace())
        .limit(DEFAULT_RAG_LIMIT)
        .threshold(DEFAULT_RAG_THRESHOLD)
        .metadata(Collections.unmodifiableMap(metadata))
        .userId(context.getIdentifier())
        .build();
    
    // Always do retrieval
    RAGResponse ragResponse = ragProvider.performRag(ragRequest);
    
    // Generate response if needed (in orchestrator)
    String response = null;
    if (needsGeneration && aiCoreService != null) {
        response = generateRAGResponse(query, ragResponse.getContext(), pipelineContext);
    }
    
    Map<String, Object> data = new LinkedHashMap<>();
    data.put(DATA_KEY_ANSWER, response);
    data.put(DATA_KEY_DOCUMENTS, ragResponse.getDocuments());
    data.put(DATA_KEY_RAG_RESPONSE, ragResponse);
    data.put(DATA_KEY_REQUIRES_GENERATION, needsGeneration);
    
    String message = StringUtils.hasText(response)
        ? response
        : MSG_SEARCH_COMPLETED;
    
    return OrchestrationResult.builder()
        .type(OrchestrationResultType.INFORMATION_PROVIDED)
        .success(Boolean.TRUE.equals(ragResponse.getSuccess()) || ragResponse.getSuccess() == null)
        .message(message)
        .data(Collections.unmodifiableMap(data))
        .nextSteps(extractNextSteps(intent))
        .build();
}

/**
 * Resolve query from intent and pipeline context.
 */
private String resolveQuery(Intent intent, PipelineContext pipelineContext) {
    String optimizedQuery = StringUtils.hasText(intent.getOptimizedQuery()) 
        ? intent.getOptimizedQuery() 
        : null;
    String processedQuery = pipelineContext != null 
        ? pipelineContext.getEffectiveQuery() 
        : null;
    
    return StringUtils.hasText(optimizedQuery)
        ? optimizedQuery
        : (StringUtils.hasText(processedQuery) ? processedQuery : intent.getIntentOrAction());
}

/**
 * Build metadata for RAG request.
 */
private Map<String, Object> buildMetadata(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(METADATA_KEY_SOURCE, METADATA_VALUE_ORCHESTRATOR);
    metadata.put(METADATA_KEY_USER_ID, context.getIdentifier());
    metadata.put(METADATA_KEY_SESSION_ID, context.getSessionId());
    metadata.put(METADATA_KEY_AUTHENTICATED, context.isAuthenticated());
    
    String optimizedQuery = StringUtils.hasText(intent.getOptimizedQuery()) 
        ? intent.getOptimizedQuery() 
        : null;
    if (optimizedQuery != null) {
        metadata.put(METADATA_KEY_OPTIMIZED_QUERY, optimizedQuery);
    }
    
    if (pipelineContext != null && !pipelineContext.getDetectedPiiTypesView().isEmpty()) {
        metadata.put("piiProcessed", true);
        metadata.put("piiDetectedTypes", pipelineContext.getDetectedPiiTypesView());
    }
    
    return metadata;
}

/**
 * Generate RAG response using LLM (in orchestrator).
 */
private String generateRAGResponse(String query, String context, PipelineContext pipelineContext) {
    if (aiCoreService == null) {
        log.warn("AICoreService not available, cannot generate response");
        return null;
    }
    
    if (StringUtils.isEmpty(context) || "No relevant context found.".equals(context)) {
        return "I don't have enough information to answer your question: " + query;
    }
    
    String prompt = buildRAGPrompt(query, context);
    
    try {
        return aiCoreService.generateText(prompt);
    } catch (Exception e) {
        log.error("Failed to generate RAG response", e);
        return null;
    }
}

/**
 * Build prompt for RAG response generation.
 */
private String buildRAGPrompt(String query, String context) {
    return String.format(
        "Based on the following context, answer the question: %s\n\n" +
        "Context:\n%s\n\n" +
        "Provide a comprehensive, accurate answer based on the context provided. " +
        "If the context doesn't contain enough information, say so.",
        query, context
    );
}
```

---

#### Step 1.5: Add Conversion Methods

**File**: `IntentHandlingStep.java`

**New Methods**:
```java
/**
 * Convert AdvancedRAGResponse.RAGDocument to RAGResponse.RAGDocument.
 */
private List<RAGResponse.RAGDocument> convertToRAGDocuments(
        List<AdvancedRAGResponse.RAGDocument> advancedDocuments) {
    if (advancedDocuments == null) {
        return Collections.emptyList();
    }
    
    return advancedDocuments.stream()
        .map(this::convertToRAGDocument)
        .collect(Collectors.toList());
}

/**
 * Convert single AdvancedRAGResponse.RAGDocument to RAGResponse.RAGDocument.
 */
private RAGResponse.RAGDocument convertToRAGDocument(AdvancedRAGResponse.RAGDocument advancedDoc) {
    return RAGResponse.RAGDocument.builder()
        .id(advancedDoc.getId())
        .content(advancedDoc.getContent())
        .title(advancedDoc.getTitle())
        .type(advancedDoc.getType())
        .score(advancedDoc.getScore())
        .similarity(advancedDoc.getSimilarity())
        .metadata(advancedDoc.getMetadata())
        .build();
}

/**
 * Convert AdvancedRAGResponse to RAGResponse for backward compatibility.
 */
private RAGResponse convertToRAGResponse(AdvancedRAGResponse advancedResponse) {
    return RAGResponse.builder()
        .response(advancedResponse.getResponse())
        .context(advancedResponse.getContext())
        .documents(convertToRAGDocuments(advancedResponse.getDocuments()))
        .totalDocuments(advancedResponse.getTotalDocuments())
        .usedDocuments(advancedResponse.getUsedDocuments())
        .confidenceScore(advancedResponse.getConfidenceScore())
        .relevanceScores(advancedResponse.getRelevanceScores())
        .processingTimeMs(advancedResponse.getProcessingTimeMs())
        .requestId(UUID.randomUUID().toString())
        .model(advancedResponse.getModel())
        .success(advancedResponse.getSuccess())
        .errorMessage(advancedResponse.getErrorMessage())
        .build();
}
```

---

### Phase 2: Add Configuration Support

#### Step 2.1: Extend AIServiceConfig

**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIServiceConfig.java`

**Add to FeatureFlags class**:
```java
@Data
public static class FeatureFlags {
    // ... existing fields ...
    
    /**
     * Enable advanced RAG features (query expansion, re-ranking, context optimization).
     * Default: false (conservative, opt-in)
     */
    @Builder.Default
    private Boolean enableAdvancedRAG = false;
    
    /**
     * Auto-enable advanced RAG for complex queries.
     * Default: false
     */
    @Builder.Default
    private Boolean autoEnableAdvancedRAGForComplexQueries = false;
    
    /**
     * Default expansion level for advanced RAG.
     * Default: 2
     */
    @Builder.Default
    private Integer defaultExpansionLevel = 2;
    
    /**
     * Default re-ranking strategy for advanced RAG.
     * Options: semantic, hybrid, diversity, score
     * Default: hybrid
     */
    @Builder.Default
    private String defaultRerankingStrategy = "hybrid";
    
    /**
     * Default context optimization level for advanced RAG.
     * Options: high, medium, low
     * Default: medium
     */
    @Builder.Default
    private String defaultContextOptimizationLevel = "medium";
}
```

---

#### Step 2.2: Add Configuration Properties

**File**: `application.yml` (example)

```yaml
ai:
  service:
    features:
      enable-advanced-rag: true
      auto-enable-advanced-rag-for-complex-queries: true
      default-expansion-level: 2
      default-reranking-strategy: hybrid
      default-context-optimization-level: medium
```

---

### Phase 3: Update Intent Extraction (Optional)

#### Step 3.1: Enhance Intent Metadata

**File**: Intent extraction service (wherever intents are created)

**Enhancement**: Allow intent extraction to indicate when advanced RAG should be used.

**Example**:
```java
// In IntentQueryExtractor or similar
Intent intent = Intent.builder()
    .type(IntentType.INFORMATION)
    .intentOrAction(query)
    .vectorSpace("product")
    .requiresGeneration(true)
    .metadata(Map.of(
        "useAdvancedRAG", true,  // ← Signal to use advanced RAG
        "expansionLevel", 3,
        "rerankingStrategy", "semantic",
        "contextOptimizationLevel", "high"
    ))
    .build();
```

---

### Phase 4: Add Constants

**File**: `IntentHandlingStep.java`

**Add constants**:
```java
// Existing constants...
private static final String DATA_KEY_ANSWER = "answer";
private static final String DATA_KEY_DOCUMENTS = "documents";
private static final String DATA_KEY_RAG_RESPONSE = "ragResponse";
private static final String DATA_KEY_REQUIRES_GENERATION = "requiresGeneration";

// NEW constants for advanced RAG
private static final String DATA_KEY_EXPANDED_QUERIES = "expandedQueries";
private static final String DATA_KEY_CONFIDENCE_SCORE = "confidenceScore";
private static final String DATA_KEY_RERANKING_STRATEGY = "rerankingStrategy";
private static final String DATA_KEY_CONTEXT_OPTIMIZATION_LEVEL = "contextOptimizationLevel";
```

---

## Complete Code Changes

### File: IntentHandlingStep.java

**Full changes summary**:

1. **Add imports**:
```java
import com.ai.infrastructure.rag.service.AdvancedRAGService;
import com.ai.infrastructure.core.AICoreService;
import com.ai.infrastructure.dto.AdvancedRAGRequest;
import com.ai.infrastructure.dto.AdvancedRAGResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
```

2. **Add dependencies** (in constructor/fields):
```java
@Autowired(required = false)
private AdvancedRAGService advancedRAGService;

@Autowired(required = false)
private AICoreService aiCoreService;

private final AIServiceConfig serviceConfig;
```

3. **Add new methods**:
   - `shouldUseAdvancedRAG()`
   - `handleInformationAdvanced()`
   - `buildAdvancedRAGRequest()`
   - `generateRAGResponse()`
   - `buildRAGPrompt()`
   - `convertToRAGDocuments()`
   - `convertToRAGDocument()`
   - `convertToRAGResponse()`
   - Helper methods for extraction

4. **Modify existing method**:
   - `handleInformation()` - add conditional logic

---

## Testing Strategy

### Unit Tests

**File**: `IntentHandlingStepTest.java`

**Test Cases**:

1. **Test shouldUseAdvancedRAG()**:
   - ✅ Returns true when intent metadata has `useAdvancedRAG=true`
   - ✅ Returns true when intent metadata has `expansionLevel`
   - ✅ Returns false when `AdvancedRAGService` is null
   - ✅ Returns false when explicitly disabled in config
   - ✅ Returns true for complex queries when auto-enable is on
   - ✅ Returns false for simple queries

2. **Test handleInformationAdvanced()**:
   - ✅ Calls `advancedRAGService.performAdvancedRAG()` with correct request
   - ✅ Converts `AdvancedRAGResponse` to `OrchestrationResult`
   - ✅ Includes expanded queries in response data
   - ✅ Includes confidence score in response data
   - ✅ Handles errors gracefully

3. **Test handleInformation() with advanced**:
   - ✅ Uses `AdvancedRAGService` when `shouldUseAdvancedRAG()` returns true
   - ✅ Falls back to basic `RAGService` when `shouldUseAdvancedRAG()` returns false
   - ✅ Falls back to basic when `AdvancedRAGService` is null

4. **Test buildAdvancedRAGRequest()**:
   - ✅ Extracts expansion level from intent metadata
   - ✅ Extracts re-ranking strategy from intent metadata
   - ✅ Uses defaults when metadata not present
   - ✅ Includes user context when available
   - ✅ Includes filters when available

---

### Integration Tests

**File**: `AdvancedRAGOrchestratorIntegrationTest.java`

**Test Cases**:

1. **Test orchestrator with AdvancedRAGService**:
   - ✅ Simple query uses basic RAG
   - ✅ Complex query uses advanced RAG (if auto-enable)
   - ✅ Explicit flag uses advanced RAG
   - ✅ Advanced RAG returns expanded queries
   - ✅ Advanced RAG returns re-ranked documents

2. **Test fallback behavior**:
   - ✅ Works when `AdvancedRAGService` is not available
   - ✅ Works when advanced features disabled
   - ✅ Basic RAG still works

---

## Migration Plan

### Step 1: Add Optional Dependencies

**Impact**: Low - Optional dependencies don't break existing code.

**Action**:
1. Add `@Autowired(required = false)` for `AdvancedRAGService`
2. Add `@Autowired(required = false)` for `AICoreService`
3. Add `AIServiceConfig` dependency

**Testing**: Verify existing tests still pass.

---

### Step 2: Add Decision Logic

**Impact**: Low - Only adds new method, doesn't change existing flow.

**Action**:
1. Add `shouldUseAdvancedRAG()` method
2. Add configuration support

**Testing**: Unit test decision logic.

---

### Step 3: Add Advanced Handler

**Impact**: Medium - New code path, but optional.

**Action**:
1. Add `handleInformationAdvanced()` method
2. Add conversion methods
3. Add helper methods

**Testing**: Unit test and integration test.

---

### Step 4: Update handleInformation

**Impact**: Medium - Changes existing method, but backward compatible.

**Action**:
1. Add conditional check at start of `handleInformation()`
2. Route to advanced or basic handler
3. Update basic handler to use orchestrator generation

**Testing**: 
- Verify existing tests still pass
- Add new tests for advanced path

---

### Step 5: Update Configuration

**Impact**: Low - Adds new configuration options.

**Action**:
1. Add configuration properties to `AIServiceConfig`
2. Update documentation
3. Add default values

**Testing**: Configuration loading tests.

---

## Configuration Examples

### Enable Advanced RAG Globally

```yaml
ai:
  service:
    features:
      enable-advanced-rag: true
      auto-enable-advanced-rag-for-complex-queries: true
```

**Result**: All complex queries automatically use advanced RAG.

---

### Enable Advanced RAG Per-Intent

```java
// In intent extraction
Intent intent = Intent.builder()
    .type(IntentType.INFORMATION)
    .intentOrAction("What are the best practices for microservices?")
    .vectorSpace("documentation")
    .metadata(Map.of(
        "useAdvancedRAG", true,
        "expansionLevel", 3,
        "rerankingStrategy", "semantic"
    ))
    .build();
```

**Result**: This specific intent uses advanced RAG.

---

### Disable Advanced RAG

```yaml
ai:
  service:
    features:
      enable-advanced-rag: false
```

**Result**: Advanced RAG never used, always falls back to basic.

---

## Error Handling

### AdvancedRAGService Not Available

**Behavior**: Falls back to basic `RAGService`.

**Code**:
```java
if (useAdvanced && advancedRAGService != null) {
    return handleInformationAdvanced(...);
}
// Falls through to basic RAG
```

---

### AdvancedRAGService Fails

**Behavior**: Log error, fall back to basic RAG.

**Code**:
```java
private OrchestrationResult handleInformationAdvanced(...) {
    try {
        AdvancedRAGResponse response = advancedRAGService.performAdvancedRAG(request);
        // ... process response
    } catch (Exception e) {
        log.error("Advanced RAG failed, falling back to basic RAG", e);
        // Fall back to basic
        return handleInformationBasic(intent, context, pipelineContext);
    }
}
```

---

### AICoreService Not Available

**Behavior**: If generation needed but `AICoreService` not available, return documents only.

**Code**:
```java
if (needsGeneration && aiCoreService != null) {
    response = generateRAGResponse(query, ragResponse.getContext(), pipelineContext);
} else if (needsGeneration) {
    log.warn("Generation requested but AICoreService not available");
    response = null;  // Return documents only
}
```

---

## Performance Considerations

### When to Use Advanced RAG

**Use Advanced RAG when**:
- ✅ Query complexity warrants expansion
- ✅ User explicitly requests advanced features
- ✅ Quality is more important than speed
- ✅ Multiple document types need re-ranking

**Use Basic RAG when**:
- ✅ Speed is critical
- ✅ Simple queries
- ✅ No LLM generation needed
- ✅ Cost concerns (avoid LLM calls)

---

### Caching Strategy

**Consideration**: Advanced RAG results can be cached.

**Implementation** (future enhancement):
```java
// Cache key: query + expansionLevel + rerankingStrategy + entityType
String cacheKey = buildCacheKey(query, expansionLevel, rerankingStrategy, entityType);
AdvancedRAGResponse cached = cache.get(cacheKey);
if (cached != null) {
    return cached;
}
```

---

## Monitoring and Observability

### Metrics to Track

1. **Advanced RAG Usage**:
   - Count of advanced RAG requests
   - Count of basic RAG requests
   - Ratio of advanced vs basic

2. **Performance**:
   - Average processing time (advanced vs basic)
   - P95/P99 latency

3. **Quality**:
   - Confidence scores
   - User satisfaction (if available)

4. **Errors**:
   - Advanced RAG failures
   - Fallback frequency

---

### Logging

**Add structured logging**:
```java
log.info("Using {} RAG for query: {}", 
    useAdvanced ? "Advanced" : "Basic", 
    query);

if (useAdvanced) {
    log.debug("Advanced RAG request: expansionLevel={}, rerankingStrategy={}, contextOptimization={}",
        expansionLevel, rerankingStrategy, contextOptimizationLevel);
}
```

---

## Rollout Strategy

### Phase 1: Feature Flag (Week 1)

**Goal**: Deploy code with feature disabled.

**Actions**:
1. Deploy code changes
2. Set `enable-advanced-rag: false` in config
3. Verify no behavior changes
4. Monitor for errors

---

### Phase 2: Opt-In Testing (Week 2)

**Goal**: Test with specific intents.

**Actions**:
1. Enable for specific test intents only
2. Monitor performance and quality
3. Gather feedback
4. Fix issues

---

### Phase 3: Auto-Enable for Complex Queries (Week 3)

**Goal**: Automatically use for complex queries.

**Actions**:
1. Enable `auto-enable-advanced-rag-for-complex-queries: true`
2. Monitor usage patterns
3. Adjust complexity thresholds if needed

---

### Phase 4: Full Rollout (Week 4)

**Goal**: Enable globally.

**Actions**:
1. Enable `enable-advanced-rag: true`
2. Monitor all metrics
3. Optimize based on data

---

## Backward Compatibility

### Guarantees

1. ✅ **Existing code continues to work** - Basic RAG still available
2. ✅ **No breaking changes** - All existing APIs unchanged
3. ✅ **Optional feature** - Can be disabled via config
4. ✅ **Graceful degradation** - Falls back if advanced not available

---

### Migration Path for Applications

**Applications using orchestrator**:
- ✅ No changes needed - works automatically
- ✅ Can opt-in via configuration
- ✅ Can opt-in via intent metadata

**Applications using AdvancedRAGService directly**:
- ✅ No changes needed - still works
- ✅ Can also use through orchestrator now

---

## Success Criteria

### Functional

- [ ] Advanced RAG used when `useAdvancedRAG=true` in intent metadata
- [ ] Advanced RAG used for complex queries when auto-enable is on
- [ ] Basic RAG used when advanced not available
- [ ] Basic RAG used when explicitly disabled
- [ ] Expanded queries included in response
- [ ] Re-ranked documents included in response
- [ ] Confidence scores included in response
- [ ] Error handling works correctly

---

### Performance

- [ ] Advanced RAG latency acceptable (<2s for complex queries)
- [ ] Basic RAG latency unchanged
- [ ] No memory leaks
- [ ] Caching works (if implemented)

---

### Quality

- [ ] Advanced RAG provides better results than basic
- [ ] User satisfaction improves (if measurable)
- [ ] Error rate acceptable (<1%)

---

## Risks and Mitigation

### Risk 1: Performance Impact

**Risk**: Advanced RAG slower than basic.

**Mitigation**:
- Use only for complex queries
- Cache results
- Monitor and optimize
- Allow users to disable

---

### Risk 2: Cost Increase

**Risk**: LLM calls for query expansion/optimization increase costs.

**Mitigation**:
- Make it opt-in
- Use only when needed
- Cache aggressively
- Monitor costs

---

### Risk 3: Complexity

**Risk**: More complex code, harder to maintain.

**Mitigation**:
- Clear separation of concerns
- Good documentation
- Comprehensive tests
- Code reviews

---

## Future Enhancements

### Enhancement 1: Adaptive Selection

**Idea**: Automatically choose advanced vs basic based on query characteristics.

**Implementation**: ML model or rule-based system to predict when advanced RAG helps.

---

### Enhancement 2: Hybrid Approach

**Idea**: Use basic RAG first, then enhance with advanced features if needed.

**Implementation**: 
1. Try basic RAG
2. If confidence low, enhance with advanced features
3. Combine results

---

### Enhancement 3: User Learning

**Idea**: Learn user preferences over time.

**Implementation**: Track which users benefit from advanced RAG, auto-enable for them.

---

## Conclusion

This implementation plan provides a clear path to integrate `AdvancedRAGService` with the orchestrator while maintaining backward compatibility and flexibility. The approach follows the framework's existing patterns and allows gradual rollout.

**Key Benefits**:
- ✅ Leverages advanced RAG features in orchestrator
- ✅ Maintains backward compatibility
- ✅ Flexible configuration
- ✅ Graceful degradation
- ✅ Clear separation of concerns

**Next Steps**:
1. Review and approve plan
2. Implement Phase 1 (add dependencies)
3. Implement Phase 2 (decision logic)
4. Implement Phase 3 (advanced handler)
5. Test and iterate
6. Rollout gradually

