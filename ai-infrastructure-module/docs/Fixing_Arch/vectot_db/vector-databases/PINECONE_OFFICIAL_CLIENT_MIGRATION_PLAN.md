# Pinecone Official Java Client Migration Plan

## Overview
This document outlines the comprehensive plan to migrate from the current REST API implementation to the official Pinecone Java client (`io.pinecone:pinecone-client`).

## Document Status
**Last Updated**: 2026-01-11
**Version**: 1.0
**Status**: Initial Implementation Plan

## Current State Analysis

### Current Implementation
- **Approach**: Manual REST API calls using Spring `RestTemplate`
- **File**: `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-pinecone/src/main/java/com/ai/infrastructure/vector/pinecone/PineconeVectorDatabaseService.java`
- **Dependencies**: `spring-web`, `jackson-databind`
- **Protocol**: HTTPS REST API with API key authentication

### Why Migrate to Official Client?

1. **Simplified API**: Strongly-typed methods instead of manual JSON serialization
2. **Built-in Features**: Connection pooling, retries, error handling
3. **Type Safety**: Compile-time checking instead of runtime errors
4. **Better Performance**: Optimized client with connection reuse
5. **Official Support**: Maintained by Pinecone team with guaranteed compatibility
6. **gRPC Support**: Official client supports both HTTP and gRPC protocols
7. **Easier Testing**: Better mock support for unit tests

## Migration Plan

### Phase 1: Add Official Client Dependency

#### 1.1 Add Official Client Dependency
**File**: `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-pinecone/pom.xml`

```xml
<dependency>
    <groupId>io.pinecone</groupId>
    <artifactId>pinecone-client</artifactId>
    <version>2.0.0</version> <!-- Check for latest version -->
</dependency>
```

**IMPORTANT NOTES**:
1. **Official Java SDK**: Pinecone provides an official Java/Kotlin SDK
2. **gRPC Support**: The client supports both REST and gRPC protocols
3. **Version Compatibility**: Ensure client version matches your Pinecone cloud deployment
4. **Dependencies**: The client uses OkHttp for HTTP and gRPC for performance

**Alternative - Pinecone Spark Connector** (for big data scenarios):
```xml
<!-- Only if using Spark for large-scale operations -->
<dependency>
    <groupId>io.pinecone</groupId>
    <artifactId>pinecone-spark-connector_2.12</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### 1.2 Remove/Keep REST Dependencies
- **Keep**: `spring-web` (may be used elsewhere)
- **Keep**: `jackson-databind` (for metadata serialization if needed)

### Phase 2: Client Initialization

#### 2.1 Create Pinecone Client Bean

**Current**:
```java
private final RestTemplate restTemplate;
private final ObjectMapper objectMapper = new ObjectMapper();
private URI baseUri;

@PostConstruct
void initializeClient() {
    this.baseUri = URI.create(resolveBaseUrl());
    log.info("Pinecone client configured for index '{}'", config.getPinecone().getIndexName());
}
```

**New**:
```java
import io.pinecone.clients.Pinecone;
import io.pinecone.clients.Index;
import jakarta.annotation.PreDestroy;

private final Pinecone pinecone;
private final Index index;

public PineconeVectorDatabaseService(AIProviderConfig providerConfig) {
    this.config = Objects.requireNonNull(providerConfig.getPinecone(), "Pinecone configuration must be present");

    try {
        // Initialize Pinecone client
        this.pinecone = new Pinecone.Builder(config.getApiKey())
            .build();

        // Get index reference
        String indexName = Optional.ofNullable(config.getIndexName())
            .orElseThrow(() -> new AIServiceException("Pinecone index name is required"));

        this.index = pinecone.getIndexConnection(indexName);

        log.info("Pinecone client initialized for index: {}", indexName);

    } catch (Exception e) {
        throw new AIServiceException("Failed to initialize Pinecone client: " + e.getMessage(), e);
    }
}

@PreDestroy
public void shutdown() {
    if (pinecone != null) {
        try {
            pinecone.close();
            log.info("Pinecone client closed successfully");
        } catch (Exception e) {
            log.warn("Error closing Pinecone client: {}", e.getMessage());
        }
    }
}
```

**CRITICAL CORRECTIONS**:
1. **Builder Pattern**: Use `Pinecone.Builder(apiKey)` for client initialization
2. **Index Connection**: Use `getIndexConnection()` to get index reference
3. **PreDestroy**: MUST add `@PreDestroy` to properly close connections
4. **Error Handling**: Wrap initialization errors in `AIServiceException`
5. **Thread Safety**: The Pinecone client is thread-safe and can be reused

### Phase 3: Method-by-Method Migration

#### 3.1 Upsert/Store Vector

**Current**: REST POST to `/vectors/upsert`
**New**: Use `Index.upsert()`

**Example** (CORRECTED):
```java
import io.pinecone.proto.UpsertRequest;
import io.pinecone.proto.UpsertResponse;
import io.pinecone.proto.Vector;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;

@Override
public String storeVector(String entityType, String entityId, String content,
                         List<Double> embedding, Map<String, Object> metadata) {
    ensureEnabled();
    String vectorId = buildVectorId(entityType, entityId);

    try {
        // Convert Double to Float (Pinecone uses float32)
        List<Float> floatVector = embedding.stream()
            .map(Double::floatValue)
            .collect(Collectors.toList());

        // Build metadata struct
        Map<String, Value> metadataMap = new HashMap<>();
        metadataMap.put("entityType", value(entityType));
        metadataMap.put("entityId", value(entityId));
        if (content != null) {
            metadataMap.put("content", value(content));
        }
        if (metadata != null) {
            metadata.forEach((key, val) ->
                metadataMap.put(key, toValue(val))
            );
        }

        Struct metadataStruct = Struct.newBuilder()
            .putAllFields(metadataMap)
            .build();

        // Build vector
        Vector vector = Vector.newBuilder()
            .setId(vectorId)
            .addAllValues(floatVector)
            .setMetadata(metadataStruct)
            .build();

        // Upsert with namespace
        String namespace = config.getNamespace() != null ? config.getNamespace() : "default";
        UpsertRequest request = UpsertRequest.newBuilder()
            .addVectors(vector)
            .setNamespace(namespace)
            .build();

        UpsertResponse response = index.upsert(request);

        log.debug("Upserted vector: {} (upserted count: {})", vectorId, response.getUpsertedCount());
        return vectorId;

    } catch (Exception e) {
        throw new AIServiceException("Failed to store vector in Pinecone: " + e.getMessage(), e);
    }
}

/**
 * Convert Java objects to Pinecone protobuf Value
 */
private Value toValue(Object obj) {
    if (obj == null) {
        return Value.newBuilder().setNullValue(com.google.protobuf.NullValue.NULL_VALUE).build();
    }
    if (obj instanceof String) {
        return Value.newBuilder().setStringValue((String) obj).build();
    }
    if (obj instanceof Integer || obj instanceof Long) {
        return Value.newBuilder().setNumberValue(((Number) obj).doubleValue()).build();
    }
    if (obj instanceof Double || obj instanceof Float) {
        return Value.newBuilder().setNumberValue(((Number) obj).doubleValue()).build();
    }
    if (obj instanceof Boolean) {
        return Value.newBuilder().setBoolValue((Boolean) obj).build();
    }
    if (obj instanceof List) {
        List<?> list = (List<?>) obj;
        List<Value> values = list.stream()
            .map(this::toValue)
            .collect(Collectors.toList());
        return Value.newBuilder()
            .setListValue(com.google.protobuf.ListValue.newBuilder().addAllValues(values))
            .build();
    }
    if (obj instanceof Map) {
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) obj;
        Map<String, Value> valueMap = map.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> toValue(e.getValue())
            ));
        return Value.newBuilder()
            .setStructValue(Struct.newBuilder().putAllFields(valueMap))
            .build();
    }
    // Fallback: convert to string
    return Value.newBuilder().setStringValue(obj.toString()).build();
}
```

**KEY POINTS**:
1. **Protobuf API**: Pinecone uses Protocol Buffers (like Qdrant)
2. **Namespaces**: Pinecone organizes vectors into namespaces
3. **Metadata**: Stored as protobuf `Struct`
4. **Float Vectors**: Convert Double to Float
5. **Synchronous**: The client methods are synchronous (blocking)

#### 3.2 Fetch/Get Vector

**Current**: REST GET `/vectors/fetch`
**New**: Use `Index.fetch()`

**Example**:
```java
import io.pinecone.proto.FetchRequest;
import io.pinecone.proto.FetchResponse;

@Override
public Optional<VectorRecord> getVector(String vectorId) {
    ensureEnabled();

    try {
        String namespace = extractNamespace(vectorId);

        FetchRequest request = FetchRequest.newBuilder()
            .addIds(vectorId)
            .setNamespace(namespace)
            .build();

        FetchResponse response = index.fetch(request);

        if (!response.containsVectors(vectorId)) {
            return Optional.empty();
        }

        Vector vector = response.getVectorsOrThrow(vectorId);
        return Optional.of(toVectorRecord(vectorId, vector, namespace));

    } catch (Exception e) {
        throw new AIServiceException("Failed to fetch vector from Pinecone: " + e.getMessage(), e);
    }
}

/**
 * Convert Pinecone Vector to VectorRecord
 */
private VectorRecord toVectorRecord(String vectorId, Vector vector, String namespace) {
    // Extract metadata
    Struct metadata = vector.getMetadata();
    String entityType = getStringFromStruct(metadata, "entityType");
    String entityId = getStringFromStruct(metadata, "entityId");
    String content = getStringFromStruct(metadata, "content");

    // Extract embedding (convert float to double)
    List<Double> embedding = vector.getValuesList().stream()
        .map(f -> (double) f)
        .collect(Collectors.toList());

    // Extract custom metadata
    Map<String, Object> customMetadata = new LinkedHashMap<>();
    metadata.getFieldsMap().forEach((key, value) -> {
        if (!List.of("entityType", "entityId", "content").contains(key)) {
            customMetadata.put(key, fromValue(value));
        }
    });

    return VectorRecord.builder()
        .vectorId(vectorId)
        .entityType(entityType)
        .entityId(entityId)
        .content(content)
        .embedding(embedding)
        .metadata(customMetadata)
        .build();
}

/**
 * Safely extract string from Struct
 */
private String getStringFromStruct(Struct struct, String key) {
    if (struct.containsFields(key)) {
        Value value = struct.getFieldsOrDefault(key, null);
        if (value != null && value.hasStringValue()) {
            return value.getStringValue();
        }
    }
    return null;
}

/**
 * Convert Pinecone protobuf Value back to Java object
 */
private Object fromValue(Value value) {
    switch (value.getKindCase()) {
        case NULL_VALUE:
            return null;
        case STRING_VALUE:
            return value.getStringValue();
        case NUMBER_VALUE:
            return value.getNumberValue();
        case BOOL_VALUE:
            return value.getBoolValue();
        case LIST_VALUE:
            return value.getListValue().getValuesList().stream()
                .map(this::fromValue)
                .collect(Collectors.toList());
        case STRUCT_VALUE:
            return value.getStructValue().getFieldsMap().entrySet().stream()
                .collect(Collectors.toMap(
                    Map.Entry::getKey,
                    e -> fromValue(e.getValue())
                ));
        default:
            return null;
    }
}
```

#### 3.3 Query/Search

**Current**: REST POST `/query`
**New**: Use `Index.query()`

**Example**:
```java
import io.pinecone.proto.QueryRequest;
import io.pinecone.proto.QueryResponse;
import io.pinecone.proto.ScoredVector;

@Override
public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
    ensureEnabled();

    if (CollectionUtils.isEmpty(queryVector)) {
        throw new AIServiceException("Query vector is required for Pinecone search");
    }

    try {
        // Convert Double to Float
        List<Float> floatVector = queryVector.stream()
            .map(Double::floatValue)
            .collect(Collectors.toList());

        // Build query request
        String namespace = Optional.ofNullable(request.getEntityType())
            .orElse(config.getNamespace() != null ? config.getNamespace() : "default");

        QueryRequest.Builder queryBuilder = QueryRequest.newBuilder()
            .addAllVector(floatVector)
            .setTopK(request.getMaxResults() != null ? request.getMaxResults() : 10)
            .setNamespace(namespace)
            .setIncludeMetadata(true)
            .setIncludeValues(true);

        // Add filter if present
        if (request.getFilters() != null && !request.getFilters().isEmpty()) {
            Struct filter = buildFilter(request.getFilters());
            queryBuilder.setFilter(filter);
        }

        QueryResponse response = index.query(queryBuilder.build());

        // Convert to AISearchResponse
        List<AISearchResponse.SearchResult> results = response.getMatchesList().stream()
            .map(this::toSearchResult)
            .collect(Collectors.toList());

        return AISearchResponse.builder()
            .results(results)
            .totalResults(results.size())
            .build();

    } catch (Exception e) {
        throw new AIServiceException("Pinecone search failed: " + e.getMessage(), e);
    }
}

/**
 * Convert ScoredVector to SearchResult
 */
private AISearchResponse.SearchResult toSearchResult(ScoredVector scoredVector) {
    Struct metadata = scoredVector.getMetadata();

    String entityType = getStringFromStruct(metadata, "entityType");
    String entityId = getStringFromStruct(metadata, "entityId");
    String content = getStringFromStruct(metadata, "content");

    // Extract embedding
    List<Double> embedding = scoredVector.getValuesList().stream()
        .map(f -> (double) f)
        .collect(Collectors.toList());

    // Extract custom metadata
    Map<String, Object> customMetadata = new LinkedHashMap<>();
    metadata.getFieldsMap().forEach((key, value) -> {
        if (!List.of("entityType", "entityId", "content").contains(key)) {
            customMetadata.put(key, fromValue(value));
        }
    });

    VectorRecord record = VectorRecord.builder()
        .vectorId(scoredVector.getId())
        .entityType(entityType)
        .entityId(entityId)
        .content(content)
        .embedding(embedding)
        .metadata(customMetadata)
        .build();

    return AISearchResponse.SearchResult.builder()
        .vectorRecord(record)
        .score(scoredVector.getScore())
        .build();
}

/**
 * Build Pinecone filter from metadata map
 */
private Struct buildFilter(Map<String, Object> filters) {
    Map<String, Value> filterMap = new HashMap<>();
    filters.forEach((key, value) -> filterMap.put(key, toValue(value)));
    return Struct.newBuilder().putAllFields(filterMap).build();
}
```

#### 3.4 Delete Vector

**Current**: REST DELETE `/vectors/delete`
**New**: Use `Index.delete()`

**Example**:
```java
import io.pinecone.proto.DeleteRequest;
import io.pinecone.proto.DeleteResponse;

@Override
public boolean deleteVector(String vectorId) {
    ensureEnabled();

    try {
        String namespace = extractNamespace(vectorId);

        DeleteRequest request = DeleteRequest.newBuilder()
            .addIds(vectorId)
            .setNamespace(namespace)
            .build();

        index.delete(request);
        log.debug("Deleted vector: {}", vectorId);
        return true;

    } catch (Exception e) {
        log.error("Failed to delete vector {}: {}", vectorId, e.getMessage());
        return false;
    }
}
```

#### 3.5 Delete by Metadata Filter

**Example**:
```java
@Override
public int deleteByEntityType(String entityType) {
    ensureEnabled();

    try {
        String namespace = entityType; // Using entityType as namespace

        // Delete all vectors in namespace
        DeleteRequest request = DeleteRequest.newBuilder()
            .setDeleteAll(true)
            .setNamespace(namespace)
            .build();

        index.delete(request);
        log.info("Deleted all vectors for entity type: {}", entityType);

        // Pinecone doesn't return count, so we can't return exact number
        return -1; // Indicate success without count

    } catch (Exception e) {
        throw new AIServiceException("Failed to delete by entity type: " + e.getMessage(), e);
    }
}
```

#### 3.6 Index Statistics

**Current**: REST GET `/describe_index_stats`
**New**: Use `Index.describeIndexStats()`

**Example**:
```java
import io.pinecone.proto.DescribeIndexStatsRequest;
import io.pinecone.proto.DescribeIndexStatsResponse;

@Override
public long count(String entityType) {
    ensureEnabled();

    try {
        DescribeIndexStatsRequest request = DescribeIndexStatsRequest.newBuilder().build();
        DescribeIndexStatsResponse stats = index.describeIndexStats(request);

        // Get count for specific namespace if entityType is provided
        if (entityType != null) {
            return stats.getNamespacesOrDefault(entityType,
                io.pinecone.proto.NamespaceSummary.getDefaultInstance())
                .getVectorCount();
        }

        // Return total count across all namespaces
        return stats.getTotalVectorCount();

    } catch (Exception e) {
        log.error("Failed to get vector count: {}", e.getMessage());
        return 0;
    }
}
```

### Phase 4: Configuration Updates

#### 4.1 Update Configuration Class

**File**: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIProviderConfig.java`

**Current**:
```java
@Data
public static class PineconeConfig {
    private boolean enabled;
    private String apiKey;
    private String environment;
    private String indexName;
    private String namespace;
}
```

**Enhanced** (add if missing):
```java
@Data
public static class PineconeConfig {
    private boolean enabled;
    private String apiKey;
    private String environment;  // e.g., "us-east1-gcp"
    private String indexName;
    private String namespace = "default";
    private String projectId;  // Optional: Pinecone project ID
    private Integer timeout = 30;  // Timeout in seconds
}
```

#### 4.2 Application Properties

```yaml
ai:
  providers:
    pinecone:
      enabled: true
      api-key: ${PINECONE_API_KEY}
      environment: us-east1-gcp
      index-name: my-vector-index
      namespace: default
      timeout: 30
```

### Phase 5: Testing Strategy

#### 5.1 Unit Tests

```java
import io.pinecone.clients.Pinecone;
import io.pinecone.clients.Index;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PineconeVectorDatabaseServiceTest {

    @Mock
    private Index mockIndex;

    @Mock
    private Pinecone mockPinecone;

    @Test
    void testStoreVector() {
        // Mock setup
        UpsertResponse mockResponse = UpsertResponse.newBuilder()
            .setUpsertedCount(1)
            .build();

        when(mockIndex.upsert(any(UpsertRequest.class)))
            .thenReturn(mockResponse);

        // Test logic
        String vectorId = service.storeVector(...);

        // Verify
        verify(mockIndex).upsert(any(UpsertRequest.class));
        assertNotNull(vectorId);
    }
}
```

#### 5.2 Integration Tests with Pinecone Sandbox

**NOTE**: Pinecone doesn't have official Testcontainers support yet.

**Options**:
1. **Use Free Tier**: Test against Pinecone free tier (starter plan)
2. **Mock Integration**: Use WireMock to mock Pinecone API
3. **Sandbox Environment**: Create dedicated test index

**Example Configuration**:
```java
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "ai.vector-db.type=pinecone",
    "ai.providers.pinecone.enabled=true",
    "ai.providers.pinecone.api-key=${PINECONE_TEST_API_KEY}",
    "ai.providers.pinecone.index-name=test-index"
})
class PineconeIntegrationTest {

    @Autowired
    private VectorDatabaseService vectorService;

    @Test
    void testFullWorkflow() {
        // Store
        String vectorId = vectorService.storeVector(...);

        // Fetch
        Optional<VectorRecord> record = vectorService.getVector(vectorId);
        assertTrue(record.isPresent());

        // Search
        AISearchResponse results = vectorService.search(...);
        assertFalse(results.getResults().isEmpty());

        // Delete
        boolean deleted = vectorService.deleteVector(vectorId);
        assertTrue(deleted);
    }
}
```

### Phase 6: Migration Checklist

#### Pre-Migration
- [ ] Review current Pinecone usage patterns
- [ ] Identify all API endpoints used
- [ ] Document current error handling
- [ ] Review metadata schema
- [ ] Check namespace usage

#### Migration Steps
- [ ] Add official client dependency
- [ ] Create Pinecone client bean with `@PreDestroy`
- [ ] Implement helper methods (`toValue()`, `fromValue()`)
- [ ] Migrate `storeVector()` method
- [ ] Migrate `getVector()` method
- [ ] Migrate `search()` method
- [ ] Migrate `deleteVector()` method
- [ ] Migrate `count()` and statistics methods
- [ ] Update configuration properties
- [ ] Add unit tests
- [ ] Add integration tests

#### Post-Migration
- [ ] Run all existing tests
- [ ] Performance comparison
- [ ] Update documentation
- [ ] Code review
- [ ] Deploy to staging
- [ ] Monitor error logs

## Potential Challenges and Solutions

### Challenge 1: Namespace Management ⚠️
**Issue**: Pinecone uses namespaces to organize vectors
**Solution**:
- Use `entityType` as namespace for better organization
- Or use single namespace with metadata filters
- Configure default namespace in config

### Challenge 2: No Testcontainers Support ⚠️
**Issue**: Pinecone is cloud-only, no local Docker image
**Solution**:
- Use Pinecone free tier for testing
- Create dedicated test index
- Use WireMock for unit testing
- Consider in-memory vector DB for local dev

### Challenge 3: Type Conversions ✅
**Issue**: Pinecone uses `float` but code uses `Double`
**Solution**: Same as Qdrant - convert at boundaries

### Challenge 4: Metadata Filtering ⚠️
**Issue**: Pinecone filter syntax is different from other DBs
**Solution**:
- Build helper methods for common filters
- Document Pinecone filter capabilities
- Leverage metadata effectively

### Challenge 5: Index Management ⚠️
**Issue**: Indexes must be pre-created in Pinecone Cloud
**Solution**:
- Document index creation process
- Use Infrastructure-as-Code (Terraform) for index management
- Validate index exists on startup

### Challenge 6: Cost Management ⚠️
**Issue**: Pinecone is a paid service with usage-based pricing
**Solution**:
- Monitor vector count and query volume
- Use free tier for development
- Implement caching for frequent queries
- Document cost implications

## Best Practices

### 1. Namespace Strategy
```java
// Option A: Use entityType as namespace
String namespace = entityType;

// Option B: Use fixed namespace with metadata filters
String namespace = "default";
metadata.put("entityType", entityType);
```

### 2. Batch Operations
```java
// Batch upsert for better performance
public void batchStore(List<VectorData> vectors) {
    UpsertRequest.Builder builder = UpsertRequest.newBuilder()
        .setNamespace(namespace);

    vectors.forEach(v -> builder.addVectors(toVector(v)));

    index.upsert(builder.build());
}
```

### 3. Error Handling
```java
try {
    index.upsert(request);
} catch (io.pinecone.exceptions.PineconeException e) {
    throw new AIServiceException("Pinecone operation failed", e);
}
```

### 4. Connection Management
- Reuse `Pinecone` and `Index` instances
- Don't create new clients per request
- Use `@PreDestroy` for cleanup

## Timeline Estimate

- **Phase 1**: 1 day (Dependencies)
- **Phase 2**: 1 day (Client initialization)
- **Phase 3**: 3-4 days (Method migration)
- **Phase 4**: 0.5 day (Configuration)
- **Phase 5**: 2-3 days (Testing)
- **Phase 6**: 1 day (Migration validation)

**Total**: ~8-10 days for complete migration

## Next Steps

1. Review this plan with the team
2. Set up Pinecone test index
3. Start with Phase 1 (dependencies)
4. Implement incrementally with tests
5. Review and iterate

## References

- [Pinecone Java Client Documentation](https://docs.pinecone.io/docs/java-client)
- [Pinecone API Reference](https://docs.pinecone.io/reference/api/introduction)
- [Pinecone Namespaces Guide](https://docs.pinecone.io/docs/namespaces)
- [Pinecone Metadata Filtering](https://docs.pinecone.io/docs/metadata-filtering)

## Migration Readiness Assessment

**Ready to Start**: ✅ YES

**Prerequisites Met**:
- [x] Configuration class exists
- [x] Clear understanding of Pinecone capabilities
- [x] Complete code examples provided
- [x] Error handling patterns defined
- [ ] Test index created in Pinecone Cloud

**Estimated Effort**: 8-10 days

**Risk Level**: MEDIUM (due to lack of local testing environment)

## Key Takeaways

1. **Protobuf API**: Pinecone uses Protocol Buffers (similar to Qdrant)
2. **Synchronous Client**: All methods are synchronous (blocking)
3. **Namespace-based**: Use namespaces for organization
4. **Cloud-Only**: No local development environment (use free tier)
5. **No Testcontainers**: Requires actual Pinecone instance for integration tests
6. **Index Pre-creation**: Indexes must be created in Pinecone Cloud before use
7. **Implement helpers first**: `toValue()`, `fromValue()` are critical
