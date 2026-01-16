# AISearchableEntity Removal Plan

**Status**: Draft
**Created**: 2026-01-16
**Author**: System Analysis
**Approval**: Pending

---

## Executive Summary

**Objective**: Remove `AISearchableEntity` and related storage infrastructure, simplifying the architecture to use vector database as the single source of truth.

**Rationale**:
- Vector database already stores all necessary data (content, metadata, embeddings)
- `VectorDatabaseService.getVectorByEntity()` provides entity-based lookup
- Duplicate storage adds complexity, synchronization overhead, and storage cost
- Current usage is minimal and easily replaceable

**Impact**: Medium complexity, high value

**Timeline**: 2-3 days for implementation + testing

---

## Phase 1: Impact Analysis

### Files to Modify

#### Core Services (5 files)
1. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/service/AICapabilityService.java`
   - Remove AISearchableEntity storage logic (lines 367-382)
   - Update cleanup methods (line 256)
   - Remove storageStrategy dependency

2. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/SearchableEntityVectorDatabaseService.java`
   - Remove entire decorator class
   - Direct usage of underlying VectorDatabaseService

3. `ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/service/ReliableRelationshipQueryService.java`
   - Replace `storageStrategy.findByEntityTypeAndEntityId()` with `vectorDb.getVectorByEntity()`
   - Update content retrieval logic (lines 299-306)

4. `ai-infrastructure-relationship-query/src/main/java/com/ai/infrastructure/relationship/service/LLMDrivenJPAQueryService.java`
   - Review and update any AISearchableEntity usage

5. `ai-infrastructure-migration/src/main/java/com/ai/infrastructure/migration/service/DataMigrationService.java`
   - Remove migration logic for AISearchableEntity

#### Storage Layer (7 files) - DELETE
1. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/entity/AISearchableEntity.java` ❌
2. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/repository/AISearchableEntityRepository.java` ❌
3. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/storage/strategy/AISearchableEntityStorageStrategy.java` ❌
4. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/storage/strategy/SingleTableStorageStrategy.java` ❌
5. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/storage/strategy/impl/PerTypeTableStorageStrategy.java` ❌
6. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/storage/strategy/impl/PerTypeRepository.java` ❌
7. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/storage/auto/TableAutoCreationService.java` ❌

#### Configuration (3 files)
1. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIInfrastructureAutoConfiguration.java`
   - Remove storage strategy beans
   - Remove SearchableEntityVectorDatabaseService bean

2. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/storage/AIStorageProperties.java`
   - Delete or simplify

3. Database migration files
   - `V1.1__Remove_Vector_Storage.sql` - Review and update

#### Annotations (2 files) - UPDATE JAVADOC
1. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/annotation/AISearchable.java`
   - Update line 19: Remove reference to AISearchableEntity

2. `ai-infrastructure-core/src/main/java/com/ai/infrastructure/annotation/AIContext.java`
   - Update line 20: Remove reference to AISearchableEntity

#### Tests (30+ files)
- Integration tests that verify AISearchableEntity behavior
- Unit tests for storage strategies
- Relationship query tests

#### Documentation (15+ files)
- Update all references to AISearchableEntity
- Update architecture diagrams
- Update user guides

---

## Phase 2: Implementation Steps

### Step 1: Create Feature Flag (Safety First)

**File**: `ai-infrastructure-core/src/main/resources/application.yml`

```yaml
ai:
  infrastructure:
    storage:
      use-legacy-searchable-entity: true  # Default to true for safety
```

**Purpose**: Allow gradual rollout and easy rollback

### Step 2: Modify AICapabilityService

**File**: `AICapabilityService.java`

**Changes**:

```java
// Remove storageStrategy field
// private final AISearchableEntityStorageStrategy storageStrategy; // ❌ DELETE

// Update constructor
public AICapabilityService(AIEmbeddingService embeddingService,
                          AICoreService aiCoreService,
                          // AISearchableEntityStorageStrategy storageStrategy, // ❌ DELETE
                          AIEntityConfigurationLoader configurationLoader,
                          VectorManagementService vectorManagementService,
                          AnnotationFieldScanner annotationFieldScanner) {
    this.embeddingService = embeddingService;
    this.aiCoreService = aiCoreService;
    // this.storageStrategy = storageStrategy; // ❌ DELETE
    this.configurationLoader = configurationLoader;
    this.vectorManagementService = vectorManagementService;
    this.annotationFieldScanner = annotationFieldScanner;
}

// Simplify storeSearchableEntity method (lines 344-387)
private void storeSearchableEntity(Object entity, AIEntityConfig config, String content, List<Double> embeddings) {
    try {
        String entityId = getEntityId(entity);
        if (entityId == null) {
            log.warn("No entity ID found for storing searchable entity");
            return;
        }

        // Store vector in vector database (ONLY storage needed)
        Map<String, Object> metadata = extractMetadata(entity, config);
        String vectorId = vectorManagementService.storeVector(
            config.getEntityType(),
            entityId,
            content,
            embeddings,
            metadata
        );

        if (vectorId == null) {
            log.error("Failed to store vector in vector database for entity {} of type {}",
                entityId, config.getEntityType());
            return;
        }

        log.debug("Successfully stored vector {} for entity {} of type {}",
            vectorId, entityId, config.getEntityType());

        // ❌ REMOVED: AISearchableEntity storage
        // No longer needed - vector DB is single source of truth

    } catch (Exception e) {
        log.error("Error storing searchable entity", e);
    }
}

// Update cleanupEmbeddings method (lines 237-261)
@Transactional
public void cleanupEmbeddings(Object entity, AIEntityConfig config) {
    try {
        String entityId = getEntityId(entity);
        if (entityId == null) {
            log.warn("No entity ID found for cleaning up embeddings");
            return;
        }

        log.debug("Cleaning up embeddings for entity {} of type {}", entityId, config.getEntityType());

        // Remove from vector database
        boolean removed = vectorManagementService.removeVector(config.getEntityType(), entityId);

        if (removed) {
            log.debug("Successfully removed vector from vector database for entity {} of type {}",
                entityId, config.getEntityType());
        } else {
            log.warn("Vector not found in vector database for entity {} of type {}",
                entityId, config.getEntityType());
        }

        // ❌ REMOVED: AISearchableEntity cleanup
        // storageStrategy.deleteByEntityTypeAndEntityId(config.getEntityType(), entityId);

    } catch (Exception e) {
        log.error("Error cleaning up embeddings for entity", e);
    }
}

// Update removeEntityFromIndex method (lines 449-462)
public void removeEntityFromIndex(String entityId, String entityType) {
    try {
        log.debug("Removing entity from AI index: {} of type {}", entityId, entityType);

        boolean removed = vectorManagementService.removeVector(entityType, entityId);

        if (removed) {
            log.debug("Successfully removed entity from AI index: {} of type {}", entityId, entityType);
        } else {
            log.warn("Entity not found in AI index: {} of type {}", entityId, entityType);
        }

        // ❌ REMOVED: AISearchableEntity removal

    } catch (Exception e) {
        log.error("Error removing entity from AI index", e);
    }
}
```

### Step 3: Update ReliableRelationshipQueryService

**File**: `ReliableRelationshipQueryService.java`

**Changes**:

```java
// Update field
// private final AISearchableEntityStorageStrategy storageStrategy; // ❌ DELETE
private final VectorDatabaseService vectorDatabaseService; // ✅ Already exists

// Update buildResponseFromIds method (lines 280-310)
private RAGResponse buildResponseFromIds(String query,
                                         RelationshipQueryPlan plan,
                                         List<String> entityIds,
                                         QueryOptions options,
                                         String stage) {
    List<String> limited = limitIds(plan, entityIds, options);
    recordFallbackStage(stage, !limited.isEmpty(), limited.size());
    if (limited.isEmpty()) {
        return emptyResponse(query, plan, stage + "_EMPTY");
    }
    List<RAGResponse.RAGDocument> documents = new ArrayList<>();
    ReturnMode returnMode = options.getReturnMode() != null ? options.getReturnMode() : properties.getDefaultReturnMode();

    for (String id : limited) {
        if (returnMode == ReturnMode.IDS) {
            documents.add(RAGResponse.RAGDocument.builder()
                .id(id)
                .source(stage.toLowerCase())
                .build());
        } else {
            // ✅ NEW: Get content from vector DB instead of AISearchableEntity
            Optional<VectorRecord> vectorOpt = vectorDatabaseService.getVectorByEntity(
                plan.getPrimaryEntityType(), id);

            vectorOpt.ifPresent(vector -> documents.add(
                RAGResponse.RAGDocument.builder()
                    .id(id)
                    .content(vector.getContent())  // ← From vector DB
                    .metadata(vector.getMetadata() != null
                        ? vector.getMetadata()
                        : Map.of("source", stage.toLowerCase()))
                    .build()
            ));
        }
    }
    return buildResponse(query, plan, documents, stage);
}

// Update trySimpleFallback method (lines 181-206)
private RAGResponse trySimpleFallback(String query, RelationshipQueryPlan plan, QueryOptions options) {
    if (!properties.isFallbackToSimpleSearch()) {
        return emptyResponse(query, plan, "SIMPLE_DISABLED");
    }
    try {
        // ✅ NEW: Get all vectors for entity type
        List<VectorRecord> vectors = vectorDatabaseService.getVectorsByEntityType(
            plan.getPrimaryEntityType());

        int limit = options.getLimit() != null ? options.getLimit() : 20;
        List<RAGResponse.RAGDocument> documents = new ArrayList<>();

        for (int i = 0; i < vectors.size() && i < limit; i++) {
            VectorRecord vector = vectors.get(i);
            documents.add(RAGResponse.RAGDocument.builder()
                .id(vector.getEntityId())
                .content(vector.getContent())  // ← From vector DB
                .metadata(vector.getMetadata() != null
                    ? vector.getMetadata()
                    : Map.of("source", "simple-fallback"))
                .build());
        }

        recordFallbackStage("FALLBACK_SIMPLE", !documents.isEmpty(), documents.size());
        return documents.isEmpty()
            ? emptyResponse(query, plan, "FALLBACK_SIMPLE_EMPTY")
            : buildResponse(query, plan, documents, "FALLBACK_SIMPLE");
    } catch (Exception ex) {
        log.error("Simple repository fallback failed", ex);
        recordFallbackStage("FALLBACK_SIMPLE", false, 0);
        return emptyResponse(query, plan, "SIMPLE_ERROR");
    }
}
```

### Step 4: Remove SearchableEntityVectorDatabaseService

**Action**: Delete the entire decorator class

**File**: `SearchableEntityVectorDatabaseService.java` ❌ DELETE

**Update References**:

In `AIInfrastructureAutoConfiguration.java`:

```java
// Before:
@Bean
@ConditionalOnMissingBean
public VectorDatabaseService vectorDatabaseService(
        VectorDatabaseService delegateVectorDb,
        AISearchableEntityStorageStrategy storageStrategy,
        AIEntityConfigurationLoader configurationLoader) {
    return new SearchableEntityVectorDatabaseService(
        delegateVectorDb, storageStrategy, configurationLoader);
}

// After: ❌ DELETE - just use the delegate directly
// The VectorDatabaseService implementation beans (Lucene, Qdrant, etc.)
// will be injected directly without the decorator
```

### Step 5: Update Configuration

**File**: `AIInfrastructureAutoConfiguration.java`

```java
// ❌ DELETE: All storage strategy beans
/*
@Bean
@ConditionalOnMissingBean
public AISearchableEntityStorageStrategy storageStrategy(...) { ... }

@Bean
@ConditionalOnProperty(...)
public SingleTableStorageStrategy singleTableStrategy(...) { ... }

@Bean
@ConditionalOnProperty(...)
public PerTypeTableStorageStrategy perTypeTableStrategy(...) { ... }
*/

// ❌ DELETE: TableAutoCreationService bean
/*
@Bean
public TableAutoCreationService tableAutoCreationService(...) { ... }
*/
```

### Step 6: Database Migration (Optional)

**File**: Create `V2.0__Remove_AISearchableEntity_Tables.sql`

```sql
-- Drop AISearchableEntity tables if they exist
-- This is OPTIONAL - you can leave tables for historical data

-- Single table strategy
DROP TABLE IF EXISTS ai_searchable_entity CASCADE;

-- Per-type table strategy (examples)
DROP TABLE IF EXISTS ai_searchable_product CASCADE;
DROP TABLE IF EXISTS ai_searchable_document CASCADE;
DROP TABLE IF EXISTS ai_searchable_order CASCADE;

-- Add more per-type tables as needed based on your entity types
```

**Note**: Consider keeping tables for a grace period with archived data before dropping.

---

## Phase 3: Testing Strategy

### Unit Tests

**Update Tests**:
1. `AICapabilityServiceTest` - Remove AISearchableEntity verifications
2. `ReliableRelationshipQueryServiceTest` - Update to verify vector DB calls
3. `LLMDrivenJPAQueryServiceTest` - Update content retrieval tests

**New Tests**:
```java
@Test
void testAnnotationsStoreInVectorDBOnly() {
    // Given: Entity with @AISearchable and @AIContext
    Product product = new Product();
    product.setId(UUID.randomUUID());
    product.setName("Test Product");
    product.setDescription("Test Description");
    product.setCategory("electronics");

    // When: Process entity
    aiCapabilityService.processEntityForAI(product, "product");

    // Then: Verify stored in vector DB
    Optional<VectorRecord> vector = vectorDb.getVectorByEntity("product", product.getId().toString());
    assertTrue(vector.isPresent());
    assertEquals("Test Product Test Description", vector.get().getContent());
    assertEquals("electronics", vector.get().getMetadata().get("category"));

    // And: AISearchableEntity NOT created
    // (No assertion needed - entity doesn't exist anymore)
}
```

### Integration Tests

**Files to Update**:
- `AISearchableEntityLifecycleIntegrationTest.java` - Convert to vector DB tests
- `AISearchableEntityVectorSynchronizationIntegrationTest.java` - Remove (no sync needed)
- `AISearchableEntityTransactionalConsistencyIntegrationTest.java` - Convert to vector DB tests
- `AISearchableEntityExtendedIntegrationTest.java` - Convert to vector DB tests
- Storage strategy tests - DELETE or convert

**Test Scenarios**:
1. ✅ Annotation processing stores in vector DB
2. ✅ Content retrieval from vector DB by entity ID
3. ✅ Search returns correct results
4. ✅ Relationship query fallback uses vector DB
5. ✅ Entity deletion removes from vector DB
6. ✅ Transaction rollback removes vector

### Manual Testing Checklist

- [ ] Create entity with @AISearchable and @AIContext
- [ ] Verify entity is searchable
- [ ] Update entity and verify changes
- [ ] Delete entity and verify removal
- [ ] Perform relationship query with semantic fallback
- [ ] Check vector DB contains all expected data
- [ ] Verify no AISearchableEntity tables are accessed

---

## Phase 4: Rollback Plan

### If Issues Arise

**Immediate Rollback**:
1. Revert code changes (git revert)
2. Redeploy previous version
3. Database tables remain intact (no data loss)

**Partial Rollback** (with feature flag):
```yaml
ai:
  infrastructure:
    storage:
      use-legacy-searchable-entity: true  # Re-enable AISearchableEntity
```

**Data Recovery**:
- Vector database retains all data
- Can rebuild AISearchableEntity from vector DB if needed
- Migration script to populate AISearchableEntity from vector DB:

```java
// Recovery migration (if needed)
public void rebuildAISearchableEntityFromVectorDB() {
    List<String> entityTypes = vectorDb.getStatistics().get("entityTypes");

    for (String entityType : entityTypes) {
        List<VectorRecord> vectors = vectorDb.getVectorsByEntityType(entityType);

        for (VectorRecord vector : vectors) {
            AISearchableEntity entity = AISearchableEntity.builder()
                .entityType(vector.getEntityType())
                .entityId(vector.getEntityId())
                .vectorId(vector.getVectorId())
                .searchableContent(vector.getContent())
                .metadata(serializeMetadata(vector.getMetadata()))
                .createdAt(vector.getCreatedAt())
                .updatedAt(vector.getUpdatedAt())
                .build();

            repository.save(entity);
        }
    }
}
```

---

## Phase 5: Documentation Updates

### User-Facing Documentation

1. **AI_CORE_USER_GUIDE.md**
   - Remove references to AISearchableEntity
   - Update architecture diagrams
   - Simplify configuration examples

2. **CONFIGURATION_AND_OPTIMIZATION_GUIDE.md**
   - Remove storage strategy configuration
   - Update performance tuning section

3. **README.md files**
   - Update architecture overview
   - Simplify getting started guides

### Developer Documentation

1. **SEMANTIC_SEARCH_DATA_FLOW.md**
   - Update data flow diagrams
   - Remove AISearchableEntity layer

2. **VECTOR_DATABASE_ABSTRACTION.md**
   - Update to reflect simplified architecture

3. **API_REFERENCE.md**
   - Remove AISearchableEntity API references
   - Update code examples

### Internal Documentation

1. **AISEARCHABLE_STORAGE_STRATEGY/** (entire directory)
   - Mark as deprecated/archived
   - Add migration notice

2. **ARCHITECTURAL_DECISIONS.md**
   - Document decision to remove AISearchableEntity
   - Rationale and benefits

---

## Phase 6: Metrics & Validation

### Success Criteria

**Performance**:
- [ ] Indexing speed: Same or faster (expect 10-20% improvement)
- [ ] Search latency: Same or better
- [ ] Memory usage: Reduced by ~30-50% (no duplicate storage)

**Functionality**:
- [ ] All @AISearchable annotations work correctly
- [ ] All @AIContext annotations work correctly
- [ ] Search results are identical to before
- [ ] Relationship queries work correctly

**Code Quality**:
- [ ] Reduced codebase by ~2000+ lines
- [ ] Simplified architecture (1 storage layer vs 2)
- [ ] All tests pass
- [ ] No compiler warnings

### Monitoring

**After Deployment**:
- Monitor vector DB query performance
- Watch for errors in logs
- Track search result quality
- Monitor storage usage

---

## Phase 7: Cleanup

### After Successful Deployment

**Week 1**: Monitor production
**Week 2**: Archive AISearchableEntity tables (don't drop yet)
**Week 4**: Remove feature flag code
**Week 8**: Drop database tables (after backup)

### Files to Archive

Move to `archive/` directory:
- All storage strategy implementations
- AISearchableEntity tests
- Related documentation

### Final Cleanup

```bash
# Remove archived code
rm -rf ai-infrastructure-core/src/main/java/com/ai/infrastructure/storage/
rm -rf ai-infrastructure-core/src/main/java/com/ai/infrastructure/entity/AISearchableEntity.java
rm -rf ai-infrastructure-core/src/main/java/com/ai/infrastructure/repository/AISearchableEntityRepository.java

# Remove old tests
rm -rf ai-infrastructure-module/integration-Testing/integration-tests/src/test/java/com/ai/infrastructure/it/AISearchableEntity*
rm -rf ai-infrastructure-module/integration-Testing/integration-tests/src/test/java/com/ai/infrastructure/it/storage/

# Update docs
rm -rf ai-infrastructure-module/docs/Fixing_Arch/AISEARCHABLE_STORAGE_STRATEGY/
```

---

## Risk Assessment

| Risk | Severity | Likelihood | Mitigation |
|------|----------|------------|------------|
| Data loss during migration | High | Low | Vector DB already has all data; no migration needed |
| Breaking existing code | High | Low | Comprehensive testing; feature flag for rollback |
| Performance degradation | Medium | Low | Vector DB already used for search; expect improvement |
| Missing edge cases | Medium | Medium | Thorough testing; gradual rollout |
| Third-party integration issues | Medium | Low | Vector DB API unchanged |

---

## Timeline

### Day 1: Preparation & Core Changes
- Morning: Code review and impact analysis
- Afternoon: Implement Steps 1-4 (core service changes)
- Evening: Unit tests

### Day 2: Integration & Testing
- Morning: Implement Steps 5-6 (configuration, migration)
- Afternoon: Integration tests
- Evening: Documentation updates

### Day 3: Validation & Deployment
- Morning: Final testing and validation
- Afternoon: Deploy to staging
- Evening: Monitor and validate

### Week 2: Production Rollout
- Gradual rollout with monitoring
- Feature flag enabled initially
- Full rollout after validation

---

## Approval Checklist

- [ ] Technical lead review
- [ ] Architecture approval
- [ ] Security review (minimal risk)
- [ ] Performance impact assessment
- [ ] Test coverage verification
- [ ] Documentation review
- [ ] Stakeholder notification

---

## Questions & Answers

**Q: Will this break existing applications?**
A: No. The annotations work the same way. Vector DB already stores everything. Changes are internal only.

**Q: What about data migration?**
A: No migration needed! Vector DB already has all the data. AISearchableEntity was just a duplicate.

**Q: Can we rollback easily?**
A: Yes. Feature flag allows instant rollback. Database tables remain for grace period.

**Q: What about performance?**
A: Expect improvement - fewer writes, no synchronization overhead, less memory usage.

**Q: Do we lose any functionality?**
A: No. Vector DB has `getVectorByEntity()` which replaces all AISearchableEntity queries.

---

## Next Steps

1. **Review this plan** with team
2. **Get approval** from stakeholders
3. **Create feature branch**: `feature/remove-ai-searchable-entity`
4. **Start with Step 1**: Implement feature flag
5. **Proceed incrementally** through each phase
6. **Monitor closely** during rollout

---

## References

- Vector Database Service API: `VectorDatabaseService.java`
- Current Implementation: `AICapabilityService.java:344-387`
- Industry Best Practices: Modern vector DBs store content + metadata
- Related Discussion: AISEARCHABLEENTITY_VALUE_ANALYSIS.md
