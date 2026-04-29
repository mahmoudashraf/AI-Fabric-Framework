# Relationship Query ↔ Orchestrator Integration Guide

## 📋 Executive Summary

**Goal:** Support dual usage patterns for the Relationship Query module - both standalone (direct service injection) and orchestrated (via RAGOrchestrator) - allowing users to choose based on their needs while avoiding architectural complexity.

**Current State:** 
- ✅ Relationship Query module works standalone via `ReliableRelationshipQueryService`
- ✅ **ALREADY has hybrid vector+relational capability** built-in
- ✅ Multi-level fallback chain: LLM → JPQL → Vector → Repository
- ❌ NOT integrated with RAGOrchestrator (no orchestration benefits)

**Target State:** 
- ✅ Keep standalone usage (simple apps) - **Already works**
- ✅ Add orchestrator integration (enterprise apps) - **New feature**
- ✅ Use existing ActionHandler SPI (no custom SPI needed)
- ✅ Let users choose their pattern

**Key Decision:** Use existing `ActionHandler` interface (which IS already an SPI pattern) instead of creating custom SPI.

**Important Note:** The relationship query module ALREADY implements sophisticated hybrid search combining vector similarity, relational database queries, and LLM-driven intent understanding. This document focuses on integrating that existing capability with the orchestrator.

## ⚠️ CRITICAL IMPLEMENTATION REQUIREMENTS

**Before implementing the handler, these MUST be completed:**

1. **🔴 CRITICAL: Enhance IntentQueryExtractor** (Phase 0.5)
   - Update LLM system prompt to extract entity types for relationship queries
   - Add validation to ensure entityTypes are in actionParams
   - **Without this, the optimization fails** (redundant LLM calls)

2. **🔴 CRITICAL: Use Correct ActionHandler Interface**
   - Method: `executeAction(Map<String, Object> params, String userId)` - **params first, userId second**
   - Required methods: `getActionMetadata()`, `handleError()`, `validateActionAllowed()`, `getConfirmationMessage()`
   - **NOT** `execute(userId, params)` - that method doesn't exist

3. **🟡 IMPORTANT: Verify actionParams Structure**
   - Test that `List<String>` for entityTypes serializes/deserializes correctly
   - Verify Jackson handles nested structures in actionParams

**See Phase 0.5 and Phase 1.2 for detailed implementation steps.**

---

## 🎯 Design Principles

1. **Dual Pattern Support** - Both direct and orchestrated usage work
2. **No Breaking Changes** - Existing users unaffected
3. **Leverage Existing SPI** - ActionHandler is already an SPI pattern
4. **Optional Integration** - Configurable via properties
5. **No Circular Dependencies** - Clean architecture maintained
6. **Clear Documentation** - Users know when to use each pattern

---

## 🏗️ Architecture Overview

### Current Architecture (Standalone Only)

```
┌─────────────────────────────────────────────────────────┐
│  User Application                                       │
│  @Autowired                                             │
│  ReliableRelationshipQueryService                       │
└────────────┬────────────────────────────────────────────┘
             │
             │ direct call
             ▼
┌─────────────────────────────────────────────────────────┐
│  ai-infrastructure-relationship-query                   │
│                                                         │
│  ReliableRelationshipQueryService (Hybrid Orchestrator) │
│    ├─→ LLMDrivenJPAQueryService (relational queries)   │
│    ├─→ VectorDatabaseService (semantic search) ✅       │
│    ├─→ RelationshipTraversalService (metadata) ✅       │
│    └─→ AISearchableEntityRepository (fallback) ✅       │
│                                                         │
│  Multi-Level Fallback Chain:                           │
│    1. LLM → JPQL query (relational)                    │
│    2. Metadata traversal fallback                      │
│    3. Vector search fallback (semantic) ✅              │
│    4. Simple repository lookup                         │
└─────────────────────────────────────────────────────────┘
```

**Characteristics:**
- ✅ Simple and direct
- ✅ Full control over QueryOptions
- ✅ **ALREADY has hybrid vector+relational** (built-in!)
- ✅ **Intelligent fallback chain** (4 levels)
- ✅ **LLM-driven query planning**
- ❌ No behavior insights (not integrated with orchestrator)
- ❌ No PII detection (not integrated with orchestrator)
- ❌ No access control (not integrated with orchestrator)
- ❌ No unified entry point

---

### New Architecture (Dual Pattern)

```
┌──────────────────────────────────────────────────────────┐
│  User Application                                        │
│                                                          │
│  Pattern 1: Direct                                       │
│  @Autowired ReliableRelationshipQueryService ──┐        │
│                                                 │        │
│  Pattern 2: Orchestrated                       │        │
│  @Autowired RAGOrchestrator ──────────┐        │        │
└────────────────────────────────────────┼────────┼────────┘
                                         │        │
                          ┌──────────────┘        │
                          │                       │
                          ▼                       │
         ┌─────────────────────────────────┐     │
         │  ai-infrastructure-core         │     │
         │  - RAGOrchestrator              │     │
         │  - ActionHandlerRegistry ◄──────┼─────┤
         │  - ActionHandler (SPI interface)│     │
         └────────┬────────────────────────┘     │
                  │                              │
                  │ discovers & delegates        │
                  ▼                              │
         ┌─────────────────────────────────┐    │
         │  ai-infrastructure-relationship │◄───┘
         │                                 │
         │  - ReliableRelationshipQuery... │ ← Direct usage
         │  - RelationshipQueryAction...   │ ← Orchestrated usage
         │    (implements ActionHandler)   │
         └─────────────────────────────────┘
```

**Characteristics:**
- ✅ Two usage patterns supported
- ✅ No circular dependency (one-way: relationship → core)
- ✅ Uses existing ActionHandler SPI
- ✅ Behavior insights available (orchestrated mode)
- ✅ PII detection, access control (orchestrated mode)
- ✅ User chooses based on needs

---

## 🔍 Why ActionHandler IS Already an SPI

### What is SPI (Service Provider Interface)?

**Definition:** A pattern where:
1. **Interface defined in one module** (core)
2. **Implementation in another module** (relationship-query)
3. **Auto-discovery mechanism** (Spring dependency injection)
4. **Loose coupling** (core doesn't know about implementations)
5. **Optional providers** (core works without implementations)

### ActionHandler Meets ALL SPI Criteria ✅

#### 1. Interface Defined in Core

```java
// Location: ai-infrastructure-core
package com.ai.infrastructure.intent.action;

/**
 * SPI for action handling.
 * Implementations are auto-discovered by Spring.
 */
public interface ActionHandler {
    /**
     * @return metadata describing the action handled by this component.
     */
    AIActionMetaData getActionMetadata();
    
    /**
     * Validate whether the current user may perform the action.
     *
     * @param userId identifier for the current user (may be {@code null} for anonymous requests)
     * @return {@code true} when the action is allowed, otherwise {@code false}
     */
    boolean validateActionAllowed(String userId);
    
    /**
     * Resolve the confirmation message presented to the user prior to executing the action.
     *
     * @param params action parameters supplied by the intent extractor
     * @return confirmation message text
     */
    String getConfirmationMessage(Map<String, Object> params);
    
    /**
     * Execute the business logic associated with the action.
     *
     * @param params action parameters supplied by the intent extractor
     * @param userId identifier for the current user (may be {@code null} for anonymous requests)
     * @return structured result describing the outcome
     */
    ActionResult executeAction(Map<String, Object> params, String userId);
    
    /**
     * Fallback invoked when {@link #executeAction(Map, String)} raises an exception.
     *
     * @param e      error thrown during execution
     * @param userId identifier for the current user
     * @return structured error result
     */
    ActionResult handleError(Exception e, String userId);
}
```

#### 2. Auto-Discovery via Spring

```java
// Location: ai-infrastructure-core
@Component
public class ActionHandlerRegistry {
    
    private final Map<String, ActionHandler> handlers;
    
    /**
     * Spring auto-wires ALL ActionHandler implementations.
     * This is the SPI auto-discovery mechanism.
     */
    public ActionHandlerRegistry(List<ActionHandler> allHandlers) {
        this.handlers = allHandlers.stream()
            .filter(handler -> handler.getActionMetadata() != null)
            .filter(handler -> handler.getActionMetadata().getName() != null)
            .collect(Collectors.toMap(
                handler -> handler.getActionMetadata().getName(),
                Function.identity()
            ));
        
        log.info("Registered {} action handlers: {}", 
            handlers.size(), handlers.keySet());
    }
    
    public Optional<ActionHandler> findHandler(String actionName) {
        return Optional.ofNullable(handlers.get(actionName));
    }
}
```

#### 3. Implementation in Relationship Module

```java
// Location: ai-infrastructure-relationship-query
package com.ai.infrastructure.relationship.action;

import com.ai.infrastructure.intent.action.ActionHandler;

/**
 * Implements ActionHandler SPI for relationship queries.
 */
@Component
public class RelationshipQueryActionHandler implements ActionHandler {
    
    @Autowired
    private ReliableRelationshipQueryService queryService;
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("relationship_query")
            .description("Execute natural language queries against relational data")
            .category("data_query")
            .build();
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        // Implementation
    }
    
    @Override
    public ActionResult handleError(Exception e, String userId) {
        // Error handling
    }
    
    // ... other methods
}
```

#### 4. No Circular Dependency

```
Core Module:
- Defines: ActionHandler interface ✅
- Discovers: ALL implementations automatically ✅
- Uses: None directly (delegates via registry) ✅
- Imports from relationship module: NONE ✅

Relationship Module:
- Depends on: ai-infrastructure-core ✅
- Implements: ActionHandler interface ✅
- Registered automatically by Spring ✅

Result: One-way dependency (relationship → core) ✅
```

---

## 🔄 Comparison: Behavior SPI vs Relationship ActionHandler

| Aspect | Behavior Module | Relationship Module |
|--------|----------------|---------------------|
| **Integration Point** | During orchestration (context building) | After intent extraction (action handling) |
| **Interface** | Custom `BehaviorContextProvider` | Existing `ActionHandler` |
| **Why Custom SPI?** | Core needs to PULL behavior data early | Not needed - delegation happens naturally |
| **Discovery** | `ObjectProvider<BehaviorContextProvider>` | `List<ActionHandler>` (standard) |
| **Pattern** | Custom SPI (required) | Standard SPI (already exists) |
| **New Code Needed** | Yes (interface + DTO in core) | No (reuse existing) |

### Behavior Flow (Needs Custom SPI)

```
User Query 
  → Orchestrator.orchestrate()
  → SystemContextBuilder.buildContext()
  → ┌─────────────────────────────────────┐
  → │ NEED behavior insights HERE         │
  → │ BehaviorContextProvider.get()       │ ← Custom SPI
  → │ (must happen DURING context build)  │
  → └─────────────────────────────────────┘
  → Continue with enriched context
  → IntentQueryExtractor.extract()
  → Handle intent
```

**Why Custom SPI Needed:** Core needs to fetch behavior data BEFORE intent extraction. Would create circular dependency without SPI.

### Relationship Flow (Uses Existing ActionHandler)

```
User Query
  → Orchestrator.orchestrate()
  → SystemContextBuilder.buildContext()
  → IntentQueryExtractor.extract()
  → Intent { type: ACTION, action: "relationship_query" }
  → RAGOrchestrator.handleAction()
  → ActionHandlerRegistry.findHandler("relationship_query")
  → ┌─────────────────────────────────────┐
  → │ RelationshipQueryActionHandler      │ ← Existing SPI
  → │ executeAction()                      │
  → └─────────────────────────────────────┘
  → Return result
```

**Why ActionHandler Sufficient:** Relationship query happens AFTER intent extraction, during action handling. ActionHandler interface already exists and handles this perfectly.

---

## 📦 Implementation

### Phase 0: Enhance IntentQueryExtractor for Entity Type Extraction

**CRITICAL:** Before implementing the handler, we must ensure the orchestrator's LLM extracts entity types during intent analysis. This optimization avoids redundant LLM calls.

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/IntentQueryExtractor.java` or `EnrichedPromptBuilder.java`

**Enhancement Required:**

1. **Update System Prompt** to instruct LLM to extract entity types for relationship queries:

```java
// In EnrichedPromptBuilder.java - add to system prompt
"""
When the action is "relationship_query", you MUST extract the entity types from the query.

Example:
User: "Find premium customers who ordered this month"
Response:
{
  "type": "ACTION",
  "action": "relationship_query",
  "actionParams": {
    "query": "find premium customers who ordered this month",
    "entityTypes": ["customer", "order"],  // ← CRITICAL: Extract these
    "limit": 20
  }
}

Entity types should be:
- Extracted from the query context
- Listed as array of strings
- Lowercase, matching entity type names in your system
- Empty array [] if cannot be determined (fallback to auto-detection)
"""
```

2. **Add Validation** to ensure entityTypes are extracted:

```java
// In IntentQueryExtractor.java - add validation
private void validateRelationshipQueryIntent(Intent intent) {
    if ("relationship_query".equals(intent.getAction())) {
        Map<String, Object> params = intent.getActionParams();
        if (params == null || !params.containsKey("entityTypes")) {
            log.warn("Relationship query intent missing entityTypes - will use auto-detection");
            // Set empty list as fallback
            params = params != null ? params : new HashMap<>();
            params.put("entityTypes", Collections.emptyList());
            intent.setActionParams(params);
        }
    }
}
```

3. **Test Entity Type Extraction:**

```java
@Test
void shouldExtractEntityTypesForRelationshipQuery() {
    String query = "Find premium customers who ordered in December";
    MultiIntentResponse response = extractor.extract(query, context);
    
    Intent intent = response.getIntents().get(0);
    assertThat(intent.getAction()).isEqualTo("relationship_query");
    
    Map<String, Object> params = intent.getActionParams();
    @SuppressWarnings("unchecked")
    List<String> entityTypes = (List<String>) params.get("entityTypes");
    
    assertThat(entityTypes).contains("customer", "order");
}
```

**Deliverables:**
- [ ] Enhanced system prompt includes entity type extraction instructions
- [ ] Validation logic ensures entityTypes are present (or sets fallback)
- [ ] Unit tests verify entity type extraction
- [ ] Integration tests verify end-to-end flow
- [ ] **Verify actionParams supports List<String>** - Test JSON serialization/deserialization

**Impact:** This enhancement enables the optimization where entity types are extracted once during intent analysis and reused, avoiding redundant LLM calls.

**Verification Required:**
- ✅ `Intent.actionParams` is `Map<String, Object>` - supports nested structures
- ✅ JSON deserialization handles `List<String>` correctly
- ✅ Test with: `{"entityTypes": ["user", "order"]}` in actionParams
- ✅ Verify Jackson/ObjectMapper can deserialize List<String> from JSON

---

### Phase 1: Create RelationshipQueryActionHandler

**Location:** `ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/action/RelationshipQueryActionHandler.java`

```java
package com.ai.infrastructure.relationship.action;

import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.ActionHandler;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.AIActionMetaData;
import com.ai.infrastructure.relationship.dto.QueryOptions;
import com.ai.infrastructure.relationship.dto.ReturnMode;
import com.ai.infrastructure.relationship.dto.QueryMode;
import com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Action handler for relationship-aware natural language queries.
 * 
 * Integrates the Relationship Query module with the RAG Orchestrator,
 * allowing users to execute relationship queries through the unified
 * orchestration API.
 * 
 * This is automatically discovered by ActionHandlerRegistry when the
 * relationship query module is present.
 * 
 * Example usage via orchestrator:
 * Query: "Use relationship query to find premium customers who ordered this month"
 * Intent: { type: ACTION, action: "relationship_query", params: {...} }
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "ai.infrastructure.relationship.enable-orchestrator-integration",
    havingValue = "true",
    matchIfMissing = true  // Enabled by default
)
public class RelationshipQueryActionHandler implements ActionHandler {
    
    private final ReliableRelationshipQueryService queryService;
    
    @Override
    public String getActionName() {
        return "relationship_query";
    }
    
    @Override
    public ActionResult execute(String userId, Map<String, Object> params) {
        try {
            // Extract parameters
            String query = extractQuery(params);
            List<String> entityTypes = extractEntityTypes(params);
            QueryOptions options = buildQueryOptions(params);
            
            log.info("Executing relationship query for user: {} (entities: {})", 
                userId, entityTypes);
            log.debug("Query: {}", query);
            
            // Execute relationship query
            // IMPORTANT: Pass entityTypes to avoid redundant LLM call
            // The orchestrator's LLM already extracted these from the intent
            long startTime = System.currentTimeMillis();
            RAGResponse response = queryService.execute(query, entityTypes, options);
            long duration = System.currentTimeMillis() - startTime;
            
            log.info("Relationship query completed: {} results in {}ms", 
                response.getTotalResults(), duration);
            
            // Build success result
            return ActionResult.builder()
                .success(response.getSuccess())
                .message(formatSuccessMessage(response))
                .data(buildResultData(response))
                .build();
                
        } catch (IllegalArgumentException e) {
            log.warn("Invalid relationship query parameters: {}", e.getMessage());
            return ActionResult.builder()
                .success(false)
                .message("Invalid query parameters: " + e.getMessage())
                .data(Map.of("error", "INVALID_PARAMETERS"))
                .build();
                
        } catch (Exception e) {
            log.error("Relationship query execution failed", e);
            return handleError(e, userId);
        }
    }
    
    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Relationship query handler error for user: {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Query execution failed: " + e.getMessage())
            .data(Map.of("error", "EXECUTION_FAILED", "errorType", e.getClass().getSimpleName()))
            .build();
    }
    
    @Override
    public boolean validateActionAllowed(String userId) {
        // Could implement permission checking here
        // For now, allow all users with valid userId
        return userId != null && !userId.isBlank();
    }
    
    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String query = extractQuery(params);
        List<String> entityTypes = extractEntityTypes(params);
        return String.format("Executing relationship query on %s: \"%s\"", 
            entityTypes, query);
    }
    
    // ========== Parameter Extraction ==========
    
    private String extractQuery(Map<String, Object> params) {
        Object queryObj = params.get("query");
        if (queryObj == null || queryObj.toString().isBlank()) {
            throw new IllegalArgumentException("'query' parameter is required");
        }
        return queryObj.toString();
    }
    
    @SuppressWarnings("unchecked")
    private List<String> extractEntityTypes(Map<String, Object> params) {
        Object entityTypesObj = params.get("entityTypes");
        
        if (entityTypesObj == null) {
            throw new IllegalArgumentException("'entityTypes' parameter is required");
        }
        
        if (entityTypesObj instanceof List) {
            List<?> list = (List<?>) entityTypesObj;
            List<String> entityTypes = new ArrayList<>();
            for (Object item : list) {
                entityTypes.add(item.toString());
            }
            return entityTypes;
        }
        
        if (entityTypesObj instanceof String) {
            // Single entity type as string
            return List.of(entityTypesObj.toString());
        }
        
        throw new IllegalArgumentException(
            "'entityTypes' must be a List<String> or String"
        );
    }
    
    private QueryOptions buildQueryOptions(Map<String, Object> params) {
        QueryOptions.QueryOptionsBuilder builder = QueryOptions.builder();
        
        // Limit
        if (params.containsKey("limit")) {
            builder.limit(parseInteger(params.get("limit"), 20));
        }
        
        // Return mode
        if (params.containsKey("returnMode")) {
            builder.returnMode(parseReturnMode(params.get("returnMode")));
        }
        
        // Query mode
        if (params.containsKey("queryMode") || params.containsKey("forceMode")) {
            Object modeObj = params.getOrDefault("queryMode", params.get("forceMode"));
            builder.forceMode(parseQueryMode(modeObj));
        }
        
        // Similarity threshold
        if (params.containsKey("similarityThreshold")) {
            builder.similarityThreshold(
                parseDouble(params.get("similarityThreshold"), 0.7)
            );
        }
        
        return builder.build();
    }
    
    // ========== Type Converters ==========
    
    private int parseInteger(Object value, int defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value: {}, using default: {}", value, defaultValue);
            return defaultValue;
        }
    }
    
    private double parseDouble(Object value, double defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            log.warn("Invalid double value: {}, using default: {}", value, defaultValue);
            return defaultValue;
        }
    }
    
    private ReturnMode parseReturnMode(Object value) {
        if (value == null) return ReturnMode.IDS;
        String str = value.toString().toUpperCase();
        try {
            return ReturnMode.valueOf(str);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid return mode: {}, using default: IDS", value);
            return ReturnMode.IDS;
        }
    }
    
    private QueryMode parseQueryMode(Object value) {
        if (value == null) return null;
        String str = value.toString().toUpperCase();
        try {
            return QueryMode.valueOf(str);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid query mode: {}, using default: null (auto-detect)", value);
            return null;
        }
    }
    
    // ========== Result Formatting ==========
    
    private String formatSuccessMessage(RAGResponse response) {
        if (response.getTotalResults() == 0) {
            return "No results found";
        }
        
        String modeInfo = Boolean.TRUE.equals(response.getHybridSearchUsed()) 
            ? " (enhanced mode)" 
            : "";
            
        return String.format("Found %d result%s in %dms%s",
            response.getTotalResults(),
            response.getTotalResults() == 1 ? "" : "s",
            response.getProcessingTimeMs(),
            modeInfo
        );
    }
    
    private Map<String, Object> buildResultData(RAGResponse response) {
        Map<String, Object> data = new HashMap<>();
        
        // Core results
        data.put("documents", response.getDocuments());
        data.put("totalResults", response.getTotalResults());
        data.put("returnedResults", response.getReturnedResults());
        
        // Performance metrics
        data.put("processingTimeMs", response.getProcessingTimeMs());
        
        // Search metadata
        data.put("hybridSearchUsed", response.getHybridSearchUsed());
        data.put("confidenceScore", response.getConfidenceScore());
        
        // Entity info
        if (response.getEntityType() != null) {
            data.put("entityType", response.getEntityType());
        }
        
        // Warnings (if any)
        if (response.getWarnings() != null && !response.getWarnings().isEmpty()) {
            data.put("warnings", response.getWarnings());
        }
        
        // Additional metadata
        if (response.getMetadata() != null && !response.getMetadata().isEmpty()) {
            data.put("metadata", response.getMetadata());
        }
        
        return data;
    }
}
```

### Phase 2: Add Configuration Property

**Location:** `ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/config/RelationshipQueryProperties.java`

```java
@ConfigurationProperties(prefix = "ai.infrastructure.relationship")
@Data
public class RelationshipQueryProperties {
    
    // ... existing properties ...
    
    /**
     * Enable integration with RAG Orchestrator via ActionHandler.
     * When enabled, relationship queries can be executed through the orchestrator.
     * Default: true
     */
    private boolean enableOrchestratorIntegration = true;
}
```

### Phase 3: Unit Tests

**Location:** `ai-infrastructure-relationship-query/src/test/java/com/ai/infrastructure/relationship/action/RelationshipQueryActionHandlerTest.java`

```java
package com.ai.infrastructure.relationship.action;

import com.ai.infrastructure.dto.RAGResponse;
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.relationship.dto.QueryOptions;
import com.ai.infrastructure.relationship.dto.ReturnMode;
import com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RelationshipQueryActionHandlerTest {
    
    @Mock
    private ReliableRelationshipQueryService queryService;
    
    @InjectMocks
    private RelationshipQueryActionHandler handler;
    
    @BeforeEach
    void setUp() {
        // Setup common mocks
    }
    
    @Test
    void getActionMetadata_shouldReturnCorrectMetadata() {
        AIActionMetaData metadata = handler.getActionMetadata();
        assertThat(metadata.getName()).isEqualTo("relationship_query");
        assertThat(metadata.getCategory()).isEqualTo("data_query");
        assertThat(metadata.getParameters()).containsKey("query");
        assertThat(metadata.getParameters()).containsKey("entityTypes");
    }
    
    @Test
    void execute_withValidParams_shouldSucceed() {
        // Arrange
        Map<String, Object> params = Map.of(
            "query", "Find premium users",
            "entityTypes", List.of("user"),
            "limit", 20
        );
        
        RAGResponse mockResponse = RAGResponse.builder()
            .success(true)
            .totalResults(5)
            .processingTimeMs(150L)
            .hybridSearchUsed(false)
            .documents(List.of())
            .build();
        
        when(queryService.execute(anyString(), anyList(), any(QueryOptions.class)))
            .thenReturn(mockResponse);
        
        // Act
        ActionResult result = handler.executeAction(params, "user-123");
        
        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("Found 5 results");
        assertThat(result.getData()).containsKey("documents");
        assertThat(result.getData()).containsKey("totalResults");
        
        verify(queryService).execute(
            eq("Find premium users"),
            eq(List.of("user")),
            any(QueryOptions.class)
        );
    }
    
    @Test
    void execute_withMissingQuery_shouldFail() {
        // Arrange
        Map<String, Object> params = Map.of(
            "entityTypes", List.of("user")
            // query is missing
        );
        
        // Act
        ActionResult result = handler.executeAction(params, "user-123");
        
        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("query");
        assertThat(result.getMessage()).contains("required");
        
        verify(queryService, never()).execute(anyString(), anyList(), any());
    }
    
    @Test
    void execute_withMissingEntityTypes_shouldFail() {
        // Arrange
        Map<String, Object> params = Map.of(
            "query", "Find users"
            // entityTypes is missing
        );
        
        // Act
        ActionResult result = handler.executeAction(params, "user-123");
        
        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("entityTypes");
        assertThat(result.getMessage()).contains("required");
    }
    
    @Test
    void execute_withQueryOptions_shouldPassThrough() {
        // Arrange
        Map<String, Object> params = Map.of(
            "query", "Find users",
            "entityTypes", List.of("user"),
            "limit", 50,
            "returnMode", "FULL",
            "similarityThreshold", 0.8
        );
        
        RAGResponse mockResponse = RAGResponse.builder()
            .success(true)
            .totalResults(10)
            .build();
        
        when(queryService.execute(anyString(), anyList(), any(QueryOptions.class)))
            .thenReturn(mockResponse);
        
        // Act
        ActionResult result = handler.executeAction(params, "user-123");
        
        // Assert
        assertThat(result.isSuccess()).isTrue();
        
        // Verify QueryOptions were built correctly
        verify(queryService).execute(
            eq("Find users"),
            eq(List.of("user")),
            argThat(options -> 
                options.getLimit() == 50 &&
                options.getReturnMode() == ReturnMode.FULL &&
                options.getSimilarityThreshold() == 0.8
            )
        );
    }
    
    @Test
    void execute_withServiceException_shouldReturnFailure() {
        // Arrange
        Map<String, Object> params = Map.of(
            "query", "Find users",
            "entityTypes", List.of("user")
        );
        
        when(queryService.execute(anyString(), anyList(), any()))
            .thenThrow(new RuntimeException("Database error"));
        
        // Act
        ActionResult result = handler.executeAction(params, "user-123");
        
        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getMessage()).contains("failed");
        assertThat(result.getData()).containsEntry("error", "EXECUTION_FAILED");
    }
    
    @Test
    void validateActionAllowed_withValidUserId_shouldReturnTrue() {
        assertThat(handler.validateActionAllowed("user-123")).isTrue();
    }
    
    @Test
    void validateActionAllowed_withNullUserId_shouldReturnFalse() {
        assertThat(handler.validateActionAllowed(null)).isFalse();
    }
    
    @Test
    void validateActionAllowed_withBlankUserId_shouldReturnFalse() {
        assertThat(handler.validateActionAllowed("")).isFalse();
    }
    
    @Test
    void getConfirmationMessage_shouldIncludeQueryAndEntityTypes() {
        // Arrange
        Map<String, Object> params = Map.of(
            "query", "Find premium users",
            "entityTypes", List.of("user", "order")
        );
        
        // Act
        String message = handler.getConfirmationMessage(params);
        
        // Assert
        assertThat(message).contains("Find premium users");
        assertThat(message).contains("user");
        assertThat(message).contains("order");
    }
}
```

---

## 🎨 Usage Patterns

### Pattern 1: Direct (Standalone)

**Use When:**
- Simple apps focused on relationship queries
- Need full control over QueryOptions
- Don't need orchestrator features (behavior insights, PII, access control)
- Performance-critical paths (fewer layers)

**Example (Simple - Recommended for Direct Usage):**

```java
@Service
public class CustomerSearchService {
    
    @Autowired
    private ReliableRelationshipQueryService queryService;
    
    public List<Customer> findCustomers(String naturalLanguageQuery) {
        // ✅ SIMPLE: Just ask the question! No entity types needed.
        // LLM automatically determines entity types from the query.
        RAGResponse response = queryService.execute(naturalLanguageQuery);
        
        return convertToCustomers(response.getDocuments());
    }
    
    public List<Customer> findCustomersAdvanced(String naturalLanguageQuery) {
        // Advanced: With options (entity types still optional)
        RAGResponse response = queryService.execute(
            naturalLanguageQuery,
            QueryOptions.builder()
                .returnMode(ReturnMode.FULL)
                .limit(50)
                .build()
        );
        
        return convertToCustomers(response.getDocuments());
    }
    
    public List<Customer> findCustomersOptimized(String naturalLanguageQuery) {
        // Performance Optimized: Provide entity type hints
        // Useful for apps with 20+ entity types to reduce token usage
        RAGResponse response = queryService.execute(
            naturalLanguageQuery,
            List.of("customer"),  // Hint: only analyze customer schema
            QueryOptions.builder()
                .returnMode(ReturnMode.FULL)
                .limit(50)
                .build()
        );
        
        return convertToCustomers(response.getDocuments());
    }
}
```

**Key Point for Direct Usage:**
- ✅ **Default (95% of cases):** `queryService.execute(query)` - Simple, no entity types
- ⚡ **Performance tuning:** `queryService.execute(query, entityTypes, options)` - When needed

**Configuration:**

```yaml
ai:
  infrastructure:
    relationship:
      enabled: true
      # No orchestrator integration needed
```

---

### Pattern 2: Via Orchestrator (Enterprise)

**Use When:**
- Need behavior insights integration
- Need PII detection/redaction
- Need access control
- Unified entry point for all query types
- Enterprise compliance requirements
- Want consistent security/audit across all queries

**Example:**

```java
@RestController
@RequestMapping("/api/query")
public class UnifiedQueryController {
    
    @Autowired
    private RAGOrchestrator orchestrator;
    
    @PostMapping
    public ResponseEntity<OrchestrationResult> query(
        @RequestBody QueryRequest request,
        @RequestHeader("Authorization") String auth,
        HttpServletRequest httpRequest
    ) {
        String userId = extractUserId(auth);
        
        // Single entry point for ALL queries
        OrchestrationContext context = OrchestrationContext.builder()
            .userId(userId)
            .sessionId(httpRequest.getSession().getId())
            .locale(httpRequest.getLocale())
            .ipAddress(httpRequest.getRemoteAddr())
            .metadata(Map.of(
                "source", "api",
                "device", extractDevice(httpRequest)
            ))
            .build();
        
        // Orchestrator handles everything:
        // - PII detection
        // - Access control
        // - Behavior enrichment
        // - Intent extraction
        // - Delegating to appropriate handler
        OrchestrationResult result = orchestrator.orchestrate(
            request.getQuery(),
            context
        );
        
        return ResponseEntity.ok(result);
    }
}
```

**User Query:**

```
"Execute relationship query to find premium customers who ordered in December"
```

**Intent Extracted by LLM:**

```json
{
  "type": "ACTION",
  "action": "relationship_query",
  "actionParams": {
    "query": "find premium customers who ordered in December",
    "entityTypes": ["customer", "order"],  // ✅ LLM extracts entity types
    "limit": 20
  }
}
```

**Flow:**

```
1. User query arrives
2. OrchestrationContext built (userId, session, behavior insights)
3. Security check (PII detection, access control)
4. Intent extraction (LLM determines: ACTION = relationship_query)
   └─→ ✅ LLM ALSO extracts entity types from query
5. ActionHandlerRegistry.findHandler("relationship_query")
6. RelationshipQueryActionHandler.execute()
   └─→ ✅ Passes entityTypes to queryService (avoids redundant LLM call!)
7. queryService.execute(query, entityTypes, options)  
   └─→ Uses provided entityTypes (no re-analysis needed)
8. Results returned with full context
```

**Key Optimization:**
- ✅ Orchestrator's LLM extracts entity types during intent analysis
- ✅ Handler passes those entity types to relationship query service
- ✅ Saves an LLM call (no need to re-analyze for entity types)
- ✅ More efficient token usage (only relevant schemas sent)

**Configuration:**

```yaml
ai:
  infrastructure:
    relationship:
      enabled: true
      enable-orchestrator-integration: true  # Enable action handler
```

---

## 📊 Pattern Comparison

| Feature | Direct Pattern | Orchestrated Pattern |
|---------|---------------|---------------------|
| **Setup Complexity** | Low | Medium |
| **Code Lines** | Minimal (1 line!) | More (context building) |
| **Entity Types** | Auto-detected (optional) | Passed from intent ✅ |
| **LLM Calls** | 1 (query planning only) | 1 (intent extraction only) ✅ |
| **Token Efficiency** | All schemas (if not specified) | Only relevant schemas ✅ |
| **Security (PII)** | Manual | Automatic ✅ |
| **Access Control** | Manual | Automatic ✅ |
| **Behavior Insights** | Not available | Automatic ✅ |
| **Unified Entry Point** | No | Yes ✅ |
| **Performance** | Fast | Same (no extra LLM call!) ✅ |
| **Flexibility** | Full control | Standardized |
| **Use Case** | Simple apps | Enterprise apps |
| **Dependencies** | Minimal | Core + orchestrator |

### LLM Call Comparison

#### Direct Pattern (Simple)
```
User: "find premium users"
  ↓
queryService.execute("find premium users")  // No entityTypes
  ↓
LLM Call #1: Analyze query against ALL entity schemas
  └─→ Extract: entities, relationships, filters
  └─→ Generate: JPQL query
  ↓
Results returned

Total LLM Calls: 1
Token Usage: HIGH (all schemas included)
```

#### Direct Pattern (Optimized)
```
User: "find premium users"
  ↓
queryService.execute("find premium users", ["user"])  // With entityTypes
  ↓
LLM Call #1: Analyze query against USER schema only
  └─→ Extract: relationships, filters
  └─→ Generate: JPQL query
  ↓
Results returned

Total LLM Calls: 1
Token Usage: LOW (only user schema included)
```

#### Orchestrated Pattern (Efficient!)
```
User: "find premium users"
  ↓
orchestrator.orchestrate("find premium users", context)
  ↓
LLM Call #1: Intent extraction
  └─→ Extract: action type, entities, parameters
  └─→ Output: { action: "relationship_query", entityTypes: ["user"] }
  ↓
RelationshipQueryActionHandler.execute(params)
  ↓
queryService.execute(query, ["user"], options)  // ✅ Uses extracted entities
  └─→ NO LLM CALL! Uses provided entityTypes
  └─→ Direct JPQL generation with user schema
  ↓
Results returned

Total LLM Calls: 1 (same as direct!)
Token Usage: LOW (orchestrator already extracted entities)
Performance: BEST (no redundant LLM processing)
```

**Key Insight:** Orchestrated pattern is NOT slower - it's actually MORE efficient because entity types are extracted once during intent analysis and reused!

---

## 🔧 Configuration Reference

### Relationship Query Module

```yaml
ai:
  infrastructure:
    relationship:
      # Core module toggle
      enabled: true
      
      # NEW: Orchestrator integration
      enable-orchestrator-integration: true  # Default: true
      
      # Query execution settings
      enable-vector-search: true
      fallback-to-metadata: true
      fallback-to-vector-search: true
      enable-query-validation: true
      enable-query-caching: true
      
      # Defaults
      default-return-mode: IDS
      default-similarity-threshold: 0.7
      max-traversal-depth: 3
```

### Behavior Integration (Orchestrated Mode Only)

When using orchestrated pattern, behavior insights are automatically available:

```yaml
ai:
  infrastructure:
    behavior:
      enabled: true  # Behavior insights will be included in orchestration
```

---

## 🚀 Migration Guide

### For New Users

**Simple App (Direct Pattern):**

```java
// 1. Add dependency
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-relationship-query</artifactId>
</dependency>

// 2. Inject service
@Autowired
private ReliableRelationshipQueryService queryService;

// 3. Use it
RAGResponse response = queryService.execute(
    "Find premium users",
    List.of("user"),
    null
);
```

**Enterprise App (Orchestrated Pattern):**

```java
// 1. Add dependencies
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-core</artifactId>
</dependency>
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-relationship-query</artifactId>
</dependency>

// 2. Configure
ai.infrastructure.relationship.enable-orchestrator-integration: true

// 3. Use orchestrator
@Autowired
private RAGOrchestrator orchestrator;

OrchestrationResult result = orchestrator.orchestrate(
    "relationship query: find premium users",
    context
);
```

### For Existing Users

**No Changes Required** ✅

If you're already using direct pattern:

```java
@Autowired
private ReliableRelationshipQueryService queryService;

// This continues to work exactly as before
RAGResponse response = queryService.execute(...);
```

**Optional: Enable Orchestrator Integration**

If you want to try orchestrated pattern:

```yaml
# Add this to enable orchestrator integration
ai:
  infrastructure:
    relationship:
      enable-orchestrator-integration: true
```

Then you can use BOTH patterns simultaneously.

---

## 🧪 Testing Strategy

### Unit Tests

**RelationshipQueryActionHandlerTest:**
- ✅ Action name registration
- ✅ Parameter extraction (query, entityTypes, options)
- ✅ Successful execution
- ✅ Error handling (missing params, service failures)
- ✅ Permission validation
- ✅ Result formatting

### Integration Tests

**RelationshipQueryOrchestratorIntegrationTest:**
- ✅ End-to-end orchestration flow
- ✅ Intent extraction (LLM recognizes relationship_query action)
- ✅ Action handler discovery
- ✅ Parameter passing
- ✅ Result propagation

**Example:**

```java
@SpringBootTest
@TestPropertySource(properties = {
    "ai.infrastructure.relationship.enable-orchestrator-integration=true"
})
class RelationshipQueryOrchestratorIntegrationTest {
    
    @Autowired
    private RAGOrchestrator orchestrator;
    
    @Autowired
    private ActionHandlerRegistry actionHandlerRegistry;
    
    @Test
    void orchestrator_shouldDiscoverRelationshipQueryHandler() {
        // Verify handler is registered
        Optional<ActionHandler> handler = actionHandlerRegistry
            .findHandler("relationship_query");
        
        assertThat(handler).isPresent();
        assertThat(handler.get())
            .isInstanceOf(RelationshipQueryActionHandler.class);
    }
    
    @Test
    void orchestrator_shouldExecuteRelationshipQuery() {
        // Arrange
        String query = "Execute relationship query to find premium users";
        OrchestrationContext context = OrchestrationContext.forUser("test-user");
        
        // Act
        OrchestrationResult result = orchestrator.orchestrate(query, context);
        
        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getType())
            .isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.getData()).containsKey("documents");
    }
}
```

---

## 📈 Success Criteria

✅ **No Breaking Changes** - Existing direct usage continues to work  
✅ **ActionHandler Registered** - Auto-discovered by Spring  
✅ **Orchestrator Integration Works** - End-to-end flow successful  
✅ **Configuration Toggleable** - Can enable/disable integration  
✅ **Both Patterns Documented** - Clear guidance on when to use each  
✅ **Tests Pass** - Unit and integration tests verify functionality  
✅ **No Circular Dependencies** - Clean architecture maintained  
✅ **Performance Acceptable** - Orchestrated mode has minimal overhead  

---

## 🧪 RealAPI Integration Tests

### Overview

The `integration-tests` module should include comprehensive RealAPI tests for relationship query orchestrator integration.

**Location:** `ai-infrastructure-module/integration-Testing/integration-tests/src/test/java/com/ai/infrastructure/it/`

### Test Coverage

#### 1. RelationshipQueryOrchestratorRealApiIntegrationTest

**Purpose:** Test end-to-end orchestration with real LLM API calls

**Test Cases:**

```java
@SpringBootTest
@ActiveProfiles("realapi")
@TestPropertySource(properties = {
    "ai.infrastructure.relationship.enable-orchestrator-integration=true",
    "ai.providers.llm-provider=openai"  // or anthropic, etc.
})
class RelationshipQueryOrchestratorRealApiIntegrationTest {
    
    @Autowired
    private RAGOrchestrator orchestrator;
    
    @Autowired
    private ActionHandlerRegistry actionHandlerRegistry;
    
    @Autowired
    private TestDataSetup testDataSetup;
    
    @BeforeEach
    void setUp() {
        // Setup test entities (users, orders, products)
        testDataSetup.createTestData();
    }
    
    @Test
    @DisplayName("Orchestrator should discover relationship query action handler")
    void shouldDiscoverRelationshipQueryHandler() {
        Optional<ActionHandler> handler = actionHandlerRegistry
            .findHandler("relationship_query");
        
        assertThat(handler).isPresent();
        assertThat(handler.get())
            .isInstanceOf(RelationshipQueryActionHandler.class);
    }
    
    @Test
    @DisplayName("Should execute relationship query via orchestrator with real LLM")
    void shouldExecuteRelationshipQueryViaOrchestrator() {
        // Arrange
        String query = "Execute relationship query to find premium users who ordered in the last month";
        OrchestrationContext context = OrchestrationContext.builder()
            .userId("test-user-123")
            .sessionId("test-session-456")
            .build();
        
        // Act
        OrchestrationResult result = orchestrator.orchestrate(query, context);
        
        // Assert
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getType())
            .isEqualTo(OrchestrationResultType.ACTION_EXECUTED);
        assertThat(result.getData()).containsKey("documents");
        assertThat(result.getData()).containsKey("totalResults");
        
        // Verify hybrid search was used
        Map<String, Object> data = result.getData();
        assertThat(data.get("hybridSearchUsed")).isNotNull();
    }
    
    @Test
    @DisplayName("Should handle complex multi-entity relationship queries")
    void shouldHandleMultiEntityQueries() {
        // Arrange
        String query = "Find orders for premium customers with products in Electronics category";
        OrchestrationContext context = OrchestrationContext.forUser("test-user");
        
        // Act
        OrchestrationResult result = orchestrator.orchestrate(query, context);
        
        // Assert
        assertThat(result.isSuccess()).isTrue();
        List<RAGDocument> documents = extractDocuments(result);
        assertThat(documents).isNotEmpty();
        
        // Verify results match criteria
        documents.forEach(doc -> {
            assertThat(doc.getMetadata())
                .containsKey("entityType")
                .containsValue("order");
        });
    }
    
    @Test
    @DisplayName("Should apply behavior insights when available")
    void shouldApplyBehaviorInsights() {
        // Arrange - setup user with behavior insights
        testDataSetup.createUserWithBehaviorInsights(
            "user-with-insights",
            "FRUSTRATED",
            0.8  // high churn risk
        );
        
        String query = "relationship query: find my recent orders";
        OrchestrationContext context = OrchestrationContext.forUser("user-with-insights");
        
        // Act
        OrchestrationResult result = orchestrator.orchestrate(query, context);
        
        // Assert
        assertThat(result.isSuccess()).isTrue();
        // Verify behavior context was used (tone, recommendations)
        assertThat(result.getMessage()).isNotBlank();
    }
    
    @Test
    @DisplayName("Should handle PII detection in relationship queries")
    void shouldHandlePiiDetection() {
        // Arrange
        String query = "Find orders for customer with email john.doe@example.com";
        OrchestrationContext context = OrchestrationContext.forUser("test-user");
        
        // Act
        OrchestrationResult result = orchestrator.orchestrate(query, context);
        
        // Assert
        // Should either redact PII or block query based on PII policy
        assertThat(result).isNotNull();
        // Verify PII was detected and handled
    }
    
    @Test
    @DisplayName("Should fallback gracefully when relationship query fails")
    void shouldFallbackGracefully() {
        // Arrange - invalid entity type
        String query = "relationship query: find non-existent entities";
        OrchestrationContext context = OrchestrationContext.forUser("test-user");
        
        // Act
        OrchestrationResult result = orchestrator.orchestrate(query, context);
        
        // Assert
        // Should either return empty results or provide helpful error
        assertThat(result).isNotNull();
        if (!result.isSuccess()) {
            assertThat(result.getMessage()).isNotBlank();
        }
    }
    
    @Test
    @DisplayName("Should respect access control for relationship queries")
    void shouldRespectAccessControl() {
        // Arrange
        String restrictedUserId = "restricted-user";
        String query = "relationship query: find all orders";
        OrchestrationContext context = OrchestrationContext.forUser(restrictedUserId);
        
        // Act
        OrchestrationResult result = orchestrator.orchestrate(query, context);
        
        // Assert
        // Verify access control was applied
        assertThat(result).isNotNull();
        // Should only return orders accessible to restricted user
    }
    
    @Test
    @DisplayName("Should combine vector and relational results effectively")
    void shouldCombineVectorAndRelationalResults() {
        // Arrange
        String query = "Find AI-related products ordered by premium users";
        OrchestrationContext context = OrchestrationContext.builder()
            .userId("test-user")
            .metadata(Map.of("forceMode", "ENHANCED"))
            .build();
        
        // Act
        OrchestrationResult result = orchestrator.orchestrate(query, context);
        
        // Assert
        assertThat(result.isSuccess()).isTrue();
        
        Map<String, Object> data = result.getData();
        assertThat(data.get("hybridSearchUsed")).isEqualTo(true);
        
        // Verify results are ranked by relevance
        List<RAGDocument> documents = extractDocuments(result);
        assertThat(documents).isSortedAccordingTo(
            Comparator.comparing(RAGDocument::getScore).reversed()
        );
    }
    
    @Test
    @DisplayName("Should cache query plans for performance")
    void shouldCacheQueryPlans() {
        // Arrange
        String query = "Find premium users";
        OrchestrationContext context = OrchestrationContext.forUser("test-user");
        
        // Act - First call
        long start1 = System.currentTimeMillis();
        OrchestrationResult result1 = orchestrator.orchestrate(query, context);
        long duration1 = System.currentTimeMillis() - start1;
        
        // Act - Second call (should use cache)
        long start2 = System.currentTimeMillis();
        OrchestrationResult result2 = orchestrator.orchestrate(query, context);
        long duration2 = System.currentTimeMillis() - start2;
        
        // Assert
        assertThat(result1.isSuccess()).isTrue();
        assertThat(result2.isSuccess()).isTrue();
        
        // Second call should be significantly faster
        assertThat(duration2).isLessThan(duration1 / 2);
        
        log.info("First call: {}ms, Cached call: {}ms", duration1, duration2);
    }
    
    @Test
    @DisplayName("Should handle different query modes (STANDALONE vs ENHANCED)")
    void shouldHandleQueryModes() {
        String baseQuery = "Find products in Electronics category";
        
        // Test STANDALONE mode
        OrchestrationContext standaloneContext = OrchestrationContext.builder()
            .userId("test-user")
            .metadata(Map.of("queryMode", "STANDALONE"))
            .build();
        
        OrchestrationResult standaloneResult = orchestrator.orchestrate(
            baseQuery, 
            standaloneContext
        );
        
        // Test ENHANCED mode
        OrchestrationContext enhancedContext = OrchestrationContext.builder()
            .userId("test-user")
            .metadata(Map.of("queryMode", "ENHANCED"))
            .build();
        
        OrchestrationResult enhancedResult = orchestrator.orchestrate(
            baseQuery, 
            enhancedContext
        );
        
        // Assert
        assertThat(standaloneResult.isSuccess()).isTrue();
        assertThat(enhancedResult.isSuccess()).isTrue();
        
        // Enhanced mode should use hybrid search
        Map<String, Object> enhancedData = enhancedResult.getData();
        assertThat(enhancedData.get("hybridSearchUsed")).isEqualTo(true);
    }
    
    // Helper methods
    private List<RAGDocument> extractDocuments(OrchestrationResult result) {
        Map<String, Object> data = result.getData();
        return (List<RAGDocument>) data.get("documents");
    }
}
```

#### 2. TestDataSetup Utility

**Purpose:** Setup test data for relationship query tests

```java
@Component
@Profile("realapi")
public class TestDataSetup {
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private AICapabilityService aiCapabilityService;
    
    @Autowired
    private BehaviorAnalysisService behaviorAnalysisService;
    
    public void createTestData() {
        createUsers();
        createProducts();
        createOrders();
        indexEntitiesForSearch();
    }
    
    private void createUsers() {
        // Create premium users
        User premium1 = User.builder()
            .id(UUID.randomUUID())
            .name("Premium User 1")
            .email("premium1@example.com")
            .tier("PREMIUM")
            .status("ACTIVE")
            .build();
        userRepository.save(premium1);
        
        // Create regular users
        User regular1 = User.builder()
            .id(UUID.randomUUID())
            .name("Regular User 1")
            .email("regular1@example.com")
            .tier("FREE")
            .status("ACTIVE")
            .build();
        userRepository.save(regular1);
    }
    
    private void createProducts() {
        // Create electronics products
        Product laptop = Product.builder()
            .id(UUID.randomUUID())
            .name("Gaming Laptop")
            .category("Electronics")
            .description("High-performance gaming laptop with RTX graphics")
            .price(1500.00)
            .build();
        productRepository.save(laptop);
        
        Product headphones = Product.builder()
            .id(UUID.randomUUID())
            .name("Wireless Headphones")
            .category("Electronics")
            .description("Premium noise-cancelling wireless headphones")
            .price(299.99)
            .build();
        productRepository.save(headphones);
    }
    
    private void createOrders() {
        // Create orders linking users and products
        // Implementation details...
    }
    
    private void indexEntitiesForSearch() {
        // Index all entities for vector search
        userRepository.findAll().forEach(user -> 
            aiCapabilityService.processEntityForAI(user, "user")
        );
        
        productRepository.findAll().forEach(product -> 
            aiCapabilityService.processEntityForAI(product, "product")
        );
        
        orderRepository.findAll().forEach(order -> 
            aiCapabilityService.processEntityForAI(order, "order")
        );
    }
    
    public void createUserWithBehaviorInsights(
        String userId, 
        String sentimentLabel, 
        double churnRisk
    ) {
        User user = User.builder()
            .id(UUID.fromString(userId))
            .name("User with Insights")
            .tier("PREMIUM")
            .build();
        userRepository.save(user);
        
        // Create behavior insights
        behaviorAnalysisService.analyzeUser(UUID.fromString(userId));
        
        // Update with specific sentiment/churn
        // Implementation details...
    }
    
    @PreDestroy
    public void cleanup() {
        orderRepository.deleteAll();
        productRepository.deleteAll();
        userRepository.deleteAll();
    }
}
```

#### 3. Test Configuration

**application-realapi.yml:**

```yaml
ai:
  infrastructure:
    relationship:
      enabled: true
      enable-orchestrator-integration: true
      enable-vector-search: true
      enable-query-caching: true
      
    behavior:
      enabled: true  # For behavior insights tests
      
  providers:
    llm-provider: openai
    embedding-provider: openai
    
  vector:
    database-type: lucene
    
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    
logging:
  level:
    com.ai.infrastructure: DEBUG
```

### Running RealAPI Tests

**Command:**

```bash
# Run relationship query orchestrator integration tests
mvn test -Dtest=RelationshipQueryOrchestratorRealApiIntegrationTest -Prealapi

# Or run all RealAPI tests
mvn verify -Prealapi
```

**Prerequisites:**

1. Set OpenAI API key: `export OPENAI_API_KEY=sk-...`
2. Database running (or use H2 in-memory)
3. Test data seeded

### Success Criteria

✅ Handler discovery works  
✅ End-to-end orchestration successful  
✅ LLM intent extraction correct  
✅ Hybrid search combines vector + relational  
✅ Behavior insights applied when available  
✅ PII detection works  
✅ Access control enforced  
✅ Fallback chain functions  
✅ Query caching improves performance  
✅ Different query modes work correctly  

---

## 🎯 Implementation Checklist

### Phase 0: Understanding Existing Capability (Already Complete ✅)
- [x] **Hybrid vector+relational search** - Already implemented in `ReliableRelationshipQueryService`
- [x] **LLM query planning** - Already implemented in `RelationshipQueryPlanner`
- [x] **Multi-level fallback** - Already implemented (4 fallback levels)
- [x] **JPQL generation** - Already implemented in `LLMDrivenJPAQueryService`
- [x] **Vector search integration** - Already uses `VectorDatabaseService` from core
- [x] **Query caching** - Already implemented in `QueryCache`

**Note:** The relationship query module ALREADY has sophisticated hybrid search. This phase is about integrating that existing capability with the orchestrator.

### Phase 0.5: Enhance IntentQueryExtractor (NEW - Required First!) ⚠️

**Status:** 🆕 **NEW REQUIREMENT** - Must be implemented before handler

- [ ] **Entity type extraction in LLM prompt** - Enhance system prompt to extract entity types
- [ ] **Validation logic** - Ensure entityTypes are present in actionParams for relationship_query
- [ ] **Fallback handling** - Set empty list if entity types cannot be extracted
- [ ] **Unit tests** - Test entity type extraction from various queries
- [ ] **Integration tests** - Verify entity types flow through to handler correctly

**Why Critical:** This optimization allows entity types to be extracted once during intent analysis, avoiding redundant LLM calls in the relationship query service. Without this, the handler will need to make an additional LLM call to determine entity types, reducing efficiency.

### Phase 1: Core Implementation (New Work)

#### 1.0 Enhance IntentQueryExtractor (CRITICAL - Do First!)

**Priority:** 🔴 **CRITICAL** - Must be done before handler implementation

**Task:** Enhance `IntentQueryExtractor` to extract entity types for relationship queries during intent analysis.

**Why:** This optimization allows entity types to be extracted once during intent analysis and reused, avoiding redundant LLM calls in the relationship query service.

**Implementation Steps:**

1. **Update System Prompt** in `EnrichedPromptBuilder.java`:
   - Add instructions for extracting entity types when action is "relationship_query"
   - Include examples showing expected format
   - Specify fallback behavior if entity types cannot be determined

2. **Add Validation** in `IntentQueryExtractor.java`:
   - Validate that relationship_query intents have entityTypes in actionParams
   - Set empty list as fallback if missing (allows auto-detection)

3. **Add Tests**:
   - Unit test: Verify entity type extraction from various queries
   - Integration test: Verify end-to-end flow with entity types

**Deliverables:**
- [ ] Enhanced system prompt with entity type extraction instructions
- [ ] Validation logic for relationship_query intents
- [ ] Unit tests for entity type extraction
- [ ] Integration tests verifying optimization works

**Estimated Time:** 2-3 hours

---

#### 1.1 Add Simpler Execute Overloads to ReliableRelationshipQueryService

**Purpose:** Make direct usage simpler by making `entityTypes` optional

```java
// In: ai-infrastructure-relationship-query/.../ReliableRelationshipQueryService.java

/**
 * Execute relationship query with full auto-detection.
 * Entity types are automatically determined by LLM from the query.
 * Use this for simplicity - recommended for direct usage.
 * 
 * @param query Natural language query
 * @return Query results
 */
public RAGResponse execute(String query) {
    return execute(query, null, null);
}

/**
 * Execute relationship query with options.
 * Entity types are automatically determined by LLM from the query.
 * 
 * @param query Natural language query
 * @param options Query options (null for defaults)
 * @return Query results
 */
public RAGResponse execute(String query, QueryOptions options) {
    return execute(query, null, options);
}

/**
 * Execute relationship query with entity type hints (advanced).
 * 
 * Entity types are OPTIONAL performance hints that:
 * - Reduce LLM token usage by limiting schema sent in prompt
 * - Guide LLM focus to specific entity domains
 * - Speed up query planning for large entity catalogs
 * 
 * If null/empty: LLM analyzes against ALL entity schemas (works fine, uses more tokens)
 * If provided: LLM only analyzes THESE entity schemas (faster, cheaper)
 * 
 * NOTE: When called via orchestrator, entityTypes SHOULD be provided
 * (already extracted by orchestrator's LLM to avoid redundant calls)
 * 
 * @param query Natural language query
 * @param entityTypes OPTIONAL entity type hints. 
 *                   Examples: ["user"], ["order", "product"], null for auto-detect
 * @param options Query options (null for defaults)
 * @return Query results
 */
public RAGResponse execute(String query, 
                          @Nullable List<String> entityTypes,
                          @Nullable QueryOptions options) {
    // Existing implementation - already handles null entityTypes!
    QueryOptions effectiveOptions = options != null ? options : QueryOptions.defaults();
    RAGResponse primary = tryPrimary(query, entityTypes, effectiveOptions);
    // ... rest of existing implementation
}
```

#### 1.2 Create RelationshipQueryActionHandler

- [ ] Create `RelationshipQueryActionHandler` class (bridges to existing service)
- [ ] Implement `getActionMetadata()` method (returns AIActionMetaData)
- [ ] Implement `executeAction(Map<String, Object> params, String userId)` method
- [ ] Implement `handleError(Exception e, String userId)` method
- [ ] Implement `validateActionAllowed(String userId)` method
- [ ] Implement `getConfirmationMessage(Map<String, Object> params)` method
- [ ] Add `enable-orchestrator-integration` property
- [ ] Implement parameter extraction logic (map to existing QueryOptions)
- [ ] **CRITICAL:** Extract and pass `entityTypes` from `actionParams` (avoid redundant LLM calls)
- [ ] Implement QueryOptions building (use existing QueryOptions class)
- [ ] Implement result formatting (convert RAGResponse to ActionResult)
- [ ] Handle `List<String>` entityTypes from actionParams (verify JSON deserialization works)

### Phase 2: Testing
- [ ] Write unit tests for `RelationshipQueryActionHandler`
  - [ ] Test `getActionMetadata()` returns correct metadata
  - [ ] Test `executeAction()` with valid parameters
  - [ ] Test `executeAction()` with missing query parameter
  - [ ] Test `executeAction()` with missing entityTypes (should use empty list fallback)
  - [ ] Test `executeAction()` with invalid entityTypes format
  - [ ] Test `handleError()` method
  - [ ] Test `validateActionAllowed()` method
  - [ ] Test `getConfirmationMessage()` method
  - [ ] Test parameter extraction (query, entityTypes, limit, returnMode, etc.)
  - [ ] Test QueryOptions building from actionParams
  - [ ] Test result formatting (RAGResponse → ActionResult)
- [ ] Create `RelationshipQueryOrchestratorRealApiIntegrationTest` (see RealAPI section above)
- [ ] **CRITICAL:** Test entity type extraction in IntentQueryExtractor
  - [ ] Test LLM extracts entity types correctly
  - [ ] Test fallback when entity types cannot be extracted
  - [ ] Test entity types are passed correctly via actionParams
- [ ] Create `TestDataSetup` utility for test data
- [ ] Configure `application-realapi.yml` for tests
- [ ] Test with various parameter combinations
- [ ] Test error scenarios (invalid entities, missing params)
- [ ] Verify handler auto-discovery via ActionHandlerRegistry
- [ ] Test behavior insights integration
- [ ] Test PII detection integration
- [ ] Test access control integration
- [ ] Test query caching performance
- [ ] Test different query modes (STANDALONE vs ENHANCED)
- [ ] Verify hybrid search combines vector + relational correctly
- [ ] **Verify entity type optimization works** (no redundant LLM calls)

### Phase 3: Documentation
- [ ] Update relationship query README
- [ ] Add orchestrator integration section to user guide
- [ ] Create usage examples for both patterns
- [ ] Document configuration properties
- [ ] Add migration guide

### Phase 4: Verification
- [ ] Verify no circular dependencies (`mvn dependency:tree`)
- [ ] Verify existing direct usage still works
- [ ] Verify orchestrated usage works
- [ ] Performance benchmarks (both patterns)
- [ ] Security validation (PII, access control work in orchestrated mode)

---

## 🔍 Architectural Decisions

### Decision 1: Use ActionHandler (Not Custom SPI)

**Decision:** Use existing `ActionHandler` interface instead of creating custom SPI

**Rationale:**
- ActionHandler IS already an SPI pattern
- Auto-discovery works via Spring List injection
- No circular dependency issues
- Consistent with other action handlers
- Less code, simpler architecture

**Alternatives Considered:**
- Custom SPI like `BehaviorContextProvider` → **Rejected** (unnecessary complexity)
- Direct orchestrator modification → **Rejected** (breaks modularity)

### Decision 2: Support Both Patterns

**Decision:** Support both direct and orchestrated usage

**Rationale:**
- Different apps have different needs
- Simple apps don't need orchestrator overhead
- Enterprise apps benefit from unified entry point
- No breaking changes for existing users
- Clear documentation guides choice

**Alternatives Considered:**
- Force orchestrator usage → **Rejected** (too restrictive)
- Only direct usage → **Rejected** (misses enterprise value)

### Decision 3: Enable by Default

**Decision:** `enable-orchestrator-integration: true` (default)

**Rationale:**
- Most users benefit from option being available
- No overhead if not used (handler just sits dormant)
- Easy to discover and try
- Consistent with Spring Boot auto-configuration philosophy

**Alternatives Considered:**
- Disable by default → **Rejected** (harder to discover)
- Always enabled → **Rejected** (no opt-out)

---

## 🎨 What's Already Implemented vs What's New

### ✅ Already Implemented (In Relationship-Query Module)

The relationship query module ALREADY has:

1. **Hybrid Search Architecture** ✅
   - Combines vector similarity + relational database queries
   - LLM-driven intent understanding
   - Automatic query strategy selection

2. **Multi-Level Fallback Chain** ✅
   ```
   Level 1: LLM → JPQL query (relational)
   Level 2: Metadata traversal fallback
   Level 3: Vector search fallback (semantic)
   Level 4: Simple repository lookup
   ```

3. **Components** ✅
   - `ReliableRelationshipQueryService` - Main orchestrator
   - `LLMDrivenJPAQueryService` - JPQL generation
   - `RelationshipQueryPlanner` - LLM intent analysis
   - `RelationshipTraversalService` - Metadata queries
   - `VectorDatabaseService` integration - Semantic search
   - `QueryCache` - Plan and embedding caching
   - `QueryMetrics` - Performance tracking

4. **Query Strategies** ✅
   - STANDALONE (pure relational)
   - ENHANCED (hybrid vector+relational)
   - Automatic mode detection

5. **Return Modes** ✅
   - IDS (entity IDs only)
   - FULL (complete content)

### 🆕 What Needs to be Built

**Only the orchestrator integration:**

1. **RelationshipQueryActionHandler** 🆕
   - Implements `ActionHandler` interface
   - Delegates to existing `ReliableRelationshipQueryService`
   - Converts params to/from ActionHandler format

2. **Configuration Property** 🆕
   - `enable-orchestrator-integration` toggle

3. **Integration Tests** 🆕
   - RealAPI tests for orchestrator integration
   - Verify behavior insights integration
   - Verify PII detection works
   - Verify access control applied

**Estimated Effort:** 
- IntentQueryExtractor enhancement: 2-3 hours ⚠️ **NEW**
- Handler implementation: 4-6 hours
- Tests: 8-10 hours (includes entity type extraction tests)
- Documentation updates: 2-3 hours
- **Total: 2-3 days** (includes critical prompt enhancement)

The heavy lifting (hybrid search, LLM planning, vector+relational fusion) is ALREADY DONE.

---

## 📚 References

- [Orchestration Context Implementation Plan](./ORCHESTRATION_CONTEXT_IMPLEMENTATION_PLAN.md)
- [Behavior Orchestrator Integration Guide](./Behaviour_Module/BEHAVIOR_ORCHESTRATOR_INTEGRATION_GUIDE.md)
- [Relationship Query User Guide](../ai-infrastructure-relationship-query/RELATIONSHIP_QUERY_USER_GUIDE.md)
- [Relationship-Aware RAG Documentation](../docs/semantic-relational-implementation/)
- [ActionHandler Interface](../ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/action/ActionHandler.java)
- [Existing Hybrid Implementation](../ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/service/ReliableRelationshipQueryService.java)

---

## 🎯 Quick Summary

### What Exists
- ✅ **Sophisticated hybrid search** combining vector + relational queries
- ✅ **LLM-driven intent understanding** and query planning
- ✅ **Multi-level fallback chain** for reliability
- ✅ **Query caching** for performance
- ✅ **Fully functional standalone** usage pattern

### What We're Adding
- 🆕 **Simplified execute() overloads** - No entity types needed for direct usage!
  ```java
  // NEW: Super simple API
  queryService.execute("find premium users")  // That's it!
  ```
- 🆕 **RelationshipQueryActionHandler** - Bridges to orchestrator
- 🆕 **Orchestrator integration** - Unified entry point with entity type optimization
- 🆕 **RealAPI integration tests** - Comprehensive test coverage
- 🆕 **Configuration toggle** - Enable/disable orchestrator integration

### Usage Patterns

**Direct (Simple - 95% of users):**
```java
// Just ask the question!
queryService.execute("find premium users who ordered in December")
```

**Direct (Performance tuning - 5% of users):**
```java
// Optimize token usage for large entity catalogs
queryService.execute("find premium users", List.of("user"), options)
```

**Orchestrated (Enterprise features):**
```java
// Automatically extracts entity types + adds behavior insights
orchestrator.orchestrate("find premium users", context)
```

### Efficiency Comparison

| Pattern | LLM Calls | Token Usage | Features |
|---------|-----------|-------------|----------|
| **Direct (simple)** | 1 | High (all schemas) | Basic |
| **Direct (optimized)** | 1 | Low (specific schemas) | Basic |
| **Orchestrated** | 1 | Low (auto-optimized) ✅ | Full ✅ |

**Key Insight:** Orchestrated pattern is MOST efficient - entity types extracted once, reused everywhere!

### Effort Required
- **Small** - Just bridging existing capability + adding simple overloads
- **Estimated:** 1-2 days of development
- **No re-invention** - Reuses all existing hybrid search logic

### Value Delivered
- ✅ **Simpler API** for direct usage (no entity types needed!)
- ✅ **More efficient** orchestrated usage (entity types passed from intent)
- ✅ Both usage patterns supported (direct + orchestrated)
- ✅ Behavior insights available in orchestrated mode
- ✅ Unified security/PII/access control
- ✅ No breaking changes to existing users

---

**Document Version:** 1.2  
**Created:** 2025-12-30  
**Updated:** 2025-12-30  
**Status:** Ready for Implementation  
**Owner:** AI Infrastructure Team  

**Change Log:**
- v1.0: Initial document with orchestrator integration plan
- v1.1: Added existing capability clarification and RealAPI tests
- v1.2: Added simplified API (entity types optional) + efficiency analysis
- v1.3: **CRITICAL FIXES** - Fixed ActionHandler method signatures, added IntentQueryExtractor enhancement requirement, corrected interface methods

**Key Decisions:**
1. Use existing `ActionHandler` SPI (not custom integration)
2. Leverage existing hybrid search capability (already implemented)
3. Add simplified `execute()` overloads (entity types optional for direct usage)
4. **CRITICAL:** Enhance IntentQueryExtractor to extract entity types (efficiency optimization)
5. Orchestrator MUST pass entity types via actionParams (avoids redundant LLM calls)
6. Add comprehensive RealAPI integration tests
7. Support both direct and orchestrated patterns
8. Use correct ActionHandler method signature: `executeAction(params, userId)` not `execute(userId, params)`

**Important Notes:**
- The relationship query module ALREADY implements sophisticated hybrid vector+relational search
- This work is about: (a) Simplifying direct usage API, (b) Integrating with orchestrator, (c) **Enhancing IntentQueryExtractor to extract entity types**
- Orchestrated pattern is MORE efficient (entity types extracted once, reused) - **BUT requires IntentQueryExtractor enhancement
- **CRITICAL:** Must enhance IntentQueryExtractor FIRST before implementing handler (Phase 0.5)
- ActionHandler interface uses `executeAction(Map<String, Object> params, String userId)` - parameters in this order
- ActionHandler also requires `getActionMetadata()`, `handleError()`, `validateActionAllowed()`, and `getConfirmationMessage()` methods

