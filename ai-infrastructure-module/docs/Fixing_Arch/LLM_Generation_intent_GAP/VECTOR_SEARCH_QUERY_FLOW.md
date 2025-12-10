# Vector Search Query Flow: From Intent to Results

## 📋 Question
Given an intent:
```
Intent {
  type: INFORMATION
  intent: "find_products"
  vectorSpace: "product"
  requiresRetrieval: true
}
```

**How does vector search happen? What query is used to search embeddings?**

---

## ✅ Short Answer

**NOT a SQL query or text query!**

Instead: **A VECTOR (array of numbers)** that represents the semantic meaning of the user's query.

```
User Input Text: "find_products"
       ↓ (embedded)
Vector: [0.123, -0.456, 0.789, ..., 0.245]  ← This searches the vector DB!
       ↓ (similarity search)
Find most similar vectors in database
       ↓
Return top 10 matching documents
```

---

## 🔄 Complete 4-Step Flow

### Step 1: RAGOrchestrator.handleInformation()

**File:** `RAGOrchestrator.java`  
**Location:** Lines 293-320  

```java
private OrchestrationResult handleInformation(Intent intent, String userId) {
    // Extract the user's query from the intent
    String query = intent.getIntent();  // ← "find_products"
    
    // Build a RAG request with search parameters
    RAGRequest ragRequest = RAGRequest.builder()
        .query(query)                              // ← User's text query
        .entityType(intent.getVectorSpace())       // ← "product"
        .limit(DEFAULT_RAG_LIMIT)                  // ← 10 (get top 10)
        .threshold(DEFAULT_RAG_THRESHOLD)          // ← 0.7 (similarity ≥ 0.7)
        .metadata(buildMetadata(userId, intent))
        .build();
    
    // Perform RAG - delegates to RAGService
    RAGResponse ragResponse = ragService.performRag(ragRequest);
    
    // Process response based on requiresGeneration flag...
}
```

**Input to Step 2:**
```
RAGRequest {
  query: "find_products",
  entityType: "product",
  limit: 10,
  threshold: 0.7
}
```

---

### Step 2: RAGService.performRag()

**File:** `RAGService.java`  
**Location:** Lines 373-415  

This is where the text query becomes a vector:

```java
public RAGResponse performRag(RAGRequest request) {
    try {
        // Step 2A: Sanitize the query (PII detection)
        PIIDetectionResult piiDetectionResult = 
            piiDetectionService.detectAndProcess(request.getQuery());
        
        String sanitizedQuery = piiDetectionResult.getProcessedQuery();
        // Result: "find_products" (no PII detected, so unchanged)
        
        log.debug("Performing RAG operation (entityType={}, query={})",
            request.getEntityType(), sanitizedQuery);

        // Step 2B: CONVERT TEXT QUERY TO VECTOR ✅✅✅
        AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
            .text(sanitizedQuery)              // ← "find_products"
            .build();
        
        AIEmbeddingResponse embeddingResponse = 
            embeddingService.generateEmbedding(embeddingRequest);
        
        List<Double> queryVector = embeddingResponse.getEmbedding();
        // Result: [0.123, -0.456, 0.789, 0.001, ..., 0.245]
        //         ↑
        //    768 or 1536 dimensions depending on model
        //    (e.g., text-embedding-3-small = 512 dims, 
        //     text-embedding-3-large = 3072 dims)

        // Step 2C: Build search request with parameters
        AISearchRequest searchRequest = AISearchRequest.builder()
            .query(sanitizedQuery)                   // ← Original text for logging
            .entityType(request.getEntityType())     // ← "product"
            .limit(request.getLimit())               // ← 10
            .threshold(request.getThreshold())       // ← 0.7
            .context(contextString)
            .filters(filtersString)
            .metadata(request.getMetadata())
            .build();
        
        // Step 2D: SEARCH USING THE VECTOR ✅✅✅
        AISearchResponse searchResponse = searchService.search(
            queryVector,      // ← [0.123, -0.456, 0.789, ...]
            searchRequest     // ← entityType, limit, threshold
        );
        
        // Convert to RAG response format
        Map<String, Object> filters = request.getFilters();
        // ... build final response ...
        
        return RAGResponse.builder()
            .originalQuery(request.getQuery())
            .response(searchResponse.getResponse())
            .success(true)
            .build();
            
    } catch (Exception e) {
        log.error("Error performing RAG operation", e);
        throw new AIServiceException("Failed to perform RAG operation", e);
    }
}
```

**Output to Step 3:**
```
queryVector: [0.123, -0.456, 0.789, ...]
searchRequest: {
  query: "find_products",
  entityType: "product",
  limit: 10,
  threshold: 0.7
}
```

---

### Step 3: VectorSearchService.search()

**File:** `VectorSearchService.java`  
**Location:** Lines 67-93  

This adds caching before delegating to the vector database:

```java
public AISearchResponse search(List<Double> queryVector, 
                               AISearchRequest request) {
    totalSearches.incrementAndGet();

    // Step 3A: Check cache first
    String cacheKey = buildCacheKey(queryVector, request);
    Cache cache = getSearchCache();
    
    AISearchResponse cachedResponse = getFromCache(cache, cacheKey);
    if (cachedResponse != null) {
        cacheHits.incrementAndGet();
        log.debug("Vector search cache hit for query: {}", request.getQuery());
        return cachedResponse;  // ← FAST PATH if cached!
    }

    // Step 3B: Cache miss - perform actual search
    long startTime = System.currentTimeMillis();
    
    AISearchResponse response = executeSearch(queryVector, request);
    // This calls: vectorDatabaseService.search(queryVector, request)
    
    long processingTime = System.currentTimeMillis() - startTime;
    cacheMisses.incrementAndGet();

    // Step 3C: Cache the result for future identical queries
    if (cache != null) {
        cache.put(cacheKey, response);
    }

    log.debug("Vector database returned {} results in {}ms", 
        response.getTotalResults(), processingTime);
    
    return response;
}

private AISearchResponse executeSearch(List<Double> queryVector, 
                                       AISearchRequest request) {
    try {
        // DELEGATES TO ACTUAL VECTOR DATABASE
        return vectorDatabaseService.search(queryVector, request);
    } catch (Exception e) {
        log.error("Error performing vector search", e);
        throw new AIServiceException("Failed to perform vector search", e);
    }
}
```

**What happens here:**
- Cache check using hash of vector + request parameters
- If cache miss, delegates to actual implementation

---

### Step 4: VectorDatabaseService.search()

**File:** `VectorDatabaseService.java` (interface)  
**Actual implementations:** `LuceneVectorDatabaseService`, `InMemoryVectorDatabaseService`, `PineconeVectorDatabaseService`, etc.

The interface:
```java
public interface VectorDatabaseService {
    /**
     * Search for similar vectors
     * 
     * @param queryVector the query vector       ← [0.123, -0.456, ...]
     * @param request the search request          ← entityType, limit, threshold
     * @return search results with similarity scores
     */
    AISearchResponse search(List<Double> queryVector, AISearchRequest request);
}
```

---

## 🎯 What Query is Actually Used?

The actual "query" is **NOT text** - it's a **vector (array of numbers)**.

### Different Vector Database Implementations:

#### 1️⃣ Lucene (Java Native)

Uses Lucene's KnnVectorQuery with HNSW (Hierarchical Navigable Small World):

```java
// Lucene internally constructs:
KnnVectorQuery query = new KnnVectorQuery(
    "embedding_field",     // Field name in Lucene index
    queryVector,           // [0.123, -0.456, 0.789, ...]
    10                     // k=10 (get top 10 neighbors)
);

// Lucene performs:
// ✓ HNSW index traversal (fast approximate nearest neighbor search)
// ✓ Cosine similarity or dot product calculation
// ✓ Return top 10 vectors closest to queryVector
// ✓ Each result includes similarity score (0.0-1.0)
// ✓ Filter by threshold: score >= 0.7
```

**Time Complexity:** O(log n) with HNSW indexing (extremely fast)

---

#### 2️⃣ In-Memory (Java - Testing)

Simulates vector search in memory:

```java
// Pseudocode of similarity search:
List<SearchResult> results = new ArrayList<>();

for (VectorRecord storedRecord : allStoredVectors) {
    // Filter by entity type
    if (!storedRecord.getEntityType().equals("product")) {
        continue;
    }
    
    // Calculate similarity between queryVector and stored vector
    double similarity = calculateCosineSimilarity(
        queryVector,              // [0.123, -0.456, 0.789, ...]
        storedRecord.getEmbedding() // [0.456, -0.123, 0.001, ...]
    );
    
    // Only include if above threshold
    if (similarity >= 0.7) {
        results.add(new SearchResult(
            storedRecord.getId(),
            similarity,
            storedRecord.getMetadata()
        ));
    }
}

// Sort by similarity (descending)
results.sort((a, b) -> Double.compare(b.similarity, a.similarity));

// Return top 10
return results.stream().limit(10).collect(toList());
```

**Cosine Similarity Calculation:**
```
cosine_similarity(A, B) = (A · B) / (||A|| * ||B||)

Where:
- A · B = dot product
- ||A|| = magnitude of A
- ||B|| = magnitude of B

Result: 0.0 to 1.0 (1.0 = perfect match, 0.0 = completely different)
```

---

#### 3️⃣ Pinecone API

Calls Pinecone's hosted vector database:

```
HTTP Request:
POST https://api.pinecone.io/query

{
  "vector": [0.123, -0.456, 0.789, ..., 0.245],  // ← Vector
  "topK": 10,                                      // ← Get top 10
  "namespace": "product",                          // ← Filter by namespace
  "filter": {                                      // ← Additional metadata filters
    "entityType": "product",
    "inStock": true
  }
}

HTTP Response:
{
  "matches": [
    {
      "id": "prod-1",
      "score": 0.92,  // ← Similarity score
      "metadata": {
        "name": "Luxury Wallet",
        "price": 199.99
      }
    },
    {
      "id": "prod-2",
      "score": 0.88,
      "metadata": { ... }
    },
    ...
  ]
}
```

---

## 📊 Complete Search Flow Diagram

```
┌──────────────────────────────────────────────────────────────────────┐
│ User Query: "find_products"                                           │
└──────────────────────────┬───────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────────┐
│ RAGOrchestrator.handleInformation()                                   │
│   → Extract query: "find_products"                                    │
│   → Create RAGRequest with entityType="product", limit=10            │
└──────────────────────────┬───────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────────┐
│ RAGService.performRag(RAGRequest)                                     │
│   1. PII Detection → "find_products" (no changes)                    │
│   2. Generate Embedding                                               │
│      Input: "find_products"                                           │
│      Output: [0.123, -0.456, 0.789, ..., 0.245]                     │
│   3. Build AISearchRequest with limit=10, threshold=0.7             │
└──────────────────────────┬───────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────────┐
│ VectorSearchService.search(vector, request)                           │
│   1. Check cache (key = hash(vector) + hash(request))               │
│   2. If miss → executeSearch()                                        │
└──────────────────────────┬───────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────────┐
│ VectorDatabaseService.search(vector, request)                         │
│                                                                       │
│   ┌─ For Lucene ─────────────────────────┐                          │
│   │ KnnVectorQuery(field="embedding",    │                          │
│   │   vector=[0.123, -0.456, ...], k=10)│                          │
│   │ HNSW Index Search                     │                          │
│   │ Return top 10 by similarity          │                          │
│   └──────────────────────────────────────┘                          │
│                                                                       │
│   ┌─ For InMemory ────────────────────────┐                         │
│   │ For each stored vector:               │                         │
│   │   similarity = cosine(query, stored)  │                         │
│   │   if similarity >= 0.7: add to results│                         │
│   │ Sort descending, return top 10        │                         │
│   └──────────────────────────────────────┘                          │
│                                                                       │
│   ┌─ For Pinecone ────────────────────────┐                         │
│   │ POST /query with vector               │                         │
│   │ Pinecone returns top-k matches        │                         │
│   └──────────────────────────────────────┘                          │
└──────────────────────────┬───────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────────────────┐
│ AISearchResponse                                                      │
│ {                                                                     │
│   "documents": [                                                      │
│     {                                                                 │
│       "id": "prod-1",                                                 │
│       "name": "Luxury Leather Wallet",                                │
│       "description": "Premium Italian leather",                       │
│       "similarity": 0.92,                                             │
│       "metadata": {...}                                               │
│     },                                                                │
│     {...},                                                            │
│     ...10 results total                                               │
│   ],                                                                  │
│   "totalResults": 10,                                                │
│   "maxScore": 0.92                                                    │
│ }                                                                     │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 🔍 Key Parameters at Each Step

### RAGOrchestrator → RAGService
```
query: "find_products"     (string - the user's search)
entityType: "product"      (which type of entity to search)
limit: 10                  (top-k results to return)
threshold: 0.7             (minimum similarity score)
```

### RAGService → VectorSearchService
```
queryVector: [0.123, -0.456, ...]  (768 or 1536 dimensions)
request: {
  query: "find_products"   (kept for logging/metadata)
  entityType: "product"    (filter results by entity type)
  limit: 10
  threshold: 0.7
}
```

### VectorSearchService → VectorDatabaseService
```
queryVector: [0.123, -0.456, ...]  (exact same vector)
request: same as above
```

### VectorDatabaseService → Results
```
Finds all stored vectors where:
  - entityType = "product"
  - similarity(queryVector, storedVector) >= 0.7
- Returns top 10 by similarity score
```

---

## ✨ Key Insights

### 1. Text → Vector Conversion
```
"find_products"
     ↓ (EmbeddingService)
[0.123, -0.456, 0.789, ...]
     ↓ (represents semantic meaning)
"Used to search vector DB"
```

### 2. Vector Similarity
```
Vector A: [0.1, 0.2, 0.3]
Vector B: [0.1, 0.2, 0.3]  → Similarity: 1.0 (perfect match)

Vector C: [0.1, 0.2, 0.3]
Vector D: [0.5, 0.6, 0.7]  → Similarity: 0.65 (somewhat similar)

Vector E: [0.1, 0.2, 0.3]
Vector F: [1.0, 1.0, 1.0]  → Similarity: 0.1 (very different)
```

### 3. Filtering
```
Search retrieves ALL vectors where similarity >= threshold
Filter by entityType = "product"
Sort by similarity descending
Return top 10
```

### 4. Performance Optimization
```
Lucene HNSW: O(log n) - extremely fast with large datasets
InMemory: O(n) - linear but only for testing
Pinecone: Network latency but handles massive scale
Caching: O(1) - instant for identical queries
```

---

## 🎯 Summary

**What query is used to search embeddings?**

✅ **A vector** - an array of 512, 768, or 1536 numbers  
✅ **Derived from user's text** - via embedding model  
✅ **Not a SQL query** - similarity-based search  
✅ **Finds most similar vectors** - in database  
✅ **Filtered by entity type and threshold** - "product" + similarity ≥ 0.7  

The "query" transforms from **text** → **vector** → **similarity search** → **results**.

