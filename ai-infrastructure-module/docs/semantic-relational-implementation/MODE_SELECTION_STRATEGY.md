# Mode Selection Strategy: Configuration vs Query Parameters

## 🤔 The Question

**Should standalone/enhanced mode be:**
1. **Configuration-based** (global setting)
2. **Query parameter-based** (per-query decision)
3. **Both** (configuration default + query override)

---

## 📊 Analysis: Configuration-Based

### **Approach: Global Configuration**

```yaml
ai:
  infrastructure:
    relationship:
      mode: standalone  # or "enhanced"
      # OR
      enable-vector-search: false
```

**Usage:**
```java
// All queries use same mode
queryService.executeQuery("Find orders...");
// Always uses configured mode
```

### **Pros:**
- ✅ **Simple API** - No parameters needed
- ✅ **Consistent behavior** - All queries same mode
- ✅ **Clear system-wide setting** - Easy to understand
- ✅ **Performance** - No per-query decision logic
- ✅ **Easier to reason about** - Predictable behavior

### **Cons:**
- ❌ **Less flexible** - Can't mix modes
- ❌ **Rigid** - All queries must use same approach
- ❌ **Can't optimize per query** - Some queries benefit from vectors, others don't

---

## 📊 Analysis: Query Parameter-Based

### **Approach: Per-Query Decision**

```java
// Each query specifies mode
queryService.executeQuery(
    "Find orders...",
    QueryOptions.builder()
        .mode(QueryMode.STANDALONE)  // or ENHANCED
        .build()
);

// Or simpler
queryService.executeQuery(
    "Find orders...",
    QueryMode.STANDALONE
);
```

**Usage:**
```java
// Query 1: Standalone
queryService.executeQuery("Find orders from active customers", QueryMode.STANDALONE);

// Query 2: Enhanced
queryService.executeQuery("Find similar products", QueryMode.ENHANCED);
```

### **Pros:**
- ✅ **Maximum flexibility** - Choose mode per query
- ✅ **Optimization** - Use best mode for each query
- ✅ **Cost control** - Use vectors only when needed
- ✅ **Progressive enhancement** - Start simple, add complexity

### **Cons:**
- ❌ **More complex API** - Need to specify mode each time
- ❌ **Easy to misuse** - Wrong mode for query
- ❌ **More parameters** - Clutters API
- ❌ **Harder to reason about** - Different queries behave differently

---

## 📊 Analysis: Hybrid Approach (Both) ✅ **RECOMMENDED**

### **Approach: Configuration Default + Query Override**

```yaml
ai:
  infrastructure:
    relationship:
      default-mode: standalone  # Default for all queries
      enable-vector-search: false  # Default vector support
```

```java
// Uses default mode (from config)
queryService.executeQuery("Find orders...");

// Override per query
queryService.executeQuery(
    "Find similar products",
    QueryOptions.builder()
        .mode(QueryMode.ENHANCED)  // Override default
        .build()
);
```

### **Pros:**
- ✅ **Best of both worlds** - Default + override
- ✅ **Simple by default** - No parameters needed
- ✅ **Flexible when needed** - Can override
- ✅ **Sensible defaults** - Works out of box
- ✅ **Progressive enhancement** - Start simple, add complexity

### **Cons:**
- ⚠️ Slightly more complex (but manageable)

---

## 🎯 Recommended Approach: Hybrid (Configuration Default + Query Override)

### **Why Hybrid is Best:**

1. **Sensible Defaults**
   - Most queries don't need vectors
   - Standalone mode is simpler
   - Better performance by default

2. **Flexibility When Needed**
   - Can enable vectors for specific queries
   - Cost control (use vectors selectively)
   - Optimization per query

3. **Progressive Enhancement**
   - Start with standalone (simple)
   - Add vectors for specific queries (enhanced)
   - Best of both worlds

---

## 🏗️ Implementation Design

### **Configuration:**

```yaml
ai:
  infrastructure:
    relationship:
      # Default mode for queries
      default-mode: standalone  # or "enhanced", "auto"
      
      # Vector search settings
      enable-vector-search: false  # Default: disabled
      vector-search-threshold: 0.7
      
      # Auto-detection
      auto-detect-mode: true  # Try to detect if query needs vectors
```

### **Query Options:**

```java
public class QueryOptions {
    private QueryMode mode;  // null = use default
    private Boolean enableVectorSearch;  // null = use default
    private Double similarityThreshold;  // null = use default
    // ... other options
}

public enum QueryMode {
    STANDALONE,  // Relational only
    ENHANCED,    // Relational + semantic
    AUTO         // Detect based on query
}
```

### **Service Implementation:**

```java
@Service
public class RelationshipQueryService {
    
    @Value("${ai.infrastructure.relationship.default-mode:standalone}")
    private QueryMode defaultMode;
    
    @Value("${ai.infrastructure.relationship.enable-vector-search:false}")
    private boolean defaultVectorSearch;
    
    public RAGResponse executeQuery(String query, List<String> entityTypes) {
        return executeQuery(query, entityTypes, QueryOptions.defaults());
    }
    
    public RAGResponse executeQuery(String query, List<String> entityTypes, 
                                   QueryOptions options) {
        // Determine mode
        QueryMode mode = determineMode(options);
        
        // Execute based on mode
        if (mode == QueryMode.ENHANCED && isVectorSearchAvailable()) {
            return executeEnhanced(query, entityTypes, options);
        } else {
            return executeStandalone(query, entityTypes, options);
        }
    }
    
    private QueryMode determineMode(QueryOptions options) {
        // 1. Use query option if specified
        if (options.getMode() != null) {
            return options.getMode();
        }
        
        // 2. Use default from config
        return defaultMode;
    }
}
```

---

## 🎯 Mode Detection Strategies

### **Strategy 1: Configuration Default**

```java
// Simple: Use config default
QueryMode mode = config.getDefaultMode();  // "standalone"
```

**Use When:**
- Consistent behavior needed
- Simple use cases
- Performance is priority

---

### **Strategy 2: Query Parameter Override**

```java
// Override per query
QueryOptions options = QueryOptions.builder()
    .mode(QueryMode.ENHANCED)
    .build();
queryService.executeQuery(query, entityTypes, options);
```

**Use When:**
- Need flexibility
- Some queries need vectors, others don't
- Cost optimization

---

### **Strategy 3: Auto-Detection**

```java
// Detect based on query content
QueryMode mode = detectMode(query);
// "Find similar..." → ENHANCED
// "Find orders from..." → STANDALONE
```

**Detection Logic:**
```java
private QueryMode detectMode(String query) {
    String lower = query.toLowerCase();
    
    // Keywords that suggest semantic search
    if (lower.contains("similar") || 
        lower.contains("like") || 
        lower.contains("related") ||
        lower.contains("recommend")) {
        return QueryMode.ENHANCED;
    }
    
    // Default to standalone
    return QueryMode.STANDALONE;
}
```

**Use When:**
- Want automatic optimization
- Don't want to specify mode
- Smart defaults

---

### **Strategy 4: LLM-Based Detection**

```java
// LLM analyzes query and suggests mode
RelationshipQueryPlan plan = queryPlanner.planQuery(query);
QueryMode suggestedMode = plan.getSuggestedMode();
// LLM says: "This query needs semantic search" → ENHANCED
```

**Use When:**
- Most intelligent
- Want LLM to decide
- Complex queries

---

## 📊 Comparison Table

| Approach | Flexibility | Simplicity | Performance | Use Case |
|----------|------------|------------|-------------|----------|
| **Configuration Only** | ⭐⭐ Low | ⭐⭐⭐⭐⭐ High | ⭐⭐⭐⭐⭐ Best | Consistent needs |
| **Query Parameter Only** | ⭐⭐⭐⭐⭐ High | ⭐⭐ Low | ⭐⭐⭐ Good | Mixed needs |
| **Hybrid (Default + Override)** | ⭐⭐⭐⭐ High | ⭐⭐⭐⭐ High | ⭐⭐⭐⭐ Good | **Best overall** |
| **Auto-Detection** | ⭐⭐⭐ Medium | ⭐⭐⭐⭐⭐ High | ⭐⭐⭐⭐ Good | Smart defaults |

---

## 🎯 Recommended: Hybrid with Auto-Detection

### **Complete Strategy:**

```java
@Service
public class RelationshipQueryService {
    
    public RAGResponse executeQuery(String query, List<String> entityTypes, 
                                   QueryOptions options) {
        // 1. Determine mode (priority order):
        QueryMode mode = determineMode(query, options);
        
        // 2. Execute based on mode
        return executeWithMode(query, entityTypes, mode, options);
    }
    
    private QueryMode determineMode(String query, QueryOptions options) {
        // Priority 1: Explicit query option
        if (options.getMode() != null && options.getMode() != QueryMode.AUTO) {
            return options.getMode();
        }
        
        // Priority 2: Auto-detection (if enabled)
        if (config.isAutoDetectMode() || options.getMode() == QueryMode.AUTO) {
            QueryMode detected = autoDetectMode(query);
            if (detected != null) {
                return detected;
            }
        }
        
        // Priority 3: Configuration default
        return config.getDefaultMode();
    }
    
    private QueryMode autoDetectMode(String query) {
        // Simple keyword detection
        String lower = query.toLowerCase();
        
        // Semantic search indicators
        if (lower.matches(".*(similar|like|related|recommend|find.*similar).*")) {
            return QueryMode.ENHANCED;
        }
        
        // Structured query indicators
        if (lower.matches(".*(from|where|with|by|in|for).*")) {
            return QueryMode.STANDALONE;
        }
        
        // Could also use LLM to detect
        // return llmDetectMode(query);
        
        return null;  // Fall back to default
    }
}
```

---

## 💡 Usage Examples

### **Example 1: Configuration Default (Simple)**

```yaml
# application.yml
ai:
  infrastructure:
    relationship:
      default-mode: standalone
```

```java
// All queries use standalone mode
queryService.executeQuery("Find orders from active customers");
// → Standalone mode (from config)
```

---

### **Example 2: Query Override**

```java
// Override for specific query
queryService.executeQuery(
    "Find products similar to iPhone",
    QueryOptions.builder()
        .mode(QueryMode.ENHANCED)  // Override default
        .build()
);
// → Enhanced mode (overrides config)
```

---

### **Example 3: Auto-Detection**

```yaml
# application.yml
ai:
  infrastructure:
    relationship:
      default-mode: standalone
      auto-detect-mode: true
```

```java
// Auto-detects mode based on query
queryService.executeQuery("Find similar products");
// → Auto-detects: ENHANCED (keyword "similar")

queryService.executeQuery("Find orders from customers");
// → Auto-detects: STANDALONE (structured query)
```

---

### **Example 4: LLM-Based Detection**

```java
// LLM analyzes and suggests mode
RelationshipQueryPlan plan = queryPlanner.planQuery(query);
// Plan includes: suggestedMode = ENHANCED

queryService.executeQuery(query, QueryOptions.auto());
// → Uses LLM-suggested mode
```

---

## 🎯 Final Recommendation

### **Hybrid Approach with Priority Order:**

1. **Explicit Query Option** (highest priority)
   ```java
   QueryOptions.builder().mode(QueryMode.ENHANCED).build()
   ```

2. **Auto-Detection** (if enabled)
   ```java
   // Detects based on keywords or LLM analysis
   ```

3. **Configuration Default** (fallback)
   ```yaml
   default-mode: standalone
   ```

### **Configuration:**

```yaml
ai:
  infrastructure:
    relationship:
      # Default mode
      default-mode: standalone
      
      # Auto-detection
      auto-detect-mode: true
      auto-detect-strategy: keyword  # or "llm"
      
      # Vector search
      enable-vector-search: false  # Default disabled
      vector-search-threshold: 0.7
```

### **API Design:**

```java
// Simple (uses defaults)
queryService.executeQuery(query, entityTypes);

// With options
queryService.executeQuery(query, entityTypes, QueryOptions.builder()
    .mode(QueryMode.ENHANCED)
    .enableVectorSearch(true)
    .similarityThreshold(0.8)
    .build());

// Auto mode
queryService.executeQuery(query, entityTypes, QueryOptions.auto());
```

---

## ✅ Benefits of Hybrid Approach

1. **Simple by Default**
   - No parameters needed
   - Works out of box
   - Sensible defaults

2. **Flexible When Needed**
   - Can override per query
   - Cost optimization
   - Performance tuning

3. **Smart Auto-Detection**
   - Automatically chooses best mode
   - No manual decision needed
   - Optimizes for each query

4. **Progressive Enhancement**
   - Start with standalone
   - Add vectors when needed
   - Best of both worlds

---

## 🎯 Summary

**Recommended: Hybrid (Configuration Default + Query Override + Auto-Detection)**

**Priority Order:**
1. Explicit query option (if specified)
2. Auto-detection (if enabled)
3. Configuration default (fallback)

**This gives:**
- ✅ Simple defaults
- ✅ Maximum flexibility
- ✅ Smart auto-detection
- ✅ Cost optimization
- ✅ Performance tuning

**Best of all worlds!** 🚀
