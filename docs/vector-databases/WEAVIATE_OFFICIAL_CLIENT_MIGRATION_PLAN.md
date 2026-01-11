# Weaviate Official Java Client Migration Plan

## Overview
This document outlines the comprehensive plan to migrate from the current REST API implementation to the official Weaviate Java client (`io.weaviate:client`).

## Document Status
**Last Updated**: 2026-01-11
**Version**: 1.0
**Status**: Initial Implementation Plan

## Current State Analysis

### Current Implementation
- **Approach**: Manual REST API calls using Spring `RestTemplate`
- **File**: `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-weaviate/src/main/java/com/ai/infrastructure/vector/weaviate/WeaviateVectorDatabaseService.java`
- **Dependencies**: `spring-web`, `jackson-databind`
- **Protocol**: HTTP/HTTPS REST API

### Why Migrate to Official Client?

1. **Strongly Typed**: Type-safe API instead of JSON manipulation
2. **Built-in Features**: Automatic retries, connection pooling, batch operations
3. **GraphQL Support**: Easier complex queries with GraphQL builder
4. **gRPC Protocol**: Official client supports gRPC for better performance
5. **Schema Management**: Built-in schema creation and validation
6. **Official Support**: Maintained by Weaviate team
7. **Better Testing**: Comprehensive mock support

## Migration Plan

### Phase 1: Add Official Client Dependency

#### 1.1 Add Official Client Dependency
**File**: `ai-infrastructure-module/victor-databases/ai-infrastructure-vector-weaviate/pom.xml`

```xml
<dependency>
    <groupId>io.weaviate</groupId>
    <artifactId>client</artifactId>
    <version>4.5.0</version> <!-- Check for latest version -->
</dependency>
```

**IMPORTANT NOTES**:
1. **Official Java Client**: Weaviate provides a mature Java client
2. **Multiple Protocols**: Supports HTTP and gRPC (gRPC recommended for performance)
3. **GraphQL Support**: Built-in GraphQL query builder
4. **Version Compatibility**: Client version should match Weaviate server version
5. **Async Support**: Client supports both sync and async operations

**Dependencies Added Transitively**:
- `io.grpc:grpc-netty-shaded` (for gRPC)
- GraphQL client libraries
- Apache HTTP client

#### 1.2 Remove/Keep REST Dependencies
- **Keep**: `spring-web` (may be used elsewhere)
- **Keep**: `jackson-databind` (for complex object serialization)

### Phase 2: Client Initialization

#### 2.1 Create Weaviate Client Bean

**Current**:
```java
private final RestTemplate restTemplate;
private final Set<String> knownClasses = ConcurrentHashMap.newKeySet();

public WeaviateVectorDatabaseService(AIProviderConfig providerConfig) {
    this.config = Objects.requireNonNull(providerConfig.getWeaviate(), "Weaviate configuration must be present");
    this.restTemplate = buildRestTemplate(config);
}
```

**New**:
```java
import io.weaviate.client.Config;
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.TimeUnit;

private final WeaviateClient client;
private final Set<String> knownClasses = ConcurrentHashMap.newKeySet();

public WeaviateVectorDatabaseService(AIProviderConfig providerConfig) {
    this.config = Objects.requireNonNull(providerConfig.getWeaviate(), "Weaviate configuration must be present");

    try {
        String scheme = Boolean.TRUE.equals(config.getSecure()) ? "https" : "http";
        String host = Optional.ofNullable(config.getHost()).orElse("localhost");
        Integer port = Optional.ofNullable(config.getPort()).orElse(8080);

        // Build Weaviate client configuration
        Config clientConfig = new Config(scheme, host + ":" + port);

        // Add API key if configured
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            clientConfig.setApiKey(config.getApiKey());
        }

        // Add OIDC token if configured
        if (config.getOidcToken() != null && !config.getOidcToken().isBlank()) {
            clientConfig.setOIDCToken(config.getOidcToken());
        }

        // Set timeout
        if (config.getTimeout() != null && config.getTimeout() > 0) {
            clientConfig.setConnectionTimeout(config.getTimeout(), TimeUnit.SECONDS);
        }

        this.client = new WeaviateClient(clientConfig);

        log.info("Weaviate client initialized: {}://{}:{}", scheme, host, port);

    } catch (Exception e) {
        throw new AIServiceException("Failed to initialize Weaviate client: " + e.getMessage(), e);
    }
}

@PreDestroy
public void shutdown() {
    // Weaviate client doesn't require explicit shutdown
    // Connection pool is managed by Apache HTTP client
    log.info("Weaviate client shutdown complete");
}
```

**CRITICAL NOTES**:
1. **Config Object**: Use `Config(scheme, host:port)` for initialization
2. **Authentication**: Supports both API key and OIDC tokens
3. **Timeout**: Set connection timeout via `setConnectionTimeout()`
4. **No Explicit Close**: Client doesn't require explicit shutdown (connection pool auto-managed)
5. **Thread Safety**: WeaviateClient is thread-safe

### Phase 3: Method-by-Method Migration

#### 3.1 Schema/Class Management

**Current**: Manual REST calls to `/v1/schema`
**New**: Use `client.schema()`

**Example**:
```java
import io.weaviate.client.v1.schema.model.Property;
import io.weaviate.client.v1.schema.model.WeaviateClass;
import io.weaviate.client.v1.schema.model.DataType;

private void ensureClassExists(String entityType) {
    if (knownClasses.contains(entityType)) {
        return;
    }

    synchronized (knownClasses) {
        if (knownClasses.contains(entityType)) {
            return;
        }

        try {
            // Check if class exists
            Result<Boolean> existsResult = client.schema()
                .classGetter()
                .withClassName(entityType)
                .run();

            if (existsResult.hasErrors()) {
                // Class doesn't exist, create it
                createClass(entityType);
            }

            knownClasses.add(entityType);

        } catch (Exception e) {
            throw new AIServiceException("Failed to ensure Weaviate class exists: " + e.getMessage(), e);
        }
    }
}

private void createClass(String className) {
    try {
        WeaviateClass weaviateClass = WeaviateClass.builder()
            .className(className)
            .description("Auto-generated class for entity type: " + className)
            .vectorizer("none")  // We provide vectors explicitly
            .properties(Arrays.asList(
                Property.builder()
                    .name("entityId")
                    .dataType(Arrays.asList(DataType.TEXT))
                    .description("Entity identifier")
                    .build(),
                Property.builder()
                    .name("content")
                    .dataType(Arrays.asList(DataType.TEXT))
                    .description("Entity content")
                    .build(),
                Property.builder()
                    .name("metadata")
                    .dataType(Arrays.asList(DataType.OBJECT))
                    .description("Additional metadata")
                    .build()
            ))
            .build();

        Result<Boolean> result = client.schema()
            .classCreator()
            .withClass(weaviateClass)
            .run();

        if (result.hasErrors()) {
            throw new AIServiceException("Failed to create Weaviate class: " +
                result.getError().getMessages());
        }

        log.info("Created Weaviate class: {}", className);

    } catch (Exception e) {
        throw new AIServiceException("Failed to create Weaviate class: " + e.getMessage(), e);
    }
}
```

#### 3.2 Store/Upsert Vector

**Current**: REST POST to `/v1/objects`
**New**: Use `client.data().creator()`

**Example**:
```java
import io.weaviate.client.v1.data.model.WeaviateObject;
import java.util.HashMap;

@Override
public String storeVector(String entityType, String entityId, String content,
                         List<Double> embedding, Map<String, Object> metadata) {
    ensureEnabled();
    ensureClassExists(entityType);
    String vectorId = buildVectorId(entityType, entityId);

    try {
        // Build properties map
        Map<String, Object> properties = new HashMap<>();
        properties.put("entityId", entityId);
        if (content != null) {
            properties.put("content", content);
        }
        if (metadata != null) {
            properties.put("metadata", metadata);
        }

        // Convert Double to Float (Weaviate uses float32)
        Float[] floatVector = embedding.stream()
            .map(Double::floatValue)
            .toArray(Float[]::new);

        // Create object
        Result<WeaviateObject> result = client.data()
            .creator()
            .withClassName(entityType)
            .withID(vectorId)
            .withProperties(properties)
            .withVector(floatVector)
            .run();

        if (result.hasErrors()) {
            throw new AIServiceException("Failed to store vector: " +
                result.getError().getMessages());
        }

        log.debug("Stored vector: {} in class: {}", vectorId, entityType);
        return vectorId;

    } catch (Exception e) {
        throw new AIServiceException("Failed to store vector in Weaviate: " + e.getMessage(), e);
    }
}
```

**KEY POINTS**:
1. **Properties as Map**: Use `Map<String, Object>` for properties
2. **Float Array**: Convert `List<Double>` to `Float[]`
3. **UUID Support**: Weaviate uses UUIDs for IDs (auto-generated or explicit)
4. **Result Pattern**: All operations return `Result<T>` with error checking

#### 3.3 Update Vector

**Example**:
```java
@Override
public boolean updateVector(String vectorId, String entityType, String entityId, String content,
                           List<Double> embedding, Map<String, Object> metadata) {
    ensureEnabled();
    ensureClassExists(entityType);

    try {
        // Build properties map
        Map<String, Object> properties = new HashMap<>();
        properties.put("entityId", entityId);
        if (content != null) {
            properties.put("content", content);
        }
        if (metadata != null) {
            properties.put("metadata", metadata);
        }

        // Convert to float array
        Float[] floatVector = embedding.stream()
            .map(Double::floatValue)
            .toArray(Float[]::new);

        // Update object (merges with existing)
        Result<Boolean> result = client.data()
            .updater()
            .withClassName(entityType)
            .withID(vectorId)
            .withProperties(properties)
            .withVector(floatVector)
            .run();

        if (result.hasErrors()) {
            log.error("Failed to update vector {}: {}", vectorId, result.getError().getMessages());
            return false;
        }

        return true;

    } catch (Exception e) {
        log.error("Failed to update vector {}: {}", vectorId, e.getMessage());
        return false;
    }
}
```

#### 3.4 Get Vector

**Current**: REST GET `/v1/objects/{id}`
**New**: Use `client.data().objectsGetter()`

**Example**:
```java
import io.weaviate.client.v1.data.model.WeaviateObject;

@Override
public Optional<VectorRecord> getVector(String vectorId) {
    ensureEnabled();
    String[] parts = parseVectorId(vectorId);
    String entityType = parts[0];

    try {
        Result<List<WeaviateObject>> result = client.data()
            .objectsGetter()
            .withClassName(entityType)
            .withID(vectorId)
            .withVector()  // Include vector in response
            .run();

        if (result.hasErrors() || result.getResult() == null || result.getResult().isEmpty()) {
            return Optional.empty();
        }

        WeaviateObject weaviateObject = result.getResult().get(0);
        return Optional.of(toVectorRecord(entityType, weaviateObject));

    } catch (Exception e) {
        log.error("Failed to get vector {}: {}", vectorId, e.getMessage());
        return Optional.empty();
    }
}

/**
 * Convert WeaviateObject to VectorRecord
 */
private VectorRecord toVectorRecord(String entityType, WeaviateObject weaviateObject) {
    // Extract properties
    Map<String, Object> properties = weaviateObject.getProperties();
    String entityId = (String) properties.get("entityId");
    String content = (String) properties.get("content");

    @SuppressWarnings("unchecked")
    Map<String, Object> metadata = (Map<String, Object>) properties.get("metadata");

    // Extract vector (convert Float[] to List<Double>)
    List<Double> embedding = new ArrayList<>();
    if (weaviateObject.getVector() != null) {
        for (Float f : weaviateObject.getVector()) {
            embedding.add((double) f);
        }
    }

    return VectorRecord.builder()
        .vectorId(weaviateObject.getId())
        .entityType(entityType)
        .entityId(entityId)
        .content(content)
        .embedding(embedding)
        .metadata(metadata != null ? metadata : new HashMap<>())
        .build();
}
```

#### 3.5 Search/Query

**Current**: REST POST `/v1/graphql`
**New**: Use `client.graphQL()` with builder

**Example**:
```java
import io.weaviate.client.v1.graphql.model.GraphQLResponse;
import io.weaviate.client.v1.graphql.query.fields.Field;
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument;

@Override
public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
    ensureEnabled();

    if (CollectionUtils.isEmpty(queryVector)) {
        throw new AIServiceException("Query vector is required for Weaviate search");
    }

    try {
        String className = Optional.ofNullable(request.getEntityType())
            .orElseThrow(() -> new AIServiceException("Entity type is required for Weaviate search"));

        ensureClassExists(className);

        // Convert to Float array
        Float[] floatVector = queryVector.stream()
            .map(Double::floatValue)
            .toArray(Float[]::new);

        // Build near vector argument
        NearVectorArgument nearVector = NearVectorArgument.builder()
            .vector(floatVector)
            .certainty(request.getMinScore() != null ? request.getMinScore().floatValue() : 0.7f)
            .build();

        // Define fields to retrieve
        Field[] fields = new Field[]{
            Field.builder().name("entityId").build(),
            Field.builder().name("content").build(),
            Field.builder().name("metadata").build(),
            Field.builder().name("_additional").fields(
                Field.builder().name("id").build(),
                Field.builder().name("certainty").build(),
                Field.builder().name("distance").build(),
                Field.builder().name("vector").build()
            ).build()
        };

        // Execute GraphQL query
        Result<GraphQLResponse> result = client.graphQL()
            .get()
            .withClassName(className)
            .withNearVector(nearVector)
            .withLimit(request.getMaxResults() != null ? request.getMaxResults() : 10)
            .withFields(fields)
            .run();

        if (result.hasErrors()) {
            throw new AIServiceException("Weaviate search failed: " + result.getError().getMessages());
        }

        // Parse GraphQL response
        return parseSearchResponse(result.getResult(), className);

    } catch (Exception e) {
        throw new AIServiceException("Weaviate search failed: " + e.getMessage(), e);
    }
}

/**
 * Parse GraphQL response to AISearchResponse
 */
private AISearchResponse parseSearchResponse(GraphQLResponse response, String className) {
    List<AISearchResponse.SearchResult> results = new ArrayList<>();

    if (response.getData() == null) {
        return AISearchResponse.builder()
            .results(results)
            .totalResults(0)
            .build();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) response.getData();

    @SuppressWarnings("unchecked")
    Map<String, Object> get = (Map<String, Object>) data.get("Get");

    if (get == null) {
        return AISearchResponse.builder().results(results).totalResults(0).build();
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> objects = (List<Map<String, Object>>) get.get(className);

    if (objects == null) {
        return AISearchResponse.builder().results(results).totalResults(0).build();
    }

    for (Map<String, Object> obj : objects) {
        results.add(toSearchResult(className, obj));
    }

    return AISearchResponse.builder()
        .results(results)
        .totalResults(results.size())
        .build();
}

/**
 * Convert GraphQL object to SearchResult
 */
private AISearchResponse.SearchResult toSearchResult(String className, Map<String, Object> obj) {
    String entityId = (String) obj.get("entityId");
    String content = (String) obj.get("content");

    @SuppressWarnings("unchecked")
    Map<String, Object> metadata = (Map<String, Object>) obj.get("metadata");

    @SuppressWarnings("unchecked")
    Map<String, Object> additional = (Map<String, Object>) obj.get("_additional");

    String vectorId = (String) additional.get("id");
    Double certainty = ((Number) additional.get("certainty")).doubleValue();

    // Extract vector if present
    List<Double> embedding = new ArrayList<>();
    if (additional.containsKey("vector")) {
        @SuppressWarnings("unchecked")
        List<Number> vectorList = (List<Number>) additional.get("vector");
        for (Number n : vectorList) {
            embedding.add(n.doubleValue());
        }
    }

    VectorRecord record = VectorRecord.builder()
        .vectorId(vectorId)
        .entityType(className)
        .entityId(entityId)
        .content(content)
        .embedding(embedding)
        .metadata(metadata != null ? metadata : new HashMap<>())
        .build();

    return AISearchResponse.SearchResult.builder()
        .vectorRecord(record)
        .score(certainty)
        .build();
}
```

**KEY POINTS**:
1. **GraphQL API**: Weaviate uses GraphQL for queries
2. **NearVector**: Use `NearVectorArgument` for similarity search
3. **Certainty**: Weaviate returns "certainty" (0-1) instead of distance
4. **_additional**: Metadata like ID, score, vector are in `_additional` field

#### 3.6 Delete Vector

**Current**: REST DELETE `/v1/objects/{class}/{id}`
**New**: Use `client.data().deleter()`

**Example**:
```java
@Override
public boolean deleteVector(String vectorId) {
    ensureEnabled();
    String[] parts = parseVectorId(vectorId);
    String entityType = parts[0];

    try {
        Result<Boolean> result = client.data()
            .deleter()
            .withClassName(entityType)
            .withID(vectorId)
            .run();

        if (result.hasErrors()) {
            log.error("Failed to delete vector {}: {}", vectorId, result.getError().getMessages());
            return false;
        }

        log.debug("Deleted vector: {}", vectorId);
        return true;

    } catch (Exception e) {
        log.error("Failed to delete vector {}: {}", vectorId, e.getMessage());
        return false;
    }
}
```

#### 3.7 Batch Operations

**Example**:
```java
import io.weaviate.client.v1.batch.model.ObjectGetResponse;

public void batchStore(String entityType, List<VectorData> vectors) {
    ensureEnabled();
    ensureClassExists(entityType);

    try {
        // Prepare batch objects
        List<WeaviateObject> objects = new ArrayList<>();

        for (VectorData data : vectors) {
            Map<String, Object> properties = new HashMap<>();
            properties.put("entityId", data.getEntityId());
            if (data.getContent() != null) {
                properties.put("content", data.getContent());
            }
            if (data.getMetadata() != null) {
                properties.put("metadata", data.getMetadata());
            }

            Float[] floatVector = data.getEmbedding().stream()
                .map(Double::floatValue)
                .toArray(Float[]::new);

            WeaviateObject obj = WeaviateObject.builder()
                .className(entityType)
                .id(buildVectorId(entityType, data.getEntityId()))
                .properties(properties)
                .vector(floatVector)
                .build();

            objects.add(obj);
        }

        // Execute batch insert
        Result<ObjectGetResponse[]> result = client.batch()
            .objectsBatcher()
            .withObjects(objects.toArray(new WeaviateObject[0]))
            .run();

        if (result.hasErrors()) {
            throw new AIServiceException("Batch insert failed: " + result.getError().getMessages());
        }

        log.info("Batch inserted {} vectors into class: {}", objects.size(), entityType);

    } catch (Exception e) {
        throw new AIServiceException("Batch insert failed: " + e.getMessage(), e);
    }
}
```

### Phase 4: Configuration Updates

#### 4.1 Update Configuration Class

**File**: `ai-infrastructure-module/ai-infrastructure-core/src/main/java/com/ai/infrastructure/config/AIProviderConfig.java`

**Current/Enhanced**:
```java
@Data
public static class WeaviateConfig {
    private boolean enabled;
    private String scheme = "http";  // or "https"
    private String host;
    private Integer port = 8080;
    private String apiKey;
    private String oidcToken;  // Alternative auth method
    private Integer timeout = 30;  // Connection timeout in seconds
}
```

#### 4.2 Application Properties

```yaml
ai:
  providers:
    weaviate:
      enabled: true
      scheme: http
      host: localhost
      port: 8080
      api-key: ${WEAVIATE_API_KEY:}
      timeout: 30
```

**Production Example**:
```yaml
ai:
  providers:
    weaviate:
      enabled: true
      scheme: https
      host: weaviate.example.com
      port: 443
      api-key: ${WEAVIATE_API_KEY}
      timeout: 60
```

### Phase 5: Testing Strategy

#### 5.1 Unit Tests

```java
import io.weaviate.client.WeaviateClient;
import io.weaviate.client.base.Result;
import org.mockito.Mock;

@ExtendWith(MockitoExtension.class)
class WeaviateVectorDatabaseServiceTest {

    @Mock
    private WeaviateClient mockClient;

    @Test
    void testStoreVector() {
        // Mock setup
        Result<WeaviateObject> mockResult = new Result<>(200, weaviateObject, null);
        when(mockClient.data().creator().withClassName(any()).run())
            .thenReturn(mockResult);

        // Test
        String vectorId = service.storeVector(...);

        // Verify
        assertNotNull(vectorId);
    }
}
```

#### 5.2 Integration Tests with Testcontainers

**GOOD NEWS**: Weaviate has official Testcontainers support!

```java
import org.testcontainers.weaviate.WeaviateContainer;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class WeaviateIntegrationTest {

    @Container
    static WeaviateContainer weaviate = new WeaviateContainer("semitechnologies/weaviate:1.23.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("ai.providers.weaviate.scheme", () -> "http");
        registry.add("ai.providers.weaviate.host", weaviate::getHost);
        registry.add("ai.providers.weaviate.port", weaviate::getFirstMappedPort);
        registry.add("ai.providers.weaviate.enabled", () -> true);
    }

    @Autowired
    private VectorDatabaseService vectorService;

    @Test
    void testFullWorkflow() {
        // Store
        String vectorId = vectorService.storeVector(...);

        // Retrieve
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

**Add Testcontainers Dependency**:
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>weaviate</artifactId>
    <scope>test</scope>
</dependency>
```

### Phase 6: Migration Checklist

#### Pre-Migration
- [ ] Review current Weaviate usage
- [ ] Identify all REST endpoints used
- [ ] Document schema/class structure
- [ ] Review GraphQL queries
- [ ] Check authentication method

#### Migration Steps
- [ ] Add official client dependency
- [ ] Add Testcontainers dependency
- [ ] Create Weaviate client bean
- [ ] Implement schema management
- [ ] Migrate `storeVector()` method
- [ ] Migrate `getVector()` method
- [ ] Migrate `search()` with GraphQL
- [ ] Migrate `deleteVector()` method
- [ ] Implement batch operations
- [ ] Update configuration
- [ ] Add unit tests
- [ ] Add integration tests with Testcontainers

#### Post-Migration
- [ ] Run all tests
- [ ] Performance comparison
- [ ] Update documentation
- [ ] Code review
- [ ] Deploy to staging

## Potential Challenges and Solutions

### Challenge 1: GraphQL Complexity ⚠️
**Issue**: Weaviate uses GraphQL which is more complex than simple REST
**Solution**:
- Use official client's GraphQL builder
- Create helper methods for common queries
- Study Weaviate GraphQL documentation
- Leverage `Field` builder for complex queries

### Challenge 2: Schema Management ✅ EASIER
**Issue**: Schemas must be defined before use
**Solution**:
- Auto-create classes on first use
- Define standard properties (entityId, content, metadata)
- Use `vectorizer: "none"` since we provide vectors
- Cache known classes to avoid repeated checks

### Challenge 3: Certainty vs Distance ⚠️
**Issue**: Weaviate returns "certainty" (0-1) not distance
**Solution**:
- Use certainty as score directly
- Document difference from other vector DBs
- Adjust threshold expectations (higher is better)

### Challenge 4: Type Conversions ✅
**Issue**: Weaviate uses Float[] but code uses List<Double>
**Solution**: Convert at boundaries (same as other providers)

### Challenge 5: Testcontainers Support ✅ EXCELLENT
**Issue**: Need local testing environment
**Solution**: Use official Weaviate Testcontainers support!

## Best Practices

### 1. Schema Design
```java
// Define comprehensive schema upfront
WeaviateClass weaviateClass = WeaviateClass.builder()
    .className(className)
    .vectorizer("none")  // We provide vectors
    .properties(Arrays.asList(
        Property.builder()
            .name("entityId")
            .dataType(Arrays.asList(DataType.TEXT))
            .tokenization(Tokenization.FIELD)  // For exact matching
            .build(),
        Property.builder()
            .name("content")
            .dataType(Arrays.asList(DataType.TEXT))
            .build()
    ))
    .build();
```

### 2. Batch Operations
```java
// Use batch API for better performance
client.batch()
    .objectsBatcher()
    .withObjects(objects...)
    .withConsistencyLevel(ConsistencyLevel.QUORUM)
    .run();
```

### 3. Error Handling
```java
Result<T> result = client.data().creator()...run();
if (result.hasErrors()) {
    throw new AIServiceException(
        result.getError().getMessages().toString());
}
```

### 4. GraphQL Field Selection
```java
// Only request fields you need
Field[] fields = new Field[]{
    Field.builder().name("entityId").build(),
    Field.builder().name("_additional")
        .fields(Field.builder().name("certainty").build())
        .build()
};
```

## Timeline Estimate

- **Phase 1**: 0.5 day (Dependencies)
- **Phase 2**: 1 day (Client initialization)
- **Phase 3**: 4-5 days (Method migration including GraphQL)
- **Phase 4**: 0.5 day (Configuration)
- **Phase 5**: 2 days (Testing with Testcontainers)
- **Phase 6**: 1 day (Migration validation)

**Total**: ~9-10 days for complete migration

## Next Steps

1. Review this plan with the team
2. Add Weaviate Testcontainers dependency
3. Start with Phase 1 (dependencies)
4. Implement incrementally with tests
5. Review and iterate

## References

- [Weaviate Java Client Documentation](https://weaviate.io/developers/weaviate/client-libraries/java)
- [Weaviate GraphQL API](https://weaviate.io/developers/weaviate/api/graphql)
- [Weaviate Testcontainers](https://java.testcontainers.org/modules/databases/weaviate/)
- [Weaviate Schema](https://weaviate.io/developers/weaviate/configuration/schema-configuration)

## Migration Readiness Assessment

**Ready to Start**: ✅ YES

**Prerequisites Met**:
- [x] Configuration class exists
- [x] Testcontainers support available
- [x] Clear understanding of GraphQL API
- [x] Complete code examples provided
- [x] Error handling patterns defined

**Estimated Effort**: 9-10 days

**Risk Level**: LOW-MEDIUM (GraphQL adds complexity but Testcontainers helps)

## Key Takeaways

1. **GraphQL API**: Weaviate uses GraphQL for queries (use builder pattern)
2. **Schema First**: Define schema before storing objects
3. **Certainty Score**: Returns 0-1 certainty (higher is better)
4. **Testcontainers**: Excellent local testing support ✅
5. **Synchronous Client**: All operations are synchronous
6. **Float Arrays**: Use `Float[]` not `List<Float>`
7. **Result Pattern**: Always check `result.hasErrors()` before using data
8. **Batch Operations**: Use batch API for multiple operations
