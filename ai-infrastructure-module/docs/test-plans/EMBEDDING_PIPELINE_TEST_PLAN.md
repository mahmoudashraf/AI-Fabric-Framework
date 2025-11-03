# Integration Test Plan - Embedding Generation Pipeline

**Component**: AIEmbeddingService, EmbeddingProvider Implementations  
**Priority**: 🔴 CRITICAL  
**Estimated Effort**: 2 weeks  
**Status**: Draft  

---

## 📋 Overview

This test plan covers comprehensive integration testing of the embedding generation pipeline, including all provider implementations (OpenAI, ONNX, REST), caching mechanisms, batch processing, and error handling.

### Components Under Test
- `AIEmbeddingService` (com.ai.infrastructure.core)
- `EmbeddingProvider` interface
- `OpenAIEmbeddingProvider`
- `ONNXEmbeddingProvider`
- `RestEmbeddingProvider`
- Embedding cache layer
- Batch processing logic

### Test Objectives
1. Verify all embedding providers work correctly
2. Validate provider switching and fallback mechanisms
3. Test batch and concurrent processing
4. Verify caching effectiveness
5. Validate error handling and recovery

---

## 🧪 Test Scenarios

### TEST-EMBED-001: OpenAI Embedding Generation (Basic)
**Priority**: Critical  
**Status**: ✅ PASSING  
**Pre-requisites**: OpenAI API key configured

#### Test Steps
1. Create text content: "This is a luxury Swiss watch with diamond bezel"
2. Call `embeddingService.generateEmbedding(request)`
3. Verify response contains 1536-dimensional vector
4. Verify processing time < 3 seconds
5. Verify embedding values are normalized floats

#### Expected Results
- ✅ Embedding generated successfully
- ✅ Dimensions = 1536 (OpenAI text-embedding-3-small)
- ✅ All values in range [-1.0, 1.0]
- ✅ Response time < 3000ms
- ✅ No errors or exceptions

#### Test Data
```java
String text = "This is a luxury Swiss watch with diamond bezel and Swiss movement";
AIEmbeddingRequest request = AIEmbeddingRequest.builder()
    .text(text)
    .model("text-embedding-3-small")
    .build();
```

#### Success Criteria
- Embedding generation successful
- Correct dimensions
- Reasonable response time
- Valid float values

---

### TEST-EMBED-002: ONNX Embedding Generation (Local)
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`ONNXEmbeddingIntegrationTest#testOnnxEmbeddingGenerationMatchesOpenAISemantics`)  
**Pre-requisites**: ONNX model downloaded and configured

#### Test Steps
1. Configure system to use ONNX provider
2. Create text content: "AI-powered smart home automation system"
3. Call `embeddingService.generateEmbedding(request)`
4. Verify ONNX provider is used (no API call)
5. Verify response contains embedding vector
6. Compare similarity with OpenAI embedding

#### Expected Results
- ✅ ONNX provider selected automatically
- ✅ Embedding generated without API call
- ✅ Processing time < 1 second (local)
- ✅ Similarity with OpenAI embedding > 0.7
- ✅ No network errors

#### Test Data
```java
// Configuration
ai.embedding.provider=onnx
ai.embedding.onnx.model-path=/models/embeddings/all-MiniLM-L6-v2.onnx

// Test code
AIEmbeddingRequest request = AIEmbeddingRequest.builder()
    .text("AI-powered smart home automation system")
    .build();
```

#### Success Criteria
- ONNX provider works offline
- Embeddings are comparable to OpenAI
- Fast local processing
- No external dependencies

#### Implementation Notes
```java
@Test
@ActiveProfiles("onnx")
public void testONNXEmbeddingGeneration() {
    // Given
    String text = "AI-powered smart home automation system";
    AIEmbeddingRequest request = AIEmbeddingRequest.builder()
        .text(text)
        .build();
    
    // When
    long startTime = System.currentTimeMillis();
    AIEmbeddingResponse response = embeddingService.generateEmbedding(request);
    long duration = System.currentTimeMillis() - startTime;
    
    // Then
    assertNotNull(response);
    assertNotNull(response.getEmbedding());
    assertTrue(response.getEmbedding().size() > 0);
    assertTrue(duration < 1000, "ONNX should be faster than 1 second");
    assertEquals("onnx", response.getProvider());
}
```

---

### TEST-EMBED-003: REST Embedding Provider
**Priority**: Medium  
**Status**: ❌ NOT IMPLEMENTED  
**Pre-requisites**: REST embedding service running (Docker)

#### Test Steps
1. Start REST embedding service in Docker
2. Configure REST provider endpoint
3. Send embedding request
4. Verify REST API is called
5. Verify embedding is returned correctly

#### Expected Results
- ✅ REST service responds successfully
- ✅ Embedding vector returned
- ✅ Response time < 2 seconds
- ✅ Proper error handling for service down

#### Test Data
```java
// docker-compose.yml
services:
  embedding-service:
    image: sentence-transformers/all-MiniLM-L6-v2
    ports:
      - "8080:8080"

// Configuration
ai.embedding.provider=rest
ai.embedding.rest.url=http://localhost:8080/embed
```

---

### TEST-EMBED-004: Batch Embedding Processing
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`ONNXBatchEmbeddingIntegrationTest#testBatchEmbeddingProcessingWithONNX`)  
**Pre-requisites**: OpenAI API key or ONNX configured

#### Test Steps
1. Prepare 100 different text samples
2. Call `embeddingService.generateEmbeddings(texts, "product")`
3. Verify all 100 embeddings generated
4. Measure total processing time
5. Verify no embeddings are duplicated
6. Check for any failures

#### Expected Results
- ✅ All 100 embeddings generated successfully
- ✅ Processing time < 120 seconds (with OpenAI)
- ✅ Processing time < 20 seconds (with ONNX)
- ✅ Each embedding is unique
- ✅ No memory issues

#### Test Data
```java
List<String> texts = new ArrayList<>();
for (int i = 0; i < 100; i++) {
    texts.add("Product " + i + ": " + faker.commerce().productName() + 
              " - " + faker.lorem().sentence(20));
}
```

#### Performance Benchmarks
- **OpenAI**: ~1.2s per embedding = 120s for 100
- **ONNX**: ~0.2s per embedding = 20s for 100
- **Memory**: < 500MB for batch

#### Implementation Notes
```java
@Test
public void testBatchEmbeddingProcessing() {
    // Given
    List<String> texts = generateTestTexts(100);
    
    // When
    long startTime = System.currentTimeMillis();
    List<AIEmbeddingResponse> responses = 
        embeddingService.generateEmbeddings(texts, "product");
    long duration = System.currentTimeMillis() - startTime;
    
    // Then
    assertEquals(100, responses.size());
    assertTrue(duration < 120000, "Should complete within 2 minutes");
    
    // Verify uniqueness
    Set<List<Double>> uniqueEmbeddings = responses.stream()
        .map(AIEmbeddingResponse::getEmbedding)
        .collect(Collectors.toSet());
    assertEquals(100, uniqueEmbeddings.size());
}
```

---

### TEST-EMBED-005: Concurrent Embedding Generation
**Priority**: Critical  
**Status**: ✅ AUTOMATED (`ONNXConcurrentEmbeddingIntegrationTest#testConcurrentEmbeddingGeneration`)  
**Pre-requisites**: Thread-safe configuration

#### Test Steps
1. Create 50 embedding requests
2. Submit all requests concurrently using ExecutorService
3. Wait for all completions
4. Verify all 50 embeddings generated correctly
5. Check for race conditions
6. Verify cache consistency

#### Expected Results
- ✅ All 50 embeddings generated
- ✅ No ConcurrentModificationException
- ✅ No cache inconsistencies
- ✅ Processing time < 60 seconds (parallelized)
- ✅ Memory usage stable

#### Test Data
```java
ExecutorService executor = Executors.newFixedThreadPool(10);
List<CompletableFuture<AIEmbeddingResponse>> futures = new ArrayList<>();

for (int i = 0; i < 50; i++) {
    String text = "Product " + i;
    CompletableFuture<AIEmbeddingResponse> future = 
        CompletableFuture.supplyAsync(() -> {
            return embeddingService.generateEmbedding(
                AIEmbeddingRequest.builder().text(text).build()
            );
        }, executor);
    futures.add(future);
}
```

#### Success Criteria
- Zero race conditions
- All requests complete successfully
- Cache remains consistent
- No thread deadlocks

#### Implementation Notes
```java
@Test
public void testConcurrentEmbeddingGeneration() throws Exception {
    // Given
    int concurrentRequests = 50;
    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(concurrentRequests);
    List<Future<AIEmbeddingResponse>> futures = new ArrayList<>();
    
    // When
    long startTime = System.currentTimeMillis();
    for (int i = 0; i < concurrentRequests; i++) {
        final int index = i;
        Future<AIEmbeddingResponse> future = executor.submit(() -> {
            try {
                return embeddingService.generateEmbedding(
                    AIEmbeddingRequest.builder()
                        .text("Product " + index)
                        .build()
                );
            } finally {
                latch.countDown();
            }
        });
        futures.add(future);
    }
    
    latch.await(60, TimeUnit.SECONDS);
    long duration = System.currentTimeMillis() - startTime;
    
    // Then
    assertEquals(concurrentRequests, futures.size());
    for (Future<AIEmbeddingResponse> future : futures) {
        assertNotNull(future.get());
    }
    assertTrue(duration < 60000);
}
```

---

### TEST-EMBED-006: Large Text Chunking
**Priority**: High  
**Status**: ✅ AUTOMATED (`EmbeddingLargeTextChunkingIntegrationTest#chunkTextAndEmbedEachSegment`)  
**Pre-requisites**: Chunking logic implemented

#### Test Steps
1. Create text content > 8000 characters
2. Call `embeddingService.chunkText(text, 1000)`
3. Verify text is chunked correctly
4. Generate embeddings for each chunk
5. Verify all chunks processed
6. Test chunk boundary handling

#### Expected Results
- ✅ Text chunked at appropriate boundaries
- ✅ No data loss in chunking
- ✅ Chunk sizes within limits
- ✅ Embeddings generated for all chunks
- ✅ Proper handling of sentence boundaries

#### Test Data
```java
String largeText = generateLargeText(10000); // 10K chars
List<String> chunks = embeddingService.chunkText(largeText, 1000);
```

#### Success Criteria
- Chunks respect sentence boundaries
- No loss of information
- All chunks processable
- Efficient memory usage

---

### TEST-EMBED-007: Multi-language Content
**Priority**: Medium  
**Status**: ✅ AUTOMATED (`EmbeddingMultilanguageIntegrationTest#embeddingsAcrossLanguagesRemainSemanticallyAligned`)  
**Pre-requisites**: Multi-language support enabled

#### Test Steps
1. Prepare texts in multiple languages (English, Spanish, Japanese, Arabic)
2. Generate embeddings for each
3. Verify embeddings are generated correctly
4. Test similarity between same content in different languages
5. Verify proper encoding handling

#### Expected Results
- ✅ All languages processed correctly
- ✅ Similar content shows high similarity across languages
- ✅ No encoding errors
- ✅ UTF-8 characters handled properly

#### Test Data
```java
Map<String, String> texts = Map.of(
    "en", "This is a luxury watch",
    "es", "Este es un reloj de lujo",
    "ja", "これは高級時計です",
    "ar", "هذه ساعة فاخرة"
);
```

---

### TEST-EMBED-008: Special Characters Handling
**Priority**: Medium  
**Status**: ✅ AUTOMATED (`EmbeddingSpecialCharactersIntegrationTest#embeddingsHandleSpecialCharacters`)  

#### Test Steps
1. Create text with special characters: emoji, symbols, unicode
2. Generate embedding
3. Verify no errors
4. Verify embedding quality

#### Expected Results
- ✅ Special characters handled gracefully
- ✅ No exceptions thrown
- ✅ Valid embeddings generated

#### Test Data
```java
String specialText = "Product 💎 with symbols: @#$%^&*() and unicode: café, naïve";
```

---

### TEST-EMBED-009: Embedding Cache Hit/Miss
**Priority**: High  
**Status**: ❌ NOT IMPLEMENTED  
**Pre-requisites**: Caching enabled

#### Test Steps
1. Generate embedding for text "test product"
2. Verify cache miss (first time)
3. Generate embedding for same text again
4. Verify cache hit (second time)
5. Measure performance difference
6. Test cache invalidation

#### Expected Results
- ✅ First request is cache miss
- ✅ Second request is cache hit
- ✅ Cache hit is 10x+ faster
- ✅ Cache statistics updated correctly

#### Test Data
```java
String text = "Luxury Swiss watch with diamond bezel";
// First call - cache miss
AIEmbeddingResponse response1 = embeddingService.generateEmbedding(request);
// Second call - cache hit
AIEmbeddingResponse response2 = embeddingService.generateEmbedding(request);
```

#### Performance Expectations
- Cache miss: ~1000ms
- Cache hit: <10ms
- Hit ratio: >80% in production

#### Implementation Notes
```java
@Test
public void testEmbeddingCacheHitMiss() {
    // Given
    String text = "Luxury Swiss watch with diamond bezel";
    AIEmbeddingRequest request = AIEmbeddingRequest.builder()
        .text(text)
        .build();
    
    // When - First call (cache miss)
    long startMiss = System.currentTimeMillis();
    AIEmbeddingResponse response1 = embeddingService.generateEmbedding(request);
    long durationMiss = System.currentTimeMillis() - startMiss;
    
    // When - Second call (cache hit)
    long startHit = System.currentTimeMillis();
    AIEmbeddingResponse response2 = embeddingService.generateEmbedding(request);
    long durationHit = System.currentTimeMillis() - startHit;
    
    // Then
    assertNotNull(response1);
    assertNotNull(response2);
    assertEquals(response1.getEmbedding(), response2.getEmbedding());
    assertTrue(durationHit < durationMiss / 10, 
               "Cache hit should be 10x faster");
    
    // Verify metrics
    Map<String, Object> metrics = embeddingService.getPerformanceMetrics();
    assertTrue((Long)metrics.get("cacheHits") > 0);
}
```

---

### TEST-EMBED-010: Provider Switching (OpenAI → ONNX)
**Priority**: Critical  
**Status**: ❌ NOT IMPLEMENTED  
**Pre-requisites**: Both providers configured

#### Test Steps
1. Start with OpenAI provider active
2. Generate embedding successfully
3. Simulate OpenAI API failure
4. Verify automatic fallback to ONNX
5. Verify embedding still generated
6. Test switch back to OpenAI when recovered

#### Expected Results
- ✅ Automatic provider switching
- ✅ No request failures
- ✅ Seamless fallback
- ✅ User not impacted
- ✅ Metrics track provider switches

#### Test Data
```java
// Simulate OpenAI failure
mockOpenAIProvider.setAvailable(false);

// Request should fallback to ONNX
AIEmbeddingResponse response = embeddingService.generateEmbedding(request);
```

#### Success Criteria
- Zero downtime
- Automatic recovery
- Proper logging
- Metrics updated

#### Implementation Notes
```java
@Test
public void testProviderSwitching() {
    // Given - OpenAI is primary
    configureProvider("openai");
    
    // When - OpenAI fails
    simulateOpenAIFailure();
    
    AIEmbeddingRequest request = AIEmbeddingRequest.builder()
        .text("Test product")
        .build();
    
    AIEmbeddingResponse response = embeddingService.generateEmbedding(request);
    
    // Then - ONNX provider used as fallback
    assertNotNull(response);
    assertEquals("onnx", response.getProvider());
    
    // When - OpenAI recovers
    simulateOpenAIRecovery();
    
    response = embeddingService.generateEmbedding(request);
    
    // Then - Back to OpenAI
    assertEquals("openai", response.getProvider());
}
```

---

## 📊 Test Data Requirements

### Sample Texts
```java
// Short text
"Luxury Swiss watch"

// Medium text (100-500 chars)
"Premium leather handbag crafted from Italian calfskin with gold-plated hardware. 
Features multiple compartments and adjustable shoulder strap."

// Long text (1000+ chars)
"[Full product description with specifications, features, benefits, etc.]"

// Special characters
"Product with émojis 💎, symbols @#$%, and unicode café"

// Multi-language
Map.of(
    "en", "...",
    "es", "...",
    "ja", "...",
    "ar", "..."
)
```

### Performance Baseline
| Scenario | OpenAI | ONNX | REST |
|----------|--------|------|------|
| Single embedding | 1.2s | 0.2s | 0.5s |
| Batch (100) | 120s | 20s | 50s |
| Concurrent (50) | 60s | 10s | 30s |

---

## 🎯 Success Criteria

### Functional Requirements
- ✅ All providers work correctly
- ✅ Provider switching works seamlessly
- ✅ Batch processing completes successfully
- ✅ Concurrent requests handled properly
- ✅ Caching improves performance
- ✅ Multi-language support works
- ✅ Error handling is robust

### Performance Requirements
- ✅ OpenAI embeddings < 3s per request
- ✅ ONNX embeddings < 1s per request
- ✅ Batch processing throughput > 30 items/min
- ✅ Cache hit ratio > 70%
- ✅ Concurrent handling 50+ requests
- ✅ Memory usage < 2GB for 100 concurrent requests

### Quality Requirements
- ✅ Test coverage > 90%
- ✅ Zero memory leaks
- ✅ Zero race conditions
- ✅ Proper error messages
- ✅ Complete logging

---

## 🐛 Known Issues & Risks

### Current Issues
1. ONNX provider not implemented
2. REST provider not tested
3. No concurrent operation tests
4. Cache effectiveness unknown
5. No multi-language validation

### Risk Mitigation
- **Risk**: ONNX embeddings different quality from OpenAI
  - **Mitigation**: Compare similarity scores, set quality thresholds
  
- **Risk**: Concurrent requests cause race conditions
  - **Mitigation**: Thorough thread safety testing, use concurrent collections

- **Risk**: Batch processing memory issues
  - **Mitigation**: Process in sub-batches, monitor memory usage

---

## 📅 Implementation Schedule

### Week 1: Critical Tests
- Day 1-2: TEST-EMBED-002 (ONNX)
- Day 3-4: TEST-EMBED-004 (Batch)
- Day 5: TEST-EMBED-005 (Concurrent)

### Week 2: High Priority Tests
- Day 6-7: TEST-EMBED-009 (Caching)
- Day 8-9: TEST-EMBED-010 (Provider Switching)
- Day 10: TEST-EMBED-006 (Chunking)

---

## 📝 Test Execution Checklist

- [ ] All test data prepared
- [ ] OpenAI API key configured
- [ ] ONNX models downloaded
- [ ] Docker environment set up
- [ ] Test database initialized
- [ ] Logging configured
- [ ] Metrics collection enabled
- [ ] All tests passing locally
- [ ] Tests integrated into CI/CD
- [ ] Performance benchmarks documented

---

**End of Test Plan**
