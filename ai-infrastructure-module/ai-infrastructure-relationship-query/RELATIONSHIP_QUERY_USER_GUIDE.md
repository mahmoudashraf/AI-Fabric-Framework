# Relationship Query Module - User Guide

## Overview

The Relationship Query Module is an LLM-powered intelligent query system that understands natural language questions about your data relationships and automatically generates optimized JPA/JPQL queries. It combines relationship traversal, semantic search, and smart fallback strategies to deliver accurate results—even when traditional querying falls short.

### What This Module Does

- **Natural Language to JPQL**: Convert plain English queries into optimized database queries
- **Relationship-Aware**: Automatically navigates complex JPA entity relationships
- **Hybrid Search**: Combines relational traversal with vector similarity when needed
- **Multi-Level Fallback**: Gracefully degrades through metadata traversal, vector search, and simple lookups
- **Intelligent Caching**: Caches plans, embeddings, and results for sub-millisecond responses
- **Production-Ready**: Validated queries, error handling, and comprehensive metrics

### Target Audience

Developers building RAG systems, knowledge bases, or applications that need to query complex relational data using natural language.

---

## Quick Start

### 1. Add the Dependency

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-relationship-query</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Configure (Optional)

The module works out-of-the-box with sensible defaults:

```yaml
ai:
  infrastructure:
    relationship:
      enabled: true  # default
      enable-vector-search: true
      fallback-to-metadata: true
      fallback-to-vector-search: true
```

### 3. Query Your Data

```java
@Autowired
private ReliableRelationshipQueryService queryService;

public void searchUserOrders() {
    // Natural language query
    String query = "Find all orders for users in the 'premium' tier placed in the last 30 days";
    
    // Execute query
    RAGResponse response = queryService.execute(
        query,
        List.of("order"),  // Primary entity type
        QueryOptions.defaults()
    );
    
    // Process results
    response.getDocuments().forEach(doc -> {
        System.out.printf("Order ID: %s%n", doc.getId());
        System.out.printf("Content: %s%n", doc.getContent());
    });
    
    System.out.printf("Found %d results%n", response.getTotalResults());
}
```

**Output:**
```
Order ID: order-123
Content: Premium tier order from 2024-12-15...
Order ID: order-456
Content: Premium tier order from 2024-12-20...
Found 2 results
```

**That's it.** Natural language queries converted to database results automatically.

---

## Core Concepts

### How It Works

```
┌─────────────────────────────────────────────────────┐
│  NATURAL LANGUAGE QUERY                              │
│  "Find orders for premium users from last month"    │
└────────┬────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  LLM QUERY PLANNER                                  │
│  🧠 Understands intent                              │
│  📊 Identifies entities & relationships             │
│  🎯 Plans traversal strategy                        │
│  Result: RelationshipQueryPlan                      │
└────────┬────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  JPQL QUERY BUILDER                                 │
│  🔧 Generates optimized JPQL                        │
│  🔗 Handles JOIN logic                              │
│  ⚡ Adds WHERE clauses                              │
│  Result: Executable JPA query                       │
└────────┬────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  EXECUTION WITH FALLBACK CHAIN                      │
│  1️⃣ JPA traversal (primary)                         │
│  2️⃣ Metadata traversal (fallback 1)                │
│  3️⃣ Vector search (fallback 2)                      │
│  4️⃣ Simple search (fallback 3)                      │
└────────┬────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  OPTIONAL VECTOR RERANKING                          │
│  🔍 Semantic similarity scoring                     │
│  📈 Re-orders results by relevance                  │
│  ✨ Enhanced mode for better accuracy               │
└────────┬────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  RAG RESPONSE                                       │
│  📄 Matched documents                               │
│  📊 Metadata & confidence                           │
│  ⚡ Performance metrics                             │
└─────────────────────────────────────────────────────┘
```

### Query Modes

**STANDALONE Mode** (Default):
- Pure relational traversal
- Fast and efficient
- No semantic search overhead
- Perfect for structured queries

**ENHANCED Mode**:
- Combines relational + semantic search
- Vector reranking for better relevance
- Auto-activated when query needs semantic understanding
- Best for complex, ambiguous queries

### Return Modes

**IDS Mode** (Default):
- Returns only entity IDs
- Minimal payload
- Fastest response
- Use when you'll fetch full entities separately

**FULL Mode**:
- Returns complete searchable content
- Includes metadata
- Ready for immediate use
- Best for RAG applications

---

## Configuration Reference

### Core Settings

```yaml
ai:
  infrastructure:
    relationship:
      # Module Toggle
      enabled: true                      # Enable/disable module (default: true)
      
      # Search Capabilities
      enable-vector-search: true         # Allow semantic search (default: true)
      fallback-to-metadata: true         # Metadata traversal fallback (default: true)
      fallback-to-vector-search: true    # Vector search fallback (default: true)
      fallback-to-simple-search: true    # Simple repository fallback (default: true)
      
      # Query Settings
      enable-query-validation: true      # Validate queries before execution (default: true)
      enable-query-caching: true         # Cache plans/results (default: true)
      query-cache-ttl-seconds: 3600      # Cache TTL (default: 1 hour)
      max-traversal-depth: 3             # Max relationship depth (default: 3)
      default-similarity-threshold: 0.7  # Vector search threshold (default: 0.7)
      
      # Defaults
      default-return-mode: IDS           # IDS or FULL (default: IDS)
      default-query-mode: STANDALONE     # STANDALONE or ENHANCED (default: STANDALONE)
```

### LLM Configuration

```yaml
ai:
  infrastructure:
    relationship:
      llm:
        model: "gpt-4"                   # LLM model (optional, uses default if empty)
        temperature: 0.1                 # Low temp for consistent planning (default: 0.1)
        max-retries: 3                   # Retry failed LLM calls (default: 3)
        timeout-seconds: 30              # LLM timeout (default: 30)
        min-confidence: 0.6              # Minimum plan confidence (default: 0.6)
```

### Planner Configuration

```yaml
ai:
  infrastructure:
    relationship:
      planner:
        log-plans: false                 # Log generated plans (default: false)
        min-confidence-to-execute: 0.55  # Minimum confidence to execute (default: 0.55)
        fail-on-parse-error: false       # Fail hard on LLM parse errors (default: false)
        max-retries: 0                   # Additional plan retries (default: 0)
```

### Schema Configuration

```yaml
ai:
  infrastructure:
    relationship:
      schema:
        auto-discover: true              # Auto-discover @AICapable entities (default: true)
        refresh-on-startup: true         # Rebuild schema cache on startup (default: true)
        log-schema: false                # Log discovered schema (default: false)
        include-fields: true             # Include field metadata (default: true)
```

### Cache Configuration

```yaml
ai:
  infrastructure:
    relationship:
      cache:
        enabled: true                    # Enable caching (default: true)
        plan:
          ttl-seconds: 3600              # Plan cache TTL (default: 1 hour)
          max-entries: 10000             # Max cached plans (default: 10K)
        embedding:
          ttl-seconds: 86400             # Embedding cache TTL (default: 24 hours)
          max-entries: 50000             # Max cached embeddings (default: 50K)
        result:
          ttl-seconds: 1800              # Result cache TTL (default: 30 min)
          max-entries: 5000              # Max cached results (default: 5K)
```

### Metrics Configuration

```yaml
ai:
  infrastructure:
    relationship:
      metrics:
        enabled: true                    # Enable metrics (default: true)
        latency-alert-ms: 1500           # Alert threshold for slow queries (default: 1500ms)
        fallback-alert-threshold: 5      # Alert after N fallbacks (default: 5)
```

---

## API Reference

### ReliableRelationshipQueryService

Primary service with multi-level fallback.

#### execute()

```java
RAGResponse execute(String query, List<String> entityTypes, @Nullable QueryOptions options)
```

**Parameters**:
- `query`: Natural language query
- `entityTypes`: List of entity types to search (e.g., `["order", "product"]`)
- `options`: Query options (nullable, uses defaults if null)

**Returns**: `RAGResponse` with matching documents

**Example**:

```java
RAGResponse response = queryService.execute(
    "Show me high-value customers who made purchases this month",
    List.of("customer"),
    QueryOptions.builder()
        .limit(25)
        .returnMode(ReturnMode.FULL)
        .similarityThreshold(0.8)
        .build()
);
```

### LLMDrivenJPAQueryService

Core service for direct LLM-driven queries (without fallbacks).

#### executeRelationshipQuery()

```java
RAGResponse executeRelationshipQuery(String query, List<String> entityTypes)
RAGResponse executeRelationshipQuery(String query, List<String> entityTypes, QueryOptions options)
```

**Example**:

```java
@Autowired
private LLMDrivenJPAQueryService jpaQueryService;

RAGResponse response = jpaQueryService.executeRelationshipQuery(
    "Find recent orders with status 'pending'",
    List.of("order")
);
```

### Query Options

```java
QueryOptions options = QueryOptions.builder()
    .forceMode(QueryMode.ENHANCED)       // Force ENHANCED mode
    .returnMode(ReturnMode.FULL)         // Return full content
    .limit(50)                           // Limit results
    .similarityThreshold(0.75)           // Vector search threshold
    .build();
```

**Available Options**:
- `forceMode`: Override mode detection (STANDALONE | ENHANCED)
- `returnMode`: Output format (IDS | FULL)
- `limit`: Maximum results
- `similarityThreshold`: Minimum similarity for vector search

### RAG Response Structure

```java
RAGResponse {
    String originalQuery;                // Original natural language query
    String entityType;                   // Primary entity type
    List<RAGDocument> documents;         // Matched documents
    Integer totalResults;                // Total matches found
    Integer returnedResults;             // Results returned (after limit)
    Boolean hybridSearchUsed;            // Whether vector search was used
    Boolean success;                     // Query successful
    Long processingTimeMs;               // Execution time
    Double confidenceScore;              // Plan confidence
    List<String> warnings;               // Any warnings
    Map<String, Object> metadata;        // Execution metadata
}
```

### RAG Document Structure

```java
RAGDocument {
    String id;                           // Entity ID
    String content;                      // Searchable content (FULL mode)
    Double score;                        // Relevance score
    Map<String, Object> metadata;        // Document metadata
    String source;                       // Source (e.g., "vector-fallback")
}
```

---

## Usage Examples

### Example 1: Simple Entity Query

```java
String query = "Find all products in the 'Electronics' category";

RAGResponse response = queryService.execute(
    query,
    List.of("product"),
    null  // Use defaults
);

System.out.printf("Found %d products%n", response.getTotalResults());
```

### Example 2: Relationship Traversal

```java
String query = "Show orders for customers in the 'VIP' tier with total > $1000";

RAGResponse response = queryService.execute(
    query,
    List.of("order"),
    QueryOptions.builder()
        .returnMode(ReturnMode.FULL)
        .limit(20)
        .build()
);

response.getDocuments().forEach(doc -> {
    System.out.printf("Order: %s%n", doc.getId());
    System.out.printf("Details: %s%n", doc.getContent());
});
```

### Example 3: Multi-Entity Query

```java
String query = "Find users who reviewed products they purchased";

RAGResponse response = queryService.execute(
    query,
    List.of("user", "review", "order"),
    QueryOptions.builder()
        .forceMode(QueryMode.ENHANCED)  // Use vector reranking
        .limit(50)
        .build()
);

System.out.printf("Mode used: %s%n", 
    response.getHybridSearchUsed() ? "ENHANCED" : "STANDALONE");
```

### Example 4: Semantic Search

```java
String query = "Documents about machine learning and AI applications";

RAGResponse response = queryService.execute(
    query,
    List.of("document"),
    QueryOptions.builder()
        .forceMode(QueryMode.ENHANCED)
        .similarityThreshold(0.8)  // High threshold for better quality
        .limit(10)
        .build()
);

response.getDocuments().forEach(doc -> {
    System.out.printf("Score: %.3f - %s%n", doc.getScore(), doc.getId());
});
```

### Example 5: With Caching

```java
// First call - executes full query
long start = System.currentTimeMillis();
RAGResponse response1 = queryService.execute(
    "Premium users with recent activity",
    List.of("user"),
    null
);
long duration1 = System.currentTimeMillis() - start;
System.out.printf("First call: %dms%n", duration1);

// Second call - cached (much faster)
start = System.currentTimeMillis();
RAGResponse response2 = queryService.execute(
    "Premium users with recent activity",
    List.of("user"),
    null
);
long duration2 = System.currentTimeMillis() - start;
System.out.printf("Cached call: %dms%n", duration2);
// Output: First call: 450ms
//         Cached call: 8ms
```

### Example 6: Error Handling

```java
try {
    RAGResponse response = queryService.execute(
        "Complex query with ambiguous intent",
        List.of("unknown-entity"),
        null
    );
    
    if (!response.getSuccess()) {
        System.err.println("Query failed but recovered via fallback");
        response.getWarnings().forEach(System.out::println);
    }
    
} catch (FallbackExhaustedException e) {
    System.err.println("All fallback strategies failed");
    System.err.println("Original query: " + e.getErrorContext().getOriginalQuery());
    System.err.println("Stage: " + e.getErrorContext().getExecutionStage());
}
```

---

## Fallback Chain

### How Fallbacks Work

When the primary LLM-driven query fails, the module automatically tries:

1. **Primary: LLM + JPA Traversal**
   - LLM plans the query
   - JPQL builder generates query
   - JPA executes traversal
   
2. **Fallback 1: Metadata Traversal**
   - Uses entity metadata instead of JPA
   - Navigates relationships via reflection
   - Works even if JPA relationships missing

3. **Fallback 2: Vector Search**
   - Generates embedding for query
   - Searches vector database
   - Returns semantically similar results

4. **Fallback 3: Simple Search**
   - Falls back to entity repository
   - Returns all entities of specified type
   - Limited by result limit

### Configuring Fallbacks

```yaml
ai:
  infrastructure:
    relationship:
      fallback-to-metadata: true        # Enable metadata fallback
      fallback-to-vector-search: true   # Enable vector fallback
      fallback-to-simple-search: true   # Enable simple fallback
```

**Disable all fallbacks** (strict mode):

```yaml
ai:
  infrastructure:
    relationship:
      fallback-to-metadata: false
      fallback-to-vector-search: false
      fallback-to-simple-search: false
```

**Results**: Throws `FallbackExhaustedException` if primary fails.

---

## Performance Optimization

### Caching Strategy

**Plan Caching**:
- Caches LLM-generated query plans
- Avoids repeated LLM calls for same query
- Default TTL: 1 hour

**Embedding Caching**:
- Caches generated embeddings
- Reused across similar queries
- Default TTL: 24 hours

**Result Caching**:
- Caches query results
- Fastest possible response
- Default TTL: 30 minutes

**Custom Cache Settings**:

```yaml
ai:
  infrastructure:
    relationship:
      cache:
        plan:
          ttl-seconds: 7200     # 2 hours
          max-entries: 5000
        embedding:
          ttl-seconds: 172800   # 48 hours
          max-entries: 100000
        result:
          ttl-seconds: 900      # 15 minutes
          max-entries: 2000
```

### Query Optimization Tips

**1. Be Specific with Entity Types**:

```java
// ❌ Less efficient
queryService.execute(query, List.of("user", "order", "product", "review"), null);

// ✅ More efficient
queryService.execute(query, List.of("order"), null);
```

**2. Use IDS Mode for Large Results**:

```java
// ❌ Slower for large result sets
QueryOptions.builder()
    .returnMode(ReturnMode.FULL)
    .limit(1000)
    .build();

// ✅ Faster
QueryOptions.builder()
    .returnMode(ReturnMode.IDS)
    .limit(1000)
    .build();
```

**3. Set Appropriate Limits**:

```java
// ❌ No limit = potential performance issues
QueryOptions.defaults();

// ✅ Reasonable limit
QueryOptions.builder()
    .limit(100)
    .build();
```

**4. Use STANDALONE for Structured Queries**:

```java
// ❌ ENHANCED mode has vector search overhead
QueryOptions.builder()
    .forceMode(QueryMode.ENHANCED)
    .build();

// ✅ STANDALONE is faster for structured data
QueryOptions.builder()
    .forceMode(QueryMode.STANDALONE)
    .build();
```

---

## Monitoring & Metrics

### Query Metrics

```java
@Autowired
private QueryMetrics queryMetrics;

public void printMetrics() {
    QueryMetrics.QueryMetricsSnapshot snapshot = queryMetrics.getSnapshot();
    
    System.out.printf("Total Queries: %d%n", snapshot.getTotalQueries());
    System.out.printf("Success Rate: %.2f%%%n", snapshot.getSuccessRate());
    System.out.printf("Avg Latency: %.0fms%n", snapshot.getAverageLatencyMs());
    System.out.printf("Cache Hit Rate: %.2f%%%n", snapshot.getCacheHitRate());
    
    snapshot.getModeDistribution().forEach((mode, count) -> {
        System.out.printf("Mode %s: %d queries%n", mode, count);
    });
}
```

### Cache Metrics

```java
QueryMetrics.CacheSnapshot cacheSnapshot = queryMetrics.getCacheSnapshot();

System.out.printf("Plan Cache: %d hits, %d misses (%.2f%% hit rate)%n",
    cacheSnapshot.getPlanHits(),
    cacheSnapshot.getPlanMisses(),
    cacheSnapshot.getPlanHitRate());

System.out.printf("Embedding Cache: %d hits, %d misses (%.2f%% hit rate)%n",
    cacheSnapshot.getEmbeddingHits(),
    cacheSnapshot.getEmbeddingMisses(),
    cacheSnapshot.getEmbeddingHitRate());
```

### Latency Alerts

```yaml
ai:
  infrastructure:
    relationship:
      metrics:
        latency-alert-ms: 1000  # Alert on queries > 1 second
```

Logs warning when queries exceed threshold:
```
WARN: Query exceeded latency threshold: 1250ms > 1000ms
```

---

## Testing

### Unit Testing

```java
@SpringBootTest
class RelationshipQueryTest {
    
    @Autowired
    private ReliableRelationshipQueryService queryService;
    
    @Test
    void shouldFindOrdersByCustomerTier() {
        // Given
        String query = "Find orders for premium customers";
        
        // When
        RAGResponse response = queryService.execute(
            query,
            List.of("order"),
            QueryOptions.defaults()
        );
        
        // Then
        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getTotalResults()).isGreaterThan(0);
        assertThat(response.getDocuments()).isNotEmpty();
    }
    
    @Test
    void shouldUseCache() {
        // Given
        String query = "Recent orders";
        
        // When - first call
        long start1 = System.currentTimeMillis();
        queryService.execute(query, List.of("order"), null);
        long duration1 = System.currentTimeMillis() - start1;
        
        // When - second call (cached)
        long start2 = System.currentTimeMillis();
        queryService.execute(query, List.of("order"), null);
        long duration2 = System.currentTimeMillis() - start2;
        
        // Then - cached call should be much faster
        assertThat(duration2).isLessThan(duration1 / 5);
    }
}
```

---

## Troubleshooting

### Issue: "All fallback strategies failed"

**Symptoms**: `FallbackExhaustedException` thrown

**Diagnosis**:
```java
catch (FallbackExhaustedException e) {
    System.err.println("Query: " + e.getErrorContext().getOriginalQuery());
    System.err.println("Entity: " + e.getErrorContext().getPrimaryEntityType());
    System.err.println("Stage: " + e.getErrorContext().getExecutionStage());
}
```

**Solutions**:
1. Check entity type exists and is indexed
2. Verify @AICapable annotation on entity
3. Enable more fallbacks
4. Check vector database connectivity

### Issue: Slow queries

**Symptoms**: Queries taking > 2 seconds

**Diagnosis**:
```java
QueryMetrics.QueryMetricsSnapshot metrics = queryMetrics.getSnapshot();
System.out.printf("Avg latency: %.0fms%n", metrics.getAverageLatencyMs());
System.out.printf("Cache hit rate: %.2f%%%n", metrics.getCacheHitRate());
```

**Solutions**:
1. Enable caching
2. Use STANDALONE mode
3. Set result limits
4. Use IDS return mode

### Issue: Inaccurate results

**Symptoms**: Wrong entities returned

**Solutions**:
1. Switch to ENHANCED mode for semantic understanding
2. Increase similarity threshold
3. Be more specific in query
4. Verify entity relationships are correct

### Issue: Cache not working

**Diagnosis**:
```yaml
ai:
  infrastructure:
    relationship:
      cache:
        enabled: true  # Verify enabled
```

**Solution**: Check cache is enabled in configuration

---

## Best Practices

### ✅ DO

- **Use specific entity types** when you know what you're looking for
- **Enable caching** in production for better performance
- **Set reasonable limits** to avoid large result sets
- **Monitor metrics** to optimize query patterns
- **Use STANDALONE mode** for structured queries
- **Use ENHANCED mode** for semantic/ambiguous queries
- **Test with real data** before production deployment

### ❌ DON'T

- Don't disable all fallbacks unless necessary
- Don't request unlimited results
- Don't use FULL mode for large datasets
- Don't ignore warnings in response
- Don't bypass QueryOptions validation
- Don't cache forever (set appropriate TTLs)

---

## FAQ

**Q: How does this differ from traditional search?**
A: Traditional search requires exact queries. This module understands natural language and relationships.

**Q: What LLM models are supported?**
A: Any model supported by AI Core Service (OpenAI, Anthropic, Azure, etc.)

**Q: Can I use without vector search?**
A: Yes. Disable with `enable-vector-search: false`. Module uses pure relational queries.

**Q: How accurate are the results?**
A: Very accurate for structured queries. Use ENHANCED mode for ambiguous queries.

**Q: What's the performance impact?**
A: First query: 200-500ms. Cached queries: 5-20ms.

**Q: Can I customize the LLM prompt?**
A: Currently prompts are optimized internally. Future versions may support custom prompts.

**Q: Does it work with any JPA entity?**
A: Yes, as long as entities are annotated with `@AICapable` and have relationships defined.

**Q: What happens if LLM is unavailable?**
A: Fallback chain activates automatically. Metadata/vector/simple search takes over.

---

## Version Information

- **Module Version**: 1.0.0
- **Minimum Java**: 17
- **Spring Boot**: 3.x
- **Dependencies**: ai-infrastructure-core, ai-infrastructure-vector (optional)

---

## Support & Resources

- **Source Code**: `com.ai.infrastructure.relationship`
- **Main Service**: `ReliableRelationshipQueryService.java`
- **Configuration**: `RelationshipQueryProperties.java`
- **Examples**: Integration tests in `src/test/java`

---

*This guide reflects the actual implementation in the codebase. For framework-wide features, refer to the main AI Infrastructure documentation.*

