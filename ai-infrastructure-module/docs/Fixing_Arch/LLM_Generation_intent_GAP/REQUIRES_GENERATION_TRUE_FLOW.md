# When requiresGeneration = true: Complete Processing Flow

## 📋 Quick Answer

When `requiresGeneration = true`, the system performs these **NEW** steps:

1. ✅ **Search** - Vector search retrieves matching documents
2. ✅ **Filter** - Remove fields where `include-in-rag: false`
3. ✅ **LLM Call** - Send filtered context to LLM for generation
4. ✅ **Generate** - LLM creates recommendation/analysis
5. ✅ **Response** - Return LLM-generated response to user

---

## 🔄 Complete 8-Step Flow

### Step 1: User Query Arrives
```
Input: "Should I buy this wallet?"
↓
RAGOrchestrator receives query
↓
PII Detection & Compliance (existing)
↓
IntentQueryExtractor.extract() called
```

### Step 2: LLM Analyzes Intent
```
EnrichedPromptBuilder sends prompt with:
  • Rule #6 about requiresGeneration
  • JSON schema with requiresGeneration field
  • Examples: "Should I buy?" → true

LLM Decision:
  ✓ Recognizes recommendation request
  ✓ Sets: requiresGeneration: true
  ✓ Returns JSON with flag
```

### Step 3: Intent Creation
```
IntentQueryExtractor.parseResponse():
  ✓ Deserializes JSON
  ✓ Creates Intent object
  ✓ requiresGeneration = true (from LLM)

Intent.normalize() called:
  ✓ requiresGeneration already true
  ✓ No changes needed (LLM set it)
```

### Step 4: Flag Check in RAGOrchestrator
```java
if (intent.requiresGenerationOrDefault(false)) {
    // TRUE → Execute LLM generation flow
} else {
    // FALSE → Execute search-only flow
}

// When TRUE:
```

### Step 5: SEARCH Phase (RAG Retrieval)
```
RAGRequest built with:
  - query: "Should I buy this wallet?"
  - entityType: "product"
  - limit: 10
  - threshold: 0.7

RAGService.performRag() called:
  ✓ Vector search executed
  ✓ Finds matching products
  ✓ Returns all fields in search response

Example Search Result:
{
  "documents": [
    {
      "id": "prod-123",
      "name": "Luxury Leather Wallet",
      "description": "Premium Italian leather, handcrafted",
      "costPrice": 50.00,              ← From database
      "retailPrice": 199.99,
      "margin": 0.75,
      "reviews": "4.8/5 stars",
      "availability": "in stock"
    }
  ]
}
```

### Step 6: CONTEXT FILTERING (New Feature!)
```
Since requiresGeneration = true:

RAGOrchestrator calls:
  filterContextForLLM(searchResponse, entityType)

What happens:
  ✓ Load entity config for "product"
  ✓ Check each field's include-in-rag flag:
    
    - name: include-in-rag: true        ✅ INCLUDE
    - description: include-in-rag: true ✅ INCLUDE
    - costPrice: include-in-rag: false  ❌ EXCLUDE
    - retailPrice: include-in-rag: true ✅ INCLUDE
    - margin: include-in-rag: false     ❌ EXCLUDE
    - reviews: include-in-rag: true     ✅ INCLUDE
    - availability: include-in-rag: true✅ INCLUDE

Filtered Context (sent to LLM):
{
  "prod-123": {
    "name": "Luxury Leather Wallet",
    "description": "Premium Italian leather, handcrafted",
    "retailPrice": 199.99,
    "reviews": "4.8/5 stars",
    "availability": "in stock"
    // ❌ costPrice removed
    // ❌ margin removed
  }
}
```

### Step 7: LLM GENERATION Phase
```
New method called (to be implemented):
  generateLLMResponse(userQuery, filteredContext)

LLM Prompt built:
  System: "You are a helpful shopping advisor..."
  Context: "Based on the following information:
           - Name: Luxury Leather Wallet
           - Description: Premium Italian leather, handcrafted
           - Retail Price: $199.99
           - Reviews: 4.8/5 stars
           - Availability: in stock"
  Query: "Should I buy this wallet?"

LLM Response (important: doesn't know costPrice = $50!):
  "This is an excellent choice! Based on the reviews (4.8/5 stars),
   the premium Italian leather construction, and availability, this wallet
   appears to be a high-quality product. At $199.99, it's positioned as
   a premium item. Whether it's worth buying depends on your budget and
   how much you value quality craftsmanship."
```

### Step 8: Response to User
```
OrchestrationResult built:
{
  "success": true,
  "type": "INFORMATION_PROVIDED",
  "message": "This is an excellent choice!...",
  "data": {
    "answer": "This is an excellent choice!...",
    "documents": [original search results],
    "filteredContext": {filtered context sent to LLM},
    "contextFiltered": true
  }
}

User receives:
  "This is an excellent choice! Based on the reviews..."
  (NOT a data dump, actual AI-generated insight)
```

---

## 🎯 WHY Context Filtering is Critical

### Without Filtering (BAD)
```
LLM sees:
  - costPrice: $50 (internal)
  - retailPrice: $199.99 (customer price)
  - margin: 0.75 (75% markup)

LLM thinks:
  "This is a 75% markup! Way overpriced!"

LLM recommendation:
  "You're paying way too much. It's only worth $50."

Result: ❌ Bad for business, wrong recommendation
```

### With Filtering (GOOD) ✅
```
LLM sees:
  - retailPrice: $199.99
  - reviews: 4.8/5 stars
  - description: Premium quality
  - availability: In stock

LLM thinks:
  "4.8 stars, premium quality, in stock - seems fair"

LLM recommendation:
  "Great product at this price point. High quality."

Result: ✅ Fair recommendation, better business outcome
```

---

## 📊 Comparison: TRUE vs FALSE

| Aspect | requiresGeneration = FALSE | requiresGeneration = TRUE |
|--------|--------------------------|--------------------------|
| Query Type | "Show me wallets" | "Should I buy?" |
| Search | ✅ Yes | ✅ Yes |
| Filtering | ❌ No | ✅ Yes (NEW) |
| LLM Call | ❌ No | ✅ Yes (NEW) |
| Response Type | Raw results | AI-generated insight |
| User Sees | Product data | Recommendation |
| Fields Visible | All fields | Filtered fields only |
| costPrice Visible | ✅ Yes | ❌ No |

---

## 🔑 Key Implementation Points

### When TRUE Triggers:
- User asks for **opinion**: "Should I buy?"
- User asks for **recommendation**: "Recommend the best"
- User asks for **comparison**: "Compare these products"
- User asks for **analysis**: "Is this a good deal?"
- User asks for **advice**: "What do you think?"

### What Happens Internally:
1. Search retrieves ALL fields (unchanged)
2. Filter removes `include-in-rag: false` fields (NEW!)
3. LLM sees only whitelisted fields (NEW!)
4. LLM generates based on filtered context (NEW!)
5. User gets AI insight instead of raw data (NEW!)

### Configuration (ai-entity-config.yml):
```yaml
ai-entities:
  product:
    searchable-fields:
      - name: name
        include-in-rag: true           ✅ Visible to LLM
      
      - name: costPrice
        include-in-rag: false          ❌ Hidden from LLM
        enable-semantic-search: true   ✅ Still searchable
      
      - name: retailPrice
        include-in-rag: true           ✅ Visible to LLM
```

---

## 🚀 Next Step: Implement in RAGOrchestrator

The `handleInformation()` method needs to be updated to:

```java
private OrchestrationResult handleInformation(Intent intent, String userId) {
    // Get search results (always)
    RAGResponse searchResponse = ragService.performRag(ragRequest);
    
    // NEW: Check if LLM generation is needed
    if (intent.requiresGenerationOrDefault(false)) {
        
        // NEW: Filter context
        Map<String, Object> filteredContext = filterContextForLLM(
            searchResponse, 
            intent.getVectorSpace()
        );
        
        // NEW: Call LLM
        String llmResponse = generateLLMResponse(
            intent.getIntent(),
            filteredContext
        );
        
        // NEW: Return LLM response
        return OrchestrationResult.builder()
            .message(llmResponse)
            .data(buildData(searchResponse, filteredContext, true))
            .build();
    } else {
        // SEARCH-ONLY: Return raw results
        return OrchestrationResult.builder()
            .message(searchResponse.getResponse())
            .data(buildData(searchResponse, null, false))
            .build();
    }
}
```

---

## ✨ Summary

**When `requiresGeneration = true`:**

The system transforms from a simple search engine to an **AI-powered advisor**:

- **Search** → Find relevant data
- **Filter** → Remove sensitive/internal fields
- **Process** → LLM analyzes filtered context
- **Generate** → AI creates insights/recommendations
- **Deliver** → User gets intelligent response

This is the power of the new `requiresGeneration` flag!

