# Vector Database Hybrid Search Implementation Plan

## Executive Summary

**Objective**: Extend `VectorDatabaseService` interface to support hybrid search (vector + keyword), keyword/BM25 search, and RRF (Reciprocal Rank Fusion) through an SPI pattern.

**Approach**: Framework defines optional interface methods with default implementations. Provider modules implement hybrid search using their native capabilities.

**Status**: Planning Phase

---

## Current State Analysis

### Current VectorDatabaseService Interface

**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/VectorDatabaseService.java`

**Current Methods**:
- ✅ `search(List<Double> queryVector, AISearchRequest request)` - Vector similarity search only
- ❌ No hybrid search method
- ❌ No keyword search method
- ❌ No capability detection

---

### Current Provider Implementations

**PineconeVectorDatabaseService**:
- ✅ Implements `VectorDatabaseService`
- ✅ Uses Pinecone `/query` API
- ❌ Doesn't use Pinecone's native hybrid search API
- ❌ Only does vector search

**QdrantVectorDatabaseService**:
- ✅ Implements `VectorDatabaseService`
- ✅ Uses Qdrant `/points/search` API
- ❌ Doesn't use Qdrant's native hybrid search API
- ❌ Only does vector search

**LuceneVectorDatabaseService**:
- ✅ Implements `VectorDatabaseService`
- ✅ Uses Lucene `KnnVectorQuery` for vector search
- ❌ Doesn't use Lucene `BM25Query` for keyword search
- ❌ Doesn't combine vector + keyword with RRF

---

### Current RAGService Stubs

**File**: `ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/service/RAGService.java`

**Stub Methods**:
```java
// Lines 429-432: Stub - just calls vector search
private AISearchResponse performHybridSearch(...) {
    return vectorDatabase.search(queryVector, request);  // ← Not actually hybrid!
}

// Lines 434-437: Stub - just calls vector search
private AISearchResponse performContextualSearch(...) {
    return vectorDatabase.search(queryVector, request);  // ← Not actually contextual!
}
```

**Problem**: These methods don't actually implement hybrid or contextual search.

---

## Architecture Design

### Target Architecture

```
RAGService
    ↓
VectorDatabaseService (Interface)
    ├─ search() - Vector similarity (existing)
    ├─ hybridSearch() - Vector + Keyword (NEW - optional)
    ├─ keywordSearch() - Keyword/BM25 only (NEW - optional)
    ├─ supportsHybridSearch() - Capability detection (NEW)
    └─ supportsKeywordSearch() - Capability detection (NEW)
        ↓
    Provider Implementations
        ├─ PineconeVectorDatabaseService
        │   └─ Uses Pinecone native hybrid search API
        ├─ QdrantVectorDatabaseService
        │   └─ Uses Qdrant native hybrid search API
        ├─ LuceneVectorDatabaseService
        │   └─ Implements RRF fusion (vector + BM25)
        └─ Others
            └─ Fall back to vector search
```

---

## Implementation Plan

### Phase 1: Extend VectorDatabaseService Interface

#### Step 1.1: Add Optional Hybrid Search Method

**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/rag/VectorDatabaseService.java`

**Changes**:
```java
/**
 * Vector Database Service Interface
 * 
 * This interface defines the contract for vector database operations.
 * Different implementations can be provided for various vector databases
 * like Lucene, Pinecone, Chroma, etc.
 * 
 * @author AI Infrastructure Team
 * @version 3.0.0  // ← Increment version
 */
public interface VectorDatabaseService {
    
    // ... existing methods ...
    
    /**
     * Perform hybrid search combining vector similarity and keyword/text search.
     * 
     * <p>This is an OPTIONAL method. Providers that support native hybrid search
     * (Pinecone, Qdrant, Weaviate) should implement this using their native APIs.
     * Providers that don't support it can use the default implementation which
     * falls back to vector search, or implement custom RRF fusion (e.g., Lucene).</p>
     * 
     * <p><strong>Implementation Strategies:</strong></p>
     * <ul>
     *   <li><strong>Native Support</strong> (Pinecone, Qdrant): Use native hybrid search API</li>
     *   <li><strong>Custom RRF</strong> (Lucene): Combine KnnVectorQuery + BM25Query with RRF</li>
     *   <li><strong>Fallback</strong>: Return vector search results if not supported</li>
     * </ul>
     * 
     * <p><strong>Hybrid Search Process:</strong></p>
     * <ol>
     *   <li>Perform vector similarity search using queryVector</li>
     *   <li>Perform keyword/text search using queryText (BM25, full-text, etc.)</li>
     *   <li>Combine results using RRF (Reciprocal Rank Fusion) or native fusion</li>
     *   <li>Return top K results sorted by combined score</li>
     * </ol>
     * 
     * @param queryVector the query vector for semantic/similarity search
     * @param queryText the original query text for keyword/text search
     * @param request the search request with filters, limits, etc.
     * @return hybrid search results combining vector and keyword search
     * @throws UnsupportedOperationException if provider doesn't support hybrid search
     *         and cannot fall back (should be rare - default implementation handles fallback)
     */
    default AISearchResponse hybridSearch(List<Double> queryVector, String queryText, AISearchRequest request) {
        // Default implementation: fall back to vector search
        log.warn("Hybrid search not supported by {}, falling back to vector search", 
            this.getClass().getSimpleName());
        return search(queryVector, request);
    }
    
    /**
     * Perform keyword/text search only (BM25, full-text, etc.).
     * 
     * <p>This is an OPTIONAL method for providers that support keyword search.
     * Useful when you only want keyword matching without semantic similarity.</p>
     * 
     * <p><strong>Use Cases:</strong></p>
     * <ul>
     *   <li>Exact keyword matching</li>
     *   <li>Fast text search without embeddings</li>
     *   <li>Combining with vector search manually</li>
     * </ul>
     * 
     * @param queryText the query text for keyword search
     * @param request the search request with filters, limits, etc.
     * @return keyword search results
     * @throws UnsupportedOperationException if provider doesn't support keyword search
     */
    default AISearchResponse keywordSearch(String queryText, AISearchRequest request) {
        throw new UnsupportedOperationException(
            "Keyword search not supported by " + this.getClass().getSimpleName());
    }
    
    /**
     * Check if this provider supports hybrid search.
     * 
     * <p>Providers should return {@code true} if they implement {@link #hybridSearch}
     * with actual hybrid search logic (not just fallback to vector search).</p>
     * 
     * @return true if hybrid search is supported, false otherwise
     */
    default boolean supportsHybridSearch() {
        return false;  // Default: not supported
    }
    
    /**
     * Check if this provider supports keyword/BM25 search.
     * 
     * <p>Providers should return {@code true} if they implement {@link #keywordSearch}
     * or can perform keyword search as part of hybrid search.</p>
     * 
     * @return true if keyword search is supported, false otherwise
     */
    default boolean supportsKeywordSearch() {
        return false;  // Default: not supported
    }
}
```

**Key Points**:
- ✅ Default implementations provide fallback
- ✅ Optional methods don't break existing code
- ✅ Capability detection methods
- ✅ Clear JavaDoc explaining implementation strategies

---

#### Step 1.2: Add Logging Support

**File**: `VectorDatabaseService.java`

**Add import**:
```java
import lombok.extern.slf4j.Slf4j;
```

**Note**: Since this is an interface, we can't use `@Slf4j`. Instead, use `LoggerFactory`:

```java
default AISearchResponse hybridSearch(List<Double> queryVector, String queryText, AISearchRequest request) {
    // Use LoggerFactory for interface default methods
    org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(this.getClass());
    log.warn("Hybrid search not supported by {}, falling back to vector search", 
        this.getClass().getSimpleName());
    return search(queryVector, request);
}
```

---

### Phase 2: Provider Implementations

#### Step 2.1: Pinecone Implementation

**File**: `victor-databases/ai-infrastructure-vector-pinecone/src/main/java/com/ai/infrastructure/vector/pinecone/PineconeVectorDatabaseService.java`

**Changes**:
```java
@Override
public boolean supportsHybridSearch() {
    return true;  // Pinecone has native hybrid search
}

@Override
public boolean supportsKeywordSearch() {
    return true;  // Pinecone supports sparse vectors (BM25-like)
}

@Override
public AISearchResponse hybridSearch(List<Double> queryVector, String queryText, AISearchRequest request) {
    if (CollectionUtils.isEmpty(queryVector)) {
        throw new AIServiceException("Query vector is required for Pinecone hybrid search");
    }
    
    long start = System.currentTimeMillis();
    
    try {
        // Generate sparse vector for keyword search (BM25-like)
        Map<String, Double> sparseVector = generateSparseVector(queryText);
        
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("namespace", namespace(request.getEntityType()));
        payload.put("vector", queryVector);  // Dense vector
        payload.put("sparseVector", sparseVector);  // Sparse vector for keyword search
        payload.put("topK", request.getLimit() != null ? request.getLimit() : 10);
        payload.put("includeMetadata", true);
        payload.put("includeValues", false);
        
        // Pinecone automatically combines dense + sparse vectors
        if (request.getMetadata() != null && !request.getMetadata().isEmpty()) {
            payload.put("filter", request.getMetadata());
        }
        
        Map<String, Object> response = post("/query", payload);
        List<Map<String, Object>> matches = (List<Map<String, Object>>) response.getOrDefault("matches", List.of());
        double threshold = request.getThreshold() != null ? request.getThreshold() : 0.0;
        
        List<Map<String, Object>> results = new ArrayList<>();
        double maxScore = 0.0;
        for (Map<String, Object> match : matches) {
            Double score = toDouble(match.get("score"));
            if (score == null || score < threshold) {
                continue;
            }
            maxScore = Math.max(maxScore, score);
            results.add(convertMatchToResult(match));
        }
        
        return AISearchResponse.builder()
            .results(results)
            .totalResults(results.size())
            .maxScore(maxScore)
            .processingTimeMs(System.currentTimeMillis() - start)
            .requestId(UUID.randomUUID().toString())
            .query(request.getQuery())
            .model(config.resolveEmbeddingDefaults().model())
            .build();
            
    } catch (Exception ex) {
        throw new AIServiceException("Failed to perform Pinecone hybrid search", ex);
    }
}

@Override
public AISearchResponse keywordSearch(String queryText, AISearchRequest request) {
    // Pinecone doesn't support keyword-only search directly
    // Use sparse vector with zero dense vector
    List<Double> zeroVector = Collections.nCopies(
        config.resolveEmbeddingDefaults().model().length(), 0.0);
    return hybridSearch(zeroVector, queryText, request);
}

/**
 * Generate sparse vector from query text (BM25-like).
 * 
 * Pinecone uses sparse vectors for keyword search. This method converts
 * query text into a sparse vector representation.
 */
private Map<String, Double> generateSparseVector(String queryText) {
    // Simple tokenization and frequency calculation
    // In production, use proper BM25 or TF-IDF calculation
    Map<String, Double> sparseVector = new HashMap<>();
    
    if (queryText == null || queryText.trim().isEmpty()) {
        return sparseVector;
    }
    
    // Tokenize query
    String[] tokens = queryText.toLowerCase()
        .replaceAll("[^a-z0-9\\s]", "")
        .split("\\s+");
    
    // Calculate term frequencies (simplified - production should use BM25)
    Map<String, Integer> termFreq = new HashMap<>();
    for (String token : tokens) {
        if (token.length() > 2) {  // Ignore very short tokens
            termFreq.put(token, termFreq.getOrDefault(token, 0) + 1);
        }
    }
    
    // Convert to sparse vector format (indices and values)
    // Pinecone sparse vector format: {"indices": [1, 2, 3], "values": [0.5, 0.3, 0.2]}
    // For simplicity, we'll use a map representation
    // In production, use proper BM25 calculation with document frequencies
    
    double maxFreq = termFreq.values().stream()
        .mapToInt(Integer::intValue)
        .max()
        .orElse(1);
    
    for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
        // Normalize frequency (simplified - production should use BM25)
        double score = entry.getValue() / maxFreq;
        sparseVector.put(entry.getKey(), score);
    }
    
    return sparseVector;
}
```

**Note**: Pinecone sparse vector format may need adjustment based on actual API. Check Pinecone documentation for exact format.

---

#### Step 2.2: Qdrant Implementation

**File**: `victor-databases/ai-infrastructure-vector-qdrant/src/main/java/com/ai/infrastructure/vector/qdrant/QdrantVectorDatabaseService.java`

**Changes**:
```java
@Override
public boolean supportsHybridSearch() {
    return true;  // Qdrant has native hybrid search
}

@Override
public boolean supportsKeywordSearch() {
    return true;  // Qdrant supports full-text search
}

@Override
public AISearchResponse hybridSearch(List<Double> queryVector, String queryText, AISearchRequest request) {
    ensureEnabled();
    String entityType = Optional.ofNullable(request.getEntityType()).orElseThrow(() ->
        new AIServiceException("Qdrant hybrid search requires request.entityType"));
    ensureCollection(entityType, queryVector.size());
    
    int limit = Optional.ofNullable(request.getLimit()).orElse(10);
    double threshold = Optional.ofNullable(request.getThreshold()).orElse(0.0);
    
    ObjectNode payload = MAPPER.createObjectNode();
    payload.put("limit", limit);
    
    // Qdrant hybrid search: combine vector and query_text
    ArrayNode vectorArray = payload.putArray("vector");
    queryVector.forEach(vectorArray::add);
    
    // Add query text for full-text search
    if (queryText != null && !queryText.trim().isEmpty()) {
        payload.put("query_text", queryText);
    }
    
    // Qdrant automatically combines vector + full-text search
    JsonNode filterNode = buildFilterNode(request.getFilters(), request.getMetadata());
    if (filterNode != null) {
        payload.set("filter", filterNode);
    }
    
    JsonNode response = execute(HttpMethod.POST, collectionPath(entityType, "/points/search"), payload, JsonNode.class);
    List<VectorRecord> results = parseSearchResults(entityType, response.path("result"), threshold);
    
    return AISearchResponse.builder()
        .query(request.getQuery())
        .results(results.stream()
            .map(record -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("vectorId", record.getVectorId());
                row.put("entityId", record.getEntityId());
                row.put("entityType", record.getEntityType());
                row.put("content", record.getContent());
                row.put("metadata", record.getMetadata());
                row.put("score", record.getSimilarityScore());
                row.put("similarity", record.getSimilarityScore());
                return row;
            })
            .collect(Collectors.toList()))
        .totalResults(results.size())
        .maxScore(results.stream()
            .mapToDouble(VectorRecord::getSimilarityScore)
            .max()
            .orElse(0.0))
        .model(entityType)
        .build();
}

@Override
public AISearchResponse keywordSearch(String queryText, AISearchRequest request) {
    ensureEnabled();
    String entityType = Optional.ofNullable(request.getEntityType()).orElseThrow(() ->
        new AIServiceException("Qdrant keyword search requires request.entityType"));
    ensureCollection(entityType, 0);  // Dimension not needed for keyword-only
    
    int limit = Optional.ofNullable(request.getLimit()).orElse(10);
    
    ObjectNode payload = MAPPER.createObjectNode();
    payload.put("limit", limit);
    payload.put("query_text", queryText);  // Qdrant full-text search
    
    JsonNode filterNode = buildFilterNode(request.getFilters(), request.getMetadata());
    if (filterNode != null) {
        payload.set("filter", filterNode);
    }
    
    JsonNode response = execute(HttpMethod.POST, collectionPath(entityType, "/points/search"), payload, JsonNode.class);
    List<VectorRecord> results = parseSearchResults(entityType, response.path("result"), 0.0);
    
    return AISearchResponse.builder()
        .query(request.getQuery())
        .results(results.stream()
            .map(record -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("vectorId", record.getVectorId());
                row.put("entityId", record.getEntityId());
                row.put("entityType", record.getEntityType());
                row.put("content", record.getContent());
                row.put("metadata", record.getMetadata());
                row.put("score", record.getSimilarityScore());
                return row;
            })
            .collect(Collectors.toList()))
        .totalResults(results.size())
        .maxScore(results.stream()
            .mapToDouble(VectorRecord::getSimilarityScore)
            .max()
            .orElse(0.0))
        .model(entityType)
        .build();
}
```

**Note**: Verify Qdrant API format for hybrid search. May need to adjust based on actual Qdrant API version.

---

#### Step 2.3: Lucene Implementation (RRF Fusion)

**File**: `victor-databases/ai-infrastructure-vector-lucene/src/main/java/com/ai/infrastructure/vector/lucene/LuceneVectorDatabaseService.java`

**Changes**:
```java
// Add imports
import org.apache.lucene.search.QueryParser;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.apache.lucene.search.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;

// Add field constant
private static final String CONTENT_FIELD = "content";

@Override
public boolean supportsHybridSearch() {
    return true;  // Lucene can do both vector + keyword
}

@Override
public boolean supportsKeywordSearch() {
    return true;  // Lucene has BM25
}

@Override
public AISearchResponse hybridSearch(List<Double> queryVector, String queryText, AISearchRequest request) {
    try {
        log.debug("Performing hybrid search in Lucene: vector + keyword (BM25)");
        
        long startTime = System.currentTimeMillis();
        
        int k = Math.min(request.getLimit() * 2, maxResults * 2);
        
        // 1. Vector search
        float[] queryVectorArray = new float[queryVector.size()];
        for (int i = 0; i < queryVector.size(); i++) {
            queryVectorArray[i] = queryVector.get(i).floatValue();
        }
        KnnVectorQuery vectorQuery = new KnnVectorQuery(VECTOR_FIELD, queryVectorArray, k);
        
        // 2. Keyword search (BM25)
        Query keywordQuery = null;
        if (queryText != null && !queryText.trim().isEmpty()) {
            try {
                QueryParser parser = new QueryParser(CONTENT_FIELD, analyzer);
                keywordQuery = parser.parse(QueryParserBase.escape(queryText));
            } catch (Exception e) {
                log.warn("Failed to parse keyword query, using vector search only", e);
            }
        }
        
        // 3. Apply entity type filter
        Query filterQuery = null;
        if (request.getEntityType() != null && !request.getEntityType().trim().isEmpty()) {
            filterQuery = new TermQuery(new Term(ENTITY_TYPE_FIELD, request.getEntityType()));
        }
        
        // 4. Perform searches
        TopDocs vectorResults = null;
        TopDocs keywordResults = null;
        
        if (filterQuery != null) {
            BooleanQuery.Builder vectorBoolBuilder = new BooleanQuery.Builder();
            vectorBoolBuilder.add(vectorQuery, BooleanClause.Occur.MUST);
            vectorBoolBuilder.add(filterQuery, BooleanClause.Occur.FILTER);
            vectorResults = indexSearcher.search(vectorBoolBuilder.build(), k);
            
            if (keywordQuery != null) {
                BooleanQuery.Builder keywordBoolBuilder = new BooleanQuery.Builder();
                keywordBoolBuilder.add(keywordQuery, BooleanClause.Occur.MUST);
                keywordBoolBuilder.add(filterQuery, BooleanClause.Occur.FILTER);
                keywordResults = indexSearcher.search(keywordBoolBuilder.build(), k);
            }
        } else {
            vectorResults = indexSearcher.search(vectorQuery, k);
            if (keywordQuery != null) {
                keywordResults = indexSearcher.search(keywordQuery, k);
            }
        }
        
        // 5. RRF Fusion
        List<Map<String, Object>> fusedResults = performRRF(
            vectorResults != null ? vectorResults.scoreDocs : new ScoreDoc[0],
            keywordResults != null ? keywordResults.scoreDocs : new ScoreDoc[0],
            request.getLimit(),
            request.getThreshold()
        );
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        return AISearchResponse.builder()
            .results(fusedResults)
            .totalResults(fusedResults.size())
            .maxScore(fusedResults.isEmpty() ? 0.0 : 
                (Double) fusedResults.get(0).get("similarity"))
            .processingTimeMs(processingTime)
            .requestId(UUID.randomUUID().toString())
            .query(request.getQuery())
            .model(config.resolveEmbeddingDefaults().model())
            .build();
            
    } catch (Exception e) {
        log.error("Error performing hybrid search in Lucene", e);
        throw new AIServiceException("Failed to perform hybrid search", e);
    }
}

@Override
public AISearchResponse keywordSearch(String queryText, AISearchRequest request) {
    try {
        log.debug("Performing keyword search in Lucene using BM25");
        
        long startTime = System.currentTimeMillis();
        
        if (queryText == null || queryText.trim().isEmpty()) {
            return AISearchResponse.builder()
                .results(Collections.emptyList())
                .totalResults(0)
                .maxScore(0.0)
                .processingTimeMs(0L)
                .requestId(UUID.randomUUID().toString())
                .query(request.getQuery())
                .model(config.resolveEmbeddingDefaults().model())
                .build();
        }
        
        int k = Math.min(request.getLimit() * 2, maxResults * 2);
        
        // Parse keyword query
        QueryParser parser = new QueryParser(CONTENT_FIELD, analyzer);
        Query keywordQuery = parser.parse(QueryParserBase.escape(queryText));
        
        // Apply entity type filter
        Query filterQuery = null;
        if (request.getEntityType() != null && !request.getEntityType().trim().isEmpty()) {
            filterQuery = new TermQuery(new Term(ENTITY_TYPE_FIELD, request.getEntityType()));
        }
        
        TopDocs topDocs;
        if (filterQuery != null) {
            BooleanQuery.Builder boolBuilder = new BooleanQuery.Builder();
            boolBuilder.add(keywordQuery, BooleanClause.Occur.MUST);
            boolBuilder.add(filterQuery, BooleanClause.Occur.FILTER);
            topDocs = indexSearcher.search(boolBuilder.build(), k);
        } else {
            topDocs = indexSearcher.search(keywordQuery, k);
        }
        
        // Process results
        List<Map<String, Object>> results = new ArrayList<>();
        double threshold = request.getThreshold() != null ? request.getThreshold() : 0.0;
        
        for (ScoreDoc hit : topDocs.scoreDocs) {
            // BM25 score is already normalized by Lucene
            double score = hit.score;
            if (score < threshold) {
                continue;
            }
            
            Document doc = indexSearcher.doc(hit.doc);
            Map<String, Object> result = new HashMap<>();
            result.put("id", doc.get(ENTITY_ID_FIELD));
            result.put("vectorId", doc.get(VECTOR_ID_FIELD));
            result.put("content", doc.get(CONTENT_FIELD));
            result.put("entityType", doc.get(ENTITY_TYPE_FIELD));
            result.put("metadata", doc.get("metadata"));
            result.put("score", score);
            result.put("similarity", score);  // BM25 score as similarity
            
            results.add(result);
            
            if (results.size() >= request.getLimit()) {
                break;
            }
        }
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        return AISearchResponse.builder()
            .results(results)
            .totalResults(results.size())
            .maxScore(results.isEmpty() ? 0.0 : (Double) results.get(0).get("similarity"))
            .processingTimeMs(processingTime)
            .requestId(UUID.randomUUID().toString())
            .query(request.getQuery())
            .model(config.resolveEmbeddingDefaults().model())
            .build();
            
    } catch (Exception e) {
        log.error("Error performing keyword search in Lucene", e);
        throw new AIServiceException("Failed to perform keyword search", e);
    }
}

/**
 * Perform Reciprocal Rank Fusion (RRF) to combine vector and keyword search results.
 * 
 * RRF formula: score = 1/(k + rank) for each result set, then sum scores
 * 
 * @param vectorDocs vector search results
 * @param keywordDocs keyword search results
 * @param limit maximum number of results to return
 * @param threshold minimum similarity threshold
 * @return fused results sorted by RRF score
 */
private List<Map<String, Object>> performRRF(
        ScoreDoc[] vectorDocs,
        ScoreDoc[] keywordDocs,
        Integer limit,
        Double threshold) {
    
    // RRF constant (typically 60)
    final int RRF_K = 60;
    
    // Map document IDs to RRF scores
    Map<Integer, Double> rrfScores = new HashMap<>();
    Map<Integer, ScoreDoc> vectorDocMap = new HashMap<>();
    Map<Integer, ScoreDoc> keywordDocMap = new HashMap<>();
    
    // Add vector search scores
    for (int i = 0; i < vectorDocs.length; i++) {
        int docId = vectorDocs[i].doc;
        double rrfScore = 1.0 / (RRF_K + i + 1);
        rrfScores.merge(docId, rrfScore, Double::sum);
        vectorDocMap.put(docId, vectorDocs[i]);
    }
    
    // Add keyword search scores
    for (int i = 0; i < keywordDocs.length; i++) {
        int docId = keywordDocs[i].doc;
        double rrfScore = 1.0 / (RRF_K + i + 1);
        rrfScores.merge(docId, rrfScore, Double::sum);
        keywordDocMap.put(docId, keywordDocs[i]);
    }
    
    // Sort by RRF score (descending)
    List<Map.Entry<Integer, Double>> sorted = rrfScores.entrySet().stream()
        .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
        .collect(Collectors.toList());
    
    // Build results
    List<Map<String, Object>> results = new ArrayList<>();
    int maxResults = limit != null ? limit : 10;
    
    for (Map.Entry<Integer, Double> entry : sorted) {
        if (results.size() >= maxResults) {
            break;
        }
        
        int docId = entry.getKey();
        double rrfScore = entry.getValue();
        
        // Apply threshold (if specified)
        if (threshold != null && rrfScore < threshold) {
            continue;
        }
        
        try {
            Document doc = indexSearcher.doc(docId);
            
            // Get original scores for reference
            ScoreDoc vectorDoc = vectorDocMap.get(docId);
            ScoreDoc keywordDoc = keywordDocMap.get(docId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("id", doc.get(ENTITY_ID_FIELD));
            result.put("vectorId", doc.get(VECTOR_ID_FIELD));
            result.put("content", doc.get(CONTENT_FIELD));
            result.put("entityType", doc.get(ENTITY_TYPE_FIELD));
            result.put("metadata", doc.get("metadata"));
            
            // Use RRF score as primary score
            result.put("score", rrfScore);
            result.put("similarity", rrfScore);
            
            // Include original scores for debugging
            if (vectorDoc != null) {
                result.put("vectorScore", vectorDoc.score);
            }
            if (keywordDoc != null) {
                result.put("keywordScore", keywordDoc.score);
            }
            
            results.add(result);
            
        } catch (Exception e) {
            log.warn("Failed to retrieve document {} for RRF result", docId, e);
        }
    }
    
    return results;
}
```

**Additional Changes for Lucene**:

1. **Ensure content is indexed for keyword search**:
```java
// In storeVector method, ensure content field is indexed
Document document = new Document();
// ... existing fields ...
document.add(new TextField(CONTENT_FIELD, content, Field.Store.YES));  // ← Add this
```

2. **Set BM25 similarity**:
```java
// In @PostConstruct or initialization
IndexWriterConfig config = new IndexWriterConfig(analyzer);
config.setSimilarity(new BM25Similarity());  // ← Use BM25 for keyword search
```

---

#### Step 2.4: Other Providers (Fallback)

**WeaviateVectorDatabaseService, MilvusVectorDatabaseService, InMemoryVectorDatabaseService**:

**Default behavior** (no changes needed):
- `supportsHybridSearch()` returns `false` (default)
- `hybridSearch()` falls back to `search()` (default)
- `keywordSearch()` throws `UnsupportedOperationException` (default)

**Optional**: Implement if provider supports it:
- Weaviate: Has native hybrid search
- Milvus: May support hybrid search in newer versions

---

### Phase 3: Update RAGService

#### Step 3.1: Update performHybridSearch Method

**File**: `ai-infrastructure-rag/src/main/java/com/ai/infrastructure/rag/service/RAGService.java`

**Changes**:
```java
private AISearchResponse performHybridSearch(List<Double> queryVector, String queryText, 
        AISearchRequest request) {
    
    // Check if vector database supports hybrid search
    if (vectorDatabase instanceof VectorDatabaseService) {
        VectorDatabaseService vectorDbService = (VectorDatabaseService) vectorDatabase;
        
        if (vectorDbService.supportsHybridSearch()) {
            // Use native hybrid search
            log.debug("Using native hybrid search from vector database");
            return vectorDbService.hybridSearch(queryVector, queryText, request);
        } else {
            log.debug("Vector database doesn't support hybrid search, using vector search only");
        }
    }
    
    // Fallback: use vector search only
    log.warn("Hybrid search not supported, falling back to vector search");
    return vectorDatabase.search(queryVector, request);
}
```

**Note**: Need to check if `vectorDatabase` field is `VectorDatabase` or `VectorDatabaseService`. Let me check:

Looking at RAGService, it uses `VectorDatabase` interface, not `VectorDatabaseService`. Need to update this.

---

#### Step 3.2: Update RAGService Dependencies

**File**: `RAGService.java`

**Current dependencies**:
```java
private final VectorDatabase vectorDatabase;
private final VectorDatabaseService vectorDatabaseService;
```

**Update**: Use `VectorDatabaseService` for hybrid search:
```java
private AISearchResponse performHybridSearch(List<Double> queryVector, String queryText, 
        AISearchRequest request) {
    
    // Use VectorDatabaseService for hybrid search capability
    if (vectorDatabaseService.supportsHybridSearch()) {
        log.debug("Using native hybrid search from vector database");
        return vectorDatabaseService.hybridSearch(queryVector, queryText, request);
    }
    
    // Fallback: use vector search only
    log.warn("Hybrid search not supported, falling back to vector search");
    return vectorDatabaseService.search(queryVector, request);
}
```

---

#### Step 3.3: Update performContextualSearch Method

**File**: `RAGService.java`

**Changes**:
```java
private AISearchResponse performContextualSearch(List<Double> queryVector, String context, 
        AISearchRequest request) {
    
    // Contextual search: use hybrid search if available, otherwise vector search
    // Context is passed in request metadata for filtering
    
    if (vectorDatabaseService.supportsHybridSearch()) {
        // Use hybrid search with context
        AISearchRequest contextualRequest = AISearchRequest.builder()
            .from(request)
            .context(context)  // Add context to request
            .build();
        
        // Extract query text from context or use empty
        String queryText = extractQueryFromContext(context);
        
        return vectorDatabaseService.hybridSearch(queryVector, queryText, contextualRequest);
    }
    
    // Fallback: use vector search with context in metadata
    AISearchRequest contextualRequest = AISearchRequest.builder()
        .from(request)
        .context(context)
        .build();
    
    return vectorDatabaseService.search(queryVector, contextualRequest);
}

/**
 * Extract query text from context if available.
 */
private String extractQueryFromContext(String context) {
    // Simple extraction - in production, use more sophisticated parsing
    if (context == null || context.trim().isEmpty()) {
        return "";
    }
    
    // Try to extract query from context (heuristic)
    // This is a simplified implementation
    return context;
}
```

---

#### Step 3.4: Remove Stub Comments

**File**: `RAGService.java`

**Update JavaDoc**:
```java
/**
 * Perform hybrid search combining vector similarity and keyword search.
 * 
 * <p>This method delegates to the vector database's hybrid search implementation
 * if supported, otherwise falls back to vector search only.</p>
 * 
 * @param queryVector the query vector for semantic search
 * @param queryText the query text for keyword search
 * @param request the search request
 * @return hybrid search results
 */
private AISearchResponse performHybridSearch(...) {
    // Implementation above
}
```

---

### Phase 4: Update VectorSearchService (Optional)

#### Step 4.1: Update hybridSearch Method

**File**: `ai-infrastructure-core/src/main/java/com/ai/infrastructure/search/VectorSearchService.java`

**Changes**:
```java
/**
 * Perform hybrid search combining vector similarity and text matching
 * 
 * Delegates to VectorDatabaseService.hybridSearch() if supported.
 */
public AISearchResponse hybridSearch(List<Double> queryVector, String queryText, AISearchRequest request) {
    try {
        log.debug("Performing hybrid search for query: {}", queryText);
        
        long startTime = System.currentTimeMillis();
        
        // Use VectorDatabaseService hybrid search if supported
        if (vectorDatabaseService.supportsHybridSearch()) {
            AISearchResponse response = vectorDatabaseService.hybridSearch(queryVector, queryText, request);
            log.debug("Hybrid search completed in {}ms", response.getProcessingTimeMs());
            return response;
        }
        
        // Fallback: perform vector search only
        log.warn("Hybrid search not supported, falling back to vector search");
        AISearchResponse response = vectorDatabaseService.search(queryVector, request);
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        return AISearchResponse.builder()
            .from(response)
            .processingTimeMs(processingTime)
            .build();
            
    } catch (Exception e) {
        log.error("Error performing hybrid search", e);
        throw new AIServiceException("Failed to perform hybrid search", e);
    }
}
```

---

## Complete Code Changes Summary

### Files to Modify

1. **VectorDatabaseService.java** (Interface)
   - Add `hybridSearch()` default method
   - Add `keywordSearch()` default method
   - Add `supportsHybridSearch()` default method
   - Add `supportsKeywordSearch()` default method

2. **PineconeVectorDatabaseService.java**
   - Override `supportsHybridSearch()` → return `true`
   - Override `supportsKeywordSearch()` → return `true`
   - Override `hybridSearch()` → use Pinecone native API
   - Override `keywordSearch()` → use sparse vector

3. **QdrantVectorDatabaseService.java**
   - Override `supportsHybridSearch()` → return `true`
   - Override `supportsKeywordSearch()` → return `true`
   - Override `hybridSearch()` → use Qdrant native API
   - Override `keywordSearch()` → use Qdrant full-text API

4. **LuceneVectorDatabaseService.java**
   - Override `supportsHybridSearch()` → return `true`
   - Override `supportsKeywordSearch()` → return `true`
   - Override `hybridSearch()` → implement RRF fusion
   - Override `keywordSearch()` → implement BM25 search
   - Add `performRRF()` helper method
   - Ensure content field is indexed

5. **RAGService.java**
   - Update `performHybridSearch()` → use `VectorDatabaseService.hybridSearch()`
   - Update `performContextualSearch()` → use hybrid search if available
   - Remove stub comments

6. **VectorSearchService.java** (Optional)
   - Update `hybridSearch()` → delegate to `VectorDatabaseService.hybridSearch()`

---

## Testing Strategy

### Unit Tests

#### Test VectorDatabaseService Interface

**File**: `VectorDatabaseServiceTest.java`

**Test Cases**:
1. ✅ Default `hybridSearch()` falls back to `search()`
2. ✅ Default `keywordSearch()` throws `UnsupportedOperationException`
3. ✅ Default `supportsHybridSearch()` returns `false`
4. ✅ Default `supportsKeywordSearch()` returns `false`

---

#### Test Pinecone Implementation

**File**: `PineconeVectorDatabaseServiceTest.java`

**Test Cases**:
1. ✅ `supportsHybridSearch()` returns `true`
2. ✅ `supportsKeywordSearch()` returns `true`
3. ✅ `hybridSearch()` calls Pinecone API with vector + sparseVector
4. ✅ `hybridSearch()` returns combined results
5. ✅ `keywordSearch()` uses sparse vector only
6. ✅ Falls back gracefully on errors

---

#### Test Qdrant Implementation

**File**: `QdrantVectorDatabaseServiceTest.java`

**Test Cases**:
1. ✅ `supportsHybridSearch()` returns `true`
2. ✅ `supportsKeywordSearch()` returns `true`
3. ✅ `hybridSearch()` calls Qdrant API with vector + query_text
4. ✅ `hybridSearch()` returns combined results
5. ✅ `keywordSearch()` uses query_text only
6. ✅ Falls back gracefully on errors

---

#### Test Lucene Implementation

**File**: `LuceneVectorDatabaseServiceTest.java`

**Test Cases**:
1. ✅ `supportsHybridSearch()` returns `true`
2. ✅ `supportsKeywordSearch()` returns `true`
3. ✅ `hybridSearch()` performs both vector and keyword search
4. ✅ `hybridSearch()` combines results with RRF
5. ✅ RRF scores are calculated correctly
6. ✅ `keywordSearch()` performs BM25 search
7. ✅ Results are sorted by score
8. ✅ Threshold filtering works

---

#### Test RAGService Updates

**File**: `RAGServiceTest.java`

**Test Cases**:
1. ✅ `performHybridSearch()` uses `VectorDatabaseService.hybridSearch()` if supported
2. ✅ `performHybridSearch()` falls back to vector search if not supported
3. ✅ `performContextualSearch()` uses hybrid search if available
4. ✅ Logs appropriate messages

---

### Integration Tests

**File**: `HybridSearchIntegrationTest.java`

**Test Cases**:
1. ✅ Pinecone hybrid search returns better results than vector-only
2. ✅ Qdrant hybrid search returns better results than vector-only
3. ✅ Lucene RRF fusion combines vector + keyword results correctly
4. ✅ Hybrid search improves recall for keyword-heavy queries
5. ✅ Performance is acceptable (<500ms for hybrid search)

---

## Migration Plan

### Step 1: Extend Interface (Week 1)

**Impact**: Low - Only adds default methods, backward compatible.

**Actions**:
1. Add optional methods to `VectorDatabaseService`
2. Add default implementations
3. Update JavaDoc
4. Run existing tests - should all pass

**Testing**: Verify all existing tests pass.

---

### Step 2: Implement Pinecone (Week 1-2)

**Impact**: Low - New implementation, doesn't affect existing code.

**Actions**:
1. Implement `hybridSearch()` using Pinecone API
2. Implement `keywordSearch()` using sparse vectors
3. Add tests
4. Verify with Pinecone documentation

**Testing**: Unit tests + integration tests with Pinecone.

---

### Step 3: Implement Qdrant (Week 2)

**Impact**: Low - New implementation.

**Actions**:
1. Implement `hybridSearch()` using Qdrant API
2. Implement `keywordSearch()` using Qdrant full-text
3. Add tests
4. Verify with Qdrant documentation

**Testing**: Unit tests + integration tests with Qdrant.

---

### Step 4: Implement Lucene RRF (Week 2-3)

**Impact**: Medium - More complex implementation.

**Actions**:
1. Implement `hybridSearch()` with RRF fusion
2. Implement `keywordSearch()` with BM25
3. Ensure content field is indexed
4. Add RRF algorithm
5. Add tests

**Testing**: Unit tests + integration tests.

---

### Step 5: Update RAGService (Week 3)

**Impact**: Medium - Changes existing methods.

**Actions**:
1. Update `performHybridSearch()` to use new interface
2. Update `performContextualSearch()` to use new interface
3. Remove stub comments
4. Update tests

**Testing**: 
- Verify existing tests still pass
- Add new tests for hybrid search

---

### Step 6: Testing & Documentation (Week 4)

**Impact**: Low - Testing and docs.

**Actions**:
1. Run full test suite
2. Performance testing
3. Update documentation
4. Create migration guide

**Testing**: Full integration testing.

---

## Configuration

### No Configuration Needed

**Rationale**: Hybrid search is automatically available if provider supports it. No configuration needed - it's capability-based.

**Optional Configuration** (future enhancement):
```yaml
ai:
  vector-db:
    hybrid-search:
      enabled: true  # Enable hybrid search (default: true if supported)
      rrf-k: 60      # RRF constant for Lucene (default: 60)
```

---

## Error Handling

### Provider Doesn't Support Hybrid Search

**Behavior**: Falls back to vector search automatically.

**Code**:
```java
// Default implementation handles this
default AISearchResponse hybridSearch(...) {
    return search(queryVector, request);  // Fallback
}
```

---

### Hybrid Search Fails

**Behavior**: Log error, fall back to vector search.

**Code** (in provider implementations):
```java
@Override
public AISearchResponse hybridSearch(...) {
    try {
        // Native hybrid search
        return performNativeHybridSearch(...);
    } catch (Exception e) {
        log.error("Hybrid search failed, falling back to vector search", e);
        return search(queryVector, request);  // Fallback
    }
}
```

---

### Keyword Search Not Supported

**Behavior**: Throws `UnsupportedOperationException` (by default).

**Code**:
```java
// Applications should check supportsKeywordSearch() first
if (vectorDbService.supportsKeywordSearch()) {
    return vectorDbService.keywordSearch(queryText, request);
} else {
    // Handle gracefully
}
```

---

## Performance Considerations

### Hybrid Search Performance

**Expected Latency**:
- Pinecone: ~100-200ms (native API)
- Qdrant: ~100-200ms (native API)
- Lucene: ~200-500ms (RRF fusion, two searches)

**Optimization**:
- Cache results when possible
- Use parallel execution for Lucene (vector + keyword in parallel)
- Limit result sets before fusion

---

### RRF Algorithm Performance

**Lucene RRF Implementation**:
- Two searches: vector + keyword (can be parallel)
- RRF fusion: O(n log n) where n = result count
- Memory: O(n) for score maps

**Optimization**:
- Limit searches to top K before fusion
- Use efficient data structures
- Consider caching

---

## Monitoring

### Metrics to Track

1. **Hybrid Search Usage**:
   - Count of hybrid search requests
   - Count of fallback to vector search
   - Provider distribution

2. **Performance**:
   - Average latency (hybrid vs vector)
   - P95/P99 latency

3. **Quality**:
   - Result quality improvement (if measurable)
   - Recall improvement

---

## Success Criteria

### Functional

- [ ] `VectorDatabaseService` interface extended with optional methods
- [ ] Pinecone implements native hybrid search
- [ ] Qdrant implements native hybrid search
- [ ] Lucene implements RRF fusion
- [ ] RAGService uses hybrid search when available
- [ ] Fallback works when hybrid search not supported
- [ ] All existing tests pass

---

### Performance

- [ ] Hybrid search latency acceptable (<500ms for Lucene, <200ms for Pinecone/Qdrant)
- [ ] No performance regression for vector-only search
- [ ] RRF fusion efficient (O(n log n))

---

### Quality

- [ ] Hybrid search improves recall for keyword-heavy queries
- [ ] RRF fusion correctly combines results
- [ ] Results are properly sorted

---

## Risks and Mitigation

### Risk 1: API Compatibility

**Risk**: Pinecone/Qdrant APIs may differ from expected format.

**Mitigation**:
- Verify API documentation
- Test with actual services
- Handle API version differences
- Provide clear error messages

---

### Risk 2: RRF Implementation Complexity

**Risk**: Lucene RRF implementation may be complex or buggy.

**Mitigation**:
- Thorough testing
- Reference RRF algorithm documentation
- Start with simple implementation, optimize later
- Add unit tests for RRF logic

---

### Risk 3: Performance Impact

**Risk**: Hybrid search slower than vector-only.

**Mitigation**:
- Make it optional (capability-based)
- Optimize RRF algorithm
- Use parallel execution where possible
- Cache results

---

## Future Enhancements

### Enhancement 1: Weighted Hybrid Search

**Idea**: Allow configuration of vector vs keyword weight.

**Implementation**:
```java
default AISearchResponse hybridSearch(
    List<Double> queryVector, 
    String queryText, 
    AISearchRequest request,
    double vectorWeight,  // NEW parameter
    double keywordWeight) {
    // ...
}
```

---

### Enhancement 2: Multiple RRF Strategies

**Idea**: Support different RRF variants (RRF, weighted RRF, etc.).

**Implementation**:
```java
enum RRFStrategy {
    STANDARD,  // k=60
    WEIGHTED,  // Custom weights
    ADAPTIVE   // Learn weights
}
```

---

### Enhancement 3: Query Understanding

**Idea**: Automatically determine when to use hybrid vs vector-only.

**Implementation**: Analyze query to determine if keyword search would help.

---

## Conclusion

This implementation plan provides a clear path to add hybrid search support to the vector database abstraction while maintaining backward compatibility and leveraging native provider capabilities.

**Key Benefits**:
- ✅ Leverages native provider features (Pinecone, Qdrant)
- ✅ Custom implementation for Lucene (RRF)
- ✅ Backward compatible (default implementations)
- ✅ Flexible (providers choose implementation)
- ✅ Consistent with existing SPI pattern

**Next Steps**:
1. Review and approve plan
2. Implement Phase 1 (extend interface)
3. Implement Phase 2 (provider implementations)
4. Implement Phase 3 (update RAGService)
5. Test and iterate
6. Document and release

