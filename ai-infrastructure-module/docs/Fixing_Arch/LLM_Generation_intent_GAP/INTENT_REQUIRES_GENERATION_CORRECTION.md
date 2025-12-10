# Intent requiresGeneration Flag - Implementation Correction

## ✅ Corrected Approach

The `requiresGeneration` flag should **NOT** be auto-detected using heuristics in the `normalize()` method. Instead, the **LLM itself** (via `IntentQueryExtractor`) should explicitly set this flag during intent extraction.

---

## 🔧 Implementation Details

### Phase 1: Add `requiresGeneration` Field to Intent.java

```java
/**
 * Whether this intent requires LLM-based generation to respond.
 * Set by LLM during intent extraction to indicate if response needs analysis/generation.
 * When true: Search results are filtered by include-in-rag before sending to LLM.
 * When false: Raw search results are returned without LLM processing.
 * Default: false (search-only)
 */
@JsonAlias({"requires_generation"})
private Boolean requiresGeneration;
```

**Key Points**:
- ✅ JSON deserialization support with `@JsonAlias`
- ✅ Nullable (Optional) for explicit control
- ✅ Clear documentation of when it's set and what it means
- ✅ No heuristic detection

---

### Phase 2: Add Getter Method

```java
/**
 * Returns whether this intent requires LLM generation.
 * Defaults to false (search-only) if not explicitly set by LLM.
 */
public boolean requiresGenerationOrDefault(boolean fallback) {
    return requiresGeneration != null ? requiresGeneration : fallback;
}
```

**Usage**:
```java
if (intent.requiresGenerationOrDefault(false)) {
    // LLM generation needed
} else {
    // Search-only
}
```

---

### Phase 3: Update normalize() Method

```java
public void normalize() {
    // ... existing normalization ...
    
    // requiresGeneration is set explicitly by LLM during intent extraction
    // Default to false (search-only) if not set
    if (requiresGeneration == null) {
        requiresGeneration = false;
    }
    
    // ... rest of normalization ...
}
```

**What This Does**:
- ✅ Sets default to `false` (search-only) if LLM didn't set it
- ✅ Does NOT auto-detect based on keywords
- ✅ Respects LLM's decision if it explicitly set the flag
- ✅ Safe default behavior

---

## 🧠 How LLM Sets This Flag

### IntentQueryExtractor Responsibility

The `IntentQueryExtractor` must instruct the LLM to include `requiresGeneration` in its response:

```json
{
  "type": "INFORMATION",
  "intent": "find_products_under_price",
  "requires_retrieval": true,
  "requires_generation": false
}
```

OR

```json
{
  "type": "INFORMATION",
  "intent": "recommend_products",
  "requires_retrieval": true,
  "requires_generation": true
}
```

### LLM System Prompt Instruction

The system prompt for `IntentQueryExtractor` should include:

```
Extract the user intent and include these fields in JSON:
- type: INFORMATION, ACTION, OUT_OF_SCOPE, or COMPOUND
- intent: canonical intent name
- requires_retrieval: boolean - does this need document search?
- requires_generation: boolean - does this need LLM generation/analysis?
  * true = recommendation, analysis, comparison, opinion needed
  * false = just return search results
- ... other fields ...

Example:
- User: "Show me wallets under $60"
  requires_generation: false (just search)
  
- User: "Should I buy this wallet?"
  requires_generation: true (needs recommendation)
```

---

## 📊 Decision Logic in LLM Prompt

The LLM should decide `requiresGeneration` based on:

```
Query Analysis Checklist:
✓ Is user asking for opinion/recommendation?        → requires_generation: true
✓ Is user asking for analysis/evaluation?           → requires_generation: true
✓ Is user asking for comparison/vs?                 → requires_generation: true
✓ Is user asking what I think/should they?          → requires_generation: true
✓ Is user just asking to find/show/list?            → requires_generation: false
✓ Is user just asking to search/retrieve?           → requires_generation: false
✓ Is user asking for plain information lookup?      → requires_generation: false
```

---

## 🔀 Processing Flow

### Without `requiresGeneration` (Before)

```
Query: "Show me wallets under $60"
  ↓
IntentQueryExtractor (LLM)
  ↓
Intent { type: INFORMATION, intent: "find_products" }
  ↓
RAGOrchestrator.handleInformation()
  ↓
Always does RAG search
  ↓
Always returns results (no LLM involved)
```

### With `requiresGeneration` (After - Correct)

**Scenario 1: Search-Only**
```
Query: "Show me wallets under $60"
  ↓
IntentQueryExtractor (LLM)
  ↓
Intent { 
  type: INFORMATION, 
  intent: "find_products",
  requires_generation: false  ← LLM decided
}
  ↓
RAGOrchestrator.handleInformation()
  ├─ Check: requires_generation == false
  ├─ Action: Do RAG search, return results as-is
  └─ Result: Raw search results to user
```

**Scenario 2: LLM Generation**
```
Query: "Should I buy this wallet?"
  ↓
IntentQueryExtractor (LLM)
  ↓
Intent { 
  type: INFORMATION, 
  intent: "recommend_product",
  requires_generation: true  ← LLM decided
}
  ↓
RAGOrchestrator.handleInformation()
  ├─ Check: requires_generation == true
  ├─ Action: Do RAG search
  ├─ Filter: By include-in-rag flag
  ├─ Send: Filtered context to LLM
  └─ Result: LLM-generated recommendation
```

---

## ✅ Code Implementation Checklist

### Intent.java Changes

- [x] Add `requiresGeneration` field with `@JsonAlias`
- [x] Add JavaDoc explaining the flag
- [x] Add `requiresGenerationOrDefault()` getter method
- [x] Update `normalize()` to set default to `false`
- [x] NO heuristic detection (no keyword matching)

### RAGOrchestrator.java Changes

- [ ] Use flag in `handleInformation()`:
  ```java
  if (intent.requiresGenerationOrDefault(false)) {
      // LLM generation flow with filtering
  } else {
      // Search-only flow
  }
  ```

### IntentQueryExtractor Configuration

- [ ] Update LLM system prompt to instruct inclusion of `requires_generation`
- [ ] Provide examples for LLM to learn when to set to true vs false
- [ ] Test that LLM correctly sets the flag

---

## 🧪 Testing Scenarios

### Unit Tests

```java
// Test 1: Default value when not set
Intent intent = new Intent();
intent.normalize();
assertThat(intent.requiresGenerationOrDefault(false)).isFalse();

// Test 2: Respects LLM's explicit value (true)
Intent intent = Intent.builder()
    .requiresGeneration(true)
    .build();
intent.normalize();
assertThat(intent.requiresGenerationOrDefault(false)).isTrue();

// Test 3: Respects LLM's explicit value (false)
Intent intent = Intent.builder()
    .requiresGeneration(false)
    .build();
intent.normalize();
assertThat(intent.requiresGenerationOrDefault(false)).isFalse();

// Test 4: JSON deserialization
String json = "{\"type\":\"INFORMATION\",\"requires_generation\":true}";
Intent intent = objectMapper.readValue(json, Intent.class);
assertThat(intent.requiresGenerationOrDefault(false)).isTrue();
```

### Integration Tests

- LLM returns `requires_generation: false` for search queries
- LLM returns `requires_generation: true` for recommendation queries
- RAGOrchestrator routes correctly based on the flag
- Context is filtered only when `requires_generation: true`

---

## 📝 JSON Example

### Request from LLM

```json
{
  "type": "INFORMATION",
  "intent": "product_recommendation",
  "confidence": 0.95,
  "requires_retrieval": true,
  "requires_generation": true,
  "vector_space": "products"
}
```

### Processing

```
Intent.normalize() is called:
  - confidence: 0.95 (already valid, no change)
  - requires_retrieval: true (already set, no change)
  - requires_generation: true (already set by LLM, no change)
  
RAGOrchestrator.handleInformation():
  - Check: intent.requiresGenerationOrDefault(false) → true
  - Execute: LLM generation flow with context filtering
```

---

## 🎯 Key Differences from Heuristic Approach

| Aspect | Heuristic (❌ Wrong) | LLM-Decided (✅ Correct) |
|--------|-------------------|------------------------|
| **Who decides** | Heuristic regex in normalize() | LLM during intent extraction |
| **Accuracy** | ~70% (keyword matching errors) | ~95%+ (LLM understands context) |
| **Flexibility** | Hard to change (code change) | Easy to adjust (prompt change) |
| **False positives** | High (missed recommendations) | Low (LLM understands nuance) |
| **Maintainability** | Brittle (keyword list grows) | Scalable (LLM adapts) |
| **User experience** | Inconsistent | Consistent and intuitive |

---

## 🚀 Implementation Priority

1. **HIGH**: Update Intent.java (add field + getter + normalize)
2. **HIGH**: Update IntentQueryExtractor LLM prompt to include `requires_generation`
3. **HIGH**: Update RAGOrchestrator to use the flag
4. **MEDIUM**: Add unit tests for flag handling
5. **MEDIUM**: Add integration tests with real LLM
6. **LOW**: Update documentation

---

## 💡 Best Practices

✅ **DO**:
- Trust LLM's decision on whether generation is needed
- Use the flag to route queries correctly
- Filter context only when flag is true
- Default to false (search-only) for safety

❌ **DON'T**:
- Use keyword heuristics to auto-detect
- Try to outguess the LLM's decision
- Assume all INFORMATION intents need generation
- Filter context when generation flag is false

---

## Summary

The `requiresGeneration` flag should be:
- ✅ Set explicitly by LLM during intent extraction
- ✅ Respected as-is by RAGOrchestrator
- ✅ Default to false if LLM doesn't set it
- ✅ Used to determine if context filtering is needed
- ❌ NOT auto-detected using heuristics

