# Integration Test Plan - Entity Processing with @AICapable

**Component**: AICapabilityService, Entity Configuration, AOP Processing  
**Priority**: 🟡 HIGH  
**Estimated Effort**: 1 week  
**Status**: Draft  

---

## 📋 Overview

This test plan covers testing of automatic AI processing for entities using the @AICapable annotation, including configuration loading, field extraction, and transactional consistency.

### Components Under Test
- `AICapabilityService` (602 lines)
- `AICapableProcessor` (AOP)
- `AIEntityConfigurationLoader`
- Entity configuration YAML
- Automatic processing triggers

---

## 🧪 Test Scenarios

### TEST-ENTITY-001: Automatic Processing on Save
**Priority**: Critical  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Create @AICapable product entity
2. Save product to database
3. Verify AI processing triggered automatically
4. Check embeddings generated
5. Verify search index updated
6. Validate vector database entry

#### Expected Results
- ✅ Processing triggered on save
- ✅ Embeddings generated automatically
- ✅ Entity searchable immediately
- ✅ Vector stored correctly
- ✅ Metadata extracted

#### Implementation Notes
```java
@Test
public void testAutomaticProcessingOnSave() {
    // Given - @AICapable entity
    Product product = Product.builder()
        .name("Luxury Swiss Watch")
        .description("Premium timepiece with automatic movement")
        .category("Watches")
        .price(new BigDecimal("5000"))
        .build();
    
    // When - Save entity
    Product saved = productRepository.save(product);
    
    // Then - Verify AI processing
    // Wait for async processing
    await().atMost(5, TimeUnit.SECONDS).until(() -> {
        Optional<AISearchableEntity> searchable = 
            searchableEntityRepository.findByEntityTypeAndEntityId(
                "product", saved.getId().toString()
            );
        return searchable.isPresent();
    });
    
    // Verify embeddings
    AISearchableEntity searchable = searchableEntityRepository
        .findByEntityTypeAndEntityId("product", saved.getId().toString())
        .orElseThrow();
    
    assertNotNull(searchable.getVectorId());
    assertNotNull(searchable.getSearchableContent());
    assertTrue(searchable.getSearchableContent().contains("Luxury Swiss Watch"));
    
    // Verify vector exists
    assertTrue(vectorManagementService.vectorExists("product", saved.getId().toString()));
}
```

---

### TEST-ENTITY-002: Configuration Loading from YAML
**Priority**: Critical  
**Status**: ⚠️ PARTIAL

#### Test Steps
1. Create entity configuration YAML
2. Load configuration
3. Verify all fields mapped correctly
4. Test searchable fields extraction
5. Test embeddable fields extraction
6. Test metadata fields extraction

#### Configuration Example
```yaml
entities:
  - entity-type: product
    indexable: true
    auto-embedding: true
    searchable-fields:
      - name: name
        weight: 2.0
      - name: description
        weight: 1.5
      - name: category
        weight: 1.0
    embeddable-fields:
      - name: name
      - name: description
    metadata-fields:
      - name: category
      - name: brand
      - name: price
```

---

### TEST-ENTITY-003: Field Extraction
**Priority**: High  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Create entity with complex fields
2. Process with AI
3. Verify searchable content extracted
4. Verify embeddable content extracted
5. Verify metadata extracted
6. Test nested fields
7. Test null fields

---

### TEST-ENTITY-004: Entity Cleanup on Deletion
**Priority**: Critical  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Create and process entity
2. Verify entity indexed
3. Delete entity
4. Verify vector removed from database
5. Verify search index cleaned up
6. Verify no orphaned data

#### Implementation Notes
```java
@Test
public void testEntityCleanupOnDeletion() {
    // Given - Processed entity
    Product product = createAndSaveProduct();
    String productId = product.getId().toString();
    
    // Verify entity indexed
    assertTrue(vectorManagementService.vectorExists("product", productId));
    assertTrue(searchableEntityRepository
        .findByEntityTypeAndEntityId("product", productId)
        .isPresent());
    
    // When - Delete entity
    productRepository.delete(product);
    
    // Then - Verify cleanup
    assertFalse(vectorManagementService.vectorExists("product", productId));
    assertFalse(searchableEntityRepository
        .findByEntityTypeAndEntityId("product", productId)
        .isPresent());
}
```

---

### TEST-ENTITY-005: Transactional Consistency
**Priority**: Critical  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Start transaction
2. Save entity (triggers AI processing)
3. Simulate processing error
4. Rollback transaction
5. Verify entity not saved
6. Verify AI processing rolled back
7. Verify no partial data

---

### TEST-ENTITY-006: Error Handling When Processing Fails
**Priority**: High  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Configure to simulate API failure
2. Save entity
3. Verify entity saved despite AI failure
4. Check error logged
5. Verify retry mechanism
6. Test manual retry

---

### TEST-ENTITY-007: Performance Impact on CRUD
**Priority**: High  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Measure baseline CRUD performance (no AI)
2. Enable AI processing
3. Measure CRUD performance with AI
4. Calculate overhead
5. Verify overhead acceptable (<500ms)
6. Test batch operations

---

### TEST-ENTITY-008: Concurrent Entity Processing
**Priority**: High  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Save 50 entities concurrently
2. Verify all processed correctly
3. Check for race conditions
4. Verify vector consistency
5. Check database integrity

---

### TEST-ENTITY-009: Configuration Validation
**Priority**: Medium  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Load invalid configuration
2. Verify validation errors
3. Test missing required fields
4. Test invalid field types
5. Verify error messages helpful

---

### TEST-ENTITY-010: Update Processing
**Priority**: High  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Create and process entity
2. Update entity fields
3. Save update
4. Verify re-processing triggered
5. Verify embeddings updated
6. Verify old data replaced

---

## 🎯 Success Criteria

- ✅ Automatic processing works reliably
- ✅ Configuration loading correct
- ✅ Field extraction accurate
- ✅ Cleanup complete on deletion
- ✅ Transactional consistency maintained
- ✅ Performance overhead < 500ms
- ✅ Concurrent processing safe

---

**End of Test Plan**
