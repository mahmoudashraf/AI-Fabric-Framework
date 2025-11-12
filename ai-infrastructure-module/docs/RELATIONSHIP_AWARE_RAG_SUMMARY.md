# Relationship-Aware RAG: Quick Summary

## 🎯 What We Built

A **smart hybrid retrieval system** that uses **LLM + Vector Search + Relational Database** to handle complex relationship queries.

## 🧠 The Creative Solution

### Problem
- Current design: Flat metadata, no relationship traversal
- User needs: Complex queries like "Find documents created by active users"
- Challenge: Bridge semantic search with relational queries

### Solution: 3-Layer Intelligence

```
┌─────────────────────────────────────────┐
│  Layer 1: LLM Intent Understanding      │
│  - Analyzes user query                  │
│  - Extracts relationship patterns       │
│  - Generates query plan                 │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│  Layer 2: Query Strategy Selection      │
│  - VECTOR_ONLY                          │
│  - RELATIONAL_ONLY                      │
│  - HYBRID                               │
│  - RELATIONSHIP_TRAVERSAL               │
│  - VECTOR_THEN_RELATIONSHIP            │
│  - RELATIONSHIP_THEN_VECTOR            │
└─────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────┐
│  Layer 3: Hybrid Execution              │
│  - Vector similarity search             │
│  - Relational database traversal        │
│  - Result fusion & re-ranking           │
│  - Relationship context enrichment       │
└─────────────────────────────────────────┘
```

## 📦 Components Created

### 1. **RelationshipQueryPlan** (DTO)
- Structured representation of query intent
- Contains relationship paths, filters, strategy

### 2. **RelationshipQueryPlanner** (Service)
- Uses LLM to analyze queries
- Generates structured query plans
- Understands relationship patterns

### 3. **RelationshipSchemaProvider** (Service)
- Provides relationship schema info to LLM
- Can be extended with custom mappings

### 4. **RelationshipTraversalService** (Service)
- Executes relationship queries
- Traverses entity relationships
- Uses metadata + optional JPA queries

### 5. **RelationshipQueryBuilder** (Service)
- Builds JPA queries for relationships
- Extensible by customers

### 6. **HybridRelationshipRAGService** (Main Service)
- Orchestrates the entire flow
- Combines vector + relational search
- Enriches results with relationship context

## 🚀 Usage Example

```java
@Autowired
private HybridRelationshipRAGService hybridRAGService;

// User query: "Find documents created by active users"
RAGRequest request = RAGRequest.builder()
    .query("Find documents created by active users")
    .entityType("document")
    .limit(10)
    .build();

RAGResponse response = hybridRAGService.performRelationshipAwareRAG(request);
```

**What Happens:**
1. LLM analyzes query → identifies `document.createdBy → user` relationship
2. Plans query → strategy: `RELATIONSHIP_TRAVERSAL`
3. Executes → finds documents where `metadata.createdBy` points to active users
4. Returns → documents with relationship context

## ✨ Key Features

### 1. **LLM-Powered Intent Understanding**
- Understands natural language relationship queries
- Extracts relationship patterns automatically
- No need to manually specify relationships

### 2. **6 Query Strategies**
- **VECTOR_ONLY**: Pure semantic search
- **RELATIONAL_ONLY**: Pure database queries
- **HYBRID**: Combine both
- **RELATIONSHIP_TRAVERSAL**: Multi-hop queries
- **VECTOR_THEN_RELATIONSHIP**: Enrich vector results
- **RELATIONSHIP_THEN_VECTOR**: Re-rank relational results

### 3. **Works with Current Design**
- Uses existing metadata fields
- No schema changes required
- Backward compatible

### 4. **Extensible**
- Customers can add custom relationship mappings
- Supports JPA queries for complex relationships
- Can integrate with graph databases

## 📊 Relationship Complexity Support

| Level | Description | Supported |
|-------|-------------|-----------|
| 1 | Direct references (metadata) | ✅ Yes |
| 2 | One-hop filtering | ✅ Yes |
| 3 | Multi-value relationships | ✅ Yes |
| 4 | Multi-hop traversal | ✅ Yes |
| 5 | Graph queries | ⚠️ Partial |
| 6 | Complex joins | ⚠️ Partial |

## 🎨 Creative Aspects

1. **Intent-Aware Planning**: LLM understands what user wants
2. **Strategy Selection**: Automatically picks best approach
3. **Hybrid Fusion**: Combines vector + relational results
4. **Graceful Degradation**: Falls back if relationships unavailable
5. **Context Enrichment**: Adds relationship info to results

## 🔧 Setup Required

### 1. Store Relationship Metadata

When indexing entities, include relationships:

```java
// Example: Document entity
metadata.put("createdBy", "user-123");
metadata.put("projectId", "proj-456");
metadata.put("category", "ai");
```

### 2. Use Hybrid Service

Replace standard RAG calls:

```java
// Before
ragService.performRag(request);

// After  
hybridRAGService.performRelationshipAwareRAG(request);
```

### 3. (Optional) Extend Schema Provider

For better LLM understanding:

```java
@Service
public class CustomRelationshipSchemaProvider extends RelationshipSchemaProvider {
    // Provide custom relationship mappings
}
```

## 📈 Benefits

1. ✅ **No Schema Changes** - Works with existing design
2. ✅ **LLM Intelligence** - Understands natural language
3. ✅ **Hybrid Power** - Best of vector + relational
4. ✅ **Backward Compatible** - Falls back gracefully
5. ✅ **Extensible** - Customers can customize

## 🎯 Use Cases

- ✅ "Find documents created by active users"
- ✅ "Show me projects with AI-related documents"
- ✅ "Find similar documents, but only from my team"
- ✅ "Show documents from projects owned by user-123"
- ✅ "Find research papers cited by ML papers"

## 🚀 Next Steps

1. **Test** with real relationship queries
2. **Tune** LLM prompts for your domain
3. **Extend** schema provider with your relationships
4. **Monitor** performance and optimize strategies

## 📚 Documentation

- Full guide: `RELATIONSHIP_AWARE_RAG.md`
- Code: `com.ai.infrastructure.relationship.*`

---

**Result**: Intelligent relationship-aware search that bridges semantic search with relational queries! 🎉
