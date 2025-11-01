# Integration Test Plan - Vector Database Operations

**Component**: Vector Database Services (Lucene, Pinecone, Vector Management)  
**Priority**: 🔴 CRITICAL  
**Estimated Effort**: 2 weeks  
**Status**: Draft  

---

## 📋 Overview

This test plan covers comprehensive integration testing of vector database operations including Lucene HNSW indexing, vector storage/retrieval, concurrent operations, index persistence, and performance under load.

### Components Under Test
- `LuceneVectorDatabaseService` (600+ lines)
- `PineconeVectorDatabaseService`
- `VectorManagementService` (400+ lines)
- `InMemoryVectorDatabaseService`
- `VectorDatabaseService` interface

### Test Objectives
1. Verify vector storage and retrieval operations
2. Validate k-NN search accuracy and performance
3. Test concurrent vector operations
4. Verify index persistence and recovery
5. Test scalability with large vector datasets

---

## 🧪 Test Scenarios

### TEST-VECTOR-001: Basic Vector Storage (Lucene)
**Priority**: Critical  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Initialize LuceneVectorDatabaseService
2. Generate 10 sample vectors (1536 dimensions)
3. Store vectors with metadata
4. Verify vectors stored successfully
5. Retrieve vectors by entity ID
6. Verify retrieved vectors match original

#### Expected Results
- ✅ All 10 vectors stored successfully
- ✅ Vector IDs assigned correctly
- ✅ Metadata stored with vectors
- ✅ Retrieved vectors match originals (exact float comparison)
- ✅ No index corruption

#### Test Data
```java
List<VectorRecord> vectors = new ArrayList<>();
for (int i = 0; i < 10; i++) {
    List<Double> embedding = generateRandomEmbedding(1536);
    Map<String, Object> metadata = Map.of(
        "entityType", "product",
        "entityId", "product_" + i,
        "name", "Product " + i
    );
    vectors.add(new VectorRecord(embedding, metadata));
}
```

#### Implementation Notes
```java
@Test
public void testBasicVectorStorage() {
    // Given
    LuceneVectorDatabaseService luceneService = 
        new LuceneVectorDatabaseService(config);
    
    List<Double> embedding = generateTestEmbedding(1536);
    Map<String, Object> metadata = Map.of(
        "entityType", "product",
        "entityId", "test-product-1",
        "name", "Test Product"
    );
    
    // When
    String vectorId = luceneService.storeVector(
        "product", "test-product-1", "Test Product Description",
        embedding, metadata
    );
    
    // Then
    assertNotNull(vectorId);
    assertTrue(luceneService.vectorExists("product", "test-product-1"));
    
    // Retrieve and verify
    Optional<VectorRecord> retrieved = 
        luceneService.getVector("product", "test-product-1");
    assertTrue(retrieved.isPresent());
    assertEquals(embedding, retrieved.get().getEmbedding());
}
```

---

### TEST-VECTOR-002: k-NN Search with HNSW
**Priority**: Critical  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Index 1000 vectors with Lucene HNSW
2. Perform k-NN search for k=10
3. Verify top 10 results returned
4. Verify results sorted by similarity (descending)
5. Validate similarity scores
6. Test different k values (5, 10, 50, 100)

#### Expected Results
- ✅ Search returns exactly k results
- ✅ Results sorted by similarity score
- ✅ Similarity scores in range [0.0, 1.0]
- ✅ Search completes in <100ms
- ✅ Results are semantically relevant

#### Test Data
```java
// Index 1000 product vectors
for (int i = 0; i < 1000; i++) {
    String description = faker.commerce().productName() + " " +
                        faker.lorem().sentence(20);
    List<Double> embedding = generateEmbedding(description);
    luceneService.storeVector("product", "prod_" + i, 
                              description, embedding, metadata);
}

// Query
String query = "luxury Swiss watch with diamonds";
List<Double> queryEmbedding = generateEmbedding(query);
```

#### Performance Benchmarks
- Search latency with 1K vectors: <50ms
- Search latency with 10K vectors: <100ms
- Search latency with 100K vectors: <500ms

#### Implementation Notes
```java
@Test
public void testKNNSearchWithHNSW() {
    // Given - Index 1000 vectors
    indexTestVectors(1000);
    
    String query = "luxury Swiss watch with diamonds";
    List<Double> queryEmbedding = embeddingService.generateEmbedding(
        AIEmbeddingRequest.builder().text(query).build()
    ).getEmbedding();
    
    AISearchRequest searchRequest = AISearchRequest.builder()
        .query(query)
        .entityType("product")
        .limit(10)
        .threshold(0.7)
        .build();
    
    // When
    long startTime = System.currentTimeMillis();
    AISearchResponse response = luceneService.search(
        queryEmbedding, searchRequest
    );
    long duration = System.currentTimeMillis() - startTime;
    
    // Then
    assertEquals(10, response.getResults().size());
    assertTrue(duration < 100, "Search should complete in <100ms");
    
    // Verify results sorted by similarity
    List<Double> similarities = response.getResults().stream()
        .map(r -> (Double) r.get("similarity"))
        .collect(Collectors.toList());
    
    for (int i = 0; i < similarities.size() - 1; i++) {
        assertTrue(similarities.get(i) >= similarities.get(i + 1),
                  "Results should be sorted by similarity");
    }
}
```

---

### TEST-VECTOR-003: Concurrent Vector Storage
**Priority**: Critical  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Create 50 threads
2. Each thread stores 10 vectors concurrently
3. Wait for all threads to complete
4. Verify all 500 vectors stored correctly
5. Check for race conditions or data corruption
6. Verify index consistency

#### Expected Results
- ✅ All 500 vectors stored successfully
- ✅ No ConcurrentModificationException
- ✅ No index corruption
- ✅ All vectors retrievable
- ✅ No duplicate vector IDs

#### Test Data
```java
ExecutorService executor = Executors.newFixedThreadPool(50);
int totalVectors = 500;
CountDownLatch latch = new CountDownLatch(totalVectors);

for (int i = 0; i < totalVectors; i++) {
    final int index = i;
    executor.submit(() -> {
        try {
            storeTestVector("product_" + index);
        } finally {
            latch.countDown();
        }
    });
}
```

#### Success Criteria
- Zero race conditions detected
- All vectors present after concurrent writes
- Index remains consistent
- No performance degradation

#### Implementation Notes
```java
@Test
public void testConcurrentVectorStorage() throws Exception {
    // Given
    int threadCount = 50;
    int vectorsPerThread = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount * vectorsPerThread);
    List<Future<String>> futures = new ArrayList<>();
    
    // When - Store vectors concurrently
    for (int t = 0; t < threadCount; t++) {
        for (int v = 0; v < vectorsPerThread; v++) {
            final int threadId = t;
            final int vectorId = v;
            
            Future<String> future = executor.submit(() -> {
                try {
                    String id = "thread_" + threadId + "_vector_" + vectorId;
                    List<Double> embedding = generateTestEmbedding(1536);
                    
                    return luceneService.storeVector(
                        "product", id, "Test content " + id,
                        embedding, Map.of("threadId", threadId)
                    );
                } finally {
                    latch.countDown();
                }
            });
            futures.add(future);
        }
    }
    
    // Wait for completion
    boolean completed = latch.await(60, TimeUnit.SECONDS);
    assertTrue(completed, "All operations should complete within 60s");
    
    // Then - Verify all vectors stored
    Set<String> vectorIds = new HashSet<>();
    for (Future<String> future : futures) {
        String vectorId = future.get();
        assertNotNull(vectorId);
        assertTrue(vectorIds.add(vectorId), "Vector IDs should be unique");
    }
    
    assertEquals(threadCount * vectorsPerThread, vectorIds.size());
}
```

---

### TEST-VECTOR-004: Index Persistence and Recovery
**Priority**: Critical  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Store 100 vectors in Lucene index
2. Verify vectors searchable
3. Close/restart LuceneVectorDatabaseService
4. Verify index loads successfully
5. Search for previously indexed vectors
6. Verify all vectors still present and searchable

#### Expected Results
- ✅ Index persists to disk
- ✅ Service restarts successfully
- ✅ All vectors recovered
- ✅ Search results identical after restart
- ✅ No data loss

#### Test Data
```java
// Store vectors
for (int i = 0; i < 100; i++) {
    storeTestVector("product_" + i);
}

// Restart service
luceneService.close();
luceneService = new LuceneVectorDatabaseService(config);

// Verify recovery
AISearchResponse results = luceneService.search(...);
```

#### Success Criteria
- 100% vector recovery
- Index integrity maintained
- Fast restart time (<5 seconds)

---

### TEST-VECTOR-005: Vector Update and Deletion
**Priority**: High  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Store vector for entity "product_1"
2. Retrieve and verify vector
3. Update vector with new embedding
4. Verify updated vector returned on search
5. Delete vector
6. Verify vector no longer found

#### Expected Results
- ✅ Vector updates successfully
- ✅ Old vector data removed
- ✅ Search returns updated vector
- ✅ Deletion removes vector completely
- ✅ No orphaned data

#### Implementation Notes
```java
@Test
public void testVectorUpdateAndDeletion() {
    // Given - Store initial vector
    List<Double> embedding1 = generateTestEmbedding(1536);
    String vectorId = luceneService.storeVector(
        "product", "test-product", "Original description",
        embedding1, Map.of("version", 1)
    );
    
    // When - Update vector
    List<Double> embedding2 = generateTestEmbedding(1536);
    String updatedId = luceneService.storeVector(
        "product", "test-product", "Updated description",
        embedding2, Map.of("version", 2)
    );
    
    // Then - Verify update
    Optional<VectorRecord> retrieved = 
        luceneService.getVector("product", "test-product");
    assertTrue(retrieved.isPresent());
    assertEquals(embedding2, retrieved.get().getEmbedding());
    assertEquals(2, retrieved.get().getMetadata().get("version"));
    
    // When - Delete vector
    boolean deleted = luceneService.removeVector("product", "test-product");
    
    // Then - Verify deletion
    assertTrue(deleted);
    assertFalse(luceneService.vectorExists("product", "test-product"));
}
```

---

### TEST-VECTOR-006: Memory Usage with Large Dataset
**Priority**: Critical  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Start with baseline memory measurement
2. Index 10,000 vectors
3. Monitor memory usage during indexing
4. Perform 100 searches
5. Verify memory doesn't continuously grow
6. Verify garbage collection works properly

#### Expected Results
- ✅ Memory usage stable during indexing
- ✅ Peak memory < 2GB for 10K vectors
- ✅ No memory leaks detected
- ✅ GC able to reclaim memory
- ✅ System remains responsive

#### Test Data
```java
Runtime runtime = Runtime.getRuntime();
long initialMemory = runtime.totalMemory() - runtime.freeMemory();

// Index 10K vectors
for (int i = 0; i < 10000; i++) {
    storeTestVector("product_" + i);
    
    if (i % 1000 == 0) {
        long currentMemory = runtime.totalMemory() - runtime.freeMemory();
        log.info("Memory after {} vectors: {} MB", 
                i, currentMemory / 1024 / 1024);
    }
}
```

#### Performance Benchmarks
- 1K vectors: <100MB
- 10K vectors: <500MB
- 100K vectors: <2GB

---

### TEST-VECTOR-007: Search Performance Degradation
**Priority**: High  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Perform baseline search with 100 vectors
2. Measure search latency
3. Add 1K vectors, measure again
4. Add 10K vectors, measure again
5. Add 100K vectors, measure again
6. Plot performance degradation curve

#### Expected Results
- ✅ Search latency scales sub-linearly
- ✅ 100 vectors: <10ms
- ✅ 1K vectors: <50ms
- ✅ 10K vectors: <100ms
- ✅ 100K vectors: <500ms

#### Test Data
```java
Map<Integer, Long> performanceResults = new HashMap<>();

for (int size : Arrays.asList(100, 1000, 10000, 100000)) {
    indexTestVectors(size);
    long avgLatency = measureSearchLatency(100); // 100 searches
    performanceResults.put(size, avgLatency);
}
```

---

### TEST-VECTOR-008: Pinecone Integration
**Priority**: Medium  
**Status**: ❌ NOT IMPLEMENTED  
**Pre-requisites**: Pinecone account and API key

#### Test Steps
1. Configure Pinecone credentials
2. Create test index
3. Store 100 vectors in Pinecone
4. Perform k-NN search
5. Verify results match expected behavior
6. Compare with Lucene results

#### Expected Results
- ✅ Pinecone API calls successful
- ✅ Vectors stored in cloud
- ✅ Search returns relevant results
- ✅ Results comparable to Lucene
- ✅ Proper error handling

---

### TEST-VECTOR-009: Index Corruption Recovery
**Priority**: Medium  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Create index with 100 vectors
2. Simulate index corruption (delete/modify index files)
3. Attempt to load service
4. Verify error detection
5. Test automatic recovery mechanism
6. Verify fallback to backup or rebuild

#### Expected Results
- ✅ Corruption detected
- ✅ Error logged appropriately
- ✅ Recovery mechanism triggered
- ✅ Service remains functional
- ✅ Data loss minimized

---

### TEST-VECTOR-010: Similarity Threshold Tuning
**Priority**: Medium  
**Status**: ❌ NOT IMPLEMENTED

#### Test Steps
1. Index 1000 product vectors
2. Perform search with threshold 0.5
3. Count results
4. Repeat with thresholds: 0.6, 0.7, 0.8, 0.9
5. Analyze precision/recall for each threshold
6. Determine optimal threshold

#### Expected Results
- ✅ Higher threshold = fewer but more relevant results
- ✅ Lower threshold = more results but lower relevance
- ✅ Optimal threshold identified for use case
- ✅ Precision/recall metrics calculated

#### Test Data
```java
Map<Double, SearchMetrics> thresholdResults = new HashMap<>();

for (double threshold : Arrays.asList(0.5, 0.6, 0.7, 0.8, 0.9)) {
    AISearchRequest request = AISearchRequest.builder()
        .query("luxury watch")
        .threshold(threshold)
        .limit(100)
        .build();
    
    AISearchResponse response = luceneService.search(queryVector, request);
    SearchMetrics metrics = calculateMetrics(response);
    thresholdResults.put(threshold, metrics);
}
```

---

## 📊 Performance Benchmarks

### Search Latency Targets

| Vector Count | Target Latency | Max Latency |
|-------------|----------------|-------------|
| 100 | <10ms | 20ms |
| 1,000 | <50ms | 100ms |
| 10,000 | <100ms | 200ms |
| 100,000 | <500ms | 1000ms |

### Throughput Targets

| Operation | Target | Max |
|-----------|--------|-----|
| Indexing | 100 vectors/sec | 50 vectors/sec |
| Searching | 1000 queries/sec | 500 queries/sec |
| Updates | 50 updates/sec | 25 updates/sec |
| Deletions | 100 deletions/sec | 50 deletions/sec |

### Memory Usage Targets

| Vector Count | Target Memory | Max Memory |
|-------------|---------------|------------|
| 1,000 | <100MB | 200MB |
| 10,000 | <500MB | 1GB |
| 100,000 | <2GB | 4GB |

---

## 🎯 Success Criteria

### Functional
- ✅ All CRUD operations work correctly
- ✅ k-NN search returns accurate results
- ✅ Concurrent operations thread-safe
- ✅ Index persistence works reliably
- ✅ Recovery from corruption succeeds

### Performance
- ✅ Search latency meets targets
- ✅ Indexing throughput acceptable
- ✅ Memory usage within limits
- ✅ Scales to 100K+ vectors
- ✅ No performance degradation over time

### Quality
- ✅ Zero data loss
- ✅ Zero race conditions
- ✅ Proper error handling
- ✅ Complete logging
- ✅ Metrics collection accurate

---

## 📅 Implementation Schedule

### Week 1
- TEST-VECTOR-001: Basic Storage
- TEST-VECTOR-002: k-NN Search
- TEST-VECTOR-003: Concurrent Operations
- TEST-VECTOR-004: Persistence

### Week 2
- TEST-VECTOR-005: Update/Delete
- TEST-VECTOR-006: Memory Usage
- TEST-VECTOR-007: Performance Degradation
- TEST-VECTOR-010: Threshold Tuning

---

**End of Test Plan**
