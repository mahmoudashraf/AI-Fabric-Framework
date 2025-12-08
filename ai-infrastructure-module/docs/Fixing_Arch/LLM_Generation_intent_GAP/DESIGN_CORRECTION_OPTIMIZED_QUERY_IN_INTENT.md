# Correction: optimizedQuery Should Be Part of Intent Object

## ❌ What We Documented (Incorrect)

Our documents show a separate flow where:
1. QueryOptimizationService returns an `OptimizedQuery` object
2. IntentQueryExtractor returns an `Intent` object with just the requiresGeneration flag
3. These are two separate objects flowing through the system

```
OptimizedQuery {
  optimizedQuery: "Product entities with price_usd < 60"
  fieldsToInclude: [...]
  confidence: 0.97
}

Intent {
  type: INFORMATION
  requiresGeneration: false
}
```

---

## ✅ What Should Happen (Correct)

The `optimizedQuery` should be **included in the Intent object** returned by the LLM:

```
Intent {
  type: INFORMATION
  intent: "find_products_by_price_and_stock"
  vectorSpace: "product"
  requiresRetrieval: true
  requiresGeneration: false,
  
  // ⭐ NEW: Add this attribute to Intent
  optimizedQuery: "Product entities with price_usd < 60 AND stock_status = 'in_stock'",
  
  confidence: 0.95
}
```

---

## 🔄 Corrected Flow

Instead of our documented 2-step process:
```
1. QueryOptimizationService → OptimizedQuery object
2. IntentQueryExtractor → Intent object
```

**Should be a single step:**
```
IntentQueryExtractor receives sanitized query
  ↓
LLM optimizes query internally (as part of intent extraction)
  ↓
LLM returns Intent object WITH optimizedQuery inside
  ↓
RAGOrchestrator uses intent.optimizedQuery for embedding
```

---

## 🛠️ Changes Needed

### 1. Modify Intent.java DTO

Add this attribute to the Intent class:

```java
/**
 * Optimized query respecting system jargon and constraints.
 * Set by LLM during intent extraction to ensure better semantic search.
 * If null, fall back to original query.
 */
@JsonAlias({"optimized_query"})
private String optimizedQuery;

// Getter
public String getOptimizedQueryOrFallback(String originalQuery) {
    return optimizedQuery != null ? optimizedQuery : originalQuery;
}
```

### 2. Update EnrichedPromptBuilder

The LLM prompt should instruct the LLM to optimize the query AND include it in the Intent JSON:

```java
// In appendOutputFormat()
prompt.append("""
    {
      "intents": [
        {
          "type": "INFORMATION",
          "intent": "find_products",
          "optimizedQuery": "Product entities with price_usd < 60",
          "requires_retrieval": true,
          "requires_generation": false,
          "confidence": 0.95
        }
      ]
    }
    """);
```

### 3. Simplify RAGService.performRag()

Instead of:
```java
// ❌ OLD: Separate optimization service with confidence check
OptimizedQuery optimized = queryOptimizationService.optimizeQuery(...);
if (optimized.getConfidence() >= 0.80) {
    String queryForEmbedding = optimized.getOptimizedQuery();
} else {
    String queryForEmbedding = originalQuery;
}
```

**Do this:**
```java
// ✅ NEW: Always use optimized query from Intent (no confidence check)
Intent intent = intentQueryExtractor.extract(sanitizedQuery);
String queryForEmbedding = intent.getOptimizedQueryOrFallback(sanitizedQuery);
// Always prefer intent.optimizedQuery, only fallback if null

// Use it for embedding
AIEmbeddingRequest embeddingRequest = AIEmbeddingRequest.builder()
    .text(queryForEmbedding)  // ← From Intent.optimizedQuery (ALWAYS)
    .build();
```

---

## 📊 Benefits of This Approach

| Aspect | Current Approach | Corrected Approach |
|--------|-----------------|-------------------|
| **Objects in Flow** | OptimizedQuery + Intent (2 objects) | Intent only (1 object) |
| **Coupling** | Loose (separate services) | Tight (LLM handles optimization) |
| **LLM Responsibility** | Only intent extraction | Intent extraction + query optimization |
| **Simplicity** | More complex (2 DTOs) | Simpler (1 DTO) |
| **Data Flow** | Parallel objects | Single object carries all info |
| **Code Elegance** | Multiple objects to track | Single Intent object |

---

## 🔧 Implementation Checklist

- [ ] Add `optimizedQuery` attribute to `Intent.java`
- [ ] Add `getOptimizedQueryOrFallback()` method to `Intent.java`
- [ ] Update `EnrichedPromptBuilder` to include optimizedQuery in JSON schema
- [ ] Update `EnrichedPromptBuilder` to instruct LLM to generate optimizedQuery
- [ ] Modify `RAGService.performRag()` to use `intent.getOptimizedQueryOrFallback()`
- [ ] Remove dependency on separate `QueryOptimizationService` from RAGService
- [ ] Update tests to verify optimizedQuery is in Intent
- [ ] Delete `QueryOptimizationService` (or keep as separate service but don't use it in RAG flow)

---

## ✨ Why This Is Better

1. **Minimal Design** - No extra services or objects
2. **Single Responsibility** - LLM generates Intent with everything needed
3. **Clean Data Flow** - One object carries all information
4. **Easy to Test** - Test Intent object has optimizedQuery set
5. **Maintainable** - Less coupling between services
6. **Backwards Compatible** - If optimizedQuery is null, use original query

---

## 📝 Summary

The corrected approach is:
- **Add** `optimizedQuery: String` to `Intent` DTO
- **Update** LLM prompt to generate optimizedQuery
- **Use** `intent.optimizedQuery` for embedding in RAGService
- **No** separate `OptimizedQuery` object needed
- **No** separate `QueryOptimizationService` in the main flow

This is simpler, cleaner, and respects the principle of minimal design! ✅

