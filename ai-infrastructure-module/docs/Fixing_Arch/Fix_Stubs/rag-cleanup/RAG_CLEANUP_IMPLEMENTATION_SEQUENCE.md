# RAG Cleanup Implementation Sequence Plan

## Executive Summary

**Objective**: Implement three related RAG cleanup changes with minimal conflicts in a greenfield project.

**Context**: This is a **greenfield project** - no backward compatibility concerns. We can make clean, direct changes without maintaining legacy behavior.

**Changes to Implement**:
1. **Extract LLM Generation from RAGService** (RAG_LLM_GENERATION_ANALYSIS.md)
2. **Integrate AdvancedRAGService with Orchestrator** (ADVANCED_RAG_ORCHESTRATOR_INTEGRATION_PLAN.md)
3. **Keep RAGService and AdvancedRAGService Separate** (RAG_SERVICE_MERGE_ANALYSIS.md) - No code changes, just context

**Recommended Sequence**: 3 phases, implemented sequentially to minimize conflicts.

---

## Conflict Analysis

### Files That Will Be Modified

| File | Change 1 (LLM Extraction) | Change 2 (Advanced Integration) | Conflict Risk |
|------|---------------------------|----------------------------------|----------------|
| `RAGService.java` | ✅ Remove `generateResponse()` | ❌ No changes | **LOW** - Independent |
| `IntentHandlingStep.java` | ✅ Add generation logic | ✅ Add AdvancedRAGService integration | **HIGH** - Both modify same method |
| `RAGResponse.java` | ✅ Make `response` optional | ❌ No changes | **LOW** - Independent |
| `AIServiceConfig.java` | ❌ No changes | ✅ Add feature flags | **LOW** - Independent |

### Dependency Graph

```
Phase 1: RAGService Refactoring
    ↓
Phase 2: Orchestrator Generation
    ↓
Phase 3: AdvancedRAGService Integration
```

**Rationale**: Each phase builds on the previous, with minimal overlap.

---

## Phase 1: Make RAGService Retrieval-Only

**Source**: `RAG_LLM_GENERATION_ANALYSIS.md` - Phase 1

**Goal**: Remove LLM generation from `RAGService`, make it retrieval-only.

**Impact**: **LOW** - Changes are isolated to `RAGService`. Since this is greenfield, we can make clean changes.

**Files Modified**:
- `RAGService.java`
- `RAGResponse.java` (remove response field entirely)
- `RAGProvider.java` (update interface if needed)
- Tests

**Changes**:

### Step 1.1: Remove response Field from RAGResponse

**File**: `RAGResponse.java`

**Change**: Remove `response` field entirely (not needed for retrieval-only service).

```java
// Before
@Builder
@Data
public class RAGResponse {
    private String response;  // ← Remove this
    private String context;
    private List<RAGDocument> documents;
    // ... other fields
}

// After
@Builder
@Data
public class RAGResponse {
    // response field removed - generation happens in orchestrator
    private String context;
    private List<RAGDocument> documents;
    // ... other fields
}
```

**Impact**: Low - clean removal, no nullable fields needed.

**Testing**: Update all tests that reference `response` field.

---

### Step 1.2: Remove generateResponse() from RAGService

**File**: `RAGService.java`

**Change**: Remove `generateResponse()` method entirely, simplify `performRAGQuery()` to be retrieval-only.

```java
// Before
@Override
public RAGResponse performRAGQuery(RAGRequest request) {
    // ... retrieval logic ...
    String context = buildContext(searchResponse);
    String response = generateResponse(processedQuery, context);  // ← Remove this
    
    return RAGResponse.builder()
        .response(response)  // ← Remove this
        .context(context)
        .documents(...)
        .build();
}

// After
@Override
public RAGResponse performRAGQuery(RAGRequest request) {
    // ... retrieval logic ...
    String context = buildContext(searchResponse);
    
    return RAGResponse.builder()
        .context(context)  // ← Keep context for orchestrator
        .documents(convertToRAGDocuments(searchResponse.getResults()))
        .totalDocuments(searchResponse.getTotalResults())
        .processingTimeMs(processingTime)
        // ... other fields
        .build();
}

// Remove this method entirely - no longer needed
// private String generateResponse(String query, String context) { ... }
```

**Impact**: Medium - clean removal, simplifies service.

**Testing**: 
- Verify `performRAGQuery()` returns valid `RAGResponse` without response field
- Verify `context` field is populated
- Verify all retrieval logic works correctly

---

### Step 1.3: Update JavaDoc

**File**: `RAGService.java`

**Change**: Update documentation to clarify retrieval-only responsibility.

```java
/**
 * RAG Service - Retrieval-Only Implementation
 * 
 * <p>This service focuses on retrieval operations only. It does NOT perform
 * LLM generation. For generation, use the orchestrator or AdvancedRAGService.</p>
 * 
 * <p><strong>Responsibilities:</strong></p>
 * <ul>
 *   <li>Content indexing</li>
 *   <li>Vector search and retrieval</li>
 *   <li>Document retrieval</li>
 * </ul>
 * 
 * <p><strong>Generation:</strong> LLM generation is handled by the orchestrator
 * ({@link IntentHandlingStep}) or by {@link AdvancedRAGService}.</p>
 */
```

**Impact**: Low - documentation only.

---

### Step 1.4: Update Tests

**File**: `RAGServiceTest.java`

**Changes**:
- Update tests to expect `null` response from `performRAGQuery()`
- Verify context is still populated
- Remove tests for `generateResponse()` method

**Impact**: Medium - test updates required.

---

### Phase 1 Completion Criteria

- [ ] `RAGResponse.response` field removed entirely
- [ ] `RAGService.generateResponse()` method removed
- [ ] `RAGService.performRAGQuery()` returns retrieval-only response
- [ ] `RAGService.performRAGQuery()` returns context
- [ ] All tests updated and passing
- [ ] JavaDoc updated

**Note**: No rollback needed - this is greenfield, we can fix issues directly.

---

## Phase 2: Add Generation to Orchestrator

**Source**: `RAG_LLM_GENERATION_ANALYSIS.md` - Phase 2

**Goal**: Add LLM generation to `IntentHandlingStep` orchestrator.

**Impact**: **MEDIUM** - Changes orchestrator behavior, but builds on Phase 1.

**Files Modified**:
- `IntentHandlingStep.java`
- Tests

**Prerequisites**: Phase 1 must be complete.

---

### Step 2.1: Add AICoreService Dependency

**File**: `IntentHandlingStep.java`

**Change**: Add `AICoreService` as required dependency (greenfield - no need for optional).

```java
@RequiredArgsConstructor
public class IntentHandlingStep implements PipelineStep {
    
    // Existing dependencies
    private final RAGProvider ragProvider;
    private final ActionHandlerRegistry actionHandlerRegistry;
    
    // NEW: Add AICoreService (required - greenfield project)
    private final AICoreService aiCoreService;
}
```

**Impact**: Low - required dependency, cleaner design.

**Testing**: Verify dependency injection works correctly.

---

### Step 2.2: Add Generation Methods

**File**: `IntentHandlingStep.java`

**Change**: Add methods for LLM generation.

```java
/**
 * Generate RAG response using LLM (in orchestrator).
 * 
 * @param query the user query
 * @param context the retrieved context
 * @param pipelineContext the pipeline context
 * @return generated response
 * @throws AIServiceException if generation fails
 */
private String generateRAGResponse(String query, String context, PipelineContext pipelineContext) {
    if (StringUtils.isEmpty(context) || "No relevant context found.".equals(context)) {
        return "I don't have enough information to answer your question: " + query;
    }
    
    String prompt = buildRAGPrompt(query, context);
    
    // AICoreService is required dependency - no null check needed
    return aiCoreService.generateText(prompt);
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

**Impact**: Low - new methods, doesn't change existing flow yet.

**Testing**: Unit test generation methods.

---

### Step 2.3: Update handleInformation() Method

**File**: `IntentHandlingStep.java`

**Change**: Update to generate response in orchestrator.

```java
// Before
private OrchestrationResult handleInformation(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
    boolean needsGeneration = intent.requiresGenerationOrDefault(false);
    // ...
    
    RAGResponse ragResponse = needsGeneration
        ? ragProvider.performRAGQuery(ragRequest)  // ← Expected generation, but doesn't happen
        : ragProvider.performRag(ragRequest);
    
    Map<String, Object> data = new LinkedHashMap<>();
    data.put(DATA_KEY_ANSWER, ragResponse.getResponse());  // ← Gets null from Phase 1
    // ...
}

// After
private OrchestrationResult handleInformation(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
    boolean needsGeneration = intent.requiresGenerationOrDefault(false);
    // ...
    
    // Always do retrieval (Phase 1 made RAGService retrieval-only)
    RAGResponse ragResponse = ragProvider.performRag(ragRequest);
    
    // Generate response if needed (NEW - in orchestrator)
    String response = null;
    if (needsGeneration) {
        // AICoreService is required - no null check needed
        response = generateRAGResponse(query, ragResponse.getContext(), pipelineContext);
    }
    
    Map<String, Object> data = new LinkedHashMap<>();
    data.put(DATA_KEY_ANSWER, response);  // ← Generated in orchestrator
    data.put(DATA_KEY_DOCUMENTS, ragResponse.getDocuments());
    data.put(DATA_KEY_RAG_RESPONSE, ragResponse);
    data.put(DATA_KEY_REQUIRES_GENERATION, needsGeneration);
    // ...
}
```

**Impact**: Medium - changes behavior, but improves consistency.

**Testing**:
- Verify generation works when `needsGeneration=true` and `AICoreService` available
- Verify fallback when `AICoreService` not available
- Verify no generation when `needsGeneration=false`

---

### Step 2.4: Add Helper Methods

**File**: `IntentHandlingStep.java`

**Change**: Add helper methods for query resolution and metadata building (if not already present).

```java
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
```

**Impact**: Low - helper methods, improves code organization.

**Testing**: Unit test helper methods.

---

### Phase 2 Completion Criteria

- [ ] `AICoreService` dependency added to `IntentHandlingStep` (required)
- [ ] `generateRAGResponse()` method added
- [ ] `buildRAGPrompt()` method added
- [ ] `handleInformation()` updated to generate in orchestrator
- [ ] Helper methods added (if needed)
- [ ] All tests updated and passing
- [ ] New tests for generation added

**Note**: No rollback needed - this is greenfield, we can fix issues directly.

---

## Phase 3: Integrate AdvancedRAGService with Orchestrator

**Source**: `ADVANCED_RAG_ORCHESTRATOR_INTEGRATION_PLAN.md`

**Goal**: Add optional `AdvancedRAGService` integration to orchestrator.

**Impact**: **MEDIUM** - Adds optional features, builds on Phase 2.

**Files Modified**:
- `IntentHandlingStep.java`
- `AIServiceConfig.java`
- Tests

**Prerequisites**: Phase 2 must be complete.

---

### Step 3.1: Add AdvancedRAGService Dependency

**File**: `IntentHandlingStep.java`

**Change**: Add `AdvancedRAGService` as required dependency (greenfield - can be required if always available, or use conditional bean).

```java
@RequiredArgsConstructor
public class IntentHandlingStep implements PipelineStep {
    
    // Existing dependencies
    private final RAGProvider ragProvider;
    private final ActionHandlerRegistry actionHandlerRegistry;
    private final AIServiceConfig serviceConfig;
    private final AICoreService aiCoreService;  // ← From Phase 2
    
    // NEW: Add AdvancedRAGService (required if enabled via config, otherwise use @ConditionalOnBean)
    // Option 1: Required (if always available)
    private final AdvancedRAGService advancedRAGService;
    
    // Option 2: Conditional (if optional feature)
    // Use Spring's @ConditionalOnBean or make it optional via configuration
    // @Autowired(required = false)
    // private AdvancedRAGService advancedRAGService;
}
```

**Impact**: Low - cleaner design, can make required if always available.

**Testing**: Verify dependency injection works correctly.

---

### Step 3.2: Add Configuration Support

**File**: `AIServiceConfig.java`

**Change**: Add feature flags for advanced RAG.

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

**Impact**: Low - adds configuration, doesn't change behavior.

**Testing**: Verify configuration loads correctly.

---

### Step 3.3: Add Decision Logic

**File**: `IntentHandlingStep.java`

**Change**: Add method to decide when to use AdvancedRAGService.

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
    // If using required dependency, this check is not needed
    // If using optional dependency, keep this check
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
    if (serviceConfig != null && serviceConfig.getFeatures() != null) {
        Boolean autoEnable = serviceConfig.getFeatures().getAutoEnableAdvancedRAGForComplexQueries();
        return Boolean.TRUE.equals(autoEnable);
    }
    return false;  // Default: false (conservative)
}
```

**Impact**: Low - new method, doesn't change existing flow yet.

**Testing**: Unit test decision logic with various scenarios.

---

### Step 3.4: Add Advanced RAG Handler

**File**: `IntentHandlingStep.java`

**Change**: Add method to handle advanced RAG.

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
```

**Impact**: Medium - new code path, but optional.

**Testing**: Unit test and integration test.

---

### Step 3.5: Add Helper Methods

**File**: `IntentHandlingStep.java`

**Change**: Add helper methods for building requests and converting responses.

```java
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
    Integer expansionLevel = extractInteger(intentMetadata, "expansionLevel", 
        serviceConfig != null && serviceConfig.getFeatures() != null
            ? serviceConfig.getFeatures().getDefaultExpansionLevel()
            : 2);
    String rerankingStrategy = extractString(intentMetadata, "rerankingStrategy",
        serviceConfig != null && serviceConfig.getFeatures() != null
            ? serviceConfig.getFeatures().getDefaultRerankingStrategy()
            : "hybrid");
    String contextOptimizationLevel = extractString(intentMetadata, "contextOptimizationLevel",
        serviceConfig != null && serviceConfig.getFeatures() != null
            ? serviceConfig.getFeatures().getDefaultContextOptimizationLevel()
            : "medium");
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

// Add helper methods: extractInteger, extractString, extractBoolean, buildUserContext, extractFilters, extractCategories
// (See ADVANCED_RAG_ORCHESTRATOR_INTEGRATION_PLAN.md for full implementations)

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

**Impact**: Medium - adds complexity, but well-organized.

**Testing**: Unit test all helper methods.

---

### Step 3.6: Update handleInformation() Method

**File**: `IntentHandlingStep.java`

**Change**: Add conditional logic to route to advanced or basic RAG.

```java
// Update existing handleInformation() method
private OrchestrationResult handleInformation(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
    boolean needsGeneration = intent.requiresGenerationOrDefault(false);
    boolean useAdvanced = shouldUseAdvancedRAG(intent, context);  // ← NEW
    
    // Route to advanced or basic handler
    // If AdvancedRAGService is required, remove null check
    if (useAdvanced) {
        // Use AdvancedRAGService
        return handleInformationAdvanced(intent, context, pipelineContext);  // ← NEW path
    }
    
    // Use basic RAGService (existing logic from Phase 2)
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
    
    // Always do retrieval (Phase 1 made RAGService retrieval-only)
    RAGResponse ragResponse = ragProvider.performRag(ragRequest);
    
    // Generate response if needed (Phase 2 added this)
    String response = null;
    if (needsGeneration) {
        // AICoreService is required - no null check needed
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
```

**Impact**: Medium - adds conditional routing, but maintains backward compatibility.

**Testing**:
- Verify advanced path works when conditions met
- Verify basic path still works (existing behavior)
- Verify fallback when advanced not available

---

### Step 3.7: Add Constants

**File**: `IntentHandlingStep.java`

**Change**: Add constants for advanced RAG data keys.

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

**Impact**: Low - adds constants.

**Testing**: N/A.

---

### Phase 3 Completion Criteria

- [ ] `AdvancedRAGService` dependency added to `IntentHandlingStep`
- [ ] `AIServiceConfig` feature flags added
- [ ] `shouldUseAdvancedRAG()` method added
- [ ] `handleInformationAdvanced()` method added
- [ ] `buildAdvancedRAGRequest()` method added
- [ ] Conversion methods added
- [ ] `handleInformation()` updated with conditional routing
- [ ] Constants added
- [ ] All tests updated and passing
- [ ] New tests for advanced path added

**Note**: No rollback needed - this is greenfield, we can fix issues directly. Can disable via configuration if needed.

---

## Implementation Timeline

### Week 1: Phase 1 (RAGService Refactoring)

**Days 1-2**: Remove `RAGResponse.response` field
- Update `RAGResponse.java` (remove field entirely)
- Update all references to response field
- Update tests

**Days 3-4**: Remove generation from `RAGService`
- Remove `generateResponse()` method
- Update `performRAGQuery()` implementation
- Update tests
- Update JavaDoc

**Day 5**: Testing and validation
- Run full test suite
- Integration testing
- Code review

---

### Week 2: Phase 2 (Orchestrator Generation)

**Days 1-2**: Add generation infrastructure
- Add `AICoreService` dependency
- Add `generateRAGResponse()` method
- Add `buildRAGPrompt()` method
- Add helper methods
- Unit tests

**Days 3-4**: Update orchestrator logic
- Update `handleInformation()` method
- Integration with Phase 1 changes
- Update tests

**Day 5**: Testing and validation
- Run full test suite
- Integration testing
- Code review

---

### Week 3: Phase 3 (AdvancedRAGService Integration)

**Days 1-2**: Add dependencies and configuration
- Add `AdvancedRAGService` dependency
- Add `AIServiceConfig` feature flags
- Add configuration properties
- Unit tests

**Days 3-4**: Add advanced RAG logic
- Add `shouldUseAdvancedRAG()` method
- Add `handleInformationAdvanced()` method
- Add helper methods
- Update `handleInformation()` with routing
- Unit tests

**Day 5**: Testing and validation
- Run full test suite
- Integration testing
- Code review

---

## Conflict Resolution Strategy

### Conflict 1: Both Phases Modify handleInformation()

**Resolution**: 
- Phase 2 modifies `handleInformation()` first (adds generation)
- Phase 3 adds conditional routing at the start (checks advanced first)
- Minimal overlap - Phase 3 wraps Phase 2 logic

**Merge Strategy**:
```java
// Phase 2 result
private OrchestrationResult handleInformation(...) {
    // Basic RAG + generation
}

// Phase 3 result (wraps Phase 2)
private OrchestrationResult handleInformation(...) {
    if (useAdvanced && advancedRAGService != null) {
        return handleInformationAdvanced(...);  // New path
    }
    // Phase 2 logic here (unchanged)
}
```

---

### Conflict 2: Both Phases Add AICoreService Dependency

**Resolution**: 
- Phase 2 adds `AICoreService` for basic generation
- Phase 3 doesn't add it again (already exists)
- No conflict - Phase 3 uses existing dependency

---

### Conflict 3: Both Phases Touch RAGService

**Resolution**: 
- Phase 1 modifies `RAGService` (removes generation)
- Phase 3 doesn't modify `RAGService` (uses it as-is)
- No conflict - Phase 3 uses Phase 1 result

---

## Testing Strategy

### Phase 1 Testing

**Unit Tests**:
- `RAGService.performRAGQuery()` returns null response
- `RAGService.performRAGQuery()` returns context
- `RAGService.performRag()` unchanged

**Integration Tests**:
- Orchestrator works with retrieval-only RAGService
- Generation happens in orchestrator correctly

---

### Phase 2 Testing

**Unit Tests**:
- `generateRAGResponse()` generates response correctly
- `buildRAGPrompt()` builds correct prompt
- `handleInformation()` generates when needed
- `handleInformation()` doesn't generate when not needed

**Integration Tests**:
- Full orchestrator flow with generation
- Generation works correctly with required `AICoreService`

---

### Phase 3 Testing

**Unit Tests**:
- `shouldUseAdvancedRAG()` decision logic
- `handleInformationAdvanced()` calls `AdvancedRAGService`
- `buildAdvancedRAGRequest()` builds correct request
- Conversion methods work correctly
- `handleInformation()` routes correctly

**Integration Tests**:
- Advanced RAG path works end-to-end
- Basic RAG path still works
- Configuration flags work correctly
- Routing logic works correctly

---

## Issue Resolution (Greenfield Approach)

Since this is a greenfield project, we fix issues directly rather than rolling back:

### Phase 1 Issues

**If issues arise**:
1. Fix the issue directly in code
2. Update tests to match new behavior
3. No need to restore old code

**Approach**: Direct fixes, no rollback needed.

---

### Phase 2 Issues

**If issues arise**:
1. Fix generation logic directly
2. Update tests
3. Ensure `AICoreService` is properly configured

**Approach**: Direct fixes, no rollback needed.

---

### Phase 3 Issues

**If issues arise**:
1. Fix advanced RAG logic directly
2. Update configuration if needed
3. Update tests
4. Can disable via configuration if feature not ready

**Approach**: Direct fixes or feature flag, no rollback needed.

---

## Success Criteria

### Phase 1 Success

- [ ] `RAGService` is retrieval-only
- [ ] `RAGResponse.response` field removed
- [ ] All tests updated and passing
- [ ] Documentation updated

---

### Phase 2 Success

- [ ] Orchestrator generates responses using LLM
- [ ] Generation works when `needsGeneration=true`
- [ ] `AICoreService` is required dependency (properly configured)
- [ ] All tests updated and passing
- [ ] Performance acceptable

---

### Phase 3 Success

- [ ] Advanced RAG integrated with orchestrator
- [ ] Conditional routing works correctly
- [ ] Configuration flags work
- [ ] Basic RAG still works
- [ ] All tests pass
- [ ] Performance acceptable

---

## Risk Mitigation

### Risk 1: Breaking Changes

**Mitigation** (Greenfield approach):
- Make clean, direct changes
- Use required dependencies where appropriate
- Fix issues directly (no rollback needed)
- Comprehensive testing

---

### Risk 2: Performance Impact

**Mitigation**:
- Phase 1: No performance impact (removes code)
- Phase 2: Generation already happens (moves location)
- Phase 3: Optional feature, only used when needed
- Monitor performance metrics

---

### Risk 3: Complexity Increase

**Mitigation**:
- Clear separation of concerns
- Well-documented code
- Comprehensive tests
- Code reviews

---

## Conclusion

This implementation sequence minimizes conflicts by:

1. **Sequential Phases**: Each phase builds on the previous
2. **Isolated Changes**: Phase 1 is independent
3. **Clean Design**: Greenfield allows direct, clean changes
4. **Required Dependencies**: No optional/nullable fields needed
5. **Direct Fixes**: Issues fixed directly, no rollback needed

**Recommended Approach**: Implement phases sequentially, with thorough testing between phases.

**Estimated Timeline**: 3 weeks (1 week per phase)

**Risk Level**: **LOW** - Well-planned sequence with minimal conflicts. Greenfield project allows cleaner implementation.

**Key Benefits of Greenfield Approach**:
- ✅ No backward compatibility constraints
- ✅ Cleaner code (no nullable/optional fields)
- ✅ Required dependencies (better design)
- ✅ Direct fixes (no rollback complexity)
- ✅ Simpler implementation

