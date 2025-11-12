# Relationship-Aware RAG: Smart Hybrid Retrieval System

## 🎯 Overview

This document describes a **creative hybrid retrieval system** that combines:
- **LLM Intent Understanding** - Extracts relationship patterns from natural language queries
- **Vector Similarity Search** - Finds semantically similar content
- **Relational Database Traversal** - Leverages existing database relationships

This system bridges the gap between semantic search and relational queries, enabling intelligent relationship-aware search without requiring a graph database.

## 🏗️ Architecture

```
User Query
    ↓
[RelationshipQueryPlanner] ← Uses LLM to understand intent
    ↓
RelationshipQueryPlan (strategy + paths)
    ↓
[HybridRelationshipRAGService] ← Orchestrates execution
    ├─→ [VectorDatabaseService] (semantic search)
    ├─→ [RelationshipTraversalService] (relational queries)
    └─→ [RAGService] (context building)
    ↓
Enhanced RAG Response (with relationship context)
```

## 🧠 How It Works

### Step 1: LLM Query Planning

The `RelationshipQueryPlanner` uses an LLM to analyze user queries and extract:

1. **Semantic Query** - Cleaned query for vector search
2. **Relationship Paths** - Which relationships to traverse
3. **Query Strategy** - How to combine vector + relational search
4. **Filters** - Direct and relationship-based filters

**Example Query:** *"Find documents created by active users"*

**Generated Plan:**
```json
{
  "semanticQuery": "documents",
  "primaryEntityType": "document",
  "relationshipPaths": [{
    "fromEntityType": "document",
    "relationshipType": "createdBy",
    "toEntityType": "user",
    "direction": "REVERSE",
    "conditions": {"status": "active"}
  }],
  "strategy": "RELATIONSHIP_TRAVERSAL",
  "relationshipFilters": {"user.status": "active"}
}
```

### Step 2: Strategy Execution

The system supports **6 query strategies**:

#### 1. **VECTOR_ONLY**
- Pure semantic similarity search
- Use when: "Find similar documents"

#### 2. **RELATIONAL_ONLY**
- Pure database relationship traversal
- Use when: "Show all documents for user-123"

#### 3. **HYBRID**
- Combine vector search + relational filters
- Use when: "Find AI projects with active users"

#### 4. **RELATIONSHIP_TRAVERSAL**
- Multi-hop relationship queries
- Use when: "Find documents created by users in team-X"

#### 5. **VECTOR_THEN_RELATIONSHIP**
- Vector search first, then enrich with relationships
- Use when: "Find similar documents and show their authors"

#### 6. **RELATIONSHIP_THEN_VECTOR**
- Relational query first, then re-rank by similarity
- Use when: "Find user's documents, ranked by relevance"

### Step 3: Relationship Traversal

The `RelationshipTraversalService` executes relationship queries using:

1. **Metadata-Based Filtering** (Current Design)
   - Uses metadata fields stored in `AISearchableEntity`
   - Example: `metadata: {"createdBy": "user-123", "projectId": "proj-456"}`
   - Works with existing design, no schema changes needed

2. **JPA Query Building** (Extensible)
   - Customers can provide entity relationship mappings
   - Generates JPQL queries for relationship traversal
   - Requires customer to implement `RelationshipQueryBuilder`

## 📝 Usage Examples

### Example 1: Simple Relationship Query

```java
@Autowired
private HybridRelationshipRAGService hybridRAGService;

RAGRequest request = RAGRequest.builder()
    .query("Find documents created by active users")
    .entityType("document")
    .limit(10)
    .build();

RAGResponse response = hybridRAGService.performRelationshipAwareRAG(request);
```

**What happens:**
1. LLM analyzes query → identifies relationship: `document.createdBy → user`
2. Traverses relationships → finds documents where `createdBy` metadata points to active users
3. Returns documents with relationship context

### Example 2: Multi-Hop Traversal

```java
RAGRequest request = RAGRequest.builder()
    .query("Show me documents from projects owned by user-123")
    .entityType("document")
    .limit(20)
    .build();

RAGResponse response = hybridRAGService.performRelationshipAwareRAG(request);
```

**What happens:**
1. LLM identifies path: `document → project → user`
2. Traverses: Find projects owned by user-123 → Find documents in those projects
3. Returns documents with full relationship chain

### Example 3: Hybrid Search

```java
RAGRequest request = RAGRequest.builder()
    .query("Find AI-related documents created this month")
    .entityType("document")
    .limit(15)
    .build();

RAGResponse response = hybridRAGService.performRelationshipAwareRAG(request);
```

**What happens:**
1. Vector search for "AI-related documents"
2. Filter by `createdAt` metadata (this month)
3. Combine results with relationship context

## 🔧 Configuration

### 1. Entity Relationship Metadata

Store relationships in entity metadata when indexing:

```java
// When indexing a document
Map<String, Object> metadata = new HashMap<>();
metadata.put("createdBy", "user-123");
metadata.put("projectId", "proj-456");
metadata.put("category", "ai");

aiCapabilityService.processEntityForAI(document, "document");
```

### 2. Relationship Schema (Optional)

Extend `RelationshipSchemaProvider` to provide relationship mappings:

```java
@Service
public class CustomRelationshipSchemaProvider extends RelationshipSchemaProvider {
    
    @Override
    public EntityRelationshipSchema getSchema(String entityType) {
        // Provide custom relationship mappings
        // This helps LLM understand available relationships
    }
}
```

### 3. JPA Query Builder (Advanced)

For complex relationships, implement custom query builder:

```java
@Service
public class CustomRelationshipQueryBuilder extends RelationshipQueryBuilder {
    
    @Override
    public String buildRelationshipQuery(RelationshipPath path, RelationshipQueryPlan plan) {
        // Generate JPQL queries for your entity relationships
        if ("document".equals(path.getToEntityType()) && 
            "createdBy".equals(path.getRelationshipType())) {
            return "SELECT d.id FROM Document d WHERE d.createdBy.id = :userId";
        }
        return null; // Fall back to metadata approach
    }
}
```

## 🎨 Creative Features

### 1. **Intent-Aware Query Planning**
- LLM understands natural language relationship queries
- Automatically selects optimal strategy
- Handles ambiguous queries gracefully

### 2. **Multi-Strategy Execution**
- Combines multiple search approaches
- Re-ranks results by relevance
- Enriches with relationship context

### 3. **Graceful Degradation**
- Falls back to vector-only if relationships unavailable
- Falls back to relational-only if vector search fails
- Always returns results, even if not optimal

### 4. **Relationship Context Enrichment**
- Automatically includes related entities in results
- Provides relationship metadata
- Enables follow-up queries

## 📊 Performance Considerations

### Caching
- Query plans can be cached (same query → same plan)
- Relationship traversals can be cached
- Vector search results are already cached

### Optimization Tips

1. **Limit Traversal Depth**
   ```java
   plan.setMaxTraversalDepth(2); // Max 2 hops
   ```

2. **Use Appropriate Strategy**
   - Simple queries → VECTOR_ONLY (fastest)
   - Complex relationships → RELATIONSHIP_TRAVERSAL
   - Balanced → HYBRID

3. **Index Metadata Fields**
   - Ensure metadata fields used in relationships are indexed
   - Use database indexes for common relationship queries

## 🚀 Advanced Use Cases

### Use Case 1: Knowledge Graph Queries

**Query:** *"Find all research papers cited by papers about machine learning"*

**Plan:**
- Strategy: RELATIONSHIP_TRAVERSAL
- Path: `paper → cites → paper` (where paper.category = "machine learning")

### Use Case 2: Social Network Queries

**Query:** *"Show me documents shared by my team members"*

**Plan:**
- Strategy: RELATIONSHIP_THEN_VECTOR
- Path: `user → team → members → documents`
- Then re-rank by relevance to query

### Use Case 3: Temporal Relationships

**Query:** *"Find documents created after user-123's last login"*

**Plan:**
- Strategy: HYBRID
- Relationship filter: `createdBy = user-123`
- Temporal filter: `createdAt > user.lastLogin`

## 🔍 Relationship Complexity Support

| Complexity Level | Supported | How |
|-----------------|-----------|-----|
| **Level 1: Direct References** | ✅ Yes | Metadata fields |
| **Level 2: One-Hop Filtering** | ✅ Yes | Metadata + filters |
| **Level 3: Multi-Value Relationships** | ✅ Yes | Array metadata |
| **Level 4: Multi-Hop Traversal** | ✅ Yes | Relationship paths |
| **Level 5: Graph Queries** | ⚠️ Partial | Via relationship paths |
| **Level 6: Complex Joins** | ⚠️ Partial | Via JPA queries |

## 🎯 Benefits

1. **No Schema Changes Required**
   - Works with existing metadata design
   - Leverages current `AISearchableEntity` structure

2. **LLM-Powered Intelligence**
   - Understands natural language relationship queries
   - Automatically plans optimal query strategy

3. **Hybrid Approach**
   - Combines best of both worlds (vector + relational)
   - Provides semantic similarity + relationship accuracy

4. **Extensible**
   - Customers can add custom relationship mappings
   - Supports JPA queries for complex relationships

5. **Backward Compatible**
   - Falls back to standard RAG if relationships unavailable
   - Works with existing vector search infrastructure

## 📚 Integration Guide

### Step 1: Enable Relationship Metadata

When indexing entities, include relationship metadata:

```java
// In your entity indexing code
Map<String, Object> metadata = extractRelationshipMetadata(entity);
aiCapabilityService.processEntityForAI(entity, entityType);
```

### Step 2: Use Hybrid RAG Service

Replace standard RAG calls with relationship-aware version:

```java
// Before
RAGResponse response = ragService.performRag(request);

// After
RAGResponse response = hybridRAGService.performRelationshipAwareRAG(request);
```

### Step 3: Test with Relationship Queries

Try queries like:
- "Find documents created by active users"
- "Show me projects with AI-related documents"
- "Find similar documents to this one, but only from my team"

## 🐛 Troubleshooting

### Issue: LLM returns incorrect relationship paths

**Solution:** 
- Provide better schema description in `RelationshipSchemaProvider`
- Add examples to system prompt
- Check entity type names match configuration

### Issue: Relationship traversal returns no results

**Solution:**
- Verify metadata contains relationship fields
- Check relationship field names match query plan
- Enable debug logging to see traversal steps

### Issue: Performance is slow

**Solution:**
- Reduce `maxTraversalDepth`
- Use VECTOR_ONLY for simple queries
- Cache query plans
- Index metadata fields in database

## 🔮 Future Enhancements

1. **Graph Database Integration**
   - Optional Neo4j/Amazon Neptune support
   - Native graph query capabilities

2. **Relationship Learning**
   - Learn relationship patterns from queries
   - Auto-discover entity relationships

3. **Multi-Model Fusion**
   - Combine results from multiple strategies
   - Learn optimal strategy per query type

4. **Relationship Visualization**
   - Show relationship paths in results
   - Visualize entity connections

## 📖 Summary

This **Relationship-Aware RAG** system creatively combines:
- ✅ LLM understanding of query intent
- ✅ Vector similarity for semantic search  
- ✅ Relational database for relationship traversal
- ✅ Hybrid strategies for optimal results

**Result:** Intelligent relationship-aware search that works with your existing database design! 🚀
