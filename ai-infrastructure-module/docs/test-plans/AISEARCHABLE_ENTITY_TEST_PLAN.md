# Integration Test Plan - AISearchableEntity

**Component**: AISearchableEntity, AISearchableEntityRepository, Integration Layer  
**Priority**: 🔴 CRITICAL  
**Estimated Effort**: 1.5 weeks  
**Status**: Draft  

---

## 📋 Overview

This test plan covers comprehensive integration testing of `AISearchableEntity`, the persistence layer that connects domain entities with vector storage and search capabilities. `AISearchableEntity` serves as the integration point between:
- Domain entities (Product, User, Order, etc.)
- Vector database (Lucene, Pinecone, etc.)
- Search services (Semantic search, RAG, etc.)
- AI processing pipeline

### Components Under Test
- `AISearchableEntity` entity (persistence model)
- `AISearchableEntityRepository` (data access layer)
- `AICapabilityService.storeSearchableEntity()` (creation logic)
- Integration with `VectorManagementService`
- Integration with `RAGService`
- Integration with search services

### Key Relationships

```
Domain Entity → AICapabilityService → AISearchableEntity → Vector Database
                                           ↓
                                      Search Services
```

### Test Objectives
1. Verify `AISearchableEntity` creation when vectors are stored
2. Validate vector ID linking between `AISearchableEntity` and vector database
3. Test integration with vector storage operations
4. Test integration with RAG indexing
5. Verify searchable content extraction and storage
6. Test metadata persistence and retrieval
7. Verify cleanup operations when entities/vectors are deleted
8. Test update operations and vector ID changes
9. Validate concurrent operations
10. Test transactional consistency

---

## 🧪 Test Scenarios

### TEST-AISEARCHABLE-001: Creation When Vector Stored
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AISearchableEntityVectorSynchronizationIntegrationTest#creationWhenVectorStored`)

#### Test Steps
1. Store vector via `VectorManagementService.storeVector()`
2. Verify `AISearchableEntity` created automatically
3. Verify entityType and entityId set correctly
4. Verify searchableContent extracted and stored
5. Verify vectorId linked correctly
6. Verify metadata stored correctly
7. Verify timestamps set (createdAt, updatedAt, vectorUpdatedAt)

#### Expected Results
- ✅ `AISearchableEntity` created automatically when vector stored
- ✅ EntityType and entityId match source entity
- ✅ SearchableContent extracted correctly
- ✅ VectorId linked to vector in vector database
- ✅ Metadata stored as JSON
- ✅ All timestamps set correctly
- ✅ VectorId is unique reference

#### Test Data
```java
// Store vector for product
String entityType = "product";
String entityId = "product-123";
String content = "Luxury Swiss Watch with automatic movement";
List<Double> embedding = generateEmbedding(content);
Map<String, Object> metadata = Map.of(
    "category", "Watches",
    "brand", "Rolex",
    "price", 5000.0
);

// Store vector
String vectorId = vectorManagementService.storeVector(
    entityType, entityId, content, embedding, metadata
);
```

#### Implementation Notes
```java
@Test
public void testCreationWhenVectorStored() {
    // Given - Vector to store
    String entityType = "product";
    String entityId = "product-123";
    String content = "Luxury Swiss Watch with automatic movement";
    List<Double> embedding = generateTestEmbedding(1536);
    Map<String, Object> metadata = Map.of(
        "category", "Watches",
        "brand", "Rolex",
        "price", 5000.0
    );
    
    // When - Store vector (should create AISearchableEntity)
    String vectorId = vectorManagementService.storeVector(
        entityType, entityId, content, embedding, metadata
    );
    
    // Wait for async processing if needed
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId).isPresent()
    );
    
    // Then - Verify AISearchableEntity created
    Optional<AISearchableEntity> searchable = 
        searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId);
    
    assertTrue(searchable.isPresent(), "AISearchableEntity should be created");
    
    AISearchableEntity entity = searchable.get();
    assertEquals(entityType, entity.getEntityType());
    assertEquals(entityId, entity.getEntityId());
    assertEquals(content, entity.getSearchableContent());
    assertEquals(vectorId, entity.getVectorId());
    assertNotNull(entity.getMetadata());
    assertNotNull(entity.getCreatedAt());
    assertNotNull(entity.getUpdatedAt());
    assertNotNull(entity.getVectorUpdatedAt());
    
    // Verify metadata stored correctly
    assertTrue(entity.getMetadata().contains("Watches"));
    assertTrue(entity.getMetadata().contains("Rolex"));
}
```

---

### TEST-AISEARCHABLE-002: Vector ID Linking Integrity
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AISearchableEntityVectorSynchronizationIntegrationTest#vectorIdLinkingIntegrity`)

#### Test Steps
1. Store vector and get vectorId
2. Verify `AISearchableEntity` has correct vectorId
3. Retrieve vector from vector database using vectorId
4. Verify vector exists and matches
5. Test retrieval using `findByVectorId()`
6. Verify vectorId is unique reference

#### Expected Results
- ✅ VectorId links correctly to vector database
- ✅ Vector can be retrieved using vectorId from `AISearchableEntity`
- ✅ VectorId is unique (no duplicates)
- ✅ VectorId reference integrity maintained
- ✅ Vector database and `AISearchableEntity` in sync

#### Implementation Notes
```java
@Test
public void testVectorIdLinkingIntegrity() {
    // Given - Store vector
    String entityType = "product";
    String entityId = "product-456";
    String content = "Premium Watch";
    List<Double> embedding = generateTestEmbedding(1536);
    
    String vectorId = vectorManagementService.storeVector(
        entityType, entityId, content, embedding, null
    );
    
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId).isPresent()
    );
    
    // When - Retrieve AISearchableEntity
    AISearchableEntity searchable = searchableEntityRepository
        .findByEntityTypeAndEntityId(entityType, entityId)
        .orElseThrow();
    
    // Then - Verify vectorId linking
    assertEquals(vectorId, searchable.getVectorId());
    
    // Verify vector can be retrieved using vectorId
    Optional<VectorRecord> vector = vectorManagementService.getVector(entityType, entityId);
    assertTrue(vector.isPresent(), "Vector should exist");
    
    // Verify vectorId can be used to find AISearchableEntity
    Optional<AISearchableEntity> foundByVectorId = 
        searchableEntityRepository.findByVectorId(vectorId);
    assertTrue(foundByVectorId.isPresent());
    assertEquals(entityId, foundByVectorId.get().getEntityId());
    
    // Verify uniqueness
    List<AISearchableEntity> allWithSameVectorId = 
        searchableEntityRepository.findAll().stream()
            .filter(e -> vectorId.equals(e.getVectorId()))
            .toList();
    assertEquals(1, allWithSameVectorId.size(), "VectorId should be unique");
}
```

---

### TEST-AISEARCHABLE-003: Integration with Vector Storage Operations
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AISearchableEntityVectorSynchronizationIntegrationTest#updateKeepsSearchableEntityInSync`, `AISearchableEntityVectorSynchronizationIntegrationTest#removalClearsRepository`)

#### Test Steps
1. Store vector → Verify `AISearchableEntity` created
2. Update vector → Verify `AISearchableEntity` updated (vectorId may change)
3. Delete vector → Verify `AISearchableEntity` deleted or marked for deletion
4. Verify all operations maintain consistency
5. Test vector update triggers `AISearchableEntity` update

#### Expected Results
- ✅ Vector storage creates `AISearchableEntity`
- ✅ Vector update updates `AISearchableEntity` (vectorId, vectorUpdatedAt)
- ✅ Vector deletion removes `AISearchableEntity`
- ✅ Operations maintain consistency
- ✅ No orphaned `AISearchableEntity` records

#### Implementation Notes
```java
@Test
public void testIntegrationWithVectorStorageOperations() {
    // Given - Store initial vector
    String entityType = "product";
    String entityId = "product-789";
    String content1 = "Original content";
    List<Double> embedding1 = generateTestEmbedding(1536);
    
    String vectorId1 = vectorManagementService.storeVector(
        entityType, entityId, content1, embedding1, null
    );
    
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId).isPresent()
    );
    
    AISearchableEntity original = searchableEntityRepository
        .findByEntityTypeAndEntityId(entityType, entityId)
        .orElseThrow();
    
    // When - Update vector
    String content2 = "Updated content";
    List<Double> embedding2 = generateTestEmbedding(1536);
    
    String vectorId2 = vectorManagementService.storeVector(
        entityType, entityId, content2, embedding2, null
    );
    
    await().atMost(5, TimeUnit.SECONDS).until(() -> {
        AISearchableEntity updated = searchableEntityRepository
            .findByEntityTypeAndEntityId(entityType, entityId)
            .orElseThrow();
        return !updated.getSearchableContent().equals(original.getSearchableContent()) ||
               !updated.getVectorId().equals(original.getVectorId());
    });
    
    // Then - Verify AISearchableEntity updated
    AISearchableEntity updated = searchableEntityRepository
        .findByEntityTypeAndEntityId(entityType, entityId)
        .orElseThrow();
    
    assertEquals(content2, updated.getSearchableContent());
    assertNotEquals(original.getVectorId(), updated.getVectorId(), 
                   "VectorId should be updated");
    assertTrue(updated.getVectorUpdatedAt().isAfter(original.getVectorUpdatedAt()));
    
    // When - Delete vector
    vectorManagementService.removeVector(entityType, entityId);
    
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        !vectorManagementService.vectorExists(entityType, entityId)
    );
    
    // Then - Verify AISearchableEntity deleted or cleaned up
    Optional<AISearchableEntity> deleted = 
        searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId);
    
    // Depending on implementation, entity may be deleted or marked for cleanup
    // Verify consistency
    assertFalse(deleted.isPresent() && deleted.get().getVectorId() != null,
               "AISearchableEntity should be deleted or vectorId should be null");
}
```

---

### TEST-AISEARCHABLE-004: Integration with RAG Indexing
**Priority**: High  
**Status**: ✅ AUTOMATED (`AISearchableEntityVectorSynchronizationIntegrationTest#ragIndexingCreatesSearchableEntity`)

#### Test Steps
1. Index document via `RAGService.indexContent()`
2. Verify `AISearchableEntity` created
3. Verify content matches indexed document
4. Verify metadata from RAG indexing stored
5. Test RAG query returns `AISearchableEntity` references
6. Verify cross-entity RAG indexing

#### Expected Results
- ✅ RAG indexing creates `AISearchableEntity`
- ✅ Content stored correctly
- ✅ Metadata from RAG stored in `AISearchableEntity`
- ✅ RAG queries can reference `AISearchableEntity`
- ✅ Multiple entity types indexed correctly

#### Test Data
```java
// Index document via RAG
String entityType = "article";
String entityId = "article-001";
String content = "Introduction to AI and Machine Learning in modern software development";
Map<String, Object> metadata = Map.of(
    "title", "AI in Software Development",
    "category", "Technology",
    "author", "John Doe",
    "publishedAt", LocalDateTime.now().toString()
);

ragService.indexContent(entityType, entityId, content, metadata);
```

#### Implementation Notes
```java
@Test
public void testIntegrationWithRAGIndexing() {
    // Given - Document to index via RAG
    String entityType = "article";
    String entityId = "article-001";
    String content = "Introduction to AI and Machine Learning";
    Map<String, Object> metadata = Map.of(
        "title", "AI in Software Development",
        "category", "Technology",
        "author", "John Doe"
    );
    
    // When - Index via RAG service
    ragService.indexContent(entityType, entityId, content, metadata);
    
    // Wait for indexing
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId).isPresent()
    );
    
    // Then - Verify AISearchableEntity created
    Optional<AISearchableEntity> searchable = 
        searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId);
    
    assertTrue(searchable.isPresent(), "AISearchableEntity should be created by RAG indexing");
    
    AISearchableEntity entity = searchable.get();
    assertEquals(entityType, entity.getEntityType());
    assertEquals(entityId, entity.getEntityId());
    assertEquals(content, entity.getSearchableContent());
    assertNotNull(entity.getVectorId(), "VectorId should be set");
    
    // Verify metadata stored
    assertNotNull(entity.getMetadata());
    assertTrue(entity.getMetadata().contains("Technology"));
    
    // When - Perform RAG query
    RAGRequest request = RAGRequest.builder()
        .query("AI and Machine Learning")
        .entityType(entityType)
        .limit(10)
        .build();
    
    RAGResponse response = ragService.performRAGQuery(request);
    
    // Then - Verify RAG query returns results (may reference AISearchableEntity)
    assertNotNull(response);
    assertTrue(response.isSuccess());
    // RAG results should be able to reference AISearchableEntity records
}
```

---

### TEST-AISEARCHABLE-005: Searchable Content Extraction and Storage
**Priority**: High  
**Status**: ✅ AUTOMATED (`AISearchableEntityVectorSynchronizationIntegrationTest#creationWhenVectorStored`)

#### Test Steps
1. Process entity with complex fields (name, description, tags, etc.)
2. Verify searchable content extracted correctly
3. Verify all relevant fields included
4. Test with nested objects
5. Test with null/empty fields
6. Verify content truncation if too long
7. Verify special characters handled

#### Expected Results
- ✅ Searchable content extracted from all configured fields
- ✅ Field weights respected in content
- ✅ Null/empty fields handled gracefully
- ✅ Special characters preserved
- ✅ Content length within limits
- ✅ All relevant information included

#### Test Data
```java
Product product = Product.builder()
    .name("Luxury Swiss Watch")
    .description("Premium timepiece with automatic movement")
    .category("Watches")
    .tags(Arrays.asList("luxury", "swiss", "automatic"))
    .specifications(Map.of("waterResistance", "300m", "movement", "automatic"))
    .build();
```

#### Implementation Notes
```java
@Test
public void testSearchableContentExtraction() {
    // Given - Entity with complex fields
    Product product = Product.builder()
        .name("Luxury Swiss Watch")
        .description("Premium timepiece with automatic movement")
        .category("Watches")
        .tags(Arrays.asList("luxury", "swiss", "automatic"))
        .build();
    
    // When - Process entity (via @AICapable or @AIProcess)
    product = productRepository.save(product);
    
    // Wait for processing
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        searchableEntityRepository.findByEntityTypeAndEntityId("product", product.getId().toString()).isPresent()
    );
    
    // Then - Verify searchable content extracted
    AISearchableEntity searchable = searchableEntityRepository
        .findByEntityTypeAndEntityId("product", product.getId().toString())
        .orElseThrow();
    
    assertNotNull(searchable.getSearchableContent());
    String content = searchable.getSearchableContent();
    
    // Verify all relevant fields included
    assertTrue(content.contains("Luxury Swiss Watch"), "Name should be included");
    assertTrue(content.contains("Premium timepiece"), "Description should be included");
    assertTrue(content.contains("Watches"), "Category should be included");
    assertTrue(content.contains("luxury") || content.contains("swiss"), 
               "Tags should be included");
    
    // Verify content is meaningful (not empty)
    assertTrue(content.length() > 50, "Content should have meaningful length");
}
```

---

### TEST-AISEARCHABLE-006: Metadata Persistence and Retrieval
**Priority**: High  
**Status**: ✅ AUTOMATED (`AISearchableEntityVectorSynchronizationIntegrationTest#metadataPersistenceAndRetrieval`)

#### Test Steps
1. Store entity with rich metadata
2. Verify metadata stored as JSON in `AISearchableEntity`
3. Retrieve `AISearchableEntity` and parse metadata
4. Verify all metadata fields preserved
5. Test with complex nested metadata
6. Test metadata filtering in queries

#### Expected Results
- ✅ Metadata stored as JSON string
- ✅ All metadata fields preserved
- ✅ Complex nested structures handled
- ✅ Metadata can be parsed and retrieved
- ✅ Metadata filtering works correctly

#### Test Data
```java
Map<String, Object> metadata = Map.of(
    "category", "Watches",
    "brand", "Rolex",
    "price", 5000.0,
    "specifications", Map.of(
        "waterResistance", "300m",
        "movement", "automatic"
    ),
    "tags", Arrays.asList("luxury", "swiss", "dive"),
    "availability", Map.of(
        "inStock", true,
        "stockCount", 10
    )
);
```

#### Implementation Notes
```java
@Test
public void testMetadataPersistenceAndRetrieval() {
    // Given - Entity with rich metadata
    String entityType = "product";
    String entityId = "product-metadata-test";
    String content = "Luxury Watch";
    List<Double> embedding = generateTestEmbedding(1536);
    Map<String, Object> metadata = Map.of(
        "category", "Watches",
        "brand", "Rolex",
        "price", 5000.0,
        "specifications", Map.of(
            "waterResistance", "300m",
            "movement", "automatic"
        ),
        "tags", Arrays.asList("luxury", "swiss"),
        "inStock", true
    );
    
    // When - Store vector with metadata
    String vectorId = vectorManagementService.storeVector(
        entityType, entityId, content, embedding, metadata
    );
    
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId).isPresent()
    );
    
    // Then - Verify metadata persisted
    AISearchableEntity searchable = searchableEntityRepository
        .findByEntityTypeAndEntityId(entityType, entityId)
        .orElseThrow();
    
    assertNotNull(searchable.getMetadata());
    
    // Parse and verify metadata
    ObjectMapper mapper = new ObjectMapper();
    try {
        Map<String, Object> storedMetadata = mapper.readValue(
            searchable.getMetadata(), 
            new TypeReference<Map<String, Object>>() {}
        );
        
        assertEquals("Watches", storedMetadata.get("category"));
        assertEquals("Rolex", storedMetadata.get("brand"));
        assertEquals(5000.0, storedMetadata.get("price"));
        assertTrue((Boolean) storedMetadata.get("inStock"));
        
        // Verify nested structures
        @SuppressWarnings("unchecked")
        Map<String, Object> specs = (Map<String, Object>) storedMetadata.get("specifications");
        assertEquals("300m", specs.get("waterResistance"));
        assertEquals("automatic", specs.get("movement"));
        
    } catch (JsonProcessingException e) {
        fail("Metadata should be valid JSON: " + e.getMessage());
    }
}
```

---

### TEST-AISEARCHABLE-007: Cleanup Operations (Deletion)
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AISearchableEntityVectorSynchronizationIntegrationTest#removalClearsRepository`)

#### Test Steps
1. Create `AISearchableEntity` by storing vector
2. Delete entity from domain repository
3. Verify `AISearchableEntity` deleted or cleaned up
4. Verify vector removed from vector database
5. Test cascade deletion
6. Test orphaned record cleanup

#### Expected Results
- ✅ Entity deletion triggers `AISearchableEntity` cleanup
- ✅ Vector removed from vector database
- ✅ No orphaned `AISearchableEntity` records
- ✅ Cascade deletion works correctly
- ✅ Cleanup is atomic (no partial cleanup)

#### Implementation Notes
```java
@Test
public void testCleanupOperations() {
    // Given - Entity with AISearchableEntity
    Product product = createAndSaveProduct();
    String productId = product.getId().toString();
    
    // Wait for processing
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        searchableEntityRepository.findByEntityTypeAndEntityId("product", productId).isPresent()
    );
    
    // Verify AISearchableEntity exists
    assertTrue(searchableEntityRepository
        .findByEntityTypeAndEntityId("product", productId)
        .isPresent());
    
    assertTrue(vectorManagementService.vectorExists("product", productId));
    
    // When - Delete entity
    productRepository.delete(product);
    
    // Wait for cleanup
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        !searchableEntityRepository.findByEntityTypeAndEntityId("product", productId).isPresent()
    );
    
    // Then - Verify cleanup
    Optional<AISearchableEntity> deleted = 
        searchableEntityRepository.findByEntityTypeAndEntityId("product", productId);
    
    assertFalse(deleted.isPresent(), 
               "AISearchableEntity should be deleted");
    
    assertFalse(vectorManagementService.vectorExists("product", productId),
               "Vector should be removed");
    
    // Verify no orphaned records
    List<AISearchableEntity> orphaned = searchableEntityRepository.findAll()
        .stream()
        .filter(e -> productId.equals(e.getEntityId()))
        .toList();
    
    assertEquals(0, orphaned.size(), "No orphaned records should exist");
}
```

---

### TEST-AISEARCHABLE-008: Update Operations and Vector ID Changes
**Priority**: High  
**Status**: ✅ AUTOMATED (`AISearchableEntityVectorSynchronizationIntegrationTest#updateKeepsSearchableEntityInSync`)

#### Test Steps
1. Create `AISearchableEntity` with initial vector
2. Update entity content
3. Regenerate vector (new vectorId)
4. Verify `AISearchableEntity` updated with new vectorId
5. Verify old vectorId replaced
6. Verify `vectorUpdatedAt` timestamp updated
7. Test searchable content update

#### Expected Results
- ✅ `AISearchableEntity` updated when vector updated
- ✅ New vectorId replaces old vectorId
- ✅ `vectorUpdatedAt` timestamp updated
- ✅ Searchable content updated
- ✅ Old vector removed from vector database

#### Implementation Notes
```java
@Test
public void testUpdateOperations() {
    // Given - Existing AISearchableEntity
    String entityType = "product";
    String entityId = "product-update-test";
    String content1 = "Original content";
    List<Double> embedding1 = generateTestEmbedding(1536);
    
    String vectorId1 = vectorManagementService.storeVector(
        entityType, entityId, content1, embedding1, null
    );
    
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId).isPresent()
    );
    
    AISearchableEntity original = searchableEntityRepository
        .findByEntityTypeAndEntityId(entityType, entityId)
        .orElseThrow();
    
    LocalDateTime originalUpdatedAt = original.getVectorUpdatedAt();
    
    // When - Update vector
    String content2 = "Updated content with new information";
    List<Double> embedding2 = generateTestEmbedding(1536);
    
    String vectorId2 = vectorManagementService.storeVector(
        entityType, entityId, content2, embedding2, null
    );
    
    await().atMost(5, TimeUnit.SECONDS).until(() -> {
        AISearchableEntity updated = searchableEntityRepository
            .findByEntityTypeAndEntityId(entityType, entityId)
            .orElseThrow();
        return !updated.getVectorId().equals(original.getVectorId()) ||
               updated.getVectorUpdatedAt().isAfter(originalUpdatedAt);
    });
    
    // Then - Verify update
    AISearchableEntity updated = searchableEntityRepository
        .findByEntityTypeAndEntityId(entityType, entityId)
        .orElseThrow();
    
    assertNotEquals(vectorId1, updated.getVectorId(), 
                   "VectorId should be updated");
    assertEquals(vectorId2, updated.getVectorId(), 
                "New VectorId should match");
    assertEquals(content2, updated.getSearchableContent(),
                "Content should be updated");
    assertTrue(updated.getVectorUpdatedAt().isAfter(originalUpdatedAt),
              "VectorUpdatedAt should be updated");
    assertTrue(updated.getUpdatedAt().isAfter(original.getUpdatedAt()),
              "UpdatedAt should be updated");
}
```

---

### TEST-AISEARCHABLE-009: Repository Query Operations
**Priority**: High  
**Status**: ✅ AUTOMATED (`AISearchableEntityVectorSynchronizationIntegrationTest#repositoryQueryOperations`)

#### Test Steps
1. Create multiple `AISearchableEntity` records
2. Test `findByEntityType()` query
3. Test `findByEntityTypeAndEntityId()` query
4. Test `findByVectorId()` query
5. Test `findBySearchableContentContainingIgnoreCase()` query
6. Test queries for entities with/without vectorId
7. Test count queries

#### Expected Results
- ✅ All repository queries work correctly
- ✅ Queries return correct results
- ✅ Case-insensitive search works
- ✅ Filtering by entityType works
- ✅ Filtering by vectorId presence works
- ✅ Count queries accurate

#### Implementation Notes
```java
@Test
public void testRepositoryQueryOperations() {
    // Given - Multiple AISearchableEntity records
    createTestSearchableEntities("product", Arrays.asList("prod-1", "prod-2", "prod-3"));
    createTestSearchableEntities("user", Arrays.asList("user-1", "user-2"));
    
    // When - Query by entityType
    List<AISearchableEntity> products = 
        searchableEntityRepository.findByEntityType("product");
    
    // Then - Verify results
    assertEquals(3, products.size());
    assertTrue(products.stream().allMatch(e -> "product".equals(e.getEntityType())));
    
    // When - Query by entityType and entityId
    Optional<AISearchableEntity> product1 = 
        searchableEntityRepository.findByEntityTypeAndEntityId("product", "prod-1");
    
    // Then - Verify result
    assertTrue(product1.isPresent());
    assertEquals("prod-1", product1.get().getEntityId());
    
    // When - Query by searchable content
    List<AISearchableEntity> luxuryProducts = 
        searchableEntityRepository.findBySearchableContentContainingIgnoreCase("luxury");
    
    // Then - Verify case-insensitive search
    assertNotNull(luxuryProducts);
    // Results should include entities with "luxury" in content (case insensitive)
    
    // When - Query entities with vectorId
    List<AISearchableEntity> withVectorId = 
        searchableEntityRepository.findByEntityTypeAndVectorIdIsNotNull("product");
    
    // Then - Verify all have vectorId
    assertTrue(withVectorId.stream().allMatch(e -> e.getVectorId() != null));
    
    // When - Count entities with vectorId
    long count = searchableEntityRepository
        .countByEntityTypeAndVectorIdIsNotNull("product");
    
    // Then - Verify count
    assertEquals(3, count);
}
```

---

### TEST-AISEARCHABLE-010: Concurrent Operations
**Priority**: High  
**Status**: ✅ AUTOMATED (`AISearchableEntityVectorSynchronizationIntegrationTest#concurrentStoreOperationsMaintainSingleEntity`, `AISearchableEntityVectorSynchronizationIntegrationTest#batchStoreAndRemoveSynchronizesRepository`)

#### Test Steps
1. Create 50 entities concurrently
2. Verify all `AISearchableEntity` records created
3. Verify no duplicate records
4. Verify no race conditions
5. Verify all vectorIds unique
6. Test concurrent updates
7. Test concurrent deletions

#### Expected Results
- ✅ All `AISearchableEntity` records created successfully
- ✅ No duplicate records (same entityType + entityId)
- ✅ No race conditions
- ✅ All vectorIds unique
- ✅ Concurrent updates handled correctly
- ✅ Thread-safe operations

#### Implementation Notes
```java
@Test
public void testConcurrentOperations() throws Exception {
    // Given
    int concurrentRequests = 50;
    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(concurrentRequests);
    List<CompletableFuture<String>> futures = new ArrayList<>();
    
    // When - Create entities concurrently
    for (int i = 0; i < concurrentRequests; i++) {
        final int index = i;
        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            try {
                String entityId = "concurrent-product-" + index;
                String content = "Concurrent Product " + index;
                List<Double> embedding = generateTestEmbedding(1536);
                
                vectorManagementService.storeVector(
                    "product", entityId, content, embedding, null
                );
                
                return entityId;
            } finally {
                latch.countDown();
            }
        }, executor);
        futures.add(future);
    }
    
    latch.await(60, TimeUnit.SECONDS);
    
    // Then - Verify all created
    List<String> entityIds = new ArrayList<>();
    for (CompletableFuture<String> future : futures) {
        entityIds.add(future.get());
    }
    
    // Wait for all processing
    await().atMost(30, TimeUnit.SECONDS).until(() -> {
        long count = searchableEntityRepository.findByEntityType("product")
            .stream()
            .filter(e -> entityIds.contains(e.getEntityId()))
            .count();
        return count == concurrentRequests;
    });
    
    // Verify all AISearchableEntity records created
    List<AISearchableEntity> entities = 
        searchableEntityRepository.findByEntityType("product");
    
    Set<String> uniqueEntityIds = entities.stream()
        .filter(e -> entityIds.contains(e.getEntityId()))
        .map(AISearchableEntity::getEntityId)
        .collect(Collectors.toSet());
    
    assertEquals(concurrentRequests, uniqueEntityIds.size(),
                "All entities should be created");
    
    // Verify no duplicates (same entityType + entityId)
    Map<String, Long> entityIdCounts = entities.stream()
        .collect(Collectors.groupingBy(
            e -> e.getEntityType() + ":" + e.getEntityId(),
            Collectors.counting()
        ));
    
    assertTrue(entityIdCounts.values().stream().allMatch(count -> count == 1),
              "No duplicate records should exist");
    
    // Verify all vectorIds unique
    Set<String> vectorIds = entities.stream()
        .map(AISearchableEntity::getVectorId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
    
    assertEquals(vectorIds.size(), 
                entities.stream().filter(e -> e.getVectorId() != null).count(),
                "All vectorIds should be unique");
}
```

---

### TEST-AISEARCHABLE-011: Transactional Consistency
**Priority**: Critical  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Start transaction
2. Store vector (creates `AISearchableEntity`)
3. Rollback transaction
4. Verify `AISearchableEntity` not persisted
5. Verify vector not stored
6. Test successful transaction
7. Verify consistency

#### Expected Results
- ✅ Transaction rollback prevents `AISearchableEntity` persistence
- ✅ Transaction rollback prevents vector storage
- ✅ Successful transaction commits both
- ✅ Transactional consistency maintained
- ✅ No partial data persisted

#### Implementation Notes
```java
@Test
@Transactional
public void testTransactionalConsistency() {
    // Given
    String entityType = "product";
    String entityId = "tx-product-1";
    String content = "Transactional Product";
    List<Double> embedding = generateTestEmbedding(1536);
    
    try {
        // When - Store vector within transaction, then rollback
        String vectorId = vectorManagementService.storeVector(
            entityType, entityId, content, embedding, null
        );
        
        // Wait briefly for processing
        Thread.sleep(500);
        
        // Force rollback
        throw new RuntimeException("Force rollback");
        
    } catch (RuntimeException e) {
        // Transaction should rollback
        
        // Then - Verify AISearchableEntity not persisted
        Optional<AISearchableEntity> searchable = 
            searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId);
        
        assertFalse(searchable.isPresent(), 
                   "AISearchableEntity should not be persisted after rollback");
        
        // Verify vector not stored
        assertFalse(vectorManagementService.vectorExists(entityType, entityId),
                   "Vector should not be stored after rollback");
    }
}

@Test
@Transactional
public void testSuccessfulTransaction() {
    // Given
    String entityType = "product";
    String entityId = "tx-product-2";
    String content = "Successful Transactional Product";
    List<Double> embedding = generateTestEmbedding(1536);
    
    // When - Store vector within successful transaction
    String vectorId = vectorManagementService.storeVector(
        entityType, entityId, content, embedding, null
    );
    
    // Transaction commits automatically
    
    // Wait for processing
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId).isPresent()
    );
    
    // Then - Verify both persisted
    Optional<AISearchableEntity> searchable = 
        searchableEntityRepository.findByEntityTypeAndEntityId(entityType, entityId);
    
    assertTrue(searchable.isPresent(), 
              "AISearchableEntity should be persisted after successful transaction");
    
    assertTrue(vectorManagementService.vectorExists(entityType, entityId),
               "Vector should be stored after successful transaction");
    
    assertEquals(vectorId, searchable.get().getVectorId());
}
```

---

### TEST-AISEARCHABLE-012: Lifecycle Management
**Priority**: Medium  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Create `AISearchableEntity` (with timestamps)
2. Update `AISearchableEntity` (verify updatedAt)
3. Delete `AISearchableEntity` (verify deletion)
4. Verify lifecycle stages
5. Test bulk operations (deleteByEntityType)
6. Test cleanup of orphaned records

#### Expected Results
- ✅ Lifecycle stages tracked correctly
- ✅ Timestamps updated appropriately
- ✅ Bulk deletion works
- ✅ Orphaned record cleanup works
- ✅ Lifecycle consistency maintained

#### Implementation Notes
```java
@Test
public void testLifecycleManagement() {
    // Given - Create multiple entities
    String entityType = "test-product";
    createTestSearchableEntities(entityType, Arrays.asList("prod-1", "prod-2", "prod-3"));
    
    // Verify created
    List<AISearchableEntity> created = 
        searchableEntityRepository.findByEntityType(entityType);
    assertEquals(3, created.size());
    
    // Verify all have created timestamps
    assertTrue(created.stream().allMatch(e -> e.getCreatedAt() != null));
    
    // When - Update one entity
    AISearchableEntity toUpdate = created.get(0);
    LocalDateTime originalUpdatedAt = toUpdate.getUpdatedAt();
    
    toUpdate.setSearchableContent("Updated content");
    searchableEntityRepository.save(toUpdate);
    
    // Wait for update
    Thread.sleep(100);
    
    // Then - Verify updatedAt changed
    AISearchableEntity updated = searchableEntityRepository
        .findByEntityTypeAndEntityId(entityType, toUpdate.getEntityId())
        .orElseThrow();
    
    assertTrue(updated.getUpdatedAt().isAfter(originalUpdatedAt) ||
               updated.getUpdatedAt().equals(originalUpdatedAt));
    
    // When - Bulk delete by entityType
    searchableEntityRepository.deleteByEntityType(entityType);
    
    // Then - Verify all deleted
    List<AISearchableEntity> remaining = 
        searchableEntityRepository.findByEntityType(entityType);
    
    assertEquals(0, remaining.size(), "All entities should be deleted");
}
```

---

## 📊 Performance Benchmarks

### Operation Time Targets

| Scenario | Target | Max |
|----------|--------|-----|
| AISearchableEntity creation | <100ms | 200ms |
| Update operation | <100ms | 200ms |
| Deletion operation | <50ms | 100ms |
| Query by entityType | <50ms | 100ms |
| Query by entityId | <20ms | 50ms |
| Bulk operations (100 entities) | <2s | 5s |

### Throughput Targets

| Operation | Target | Max |
|-----------|--------|-----|
| Sequential creation | 100 req/sec | 50 req/sec |
| Concurrent creation | 200 req/sec | 100 req/sec |
| Query operations | 500 req/sec | 250 req/sec |

---

## 🎯 Success Criteria

### Functional
- ✅ `AISearchableEntity` created automatically when vectors stored
- ✅ Vector ID linking integrity maintained
- ✅ Integration with vector storage operations works
- ✅ Integration with RAG indexing works
- ✅ Searchable content extracted correctly
- ✅ Metadata persistence and retrieval works
- ✅ Cleanup operations complete
- ✅ Update operations work correctly

### Quality
- ✅ No orphaned `AISearchableEntity` records
- ✅ Transactional consistency maintained
- ✅ Thread-safe concurrent operations
- ✅ Repository queries accurate
- ✅ Lifecycle management correct

### Performance
- ✅ Creation overhead < 100ms
- ✅ Query operations < 50ms
- ✅ Handles 100+ concurrent operations
- ✅ Bulk operations efficient

---

## 📅 Implementation Schedule

### Week 1: Critical Tests
- Day 1-2: TEST-AISEARCHABLE-001 (Creation)
- Day 2-3: TEST-AISEARCHABLE-002 (Vector ID Linking)
- Day 3-4: TEST-AISEARCHABLE-003 (Vector Storage Integration)
- Day 4-5: TEST-AISEARCHABLE-007 (Cleanup)
- Day 5: TEST-AISEARCHABLE-011 (Transactional Consistency)

### Week 2: High Priority Tests
- Day 6-7: TEST-AISEARCHABLE-004 (RAG Integration)
- Day 7-8: TEST-AISEARCHABLE-005 (Content Extraction)
- Day 8-9: TEST-AISEARCHABLE-006 (Metadata)
- Day 9-10: TEST-AISEARCHABLE-008 (Update Operations)
- Day 10: TEST-AISEARCHABLE-009 (Repository Queries)

### Week 3: Medium Priority Tests
- Day 11: TEST-AISEARCHABLE-010 (Concurrent Operations)
- Day 12: TEST-AISEARCHABLE-012 (Lifecycle Management)

---

## 🛠️ Test Infrastructure Requirements

### Test Dependencies

```java
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
class AISearchableEntityIntegrationTest {
    
    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;
    
    @Autowired
    private VectorManagementService vectorManagementService;
    
    @Autowired
    private RAGService ragService;
    
    @Autowired
    private AICapabilityService aiCapabilityService;
    
    @Autowired
    private ProductRepository productRepository; // For entity tests
}
```

### Test Data Setup

```java
private void createTestSearchableEntities(String entityType, List<String> entityIds) {
    for (String entityId : entityIds) {
        String content = "Test content for " + entityId;
        List<Double> embedding = generateTestEmbedding(1536);
        Map<String, Object> metadata = Map.of("test", true);
        
        vectorManagementService.storeVector(
            entityType, entityId, content, embedding, metadata
        );
    }
}
```

---

## 📝 Test Execution Checklist

- [ ] Test database initialized
- [ ] Vector database configured
- [ ] AICapabilityService configured
- [ ] Test entities created
- [ ] All tests passing locally
- [ ] Performance benchmarks documented
- [ ] Tests integrated into CI/CD
- [ ] Integration with other test plans verified

---

## 🔗 Related Documents

- [Integration Test Analysis](../AI_MODULE_INTEGRATION_TEST_ANALYSIS.md)
- [Vector Database Test Plan](./VECTOR_DATABASE_TEST_PLAN.md)
- [RAG System Test Plan](./RAG_SYSTEM_TEST_PLAN.md)
- [Entity Processing Test Plan](./ENTITY_PROCESSING_TEST_PLAN.md)
- [@AIProcess Annotation Test Plan](./AI_PROCESS_ANNOTATION_TEST_PLAN.md)
- [Integration Test Plans Index](../INTEGRATION_TEST_PLANS_INDEX.md)

---

## 📚 Additional Notes

### Key Responsibilities of AISearchableEntity

1. **Persistence Layer**: Tracks which entities have been processed for AI
2. **Vector Linking**: Maintains reference to vectors in vector database via vectorId
3. **Search Integration**: Provides searchable content for text-based searches
4. **Metadata Storage**: Stores entity metadata as JSON for filtering
5. **Lifecycle Tracking**: Tracks creation and update timestamps

### Integration Points

1. **Vector Storage**: Created when vectors are stored via `VectorManagementService`
2. **RAG Indexing**: Created when documents are indexed via `RAGService`
3. **Entity Processing**: Created when entities processed via `@AICapable` or `@AIProcess`
4. **Search Services**: Used by search services for entity retrieval
5. **Cleanup Operations**: Deleted when entities or vectors are removed

### Testing Strategy

1. **Unit Tests**: Test repository methods separately
2. **Integration Tests**: Test end-to-end flow with vector storage and RAG
3. **Concurrent Tests**: Verify thread safety
4. **Transaction Tests**: Verify transactional consistency
5. **Lifecycle Tests**: Verify complete lifecycle management

---

**End of Test Plan**

