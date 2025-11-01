# Semantic Search Data Flow in AI Infrastructure Module

This document describes how semantic search works in the AI Infrastructure Module, including the complete data flow from indexing to search execution.

## Overview

Semantic search uses vector embeddings to find content based on meaning rather than exact text matches. The system converts text into high-dimensional vectors (embeddings) and uses cosine similarity to find the most semantically similar content.

## Architecture Components

### 1. **Embedding Generation Layer**
- **Service**: `AIEmbeddingService`
- **Purpose**: Converts text into vector embeddings using OpenAI's embedding models
- **Key Features**:
  - Caching support (`@Cacheable`)
  - Batch processing capabilities
  - Async processing support
  - Performance metrics tracking

### 2. **Vector Storage Layer**
- **Services**: `VectorDatabaseService` implementations
  - `LuceneVectorDatabaseService` (default, file-based)
  - `InMemoryVectorDatabaseService` (for testing)
  - `PineconeVectorDatabase` (for production/cloud)
- **Purpose**: Stores vectors with metadata for efficient similarity search

### 3. **Search Layer**
- **Service**: `VectorSearchService`
- **Purpose**: Performs vector similarity search with ranking and filtering
- **Support**: Hybrid search (vector + text), contextual search

### 4. **Orchestration Layer**
- **Services**: 
  - `AICoreService` (entry point)
  - `AISearchService` (coordination)
  - `VectorManagementService` (vector CRUD operations)
  - `AICapabilityService` (automatic indexing)

---

## Data Flow: Indexing Phase (When Entities Are Saved)

### Step 1: Entity Save Event
When an entity (e.g., User, Post, Job) is saved/updated, the `AICapableProcessor` intercepts the operation.

```
Entity Save/Update
    ↓
AICapableProcessor (intercepts entity operations)
```

### Step 2: Configuration Lookup
The system checks if the entity type has AI capabilities enabled in `ai-entity-config.yml`:

```yaml
entities:
  - entity-type: user
    auto-embedding: true
    embeddable-fields:
      - name: bio
        enable-semantic-search: true
```

### Step 3: Content Extraction
`AICapabilityService.extractEmbeddableContent()` extracts text from configured fields:

```
AICapabilityService.generateEmbeddings()
    ↓
extractEmbeddableContent() → "John Doe Software Engineer Python..."
```

### Step 4: Embedding Generation
The extracted content is sent to `AIEmbeddingService`:

```
AIEmbeddingService.generateEmbedding()
    ↓
OpenAI API (text-embedding-3-small or configured model)
    ↓
List<Double> embedding (1536 dimensions typically)
```

**Process Details**:
1. Text preprocessing (normalization, length limiting)
2. API call to OpenAI with embedding model
3. Caching check (cached if same text + model)
4. Returns `AIEmbeddingResponse` with vector

### Step 5: Metadata Extraction
`AICapabilityService.extractMetadata()` collects additional metadata:

```
Metadata Fields:
- entityId: "123"
- entityType: "user"
- custom fields from config
```

### Step 6: Vector Storage
`VectorManagementService.storeVector()` orchestrates storage:

```
VectorManagementService.storeVector()
    ↓
VectorDatabaseService.storeVector() (Lucene/InMemory/Pinecone)
```

**For Lucene (default)**:
- Creates Lucene `Document` with:
  - `vectorId` (UUID)
  - `entityId`, `entityType`
  - `content` (original text)
  - `embedding` (comma-separated vector values)
  - `metadata` (JSON string)
  - Timestamps
- Indexes document using `IndexWriter`
- Commits and refreshes reader for immediate searchability

### Step 7: Searchable Entity Storage
`AISearchableEntity` record saved to database:

```
AISearchableEntity:
- entityType: "user"
- entityId: "123"
- searchableContent: "John Doe Software Engineer..."
- vectorId: "uuid-from-vector-db"
- metadata: JSON
- timestamps
```

---

## Data Flow: Search Phase (Query Execution)

### Step 1: Search Request
Client sends `AISearchRequest`:

```
POST /api/ai/search
{
  "query": "find software engineers with Python experience",
  "entityType": "user",
  "limit": 10,
  "threshold": 0.7
}
```

### Step 2: Entry Point
Request handled by `AICoreService.performSearch()`:

```
AICoreService.performSearch(AISearchRequest)
```

### Step 3: Query Embedding
Query text converted to embedding:

```
AIEmbeddingRequest:
  text: "find software engineers with Python experience"
    ↓
AIEmbeddingService.generateEmbedding()
    ↓
OpenAI API
    ↓
List<Double> queryVector (1536 dimensions)
```

**Note**: Embeddings are cached based on text + model hash.

### Step 4: Vector Search
Query embedding sent to search layer:

```
AISearchService.search(queryVector, request)
    ↓
VectorSearchService.search(queryVector, request)
```

### Step 5: Similarity Calculation
For each indexed vector in the target entity type:

```
For each entity in entityType:
  1. Retrieve stored embedding
  2. Calculate cosine similarity:
     similarity = dot_product(queryVector, entityVector) 
                  / (norm(queryVector) * norm(entityVector))
  3. Apply threshold filter (similarity >= threshold)
  4. Calculate ranking score:
     - Base: similarity
     - Recency boost (newer content weighted higher)
     - Metadata boost (priority, relevance)
  5. Sort by ranking score (descending)
  6. Limit results
```

### Step 6: Result Assembly
`AISearchResponse` created with:

```
AISearchResponse:
- results: List<Map> [
    {
      "id": "entityId",
      "content": "original text",
      "similarity": 0.87,
      "rankingScore": 0.89,
      "metadata": {...}
    },
    ...
  ]
- totalResults: 5
- maxScore: 0.89
- processingTimeMs: 45
- query: "original query"
- model: "text-embedding-3-small"
```

### Step 7: Response
Results returned to client sorted by relevance (highest similarity first).

---

## Advanced Search Modes

### Hybrid Search
Combines vector similarity + text matching:

```
VectorSearchService.hybridSearch()
  ├─ Vector similarity search
  └─ Text matching (keyword search)
      └─ Combine scores: (vectorScore + textScore) / 2
```

### Contextual Search
Enhances search with additional context:

```
VectorSearchService.contextualSearch()
  ├─ Vector similarity
  └─ Context weighting
      └─ Boost results matching context terms
```

---

## Storage Options

### 1. Lucene (Default)
- **Location**: `./data/lucene-vector-index/`
- **Format**: Apache Lucene index files
- **Use Case**: Development, single-server deployments
- **Features**: 
  - File-based persistence
  - Text search integration
  - Local file system storage

### 2. In-Memory
- **Location**: Java heap memory
- **Format**: `ConcurrentHashMap<String, VectorRecord>`
- **Use Case**: Testing, ephemeral instances
- **Features**:
  - Fast but non-persistent
  - No disk I/O

### 3. Pinecone (Production)
- **Location**: Cloud service
- **Format**: Pinecone vector database
- **Use Case**: Production, distributed systems
- **Features**:
  - Scalable, managed service
  - High-performance similarity search

---

## Performance Optimizations

1. **Caching**: Embeddings cached by text + model hash
2. **Batch Operations**: Multiple embeddings generated in single API call
3. **Async Processing**: Non-blocking embedding generation
4. **Lazy Loading**: Index readers refreshed on-demand
5. **Filtering**: Early threshold filtering reduces computation

---

## Configuration

Entity indexing controlled via `ai-entity-config.yml`:

```yaml
entities:
  - entity-type: user
    auto-embedding: true
    embeddable-fields:
      - name: bio
        enable-semantic-search: true
      - name: skills
        enable-semantic-search: true
    metadata-fields:
      - name: location
      - name: experience
```

Vector database type configured via `application.yml`:

```yaml
ai:
  vector-db:
    type: lucene  # or "memory" or "pinecone"
    lucene:
      index-path: ./data/lucene-vector-index
      similarity-threshold: 0.7
      max-results: 100
```

---

## Example Complete Flow

### Indexing Example:
```
1. User entity saved:
   User(id=1, bio="Python developer with 5 years experience", skills="Python, Java")

2. AICapableProcessor intercepts → AICapabilityService.generateEmbeddings()

3. Content extracted: "Python developer with 5 years experience Python, Java"

4. OpenAI API called:
   Input: "Python developer with 5 years experience Python, Java"
   Output: [0.123, -0.456, 0.789, ...] (1536 dimensions)

5. Vector stored in Lucene:
   - vectorId: "uuid-123"
   - entityId: "1"
   - entityType: "user"
   - content: "Python developer..."
   - embedding: "0.123,-0.456,0.789,..."

6. AISearchableEntity saved to database
```

### Search Example:
```
1. Query: "find experienced Python developers"

2. Query embedding generated: [0.234, -0.345, 0.567, ...]

3. Cosine similarity calculated for all user entities:
   User 1: similarity = 0.89
   User 2: similarity = 0.45
   User 3: similarity = 0.92

4. Filtered (threshold 0.7): Users 1 and 3

5. Ranked: User 3 (0.92), User 1 (0.89)

6. Results returned to client
```

---

## Data Structures

### Vector Embedding
- **Type**: `List<Double>`
- **Dimensions**: 1536 (default OpenAI model)
- **Range**: Typically -1.0 to 1.0
- **Storage**: Comma-separated string in Lucene, native array in memory

### Similarity Score
- **Type**: `Double`
- **Range**: 0.0 to 1.0 (cosine similarity)
- **Interpretation**: 
  - 1.0 = identical meaning
  - 0.7+ = highly similar
  - 0.5-0.7 = somewhat related
  - <0.5 = different meanings

---

## Troubleshooting

### Common Issues:

1. **No search results**
   - Check if entities are indexed (`AISearchableEntity` records exist)
   - Verify threshold isn't too high
   - Check vector database is initialized

2. **Slow search**
   - Check embedding cache hits
   - Consider batch operations for bulk indexing
   - Optimize Lucene index settings

3. **Poor relevance**
   - Adjust similarity threshold
   - Review embedding model selection
   - Improve content extraction (include more relevant fields)

---

## Key Classes Reference

- `AICoreService`: Main entry point for search operations
- `AIEmbeddingService`: Converts text to embeddings
- `AISearchService`: Orchestrates search operations
- `VectorSearchService`: Performs similarity calculations
- `VectorManagementService`: Manages vector CRUD
- `VectorDatabaseService`: Abstract interface for vector storage
- `LuceneVectorDatabaseService`: Lucene-based implementation
- `AICapabilityService`: Automatic entity indexing

SX  ``wFHDQQsa``
