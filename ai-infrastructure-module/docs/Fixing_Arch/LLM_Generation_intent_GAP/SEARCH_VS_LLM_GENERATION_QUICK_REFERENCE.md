# Search vs LLM Generation - Quick Reference

## What's Being Implemented?

**Problem**: Current system treats all INFORMATION intents identically (RAG search only). No differentiation between "just search" and "search + analyze" queries.

**Solution**: Add `requiresGeneration` flag to distinguish:
- **Search-only**: Return raw results with all fields
- **LLM generation**: Filter context by `include-in-rag` before sending to LLM

---

## Files to Modify

### 1. Intent.java
```java
// Add field
@JsonAlias({"requires_generation"})
private Boolean requiresGeneration;

// Add method
private boolean detectIfGenerationNeeded() {
    return intent.toLowerCase().matches(
        ".*\\b(should|recommend|opinion|advise|compare|analyze|evaluate)\\b.*"
    );
}

// Update normalize()
if (requiresGeneration == null) {
    requiresGeneration = detectIfGenerationNeeded();
}
```

### 2. RAGOrchestrator.java
```java
// Add filtering method
private Map<String, Object> filterContextForLLM(
    AISearchResponse response, String entityType) {
    // Filter by include-in-rag flag
}

// Update handleInformation()
if (intent.requiresGenerationOrDefault(false)) {
    // LLM generation flow with filtering
} else {
    // Search-only flow
}
```

### 3. ai-entity-config.yml
```yaml
searchable-fields:
  - name: costPrice
    include-in-rag: false  # ← Add this
    enable-semantic-search: true
```

---

## Key Concepts

### Intent Flags

| Flag | Meaning |
|------|---------|
| `requiresRetrieval` | Does query need data? (existing) |
| `requiresGeneration` | Does query need LLM analysis? (NEW) |

### Field Configuration

| Config | Search Visible | LLM Visible |
|--------|---|---|
| `include-in-rag: true` + `enable-semantic-search: true` | ✅ | ✅ |
| `include-in-rag: false` + `enable-semantic-search: true` | ✅ | ❌ |
| `include-in-rag: true` + `enable-semantic-search: false` | ❌ | ✅ |
| `include-in-rag: false` + `enable-semantic-search: false` | ❌ | ❌ |

### Query Flows

**Flow 1: Search-Only** (`requiresGeneration=false`)
```
User: "Show me wallets under $60"
  → Search (all embedded fields)
  → Return raw results
  → User sees: name, description, costPrice ✅
```

**Flow 2: LLM Generation** (`requiresGeneration=true`)
```
User: "Should I buy this?"
  → Search (all embedded fields)
  → Filter by include-in-rag
  → Send to LLM (filtered context)
  → LLM sees: name, description (NOT costPrice) ❌
```

---

## Configuration Examples

### Hide Internal Cost from LLM
```yaml
ai-entities:
  product:
    searchable-fields:
      - name: costPrice
        include-in-rag: false      # Hide from LLM
        enable-semantic-search: true  # Keep searchable
```

### Hide Non-Customer Fields
```yaml
      - name: internalMargin
        include-in-rag: false
        enable-semantic-search: false  # Not searchable either
```

### Multi-Field Filtering Example
```yaml
searchable-fields:
  # User-facing fields (visible to LLM)
  - name: name
    include-in-rag: true
  
  - name: description
    include-in-rag: true
  
  - name: retailPrice
    include-in-rag: true
  
  # Internal fields (hidden from LLM)
  - name: costPrice
    include-in-rag: false
  
  - name: profitMargin
    include-in-rag: false
```

---

## Testing Checklist

### Unit Tests
```bash
mvn test -Dtest=IntentTest
mvn test -Dtest=RAGOrchestratorTest
```

### Integration Tests (Mocked)
```bash
mvn verify -Dtest=SearchVsLLMGenerationIntegrationTest
```

### Real API Tests
```bash
export OPENAI_API_KEY=your-key
mvn verify -Dtest=SearchVsLLMGenerationRealApiIntegrationTest
```

### Test Scenarios
- [ ] Search-only returns all fields
- [ ] LLM generation filters context
- [ ] include-in-rag: false fields hidden from LLM
- [ ] Unknown fields included (fail-open)
- [ ] Missing config handled gracefully
- [ ] LLM errors fallback to search
- [ ] Performance targets met

---

## Performance Targets

| Metric | Target |
|--------|--------|
| Search-only latency | < 250ms |
| LLM generation latency | < 3 sec |
| Context filtering overhead | < 10ms |
| Success rate | > 95% |
| Error rate | < 0.5% |

---

## Deployment Steps

### Immediate (Day 1)
1. Add Intent field + logic
2. Add RAGOrchestrator filtering
3. Update configuration

### Testing (Days 2-3)
1. Unit tests all passing
2. Integration tests passing
3. Real API tests passing

### Staging (Day 4)
1. Deploy to staging
2. Run smoke tests
3. Performance validation

### Production (Days 5-7)
1. Day 5: Deploy with feature flag OFF
2. Day 6: Enable to 5% traffic
3. Day 7: Increase to 100% traffic

---

## Monitoring

### Key Metrics
```
- LLM generation success rate
- Context filtering count
- Search-only vs LLM ratio
- Latency (search vs LLM)
- Error rate by flow
- Field filtering statistics
```

### Dashboards
- Create dashboard for flow distribution
- Create dashboard for latency by flow
- Create alerts for high error rates

---

## Troubleshooting

### Issue: LLM not seeing important fields

**Check**:
1. Field has `include-in-rag: true`?
2. Field is in `searchable-fields`?
3. Configuration loaded correctly?

**Fix**:
```yaml
searchable-fields:
  - name: importantField
    include-in-rag: true  # ← Add this
```

### Issue: Search results missing fields

**Check**:
1. Is this search-only flow?
2. No filtering should happen
3. Check `requiresGeneration` = false

**Fix**:
- Ensure `handleInformation()` doesn't filter search-only
- Verify condition: `if (intent.requiresGenerationOrDefault(false))`

### Issue: Configuration not applied

**Check**:
1. YAML syntax correct?
2. Entity type matches?
3. Spelling of field names?

**Fix**:
```yaml
ai-entities:          # ← Check top level
  product:            # ← Check entity type
    searchable-fields:
      - name: field   # ← Check field name
```

---

## Code Examples

### Detecting Generation Intent
```java
// Current intent text
String intent = "should_i_buy_this_product";

// Regex matches "should"
boolean needsGeneration = intent.toLowerCase()
    .matches(".*\\b(should|recommend|analyze)\\b.*");

// Result: true → LLM generation needed
```

### Filtering Context
```java
// Before: {"name": "...", "costPrice": 50, "margin": 0.75}
Map<String, Object> allFields = searchResult;

// Filter
Map<String, Object> filtered = new HashMap<>();
for (String field : allFields.keySet()) {
    if (config.getField(field).isIncludeInRag()) {
        filtered.put(field, allFields.get(field));  // ✅ Include
    }
    // else: skip this field
}

// After: {"name": "...", "costPrice": null}
```

### Routing Query
```java
RAGResponse response = ragService.performRag(query);

if (intent.requiresGenerationOrDefault(false)) {
    // FLOW 2: LLM generation
    Map<String, Object> filtered = filterContextForLLM(response);
    String llmResponse = llmService.generate(filtered);
    return llmResponse;
} else {
    // FLOW 1: Search only
    return response.getResponse();
}
```

---

## Documentation Files

1. **SEARCH_VS_LLM_GENERATION_FEATURE.md**
   - Full technical specification
   - Problem statement
   - Implementation details

2. **SEARCH_VS_LLM_GENERATION_IMPLEMENTATION_CHECKLIST.md**
   - Step-by-step deployment guide
   - Testing procedures
   - Rollout phases

3. **SearchVsLLMGenerationIntegrationTest.java**
   - Mocked integration tests
   - 50+ test cases

4. **SearchVsLLMGenerationRealApiIntegrationTest.java**
   - Real OpenAI API tests
   - End-to-end scenarios

---

## Questions & Answers

**Q: What if `include-in-rag` isn't set?**
A: Default is true (fail-open). Field included in LLM.

**Q: Can search results include hidden fields?**
A: Yes! `include-in-rag: false` only hides from LLM, not from search.

**Q: What if LLM service fails?**
A: Fallback to search results, error logged.

**Q: How is intent detection decided?**
A: Regex pattern on intent keywords. Future: ML model.

**Q: Can I manually set `requiresGeneration`?**
A: Yes, via JSON: `{"requires_generation": true}`

**Q: Performance impact?**
A: Filtering < 10ms. LLM calls 500-2000ms (unavoidable).

---

## Success Criteria

✅ All tests passing (100+ test cases)
✅ Context filtering working correctly
✅ Search-only unaffected (no performance regression)
✅ LLM generation respects include-in-rag flags
✅ Error handling robust
✅ Monitoring/metrics in place
✅ Documentation complete
✅ Zero incidents in first week post-deployment

