# RAG and AI-Core Module Separation - Implementation Architecture Change Plan

**Version:** 1.0  
**Date:** January 2026  
**Status:** Comprehensive Implementation Plan  
**Goal:** Achieve clean separation between RAG service, AI-Core, and Orchestrator responsibilities

---

## Executive Summary

This document outlines a comprehensive plan to:
1. **Make AI-Core less RAG-related** - Remove RAG-specific interfaces and dependencies from core
2. **Make RAG a complete RAG service responsibility** - RAG module owns all RAG-related concerns
3. **Remove dependency between PII detection services and RAG** - PII detection becomes orchestrator responsibility

**Philosophy:** Following the AI Fabric Framework principles of minimalism, separation of concerns, and fail-closed security.

---

## Table of Contents

1. [Current State Analysis](#current-state-analysis)
2. [Target Architecture](#target-architecture)
3. [Detailed Implementation Plan](#detailed-implementation-plan)
4. [Migration Strategy](#migration-strategy)
5. [Testing Strategy](#testing-strategy)
6. [Risk Assessment](#risk-assessment)
7. [Rollback Plan](#rollback-plan)

---

## Current State Analysis

### 1.1 RAG-Related Code in AI-Core

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/`

**Files:**
- `VectorDatabaseService.java` - Interface for vector database operations
- `SearchableEntityVectorDatabaseService.java` - Decorator for entity synchronization

**Dependencies:**
- Core services depend on `VectorDatabaseService`:
  - `VectorSearchService`
  - `VectorManagementService`
  - `KnowledgeBaseOverviewService`
  - `UserDataDeletionService`
  - `RemoveVectorActionHandler`
  - `ClearVectorIndexActionHandler`
  - `VectorDatabaseServiceAdapter`

**Problem:** Core module contains RAG-specific abstractions that should be in the RAG module.

### 1.2 PII Detection in RAG Service

**Location:** `ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/service/RAGService.java`

**Current Dependencies:**
```java
private final PIIDetectionService piiDetectionService;  // Line 107

// Used in multiple places:
PIIDetectionResult piiDetectionResult = piiDetectionService.detectAndProcess(request.getQuery());  // Line 191, 290, 392
```

**Problem:** RAG service directly depends on PII detection, violating separation of concerns. PII detection should be handled by the orchestrator before calling RAG.

### 1.3 Orchestrator PII Detection

**Location:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/`

**Current Flow:**
1. `PIIDetectionStep` (Order 30) - Detects and redacts PII from input query
2. `IntentHandlingStep` (Order 60) - Calls RAG service
3. `ResponseSanitizationStep` (Order 90) - Sanitizes output

**Problem:** RAG service performs PII detection again, duplicating work and creating tight coupling.

### 1.4 Dependency Graph

```
┌─────────────────┐
│   Orchestrator  │
│   (ai-core)     │
└────────┬────────┘
         │
         ├───► PIIDetectionService (ai-core)
         │
         └───► RAGProvider SPI (ai-core)
                 │
                 └───► RAGService (ai-rag)
                         │
                         └───► PIIDetectionService (ai-core) ❌ WRONG
```

**Issues:**
- RAG service depends on PII detection (should not)
- Core contains RAG interfaces (should be in RAG module)
- Circular dependency risk

---

## Target Architecture

### 2.1 Separation of Concerns

```
┌─────────────────────────────────────────────────────────────┐
│                    Orchestrator (ai-core)                    │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │   Security   │→ │AccessControl │→ │PIIDetection  │      │
│  │   (Order 10) │  │  (Order 20)  │  │  (Order 30)  │      │
│  └──────────────┘  └──────────────┘  └──────────────┘      │
│                                                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │IntentExtract │→ │IntentHandling │→ │ResponseSanit │      │
│  │  (Order 50)  │  │  (Order 60)  │  │  (Order 90)  │      │
│  └──────────────┘  └──────┬───────┘  └──────────────┘      │
│                           │                                   │
└───────────────────────────┼───────────────────────────────────┘
                             │
                             │ (calls with processed query)
                             ▼
┌─────────────────────────────────────────────────────────────┐
│                    RAG Service (ai-rag)                     │
│  • Receives already-processed queries (no PII)              │
│  • Pure RAG operations: retrieval, embedding, search       │
│  • No PII detection dependencies                           │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 Module Responsibilities

#### AI-Core Module
**Responsibilities:**
- Orchestration and pipeline management
- Security, access control, compliance
- **PII Detection** (orchestrator responsibility)
- Intent extraction and handling
- Response sanitization
- Core services (embedding, search abstractions)

**Should NOT contain:**
- RAG-specific interfaces (`VectorDatabaseService`)
- RAG implementation details
- Direct RAG service dependencies (only SPI)

#### RAG Module
**Responsibilities:**
- All RAG-related interfaces (`VectorDatabaseService`, `RAGProvider`)
- RAG service implementation
- Vector database operations
- Embedding integration for RAG
- Search and retrieval logic

**Should NOT contain:**
- PII detection dependencies
- Orchestration logic
- Security/access control logic

### 2.3 PII Detection Flow

**Before (Current):**
```
User Query
    ↓
Orchestrator.PIIDetectionStep → detects PII
    ↓
IntentHandlingStep → calls RAGService
    ↓
RAGService → detects PII AGAIN ❌
    ↓
Returns results
```

**After (Target):**
```
User Query
    ↓
Orchestrator.PIIDetectionStep → detects & redacts PII
    ↓
IntentHandlingStep → calls RAGService with processed query
    ↓
RAGService → pure RAG operations (no PII detection) ✅
    ↓
Returns results
    ↓
Orchestrator.ResponseSanitizationStep → sanitizes output
```

---

## Detailed Implementation Plan

### Phase 1: Move RAG Interfaces from Core to RAG Module

#### Step 1.1: Move VectorDatabaseService Interface

**Source:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/VectorDatabaseService.java`  
**Target:** `ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/VectorDatabaseService.java`

**Actions:**
1. Create new file in RAG module
2. Copy interface definition
3. Update package name: `com.ai.infrastructure.rag`
4. Update all imports in core module
5. Add RAG module dependency to core (if not already present)

**Files to Update in Core:**
- `VectorSearchService.java`
- `VectorManagementService.java`
- `KnowledgeBaseOverviewService.java`
- `UserDataDeletionService.java`
- `RemoveVectorActionHandler.java`
- `ClearVectorIndexActionHandler.java`
- `VectorDatabaseServiceAdapter.java`
- `SearchableEntityVectorDatabaseService.java` (move this too)

**Files to Update in Vector Database Modules:**
- `victor-databases/ai-infrastructure-vector-memory/InMemoryVectorDatabaseService.java`
- `victor-databases/ai-infrastructure-vector-milvus/MilvusVectorDatabaseService.java`
- `victor-databases/ai-infrastructure-vector-qdrant/QdrantVectorDatabaseService.java`
- `victor-databases/ai-infrastructure-vector-weaviate/WeaviateVectorDatabaseService.java`
- `victor-databases/ai-infrastructure-vector-pinecone/PineconeVectorDatabaseService.java`
- `victor-databases/ai-infrastructure-vector-lucene/LuceneVectorDatabaseService.java`

**Note:** All vector database implementations will need to update their imports to reference the interface from the RAG module.

**Dependencies:**
- Ensure RAG module exports this interface
- Update `pom.xml` if needed

#### Step 1.2: Move SearchableEntityVectorDatabaseService

**Source:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/SearchableEntityVectorDatabaseService.java`  
**Target:** `ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/SearchableEntityVectorDatabaseService.java`

**Actions:**
1. Move file to RAG module
2. Update package name
3. Update imports in core module
4. This decorator can stay in RAG module as it's RAG-specific

**Note:** This service depends on `AISearchableEntityStorageStrategy` from core. This is acceptable as it's a storage abstraction, not RAG-specific.

#### Step 1.3: Update Core Module Imports

**Search Pattern:** `import com.ai.infrastructure.rag.VectorDatabaseService;`  
**Replace With:** `import com.ai.infrastructure.rag.VectorDatabaseService;` (same, but from RAG module)

**Files to Update:**
- All files listed in Step 1.1
- Test files
- Configuration files

#### Step 1.4: Update Module Dependencies

**In `ai-infrastructure-core/pom.xml`:**
```xml
<dependency>
    <groupId>com.ai.infrastructure</groupId>
    <artifactId>ai-infrastructure-rag</artifactId>
    <version>${project.version}</version>
</dependency>
```

**Ensure:** RAG module is properly configured to export its interfaces.

#### Step 1.5: Update Vector Database Module Imports

**Vector Database Implementations:**
All vector database implementations in `victor-databases/` modules implement `VectorDatabaseService`. After moving the interface to the RAG module, these implementations need to:

1. **Update Module Dependencies:**
   Each vector database module's `pom.xml` should depend on `ai-infrastructure-rag` instead of (or in addition to) `ai-infrastructure-core`:
   ```xml
   <dependency>
       <groupId>com.ai.infrastructure</groupId>
       <artifactId>ai-infrastructure-rag</artifactId>
       <version>${project.version}</version>
   </dependency>
   ```

2. **Verify Imports:**
   Since the package name remains the same (`com.ai.infrastructure.rag`), the import statement doesn't change:
   ```java
   import com.ai.infrastructure.rag.VectorDatabaseService;  // Same import, but from RAG module
   ```

**Affected Modules:**
- `ai-infrastructure-vector-memory`
- `ai-infrastructure-vector-milvus`
- `ai-infrastructure-vector-qdrant`
- `ai-infrastructure-vector-weaviate`
- `ai-infrastructure-vector-pinecone`
- `ai-infrastructure-vector-lucene`

**Note:** This is a low-risk change since the package name remains the same. Only module dependencies need updating.

---

### Phase 2: Remove PII Detection from RAG Service

#### Step 2.1: Remove PII Detection Dependency

**File:** `ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/service/RAGService.java`

**Actions:**
1. Remove field: `private final PIIDetectionService piiDetectionService;`
2. Remove from constructor
3. Remove all calls to `piiDetectionService.detectAndProcess()`
4. Remove all calls to `piiDetectionService.analyze()`

**Lines to Remove/Modify:**
- Line 107: Remove field declaration
- Line 191: Remove PII detection call
- Line 290: Remove PII detection call
- Line 392: Remove PII detection call
- Line 518: Remove PII detection call

#### Step 2.2: Update RAGService Methods

**Method: `performRag(RAGRequest request)`**

**Before:**
```java
PIIDetectionResult piiDetectionResult = piiDetectionService.detectAndProcess(request.getQuery());
String sanitizedQuery = piiDetectionResult.getProcessedQuery();
String embeddingQuery = resolveEmbeddingQuery(request, sanitizedQuery);
```

**After:**
```java
// Assume query is already processed by orchestrator
String query = request.getQuery();
String embeddingQuery = resolveEmbeddingQuery(request, query);
```

**Method: `performRAGQuery(RAGRequest request)`**

**Before:**
```java
PIIDetectionResult piiDetectionResult = piiDetectionService.detectAndProcess(request.getQuery());
String sanitizedQuery = piiDetectionResult.getProcessedQuery();
```

**After:**
```java
// Assume query is already processed by orchestrator
String query = request.getQuery();
```

**Method: `performRAGQuery(String query, String entityType, int limit)`**

**Before:**
```java
PIIDetectionResult piiDetectionResult = piiDetectionService.detectAndProcess(query);
String sanitizedQuery = piiDetectionResult.getProcessedQuery();
```

**After:**
```java
// Assume query is already processed by orchestrator
// Use query directly
```

#### Step 2.3: Update RAGResponse Building

**Remove PII-related metadata:**
- Remove `piiDetectionResult` from response building
- Remove PII detection metadata aggregation
- Keep `originalQuery` field (but it's already processed)

**Method: `buildAggregatedMetadata()`**

**Before:**
```java
aggregatedMetadata.put(METADATA_KEY_PII_DETECTION, Map.of(
    METADATA_KEY_DETECTED, piiDetectionResult.isPiiDetected(),
    // ...
));
```

**After:**
```java
// Remove PII detection metadata
// PII information should come from orchestrator context, not RAG service
```

**Note:** If PII information is needed in the response, it should be passed from orchestrator via `RAGRequest.metadata`.

#### Step 2.4: Update RAGProvider SPI Documentation

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/spi/RAGProvider.java`

**Update JavaDoc:**
- Remove mention of PII detection being handled internally (line 25, 90, 112)
- Update to state that queries should be pre-processed by orchestrator

**Before:**
```java
/**
 * <li>Implementations SHOULD handle PII detection internally</li>
 * ...
 * <li>Process query for PII (if configured)</li>
 */
```

**After:**
```java
/**
 * <li>Implementations receive pre-processed queries (PII already handled by orchestrator)</li>
 * ...
 * <li>Query is pre-processed by orchestrator (PII redacted, sanitized)</li>
 */
```

#### Step 2.5: Update RAGRequest Contract

**Enhancement:** Add documentation that query should be pre-processed.

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/dto/RAGRequest.java`

**Add JavaDoc:**
```java
/**
 * The query string. Should be pre-processed (PII redacted, sanitized)
 * by the orchestrator before calling RAG service.
 * 
 * <p>RAG service assumes the query is ready for embedding and search.
 * No additional PII detection or sanitization is performed.</p>
 */
private String query;
```

#### Step 2.6: Update RAGResponse Contract

**Remove:**
- `piiDetectionResult` field (if present)

**Keep:**
- `originalQuery` field (for reference, but it's already processed)

**Add JavaDoc:**
```java
/**
 * The original query as received. Note: This query has been processed
 * by the orchestrator (PII redacted, sanitized) before RAG operations.
 */
private String originalQuery;
```

---

### Phase 3: Update Orchestrator to Handle PII Before RAG

#### Step 3.1: Ensure PIIDetectionStep Processes Query

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/PIIDetectionStep.java`

**Current State:** Already processes query correctly.

**Verification:**
- Step order: 30 (before IntentHandlingStep at 60)
- Updates `context.processedQuery` with redacted query
- Stores detected PII types in context

**Action:** No changes needed, but add documentation.

#### Step 3.2: Update IntentHandlingStep to Use Processed Query

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/IntentHandlingStep.java`

**Current State:** Uses `intent.getIntentOrAction()` or `optimizedQuery`.

**Update:** Use `context.getProcessedQuery()` instead of original query.

**Method: `handleInformation(Intent intent, OrchestrationContext context)`**

**Before:**
```java
String optimizedQuery = StringUtils.hasText(intent.getOptimizedQuery()) ? intent.getOptimizedQuery() : null;
String query = StringUtils.hasText(optimizedQuery) ? optimizedQuery : intent.getIntentOrAction();
```

**After:**
```java
// Priority: optimizedQuery > processedQuery (from PII step) > intent query
String optimizedQuery = StringUtils.hasText(intent.getOptimizedQuery()) ? intent.getOptimizedQuery() : null;
String processedQuery = context.getProcessedQuery(); // From PIIDetectionStep
String query = StringUtils.hasText(optimizedQuery) 
    ? optimizedQuery 
    : (StringUtils.hasText(processedQuery) ? processedQuery : intent.getIntentOrAction());
```

**Note:** We need to access `PipelineContext` in `IntentHandlingStep`. Currently it only receives `OrchestrationContext`. This needs to be fixed.

#### Step 3.3: Fix PipelineContext Access in IntentHandlingStep

**Problem:** `IntentHandlingStep.process()` receives `PipelineContext`, but `handleInformation()` only receives `OrchestrationContext`.

**Solution:** Pass `PipelineContext` to helper methods or extract processed query earlier.

**Update Method Signature:**
```java
private OrchestrationResult handleInformation(Intent intent, OrchestrationContext context, PipelineContext pipelineContext) {
    // Use pipelineContext.getProcessedQuery()
}
```

**Update Call Site:**
```java
case INFORMATION -> handleInformation(intent, orchContext, context);
```

#### Step 3.4: Pass PII Information via Metadata

**Enhancement:** Pass PII detection results to RAG service via metadata (if needed for logging/auditing).

**In IntentHandlingStep:**
```java
if (!context.getDetectedPiiTypes().isEmpty()) {
    metadata.put("piiDetectedTypes", context.getDetectedPiiTypes());
    metadata.put("piiProcessed", true);
}
```

**Note:** RAG service should not use this for processing, only for metadata/auditing.

---

### Phase 4: Update Response Sanitization

#### Step 4.1: Verify ResponseSanitizationStep

**File:** `ai-infrastructure-core/src/main/java/com/ai/infrastructure/intent/orchestration/pipeline/steps/ResponseSanitizationStep.java`

**Current State:** Already handles output PII detection.

**Action:** Ensure it properly merges input PII detection info from context.

**Update:** Merge `context.getDetectedPiiTypes()` with output PII detection results.

---

### Phase 5: Update Tests

#### Step 5.1: Update RAGService Tests

**Files:**
- `ai-infrastructure-rag/src/test/java/com/ai/infrastructure/rag/service/RAGServiceTest.java`
- `ai-infrastructure-rag/src/test/java/com/ai/infrastructure/rag/service/RAGServiceOptimizedQueryTest.java`

**Actions:**
1. Remove PII detection mocks
2. Remove PII detection assertions
3. Update test queries to be pre-processed (no PII)
4. Test that RAG service accepts already-processed queries

#### Step 5.2: Update Orchestrator Tests

**File:** `ai-infrastructure-core/src/test/java/com/ai/infrastructure/intent/orchestration/RAGOrchestratorTest.java`

**Actions:**
1. Verify PII detection happens in PIIDetectionStep
2. Verify processed query is passed to RAG service
3. Verify RAG service is called with processed query (no PII)
4. Add tests for PII detection → RAG flow

#### Step 5.3: Update Integration Tests

**Files:**
- `ai-infrastructure-core/src/test/java/com/ai/infrastructure/integration/RAGIntegrationFlowTest.java`

**Actions:**
1. Test full flow: PII detection → RAG → Response sanitization
2. Verify no duplicate PII detection
3. Verify PII information flows correctly

---

### Phase 6: Update Documentation

#### Step 6.1: Update RAG Module Documentation

**Files:**
- `ai-infrastructure-rag/README.md`
- API documentation

**Content:**
- Document that RAG service expects pre-processed queries
- Document that PII detection is orchestrator responsibility
- Update examples to show pre-processed queries

#### Step 6.2: Update Orchestrator Guide

**File:** `Final_Documentation/System_Archtecture_Guides/Orchestrator_User_Guide.md`

**Updates:**
- Clarify PII detection flow
- Document that RAG service receives processed queries
- Update pipeline step documentation

#### Step 6.3: Update Core Module Documentation

**Files:**
- `ai-infrastructure-core/README.md`
- `ai-infrastructure-core/AI_CORE_USER_GUIDE.md`

**Content:**
- Remove RAG-specific documentation (moved to RAG module)
- Document orchestrator responsibilities
- Update examples

---

## Migration Strategy

### Approach: Incremental Migration

**Principle:** Minimize risk by migrating in small, testable steps.

### Phase Order

1. **Phase 1** (Low Risk): Move interfaces - no behavior change
2. **Phase 2** (Medium Risk): Remove PII from RAG - requires testing
3. **Phase 3** (Medium Risk): Update orchestrator - requires integration testing
4. **Phase 4** (Low Risk): Response sanitization - mostly verification
5. **Phase 5** (Low Risk): Tests - ensure everything works
6. **Phase 6** (Low Risk): Documentation - no code changes

### Backward Compatibility

**Strategy:** Maintain backward compatibility during migration, then remove in next major version.

**Temporary Compatibility Layer:**
- Keep old `VectorDatabaseService` in core with deprecation warning
- Delegate to new location in RAG module
- Remove in next major version

**For PII Detection:**
- RAG service can accept queries with or without PII
- If PII detected in RAG service, log warning but continue
- Remove in next major version

**Note:** Per AI Fabric Framework philosophy, we prefer clean breaks over backward compatibility. However, for safety, we can add deprecation warnings.

---

## Testing Strategy

### Unit Tests

**RAG Service:**
- Test that RAG service accepts pre-processed queries
- Test that no PII detection is performed
- Test that queries are used as-is for embedding/search

**Orchestrator:**
- Test that PIIDetectionStep processes queries
- Test that processed query flows to IntentHandlingStep
- Test that RAG service receives processed query

### Integration Tests

**Full Pipeline Test:**
```
1. Send query with PII
2. Verify PIIDetectionStep detects and redacts
3. Verify IntentHandlingStep uses processed query
4. Verify RAG service receives processed query (no PII)
5. Verify ResponseSanitizationStep sanitizes output
```

**PII Detection Flow Test:**
- Test that PII is detected once (in orchestrator)
- Test that RAG service doesn't detect PII again
- Test that PII information flows correctly

### Regression Tests

**Existing Functionality:**
- All existing RAG tests should pass
- All existing orchestrator tests should pass
- All integration tests should pass

**Performance:**
- Verify no performance degradation
- Verify PII detection happens once (not twice)

---

## Risk Assessment

### High Risk Areas

1. **Breaking Changes:**
   - Moving `VectorDatabaseService` may break external code
   - Removing PII from RAG may break code expecting PII detection in RAG
   - **Mitigation:** Add deprecation warnings, provide migration guide

2. **Integration Issues:**
   - Orchestrator and RAG service must coordinate query processing
   - **Mitigation:** Comprehensive integration tests

3. **Data Flow:**
   - Processed query must flow correctly through pipeline
   - **Mitigation:** Add logging, verify in tests

### Medium Risk Areas

1. **Test Updates:**
   - Many tests need updates
   - **Mitigation:** Update tests incrementally, verify each phase

2. **Documentation:**
   - Documentation must be accurate
   - **Mitigation:** Review documentation carefully

### Low Risk Areas

1. **Interface Moves:**
   - Moving interfaces is low risk if imports are updated correctly
   - **Mitigation:** Use IDE refactoring tools

2. **Code Removal:**
   - Removing PII detection from RAG is straightforward
   - **Mitigation:** Remove incrementally, test after each removal

---

## Rollback Plan

### If Issues Arise

**Phase 1 Rollback:**
- Revert interface moves
- Restore original imports
- No data loss risk

**Phase 2 Rollback:**
- Restore PII detection in RAG service
- Restore dependencies
- No data loss risk

**Phase 3 Rollback:**
- Revert orchestrator changes
- Restore original query flow
- No data loss risk

### Rollback Procedure

1. **Identify Issue:**
   - Log error details
   - Identify affected phase

2. **Revert Changes:**
   - Use git to revert specific commits
   - Restore previous version

3. **Verify:**
   - Run all tests
   - Verify functionality restored

4. **Document:**
   - Document issue and resolution
   - Update migration plan

---

## Success Criteria

### Functional Requirements

✅ **AI-Core is less RAG-related:**
- No RAG-specific interfaces in core (except SPI)
- Core only depends on RAG via SPI
- Core focuses on orchestration

✅ **RAG is complete RAG service:**
- RAG module owns all RAG interfaces
- RAG service is self-contained
- No dependencies on orchestrator internals

✅ **PII detection is orchestrator responsibility:**
- PII detection happens in orchestrator pipeline
- RAG service receives pre-processed queries
- No PII detection in RAG service

### Non-Functional Requirements

✅ **Performance:**
- No performance degradation
- PII detection happens once (not twice)

✅ **Maintainability:**
- Clear separation of concerns
- Easy to understand and modify
- Follows AI Fabric Framework principles

✅ **Testability:**
- All tests pass
- New tests cover migration scenarios
- Integration tests verify full flow

---

## Implementation Checklist

### Phase 1: Move RAG Interfaces
- [ ] Move `VectorDatabaseService` to RAG module
- [ ] Move `SearchableEntityVectorDatabaseService` to RAG module
- [ ] Update all imports in core module
- [ ] Update all imports in vector database modules (victor-databases)
- [ ] Update module dependencies
- [ ] Update RAGProvider SPI documentation
- [ ] Run tests to verify

### Phase 2: Remove PII from RAG
- [ ] Remove `PIIDetectionService` dependency from `RAGService`
- [ ] Remove all PII detection calls
- [ ] Update `performRag()` method
- [ ] Update `performRAGQuery()` methods
- [ ] Remove PII metadata from responses
- [ ] Update `RAGRequest` documentation
- [ ] Update `RAGResponse` documentation
- [ ] Run tests to verify

### Phase 3: Update Orchestrator
- [ ] Verify `PIIDetectionStep` processes queries correctly
- [ ] Update `IntentHandlingStep` to use processed query
- [ ] Fix `PipelineContext` access in `IntentHandlingStep`
- [ ] Pass PII information via metadata (if needed)
- [ ] Run tests to verify

### Phase 4: Update Response Sanitization
- [ ] Verify `ResponseSanitizationStep` works correctly
- [ ] Merge input PII info with output PII info
- [ ] Run tests to verify

### Phase 5: Update Tests
- [ ] Update RAG service tests
- [ ] Update orchestrator tests
- [ ] Update integration tests
- [ ] Add new tests for migration scenarios
- [ ] Run all tests

### Phase 6: Update Documentation
- [ ] Update RAG module documentation
- [ ] Update orchestrator guide
- [ ] Update core module documentation
- [ ] Create migration guide
- [ ] Review all documentation

---

## Timeline Estimate

**Phase 1:** 3-4 days (interface moves, import updates across all modules)  
**Phase 2:** 3-4 days (remove PII, update methods, tests)  
**Phase 3:** 2-3 days (orchestrator updates, integration)  
**Phase 4:** 1 day (verification)  
**Phase 5:** 2-3 days (test updates, new tests)  
**Phase 6:** 1-2 days (documentation)

**Total:** 12-17 days

**Note:** Phase 1 may take longer due to multiple vector database implementations needing import updates.

---

## Conclusion

This plan provides a comprehensive, incremental approach to achieving clean separation between RAG, AI-Core, and Orchestrator modules. By following the AI Fabric Framework principles of minimalism, separation of concerns, and fail-closed security, we will create a more maintainable and extensible architecture.

**Key Benefits:**
- Clear separation of concerns
- Reduced coupling
- Better testability
- Easier maintenance
- Follows framework philosophy

**Next Steps:**
1. Review and approve this plan
2. Create implementation tickets
3. Begin Phase 1 implementation
4. Regular checkpoints after each phase

---

**Document Status:** Ready for Implementation  
**Last Updated:** January 2026  
**Owner:** AI Infrastructure Team
