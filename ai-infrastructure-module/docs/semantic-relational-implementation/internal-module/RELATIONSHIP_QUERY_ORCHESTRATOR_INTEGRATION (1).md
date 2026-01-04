# Relationship Query ↔ Orchestrator Integration Guide

## 📑 Table of Contents

- [📋 Executive Summary](#-executive-summary)
- [⚠️ CRITICAL IMPLEMENTATION REQUIREMENTS](#️-critical-implementation-requirements)
- [🎯 Design Principles](#-design-principles)
- [🏗️ Architecture Overview](#️-architecture-overview)
  - [Current Architecture (Standalone Only)](#current-architecture-standalone-only)
  - [New Architecture (Dual Pattern)](#new-architecture-dual-pattern)
- [🔍 Why ActionHandler IS Already an SPI](#-why-actionhandler-is-already-an-spi)
- [🔄 Comparison: Behavior SPI vs Relationship ActionHandler](#-comparison-behavior-spi-vs-relationship-actionhandler)
- [📦 Implementation](#-implementation)
  - [Phase 0: Enhance IntentQueryExtractor for Entity Type Extraction](#phase-0-enhance-intentqueryextractor-for-entity-type-extraction)
  - [Phase 1: Create RelationshipQueryActionHandler](#phase-1-create-relationshipqueryactionhandler)
  - [Phase 2: Add Configuration Property](#phase-2-add-configuration-property)
  - [Phase 3: Unit Tests](#phase-3-unit-tests)
- [🔐 Access Control: Entity Type Filtering](#-access-control-entity-type-filtering) ⭐ **START HERE FOR ACCESS CONTROL**
  - [Overview](#overview)
  - [How Framework Users Control Access](#how-framework-users-control-access) ⚠️ **CRITICAL: Read This First**
  - [How It Works](#how-it-works)
  - [Implementation Patterns](#implementation-patterns)
    - [Pattern 1: Role-Based Entity Type Access](#pattern-1-role-based-entity-type-access)
    - [Pattern 2: Permission-Based Access Control](#pattern-2-permission-based-access-control)
    - [Pattern 3: Tenant-Based Access Control](#pattern-3-tenant-based-access-control)
    - [Pattern 4: Data Classification-Based Access](#pattern-4-data-classification-based-access)
    - [Pattern 5: Hybrid Access Control (Recommended)](#pattern-5-hybrid-access-control-recommended)
  - [Integration with EntityAccessPolicy](#integration-with-entityaccesspolicy)
  - [Result-Level Access Control](#result-level-access-control)
  - [Configuration](#configuration)
  - [Testing Access Control](#testing-access-control)
  - [Best Practices](#best-practices)
  - [Summary](#summary)
- [🎨 Usage Patterns](#-usage-patterns)
  - [Pattern 1: Direct (Standalone)](#pattern-1-direct-standalone)
  - [Pattern 2: Via Orchestrator (Enterprise)](#pattern-2-via-orchestrator-enterprise)
- [📊 Pattern Comparison](#-pattern-comparison)
- [🔧 Configuration Reference](#-configuration-reference)
- [🚀 Migration Guide](#-migration-guide)
- [🧪 Testing Strategy](#-testing-strategy)
- [🧪 RealAPI Integration Tests](#-realapi-integration-tests)
- [🎯 Implementation Checklist](#-implementation-checklist)
- [🔍 Architectural Decisions](#-architectural-decisions)
- [🎨 What's Already Implemented vs What's New](#-whats-already-implemented-vs-whats-new)
- [📚 References](#-references)
- [🎯 Quick Summary](#-quick-summary)

---

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
7. **Access Control** - Entity type filtering based on user permissions

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
import com.ai.infrastructure.intent.action.ActionResult;
import com.ai.infrastructure.intent.action.AIActionMetaData;

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
        // Implementation - NOTE: params come first, userId second
    }
    
    @Override
    public ActionResult handleError(Exception e, String userId) {
        // Error handling
    }
    
    // ... other methods (validateActionAllowed, getConfirmationMessage)
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
import java.util.Collections;
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
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name("relationship_query")
            .description("Execute natural language queries against relational data with automatic relationship traversal")
            .category("data_query")
            .parameters(Map.of(
                "query", "Natural language query (required)",
                "entityTypes", "List of entity types to search (required)",
                "limit", "Maximum results to return (optional, default: 20)",
                "returnMode", "IDS or FULL (optional, default: IDS)",
                "forceMode", "STANDALONE or ENHANCED (optional, auto-detected)",
                "similarityThreshold", "Vector search threshold 0.0-1.0 (optional, default: 0.7)"
            ))
            .build();
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        try {
            // Extract parameters
            String query = extractQuery(params);
            List<String> entityTypes = extractEntityTypes(params);
            
            // ⚠️ ACCESS CONTROL: Filter entity types based on user permissions
            List<String> allowedEntityTypes = filterAllowedEntityTypes(userId, entityTypes);
            
            if (allowedEntityTypes.isEmpty()) {
                log.warn("User {} has no access to requested entity types: {}", userId, entityTypes);
                return ActionResult.builder()
                    .success(false)
                    .message("Access denied: You don't have permission to query the requested entity types")
                    .data(Map.of("error", "ACCESS_DENIED", "requestedEntityTypes", entityTypes))
                    .build();
            }
            
            // Log if some entity types were filtered out
            if (allowedEntityTypes.size() < entityTypes.size()) {
                List<String> deniedTypes = new ArrayList<>(entityTypes);
                deniedTypes.removeAll(allowedEntityTypes);
                log.info("User {} access filtered: denied {} ({}), allowed {} ({})", 
                    userId, deniedTypes.size(), deniedTypes, allowedEntityTypes.size(), allowedEntityTypes);
            }
            
            QueryOptions options = buildQueryOptions(params);
            
            log.info("Executing relationship query for user: {} (entities: {})", 
                userId, allowedEntityTypes);
            log.debug("Query: {}", query);
            
            // Execute relationship query with filtered entity types
            // IMPORTANT: Pass filtered entityTypes to avoid redundant LLM call
            // The orchestrator's LLM already extracted these from the intent
            long startTime = System.currentTimeMillis();
            RAGResponse response = queryService.execute(query, allowedEntityTypes, options);
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
        // Basic validation: user must be authenticated
        if (userId == null || userId.isBlank()) {
            return false;
        }
        
        // Optional: Check if user has permission to execute relationship queries
        // This is a framework-level check - entity type filtering happens in executeAction()
        // You can integrate with your permission service here:
        // return permissionService.hasPermission(userId, "relationship_query:execute");
        
        return true;  // Default: allow if authenticated
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
    
    // ========== Access Control: Entity Type Filtering ==========
    
    /**
     * Filter entity types based on user permissions.
     * 
     * This method allows you to control which entity types a user can query.
     * Implement your business logic here to filter entity types based on:
     * - User roles (e.g., admin can query all, user can only query their own entities)
     * - User permissions (e.g., "relationship_query:customer", "relationship_query:order")
     * - Tenant isolation (e.g., multi-tenant apps)
     * - Data classification (e.g., sensitive entities require special permissions)
     * 
     * @param userId User identifier
     * @param requestedEntityTypes Entity types requested in the query
     * @return Filtered list of entity types the user is allowed to query
     */
    private List<String> filterAllowedEntityTypes(String userId, List<String> requestedEntityTypes) {
        if (requestedEntityTypes == null || requestedEntityTypes.isEmpty()) {
            // If no entity types specified, return empty list
            // The query service will auto-detect, but we want explicit control
            return getAllowedEntityTypesForUser(userId);
        }
        
        // Filter requested entity types based on user permissions
        List<String> allowed = new ArrayList<>();
        for (String entityType : requestedEntityTypes) {
            if (canUserQueryEntityType(userId, entityType)) {
                allowed.add(entityType);
            } else {
                log.debug("User {} denied access to entity type: {}", userId, entityType);
            }
        }
        
        return allowed;
    }
    
    /**
     * Check if user can query a specific entity type.
     * 
     * Override this method to implement your access control logic.
     * 
     * @param userId User identifier
     * @param entityType Entity type to check
     * @return true if user can query this entity type, false otherwise
     */
    private boolean canUserQueryEntityType(String userId, String entityType) {
        // TODO: Implement your access control logic here
        // Examples:
        
        // Option 1: Role-based access control
        // User user = userService.getUser(userId);
        // if (user.getRole().equals("ADMIN")) {
        //     return true;  // Admins can query all entity types
        // }
        // return user.getAllowedEntityTypes().contains(entityType);
        
        // Option 2: Permission-based access control
        // return permissionService.hasPermission(userId, "relationship_query:" + entityType);
        
        // Option 3: Tenant-based access control
        // String userTenant = userService.getTenantId(userId);
        // return entityTenantService.isEntityTypeAccessible(userTenant, entityType);
        
        // Option 4: Data classification
        // EntityClassification classification = entityClassificationService.getClassification(entityType);
        // if (classification == EntityClassification.SENSITIVE) {
        //     return permissionService.hasPermission(userId, "relationship_query:sensitive");
        // }
        // return true;
        
        // Default: Allow all (for development/testing)
        // In production, implement proper access control!
        log.warn("Access control not implemented - allowing all entity types for user: {}", userId);
        return true;
    }
    
    /**
     * Get all entity types a user is allowed to query.
     * 
     * Used when no entity types are specified in the query.
     * 
     * @param userId User identifier
     * @return List of entity types the user can query
     */
    private List<String> getAllowedEntityTypesForUser(String userId) {
        // TODO: Implement your logic to return allowed entity types
        // Examples:
        
        // Option 1: Return all entity types user has permission for
        // return permissionService.getEntityTypesWithPermission(userId, "relationship_query:");
        
        // Option 2: Return entity types based on user role
        // User user = userService.getUser(userId);
        // if (user.getRole().equals("ADMIN")) {
        //     return schemaProvider.getSchema().entities().keySet().stream().toList();
        // }
        // return user.getAllowedEntityTypes();
        
        // Option 3: Return empty list to force explicit entity type specification
        // return Collections.emptyList();
        
        // Default: Return empty list (force explicit specification)
        log.warn("getAllowedEntityTypesForUser not implemented - returning empty list");
        return Collections.emptyList();
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
import com.ai.infrastructure.intent.action.AIActionMetaData;
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
    void executeAction_withValidParams_shouldSucceed() {
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
    void executeAction_withMissingQuery_shouldFail() {
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
    void executeAction_withMissingEntityTypes_shouldFail() {
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
    void executeAction_withQueryOptions_shouldPassThrough() {
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
    void executeAction_withServiceException_shouldReturnFailure() {
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

## 🔐 Access Control: Entity Type Filtering

### Overview

The Relationship Query ActionHandler supports **entity type-level access control**, allowing you to control which entity types each user can query. This is critical for:

- **Multi-tenant applications** - Users can only query their tenant's entities
- **Role-based access** - Different roles have access to different entity types
- **Data classification** - Sensitive entities require special permissions
- **Compliance** - Ensure users can't access restricted data

### How Framework Users Control Access

⚠️ **CRITICAL QUESTION:** If the framework provides `RelationshipQueryActionHandler`, how do framework users control access?

**Answer:** The framework uses **SPI (Service Provider Interface)** pattern with `RelationshipQueryAccessControlPolicy`.

**Solution Architecture:**

```
┌─────────────────────────────────────────────────────────────────────┐
│  FRAMEWORK PROVIDES                                                 │
│  ═══════════════════════════════════════════════════════════════════│
│  RelationshipQueryActionHandler (framework implementation)           │
│    │                                                                 │
│    ├─→ Uses ObjectProvider<RelationshipQueryAccessControlPolicy>   │
│    ├─→ Calls policy.canUserExecuteRelationshipQueries()             │
│    ├─→ Calls policy.canUserQueryEntityType()                        │
│    └─→ Calls policy.canUserAccessEntity()                            │
└────────────────────────────┬────────────────────────────────────────┘
                             │
                             ▼
┌─────────────────────────────────────────────────────────────────────┐
│  USER IMPLEMENTS                                                     │
│  ═══════════════════════════════════════════════════════════════════│
│  RelationshipQueryAccessControlPolicy (user implementation)          │
│    │                                                                 │
│    ├─→ canUserExecuteRelationshipQueries() - Your business logic    │
│    ├─→ canUserQueryEntityType() - Your business logic               │
│    └─→ canUserAccessEntity() - Your business logic                  │
└─────────────────────────────────────────────────────────────────────┘
```

**Quick Start:**

1. **Framework provides handler** - You don't need to implement `ActionHandler`
2. **You implement SPI** - Create `RelationshipQueryAccessControlPolicy` bean
3. **Framework uses your SPI** - Handler automatically discovers and uses it

**Example Implementation:**

```java
// Your application code
@Component
@RequiredArgsConstructor
public class MyRelationshipQueryAccessControlPolicy 
        implements RelationshipQueryAccessControlPolicy {
    
    private final PermissionService permissionService;
    
    @Override
    public boolean canUserExecuteRelationshipQueries(String userId) {
        return permissionService.hasPermission(userId, "relationship_query:execute");
    }
    
    @Override
    public boolean canUserQueryEntityType(String userId, String entityType) {
        return permissionService.hasPermission(userId, "relationship_query:" + entityType);
    }
    
    @Override
    public List<String> getAllowedEntityTypesForUser(String userId) {
        return permissionService.getEntityTypesWithPermission(userId, "relationship_query:");
    }
}
```

**That's it!** The framework handler automatically uses your implementation.

**For complete details, see:** [`RELATIONSHIP_QUERY_ACCESS_CONTROL_SPI.md`](./RELATIONSHIP_QUERY_ACCESS_CONTROL_SPI.md)

---

### How It Works

**Access control happens at multiple levels:**

1. **Orchestrator Level** (Framework-level check)
   - `AIAccessControlService.checkAccess()` validates general access
   - Uses `EntityAccessPolicy` to check if user can execute relationship queries

2. **Action Handler Level** (Action-specific check)
   - `validateActionAllowed()` - Checks if user can execute relationship queries
   - `filterAllowedEntityTypes()` - Filters entity types based on user permissions

3. **Query Execution Level** (Result filtering)
   - Results can be further filtered based on entity-level access control
   - Uses `EntityAccessPolicy` to filter individual results

### Implementation Patterns

#### Pattern 1: Role-Based Entity Type Access

**Example:** Admins can query all entities, regular users can only query specific entities.

```java
@Component
public class RelationshipQueryActionHandler implements ActionHandler {
    
    @Autowired
    private UserService userService;
    
    private boolean canUserQueryEntityType(String userId, String entityType) {
        User user = userService.getUser(userId);
        
        // Admins can query all entity types
        if (user.getRole().equals("ADMIN")) {
            return true;
        }
        
        // Regular users can only query specific entity types
        Set<String> allowedTypes = user.getAllowedEntityTypes();
        return allowedTypes.contains(entityType);
    }
    
    private List<String> getAllowedEntityTypesForUser(String userId) {
        User user = userService.getUser(userId);
        
        if (user.getRole().equals("ADMIN")) {
            // Return all entity types
            return schemaProvider.getSchema().entities().keySet().stream()
                .sorted()
                .toList();
        }
        
        // Return user's allowed entity types
        return new ArrayList<>(user.getAllowedEntityTypes());
    }
}
```

**User Configuration:**

```java
@Entity
public class User {
    @Id private UUID id;
    private String role;  // "ADMIN", "USER", "ANALYST"
    
    @ElementCollection
    @CollectionTable(name = "user_allowed_entity_types")
    private Set<String> allowedEntityTypes;  // ["customer", "order", "product"]
}
```

#### Pattern 2: Permission-Based Access Control

**Example:** Users have explicit permissions for each entity type.

```java
@Component
public class RelationshipQueryActionHandler implements ActionHandler {
    
    @Autowired
    private PermissionService permissionService;
    
    private boolean canUserQueryEntityType(String userId, String entityType) {
        // Check if user has permission: "relationship_query:customer"
        String permission = "relationship_query:" + entityType;
        return permissionService.hasPermission(userId, permission);
    }
    
    private List<String> getAllowedEntityTypesForUser(String userId) {
        // Get all entity types user has relationship_query permission for
        return permissionService.getResourcesWithPermission(
            userId, 
            "relationship_query:"
        ).stream()
            .map(permission -> permission.substring("relationship_query:".length()))
            .toList();
    }
}
```

**Permission Structure:**

```
User Permissions:
- user-123: ["relationship_query:customer", "relationship_query:order"]
- user-456: ["relationship_query:product", "relationship_query:category"]
- admin-789: ["relationship_query:*"]  // Wildcard = all entity types
```

#### Pattern 3: Tenant-Based Access Control

**Example:** Multi-tenant application where users can only query their tenant's entities.

```java
@Component
public class RelationshipQueryActionHandler implements ActionHandler {
    
    @Autowired
    private TenantService tenantService;
    
    @Autowired
    private EntityTenantMappingService tenantMappingService;
    
    private boolean canUserQueryEntityType(String userId, String entityType) {
        String userTenant = tenantService.getTenantId(userId);
        
        // Check if this entity type is accessible to the user's tenant
        return tenantMappingService.isEntityTypeAccessible(userTenant, entityType);
    }
    
    private List<String> getAllowedEntityTypesForUser(String userId) {
        String userTenant = tenantService.getTenantId(userId);
        
        // Return entity types accessible to user's tenant
        return tenantMappingService.getAccessibleEntityTypes(userTenant);
    }
}
```

**Tenant Configuration:**

```yaml
tenants:
  tenant-a:
    accessible-entity-types: ["customer", "order", "product"]
  tenant-b:
    accessible-entity-types: ["customer", "order"]  # No product access
```

#### Pattern 4: Data Classification-Based Access

**Example:** Sensitive entities require special permissions.

```java
@Component
public class RelationshipQueryActionHandler implements ActionHandler {
    
    @Autowired
    private EntityClassificationService classificationService;
    
    @Autowired
    private PermissionService permissionService;
    
    private boolean canUserQueryEntityType(String userId, String entityType) {
        EntityClassification classification = classificationService.getClassification(entityType);
        
        switch (classification) {
            case PUBLIC:
                // Everyone can query public entities
                return true;
                
            case INTERNAL:
                // Authenticated users can query internal entities
                return userId != null;
                
            case SENSITIVE:
                // Requires special permission
                return permissionService.hasPermission(userId, "relationship_query:sensitive");
                
            case RESTRICTED:
                // Requires admin role
                return permissionService.hasRole(userId, "ADMIN");
                
            default:
                return false;
        }
    }
}
```

**Entity Classification:**

```java
@Entity
@AICapable(entityType = "customer")
@EntityClassification(level = Classification.SENSITIVE)
public class Customer {
    // Sensitive data - requires special permission
}

@Entity
@AICapable(entityType = "product")
@EntityClassification(level = Classification.PUBLIC)
public class Product {
    // Public data - everyone can query
}
```

#### Pattern 5: Hybrid Access Control (Recommended)

**Example:** Combine multiple access control strategies.

```java
@Component
public class RelationshipQueryActionHandler implements ActionHandler {
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private PermissionService permissionService;
    
    @Autowired
    private EntityClassificationService classificationService;
    
    private boolean canUserQueryEntityType(String userId, String entityType) {
        User user = userService.getUser(userId);
        
        // Step 1: Check role-based access
        if (user.getRole().equals("ADMIN")) {
            return true;  // Admins can query all
        }
        
        // Step 2: Check data classification
        EntityClassification classification = classificationService.getClassification(entityType);
        if (classification == EntityClassification.RESTRICTED) {
            return false;  // Restricted entities require admin
        }
        
        // Step 3: Check explicit permissions
        if (permissionService.hasPermission(userId, "relationship_query:" + entityType)) {
            return true;
        }
        
        // Step 4: Check user's allowed entity types
        if (user.getAllowedEntityTypes().contains(entityType)) {
            return true;
        }
        
        // Default: deny
        return false;
    }
}
```

### Integration with EntityAccessPolicy

**The orchestrator also checks access via `EntityAccessPolicy`:**

```java
@Component
public class MyEntityAccessPolicy implements EntityAccessPolicy {
    
    @Override
    public boolean canUserAccessEntity(String userId, Map<String, Object> entityContext) {
        String resourceId = (String) entityContext.get("resourceId");
        String operationType = (String) entityContext.get("operationType");
        
        // Check relationship query action access
        if (resourceId.equals("action:relationship_query") && 
            operationType.equals("EXECUTE")) {
            
            // Extract entity types from metadata if available
            @SuppressWarnings("unchecked")
            Map<String, Object> metadata = (Map<String, Object>) entityContext.get("metadata");
            if (metadata != null) {
                @SuppressWarnings("unchecked")
                List<String> entityTypes = (List<String>) metadata.get("entityTypes");
                
                if (entityTypes != null) {
                    // Check each entity type
                    for (String entityType : entityTypes) {
                        if (!canUserQueryEntityType(userId, entityType)) {
                            return false;
                        }
                    }
                }
            }
            
            // General check: user can execute relationship queries
            return permissionService.hasPermission(userId, "relationship_query:execute");
        }
        
        return false;
    }
    
    private boolean canUserQueryEntityType(String userId, String entityType) {
        // Your access control logic
        return permissionService.hasPermission(userId, "relationship_query:" + entityType);
    }
}
```

### Result-Level Access Control

**After query execution, filter results based on entity-level access:**

```java
@Override
public ActionResult executeAction(Map<String, Object> params, String userId) {
    // ... execute query ...
    
    RAGResponse response = queryService.execute(query, allowedEntityTypes, options);
    
    // Filter results based on entity-level access control
    List<RAGDocument> filteredDocuments = response.getDocuments().stream()
        .filter(doc -> canUserAccessEntity(userId, doc))
        .toList();
    
    // Build response with filtered results
    RAGResponse filteredResponse = response.toBuilder()
        .documents(filteredDocuments)
        .totalResults(filteredDocuments.size())
        .build();
    
    return ActionResult.builder()
        .success(true)
        .data(buildResultData(filteredResponse))
        .build();
}

private boolean canUserAccessEntity(String userId, RAGDocument document) {
    // Use EntityAccessPolicy to check individual entity access
    String entityType = document.getMetadata().get("entityType");
    String entityId = document.getId();
    
    // Check via EntityAccessPolicy
    Map<String, Object> entityContext = Map.of(
        "resourceId", "rag:" + entityType,
        "operationType", "READ",
        "entityId", entityId,
        "entityType", entityType
    );
    
    return entityAccessPolicy.canUserAccessEntity(userId, entityContext);
}
```

### Configuration

**Enable access control:**

```yaml
ai:
  infrastructure:
    relationship:
      enabled: true
      enable-orchestrator-integration: true
      
      # Access control settings
      enable-entity-type-filtering: true  # Filter entity types in handler
      require-explicit-entity-types: false  # If true, empty entityTypes list = deny
      
    access:
      # Enable EntityAccessPolicy
      enable-access-control: true
      fail-closed: true  # Deny on policy exception
```

### Testing Access Control

```java
@Test
void shouldFilterEntityTypesBasedOnUserPermissions() {
    // Arrange
    String userId = "user-with-limited-access";
    Map<String, Object> params = Map.of(
        "query", "Find customers and orders",
        "entityTypes", List.of("customer", "order", "product")  // User can only access customer, order
    );
    
    // Mock permission service
    when(permissionService.hasPermission(userId, "relationship_query:customer"))
        .thenReturn(true);
    when(permissionService.hasPermission(userId, "relationship_query:order"))
        .thenReturn(true);
    when(permissionService.hasPermission(userId, "relationship_query:product"))
        .thenReturn(false);  // User cannot access product
    
    // Act
    ActionResult result = handler.executeAction(params, userId);
    
    // Assert
    assertThat(result.isSuccess()).isTrue();
    
    // Verify only allowed entity types were passed to query service
    verify(queryService).execute(
        eq("Find customers and orders"),
        eq(List.of("customer", "order")),  // product filtered out
        any(QueryOptions.class)
    );
}

@Test
void shouldDenyWhenNoEntityTypesAllowed() {
    // Arrange
    String userId = "user-with-no-access";
    Map<String, Object> params = Map.of(
        "query", "Find products",
        "entityTypes", List.of("product")
    );
    
    when(permissionService.hasPermission(userId, "relationship_query:product"))
        .thenReturn(false);
    
    // Act
    ActionResult result = handler.executeAction(params, userId);
    
    // Assert
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getMessage()).contains("Access denied");
    assertThat(result.getData()).containsEntry("error", "ACCESS_DENIED");
    
    // Verify query service was never called
    verify(queryService, never()).execute(anyString(), anyList(), any());
}
```

### Best Practices

1. **Fail Closed** - If access control check fails, deny access
2. **Log Denials** - Log all access denials for audit purposes
3. **Cache Permissions** - Cache user permissions to avoid repeated checks
4. **Validate Early** - Filter entity types before query execution (saves LLM calls)
5. **Result Filtering** - Also filter results after query execution (defense in depth)
6. **Clear Error Messages** - Tell users which entity types they can't access

### Summary

**Access control for entity types works at three levels:**

1. **Orchestrator Level** - `EntityAccessPolicy` checks general access
2. **Handler Level** - `filterAllowedEntityTypes()` filters entity types before query
3. **Result Level** - Filter individual results based on entity-level access

**Implementation:**
- Override `canUserQueryEntityType()` in `RelationshipQueryActionHandler`
- Implement your business logic (role-based, permission-based, tenant-based, etc.)
- The handler automatically filters entity types before query execution
- Results can be further filtered for defense in depth

---

## 🎨 Usage Patterns

### Pattern 1: Direct (Standalone)

**Use When:**
- Simple apps focused on relationship queries
- Need full control over QueryOptions
- Don't need orchestrator features (behavior insights, PII, access control)
- Performance-critical paths (fewer layers)

**Access Control in Direct Pattern:**

When using the direct pattern, you need to implement access control manually:

```java
@Service
public class CustomerSearchService {
    
    @Autowired
    private ReliableRelationshipQueryService queryService;
    
    @Autowired
    private PermissionService permissionService;  // Your permission service
    
    public List<Customer> findCustomers(String userId, String query) {
        // Step 1: Filter entity types based on user permissions
        List<String> allowedEntityTypes = getAllowedEntityTypes(userId);
        
        if (allowedEntityTypes.isEmpty()) {
            throw new AccessDeniedException("No entity types accessible");
        }
        
        // Step 2: Execute query with filtered entity types
        RAGResponse response = queryService.execute(
            query,
            allowedEntityTypes,  // Only query allowed entity types
            QueryOptions.defaults()
        );
        
        // Step 3: Filter results based on entity-level access (optional)
        List<RAGDocument> filtered = response.getDocuments().stream()
            .filter(doc -> canUserAccessEntity(userId, doc))
            .toList();
        
        return convertToCustomers(filtered);
    }
    
    private List<String> getAllowedEntityTypes(String userId) {
        // Your access control logic
        return permissionService.getEntityTypesWithPermission(
            userId, 
            "relationship_query:"
        );
    }
    
    private boolean canUserAccessEntity(String userId, RAGDocument doc) {
        // Your entity-level access control
        return true;  // Implement your logic
    }
}
```

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
| **LLM Calls** | 1 (query planning only) | 2 (intent extraction + query planning) |
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
RelationshipQueryActionHandler.executeAction(params)
  ↓
queryService.execute(query, ["user"], options)  // ✅ Uses extracted entities
  └─→ LLM Call #2: Query planning (still needed for relationships/filters)
  └─→ Direct JPQL generation with user schema
  ↓
Results returned

Total LLM Calls: 2 (intent extraction + query planning)
Token Usage: LOW (orchestrator already extracted entities, planner uses limited schemas)
Performance: BEST (no redundant entity type detection)
```

**Key Insight:** Orchestrated pattern requires 2 LLM calls (intent extraction + query planning), but the entity type optimization reduces token usage in the query planning call.

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
| **Orchestrated** | 2 | Low (auto-optimized) ✅ | Full ✅ |

**Key Insight:** Orchestrated pattern requires 2 LLM calls (intent extraction + query planning), but entity types are extracted once and reused, reducing token usage in the query planning call.

### Effort Required
- **Small** - Just bridging existing capability + adding simple overloads
- **Estimated:** 2-3 days of development (includes IntentQueryExtractor enhancement)
- **No re-invention** - Reuses all existing hybrid search logic

### Value Delivered
- ✅ **Simpler API** for direct usage (no entity types needed!)
- ✅ **More efficient** orchestrated usage (entity types passed from intent)
- ✅ Both usage patterns supported (direct + orchestrated)
- ✅ Behavior insights available in orchestrated mode
- ✅ Unified security/PII/access control
- ✅ No breaking changes to existing users

---

**Document Version:** 1.3  
**Created:** 2025-12-30  
**Updated:** 2025-12-30  
**Status:** Ready for Implementation  
**Owner:** AI Infrastructure Team  

**Change Log:**
- v1.0: Initial document with orchestrator integration plan
- v1.1: Added existing capability clarification and RealAPI tests
- v1.2: Added simplified API (entity types optional) + efficiency analysis
- v1.3: **CRITICAL FIXES** - Fixed ActionHandler method signatures, added IntentQueryExtractor enhancement requirement, corrected interface methods, corrected LLM call count (2 calls for orchestrated pattern)

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
- Orchestrated pattern requires 2 LLM calls (intent extraction + query planning) but reduces token usage via entity type optimization
- **CRITICAL:** Must enhance IntentQueryExtractor FIRST before implementing handler (Phase 0.5)
- ActionHandler interface uses `executeAction(Map<String, Object> params, String userId)` - parameters in this order
- ActionHandler also requires `getActionMetadata()`, `handleError()`, `validateActionAllowed()`, and `getConfirmationMessage()` methods

