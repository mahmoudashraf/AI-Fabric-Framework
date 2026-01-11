# Qdrant Official Java Client Migration Plan

## Overview
This document outlines the plan to migrate from the current REST API implementation to the official Qdrant Java client (`io.qdrant:client`).

## Current State Analysis

### Current Implementation
- **Technology**: Spring `RestTemplate` with manual JSON serialization
- **API**: Direct REST API calls to Qdrant HTTP endpoints
- **Methods**: All `VectorDatabaseService` interface methods implemented
- **Configuration**: Uses `AIProviderConfig.QdrantConfig` for connection settings

### Key Methods to Migrate
1. `storeVector()` - Store/upsert vectors
2. `getVector()` / `getVectorByEntity()` - Retrieve vectors by ID
3. `search()` / `searchByEntityType()` - Vector similarity search
4. `removeVector()` / `removeVectorById()` - Delete vectors
5. `batchStoreVectors()` / `batchUpdateVectors()` - Batch operations
6. `batchRemoveVectors()` - Batch deletion
7. `getAllVectors()` - Retrieve all vectors for entity type
8. `getVectorCountByEntityType()` - Count vectors
9. `clearVectorsByEntityType()` - Clear all vectors for entity type
10. `vectorExists()` - Check if vector exists

## Migration Plan

### Phase 1: Setup and Dependencies

#### 1.1 Add Official Client Dependency
**File**: `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-qdrant/pom.xml`

```xml
<dependency>
    <groupId>io.qdrant</groupId>
    <artifactId>client</artifactId>
    <version>1.16.1</version> <!-- Check for latest version -->
</dependency>
```

**Note**: The official client may have gRPC dependencies. Consider:
- If using HTTP only: May need to exclude gRPC dependencies
- If using gRPC: Ensure gRPC port is configured and accessible
- Check for conflicts with existing dependencies

#### 1.2 Remove/Keep REST Dependencies
- **Keep**: `spring-web` and `RestTemplate` (may be used elsewhere)
- **Keep**: `jackson-databind` (for payload serialization if needed)
- **Evaluate**: Can remove `RestTemplate` if not used elsewhere

### Phase 2: Client Initialization

#### 2.1 Create QdrantClient Bean/Instance
**Location**: `QdrantVectorDatabaseService` constructor

**Current**:
```java
private final RestTemplate restTemplate;

public QdrantVectorDatabaseService(AIProviderConfig providerConfig) {
    this.config = Objects.requireNonNull(providerConfig.getQdrant(), "Qdrant configuration must be present");
    this.restTemplate = buildRestTemplate(config);
}
```

**New**:
```java
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Points;

private final QdrantClient qdrantClient;

public QdrantVectorDatabaseService(AIProviderConfig providerConfig) {
    this.config = Objects.requireNonNull(providerConfig.getQdrant(), "Qdrant configuration must be present");
    this.qdrantClient = buildQdrantClient(config);
}

private QdrantClient buildQdrantClient(AIProviderConfig.QdrantConfig config) {
    String host = Optional.ofNullable(config.getHost()).orElse("localhost");
    int port = Optional.ofNullable(config.getPort()).orElse(6333);
    int grpcPort = Optional.ofNullable(config.getGrpcPort()).orElse(6334);
    
    QdrantClient.Builder builder = new QdrantClient.Builder(
        host, 
        config.getPreferGrpc() ? grpcPort : port,
        config.getPreferGrpc()
    );
    
    if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
        builder.withApiKey(config.getApiKey());
    }
    
    if (config.getTimeout() != null) {
        builder.withTimeout(config.getTimeout());
    }
    
    return builder.build();
}
```

#### 2.2 Handle Client Lifecycle
- **Add**: `@PreDestroy` method to close client gracefully
- **Consider**: Connection pooling if client supports it
- **Error Handling**: Handle connection failures and retries

### Phase 3: Method-by-Method Migration

#### 3.1 Collection Management
**Current**: `ensureCollection()` uses REST API
**New**: Use `QdrantClient.createCollection()` or `QdrantClient.collectionExists()`

**Key Changes**:
- Replace REST `GET /collections/{name}` with `client.collectionExists(collectionName)`
- Replace REST `PUT /collections/{name}` with `client.createCollection()`
- Use `CollectionInfo` and `Distance` enums from client library

**Example**:
```java
private void ensureCollection(String collection, Integer vectorSize) {
    if (collectionCache.containsKey(collection)) {
        return;
    }
    synchronized (collectionCache) {
        if (collectionCache.containsKey(collection)) {
            return;
        }
        try {
            if (!qdrantClient.collectionExists(collection).join()) {
                qdrantClient.createCollection(
                    collection,
                    VectorParams.newBuilder()
                        .setSize(vectorSize)
                        .setDistance(Distance.Cosine)
                        .build()
                ).join();
            }
            collectionCache.put(collection, true);
        } catch (Exception e) {
            throw new AIServiceException("Failed to ensure collection: " + e.getMessage(), e);
        }
    }
}
```

#### 3.2 Store Vector
**Current**: `storeVector()` - REST PUT `/collections/{name}/points`
**New**: Use `QdrantClient.upsert()`

**Key Changes**:
- Convert `List<Double>` to `List<Float>` (Qdrant client uses float)
- Use `PointIdFactory` for ID creation
- Use `VectorsFactory` for vector creation
- Use `ValueFactory` for payload values
- Handle `ListenableFuture` (async operations)

**Example**:
```java
@Override
public String storeVector(String entityType, String entityId, String content, 
                         List<Double> embedding, Map<String, Object> metadata) {
    ensureEnabled();
    ensureCollection(entityType, embedding.size());
    String vectorId = buildVectorId(entityType, entityId);
    
    // Convert Double to Float
    List<Float> floatVector = embedding.stream()
        .map(Double::floatValue)
        .collect(Collectors.toList());
    
    // Build payload
    Map<String, Value> payloadMap = new HashMap<>();
    payloadMap.put("entityId", ValueFactory.value(entityId));
    if (content != null) {
        payloadMap.put("content", ValueFactory.value(content));
    }
    if (metadata != null) {
        metadata.forEach((key, value) -> 
            payloadMap.put(key, ValueFactory.value(value))
        );
    }
    
    // Create point
    PointStruct point = PointStruct.newBuilder()
        .setId(PointIdFactory.string(vectorId))
        .setVectors(VectorsFactory.vectors(floatVector))
        .putAllPayload(payloadMap)
        .build();
    
    // Upsert
    qdrantClient.upsert(entityType, List.of(point), null).join();
    
    return vectorId;
}
```

#### 3.3 Get Vector
**Current**: `getVector()` - REST POST `/collections/{name}/points/scroll`
**New**: Use `QdrantClient.retrieve()`

**Key Changes**:
- Use `PointIdFactory` to create point ID
- Handle `RetrievedPoint` response
- Convert `Value` back to Java objects
- Convert `List<Float>` back to `List<Double>`

**Example**:
```java
@Override
public Optional<VectorRecord> getVector(String vectorId) {
    ensureEnabled();
    String[] parts = parseVectorId(vectorId);
    String entityType = parts[0];
    ensureCollection(entityType, null);
    
    List<RetrievedPoint> points = qdrantClient.retrieve(
        entityType,
        List.of(PointIdFactory.string(vectorId)),
        null,
        true,
        true
    ).join();
    
    if (points.isEmpty()) {
        return Optional.empty();
    }
    
    return Optional.of(toVectorRecord(entityType, points.get(0)));
}
```

#### 3.4 Search
**Current**: `search()` - REST POST `/collections/{name}/points/search`
**New**: Use `QdrantClient.search()`

**Key Changes**:
- Convert query vector to `List<Float>`
- Build `Filter` using `FilterFactory` for metadata filtering
- Handle `ScoredPoint` response
- Convert `Value` payload back to Java objects

**Example**:
```java
@Override
public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
    ensureEnabled();
    String entityType = request.getEntityType();
    ensureCollection(entityType, queryVector.size());
    
    // Convert to float
    List<Float> floatVector = queryVector.stream()
        .map(Double::floatValue)
        .collect(Collectors.toList());
    
    // Build filter if needed
    Filter filter = buildFilter(request.getMetadataFilters());
    
    // Search
    List<ScoredPoint> results = qdrantClient.search(
        entityType,
        floatVector,
        filter,
        request.getLimit(),
        null,
        null,
        null,
        null
    ).join();
    
    // Convert results
    List<VectorRecord> records = results.stream()
        .filter(point -> point.getScore() >= request.getThreshold())
        .map(point -> toVectorRecord(entityType, point))
        .collect(Collectors.toList());
    
    return AISearchResponse.builder()
        .results(records)
        .total(records.size())
        .build();
}
```

#### 3.5 Delete Vector
**Current**: `removeVectorById()` - REST POST `/collections/{name}/points/delete`
**New**: Use `QdrantClient.delete()`

**Example**:
```java
@Override
public boolean removeVectorById(String vectorId) {
    ensureEnabled();
    String[] parts = parseVectorId(vectorId);
    String entityType = parts[0];
    ensureCollection(entityType, null);
    
    qdrantClient.delete(
        entityType,
        List.of(PointIdFactory.string(vectorId)),
        null
    ).join();
    
    return true;
}
```

#### 3.6 Batch Operations
**Current**: Manual iteration
**New**: Use batch methods or single calls with multiple points

**Example**:
```java
@Override
public List<String> batchStoreVectors(List<VectorRecord> vectors) {
    if (CollectionUtils.isEmpty(vectors)) {
        return Collections.emptyList();
    }
    
    // Group by entity type
    Map<String, List<VectorRecord>> byType = vectors.stream()
        .collect(Collectors.groupingBy(VectorRecord::getEntityType));
    
    List<String> vectorIds = new ArrayList<>();
    
    byType.forEach((entityType, records) -> {
        ensureCollection(entityType, records.get(0).getEmbedding().size());
        
        List<PointStruct> points = records.stream()
            .map(record -> toPointStruct(record))
            .collect(Collectors.toList());
        
        qdrantClient.upsert(entityType, points, null).join();
        
        records.forEach(record -> 
            vectorIds.add(buildVectorId(record.getEntityType(), record.getEntityId()))
        );
    });
    
    return vectorIds;
}
```

#### 3.7 Count and Clear Operations
**Current**: REST API calls
**New**: Use `QdrantClient.count()` and `QdrantClient.delete()` with filter

**Example**:
```java
@Override
public long getVectorCountByEntityType(String entityType) {
    ensureEnabled();
    try {
        CollectionInfo info = qdrantClient.getCollectionInfo(entityType).join();
        return info.getPointsCount();
    } catch (Exception e) {
        log.debug("Could not get count for collection {}: {}", entityType, e.getMessage());
        return 0;
    }
}

@Override
public long clearVectorsByEntityType(String entityType) {
    ensureEnabled();
    ensureCollection(entityType, null);
    
    // Delete all points using empty filter
    Filter emptyFilter = Filter.newBuilder().build();
    qdrantClient.delete(entityType, emptyFilter, null).join();
    
    return 0; // Qdrant doesn't return count
}
```

### Phase 4: Helper Methods and Utilities

#### 4.1 Value Conversion Utilities
Create helper methods to convert between:
- `List<Double>` ↔ `List<Float>`
- Java objects ↔ `Value` (Qdrant protobuf)
- `RetrievedPoint` / `ScoredPoint` → `VectorRecord`

**Example**:
```java
private VectorRecord toVectorRecord(String entityType, RetrievedPoint point) {
    String id = point.getId().getStringValue();
    JsonNode payload = point.getPayload();
    String entityId = payload.path("entityId").asText(null);
    String content = payload.path("content").asText(null);
    
    List<Double> vector = new ArrayList<>();
    if (point.hasVectors()) {
        point.getVectors().getVector().getDataList().forEach(v -> 
            vector.add((double) v)
        );
    }
    
    Map<String, Object> metadata = new LinkedHashMap<>();
    payload.fields().forEachRemaining(entry -> {
        if (!List.of("entityId", "content").contains(entry.getKey())) {
            metadata.put(entry.getKey(), convertValue(entry.getValue()));
        }
    });
    
    return VectorRecord.builder()
        .vectorId(id)
        .entityType(entityType)
        .entityId(entityId)
        .content(content)
        .embedding(vector)
        .metadata(metadata)
        .build();
}

private Object convertValue(Value value) {
    // Convert Qdrant Value to Java object
    // Handle different value types: string, integer, double, bool, list, etc.
    // Implementation depends on Value API
}
```

#### 4.2 Filter Building
Create method to convert metadata filters to Qdrant `Filter`:

```java
private Filter buildFilter(Map<String, Object> metadataFilters) {
    if (metadataFilters == null || metadataFilters.isEmpty()) {
        return null;
    }
    
    // Build filter using FilterFactory
    // Example structure depends on Qdrant client API
    Filter.Builder filterBuilder = Filter.newBuilder();
    
    metadataFilters.forEach((key, value) -> {
        // Add condition to filter
        // Structure depends on Qdrant client API
    });
    
    return filterBuilder.build();
}
```

### Phase 5: Error Handling and Async Operations

#### 5.1 Handle Async Operations
The official client uses `ListenableFuture` or `CompletableFuture`. Options:

**Option A: Block on all operations** (simpler, matches current sync API)
```java
qdrantClient.upsert(...).join(); // Block until complete
```

**Option B: Make service async** (better performance, requires interface changes)
```java
CompletableFuture<UpdateResult> future = qdrantClient.upsert(...);
// Handle async
```

**Recommendation**: Start with Option A to maintain current API contract.

#### 5.2 Error Handling
- Wrap Qdrant exceptions in `AIServiceException`
- Handle connection errors gracefully
- Log errors appropriately
- Maintain backward compatibility with error messages

**Example**:
```java
try {
    qdrantClient.upsert(...).join();
} catch (Exception e) {
    log.error("Qdrant operation failed: {}", e.getMessage(), e);
    throw new AIServiceException("Qdrant request failed: " + e.getMessage(), e);
}
```

### Phase 6: Testing Strategy

#### 6.1 Unit Tests
- Mock `QdrantClient` for unit tests
- Test each method independently
- Test error handling

#### 6.2 Integration Tests
- Use Testcontainers with Qdrant (already set up)
- Test with real Qdrant instance
- Verify all operations work correctly
- Test both HTTP and gRPC modes (if applicable)

#### 6.3 Migration Testing
- Run existing integration tests
- Compare results with REST implementation
- Verify performance characteristics
- Test edge cases (empty collections, large batches, etc.)

### Phase 7: Configuration Updates

#### 7.1 Update Configuration Class
**File**: `AIProviderConfig.QdrantConfig`

**Add/Verify**:
- `grpcPort` - For gRPC connections
- `preferGrpc` - Boolean to choose transport
- `timeout` - Connection timeout
- `apiKey` - API key support

#### 7.2 Update Application Properties
Ensure all necessary properties are documented and have defaults.

### Phase 8: Documentation and Cleanup

#### 8.1 Update Code Comments
- Remove REST API endpoint references
- Add Qdrant client API references
- Update method documentation

#### 8.2 Remove Unused Code
- Remove `RestTemplate` if not used elsewhere
- Remove manual JSON serialization code
- Clean up helper methods specific to REST API

#### 8.3 Update README/Documentation
- Document new dependency
- Update configuration examples
- Note any breaking changes

## Implementation Checklist

### Pre-Migration
- [ ] Research official client API thoroughly
- [ ] Check client version compatibility with Qdrant server version
- [ ] Verify dependency conflicts
- [ ] Set up test environment with official client

### Phase 1: Setup
- [ ] Add `io.qdrant:client` dependency
- [ ] Resolve any dependency conflicts
- [ ] Update build configuration if needed

### Phase 2: Client Initialization
- [ ] Implement `buildQdrantClient()` method
- [ ] Add client lifecycle management
- [ ] Test client connection

### Phase 3: Core Methods
- [ ] Migrate `ensureCollection()`
- [ ] Migrate `storeVector()`
- [ ] Migrate `getVector()` / `getVectorByEntity()`
- [ ] Migrate `search()` / `searchByEntityType()`
- [ ] Migrate `removeVector()` / `removeVectorById()`
- [ ] Migrate `batchStoreVectors()` / `batchUpdateVectors()`
- [ ] Migrate `batchRemoveVectors()`
- [ ] Migrate `getAllVectors()`
- [ ] Migrate `getVectorCountByEntityType()`
- [ ] Migrate `clearVectorsByEntityType()`
- [ ] Migrate `vectorExists()`

### Phase 4: Utilities
- [ ] Implement value conversion utilities
- [ ] Implement filter building utilities
- [ ] Implement point conversion utilities

### Phase 5: Error Handling
- [ ] Add comprehensive error handling
- [ ] Test error scenarios
- [ ] Ensure error messages are user-friendly

### Phase 6: Testing
- [ ] Update unit tests
- [ ] Run integration tests
- [ ] Performance testing
- [ ] Edge case testing

### Phase 7: Configuration
- [ ] Update configuration class
- [ ] Update application properties
- [ ] Test configuration options

### Phase 8: Cleanup
- [ ] Remove unused REST code
- [ ] Update documentation
- [ ] Code review
- [ ] Final testing

## Potential Challenges and Solutions

### Challenge 1: Type Conversions
**Issue**: Qdrant client uses `float` but our code uses `Double`
**Solution**: Create conversion utilities, convert at boundaries

### Challenge 2: Async Operations
**Issue**: Client is async but our interface is sync
**Solution**: Use `.join()` to block, or consider async interface in future

### Challenge 3: Payload Serialization
**Issue**: Converting Java objects to Qdrant `Value` type
**Solution**: Use `ValueFactory` utilities, handle all types

### Challenge 4: Filter Building
**Issue**: Complex filter construction from metadata
**Solution**: Build helper methods using `FilterFactory`

### Challenge 5: Error Handling
**Issue**: Different exception types from client
**Solution**: Wrap in `AIServiceException` for consistency

### Challenge 6: gRPC vs HTTP
**Issue**: Client may prefer gRPC, but HTTP may be more accessible
**Solution**: Support both, make configurable via `preferGrpc`

### Challenge 7: Version Compatibility
**Issue**: Client version must match server capabilities
**Solution**: Document version requirements, test compatibility

## Rollback Plan

If migration encounters issues:

1. **Keep REST implementation** as fallback
2. **Feature flag** to switch between implementations
3. **Gradual migration** - migrate methods one at a time
4. **A/B testing** - test both implementations in parallel

## Success Criteria

- [ ] All existing tests pass
- [ ] No performance regression
- [ ] All features work as before
- [ ] Code is cleaner and more maintainable
- [ ] Better error messages
- [ ] Official client benefits (type safety, better API)

## Timeline Estimate

- **Phase 1-2**: 1-2 days (Setup and initialization)
- **Phase 3**: 3-5 days (Core method migration)
- **Phase 4**: 1-2 days (Utilities)
- **Phase 5**: 1 day (Error handling)
- **Phase 6**: 2-3 days (Testing)
- **Phase 7**: 1 day (Configuration)
- **Phase 8**: 1 day (Cleanup)

**Total**: ~10-15 days for complete migration

## Next Steps

1. Review this plan with the team
2. Set up development branch
3. Start with Phase 1 (dependencies)
4. Implement incrementally with tests
5. Review and iterate
