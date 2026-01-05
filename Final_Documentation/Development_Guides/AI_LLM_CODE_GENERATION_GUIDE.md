# 🤖 AI/LLM Code Generation Guide
# AI Infrastructure Framework Development Standards

**Document Purpose:** Comprehensive guide for AI/LLM models generating code for the AI Infrastructure Framework

**Last Updated:** January 2026  
**Framework:** AI Infrastructure (ai-infrastructure-module)  
**Project Type:** Open-Source Framework  
**Status:** Production Ready ✅

---

## 📋 Table of Contents

1. [Framework Overview](#framework-overview)
2. [Core Philosophy](#core-philosophy)
3. [Module Structure](#module-structure)
4. [Code Generation Rules](#code-generation-rules)
5. [Security Standards](#security-standards)
6. [LLM Integration Patterns](#llm-integration-patterns)
7. [Testing Requirements](#testing-requirements)
8. [Performance Standards](#performance-standards)
9. [Common Patterns](#common-patterns)
10. [Anti-Patterns to Avoid](#anti-patterns-to-avoid)
11. [Code Examples](#code-examples)
12. [Review Checklist](#review-checklist)

---

## 🎯 Framework Overview

### What This Framework Does

The AI Infrastructure Framework is an **open-source Java framework** providing:
- **LLM-Powered Orchestration** (RAGOrchestrator)
- **Relationship Query Engine** (Natural language → JPQL)
- **Behavior Analytics** (User sentiment, churn prediction)
- **Vector Search Integration** (Lucene, Pinecone, Weaviate, etc.)
- **PII Detection & Sanitization** (Security layer)
- **Access Control & Compliance** (Enterprise security)

### Technology Stack

```yaml
language: Java 21
framework: Spring Boot 3.2.0
build_tool: Maven 3.9+
key_libraries:
  - Lombok (code generation)
  - Jackson (JSON)
  - Spring Data JPA
  - JUnit 5 + Mockito
  - AssertJ (assertions)
testing:
  unit: JUnit 5 + Mockito
  integration: Spring Boot Test + Testcontainers
  realapi: OpenAI API integration tests
```

### Project Structure

```
ai-infrastructure-module/
├── ai-infrastructure-core/          # Core orchestration, RAG, security
├── ai-infrastructure-relationship-query/  # Relationship query engine
├── ai-infrastructure-behavior/      # Behavior analytics
├── ai-fabric-providers/             # LLM provider integrations
├── victor-databases/                # Vector database implementations
└── integration-Testing/             # Integration test suites
```

---

## 🎯 Core Philosophy

### Principle 1: Greenfield Architecture

**Rule:** This is a NEW framework with NO legacy constraints.

```java
// ❌ NEVER do this:
@Deprecated  // "For backward compatibility"
public void oldMethod() { }

// ✅ ALWAYS do this:
// Remove deprecated code immediately
// No backward compatibility layers
// Clean, modern patterns only
```

**Why:** We're building the foundation others will use for years. Start clean.

---

### Principle 2: Security-First (Fail-Closed Model)

**Rule:** If ANY part of a request is unauthorized → DENY the ENTIRE request.

```java
// ❌ WRONG - Silent Filtering:
List<String> allowed = filterAllowed(requested);
if (allowed.size() < requested.size()) {
    log.debug("Some denied");
    return execute(allowed);  // ← Executes partial request
}

// ✅ CORRECT - Fail-Closed:
List<String> allowed = filterAllowed(requested);
if (allowed.size() < requested.size()) {
    List<String> denied = getDenied(requested, allowed);
    log.warn("Access denied: user {} requested unauthorized: {}", userId, denied);
    return Result.builder()
        .success(false)
        .errorCode("ACCESS_DENIED")
        .message("You do not have permission for some requested items")
        .data(Map.of(
            "requestedItems", requested,
            "allowedItems", allowed,
            "deniedItems", denied
        ))
        .build();
}
return execute(allowed);
```

**Why:** Framework users trust us for security. Silent filtering can leak information.

---

### Principle 3: LLM-Driven Intelligence

**Rule:** LLM analyzes queries and makes decisions. Configuration provides CONSTRAINTS, not OVERRIDES.

```java
// ❌ WRONG - Application Overrides LLM:
private Mode resolveMode(Plan llmPlan, Options appOptions) {
    if (appOptions.getMode() != null) {
        return appOptions.getMode();  // ← Ignores LLM's analysis
    }
    return llmPlan.getRecommendedMode();
}

// ✅ CORRECT - LLM Decides, Config Constrains:
private Mode resolveMode(Plan llmPlan, Options appOptions) {
    // LLM's intelligent decision based on query analysis
    if (llmPlan.needsSemanticSearch()) {
        // Configuration constraint (not override)
        if (!config.vectorSearchEnabled()) {
            log.info("LLM recommended semantic search but config disables it");
            return Mode.STANDALONE;
        }
        // System availability constraint
        if (!system.hasVectorDB()) {
            log.warn("LLM recommended semantic search but vector DB unavailable");
            return Mode.STANDALONE;
        }
        return Mode.ENHANCED;  // LLM + constraints = decision
    }
    return Mode.STANDALONE;
}
```

**Why:** The LLM analyzed the SPECIFIC query. It knows better than static configuration.

---

### Principle 4: Clean Separation of Concerns

**Rule:** Each layer has ONE clear responsibility. No mixing.

#### User Intent (from LLM) ≠ Application Configuration

```java
// ❌ WRONG - Mixing:
Map<String, Object> actionParams = Map.of(
    "query", "find users",         // ← User intent (from LLM)
    "entityTypes", ["user"],       // ← User intent (from LLM)
    "limit", 20,                   // ← App config (NOT from user)
    "returnMode", "FULL"           // ← App config (NOT from user)
);  // ← Confusing mix!

// ✅ CORRECT - Separated:
// LLM extracts from user's natural language:
Intent {
    query: "find users",
    entityTypes: ["user"]
}

// Application provides runtime options:
QueryOptions {
    limit: 20,
    returnMode: ReturnMode.FULL
}
```

#### Production Code ≠ Test Code

```java
// ❌ WRONG - Test code in production (src/main/java):
@Service
public class MyService {
    @Autowired
    public MyService(Dep1 dep1, Dep2 dep2) { }
    
    // Convenience constructor for tests ← NO!
    public MyService(Dep1 dep1) {
        this(dep1, null);
    }
}

// ✅ CORRECT - Single production constructor:
@Service
@RequiredArgsConstructor  // Lombok generates constructor
public class MyService {
    private final Dep1 dep1;
    private final Dep2 dep2;
}

// Tests handle their own mocking (src/test/java):
@Test
void myTest() {
    Dep1 dep1 = mock(Dep1.class);
    Dep2 dep2 = mock(Dep2.class);
    MyService service = new MyService(dep1, dep2);
}
```

**Why:** Production code should have ZERO test-specific concerns.

---

### Principle 5: Performance & Caching

**Rule:** Cache expensive operations at APPLICATION level with thread safety.

```java
// ❌ WRONG - No caching:
public Set<String> getEntityTypes() {
    // Reflection every time (1-2ms overhead per call)
    Class<?> clazz = Class.forName("...");
    return (Set<String>) method.invoke(bean);
}

// ❌ WRONG - Request-scoped cache:
@Scope("request")
class Cache { }  // ← Per-request cache for static data

// ✅ CORRECT - Application-level cache:
@Slf4j
@Service  // ← Singleton (application scope)
public class MyService {
    // Cache with double-checked locking
    private volatile Set<String> cachedData = null;
    private volatile boolean cacheInitialized = false;
    
    private Set<String> getData() {
        // Fast path (no synchronization)
        if (cacheInitialized) {
            return cachedData != null ? cachedData : Set.of();
        }
        
        // Slow path (synchronized initialization)
        synchronized (this) {
            if (cacheInitialized) {
                return cachedData != null ? cachedData : Set.of();
            }
            cachedData = expensiveOperation();  // Only once!
            cacheInitialized = true;
            log.debug("Initialized cache with {} items", cachedData.size());
            return cachedData;
        }
    }
}
```

**Why:** Reflection, LLM calls, schema discovery are expensive. Cache them.

---

### Principle 6: Extensibility via SPI

**Rule:** Framework provides interfaces. Users implement SPIs. Required SPIs fail at startup.

```java
// ✅ CORRECT - SPI Pattern:

// 1. Interface in core module (SPI definition)
package com.ai.infrastructure.spi;

public interface RelationshipQueryAccessControlPolicy {
    boolean canUserExecuteRelationshipQueries(String userId);
    boolean canUserQueryEntityType(String userId, String entityType);
    List<String> getAllowedEntityTypesForUser(String userId);
}

// 2. Handler requires implementation
@Component
@ConditionalOnBean(RelationshipQueryAccessControlPolicy.class)  // ← Required!
public class MyHandler {
    private final RelationshipQueryAccessControlPolicy policy;  // ← Not Optional
}

// 3. No default implementation
// If integration enabled and no policy → Application fails to start ✅

// 4. Users implement in their application:
@Component
public class MyAccessPolicy implements RelationshipQueryAccessControlPolicy {
    // Custom business logic
}
```

**Why:** Framework users control behavior through SPIs, not by modifying framework code.

---

## 📐 Module Structure

### Core Module (`ai-infrastructure-core`)

**Responsibilities:**
- Orchestration (RAGOrchestrator)
- Intent extraction (IntentQueryExtractor)
- Security (ResponseSanitizer, PIIDetection)
- SPI definitions (BehaviorContextProvider, etc.)
- Common DTOs (OrchestrationResult, Intent, etc.)

**Key Classes:**
```
com.ai.infrastructure.intent.orchestration.RAGOrchestrator
com.ai.infrastructure.intent.IntentQueryExtractor
com.ai.infrastructure.security.ResponseSanitizer
com.ai.infrastructure.spi.*  ← SPI interfaces
```

### Relationship Query Module (`ai-infrastructure-relationship-query`)

**Responsibilities:**
- Natural language → JPQL conversion
- Relationship traversal
- Hybrid search (relational + vector)
- Query planning

**Key Classes:**
```
com.ai.infrastructure.relationship.service.ReliableRelationshipQueryService
com.ai.infrastructure.relationship.service.RelationshipQueryPlanner
com.ai.infrastructure.relationship.action.RelationshipQueryActionHandler
com.ai.infrastructure.relationship.spi.RelationshipQueryAccessControlPolicy  ← SPI
```

### Behavior Module (`ai-infrastructure-behavior`)

**Responsibilities:**
- User behavior analysis
- Sentiment detection
- Churn prediction
- Pattern recognition

---

## 🔒 Security Standards

### Rule 1: Access Control is REQUIRED

```java
// When creating a new action handler:

@Component
@ConditionalOnProperty("feature.enabled", havingValue = "true")
@ConditionalOnBean(FeatureAccessControlPolicy.class)  // ← MUST require policy
public class MyFeatureHandler implements ActionHandler {
    
    private final FeatureAccessControlPolicy policy;  // ← Required dependency
    
    @Override
    public boolean validateActionAllowed(String userId) {
        return policy.canUserExecuteFeature(userId);  // ← Delegate to policy
    }
}
```

**Checklist:**
- [ ] Policy SPI interface defined?
- [ ] Handler requires policy via `@ConditionalOnBean`?
- [ ] No default "allow-all" implementation?
- [ ] Application fails at startup if policy missing?

### Rule 2: Fail-Closed Security

**Template for access control:**

```java
private List<T> filterAllowed(String userId, List<T> requested) {
    List<T> allowed = new ArrayList<>();
    for (T item : requested) {
        if (policy.canAccess(userId, item)) {
            allowed.add(item);
        }
    }
    return allowed;
}

public Result execute(String userId, List<T> requested) {
    List<T> allowed = filterAllowed(userId, requested);
    
    // CRITICAL: Check if ANY were denied
    if (allowed.size() < requested.size()) {
        List<T> denied = new ArrayList<>(requested);
        denied.removeAll(allowed);
        
        log.warn("Access denied: user {} requested unauthorized items: {}", userId, denied);
        
        return Result.builder()
            .success(false)
            .errorCode("ACCESS_DENIED")
            .message("You do not have permission for some requested items")
            .data(Map.of(
                "requestedItems", requested,
                "allowedItems", allowed,
                "deniedItems", denied
            ))
            .build();
    }
    
    // All checks passed - execute
    return executeWithAllowed(allowed);
}
```

### Rule 3: Comprehensive Audit Logging

```java
// Security events → WARN level
log.warn("Access denied: user {} attempted to access {}", userId, resource);

// Security decisions → INFO level  
log.info("Access granted: user {} for resource {}", userId, resource);

// Unexpected security errors → ERROR level
log.error("Access control policy threw exception for user {}", userId, ex);
```

---

## 🤖 LLM Integration Patterns

### Rule 1: Give LLM Clear, Specific Instructions

```java
// ❌ WRONG - Vague:
prompt.append("Extract entity types from the query");

// ✅ CORRECT - Specific:
prompt.append("8. When action == \"relationship_query\", extract entityTypes from the user request as an array of lower-case strings. ");
prompt.append("Available entity types: ").append(String.join(", ", availableTypes)).append(". ");
prompt.append("Only use entity types from this list. ");
prompt.append("Use [] when unknown or when no entity types match. ");
prompt.append("Example: {\"type\":\"ACTION\",\"action\":\"relationship_query\",\"actionParams\":{\"query\":\"find premium customers\",\"entityTypes\":[\"customer\",\"order\"],\"limit\":20}}");
```

### Rule 2: Always Include Available Options

```java
// When building LLM prompts for relationship queries:
StringBuilder prompt = new StringBuilder();

// Include entity schema
String schema = schemaProvider.getSchemaDescription(entityTypes);
prompt.append("Schema:\n").append(schema);

// Include available entity types
if (!availableEntityTypes.isEmpty()) {
    prompt.append("Available entity types: ");
    prompt.append(String.join(", ", availableEntityTypes));
}

// Include examples
prompt.append("Example plans:\n");
examples.forEach(ex -> prompt.append(ex).append("\n"));
```

### Rule 3: Validate LLM Output

```java
// After LLM extraction:
private void validateRelationshipActionParams(Intent intent) {
    if (!"relationship_query".equals(intent.getAction())) {
        return;
    }
    
    Map<String, Object> params = intent.getActionParams();
    if (params == null) {
        params = new LinkedHashMap<>();
    }
    
    // Ensure entityTypes exists (even if empty)
    if (!params.containsKey("entityTypes")) {
        log.warn("LLM did not extract entityTypes - defaulting to empty list");
        params.put("entityTypes", List.of());
    }
    
    // Normalize entity types
    Object rawTypes = params.get("entityTypes");
    List<String> normalized = normalizeEntityTypes(rawTypes);
    params.put("entityTypes", normalized);
    
    intent.setActionParams(params);
}
```

### Rule 4: LLM Decisions vs Application Configuration

**What LLM Extracts (from user's natural language):**
- User's query/question
- Entity types mentioned
- Filters/criteria implied
- Relationships to traverse

**What Configuration Provides:**
- System capabilities (vector search enabled/disabled)
- Resource limits (max results, timeout)
- Output format (IDS vs FULL)
- Performance tuning (similarity threshold)

```java
// LLM extracts user intent
Intent {
    query: "find premium customers who ordered this month",
    entityTypes: ["customer", "order"]  // ← From user's words
}

// Application provides constraints
QueryOptions {
    limit: 20,              // ← From config
    returnMode: IDS,        // ← From config
    similarityThreshold: 0.7  // ← From config
}
```

---

## 🧱 Code Generation Rules

### Rule 1: No Magic Strings

**ALWAYS extract string literals to constants.**

```java
// ❌ WRONG:
params.get("query");
params.get("entityTypes");
data.put("documents", docs);
if (mode.equals("ENHANCED")) { }

// ✅ CORRECT:
public class MyHandler {
    // Parameter names
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_ENTITY_TYPES = "entityTypes";
    
    // Data keys
    private static final String DATA_KEY_DOCUMENTS = "documents";
    private static final String DATA_KEY_TOTAL_RESULTS = "totalResults";
    
    // Error codes
    private static final String ERROR_ACCESS_DENIED = "ACCESS_DENIED";
    private static final String ERROR_INVALID_PARAMS = "INVALID_PARAMETERS";
    
    // Defaults
    private static final int DEFAULT_LIMIT = 20;
    
    // Usage
    params.get(PARAM_QUERY);
    data.put(DATA_KEY_DOCUMENTS, docs);
}
```

**Group constants by category:**
```java
// Good organization
private static final class ParamNames {
    static final String QUERY = "query";
    static final String ENTITY_TYPES = "entityTypes";
}

private static final class ErrorCodes {
    static final String ACCESS_DENIED = "ACCESS_DENIED";
    static final String INVALID_PARAMS = "INVALID_PARAMETERS";
}
```

### Rule 2: Single @Autowired Constructor

```java
// ❌ WRONG - Multiple constructors:
public class MyService {
    public MyService(Dep1 dep1, Dep2 dep2) { }  // Production
    public MyService(Dep1 dep1) { }  // For tests ← NO!
}

// ✅ CORRECT - Lombok @RequiredArgsConstructor:
@Service
@RequiredArgsConstructor
@Slf4j
public class MyService {
    private final Dep1 dep1;
    private final Dep2 dep2;
    private final Dep3 dep3;
    // Lombok generates single constructor
}

// Or explicit @Autowired:
@Service
public class MyService {
    private final Dep1 dep1;
    private final Dep2 dep2;
    
    @Autowired
    public MyService(Dep1 dep1, Dep2 dep2) {
        this.dep1 = dep1;
        this.dep2 = dep2;
    }
}
```

### Rule 3: Required vs Optional Dependencies

```java
// Required dependencies (fail at startup if missing):
@ConditionalOnBean(RequiredService.class)
public class MyFeature {
    private final RequiredService service;  // ← Not Optional, not nullable
}

// Optional dependencies (graceful degradation):
@Service
public class MyService {
    private final Optional<OptionalProvider> optionalProvider;
    
    @Autowired
    public MyService(ObjectProvider<OptionalProvider> provider) {
        this.optionalProvider = Optional.ofNullable(provider.getIfAvailable());
    }
    
    public void doWork() {
        optionalProvider.ifPresent(provider -> {
            // Use if available
        });
        // Continues if not available
    }
}
```

### Rule 4: Comprehensive JavaDoc

**EVERY public method/class MUST have JavaDoc:**

```java
/**
 * Executes a relationship query using natural language.
 * 
 * <p>This method combines LLM-driven query planning with relational database
 * traversal and optional vector reranking based on query complexity.</p>
 * 
 * <p><strong>Process:</strong></p>
 * <ol>
 *   <li>LLM analyzes query to determine entity types and relationships</li>
 *   <li>Generate optimized JPQL query</li>
 *   <li>Execute with fallback chain (JPA → metadata → vector → simple)</li>
 *   <li>Optionally rerank with vector similarity if ENHANCED mode</li>
 * </ol>
 * 
 * <p><strong>Thread Safety:</strong> This method is thread-safe. Multiple concurrent
 * calls are supported.</p>
 * 
 * <p><strong>Performance:</strong> First call may be slower (plan generation).
 * Subsequent calls benefit from caching.</p>
 * 
 * @param query Natural language query (e.g., "find premium customers who ordered this month")
 * @param entityTypes Entity types to search, extracted by LLM (e.g., ["customer", "order"])
 * @param options Query options for pagination and output format (nullable, uses defaults)
 * @return RAGResponse containing matching documents, metadata, and performance metrics
 * @throws IllegalArgumentException if query is null or blank
 * @throws AccessDeniedException if user lacks permission for requested entity types
 * @throws FallbackExhaustedException if all fallback strategies fail
 * @see RelationshipQueryPlanner for query analysis
 * @see QueryOptions for available options
 */
public RAGResponse execute(String query, List<String> entityTypes, @Nullable QueryOptions options) {
    // Implementation
}
```

### Rule 5: Null Safety

```java
// Check nulls for external inputs:
public Result process(String query, List<String> types) {
    if (query == null || query.isBlank()) {
        throw new IllegalArgumentException("query cannot be null or blank");
    }
    // Continue
}

// Fail fast for unexpected nulls:
if (result == null) {
    log.error("Orchestration produced null result - this should never happen");
    return OrchestrationResult.error("Internal error: orchestration failed");
}

// Optional handling with clear semantics:
if (response.getMetadata() != null && !response.getMetadata().isEmpty()) {
    data.put("metadata", response.getMetadata());
}
```

---

## 🧪 Testing Requirements

### Unit Test Standards

```java
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)  // ← For flexible mocking
class MyServiceTest {
    
    @Mock
    private Dependency dependency;
    
    @Mock
    private AccessControlPolicy accessPolicy;
    
    @InjectMocks
    private MyService service;
    
    @BeforeEach
    void setUp() {
        // Setup common mocks
        when(accessPolicy.canAccess(anyString())).thenReturn(true);
    }
    
    @Test
    @DisplayName("Should deny access when user lacks permission")
    void shouldDenyAccessWhenUserLacksPermission() {
        // Arrange
        when(accessPolicy.canAccess("user-123")).thenReturn(false);
        
        // Act
        Result result = service.process("user-123", request);
        
        // Assert
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("ACCESS_DENIED");
        
        // Verify no execution happened
        verify(dependency, never()).execute(any());
    }
}
```

### Integration Test Standards

```java
@SpringBootTest
@ActiveProfiles("test")
class MyIntegrationTest {
    
    @Autowired
    private MyService service;
    
    @BeforeEach
    void setUp() {
        // Clean state before each test
        repository.deleteAll();
    }
    
    @Test
    void shouldExecuteEndToEndFlow() {
        // Arrange
        seedTestData();
        
        // Act
        Result result = service.execute("test query");
        
        // Assert
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
    }
}
```

### RealAPI Test Standards

```java
@SpringBootTest
@ActiveProfiles("realapi")
@Import(TestAccessControlPolicy.class)  // ← Provide required SPI
class MyRealApiTest {
    
    @Autowired
    private RAGOrchestrator orchestrator;
    
    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(
            System.getenv("OPENAI_API_KEY") != null,
            "OPENAI_API_KEY required for RealAPI tests"
        );
    }
    
    @Test
    void shouldExtractEntityTypesFromNaturalLanguage() {
        OrchestrationResult result = orchestrator.orchestrate(
            "relationship query: find products from Nike",
            OrchestrationContext.forUser("test-user")
        );
        
        assertThat(result.isSuccess()).isTrue();
        // Verify LLM extracted entity types correctly
    }
}
```

---

## ⚡ Performance Standards

### Rule 1: Cache Reflection

```java
@Slf4j
@Service
public class MyBuilder {
    private volatile Set<String> cachedEntityTypes = null;
    private volatile boolean cacheInitialized = false;
    
    private Set<String> getEntityTypes() {
        if (cacheInitialized) {
            return cachedEntityTypes != null ? cachedEntityTypes : Set.of();
        }
        
        synchronized (this) {
            if (cacheInitialized) {
                return cachedEntityTypes != null ? cachedEntityTypes : Set.of();
            }
            cachedEntityTypes = extractViaReflection();
            cacheInitialized = true;
            log.debug("Cached {} entity types", cachedEntityTypes.size());
            return cachedEntityTypes;
        }
    }
    
    private Set<String> extractViaReflection() {
        try {
            Class<?> clazz = Class.forName("com.ai.module.Mapper");
            Object bean = beanFactory.getBean(clazz);
            Method method = clazz.getMethod("getAllMappings");
            @SuppressWarnings("unchecked")
            Map<String, ?> mappings = (Map<String, ?>) method.invoke(bean);
            return mappings != null ? mappings.keySet() : Set.of();
        } catch (ClassNotFoundException ex) {
            log.debug("Module not present - expected when module not included");
            return Set.of();
        } catch (Exception ex) {
            log.warn("Reflection failed: {}. Using empty set.", ex.getMessage());
            return Set.of();
        }
    }
}
```

### Rule 2: Use ConcurrentHashMap for Mutable Caches

```java
@Service
public class EntityMapper {
    // Thread-safe cache
    private final ConcurrentMap<String, EntityMapping> mappings = new ConcurrentHashMap<>();
    
    public EntityMapping register(String type, Class<?> clazz) {
        EntityMapping mapping = new EntityMapping(type, clazz);
        return mappings.computeIfAbsent(type, k -> mapping);
    }
    
    public Map<String, EntityMapping> getAllMappings() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(mappings));
    }
}
```

---

## 📝 Common Patterns

### Pattern 1: ActionHandler Implementation

```java
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean({MyService.class, MyAccessControlPolicy.class})
@ConditionalOnProperty("feature.enabled", havingValue = "true", matchIfMissing = true)
public class MyActionHandler implements ActionHandler {
    
    private static final String ACTION_NAME = "my_action";
    
    // Parameter names
    private static final String PARAM_QUERY = "query";
    private static final String PARAM_OPTIONS = "options";
    
    // Error codes
    private static final String ERROR_ACCESS_DENIED = "ACCESS_DENIED";
    private static final String ERROR_INVALID_PARAMS = "INVALID_PARAMETERS";
    
    private final MyService service;
    private final MyAccessControlPolicy accessPolicy;
    
    @Override
    public AIActionMetaData getActionMetadata() {
        return AIActionMetaData.builder()
            .name(ACTION_NAME)
            .description("Executes my custom action")
            .category("my_category")
            .parameters(Map.of(
                PARAM_QUERY, "Natural language query (required)",
                PARAM_OPTIONS, "Additional options (optional)"
            ))
            .build();
    }
    
    @Override
    public ActionResult executeAction(Map<String, Object> params, String userId) {
        try {
            String query = requireParam(params, PARAM_QUERY);
            
            // Execute
            Result result = service.execute(query, userId);
            
            return ActionResult.builder()
                .success(result.isSuccess())
                .message(result.getMessage())
                .data(result.getData())
                .build();
                
        } catch (IllegalArgumentException ex) {
            return ActionResult.builder()
                .success(false)
                .message(ex.getMessage())
                .errorCode(ERROR_INVALID_PARAMS)
                .build();
        } catch (Exception ex) {
            return handleError(ex, userId);
        }
    }
    
    @Override
    public ActionResult handleError(Exception e, String userId) {
        log.error("Action execution failed for user {}", userId, e);
        return ActionResult.builder()
            .success(false)
            .message("Execution failed: " + e.getMessage())
            .errorCode("EXECUTION_FAILED")
            .build();
    }
    
    @Override
    public boolean validateActionAllowed(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }
        return accessPolicy.canUserExecuteAction(userId);
    }
    
    @Override
    public String getConfirmationMessage(Map<String, Object> params) {
        String query = requireParam(params, PARAM_QUERY);
        return "Execute action: \"" + query + "\"";
    }
    
    private String requireParam(Map<String, Object> params, String paramName) {
        Object value = params != null ? params.get(paramName) : null;
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("'" + paramName + "' parameter is required");
        }
        return value.toString();
    }
}
```

### Pattern 2: SPI Interface Definition

```java
// In core module: com.ai.infrastructure.spi
package com.ai.infrastructure.spi;

/**
 * SPI for providing custom access control logic for MyFeature.
 * 
 * <p>Framework users MUST implement this interface when MyFeature is enabled.
 * The application will fail to start if no implementation is provided.</p>
 * 
 * <p><strong>Example Implementation:</strong></p>
 * <pre>{@code
 * @Component
 * public class MyAccessPolicy implements MyFeatureAccessControlPolicy {
 *     private final PermissionService permissionService;
 *     
 *     @Override
 *     public boolean canUserExecuteFeature(String userId) {
 *         return permissionService.hasPermission(userId, "myfeature:execute");
 *     }
 * }
 * }</pre>
 * 
 * <p><strong>Note:</strong> When MyFeature is disabled, this policy is NOT required.</p>
 */
public interface MyFeatureAccessControlPolicy {
    
    /**
     * Check if the user can execute MyFeature at all.
     * 
     * @param userId User identifier (may be null for anonymous users)
     * @return true if user can execute feature, false otherwise
     */
    boolean canUserExecuteFeature(String userId);
    
    /**
     * Check if the user can access a specific resource within MyFeature.
     * 
     * @param userId User identifier
     * @param resourceId Resource identifier
     * @return true if user can access resource, false otherwise
     */
    boolean canUserAccessResource(String userId, String resourceId);
}
```

### Pattern 3: Configuration Properties

```java
@Data
@Validated
@ConfigurationProperties(prefix = "ai.infrastructure.myfeature")
public class MyFeatureProperties {
    
    /**
     * Enable MyFeature module.
     * Default: true
     */
    private boolean enabled = true;
    
    /**
     * Enable orchestrator integration via ActionHandler.
     * When enabled, users MUST provide MyFeatureAccessControlPolicy implementation.
     * Default: true
     */
    private boolean enableOrchestratorIntegration = true;
    
    /**
     * Enable advanced processing (may require additional resources).
     * LLM can activate advanced mode when needed if this is enabled.
     * Default: true
     */
    private boolean enableAdvancedMode = true;
    
    /**
     * Maximum processing timeout in seconds.
     */
    @Positive
    private int timeoutSeconds = 30;
    
    /**
     * Default limit for results.
     */
    @Min(1)
    @Max(1000)
    private int defaultLimit = 20;
}
```

---

## 🚫 Anti-Patterns to Avoid

### Anti-Pattern 1: Silent Filtering

```java
// ❌ NEVER do this:
List<Item> filtered = requested.stream()
    .filter(item -> hasAccess(user, item))
    .collect(toList());
return filtered;  // User requested 10, got 3, doesn't know 7 were denied

// ✅ ALWAYS do this:
if (filtered.size() < requested.size()) {
    return Result.accessDenied("Some items were unauthorized", denied);
}
```

### Anti-Pattern 2: Test Code in Production

```java
// ❌ NEVER in src/main/java:
public MyClass(Dep1 dep1) { }  // Convenience for tests

@VisibleForTesting
void helper() { }

public void resetForTesting() { }

// ✅ Keep production clean:
// Single @Autowired constructor only
// No @VisibleForTesting
// No test-specific methods
```

### Anti-Pattern 3: Configuration Overriding LLM

```java
// ❌ NEVER:
if (config.getMode() != null) {
    return config.getMode();  // Blind override
}
return llm.analyze();

// ✅ ALWAYS:
Mode llmDecision = llm.analyze();
if (llmDecision == ADVANCED && !config.advancedEnabled()) {
    return BASIC;  // Configuration constrains
}
return llmDecision;  // LLM decides
```

### Anti-Pattern 4: Redundant Fallbacks

```java
// ❌ NEVER have fallbacks in multiple layers:
// Component A:
if (data.isEmpty()) {
    data = fallback();  // Fallback 1
}

// Component B (calling A):
result = componentA.process();
if (result.isEmpty()) {
    result = fallback();  // Fallback 2 ← Redundant!
}

// ✅ Single fallback in the right place:
// Component A guarantees non-empty
// Component B trusts Component A
```

### Anti-Pattern 5: Uncached Reflection

```java
// ❌ NEVER:
public Data getData() {
    Class<?> clazz = Class.forName("...");  // Every call!
    return invoke(clazz);
}

// ✅ ALWAYS cache:
private volatile Data cachedData = null;
public Data getData() {
    if (cachedData != null) return cachedData;
    synchronized (this) {
        if (cachedData != null) return cachedData;
        cachedData = reflectionCall();
        return cachedData;
    }
}
```

---

## 📚 Code Examples

### Example 1: Complete Handler with Access Control

See `RelationshipQueryActionHandler.java` for full example.

Key elements:
- Constants for all strings
- Required access control policy
- Fail-closed security
- Clear error messages
- No test code
- Comprehensive JavaDoc

### Example 2: System Context Builder

See `SystemContextBuilder.java` for full example.

Key elements:
- Application-level caching
- Reflection with error handling
- Optional dependencies via ObjectProvider
- Clean logging

### Example 3: Response Sanitizer

See `ResponseSanitizer.java` for full example.

Key elements:
- Recursive sanitization
- PII detection integration
- Guaranteed non-empty payload
- Comprehensive risk assessment

---

## ✅ Pre-Commit Checklist

Before generating/committing code, verify:

### Security
- [ ] Access control enforced (fail-closed)?
- [ ] No silent filtering?
- [ ] Required SPIs have `@ConditionalOnBean`?
- [ ] Security events logged at WARN level?

### Architecture
- [ ] No test code in `src/main/java`?
- [ ] Single `@Autowired` constructor?
- [ ] SPIs defined in correct module?
- [ ] Optional dependencies use `ObjectProvider`?

### Code Quality  
- [ ] All string literals extracted to constants?
- [ ] Public methods have comprehensive JavaDoc?
- [ ] Null safety for external inputs?
- [ ] Appropriate log levels?

### LLM Integration
- [ ] LLM receives clear instructions?
- [ ] LLM decisions respected?
- [ ] User intent separated from app config?
- [ ] Validation for LLM-extracted parameters?

### Performance
- [ ] Expensive operations cached?
- [ ] Reflection results cached at application level?
- [ ] Thread-safe caching (volatile + synchronized)?
- [ ] Cache initialization logged?

### Testing
- [ ] Unit tests with mocks?
- [ ] Integration tests for flows?
- [ ] RealAPI tests for LLM integration?
- [ ] Test names descriptive (should...)?

---

## 🎯 Module-Specific Rules

### For ai-infrastructure-core:

1. **SPI interfaces go here** (com.ai.infrastructure.spi)
2. **No dependencies on relationship-query or behavior modules**
3. **Use reflection + ObjectProvider for optional modules**
4. **Orchestration logic stays in RAGOrchestrator**

### For ai-infrastructure-relationship-query:

1. **Must implement RelationshipQueryAccessControlPolicy SPI**
2. **LLM planner determines queryStrategy and needsSemanticSearch**
3. **Configuration: `enable-vector-search` controls ENHANCED mode**
4. **Entity types ALWAYS from registered EntityRelationshipMapper**

### For Action Handlers:

1. **Implement all 5 ActionHandler methods**
2. **Require access control policy via `@ConditionalOnBean`**
3. **Extract all parameter names to constants**
4. **Validate all inputs, clear error messages**

---

## 🔍 Code Review Template

Use this when reviewing generated code:

```markdown
## File: [filename]

### ✅ Passes
- [ ] No magic strings
- [ ] No test code in production
- [ ] Fail-closed security
- [ ] LLM decisions respected
- [ ] Comprehensive JavaDoc
- [ ] Appropriate caching
- [ ] Required dependencies enforced

### ❌ Issues Found
[List specific violations with line numbers]

### 🔧 Fixes Required
[Specific code changes needed]

### 📊 Rating: [1-10]/10
### 🚀 Production Ready: [Yes/No]
```

---

## 🎓 Key Takeaways

1. **Greenfield = No Legacy Baggage**
   - Remove deprecated code immediately
   - No backward compatibility
   - Modern patterns only

2. **Security = Fail-Closed**
   - If ANY part unauthorized → Deny ALL
   - Transparent error messages
   - Audit logging

3. **LLM = Intelligent Decision Maker**
   - LLM analyzes specific queries
   - Respect LLM's decisions
   - Configuration constrains, not overrides

4. **Production = Pure**
   - No test code in `src/main/java`
   - Single `@Autowired` constructor
   - Tests handle their own setup

5. **Performance = Cache Smart**
   - Application-level caching
   - Thread-safe (volatile + synchronized)
   - Log initialization

6. **Quality = Constants + JavaDoc + Tests**
   - No magic strings
   - Comprehensive documentation
   - Unit + integration + RealAPI tests

---

## 📖 Reference Documents

- `CODE_REVIEW_PROMPT.md` - Detailed code review guidelines
- `ARCHITECTURE_AND_DEVELOPMENT_DECISIONS.md` - Architectural decisions
- `PROJECT_GUIDELINES.yaml` - Project standards
- Module READMEs - Module-specific documentation

---

## 🚀 Getting Started Generating Code

When asked to implement a feature:

1. **Understand the module** - Which module does this belong to?
2. **Check for SPIs** - Does this need a new SPI or use existing?
3. **Security first** - What access control is needed?
4. **LLM integration** - Will LLM make decisions?
5. **Follow patterns** - Use existing patterns as templates
6. **Test comprehensively** - Unit + integration + RealAPI
7. **Document thoroughly** - JavaDoc on everything public

---

**Remember:** This is an open-source framework. Your code will be used by thousands of developers in production systems. Quality, security, and clarity are paramount.

**Be strict. Be clear. Be secure. Be performant. Be consistent.**

---

**Generated:** 2026-01-04  
**Version:** 1.0  
**Maintainer:** AI Infrastructure Team

