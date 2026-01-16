# Compliance and Retention Analysis

**Question**: Does compliance and retention involve AISearchableEntity? How will it work after removal?

**Answer**: Yes, heavily. But it can work better WITHOUT AISearchableEntity.

---

## Current Compliance & Retention Features

### 1. Retention Policies (`RetentionPolicyProvider.java`)

```java
public interface RetentionPolicyProvider {
    // How long to retain data
    int getRetentionDays(String classification, String entityType);

    // Should this entity be deleted?
    boolean shouldDelete(AISearchableEntity entity);  // ← Uses AISearchableEntity

    // Custom cleanup before deletion
    boolean executeDelete(AISearchableEntity entity);  // ← Uses AISearchableEntity
}
```

**Current Usage**:
- Hook for customers to implement retention rules
- Passed AISearchableEntity to make decisions
- Used by cleanup scheduler

### 2. Scheduled Cleanup (`SearchableEntityCleanupScheduler.java`)

**Three cleanup jobs**:

#### A. Cleanup Orphaned Entities (Line 40-60)
```java
@Scheduled(cron = "0 0 4 * * SUN")  // Weekly
public void cleanupOrphanedEntities() {
    // Find all AISearchableEntity records
    List<AISearchableEntity> entities = storageStrategy.findByVectorIdIsNotNull();

    for (AISearchableEntity entity : entities) {
        // Check if vector still exists
        if (!vectorExists(entity)) {
            deleteEntity(entity);  // ← Remove orphaned AISearchableEntity
        }
    }
}
```

**Purpose**: Remove AISearchableEntity records when vector DB no longer has the vector

**Problem**: This exists ONLY because AISearchableEntity can get out of sync!

#### B. Cleanup Entities Without Vectors (Line 62-87)
```java
@Scheduled(cron = "0 0 5 * * SUN")  // Weekly
public void cleanupEntitiesWithoutVectors() {
    // Find AISearchableEntity records with no vectorId
    List<AISearchableEntity> entities = storageStrategy.findByVectorIdIsNull();

    for (AISearchableEntity entity : entities) {
        if (entity.getCreatedAt().isBefore(cutoff)) {
            deleteEntity(entity);  // ← Remove stale records
        }
    }
}
```

**Purpose**: Remove failed/incomplete indexing attempts

**Problem**: This exists ONLY because AISearchableEntity tracks indexing failures!

#### C. Cleanup by Retention Policy (Line 89-111)
```java
@Scheduled(cron = "0 30 3 * * *")  // Daily
public void cleanupByRetentionPolicy() {
    for (String entityType : retentionPolicies) {
        LocalDateTime cutoff = now.minusDays(retentionDays);

        // Get all indexed entities of type
        List<AISearchableEntity> entities = storageStrategy.findByEntityType(entityType);

        for (AISearchableEntity entity : entities) {
            if (entity.getCreatedAt().isBefore(cutoff)) {
                applyPolicy(entityType, entity);  // ← Soft delete, archive, or hard delete
            }
        }
    }
}
```

**Cleanup Strategies**:
- **SOFT_DELETE**: Mark as deleted, remove vector, keep metadata
- **ARCHIVE**: Remove vector, delete AISearchableEntity
- **HARD_DELETE**: Remove both vector and AISearchableEntity

### 3. User Data Deletion - GDPR/CCPA (`UserDataDeletionService.java`)

```java
public UserDataDeletionResult deleteUser(String userId) {
    // 1. Find all indexed entities for this user
    List<UserEntityReference> references = provider.findIndexedEntities(userId);

    for (UserEntityReference ref : references) {
        // Remove from vector DB
        vectorDatabaseService.removeVector(ref.entityType(), ref.entityId());

        // Remove from AISearchableEntity
        storageStrategy.deleteByEntityTypeAndEntityId(ref.entityType(), ref.entityId());
    }

    // 2. Fallback: Search metadata for userId (Line 113-124)
    List<AISearchableEntity> metadataMatches =
        storageStrategy.findByMetadataContainingSnippet("\"" + userId + "\"");

    for (AISearchableEntity entity : metadataMatches) {
        vectorDatabaseService.removeVector(entity.getEntityType(), entity.getEntityId());
        storageStrategy.deleteByEntityTypeAndEntityId(entity.getEntityType(), entity.getEntityId());
    }
}
```

**Purpose**: GDPR "right to be forgotten" - delete all user data

**Uses AISearchableEntity for**:
1. Finding entities owned by user (via metadata search)
2. Coordinating deletion across both systems

---

## Why This Creates Problems

### Problem 1: Dual Deletion Complexity

Current approach requires deleting from **two places**:

```java
// Delete vector
vectorDatabaseService.removeVector(entityType, entityId);

// Delete AISearchableEntity
storageStrategy.deleteByEntityTypeAndEntityId(entityType, entityId);
```

**Issues**:
- What if one succeeds and other fails?
- Transaction coordination across systems
- Partial deletion state
- Retry logic complexity

### Problem 2: Synchronization Jobs Exist ONLY for AISearchableEntity

**Orphaned cleanup** and **no-vector cleanup** are symptoms of dual storage:
- If vector DB is deleted but AISearchableEntity isn't → orphan
- If AISearchableEntity is created but vector fails → no vector

**Without AISearchableEntity**: These problems don't exist!

### Problem 3: Metadata Search Limitation

```java
// Current: Search AISearchableEntity metadata
List<AISearchableEntity> matches =
    storageStrategy.findByMetadataContainingSnippet("\"userId123\"");
```

**Vector DB can do this too!** Most modern vector DBs support metadata filtering:

```java
// Vector DB metadata search
List<VectorRecord> matches = vectorDb.searchByMetadata("userId", "userId123");
```

---

## After Removing AISearchableEntity

### Solution 1: Update Retention Policy Interface

**Current**:
```java
public interface RetentionPolicyProvider {
    boolean shouldDelete(AISearchableEntity entity);  // ❌ Tightly coupled
    boolean executeDelete(AISearchableEntity entity);
}
```

**New**:
```java
public interface RetentionPolicyProvider {
    boolean shouldDelete(VectorRecord vector);  // ✅ Use VectorRecord instead
    boolean executeDelete(String entityType, String entityId);  // ✅ Just need IDs
}
```

**Why better**:
- VectorRecord has all the same data (entityType, entityId, metadata, createdAt)
- More generic - not tied to storage implementation
- Single source of truth

### Solution 2: Update Cleanup Scheduler

#### A. Remove Orphan Cleanup (DELETE ENTIRELY)
```java
// ❌ DELETE THIS METHOD - No longer needed!
// @Scheduled(cron = "0 0 4 * * SUN")
// public void cleanupOrphanedEntities() { ... }
```

**Why**: Orphans can't exist with single storage

#### B. Remove No-Vector Cleanup (DELETE ENTIRELY)
```java
// ❌ DELETE THIS METHOD - No longer needed!
// @Scheduled(cron = "0 0 5 * * SUN")
// public void cleanupEntitiesWithoutVectors() { ... }
```

**Why**: Vector DB is the only storage - no "no vector" state possible

#### C. Update Retention Policy Cleanup

**Current** (Line 89-111):
```java
@Scheduled(cron = "0 30 3 * * *")
public void cleanupByRetentionPolicy() {
    for (String entityType : retentionPolicies) {
        LocalDateTime cutoff = now.minusDays(retentionDays);

        // ❌ OLD: Query AISearchableEntity
        List<AISearchableEntity> entities = storageStrategy.findByEntityType(entityType);

        for (AISearchableEntity entity : entities) {
            if (entity.getCreatedAt().isBefore(cutoff)) {
                applyPolicy(entityType, entity);
            }
        }
    }
}
```

**New**:
```java
@Scheduled(cron = "0 30 3 * * *")
public void cleanupByRetentionPolicy() {
    for (String entityType : retentionPolicies) {
        LocalDateTime cutoff = now.minusDays(retentionDays);

        // ✅ NEW: Query vector DB directly
        List<VectorRecord> vectors = vectorDatabaseService.getVectorsByEntityType(entityType);

        for (VectorRecord vector : vectors) {
            if (vector.getCreatedAt().isBefore(cutoff)) {
                applyPolicy(entityType, vector);
            }
        }
    }
}

private void applyPolicy(String entityType, VectorRecord vector) {
    CleanupStrategy strategy = policyProvider.getStrategy(entityType);
    switch (strategy) {
        case SOFT_DELETE -> softDelete(vector);
        case ARCHIVE, HARD_DELETE, CASCADE -> deleteVector(vector);
    }
}

private void softDelete(VectorRecord vector) {
    // Update vector metadata to mark as deleted
    Map<String, Object> metadata = new HashMap<>(vector.getMetadata());
    metadata.put("_softDeleted", true);
    metadata.put("_deletedAt", LocalDateTime.now(clock).toString());

    // Update vector with soft delete marker
    vectorDatabaseService.updateVector(
        vector.getVectorId(),
        vector.getEntityType(),
        vector.getEntityId(),
        null,  // Clear content
        vector.getEmbedding(),  // Keep embedding for reference
        metadata
    );
}

private void deleteVector(VectorRecord vector) {
    vectorDatabaseService.removeVector(vector.getEntityType(), vector.getEntityId());
}
```

**Benefits**:
- Single deletion point (just vector DB)
- No sync issues
- Simpler logic
- Soft delete stored in vector metadata

### Solution 3: Update User Data Deletion (GDPR)

**Current** (Line 103-127):
```java
private IndexedDeletionStats deleteIndexedEntities(String userId, UserDataDeletionProvider provider) {
    // 1. Get references from provider
    List<UserEntityReference> references = provider.findIndexedEntities(userId);
    references.forEach(ref -> {
        vectorDatabaseService.removeVector(ref.entityType(), ref.entityId());
        storageStrategy.deleteByEntityTypeAndEntityId(ref.entityType(), ref.entityId());  // ❌ Dual deletion
    });

    // 2. Metadata search fallback
    List<AISearchableEntity> metadataMatches =
        storageStrategy.findByMetadataContainingSnippet("\"" + userId + "\"");  // ❌ AISearchableEntity search

    metadataMatches.forEach(entity -> {
        vectorDatabaseService.removeVector(entity.getEntityType(), entity.getEntityId());
        storageStrategy.deleteByEntityTypeAndEntityId(entity.getEntityType(), entity.getEntityId());
    });
}
```

**New**:
```java
private IndexedDeletionStats deleteIndexedEntities(String userId, UserDataDeletionProvider provider) {
    Set<String> processedKeys = new HashSet<>();
    AtomicInteger vectorsDeleted = new AtomicInteger();

    // 1. Get references from provider
    List<UserEntityReference> references = provider.findIndexedEntities(userId);
    references.forEach(ref -> {
        String key = ref.entityType() + "::" + ref.entityId();
        if (processedKeys.add(key)) {
            // ✅ Single deletion point
            if (vectorDatabaseService.removeVector(ref.entityType(), ref.entityId())) {
                vectorsDeleted.incrementAndGet();
            }
        }
    });

    // 2. Metadata search fallback (if vector DB supports it)
    if (vectorDatabaseService.supportsMetadataSearch()) {
        List<VectorRecord> metadataMatches =
            vectorDatabaseService.searchByMetadata("userId", userId);  // ✅ Vector DB search

        metadataMatches.forEach(vector -> {
            String key = vector.getEntityType() + "::" + vector.getEntityId();
            if (processedKeys.add(key)) {
                // ✅ Single deletion point
                if (vectorDatabaseService.removeVector(vector.getEntityType(), vector.getEntityId())) {
                    vectorsDeleted.incrementAndGet();
                }
            }
        });
    }

    return new IndexedDeletionStats(vectorsDeleted.get(), vectorsDeleted.get());
}
```

**If Vector DB Doesn't Support Metadata Search**:

You can implement it at the application level:

```java
// Alternative: Get all vectors and filter in memory
private List<VectorRecord> findVectorsByUserIdInMetadata(String userId) {
    // Get all entity types that might have user data
    List<String> entityTypes = provider.getIndexableEntityTypes();

    List<VectorRecord> matches = new ArrayList<>();
    for (String entityType : entityTypes) {
        List<VectorRecord> vectors = vectorDatabaseService.getVectorsByEntityType(entityType);

        // Filter by userId in metadata
        vectors.stream()
            .filter(v -> metadataContainsUserId(v.getMetadata(), userId))
            .forEach(matches::add);
    }

    return matches;
}

private boolean metadataContainsUserId(Map<String, Object> metadata, String userId) {
    if (metadata == null) return false;

    // Check common user ID fields
    Object userIdValue = metadata.get("userId");
    if (userId.equals(String.valueOf(userIdValue))) {
        return true;
    }

    Object ownerValue = metadata.get("ownerId");
    if (userId.equals(String.valueOf(ownerValue))) {
        return true;
    }

    // Check if userId appears in any metadata value
    return metadata.values().stream()
        .filter(Objects::nonNull)
        .anyMatch(v -> String.valueOf(v).contains(userId));
}
```

---

## File Changes Required

### 1. RetentionPolicyProvider.java

```java
// Before:
public interface RetentionPolicyProvider {
    int getRetentionDays(String classification, String entityType);
    boolean shouldDelete(AISearchableEntity entity);
    boolean executeDelete(AISearchableEntity entity);
}

// After:
public interface RetentionPolicyProvider {
    int getRetentionDays(String classification, String entityType);
    boolean shouldDelete(VectorRecord vector);  // ✅ Changed
    boolean executeDelete(String entityType, String entityId);  // ✅ Changed
}
```

### 2. SearchableEntityCleanupScheduler.java

```java
// Remove dependencies
// - private final AISearchableEntityStorageStrategy storageStrategy;  // ❌ DELETE

// Add dependency
+ private final VectorDatabaseService vectorDatabaseService;  // ✅ ADD

// Delete methods (lines 40-87)
// - cleanupOrphanedEntities()  // ❌ DELETE - no longer needed
// - cleanupEntitiesWithoutVectors()  // ❌ DELETE - no longer needed

// Update method (lines 89-111)
public void cleanupByRetentionPolicy() {
    // ... use vectorDatabaseService.getVectorsByEntityType()
}
```

### 3. UserDataDeletionService.java

```java
// Remove dependency (line 32)
// - private final AISearchableEntityStorageStrategy storageStrategy;  // ❌ DELETE

// Update method (line 103-127)
private IndexedDeletionStats deleteIndexedEntities(String userId, UserDataDeletionProvider provider) {
    // ... use vectorDatabaseService only
}
```

### 4. Add to VectorDatabaseService.java (Optional)

```java
public interface VectorDatabaseService {
    // ... existing methods

    /**
     * Search vectors by metadata field (for GDPR compliance)
     * Optional - providers can implement if they support it
     */
    default List<VectorRecord> searchByMetadata(String metadataKey, String metadataValue) {
        throw new UnsupportedOperationException(
            "Metadata search not supported by " + this.getClass().getSimpleName());
    }

    /**
     * Check if this provider supports metadata-based search
     */
    default boolean supportsMetadataSearch() {
        return false;
    }
}
```

---

## Benefits After Removal

### 1. Simpler Compliance

**Before**: Delete from 2 places
```java
vectorDb.removeVector(type, id);
storageStrategy.deleteByEntityTypeAndEntityId(type, id);
```

**After**: Delete from 1 place
```java
vectorDb.removeVector(type, id);  // Done!
```

### 2. No Synchronization Jobs

**Deleted**:
- Orphaned entity cleanup (weekly)
- No-vector entity cleanup (weekly)

**Saved**: ~100 lines of code + cron overhead

### 3. Atomic Deletions

**Before**: Two-phase deletion can fail partially
**After**: Single operation - succeeds or fails atomically

### 4. Better Soft Delete

**Before**: Soft delete stores marker in AISearchableEntity
**After**: Soft delete stores marker in vector DB metadata

**Benefit**: Soft-deleted data stays with the vector, easier to audit

### 5. Cleaner GDPR Implementation

**Before**: Search AISearchableEntity metadata + vector DB
**After**: Search vector DB only (single source of truth)

---

## Compliance Feature Comparison

| Feature | With AISearchableEntity | Without AISearchableEntity |
|---------|------------------------|---------------------------|
| **Retention Policies** | Query AISearchableEntity | Query vector DB |
| **Soft Delete** | Update AISearchableEntity | Update vector metadata |
| **Hard Delete** | Delete from 2 places | Delete from 1 place |
| **GDPR Deletion** | Search 2 systems | Search 1 system |
| **Orphan Cleanup** | Weekly job required | Not needed |
| **Failed Index Cleanup** | Weekly job required | Not needed |
| **Audit Trail** | AISearchableEntity timestamps | Vector DB timestamps |
| **Metadata Search** | SQL query | Vector DB query or app filter |

---

## Migration Path for Compliance

### Step 1: Update Retention Policy Implementations

```java
// Your custom retention policy
public class MyRetentionPolicy implements RetentionPolicyProvider {

    @Override
    public boolean shouldDelete(VectorRecord vector) {  // ✅ Changed parameter
        // Access same data from VectorRecord
        String entityType = vector.getEntityType();
        LocalDateTime createdAt = vector.getCreatedAt();
        Map<String, Object> metadata = vector.getMetadata();

        // Your retention logic
        return createdAt.isBefore(cutoff);
    }

    @Override
    public boolean executeDelete(String entityType, String entityId) {  // ✅ Changed signature
        // Custom cleanup before deletion
        myBusinessLogic.cleanup(entityType, entityId);
        return true;
    }
}
```

### Step 2: Update Cleanup Configuration

```yaml
ai:
  cleanup:
    enabled: true
    retention-cron: "0 30 3 * * *"  # Daily at 3:30 AM
    retention-days:
      product: 365
      order: 2555  # 7 years
      user-profile: -1  # Never delete

    # ❌ DELETE: These are no longer needed
    # orphaned-entities:
    #   enabled: false
    # no-vector-entities:
    #   enabled: false
```

### Step 3: Test GDPR Deletion

```java
@Test
void testUserDeletionGDPR() {
    // Given: User with indexed data
    String userId = "user-123";

    Product product = createProduct();
    product.setOwnerId(userId);
    aiCapabilityService.processEntityForAI(product, "product");

    // When: Delete user
    UserDataDeletionResult result = deletionService.deleteUser(userId);

    // Then: Vector removed
    assertFalse(vectorDb.vectorExists("product", product.getId().toString()));

    // And: No AISearchableEntity check needed!
    assertEquals(1, result.getVectorsDeleted());
}
```

---

## Summary

**Does compliance/retention involve AISearchableEntity?**
- ✅ Yes, heavily involved currently

**Can it work without AISearchableEntity?**
- ✅ Yes, and BETTER!

**What changes?**
1. Retention policies receive `VectorRecord` instead of `AISearchableEntity`
2. Cleanup scheduler queries vector DB instead of AISearchableEntity
3. GDPR deletion removes from 1 place instead of 2
4. Delete 2 unnecessary cleanup jobs (orphans, no-vector)

**Benefits**:
- Simpler code (~200 lines removed)
- Atomic deletions (no partial failures)
- Single source of truth
- No synchronization bugs
- Better soft delete (stored in vector metadata)

**Compliance still works - just cleaner and more reliable.**
