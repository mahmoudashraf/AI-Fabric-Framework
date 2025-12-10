# Search vs LLM Generation - Architecture Integration

## Current Architecture Overview

Your system currently has this flow:

```
User Query
    ↓
IntentQueryExtractor (LLM)
    ↓ (extracts: type, intent, vectorSpace, requiresRetrieval)
    ↓
RAGOrchestrator.orchestrate()
    ↓
  Switch on IntentType:
    - ACTION → handleAction()
    - INFORMATION → handleInformation()  ← THIS WE'RE MODIFYING
    - OUT_OF_SCOPE → handleOutOfScope()
    - COMPOUND → handleCompound()
    ↓
  handleInformation() currently does:
    1. Build RAGRequest
    2. Call ragService.performRag()
    3. Return search results as-is
    ↓
RAGResponse
    ↓
User gets: Raw search results
```

---

## New Architecture (With This Feature)

```
User Query
    ↓
IntentQueryExtractor (LLM)
    ↓ (extracts: type, intent, vectorSpace, requiresRetrieval, requiresGeneration*)
    ↓                                                           ↑ NEW FIELD
RAGOrchestrator.orchestrate()
    ↓
  Switch on IntentType:
    - ACTION → handleAction()
    - INFORMATION → handleInformation()  ← ENHANCED
    - OUT_OF_SCOPE → handleOutOfScope()
    - COMPOUND → handleCompound()
    ↓
  handleInformation() NEW logic:
    1. Build RAGRequest
    2. Call ragService.performRag()
    3. Check requiresGeneration:
       ├─ FALSE (Search-only)
       │   └─ Return search results as-is ✅
       └─ TRUE (LLM generation)
           ├─ Load entity config
           ├─ Call filterContextForLLM()
           ├─ Call llmService.generateResponse(filtered)
           └─ Return LLM response ✅
    ↓
RAGResponse (modified)
    ↓
User gets: Raw results (search) OR LLM analysis (generation)
```

---

## Component Interactions

### 1. Intent Enhancement

**Before**:
```
Intent {
  type: INFORMATION
  intent: "find_products"
  vectorSpace: "product"
  requiresRetrieval: true
}
```

**After**:
```
Intent {
  type: INFORMATION
  intent: "should_i_buy_product"
  vectorSpace: "product"
  requiresRetrieval: true
  requiresGeneration: true  ← NEW
}
```

### 2. Configuration Layer

**Existing** (unchanged):
```java
AIEntityConfig config = configurationLoader.getEntityConfig("product");
// Has: entityType, enableSearch, searchableFields[], etc.
```

**Enhanced** (uses existing):
```java
AIEntityConfig config = configurationLoader.getEntityConfig("product");
for (SearchableFieldConfig field : config.getSearchableFields()) {
    if (!field.isIncludeInRag()) {
        // NEW: This field hidden from LLM
    }
}
```

### 3. RAG Service Layer

**Unchanged**:
```java
RAGResponse ragService.performRag(RAGRequest request)
// Still returns all fields
```

**New filtering happens after RAG returns**:
```java
RAGResponse response = ragService.performRag(request);
// Then: if (needsLLM) { filter(response) }
```

### 4. LLM Service Integration

**New call** (optional, you may have existing LLM service):
```java
// If you have existing LLM service:
String llmResponse = llmService.generateResponse(
    filteredContext,  ← Filtered by include-in-rag
    userQuery,
    systemPrompt
);
```

---

## Data Flow Example

### Scenario: User asks "Should I buy this wallet?"

```
STEP 1: Query Analysis
┌─────────────────────────────────┐
│ User: "Should I buy this?"      │
└──────────────┬──────────────────┘
               ↓
        IntentQueryExtractor
        (LLM analysis)
               ↓
┌─────────────────────────────────┐
│ Intent {                        │
│   type: INFORMATION             │
│   intent: "buy_decision"        │
│   requiresRetrieval: true       │
│   requiresGeneration: true ←NEW │
│ }                               │
└──────────────┬──────────────────┘

STEP 2: Routing
               ↓
        RAGOrchestrator
        .orchestrate()
               ↓
        Detects type = INFORMATION
        Routes to handleInformation()
               ↓

STEP 3: Search (Always Happens)
┌─────────────────────────────────┐
│ RAGService.performRag(          │
│   query: "Should I buy?"        │
│   entityType: "product"         │
│ )                               │
└──────────────┬──────────────────┘
               ↓
        Vector search finds wallet
               ↓
┌─────────────────────────────────┐
│ RAGResponse {                   │
│   documents: [{                 │
│     name: "Wallet"              │
│     description: "Italian..."   │
│     costPrice: 50.00    ← ALL   │
│     retailPrice: 199.99 ← FIELDS│
│     margin: 0.75        ← INCLUDED
│   }]                            │
│ }                               │
└──────────────┬──────────────────┘

STEP 4: Check requiresGeneration
               ↓
        YES → LLM Generation Flow
               ↓

STEP 5: Filter Context (NEW)
┌─────────────────────────────────┐
│ filterContextForLLM(             │
│   response,                      │
│   "product"                      │
│ )                               │
└──────────────┬──────────────────┘
               ↓
        Load config: include-in-rag flags
        ├─ name: true ✅
        ├─ description: true ✅
        ├─ costPrice: false ❌ REMOVE
        ├─ retailPrice: true ✅
        └─ margin: false ❌ REMOVE
               ↓
┌─────────────────────────────────┐
│ Filtered Context {              │
│   name: "Wallet"                │
│   description: "Italian..."     │
│   retailPrice: 199.99           │
│ }                               │
│                                 │
│ (costPrice: HIDDEN)             │
│ (margin: HIDDEN)                │
└──────────────┬──────────────────┘

STEP 6: Generate LLM Response (NEW)
               ↓
┌─────────────────────────────────┐
│ LLMService.generateResponse(    │
│   context: filtered,            │
│   query: "Should I buy?",       │
│   systemPrompt: "..."           │
│ )                               │
└──────────────┬──────────────────┘
               ↓
        LLM creates response
        (doesn't see costPrice!)
               ↓
┌─────────────────────────────────┐
│ LLM Response:                   │
│ "This is a quality wallet...    │
│  At $199.99, it's premium       │
│  Italian leather. Good choice   │
│  for durability."               │
│                                 │
│ (LLM didn't calculate markup)   │
└──────────────┬──────────────────┘

STEP 7: Return to User
               ↓
┌─────────────────────────────────┐
│ OrchestrationResult {           │
│   success: true                 │
│   message: "LLM response"       │
│   contextFiltered: true ← NEW   │
│   documents: [raw results]      │
│ }                               │
└──────────────┬──────────────────┘
               ↓
        User sees:
        "This is a quality wallet...
         At $199.99, it's premium..."
```

---

## Alternative Scenario: Search-Only Query

### User asks: "Show me wallets under $60"

```
STEP 1-2: Query Analysis & Routing
(same as above, but...)

        Intent {
          ...
          requiresGeneration: false ← DIFFERENT
        }

        Routes to handleInformation()

STEP 3: Search (Same)
        RAGResponse with all fields

STEP 4: Check requiresGeneration
               ↓
        NO → Search-Only Flow
               ↓

STEP 5: Return Immediately
┌─────────────────────────────────┐
│ NO filtering happens            │
│                                 │
│ Return raw search results:      │
│ {                               │
│   name: "Wallet"                │
│   description: "Italian..."     │
│   costPrice: 50.00 ✅ INCLUDED  │
│   retailPrice: 199.99 ✅        │
│   margin: 0.75 ✅               │
│ }                               │
└──────────────┬──────────────────┘
               ↓
        User sees:
        "Found wallet for $50"
        (includes costPrice)
```

---

## Class Responsibilities

### Intent.java
**Responsibility**: Extract and normalize user intent

**Changes**:
- Add `requiresGeneration` field
- Add detection logic in `normalize()`
- Add `requiresGenerationOrDefault()` getter

**Responsibilities**:
- ✅ Parse JSON (existing)
- ✅ Normalize confidence (existing)
- ✅ **NEW: Detect if LLM generation needed**
- ✅ **NEW: Set reasonable defaults**

### RAGOrchestrator.java
**Responsibility**: Orchestrate user query through system

**Changes**:
- Add `filterContextForLLM()` method
- Add `generateLLMResponse()` method stub
- Update `handleInformation()` with conditional logic

**Responsibilities**:
- ✅ Route based on intent type (existing)
- ✅ Call RAG service (existing)
- ✅ **NEW: Check if LLM generation needed**
- ✅ **NEW: Filter context if needed**
- ✅ **NEW: Call LLM if needed**
- ✅ **NEW: Handle fallbacks**

### AIEntityConfigurationLoader.java
**Responsibility**: Load entity configurations

**Changes**: NONE

**Why**: Already has `include-in-rag` support in SearchableFieldConfig

### RAGService.java
**Responsibility**: Perform RAG search

**Changes**: NONE

**Why**: Still returns all fields (filtering happens after)

---

## Integration Points

### 1. With IntentQueryExtractor
```
Current: Returns Intent with type, intent, vectorSpace, requiresRetrieval
New: Should also return requiresGeneration (if LLM can detect)
Alternative: Intent class auto-detects via heuristics
```

### 2. With RAGService
```
Current: performRag() returns RAGResponse with all fields
New: Still does the same (filtering happens after)
No changes needed to RAGService
```

### 3. With LLM Service (if exists)
```
Current: May not exist yet
New: Will call LLMService.generateResponse(context, query)
Implementation: You provide LLM service interface
```

### 4. With Configuration System
```
Current: ai-entity-config.yml has searchable-fields
New: Uses existing include-in-rag: true/false flag
No changes needed to configuration format
```

---

## Database Layer

### No Changes Required

**Why**:
- `AISearchableEntity` stores vectors (not changing)
- Search retrieves by vector (not changing)
- Context filtering is in-memory operation
- No new tables or indexes needed

---

## Backward Compatibility

✅ **Fully Backward Compatible**

- Existing code without `requiresGeneration` still works
- Defaults to: search-only (requiresGeneration=false)
- Search-only path unchanged
- LLM generation is optional enhancement

### Migration Path
1. Add field to Intent (no impact)
2. Update handleInformation() (conditional logic)
3. Update config (add include-in-rag flags)
4. Gradually enable LLM generation

---

## Performance Impact

### By Flow

**Search-Only Flow**:
- No new overhead
- Same latency as before: ~200ms

**LLM Generation Flow**:
- Context filtering: +10ms (O(n) where n=fields)
- LLM call: +500-2000ms (depends on provider)
- Total: ~600-2100ms vs. previous 200ms

### Optimization Opportunities

1. **Cache Entity Config**
   - Load once, reuse for all queries
   - Impact: -5ms per query

2. **Parallel Filtering**
   - Filter fields in parallel
   - Impact: -3ms for large field sets

3. **Lazy Filtering**
   - Only filter fields that exist in result
   - Impact: -2ms for small results

---

## Deployment Architecture

### Service Boundaries
```
┌─────────────────────────────────────┐
│ API Gateway                         │
└────────────┬────────────────────────┘
             ↓
┌─────────────────────────────────────┐
│ RAGOrchestrator Service (MODIFIED)  │
│ - Intent routing                    │
│ - Context filtering (NEW)           │
│ - LLM generation (NEW)              │
└────────────┬────────────────────────┘
             ↓
    ┌────────┴────────┐
    ↓                 ↓
┌──────────┐    ┌──────────────┐
│ RAGService│    │ LLMService   │
│(unchanged)│    │(new/wrapped) │
└──────────┘    └──────────────┘
    ↓
┌──────────────────────────────────┐
│ VectorDatabase (unchanged)        │
└──────────────────────────────────┘
```

---

## Testing Matrix

| Component | Test Type | Status |
|-----------|-----------|--------|
| Intent detection | Unit | NEW |
| Context filtering | Unit | NEW |
| Search-only flow | Integration | NEW |
| LLM generation flow | Integration | NEW |
| RAG integration | Real API | NEW |
| LLM integration | Real API | NEW |
| Config loading | Integration | NEW |
| Error handling | Integration | NEW |
| Performance | Performance | NEW |

---

## Migration Checklist

- [ ] Add `requiresGeneration` field to Intent
- [ ] Implement detection logic
- [ ] Add filtering methods to RAGOrchestrator
- [ ] Update `handleInformation()` logic
- [ ] Update entity config with `include-in-rag` flags
- [ ] Create unit tests
- [ ] Create integration tests
- [ ] Create real API tests
- [ ] Performance baseline
- [ ] Staging deployment
- [ ] Production gradual rollout
- [ ] Monitor metrics
- [ ] Documentation
- [ ] Team training

---

## Success Indicators

✅ **Technical**:
- All tests passing
- Performance targets met
- No data loss
- Error rate < 0.5%

✅ **User Experience**:
- LLM responses improve quality
- Search unaffected
- Response times acceptable

✅ **Business**:
- Improved RAG quality
- Better context isolation
- Regulatory compliance

---

## Future Extensions

1. **Machine Learning Intent Detection**
   - Replace heuristics with trained model
   - Better accuracy for generation detection

2. **Dynamic Field Weighting**
   - Different include-in-rag per user role
   - Context-aware field inclusion

3. **Query-Specific Filtering**
   - Different rules per query type
   - Custom filtering per domain

4. **Result Ranking**
   - Rank results differently for LLM vs search
   - Confidence-based filtering

5. **Context Compression**
   - Summarize documents before LLM
   - Reduce token usage

