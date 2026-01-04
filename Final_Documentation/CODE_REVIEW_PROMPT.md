# AI Infrastructure Framework - Code Review Philosophy & Guidelines

## Context

You are reviewing code for an **open-source AI infrastructure framework** that other applications will integrate. This framework provides LLM-powered capabilities for orchestration, relationship queries, behavior analysis, and RAG (Retrieval-Augmented Generation).

## Framework Philosophy

### Core Principles

1. **Greenfield Architecture**
   - No backward compatibility constraints
   - No legacy support
   - Clean, modern design decisions
   - Remove deprecated patterns immediately

2. **Security-First (Fail-Closed Model)**
   - If ANY part of a request is unauthorized → DENY the entire request
   - Never silently filter/modify user requests
   - Transparent error messages showing what was denied
   - Log all security decisions for audit

3. **LLM-Driven Intelligence**
   - LLM analyzes queries and makes intelligent decisions
   - Respect LLM's analysis (don't blindly override)
   - Configuration provides CONSTRAINTS, not OVERRIDES
   - LLM decisions are based on actual query analysis

4. **Clean Separation of Concerns**
   - Production code in `src/main/java` - NO test-specific code
   - Configuration in YAML - not hardcoded
   - User intent (from LLM) ≠ Application configuration
   - Each layer has clear responsibility

5. **Performance & Caching**
   - Cache expensive operations (reflection, LLM calls)
   - Application-level caching (singleton beans)
   - Thread-safe implementations (volatile, ConcurrentHashMap)
   - Log cache initialization for debugging

6. **Extensibility via SPI**
   - Framework provides interfaces
   - Users implement SPIs for custom behavior
   - Required SPIs fail fast at startup if missing
   - No default implementations that hide security issues

---

## Code Review Checklist

### 1. Security & Access Control

#### ❌ **REJECT** - Violations:
```java
// Silent filtering (security violation)
List<String> allowed = filter(requested);
if (allowed.size() < requested.size()) {
    log.info("Filtered some items");  // ← Logs but continues!
    execute(allowed);  // ← Executes partial request ❌
}

// Permissive defaults
public boolean hasAccess(String userId) {
    if (policy == null) {
        return true;  // ← Allow all by default ❌
    }
}

// Missing audit logs
if (!authorized) {
    return false;  // ← No log of denial ❌
}
```

#### ✅ **ACCEPT** - Correct patterns:
```java
// Fail-closed security
List<String> allowed = filter(requested);
if (allowed.size() < requested.size()) {
    List<String> denied = getDenied(requested, allowed);
    log.warn("Access denied for user {}: {}", userId, denied);
    return Result.accessDenied(
        "You do not have permission for some requested items",
        Map.of(
            "requested", requested,
            "denied", denied
        )
    );  // ← Deny entire request ✅
}

// Required dependencies
@ConditionalOnBean(AccessControlPolicy.class)  // ← Fails if missing ✅
private final AccessControlPolicy policy;  // ← Required, not Optional ✅

// Comprehensive audit logging
log.warn("Access denied: user {} requested unauthorized items: {}", 
    userId, denied);  // ← Clear audit trail ✅
```

### 2. LLM Integration & Decision Making

#### ❌ **REJECT** - Violations:
```java
// Blind overrides that ignore LLM analysis
if (appConfig.getMode() != null) {
    return appConfig.getMode();  // ← Ignores LLM's decision ❌
}

// No instructions to LLM
prompt.append("Extract data from query");  // ← Vague ❌

// Application guessing what LLM should extract
if (query.contains("limit")) {
    params.put("limit", 10);  // ← App guessing, not LLM extracting ❌
}
```

#### ✅ **ACCEPT** - Correct patterns:
```java
// LLM decides, configuration constrains
if (llm.needsSemanticSearch() &&  // ← LLM's intelligent decision
    config.vectorSearchEnabled() &&  // ← Configuration constraint
    system.hasVectorDB()) {  // ← System availability
    return Mode.ENHANCED;  // ← Combines all factors ✅
}

// Clear LLM instructions
prompt.append("8. When action == \"relationship_query\", extract entityTypes from the user request as an array of lower-case strings. ");
prompt.append("Available entity types: " + entityTypes);
prompt.append("Only use types from this list.");  // ← Specific instructions ✅

// Validate LLM extraction
if (intent.getEntityTypes() == null) {
    log.warn("LLM did not extract entityTypes - using fallback");
    intent.setEntityTypes(List.of());  // ← Explicit fallback ✅
}
```

### 3. Configuration vs Runtime Parameters

#### ❌ **REJECT** - Mixing concerns:
```java
// Mixing user intent with technical config in same map
Map<String, Object> params = Map.of(
    "query", "find users",        // ← User intent (from LLM)
    "entityTypes", ["user"],      // ← User intent (from LLM)
    "queryMode", "ENHANCED",      // ← Technical config (NOT from user) ❌
    "cacheEnabled", true          // ← Technical config (NOT from user) ❌
);

// Configuration in code
private static final int LIMIT = 20;  // ← Should be in YAML ❌
```

#### ✅ **ACCEPT** - Clean separation:
```java
// LLM-extracted parameters (from user query)
Intent {
    query: "find users",
    entityTypes: ["user"]  // ← From user's natural language
}

// Application configuration (from YAML or code)
QueryOptions {
    limit: 20,              // ← Application config
    returnMode: IDS,        // ← Application config
    disableEnhancedMode: false  // ← Application constraint
}

// Configuration in YAML
ai:
  infrastructure:
    relationship:
      enable-vector-search: true  // ← Configuration ✅
      default-limit: 20           // ← Configuration ✅
```

### 4. Production Code Purity

#### ❌ **REJECT** - Test code in production:
```java
// In src/main/java
@Service
public class MyService {
    // Constructor for production
    public MyService(Dep1 dep1, Dep2 dep2) { }
    
    // Convenience constructor for tests ❌
    public MyService(Dep1 dep1) {
        this(dep1, null);
    }
}

// Test-specific methods
public void resetForTesting() { }  // ❌

// @VisibleForTesting in production code
@VisibleForTesting
void helperMethod() { }  // ❌
```

#### ✅ **ACCEPT** - Clean production code:
```java
// In src/main/java - single @Autowired constructor
@Service
public class MyService {
    @Autowired
    public MyService(Dep1 dep1, Dep2 dep2, Dep3 dep3) {
        // Production constructor only ✅
    }
}

// In src/test/java - tests handle their own setup
@Test
void myTest() {
    Dep1 dep1 = mock(Dep1.class);
    Dep2 dep2 = mock(Dep2.class);
    Dep3 dep3 = mock(Dep3.class);
    MyService service = new MyService(dep1, dep2, dep3);  // ✅
}
```

### 5. Magic Strings & Constants

#### ❌ **REJECT** - Magic strings everywhere:
```java
params.get("limit");  // ← Magic string ❌
if (type.equals("customer")) { }  // ← Magic string ❌
data.put("totalResults", 10);  // ← Magic string ❌
Map.of("error", "ACCESS_DENIED");  // ← Both magic strings ❌
```

#### ✅ **ACCEPT** - Constants:
```java
// At class level
private static final String PARAM_LIMIT = "limit";
private static final String ENTITY_TYPE_CUSTOMER = "customer";
private static final String DATA_KEY_TOTAL_RESULTS = "totalResults";
private static final String ERROR_ACCESS_DENIED = "ACCESS_DENIED";
private static final int DEFAULT_LIMIT = 20;

// Usage
params.get(PARAM_LIMIT);  // ✅
if (type.equals(ENTITY_TYPE_CUSTOMER)) { }  // ✅
data.put(DATA_KEY_TOTAL_RESULTS, count);  // ✅
Map.of(DATA_KEY_ERROR, ERROR_ACCESS_DENIED);  // ✅
```

### 6. Caching Strategy

#### ❌ **REJECT** - Inefficient or incorrect caching:
```java
// No caching for expensive operations
public Set<String> getEntityTypes() {
    return reflectionCall();  // ← Called every time ❌
}

// Request-scoped cache (wrong level)
@Scope("request")
@Component
class MyCache { }  // ← Should be application-level ❌

// Not thread-safe
private Set<String> cache = null;  // ← Not volatile ❌
public Set<String> getCached() {
    if (cache == null) {
        cache = load();  // ← Race condition ❌
    }
    return cache;
}
```

#### ✅ **ACCEPT** - Proper caching:
```java
// Application-level cache with double-checked locking
@Service  // ← Singleton (application scope) ✅
public class MyService {
    private volatile Set<String> cachedData = null;  // ← volatile ✅
    private volatile boolean cacheInitialized = false;
    
    public Set<String> getData() {
        if (cacheInitialized) {
            return cachedData != null ? cachedData : Set.of();  // ← Fast path ✅
        }
        
        synchronized (this) {  // ← Thread-safe initialization
            if (cacheInitialized) {
                return cachedData != null ? cachedData : Set.of();
            }
            cachedData = expensiveOperation();  // ← Only once
            cacheInitialized = true;
            log.debug("Cached {} items", cachedData.size());
            return cachedData;
        }
    }
}

// ConcurrentHashMap for mutable caches
private final ConcurrentMap<String, Data> cache = new ConcurrentHashMap<>();  // ✅
```

### 7. Error Handling & Fallbacks

#### ❌ **REJECT** - Masking bugs:
```java
// Silent fallback that hides bugs
Map<String, Object> payload = sanitizer.sanitize(result);
if (payload.isEmpty()) {
    payload = createFallback();  // ← Hides sanitizer bug ❌
}

// Catching and ignoring errors
try {
    criticalOperation();
} catch (Exception ex) {
    return defaultValue;  // ← Silent failure ❌
}

// Duplicate fallback logic
// ResponseSanitizer has fallback AND orchestrator has fallback ❌
```

#### ✅ **ACCEPT** - Fail fast, fix bugs:
```java
// Fail fast - surface bugs
if (result == null) {
    log.error("Operation produced null result - this is a bug");
    return OrchestrationResult.error("Internal error");  // ← Fail fast ✅
}

Map<String, Object> payload = sanitizer.sanitize(result);
// Trust sanitizer - it guarantees non-empty (has its own fallback)

// Log unexpected errors
try {
    operation();
} catch (ClassNotFoundException ex) {
    log.debug("Optional module not present");  // ← Expected ✅
    return Optional.empty();
} catch (Exception ex) {
    log.warn("Unexpected error: {}. Using fallback.", ex.getMessage());  // ← Log ✅
    return fallback();
}

// Single fallback in right place
// ResponseSanitizer guarantees non-empty - orchestrator trusts it ✅
```

### 8. SPI (Service Provider Interface) Pattern

#### ❌ **REJECT** - Weak SPI boundaries:
```java
// Optional with permissive default
@Autowired(required = false)
private AccessControlPolicy policy;

public boolean checkAccess(String userId) {
    if (policy == null) {
        return true;  // ← Allow all if no policy ❌
    }
    return policy.check(userId);
}

// Interface in wrong module
// AccessControlPolicy defined in relationship-query module ❌
// Should be in core for SPI pattern
```

#### ✅ **ACCEPT** - Strong SPI boundaries:
```java
// Interface in core (SPI definition)
package com.ai.infrastructure.spi;
public interface AccessControlPolicy {
    boolean canAccess(String userId, String resource);
}

// Required when feature is enabled
@ConditionalOnBean(AccessControlPolicy.class)  // ← Required ✅
@ConditionalOnProperty("feature.enabled", havingValue = "true")
public class MyHandler {
    private final AccessControlPolicy policy;  // ← Required field ✅
}

// No default - users must implement
// If feature enabled and no policy → startup fails ✅
```

### 9. Null Safety & Defensive Programming

#### ❌ **REJECT** - Unsafe assumptions:
```java
// No null checks
data.put("key", response.getValue());  // ← Might be null ❌
return response.getItems().size();  // ← NPE if null ❌

// Defensive programming that masks bugs
if (result == null) {
    result = new Result();  // ← Why is it null? ❌
}
```

#### ✅ **ACCEPT** - Null safety done right:
```java
// Null safety with clear semantics
if (response.getValue() != null) {
    data.put("key", response.getValue());  // ✅
}

// Fail fast when null is unexpected
if (result == null) {
    log.error("Result should never be null - this is a bug");
    throw new IllegalStateException("Internal error");  // ✅
}

// Null checks with purpose
if (response.getItems() != null && !response.getItems().isEmpty()) {
    return response.getItems().size();
}
return 0;  // ← Explicit default ✅
```

### 10. Documentation Requirements

#### ❌ **REJECT** - Poor documentation:
```java
// No JavaDoc
public class MyService { }

// Vague JavaDoc
/**
 * Does something.
 */
public void process() { }

// Missing parameter docs
/**
 * Processes data.
 */
public Result process(String query, List<String> types) { }  // ❌
```

#### ✅ **ACCEPT** - Comprehensive documentation:
```java
/**
 * Service for processing relationship queries using LLM-driven JPQL generation.
 * 
 * <p>This service combines relational database queries with optional vector reranking
 * based on the LLM's analysis of query complexity.</p>
 * 
 * <p><strong>Thread Safety:</strong> This is a singleton Spring bean. All methods are thread-safe.</p>
 * 
 * @see RelationshipQueryPlanner for query analysis
 * @see AccessControlPolicy for security integration
 */
@Service
public class MyService {
    
    /**
     * Processes a relationship query and returns matching entities.
     * 
     * <p>The LLM analyzes the query to determine:
     * <ul>
     *   <li>Which entity types are involved</li>
     *   <li>What relationships to traverse</li>
     *   <li>Whether semantic search would improve results</li>
     * </ul>
     * </p>
     * 
     * @param query Natural language query (e.g., "find premium customers who ordered this month")
     * @param entityTypes Entity types to search (extracted by LLM from query)
     * @param options Query options for pagination and output format (application-provided)
     * @return Response containing matching documents
     * @throws IllegalArgumentException if query is null or blank
     * @throws AccessDeniedException if user lacks permission for entity types
     */
    public Result process(String query, List<String> entityTypes, QueryOptions options) {
        // Implementation
    }
}
```

### 11. Reflection & Dynamic Loading

#### ❌ **REJECT** - Inefficient reflection:
```java
// Reflection on every call
public Set<String> getEntityTypes() {
    Class<?> clazz = Class.forName("com.example.Mapper");  // ← Every time ❌
    Object bean = beanFactory.getBean(clazz);
    return (Set<String>) method.invoke(bean);
}

// Silent failures
try {
    return reflectionCall();
} catch (Exception ex) {
    return Set.of();  // ← No logging ❌
}

// Hard dependency on optional module
import com.ai.relationship.EntityMapper;  // ← Breaks without module ❌
```

#### ✅ **ACCEPT** - Cached reflection with logging:
```java
// Cached reflection (application-level)
@Slf4j
@Service
public class MyService {
    private volatile Set<String> cachedTypes = null;
    private volatile boolean cacheInitialized = false;
    
    private Set<String> getEntityTypes() {
        if (cacheInitialized) {
            return cachedTypes != null ? cachedTypes : Set.of();  // ✅
        }
        
        synchronized (this) {
            if (cacheInitialized) {
                return cachedTypes != null ? cachedTypes : Set.of();
            }
            cachedTypes = extractViaReflection();
            cacheInitialized = true;
            log.debug("Cached {} entity types", cachedTypes.size());  // ✅
            return cachedTypes;
        }
    }
    
    private Set<String> extractViaReflection() {
        try {
            Class<?> clazz = Class.forName("com.ai.relationship.EntityMapper");
            // ... reflection logic
        } catch (ClassNotFoundException ex) {
            log.debug("Module not present");  // ← Expected case ✅
            return Set.of();
        } catch (Exception ex) {
            log.warn("Unexpected reflection error: {}", ex.getMessage());  // ← Log unexpected ✅
            return Set.of();
        }
    }
}
```

### 12. Constants & Magic Numbers

#### ❌ **REJECT** - Magic strings/numbers:
```java
params.get("query");  // ← String appears 15 times ❌
if (count > 100) { }  // ← What is 100? ❌
data.put("success", true);  // ← String keys everywhere ❌
```

#### ✅ **ACCEPT** - Named constants:
```java
// Group related constants
private static final class ParamNames {
    static final String QUERY = "query";
    static final String ENTITY_TYPES = "entityTypes";
    static final String LIMIT = "limit";
}

private static final class ErrorCodes {
    static final String ACCESS_DENIED = "ACCESS_DENIED";
    static final String INVALID_PARAMS = "INVALID_PARAMETERS";
}

private static final class DataKeys {
    static final String SUCCESS = "success";
    static final String DOCUMENTS = "documents";
}

// Named limits with clear purpose
private static final int MAX_QUERY_LENGTH = 1000;  // Prevent DOS attacks
private static final int DEFAULT_RESULT_LIMIT = 20;  // Balance UX and performance
private static final int MAX_TRAVERSAL_DEPTH = 5;  // Prevent infinite loops
```

### 13. Dependency Injection

#### ❌ **REJECT** - Wrong patterns:
```java
// Field injection
@Autowired
private MyService service;  // ← Field injection ❌

// Multiple constructors in production
public MyClass(Dep1 dep1) { }  // ← For tests ❌
public MyClass(Dep1 dep1, Dep2 dep2) { }  // ← For production ❌

// Optional with null fallback in production logic
@Autowired(required = false)
private PolicyProvider policy;
if (policy == null) {
    policy = new DefaultPolicy();  // ← Create in code ❌
}
```

#### ✅ **ACCEPT** - Constructor injection:
```java
// Single @Autowired constructor
@Service
@RequiredArgsConstructor  // ← Lombok generates constructor ✅
public class MyClass {
    private final Dep1 dep1;
    private final Dep2 dep2;
    private final Dep3 dep3;
}

// Optional dependencies via ObjectProvider
@Service
public class MyClass {
    private final Optional<PolicyProvider> policy;
    
    @Autowired
    public MyClass(ObjectProvider<PolicyProvider> policyProvider) {
        this.policy = Optional.ofNullable(policyProvider.getIfAvailable());  // ✅
    }
}

// Required SPI with conditional registration
@ConditionalOnBean(PolicyProvider.class)  // ← Required when feature enabled ✅
public class MyFeature {
    private final PolicyProvider policy;  // ← Always present here ✅
}
```

### 14. Error Messages & User Experience

#### ❌ **REJECT** - Cryptic errors:
```java
return Result.error("Invalid");  // ← What's invalid? ❌
return Result.error("Error 403");  // ← No context ❌
throw new RuntimeException("Failed");  // ← No details ❌
```

#### ✅ **ACCEPT** - Clear, actionable errors:
```java
return ActionResult.builder()
    .success(false)
    .message("Access denied: You do not have permission to query entity types: " + denied)
    .errorCode("ACCESS_DENIED")
    .data(Map.of(
        "requestedEntityTypes", requested,
        "allowedEntityTypes", allowed,
        "deniedEntityTypes", denied
    ))
    .build();  // ✅ User knows exactly what failed and why

throw new IllegalArgumentException(
    "'" + PARAM_ENTITY_TYPES + "' must be a List<String> or String, " +
    "but was: " + value.getClass().getSimpleName()
);  // ✅ Clear error with type info
```

### 15. Logging Levels

#### ❌ **REJECT** - Wrong log levels:
```java
log.info("Caching started");  // ← Internal detail ❌
log.error("Optional module not found");  // ← Expected case ❌
log.debug("Access denied for user");  // ← Security event ❌
```

#### ✅ **ACCEPT** - Appropriate levels:
```java
log.debug("Caching started");  // ← Internal detail ✅
log.debug("Optional module not found");  // ← Expected case ✅
log.warn("Access denied: user {} requested unauthorized resource", userId);  // ← Security ✅
log.error("Unexpected null result - this is a bug");  // ← Actual error ✅
log.info("Relationship query module initialized with {} entity types", count);  // ← Useful info ✅
```

---

## Review Process

### For Each File:

1. **Check Security**
   - [ ] Fail-closed model enforced?
   - [ ] No silent filtering?
   - [ ] Access denied = entire request denied?
   - [ ] Audit logs for security decisions?

2. **Check LLM Integration**
   - [ ] LLM receives clear instructions?
   - [ ] LLM decisions respected?
   - [ ] Configuration constrains, not overrides?
   - [ ] User intent separated from app config?

3. **Check Code Quality**
   - [ ] No test code in production?
   - [ ] No magic strings?
   - [ ] Constants for all literals?
   - [ ] Proper JavaDoc?

4. **Check Architecture**
   - [ ] Single @Autowired constructor?
   - [ ] Required dependencies fail fast?
   - [ ] SPI interfaces in core module?
   - [ ] Reflection cached?

5. **Check Error Handling**
   - [ ] Clear error messages?
   - [ ] Fail fast on bugs?
   - [ ] No redundant fallbacks?
   - [ ] Appropriate log levels?

---

## Common Anti-Patterns to Flag

### 🚫 Anti-Pattern 1: "Silent Filter"
```java
// User requests A+B+C, only allowed A
// System returns results for A without telling user B+C were denied
```
**Fix:** Deny entire request, return clear error with denied items

### 🚫 Anti-Pattern 2: "Configuration Override"
```java
// App setting overrides LLM's intelligent analysis
if (appSetting != null) return appSetting;  // Ignores LLM
```
**Fix:** LLM decides, configuration constrains

### 🚫 Anti-Pattern 3: "Test Constructor in Production"
```java
// src/main/java has constructor "for testing"
public MyClass(Dep1 dep1) { }  // Convenience for tests
```
**Fix:** Remove, tests use full constructor with mocks

### 🚫 Anti-Pattern 4: "Masking Bugs with Fallbacks"
```java
// Component X should guarantee Y, but component Z has fallback for when X fails
```
**Fix:** Fix component X, remove component Z's fallback

### 🚫 Anti-Pattern 5: "Magic Strings Everywhere"
```java
params.get("entityTypes")  // Appears 20 times
```
**Fix:** `private static final String PARAM_ENTITY_TYPES = "entityTypes"`

### 🚫 Anti-Pattern 6: "Uncached Reflection"
```java
// Reflection call on every invocation
public Data getData() {
    return (Data) reflectionCall();  // Every time!
}
```
**Fix:** Cache at application level with double-checked locking

### 🚫 Anti-Pattern 7: "Permissive Defaults for Security"
```java
// No policy? Allow everything!
if (policy == null) return true;
```
**Fix:** Required policy with `@ConditionalOnBean`, fail at startup

---

## Framework-Specific Rules

### Rule 1: Entity Types
- Extracted by LLM from user query
- Validated against registered entity types
- ALL requested types must be allowed (fail-closed)
- Never silently filter entity types

### Rule 2: Query Mode
- Determined by LLM's `needsSemanticSearch` flag
- Configuration can PREVENT Enhanced mode (enable-vector-search=false)
- Configuration cannot FORCE Enhanced mode when LLM says not needed
- One config controls it: `enable-vector-search`

### Rule 3: Access Control
- `RelationshipQueryAccessControlPolicy` is REQUIRED when orchestrator integration enabled
- No default implementation
- Application fails to start if missing
- All access decisions logged

### Rule 4: Parameters
**LLM Extracts (from user natural language):**
- `query` - the question
- `entityTypes` - which entities to search

**Application Provides (configuration/code):**
- `limit` - pagination
- `returnMode` - IDS vs FULL
- `similarityThreshold` - vector search tuning

### Rule 5: Caching
- Reflection results: cached at application level
- Entity mappings: ConcurrentHashMap
- LLM plans: configurable TTL cache
- Thread-safe (volatile + synchronized or ConcurrentHashMap)

---

## Review Template

When reviewing code, use this template:

```
## File: [filename]

### Security Review
- [ ] Fail-closed model enforced?
- [ ] Access control properly integrated?
- [ ] No silent filtering?
- [ ] Security events logged?

### Architecture Review
- [ ] No test code in production?
- [ ] SPI pattern correctly used?
- [ ] Dependencies properly injected?
- [ ] Reflection cached if used?

### Code Quality Review
- [ ] No magic strings (all constants)?
- [ ] Proper JavaDoc on public methods?
- [ ] Clear error messages?
- [ ] Appropriate log levels?

### LLM Integration Review
- [ ] LLM decisions respected?
- [ ] Clear instructions in prompts?
- [ ] User intent separated from app config?
- [ ] Configuration provides constraints?

### Issues Found:
[List any violations]

### Recommendations:
[Specific fixes]

### Overall Rating: [1-10]
### Production Ready: [Yes/No]
```

---

## Examples of Good vs Bad

### Example 1: Access Control

**❌ BAD:**
```java
List<String> filtered = requestedTypes.stream()
    .filter(type -> policy.canAccess(userId, type))
    .collect(toList());
return execute(filtered);  // Silent filtering
```

**✅ GOOD:**
```java
List<String> allowed = filter(userId, requestedTypes);
if (allowed.size() < requestedTypes.size()) {
    List<String> denied = getDenied(requestedTypes, allowed);
    log.warn("Access denied: user {} requested unauthorized types: {}", userId, denied);
    return Result.accessDenied("Some entity types are not allowed", 
        Map.of("denied", denied, "requested", requestedTypes));
}
return execute(allowed);
```

### Example 2: LLM Decision Respect

**❌ BAD:**
```java
if (options.getMode() != null) {
    return options.getMode();  // Blind override
}
return llm.getRecommendedMode();
```

**✅ GOOD:**
```java
QueryMode llmRecommendation = llm.analyze(query);
if (llmRecommendation == ENHANCED && !config.vectorSearchEnabled()) {
    log.info("LLM recommended ENHANCED but config disables it");
    return STANDALONE;
}
return llmRecommendation;  // Respect LLM unless constrained
```

### Example 3: Constants

**❌ BAD:**
```java
params.get("query");
params.get("entityTypes");
data.put("results", list);
if (mode.equals("ENHANCED")) { }
```

**✅ GOOD:**
```java
private static final String PARAM_QUERY = "query";
private static final String PARAM_ENTITY_TYPES = "entityTypes";
private static final String DATA_KEY_RESULTS = "results";
private static final QueryMode MODE_ENHANCED = QueryMode.ENHANCED;

params.get(PARAM_QUERY);
params.get(PARAM_ENTITY_TYPES);
data.put(DATA_KEY_RESULTS, list);
if (mode == MODE_ENHANCED) { }
```

---

## Key Questions to Ask During Review

1. **Security**: "If a user requests unauthorized data, does the system deny the ENTIRE request or return partial results?"

2. **LLM Decisions**: "Does the application override the LLM's analysis, or does it provide constraints?"

3. **Separation**: "Is this parameter from the user (via LLM) or from the application (config)?"

4. **Production Purity**: "Is there test-specific code in src/main/java?"

5. **Performance**: "Is expensive reflection/computation cached at application level?"

6. **Fail Fast**: "Does this fallback hide a bug, or handle an expected case?"

7. **Clarity**: "If this code fails in production, will the error message help debug it?"

8. **SPI Boundaries**: "Is the interface in the right module? Is the implementation required or optional?"

---

## Final Mindset

This is a **framework** that other developers will use. Every design decision affects:
- **Security** of downstream applications
- **Performance** of production systems
- **Maintainability** of long-term codebases
- **Developer experience** of framework users

**Priorities:**
1. **Correctness** > Convenience
2. **Security** > Features
3. **Clarity** > Cleverness
4. **Fail-fast** > Silent degradation
5. **Explicit** > Implicit

**Remember:** In an open-source framework:
- Bad patterns get copied to thousands of applications
- Security flaws affect all users
- Performance issues compound across all deployments
- Poor documentation blocks adoption

**Be strict. Be clear. Be secure. Be performant.**

---

**Use this prompt** to review any code in this framework and ensure it follows our established philosophy.

