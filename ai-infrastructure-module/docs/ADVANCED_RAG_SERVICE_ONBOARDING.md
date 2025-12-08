# AdvancedRAGService - Team Onboarding Guide

Welcome to the team! 👋 This guide introduces you to `AdvancedRAGService`, one of the core components of our AI infrastructure. Whether you're a backend developer, frontend engineer, or QA specialist, this document will help you understand what it does, how to use it, and how to test it.

## Table of Contents

1. [What is AdvancedRAGService?](#what-is-advancedragservice)
2. [Key Concepts](#key-concepts)
3. [Architecture Overview](#architecture-overview)
4. [How It Works](#how-it-works)
5. [API Integration](#api-integration)
6. [Usage Examples](#usage-examples)
7. [Testing](#testing)
8. [Troubleshooting](#troubleshooting)
9. [Common Tasks](#common-tasks)

---

## What is AdvancedRAGService?

**AdvancedRAGService** is the intelligent search & question-answering engine of our AI infrastructure. **RAG** stands for **Retrieval-Augmented Generation** – it means we retrieve relevant documents from our knowledge base and feed them to an AI model to generate accurate, contextual answers.

### Why is it "Advanced"?

The basic RAG process is: *search documents → feed to LLM → generate answer*

**AdvancedRAGService** enhances this with:
- 🔍 **Query Expansion** - Understands what you're really asking by generating related questions
- 🔄 **Re-ranking** - Sorts results by relevance using multiple strategies
- 📦 **Context Optimization** - Intelligently packs the best information for the LLM
- 🎯 **Semantic Understanding** - Uses embeddings to find conceptually similar documents
- ⚙️ **Hybrid Search** - Combines multiple search strategies for better results

---

## Key Concepts

### 1. Query Expansion
When a user asks "What are luxury watches?", the service also generates related queries like:
- "Premium timepiece collections"
- "Collector chronograph showcase"
- "Modern horology trends"

**Why?** This catches documents about watches from different angles, not just exact keyword matches.

### 2. Re-ranking Strategies
After retrieving candidate documents, we can sort them by:

| Strategy | Description |
|----------|-------------|
| **score** | Original search relevance score |
| **semantic** | Embedding-based similarity to query |
| **hybrid** | Combination of score + similarity |
| **diversity** | Mix of different document types |

### 3. Context Optimization Levels

| Level | What It Does |
|-------|-------------|
| **low** | Returns all found documents as-is |
| **medium** | Returns top 5 documents, sorted by score |
| **high** | AI-optimized summary removing redundancy & improving clarity |

### 4. Embeddings
Think of embeddings as numerical "fingerprints" of text. Documents with similar meanings have similar fingerprints, so we can mathematically compare them.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                     REST API                                │
│              POST /api/ai/advanced-rag/search                │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│           AdvancedRAGController                             │
│  (HTTP request handler - validates input, calls service)    │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│           AdvancedRAGService                                │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 1. Query Expansion (AI-powered)                      │  │
│  │    Generates 2-4 alternative queries                 │  │
│  ├──────────────────────────────────────────────────────┤  │
│  │ 2. Multi-Strategy Search (Parallel)                  │  │
│  │    Searches for each expanded query                  │  │
│  ├──────────────────────────────────────────────────────┤  │
│  │ 3. Re-ranking (Semantic/Hybrid/Diversity)           │  │
│  │    Sorts results by relevance strategy               │  │
│  ├──────────────────────────────────────────────────────┤  │
│  │ 4. Context Optimization                             │  │
│  │    Packages results for LLM consumption              │  │
│  ├──────────────────────────────────────────────────────┤  │
│  │ 5. Response Generation                              │  │
│  │    LLM generates final answer                        │  │
│  └──────────────────────────────────────────────────────┘  │
└────────────────────────┬────────────────────────────────────┘
                         │
        ┌────────────────┼────────────────┐
        ▼                ▼                ▼
   ┌─────────┐    ┌─────────────┐   ┌──────────┐
   │RAGService│   │Embedding    │   │AICoreService│
   │(Search) │   │Service      │   │(LLM calls)  │
   └─────────┘    └─────────────┘   └──────────┘
```

---

## How It Works

### Step-by-Step Walkthrough

Let's trace a real query: **"I need luxury watch recommendations"**

#### 1️⃣ Query Expansion (via AICoreService)
```
Input: "I need luxury watch recommendations"
↓
AI generates alternatives:
  - "luxury timepiece inspirations"
  - "premium watch collections"
  - "collector chronograph showcase"
  - "modern horology trends"
↓
Output: [original query, + 3 expansions] = 4 queries total
```

#### 2️⃣ Multi-Strategy Search (Parallel CompletableFutures)
Each of the 4 queries is searched in parallel:
```
Query 1 → RAGService.performRag() → ~8 documents found
Query 2 → RAGService.performRag() → ~12 documents found
Query 3 → RAGService.performRag() → ~6 documents found
Query 4 → RAGService.performRag() → ~10 documents found
```

#### 3️⃣ Re-ranking
All documents are deduplicated and re-ranked. With **semantic strategy**:
```
For each document:
  - Generate embedding of document content
  - Calculate cosine similarity to query embedding
  - Sort by descending similarity
Result: Top N documents, most semantically relevant first
```

#### 4️⃣ Context Optimization
With **high level**:
```
Top 10 documents → AI processes → Optimized summary
- Removes duplicate information
- Improves clarity
- Keeps key facts
Result: ~2000-3000 tokens of focused context
```

#### 5️⃣ Response Generation
```
Prompt: "Based on this context, answer: I need luxury watch recommendations"
Context: [optimized summary from step 4]
↓
LLM generates: "Based on our collection, I recommend..."
```

---

## API Integration

### Endpoint
```
POST /api/ai/advanced-rag/search
```

### Request Body (AdvancedRAGRequest)
```json
{
  "query": "I need luxury watch recommendations",
  "expansionLevel": 3,
  "enableHybridSearch": true,
  "enableContextualSearch": true,
  "categories": ["watches", "jewelry"],
  "maxResults": 20,
  "maxDocuments": 5,
  "rerankingStrategy": "semantic",
  "contextOptimizationLevel": "high",
  "similarityThreshold": 0.5,
  "entityType": "product",
  "context": "User is a collector",
  "metadata": {
    "priceRange": "premium",
    "brand": "Rolex"
  }
}
```

### Response (AdvancedRAGResponse)
```json
{
  "query": "I need luxury watch recommendations",
  "expandedQueries": [
    "I need luxury watch recommendations",
    "luxury timepiece inspirations",
    "premium watch collections"
  ],
  "response": "Based on our collection, I recommend the following luxury watches...",
  "context": "Optimized context summary with key information...",
  "documents": [
    {
      "id": "doc_001",
      "title": "Rolex Submariner Collection",
      "content": "The Rolex Submariner is a professional diving watch...",
      "type": "product",
      "score": 0.95,
      "similarity": 0.92,
      "metadata": {
        "category": "watches",
        "brand": "Rolex"
      },
      "author": "Product Team",
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ],
  "totalDocuments": 25,
  "usedDocuments": 5,
  "relevanceScores": [0.92, 0.88, 0.85, 0.82, 0.78],
  "confidenceScore": 0.87,
  "processingTimeMs": 342,
  "success": true,
  "rerankingStrategy": "semantic",
  "expansionLevel": 3,
  "contextOptimizationLevel": "high"
}
```

### Key Request Parameters

| Parameter | Type | Default | Purpose |
|-----------|------|---------|---------|
| `query` | String | Required | The user's question |
| `expansionLevel` | Integer | 3 | How many related queries to generate (1-5) |
| `enableHybridSearch` | Boolean | true | Use multiple search strategies |
| `enableContextualSearch` | Boolean | true | Consider user context in search |
| `rerankingStrategy` | String | "semantic" | One of: score, semantic, hybrid, diversity |
| `contextOptimizationLevel` | String | "medium" | One of: low, medium, high |
| `maxResults` | Integer | 20 | Max documents to search for |
| `maxDocuments` | Integer | 5 | Max documents to include in response |
| `similarityThreshold` | Double | 0.5 | Minimum relevance score (0.0-1.0) |
| `entityType` | String | null | Filter by entity type |
| `categories` | List<String> | null | Filter by categories |

---

## Usage Examples

### Example 1: Simple Question with Defaults
```bash
curl -X POST http://localhost:8080/api/ai/advanced-rag/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "What is machine learning?"
  }'
```

### Example 2: Advanced Search with All Options
```bash
curl -X POST http://localhost:8080/api/ai/advanced-rag/search \
  -H "Content-Type: application/json" \
  -d '{
    "query": "luxury watches for collectors",
    "expansionLevel": 4,
    "enableHybridSearch": true,
    "enableContextualSearch": true,
    "categories": ["watches", "collectibles"],
    "maxResults": 50,
    "maxDocuments": 10,
    "rerankingStrategy": "hybrid",
    "contextOptimizationLevel": "high",
    "similarityThreshold": 0.6,
    "entityType": "product",
    "metadata": {
      "priceRange": "premium",
      "condition": "new"
    }
  }'
```

### Example 3: Frontend (React/TypeScript)
```typescript
// Using the useAdvancedRAG hook
import { useAdvancedRAG } from '@/hooks/useAdvancedRAG';

function SearchPage() {
  const { search, results, loading } = useAdvancedRAG();

  const handleSearch = async () => {
    const response = await search({
      query: "What are luxury watches?",
      expansionLevel: 3,
      rerankingStrategy: "semantic",
      contextOptimizationLevel: "high"
    });

    // response.documents contains the found documents
    // response.response contains the AI-generated answer
  };

  return (
    <div>
      <button onClick={handleSearch} disabled={loading}>
        {loading ? 'Searching...' : 'Search'}
      </button>
      {results && (
        <div>
          <h3>Answer:</h3>
          <p>{results.response}</p>
          <h3>Source Documents:</h3>
          {results.documents.map(doc => (
            <div key={doc.id}>
              <h4>{doc.title}</h4>
              <p>{doc.content}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
```

---

## Testing

### Integration Tests Available

We have comprehensive test coverage for AdvancedRAGService:

```
├── AdvancedRAGQueryExpansionIntegrationTest
│   └── Verifies query expansion improves document coverage
├── AdvancedRAGResultRerankingIntegrationTest
│   └── Tests re-ranking strategies (semantic, hybrid, diversity)
├── AdvancedRAGConfidenceScoreIntegrationTest
│   └── Validates confidence score calculation
├── AdvancedRAGMetadataFilteringIntegrationTest
│   └── Tests metadata-based filtering
├── AdvancedRAGContextualSearchIntegrationTest
│   └── Tests context-aware search
├── AdvancedRAGMultiDocumentContextIntegrationTest
│   └── Tests multi-document context optimization
├── AdvancedRAGQueryExpansionCoverageIntegrationTest
│   └── Tests coverage improvement with expansion
└── RAGCrossEntityQueryIntegrationTest
    └── Tests cross-entity search scenarios
```

### Running Tests Locally

```bash
# Run all AdvancedRAG tests
./gradlew test --tests "*AdvancedRAG*"

# Run a specific test
./gradlew test --tests "AdvancedRAGQueryExpansionIntegrationTest"

# Run with coverage
./gradlew test --tests "*AdvancedRAG*" jacocoTestReport
```

### Writing Your Own Test

Example test template:

```java
@SpringBootTest(classes = TestApplication.class)
@ActiveProfiles("dev")
class MyAdvancedRAGTest {

    @Autowired
    private AdvancedRAGService advancedRAGService;

    @Test
    void shouldPerformAdvancedRAGWithExpansion() {
        // Arrange
        AdvancedRAGRequest request = AdvancedRAGRequest.builder()
            .query("test query")
            .expansionLevel(3)
            .rerankingStrategy("semantic")
            .contextOptimizationLevel("high")
            .maxDocuments(5)
            .build();

        // Act
        AdvancedRAGResponse response = advancedRAGService.performAdvancedRAG(request);

        // Assert
        assertNotNull(response);
        assertTrue(response.getSuccess());
        assertFalse(response.getDocuments().isEmpty());
        assertNotNull(response.getResponse());
        assertTrue(response.getProcessingTimeMs() > 0);
    }
}
```

---

## Troubleshooting

### Common Issues & Solutions

#### ❌ "Empty document list returned"
**Cause:** No documents matching the query in vector database
**Solution:**
- Check if documents are indexed: `GET /api/ai/vector-db/status`
- Increase `maxResults` parameter
- Lower `similarityThreshold`
- Check query logic and ensure documents match entity type

#### ❌ "Processing takes too long (>5 seconds)"
**Cause:** Too many documents, high optimization level, or LLM latency
**Solution:**
- Reduce `maxResults` (default: 20)
- Change `contextOptimizationLevel` from "high" to "medium"
- Reduce `expansionLevel` (fewer parallel searches)
- Check LLM service status

#### ❌ "Confidence score is low (<0.5)"
**Cause:** Poor document relevance or mismatched query
**Solution:**
- Check document content quality and relevance
- Try different `rerankingStrategy`
- Adjust `categories` filter if applicable
- Provide more context in `context` parameter

#### ❌ "Response is off-topic or hallucinates"
**Cause:** Poor context packaging or irrelevant documents
**Solution:**
- Increase `contextOptimizationLevel` to "high"
- Use `rerankingStrategy: "semantic"` for better relevance
- Lower `similarityThreshold` to filter noise
- Check if documents contain accurate information

#### ❌ "NullPointerException in response mapping"
**Cause:** Missing fields in AdvancedRAGRequest
**Solution:**
- Ensure `query` is provided
- Check all required fields are set
- Enable validation in Spring with `@Valid`

### Debug Logging

Enable debug logs in `application.yml`:

```yaml
logging:
  level:
    com.ai.infrastructure.rag.AdvancedRAGService: DEBUG
    com.ai.infrastructure.rag.RAGService: DEBUG
    com.ai.infrastructure.controller.AdvancedRAGController: DEBUG
```

---

## Common Tasks

### Task 1: Adding a New Re-ranking Strategy

**Location:** `AdvancedRAGService.java`, line 193-211

```java
private List<RAGResponse.RAGDocument> rerankDocuments(
    List<RAGResponse> searchResults, String originalQuery, String strategy) {
    
    List<RAGResponse.RAGDocument> allDocuments = /* ... */;
    
    switch (strategy.toLowerCase()) {
        case "semantic":
            return rerankBySemanticSimilarity(allDocuments, originalQuery);
        case "hybrid":
            return rerankByHybridScore(allDocuments, originalQuery);
        case "diversity":
            return rerankByDiversity(allDocuments);
        case "my-new-strategy":  // ← ADD HERE
            return rerankByMyNewStrategy(allDocuments);
        default:
            return rerankByScore(allDocuments);
    }
}

// Then implement the method
private List<RAGResponse.RAGDocument> rerankByMyNewStrategy(
    List<RAGResponse.RAGDocument> documents) {
    // Your sorting logic here
    return documents.stream()
        .sorted(/* your comparator */)
        .collect(Collectors.toList());
}
```

### Task 2: Modifying Context Optimization

**Location:** `AdvancedRAGService.java`, line 304-321

```java
private String optimizeContext(List<RAGResponse.RAGDocument> documents, String level) {
    if (documents.isEmpty()) {
        return "";
    }
    
    switch (level.toLowerCase()) {
        case "high":
            return optimizeContextHigh(documents);
        case "medium":
            return optimizeContextMedium(documents);
        case "low":
            return optimizeContextLow(documents);
        case "custom":  // ← ADD HERE
            return optimizeContextCustom(documents);
        default:
            return documents.stream()
                .map(RAGResponse.RAGDocument::getContent)
                .collect(Collectors.joining("\n\n"));
    }
}
```

### Task 3: Adding Metrics/Monitoring

The service already logs key metrics. Add custom monitoring:

```java
// In AdvancedRAGService.performAdvancedRAG()
long startTime = System.currentTimeMillis();
// ... processing ...
long processingTime = System.currentTimeMillis() - startTime;

// Log metrics
log.info("Advanced RAG completed - Query: {}, Docs: {}, Time: {}ms, Confidence: {}",
    request.getQuery(),
    convertedDocuments.size(),
    processingTime,
    calculateConfidence(rerankedDocuments));
```

### Task 4: Integrating with Custom Policies

The service is already designed to work with your custom hooks. Override specific methods:

```java
// Example: Custom similarity calculation
@Override
private double calculateCosineSimilarity(List<Double> vector1, List<Double> vector2) {
    // Your custom logic here
    return super.calculateCosineSimilarity(vector1, vector2);
}
```

---

## Quick Reference Cheat Sheet

```
┌─────────────────────────────────────────────────────────────┐
│ AdvancedRAGService Quick Reference                          │
├─────────────────────────────────────────────────────────────┤
│ Main Method: performAdvancedRAG(AdvancedRAGRequest)        │
│ Controller Endpoint: POST /api/ai/advanced-rag/search       │
│                                                             │
│ Key Strategies:                                             │
│   • Query Expansion: Generate 2-4 alternative queries       │
│   • Re-ranking: semantic, hybrid, diversity, score          │
│   • Optimization: low, medium, high                         │
│                                                             │
│ Performance Targets:                                        │
│   • Response Time: <500ms                                   │
│   • Confidence Score: >0.7                                  │
│   • Success Rate: >95%                                      │
│                                                             │
│ Key Classes:                                                │
│   • AdvancedRAGService - Main logic                         │
│   • AdvancedRAGController - HTTP endpoint                   │
│   • AdvancedRAGRequest - Input DTO                          │
│   • AdvancedRAGResponse - Output DTO                        │
└─────────────────────────────────────────────────────────────┘
```

---

## Next Steps

1. **Run the integration tests** to see the service in action
2. **Try the REST API** locally with different parameters
3. **Read the code** - Start with `AdvancedRAGService.performAdvancedRAG()` 
4. **Explore RAGService** - The underlying search component
5. **Check out the frontend hook** - `useAdvancedRAG.ts` for UI integration

---

## Getting Help

- 📚 **Documentation:** Check `/ai-infrastructure-module/docs/`
- 🧪 **Tests:** Look at `integration-tests/src/test/java/com/ai/infrastructure/it/`
- 💬 **Team Slack:** #ai-infrastructure channel
- 📞 **Pair Programming:** Schedule time with someone from the AI team

Welcome aboard! Feel free to ask questions – we're all here to help! 🚀




