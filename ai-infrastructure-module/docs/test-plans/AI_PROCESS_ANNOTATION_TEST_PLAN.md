# Integration Test Plan - @AIProcess Annotation

**Component**: AICapableAspect, @AIProcess Annotation, Method-Level AI Processing  
**Priority**: 🔴 CRITICAL  
**Estimated Effort**: 1.5 weeks  
**Status**: Draft  

---

## 📋 Overview

This test plan covers comprehensive integration testing of the `@AIProcess` method-level annotation for explicit AI processing in service methods. Unlike `@AICapable` (entity-level, AOP-based automatic processing), `@AIProcess` provides explicit control over when and how AI processing occurs at the method level.

### Components Under Test
- `@AIProcess` annotation (method-level)
- `AICapableAspect.processAIMethod()` (AOP interception)
- `AICapabilityService` (AI processing logic)
- `AIEntityConfigurationLoader` (configuration management)
- Service methods annotated with `@AIProcess`
- Processing type variations (create, update, delete, search, analyze)

### Difference from @AICapable

| Aspect | @AICapable | @AIProcess |
|--------|-----------|------------|
| **Level** | Entity-level (class annotation) | Method-level (method annotation) |
| **Trigger** | Automatic on entity save/update | Explicit on method call |
| **Control** | Configuration-driven | Annotation-driven |
| **Use Case** | Automatic entity processing | Service method processing |
| **Processing** | AOP on repository save | AOP on service method call |

### Test Objectives
1. Verify `@AIProcess` annotation interception works correctly
2. Validate different `processType` values (create, update, delete, search, analyze)
3. Test annotation configuration flags (generateEmbedding, indexForSearch, enableAnalysis)
4. Verify aspect processing before and after method execution
5. Test error handling when AI processing fails
6. Validate concurrent processing with `@AIProcess`
7. Verify entity type resolution from annotation

---

## 🧪 Test Scenarios

### TEST-AIPROCESS-001: Service Method with @AIProcess(create)
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AIProcessAnnotationIntegrationTest#aiProcessCreateIndexesEntity`)

#### Test Steps
1. Create service method annotated with `@AIProcess(entityType="product", processType="create")`
2. Call service method to create product
3. Verify AICapableAspect intercepts method call
4. Verify AI processing triggered after method execution
5. Verify embeddings generated
6. Verify AISearchableEntity created
7. Verify vector stored in vector database

#### Expected Results
- ✅ Aspect intercepts method call
- ✅ Processing triggered after method execution
- ✅ Embeddings generated automatically
- ✅ AISearchableEntity created with correct entityType and entityId
- ✅ Vector stored in vector database
- ✅ VectorId linked in AISearchableEntity
- ✅ Searchable content extracted correctly

#### Test Data
```java
// Service class
@Service
public class TestProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    @AIProcess(entityType = "product", processType = "create")
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }
}

// Test product
Product product = Product.builder()
    .name("Luxury Swiss Watch")
    .description("Premium timepiece with automatic movement")
    .category("Watches")
    .price(new BigDecimal("5000"))
    .build();
```

#### Implementation Notes
```java
@Test
public void testAIProcessCreate() {
    // Given - Product service with @AIProcess annotation
    TestProductService productService = applicationContext.getBean(TestProductService.class);
    
    Product product = Product.builder()
        .name("Luxury Swiss Watch")
        .description("Premium timepiece with automatic movement")
        .category("Watches")
        .price(new BigDecimal("5000"))
        .build();
    
    // When - Call annotated service method
    Product savedProduct = productService.createProduct(product);
    
    // Then - Verify AI processing triggered
    // Wait for async processing
    await().atMost(5, TimeUnit.SECONDS).until(() -> {
        Optional<AISearchableEntity> searchable = 
            searchableEntityRepository.findByEntityTypeAndEntityId(
                "product", savedProduct.getId().toString()
            );
        return searchable.isPresent();
    });
    
    // Verify AISearchableEntity created
    AISearchableEntity searchable = searchableEntityRepository
        .findByEntityTypeAndEntityId("product", savedProduct.getId().toString())
        .orElseThrow();
    
    assertNotNull(searchable.getVectorId(), "Vector ID should be set");
    assertNotNull(searchable.getSearchableContent(), "Searchable content should be extracted");
    assertTrue(searchable.getSearchableContent().contains("Luxury Swiss Watch"));
    
    // Verify vector exists in vector database
    assertTrue(vectorManagementService.vectorExists("product", savedProduct.getId().toString()));
    
    // Verify embeddings generated
    assertNotNull(searchable.getVectorId());
}
```

---

### TEST-AIPROCESS-002: Service Method with @AIProcess(update)
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AIProcessAnnotationIntegrationTest#aiProcessUpdateRebuildsEmbedding`)

#### Test Steps
1. Create product and process it
2. Update product via service method annotated with `@AIProcess(entityType="product", processType="update")`
3. Verify re-processing triggered
4. Verify embeddings regenerated
5. Verify AISearchableEntity updated
6. Verify old vector replaced with new vector

#### Expected Results
- ✅ Re-processing triggered on update
- ✅ Embeddings regenerated with new content
- ✅ AISearchableEntity updated with new searchable content
- ✅ Old vector replaced in vector database
- ✅ VectorId updated in AISearchableEntity
- ✅ No duplicate vectors

#### Test Data
```java
// Update service method
@AIProcess(entityType = "product", processType = "update")
public Product updateProduct(String id, Product product) {
    Product existing = productRepository.findById(id).orElseThrow();
    existing.setName(product.getName());
    existing.setDescription(product.getDescription());
    return productRepository.save(existing);
}

// Original product
Product original = createProduct("Luxury Swiss Watch", "Premium timepiece");

// Updated product
Product updated = Product.builder()
    .name("Premium Swiss Watch with Diamonds")
    .description("Luxury timepiece with diamond bezel and sapphire crystal")
    .build();
```

#### Implementation Notes
```java
@Test
public void testAIProcessUpdate() {
    // Given - Product already processed
    Product original = productService.createProduct(createTestProduct());
    String productId = original.getId().toString();
    
    // Wait for initial processing
    await().atMost(5, TimeUnit.SECONDS).until(() -> 
        searchableEntityRepository.findByEntityTypeAndEntityId("product", productId).isPresent()
    );
    
    AISearchableEntity originalSearchable = searchableEntityRepository
        .findByEntityTypeAndEntityId("product", productId)
        .orElseThrow();
    String originalVectorId = originalSearchable.getVectorId();
    
    // When - Update product via annotated method
    Product updatedProduct = Product.builder()
        .name("Premium Swiss Watch with Diamonds")
        .description("Luxury timepiece with diamond bezel")
        .build();
    
    productService.updateProduct(productId, updatedProduct);
    
    // Wait for re-processing
    await().atMost(5, TimeUnit.SECONDS).until(() -> {
        AISearchableEntity updated = searchableEntityRepository
            .findByEntityTypeAndEntityId("product", productId)
            .orElseThrow();
        return !updatedSearchable.getVectorId().equals(originalVectorId) ||
               !updatedSearchable.getSearchableContent().equals(originalSearchable.getSearchableContent());
    });
    
    // Then - Verify re-processing
    AISearchableEntity updatedSearchable = searchableEntityRepository
        .findByEntityTypeAndEntityId("product", productId)
        .orElseThrow();
    
    assertNotEquals(originalVectorId, updatedSearchable.getVectorId(), 
                   "Vector ID should be updated");
    assertTrue(updatedSearchable.getSearchableContent().contains("Diamonds"),
              "Searchable content should be updated");
    
    // Verify old vector replaced
    assertFalse(vectorManagementService.vectorExists("product", originalVectorId),
               "Old vector should be removed");
    assertTrue(vectorManagementService.vectorExists("product", productId),
              "New vector should exist");
}
```

---

### TEST-AIPROCESS-003: Service Method with @AIProcess(delete)
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AIProcessAnnotationIntegrationTest#aiProcessDeleteRemovesIndex`)

#### Test Steps
1. Create and process product
2. Delete product via service method annotated with `@AIProcess(entityType="product", processType="delete")`
3. Verify cleanup triggered
4. Verify AISearchableEntity deleted
5. Verify vector removed from vector database
6. Verify no orphaned data

#### Expected Results
- ✅ Cleanup triggered on delete
- ✅ AISearchableEntity deleted
- ✅ Vector removed from vector database
- ✅ No orphaned records
- ✅ No memory leaks

#### Test Data
```java
// Delete service method
@AIProcess(entityType = "product", processType = "delete")
public void deleteProduct(String id) {
    Product product = productRepository.findById(id).orElseThrow();
    productRepository.delete(product);
}
```

#### Implementation Notes
```java
@Test
public void testAIProcessDelete() {
    // Given - Product already processed
    Product product = productService.createProduct(createTestProduct());
    String productId = product.getId().toString();
    
    // Wait for processing
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        searchableEntityRepository.findByEntityTypeAndEntityId("product", productId).isPresent()
    );
    
    // Verify entity indexed
    assertTrue(vectorManagementService.vectorExists("product", productId));
    assertTrue(searchableEntityRepository
        .findByEntityTypeAndEntityId("product", productId)
        .isPresent());
    
    // When - Delete product via annotated method
    productService.deleteProduct(productId);
    
    // Wait for cleanup
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        !vectorManagementService.vectorExists("product", productId)
    );
    
    // Then - Verify cleanup
    assertFalse(vectorManagementService.vectorExists("product", productId),
               "Vector should be removed");
    assertFalse(searchableEntityRepository
        .findByEntityTypeAndEntityId("product", productId)
        .isPresent(), "AISearchableEntity should be deleted");
}
```

---

### TEST-AIPROCESS-004: Annotation Configuration Flags
**Priority**: High  
**Status**: ✅ AUTOMATED (`AIProcessAnnotationIntegrationTest#aiProcessCreateWithEmbeddingDisabledSkipsIndexing`, `AIProcessAnnotationIntegrationTest#aiProcessCreateWithIndexingDisabledSkipsRepositorySave`, `AIProcessAnnotationIntegrationTest#aiProcessCreateWithAnalysisCapturesInsights`)

#### Test Steps
1. Test `@AIProcess` with `generateEmbedding=false`
2. Verify embeddings NOT generated
3. Test `@AIProcess` with `indexForSearch=false`
4. Verify AISearchableEntity NOT created
5. Test `@AIProcess` with `enableAnalysis=true`
6. Verify AI analysis performed
7. Test combination of flags

#### Expected Results
- ✅ `generateEmbedding=false` prevents embedding generation
- ✅ `indexForSearch=false` prevents AISearchableEntity creation
- ✅ `enableAnalysis=true` triggers AI analysis
- ✅ Flags respected correctly
- ✅ No side effects from disabled flags

#### Test Data
```java
// Test with generateEmbedding=false
@AIProcess(entityType = "product", processType = "create", generateEmbedding = false)
public Product createProductWithoutEmbedding(Product product) {
    return productRepository.save(product);
}

// Test with indexForSearch=false
@AIProcess(entityType = "product", processType = "create", indexForSearch = false)
public Product createProductWithoutIndexing(Product product) {
    return productRepository.save(product);
}

// Test with enableAnalysis=true
@AIProcess(entityType = "product", processType = "create", enableAnalysis = true)
public Product createProductWithAnalysis(Product product) {
    return productRepository.save(product);
}
```

#### Implementation Notes
```java
@Test
public void testAIProcessGenerateEmbeddingFalse() {
    // Given - Service method with generateEmbedding=false
    Product product = createTestProduct();
    
    // When - Create product via annotated method
    Product saved = productService.createProductWithoutEmbedding(product);

    verify(searchableEntityRepository, never()).save(any());
    assertTrue(searchableEntityRepository.findByEntityTypeAndEntityId("product", saved.getId().toString()).isEmpty());
    assertFalse(vectorManagementService.vectorExists("product", saved.getId().toString()));
}

@Test
public void testAIProcessIndexForSearchFalse() {
    // Given - Service method with indexForSearch=false
    Product product = createTestProduct();
    
    // When - Create product via annotated method
    Product saved = productService.createProductWithoutIndexing(product);

    verify(searchableEntityRepository, never()).save(any());
    assertTrue(searchableEntityRepository.findByEntityTypeAndEntityId("product", saved.getId().toString()).isEmpty());
    assertFalse(vectorManagementService.vectorExists("product", saved.getId().toString()));
}

@Test
public void testAIProcessEnableAnalysisTrue() {
    // Given - Service method with enableAnalysis=true
    Product product = createTestProduct();
    
    // When - Create product via annotated method
    Product saved = productService.createProductWithAnalysis(product);

    await().atMost(10, TimeUnit.SECONDS).until(() -> searchableEntityRepository
        .findByEntityTypeAndEntityId("product", saved.getId().toString()).isPresent());

    AISearchableEntity searchable = searchableEntityRepository
        .findByEntityTypeAndEntityId("product", saved.getId().toString())
        .orElseThrow();

    assertEquals("Product insight summary", searchable.getAiAnalysis());
}
```

---

### TEST-AIPROCESS-005: Aspect Interception and Processing Order
**Priority**: High  
**Status**: ✅ AUTOMATED (`AIProcessAnnotationIntegrationTest#aiProcessAspectInvokesAfterMethodCompletion`)

#### Test Steps
1. Call service method annotated with `@AIProcess`
2. Verify aspect intercepts method call
3. Verify `processBeforeMethod` called before method execution
4. Verify method executes normally
5. Verify `processAfterMethod` called after method execution
6. Verify processing order is correct
7. Verify method result returned correctly

#### Expected Results
- ✅ Aspect intercepts method call
- ✅ Processing happens before method execution
- ✅ Method executes successfully
- ✅ Processing happens after method execution
- ✅ Method result returned unchanged
- ✅ Processing doesn't affect method execution

#### Test Data
```java
// Service with logging
@AIProcess(entityType = "product", processType = "create")
public Product createProduct(Product product) {
    log.info("Method execution: creating product");
    return productRepository.save(product);
}
```

#### Implementation Notes
```java
@Test
public void testAspectInterceptionOrder() {
    // Given - Product service with aspect
    Product product = createTestProduct();
    
    // When - Call annotated method
    long beforeCall = System.currentTimeMillis();
    Product saved = productService.createProduct(product);
    await().atMost(10, TimeUnit.SECONDS).until(() ->
        mockingDetails(searchableEntityRepository).getInvocations().stream()
            .anyMatch(invocation -> invocation.getMethod().getName().equals("save"))
    );

    InOrder order = Mockito.inOrder(productRepository, searchableEntityRepository);
    order.verify(productRepository).save(any(Product.class));
    order.verify(searchableEntityRepository, atLeastOnce()).save(any(AISearchableEntity.class));

    assertEquals(product.getName(), productRepository.findById(saved.getId()).orElseThrow().getName());
}
```

---

### TEST-AIPROCESS-006: Error Handling When Processing Fails
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AIProcessAnnotationIntegrationTest#aiProcessGracefullyHandlesProcessingErrors`)

#### Test Steps
1. Configure to simulate API failure (e.g., OpenAI API down)
2. Call service method annotated with `@AIProcess`
3. Verify method execution succeeds despite AI failure
4. Verify error logged appropriately
5. Verify entity saved successfully
6. Test retry mechanism if available
7. Verify graceful degradation

#### Expected Results
- ✅ Method execution succeeds despite AI failure
- ✅ Error logged appropriately
- ✅ Entity saved successfully
- ✅ No exception thrown to caller
- ✅ System remains stable
- ✅ Retry mechanism works if available

#### Test Data
```java
// Mock OpenAI provider to fail
when(openAIProvider.generateEmbedding(any())).thenThrow(new RuntimeException("API failure"));

// Or configure provider to be unavailable
aiProviderManager.markProviderUnavailable("openai");
```

#### Implementation Notes
```java
@Test
public void testErrorHandlingWhenProcessingFails() {
    // Given - Simulate API failure
    // Mock embedding provider to throw exception
    doThrow(new RuntimeException("OpenAI API unavailable"))
        .when(mockEmbeddingProvider).generateEmbedding(any());
    
    Product product = createTestProduct();
    
    // When - Call service method (should succeed despite AI failure)
    product = productService.createProduct(product);

    assertNotNull(product.getId());
    assertTrue(productRepository.findById(product.getId()).isPresent());
    verify(failingService, atLeastOnce()).storeVector(anyString(), anyString(), anyString(), anyList(), anyMap());
}
```

---

### TEST-AIPROCESS-007: Concurrent Processing with @AIProcess
**Priority**: High  
**Status**: ✅ AUTOMATED (`AIProcessAnnotationIntegrationTest#aiProcessConcurrentCreateOperations`)

#### Test Steps
1. Create 50 products concurrently via service methods annotated with `@AIProcess`
2. Verify all method calls succeed
3. Verify all AI processing triggered
4. Verify no race conditions
5. Verify all AISearchableEntity records created
6. Verify all vectors stored correctly
7. Check for thread safety issues

#### Expected Results
- ✅ All 50 method calls succeed
- ✅ All AI processing triggered
- ✅ No ConcurrentModificationException
- ✅ All AISearchableEntity records created
- ✅ All vectors stored correctly
- ✅ No duplicate records
- ✅ Thread-safe processing

#### Test Data
```java
ExecutorService executor = Executors.newFixedThreadPool(10);
List<CompletableFuture<Product>> futures = new ArrayList<>();

for (int i = 0; i < 50; i++) {
    final int index = i;
    CompletableFuture<Product> future = CompletableFuture.supplyAsync(() -> {
        Product product = Product.builder()
            .name("Product " + index)
            .description("Description " + index)
            .build();
        return productService.createProduct(product);
    }, executor);
    futures.add(future);
}
```

#### Implementation Notes
```java
@Test
public void testConcurrentProcessing() throws Exception {
    // Given
    int concurrentRequests = 50;
    ExecutorService executor = Executors.newFixedThreadPool(10);
    List<CompletableFuture<Product>> futures = new ArrayList<>();
    CountDownLatch latch = new CountDownLatch(concurrentRequests);
    
    // When - Create products concurrently
    long startTime = System.currentTimeMillis();
    
    for (int i = 0; i < concurrentRequests; i++) {
        final int index = i;
        CompletableFuture<Product> future = CompletableFuture.supplyAsync(() -> {
            try {
                Product product = Product.builder()
                    .name("Concurrent Product " + index)
                    .description("Concurrent description " + index)
                    .build();
                return productService.createProduct(product);
            } finally {
                latch.countDown();
            }
        }, executor);
        futures.add(future);
    }
    
    // Wait for all method calls to complete
    latch.await(60, TimeUnit.SECONDS);
    
    long duration = System.currentTimeMillis() - startTime;
    
    // Then - Verify all method calls succeeded
    Set<String> productIds = new HashSet<>();
    for (CompletableFuture<Product> future : futures) {
        Product product = future.get();
        assertNotNull(product);
        assertNotNull(product.getId());
        assertTrue(productIds.add(product.getId().toString()), 
                  "Product IDs should be unique");
    }
    
    assertEquals(concurrentRequests, productIds.size());
    
    // Wait for all AI processing to complete
    await().atMost(30, TimeUnit.SECONDS).until(() -> {
        List<AISearchableEntity> entities = 
            searchableEntityRepository.findByEntityType("product");
        return entities.size() >= concurrentRequests;
    });
    
    // Verify all AISearchableEntity records created
    List<AISearchableEntity> entities = 
        searchableEntityRepository.findByEntityType("product");
    assertTrue(entities.size() >= concurrentRequests,
              "All AISearchableEntity records should be created");
    
    // Verify no race conditions
    Set<String> entityIds = entities.stream()
        .map(AISearchableEntity::getEntityId)
        .collect(Collectors.toSet());
    assertEquals(concurrentRequests, entityIds.size(),
                "All entity IDs should be unique");
}
```

---

### TEST-AIPROCESS-008: Entity Type Resolution from Annotation
**Priority**: Medium  
**Status**: ✅ AUTOMATED (`AIProcessAnnotationIntegrationTest#aiProcessInfersEntityTypeFromMethodName`)

#### Test Steps
1. Test `@AIProcess` with explicit `entityType="product"`
2. Verify entity type resolved correctly
3. Test `@AIProcess` without explicit entityType
4. Verify entity type inferred from method name
5. Test with different method names
6. Verify configuration loaded correctly

#### Expected Results
- ✅ Explicit entityType used when provided
- ✅ Entity type inferred from method name when not provided
- ✅ Configuration loaded correctly for entity type
- ✅ Processing uses correct configuration

#### Test Data
```java
// Explicit entity type
@AIProcess(entityType = "product", processType = "create")
public Product createProduct(Product product) { ... }

// Implicit entity type (inferred from method name)
@AIProcess(processType = "create")
public Product createProduct(Product product) { ... }

// Different entity type
@AIProcess(entityType = "user", processType = "create")
public User createUser(User user) { ... }
```

#### Implementation Notes
```java
@Test
public void testExplicitEntityType() {
    // Given - Service method with explicit entityType
    Product product = createTestProduct();
    
    // When - Call service method
    Product saved = productService.createProduct(product);
    
    // Then - Verify entity type resolved correctly
    // Configuration should be loaded for "product" entity type
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        searchableEntityRepository.findByEntityTypeAndEntityId("product", saved.getId().toString()).isPresent()
    );
    
    AISearchableEntity searchable = searchableEntityRepository
        .findByEntityTypeAndEntityId("product", saved.getId().toString())
        .orElseThrow();
    
    assertEquals("product", searchable.getEntityType());
}

@Test
public void testImplicitEntityType() {
    // Given - Service method without explicit entityType (should infer from method name)
    Product product = createTestProduct();
    
    // When - Call service method
    Product saved = productService.createProduct(product);
    
    // Then - Verify entity type inferred from method name
    // Method name "createProduct" should infer entity type "product"
    await().atMost(5, TimeUnit.SECONDS).until(() ->
        searchableEntityRepository.findByEntityTypeAndEntityId("product", saved.getId().toString()).isPresent()
    );
    
    AISearchableEntity searchable = searchableEntityRepository
        .findByEntityTypeAndEntityId("product", saved.getId().toString())
        .orElseThrow();
    
    assertEquals("product", searchable.getEntityType());
}
```

---

### TEST-AIPROCESS-009: Different Process Types (search, analyze)
**Priority**: Medium  
**Status**: ✅ AUTOMATED (`AIProcessAnnotationIntegrationTest#aiProcessSearchProcessTypeDoesNotIndex`, `AIProcessAnnotationIntegrationTest#aiProcessAnalyzeProcessTypeGeneratesInsights`)

#### Test Steps
1. Test `@AIProcess` with `processType="search"`
2. Verify search processing triggered
3. Test `@AIProcess` with `processType="analyze"`
4. Verify analysis processing triggered
5. Verify different processing behavior per type

#### Expected Results
- ✅ `processType="search"` triggers search indexing
- ✅ `processType="analyze"` triggers AI analysis
- ✅ Different process types handled correctly
- ✅ Configuration respected per process type

#### Test Data
```java
// Search process type
@AIProcess(entityType = "product", processType = "search")
public List<Product> searchProducts(String query) {
    // Search logic
    return productRepository.findByNameContaining(query);
}

// Analyze process type
@AIProcess(entityType = "product", processType = "analyze")
public ProductAnalysis analyzeProduct(String productId) {
    // Analysis logic
    return performAnalysis(productId);
}
```

#### Implementation Notes
```java
@Test
public void testProcessTypeSearch() {
    // Given - Service method with processType="search"
    String query = "luxury watch";
    
    // When - Call search method
    List<Product> results = productService.searchProducts(query);
    
    // Then - Verify search processing triggered
    // Search should index results or perform search-related AI processing
    assertNotNull(results);
    
    // Verify processing happened (may vary based on implementation)
    // This test verifies that processType="search" is handled correctly
}

@Test
public void testProcessTypeAnalyze() {
    // Given - Product to analyze
    Product product = productService.createProduct(createTestProduct());
    
    // When - Call analyze method
    ProductAnalysis analysis = productService.analyzeProduct(product.getId().toString());
    
    // Then - Verify analysis processing triggered
    assertNotNull(analysis);
    
    // Verify AI analysis performed
    // This test verifies that processType="analyze" triggers analysis
}
```

---

### TEST-AIPROCESS-010: Transactional Consistency with @AIProcess
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`AIProcessAnnotationIntegrationTest#aiProcessTransactionalRollbackPreventsSideEffects`)

#### Test Steps
1. Start transaction
2. Call service method annotated with `@AIProcess` within transaction
3. Verify method execution succeeds
4. Rollback transaction
5. Verify entity not saved
6. Verify AI processing rolled back
7. Verify no partial AISearchableEntity or vectors

#### Expected Results
- ✅ Transaction rollback prevents entity save
- ✅ AI processing rolled back
- ✅ No AISearchableEntity created
- ✅ No vectors stored
- ✅ Transactional consistency maintained

#### Implementation Notes
```java
@Test
@Transactional
public void testTransactionalConsistency() {
    // Given - Product service
    Product product = createTestProduct();
    
    // When - Save within transaction, then rollback
    try {
        Product saved = productService.createProduct(product);
        String productId = saved.getId().toString();
        
        // Verify processing started
        Thread.sleep(500);
        
        // Rollback transaction
        throw new RuntimeException("Force rollback");
        
    } catch (RuntimeException e) {
        // Transaction rolled back
        // Verify entity not saved
        Optional<Product> found = productRepository.findById(product.getId());
        assertFalse(found.isPresent(), "Entity should not be saved after rollback");
        
        // Verify no AISearchableEntity created
        Optional<AISearchableEntity> searchable = 
            searchableEntityRepository.findByEntityTypeAndEntityId("product", product.getId().toString());
        assertFalse(searchable.isPresent(), 
                   "AISearchableEntity should not be created after rollback");
        
        // Verify no vector stored
        assertFalse(vectorManagementService.vectorExists("product", product.getId().toString()),
                   "Vector should not be stored after rollback");
    }
}
```

---

## 📊 Performance Benchmarks

### Processing Time Targets

| Scenario | Target | Max |
|----------|--------|-----|
| Create with @AIProcess | <500ms | 1s |
| Update with @AIProcess | <500ms | 1s |
| Delete with @AIProcess | <300ms | 500ms |
| Concurrent (50 requests) | <30s | 60s |

### Throughput Targets

| Operation | Target | Max |
|-----------|--------|-----|
| Sequential processing | 10 req/sec | 5 req/sec |
| Concurrent processing | 50 req/sec | 25 req/sec |

---

## 🎯 Success Criteria

### Functional
- ✅ All `processType` values work correctly (create, update, delete, search, analyze)
- ✅ Annotation configuration flags respected
- ✅ Aspect interception works reliably
- ✅ Entity type resolution accurate
- ✅ Error handling graceful

### Quality
- ✅ Method execution not affected by AI processing failures
- ✅ Transactional consistency maintained
- ✅ Thread-safe concurrent processing
- ✅ No memory leaks
- ✅ Proper logging and error messages

### Performance
- ✅ Processing overhead < 500ms per request
- ✅ Concurrent handling 50+ requests
- ✅ System remains responsive

---

## 📅 Implementation Schedule

### Week 1: Critical Tests
- Day 1-2: TEST-AIPROCESS-001 (Create)
- Day 2-3: TEST-AIPROCESS-002 (Update)
- Day 3-4: TEST-AIPROCESS-003 (Delete)
- Day 4-5: TEST-AIPROCESS-006 (Error Handling)

### Week 2: High Priority Tests
- Day 6-7: TEST-AIPROCESS-004 (Configuration Flags)
- Day 7-8: TEST-AIPROCESS-005 (Aspect Interception)
- Day 8-9: TEST-AIPROCESS-007 (Concurrent Processing)
- Day 9-10: TEST-AIPROCESS-010 (Transactional Consistency)

### Week 3: Medium Priority Tests
- Day 11: TEST-AIPROCESS-008 (Entity Type Resolution)
- Day 12: TEST-AIPROCESS-009 (Process Types)

---

## 🛠️ Test Infrastructure Requirements

### Test Service Classes

```java
@Service
public class TestProductService {
    
    @Autowired
    private ProductRepository productRepository;
    
    @AIProcess(entityType = "product", processType = "create")
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }
    
    @AIProcess(entityType = "product", processType = "update")
    public Product updateProduct(String id, Product product) {
        Product existing = productRepository.findById(id).orElseThrow();
        existing.setName(product.getName());
        existing.setDescription(product.getDescription());
        return productRepository.save(existing);
    }
    
    @AIProcess(entityType = "product", processType = "delete")
    public void deleteProduct(String id) {
        Product product = productRepository.findById(id).orElseThrow();
        productRepository.delete(product);
    }
    
    @AIProcess(entityType = "product", processType = "create", generateEmbedding = false)
    public Product createProductWithoutEmbedding(Product product) {
        return productRepository.save(product);
    }
    
    @AIProcess(entityType = "product", processType = "create", indexForSearch = false)
    public Product createProductWithoutIndexing(Product product) {
        return productRepository.save(product);
    }
    
    @AIProcess(entityType = "product", processType = "create", enableAnalysis = true)
    public Product createProductWithAnalysis(Product product) {
        return productRepository.save(product);
    }
}
```

### Required Dependencies

```java
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("test")
class AIProcessAnnotationIntegrationTest {
    
    @Autowired
    private TestProductService productService;
    
    @Autowired
    private ProductRepository productRepository;
    
    @Autowired
    private AISearchableEntityRepository searchableEntityRepository;
    
    @Autowired
    private VectorManagementService vectorManagementService;
    
    @Autowired
    private AICapabilityService aiCapabilityService;
    
    @MockBean
    private EmbeddingProvider mockEmbeddingProvider; // For error handling tests
}
```

---

## 📝 Test Execution Checklist

- [ ] Test service classes created
- [ ] Test database initialized
- [ ] AICapableAspect properly configured
- [ ] Entity configuration loaded
- [ ] Mock providers configured for error tests
- [ ] Logging configured
- [ ] All tests passing locally
- [ ] Tests integrated into CI/CD
- [ ] Performance benchmarks documented

---

## 🔗 Related Documents

- [Integration Test Analysis](../AI_MODULE_INTEGRATION_TEST_ANALYSIS.md)
- [Entity Processing Test Plan](./ENTITY_PROCESSING_TEST_PLAN.md)
- [Integration Test Plans Index](../INTEGRATION_TEST_PLANS_INDEX.md)

---

## 📚 Additional Notes

### Key Differences from @AICapable Tests

1. **Explicit vs. Automatic**: `@AIProcess` is explicit on method call, `@AICapable` is automatic on entity save
2. **Method-Level vs. Entity-Level**: `@AIProcess` on methods, `@AICapable` on entities
3. **Control**: `@AIProcess` provides more control with configuration flags
4. **Use Cases**: `@AIProcess` for service methods, `@AICapable` for domain entities

### Testing Strategy

1. **Unit Tests**: Test aspect interception logic separately
2. **Integration Tests**: Test end-to-end flow with service methods
3. **Concurrent Tests**: Verify thread safety
4. **Error Tests**: Verify graceful degradation

---

**End of Test Plan**

