# AI Infrastructure Core - User Guide

## Overview

The AI Infrastructure Core is the foundational module that powers intelligent applications with production-ready AI capabilities. It provides a comprehensive framework for embedding generation, semantic search, RAG (Retrieval-Augmented Generation), content analysis, and intelligent indexing — all through simple annotations and service interfaces.

### What This Module Does

- **LLM Integration**: Generate text content using OpenAI, Anthropic, Azure, or any LLM provider
- **Embedding Generation**: Convert text to vectors with swappable providers (ONNX, OpenAI, Cohere, etc.)
- **Semantic Search**: Find similar content using vector similarity
- **RAG (Retrieval-Augmented Generation)**: Combine retrieval with generation for context-aware responses
- **Intelligent Indexing**: Automatic, async indexing with queue management and retry logic
- **Entity Management**: Annotation-driven AI capabilities for JPA entities
- **Privacy & Security**: PII detection, content filtering, access control, compliance
- **Caching**: Multi-level caching for embeddings, plans, and search results
- **Observability**: Health checks, metrics, and performance monitoring

### Target Audience

Developers building AI-powered applications who need:
- Semantic search capabilities
- RAG systems for chatbots or knowledge bases
- Automated content analysis
- Vector database integration
- Privacy-compliant AI processing

---

## Quick Start

### 1. Add the Dependency

```xml
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-fabric-core</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Choose an embedding provider -->
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-onnx-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Choose a vector database -->
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-vector-lucene</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Enable AI Features

```yaml
ai:
  enabled: true
  providers:
    embedding-provider: onnx  # or openai, cohere, etc.
  vector:
    database-type: lucene     # or milvus, qdrant, etc.
```

### 3. Annotate Your Entities

```java
@Entity
@AICapable(
    entityType = "product",
    autoEmbedding = true,
    indexable = true
)
public class Product {
    @Id
    private UUID id;
    
    private String name;
    private String description;
    private String category;
    
    // Getters/setters
}
```

### 4. Use AI Services

```java
@Service
public class ProductSearchService {
    
    @Autowired
    private AICoreService aiCoreService;
    
    @Autowired
    private AISearchService searchService;
    
    @Autowired
    private AIEmbeddingService embeddingService;
    
    public List<Product> searchSimilar(String query) {
        // Generate embedding for query
        AIEmbeddingRequest request = AIEmbeddingRequest.builder()
            .text(query)
            .build();
        AIEmbeddingResponse embedding = embeddingService.generateEmbedding(request);
        
        // Search for similar products
        AISearchRequest searchRequest = AISearchRequest.builder()
            .query(query)
            .entityType("product")
            .limit(10)
            .threshold(0.7)
            .build();
        AISearchResponse results = searchService.search(
            embedding.getEmbedding(), 
            searchRequest
        );
        
        // Convert to Product entities
        return results.getResults().stream()
            .map(r -> productRepository.findById((String) r.get("id")))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }
}
```

**That's it.** Your application now has AI-powered search.

---

## Core Concepts

### Architecture Overview

```
┌─────────────────────────────────────────────────────┐
│  YOUR APPLICATION                                    │
│  @AICapable entities + Service calls                │
└────────┬────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  CORE SERVICES                                      │
│  • AICoreService (LLM generation)                   │
│  • AIEmbeddingService (vector generation)           │
│  • AISearchService (semantic search)                │
│  • RAGService (retrieval + generation)              │
└────────┬────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  INDEXING SYSTEM                                    │
│  • IndexingCoordinator (routes requests)            │
│  • IndexingQueueService (queue management)          │
│  • AsyncIndexingWorker (background processing)      │
│  • BatchIndexingWorker (batch processing)           │
└────────┬────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  PROVIDER ABSTRACTION                               │
│  • EmbeddingProvider (ONNX, OpenAI, Cohere...)     │
│  • VectorDatabase (Lucene, Milvus, Qdrant...)      │
│  • LLM Provider (OpenAI, Anthropic, Azure...)      │
└────────┬────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────┐
│  STORAGE & PERSISTENCE                              │
│  • AISearchableEntity (metadata table)              │
│  • Vector Database (embeddings)                     │
│  • IndexingQueueEntry (processing queue)            │
└─────────────────────────────────────────────────────┘
```

### Annotation-Driven Development

Use annotations to enable AI features declaratively:

```java
@Entity
@AICapable(
    entityType = "article",
    autoEmbedding = true,      // Auto-generate embeddings
    indexable = true,          // Auto-index for search
    enableSearch = true,       // Enable semantic search
    features = {"embedding", "search", "rag"}
)
public class Article {
    @Id
    private UUID id;
    
    @AIEmbedding(weight = 2.0)  // High importance
    private String title;
    
    @AIEmbedding(weight = 1.0)
    private String content;
    
    @AIKnowledge
    private String category;
}
```

### Indexing Strategies

**SYNC**: Immediate indexing (blocks request)
```java
@AICapable(indexingStrategy = IndexingStrategy.SYNC)
```

**ASYNC**: Background indexing (returns immediately)
```java
@AICapable(indexingStrategy = IndexingStrategy.ASYNC)  // Default
```

**BATCH**: Scheduled batch indexing
```java
@AICapable(indexingStrategy = IndexingStrategy.BATCH)
```

**AUTO**: Inherit from operation-level strategy
```java
@AICapable(
    indexingStrategy = IndexingStrategy.ASYNC,  // Default for all
    onCreateStrategy = IndexingStrategy.SYNC,   // Override for creates
    onUpdateStrategy = IndexingStrategy.ASYNC,  // Inherit default
    onDeleteStrategy = IndexingStrategy.SYNC    // Override for deletes
)
```

---

## Configuration Reference

### Core Settings

```yaml
ai:
  # Master switch
  enabled: true                        # Enable AI infrastructure (default: true)
  
  # Provider Selection
  providers:
    embedding-provider: onnx          # onnx, openai, cohere, azure, rest
    llm-provider: openai              # openai, anthropic, azure
    
    # LLM Configuration
    openai-api-key: ${OPENAI_API_KEY}
    openai-model: gpt-4o
    openai-temperature: 0.7
    openai-max-tokens: 1000
    
    # Embedding Configuration
    embedding-model: text-embedding-3-small
    embedding-dimensions: 384
    
    # Fallback
    enable-fallback: true              # Enable provider fallback
  
  # Vector Database
  vector:
    database-type: lucene              # lucene, milvus, qdrant, weaviate, pinecone
    similarity-threshold: 0.7
    max-results: 100
  
  # Indexing
  indexing:
    default-strategy: ASYNC            # SYNC, ASYNC, BATCH
    queue:
      enabled: true
      max-retries: 5
      retry-delay-ms: 1000
      batch-size: 50
    workers:
      async:
        enabled: true
        poll-interval-ms: 5000
        batch-size: 10
      batch:
        enabled: true
        schedule-cron: "0 */5 * * * *"  # Every 5 minutes
        batch-size: 100
    cleanup:
      enabled: true
      schedule-cron: "0 0 2 * * *"      # 2 AM daily
      retention-days: 7
  
  # Caching
  cache:
    enabled: true
    ttl-seconds: 3600
  
  # Cleanup
  cleanup:
    enabled: true
    retention-days: 30
    schedule-cron: "0 0 3 * * *"        # 3 AM daily
```

### Privacy & Security

```yaml
ai:
  privacy:
    pii-detection:
      enabled: true
      mode: REDACT                      # REDACT, ENCRYPT, PASS_THROUGH, BLOCK
      encryption-key: ${PII_ENCRYPTION_KEY}
    
  security:
    enable-content-filtering: true
    enable-access-control: true
    
  compliance:
    enabled: true
    regulations: ["GDPR", "HIPAA", "SOC2"]
```

---

## Core Services

### 1. AICoreService

The primary service for LLM interactions.

#### Generate Text Content

```java
@Autowired
private AICoreService coreService;

public String generateContent(String prompt) {
    AIGenerationRequest request = AIGenerationRequest.builder()
        .prompt("Write a product description for: " + prompt)
        .systemPrompt("You are a creative marketing writer")
        .temperature(0.8)
        .maxTokens(500)
        .build();
    
    AIGenerationResponse response = coreService.generateContent(request);
    return response.getContent();
}
```

#### Simple Text Generation

```java
String result = coreService.generateText("Explain quantum computing in simple terms");
```

### 2. AIEmbeddingService

Generate vector embeddings for semantic search.

#### Single Embedding

```java
@Autowired
private AIEmbeddingService embeddingService;

public List<Double> getEmbedding(String text) {
    AIEmbeddingRequest request = AIEmbeddingRequest.builder()
        .text(text)
        .entityType("document")
        .build();
    
    AIEmbeddingResponse response = embeddingService.generateEmbedding(request);
    return response.getEmbedding();  // 384 dimensions (default)
}
```

#### Batch Embeddings

```java
List<String> texts = List.of(
    "First document",
    "Second document",
    "Third document"
);

List<AIEmbeddingResponse> responses = 
    embeddingService.generateEmbeddings(texts, "document");

// Process embeddings
responses.forEach(r -> {
    System.out.printf("%s: %d dimensions in %dms%n",
        r.getModel(),
        r.getDimensions(),
        r.getProcessingTimeMs()
    );
});
```

#### Async Embeddings

```java
CompletableFuture<AIEmbeddingResponse> future = 
    embeddingService.generateEmbeddingAsync(request);

future.thenAccept(response -> {
    // Handle response
    storeEmbedding(response.getEmbedding());
});
```

### 3. AISearchService

Perform semantic search using vector similarity.

#### Basic Search

```java
@Autowired
private AISearchService searchService;

public List<Map<String, Object>> search(String query) {
    // Generate query embedding
    AIEmbeddingRequest embReq = AIEmbeddingRequest.builder()
        .text(query)
        .build();
    List<Double> queryVector = embeddingService
        .generateEmbedding(embReq)
        .getEmbedding();
    
    // Search
    AISearchRequest searchReq = AISearchRequest.builder()
        .query(query)
        .entityType("product")
        .limit(10)
        .threshold(0.7)
        .build();
    
    AISearchResponse results = searchService.search(queryVector, searchReq);
    return results.getResults();
}
```

#### Hybrid Search

```java
AISearchResponse results = searchService.hybridSearch(
    queryVector,
    "original query text",
    searchRequest
);
```

#### Contextual Search

```java
AISearchResponse results = searchService.contextualSearch(
    queryVector,
    "user is in electronics category",
    searchRequest
);
```

### 4. RAGService

Combine retrieval and generation for intelligent responses.

#### Perform RAG Query

```java
@Autowired
private RAGService ragService;

public String answerQuestion(String question, String entityType) {
    RAGRequest request = RAGRequest.builder()
        .query(question)
        .entityType(entityType)
        .limit(5)
        .threshold(0.8)
        .enableHybridSearch(true)
        .requestId(UUID.randomUUID().toString())
        .build();
    
    RAGResponse response = ragService.performRag(request);
    
    System.out.printf("Found %d relevant documents%n", response.getTotalDocuments());
    System.out.printf("Confidence: %.2f%n", response.getConfidenceScore());
    
    return response.getResponse();
}
```

#### Index Content for RAG

```java
ragService.indexContent(
    "document",                          // Entity type
    "doc-123",                          // Entity ID
    "Content to index...",              // Text content
    Map.of("category", "technical")     // Metadata
);
```

---

## Annotation Reference

### @AICapable

Entity-level annotation to enable AI features.

```java
@Entity
@AICapable(
    entityType = "product",              // Required: unique entity type
    autoEmbedding = true,                // Auto-generate embeddings
    indexable = true,                    // Enable search indexing
    enableSearch = true,                 // Enable semantic search
    enableRecommendations = false,       // Enable recommendations
    features = {"embedding", "search"},  // Enabled features
    indexingStrategy = IndexingStrategy.ASYNC,  // Default strategy
    onCreateStrategy = IndexingStrategy.SYNC,   // Override for creates
    onUpdateStrategy = IndexingStrategy.ASYNC,  // Override for updates
    onDeleteStrategy = IndexingStrategy.SYNC,   // Override for deletes
    migrationRepository = ProductRepository.class  // For migration module
)
public class Product {
    // ...
}
```

### @AIProcess

Method-level annotation for automatic AI processing.

```java
@Service
public class ProductService {
    
    @AIProcess(
        entityType = "product",
        processType = "create",
        generateEmbedding = true,
        indexForSearch = true,
        enableAnalysis = false,
        indexingStrategy = IndexingStrategy.ASYNC
    )
    public Product createProduct(Product product) {
        // Save product
        product = productRepository.save(product);
        
        // AI processing happens automatically via aspect
        return product;
    }
}
```

### @AIEmbedding

Field-level annotation for embedding generation.

```java
@Entity
@AICapable(entityType = "article")
public class Article {
    
    @Id
    private UUID id;
    
    @AIEmbedding(weight = 2.0)  // High importance in embedding
    private String title;
    
    @AIEmbedding(weight = 1.0)
    private String content;
    
    @AIEmbedding(weight = 0.5)  // Lower importance
    private String summary;
}
```

### @AIKnowledge

Mark fields as knowledge for AI context.

```java
@Entity
@AICapable(entityType = "support-ticket")
public class SupportTicket {
    
    @Id
    private UUID id;
    
    @AIKnowledge  // Include in AI analysis
    private String category;
    
    @AIKnowledge
    private String priority;
    
    private String description;
}
```

---

## Indexing System

### How Indexing Works

1. **Entity Change** → Entity created/updated/deleted
2. **Aspect Intercepts** → `@AIProcess` or `@AICapable` triggers
3. **Request Created** → `IndexingRequest` built
4. **Strategy Resolved** → SYNC/ASYNC/BATCH determined
5. **Queued** → Added to `IndexingQueueEntry` table
6. **Worker Picks Up** → `AsyncIndexingWorker` or `BatchIndexingWorker`
7. **Processed** → Embedding generated, vector stored
8. **AISearchableEntity** → Metadata saved for tracking

### Indexing Strategies

**SYNC (Immediate)**:
- Blocks until indexing complete
- Best for: Critical content, real-time requirements
- Latency: +100-500ms per request

**ASYNC (Background)**:
- Returns immediately, indexes in background
- Best for: Most use cases
- Latency: +5-20ms per request

**BATCH**:
- Scheduled batch processing
- Best for: High-volume, non-time-sensitive
- Latency: Processed on schedule (e.g., every 5 minutes)

### Manual Indexing

```java
@Autowired
private IndexingCoordinator indexingCoordinator;

public void indexEntity(Product product) {
    IndexingRequest request = IndexingRequest.builder()
        .entityType("product")
        .entityId(product.getId().toString())
        .entityClassName(Product.class.getName())
        .operation(IndexingOperation.CREATE)
        .strategy(IndexingStrategy.ASYNC)
        .actionPlan(new IndexingActionPlan(true, true, false, false, false))
        .payload(objectMapper.writeValueAsString(product))
        .maxRetries(5)
        .build();
    
    indexingCoordinator.coordinate(request);
}
```

---

## Entity Configuration

### Configuration File Structure

Create `ai-entity-config.yml`:

```yaml
ai-entities:
  product:
    auto-embedding: true
    indexable: true
    features: ["embedding", "search", "recommendations"]
    
    # Searchable fields
    searchable-fields:
      - name: title
        weight: 2.0
      - name: description
        weight: 1.5
      - name: category
        weight: 1.0
    
    # Embeddable fields
    embeddable-fields:
      - name: title
        weight: 2.0
      - name: description
        weight: 1.0
    
    # Metadata fields
    metadata-fields:
      - name: category
        type: string
        include-in-search: true
      - name: price
        type: double
        include-in-search: true
      - name: inStock
        type: boolean
        include-in-search: false
    
    # CRUD operations
    crud-operations:
      create:
        generate-embedding: true
        index-for-search: true
      update:
        generate-embedding: true
        index-for-search: true
      delete:
        remove-from-index: true
```

---

## Privacy & Security Features

### PII Detection

Automatically detect and handle personally identifiable information.

**Configuration**:

```yaml
ai:
  privacy:
    pii-detection:
      enabled: true
      mode: REDACT  # REDACT, ENCRYPT, PASS_THROUGH, BLOCK
      encryption-key: ${PII_ENCRYPTION_KEY}
      patterns:
        - EMAIL
        - PHONE
        - SSN
        - CREDIT_CARD
```

**Usage**:

```java
@Autowired
private PIIDetectionService piiService;

public void processUserInput(String input) {
    PIIDetectionResult result = piiService.detectAndProcess(input);
    
    if (result.isPiiDetected()) {
        System.out.printf("PII detected: %d instances%n", 
            result.getDetections().size());
        System.out.printf("Mode applied: %s%n", 
            result.getModeApplied());
    }
    
    String safeText = result.getProcessedQuery();  // PII handled
    processText(safeText);
}
```

### Access Control

```java
@Autowired
private AIAccessControlService accessControl;

public boolean canAccess(String userId, String entityId) {
    AIAccessControlRequest request = AIAccessControlRequest.builder()
        .userId(userId)
        .entityId(entityId)
        .entityType("document")
        .operation("read")
        .build();
    
    AIAccessControlResponse response = accessControl.checkAccess(request);
    return response.isAllowed();
}
```

### Compliance Checking

```java
@Autowired
private AIComplianceService compliance;

public void checkCompliance(String content) {
    AIComplianceRequest request = AIComplianceRequest.builder()
        .content(content)
        .regulations(List.of("GDPR", "HIPAA"))
        .build();
    
    AIComplianceResponse response = compliance.checkCompliance(request);
    
    if (!response.isCompliant()) {
        System.out.println("Violations: " + response.getViolations());
    }
}
```

### User Data Deletion

```java
@Autowired
private UserDataDeletionService deletionService;

public void deleteUserData(UUID userId) {
    UserDataDeletionResult result = deletionService.deleteUserData(userId);
    
    System.out.printf("Status: %s%n", result.getStatus());
    System.out.printf("Entities deleted: %d%n", result.getDeletedCount());
    System.out.printf("Vectors removed: %d%n", result.getVectorDeletedCount());
}
```

---

## Monitoring & Observability

### Health Checks

```java
GET /actuator/health/ai

{
  "status": "UP",
  "details": {
    "embeddingProvider": "onnx",
    "vectorDatabase": "lucene",
    "indexingQueue": "operational",
    "totalIndexed": 125000
  }
}
```

### Performance Metrics

```java
@Autowired
private AIEmbeddingService embeddingService;

public void logMetrics() {
    Map<String, Object> metrics = embeddingService.getPerformanceMetrics();
    
    System.out.printf("Total embeddings: %d%n", 
        metrics.get("totalEmbeddingsGenerated"));
    System.out.printf("Avg processing time: %.2fms%n", 
        metrics.get("averageProcessingTimeMs"));
    System.out.printf("Cache hits: %s%n", 
        metrics.get("cacheHits"));
}
```

### Search Statistics

```java
Map<String, Object> stats = searchService.getSearchStatistics();
System.out.println(stats);
```

---

## Advanced Features

### Custom Embedding Provider

```java
@Component
public class CustomEmbeddingProvider implements EmbeddingProvider {
    
    @Override
    public String getProviderName() {
        return "custom";
    }
    
    @Override
    public boolean isAvailable() {
        return true;
    }
    
    @Override
    public AIEmbeddingResponse generateEmbedding(AIEmbeddingRequest request) {
        // Your custom logic
        List<Double> embedding = generateCustomEmbedding(request.getText());
        
        return AIEmbeddingResponse.builder()
            .embedding(embedding)
            .model("custom-model")
            .dimensions(embedding.size())
            .build();
    }
    
    @Override
    public int getEmbeddingDimension() {
        return 384;
    }
    
    @Override
    public Map<String, Object> getStatus() {
        return Map.of("provider", "custom", "available", true);
    }
}
```

### Custom Vector Database

```java
@Component
public class CustomVectorDB implements VectorDatabase {
    
    @Override
    public void storeVector(String id, List<Double> vector, Map<String, Object> metadata) {
        // Store in your custom vector DB
    }
    
    @Override
    public AISearchResponse search(List<Double> queryVector, AISearchRequest request) {
        // Implement search logic
        List<Map<String, Object>> results = performSimilaritySearch(
            queryVector, 
            request.getLimit()
        );
        
        return AISearchResponse.builder()
            .results(results)
            .totalResults(results.size())
            .build();
    }
    
    @Override
    public Map<String, Object> getStatistics() {
        return Map.of("totalVectors", getTotalVectorCount());
    }
}
```

---

## Use Case Examples

### Example 1: Semantic Product Search

```java
@Service
public class ProductSearchService {
    
    @Autowired
    private AIEmbeddingService embeddingService;
    
    @Autowired
    private VectorDatabaseService vectorDB;
    
    public List<Product> findSimilar(String query) {
        // Generate embedding
        AIEmbeddingResponse embedding = embeddingService.generateEmbedding(
            AIEmbeddingRequest.builder().text(query).build()
        );
        
        // Search
        AISearchResponse results = vectorDB.searchByEntityType(
            embedding.getEmbedding(),
            "product",
            10,
            0.7
        );
        
        // Convert to entities
        return results.getResults().stream()
            .map(r -> productRepository.findById((String) r.get("entityId")))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
    }
}
```

### Example 2: RAG-Powered Chatbot

```java
@Service
public class ChatbotService {
    
    @Autowired
    private RAGService ragService;
    
    @Autowired
    private AICoreService coreService;
    
    public String chat(String userQuestion) {
        // Retrieve relevant context
        RAGResponse ragResponse = ragService.performRag(
            RAGRequest.builder()
                .query(userQuestion)
                .entityType("knowledge-article")
                .limit(3)
                .threshold(0.75)
                .build()
        );
        
        // Build prompt with context
        String context = ragResponse.getDocuments().stream()
            .map(RAGResponse.RAGDocument::getContent)
            .collect(Collectors.joining("\n\n"));
        
        String prompt = String.format("""
            Context: %s
            
            Question: %s
            
            Provide a helpful answer based on the context.
            """, context, userQuestion);
        
        // Generate response
        return coreService.generateText(prompt);
    }
}
```

### Example 3: Content Recommendations

```java
@Service
public class RecommendationService {
    
    @Autowired
    private AICoreService coreService;
    
    public List<Map<String, Object>> recommend(String userId, String context) {
        return coreService.generateRecommendations(
            "product",
            context,
            10
        );
    }
}
```

---

## Testing

### Unit Testing

```java
@SpringBootTest
class AICoreServiceTest {
    
    @Autowired
    private AICoreService coreService;
    
    @Test
    void shouldGenerateContent() {
        AIGenerationRequest request = AIGenerationRequest.builder()
            .prompt("Write a haiku about Spring Boot")
            .maxTokens(100)
            .build();
        
        AIGenerationResponse response = coreService.generateContent(request);
        
        assertThat(response.getContent()).isNotEmpty();
        assertThat(response.getModel()).isNotNull();
    }
}
```

---

## Troubleshooting

### Issue: No embedding provider available

**Solution**:
```xml
<!-- Add an embedding provider dependency -->
<dependency>
    <groupId>com.ai.fabric</groupId>
    <artifactId>ai-infrastructure-onnx-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Issue: Slow indexing

**Solution**:
```yaml
ai:
  indexing:
    workers:
      async:
        batch-size: 20  # Increase batch size
        poll-interval-ms: 2000  # Poll more frequently
```

### Issue: High memory usage

**Solution**:
```yaml
ai:
  indexing:
    queue:
      batch-size: 25  # Reduce batch size
  cache:
    ttl-seconds: 1800  # Shorter cache TTL
```

---

## Best Practices

### ✅ DO

- Use ASYNC indexing for most cases
- Enable caching in production
- Set appropriate result limits
- Monitor health endpoints
- Use batch embedding generation
- Implement custom providers for special needs

### ❌ DON'T

- Don't use SYNC indexing for high-volume operations
- Don't disable caching in production
- Don't index extremely long texts without chunking
- Don't ignore PII detection warnings
- Don't bypass access control

---

## FAQ

**Q: What LLM providers are supported?**
A: OpenAI, Anthropic, Azure OpenAI, Cohere, and custom providers.

**Q: What vector databases are supported?**
A: Lucene, Milvus, Qdrant, Weaviate, Pinecone, and in-memory.

**Q: Can I use multiple providers?**
A: Yes, with fallback support for resilience.

**Q: Is this production-ready?**
A: Yes. Thread-safe, async processing, error handling, metrics.

**Q: How do I migrate existing data?**
A: Use the Migration Module for bulk indexing.

---

## Version Information

- **Module Version**: 1.0.0
- **Minimum Java**: 17
- **Spring Boot**: 3.x
- **Dependencies**: Provider modules (embedding, vector DB, LLM)

---

*This guide reflects the actual implementation in the codebase. For specific modules (Behavior, Migration, Relationship Query), refer to their respective guides.*
